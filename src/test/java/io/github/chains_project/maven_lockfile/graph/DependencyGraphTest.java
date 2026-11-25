package io.github.chains_project.maven_lockfile.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import io.github.chains_project.maven_lockfile.checksum.FakeChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.MavenScope;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.shared.dependency.graph.internal.DefaultDependencyNode;
import org.apache.maven.shared.dependency.graph.internal.VerboseDependencyNodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DependencyGraph#of}. Uses {@link FakeChecksumCalculator} instead of a real checksum
 * calculator, so no local repository or network access is needed.
 */
class DependencyGraphTest {

    private FakeChecksumCalculator calc;
    private MutableGraph<org.apache.maven.shared.dependency.graph.DependencyNode> mavenGraph;
    private org.apache.maven.shared.dependency.graph.DependencyNode project;

    private static Artifact artifact(String groupId, String artifactId, String version, String scope) {
        return new DefaultArtifact(groupId, artifactId, version, scope, "jar", null, new DefaultArtifactHandler("jar"));
    }

    private org.apache.maven.shared.dependency.graph.DependencyNode addChild(
            org.apache.maven.shared.dependency.graph.DependencyNode parent, Artifact artifact) {
        var child = new DefaultDependencyNode(parent, artifact, null, null, null);
        mavenGraph.putEdge(parent, child);
        return child;
    }

    private static DependencyNode findByArtifactId(Set<DependencyNode> nodes, String artifactId) {
        return nodes.stream()
                .filter(n -> n.getArtifactId().equals(ArtifactId.of(artifactId)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No node with artifactId " + artifactId + " in " + nodes));
    }

    @BeforeEach
    void setUp() {
        calc = new FakeChecksumCalculator();
        mavenGraph = GraphBuilder.directed().build();
        project = new DefaultDependencyNode(artifact("com.example", "my-app", "1.0", null));
        mavenGraph.addNode(project);
    }

    @Test
    void projectRootIsRemovedAndDirectDependenciesBecomeRoots() {
        var a = addChild(project, artifact("org.a", "a", "1.0", "compile"));
        addChild(a, artifact("org.b", "b", "2.0", "compile"));
        addChild(project, artifact("org.c", "c", "3.0", "test"));

        DependencyGraph graph = DependencyGraph.of(mavenGraph, calc, false, Set.of());

        Set<DependencyNode> roots = graph.getRoots();
        assertThat(roots).extracting(n -> n.getArtifactId().getValue()).containsExactly("a", "c");
        assertThat(roots).allSatisfy(n -> assertThat(n.getParent()).isNull());

        DependencyNode nodeA = findByArtifactId(roots, "a");
        assertThat(nodeA.getChildren()).hasSize(1);
        DependencyNode nodeB = nodeA.getChildren().iterator().next();
        assertThat(nodeB.getArtifactId().getValue()).isEqualTo("b");
        assertThat(nodeB.getParent()).isEqualTo(nodeA.id);
        assertThat(graph.getParentForNode(nodeB)).contains(nodeA);
    }

    @Test
    void nodesCarryChecksumScopeAndRepositoryInformationFromCalculator() {
        addChild(project, artifact("org.a", "a", "1.0", "test"));

        DependencyNode nodeA = DependencyGraph.of(mavenGraph, calc, false, Set.of())
                .getRoots()
                .iterator()
                .next();

        assertThat(nodeA.getChecksum()).isEqualTo(FakeChecksumCalculator.checksumFor("org.a", "a", "1.0"));
        assertThat(nodeA.getChecksumAlgorithm()).isEqualTo("SHA-256");
        assertThat(nodeA.getScope()).isEqualTo(MavenScope.TEST);
        assertThat(nodeA.getResolved()).isEqualTo(ResolvedUrl.of(FakeChecksumCalculator.REPOSITORY_URL + "a.jar"));
        assertThat(nodeA.getRepositoryId()).isEqualTo(RepositoryId.of("fake"));
        assertThat(nodeA.isIncluded()).isTrue();
        assertThat(nodeA.getSelectedVersion()).isEqualTo("1.0");
    }

    @Test
    void cacheIsPrewarmedOnceWithUniqueNonRootArtifacts() {
        var a = addChild(project, artifact("org.a", "a", "1.0", "compile"));
        var c = addChild(project, artifact("org.c", "c", "1.0", "compile"));
        // the same artifact reached through two paths appears as two separate nodes in Maven's tree
        addChild(a, artifact("org.shared", "shared", "1.0", "compile"));
        addChild(c, artifact("org.shared", "shared", "1.0", "compile"));

        DependencyGraph.of(mavenGraph, calc, false, Set.of());

        assertThat(calc.getPrewarmCalls()).hasSize(1);
        assertThat(calc.getPrewarmCalls().get(0))
                .extracting(Artifact::getArtifactId)
                .containsExactlyInAnyOrder("a", "c", "shared");
        // the project root never gets a checksum
        assertThat(calc.getChecksumRequests())
                .extracting(Artifact::getArtifactId)
                .doesNotContain("my-app");
    }

    @Test
    void reactorSiblingGetsEmptyChecksum() {
        var sibling = artifact("com.example", "module-b", "1.0-SNAPSHOT", "compile");
        addChild(project, sibling);
        addChild(project, artifact("org.a", "a", "1.0", "compile"));

        DependencyGraph graph =
                DependencyGraph.of(mavenGraph, calc, false, Set.of("com.example:module-b:1.0-SNAPSHOT"));

        assertThat(findByArtifactId(graph.getRoots(), "module-b").getChecksum()).isEmpty();
        assertThat(findByArtifactId(graph.getRoots(), "a").getChecksum()).isNotEmpty();
    }

    @Test
    void conflictLoserIsKeptButMarkedNotIncludedInNonReducedMode() {
        var a = addChild(project, artifact("org.a", "a", "1.0", "compile"));
        var loser = VerboseDependencyNodes.conflictLoser(a, artifact("org.x", "x", "1.0", "compile"), "2.0");
        mavenGraph.putEdge(a, loser);

        DependencyNode nodeA = DependencyGraph.of(mavenGraph, calc, false, Set.of())
                .getRoots()
                .iterator()
                .next();

        DependencyNode nodeX = findByArtifactId(nodeA.getChildren(), "x");
        assertThat(nodeX.isIncluded()).isFalse();
        assertThat(nodeX.getVersion().getValue()).isEqualTo("1.0");
        assertThat(nodeX.getSelectedVersion()).isEqualTo("2.0");
    }

    @Test
    void conflictLoserIsDroppedInReducedMode() {
        var a = addChild(project, artifact("org.a", "a", "1.0", "compile"));
        var loser = VerboseDependencyNodes.conflictLoser(a, artifact("org.x", "x", "1.0", "compile"), "2.0");
        mavenGraph.putEdge(a, loser);

        DependencyNode nodeA = DependencyGraph.of(mavenGraph, calc, true, Set.of())
                .getRoots()
                .iterator()
                .next();

        assertThat(nodeA.getChildren()).isEmpty();
    }

    @Test
    void dependencySetFlattensTheTreeAndDeduplicates() {
        var a = addChild(project, artifact("org.a", "a", "1.0", "compile"));
        var c = addChild(project, artifact("org.c", "c", "1.0", "compile"));
        addChild(a, artifact("org.shared", "shared", "1.0", "compile"));
        addChild(c, artifact("org.shared", "shared", "1.0", "compile"));

        Set<DependencyNode> all =
                DependencyGraph.of(mavenGraph, calc, false, Set.of()).getDependencySet();

        assertThat(all.stream().map(n -> n.getArtifactId().getValue()).collect(Collectors.toList()))
                .containsExactly("a", "c", "shared");
    }

    @Test
    void emptyGraphProducesNoNodes() {
        mavenGraph.removeNode(project);

        DependencyGraph graph = DependencyGraph.of(mavenGraph, calc, false, Set.of());

        assertThat(graph.getGraph()).isEmpty();
        assertThat(graph.getDependencySet()).isEmpty();
        assertThat(calc.getPrewarmCalls()).containsExactly(java.util.List.of());
    }

    @Test
    void graphsBuiltFromTheSameInputAreEqual() {
        addChild(project, artifact("org.a", "a", "1.0", "compile"));

        DependencyGraph first = DependencyGraph.of(mavenGraph, calc, false, Set.of());
        DependencyGraph second = DependencyGraph.of(mavenGraph, calc, false, Set.of());

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }
}

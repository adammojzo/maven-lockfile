package io.github.chains_project.maven_lockfile.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.ArtifactType;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MavenExtension;
import io.github.chains_project.maven_lockfile.data.MavenPlugin;
import io.github.chains_project.maven_lockfile.data.MavenScope;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LockFileDifferenceTest {

    private static final GroupId G = GroupId.of("com.example");
    private static final VersionNumber V = VersionNumber.of("1.0");

    private static DependencyNode dep(String artifactId, String checksum) {
        return DependencyNode.of(
                ArtifactId.of(artifactId),
                G,
                V,
                null,
                ArtifactType.of("jar"),
                MavenScope.COMPILE,
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-256",
                checksum);
    }

    private static MavenPlugin plugin(String artifactId) {
        return new MavenPlugin(
                G, ArtifactId.of(artifactId), V, ResolvedUrl.Unresolved(), RepositoryId.None(), "SHA-256", "c");
    }

    private static MavenExtension extension(String artifactId) {
        return new MavenExtension(
                G, ArtifactId.of(artifactId), V, "c", "SHA-256", ResolvedUrl.Unresolved(), RepositoryId.None(), null);
    }

    private static LockFile lockFile(Set<DependencyNode> deps, Set<MavenPlugin> plugins, Set<MavenExtension> ext) {
        return new LockFile(G, ArtifactId.of("app"), V, null, deps, plugins, ext, null, Set.of());
    }

    @Test
    void identicalLockfilesHaveNoDifferences() {
        var file = lockFile(Set.of(dep("a", "c1")), Set.of(plugin("p")), Set.of(extension("e")));
        var project = lockFile(Set.of(dep("a", "c1")), Set.of(plugin("p")), Set.of(extension("e")));

        var diff = LockFileDifference.diff(file, project);

        assertThat(diff.getMissingDependenciesInFile()).isEmpty();
        assertThat(diff.getMissingDependenciesInProject()).isEmpty();
        assertThat(diff.getMissingPluginsInFile()).isEmpty();
        assertThat(diff.getMissingPluginsInProject()).isEmpty();
        assertThat(diff.getMissingExtensionsInFile()).isEmpty();
        assertThat(diff.getMissingExtensionsInProject()).isEmpty();
    }

    @Test
    void reportsElementsMissingOnEachSide() {
        var file = lockFile(Set.of(dep("only-in-file", "c")), Set.of(plugin("p1")), Set.of(extension("e1")));
        var project = lockFile(Set.of(dep("only-in-project", "c")), Set.of(plugin("p2")), Set.of(extension("e2")));

        var diff = LockFileDifference.diff(file, project);

        assertThat(diff.getMissingDependenciesInProject()).containsExactly(dep("only-in-file", "c"));
        assertThat(diff.getMissingDependenciesInFile()).containsExactly(dep("only-in-project", "c"));
        assertThat(diff.getMissingPluginsInProject()).containsExactly(plugin("p1"));
        assertThat(diff.getMissingPluginsInFile()).containsExactly(plugin("p2"));
        assertThat(diff.getMissingExtensionsInProject()).containsExactly(extension("e1"));
        assertThat(diff.getMissingExtensionsInFile()).containsExactly(extension("e2"));
    }

    @Test
    void changedChecksumShowsUpOnBothSides() {
        var file = lockFile(Set.of(dep("a", "old")), Set.of(), Set.of());
        var project = lockFile(Set.of(dep("a", "new")), Set.of(), Set.of());

        var diff = LockFileDifference.diff(file, project);

        assertThat(diff.getMissingDependenciesInProject()).containsExactly(dep("a", "old"));
        assertThat(diff.getMissingDependenciesInFile()).containsExactly(dep("a", "new"));
    }

    @Test
    void gettersReturnDefensiveCopies() {
        var file = lockFile(Set.of(dep("a", "c")), Set.of(), Set.of());
        var diff = LockFileDifference.diff(file, lockFile(Set.of(), Set.of(), Set.of()));

        diff.getMissingDependenciesInProject().clear();

        assertThat(diff.getMissingDependenciesInProject()).hasSize(1);
    }
}

package org.apache.maven.shared.dependency.graph.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.junit.jupiter.api.Test;

class SpyingDependencyNodeUtilsTest {

    private static Artifact artifact(String version) {
        return new DefaultArtifact("com.example", "lib", version, "compile", "jar", null, new DefaultArtifactHandler());
    }

    @Test
    void winnerVersionIsReadFromVerboseNodeConflictData() {
        DependencyNode node = VerboseDependencyNodes.conflictLoser(null, artifact("1.0"), "2.0");

        assertThat(SpyingDependencyNodeUtils.getWinnerVersion(node)).contains("2.0");
    }

    @Test
    void verboseNodeWithoutConflictHasNoWinnerVersion() {
        DependencyNode node = VerboseDependencyNodes.conflictLoser(null, artifact("1.0"), null);

        assertThat(SpyingDependencyNodeUtils.getWinnerVersion(node)).isEmpty();
    }

    @Test
    void nonVerboseNodeHasNoWinnerVersion() {
        DependencyNode node = new DefaultDependencyNode(artifact("1.0"));

        assertThat(SpyingDependencyNodeUtils.getWinnerVersion(node)).isEmpty();
    }
}

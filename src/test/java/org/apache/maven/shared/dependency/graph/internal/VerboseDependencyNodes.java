package org.apache.maven.shared.dependency.graph.internal;

import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.dependency.graph.DependencyNode;

/**
 * Test helper that exposes the package-private {@link VerboseDependencyNode} constructor, so tests in other
 * packages can build nodes that carry Maven's conflict-resolution data.
 */
public final class VerboseDependencyNodes {

    private VerboseDependencyNodes() {}

    /**
     * A node that lost version conflict resolution against {@code winnerVersion}
     * (shown as "omitted for conflict with ..." in {@code mvn dependency:tree -Dverbose}).
     */
    public static DependencyNode conflictLoser(DependencyNode parent, Artifact artifact, String winnerVersion) {
        return new VerboseDependencyNode(
                parent, artifact, null, null, null, false, List.of(), new ConflictData(winnerVersion, null));
    }
}

package io.github.chains_project.maven_lockfile.graph;

import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.ArtifactType;
import io.github.chains_project.maven_lockfile.data.Classifier;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.MavenScope;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import io.github.chains_project.maven_lockfile.data.VersionNumber;

/** Test helper for building {@link DependencyNode} trees from tests outside the {@code graph} package. */
public final class DependencyNodes {

    private DependencyNodes() {}

    public static DependencyNode node(String gav, MavenScope scope, boolean included) {
        return node(gav, null, scope, included);
    }

    /** @param gav {@code groupId:artifactId:version} */
    public static DependencyNode node(String gav, String classifier, MavenScope scope, boolean included) {
        String[] parts = gav.split(":");
        var node = new DependencyNode(
                ArtifactId.of(parts[1]),
                GroupId.of(parts[0]),
                VersionNumber.of(parts[2]),
                Classifier.of(classifier),
                ArtifactType.of("jar"),
                scope,
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-256",
                "checksum-of-" + gav + (classifier == null ? "" : ":" + classifier));
        node.setSelectedVersion(parts[2]);
        node.setIncluded(included);
        return node;
    }

    public static DependencyNode withChildren(DependencyNode parent, DependencyNode... children) {
        for (DependencyNode child : children) {
            parent.addChild(child);
        }
        return parent;
    }
}

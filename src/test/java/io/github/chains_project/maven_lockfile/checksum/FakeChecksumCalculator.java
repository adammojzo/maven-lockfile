package io.github.chains_project.maven_lockfile.checksum;

import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.maven.artifact.Artifact;

/**
 * Hand-written fake for {@link AbstractChecksumCalculator}. Returns deterministic checksums derived from
 * the artifact coordinates instead of hashing files or downloading from a repository, and records every
 * call so tests can assert on how the code under test used it.
 */
public class FakeChecksumCalculator extends AbstractChecksumCalculator {

    public static final String REPOSITORY_URL = "https://fake.example/repo/";

    private final List<Collection<Artifact>> prewarmCalls = new ArrayList<>();
    private final List<Artifact> checksumRequests = new ArrayList<>();

    public FakeChecksumCalculator() {
        super("SHA-256");
    }

    public static String checksumFor(String groupId, String artifactId, String version) {
        return "fake-" + groupId + ":" + artifactId + ":" + version;
    }

    @Override
    public String calculateArtifactChecksum(Artifact artifact) {
        checksumRequests.add(artifact);
        return checksumFor(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }

    @Override
    public String calculatePluginChecksum(Artifact artifact) {
        return calculateArtifactChecksum(artifact);
    }

    @Override
    public String getDefaultChecksumAlgorithm() {
        return "SHA-256";
    }

    @Override
    public RepositoryInformation getArtifactResolvedField(Artifact artifact) {
        return new RepositoryInformation(
                ResolvedUrl.of(REPOSITORY_URL + artifact.getArtifactId() + ".jar"), RepositoryId.of("fake"));
    }

    @Override
    public RepositoryInformation getPluginResolvedField(Artifact artifact) {
        return getArtifactResolvedField(artifact);
    }

    @Override
    public void prewarmArtifactCache(Collection<Artifact> artifacts) {
        prewarmCalls.add(new ArrayList<>(artifacts));
    }

    public List<Collection<Artifact>> getPrewarmCalls() {
        return prewarmCalls;
    }

    public List<Artifact> getChecksumRequests() {
        return checksumRequests;
    }
}

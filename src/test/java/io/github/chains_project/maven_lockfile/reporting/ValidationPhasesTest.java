package io.github.chains_project.maven_lockfile.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.Environment;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MavenExtension;
import io.github.chains_project.maven_lockfile.data.MavenPlugin;
import io.github.chains_project.maven_lockfile.data.MetaData;
import io.github.chains_project.maven_lockfile.data.Pom;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Tests for the built-in {@link ValidationPhase}s. {@link Config} is replaced by a Mockito stub so each test
 * only states the one setting the phase under test reads.
 */
class ValidationPhasesTest {

    private static final GroupId G = GroupId.of("com.example");
    private static final ArtifactId A = ArtifactId.of("app");
    private static final VersionNumber V = VersionNumber.of("1.0");
    private static final Environment ENV = new Environment("Linux", "3.9.11", "17");

    private static ValidationPhase phase(String simpleName) {
        return ValidationPhases.all().stream()
                .filter(p -> p.getClass().getSimpleName().equals(simpleName))
                .findFirst()
                .orElseThrow();
    }

    private static Pom pom(String checksum) {
        return new Pom(G, A, V, "pom.xml", null, null, "SHA-256", checksum, null);
    }

    private static MavenPlugin plugin(String artifactId, String checksum) {
        return new MavenPlugin(
                G, ArtifactId.of(artifactId), V, ResolvedUrl.Unresolved(), RepositoryId.None(), "SHA-256", checksum);
    }

    private static MavenExtension extension(String artifactId) {
        return new MavenExtension(
                G, ArtifactId.of(artifactId), V, "c", "SHA-256", ResolvedUrl.Unresolved(), RepositoryId.None(), null);
    }

    private static LockFile lockFile(
            Pom pom, Set<MavenPlugin> plugins, Set<MavenExtension> extensions, Environment environment) {
        return new LockFile(G, A, V, pom, Set.of(), plugins, extensions, new MetaData(environment, null), Set.of());
    }

    private static LockFile baseline() {
        return lockFile(pom("abc"), Set.of(plugin("p", "c1")), Set.of(extension("e")), ENV);
    }

    @Test
    void allPhasesAreRunInDocumentedOrder() {
        List<String> names = ValidationPhases.all().stream()
                .map(p -> p.getClass().getSimpleName())
                .collect(Collectors.toList());

        assertThat(names)
                .containsExactly(
                        "EnvironmentPhase",
                        "PomPhase",
                        "CorePhase",
                        "MavenPluginsPhase",
                        "BomsPhase",
                        "ParentPomPhase",
                        "ExtensionsPhase");
    }

    @Test
    void identicalLockfilesPassEveryPhase() {
        Config config = mock(Config.class);
        for (ValidationPhase phase : ValidationPhases.all()) {
            assertThat(phase.validate(baseline(), baseline(), config))
                    .as(phase.getClass().getSimpleName())
                    .isEmpty();
        }
    }

    @Test
    void environmentPhaseIsEnabledOnlyWhenEnvironmentIsIncluded() {
        Config config = mock(Config.class);
        when(config.isIncludeEnvironment()).thenReturn(true);
        assertThat(phase("EnvironmentPhase").isEnabled(config)).isTrue();

        when(config.isIncludeEnvironment()).thenReturn(false);
        assertThat(phase("EnvironmentPhase").isEnabled(config)).isFalse();
    }

    @Test
    void environmentPhaseReportsBothEnvironments() {
        Config config = mock(Config.class);
        var other = lockFile(pom("abc"), Set.of(), Set.of(), new Environment("Windows", "3.9.11", "21"));

        var failure = phase("EnvironmentPhase").validate(baseline(), other, config);

        assertThat(failure).hasValueSatisfying(msg -> assertThat(msg)
                .contains("environment does not match")
                .contains("Linux")
                .contains("Windows"));
    }

    @Test
    void environmentPhaseWarnsOnlyWhenConfiguredToWarn() {
        Config config = mock(Config.class);
        when(config.getOnEnvironmentalValidationFailure()).thenReturn(Config.OnEnvironmentalValidationFailure.Warn);
        assertThat(phase("EnvironmentPhase").isWarn(config)).isTrue();

        when(config.getOnEnvironmentalValidationFailure()).thenReturn(Config.OnEnvironmentalValidationFailure.Error);
        assertThat(phase("EnvironmentPhase").isWarn(config)).isFalse();
    }

    @Test
    void pomPhaseIsAlwaysEnabledAndReportsChecksumMismatch() {
        Config config = mock(Config.class);
        var other = lockFile(pom("DIFFERENT"), Set.of(plugin("p", "c1")), Set.of(extension("e")), ENV);

        assertThat(phase("PomPhase").isEnabled(config)).isTrue();
        assertThat(phase("PomPhase").validate(baseline(), other, config)).hasValueSatisfying(msg -> assertThat(msg)
                .contains("Pom checksum mismatch")
                .contains("abc")
                .contains("DIFFERENT"));

        when(config.getOnPomValidationFailure()).thenReturn(Config.OnPomValidationFailure.Warn);
        assertThat(phase("PomPhase").isWarn(config)).isTrue();
    }

    @Test
    void corePhaseReportsProjectCoordinatesOnMismatch() {
        Config config = mock(Config.class);
        var other = new LockFile(
                G, ArtifactId.of("other-app"), V, pom("abc"), Set.of(), Set.of(), Set.of(), null, Set.of());

        assertThat(phase("CorePhase").isEnabled(config)).isTrue();
        assertThat(phase("CorePhase").validate(baseline(), other, config))
                .hasValueSatisfying(
                        msg -> assertThat(msg).contains("com.example:app:1.0").contains("com.example:other-app:1.0"));

        when(config.getOnValidationFailure()).thenReturn(Config.OnValidationFailure.Warn);
        assertThat(phase("CorePhase").isWarn(config)).isTrue();
    }

    @Test
    void mavenPluginsPhaseListsMissingPluginsOnBothSides() {
        Config config = mock(Config.class);
        when(config.isIncludeMavenPlugins()).thenReturn(true);
        var other = lockFile(pom("abc"), Set.of(plugin("q", "c1")), Set.of(extension("e")), ENV);

        assertThat(phase("MavenPluginsPhase").isEnabled(config)).isTrue();
        assertThat(phase("MavenPluginsPhase").validate(baseline(), other, config))
                .hasValueSatisfying(msg -> assertThat(msg)
                        .contains("Maven plugin validation failed")
                        .contains("\"artifactId\": \"p\"")
                        .contains("\"artifactId\": \"q\""));

        when(config.getOnMavenPluginValidationFailure()).thenReturn(Config.OnMavenPluginValidationFailure.Warn);
        assertThat(phase("MavenPluginsPhase").isWarn(config)).isTrue();
    }

    @Test
    void bomsPhaseFailsWhenTopLevelBomsDiffer() {
        Config config = mock(Config.class);
        when(config.isIncludeBoms()).thenReturn(true);
        Pom bom = new Pom(G, ArtifactId.of("bom"), V, null, null, null, "SHA-256", "bomsum", null);
        var withBom = new LockFile(G, A, V, pom("abc"), Set.of(), Set.of(), Set.of(), null, Set.of(bom));
        var withoutBom = new LockFile(G, A, V, pom("abc"), Set.of(), Set.of(), Set.of(), null, Set.of());

        assertThat(phase("BomsPhase").isEnabled(config)).isTrue();
        assertThat(phase("BomsPhase").validate(withBom, withoutBom, config))
                .hasValueSatisfying(msg -> assertThat(msg).startsWith("Bom validation failed"));

        when(config.getOnBomValidationFailure()).thenReturn(Config.OnBomValidationFailure.Warn);
        assertThat(phase("BomsPhase").isWarn(config)).isTrue();
    }

    @Test
    void parentPomPhaseFailsWhenPluginParentPomDiffers() {
        Config config = mock(Config.class);
        when(config.isIncludeParentPom()).thenReturn(true);
        Pom parent = new Pom(G, ArtifactId.of("parent"), V, null, null, null, "SHA-256", "parentsum", null);
        var withParent = new MavenPlugin(
                G, ArtifactId.of("p"), V, ResolvedUrl.Unresolved(), RepositoryId.None(), "SHA-256", "c1", null, parent);
        var other = lockFile(pom("abc"), Set.of(withParent), Set.of(extension("e")), ENV);

        assertThat(phase("ParentPomPhase").isEnabled(config)).isTrue();
        assertThat(phase("ParentPomPhase").validate(baseline(), other, config))
                .hasValueSatisfying(msg -> assertThat(msg).startsWith("Parent POM validation failed"));

        when(config.getOnParentPomValidationFailure()).thenReturn(Config.OnParentPomValidationFailure.Warn);
        assertThat(phase("ParentPomPhase").isWarn(config)).isTrue();
    }

    @Test
    void extensionsPhaseListsMissingExtensions() {
        Config config = mock(Config.class);
        when(config.isIncludeMavenExtensions()).thenReturn(true);
        var other = lockFile(pom("abc"), Set.of(plugin("p", "c1")), Set.of(), ENV);

        assertThat(phase("ExtensionsPhase").isEnabled(config)).isTrue();
        assertThat(phase("ExtensionsPhase").validate(baseline(), other, config))
                .hasValueSatisfying(msg -> assertThat(msg)
                        .contains("Maven extensions validation failed")
                        .contains("\"artifactId\": \"e\""));

        when(config.getOnMavenExtensionsValidationFailure()).thenReturn(Config.OnMavenExtensionsValidationFailure.Warn);
        assertThat(phase("ExtensionsPhase").isWarn(config)).isTrue();
    }

    @Test
    void optionalPhasesAreDisabledByDefault() {
        // a Mockito mock returns false for every boolean getter
        Config config = mock(Config.class);

        assertThat(ValidationPhases.all().stream().filter(p -> p.isEnabled(config)))
                .extracting(p -> p.getClass().getSimpleName())
                .containsExactly("PomPhase", "CorePhase");
    }
}

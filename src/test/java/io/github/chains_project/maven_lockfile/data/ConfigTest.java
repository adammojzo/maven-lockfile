package io.github.chains_project.maven_lockfile.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chains_project.maven_lockfile.JsonUtils;
import io.github.chains_project.maven_lockfile.checksum.ChecksumModes;
import org.junit.jupiter.api.Test;

class ConfigTest {

    private static Config allEnabled() {
        return new Config(
                Config.MavenPluginsInclusion.Include,
                Config.OnValidationFailure.Warn,
                Config.OnPomValidationFailure.Warn,
                Config.OnMavenPluginValidationFailure.Warn,
                Config.OnEnvironmentalValidationFailure.Warn,
                Config.EnvironmentInclusion.Include,
                Config.ReductionState.Reduced,
                "5.18.0",
                ChecksumModes.REMOTE,
                "SHA-512",
                Config.BomsInclusion.Include,
                Config.OnBomValidationFailure.Warn,
                Config.ParentPomInclusion.Include,
                Config.OnParentPomValidationFailure.Warn,
                Config.MavenExtensionsInclusion.Include,
                Config.OnMavenExtensionsValidationFailure.Warn,
                Config.HermeticInclusion.Include);
    }

    private static Config allDisabled() {
        return new Config(
                Config.MavenPluginsInclusion.Exclude,
                Config.OnValidationFailure.Error,
                Config.OnPomValidationFailure.Error,
                Config.OnMavenPluginValidationFailure.Error,
                Config.OnEnvironmentalValidationFailure.Error,
                Config.EnvironmentInclusion.Exclude,
                Config.ReductionState.NonReduced,
                "5.18.0",
                ChecksumModes.LOCAL,
                "SHA-256",
                Config.BomsInclusion.Exclude,
                Config.OnBomValidationFailure.Error,
                Config.ParentPomInclusion.Exclude,
                Config.OnParentPomValidationFailure.Error,
                Config.MavenExtensionsInclusion.Exclude,
                Config.OnMavenExtensionsValidationFailure.Error,
                Config.HermeticInclusion.Exclude);
    }

    @Test
    void defaultConfigUsesLocalSha256AndIncludesPluginsAndEnvironment() {
        Config config = new Config();

        assertThat(config.getChecksumMode()).isEqualTo(ChecksumModes.LOCAL);
        assertThat(config.getChecksumAlgorithm()).isEqualTo("SHA-256");
        assertThat(config.isIncludeMavenPlugins()).isTrue();
        assertThat(config.isIncludeEnvironment()).isTrue();
        assertThat(config.isReduced()).isFalse();
        assertThat(config.isIncludeBoms()).isFalse();
        assertThat(config.isIncludeParentPom()).isFalse();
        assertThat(config.isIncludeMavenExtensions()).isFalse();
        assertThat(config.isHermetic()).isFalse();
        assertThat(config.getOnValidationFailure()).isEqualTo(Config.OnValidationFailure.Error);
    }

    @Test
    void enumAccessorsRoundTripWhenEverythingIsEnabled() {
        Config config = allEnabled();

        assertThat(config.getMavenPluginsInclusion()).isEqualTo(Config.MavenPluginsInclusion.Include);
        assertThat(config.getOnValidationFailure()).isEqualTo(Config.OnValidationFailure.Warn);
        assertThat(config.getOnPomValidationFailure()).isEqualTo(Config.OnPomValidationFailure.Warn);
        assertThat(config.getOnMavenPluginValidationFailure()).isEqualTo(Config.OnMavenPluginValidationFailure.Warn);
        assertThat(config.getOnEnvironmentalValidationFailure())
                .isEqualTo(Config.OnEnvironmentalValidationFailure.Warn);
        assertThat(config.getEnvironmentInclusion()).isEqualTo(Config.EnvironmentInclusion.Include);
        assertThat(config.getReductionState()).isEqualTo(Config.ReductionState.Reduced);
        assertThat(config.getBomsInclusion()).isEqualTo(Config.BomsInclusion.Include);
        assertThat(config.getOnBomValidationFailure()).isEqualTo(Config.OnBomValidationFailure.Warn);
        assertThat(config.getParentPomInclusion()).isEqualTo(Config.ParentPomInclusion.Include);
        assertThat(config.getOnParentPomValidationFailure()).isEqualTo(Config.OnParentPomValidationFailure.Warn);
        assertThat(config.getMavenExtensionsInclusion()).isEqualTo(Config.MavenExtensionsInclusion.Include);
        assertThat(config.getOnMavenExtensionsValidationFailure())
                .isEqualTo(Config.OnMavenExtensionsValidationFailure.Warn);
        assertThat(config.getHermeticInclusion()).isEqualTo(Config.HermeticInclusion.Include);
        assertThat(config.getMavenLockfileVersion()).isEqualTo("5.18.0");
        assertThat(config.getChecksumMode()).isEqualTo(ChecksumModes.REMOTE);
        assertThat(config.getChecksumAlgorithm()).isEqualTo("SHA-512");
    }

    @Test
    void enumAccessorsRoundTripWhenEverythingIsDisabled() {
        Config config = allDisabled();

        assertThat(config.getMavenPluginsInclusion()).isEqualTo(Config.MavenPluginsInclusion.Exclude);
        assertThat(config.getOnPomValidationFailure()).isEqualTo(Config.OnPomValidationFailure.Error);
        assertThat(config.getOnMavenPluginValidationFailure()).isEqualTo(Config.OnMavenPluginValidationFailure.Error);
        assertThat(config.getEnvironmentInclusion()).isEqualTo(Config.EnvironmentInclusion.Exclude);
        assertThat(config.getReductionState()).isEqualTo(Config.ReductionState.NonReduced);
        assertThat(config.getBomsInclusion()).isEqualTo(Config.BomsInclusion.Exclude);
        assertThat(config.getOnBomValidationFailure()).isEqualTo(Config.OnBomValidationFailure.Error);
        assertThat(config.getParentPomInclusion()).isEqualTo(Config.ParentPomInclusion.Exclude);
        assertThat(config.getOnParentPomValidationFailure()).isEqualTo(Config.OnParentPomValidationFailure.Error);
        assertThat(config.getMavenExtensionsInclusion()).isEqualTo(Config.MavenExtensionsInclusion.Exclude);
        assertThat(config.getOnMavenExtensionsValidationFailure())
                .isEqualTo(Config.OnMavenExtensionsValidationFailure.Error);
        assertThat(config.getHermeticInclusion()).isEqualTo(Config.HermeticInclusion.Exclude);
    }

    @Test
    void configSurvivesJsonRoundTrip() {
        Config original = allEnabled();

        Config parsed = JsonUtils.fromJson(JsonUtils.toJson(original), Config.class);

        assertThat(parsed).usingRecursiveComparison().isEqualTo(original);
        assertThat(JsonUtils.toJson(original)).contains("\"checksumMode\": \"remote\"");
    }
}

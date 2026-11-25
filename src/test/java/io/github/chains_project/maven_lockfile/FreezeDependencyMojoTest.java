package io.github.chains_project.maven_lockfile;

import static io.github.chains_project.maven_lockfile.graph.DependencyNodes.node;
import static io.github.chains_project.maven_lockfile.graph.DependencyNodes.withChildren;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.chains_project.maven_lockfile.data.ArtifactId;
import io.github.chains_project.maven_lockfile.data.GroupId;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MavenPlugin;
import io.github.chains_project.maven_lockfile.data.MavenScope;
import io.github.chains_project.maven_lockfile.data.RepositoryId;
import io.github.chains_project.maven_lockfile.data.ResolvedUrl;
import io.github.chains_project.maven_lockfile.data.VersionNumber;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link FreezeDependencyMojo}. The {@link MavenProject} is a Mockito stub that only answers
 * where the project lives, and the Maven {@link Log} is a Mockito mock used to verify which warnings
 * the mojo emits.
 */
class FreezeDependencyMojoTest {

    private static final GroupId G = GroupId.of("com.example");

    @TempDir
    Path projectDir;

    private MavenProject project;
    private Log log;

    @BeforeEach
    void setUp() {
        project = mock(MavenProject.class);
        when(project.getBasedir()).thenReturn(projectDir.toFile());
        when(project.getFile()).thenReturn(projectDir.resolve("pom.xml").toFile());
        log = mock(Log.class);
    }

    private FreezeDependencyMojo mojo(String exactVersionStrings) throws Exception {
        var mojo = new FreezeDependencyMojo();
        setField(mojo, "project", project);
        setField(mojo, "pomLockfileOutput", "pom.lockfile.xml");
        setField(mojo, "lockfileName", "lockfile.json");
        setField(mojo, "exactVersionStrings", exactVersionStrings);
        mojo.setLog(log);
        return mojo;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Dependency dependency(String groupId, String artifactId, String version) {
        var dep = new Dependency();
        dep.setGroupId(groupId);
        dep.setArtifactId(artifactId);
        dep.setVersion(version);
        return dep;
    }

    private void writePom(List<Dependency> dependencies, List<Plugin> plugins) throws Exception {
        var model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId("com.example");
        model.setArtifactId("app");
        model.setVersion("1.0");
        model.setDependencies(dependencies);
        if (!plugins.isEmpty()) {
            var build = new Build();
            build.setPlugins(plugins);
            model.setBuild(build);
        }
        try (var writer = new FileWriter(projectDir.resolve("pom.xml").toFile())) {
            new MavenXpp3Writer().write(writer, model);
        }
    }

    private void writeLockfile(Set<DependencyNode> dependencies, Set<MavenPlugin> plugins) throws Exception {
        var lockFile = new LockFile(
                G,
                ArtifactId.of("app"),
                VersionNumber.of("1.0"),
                null,
                dependencies,
                plugins,
                Set.of(),
                null,
                Set.of());
        Files.writeString(projectDir.resolve("lockfile.json"), JsonUtils.toJson(lockFile));
    }

    private Model readFrozenPom() throws Exception {
        try (var reader = new FileReader(projectDir.resolve("pom.lockfile.xml").toFile())) {
            return new MavenXpp3Reader().read(reader);
        }
    }

    private static <T> java.util.Map<String, T> byArtifactId(List<T> items, Function<T, String> artifactId) {
        return items.stream().collect(Collectors.toMap(artifactId, Function.identity()));
    }

    @Test
    void directDependencyIsPinnedAndTransitiveDependencyIsManaged() throws Exception {
        writePom(List.of(dependency("org.a", "a", "[1.0,2.0)")), List.of());
        writeLockfile(
                Set.of(withChildren(
                        node("org.a:a:1.5", MavenScope.COMPILE, true),
                        withChildren(
                                node("org.b:b:2.0", MavenScope.COMPILE, true),
                                // lost version conflict resolution against another org.x:x
                                node("org.x:x:0.9", MavenScope.COMPILE, false)))),
                Set.of());

        mojo("true").execute();

        Model frozen = readFrozenPom();
        assertThat(frozen.getDependencies()).hasSize(1);
        assertThat(frozen.getDependencies().get(0).getVersion()).isEqualTo("[1.5]");
        assertThat(frozen.getDependencies().get(0).getScope()).isEqualTo("compile");

        var managed = byArtifactId(frozen.getDependencyManagement().getDependencies(), Dependency::getArtifactId);
        assertThat(managed).containsOnlyKeys("b");
        assertThat(managed.get("b").getVersion()).isEqualTo("[2.0]");
    }

    @Test
    void plainVersionsAreWrittenWhenExactVersionStringsIsDisabled() throws Exception {
        writePom(List.of(dependency("org.a", "a", "1.+")), List.of());
        writeLockfile(Set.of(node("org.a:a:1.5", MavenScope.TEST, true)), Set.of());

        mojo("false").execute();

        Dependency frozen = readFrozenPom().getDependencies().get(0);
        assertThat(frozen.getVersion()).isEqualTo("1.5");
        assertThat(frozen.getScope()).isEqualTo("test");
    }

    @Test
    void existingDependencyManagementEntryIsUpdatedInPlace() throws Exception {
        writePom(List.of(), List.of());
        Model model = new MavenXpp3Reader()
                .read(new FileReader(projectDir.resolve("pom.xml").toFile()));
        var management = new org.apache.maven.model.DependencyManagement();
        management.addDependency(dependency("org.b", "b", "1.0"));
        model.setDependencyManagement(management);
        try (var writer = new FileWriter(projectDir.resolve("pom.xml").toFile())) {
            new MavenXpp3Writer().write(writer, model);
        }
        writeLockfile(Set.of(node("org.b:b:2.0", MavenScope.COMPILE, true)), Set.of());

        mojo("true").execute();

        List<Dependency> managed = readFrozenPom().getDependencyManagement().getDependencies();
        assertThat(managed).hasSize(1);
        assertThat(managed.get(0).getVersion()).isEqualTo("[2.0]");
    }

    @Test
    void versionThatIsAlreadyARangeIsKeptAndWarnedAbout() throws Exception {
        writePom(List.of(), List.of());
        writeLockfile(Set.of(node("org.a:a:[1.0,2.0)", MavenScope.COMPILE, true)), Set.of());

        mojo("true").execute();

        assertThat(readFrozenPom()
                        .getDependencyManagement()
                        .getDependencies()
                        .get(0)
                        .getVersion())
                .isEqualTo("[1.0,2.0)");
        verify(log).warn(contains("Version is already a range, '[1.0,2.0)'"));
    }

    @Test
    void pluginsArePinnedAndMissingPluginsAreAddedWithTheirRuntimeDependencies() throws Exception {
        var compiler = new Plugin();
        compiler.setGroupId("org.apache.maven.plugins");
        compiler.setArtifactId("maven-compiler-plugin");
        writePom(List.of(), List.of(compiler));
        var compilerFromLockfile = new MavenPlugin(
                GroupId.of("org.apache.maven.plugins"),
                ArtifactId.of("maven-compiler-plugin"),
                VersionNumber.of("3.14.1"),
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-256",
                "c");
        var extraPlugin = new MavenPlugin(
                GroupId.of("org.extra"),
                ArtifactId.of("extra-plugin"),
                VersionNumber.of("1.2"),
                ResolvedUrl.Unresolved(),
                RepositoryId.None(),
                "SHA-256",
                "c",
                Set.of(
                        node("org.dep:runtime-dep:1.0", MavenScope.RUNTIME, true),
                        node("org.dep:test-dep:1.0", MavenScope.TEST, true),
                        node("org.dep:loser:1.0", MavenScope.COMPILE, false)),
                null);
        writeLockfile(Set.of(), Set.of(compilerFromLockfile, extraPlugin));

        mojo("true").execute();

        var plugins = byArtifactId(readFrozenPom().getBuild().getPlugins(), Plugin::getArtifactId);
        assertThat(plugins).containsOnlyKeys("maven-compiler-plugin", "extra-plugin");
        assertThat(plugins.get("maven-compiler-plugin").getVersion()).isEqualTo("3.14.1");
        // plugin versions are never written as ranges, Maven can't resolve those for plugins
        assertThat(plugins.get("extra-plugin").getVersion()).isEqualTo("1.2");
        assertThat(plugins.get("extra-plugin").getDependencies())
                .extracting(Dependency::getArtifactId)
                .containsExactly("runtime-dep");
        verify(log).warn(contains("Plugin org.extra:extra-plugin not found in POM"));
        verify(log, never()).warn(contains("maven-compiler-plugin"));
    }

    @Test
    void classifierVariantsOfTheSameArtifactAreAllManaged() throws Exception {
        writePom(List.of(dependency("org.a", "a", "1.0")), List.of());
        writeLockfile(
                Set.of(withChildren(
                        node("org.a:a:1.0", MavenScope.COMPILE, true),
                        node("io.netty:netty-transport-native-epoll:4.1.0", "linux-x86_64", MavenScope.COMPILE, true),
                        node(
                                "io.netty:netty-transport-native-epoll:4.1.0",
                                "linux-aarch_64",
                                MavenScope.COMPILE,
                                true))),
                Set.of());

        mojo("true").execute();

        assertThat(readFrozenPom().getDependencyManagement().getDependencies())
                .extracting(Dependency::getClassifier)
                .containsExactlyInAnyOrder("linux-x86_64", "linux-aarch_64");
    }

    @Test
    void missingLockfileFailsWithMojoExecutionException() throws Exception {
        writePom(List.of(), List.of());

        assertThatThrownBy(() -> mojo("true").execute())
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Could not freeze versions");
        assertThat(projectDir.resolve("pom.lockfile.xml")).doesNotExist();
    }
}

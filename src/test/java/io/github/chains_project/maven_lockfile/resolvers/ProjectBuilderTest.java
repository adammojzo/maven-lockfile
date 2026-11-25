package io.github.chains_project.maven_lockfile.resolvers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.codehaus.plexus.PlexusContainer;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * Tests for {@link ProjectBuilder}. Maven's own project builder and the session are Mockito mocks, so the
 * tests can verify which request the wrapper hands to Maven without running a real build.
 */
class ProjectBuilderTest {

    @TempDir
    Path localRepo;

    private MavenSession session;
    private PlexusContainer container;
    private org.apache.maven.project.ProjectBuilder mavenProjectBuilder;
    private ProjectBuildingResult result;
    private Path pomInLocalRepo;

    @BeforeEach
    void setUp() throws Exception {
        pomInLocalRepo = localRepo.resolve("com/example/lib/1.0/lib-1.0.pom");
        Files.createDirectories(pomInLocalRepo.getParent());
        Files.writeString(pomInLocalRepo, "<project/>");

        session = mock(MavenSession.class);
        RepositorySystemSession repositorySession = mock(RepositorySystemSession.class);
        when(session.getRepositorySession()).thenReturn(repositorySession);
        when(repositorySession.getLocalRepository()).thenReturn(new LocalRepository(localRepo.toFile()));
        when(session.getProjectBuildingRequest()).thenReturn(new DefaultProjectBuildingRequest());

        container = mock(PlexusContainer.class);
        when(session.getContainer()).thenReturn(container);
        mavenProjectBuilder = mock(org.apache.maven.project.ProjectBuilder.class);
        when(container.lookup(org.apache.maven.project.ProjectBuilder.class)).thenReturn(mavenProjectBuilder);
        result = mock(ProjectBuildingResult.class);
        when(result.getProject()).thenReturn(new MavenProject());
        when(mavenProjectBuilder.build(any(File.class), any(ProjectBuildingRequest.class)))
                .thenReturn(result);
    }

    private ProjectBuildingRequest capturedRequest() throws Exception {
        ArgumentCaptor<ProjectBuildingRequest> request = ArgumentCaptor.forClass(ProjectBuildingRequest.class);
        verify(mavenProjectBuilder).build(any(File.class), request.capture());
        return request.getValue();
    }

    @Test
    void defaultBuilderResolvesDependencies() throws Exception {
        var project = new ProjectBuilder(session, List.of()).buildFromGav("com.example", "lib", "1.0");

        assertThat(project).isPresent();
        assertThat(capturedRequest().isResolveDependencies()).isTrue();
        assertThat(capturedRequest().isProcessPlugins()).isFalse();
    }

    @Test
    void modelOnlyBuilderSkipsDependencyResolution() throws Exception {
        var project = new ProjectBuilder(session, List.of(), false).buildFromGav("com.example", "lib", "1.0");

        assertThat(project).isPresent();
        assertThat(capturedRequest().isResolveDependencies()).isFalse();
    }

    @Test
    void pomAlreadyInLocalRepositoryIsUsedWithoutResolvingIt() throws Exception {
        new ProjectBuilder(session, List.of(), false).buildFromGav("com.example", "lib", "1.0");

        ArgumentCaptor<File> pomFile = ArgumentCaptor.forClass(File.class);
        verify(mavenProjectBuilder).build(pomFile.capture(), any(ProjectBuildingRequest.class));
        assertThat(pomFile.getValue().toPath()).isEqualTo(pomInLocalRepo);
        verify(container, never()).lookup(ArtifactResolver.class);
    }

    @Test
    void projectThatCannotBeBuiltYieldsEmpty() throws Exception {
        when(result.getProject()).thenReturn(null);

        assertThat(new ProjectBuilder(session, List.of(), false).buildFromGav("com.example", "lib", "1.0"))
                .isEmpty();
    }
}

/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.sonatype.central.publisher.plugin.deffer.ArtifactDeferrerImpl;
import org.sonatype.central.publisher.plugin.model.ArtifactWithFile;
import org.sonatype.central.publisher.plugin.model.DeferArtifactRequest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the deferred deployment of snapshots: the artifacts of every module are installed into a local directory
 * and recorded in an index file, and the last module of the reactor deploys what the index lists to the snapshot
 * repository.
 * <p>
 * The interesting part is the round trip through that index file, and the legacy Maven artifact API the deferrer
 * drives ({@code ArtifactInstaller}, {@code ArtifactDeployer}, {@code ArtifactRepositoryFactory}), which no other
 * test touches - it is the part of the plugin most exposed to an upgrade of the {@code maven-*} artifacts.
 */
class ArtifactDeferrerImplTest
{
  private static final String GROUP_ID = "ch.admin.bit.jeap.test";

  private static final String ARTIFACT_ID = "deferred-project";

  private static final String VERSION = "1.0.0-SNAPSHOT";

  private static final String SNAPSHOTS_URL = "https://central.sonatype.com/repository/maven-snapshots/";

  private static final String SERVER_ID = "central";

  private final ArtifactRepositoryFactory repositoryFactory = mock(ArtifactRepositoryFactory.class);

  private final ArtifactRepositoryLayout repositoryLayout = mock(ArtifactRepositoryLayout.class);

  private final ArtifactInstaller artifactInstaller = mock(ArtifactInstaller.class);

  private final ArtifactDeployer artifactDeployer = mock(ArtifactDeployer.class);

  private final MavenSession session = mock(MavenSession.class);

  private ArtifactDeferrerImpl deferrer;

  private Path deferredDirectory;

  private Path artifactFile;

  private Path pomFile;

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp(@TempDir final Path tempDir) throws IOException {
    deferredDirectory = tempDir.resolve("central-deferred");
    artifactFile = Files.writeString(tempDir.resolve(ARTIFACT_ID + "-" + VERSION + ".jar"), "artifact");
    pomFile = Files.writeString(tempDir.resolve(ARTIFACT_ID + "-" + VERSION + ".pom"), "<project/>");

    // every repository the deferrer asks for is created through the factory; keep id and url, they end up in the index
    when(repositoryFactory.createDeploymentArtifactRepository(
            anyString(), anyString(), any(ArtifactRepositoryLayout.class), anyBoolean()))
        .thenAnswer(invocation -> {
          ArtifactRepository repository = mock(ArtifactRepository.class);
          when(repository.getId()).thenReturn(invocation.getArgument(0));
          when(repository.getUrl()).thenReturn(invocation.getArgument(1));
          when(repository.pathOf(any())).thenAnswer(pathOf -> {
            Artifact artifact = pathOf.getArgument(0);
            return artifact.getGroupId().replace('.', '/') + "/" + artifact.getArtifactId() + "/"
                + artifact.getVersion() + "/" + artifact.getArtifactId() + "-" + artifact.getVersion() + ".jar";
          });
          return repository;
        });

    deferrer = new ArtifactDeferrerImpl(repositoryFactory, repositoryLayout, artifactInstaller, artifactDeployer);
  }

  @Test
  void anInstalledArtifactIsStagedAndRecordedInTheIndex() throws Exception {
    deferrer.install(deferRequest(artifact()));

    verify(artifactInstaller).install(any(File.class), any(Artifact.class), any(ArtifactRepository.class));

    List<String> index = indexLines();
    assertEquals(1, index.size(), "the index should hold one line per installed artifact");
    String line = index.get(0);
    assertTrue(line.contains(GROUP_ID + ":" + ARTIFACT_ID + ":" + VERSION),
        "the index should record the coordinates, but was: " + line);
    assertTrue(line.contains(SERVER_ID + ":" + SNAPSHOTS_URL),
        "the index should record where the artifact has to be deployed, but was: " + line);
    assertTrue(line.contains(pomFile.getFileName().toString()),
        "the index should record the pom that belongs to the artifact, but was: " + line);
  }

  @Test
  void whatWasInstalledIsDeployedToTheRepositoryRecordedInTheIndex() throws Exception {
    deferrer.install(deferRequest(artifact()));

    // no repository passed in: the deferrer has to recreate it from the index, which is what the last module does
    deferrer.deployUp(session, deferredDirectory.toFile(), null);

    ArgumentCaptor<Artifact> deployed = ArgumentCaptor.forClass(Artifact.class);
    ArgumentCaptor<ArtifactRepository> repository = ArgumentCaptor.forClass(ArtifactRepository.class);
    verify(artifactDeployer).deploy(any(File.class), deployed.capture(), repository.capture(), any());

    assertEquals(GROUP_ID, deployed.getValue().getGroupId());
    assertEquals(ARTIFACT_ID, deployed.getValue().getArtifactId());
    assertEquals(VERSION, deployed.getValue().getVersion());
    assertEquals("jar", deployed.getValue().getType());
    assertNull(deployed.getValue().getClassifier(), "the artifact has no classifier");
    assertEquals(SERVER_ID, repository.getValue().getId());
    assertEquals(SNAPSHOTS_URL, repository.getValue().getUrl());
  }

  @Test
  void aClassifiedArtifactKeepsItsClassifier() throws Exception {
    DefaultArtifact sources = new DefaultArtifact(GROUP_ID, ARTIFACT_ID, VersionRange.createFromVersion(VERSION),
        null, "jar", "sources", new DefaultArtifactHandler("jar"));
    deferrer.install(deferRequest(sources));

    deferrer.deployUp(session, deferredDirectory.toFile(), null);

    ArgumentCaptor<Artifact> deployed = ArgumentCaptor.forClass(Artifact.class);
    verify(artifactDeployer).deploy(any(File.class), deployed.capture(), any(), any());
    assertEquals("sources", deployed.getValue().getClassifier());
  }

  @Test
  void aCorruptIndexIsReportedInsteadOfDeployingSomethingWrong() throws Exception {
    Files.createDirectories(deferredDirectory);
    Files.writeString(deferredDirectory.resolve(ArtifactDeferrerImpl.INDEX_FILE_NAME), "some/path=not-an-index-line\n");

    ArtifactDeploymentException failure = assertThrows(ArtifactDeploymentException.class,
        () -> deferrer.deployUp(session, deferredDirectory.toFile(), null));
    assertTrue(failure.getMessage().contains("does not match pattern"), failure.getMessage());
  }

  @Test
  void theConfiguredSnapshotRepositoryWinsOverTheOneOfTheProject() throws Exception {
    deferrer.install(deferRequest(artifact()));

    assertTrue(indexLines().get(0).endsWith(SERVER_ID + ":" + SNAPSHOTS_URL),
        "the configured repository should be the one recorded, but was: " + indexLines().get(0));
  }

  @Test
  @SuppressWarnings("deprecation")
  void withoutAConfiguredUrlTheRepositoryOfTheProjectIsUsed() throws Exception {
    ArtifactRepository fromProject = mock(ArtifactRepository.class);
    when(fromProject.getId()).thenReturn("project-snapshots");
    when(fromProject.getUrl()).thenReturn("https://example.org/snapshots/");
    MavenProject project = mock(MavenProject.class);
    when(project.getDistributionManagementArtifactRepository()).thenReturn(fromProject);
    when(session.getCurrentProject()).thenReturn(project);

    deferrer.install(new DeferArtifactRequest(session, List.of(new ArtifactWithFile(artifactFile.toFile(),
        artifact())), deferredDirectory.toFile(), null, null));

    assertTrue(indexLines().get(0).endsWith("project-snapshots:https://example.org/snapshots/"),
        "the repository of the project should be the one recorded, but was: " + indexLines().get(0));
  }

  @Test
  void withoutAnyRepositoryTheBuildIsToldWhatIsMissing() {
    when(session.getCurrentProject()).thenReturn(mock(MavenProject.class));

    MojoExecutionException failure = assertThrows(MojoExecutionException.class,
        () -> deferrer.install(new DeferArtifactRequest(session, List.of(new ArtifactWithFile(
            artifactFile.toFile(), artifact())), deferredDirectory.toFile(), null, null)));
    assertTrue(failure.getMessage().contains("missing snapshots url"), failure.getMessage());
  }

  private DefaultArtifact artifact() {
    DefaultArtifact artifact = new DefaultArtifact(GROUP_ID, ARTIFACT_ID, VersionRange.createFromVersion(VERSION),
        null, "jar", null, new DefaultArtifactHandler("jar"));
    artifact.addMetadata(new ProjectArtifactMetadata(artifact, pomFile.toFile()));
    return artifact;
  }

  private DeferArtifactRequest deferRequest(final Artifact artifact) {
    return new DeferArtifactRequest(
        session,
        List.of(new ArtifactWithFile(artifactFile.toFile(), artifact)),
        deferredDirectory.toFile(),
        SNAPSHOTS_URL,
        SERVER_ID);
  }

  private List<String> indexLines() throws IOException {
    Path index = deferredDirectory.resolve(ArtifactDeferrerImpl.INDEX_FILE_NAME);
    assertTrue(Files.exists(index), "the deferrer should have written " + index);
    return Files.readAllLines(index, StandardCharsets.ISO_8859_1).stream().filter(l -> !l.isBlank()).toList();
  }
}

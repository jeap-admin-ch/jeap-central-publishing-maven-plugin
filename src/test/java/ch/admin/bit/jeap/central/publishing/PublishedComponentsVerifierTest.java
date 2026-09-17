/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import ch.admin.bit.jeap.central.publishing.PublishedComponentsVerifier.Component;
import org.sonatype.central.publisher.plugin.published.ComponentPublishedChecker;

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishedComponentsVerifierTest
{
  private static final String GROUP_ID = "ch.admin.bit.jeap";

  private static final String ARTIFACT_ID = "jeap-messaging";

  private static final String VERSION = "18.9.0";

  private final ComponentPublishedChecker componentPublishedChecker = mock(ComponentPublishedChecker.class);

  private File stagingDirectory;

  @BeforeEach
  void setUp(@TempDir final Path tempDir) {
    stagingDirectory = tempDir.resolve("central-staging").toFile();
  }

  @Test
  void componentsAreDerivedFromTheRepositoryLayoutOfTheStagingDirectory() throws IOException {
    stage(GROUP_ID, ARTIFACT_ID, VERSION);
    stage(GROUP_ID, "jeap-messaging-kafka", VERSION);

    List<Component> components = verifier(0, 1).findStagedComponents(stagingDirectory);

    assertEquals(2, components.size());
    assertTrue(components.toString().contains("ch.admin.bit.jeap:jeap-messaging:18.9.0"), components.toString());
    assertTrue(components.toString().contains("ch.admin.bit.jeap:jeap-messaging-kafka:18.9.0"), components.toString());
  }

  @Test
  void publishedComponentsAreConfirmedWithoutWaiting() throws IOException {
    stage(GROUP_ID, ARTIFACT_ID, VERSION);
    when(componentPublishedChecker.isComponentPublished(GROUP_ID, ARTIFACT_ID, VERSION)).thenReturn(true);

    assertTrue(verifier(60, 1).allComponentsPublished(stagingDirectory, true));
    verify(componentPublishedChecker, times(1)).isComponentPublished(GROUP_ID, ARTIFACT_ID, VERSION);
  }

  @Test
  void componentsThatShowUpLateAreWaitedFor() throws IOException {
    stage(GROUP_ID, ARTIFACT_ID, VERSION);
    when(componentPublishedChecker.isComponentPublished(GROUP_ID, ARTIFACT_ID, VERSION))
        .thenReturn(false, false, true);

    assertTrue(verifier(60, 0).allComponentsPublished(stagingDirectory, true));
    verify(componentPublishedChecker, times(3)).isComponentPublished(GROUP_ID, ARTIFACT_ID, VERSION);
  }

  @Test
  void nothingIsWaitedForWhenTheDeploymentNeedsToBePublishedManually() throws IOException {
    stage(GROUP_ID, ARTIFACT_ID, VERSION);
    when(componentPublishedChecker.isComponentPublished(GROUP_ID, ARTIFACT_ID, VERSION)).thenReturn(false);

    assertFalse(verifier(60, 1).allComponentsPublished(stagingDirectory, false));
    verify(componentPublishedChecker, times(1)).isComponentPublished(GROUP_ID, ARTIFACT_ID, VERSION);
  }

  @Test
  void componentsThatNeverShowUpAreReportedAsNotPublished() throws IOException {
    stage(GROUP_ID, ARTIFACT_ID, VERSION);
    when(componentPublishedChecker.isComponentPublished(GROUP_ID, ARTIFACT_ID, VERSION)).thenReturn(false);

    assertFalse(verifier(1, 1).allComponentsPublished(stagingDirectory, true));
  }

  @Test
  void failingChecksDoNotConfirmAComponent() throws IOException {
    stage(GROUP_ID, ARTIFACT_ID, VERSION);
    when(componentPublishedChecker.isComponentPublished(GROUP_ID, ARTIFACT_ID, VERSION))
        .thenThrow(new RuntimeException("Cannot get component published status"));

    assertFalse(verifier(60, 1).allComponentsPublished(stagingDirectory, false));
  }

  @Test
  void anEmptyStagingDirectoryCannotBeConfirmed() {
    assertFalse(verifier(60, 1).allComponentsPublished(stagingDirectory, true));
  }

  private PublishedComponentsVerifier verifier(final int graceSeconds, final int pollIntervalSeconds) {
    RetryConfig config = new RetryConfig(60, 900, 4, 0, 0, true, graceSeconds, pollIntervalSeconds);
    return new PublishedComponentsVerifier(componentPublishedChecker, config, new SystemStreamLog());
  }

  private void stage(final String groupId, final String artifactId, final String version) throws IOException {
    Path versionDirectory = stagingDirectory.toPath()
        .resolve(groupId.replace('.', '/'))
        .resolve(artifactId)
        .resolve(version);
    Files.createDirectories(versionDirectory);

    Files.createFile(versionDirectory.resolve(artifactId + "-" + version + ".pom"));
    Files.createFile(versionDirectory.resolve(artifactId + "-" + version + ".jar"));
  }
}

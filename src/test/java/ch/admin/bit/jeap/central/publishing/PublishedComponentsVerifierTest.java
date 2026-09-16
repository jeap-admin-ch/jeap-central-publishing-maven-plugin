/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.io.File;
import java.io.IOException;
import java.util.List;

import ch.admin.bit.jeap.central.publishing.PublishedComponentsVerifier.Component;
import org.sonatype.central.publisher.plugin.published.ComponentPublishedChecker;

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PublishedComponentsVerifierTest
{
  private static final String GROUP_ID = "ch.admin.bit.jeap";

  private static final String ARTIFACT_ID = "jeap-messaging";

  private static final String VERSION = "18.9.0";

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final ComponentPublishedChecker componentPublishedChecker = mock(ComponentPublishedChecker.class);

  private File stagingDirectory;

  @Before
  public void setUp() throws IOException {
    stagingDirectory = temporaryFolder.newFolder("central-staging");
  }

  @Test
  public void componentsAreDerivedFromTheRepositoryLayoutOfTheStagingDirectory() throws IOException {
    stage(GROUP_ID, ARTIFACT_ID, VERSION);
    stage("ch.admin.bit.jeap", "jeap-messaging-kafka", "18.9.0");

    List<Component> components = verifier(0, 1).findStagedComponents(stagingDirectory);

    assertEquals(2, components.size());
    assertTrue(components.toString(), components.toString().contains("ch.admin.bit.jeap:jeap-messaging:18.9.0"));
    assertTrue(components.toString(),
        components.toString().contains("ch.admin.bit.jeap:jeap-messaging-kafka:18.9.0"));
  }

  @Test
  public void publishedComponentsAreConfirmedWithoutWaiting() throws IOException {
    stage(GROUP_ID, ARTIFACT_ID, VERSION);
    when(componentPublishedChecker.isComponentPublished(eq(GROUP_ID), eq(ARTIFACT_ID), eq(VERSION)))
        .thenReturn(true);

    assertTrue(verifier(60, 1).allComponentsPublished(stagingDirectory, true));
    verify(componentPublishedChecker, times(1)).isComponentPublished(GROUP_ID, ARTIFACT_ID, VERSION);
  }

  @Test
  public void componentsThatShowUpLateAreWaitedFor() throws IOException {
    stage(GROUP_ID, ARTIFACT_ID, VERSION);
    when(componentPublishedChecker.isComponentPublished(eq(GROUP_ID), eq(ARTIFACT_ID), eq(VERSION)))
        .thenReturn(false, false, true);

    assertTrue(verifier(60, 0).allComponentsPublished(stagingDirectory, true));
    verify(componentPublishedChecker, times(3)).isComponentPublished(GROUP_ID, ARTIFACT_ID, VERSION);
  }

  @Test
  public void nothingIsWaitedForWhenTheDeploymentNeedsToBePublishedManually() throws IOException {
    stage(GROUP_ID, ARTIFACT_ID, VERSION);
    when(componentPublishedChecker.isComponentPublished(eq(GROUP_ID), eq(ARTIFACT_ID), eq(VERSION)))
        .thenReturn(false);

    assertFalse(verifier(60, 1).allComponentsPublished(stagingDirectory, false));
    verify(componentPublishedChecker, times(1)).isComponentPublished(GROUP_ID, ARTIFACT_ID, VERSION);
  }

  @Test
  public void componentsThatNeverShowUpAreReportedAsNotPublished() throws IOException {
    stage(GROUP_ID, ARTIFACT_ID, VERSION);
    when(componentPublishedChecker.isComponentPublished(eq(GROUP_ID), eq(ARTIFACT_ID), eq(VERSION)))
        .thenReturn(false);

    assertFalse(verifier(1, 1).allComponentsPublished(stagingDirectory, true));
  }

  @Test
  public void failingChecksDoNotConfirmAComponent() throws IOException {
    stage(GROUP_ID, ARTIFACT_ID, VERSION);
    when(componentPublishedChecker.isComponentPublished(eq(GROUP_ID), eq(ARTIFACT_ID), eq(VERSION)))
        .thenThrow(new RuntimeException("Cannot get component published status"));

    assertFalse(verifier(60, 1).allComponentsPublished(stagingDirectory, false));
  }

  @Test
  public void anEmptyStagingDirectoryCannotBeConfirmed() {
    assertFalse(verifier(60, 1).allComponentsPublished(stagingDirectory, true));
  }

  private PublishedComponentsVerifier verifier(final int graceSeconds, final int pollIntervalSeconds) {
    RetryConfig config = new RetryConfig(60, 900, 4, 0, 0, true, graceSeconds, pollIntervalSeconds);
    return new PublishedComponentsVerifier(componentPublishedChecker, config, new SystemStreamLog());
  }

  private void stage(final String groupId, final String artifactId, final String version) throws IOException {
    File versionDirectory = new File(stagingDirectory,
        groupId.replace('.', File.separatorChar) + File.separator + artifactId + File.separator + version);
    assertTrue(versionDirectory.mkdirs() || versionDirectory.isDirectory());

    assertTrue(new File(versionDirectory, artifactId + "-" + version + ".pom").createNewFile());
    assertTrue(new File(versionDirectory, artifactId + "-" + version + ".jar").createNewFile());
  }
}

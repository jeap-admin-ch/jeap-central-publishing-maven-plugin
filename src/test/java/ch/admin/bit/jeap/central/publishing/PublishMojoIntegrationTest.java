/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.inject.Inject;

import org.sonatype.central.publisher.client.model.DeploymentState;
import org.sonatype.central.publisher.plugin.PublishMojo;
import org.sonatype.central.publisher.plugin.exceptions.DeploymentPublishFailedException;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static ch.admin.bit.jeap.central.publishing.RetryConfig.CONNECT_TIMEOUT_SECONDS;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.MAX_RETRIES;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.PUBLISHED_GRACE_SECONDS;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.RETRY_INITIAL_DELAY_SECONDS;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.RETRY_MAX_DELAY_SECONDS;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.SOCKET_TIMEOUT_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Drives the {@code publish} goal against a local stand-in for the Central Publisher Portal, covering the whole cycle
 * of staging, bundling, uploading and watching a deployment - including the upload retry that this fork adds.
 * <p>
 * The maven-plugin-testing-harness configures the mojo from {@code src/test/resources/unit/publish-project/pom.xml}
 * and hands it a mocked {@link MavenSession}, which is completed here with the project to publish.
 */
@MojoTest
class PublishMojoIntegrationTest
{
  private static final String POM = "classpath:/unit/publish-project/pom.xml";

  private static final String POM_CHECKSUMS = "classpath:/unit/publish-project-checksums/pom.xml";

  private static final String POM_SKIP = "classpath:/unit/publish-project-skip/pom.xml";

  private static final String POM_UPLOADED = "classpath:/unit/publish-project-uploaded/pom.xml";

  private static final String POM_PUBLISHED = "classpath:/unit/publish-project-published/pom.xml";

  private static final String GROUP_ID = "ch.admin.bit.jeap.test";

  private static final String ARTIFACT_ID = "publish-project";

  private static final String VERSION = "1.0.0";

  private static final String PLUGIN_GROUP_ID = "ch.admin.bit.jeap";

  private static final String PLUGIN_ARTIFACT_ID = "jeap-central-publishing-maven-plugin";

  private final StubPortal portal = new StubPortal();

  @Inject
  private MavenSession session;

  /**
   * Staging uses the legacy artifact installer, which takes the session from here rather than from the mojo.
   */
  @Inject
  private LegacySupport legacySupport;

  private Path projectDirectory;

  @BeforeEach
  void setUp(@TempDir final Path tempDir) throws IOException {
    UploadRetryState.reset();
    projectDirectory = tempDir;

    // short timeouts and no backoff, so that a read timeout happens - and is retried - within a few seconds
    System.setProperty(SOCKET_TIMEOUT_SECONDS, "1");
    System.setProperty(CONNECT_TIMEOUT_SECONDS, "2");
    System.setProperty(MAX_RETRIES, "3");
    System.setProperty(RETRY_INITIAL_DELAY_SECONDS, "0");
    System.setProperty(RETRY_MAX_DELAY_SECONDS, "0");
    System.setProperty(PUBLISHED_GRACE_SECONDS, "0");

    portal.start();

    // the pom configuring the mojo refers to this, see src/test/resources/unit/publish-project/pom.xml
    session.getUserProperties().setProperty("test.centralBaseUrl", portal.baseUrl());
  }

  @AfterEach
  void tearDown() {
    portal.stop();

    System.clearProperty(SOCKET_TIMEOUT_SECONDS);
    System.clearProperty(CONNECT_TIMEOUT_SECONDS);
    System.clearProperty(MAX_RETRIES);
    System.clearProperty(RETRY_INITIAL_DELAY_SECONDS);
    System.clearProperty(RETRY_MAX_DELAY_SECONDS);
    System.clearProperty(PUBLISHED_GRACE_SECONDS);
    UploadRetryState.reset();
  }

  @Test
  @InjectMojo(goal = "publish", pom = POM)
  void theDeploymentIsUploadedAndValidated(final PublishMojo mojo) throws Exception {
    givenAProjectToPublish();
    portal.reportsDeploymentState(DeploymentState.VALIDATED);

    mojo.execute();

    assertEquals(1, portal.uploadAttempts());
    assertTrue(Files.exists(bundleFile()), "the bundle should have been created at " + bundleFile());
    assertTrue(portal.statusRequests() > 0, "the deployment should have been watched");
  }

  @Test
  @InjectMojo(goal = "publish", pom = POM)
  void theBundleIsUploadedAgainWhenTheFirstUploadTimesOut(final PublishMojo mojo) throws Exception {
    givenAProjectToPublish();
    portal.onUpload(attempt -> attempt == 1
        ? StubPortal.Reply.readTimeout()
        : StubPortal.Reply.ok(StubPortal.DEPLOYMENT_ID));
    portal.reportsDeploymentState(DeploymentState.VALIDATED);

    mojo.execute();

    assertEquals(2, portal.uploadAttempts(), "the timed out upload should have been repeated once");
    assertTrue(UploadRetryState.wasAmbiguouslyRetried(),
        "the portal may have received the bundle of the timed out attempt");
    assertTrue(Files.exists(bundleFile()), "the bundle should have been created at " + bundleFile());
  }

  @Test
  @InjectMojo(goal = "publish", pom = POM)
  void aDuplicateDeploymentDoesNotFailTheBuildWhenTheComponentsArePublished(final PublishMojo mojo) throws Exception {
    givenAProjectToPublish();
    portal.onUpload(attempt -> attempt == 1
        ? StubPortal.Reply.readTimeout()
        : StubPortal.Reply.ok(StubPortal.DEPLOYMENT_ID));
    // the repeated upload created a second deployment, which fails because the first one was published already
    portal.reportsDeploymentState(DeploymentState.FAILED);
    portal.reportsPublished(true);

    mojo.execute();

    assertEquals(2, portal.uploadAttempts());
    assertTrue(portal.publishedRequests() > 0, "the components should have been looked up on Maven Central");
  }

  @Test
  @InjectMojo(goal = "publish", pom = POM)
  void aFailedDeploymentStillFailsTheBuildWhenNothingWasPublished(final PublishMojo mojo) throws IOException {
    givenAProjectToPublish();
    portal.onUpload(attempt -> attempt == 1
        ? StubPortal.Reply.readTimeout()
        : StubPortal.Reply.ok(StubPortal.DEPLOYMENT_ID));
    portal.reportsDeploymentState(DeploymentState.FAILED);
    portal.reportsPublished(false);

    assertThrows(DeploymentPublishFailedException.class, mojo::execute);

    assertTrue(portal.publishedRequests() > 0, "the components should have been looked up on Maven Central");
  }

  @Test
  @InjectMojo(goal = "publish", pom = POM)
  void aFailedDeploymentFailsTheBuild(final PublishMojo mojo) throws IOException {
    givenAProjectToPublish();
    portal.reportsDeploymentState(DeploymentState.FAILED);
    portal.reportsPublished(false);

    assertThrows(DeploymentPublishFailedException.class, mojo::execute);
  }

  @Test
  @InjectMojo(goal = "publish", pom = POM_PUBLISHED)
  void theBuildCanWaitUntilTheDeploymentIsPublished(final PublishMojo mojo) throws Exception {
    givenAProjectToPublish();
    portal.reportsDeploymentState(DeploymentState.PUBLISHED);

    mojo.execute();

    assertEquals(1, portal.uploadAttempts());
    assertTrue(portal.statusRequests() > 0, "the deployment should have been watched until it was published");
  }

  @Test
  @InjectMojo(goal = "publish", pom = POM_UPLOADED)
  void theBuildDoesNotWaitWhenOnlyTheUploadIsRequested(final PublishMojo mojo) throws Exception {
    givenAProjectToPublish();

    mojo.execute();

    assertEquals(1, portal.uploadAttempts(), "the bundle should still be uploaded");
    assertEquals(0, portal.statusRequests(), "the deployment should not be watched when waiting is not requested");
  }

  @Test
  @InjectMojo(goal = "publish", pom = POM_SKIP)
  void nothingIsBundledOrUploadedWhenPublishingIsSkipped(final PublishMojo mojo) throws Exception {
    givenAProjectToPublish();

    mojo.execute();

    assertEquals(0, portal.uploadAttempts(), "nothing should reach the portal when publishing is skipped");
    assertFalse(Files.exists(bundleFile()), "no bundle should have been written to " + bundleFile());
  }

  @Test
  @InjectMojo(goal = "publish", pom = POM_CHECKSUMS)
  void theBundleHoldsTheArtifactsAndTheRequestedChecksums(final PublishMojo mojo) throws Exception {
    givenAProjectToPublish();
    portal.reportsDeploymentState(DeploymentState.VALIDATED);

    mojo.execute();

    List<String> entries = bundleEntries();
    assertTrue(entries.stream().anyMatch(entry -> entry.endsWith(".pom")),
        "the bundle should hold the pom of the project, but held " + entries);
    for (String algorithm : List.of("md5", "sha1", "sha256", "sha512")) {
      assertTrue(entries.stream().anyMatch(entry -> entry.endsWith("." + algorithm)),
          "the bundle should hold the " + algorithm + " checksums, but held " + entries);
    }
  }

  /**
   * The user token is what authenticates a release; if it stopped reaching the portal, every release would fail.
   */
  @Test
  @InjectMojo(goal = "publish", pom = POM)
  void theUploadIsAuthenticatedWithTheUserTokenFromTheSettings(final PublishMojo mojo) throws Exception {
    givenAProjectToPublish();
    portal.reportsDeploymentState(DeploymentState.VALIDATED);

    mojo.execute();

    String authorization = portal.uploadAuthorization();
    assertNotNull(authorization, "the upload should carry an Authorization header");
    assertTrue(authorization.startsWith("UserToken "),
        "the portal expects the user token scheme, but got: " + authorization);
    assertEquals("a-user-token-name:a-user-token-secret", new String(Base64.getDecoder()
        .decode(authorization.substring("UserToken ".length())), StandardCharsets.UTF_8));
  }

  /**
   * Puts the project to publish into the session. This happens inside the test methods, as the harness sets the
   * session up again while it injects the mojo, which is after {@code @BeforeEach} has run.
   */
  private void givenAProjectToPublish() throws IOException {
    MavenProject project = projectToPublish();

    when(session.getProjects()).thenReturn(Collections.singletonList(project));
    when(session.getCurrentProject()).thenReturn(project);
    when(session.getResult()).thenReturn(new DefaultMavenExecutionResult());
    when(session.getSettings()).thenReturn(settingsWithPublishingCredentials());

    legacySupport.setSession(session);
  }

  private MavenProject projectToPublish() throws IOException {
    Path pomFile = projectDirectory.resolve("pom.xml");
    Files.write(pomFile, ("<project><modelVersion>4.0.0</modelVersion>"
        + "<groupId>" + GROUP_ID + "</groupId>"
        + "<artifactId>" + ARTIFACT_ID + "</artifactId>"
        + "<version>" + VERSION + "</version>"
        + "<packaging>pom</packaging></project>").getBytes(StandardCharsets.UTF_8));

    PluginExecution publishExecution = new PluginExecution();
    publishExecution.setGoals(Collections.singletonList("publish"));

    Plugin plugin = new Plugin();
    plugin.setGroupId(PLUGIN_GROUP_ID);
    plugin.setArtifactId(PLUGIN_ARTIFACT_ID);
    plugin.addExecution(publishExecution);

    Build build = new Build();
    build.setDirectory(buildDirectory().toString());
    build.addPlugin(plugin);

    Model model = new Model();
    model.setModelVersion("4.0.0");
    model.setGroupId(GROUP_ID);
    model.setArtifactId(ARTIFACT_ID);
    model.setVersion(VERSION);
    model.setPackaging("pom");
    model.setBuild(build);

    MavenProject project = new MavenProject(model);
    project.setFile(pomFile.toFile());
    project.setArtifact(new DefaultArtifact(GROUP_ID, ARTIFACT_ID, VERSION, null, "pom", null,
        new DefaultArtifactHandler("pom")));

    return project;
  }

  private static Settings settingsWithPublishingCredentials() {
    Server server = new Server();
    server.setId("central");
    server.setUsername("a-user-token-name");
    server.setPassword("a-user-token-secret");

    Settings settings = new Settings();
    settings.addServer(server);

    return settings;
  }

  private Path buildDirectory() {
    return projectDirectory.resolve("target");
  }

  private List<String> bundleEntries() throws IOException {
    assertTrue(Files.exists(bundleFile()), "the bundle should have been created at " + bundleFile());
    try (ZipFile bundle = new ZipFile(bundleFile().toFile())) {
      return bundle.stream().map(ZipEntry::getName).toList();
    }
  }

  private Path bundleFile() {
    return buildDirectory().resolve("central-publishing").resolve("central-bundle.zip");
  }
}

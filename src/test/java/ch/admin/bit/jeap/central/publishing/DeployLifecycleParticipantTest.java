/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonatype.central.publisher.plugin.DeployLifecycleParticipant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonatype.central.publisher.plugin.Constants.CENTRAL_PUBLISHING_PLUGIN_ARTIFACT_ID;
import static org.sonatype.central.publisher.plugin.Constants.CENTRAL_PUBLISHING_PLUGIN_GROUP_ID;
import static org.sonatype.central.publisher.plugin.Constants.DEPLOY_PHASE;
import static org.sonatype.central.publisher.plugin.Constants.MAVEN_DEPLOY_PLUGIN_ARTIFACT_ID;
import static org.sonatype.central.publisher.plugin.Constants.MAVEN_DEPLOY_PLUGIN_GROUP_ID;
import static org.sonatype.central.publisher.plugin.Constants.NEXUS_STAGING_PLUGIN_ARTIFACT_ID;
import static org.sonatype.central.publisher.plugin.Constants.NEXUS_STAGING_PLUGIN_GROUP_ID;
import static org.sonatype.central.publisher.plugin.Constants.PUBLISH_GOAL;
import static org.sonatype.central.publisher.plugin.Constants.PUBLISH_GOAL_ID;

/**
 * Tests that the lifecycle participant recognizes this fork's coordinates, and that it keeps its hands off a
 * project that binds the publish goal itself.
 */
class DeployLifecycleParticipantTest
{
  private DeployLifecycleParticipant participant;

  @BeforeEach
  void setUp() {
    participant = new DeployLifecycleParticipant();
    participant.enableLogging(new ConsoleLogger());
  }

  @Test
  void bindsThePublishGoalToDeployWhenTheProjectDeclaresTheDeployPlugin() throws Exception {
    Xpp3Dom configuration = new Xpp3Dom("configuration");
    Plugin publishingPlugin = publishingPlugin();
    publishingPlugin.setConfiguration(configuration);
    Plugin deployPlugin = deployPlugin();
    Model model = modelWith(publishingPlugin, deployPlugin);

    participant.afterProjectsRead(sessionWith(model));

    assertTrue(deployPlugin.getExecutions().isEmpty(), "the deploy plugin's executions should be cleared");
    assertEquals(1, publishingPlugin.getExecutions().size());
    PluginExecution injected = publishingPlugin.getExecutions().get(0);
    assertEquals(PUBLISH_GOAL_ID, injected.getId());
    assertEquals(DEPLOY_PHASE, injected.getPhase());
    assertEquals(Collections.singletonList(PUBLISH_GOAL), injected.getGoals());
    assertSame(configuration, injected.getConfiguration(), "the plugin's configuration should be handed on");
  }

  @Test
  void bindsThePublishGoalToDeployWhenTheProjectDeclaresTheNexusStagingPlugin() throws Exception {
    Plugin publishingPlugin = publishingPlugin();
    Plugin nexusStagingPlugin =
        plugin(NEXUS_STAGING_PLUGIN_GROUP_ID, NEXUS_STAGING_PLUGIN_ARTIFACT_ID, execution("default-deploy", "deploy"));
    Model model = modelWith(publishingPlugin, nexusStagingPlugin);

    participant.afterProjectsRead(sessionWith(model));

    assertTrue(nexusStagingPlugin.getExecutions().isEmpty());
    assertEquals(PUBLISH_GOAL_ID, publishingPlugin.getExecutions().get(0).getId());
  }

  @Test
  void leavesTheBuildAloneWhenTheProjectBindsThePublishGoalItself() throws Exception {
    Plugin publishingPlugin = publishingPlugin(execution("central-publish", PUBLISH_GOAL));
    Plugin deployPlugin = deployPlugin();
    Model model = modelWith(publishingPlugin, deployPlugin);

    participant.afterProjectsRead(sessionWith(model));

    assertEquals(1, deployPlugin.getExecutions().size(), "the deploy plugin's executions should be kept");
    assertEquals(1, publishingPlugin.getExecutions().size());
    assertEquals("central-publish", publishingPlugin.getExecutions().get(0).getId());
  }

  @Test
  void leavesTheBuildAloneWhenAnotherModuleBindsThePublishGoal() throws Exception {
    Model bindingModule = modelWith(publishingPlugin(execution("central-publish", PUBLISH_GOAL)));
    Plugin publishingPlugin = publishingPlugin();
    Plugin deployPlugin = deployPlugin();
    Model otherModule = modelWith(publishingPlugin, deployPlugin);

    participant.afterProjectsRead(sessionWith(bindingModule, otherModule));

    assertEquals(1, deployPlugin.getExecutions().size());
    assertTrue(publishingPlugin.getExecutions().isEmpty());
  }

  @Test
  void leavesTheBuildAloneWhenThePluginIsNotDeclared() throws Exception {
    Plugin deployPlugin = deployPlugin();

    participant.afterProjectsRead(sessionWith(modelWith(deployPlugin)));

    assertEquals(1, deployPlugin.getExecutions().size());
  }

  @Test
  void bindsNothingWhenTheProjectDeclaresNeitherDeployNorStagingPlugin() throws Exception {
    Plugin publishingPlugin = publishingPlugin();

    participant.afterProjectsRead(sessionWith(modelWith(publishingPlugin)));

    assertTrue(publishingPlugin.getExecutions().isEmpty());
  }

  @Test
  void bindsThePublishGoalOnlyOnce() throws Exception {
    Plugin publishingPlugin = publishingPlugin();
    Plugin deployPlugin = deployPlugin();
    Plugin nexusStagingPlugin = plugin(NEXUS_STAGING_PLUGIN_GROUP_ID, NEXUS_STAGING_PLUGIN_ARTIFACT_ID);
    Model model = modelWith(publishingPlugin, deployPlugin, nexusStagingPlugin);

    participant.afterProjectsRead(sessionWith(model));

    assertEquals(1, publishingPlugin.getExecutions().size());
  }

  @Test
  void ignoresTheUpstreamCoordinates() throws Exception {
    Plugin upstreamPlugin = plugin("org.sonatype.central", "central-publishing-maven-plugin");
    Plugin deployPlugin = deployPlugin();

    participant.afterProjectsRead(sessionWith(modelWith(upstreamPlugin, deployPlugin)));

    assertEquals(1, deployPlugin.getExecutions().size());
    assertTrue(upstreamPlugin.getExecutions().isEmpty());
  }

  private MavenSession sessionWith(final Model... models) {
    MavenSession session = mock(MavenSession.class);
    when(session.getProjects()).thenReturn(projects(models));
    return session;
  }

  private List<MavenProject> projects(final Model... models) {
    return Arrays.stream(models).map(MavenProject::new).collect(Collectors.toList());
  }

  private Model modelWith(final Plugin... plugins) {
    Build build = new Build();
    for (Plugin plugin : plugins) {
      build.addPlugin(plugin);
    }
    Model model = new Model();
    model.setBuild(build);
    return model;
  }

  private Plugin publishingPlugin(final PluginExecution... executions) {
    return plugin(CENTRAL_PUBLISHING_PLUGIN_GROUP_ID, CENTRAL_PUBLISHING_PLUGIN_ARTIFACT_ID, executions);
  }

  private Plugin deployPlugin() {
    return plugin(MAVEN_DEPLOY_PLUGIN_GROUP_ID, MAVEN_DEPLOY_PLUGIN_ARTIFACT_ID, execution("default-deploy", "deploy"));
  }

  private Plugin plugin(final String groupId, final String artifactId, final PluginExecution... executions) {
    Plugin plugin = new Plugin();
    plugin.setGroupId(groupId);
    plugin.setArtifactId(artifactId);
    for (PluginExecution execution : executions) {
      plugin.addExecution(execution);
    }
    return plugin;
  }

  private PluginExecution execution(final String id, final String goal) {
    PluginExecution execution = new PluginExecution();
    execution.setId(id);
    execution.getGoals().add(goal);
    return execution;
  }
}

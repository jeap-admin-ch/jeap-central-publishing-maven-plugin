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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.sonatype.central.publisher.plugin.published.ComponentPublishedChecker;

import org.apache.maven.plugin.logging.Log;

import static java.lang.String.format;

/**
 * Checks whether the components of a release are available on Maven Central.
 * <p>
 * This is used to keep a build green when a bundle upload had to be repeated after an ambiguous failure (see
 * {@link UploadRetryState}): in that case the portal may hold two deployments for the same components, of which one
 * inevitably fails. If the components are on Central, the release did happen and the failing duplicate must not fail
 * the build.
 */
public class PublishedComponentsVerifier
{
  private static final String POM_FILE_SUFFIX = ".pom";

  private final ComponentPublishedChecker componentPublishedChecker;

  private final RetryConfig config;

  private final Log log;

  public PublishedComponentsVerifier(
      final ComponentPublishedChecker componentPublishedChecker,
      final RetryConfig config,
      final Log log)
  {
    this.componentPublishedChecker = componentPublishedChecker;
    this.config = config;
    this.log = log;
  }

  /**
   * Returns whether all components in the staging directory are published on Maven Central.
   *
   * @param stagingDirectory the staging directory holding the components that went into the bundle
   * @param waitForPublishing whether to poll until the components show up, which only makes sense if the deployment
   *                          publishes automatically. A deployment waiting for manual publishing will never show up
   *                          on its own, so we check once and report the result.
   */
  public boolean allComponentsPublished(final File stagingDirectory, final boolean waitForPublishing) {
    List<Component> components = findStagedComponents(stagingDirectory);
    if (components.isEmpty()) {
      log.warn("Cannot determine which components were part of the deployment, no staged poms found in "
          + stagingDirectory);
      return false;
    }

    long deadline = System.currentTimeMillis()
        + (waitForPublishing ? config.getPublishedGraceSeconds() * 1000L : 0L);
    long pollIntervalMillis = config.getPublishedPollIntervalSeconds() * 1000L;

    Set<Component> pending = new LinkedHashSet<>(components);
    while (true) {
      pending.removeIf(this::isPublished);

      if (pending.isEmpty()) {
        return true;
      }

      long remaining = deadline - System.currentTimeMillis();
      if (remaining <= 0) {
        log.warn(format("%d of %d components of the deployment are not published on Maven Central, e.g. %s",
            pending.size(), components.size(), pending.iterator().next()));
        return false;
      }

      log.info(format("Waiting for %d of %d components to show up on Maven Central (up to %d more seconds)",
          pending.size(), components.size(), remaining / 1000));

      try {
        Thread.sleep(Math.min(pollIntervalMillis, remaining));
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
  }

  private boolean isPublished(final Component component) {
    try {
      return componentPublishedChecker.isComponentPublished(
          component.groupId, component.artifactId, component.version);
    }
    catch (RuntimeException e) {
      log.warn("Could not check whether " + component + " is published on Maven Central: " + e.getMessage());
      return false;
    }
  }

  /**
   * Derives the components from the staging directory, which holds the bundle content in the Maven repository
   * layout {@code group/id/artifactId/version/artifactId-version.pom}.
   */
  List<Component> findStagedComponents(final File stagingDirectory) {
    List<Component> components = new ArrayList<>();
    Path stagingPath = stagingDirectory.toPath();

    if (!Files.isDirectory(stagingPath)) {
      return components;
    }

    try (Stream<Path> files = Files.walk(stagingPath)) {
      files.filter(Files::isRegularFile)
          .filter(file -> file.getFileName().toString().endsWith(POM_FILE_SUFFIX))
          .forEach(file -> toComponent(stagingPath.relativize(file), components));
    }
    catch (IOException e) {
      log.warn("Could not read the staged components from " + stagingDirectory + ": " + e.getMessage());
    }

    return components;
  }

  private void toComponent(final Path relativePomPath, final List<Component> components) {
    int nameCount = relativePomPath.getNameCount();
    // groupId (at least one segment) / artifactId / version / file
    if (nameCount < 4) {
      log.debug("Ignoring unexpected staged file " + relativePomPath);
      return;
    }

    StringBuilder groupId = new StringBuilder();
    for (int i = 0; i < nameCount - 3; i++) {
      if (groupId.length() > 0) {
        groupId.append('.');
      }
      groupId.append(relativePomPath.getName(i));
    }

    Component component = new Component(
        groupId.toString(),
        relativePomPath.getName(nameCount - 3).toString(),
        relativePomPath.getName(nameCount - 2).toString());

    if (!components.contains(component)) {
      components.add(component);
    }
  }

  static class Component
  {
    final String groupId;

    final String artifactId;

    final String version;

    Component(final String groupId, final String artifactId, final String version) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Component)) {
        return false;
      }
      Component other = (Component) o;
      return toString().equals(other.toString());
    }

    @Override
    public int hashCode() {
      return toString().hashCode();
    }

    @Override
    public String toString() {
      return groupId + ":" + artifactId + ":" + version;
    }
  }
}

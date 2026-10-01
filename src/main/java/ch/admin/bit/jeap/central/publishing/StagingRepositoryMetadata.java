/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.apache.maven.project.MavenProject;

/** Removes repository bookkeeping from release staging before checksums and the bundle are generated. */
public final class StagingRepositoryMetadata
{
  private static final Pattern METADATA = Pattern.compile(
      "(?:maven-metadata(?:-[^.]+)?\\.xml|_remote\\.repositories)(?:\\.(?:md5|sha1|sha256|sha512))?");

  private StagingRepositoryMetadata() {
  }

  public static void remove(final MavenProject project, final Path stagingDirectory) {
    for (MavenProject current = project; current != null; current = current.getParent()) {
      Path group = stagingDirectory.resolve(current.getGroupId().replace('.', '/'));
      Path artifact = group.resolve(current.getArtifactId());
      removeFrom(group);
      removeFrom(artifact);
      removeFrom(artifact.resolve(current.getVersion()));
    }
  }

  private static void removeFrom(final Path directory) {
    if (!Files.isDirectory(directory)) {
      return;
    }
    try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
      for (Path file : files) {
        if (Files.isRegularFile(file) && METADATA.matcher(file.getFileName().toString()).matches()) {
          // Parallel reactor modules can clean the same group or parent metadata.
          Files.deleteIfExists(file);
        }
      }
    }
    catch (IOException e) {
      // Publishing an incomplete cleanup would produce a bundle Central cannot validate.
      throw new UncheckedIOException("Cannot remove repository metadata from " + directory, e);
    }
  }
}

/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonatype.central.publisher.plugin.bundler.ArtifactBundlerImpl;
import org.sonatype.central.publisher.plugin.model.BundleArtifactRequest;
import org.sonatype.central.publisher.plugin.model.ChecksumRequest;
import org.sonatype.central.publisher.plugin.utils.ProjectUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

class StagingRepositoryMetadataTest
{
  private AutoCloseable mocks;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws Exception {
    mocks.close();
  }

  @TempDir
  Path temp;

  @Mock
  ProjectUtils projectUtils;

  @InjectMocks
  ArtifactBundlerImpl bundler;

  @Test
  void repositoryMetadataIsRemovedBeforeChecksumsAndDoesNotEnterTheBundle() throws Exception {
    Path staging = temp.resolve("staging");
    MavenProject parent = project("example.parent", "parent", "1.0");
    MavenProject child = project("example.child", "child", "2.0");
    child.setParent(parent);
    Set<String> expected = new TreeSet<>();
    for (MavenProject project : List.of(parent, child)) {
      String group = project.getGroupId().replace('.', '/');
      String artifact = group + "/" + project.getArtifactId();
      String version = artifact + "/" + project.getVersion();
      for (String dir : List.of(group, artifact, version)) {
        for (String metadata : List.of("maven-metadata-central-staging.xml", "maven-metadata-local.xml",
            "maven-metadata.xml", "_remote.repositories")) {
          for (String suffix : List.of("", ".md5", ".sha1", ".sha256", ".sha512")) {
            write(staging, dir + "/" + metadata + suffix);
          }
        }
      }
      for (String extension : List.of(".pom", ".jar", "-sources.jar", "-javadoc.jar", ".xml", ".module")) {
        String file = version + "/" + project.getArtifactId() + "-" + project.getVersion() + extension;
        for (String suffix : List.of("", ".asc", ".md5", ".sha1", ".sha256", ".sha512")) {
          write(staging, file + suffix);
          expected.add(file + suffix);
        }
      }
    }
    doAnswer(invocation -> {
      assertEquals(expected, files(staging), "metadata must be gone before checksums are generated");
      return null;
    }).when(projectUtils).createChecksumFiles(child, staging, ChecksumRequest.ALL);
    bundler.enableLogging(new ConsoleLogger(ConsoleLogger.LEVEL_DISABLED, "test"));

    bundler.preBundle(child, staging, ChecksumRequest.ALL);
    Path bundle = bundler.bundle(new BundleArtifactRequest(child, staging.toFile(), temp.resolve("output").toFile(),
        "bundle.zip", ChecksumRequest.ALL));

    verify(projectUtils).createChecksumFiles(child, staging, ChecksumRequest.ALL);
    try (ZipFile zip = new ZipFile(bundle.toFile())) {
      assertEquals(expected, zip.stream().map(ZipEntry::getName).collect(Collectors.toSet()));
      for (String file : expected) {
        assertEquals(file, new String(zip.getInputStream(zip.getEntry(file)).readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8));
      }
    }
  }

  @Test
  void absentDirectoriesAndRepeatedCleanupAreHarmless() throws Exception {
    MavenProject project = project("example", "artifact", "1.0");
    StagingRepositoryMetadata.remove(project, temp);
    write(temp, "example/artifact/1.0/artifact-1.0.pom");
    write(temp, "example/artifact/maven-metadata-local.xml");
    write(temp, "unrelated/maven-metadata-local.xml");

    StagingRepositoryMetadata.remove(project, temp);
    StagingRepositoryMetadata.remove(project, temp);

    assertEquals(Set.of("example/artifact/1.0/artifact-1.0.pom", "unrelated/maven-metadata-local.xml"), files(temp));
  }

  private static MavenProject project(String group, String artifact, String version) {
    Model model = new Model();
    model.setGroupId(group);
    model.setArtifactId(artifact);
    model.setVersion(version);
    return new MavenProject(model);
  }

  private static void write(Path root, String file) throws Exception {
    Path path = root.resolve(file);
    Files.createDirectories(path.getParent());
    Files.writeString(path, file);
  }

  private static Set<String> files(Path root) throws Exception {
    try (var paths = Files.walk(root)) {
      return paths.filter(Files::isRegularFile).map(root::relativize).map(Path::toString).collect(Collectors.toSet());
    }
  }
}

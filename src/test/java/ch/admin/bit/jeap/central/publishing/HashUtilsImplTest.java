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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.sonatype.central.publisher.plugin.utils.HashAlgorithm;
import org.sonatype.central.publisher.plugin.utils.HashUtilsImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The checksums that go into the bundle are computed with Guava's hashing, and the portal rejects a deployment whose
 * checksums do not match. This test pins them against the JDK's own digests, so that an upgrade of Guava - or of the
 * JDK - cannot change them unnoticed.
 */
class HashUtilsImplTest
{
  private static final String CONTENT = "the quick brown fox jumps over the lazy dog\n";

  private final HashUtilsImpl hashUtils = new HashUtilsImpl();

  private File file;

  @BeforeEach
  void setUp(@TempDir final Path tempDir) throws IOException {
    file = Files.writeString(tempDir.resolve("artifact.jar"), CONTENT, StandardCharsets.UTF_8).toFile();
  }

  @Test
  void md5MatchesTheJdk() throws Exception {
    assertEquals(digest("MD5"), hashUtils.hash(file, HashAlgorithm.MD5));
  }

  @Test
  void sha1MatchesTheJdk() throws Exception {
    assertEquals(digest("SHA-1"), hashUtils.hash(file, HashAlgorithm.SHA1));
  }

  @Test
  void sha256MatchesTheJdk() throws Exception {
    assertEquals(digest("SHA-256"), hashUtils.hash(file, HashAlgorithm.SHA256));
  }

  @Test
  void sha512MatchesTheJdk() throws Exception {
    assertEquals(digest("SHA-512"), hashUtils.hash(file, HashAlgorithm.SHA512));
  }

  private static String digest(final String algorithm) throws NoSuchAlgorithmException {
    MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
    return HexFormat.of().formatHex(messageDigest.digest(CONTENT.getBytes(StandardCharsets.UTF_8)));
  }
}

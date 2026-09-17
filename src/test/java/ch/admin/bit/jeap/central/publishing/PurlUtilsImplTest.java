/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.util.Optional;

import org.sonatype.central.publisher.plugin.utils.PurlUtilsImpl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The portal reports the components of a deployment as package URLs, which the plugin turns into the Maven Central
 * links it prints at the end of a release. Parsing them is delegated to {@code packageurl-java}, so this test also
 * guards an upgrade of that library.
 */
class PurlUtilsImplTest
{
  private final PurlUtilsImpl purlUtils = new PurlUtilsImpl();

  @Test
  void aMavenPurlBecomesALinkIntoMavenCentral() {
    Optional<String> url = purlUtils.toRepo1Url("pkg:maven/ch.admin.bit.jeap/jeap-central-publishing-maven-plugin@1.2.3");

    assertEquals(Optional.of("https://repo1.maven.org/maven2/"
        + "ch/admin/bit/jeap/jeap-central-publishing-maven-plugin/1.2.3/"), url);
  }

  @Test
  void aPurlWithQualifiersStillResolves() {
    Optional<String> url = purlUtils.toRepo1Url("pkg:maven/org.example/lib@1.0.0?type=jar&classifier=sources");

    assertEquals(Optional.of("https://repo1.maven.org/maven2/org/example/lib/1.0.0/"), url);
  }

  @Test
  void aPurlWithoutAVersionYieldsNoLink() {
    assertTrue(purlUtils.toRepo1Url("pkg:maven/org.example/lib").isEmpty(),
        "without a version there is no directory to link to");
  }

  @Test
  void anUnparseablePurlYieldsNoLinkInsteadOfFailingTheRelease() {
    assertTrue(purlUtils.toRepo1Url("not a purl at all").isEmpty());
  }
}

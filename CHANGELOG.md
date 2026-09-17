# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/). The version of this fork is
the upstream release it is based on, plus a fork revision; releases before 0.11.0.1 are not documented here.

## [0.11.0.2] - 2026-09-17

### Dependencies
- **org.sonatype.buildsupport:buildsupport**: 55 → 57 (major)
- **org.apache.maven:maven-plugin-api**: 3.9.2 → 3.9.16 (patch)
- **org.apache.maven.plugin-tools:maven-plugin-annotations**: 3.8.2 → 3.16.0 (minor)
- **org.apache.maven:maven-compat**: 3.9.0 → 3.9.16 (patch)
- **org.codehaus.plexus:plexus-utils**: 3.5.1 → 4.1.0 (major)
- **com.github.package-url:packageurl-java**: 1.4.1 → 1.5.0 (minor)
- **com.google.guava:guava**: 32.1.0-jre → 33.7.1-jre (major)
- **commons-io:commons-io**: 2.15.1 → 2.22.0 (minor)
- **org.apache.commons:commons-lang3**: 3.18.0 → 3.20.0 (minor)
- **ch.admin.bit.jeap:jeap-license-template**: 1.0.2 → 1.0.3 (patch)
- **org.hamcrest:hamcrest**: 2.2 → 3.0 (major)
- **org.mockito:mockito-core**: 4.8.1 → 5.23.0 (major)
- **com.fasterxml.jackson.core:jackson-databind**: 2.16.1 → 2.22.2 (minor)
- **org.apache.httpcomponents.client5:httpclient5**: 5.3.1 → 5.6.4 (minor)
- **org.codehaus.plexus:plexus-utils**: 3.6.2 → 4.1.0 (major)
- **org.mock-server:mockserver-client-java**: 5.15.0 → 8.0.0 (major)
- **org.apache.maven.plugins:maven-invoker-plugin**: 3.5.1 → 3.10.1 (minor)
- **org.codehaus.plexus:plexus-component-metadata**: 2.1.1 → 2.2.0 (minor)
- **org.apache.maven.plugins:maven-plugin-plugin**: 3.8.2 → 3.16.0 (minor)
- **org.codehaus.mojo:build-helper-maven-plugin**: 3.4.0 → 3.6.2 (minor)
- **io.fabric8:docker-maven-plugin**: 0.43.0 → 0.49.0 (minor)
- **org.codehaus.mojo:exec-maven-plugin**: 3.1.0 → 3.6.4 (minor)
- **org.apache.maven.plugins:maven-javadoc-plugin**: 3.5.0 → 3.12.0 (minor)
- **org.apache.maven.plugins:maven-source-plugin**: 3.2.1 → 3.4.0 (minor)
- **org.sonatype.plugins:nexus-staging-maven-plugin**: 1.6.13 → 1.7.0 (minor)

### Fixed

- Compile the plugin's classes for Java 11. They are scanned at runtime by the sisu class scanner of the
  Maven that runs the plugin, which bundles an ASM as old as that Maven: Maven 3.9.9 skipped the Java 25
  class files of 0.11.0.1 without an error, leaving every component of the plugin unbound, and publishing
  failed with `No implementation for MojoUtils was bound`.

## [0.11.0.1] - 2026-09-17

### Added

- Configurable connect and socket timeouts for the requests to the Central Publisher Portal, with retries on
  transient failures, and reconciliation of a failed duplicate deployment against the components published on
  Maven Central.

### Changed

- Rebase the fork on upstream 0.11.0.
- Recognize this fork's own coordinates in the lifecycle participant that binds the `publish` goal to the
  `deploy` phase.
- Declare the plugin's components as JSR-330 beans instead of Plexus components.

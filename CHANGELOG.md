# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/). The version of this fork is
the upstream release it is based on, plus a fork revision; releases before 0.11.0.1 are not documented here.

## [0.11.0.2] - 2026-09-17

### Dependencies

- **ch.admin.bit.jeap:jeap-license-template**: 1.0.2 → 1.0.3 (patch)
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

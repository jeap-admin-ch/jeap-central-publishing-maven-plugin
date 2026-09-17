Maven Central Publishing Plugin
===

This is a fork of Sonatype's central-publishing-maven-plugin with three changes:

* The creation of the HTTP client has been patched to respect HTTP proxy system properties.
* Requests to the Central Publisher Portal use configurable timeouts and are repeated on transient failures, so that
  a release does not fail because of a slow or flaky upload. See [Getting started](docs/getting-started.md#timeouts-and-retries).
* The lifecycle participant that binds the `publish` goal to the `deploy` phase recognizes this fork's coordinates.
  See [Goals and configuration](docs/goals-and-configuration.md#binding-the-goal).

See [Getting started](docs/getting-started.md) and [Goals and configuration](docs/goals-and-configuration.md)
for how to use the plugin, and
https://central.sonatype.org/publish/publish-portal-maven/ for Sonatype's documentation of publishing to
Maven Central.

## Note

This repository is part the open source distribution of jEAP. See [github.com/jeap-admin-ch/jeap](https://github.com/jeap-admin-ch/jeap)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).

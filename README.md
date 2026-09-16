Maven Central Publishing Plugin
===

This is a fork of Sonatype's central-publishing-maven-plugin with two changes:

* The creation of the HTTP client has been patched to respect HTTP proxy system properties.
* Requests to the Central Publisher Portal use configurable timeouts and are repeated on transient failures, so that
  a release does not fail because of a slow or flaky upload. See [Getting started](docs/getting-started.md#timeouts-and-retries).

See https://central.sonatype.org/publish/publish-portal-maven/ for more information on how to use the plugin.

## Note

This repository is part the open source distribution of jEAP. See [github.com/jeap-admin-ch/jeap](https://github.com/jeap-admin-ch/jeap)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).

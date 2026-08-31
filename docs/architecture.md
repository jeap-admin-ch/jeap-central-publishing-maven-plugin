# Architecture

`jeap-central-publishing-maven-plugin` is a fork of Sonatype's
[central-publishing-maven-plugin](https://github.com/sonatype/central-publishing-maven-plugin), used
to publish jEAP's open-source Maven artifacts to Maven Central via the
[Central Publisher Portal](https://central.sonatype.org/publish/publish-portal-maven/).

## Why a fork

Upstream builds its HTTP client with `HttpClients.createDefault()`, which does not pick up the JVM's
`http.proxyHost`/`https.proxyHost` system properties. jEAP's build infrastructure runs behind an HTTP
proxy, so uploads to the Central Publisher Portal would otherwise fail. The single functional change in
this fork is in `PublisherHttpClient`: both the `GET` and `POST` code paths use
`HttpClients.createSystem()` instead, which honors the standard JVM proxy system properties.

No other behavior is changed; the plugin's goals, parameters, and usage are otherwise identical to
upstream. See the
[upstream documentation](https://central.sonatype.org/publish/publish-portal-maven/) for how to
configure and use the plugin (`publish` goal, staging/auto-publish, credentials, etc.).

## Module layout

```text
src/main/java/org/sonatype/central/publisher/   # forked upstream plugin code (mojo, client, auth)
  client/httpclient/PublisherHttpClient.java     # the patched HTTP client (see above)
```

## Related

- [Getting started](getting-started.md)
- [Upstream: Publishing to Central via the Portal](https://central.sonatype.org/publish/publish-portal-maven/)
- [Upstream project: central-publishing-maven-plugin](https://github.com/sonatype/central-publishing-maven-plugin)

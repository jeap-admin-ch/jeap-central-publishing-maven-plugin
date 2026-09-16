# Architecture

`jeap-central-publishing-maven-plugin` is a fork of Sonatype's
[central-publishing-maven-plugin](https://github.com/sonatype/central-publishing-maven-plugin), used
to publish jEAP's open-source Maven artifacts to Maven Central via the
[Central Publisher Portal](https://central.sonatype.org/publish/publish-portal-maven/).

## Why a fork

The fork carries two functional changes against upstream. Everything else is upstream code, kept as close to the
original as possible to ease future rebases.

### 1. HTTP proxy support

Upstream builds its HTTP client with `HttpClients.createDefault()`, which does not pick up the JVM's
`http.proxyHost`/`https.proxyHost` system properties. jEAP's build infrastructure runs behind an HTTP
proxy, so uploads to the Central Publisher Portal would otherwise fail. The fork builds its client with
`HttpClients.custom().useSystemProperties()` (and the matching `useSystemProperties()` on the connection manager
builder), which honors the standard JVM proxy system properties.

### 2. Timeouts and retries

Upstream sends every request to the portal exactly once, using httpclient's default socket timeout of three minutes.
Uploading a release bundle regularly takes longer than that, which made jEAP releases fail with
`Invalid request. Read timed out` even though the portal had already received the bundle. A single transient failure
while polling the deployment status had the same effect.

The fork therefore configures generous connect and socket timeouts and repeats failed requests with an exponential
backoff. Both are configured through JVM system properties (see [Getting started](getting-started.md)), so that no
`pom.xml` of a publishing project has to change; the set of Maven plugin parameters is identical to upstream.

A bundle upload that fails with a read timeout may or may not have been received by the portal. Repeating it can
therefore leave a duplicate deployment behind, of which one inevitably fails. To keep a release green when the
artifacts did make it to Central, the publish mojo reconciles such a failure against the components published on
Maven Central before failing the build.

## Module layout

```text
src/main/java/org/sonatype/central/publisher/   # forked upstream plugin code (mojo, client, auth)
  client/httpclient/PublisherHttpClient.java     # patched: delegates to the jEAP executor below
  plugin/PublishMojo.java                        # patched: reconciles a failed duplicate deployment

src/main/java/ch/admin/bit/jeap/central/publishing/   # jEAP additions, no counterpart upstream
  RetryConfig.java                               # system properties for timeouts and retries
  RetryClassifier.java                           # which failures may be repeated, and which are ambiguous
  RetryingHttpRequestExecutor.java               # HTTP client creation, timeouts, retry loop
  UploadRetryState.java                          # records an upload that was repeated after an ambiguous failure
  PublishedComponentsVerifier.java               # checks whether the released components are on Maven Central
  PublishingLog.java                             # logging for the client layer, which has no Plexus logger
```

## Related

- [Getting started](getting-started.md)
- [Upstream: Publishing to Central via the Portal](https://central.sonatype.org/publish/publish-portal-maven/)
- [Upstream project: central-publishing-maven-plugin](https://github.com/sonatype/central-publishing-maven-plugin)

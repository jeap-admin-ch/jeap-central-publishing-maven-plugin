# Getting started

## Using the plugin

Declare the plugin in a project's `pom.xml` the same way you would the upstream
`central-publishing-maven-plugin`:

```xml
<plugin>
    <groupId>ch.admin.bit.jeap</groupId>
    <artifactId>jeap-central-publishing-maven-plugin</artifactId>
    <version>${jeap-central-publishing-maven-plugin.version}</version>
    <extensions>true</extensions>
    <configuration>
        <publishingServerId>central</publishingServerId>
        <autoPublish>true</autoPublish>
    </configuration>
</plugin>
```

Configure the `central` server's credentials (a Central Portal user token) in your Maven
`settings.xml`, then run:

```bash
mvn deploy
```

For the full set of configuration options (`autoPublish`, `waitUntil`, `deploymentName`,
`centralBaseUrl`, checksum/signature handling, etc.) see the
[upstream plugin documentation](https://central.sonatype.org/publish/publish-portal-maven/) — this
fork does not add, remove, or rename any parameter.

## Proxy support

Behind an HTTP/HTTPS proxy, no extra plugin configuration is needed: set the standard JVM proxy system
properties (`-Dhttps.proxyHost=... -Dhttps.proxyPort=...`, and their `http.*` counterparts as needed),
for example via `MAVEN_OPTS`. Unlike the upstream plugin, this fork's HTTP client honors those
properties automatically (see [Architecture](architecture.md)).

## Timeouts and retries

Unlike the upstream plugin, this fork applies timeouts to the requests it sends to the Central Publisher Portal and
repeats requests that failed transiently, so that a release does not fail because of a slow upload or a short network
problem. The behaviour is configured with JVM system properties, for example via `MAVEN_OPTS`:

```bash
MAVEN_OPTS="-Djeap.centralPublishing.socketTimeoutSeconds=1200 -Djeap.centralPublishing.maxRetries=5"
```

| System property | Default | Description |
|---|---|---|
| `jeap.centralPublishing.connectTimeoutSeconds` | 60 | Timeout for establishing the connection to the portal or the proxy. |
| `jeap.centralPublishing.socketTimeoutSeconds` | 900 | Timeout for reading the response. The upstream plugin uses httpclient's default of 180 seconds, which is regularly exceeded when uploading a bundle. |
| `jeap.centralPublishing.maxRetries` | 4 | How many times a failed request is repeated. Set to `0` to disable retries. |
| `jeap.centralPublishing.retryInitialDelaySeconds` | 10 | Wait time before the first retry. It doubles with every further attempt. |
| `jeap.centralPublishing.retryMaxDelaySeconds` | 120 | Upper bound for that wait time. |
| `jeap.centralPublishing.retryOnAmbiguousFailure` | true | Whether to repeat a bundle upload that failed in a way that leaves it open whether the portal received the bundle, such as a read timeout. See below. |
| `jeap.centralPublishing.publishedGraceSeconds` | 600 | How long a failed deployment is reconciled against Maven Central before failing the build. See below. |
| `jeap.centralPublishing.publishedPollIntervalSeconds` | 15 | Poll interval used while doing so. |

### Duplicate deployments

When an upload fails with a read timeout, the portal may well have received the bundle already. Uploading it again -
which is what makes the release succeed in the vast majority of cases - can therefore create a second deployment for
the same components, of which one inevitably fails.

To avoid failing a release that actually succeeded, the plugin checks in that situation whether the components are
published on Maven Central, waiting up to `jeap.centralPublishing.publishedGraceSeconds` for them to show up, and only
fails the build if they are not. Waiting only happens with `autoPublish` set to `true`; a deployment that waits for
manual publishing will never show up on its own and is reported immediately.

The build log names the deployment in both cases. After such a build, check
`https://central.sonatype.com/publishing/deployments` and drop the leftover deployment.

Set `jeap.centralPublishing.retryOnAmbiguousFailure=false` to never repeat an upload that might have been received;
the build then fails on a read timeout, as it does with the upstream plugin.

## Related

- [Architecture](architecture.md)
- [Publishing to Central via the Portal (upstream docs)](https://central.sonatype.org/publish/publish-portal-maven/)
- [jeap-central-publishing-maven-plugin README](../README.md)

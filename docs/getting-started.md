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

## Related

- [Architecture](architecture.md)
- [Publishing to Central via the Portal (upstream docs)](https://central.sonatype.org/publish/publish-portal-maven/)
- [jeap-central-publishing-maven-plugin README](../README.md)

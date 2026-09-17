# Goals and configuration

The plugin publishes a project's artifacts to Maven Central through the
[Central Publisher Portal](https://central.sonatype.org/publish/publish-portal-maven/): it stages the artifacts
of the reactor into a local repository layout, zips them into one deployment bundle, uploads that bundle and
then watches what the portal does with it.

The plugin requires a build running on Java 25 or newer, on Maven 3.

## Goals

| Goal | Bound to | Description |
|---|---|---|
| `central-publishing:publish` | `deploy` | Stages and bundles the artifacts of the project, uploads the bundle to the Central Publisher Portal and waits for the deployment to reach the requested state. Requires network access. |
| `central-publishing:help` | - | Prints the goals and parameters of the plugin. `mvn central-publishing:help -Ddetail=true` lists every parameter. |

A release (a version without `-SNAPSHOT`) is staged, bundled and uploaded as one deployment for the whole
reactor, in the last module that runs the goal. A snapshot is not bundled at all: it is deferred and deployed
to `centralSnapshotsUrl` with the usual Maven deploy mechanism at the end of the reactor.

## Binding the goal

Bind the `publish` goal to the `deploy` phase, and keep the default deploy of `maven-deploy-plugin` from
running before it:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>ch.admin.bit.jeap</groupId>
      <artifactId>jeap-central-publishing-maven-plugin</artifactId>
      <version>${jeap-central-publishing-maven-plugin.version}</version>
      <extensions>true</extensions>
      <executions>
        <execution>
          <id>central-publish</id>
          <phase>deploy</phase>
          <goals>
            <goal>publish</goal>
          </goals>
        </execution>
      </executions>
      <configuration>
        <publishingServerId>central</publishingServerId>
        <autoPublish>true</autoPublish>
      </configuration>
    </plugin>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-deploy-plugin</artifactId>
      <executions>
        <!-- Would otherwise run before the publish goal. -->
        <execution>
          <id>default-deploy</id>
          <phase>none</phase>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Then publish with `mvn deploy`.

In a multi-module build the binding belongs in the parent, so that it is inherited by every module: the goal
itself figures out in which module to stage, bundle and upload (see above).

### Automatic binding

With `<extensions>true</extensions>` the plugin registers a lifecycle participant that can add that binding
by itself. It does so only for a project that

- declares this plugin in `<build><plugins>` - a declaration in `<pluginManagement>` neither loads the
  participant nor counts, and
- declares `maven-deploy-plugin` or `nexus-staging-maven-plugin`, whose executions it clears in exchange, and
- binds the `publish` goal in no module of the reactor.

For such a project it adds an execution `injected-central-publishing` of the `publish` goal in the `deploy`
phase, carrying the plugin's `<configuration>`, and the build publishes to the Central Publisher Portal
instead of deploying with `maven-deploy-plugin`. As soon as one module binds the goal explicitly, the
participant leaves the whole reactor alone, so an explicit binding always wins over the automatism and is
the clearer thing to write.

## Credentials

The plugin authenticates with a Central Portal user token, taken from the `server` in `settings.xml` whose id
matches `publishingServerId`. Encrypted passwords are decrypted the usual Maven way.

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>a-user-token-name</username>
      <password>a-user-token-secret</password>
    </server>
  </servers>
</settings>
```

## Configuration of the publish goal

Every parameter can also be set on the command line through the property of the same name, for example
`mvn deploy -DskipPublishing=true`.

```xml
<configuration>
  <!-- which server in settings.xml holds the Central Portal user token -->
  <publishingServerId>central</publishingServerId>

  <!-- publish a valid deployment without manual intervention in the portal -->
  <autoPublish>true</autoPublish>

  <!-- how far to follow the deployment before the build continues: uploaded, validated or published -->
  <waitUntil>validated</waitUntil>
  <waitMaxTime>1800</waitMaxTime>
  <waitPollingInterval>5</waitPollingInterval>

  <!-- name the deployment gets in the portal -->
  <deploymentName>${project.artifactId} ${project.version}</deploymentName>

  <!-- which checksums to generate next to the artifacts: all, required or none -->
  <checksums>all</checksums>

  <!-- artifactIds that must not end up in the bundle -->
  <excludeArtifacts>
    <excludeArtifact>an-internal-module</excludeArtifact>
  </excludeArtifacts>

  <!-- leave components that are already on Maven Central out of the bundle instead of failing on them -->
  <ignorePublishedComponents>false</ignorePublishedComponents>

  <!-- skip publishing altogether: nothing is staged, bundled or uploaded -->
  <skipPublishing>false</skipPublishing>

  <!-- endpoints, normally left alone -->
  <centralBaseUrl>https://central.sonatype.com</centralBaseUrl>
  <centralSnapshotsUrl>https://central.sonatype.com/repository/maven-snapshots/</centralSnapshotsUrl>

  <!-- working directories and bundle name, normally left alone -->
  <stagingDirectory>${project.build.directory}/central-staging</stagingDirectory>
  <outputDirectory>${project.build.directory}/central-publishing</outputDirectory>
  <deferredDirectory>${project.build.directory}/central-deferred</deferredDirectory>
  <outputFilename>central-bundle.zip</outputFilename>
</configuration>
```

### Parameter reference

| Parameter | Property | Default | Meaning |
|---|---|---|---|
| `publishingServerId` | `publishingServerId` | `central` | Id of the `server` in `settings.xml` holding the Central Portal user token. |
| `autoPublish` | `autoPublish` | `false` | `true` publishes a valid deployment automatically, `false` leaves it for manual publishing in the portal. |
| `waitUntil` | `waitUntil` | `VALIDATED` | How far to follow the deployment: `uploaded`, `validated` or `published`. `published` requires `autoPublish`, otherwise it falls back to `validated`. |
| `waitMaxTime` | `waitMaxTime` | `1800` | Seconds to wait for that state before the build fails. Values below the default are raised to it. |
| `waitPollingInterval` | `waitPollingInterval` | `5` | Seconds between two status requests. Values below the default are raised to it. |
| `deploymentName` | `deploymentName` | `Deployment` | Name the deployment gets in the portal. |
| `checksums` | `checksums` | `ALL` | `all` (MD5, SHA1, SHA256, SHA512), `required` (MD5, SHA1) or `none`. |
| `excludeArtifacts` | `excludeArtifacts` | - | ArtifactIds that are left out of the bundle. |
| `ignorePublishedComponents` | `ignorePublishedComponents` | `false` | Leaves components that are already published on Maven Central out of the bundle, instead of letting the deployment fail on them. Useful when only some modules of a multi-module build get a new version. |
| `skipPublishing` | `skipPublishing` | `false` | Leaves every artifact out of the staging, so that nothing is staged, bundled or uploaded. |
| `failOnBuildFailure` | `failOnBuildFailure` | `true` | Whether an earlier failure in the reactor stops the publishing. |
| `centralBaseUrl` | `centralBaseUrl` | `https://central.sonatype.com` | Base URL of the Central Publisher Portal. |
| `centralSnapshotsUrl` | `centralSnapshotsUrl` | see note | Repository snapshots are deployed to. Unset, the repository from `distributionManagement` is used, and if there is none, `https://central.sonatype.com/repository/maven-snapshots/`. |
| `stagingDirectory` | `stagingDirectory` | `target/central-staging` | Where releases are staged before bundling. |
| `deferredDirectory` | `deferredDirectory` | `target/central-deferred` | Where snapshots are deferred until the end of the reactor. |
| `outputDirectory` | `outputDirectory` | `target/central-publishing` | Where the bundle is written. |
| `outputFilename` | `outputFilename` | `central-bundle.zip` | Name of the bundle. |
| `publishCompletionPollInterval` | `publishCompletionPollInterval` | `1000` | Deprecated, milliseconds; use `waitPollingInterval`. |
| `waitForPublishCompletion` | `waitForPublishCompletion` | `false` | Deprecated; use `autoPublish` together with `waitUntil`. |

The working directories are cleaned in the first module of the reactor that runs the goal, so do not point
them at a directory holding anything else.

## Behaviour added by this fork

Timeouts, retries and the handling of a duplicate deployment are configured with system properties rather than
with plugin parameters, so that they can be set centrally in the build infrastructure - see
[Getting started](getting-started.md#timeouts-and-retries). Everything else is identical to upstream.

## Related

- [Getting started](getting-started.md)
- [Architecture](architecture.md)
- [Upstream: Publishing to Central via the Portal](https://central.sonatype.org/publish/publish-portal-maven/)

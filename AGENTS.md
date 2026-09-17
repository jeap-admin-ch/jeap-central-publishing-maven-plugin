# AGENTS.md

Guidance for AI coding agents working **in this repository**. For how to use the plugin, read
[README.md](README.md) and the [docs/](docs/) folder instead.

## Project

`jeap-central-publishing-maven-plugin` is a fork of Sonatype's
[central-publishing-maven-plugin](https://github.com/sonatype/central-publishing-maven-plugin), used to
publish jEAP's open-source Maven artifacts to Maven Central. **This fork has exactly three intentional
behavioral changes versus upstream**:

1. Its HTTP client is built with `useSystemProperties()`, so uploads respect the JVM's
   `http.proxy*`/`https.proxy*` system properties (required by jEAP's build infrastructure, which runs
   behind a proxy).
2. Requests to the Central Publisher Portal use configurable connect/socket timeouts and are repeated on
   transient failures, and a failed deployment caused by a repeated upload is reconciled against the
   components published on Maven Central instead of failing the build.
3. `Constants` holds this fork's plugin coordinates, so that `DeployLifecycleParticipant` recognizes the
   plugin in the projects it inspects.

The first two live in `ch.admin.bit.jeap.central.publishing`, which has no counterpart upstream; the upstream
classes only call into it.

On top of those, the fork's components are **JSR-330 beans instead of Plexus components** — a structural
deviation that touches upstream files all over the tree, taken deliberately so the build no longer depends on
the deprecated, archived `plexus-component-metadata`. See [docs/architecture.md](docs/architecture.md).

## Repository layout

```
src/main/java/org/sonatype/central/publisher/   # forked upstream plugin source, package unchanged from upstream
  client/httpclient/PublisherHttpClient.java     # patched: delegates to RetryingHttpRequestExecutor
  client/PublisherClientProvider.java            # added: JSR-330 binding, replaces upstream's PlexusContextConfig
  plugin/PublishMojo.java                        # patched: reconciles a failed duplicate deployment
  plugin/Constants.java                          # patched: the plugin coordinates of this fork
src/main/java/ch/admin/bit/jeap/central/publishing/  # jEAP additions, no counterpart upstream
src/test/java/ch/admin/bit/jeap/central/publishing/  # tests for those additions (JUnit 5, Mockito)
```

The three patched files named above are the ONLY upstream files with an intentional *functional* change.
Apart from the JSR-330 annotations (see below), everything else under `org.sonatype.central.publisher` is
upstream code kept as close to the original as possible, to ease future rebases against new upstream
releases. New behavior belongs in `ch.admin.bit.jeap.central.publishing`, called from the smallest possible
hook in the upstream code.

## Build

```bash
./mvnw verify
```

- Parent: `org.sonatype.buildsupport:buildsupport`.
- Builds with JDK 25 (`maven.compiler.release`) on the latest Maven 3.9.x. Dependencies therefore only have
  to be Java 25 compatible, but they still have to work inside Maven 3: `plexus-utils` has to stay on 3.x,
  for example, because 4.x drops classes Maven 3 uses, and the Maven 4 lines of the `maven-*` artifacts and
  of the plugin testing harness are off limits for the same reason.
- `./mvnw` downloads Maven from the BIT-internal repository, so it only works inside the BIT network.
- The `run-its`/`run-publish-its` profiles cannot be used in this fork: the `src/it` projects and the
  mockserver configuration class they reference were not taken over from upstream. What they used to
  cover is covered by `PublishMojoIntegrationTest`, which runs the `publish` goal against a local
  stand-in for the Central Publisher Portal (`StubPortal`) and needs neither Docker nor the network.

## Tests

- Tests are JUnit 5. Mojos are tested with the JUnit 5 extension of the maven-plugin-testing-harness
  (`@MojoTest`, `@InjectMojo`), **not** with the deprecated JUnit 3/4 `AbstractMojoTestCase` or `MojoRule`.
- The harness hands the test a *mocked* `MavenSession`, and it sets that session up again while injecting
  the mojo. Stub the session (projects, settings, result) from inside the test method rather than from
  `@BeforeEach`, otherwise the stubbing is overwritten. Properties the test pom refers to via `${...}`
  do have to be set on `session.getUserProperties()` in `@BeforeEach`, before the mojo is configured.
- Do not rely on `${...}` inside `File` parameters of the test pom; the harness leaves those unresolved.
  `PublishMojoIntegrationTest` therefore steers the plugin's working directories through the build
  directory of the project it puts into the session.
- The suite is meant to be good enough to **auto-merge dependency updates**, so it is organized around what
  an upgrade can break, not around classes:
  - `ComponentWiringTest` checks the component index against the plugin descriptor. The harness brings its
    own container, so no other test would notice a component that lost its `@Named`, or an upgrade that
    stopped writing `META-INF/sisu/javax.inject.Named`.
  - `PublishMojoIntegrationTest` drives the `publish` goal against `StubPortal` over real sockets, which is
    what covers `httpclient5` and `jackson`. Variants of the mojo configuration live next to it as
    `src/test/resources/unit/publish-project-*/pom.xml`.
  - `ArtifactDeferrerImplTest` covers the snapshot path and the legacy `maven-*` artifact API it drives.
  - `HashUtilsImplTest` pins the bundle checksums against the JDK's digests, `PurlUtilsImplTest` the purl
    parsing - the two places where a Guava or `packageurl-java` upgrade would silently change output.
- What no test covers is the plugin running inside a **real** Maven: only an invoker IT would, and `src/it`
  was not taken over from upstream. `waitMaxTime` is likewise untested, as the plugin raises anything below
  its 1800 s default.

## Conventions (load-bearing)

- **Minimize the diff against upstream — but not at the price of dead dependencies.** The default is still
  to touch as little upstream code as possible, so that rebases stay cheap: when pulling in a new upstream
  release, re-apply the patches and do not reformat or refactor unrelated upstream code. The exception the
  fork has already taken is dependency health: upstream's Plexus DI was migrated to JSR-330 because
  `plexus-component-metadata` is deprecated and its project archived, which left it unable to read Java 25
  class files and unable to receive security fixes. A rebase therefore has to re-apply the annotation
  migration described in [docs/architecture.md](docs/architecture.md) — mechanical, but tree-wide.
- **Do not reintroduce Plexus DI.** New components are `@Named`/`@Inject` (`javax.inject`, not
  `jakarta.inject`, because the plugin has to keep running on Maven 3) and are indexed by
  `sisu-maven-plugin` into `META-INF/sisu/javax.inject.Named`. `@Mojo`/`@Parameter` stay as they are; they
  feed the plugin descriptor, which is a different mechanism.
- Any new intentional deviation from upstream must be clearly commented in the source (as the existing
  patches are: `// Patched compared to upstream repo`) and mentioned in
  [docs/architecture.md](docs/architecture.md), so future maintainers rebasing against upstream know
  exactly what to preserve.
- Do not rename the Maven coordinates' package (`org.sonatype.central.publisher`) — downstream
  `pom.xml` plugin declarations and Central Portal tooling assume it matches upstream.

## Docs

When the fork's patch set changes (e.g. a new proxy-related fix, or picking up a new upstream feature),
update [docs/architecture.md](docs/architecture.md) to describe the current diff against upstream.

## Versioning

- Semantic-ish versioning tracking the upstream release the fork is based on: the fork's version is the
  upstream version plus a fork revision, currently `0.11.0.1` on top of upstream `0.11.0` (see
  `<version>` in `pom.xml`); there is no `CHANGELOG.md` or `publiccode.yml` in this repository.
- On a feature branch keep the `-SNAPSHOT` suffix if used; otherwise follow the version already set in
  `pom.xml`.
- Use the JIRA ID from the branch name as the commit-message prefix (e.g. `JEAP-1234 Add ...`); do not
  use conventional commits.

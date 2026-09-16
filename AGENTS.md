# AGENTS.md

Guidance for AI coding agents working **in this repository**. For how to use the plugin, read
[README.md](README.md) and the [docs/](docs/) folder instead.

## Project

`jeap-central-publishing-maven-plugin` is a fork of Sonatype's
[central-publishing-maven-plugin](https://github.com/sonatype/central-publishing-maven-plugin), used to
publish jEAP's open-source Maven artifacts to Maven Central. **This fork has exactly two intentional
behavioral changes versus upstream**:

1. Its HTTP client is built with `useSystemProperties()`, so uploads respect the JVM's
   `http.proxy*`/`https.proxy*` system properties (required by jEAP's build infrastructure, which runs
   behind a proxy).
2. Requests to the Central Publisher Portal use configurable connect/socket timeouts and are repeated on
   transient failures, and a failed deployment caused by a repeated upload is reconciled against the
   components published on Maven Central instead of failing the build.

Both live in `ch.admin.bit.jeap.central.publishing`, which has no counterpart upstream; the upstream
classes only call into it. See [docs/architecture.md](docs/architecture.md).

## Repository layout

```
src/main/java/org/sonatype/central/publisher/   # forked upstream plugin source, package unchanged from upstream
  client/httpclient/PublisherHttpClient.java     # patched: delegates to RetryingHttpRequestExecutor
  plugin/PublishMojo.java                        # patched: reconciles a failed duplicate deployment
src/main/java/ch/admin/bit/jeap/central/publishing/  # jEAP additions, no counterpart upstream
src/test/java/ch/admin/bit/jeap/central/publishing/  # tests for those additions (JUnit 4, Mockito)
```

The two files named above are the ONLY upstream files with an intentional functional change; everything
else under `org.sonatype.central.publisher` is upstream code kept as close to the original as possible,
to ease future rebases against new upstream releases. New behavior belongs in
`ch.admin.bit.jeap.central.publishing`, called from the smallest possible hook in the upstream code.

## Build

```bash
./mvnw verify
```

- Parent: `org.sonatype.buildsupport:buildsupport`.
- `./mvnw` downloads Maven from the BIT-internal repository, so it only works inside the BIT network.
- The `run-its`/`run-publish-its` profiles cannot be used in this fork: the `src/it` projects and the
  mockserver configuration class they reference were not taken over from upstream.

## Conventions (load-bearing)

- **Minimize the diff against upstream.** This is a fork maintained for two patches (HTTP-client proxy
  support, and timeouts/retries). When pulling in a new upstream release, re-apply only those patches —
  do not reformat/refactor unrelated upstream code, as that makes future rebases much harder.
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

- Semantic-ish versioning tracking the upstream release the fork is based on (see `<version>` in
  `pom.xml`); there is no `CHANGELOG.md` or `publiccode.yml` in this repository.
- On a feature branch keep the `-SNAPSHOT` suffix if used; otherwise follow the version already set in
  `pom.xml`.
- Use the JIRA ID from the branch name as the commit-message prefix (e.g. `JEAP-1234 Add ...`); do not
  use conventional commits.

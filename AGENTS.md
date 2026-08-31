# AGENTS.md

Guidance for AI coding agents working **in this repository**. For how to use the plugin, read
[README.md](README.md) and the [docs/](docs/) folder instead.

## Project

`jeap-central-publishing-maven-plugin` is a fork of Sonatype's
[central-publishing-maven-plugin](https://github.com/sonatype/central-publishing-maven-plugin), used to
publish jEAP's open-source Maven artifacts to Maven Central. **This fork has exactly one intentional
behavioral change versus upstream**: `PublisherHttpClient` builds its HTTP client with
`HttpClients.createSystem()` instead of `HttpClients.createDefault()`, so uploads respect the JVM's
`http.proxy*`/`https.proxy*` system properties (required by jEAP's build infrastructure, which runs
behind a proxy).

## Repository layout

```
src/main/java/org/sonatype/central/publisher/   # forked upstream plugin source, package unchanged from upstream
  client/httpclient/PublisherHttpClient.java     # the ONLY file with an intentional functional change
```

Everything else under `org.sonatype.central.publisher` is upstream code kept as close to the original
as possible, to ease future rebases against new upstream releases.

## Build

```bash
./mvnw verify
```

- Parent: `org.sonatype.buildsupport:buildsupport`.

## Conventions (load-bearing)

- **Minimize the diff against upstream.** This is a fork maintained for exactly one patch (HTTP-client
  proxy support). When pulling in a new upstream release, re-apply only that patch — do not
  reformat/refactor unrelated upstream code, as that makes future rebases much harder.
- Any new intentional deviation from upstream must be clearly commented in the source (as the existing
  proxy patch is: `// createSystem(): Patched compared to upstream repo`) and mentioned in
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

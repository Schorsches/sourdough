# Continuous integration and the container image

Two workflows, one gate between them.

| Workflow | Trigger | What it does |
|---|---|---|
| `.github/workflows/ci.yaml` | every pull request, and `workflow_call` | runs the test suite and builds the jar |
| `.github/workflows/docker-publish.yaml` | pushes to `main`, and `v*.*.*` tags | runs `ci.yaml` first, then builds the container image and — on the upstream repository only — pushes it to GHCR |

## The test gate

`docker-publish` does not have its own test step. It calls `ci.yaml` as a reusable
workflow and declares `needs: test`, so no image is ever published from a commit whose
suite is red.

This is deliberate. Before the gate existed the two workflows were independent, and an
image kept being published from commits whose build was broken — the failure sat in the
offline Maven build for several pushes without blocking anything. Making the publish
depend on the tests is what makes a red build visible.

`ci.yaml` also has no `push:` trigger of its own. Pushes to `main` are covered through
the reusable call, which means the suite runs exactly once per commit rather than twice.

## Why forks build the image but do not push it

`IMAGE_NAME` names the upstream repository. A fork's `GITHUB_TOKEN` carries
`packages: write` for the fork's own namespace only, so a fork that tried to publish
would build both platforms, take several minutes doing it, and then fail at the last
step with:

```
denied: permission_denied: The requested installation does not exist.
```

So the login and the push are skipped on forks:

```yaml
- name: Login to Registry ${{ env.REGISTRY }}
  if: ${{ !github.event.repository.fork }}

- name: Build and push Docker image
  with:
    push: ${{ !github.event.repository.fork }}
```

The image is still **built** on a fork. That is the part with review value — it is what
checks that the Dockerfile still works — and it costs nothing that the push was going to
fail after anyway.

### Changing this

To publish from a fork, pick one:

- **Publish to the fork's own namespace.** Set
  `IMAGE_NAME: ${{ github.repository }}-builder` and remove the two
  `if: ${{ !github.event.repository.fork }}` guards. GHCR requires a lowercase image
  name, so if the fork's owner or repository name has uppercase letters, lowercase it
  first (`tr '[:upper:]' '[:lower:]'` into a step output, or the `ASCII downcase`
  expression `${{ github.repository }}` already gives you if the names are lowercase).
- **Keep the guards and allowlist explicitly.** Replace the fork check with a repository
  check — `if: ${{ github.repository == 'owner/name' }}` — listing each repository that
  is meant to publish. This is the safer option when several forks exist and only some
  should push.

Either way, change both places: a login without a push wastes a step, and a push without
a login fails.

## Why the image build skips tests

`Dockerfile` runs:

```dockerfile
RUN mvn dependency:go-offline -B
...
RUN mvn package -o -DskipTests
```

Surefire resolves its JUnit provider (`surefire-junit-platform`) lazily, at execution
time rather than as a declared dependency, so `dependency:go-offline` cannot know to
pre-fetch it. Running the tests inside the offline image build therefore fails with the
provider missing from the local repository, even though every project dependency is
present.

The tests still **compile** in the image build, so a compile error in test code is caught
there. They **run** in `ci.yaml`, on the same commit, before the image is allowed to
publish. Nothing is unverified; the verification just happens in the workflow that has
network access.

## Running the suites locally

```bash
mvn test                     # unit, layer and schema-conformance suites
mvn test -DexcludedTestGroups=   # adds the integration and differential suites
```

The integration and differential suites are excluded by default because they need input
data CI does not carry (`data/sources/*.osm.pbf` and the water polygons archive) and take
tens of seconds per schema. They skip rather than fail when the data is absent, so a
checkout without it is not reported as broken. See USAGE.md.

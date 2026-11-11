# Contributing

## Workflow

1. **Spec first.** For any non-trivial change, add `specs/NNN-name.md`
   (see [specs/README.md](specs/README.md)). Raise design questions in the spec's
   *Open questions* section **before** building.
2. **Branch from `main`:**

   | Prefix | Use |
   |---|---|
   | `feature/<spec-no>-<name>` | New behaviour, e.g. `feature/003-distributed-tracing` |
   | `fix/<name>` | Bug fixes |
   | `test/<name>`, `docs/<name>`, `chore/<name>` | Tests, docs, tooling |
   | `release/x.y` | Release branches (cut from `main`) |

3. **Commits** follow [Conventional Commits](https://www.conventionalcommits.org/):
   `feat(gateway): derive X-User-ID from verified JWT`. Reference the spec in the body.
4. **Tests ship with the code** they cover: unit / web-slice tests (`*Test`) and,
   for anything touching a database or Kafka, a Testcontainers integration test (`*IT`).
5. **Open a PR**. CI must be green, and at least one review is required. Squash-merge into `main`.

## Release and backport conventions

- Fixes land on `main` first, then are backported to supported `release/x.y` branches:
  ```bash
  git switch release/1.2
  git switch -c backport/1.2/fix-jwt-identity
  git cherry-pick -x <commit-sha-on-main>   # -x records the original sha
  ```
  Open the backport PR against `release/1.2` with title prefix `[1.2]`.
- Never commit directly to `release/*` without the change existing on `main`, unless
  the code path no longer exists on `main` (explain why in the PR).

## Running tests

```bash
cd <service>
./mvnw test      # unit + web-slice tests, no Docker needed
./mvnw verify    # + Testcontainers integration tests (*IT), needs Docker
```

Frontend: `cd fitness-frontend && npm run lint && npm run build`.

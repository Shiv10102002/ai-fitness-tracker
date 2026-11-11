# Specs — how features are built in this repo

This project follows **Spec-Driven Development (SDD)** within an
**AI-assisted development lifecycle (AI-DLC)**. Every non-trivial change starts as a
short spec in this folder, *before* any code is written.

## Workflow

```
 1. Spec        Write specs/NNN-short-name.md from the template: problem,
                numbered requirements, acceptance criteria, out-of-scope.
                Open design questions are raised here, before building.
       │
 2. Review      The spec is reviewed (by a person and/or an AI reviewer) and
                questions are resolved. Status: Draft → Accepted.
       │
 3. Tests       Acceptance criteria become tests first: unit / web-slice tests
                (*Test) and Testcontainers integration tests (*IT).
       │
 4. Implement   Code is written (AI-assisted, human-reviewed) until the tests
                pass. Code comments link back to the spec (e.g. "specs/002, M3").
       │
 5. Verify      CI runs `mvn verify` per service + frontend lint/build.
                The spec's acceptance checklist is ticked; Status → Implemented.
```

### Where AI fits in (and where it doesn't)

| Stage | AI assistant does | Human owns |
|---|---|---|
| Spec | Drafts requirements from the problem statement, flags edge cases | Scope, priorities, final wording |
| Tests | Turns acceptance criteria into test cases | Checking tests assert the *right* behaviour |
| Implement | Writes code against the spec and tests | Design decisions, code review of every line |
| Review | First-pass review for bugs and security | Approval and merge |

Project conventions for AI assistants are in [`/CLAUDE.md`](../CLAUDE.md).

## Index

| # | Spec | Status |
|---|---|---|
| 001 | [Caller identity is derived only from the verified JWT](001-identity-from-jwt.md) | Implemented |
| 002 | [Consistent error responses and resilient Kafka messaging](002-error-handling-and-resilient-messaging.md) | Implemented |
| 003 | [Distributed tracing across HTTP and Kafka](003-distributed-tracing.md) | Implemented |

Architecture decisions that span many specs are in [`docs/adr`](../docs/adr).

New spec: copy [`000-template.md`](000-template.md).

# CLAUDE.md — guide for AI coding assistants

Conventions an AI assistant (or a new contributor) must follow in this repo.
The development process is described in [specs/README.md](specs/README.md).

## Repo layout

Six independent Maven projects (no parent POM) and one React app:

| Dir | Service | Port | Storage |
|---|---|---|---|
| `eureka/` | Service discovery | 8761 | — |
| `configServer/` | Spring Cloud Config (native, `src/main/resources/config/*.properties`) | 8888 | — |
| `gateway/` | Spring Cloud Gateway (WebFlux), OAuth2 resource server | 8080 | — |
| `userservice/` | User profiles | 8081 | PostgreSQL |
| `activityservice/` | Activity logging, Kafka producer | 8082 | MongoDB |
| `aiservice/` | Kafka consumer, Gemini client, recommendations | 8083 | MongoDB |
| `fitness-frontend/` | React 19 + Vite + MUI | 5173 | — |

Java 21, Spring Boot 4, Spring Cloud 2025.1, Jackson 3 (`tools.jackson.*`).

## Commands

```bash
docker compose up -d                 # Postgres, MongoDB, Kafka, Keycloak
cd <service> && ./mvnw test          # fast tests, no Docker
cd <service> && ./mvnw verify        # + Testcontainers *IT tests (Docker required)
cd fitness-frontend && npm run lint && npm run build
```

## Rules

1. **Spec first.** Non-trivial changes start with `specs/NNN-*.md` (copy `000-template.md`).
   Put open design questions in the spec and ask before implementing.
   Reference spec requirement ids in code comments only where the *why* is non-obvious.
2. **Identity.** Downstream services read the caller from the `X-User-ID` header, which
   the gateway sets from the verified JWT `sub` (spec 001). Never accept a user id from a
   request body or path for authorization, and never add endpoints that take another
   user's id.
3. **Errors.** Throw `ResourceNotFoundException` (404) or `ServiceUnavailableException` (503).
   Each service's `GlobalExceptionHandler` turns them into `ProblemDetail`. Don't use
   `printStackTrace()`, and don't catch-and-ignore (spec 002).
4. **Kafka.** Consumers must be idempotent (at-least-once delivery). Poison messages go
   to `<topic>.DLT` via the `DefaultErrorHandler`. Don't widen `trusted.packages`.
5. **Tests ship with code.**
   - `*Test`: Mockito unit tests and `@WebMvcTest` slices. Must run without Docker.
   - `*IT`: `@SpringBootTest` + Testcontainers (`@ServiceConnection`), run by Failsafe.
   - Test names describe behaviour: `duplicateEvent_isSkipped_withoutCallingGemini`.
   - Stub HTTP dependencies with `WebClient.builder().exchangeFunction(...)`, not live calls.
6. **Config & secrets.** Service config lives in `configServer/src/main/resources/config/`.
   Secrets are `${ENV_VAR}` placeholders only (see `.env.example`). Never commit real keys.
7. **Logging.** Use SLF4J with parameterised messages and include domain ids
   (activityId, userId). Don't log tokens or full LLM payloads at INFO.
8. **Scope.** Keep changes minimal and focused on the spec. Don't reformat unrelated code.

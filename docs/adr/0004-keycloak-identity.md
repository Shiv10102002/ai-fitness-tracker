# ADR 0004 — Keycloak as the only identity provider

**Status:** Accepted (recorded retroactively, revised by spec 001)

## Context
The app needs login, registration and token issuance, which are security-sensitive and
not the app's core value.

## Decision
- Keycloak issues JWTs via OAuth2 Authorization Code + PKCE (public SPA client).
- The gateway validates JWTs as an OAuth2 resource server.
- The application **never stores credentials**. user-service keeps only a profile linked
  by Keycloak `sub` (the original implementation stored a placeholder password; removed in spec 001).

## Consequences
- \+ No password handling, hashing or reset flows in application code.
- \+ Standard tokens make adding other clients (mobile, CLI) straightforward.
- − Keycloak is an extra runtime dependency (realm auto-imported by `docker-compose.yml`).

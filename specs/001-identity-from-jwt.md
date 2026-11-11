# Spec 001 — Caller identity is derived only from the verified JWT

| | |
|---|---|
| **Status** | Implemented |
| **Services** | gateway, user-service, ai-service, fitness-frontend |

## Problem

The gateway forwarded a client-supplied `X-User-ID` header when present and only
fell back to the JWT `sub` claim when it was missing. Downstream services trust
`X-User-ID`, so any authenticated user could read another user's activities and
recommendations by setting the header (`/api/recommendation/user/{userId}` did not
even need the header). In addition:

- the gateway parsed the raw `Authorization` header itself instead of using the
  token Spring Security had already validated;
- every synced user was stored with the hard-coded password `dummy@123`, in plain
  text, and `UserResponse` returned the password in API responses;
- `/api/users/register` was publicly reachable through the gateway.

## Requirements

1. **R1** — The gateway MUST set `X-User-ID` to the `sub` claim of the JWT validated
   by the OAuth2 resource server, overwriting any client-supplied value.
2. **R2** — Requests with no authenticated JWT MUST NOT carry a client-supplied
   `X-User-ID` downstream.
3. **R3** — Internal endpoints `POST /api/users/register` and
   `GET /api/users/{id}/validate` MUST NOT be reachable through the gateway.
   The gateway calls them service-to-service via Eureka.
4. **R4** — user-service MUST NOT store or return passwords. Keycloak is the only
   credential store.
5. **R5** — User registration MUST be idempotent on `keyCloakId`. A user that
   already exists by email but has no `keyCloakId` is linked, not duplicated.
6. **R6** — A user reads their own profile via `GET /api/users/me`. Lookup of
   arbitrary users by id is removed.
7. **R7** — Recommendation endpoints MUST be scoped to the caller:
   - `GET /api/recommendation/user` returns only the caller's recommendations.
   - `GET /api/recommendation/activity/{activityId}` returns 404 when the
     recommendation belongs to another user (no existence leak).

## Trust boundary

Downstream services trust `X-User-ID` **only because** the gateway is the single
ingress. In a deployed environment, services must not be exposed publicly
(private network / Kubernetes `ClusterIP`). This is documented, not enforced in code.

## Acceptance criteria

- [x] Authenticated request with `X-User-ID: victim` reaches the downstream service
      with `X-User-ID: <caller sub>` (gateway filter unit test).
- [x] Unauthenticated request carrying `X-User-ID` has the header stripped.
- [x] `POST /api/users/register` through the gateway returns 403.
- [x] `UserResponse` JSON has no `password` field.
- [x] Registering the same `keyCloakId` twice returns the same user.
- [x] Fetching another user's activity recommendation returns 404.

## Migration note

The `password` column is removed from the `users` entity. `ddl-auto=update`
does not drop columns, so on an existing local database run:

```sql
ALTER TABLE users DROP COLUMN IF EXISTS password;
```

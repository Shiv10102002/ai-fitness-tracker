# ADR 0003 — PostgreSQL for users, MongoDB for activities and recommendations

**Status:** Accepted (recorded retroactively)

## Context
User profiles are small, relational and need uniqueness constraints (email,
Keycloak id). Activities carry free-form `additionalMetrics` that differ by
activity type, and recommendations are nested documents produced by an LLM.

## Decision
- user-service → PostgreSQL via Spring Data JPA.
- activity-service and ai-service → MongoDB (separate databases), schema-flexible documents.

## Consequences
- \+ Unique constraints and transactions where they matter (users).
- \+ No migrations needed when new activity metrics are added.
- − Two database technologies to operate.
- − Each service owns its data, so cross-service queries go through APIs, never shared tables.

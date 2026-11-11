# ADR 0001 — Microservices behind an API gateway with service discovery

**Status:** Accepted (recorded retroactively)

## Context
The app has three distinct responsibilities with different scaling and failure
profiles: user profiles (low traffic, relational), activity logging (write-heavy),
and AI analysis (slow, depends on an external LLM API with rate limits).

## Decision
- One Spring Boot service per responsibility: user-service, activity-service, ai-service.
- Spring Cloud Gateway is the single ingress: JWT validation, CORS, routing, and
  setting the trusted `X-User-ID` header (see spec 001).
- Eureka for service discovery (`lb://service-name`), Spring Cloud Config Server for
  centralised configuration.

## Consequences
- \+ AI latency or Gemini outages cannot slow down activity logging.
- \+ Each service can be deployed and scaled independently.
- − More moving parts locally (mitigated by `docker-compose.yml`).
- − Cross-service debugging needs correlation (addressed by spec 003, tracing).
- − Downstream services trust `X-User-ID`, so they must not be exposed publicly.

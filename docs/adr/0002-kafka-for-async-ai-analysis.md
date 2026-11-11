# ADR 0002 — Kafka for asynchronous AI analysis

**Status:** Accepted (recorded retroactively)

## Context
A Gemini call takes seconds and can fail or be rate limited. Logging an activity
should be fast and must not fail because the AI is unavailable.

## Decision
activity-service publishes an event to the `activity-events` topic (keyed by user id)
after saving. ai-service consumes it, calls Gemini and stores a recommendation.
The frontend fetches the recommendation later.

## Consequences
- \+ Activity logging latency is independent of Gemini.
- \+ Events keyed by user keep each user's activities ordered within a partition.
- \+ Failed analyses can be retried or dead-lettered without user impact (spec 002).
- − Eventual consistency: a recommendation can be "not ready yet" (UI shows this).
- − At-least-once delivery requires idempotent consumers (spec 002, M3).
- − Save-then-publish is not atomic; a transactional outbox is the documented next step.

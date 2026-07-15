# cloud-itonami-isic-932: Amusement & Recreation Facility Operations Coordination

An LLM/actor framework for amusement parks, arcades, and recreational facility back-office administrative coordination.

**ISIC Classification**: Rev.4 Division 93 (Other amusement and recreation activities)

## What This Does

This actor coordinates the operational infrastructure of amusement parks, arcades, and recreational facilities:

- Venue/attraction-area booking scheduling
- Administrative maintenance-schedule coordination (when technicians visit)
- Non-safety-critical consumables supply coordination (guest supplies, office supplies, cleaning)
- Guest check-in/ticketing/wayfinding logistics coordination
- Facility/ride/guest safety concern flagging

## What This Does NOT Do

This actor explicitly does **not** handle:

- Ride-safety-inspection sign-offs or safety certifications
- Pricing or programming policy decisions
- Operational-readiness (go/no-go) decisions
- Safety-authority overrides or mandatory reporting (escalates instead)

## Architecture

**Namespace**: `amusementfacilityops`

**Modules**:
- `store` — String-keyed facility/booking directory (MemStore, EDN-backed)
- `advisor` — Proposal generation and rationale (LLM seam)
- `governor` — Three HARD checks (facility verification, effect validation, scope exclusion)
- `phase` — Rollout phases 0–3 (read-only → auto-commit with escalation)
- `operation` — State machine: intake → advise → govern → decide → commit | hold | request-approval
- `sim` — Demo driver
- `test` — Comprehensive test suite

**All modules are `.cljc`** (ClojureScript + JVM compatible).

## Governor: Three HARD Checks

1. **Facility unverified**: Target facility must exist AND be `:registered?`/`:verified?` in store.
2. **Effect not :propose**: Any `:effect` other than `:propose` is rejected outright.
3. **Scope exclusion**: Any proposal touching ride-safety-inspection, pricing/programming policy, operational-readiness decisions, or safety-authority territory is permanently blocked.

These are un-overridable, even with human approval.

## Operations (Closed Allowlist)

- `:schedule-facility-booking`
- `:coordinate-maintenance-schedule-proposal`
- `:coordinate-supply-request`
- `:coordinate-guest-services-logistics`
- `:flag-safety-concern` (always escalates)

## Running

### Tests

```bash
clojure -M:test
```

### Demo

```bash
clojure -M:dev -e "(amusementfacilityops.sim/run-all-demos)"
```

### Lint

```bash
clojure -M:lint
```

## Rollout Phases

- **Phase 0**: Read-only (all proposals held for review)
- **Phase 1**: Facility booking + maintenance-schedule auto-commit
- **Phase 2**: + supply coordination + guest-services auto-commit
- **Phase 3**: All auto-commit with escalation (safety concerns always escalate)

## References

- ADR-2607121000 (cloud-itonami Wave-4 rollout plan)
- ADR-2607152500 (Wave-4 amendment)
- ADR-2607154200 (this actor's design)

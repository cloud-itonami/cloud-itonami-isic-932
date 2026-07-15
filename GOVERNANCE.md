# Governance: ISIC-932 Amusement Facility Coordination Actor

## Overview

This actor is part of the cloud-itonami fleet of LLM-driven business coordination services. It operates under three **HARD, permanent, un-overridable** governor checks that enforce scope boundaries.

## Three HARD Governor Checks

### 1. Facility Unverified

**Rule**: Target facility must be registered AND independently verified.

**Mechanism**: Re-derived from the facility's own `:registered?` and `:verified?` fields in the store, never from proposal self-report.

**Override Path**: None. This is permanent and cannot be waived.

### 2. Effect Not :propose

**Rule**: Any effect other than `:propose` is rejected outright.

**Mechanism**: All operations that pass the governor must have `:effect :propose`.

**Override Path**: None. This is permanent and cannot be waived.

### 3. Scope Exclusion

**Rule**: Proposals touching the following are permanently blocked:

- Ride-safety-inspection sign-offs or certifications
- Pricing or programming policy decisions
- Operational-readiness (go/no-go) decisions
- Safety-authority overrides or mandatory reporting

**Mechanism**: Qualified substring scan (English + Japanese) with carve-out for legitimate `:flag-safety-concern` operations.

**Override Path**: None. This is permanent and cannot be waived.

## Escalation

The operation `:flag-safety-concern` always escalates to a human decision-maker, even if governance passes. This ensures that safety concerns never auto-commit.

## Phases

The actor operates in four rollout phases:

- **Phase 0**: Read-only (no auto-commit)
- **Phase 1**: Facility booking + maintenance-schedule auto-commit
- **Phase 2**: + supply coordination + guest-services auto-commit
- **Phase 3**: All non-safety ops auto-commit, safety concerns escalate

Phase progression is controlled externally (by human decision or organization policy), not by the actor.

## Audit Trail

Every proposal is recorded in an append-only ledger, capturing:

- Proposal timestamp
- Operation type
- Target facility
- Outcome (committed, held, escalated, pending-approval)
- Any governance violations

## References

- ADR-2607121000: Wave-4 Rollout Plan
- ADR-2607152500: Wave-4 Amendment
- ADR-2607154200: ISIC-932 Actor Design

# Security Policy

## Reporting Security Vulnerabilities

If you discover a security vulnerability, please do not open a public issue. Instead, please contact the project maintainers privately through the cloud-itonami organization's security contact.

## Security Principles

This actor is designed with the following security principles:

1. **Hard Governor Checks**: Three permanent, un-overridable checks prevent scope violations.
2. **Scope Boundaries**: The actor operates only within amusement facility administrative coordination. Safety-critical operations (ride-safety sign-offs, operational-readiness decisions) are permanently blocked.
3. **Audit Trails**: All proposals and decisions are recorded in an append-only ledger.
4. **Escalation**: Safety concerns always escalate to human review.

## Known Limitations

- This actor operates in a Clojure/ClojureScript environment with dependencies on `langgraph-clj` and `langchain-clj`. Dependencies should be kept up to date.
- The LLM advisor seam is subject to prompt injection and hallucination risks. In production, the advisor output must be validated against hard constraints.

## Security Updates

Security updates will be released as patches with clear communication of the vulnerability and fix.

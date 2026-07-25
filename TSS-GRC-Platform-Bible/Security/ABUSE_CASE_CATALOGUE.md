# Abuse Case Catalogue

| ID | Abuse case | Prevent | Detect / prove |
|---|---|---|---|
| AC-01 | Writer approves own change under another name | authenticated actor ID, APPROVE permission, SoD | vote actor/authority audit and duplicate constraint |
| AC-02 | Requester marks an emergency change approved | named command, ECAB authority, independent ratification | emergency-rate and self-approval alerts |
| AC-03 | Secretary invents attendees to satisfy quorum | effective membership-derived denominator | attendance reconciliation exception |
| AC-04 | One user votes repeatedly using aliases | user-ID uniqueness per resolution | rejected duplicate audit |
| AC-05 | User accesses an unlicensed package API | universal entitlement interceptor | entitlement-denial telemetry |
| AC-06 | Tenant guesses another tenant's object ID | tenant-scoped owner query and DB guard | two-tenant negative suite |
| AC-07 | Export reveals records beyond interactive scope | export-specific permission and same query policy | export manifest and audit |
| AC-08 | Scheduler processes wrong tenant | explicit organisation parameter and per-tenant transaction | scheduler tenant audit |
| AC-09 | Evidence file replaced after approval | immutable versions and checksum links | hash verification |
| AC-10 | Malicious upload compromises reviewer | scanning, safe preview, content controls | quarantine and scan event |
| AC-11 | Public token is enumerated or replayed | high entropy, expiry, one-time use, uniform errors | replay/rate alert |
| AC-12 | Whistleblower identity inferred through dashboard | restricted identity vault and aggregation thresholds | sensitive-query audit |
| AC-13 | AI prompt leaks confidential evidence | data-class/provider policy and redaction | prompt/model audit |
| AC-14 | AI creates authoritative legal conclusion | suggestion-only capability and human command | accepted/edited/rejected trace |
| AC-15 | Integration replays a high-impact webhook | signature, timestamp and idempotency | replay rejection metric |
| AC-16 | Admin silently grants self elevated access | maker-checker admin workflow | privilege-change alert |
| AC-17 | Break-glass remains active after scheduler failure | access-time expiry enforcement | stale privilege sweep |
| AC-18 | Soft-deleted sensitive record remains searchable | deletion propagation and index tombstone | deletion reconciliation |
| AC-19 | Old framework version inflates compliance | content version/effective date and stale warning | posture version manifest |
| AC-20 | User backdates approval/evidence | server timestamp and immutable history | clock/source audit |
| AC-21 | Concurrent approvers overwrite conditions | optimistic locking and decision append log | conflict response/audit |
| AC-22 | Package removal orphans regulator evidence | dependency/retention preflight | signed removal report |
| AC-23 | Support operator browses tenant data | time-bound support session, customer approval, masking | enhanced support audit |
| AC-24 | CSV/Excel export performs formula injection | escape dangerous cells | export security tests |
| AC-25 | Arabic/English mismatch changes legal meaning | bilingual review status and dual-language approval | translation-version history |

## Test rule

Every Critical/High finding must map to at least one abuse case and automated negative test. Security acceptance demonstrates the denial and the retained audit evidence.


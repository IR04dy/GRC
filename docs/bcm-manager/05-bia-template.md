# 05 · BIA Template

> **HISTORIC — point-in-time snapshot (all files 2026-05-23). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*

**Document owner:** BIA Owner (per process) · **Cadence:** Annual · **Backing entity:** `bcm.bia_records`

> Fill in every `[FILL]` placeholder. Attach evidence via the platform. Do not delete sections — mark "N/A" if not applicable so reviewers can see they weren't skipped.

---

## A · Identification

| Field | Value |
|---|---|
| BIA Code | `BIA-[FILL]` |
| Process name (EN) | [FILL] |
| Process name (AR) | [FILL] |
| Business unit | [FILL] |
| Process Owner | [FILL] |
| BIA Owner (this doc) | [FILL] |
| Effective date | [FILL] |
| Next review due | [Effective date + 12 months] |
| Hijri date | [FILL] |
| Version | 1.0 |
| Prior version link | n/a or [VERSION-LINK] |

## B · Process description

| Field | Value |
|---|---|
| Purpose (EN, 1-2 sentences) | [FILL] |
| Purpose (AR) | [FILL] |
| Frequency | Continuous / Daily / Weekly / Monthly / Event-driven |
| Peak windows | Hajj / Ramadan / Year-end / Eid bonus / Quarter-end / Other |
| Volume (typical day) | [FILL transactions or units] |
| Volume (peak day) | [FILL] |
| Revenue per day (typical) | SAR [FILL] |

## C · Dependencies

### C.1 Upstream (this process needs to function)
| Dependency | Type | Owner | Criticality to this process |
|---|---|---|---|
| [FILL system/process/vendor] | SYSTEM / PROCESS / VENDOR / DATA / PEOPLE | [FILL] | HARD / SOFT |

### C.2 Downstream (these depend on this process)
| Dependent | Type | Owner | Their RTO |
|---|---|---|---|
| [FILL] | [FILL] | [FILL] | [FILL hours] |

### C.3 Cross-border data flows (PDPL Art. 29)
- Does this process transfer personal data outside KSA? **Yes / No**
- If yes: destination(s), data category, safeguard (SCC / adequacy / consent), monitoring frequency.

## D · Recovery objectives

| Metric | Steady-state | Peak (Hajj/Ramadan) | Source / rationale |
|---|---|---|---|
| **MTPD** (max tolerable disruption) | [FILL h] | [FILL h] | [FILL] |
| **RTO** (recovery time objective) | [FILL h] | [FILL h] | ≤ 0.5 × MTPD |
| **RPO** (recovery point objective) | [FILL min] | [FILL min] | ≤ 0.1 × MTPD |
| **MBCO** (minimum continuity objective — what's the smallest working level we can run at?) | [FILL] | [FILL] | [FILL] |

CFO sign-off on financial RTO commitment: ☐ Signed `[Date]`

## E · Impact matrix (time-band × dimension)

Score each cell 1-5 per BIA methodology §3.3.

|  | T1 (0-1h) | T2 (1-4h) | T3 (4-24h) | T4 (24-72h) | T5 (72h-7d) | T6 (7d+) |
|---|---|---|---|---|---|---|
| **D1 Financial** | [#] | [#] | [#] | [#] | [#] | [#] |
| **D2 Operational** | [#] | [#] | [#] | [#] | [#] | [#] |
| **D3 Regulatory** | [#] | [#] | [#] | [#] | [#] | [#] |
| **D4 Reputational** | [#] | [#] | [#] | [#] | [#] | [#] |
| **D5 Health/Safety** | [#] | [#] | [#] | [#] | [#] | [#] |
| **D6 Legal** | [#] | [#] | [#] | [#] | [#] | [#] |

**Overall impact score** = MAX × 0.6 + AVG × 0.4 = **[FILL]**

## F · Quantitative financial impact

| Time band | Direct revenue loss (SAR) | Regulatory fine exposure (SAR) | Recovery cost (SAR) | Total (SAR) |
|---|---|---|---|---|
| T1 (0-1h) | | | | |
| T2 (1-4h) | | | | |
| T3 (4-24h) | | | | |
| T4 (24-72h) | | | | |
| Beyond T4 | | | | |

## G · Continuity options considered

| Option | Cost | Recovery time | Trade-off | Decision |
|---|---|---|---|---|
| Do nothing | SAR 0 | Indefinite | Customers + regulator | ☐ |
| Manual workaround | [FILL] | [FILL] | Throughput drop | ☐ |
| Warm DR site | [FILL] | [FILL h] | Capital cost | ☐ |
| Hot DR site | [FILL] | [FILL h] | Highest cost | ☐ |
| Cloud failover (AWS Bahrain) | [FILL] | [FILL h] | Cross-border | ☐ |
| Vendor alternate | [FILL] | [FILL h] | Onboarding time | ☐ |

**Selected strategy:** [FILL] · Rationale: [FILL]

## H · Workforce + premises

| Resource | Steady-state | Recovery minimum | Where |
|---|---|---|---|
| Staff | [#] | [#] | [Location(s)] |
| Workstations | [#] | [#] | [Primary + DR] |
| Specialist skills required | [FILL] | n/a | [FILL] |

## I · Vendors

| Vendor | Function | Tier | RTO commit | BCP attested? | Last attested |
|---|---|---|---|---|---|
| [FILL] | [FILL] | TIER_1/2/3 | [FILL h] | Yes/No | [Date] |

## J · Acknowledgements

| Role | Name | Signature | Date |
|---|---|---|---|
| BIA Owner | [FILL] | ☐ | [FILL] |
| Process Owner | [FILL] | ☐ | [FILL] |
| BCM Manager | [FILL] | ☐ | [FILL] |
| CRO | [FILL] | ☐ | [FILL] |
| CFO (financial RTO sign-off) | [FILL] | ☐ | [FILL] |

## K · Change log

| Version | Date | Author | Summary of change |
|---|---|---|---|
| 1.0 | [FILL] | [FILL] | Initial BIA |

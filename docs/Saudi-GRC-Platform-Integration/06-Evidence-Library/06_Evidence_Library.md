# 06 — Evidence Library

Evidence is the connective tissue of the platform — every control
test, attestation, audit finding, regulatory submission, and CAPA
closure can point at evidence rows. Lives in `module/evidence` with
chain-of-custody and expiry.

## Schema (highlights)

`evidence.evidence_items` carries:

| Column | Notes |
|---|---|
| `code` / `title_en` / `title_ar` | unique-per-tenant code |
| `evidence_type` | POLICY · BCP · DRP · ASSESSMENT · ATTESTATION · LOG · SCREENSHOT · REPORT · OTHER |
| `confidentiality` | PUBLIC · INTERNAL · CONFIDENTIAL · SECRET · TOP_SECRET |
| `owner_user_id`, `owner_name` | |
| `framework_requirement` | soft reference to citation |
| `version`, `effective_date`, `review_date`, `expiry_date` | |
| `status` | DRAFT · PENDING · ACTIVE · SUPERSEDED · EXPIRED · ARCHIVED |
| `file_name`, `mime_type`, `size_bytes`, `storage_uri` | physical file |
| `checksum_sha256` | tamper-evidence |
| `digital_signature_ref` | optional FK to `signature.digital_signature_transactions` |
| `retention_until` | for purge policy |
| `parent_evidence_id` | superseded chain |

`evidence.evidence_links` ties evidence to any anchor (control,
control assessment, risk, finding, CAPA, attestation, breach,
policy attestation, NDI submission). Polymorphic: `entity_type` +
`entity_id`.

`evidence.evidence_requests` carries asks: who, by when, against
which control or finding, current status.

`evidence.chain_of_custody` records every access / approve / reject /
move event for high-confidentiality items.

## Evidence catalog (by control / workflow area)

Format per row: name → frequency → owner → accepted types → expiry
rule → digital signature?

### BCM
| Evidence | Freq | Owner | Types | Expiry | Sig |
|---|---|---|---|---|---|
| BCM Policy | Annual | BCM Manager | PDF | 12m | ✅ |
| BIA report (per critical service) | Annual | BIA Owner | PDF, XLSX | 12m | — |
| BCP (per service) | Annual | Service Owner | PDF | 12m | ✅ on publish |
| DRP runbook | Annual | DR Owner | PDF, MD | 12m | — |
| Exercise report | Per exercise | BCM Manager | PDF | event-based | — |
| Crisis comms log | Per crisis | Crisis Manager | PDF, MD | retain 5y | — |
| Management review minutes | Annual | BCM Manager | PDF | 12m | ✅ |

### Supplier resilience
| Evidence | Freq | Owner | Types | Expiry | Sig |
|---|---|---|---|---|---|
| Supplier BCP attestation | Annual | Supplier Manager | PDF | 12m | ✅ |
| Resilience test report | Per test | Supplier Manager | PDF | event | — |
| Contract resilience clause | Per contract | Procurement | DOCX, PDF | per contract | ✅ |
| 4th-party register | Quarterly | Supplier Manager | XLSX | 3m | — |
| Exit plan | Annual | Supplier Manager | PDF | 12m | ✅ |

### ERM
| Evidence | Freq | Owner | Types | Expiry | Sig |
|---|---|---|---|---|---|
| Risk register snapshot | Quarterly | Risk Manager | XLSX | 3m | — |
| Risk acceptance memo | Per acceptance | Risk Committee | PDF | per acceptance term | ✅ |
| KRI dashboard export | Monthly | Risk Manager | PDF | 1m | — |
| Risk committee minutes | Per meeting | Board Secretary | PDF | retain 5y | ✅ |

### NCA cybersecurity
| Evidence | Freq | Owner | Types | Expiry | Sig |
|---|---|---|---|---|---|
| Control assessment report | Annual | NCA Compliance Owner | PDF | 12m | ✅ |
| Vulnerability scan | Weekly / Monthly | Cybersecurity Manager | PDF, XML | 1m | — |
| Pen-test report | Annual | Cybersecurity Manager | PDF | 12m | ✅ |
| Privileged access review | Quarterly | IAM Owner | XLSX | 3m | — |
| Cloud (CCC) attestation | Annual | Cybersecurity Manager | PDF | 12m | ✅ |
| OT (OTCC) attestation | Annual | OT Owner | PDF | 12m | ✅ |
| Awareness training log | Annual | HR + Cyber | XLSX | 12m | — |

### PDPL
| Evidence | Freq | Owner | Types | Expiry | Sig |
|---|---|---|---|---|---|
| ROPA snapshot | Quarterly | DPO | XLSX | 3m | — |
| PIA (per high-risk processing) | Per change | DPO | PDF | per change | ✅ |
| Privacy notice (per channel) | Annual | DPO | HTML, PDF | 12m | — |
| Consent record sample | Continuous | DPO | XLSX | 12m | — |
| Cross-border transfer assessment | Per request | DPO | PDF | per request | ✅ |
| Breach notification | Per breach | DPO | PDF | event | ✅ |
| Retention disposal certificate | Per cycle | Data Owner | PDF | retain 7y | ✅ |

### Cross-cutting (Reports, Approvals)
| Evidence | Freq | Owner | Sig |
|---|---|---|---|
| Board report | Quarterly | Board Secretary | ✅ |
| Regulatory evidence package | Per submission | Compliance Officer | ✅ |
| Policy approvals | Per publish | Approver | ✅ |
| CAPA closure evidence | Per closure | CAPA owner | — |

## Quality criteria (every evidence item)

- **Source** — system-generated > human-uploaded.
- **Date** — recent enough for the frequency.
- **Signed** — for evidence flagged "Sig ✅" above.
- **Versioned** — supersession via `parent_evidence_id` for any
  policy/plan that gets updated.
- **Linked** — at least one `evidence_links` row tying it to a
  control / finding / CAPA / attestation.
- **Confidentiality** — set to the strictest applicable level.
- **Retention** — `retention_until` set per PDPL Article 18.

## Lifecycle

```
PENDING → ACTIVE → EXPIRING_SOON (60-day window) → EXPIRED → ARCHIVED
                       │
                       └── (admin) → SUPERSEDED → (replacement ACTIVE)
```

Automation rule **AR-09** notifies evidence + control owners 60 days
before expiry.

## Digital signature integration

Evidence flagged "Sig ✅" requires a successful digital-signature
transaction before the evidence can move to `ACTIVE`. The
transaction reference is stored in `digital_signature_ref` and
appears in the audit log as a `DIGITAL_SIGNATURE_COMPLETE` action
type.

---

**See also:** `06_Evidence_Excel_Structure.md` for the bulk-import
sheet, `module/evidence`, `module/signature`.

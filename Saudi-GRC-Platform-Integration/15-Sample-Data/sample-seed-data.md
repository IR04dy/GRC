# 16 — Sample Seed Data
> **HISTORIC — point-in-time snapshot (all files 2026-05-11). Not a statement of current state.**
> ⚠ **This is PACK A of two generations, and neither supersedes the other** — finding **F2-135**. PACK B (`docs/Saudi-GRC-Platform-Integration/`, 2026-05-18) covers 16 shared sections more deeply, but **dropped sections 16–18 of this pack entirely** (AI features, frontend requirements, Excel workbook — 1,010 lines found only here) and reused the number `16` for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


Demo seed data for every required scenario. Implemented as Flyway
migrations (V2 … V54) plus Python bootstrap scripts. Everything below
is realistic for a Saudi-headquartered listed entity but contains no
real confidential information.

---

## Seed sources

| Migration | Scope |
|---|---|
| `V2__core_seed.sql` | Roles + first organisation + default users (admin, riskmanager, auditor) |
| `setup_cyber_db.py` | Cyber schema + 41 seed rows (dev only) |
| `setup_bcm_db.py` | BCM schema + seed (dev only) |
| `V_seed_*` migrations | Saudi-native risk taxonomy, sample evidence, sample policies |
| `V30__digital_signatures.sql` | 11 signature transactions (inbox + history) |
| `V31__root_cause_analyses.sql` | 7 RCA records linked to incidents |
| `V32__pdpl_extensions.sql` | Cross-border, disposal, notices schemas + first seeds |
| `V33-V34__demo_seed_for_new_modules.sql` | Top-up rows so every status tab has at least one record |
| `V35__risk_scenarios.sql` | 11 risk scenarios (MC + stress + what-if) |
| `V36__supplier_resilience.sql` | 10 alternates + 7 exit plans |
| `V37__nca_framework_seeds.sql` | 8 cyber frameworks (NCA + SAMA + ISO + PCI) |
| `V38__top_up_seed_gaps_3_4_5.sql` | Status-tab top-up rows |
| `V39__emerging_risks.sql` | 10 horizon-scan signals |
| `V40__committee_workflows.sql` | 5 committees + 8 meetings |
| `V41__disaster_recovery_plans.sql` | 8 DR plans |
| `V42__nca_cscc_control_seed.sql` | 32 CSCC controls |
| `V43__nca_ccc_control_seed.sql` | 30 CCC controls |
| `V44__nca_otcc_control_seed.sql` | 27 OTCC controls |
| `V45__contract_clause_library.sql` | 22 contract clauses across 9 categories |
| `V46__cyber_pdpl_breach_bridge.sql` | Cross-link columns + bridge log table |
| `V46a__cyber_pdpl_bridge_seed_fix.sql` | 5 bridge events (CREATED + SKIPPED) |
| `V47__evidence_chain_of_custody.sql` | SHA-256 chain hashing on every evidence version |
| `V47a__evidence_chain_broken_demo.sql` | Inject a broken chain so the BROKEN filter shows ≥1 |
| `V48__align_ropa_codes_to_demo_register.sql` | Align ROPA-2026-NNN → ROPA-NNN |
| `V49__seed_empty_framework_and_demo_tabs.sql` | 8 SAMA + 8 ISO 22301 + 8 PCI DSS + Linked bridge + Unverified chain |
| `V50__fourth_party_exposure.sql` | 7 fourth-party edges including HIGH-concentration |
| `V51__data_sharing_agreements.sql` | 8 DSAs (SIMAH, Yakeen, group HR, NCA, HubSpot, KFUPM, MoCI, group cyber) |
| `V52__processor_due_diligence.sql` | 8 PDPL Art. 28 assessments (incl. 1 FAILED, 1 IN_PROGRESS) |
| `V53__third_party_cyber_risk.sql` | 8 TPRM cyber assessments (STC, AWS, OpenAI, TCS, HubSpot, Sentry, legacy, Stripe) |
| `V54__supplier_resilience_tests.sql` | 8 resilience tests (incl. 1 FAILED OpenAI, 1 CANCELLED, 2 SCHEDULED) |
| `fix_arabic_mojibake.py` | Repairs any UTF-8/CP1252 mojibake from prior imports |

---

## Sample organisations

- **Saudi government entity**: "Authority for Statistics" (variant)
- **Financial institution**: a Saudi commercial bank with SAMA Cyber +
  ECC + outsourcing scope
- **Vision-2030 listed firm**: a TASI-listed industrial company

## Sample critical service

- "Customer onboarding & KYC" — ROPA-001
- "Payment processing" — ROPA-003
- "Employee payroll (WPS)" — ROPA-005

## Sample digital government service

- "Yakeen identity verification" via Elm Tabadul (DSA-2026-002).

## Sample cloud service

- Workday HCM on AWS Ireland (Workday with AWS as fourth-party).
- AWS eu-west-1 hosting analytics (concentration risk).

## Sample critical supplier

- Solutions by STC (managed-cloud, sector CRITICAL)
- AWS (CRITICAL)
- Workday (CRITICAL — HR + payroll)

## Sample cybersecurity supplier

- Solutions by STC (managed services), Help AG (SOC), DXC (SOC), Mobily
  (alternate cloud).

## Sample personal-data processing activity

- ROPA-001 Customer onboarding & KYC (legal obligation, identity,
  contact, financial)
- ROPA-004 Marketing communications (consent-based)
- ROPA-007 Job applicant screening (consent-based, cross-border).

## Sample high-risk PIA

- ROPA-004 marketing analytics: consent + cross-border + behavioural data.

## Sample NCA assessment

- Per-organisation ECC self-assessment (V42 + V43 + V44 + V49 seeded
  controls + V37 framework rows).

## Sample BIA

- BIA for "Customer onboarding & KYC" with MTPD 24h, RTO 4h, RPO 1h.

## Sample BCP

- BCP for the same service with comms tree + alternate site + named
  team.

## Sample DRP

- 8 DR plans in `bcm.dr_plans` (V41) covering SQL Server primary,
  Workday connector, Elm Tabadul connector, payment gateway, etc.

## Sample cyber incident

- INC-2026-0021 phishing email (single user clicked); resolved.
- INC-2026-001 broader phishing campaign (3 users entered creds).

## Sample personal-data breach

- Generated automatically by the cyber→PDPL bridge from one of the cyber
  incidents above.

## Sample supplier disruption

- "OpenAI API outage joint crisis sim" — SRT-2026-006 FAILED in V54.

## Sample enterprise risk

- RSK-2026-001 above-appetite Aramco contract migration risk.
- RSK-2026-004 18-month exposure above amber appetite (linked to
  acceptance with CRO signature).

## Sample corrective action

- Auto-generated CAPAs from FAILED processor assessment, HIGH
  fourth-party concentration, and FAILED resilience test.

## Sample evidence records

- 58 evidence items in `evidence.evidence_items` with SHA-256 chain hashes.
- 1 broken chain demo (V47a).
- 1 unverified chain demo (V49).

## Sample board dashboard data

- All KPIs on `/dashboard` are real (live from the seeded data); board
  pack PDF generates with real risk + compliance + DR figures.

---

## Re-seeding a fresh dev DB

```bash
# 1. Drop + recreate DB
sqlcmd -S localhost -U sa -P "tss.123" \
  -Q "IF DB_ID('TSS_GRC') IS NOT NULL DROP DATABASE TSS_GRC; CREATE DATABASE TSS_GRC COLLATE Latin1_General_100_CI_AS_SC_UTF8;"

# 2. Apply Flyway migrations (V1..V54) — easiest path via Spring Boot dev
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# or via the Spring Boot Maven Plugin's spring-boot.run.arguments

# 3. Top-up Python scripts (dev only)
python setup_cyber_db.py
python setup_bcm_db.py
python fix_arabic_db.py

# 4. Repair any imported mojibake
python fix_arabic_mojibake.py
```

Total seeded rows across the platform at HEAD: **~3,500** across 80+
tables.

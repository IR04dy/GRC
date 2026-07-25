# Source Module Inventory and Target Ownership

`VERIFIED` inventory of the 58 top-level backend source directories. “Target package” is the proposed commercial/architectural owner, not the current runtime boundary.

| Source module | Java files | Target package | Current role | Primary assessment concern |
|---|---:|---|---|---|
| `admin` | 48 | Kernel / Governance | administration and committee engine | committee code untested as a module; split domain committee ownership from platform admin |
| `ai` | 32 | AI & Model Governance | suggestions and AI governance | preserve human oversight; define provider/data boundary |
| `aml` | 16 | Saudi Financial Services | sanctions screening | validate source licensing, matching quality and case workflow |
| `approvals` | 6 | Kernel | generic approvals | reconcile with workflow chains; one authoritative approval primitive |
| `attachment` | 7 | Kernel | file metadata/storage | zero module tests; malware, retention and classification evidence |
| `audit_committee` | 8 | Internal Audit | committee dashboard snapshots | separate projection from authoritative committee decisions |
| `audit_eng` | 6 | Internal Audit | engagement workspace | overlaps `audit_lc`; consolidate authority |
| `audit_lc` | 28 | Internal Audit | audit lifecycle and IA planning | two audit implementations and cross-package coupling |
| `auth` | 6 | Kernel | login and refresh | production identity/MFA/SSO acceptance evidence |
| `bcm` | 198 | BCM & Crisis | full BCM domain | very large module; split internal subdomains and UX |
| `bcm_lc` | 6 | BCM & Crisis | invocation lifecycle | reconcile with core BCM plan invocation |
| `board_pack` | 6 | Governance | board pack records | zero module tests; ensure metric lineage |
| `capa` | 6 | Kernel or Assurance | shared corrective action | define canonical issue/action ownership and source-type extensibility |
| `ccm` | 22 | Compliance | continuous control monitoring | define connector truth, freshness and false-positive handling |
| `change_mgmt` | 18 | Change Enablement | change/CAB | critical actor, permission and state-transition defects |
| `compliance` | 52 | Compliance | frameworks, controls and assessments | canonical control ownership and obligation versioning |
| `consumer` | 10 | Ethics / Complaints | complaint management | sensitive public token and case access assurance |
| `controlmap` | 7 | Compliance | control crosswalk | zero tests; AI/human mapping provenance |
| `cpty` | 14 | Saudi Financial Services | counterparty risk | zero tests; clarify relation to TPRM and ERM |
| `cyber` | 68 | Cyber GRC | cyber controls and exposure | boundary with SOC Ops and Compliance |
| `cyber_lc` | 6 | Cyber GRC | cyber lifecycle | reconcile lifecycle ownership |
| `dashboard` | 8 | Kernel projections | landing dashboards | zero tests; direct cross-module aggregation and metric lineage |
| `data_officer` | 23 | Data Governance / Privacy | data-officer workspace | zero tests; overlaps Privacy and NDI |
| `dataclass` | 12 | Data Governance | classification/assets/flows | zero tests; canonical data asset ownership |
| `datagov` | 1 | Data Governance | placeholder | stub is not a product capability |
| `ethics` | 46 | Ethics | speak-up and COI | need case-level confidentiality and portal assurance |
| `evidence` | 55 | Kernel | evidence lifecycle | only two module tests despite platform-wide criticality |
| `fraud` | 10 | Ethics / Financial Crime | fraud cases | zero tests; sensitive-case security |
| `governance` | 13 | Governance | three-lines/pivot | clarify projections versus authoritative objects |
| `iam` | 37 | Access Governance | access requests/SoD/certification | scheduled/off-request tenant and break-glass assurance |
| `incident` | 13 | Cross-package case or Cyber | incident/RCA | define whether generic incident or cyber-owned |
| `intake` | 9 | Kernel | forms/connectors/inbox | public intake abuse controls and routing contracts |
| `integrations` | 68 | Kernel adapters | many connectors and bridges | avoid connector-driven domain coupling; secret/residency governance |
| `knowledge` | 6 | Kernel | knowledge base | zero tests; content permissions and localization |
| `nca_ecc` | 6 | Cyber content pack | NCA export/filing | content pack should not become isolated control truth |
| `ndi` | 62 | Data Governance / NDI | National Data Index workflows | huge 15-tab UI; limited tests; official content provenance |
| `openbanking` | 14 | Saudi Financial Services | TPP/consent/incident | zero tests; specialist regulatory verification |
| `opres` | 10 | SAMA Resilience | operational-resilience assessment | zero tests; critical-service/impact-tolerance linkage |
| `outsourcing` | 14 | SAMA Resilience | arrangements/cloud/exit | zero tests; overlap with TPRM |
| `platform` | 100 | Kernel | shared platform services | attracts peer dependencies; must contain no commercial semantics |
| `policy` | 13 | Policy Management | policy lifecycle | consolidate with compliance without losing package independence |
| `privacy` | 63 | Privacy | PDPL operations | strong breadth; strict sensitive access and statutory clock evidence |
| `privacy_lc` | 6 | Privacy | privacy lifecycle | reconcile duplicate lifecycle authority |
| `regchange` | 6 | Regulatory Affairs | regulatory change | integrate source/version with compliance impact |
| `reginspect` | 10 | Regulatory Affairs | inspection workspace | zero tests; evidence and regulator correspondence |
| `regreporting` | 10 | Regulatory Affairs | filings | zero tests; immutable submission record/signature |
| `reporting` | 22 | Kernel | exports and schedules | some permission semantics incorrect; enforce package entitlement |
| `risk` | 99 | ERM | risk/KRI/FAIR/bow-tie | large domain; fragmented UX and peer dependencies |
| `risk_lc` | 7 | ERM | risk lifecycle | consolidate current-state authority |
| `rrp` | 14 | Saudi Financial Services | recovery/resolution cycles | zero tests; verify regulator-specific process |
| `scm` | 46 | TPRM | supply-chain/resilience | only one module test; clarify party master |
| `sharia` | 16 | Saudi Financial Services | Sharia governance | zero tests; formal SME/regulatory validation |
| `signature` | 12 | Kernel | signatures and transactions | high-consequence authorization and non-repudiation assurance |
| `socops` | 101 | SOC Ops | operational cyber capabilities | package entitlement strongest; volume/retention and boundary testing |
| `sust` | 140 | ESG | ESG/GHG/finance/climate | very broad; large UX and complex calculation lineage |
| `tprm` | 43 | TPRM | vendor lifecycle | party duplication with SCM/outsourcing; portal and monitoring |
| `workflow` | 45 | Kernel | workflow, requests, approval chains | multiple approval abstractions; critical shared-service test depth |
| `workflow_submission` | 4 | Kernel | workflow submissions | clarify relation to requests and engine |

## Consolidation decisions required

1. `audit_eng` versus `audit_lc`;
2. `approvals` versus workflow approval chains versus committee decisions;
3. `tprm` versus `scm` versus `outsourcing` party/arrangement ownership;
4. `privacy` versus `data_officer` versus `dataclass`;
5. `risk`/`*_lc` duplicate lifecycle authority;
6. `incident` generic platform case versus domain incidents;
7. `capa` and findings/issues ownership;
8. dashboard/reporting read models versus direct peer queries.


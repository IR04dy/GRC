# 05 — UI Screen Requirements

Inventory of every page in `tss-grc-ui-v2/src/pages/`. Each page is
mounted as a `Shell` + `PageHeader` + tabbed body via the
`<Tabs>` component. Bilingual via `useI18n()` + `translations/{en,ar}.ts`.

## Pages (live)

| Route | EN | AR | File | Tabs | Notes |
|---|---|---|---|---|---|
| `/dashboard` | Executive Dashboard | لوحة التنفيذيين | `DashboardPage.tsx` | overview, KPIs, top risks, posture, alerts | Reads from `module/dashboard` |
| `/risk` | Risk Register | سجل المخاطر | `risk/RiskRegisterPage.tsx` | register, heatmap, kri, appetite, workflows, **alignment** (F5) | Detail at `/risk/:id` |
| `/cyber` | Cybersecurity | الأمن السيبراني | `cyber/CyberPage.tsx` | overview, controls, subcontrols, assessments, vuln, exceptions, incidents, assets, evidence, actions, workflows, comms, **alignment** (F5) | |
| `/bcm` | Business Continuity | استمرارية الأعمال | `bcm/BcmPage.tsx` | overview, plans, processes, bia, graph, exercises, comms, workflows, **alignment** (F5) | |
| `/privacy` | PDPL | حماية البيانات | `privacy/PrivacyPage.tsx` | overview, dsr, breaches, ropa, dpia, consent, workflows, comms, **alignment** (F5) | |
| `/tprm` | Third-Party Risk | إدارة مخاطر الأطراف الخارجية | `tprm/TprmPage.tsx` | overview, vendors, onboarding, reassessment, assessments, **questionnaires**, contracts, workflows, comms, **alignment** (F5) | Questionnaire engine V81 |
| `/policy-management` | Policy Management | إدارة السياسات | `policy/PolicyManagementPage.tsx` | overview, documents, exceptions, attestations | V79 |
| `/ndi` | NDI / نضيء | نظام النضج للبيانات | `ndi/NdiPage.tsx` | 14-tab cycle (Sprint 3) | |
| `/compliance` | Compliance | الامتثال | `compliance/CompliancePage.tsx` | self-assessments, findings | |
| `/compliance/posture` | **Compliance Posture Hub (F6)** | وضع الامتثال | `compliance/PosturePage.tsx` | heatmap + drilldown | New |
| `/regulatory/library` | Regulatory Library (F1) | مكتبة المرجعيات | `regulatory/RegulatoryLibraryPage.tsx` | family chips + citations tree | New |
| `/controls/library` | Control Library (F2) | مكتبة الضوابط | `controls/ControlLibraryPage.tsx` | controls + mappings + assessments | New |
| `/audit-workspace` | Audit Workspace | مساحة المراجعة | `audit/AuditWorkspacePage.tsx` | findings, engagements | |
| `/audit-lifecycle` | Audit Lifecycle | دورة المراجعة | `audit-lifecycle/AuditLifecyclePage.tsx` | engagement → finding → CAPA | |
| `/evidence` | Evidence Repository | مستودع الأدلة | `evidence/EvidencePage.tsx` | items + chain + requests | |
| `/evidence-chain` | Evidence Chain | سلسلة الأدلة | `evidence-chain/...` | chain-of-custody | |
| `/incidents` | Incidents | الحوادث | `incidents/IncidentsPage.tsx` | all-domain incident triage | |
| `/rca` | Root Cause Analysis | تحليل الجذور | `rca/...` | per-incident RCA | |
| `/crisis` | Crisis Room | غرفة الأزمات | `crisis/...` | declared crisis events | |
| `/dr-plans` | DR Plans | خطط التعافي | `dr-plans/...` | DRPs | |
| `/scm` | Supply Chain | سلاسل الإمداد | `scm/ScmPage.tsx` | suppliers, dependencies, exit | |
| `/bcm-dependency` | BCM Dependency Graph | شجرة الترابط | `bcm-dependency/...` | interactive graph | |
| `/risk-scenarios` | Risk Scenarios | السيناريوهات | `risk-scenarios/...` | scenario planning | |
| `/emerging-risk` | Emerging Risk | المخاطر الناشئة | `emerging-risk/...` | horizon scanning | |
| `/committees` | Committees | اللجان | `committees/...` | committee + meetings | |
| `/board-pack` | Board Pack | حزمة المجلس | `board-pack/...` | board reporting | |
| `/data-sharing` | Data Sharing | مشاركة البيانات | `data-sharing/...` | PDPL sharing |  |
| `/data-lifecycle` | Data Lifecycle | دورة حياة البيانات | `pdpl-extensions/...` | retention + disposal | |
| `/regulatory-change` | Regulatory Change | التحديثات التنظيمية | `regulatory-change/...` | reg-change watch | |
| `/framework-controls` | Framework Controls | الضوابط حسب الإطار | `framework-controls/...` | per-framework table | |
| `/framework-packs` | Framework Packs | حزم الأطر | `framework-packs/...` | downloadable packs | |
| `/policy-templates` | Policy Templates | قوالب السياسات | `policy-templates/...` | template library | |
| `/control-crosswalk` | Control Crosswalk | المطابقة بين الضوابط | `control-crosswalk/...` | manual cross-mapping | superseded by F2 |
| `/attestations` | Attestation Campaigns | حملات الإقرار | `attestations/...` | campaigns | |
| `/ethics` | Ethics | الأخلاقيات | `ethics/...` | case mgmt | |
| `/esg` | ESG | الاستدامة | `esg/...` | disclosures + GHG | |
| `/nca-ecc-export` | NCA ECC Filing | تقديم ECC للهيئة الوطنية | `nca-ecc/...` | annual self-assessment | |
| `/authority` + `/authority/:code` | Authorities directory | دليل الجهات | `authority/...` | authority workspaces | superseded by F1 catalog |
| `/admin` | Admin | الإدارة | `admin/...` | users, roles, perms | |
| `/admin/audit-log` | Audit Log | سجل التدقيق | `admin/...` | workflow + F7 unified |
| `/knowledge-base` | Knowledge Base | قاعدة المعرفة | `knowledge-base/...` | articles | |
| `/intake` | Intake | البوابة | `intake/...` | record promotion | |
| `/login` | Login | تسجيل الدخول | `LoginPage.tsx` | — | |
| `/settings` | Settings | الإعدادات | `SettingsPage.tsx` | per-user settings | |

## Pattern: per-page anatomy

```jsx
<Shell title="…" sub="…">
  <PageHeader eyebrow="…" title="…" />
  <Tabs items={tabs} active={tab} onChange={setTab} />
  {tab === '…' && <…Tab />}
  {tab === 'alignment' && <RegulatoryAlignmentTab domain="…" families={[…]} />}
  <FormModal …/>     {/* shared */}
  <ConfirmDialog …/> {/* shared */}
</Shell>
```

## Validation rules

- All required-field validation runs in the service layer + DTO
  bean-validation annotations. Frontend mirrors with HTML5
  `required` + the `Form` component's built-in check.
- Workflow transitions are enforced server-side; the UI hides
  buttons that would 400.

## Role-based UI visibility

The sidebar reads the JWT `enabled_modules` claim (F9) and hides
unlicensed entries. Within a page, action buttons check the user's
roles via the `hasPermission(...)` helper.

## Mobile / responsive

`Shell.tsx` collapses the sidebar to a hamburger on < 980 px.
Tables on narrow screens shift to a card list via the
`MobileTableFallback` component pattern. Tested across iPad, common
Android tablets, and modern desktops.

---

**See also:** `05_Navigation_Menu.md`, `05_RTL_Localization_Requirements.md`.

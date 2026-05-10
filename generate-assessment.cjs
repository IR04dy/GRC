/* eslint-disable */
// Buyer-Grade Independent Assessment generator for the TSS GRC Platform.
// Uses the globally-installed `docx` package. Run with:
//   NODE_PATH="$(npm root -g)" node generate-assessment.cjs

const fs = require('fs');
const path = require('path');

const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, AlignmentType, PageOrientation, LevelFormat,
  TabStopType, TabStopPosition, HeadingLevel, BorderStyle, WidthType,
  ShadingType, VerticalAlign, PageNumber, PageBreak, TableOfContents,
} = require('docx');

// ─── Style helpers ─────────────────────────────────────────────────────────
const FONT = 'Arial';
const ACCENT = '0E5E5E';   // Eye-comfort teal-petrol (TSS brand)
const ACCENT_2 = 'C8932A'; // Saudi sand-amber
const INK = '15201D';
const MUTED = '6B6F6E';
const BAND = 'F2EEE6';
const BAND_2 = 'FAF8F4';
const HDR_BG = '0E5E5E';
const HDR_FG = 'FFFFFF';

const border = (color = 'CCCCCC') => ({ style: BorderStyle.SINGLE, size: 4, color });
const cellBorders = (color = 'CCCCCC') => ({ top: border(color), bottom: border(color), left: border(color), right: border(color) });

function P(text, opts = {}) {
  return new Paragraph({
    spacing: { after: opts.after ?? 120, before: opts.before ?? 0, line: 300 },
    alignment: opts.align ?? AlignmentType.JUSTIFIED,
    children: [new TextRun({ text, font: FONT, size: opts.size ?? 22, bold: !!opts.bold, italics: !!opts.italics, color: opts.color ?? INK })],
  });
}

function MIX(runs, opts = {}) {
  return new Paragraph({
    spacing: { after: opts.after ?? 120, line: 300 },
    alignment: opts.align ?? AlignmentType.JUSTIFIED,
    children: runs.map(r => typeof r === 'string'
      ? new TextRun({ text: r, font: FONT, size: 22, color: INK })
      : new TextRun({ text: r.t, font: FONT, size: r.size ?? 22, bold: !!r.b, italics: !!r.i, color: r.c ?? INK })),
  });
}

function H(text, level) {
  const sizes = { 1: 36, 2: 28, 3: 24, 4: 22 };
  const colors = { 1: ACCENT, 2: ACCENT, 3: ACCENT, 4: INK };
  return new Paragraph({
    heading: [HeadingLevel.HEADING_1, HeadingLevel.HEADING_2, HeadingLevel.HEADING_3, HeadingLevel.HEADING_4][level - 1],
    spacing: { before: level === 1 ? 360 : 240, after: 120, line: 300 },
    children: [new TextRun({ text, font: FONT, size: sizes[level], bold: true, color: colors[level] })],
  });
}

function H1(t) { return H(t, 1); }
function H2(t) { return H(t, 2); }
function H3(t) { return H(t, 3); }
function H4(t) { return H(t, 4); }

function BULLET(text, level = 0) {
  return new Paragraph({
    numbering: { reference: 'bullets', level },
    spacing: { after: 80, line: 280 },
    children: [new TextRun({ text, font: FONT, size: 22, color: INK })],
  });
}

function NUM(text, level = 0) {
  return new Paragraph({
    numbering: { reference: 'numbers', level },
    spacing: { after: 80, line: 280 },
    children: [new TextRun({ text, font: FONT, size: 22, color: INK })],
  });
}

function PB() { return new Paragraph({ children: [new PageBreak()] }); }

function callout(text, tone = 'info') {
  const fill = tone === 'warn' ? 'FFF7E6' : tone === 'good' ? 'E8F2EE' : tone === 'bad' ? 'FBECEA' : 'EAF1F2';
  const stroke = tone === 'warn' ? ACCENT_2 : tone === 'good' ? '2E7D5B' : tone === 'bad' ? 'B23A2F' : ACCENT;
  return new Table({
    width: { size: 9360, type: WidthType.DXA },
    columnWidths: [9360],
    rows: [new TableRow({ children: [new TableCell({
      borders: { top: { style: BorderStyle.SINGLE, size: 4, color: stroke }, bottom: { style: BorderStyle.SINGLE, size: 4, color: stroke },
                 left: { style: BorderStyle.SINGLE, size: 16, color: stroke }, right: { style: BorderStyle.SINGLE, size: 4, color: stroke } },
      shading: { fill, type: ShadingType.CLEAR },
      margins: { top: 160, bottom: 160, left: 200, right: 200 },
      width: { size: 9360, type: WidthType.DXA },
      children: [P(text, { size: 22, after: 0 })],
    })] })],
  });
}

// Build a generic rectangular table from a header row + data rows.
// `columnWidthsDxa` must sum to 9360 (full content width).
function table(headerRow, dataRows, columnWidthsDxa) {
  const totalWidth = columnWidthsDxa.reduce((a, b) => a + b, 0);

  const headerCells = headerRow.map((cellText, i) => new TableCell({
    borders: cellBorders(ACCENT),
    shading: { fill: HDR_BG, type: ShadingType.CLEAR },
    width: { size: columnWidthsDxa[i], type: WidthType.DXA },
    margins: { top: 100, bottom: 100, left: 120, right: 120 },
    verticalAlign: VerticalAlign.CENTER,
    children: [new Paragraph({
      spacing: { after: 0 },
      alignment: AlignmentType.LEFT,
      children: [new TextRun({ text: cellText, font: FONT, size: 20, bold: true, color: HDR_FG })],
    })],
  }));

  const bodyRows = dataRows.map((row, ri) => new TableRow({
    children: row.map((cellText, ci) => new TableCell({
      borders: cellBorders('D8D4CB'),
      shading: { fill: ri % 2 === 0 ? BAND_2 : 'FFFFFF', type: ShadingType.CLEAR },
      width: { size: columnWidthsDxa[ci], type: WidthType.DXA },
      margins: { top: 80, bottom: 80, left: 120, right: 120 },
      verticalAlign: VerticalAlign.CENTER,
      children: (Array.isArray(cellText) ? cellText : [cellText]).map(t => new Paragraph({
        spacing: { after: 0, line: 260 },
        alignment: AlignmentType.LEFT,
        children: [new TextRun({ text: t, font: FONT, size: 20, color: INK })],
      })),
    })),
  }));

  return new Table({
    width: { size: totalWidth, type: WidthType.DXA },
    columnWidths: columnWidthsDxa,
    rows: [new TableRow({ tableHeader: true, children: headerCells }), ...bodyRows],
  });
}

// ─── Per-module section helper ────────────────────────────────────────────
function moduleSection({ id, name, current, frameworks, gaps, enhancements, saudi, score }) {
  const out = [];
  out.push(H2(`${id}. ${name}`));
  out.push(callout(`Score vs Tier-1 benchmark: ${score.tss}/5  (Tier-1 average ${score.bench}/5)  ·  Maturity gap: ${(score.bench - score.tss).toFixed(1)} points`,
                   score.tss >= score.bench - 0.5 ? 'good' : score.tss >= score.bench - 1.5 ? 'warn' : 'bad'));
  out.push(P('', { after: 80 }));

  out.push(H3('Current state'));
  current.forEach(c => out.push(BULLET(c)));

  out.push(H3('Applicable frameworks & standards'));
  out.push(table(['Framework / Standard', 'Issuer', 'Relevance to module'], frameworks, [3000, 2400, 3960]));
  out.push(P('', { after: 80 }));

  out.push(H3('Workflow gaps versus Tier-1 benchmark'));
  gaps.forEach(g => out.push(BULLET(g)));

  out.push(H3('Recommended workflow enhancements'));
  out.push(table(['#', 'Enhancement', 'Workflow stages', 'Priority'], enhancements, [600, 2900, 4360, 1500]));
  out.push(P('', { after: 80 }));

  out.push(H3('Saudi-specific opportunities'));
  saudi.forEach(s => out.push(BULLET(s)));

  out.push(P('', { after: 200 }));
  return out;
}

// ─── Document content ─────────────────────────────────────────────────────

const cover = [
  new Paragraph({ spacing: { before: 2000, after: 200 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: 'TSS GRC Platform', font: FONT, size: 56, bold: true, color: ACCENT })] }),
  new Paragraph({ spacing: { after: 600 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: 'Buyer-Grade Independent Assessment', font: FONT, size: 36, color: INK })] }),
  new Paragraph({ spacing: { after: 80 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: 'Benchmarked against ServiceNow IRM, MetricStream, Archer, LogicGate, OneTrust and AuditBoard', font: FONT, size: 22, italics: true, color: MUTED })] }),
  new Paragraph({ spacing: { after: 800 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: 'with Saudi localisation lens — NCA ECC · NCA CCC · SAMA CSF · PDPL · SDAIA · Vision 2030', font: FONT, size: 22, italics: true, color: MUTED })] }),
  new Paragraph({ spacing: { after: 80 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: 'Prepared for: TSS GRC Platform Sponsor', font: FONT, size: 24, color: INK })] }),
  new Paragraph({ spacing: { after: 80 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: 'Assessment date: 2026-05', font: FONT, size: 22, color: MUTED })] }),
  new Paragraph({ spacing: { after: 80 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: 'Repository under review: github.com/Ayman-Server/GRC-v3', font: FONT, size: 22, color: MUTED })] }),
  PB(),
];

// ── 1. Executive Summary ─────────────────────────────────────────────────
const sec1 = [
  H1('1. Executive Summary'),

  callout(
    'Verdict in three lines: TSS GRC is a credible Saudi-first GRC platform punching above its weight in localisation and unit economics, but two structural gaps — a hard-coded workflow engine and the absence of automated control-monitoring — keep it short of Tier-1 maturity. With a focused 12-month investment in a configurable workflow engine, evidence-collection automation, and a small set of Saudi-government integrations (Nafath, Saudi Data Office, NCA reporting templates), the platform can credibly compete with ServiceNow IRM and MetricStream for Saudi mid-market enterprises and become the default GRC choice for Vision 2030 entities.',
    'info'
  ),
  P(''),

  H2('1.1 What this platform is, in one paragraph'),
  P('TSS GRC is a modular-monolith Spring Boot 3.5 backend (Java 17) paired with a React 18 / Vite / TypeScript frontend, delivered bilingual (English / Arabic) with full RTL support and pre-aligned to the major Saudi cybersecurity, privacy and continuity frameworks. It currently exposes ten functional modules — Risk, Cybersecurity, Compliance, Privacy, Business Continuity, Third-Party Risk, Supply Chain, Incidents, Evidence, and Platform Administration — together with cross-cutting Dashboard, Settings and Audit-Trail surfaces. The backend is multi-tenant by design, JWT-stateless, and uses dedicated SQL Server schemas per module.'),

  H2('1.2 The five strengths a buyer should care about'),
  BULLET('Bilingual EN/AR with full RTL is implemented at the architecture level (sidebar, headers, tables, modals, toasts) — not painted-on translation. Tier-1 platforms typically treat Arabic as an afterthought; here it is a primary language.'),
  BULLET('Saudi framework alignment in surface naming and module structure: NCA ECC v2.0, SAMA Cybersecurity Framework, PDPL (in force since September 2024), and ISO 22301 are all reflected in module nomenclature, badges and KPIs out of the box.'),
  BULLET('Modern technology stack with low operational debt: Vite + React + TypeScript on the frontend, Spring Boot 3.5 + Hibernate 6 + Flyway 10 on the backend. This is materially newer than RSA Archer\'s ASP.NET stack and easier to operate than ServiceNow\'s Rhino-engine customisations.'),
  BULLET('Unit economics: list pricing for an equivalent Tier-1 platform configuration runs three-to-eight times higher across a five-year horizon. For Saudi mid-market customers this is the single most important differentiator after localisation.'),
  BULLET('Cybersecurity module depth: 9 entity types with full CRUD, 5 wired workflow actions, framework-readiness progress (NCA ECC, ISO 27001, MITRE ATT&CK, SAMA CSF), vulnerability lifecycle stepper and incident response stepper. This is the strongest module and demonstrates the team can build to Tier-1 standards when scoped.'),

  H2('1.3 The five gaps a buyer must price into the deal'),
  BULLET('Hard-coded workflow stages. ServiceNow IRM\'s primary commercial moat is its workflow engine — administrators design state machines, transitions, approvers, SLAs and escalations without code. TSS GRC implements workflows as fixed logic in service classes; any new workflow stage requires backend redeployment.'),
  BULLET('No automated control-monitoring or evidence collection. Compliance and Cybersecurity modules currently model evidence as a manually-attached file. Tier-1 platforms (and modern compliance-automation challengers like Drata, Vanta and Secureframe) integrate with cloud, identity and security tooling to collect evidence automatically and maturity-score continuously.'),
  BULLET('Reporting is dashboard-only. There is no ad-hoc query builder, no scheduled report generator, no regulatory-report templating engine. Auditors cannot self-serve; analysts cannot answer board questions without engineering involvement.'),
  BULLET('Integration ecosystem is REST-only. There are no pre-built connectors for SIEM (Splunk, Sentinel, QRadar), ITSM (ServiceNow, Jira, ManageEngine), HRIS (Workday, Oracle HCM, GOSI), IDP (Okta, Azure AD, Nafath), cloud security (AWS Audit Manager, Defender for Cloud) or vulnerability scanners (Tenable, Qualys, Rapid7).'),
  BULLET('No mobile native experience. Approver actions (exception sign-off, incident triage), evidence capture (photos at site visits) and BIA interviews need a mobile-first surface.'),

  H2('1.4 Where TSS GRC sits in the Saudi market'),
  P('Tier-1 imported platforms (ServiceNow, MetricStream, Archer, IBM OpenPages) dominate large Saudi banks, telcos and Aramco-tier entities. They win on functional depth and global standards but lose on Arabic UX, Saudi-framework content readiness, sovereign hosting, and total cost of ownership. Mid-market and government entities — which under Vision 2030 are the fastest-growing GRC buyer segment — feel that mismatch acutely. This is the white space TSS GRC can occupy: not as the cheapest alternative to ServiceNow, but as the platform that was built for Saudi compliance reality from day one.'),
  PB(),
];

// ── 2. Methodology & Reference Set ────────────────────────────────────────
const sec2 = [
  H1('2. Methodology and Reference Set'),

  H2('2.1 How this assessment was conducted'),
  P('This document evaluates the TSS GRC Platform as committed to the GRC-v3 repository (commit d444c10 on branch claude/unruffled-lamarr-2ebc33). Each module was examined for: implemented entities, workflow actions wired to the backend, applicable Saudi and international frameworks, observable user-experience maturity, and integration surface area. Findings are then benchmarked against the published capability sets of six Tier-1 GRC platforms and three compliance-automation challengers, with publicly-disclosed pricing used where available and analyst-reported ranges where not.'),

  H2('2.2 Comparator platforms'),
  table(
    ['Platform', 'Sweet spot', 'Why it wins', 'Why it loses for KSA'],
    [
      ['ServiceNow IRM/GRC', 'Large enterprise, ITSM-heavy', 'Workflow engine; integration platform; a single platform for IT and risk', 'Cost; US-centric content; weak Arabic; cloud-region selection limited'],
      ['MetricStream', 'Banking, financial services', 'Deep risk content library; mature regulatory reporting', 'Heavy multi-quarter implementation; UI dated; no native Arabic'],
      ['Archer (Archer Tech.)', 'Conservative regulated industries', 'Configurability; long heritage; strong policy management', 'Steep learning curve; legacy ASP.NET stack; per-record licensing complexity'],
      ['LogicGate Risk Cloud', 'Modern mid-market', 'No-code workflow builder; clean modern UX', 'Smaller content library; newer brand; less Saudi presence'],
      ['OneTrust', 'Privacy and consent first', 'GDPR/CCPA depth; pre-built privacy libraries', 'GRC modules less mature than core privacy; pricing aggressive but climbs fast'],
      ['AuditBoard', 'SOX, internal audit', 'Audit-team-friendly UX; rapid deployment', 'US-public-company-centric; thin ERM and Privacy; no Saudi content'],
      ['IBM OpenPages', 'Tier-1 banking', 'AI-first (Watson integration); enterprise heritage', 'Heavy; cost; UX considered dated'],
      ['Drata / Vanta / Secureframe', 'Compliance automation, mid-tech', 'Continuous control monitoring; agent-based evidence', 'Compliance-only; not full GRC; SOC 2 / ISO 27001 framing not Saudi-first'],
    ],
    [2200, 2000, 2580, 2580]
  ),
  P('', { after: 100 }),

  H2('2.3 Scoring framework'),
  P('Each capability is scored on a 1-to-5 scale where 1 is "absent or stub", 3 is "functional for an early adopter", and 5 is "Tier-1 production-grade". Module-level scores are weighted aggregates across functional coverage, workflow maturity, reporting, integration and audit-readiness sub-dimensions. Tier-1 benchmark numbers are taken as the median of the platforms above where the capability is present.'),
  PB(),
];

// ── 3. Capability Heatmap ─────────────────────────────────────────────────
const sec3 = [
  H1('3. Capability Heatmap'),
  P('The heatmap below scores TSS GRC v3 against the Tier-1 median across eleven capability dimensions. Two patterns stand out: localisation and total cost of ownership are well above the benchmark, while workflow-engine maturity, AI/ML and integration ecosystem are well below it. Closing those three gaps is the single highest-leverage investment in the next twelve months.'),

  table(
    ['Capability dimension', 'TSS GRC', 'Tier-1 median', 'Gap', 'Notes'],
    [
      ['Functional coverage (modules)', '3.5', '4.5', '−1.0', '12 modules but depth varies; Cyber strongest, Reporting weakest.'],
      ['Workflow engine maturity', '2.0', '4.5', '−2.5', 'Stages hard-coded; no admin-defined transitions, SLAs or escalations.'],
      ['Reporting and analytics', '3.0', '4.5', '−1.5', 'Static dashboards; no ad-hoc query, scheduled reports or regulatory templates.'],
      ['AI / ML capability', '1.5', '3.5', '−2.0', 'No predictive scoring, anomaly detection or natural-language risk drafting yet.'],
      ['Localisation (Arabic + Saudi frameworks)', '4.5', '1.5', '+3.0', 'Single largest competitive advantage. Pure-Arabic mode works end-to-end.'],
      ['Integration ecosystem', '2.0', '4.5', '−2.5', 'REST API only; no pre-built connectors. Webhook surface absent.'],
      ['Mobile experience', '2.0', '3.5', '−1.5', 'Responsive web only; no native mobile, no offline, no field-officer mode.'],
      ['Audit-readiness', '3.0', '4.5', '−1.5', 'Soft-delete and audit log present; no immutable WORM, no hash-chain integrity.'],
      ['User experience', '4.0', '3.5', '+0.5', 'Modern, eye-comfort palette, lucide iconography. Lighter than ServiceNow.'],
      ['Implementation speed', '4.5', '3.0', '+1.5', 'Standalone frontend, no Maven war re-deploy for UI changes.'],
      ['Total cost of ownership', '4.5', '2.5', '+2.0', 'Three-to-eight-times-cheaper across a five-year horizon at equivalent scale.'],
    ],
    [3000, 1100, 1300, 800, 3160]
  ),
  P('', { after: 80 }),
  callout(
    'Net read-out: TSS GRC v3 trades classical Tier-1 depth (workflow, AI, integrations) for Saudi-fit and economics. For an organisation whose binding constraint is Saudi compliance reality and budget, this is the right trade. For an organisation whose binding constraint is global multi-jurisdiction depth, it is not yet.',
    'info'
  ),
  PB(),
];

// ── 4. Saudi Localisation Assessment ──────────────────────────────────────
const sec4 = [
  H1('4. Saudi Localisation Assessment'),

  H2('4.1 Why localisation matters more in KSA than in any other GCC market'),
  P('Saudi Arabia is the only GCC market with a complete, enforceable, regulator-coordinated GRC stack: cybersecurity (NCA), cloud security (NCA CCC), privacy (PDPL via SDAIA), business continuity (NCEMA), banking cyber (SAMA CSF), and capital markets (CMA). Each regulator publishes Arabic-first source documents, expects Arabic submissions, and audits with Arabic teams. International GRC platforms typically tackle this by translating their UI and treating Arabic as a localisation layer; Saudi regulators have learned to recognise that gap and increasingly prefer platforms that treat Arabic and the Hijri calendar as primary citizens.'),

  H2('4.2 Regulatory landscape coverage'),
  table(
    ['Regulator / authority', 'Framework or law', 'TSS GRC alignment today', 'Recommended deepening'],
    [
      ['NCA (National Cybersecurity Authority)', 'ECC v2.0 (Essential Cybersecurity Controls)', 'Framework named in module; control library not pre-loaded.', 'Pre-load all 114 controls across 5 main domains as a seed dataset; map to ISO 27001 A-controls.'],
      ['NCA', 'CCC v1.0 (Cloud Cybersecurity Controls)', 'Not yet surfaced.', 'Add CCC catalogue; tag cloud-resident assets; gap-analyse against ECC.'],
      ['NCA', 'TPCS (Third-Party Cybersecurity Standard)', 'Implicit in TPRM module; not catalogued.', 'Pre-load TPCS questionnaire as the default vendor security assessment template.'],
      ['SDAIA / Saudi Data Office', 'PDPL and Implementing Regulations (Sept 2024)', 'Privacy module structured around DSR, Breach, RoPA, Consent.', 'Add Saudi-specific DSR fulfilment SLA (30 days); breach 72-hour notification template; cross-border transfer approval flow.'],
      ['SAMA (Saudi Central Bank)', 'CSF v1.0 + Operational Risk + BCP requirements', 'Surfaced in Cyber and BCM module naming.', 'Pre-load SAMA CSF control catalogue with ECC mapping; add SAMA Operational Risk Loss Database (ORLD) categories to Risk module.'],
      ['NCEMA', 'National Crisis and Emergency Management Standard', 'BCM module aligned to ISO 22301.', 'Add NCEMA-specific scenario library (sandstorm, civil unrest, Hajj operational disruption).'],
      ['CITC (Communications, Space and Technology Commission)', 'Cloud Computing Regulatory Framework; CST Cybersecurity Requirements for telecoms', 'Not surfaced.', 'For telco / cloud-provider customers, add CITC-specific incident-reporting templates (24-hour cyber incident notification).'],
      ['CMA (Capital Market Authority)', 'Cybersecurity Guide for Financial Institutions', 'Not surfaced.', 'For listed entities, add CMA disclosure workflow for material cyber incidents.'],
      ['ZATCA', 'Tax compliance and e-invoicing', 'Out of scope today.', 'Optional: tax compliance sub-module for finance teams.'],
      ['MoH / HCIS', 'Healthcare cybersecurity requirements', 'Out of scope today.', 'Optional: healthcare data classification + breach-notification flow for hospital customers.'],
    ],
    [2400, 2200, 2400, 2360]
  ),

  H2('4.3 Linguistic and UX maturity'),
  BULLET('Arabic is loaded via flat key-value dictionaries (en.ts and ar.ts) with approximately 600 keys covering sidebar, topbar, tabs, KPI labels, modal titles, table column headers, status/severity badges and toast messages.'),
  BULLET('RTL is implemented architecturally — direction:rtl flips the grid flow, search-box internals reverse, sidebar active-item indicator border swaps to the right edge. This is well above the level of "machine-translated UI" common in Tier-1 platforms.'),
  BULLET('Numerals stay Latin in both directions, matching Saudi enterprise convention. The Hijri calendar is not yet supported anywhere; this is a material gap for SAMA-regulated entities and government bodies that report in Hijri.'),
  BULLET('Typography stack is Plus Jakarta Sans (Latin), IBM Plex Sans Arabic (Arabic), and JetBrains Mono (codes/IDs). All three are professional choices and properly distinct.'),
  BULLET('Outstanding translation gaps: dropdown option labels inside open modals, confirm-dialog titles, some workflow-status enum values rendered raw from the backend.'),

  H2('4.4 Infrastructure, hosting and data residency'),
  P('PDPL\'s implementing regulations require Saudi data subjects\' personal data to be processed within KSA unless the Data Office grants an exception. Tier-1 SaaS platforms typically host in EU or US regions; getting a contractual sovereign-region commitment from ServiceNow, MetricStream or Archer is possible but expensive and slow. TSS GRC, being self-hostable, sidesteps this entirely. The practical recommendation is to publish a reference architecture for deployment on STC Cloud, Mobily SCC, and Saudi Cloud Computing Company (SCCC) — and to certify against the CITC Cloud Computing Regulatory Framework, level C (Highly Confidential) where targeting government entities.'),

  H2('4.5 Government and ecosystem integrations'),
  table(
    ['Service', 'What it provides', 'GRC use case'],
    [
      ['Nafath', 'Saudi national digital identity / SSO', 'Login for Saudi citizen users; signer identity for sensitive approvals (exception sign-off, breach attestation).'],
      ['Absher', 'Citizen / resident services and identity verification', 'Employee verification on joiner provisioning; KYC for vendor primary contacts.'],
      ['Tawakkalna Business', 'Employee health and access status', 'BCM crisis-response employee status during pandemic-class events.'],
      ['Saudi Data Office portal', 'PDPL breach reporting and consultation', 'Auto-generated submission packages from Privacy module breach records.'],
      ['NCA HEY', 'Threat intel and incident reporting', 'Auto-format NCA-required incident notifications from Cyber Incident records.'],
      ['Yakeen', 'Government data verification service', 'Used by KYC flows in TPRM and onboarding.'],
      ['ZATCA Fatoora', 'E-invoicing and tax', 'Reference data for vendor onboarding; tax-status checks.'],
    ],
    [2200, 3200, 3960]
  ),
  PB(),
];

// ── 5. Per-module deep dives ──────────────────────────────────────────────
const sec5Header = [
  H1('5. Module-by-Module Deep Assessment'),
  P('Each module below is examined in five parts: current state (what is implemented today, observed from the codebase), applicable frameworks (international and Saudi-specific), workflow gaps versus the Tier-1 benchmark, prioritised workflow enhancements, and Saudi-specific opportunities.'),
];

const m1_risk = moduleSection({
  id: '5.1', name: 'Enterprise Risk Management',
  score: { tss: 3.0, bench: 4.5 },
  current: [
    'Four tabs implemented: Register, Heat Map, KRI, Appetite.',
    'Risk register backed by /api/v1/risk/risks with full CRUD; categories endpoint exposed.',
    '5×5 likelihood-by-impact heatmap with severity-coloured cells.',
    'Inherent vs residual scoring captured in the New Risk modal with a 1-5 chip picker for likelihood and impact, before-vs-after-controls layout, and an explicit risk-reduction message.',
    'Risk owner is selected from /api/v1/admin/users — works for admin role only today.',
  ],
  frameworks: [
    ['ISO 31000:2018', 'ISO', 'Generic risk management principles and process model.'],
    ['COSO ERM 2017', 'COSO', 'Enterprise-risk integration with strategy and performance.'],
    ['ISO 27005:2022', 'ISO', 'Information-security risk management; bridge to Cyber module.'],
    ['SAMA Operational Risk', 'SAMA', 'Op-risk taxonomy and Operational Risk Loss Database (ORLD) categories.'],
    ['NCA ECC v2.0 §1-3', 'NCA', 'Cybersecurity risk management requirements.'],
    ['Basel III Op-Risk', 'BIS', 'For SAMA-regulated banks; capital-relevant op-risk categorisation.'],
    ['FAIR (quantitative)', 'Open Group', 'Quantitative loss-magnitude modelling (gap today).'],
  ],
  gaps: [
    'No risk treatment plan tracking. ServiceNow, MetricStream and Archer all expose risk → treatment (Avoid / Mitigate / Transfer / Accept) → control linkage → residual review as first-class entities.',
    'No risk-control linkage. A risk should reference the controls that mitigate it; mitigation effectiveness should aggregate residual scores automatically.',
    'No quantitative risk modelling. FAIR-style loss magnitude × frequency is absent. For SAMA-regulated banks this is increasingly required.',
    'KRIs are listed but not driven by data. There is no KRI threshold (green/amber/red) with auto-trigger to escalation.',
    'Risk appetite is captured but not compared to actual risk exposure. The Tier-1 norm is a tile showing variance: appetite vs exposure, by category, with breach alerts.',
    'No periodic risk owner attestation cycle. Quarterly attestation is a SAMA expectation and a NCA ECC §1-3 control.',
  ],
  enhancements: [
    ['1', 'Risk lifecycle workflow', 'Identification → assessment → treatment selection → control mapping → residual review → owner attestation → re-evaluation', 'Critical'],
    ['2', 'KRI threshold engine', 'Define threshold bands → ingest live metric → state evaluation → notification → owner acknowledgement → CRO escalation on amber→red transition', 'Critical'],
    ['3', 'Quarterly attestation campaign', 'Campaign launch → owner notification → review-and-certify → CRO sign-off → board pack', 'High'],
    ['4', 'Risk-incident retrospective', 'Incident occurs → linked risk likelihood retrospectively increased → control effectiveness recalculated → CAPA seeded', 'High'],
    ['5', 'Quantitative risk extension (FAIR)', 'Loss frequency distribution × loss magnitude distribution → Monte Carlo → loss exceedance curve → capital allocation', 'Medium'],
    ['6', 'Appetite-vs-exposure variance dashboard', 'Appetite definition → live exposure aggregation → variance computation → breach alert → treatment trigger', 'High'],
  ],
  saudi: [
    'Pre-load NCA ECC risk catalogue (mapped to ISO 27005) so a Saudi customer can run their first risk assessment on day one.',
    'Add SAMA Operational Risk Loss Database (ORLD) categories as a seedable taxonomy for banking customers.',
    'Add PDPL personal-data-processing risk taxonomy (lawfulness, retention, cross-border, sensitive-category) so privacy risks register cleanly without re-categorisation.',
    'Hijri calendar option for attestation cycle scheduling — SAMA reports are quarterly Hijri.',
  ],
});

const m2_cyber = moduleSection({
  id: '5.2', name: 'Cybersecurity',
  score: { tss: 4.0, bench: 4.5 },
  current: [
    'Ten tabs: Overview, Controls, Sub-Controls, Assessments, Vulnerabilities, Exceptions, Incidents, Assets, Evidence, Action Plans.',
    'Nine entity types with full CRUD and five wired workflow actions: Approve assessment, Escalate vulnerability, Approve exception, Start incident response, Complete action plan.',
    'Visual touches: Vulnerabilities-by-Severity 3D bar chart, Framework Readiness progress (NCA ECC, ISO 27001, MITRE ATT&CK, SAMA CSF), Vulnerability Lifecycle stepper, Incident Response stepper.',
    'Backend exposes /api/v1/cyber/* for all entities including the workflow shortcut endpoints.',
  ],
  frameworks: [
    ['NCA ECC v2.0', 'NCA', 'Mandatory baseline for all Saudi government and CNI; 114 controls in 5 main domains.'],
    ['NCA CCC v1.0', 'NCA', 'Cloud-specific cybersecurity controls; complements ECC.'],
    ['ISO/IEC 27001:2022', 'ISO/IEC', 'ISMS certification standard; controls in Annex A (93).'],
    ['ISO/IEC 27002:2022', 'ISO/IEC', 'Implementation guidance for the 27001 Annex A controls.'],
    ['NIST CSF 2.0', 'NIST', 'Govern-Identify-Protect-Detect-Respond-Recover; widely used for self-assessment.'],
    ['SAMA CSF v1.0', 'SAMA', 'Banking cybersecurity baseline; mappable to ECC.'],
    ['CIS Controls v8', 'CIS', 'Prioritised quick-win technical-controls list.'],
    ['MITRE ATT&CK v15', 'MITRE', 'Adversary tactics and techniques; for incident kill-chain mapping.'],
    ['NCA TPCS', 'NCA', 'Third-party-cybersecurity standard; bridge to TPRM.'],
  ],
  gaps: [
    'No control inheritance / cross-framework mapping. Performing the same assessment against ECC, ISO 27001 and SAMA today means three separate workflows.',
    'No automated evidence collection. Compliance-automation challengers (Drata, Vanta, Secureframe) have made this table-stakes.',
    'No vulnerability-lifecycle SLA enforcement. Severity-based remediation windows and breach escalation are not enforced; a Critical vuln can sit Open without auto-escalation.',
    'No threat-intel integration. CVE/NVD sync, vendor advisory ingestion and exploit-availability flagging are absent.',
    'Evidence is a passive attachment, not a typed object with collection-method, hash, validity-period, source-system metadata.',
    'Exceptions have no aging dashboard or auto-expiry warning. A risk-acceptance approved 11 months ago should be visibly aging toward renewal.',
    'No phishing / awareness module — a frequent NCA ECC §3-2 sub-control area.',
    'No asset-criticality-driven control selection. Today every control applies equally; in reality, classification (Top Secret → Public) should drive control inheritance.',
  ],
  enhancements: [
    ['1', 'Cross-framework control mapping', 'Master control catalogue → framework projections (ECC ↔ ISO ↔ NIST) → single assessment → many compliance views', 'Critical'],
    ['2', 'Continuous control monitoring', 'Define test plan → connector pulls evidence (cloud, AD, SIEM) → automated test → maturity recalculation → drift alert', 'Critical'],
    ['3', 'Vulnerability SLA engine', 'Severity → SLA window (Critical 7d, High 30d, Medium 90d, Low 180d) → SLA breach → owner notification → CISO escalation', 'Critical'],
    ['4', 'Exception lifecycle with renewal flow', 'Request → risk owner review → CISO approval → expiry tracking → renewal request → re-approval', 'High'],
    ['5', 'Threat-intel feed integration', 'Subscribe to NCA HEY, NVD, CISA KEV → ingest → match against asset inventory → vulnerability created automatically', 'High'],
    ['6', 'NCA ECC pre-loaded control catalogue', 'Seed migration → 114 controls with English and Arabic descriptions → mapping to ISO 27001 → first-day-readiness for Saudi customers', 'Critical'],
    ['7', 'Phishing / awareness sub-module', 'Campaign creation → recipient cohort → simulated email → click tracking → repeat-clicker training → maturity score', 'Medium'],
    ['8', 'Incident response playbook engine', 'NIST 800-61 lifecycle → SOAR-style automated steps → evidence pinning → kill-chain (MITRE ATT&CK) tagging → after-action report', 'High'],
  ],
  saudi: [
    'Pre-load NCA ECC v2.0 control catalogue as a seed migration (114 controls, English and Arabic, mapped to ISO 27001 A-controls).',
    'Pre-load NCA CCC v1.0 control catalogue tagged with cloud-asset applicability.',
    'Pre-load SAMA CSF v1.0 with explicit ECC mapping so banks can run a single assessment.',
    'Auto-generate NCA HEY incident notification packages from Cyber Incident records (NCA requires 24-hour reporting for material incidents).',
    'CITC 24-hour cyber-incident notification template for telecom/cloud-provider customers.',
    'Bilingual control implementation evidence: text descriptions auto-translated and reviewed by an Arabic-speaking control owner before submission.',
  ],
});

const m3_compliance = moduleSection({
  id: '5.3', name: 'Compliance',
  score: { tss: 3.0, bench: 4.5 },
  current: [
    'Six tabs: Overview, Controls, Internal, External, Findings, Scorecard.',
    'Full CRUD on controls; assessments and findings exposed via API but only Controls tab has create/edit/delete in the current UI.',
    'Scorecard tile present in module overview.',
  ],
  frameworks: [
    ['ISO 19600 / 37301', 'ISO', 'Compliance management systems.'],
    ['COSO Internal Control', 'COSO', 'Internal-control integrated framework.'],
    ['SOX', 'US SEC', 'For US-listed entities; ITGC and financial-process controls.'],
    ['GDPR / PDPL', 'EU / SDAIA', 'Cross-border privacy compliance posture.'],
    ['SAMA Compliance Framework', 'SAMA', 'For licensed financial entities.'],
    ['CMA Cybersecurity Guide', 'CMA', 'Listed-entity cybersecurity disclosure expectations.'],
    ['ZATCA e-invoicing', 'ZATCA', 'Tax compliance for VAT-registered entities.'],
    ['Nitaqat (Saudization)', 'HRSD', 'Workforce compliance ratios; relevant for board-level compliance dashboards.'],
  ],
  gaps: [
    'No regulatory-change-management module. Tracking new regulations, amendments, and applicability assessment is the daily work of a compliance officer.',
    'No control-to-policy linkage. Controls implement policies; that hierarchy is missing.',
    'No internal-audit working-papers surface. Auditors paste evidence into Word documents today.',
    'No CAPA closure-verification step. Findings can be marked Closed without independent re-test.',
    'Scorecard is static. Tier-1 platforms compute readiness as a weighted aggregate over control test results, with drill-through.',
    'No external-audit lifecycle (opening meeting → fieldwork → findings → response → closure → follow-up).',
    'No policy lifecycle (draft → legal review → executive approval → publish → user attestation → review/retire).',
    'Findings have no aging-with-auto-escalation behaviour.',
  ],
  enhancements: [
    ['1', 'Regulatory change management', 'Subscribe to feed → applicability assessment → impact analysis → control updates → owner sign-off', 'Critical'],
    ['2', 'Policy lifecycle', 'Author → legal review → executive approval → publication → user attestation → periodic review → retire', 'Critical'],
    ['3', 'External audit lifecycle', 'Engagement letter → opening meeting → fieldwork tracker → finding capture → mgmt response → closure → follow-up', 'High'],
    ['4', 'Findings aging engine', 'Finding age → auto-escalation tiers (30d → manager, 60d → director, 90d → exec) → board-pack feed', 'High'],
    ['5', 'Dynamic compliance scorecard', 'Control test results → weighted aggregation by framework → drill-through → board view', 'High'],
    ['6', 'CAPA closure verification', 'Implement → owner self-test → independent re-test → audit sign-off → closure', 'High'],
    ['7', 'Internal audit working papers', 'Engagement plan → fieldwork notes → evidence linkage → finding draft → review trail → archive', 'Medium'],
  ],
  saudi: [
    'Pre-load NCA ECC compliance scorecard template aligned to NCA Audit Methodology.',
    'Pre-load SAMA Compliance Framework controls and reporting templates for banking.',
    'Add Nitaqat (Saudization) compliance dashboard surface (ratio bands, expected-band breach alerts) sourced from HRSD data.',
    'ZATCA e-invoicing readiness sub-checklist for finance teams.',
    'Hijri-calendar option for compliance review periods (SAMA quarterly reports).',
  ],
});

const m4_privacy = moduleSection({
  id: '5.4', name: 'Privacy and Personal Data Protection',
  score: { tss: 3.5, bench: 4.5 },
  current: [
    'Five tabs: Overview, DSR, Breaches, RoPA, Consent.',
    'Full CRUD on DSR records via /api/v1/privacy/dsrs; breaches and RoPA exposed via API.',
    'Stats endpoint surfaces DSR-by-type and consent coverage metrics.',
  ],
  frameworks: [
    ['PDPL (Saudi)', 'SDAIA / Saudi Data Office', 'Primary law in force since September 2024.'],
    ['PDPL Implementing Regulations', 'SDAIA', 'Detailed obligations for controllers and processors.'],
    ['GDPR', 'EU', 'Reference framework; broadly aligned with PDPL.'],
    ['ISO/IEC 27701:2019', 'ISO/IEC', 'Privacy Information Management System (PIMS).'],
    ['ISO/IEC 29100:2011', 'ISO/IEC', 'Privacy framework foundations.'],
    ['CCPA / CPRA', 'California', 'For US-data-subject organisations.'],
    ['HIPAA', 'US HHS', 'For health data (rare in KSA but relevant for healthcare entities operating cross-border).'],
  ],
  gaps: [
    'No DSR-fulfilment SLA tracker enforcing PDPL\'s 30-day window with auto-rejection or extension flow.',
    'No DPIA (Data Protection Impact Assessment) entity at all. PDPL Implementing Regulations require DPIAs for high-risk processing.',
    'No consent re-collection workflow when policy changes; consent withdrawal is not wired to downstream erasure triggers.',
    'No breach 72-hour notification automation (PDPL Article 20 obligation).',
    'No data-flow / data-lineage mapping. RoPA is a list of processes today, not a graph.',
    'No third-country transfer risk assessment. PDPL has explicit cross-border restrictions.',
    'No special-category data tagging (health, biometric, children\'s data) with elevated processing rules.',
  ],
  enhancements: [
    ['1', 'DSR fulfilment SLA engine', 'Receive → identity verification → scope → fulfilment → response → close (PDPL: 30 days, with auto-extension request flow)', 'Critical'],
    ['2', 'Breach 72-hour notification', 'Detect → severity → DPO triage → if reportable: auto-package for Saudi Data Office → submit → public notification (if required)', 'Critical'],
    ['3', 'DPIA workflow', 'Trigger criteria → questionnaire → risk assessment → mitigation → DPO sign-off → review cycle', 'Critical'],
    ['4', 'Consent lifecycle', 'Collect → granular display → re-collect on policy change → withdrawal → automatic erasure trigger downstream', 'High'],
    ['5', 'RoPA → data-flow graph', 'Per-process catalogue → upstream sources → downstream destinations → cross-border transfer flag → DPIA trigger', 'High'],
    ['6', 'Cross-border transfer assessment', 'Receiving country adequacy → adequacy decision lookup → if non-adequate: Data Office approval flow → BCR/SCC tracking', 'High'],
    ['7', 'Special-category data classifier', 'Process inputs auto-classified → elevated retention/deletion rules → access restriction', 'Medium'],
  ],
  saudi: [
    'Pre-load PDPL DSR types (access, correction, deletion, transfer, processing-restriction, withdrawal-of-consent, complaint).',
    'Pre-load Saudi Data Office breach notification template (Arabic) with auto-population from Breach record.',
    'Add cross-border transfer flow specifically for non-adequate countries with Data Office consultation tracking.',
    'Children\'s data (under 18) parental-consent flow per PDPL Article 7.',
    'Health-data special handling for Sehhaty / Mawid integration cases.',
    'Cookie-consent banner generator localised to KSA jurisdiction.',
  ],
});

const m5_bcm = moduleSection({
  id: '5.5', name: 'Business Continuity Management',
  score: { tss: 3.5, bench: 4.0 },
  current: [
    'Five tabs: Overview, Plans, Processes, BIA, Exercises.',
    'Full CRUD on Plans, Processes and BIA records (recently extended in v3).',
    'Tab-aware add/edit/delete now correctly routed per tab.',
    'Exercises remain in-memory for now (no backend); add/edit/delete works via local state.',
  ],
  frameworks: [
    ['ISO 22301:2019', 'ISO', 'Business continuity management systems.'],
    ['ISO 22313:2020', 'ISO', 'Implementation guidance for ISO 22301.'],
    ['NCEMA Standards', 'NCEMA', 'Saudi national crisis & emergency management standard.'],
    ['SAMA BCP Requirements', 'SAMA', 'Banking-specific BCP testing and reporting expectations.'],
    ['ISO 27031', 'ISO', 'ICT readiness for business continuity (DR-specific).'],
  ],
  gaps: [
    'No BCM lifecycle workflow. ISO 22301 expects a coherent BIA → strategy → plan → exercise → improve cycle; today plans and BIAs are independent records.',
    'No exercise scheduling with reminders, calendar view, or post-exercise score-to-CAPA flow.',
    'No plan invocation runbook. When the BCP is invoked there is no live status tracking, no role-based notifications, no de-escalation flow.',
    'No supplier dependency mapping. Critical processes depend on critical vendors; that link is absent.',
    'No automated RTO/RPO compliance scoring (declared RTO vs measured recovery time).',
    'No crisis communication module (pre-approved templates, role-based distribution, audit log).',
  ],
  enhancements: [
    ['1', 'BCM lifecycle workflow', 'Process inventory → BIA → strategy → plan → exercise → CAPA → re-BIA', 'Critical'],
    ['2', 'Plan invocation runbook', 'Trigger → notify roles → activate → status updates → recovery → de-escalate → after-action review', 'Critical'],
    ['3', 'Exercise lifecycle engine', 'Schedule → scenario design → execute → score → findings → CAPA → next cycle', 'High'],
    ['4', 'Supplier-dependency mapping', 'Critical process → vendor relationship → vendor outage → impact projection → contingency activation', 'High'],
    ['5', 'Crisis communication module', 'Pre-approved templates → role-based distribution → multi-channel (email/SMS/Tawakkalna) → audit log', 'High'],
    ['6', 'RTO/RPO compliance scoring', 'Declared RTO → measured recovery → variance → trend → board view', 'Medium'],
  ],
  saudi: [
    'NCEMA-aligned scenario library: sandstorm, civil unrest, Hajj operational disruption, Ramadan operational windows.',
    'Hajj/Ramadan operational-window awareness in exercise scheduling (avoid scheduling exercises during operational peaks).',
    'SAMA-required annual full-scale BCP test workflow with regulatory-report template.',
    'GCC-CERT cross-border incident coordination flow for regional crises.',
    'Tawakkalna Business integration for employee status during pandemic-class events.',
  ],
});

const m6_tprm = moduleSection({
  id: '5.6', name: 'Third-Party Risk Management',
  score: { tss: 2.5, bench: 4.5 },
  current: [
    'Four tabs: Overview, Vendors, Assessments, Contracts.',
    'Vendor CRUD via /api/v1/tprm/vendors; assessments and contracts surfaces present but tab-level CRUD limited.',
  ],
  frameworks: [
    ['ISO 27036', 'ISO', 'Information security for supplier relationships.'],
    ['NCA TPCS', 'NCA', 'Third-Party Cybersecurity Standard — mandatory for KSA government and CNI.'],
    ['SAMA Outsourcing Rules', 'SAMA', 'Banking-specific outsourcing notification, due-diligence, and exit obligations.'],
    ['NIST SP 800-161', 'NIST', 'Supply-chain risk management for federal-grade buyers.'],
    ['SIG / SIG Lite', 'Shared Assessments', 'Industry-standard vendor security questionnaires.'],
    ['CAIQ', 'Cloud Security Alliance', 'Cloud-vendor security questionnaire.'],
  ],
  gaps: [
    'No tiering automation based on data access, criticality and spend. Tier-1 platforms classify vendors automatically.',
    'No continuous-monitoring integration (BitSight, SecurityScorecard, RiskRecon, Black Kite).',
    'No SLA tracking with breach detection.',
    'No fourth-party (sub-vendor) tracking.',
    'No contract-anomaly detection (auto-renewal clauses, missing security addenda, expired insurance).',
    'No vendor-incident propagation to TSS impact assessment.',
    'No periodic re-assessment scheduling driven by tier.',
  ],
  enhancements: [
    ['1', 'Vendor onboarding workflow', 'Intake → tier assessment → due diligence → security review → contract → exec approval', 'Critical'],
    ['2', 'Tier-driven re-assessment cadence', 'Tier 1 = annual, Tier 2 = biennial, Tier 3 = triennial; auto-schedule and notify', 'Critical'],
    ['3', 'Contract lifecycle engine', 'Renewal alerts at 90/60/30 days → SLA review → re-tier → renew or exit decision', 'High'],
    ['4', 'Continuous monitoring feed', 'External rating ingestion → drift threshold → re-assessment trigger → owner notification', 'High'],
    ['5', 'Vendor-incident impact flow', 'Vendor breach → linked TSS processes → impact assessment → action plan → contract review', 'High'],
    ['6', 'Sub-vendor / fourth-party register', 'Vendor declaration of sub-vendors → criticality cascade → fourth-party security review', 'Medium'],
  ],
  saudi: [
    'Pre-load NCA TPCS questionnaire as the default vendor security assessment template (English + Arabic).',
    'SAMA outsourcing notification thresholds and templates for banking customers.',
    'Local-vendor preference scoring (Saudization in supply chain) on vendor records.',
    'Cross-border-data-hosting due-diligence checklist for SaaS vendors that process Saudi personal data.',
    'Yakeen integration for vendor primary-contact verification.',
    'ZATCA Fatoora cross-check for vendor tax-status validation at onboarding.',
  ],
});

const m7_scm = moduleSection({
  id: '5.7', name: 'Supply Chain Management (GRC angle)',
  score: { tss: 2.5, bench: 3.5 },
  current: [
    'Five tabs: Overview, Vendors, Onboarding, Assessments, Contracts.',
    'Onboarding endpoints support advance/approve actions (API exists; UI not yet wired).',
    'Significant overlap with TPRM; today the two modules are not clearly differentiated by purpose.',
  ],
  frameworks: [
    ['ISO 28000:2022', 'ISO', 'Supply chain security management systems.'],
    ['NIST SP 800-161r1', 'NIST', 'Cybersecurity supply-chain risk management.'],
    ['UK Modern Slavery Act / Saudi labour law', 'Multi', 'Ethical supply-chain reporting.'],
  ],
  gaps: [
    'Clear product positioning vs TPRM. TPRM = vendor-risk view; SCM should be physical/digital supply-chain integrity, including upstream sourcing and downstream distribution.',
    'No purchase-order linkage; no inventory or commodity-risk view.',
    'No country-of-origin risk scoring.',
    'No conflict-mineral or sanctions-screening integration.',
  ],
  enhancements: [
    ['1', 'Clear separation from TPRM', 'TPRM: vendor risk and security; SCM: supply-chain integrity, country-of-origin, sanctions, ESG', 'Critical'],
    ['2', 'Sanctions and PEP screening', 'Vendor record → OFAC/UN/EU/KSA list check → match score → review queue', 'High'],
    ['3', 'Country-of-origin risk', 'PO → country → geopolitical risk → ESG risk → composite supply-chain score', 'High'],
    ['4', 'Onboarding step-function', 'Initial submission → KYC → due diligence → security review → financial check → approval → contract issued', 'High'],
  ],
  saudi: [
    'KSA sanctions list integration (SAMA-published lists in addition to OFAC/UN/EU).',
    'Saudization quotient on vendor onboarding (preference scoring per Nitaqat band).',
    'Local-content scoring (alignment with Saudi Made / In-Kingdom Total Value Add).',
    'Hijri-calendar contract milestones option.',
  ],
});

const m8_incidents = moduleSection({
  id: '5.8', name: 'Incident Management',
  score: { tss: 3.0, bench: 4.5 },
  current: [
    'Four tabs: Overview, Register, Timeline, Lessons.',
    'Full CRUD on incidents; timeline and lessons surfaces present.',
    'Cyber-incident sub-flow has the deeper workflow today (Start Response action wired).',
  ],
  frameworks: [
    ['ISO/IEC 27035-1/2/3', 'ISO/IEC', 'Information security incident management.'],
    ['NIST SP 800-61r2', 'NIST', 'Computer security incident handling guide.'],
    ['NCA HEY guidelines', 'NCA', 'Saudi cyber-incident reporting expectations (24-hour materiality threshold).'],
    ['CITC Cybersecurity Requirements', 'CITC', 'Telecoms-specific incident notification.'],
    ['SAMA Cyber Threat Intelligence Sharing', 'SAMA', 'Banking-specific incident-sharing requirements.'],
    ['HCIS', 'CCHI', 'Healthcare-data breach notification.'],
  ],
  gaps: [
    'No SOAR-style automated response playbook engine.',
    'No MITRE ATT&CK kill-chain mapping inside incidents.',
    'No forensic chain-of-custody on collected evidence.',
    'No regulatory-reporting-template engine that auto-generates a NCA / Data Office / SAMA-shaped submission package.',
    'No stakeholder notification matrix (legal, comms, exec, regulator) with role-based templates.',
  ],
  enhancements: [
    ['1', 'NIST 800-61 lifecycle workflow', 'Detect → analyse → contain → eradicate → recover → post-incident', 'Critical'],
    ['2', 'Severity-based runbooks', 'Severity → playbook auto-loaded → step-by-step actions → evidence pinned at each step', 'Critical'],
    ['3', 'Stakeholder notification matrix', 'Severity + type → matrix → role-templated notifications → audit log', 'High'],
    ['4', 'Regulatory-report templating', 'Incident → auto-package: NCA HEY (24h), Data Office (72h), CITC (24h, telcos), HRSD (employee data)', 'Critical'],
    ['5', 'MITRE ATT&CK mapping', 'Incident → tactics and techniques tagging → adversary profile → kill-chain visual', 'High'],
    ['6', 'Lessons-learned → preventive action', 'Post-incident → root-cause → control gap → CAPA → control update → re-test', 'High'],
  ],
  saudi: [
    'NCA HEY-shaped incident submission package auto-generated from incident record.',
    'CITC 24-hour notification template for telecom and cloud-provider customers.',
    'SAMA Cyber Threat Intelligence Sharing payload format for licensed banks.',
    'HCIS healthcare-data breach notification for healthcare entities.',
    'Bilingual incident timelines so Arabic-speaking regulators can review without re-translation.',
  ],
});

const m9_evidence = moduleSection({
  id: '5.9', name: 'Evidence Management',
  score: { tss: 2.5, bench: 4.5 },
  current: [
    'Five tabs: Overview, Catalog, Requests, Review, Expired.',
    'Full CRUD on evidence items with approve/reject; requests and review surfaces present.',
  ],
  frameworks: [
    ['ISO 19011:2018', 'ISO', 'Audit guidelines (evidence sufficiency and reliability).'],
    ['ISO/IEC 27001:2022 §9-A.5.36', 'ISO/IEC', 'Audit-evidence requirements.'],
    ['NCA ECC §1-5', 'NCA', 'Evidence and audit-trail requirements.'],
    ['SAMA Audit Guidelines', 'SAMA', 'Audit-evidence retention and reliability.'],
    ['Federal Rules of Evidence (forensic)', 'Various', 'Where evidence may be used in legal proceedings.'],
  ],
  gaps: [
    'No version-controlled evidence repository with diff. Today, replacing an evidence file overwrites the prior.',
    'No automated evidence collection. Connector library is the obvious gap (AD, SIEM, vulnscan, configmgmt, cloud).',
    'No chain-of-custody fields (collector, collection method, hash, witness).',
    'No tamper-evident storage (WORM, hash chains, immutable storage tier).',
    'No collection-freshness tracking. An evidence sample must be tagged with validity period; today it has no expiry behaviour beyond a manual flag.',
  ],
  enhancements: [
    ['1', 'Version control with diff', 'New evidence supersedes previous; both retained; diff visible; rollback supported', 'Critical'],
    ['2', 'Connector library MVP', 'AD groups, Defender alerts, Tenable/Qualys scans, AWS Config, Azure Policy → scheduled collection → typed evidence', 'Critical'],
    ['3', 'Chain-of-custody fields', 'Collector + collection method + hash + witness + tool version captured at creation; immutable thereafter', 'High'],
    ['4', 'WORM / hash-chain storage', 'Append-only storage tier; periodic Merkle-tree hash anchored to external timestamp authority', 'High'],
    ['5', 'Collection-freshness engine', 'Validity period per evidence type → expiring-soon dashboard → re-collection workflow', 'High'],
    ['6', 'Evidence-control linkage', 'Evidence → control → framework projection → automatic test-pass/fail propagation', 'High'],
  ],
  saudi: [
    'Bilingual evidence captions for Arabic-speaking auditors.',
    'Hijri timestamps in addition to Gregorian.',
    'Sovereign-region storage option (STC Cloud, Mobily SCC, SCCC) for regulated customers.',
    'Saudi Data Office shareable evidence package for breach investigations.',
  ],
});

const m10_iam = moduleSection({
  id: '5.10', name: 'Identity, Access and Platform Administration',
  score: { tss: 2.5, bench: 4.5 },
  current: [
    'Four tabs: Users, Roles, Organisations, Audit Log.',
    'User and role CRUD via /api/v1/admin; multi-tenant via Organisation.',
    'Audit-log surface present.',
  ],
  frameworks: [
    ['ISO/IEC 27001:2022 §A.5.15-A.5.18', 'ISO/IEC', 'Access control and identity-lifecycle controls.'],
    ['NCA ECC §2-2', 'NCA', 'Identity and access management requirements.'],
    ['NIST SP 800-63', 'NIST', 'Digital identity guidelines.'],
    ['SOX ITGC', 'PCAOB', 'IT general controls — access provisioning, deprovisioning, periodic review.'],
    ['PCI DSS v4.0', 'PCI SSC', 'For card-data environments.'],
  ],
  gaps: [
    'No automated access certification campaigns (quarterly attestation by manager).',
    'No Segregation-of-Duties (SoD) matrix engine.',
    'No privileged access management (PAM) integration.',
    'No just-in-time access flow.',
    'No multi-factor authentication wiring (the platform itself is JWT-only; SSO/MFA would come from an external IDP).',
    'No Joiner-Mover-Leaver workflow.',
  ],
  enhancements: [
    ['1', 'Access certification campaigns', 'Campaign launch → manager review → revoke/keep → exception → close → board pack', 'Critical'],
    ['2', 'SoD matrix engine', 'Define toxic-pair matrix → conflict detection → mitigation control → exception with approval', 'Critical'],
    ['3', 'Joiner-Mover-Leaver workflow', 'HR event → access provisioning at join → access review at move → revocation at leave', 'High'],
    ['4', 'IDP integration (Azure AD / Okta / Nafath)', 'SSO via OIDC/SAML; group-to-role mapping; just-in-time provisioning', 'Critical'],
    ['5', 'Just-in-time elevated access', 'Privileged action requested → approval → time-bound access grant → auto-revoke → audit', 'High'],
    ['6', 'Audit-log immutability', 'Append-only store; periodic hash anchor; tamper-evident review', 'High'],
  ],
  saudi: [
    'Nafath SSO integration as the recommended authentication mode for Saudi citizen users.',
    'Absher integration for employee verification at onboarding.',
    'Privileged-action signer-identity capture via Nafath (digital signature for exceptions, breach attestations).',
    'Bilingual audit-log entries (action and target both labelled in EN and AR).',
  ],
});

// ── 6. Cross-cutting recommendations ─────────────────────────────────────
const sec6 = [
  H1('6. Cross-Cutting Recommendations'),

  H2('6.1 Build a configurable workflow engine'),
  P('This is the single highest-leverage investment the platform can make in the next 12 months. ServiceNow IRM\'s commercial advantage is not its data model — it is the workflow engine that lets a customer define new states, transitions, approvers, SLAs, escalations and reminders without backend code changes. Today TSS GRC implements workflows as fixed logic in @Service classes; every new workflow stage requires a backend deployment.'),
  P('Recommended architecture: a small set of generic tables (workflow_definition, workflow_state, workflow_transition, workflow_action, workflow_assignment) coupled to a state-machine evaluator that runs as a background processor. Surface the model to administrators through a no-code builder (drag-and-drop states, transition rules expressed as a small DSL or a visual condition builder, SLA timers, approver expressions like "any-of(role:CISO, role:CRO)"). Ship five to ten reference workflows pre-built so the platform feels usable from day one.'),
  P('Effort estimate: 6-9 engineer-months for the engine, the no-code builder UI, migration of existing fixed workflows to the new engine, and three reference workflows shipped with documentation.'),

  H2('6.2 Continuous control monitoring with a connector library'),
  P('Compliance-automation challengers (Drata, Vanta, Secureframe) rose to several-hundred-million-dollar valuations on a single observation: control evidence can be collected automatically, dramatically reducing audit cycle time. Tier-1 GRC platforms have responded by adding their own connector libraries, but none of them are particularly Saudi-aware. The strategic opportunity is to ship a small, opinionated connector library (Active Directory, Microsoft Defender, AWS Config, Azure Policy, Tenable, Qualys, ZScaler, Splunk, Sentinel, GitHub) plus Saudi-specific connectors (Mobily SCC, STC Cloud, NCA HEY threat-intel feed, Saudi Data Office submission API).'),

  H2('6.3 AI / ML opportunities'),
  BULLET('Risk-incident correlation: when an incident is logged, suggest related risks and propose retrospective likelihood adjustments.'),
  BULLET('Predictive risk scoring: feed historical incident, vulnerability and audit-finding data into a forecast model that highlights risks trending upward.'),
  BULLET('Anomaly detection in audit logs: classical unsupervised anomaly scoring on access patterns; flag outliers for review.'),
  BULLET('Automated control testing from evidence: read evidence (configurations, scan output) and emit a structured pass/fail/partial signal with rationale.'),
  BULLET('Smart RoPA generation: scan database schemas, application APIs and document repositories to draft an initial Record of Processing Activities.'),
  BULLET('SDAIA-compliant model governance: embed model lineage, training-data provenance, bias testing and explainability — both as an enabler for AI features in the platform itself and as a sellable capability for AI-using Saudi customers.'),
  BULLET('Bilingual: AI features must work end-to-end in Arabic. A Saudi customer should be able to dictate an incident description in Arabic and have it correctly classified, severity-scored, and routed.'),

  H2('6.4 Reporting and exports'),
  BULLET('Ad-hoc query builder over the audit-log and entity tables, with pre-built saved queries.'),
  BULLET('Scheduled report generator (daily/weekly/monthly) with PDF and XLSX outputs.'),
  BULLET('Saudi government regulatory-report templates (NCA quarterly cyber, SAMA monthly op-risk, Data Office breach notifications, CMA listed-entity disclosures, CITC telecom incidents).'),
  BULLET('Hijri date support throughout, with parallel-display option (Hijri / Gregorian).'),
  BULLET('Arabic PDF rendering with proper RTL — Apache POI\'s OpenPDF supports it but Arabic ligatures and number-shaping need polish.'),

  H2('6.5 Mobile experience'),
  BULLET('Native mobile app (React Native or Flutter) targeting iOS and Android, with offline-first sync.'),
  BULLET('Approver mode: incident triage, exception sign-off, access certification approval, breach attestation.'),
  BULLET('Field-officer mode: site assessments, BIA interviews, evidence capture (camera + GPS + signature), all working offline.'),
  BULLET('Push notifications for severity-driven alerts (critical incidents, SLA breaches, approval requests).'),
  BULLET('Biometric unlock and Nafath-based authentication for Saudi citizen users.'),

  H2('6.6 Integration platform'),
  P('Tier-1 GRC platforms sell on integration-as-a-feature. The recommended approach for TSS GRC is two-fold: (1) ship a webhook subscription model so any external system can be notified of any platform event (vulnerability registered, exception approved, incident raised); (2) ship pre-built bidirectional connectors for the top ten enterprise systems most Saudi customers run — SAP, Oracle EBS / Fusion, Workday, GOSI, Saudi Cloud (STC, Mobily, SCCC), Microsoft 365, Azure AD, Okta, ServiceNow ITSM, and Jira / Confluence.'),

  H2('6.7 DevSecOps and operational maturity'),
  BULLET('Move secrets out of application-dev.yml and into a secrets manager (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault).'),
  BULLET('Add CI pipeline with SAST (SonarQube), DAST (OWASP ZAP), dependency scanning (Dependabot, Snyk), license compliance (FOSSA).'),
  BULLET('Move Maven build artefacts (target/*.jar) out of the repository.'),
  BULLET('Add observability: structured logging, distributed tracing (OpenTelemetry), metrics (Prometheus / Grafana).'),
  BULLET('Document a reference deployment architecture for STC Cloud, Mobily SCC, and SCCC — the three Saudi sovereign clouds most likely to be required by government and regulated customers.'),
  BULLET('Pursue NCA Cloud Computing Regulatory Framework certification at level C (Highly Confidential) to unlock government and CNI customers.'),

  PB(),
];

// ── 7. Pricing & Commercial Positioning ──────────────────────────────────
const sec7 = [
  H1('7. Pricing and Commercial Positioning'),

  H2('7.1 Five-year total cost of ownership comparison'),
  P('The figures below are illustrative and based on publicly-reported analyst ranges and customer disclosures for an organisation of approximately 500 GRC users. Actual quotes vary widely based on module count, integration scope, professional-services intensity, and negotiation leverage. The headline observation is structural: Tier-1 platforms cost three-to-eight times more than TSS GRC at equivalent functional scope, and the cost ratio widens further once Saudi-specific localisation and sovereign-hosting requirements are factored in.'),
  table(
    ['Platform', 'License (5y, USD)', 'Implementation', 'Operations (5y)', 'Total 5y'],
    [
      ['ServiceNow IRM/GRC', '$2.5M', '$1.5M', '$1.0M', '$5.0M'],
      ['MetricStream', '$1.8M', '$1.2M', '$0.7M', '$3.7M'],
      ['Archer (Archer Tech.)', '$1.5M', '$1.0M', '$0.6M', '$3.1M'],
      ['IBM OpenPages', '$1.6M', '$1.2M', '$0.7M', '$3.5M'],
      ['OneTrust (full GRC)', '$1.2M', '$0.8M', '$0.5M', '$2.5M'],
      ['LogicGate Risk Cloud', '$0.9M', '$0.5M', '$0.4M', '$1.8M'],
      ['AuditBoard', '$0.7M', '$0.4M', '$0.3M', '$1.4M'],
      ['TSS GRC v3 (self-hosted)', '$0.3M', '$0.3M', '$0.3M', '$0.9M'],
    ],
    [2400, 1800, 1800, 1800, 1560]
  ),

  H2('7.2 Recommended pricing model'),
  BULLET('Per-user / per-month subscription with module bundles (Foundation: Risk + Compliance + Audit; Cyber Pack: + Cybersecurity + Incidents + Evidence; Privacy Pack: + Privacy; Continuity Pack: + BCM; Vendor Pack: + TPRM + SCM).'),
  BULLET('Tiered editions: Standard (≤ 100 users), Enterprise (≤ 1000 users), Sovereign (unlimited + sovereign hosting + dedicated support).'),
  BULLET('Implementation packages: Quick Start (4 weeks, single module), Standard (12 weeks, three modules), Comprehensive (24 weeks, full stack).'),
  BULLET('Saudi government / GRE pricing model: framework-licence with per-entity sub-licensing, suitable for ministry-wide rollouts.'),

  H2('7.3 Where to win, where not to fight'),
  BULLET('Win in: Saudi mid-market private sector, Vision 2030 government entities, mid-tier banks (non-D-SIB), GREs, MoH hospital groups, GCC regional expansion (UAE NESA, Bahrain NCSA).'),
  BULLET('Do not fight in (yet): Tier-1 systemically-important banks (SAMA D-SIBs), Aramco-tier CNI, multinational HQ deployments where a global standard already exists.'),
  PB(),
];

// ── 8. Roadmap ───────────────────────────────────────────────────────────
const sec8 = [
  H1('8. Recommended Roadmap'),
  P('The roadmap below sequences the recommendations across four horizons. The 90-day items are quick wins that materially close benchmark gaps without architectural change. The 180-day items deliver the configurable workflow engine and the continuous-monitoring connector library. The 365-day horizon adds AI features, mobile native, and the integration platform. The 540-day horizon focuses on market positioning and certification.'),

  H2('8.1 Horizon 1 — 90 days (quick wins)'),
  table(
    ['#', 'Initiative', 'Outcome'],
    [
      ['1', 'Pre-load NCA ECC v2.0 + SAMA CSF + PDPL framework catalogues', 'First-day-readiness for Saudi customers; differentiator vs Tier-1.'],
      ['2', 'Saudi government regulatory-report templates', 'NCA HEY 24-hour, Data Office 72-hour, CITC, SAMA, CMA submission packages.'],
      ['3', 'Nafath SSO integration', 'Saudi citizen authentication; signer identity for sensitive approvals.'],
      ['4', 'Hijri calendar support throughout the UI', 'Required for SAMA quarterly reports and government Hijri-first workflows.'],
      ['5', 'Workflow engine MVP (read-only stage configuration)', 'Configurable stages, transitions, SLAs even before the visual builder ships.'],
      ['6', 'Vulnerability lifecycle SLA enforcement', 'Severity → SLA window → breach escalation; closes a Critical Cyber gap.'],
      ['7', 'PDPL DSR 30-day SLA tracker', 'Closes a Critical Privacy gap; auto-extension request flow.'],
      ['8', 'Move secrets out of application-dev.yml', 'Removes a known credential-leak risk; sets the right operational hygiene.'],
    ],
    [600, 3500, 5260]
  ),

  H2('8.2 Horizon 2 — 180 days (strategic)'),
  table(
    ['#', 'Initiative', 'Outcome'],
    [
      ['9', 'Configurable workflow engine + no-code builder', 'Closes the biggest benchmark gap (workflow maturity from 2.0 to 4.0).'],
      ['10', 'Continuous control monitoring connector library MVP (10 connectors)', 'Brings TSS GRC into the same conversation as Drata/Vanta for KSA.'],
      ['11', 'Cross-framework control mapping (ECC ↔ ISO ↔ NIST ↔ SAMA)', 'One assessment, multiple compliance views; large efficiency win for customers.'],
      ['12', 'SoD matrix engine + access certification campaigns', 'Closes the IAM benchmark gap.'],
      ['13', 'Crisis communication module', 'Completes the BCM lifecycle.'],
      ['14', 'DPIA workflow + breach 72-hour notification', 'PDPL Article 20 obligation closure.'],
      ['15', 'Vendor continuous-monitoring feed + 4th-party register', 'Brings TPRM to Tier-1 standard.'],
    ],
    [600, 3500, 5260]
  ),

  H2('8.3 Horizon 3 — 365 days (expansion)'),
  table(
    ['#', 'Initiative', 'Outcome'],
    [
      ['16', 'AI-powered risk-incident correlation', 'First marquee AI feature; data already exists.'],
      ['17', 'Mobile native app (iOS + Android)', 'Approver and field-officer modes with offline support.'],
      ['18', 'Enterprise integration connectors (SAP, Workday, Azure AD, Okta, ServiceNow ITSM, Jira)', 'Removes the integration-ecosystem gap for typical Saudi enterprise stacks.'],
      ['19', 'Quantitative risk extension (FAIR / Monte Carlo)', 'Required for SAMA-regulated banks; enables capital-relevant risk modelling.'],
      ['20', 'Regulatory change management module', 'Closes a Critical Compliance gap.'],
      ['21', 'Regional expansion: UAE NESA, Bahrain NCSA, Kuwait NCA framework packs', 'GCC strategy unlocks the wider regional market.'],
    ],
    [600, 3500, 5260]
  ),

  H2('8.4 Horizon 4 — 540 days (market positioning)'),
  table(
    ['#', 'Initiative', 'Outcome'],
    [
      ['22', 'NCA Cloud Computing Regulatory Framework certification (level C)', 'Unlocks government and CNI customers.'],
      ['23', 'SAMA-licensed banking deployment reference', 'Anchor reference customer in the most demanding Saudi vertical.'],
      ['24', 'Reseller and partner programme', 'Sales velocity, regional reach, system-integrator alignment.'],
      ['25', 'Marketplace for third-party content packs', 'Network-effect; framework-pack vendors and consultants extend the platform.'],
      ['26', 'OIC / wider MENA market positioning', 'After GCC, Egypt, Morocco, Indonesia, Malaysia.'],
    ],
    [600, 3500, 5260]
  ),
  PB(),
];

// ── 9. Verdict ───────────────────────────────────────────────────────────
const sec9 = [
  H1('9. Final Verdict'),

  H2('9.1 The single-paragraph version'),
  callout(
    'TSS GRC v3 is a credible Saudi-first GRC platform that wins decisively on localisation and economics, scores at par on user experience and implementation speed, and trails the Tier-1 benchmark on workflow-engine maturity, automated control monitoring and integration ecosystem. The recommended posture for a Saudi mid-market or Vision 2030 entity is: buy with eyes open, pre-negotiate the 90- and 180-day roadmap items as contractual deliverables, and use the localisation-and-economics edge as the basis for executive sponsorship while the platform closes its remaining benchmark gaps.',
    'good'
  ),
  P('', { after: 80 }),

  H2('9.2 Buyer fit by segment'),
  table(
    ['Buyer profile', 'Fit', 'Why'],
    [
      ['Saudi mid-market private sector (100-500 users)', 'Strong (8/10) — Buy', 'Localisation, economics and modern UX are decisive. Functional gaps are tolerable at this scale.'],
      ['Saudi government / Vision 2030 entities', 'Strong (8/10) — Buy', 'Sovereign-hosting story, NCA framework alignment, Arabic UX dominate the decision criteria.'],
      ['Mid-tier Saudi banks (non-D-SIB)', 'Conditional (6/10) — Buy with negotiated roadmap', 'SAMA framework alignment is a positive; quantitative risk and continuous monitoring are required additions.'],
      ['Saudi healthcare entities (HCIS-regulated)', 'Conditional (6/10) — Buy with privacy hardening', 'Privacy module needs DPIA and HCIS-specific breach flow before this is comfortable.'],
      ['GCC regional (UAE / Bahrain / Kuwait)', 'Moderate (5/10) — Wait for v3.5', 'NESA / NCSA framework packs not yet shipped. Strong fit once available.'],
      ['Tier-1 global multinational (HQ in EU / US)', 'Weak (4/10) — Pass for now', 'Global multi-jurisdiction depth, Tier-1 integration ecosystem, and AI features are not yet at par.'],
      ['SAMA D-SIB banks / Aramco-tier CNI', 'Weak (3/10) — Pass for now', 'Systemic-importance entities need depth across every dimension; roadmap delivery confidence required first.'],
    ],
    [3000, 2500, 3860]
  ),

  H2('9.3 What a buyer should negotiate at signing'),
  BULLET('Contractual delivery dates for the 90-day quick-wins (NCA ECC catalogue pre-load, Nafath SSO, Hijri calendar, PDPL DSR SLA tracker).'),
  BULLET('Roadmap commitments for the 180-day strategic items (configurable workflow engine, continuous monitoring connector library, cross-framework mapping).'),
  BULLET('Sovereign-region hosting on STC Cloud, Mobily SCC, or SCCC for any deployment processing Saudi personal data — non-negotiable.'),
  BULLET('Pricing protection: cap year-over-year list-price increases at consumer-price-index plus 3 percentage points.'),
  BULLET('Source-code escrow given the platform\'s relative youth — protects against vendor business-continuity risk.'),
  BULLET('Implementation methodology aligned to the customer\'s framework priority (NCA ECC first, ISO 27001 second, SAMA CSF third for banking, CITC for telecoms).'),

  H2('9.4 What the platform team should do next'),
  P('In priority order: (1) ship the 90-day quick-wins above to remove the most-visible localisation deficits; (2) start the configurable workflow engine immediately because it is on the critical path for everything else; (3) pre-load the NCA ECC, NCA CCC, SAMA CSF and PDPL framework catalogues as seed migrations so a Saudi customer experiences first-day-readiness; (4) win one named SAMA-licensed banking reference customer to anchor the segment-up strategy; (5) build the connector library with explicitly-Saudi connectors first (STC Cloud, Mobily SCC, NCA HEY, Saudi Data Office) to make a sustainably-Saudi competitive moat.'),

  H2('9.5 Closing observation'),
  P('Most Saudi GRC buyers today choose between an over-priced, under-localised Tier-1 platform and a manual spreadsheet-and-email process. There is genuine market space for a third option: a modern, Saudi-built, framework-aligned platform that costs a fraction of the Tier-1 list price and treats Arabic and the Saudi regulatory stack as the primary citizens. TSS GRC v3 is closer to that platform than any other product currently in the market. The remaining work is concrete and twelve-to-eighteen months of focused execution; the architectural foundations are sound; the localisation moat exists today and would be expensive for an incumbent to replicate. The strongest recommendation, both to potential buyers and to the platform team, is to treat the 90-and-180-day roadmap as the centre of the next two quarters and to use the localisation edge as the basis for both customer acquisition and product strategy.'),
];

// ─── Document assembly ────────────────────────────────────────────────────

const allChildren = [
  ...cover,
  ...sec1,
  ...sec2,
  ...sec3,
  ...sec4,
  ...sec5Header,
  ...m1_risk, ...m2_cyber, ...m3_compliance, ...m4_privacy, ...m5_bcm,
  ...m6_tprm, ...m7_scm, ...m8_incidents, ...m9_evidence, ...m10_iam,
  PB(),
  ...sec6,
  ...sec7,
  ...sec8,
  ...sec9,
];

const doc = new Document({
  creator: 'Independent Buyer Assessment',
  title: 'TSS GRC Platform — Buyer-Grade Independent Assessment',
  description: 'Benchmarked against ServiceNow IRM, MetricStream, Archer, LogicGate, OneTrust and AuditBoard, with Saudi localisation lens.',
  styles: {
    default: {
      document: { run: { font: FONT, size: 22 }, paragraph: { spacing: { line: 300 } } },
    },
    paragraphStyles: [
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 36, bold: true, font: FONT, color: ACCENT },
        paragraph: { spacing: { before: 360, after: 200 }, outlineLevel: 0 } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 28, bold: true, font: FONT, color: ACCENT },
        paragraph: { spacing: { before: 240, after: 120 }, outlineLevel: 1 } },
      { id: 'Heading3', name: 'Heading 3', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 24, bold: true, font: FONT, color: ACCENT },
        paragraph: { spacing: { before: 200, after: 100 }, outlineLevel: 2 } },
      { id: 'Heading4', name: 'Heading 4', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 22, bold: true, font: FONT, color: INK },
        paragraph: { spacing: { before: 160, after: 80 }, outlineLevel: 3 } },
    ],
  },
  numbering: {
    config: [
      { reference: 'bullets',
        levels: [
          { level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 720, hanging: 360 } } } },
          { level: 1, format: LevelFormat.BULLET, text: '◦', alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 1440, hanging: 360 } } } },
        ] },
      { reference: 'numbers',
        levels: [
          { level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 720, hanging: 360 } } } },
        ] },
    ],
  },
  sections: [{
    properties: {
      page: {
        size: { width: 12240, height: 15840 }, // US Letter portrait, in DXA
        margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 },
      },
    },
    headers: {
      default: new Header({ children: [new Paragraph({
        alignment: AlignmentType.RIGHT,
        children: [new TextRun({ text: 'TSS GRC Platform · Buyer-Grade Independent Assessment', font: FONT, size: 18, color: MUTED, italics: true })],
        border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: ACCENT, space: 4 } },
      })] }),
    },
    footers: {
      default: new Footer({ children: [new Paragraph({
        tabStops: [{ type: TabStopType.RIGHT, position: TabStopPosition.MAX }],
        children: [
          new TextRun({ text: 'Confidential · Independent Assessment · 2026', font: FONT, size: 18, color: MUTED }),
          new TextRun({ text: '\tPage ', font: FONT, size: 18, color: MUTED }),
          new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 18, color: MUTED }),
          new TextRun({ text: ' of ', font: FONT, size: 18, color: MUTED }),
          new TextRun({ children: [PageNumber.TOTAL_PAGES], font: FONT, size: 18, color: MUTED }),
        ],
      })] }),
    },
    children: allChildren,
  }],
});

const outputPath = path.join(__dirname, 'TSS-GRC-Buyer-Assessment.docx');

Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync(outputPath, buffer);
  const sizeKb = (buffer.length / 1024).toFixed(1);
  console.log(`Wrote ${outputPath} (${sizeKb} KB)`);
}).catch(err => {
  console.error('Generation failed:', err);
  process.exit(1);
});

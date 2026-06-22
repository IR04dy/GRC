import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { ShieldCheck, LayoutDashboard, AlertTriangle, LockKeyhole,
         Fingerprint, ClipboardCheck, Workflow, AlertOctagon, Files, UserCog, Truck,
         GitBranch, ShieldAlert, Calendar, Settings, Landmark, Stamp, Send, Wrench,
         Inbox, Plug, FileCheck2, Users, Building2, CheckCheck, Server, FileText,
         Newspaper, Briefcase, Activity, Leaf, Database, Layers, Compass, BookOpen, PenLine, Microscope,
         Sigma, Telescope, Landmark as LandmarkIcon,
         TrendingUp,
         Network, Share2, Bug, BarChart3, KeyRound, Siren, Bell, Megaphone, MessageSquare, Sliders,
         Bot,
         CloudLightning, Factory, FileSearch,
         ScanSearch,
         Scale,
         ShieldX, Shield, ClipboardList, CalendarDays, Banknote, Moon,
         Cloud, ChevronRight, ScrollText, Archive, GraduationCap,
         Calculator, Radar, Spline, Gauge, Sparkles, HelpCircle,
         type LucideIcon } from 'lucide-react';
import { NavLink, useLocation } from 'react-router-dom';
import { useI18n } from '../../lib/i18n';
import { cn } from '../../lib/cn';
import { capaTaskApi, notificationApi, workflowSubmissionApi } from '../../lib/api';

interface NavItem {
  id: string;
  to: string;
  Icon: LucideIcon;
  labelKey: string;
  /** Sub-items rendered indented under the parent when the parent's path
   *  prefix is on the active route or the user has expanded the parent. */
  children?: NavItem[];
}
interface NavGroup {
  id: string;
  titleKey: string;
  items: NavItem[];
  /** Always expanded — Home/Workspace never collapses. */
  pinned?: boolean;
}

/**
 * Sidebar — GRC-SME structure modelled on world-leader IRM/GRC platforms.
 *
 * <p><b>Structural principles (ServiceNow IRM, OneTrust, IBM OpenPages,
 * RSA Archer, MetricStream pattern):</b>
 * <ol>
 *   <li>Workspace pinned at top (universal landing, no collapse).</li>
 *   <li>Domains grouped by accountable risk owner — CRO, CCO, CISO, DPO,
 *       Head of Audit, BCM Manager, Third-Party Manager, AI Officer,
 *       Sustainability Officer, Compliance Officer.</li>
 *   <li>Each group ≤12 items (cognitive-load cap; collapsible if longer).</li>
 *   <li>Multi-page modules (Outsourcing, AI Lifecycle, Data Classification)
 *       use true parent/child nesting — no "· " prefix kludge.</li>
 *   <li>Active route auto-expands its parent group and its parent item's
 *       children, regardless of the user's last manual collapse.</li>
 *   <li>User's manual collapse/expand state survives navigation
 *       (localStorage) — clicking a slice and exiting does not snap the
 *       sidebar back to the top.</li>
 *   <li>Scroll position is restored on every mount so navigating between
 *       deep pages doesn't lose the user's place in the nav.</li>
 * </ol>
 *
 * <p><b>Group order (frequency × ownership):</b><br>
 * Workspace → Audit Committee → Risk → Compliance → Internal Audit →
 * Cybersecurity → Privacy & Data Protection → Business Continuity →
 * Third-Party &amp; Outsourcing → AI &amp; Model Risk →
 * Ethics &amp; Conduct → Regulatory Affairs → ESG &amp; Climate →
 * Reporting &amp; Outputs → Administration.
 */
const NAV_GROUPS: NavGroup[] = [
  // ─── 0. Workspace (pinned) ──────────────────────────────────────────
  // Universal "what do I need to do today" landing. Pinned because every
  // role hits these every session — collapsing them would hide the
  // platform's most-used surfaces behind a chevron.
  {
    id: 'my-work', titleKey: 'navMyWork', pinned: true,
    items: [
      { id: 'dashboard',     to: '/dashboard',            Icon: LayoutDashboard, labelKey: 'itemDashboard' },
      { id: 'workflow-hub',  to: '/workflows',            Icon: Workflow,        labelKey: 'wfHubSidebar' },
      { id: 'wf-submissions',to: '/workflows/submissions', Icon: ClipboardCheck, labelKey: 'wfSubmissionsSidebar' },
      { id: 'calendar',      to: '/calendar',             Icon: Calendar,        labelKey: 'calendarSidebar' },
      { id: 'lc-overview',   to: '/lifecycle-overview',   Icon: Activity,        labelKey: 'loSidebar' },
      { id: 'inbox',         to: '/intake-inbox',         Icon: Inbox,           labelKey: 'inboxTitle' },
      { id: 'notifications', to: '/notifications',        Icon: Bell,            labelKey: 'inboxNotifSidebar' },
      { id: 'approvals',     to: '/approvals',            Icon: CheckCheck,      labelKey: 'approvalsSidebar' },
      { id: 'signatures',    to: '/signatures',           Icon: PenLine,         labelKey: 'sigSidebar' },
      { id: 'notif-outbox',  to: '/notifications/outbox', Icon: Inbox,           labelKey: 'noxSidebar' },
    ],
  },

  // ─── 1. Audit Committee (board-facing) ──────────────────────────────
  // Top placement matches SAMA CG Reg §6 + CMA CG Code Art. 54 — the
  // Audit Committee Chair is the system's most senior reader and
  // expects the curated view first. Two items only.
  {
    id: 'audit-committee', titleKey: 'navAuditCommittee',
    items: [
      { id: 'ac-dash',   to: '/audit-committee/dashboard', Icon: LayoutDashboard, labelKey: 'acDashSidebar' },
      { id: 'rcf-pivot', to: '/governance/pivot/risk/1',   Icon: GitBranch,       labelKey: 'rcfPivotSidebar' },
    ],
  },

  // ─── 2. Risk Management (CRO surface) ──────────────────────────────
  {
    id: 'risk', titleKey: 'navRisk',
    items: [
      { id: 'risk-dash', to: '/risk/dashboard',  Icon: LayoutDashboard, labelKey: 'ermDashSidebar' },
      { id: 'risk',      to: '/risk',            Icon: AlertTriangle,   labelKey: 'modRisk' },
      { id: 'risk-lc',   to: '/risk-lifecycle',  Icon: GitBranch,       labelKey: 'rlcSidebar' },
      { id: 'kri',       to: '/risk/kri/dashboard', Icon: Gauge,        labelKey: 'kriSidebar' },
      { id: 'scenarios', to: '/risk-scenarios',  Icon: Sigma,           labelKey: 'scnSidebar' },
      { id: 'bowtie',    to: '/risk/bowtie',     Icon: GitBranch,       labelKey: 'bowtieSidebar' },
      { id: 'fair-calc', to: '/risk/fair',       Icon: Calculator,      labelKey: 'fairCalcSidebar' },
      { id: 'emerging',  to: '/emerging-risk',   Icon: Telescope,       labelKey: 'emrSidebar' },
      { id: 'incidents', to: '/incidents',       Icon: AlertOctagon,    labelKey: 'incidentsTitle' },
      { id: 'rca',       to: '/rca',             Icon: Microscope,      labelKey: 'rcaSidebar' },
      { id: 'changes',   to: '/changes',         Icon: GitBranch,       labelKey: 'modChanges' },
      { id: 'cpty',      to: '/cpty/dashboard',  Icon: Users,           labelKey: 'cpty.sidebar' },
    ],
  },

  // ─── 3. Compliance & Policy (CCO surface) ───────────────────────────
  // Slimmed from 28 → 12. Ethics/Fraud/COI/Complaints/Whistleblower
  // moved to "Ethics & Conduct". Sanctions moved to Regulatory Affairs.
  // Outsourcing/AI/Data-Class/ESG split into their own domain groups.
  {
    id: 'compliance', titleKey: 'navCompliancePolicy',
    items: [
      { id: 'compliance',   to: '/compliance',             Icon: ClipboardCheck, labelKey: 'modCompliance' },
      { id: 'obligations',  to: '/compliance/obligations', Icon: FileCheck2,     labelKey: 'obligationRegisterSidebar' },
      { id: 'reg-library',  to: '/regulatory/library',     Icon: Landmark,       labelKey: 'regLibrarySidebar' },
      { id: 'reg-change',   to: '/regulatory-change',      Icon: Newspaper,      labelKey: 'regChangeSidebar' },
      { id: 'reg-horizon',  to: '/regulatory/horizon',     Icon: Compass,        labelKey: 'regHorizonSidebar' },
      { id: 'ctrl-library', to: '/controls/library',       Icon: ShieldCheck,    labelKey: 'ctrlLibrarySidebar' },
      { id: 'crosswalk',    to: '/control-crosswalk',      Icon: GitBranch,      labelKey: 'cwSidebar' },
      { id: 'ctrl-ai-map',  to: '/controls/ai-mapper',     Icon: Bot,            labelKey: 'aiCtrlMapSidebar' },
      { id: 'ccm-dash',     to: '/ccm/dashboard',          Icon: Activity,       labelKey: 'ccmDashSidebar' },
      { id: 'posture-hub',  to: '/compliance/posture',     Icon: BarChart3,      labelKey: 'postureHubSidebar' },
      { id: 'fw-packs',     to: '/framework-packs',        Icon: Layers,         labelKey: 'fpSidebar' },
      { id: 'pol-tpl',      to: '/policy-templates',       Icon: BookOpen,       labelKey: 'ptSidebar' },
      { id: 'pol-mgmt',     to: '/policy-management',      Icon: ShieldCheck,    labelKey: 'polMgmtSidebar' },
      { id: 'evidence',     to: '/evidence',               Icon: Files,          labelKey: 'evidenceTitle' },
      { id: 'grc-expert',   to: '/expert',                 Icon: GraduationCap,  labelKey: 'expert.sidebar' },
      { id: 'attestations', to: '/attestations',           Icon: FileCheck2,     labelKey: 'campaignsTitle' },
    ],
  },

  // ─── 4. Internal Audit (Head-of-Audit surface) ──────────────────────
  // 3LoD at top because Internal Audit is Line 3 and the dashboard
  // maps every artefact to its accountable line per SAMA CSF 1.1.1
  // + IIA 2020.
  {
    id: 'audit', titleKey: 'navAudit',
    items: [
      { id: '3lod',         to: '/governance/three-lines', Icon: ShieldCheck,   labelKey: '3lodSidebar' },
      { id: 'audit-ws',     to: '/audit-workspace',        Icon: Stamp,         labelKey: 'modAuditWorkspace' },
      { id: 'ia-plan',      to: '/audit/ia/plans',         Icon: CalendarDays,  labelKey: 'iaPlanSidebar' },
      { id: 'ia-universe',  to: '/audit/ia/universe',      Icon: Telescope,     labelKey: 'iaUniverseSidebar' },
      { id: 'audit-lc',     to: '/audit-lifecycle',        Icon: BookOpen,      labelKey: 'alcSidebar' },
      { id: 'ev-chain',     to: '/evidence-chain',         Icon: Fingerprint,   labelKey: 'echSidebar' },
      { id: 'nca-ecc',      to: '/nca-ecc-export',         Icon: ShieldCheck,   labelKey: 'ncaSidebar' },
      { id: 'nca-ecc-filings', to: '/nca-ecc/filings',    Icon: FileText,      labelKey: 'ncaFilingsSidebar' },
      { id: 'reginspect',   to: '/reginspect/dashboard',   Icon: ClipboardList, labelKey: 'reginspect.sidebar' },
      { id: 'regreporting', to: '/regreporting/dashboard', Icon: CalendarDays,  labelKey: 'regreporting.sidebar' },
    ],
  },

  // ─── 5. Cybersecurity (CISO surface) ────────────────────────────────
  // SOC Ops sub-pages rendered as nested children under /socops parent.
  // Vuln SLA dashboard + policies nested under one parent for the same
  // reason — these are operationally one product, two views.
  {
    id: 'cyber', titleKey: 'navCyber',
    items: [
      {
        id: 'socops', to: '/socops', Icon: Siren, labelKey: 'socopsSidebar',
        children: [
          { id: 'socops-events',  to: '/socops/events',          Icon: Siren,       labelKey: 'socopsEventsSidebar' },
          { id: 'socops-playbks', to: '/socops/playbooks',       Icon: BookOpen,    labelKey: 'socopsPlaybooksSidebar' },
          { id: 'socops-pentest', to: '/socops/pentests',        Icon: ShieldCheck, labelKey: 'socopsPentestsSidebar' },
          { id: 'socops-regexam', to: '/socops/regulator-exams', Icon: LockKeyhole, labelKey: 'socopsRegulatorsSidebar' },
          { id: 'socops-ot',      to: '/socops/ot-assets',       Icon: Server,      labelKey: 'socopsOtSidebar' },
        ],
      },
      { id: 'cyber-dash', to: '/cyber/dashboard',     Icon: LayoutDashboard, labelKey: 'cyberDashSidebar' },
      { id: 'cyber',      to: '/cyber',               Icon: LockKeyhole,     labelKey: 'modCyber' },
      { id: 'cyber-lc',   to: '/cyber-lifecycle',     Icon: ShieldCheck,     labelKey: 'cycSidebar' },
      { id: 'asm',        to: '/cyber/attack-surface', Icon: Network,        labelKey: 'asmSidebar' },
      {
        id: 'vuln-sla', to: '/cyber/vuln-sla/dashboard', Icon: TrendingUp, labelKey: 'vulnSlaDashSidebar',
        children: [
          { id: 'vuln-sla-pol', to: '/cyber/vuln-sla/policies', Icon: Sliders, labelKey: 'vulnSlaPolicySidebar' },
        ],
      },
      { id: 'fw-ctrls', to: '/framework-controls', Icon: BookOpen,    labelKey: 'fwcSidebar' },
      { id: 'assets',   to: '/assets',             Icon: Server,      labelKey: 'assetsSidebar' },
      { id: 'cloud-svc', to: '/cyber/cloud-services', Icon: Cloud,    labelKey: 'cloudServicesSidebar' },
      { id: 'ti-correlate', to: '/cyber/threat-intel', Icon: Radar,   labelKey: 'threatIntelSidebar' },
    ],
  },

  // ─── 6. Privacy & Data Protection (DPO surface) ─────────────────────
  // Data Classification (Sprint 32) folds in here because the DPO owns
  // it — the NDMO 4-level classification is the same person's daily
  // tool as PDPL Art.4/Art.7 review. Asset register + Data flows are
  // nested children of the Data Classification parent.
  {
    id: 'privacy', titleKey: 'navPrivacy',
    items: [
      { id: 'pdpl-dash',  to: '/privacy/dashboard',     Icon: LayoutDashboard, labelKey: 'pdplDashSidebar' },
      { id: 'privacy',    to: '/privacy',               Icon: Fingerprint,     labelKey: 'modPdpl' },
      { id: 'privacy-lc', to: '/privacy-lifecycle',     Icon: UserCog,         labelKey: 'prcSidebar' },
      { id: 'ropa',       to: '/ropa',                  Icon: Files,           labelKey: 'ropaSidebar' },
      { id: 'data-flow',  to: '/privacy/data-flow',     Icon: Spline,          labelKey: 'dataFlowSidebar' },
      { id: 'data-lc',    to: '/data-lifecycle',        Icon: Compass,         labelKey: 'pdplExtSidebar' },
      { id: 'cyb-pdpl',   to: '/cyber-pdpl-bridge',     Icon: Workflow,        labelKey: 'cpbSidebar' },
      { id: 'dsa',        to: '/data-sharing',          Icon: Share2,          labelKey: 'dsaSidebar' },
      { id: 'proc-dd',    to: '/processor-assessments', Icon: ClipboardCheck,  labelKey: 'procSidebar' },
      { id: 'subp',       to: '/privacy/subprocessors', Icon: Share2,          labelKey: 'subpSidebar' },
      // NDMO Data Classification + NDI merged under a single "National Data
      // Governance" parent — both are SDAIA pillars, jointly owned by the
      // DPO + Data Office. Per user feedback, PDPL items above remain
      // separate; only NDMO + NDI consolidate here. NDI's measurable
      // SDAIA-compliance surfaces (cycles, framework, maturity, compliance,
      // evidence, gaps, submission) are exposed as deep-links so the DPO
      // can reach them in one click — the earlier single /ndi link hid
      // those critical features behind a tab strip.
      {
        id: 'national-data', to: '/dataclass/dashboard', Icon: Database, labelKey: 'navNationalData',
        children: [
          { id: 'dataclass-assets',  to: '/dataclass/assets',          Icon: Files,         labelKey: 'dataclass.assets' },
          { id: 'dataclass-flows',   to: '/dataclass/flows',           Icon: Share2,        labelKey: 'dataclass.flows' },
          { id: 'ndi-home',          to: '/ndi',                       Icon: Database,      labelKey: 'ndiSidebar' },
          { id: 'ndi-cycles',        to: '/ndi?tab=cycles',            Icon: Activity,      labelKey: 'ndi.cycles' },
          { id: 'ndi-framework',     to: '/ndi?tab=framework',         Icon: BookOpen,      labelKey: 'ndi.framework' },
          { id: 'ndi-maturity',      to: '/ndi?tab=maturity',          Icon: TrendingUp,    labelKey: 'ndi.maturity' },
          { id: 'ndi-compliance',    to: '/ndi?tab=compliance',        Icon: CheckCheck,    labelKey: 'ndi.compliance' },
          { id: 'ndi-evidence',      to: '/ndi?tab=evidence',          Icon: Files,         labelKey: 'ndi.evidence' },
          { id: 'ndi-gaps',          to: '/ndi?tab=gaps',              Icon: AlertOctagon,  labelKey: 'ndi.gaps' },
          { id: 'ndi-submission',    to: '/ndi?tab=submission',        Icon: Stamp,         labelKey: 'ndi.submission' },
        ],
      },
    ],
  },

  // ─── 6b. Data Officer Toolkit (DPO/CDO surface) — Sprint 33 ─────────
  // Saudi Data Officer's daily book of work: DSR (PDPL Art.9-14),
  // Consent (Art.5-6), DPIA (Art.25), Breach 72h (Art.20), Privacy
  // Notice (Art.12), Retention (Art.18), NDR (SDAIA NDR), Catalog +
  // Quality (NDMO DGSP), Ethics (SDAIA AI Ethics + NDMO Data Ethics).
  {
    id: 'data-officer', titleKey: 'navDataOfficer',
    items: [
      { id: 'do-dash',       to: '/data-officer/dashboard', Icon: LayoutDashboard, labelKey: 'dataOfficer.dashboard' },
      { id: 'do-dsr',        to: '/data-officer/dsr',       Icon: Inbox,           labelKey: 'dataOfficer.dsr' },
      { id: 'do-consents',   to: '/data-officer/consents',  Icon: ShieldCheck,     labelKey: 'dataOfficer.consents' },
      { id: 'do-dpia',       to: '/data-officer/dpia',      Icon: ScrollText,      labelKey: 'dataOfficer.dpia' },
      { id: 'do-breaches',   to: '/data-officer/breaches',  Icon: Siren,           labelKey: 'dataOfficer.breaches' },
      { id: 'do-notices',    to: '/data-officer/notices',   Icon: BookOpen,        labelKey: 'dataOfficer.notices' },
      { id: 'do-retention',  to: '/data-officer/retention', Icon: Archive,         labelKey: 'dataOfficer.retention' },
      { id: 'do-ndr',        to: '/data-officer/ndr',       Icon: ScrollText,      labelKey: 'dataOfficer.ndr' },
      { id: 'do-catalog',    to: '/data-officer/catalog',   Icon: Database,        labelKey: 'dataOfficer.catalog' },
      { id: 'do-quality',    to: '/data-officer/quality',   Icon: Sigma,           labelKey: 'dataOfficer.quality' },
      { id: 'do-ethics',     to: '/data-officer/ethics',    Icon: Scale,           labelKey: 'dataOfficer.ethics' },
    ],
  },

  // ─── 7. Business Continuity (BCM Manager surface) ───────────────────
  {
    id: 'bcm', titleKey: 'navBcm',
    items: [
      { id: 'bcm',             to: '/bcm',                   Icon: ShieldCheck,    labelKey: 'bcmWorkspaceSidebar' },
      { id: 'bcm-dash',        to: '/bcm/dashboard',         Icon: LayoutDashboard, labelKey: 'bcmDashSidebar' },
      { id: 'bcm-readiness',   to: '/bcm/readiness',         Icon: Activity,       labelKey: 'bcmReadinessSidebar' },
      { id: 'bcm-cascade',     to: '/bcm/dependency-cascade', Icon: Network,       labelKey: 'cascadeSidebar' },
      { id: 'bcm-dr-trend',    to: '/bcm/dr-test-trend',     Icon: BarChart3,     labelKey: 'drTrendSidebar' },
      {
        id: 'bia-wizard', to: '/bcm/bia', Icon: ClipboardCheck, labelKey: 'biaWizardSidebar',
        children: [
          { id: 'bia-quantitative', to: '/bcm/bia-quantitative', Icon: Sigma,      labelKey: 'biaQuantSidebar' },
          { id: 'strategy-roi',     to: '/bcm/strategy-roi',     Icon: TrendingUp, labelKey: 'strategyRoiSidebar' },
          { id: 'mtpd-inh',         to: '/bcm/mtpd-inheritance', Icon: GitBranch,  labelKey: 'mtpdInhSidebar' },
        ],
      },
      { id: 'scenario-library', to: '/bcm/scenarios',       Icon: BookOpen,       labelKey: 'scenarioLibrarySidebar' },
      { id: 'bcp-builder',      to: '/bcm/plans',           Icon: FileText,       labelKey: 'bcpBuilderSidebar' },
      { id: 'dr-plans',         to: '/dr-plans',            Icon: Server,         labelKey: 'drpSidebar' },
      {
        id: 'crisis-room', to: '/bcm/crisis-room', Icon: Siren, labelKey: 'crisisRoomSidebar',
        children: [
          { id: 'crisis-mobile', to: '/crisis/mobile',       Icon: Siren,    labelKey: 'crisisMobileSidebar' },
          { id: 'crisis-comms',  to: '/bcm/comms/templates', Icon: Megaphone, labelKey: 'crisisCommsSidebar' },
          { id: 'siem-bridge',   to: '/bcm/siem-events',     Icon: Activity, labelKey: 'siemBridgeSidebar' },
        ],
      },
      { id: 'bcm-remediation', to: '/bcm/remediation', Icon: AlertOctagon, labelKey: 'bcmRemediationSidebar' },
      { id: 'bcm-compliance',  to: '/bcm/compliance',  Icon: Stamp,        labelKey: 'bcmComplianceSidebar' },
      { id: 'bcm-mgmt-reviews', to: '/bcm/management-reviews', Icon: ClipboardList, labelKey: 'bcmMgmtReviewsSidebar' },
      { id: 'bcm-reports',     to: '/bcm/reports',     Icon: BarChart3,    labelKey: 'bcmReportsSidebar' },
      { id: 'opres-dash',      to: '/opres/dashboard', Icon: Shield,       labelKey: 'opres.sidebar' },
      { id: 'rrp-dash',        to: '/rrp/dashboard',   Icon: ShieldAlert,  labelKey: 'rrp.sidebar' },
    ],
  },

  // ─── 8. Third-Party & Outsourcing (TPRM Manager surface) ────────────
  // Outsourcing & Cloud (Sprint 30) consolidated here because in
  // Saudi banking practice the TPRM Manager owns vendor onboarding,
  // and SAMA Outsourcing Reg §3-§8 + Cloud Computing Framework are
  // vendor governance, not generic compliance.
  {
    id: 'third-party', titleKey: 'navThirdParty',
    items: [
      { id: 'scm-dash',      to: '/scm/dashboard',         Icon: LayoutDashboard, labelKey: 'scmDashSidebar' },
      { id: 'tprm',          to: '/tprm',                  Icon: Workflow,        labelKey: 'modThirdParty' },
      { id: 'outsourcing',   to: '/tprm/outsourcing',      Icon: Briefcase,       labelKey: 'outsourcingSidebar' },
      {
        id: 'outsourcing-cloud', to: '/outsourcing/dashboard', Icon: Cloud,
        labelKey: 'outsourcingCloud.sidebar',
        children: [
          { id: 'outsourcing-arr', to: '/outsourcing/arrangements',   Icon: Briefcase, labelKey: 'outsourcingCloud.arrangements' },
          { id: 'outsourcing-svc', to: '/outsourcing/cloud-services', Icon: Cloud,     labelKey: 'outsourcingCloud.cloudServices' },
        ],
      },
      { id: 'vendor-bcp',    to: '/tprm/bcp-campaigns',  Icon: ClipboardCheck, labelKey: 'vbcpSidebar' },
      { id: 'vendor-portal', to: '/portal/vendor',       Icon: Briefcase,      labelKey: 'vpSidebar' },
      { id: 'scm',           to: '/scm',                 Icon: Truck,          labelKey: 'scmEyebrow' },
      { id: 'srp',           to: '/supplier-resilience', Icon: ShieldCheck,    labelKey: 'srpSidebar' },
      { id: 'clauses',       to: '/contract-clauses',    Icon: Stamp,          labelKey: 'cclSidebar' },
      { id: 'fourth-party',  to: '/fourth-party',        Icon: Network,        labelKey: 'f4pSidebar' },
      { id: 'tpc',           to: '/third-party-cyber',   Icon: Bug,            labelKey: 'tpcSidebar' },
      { id: 'res-tests',     to: '/resilience-tests',    Icon: Activity,       labelKey: 'srtSidebar' },
    ],
  },

  // ─── 9. AI & Model Risk (AI Officer / Model Validator surface) ──────
  // AI Registry + AI Lifecycle (validations, retrainings, recerts) are
  // one operational domain — the Model Validator opens both surfaces
  // every day. Splitting them across two groups (as before) forced an
  // extra mental hop.
  {
    id: 'ai-model-risk', titleKey: 'navAiGovernance',
    items: [
      { id: 'ai-models', to: '/ai/models', Icon: Bot, labelKey: 'aiModelRegistrySidebar' },
      { id: 'ai-suggestions', to: '/ai/suggestions', Icon: Sparkles, labelKey: 'aiSuggestionsSidebar' },
      {
        id: 'ai-lifecycle', to: '/ai-lifecycle/dashboard', Icon: Activity,
        labelKey: 'aiLifecycle.sidebar',
        children: [
          { id: 'ai-validations', to: '/ai-lifecycle/validations',      Icon: ClipboardCheck, labelKey: 'aiLifecycle.validations' },
          { id: 'ai-retrainings', to: '/ai-lifecycle/retrainings',      Icon: Activity,       labelKey: 'aiLifecycle.retrainings' },
          { id: 'ai-recerts',     to: '/ai-lifecycle/recertifications', Icon: Stamp,          labelKey: 'aiLifecycle.recertifications' },
        ],
      },
    ],
  },

  // ─── 10. Ethics & Conduct (Compliance Officer / OmbudsP surface) ────
  // Split out from compliance because investigation cases (whistleblower,
  // COI, fraud, customer complaints) are a distinct operational rhythm
  // — they have triage, intake, investigator assignment, evidence chain,
  // and disposition, which generic compliance pages don't. The Ombudsman
  // or Ethics Officer owns these as a single book of work.
  {
    id: 'ethics', titleKey: 'navEthicsConduct',
    items: [
      { id: 'ethics',     to: '/ethics',                     Icon: ShieldAlert,   labelKey: 'modEthics' },
      { id: 'wb-cases',   to: '/ethics/whistleblower/cases', Icon: Megaphone,     labelKey: 'whistleblowerSidebar' },
      { id: 'wb-retal',   to: '/ethics/whistleblower/retaliation', Icon: ShieldCheck, labelKey: 'wbRetaliationSidebar' },
      { id: 'coi',        to: '/ethics/coi',                 Icon: Scale,         labelKey: 'coi.title' },
      { id: 'complaints', to: '/complaints',                 Icon: MessageSquare, labelKey: 'complaints.sidebar' },
      { id: 'fraud',      to: '/fraud/dashboard',            Icon: ShieldX,       labelKey: 'fraud.sidebar' },
    ],
  },

  // ─── 11. Regulatory Affairs (Government Relations surface) ──────────
  // KSA-specific regulator gateway — sanctions, Sharia, open banking,
  // and the HRSD/MOC tax/labor surfaces. This is OUR differentiator vs.
  // imported GRC stacks (which don't know about NCA ECC, SAMA Sharia
  // §7, or HRSD Nitaqat).
  {
    id: 'reg-affairs', titleKey: 'navSaudiRegulators',
    items: [
      { id: 'authorities', to: '/authorities',             Icon: Landmark,  labelKey: 'modAuthorities' },
      { id: 'sanctions',   to: '/aml/sanctions/dashboard', Icon: ScanSearch, labelKey: 'sanctionsSidebar' },
      { id: 'sharia',      to: '/sharia/dashboard',        Icon: Moon,       labelKey: 'sharia.sidebar' },
      { id: 'openbanking', to: '/openbanking/dashboard',   Icon: Banknote,   labelKey: 'ob.sidebar' },
      { id: 'hrsd',        to: '/authority/HRSD',          Icon: Users,      labelKey: 'modHrsd' },
      { id: 'moc',         to: '/authority/MOC',           Icon: Building2,  labelKey: 'modMoc' },
    ],
  },

  // ─── 12. ESG & Climate (Sustainability Officer surface) ─────────────
  // Split from Compliance because SUS-Officer's cadence (TCFD scenario
  // refresh, PCAF emissions calc, annual climate report) is different
  // from a CCO's quarterly obligation review.
  {
    id: 'esg', titleKey: 'navEsgClimate',
    items: [
      { id: 'esg',           to: '/esg',                    Icon: Leaf,           labelKey: 'esgSidebar' },
      { id: 'tcfd-scn',      to: '/esg/tcfd-scenarios',     Icon: CloudLightning, labelKey: 'tcfdScenariosSidebar' },
      { id: 'fin-emissions', to: '/esg/financed-emissions', Icon: Factory,        labelKey: 'financedEmissionsSidebar' },
      { id: 'climate-rpt',   to: '/esg/climate-report',     Icon: FileSearch,     labelKey: 'climateReportSidebar' },
    ],
  },

  // ─── 13. Reporting & Outputs (cross-cutting) ────────────────────────
  // Where every "what came out of the platform" surface lives — board
  // pack, scheduled reports, communications, service catalog, CAPA,
  // knowledge base.
  {
    id: 'reporting', titleKey: 'navReporting',
    items: [
      { id: 'intake',     to: '/intake',            Icon: Send,     labelKey: 'intakeCatalogTitle' },
      { id: 'capa',       to: '/capa',              Icon: Wrench,   labelKey: 'modCapa' },
      { id: 'board-pack', to: '/board-pack',        Icon: FileText, labelKey: 'boardPackSidebar' },
      { id: 'reports-centre', to: '/reports/centre', Icon: Files,   labelKey: 'reportsCentreSidebar' },
      { id: 'schedules',  to: '/reports/schedules', Icon: Calendar, labelKey: 'rsTitle' },
      { id: 'kb',         to: '/knowledge-base',    Icon: BookOpen, labelKey: 'kbSidebar' },
      { id: 'comms',      to: '/communications',    Icon: Send,     labelKey: 'modCommunications' },
    ],
  },

  // ─── 14. Administration (Platform Admin surface) ────────────────────
  // Bottom of nav per every market-leader pattern. Pinned admin items —
  // users, licensing, committees, SSO, workflow versions, connectors,
  // notification channels, file upload, onboarding, settings.
  {
    id: 'administration', titleKey: 'navAdministration',
    items: [
      { id: 'admin',         to: '/admin',                  Icon: UserCog,      labelKey: 'adminTitle' },
      { id: 'licensing',     to: '/admin/licensing',        Icon: KeyRound,     labelKey: 'licensingSidebar' },
      { id: 'help-topics',   to: '/admin/help-topics',      Icon: HelpCircle,   labelKey: 'helpTopicsSidebar' },
      {
        id: 'committees', to: '/committees', Icon: LandmarkIcon, labelKey: 'comSidebar',
        children: [
          { id: 'com-catalog', to: '/committees/catalog', Icon: BookOpen, labelKey: 'comCatalogSidebar' },
        ],
      },
      { id: 'sso',           to: '/sso',                    Icon: LockKeyhole,  labelKey: 'ssoSidebar' },
      { id: 'wf-versions',   to: '/workflow-versions',      Icon: Layers,       labelKey: 'wvSidebar' },
      { id: 'connectors',    to: '/connectors',             Icon: Plug,         labelKey: 'connectorsTitle' },
      { id: 'audit-log',     to: '/audit-log',              Icon: ScrollText,   labelKey: 'auditLogTitle' },
      { id: 'sessions',      to: '/account/sessions',       Icon: KeyRound,     labelKey: 'sessionsTitle' },
      { id: 'issues',        to: '/issues',                 Icon: ClipboardList, labelKey: 'issuesTitle' },
      {
        id: 'notif-dispatch', to: '/notifications/dispatch', Icon: Send, labelKey: 'ndSidebar',
        children: [
          { id: 'nc', to: '/notifications/channels', Icon: Send, labelKey: 'ncSidebar' },
        ],
      },
      { id: 'file-upload',   to: '/file-upload',            Icon: FileCheck2,   labelKey: 'fuSidebar' },
      { id: 'onboarding',    to: '/onboarding',             Icon: Compass,      labelKey: 'onbSidebar' },
      { id: 'settings',      to: '/settings',               Icon: Settings,     labelKey: 'itemSettings' },
    ],
  },
];

/** Sidebar inbox counters — one place to declare every "what's waiting
 *  on me?" number the nav can render. */
interface InboxCounts {
  pendingReviews: number | null;
  capaOpen: number | null;
  capaOverdue: number | null;
  unreadNotifications: number | null;
}
function useInboxCounts(): InboxCounts {
  const [counts, setCounts] = useState<InboxCounts>({
    pendingReviews: null, capaOpen: null, capaOverdue: null,
    unreadNotifications: null,
  });
  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const [subs, capa, notif] = await Promise.all([
          workflowSubmissionApi.countsByWorkflow().catch(() => null),
          capaTaskApi.counts().catch(() => null),
          notificationApi.unreadCount().catch(() => null),
        ]);
        if (cancelled) return;
        setCounts({
          pendingReviews: subs ? subs.reduce((s, r) => s + r.open, 0) : null,
          capaOpen:       capa ? capa.open    : null,
          capaOverdue:    capa ? capa.overdue : null,
          unreadNotifications: notif ? notif.count : null,
        });
      } catch { /* unauthenticated or backend down — leave as nulls */ }
    }
    void load();
    function onFocus() { if (document.visibilityState === 'visible') void load(); }
    document.addEventListener('visibilitychange', onFocus);
    return () => { cancelled = true; document.removeEventListener('visibilitychange', onFocus); };
  }, []);
  return counts;
}

// ─── Expand/collapse state persistence ──────────────────────────────
//
// We track two persisted sets:
//   1. `collapsedGroups`  — groups the user has explicitly collapsed.
//      Active route still force-expands its group so the user can see
//      where they are; the user's collapse only applies to inactive
//      groups.
//   2. `expandedItems` — items whose children the user has explicitly
//      expanded. Combined with "auto-expand if any child is active",
//      so the user's manual expansion sticks even after they
//      navigate away — fixing the "click slice and exit returns to
//      upper" complaint.

const STORAGE_COLLAPSED = 'sb.collapsedGroups.v2';
const STORAGE_EXPANDED  = 'sb.expandedItems.v2';
const STORAGE_SCROLL    = 'sb.scrollTop.v2';

function readSet(key: string): Set<string> {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return new Set();
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? new Set(arr.filter((x): x is string => typeof x === 'string')) : new Set();
  } catch {
    return new Set();
  }
}
function writeSet(key: string, set: Set<string>) {
  try { localStorage.setItem(key, JSON.stringify([...set])); } catch { /* quota or private mode */ }
}

/** True iff `pathname` falls under `to` — either an exact match or
 *  one level deeper. We do NOT use plain `startsWith(to)` because
 *  `/risk` would falsely match `/risk-lifecycle`. */
function pathMatches(pathname: string, to: string): boolean {
  if (pathname === to) return true;
  return pathname.startsWith(to + '/');
}

/** Active-state for a child whose sibling set uses query-string tabs
 *  (e.g. /ndi?tab=cycles). NavLink matches on pathname only and ignores
 *  the query string, so without this every NDI tab — plus the bare /ndi
 *  link — highlighted simultaneously. A query child is active iff the path
 *  matches AND all of its query params equal the current location's; a
 *  bare-path child (no query) is active only when no query is present. */
function queryAwareChildActive(to: string, pathname: string, search: string): boolean {
  const qIdx = to.indexOf('?');
  const path = qIdx >= 0 ? to.slice(0, qIdx) : to;
  if (pathname !== path && !pathname.startsWith(path + '/')) return false;
  const current = new URLSearchParams(search);
  if (qIdx < 0) return [...current.keys()].length === 0;
  const want = new URLSearchParams(to.slice(qIdx + 1));
  for (const [k, v] of want) if (current.get(k) !== v) return false;
  return true;
}

function itemContainsActive(item: NavItem, pathname: string): boolean {
  if (pathMatches(pathname, item.to)) return true;
  return !!item.children?.some(c => pathMatches(pathname, c.to));
}

function groupContainsActive(group: NavGroup, pathname: string): boolean {
  return group.items.some(it => itemContainsActive(it, pathname));
}

export function Sidebar({ open = false }: { open?: boolean }) {
  const { t } = useI18n();
  const counts = useInboxCounts();
  const { pathname, search } = useLocation();
  const asideRef = useRef<HTMLElement>(null);

  // Manual collapse/expand state — read from localStorage on first
  // render so a refresh preserves the user's last layout. The current
  // route always force-expands the group/item containing it, even if
  // the user previously collapsed that group, so they can always see
  // "you are here".
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(() => readSet(STORAGE_COLLAPSED));
  const [expandedItems, setExpandedItems]   = useState<Set<string>>(() => readSet(STORAGE_EXPANDED));

  useEffect(() => { writeSet(STORAGE_COLLAPSED, collapsedGroups); }, [collapsedGroups]);
  useEffect(() => { writeSet(STORAGE_EXPANDED,  expandedItems);   }, [expandedItems]);

  // Sidebar scroll memory. Continuously save scrollTop as the user
  // scrolls; restore it on every navigation so the sidebar never snaps
  // back to the top — the "click slice and exit returns to upper"
  // complaint. We also restore on full mount (cold reload).
  useEffect(() => {
    const el = asideRef.current;
    if (!el) return;
    const save = () => {
      try { sessionStorage.setItem(STORAGE_SCROLL, String(el.scrollTop)); } catch {}
    };
    el.addEventListener('scroll', save, { passive: true });
    return () => { save(); el.removeEventListener('scroll', save); };
  }, []);

  // Restore scroll on every route change / mount. Because each page
  // renders its own <Shell>, the Sidebar is a *fresh* instance on every
  // navigation — it always mounts at scrollTop 0. We MUST re-apply the
  // saved position synchronously, before the browser paints, or the user
  // sees the nav flash to the top and then jump back down (the "click a
  // slice and exit returns to upper" complaint). useLayoutEffect runs
  // after the DOM is laid out (active-class + auto-expand already applied)
  // but before paint, so there is no visible top-flash. A post-paint
  // useEffect + microtask — the previous approach — paints at the top
  // first and is exactly what made the reset visible.
  useLayoutEffect(() => {
    const el = asideRef.current;
    if (!el) return;
    const saved = sessionStorage.getItem(STORAGE_SCROLL);
    if (!saved) return;
    const n = Number(saved);
    if (Number.isNaN(n)) return;
    el.scrollTop = n;
  }, [pathname]);

  function toggleGroup(id: string) {
    setCollapsedGroups(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }
  function toggleItem(id: string) {
    setExpandedItems(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  function badgeFor(id: string): NavBadge | null {
    if (id === 'wf-submissions' && counts.pendingReviews && counts.pendingReviews > 0) {
      return {
        value: counts.pendingReviews,
        title: `${counts.pendingReviews} pending review${counts.pendingReviews === 1 ? '' : 's'}`,
      };
    }
    if (id === 'notifications' && counts.unreadNotifications && counts.unreadNotifications > 0) {
      return {
        value: counts.unreadNotifications,
        title: counts.unreadNotifications === 1
          ? '1 unread notification'
          : `${counts.unreadNotifications} unread notifications`,
      };
    }
    if (id === 'capa' && counts.capaOpen && counts.capaOpen > 0) {
      const overdue = counts.capaOverdue ?? 0;
      return {
        value: counts.capaOpen,
        title: overdue > 0
          ? `${counts.capaOpen} open · ${overdue} overdue`
          : `${counts.capaOpen} open CAPA${counts.capaOpen === 1 ? '' : 's'}`,
        tone: overdue > 0 ? 'danger' : undefined,
      };
    }
    return null;
  }

  // Auto-expand sets — derived, not stored. Computed every render
  // from the active route, then OR'd with the user's manual sets.
  const autoExpandGroups = useMemo(() => {
    const s = new Set<string>();
    for (const g of NAV_GROUPS) if (groupContainsActive(g, pathname)) s.add(g.id);
    return s;
  }, [pathname]);
  const autoExpandItems = useMemo(() => {
    const s = new Set<string>();
    for (const g of NAV_GROUPS) {
      for (const it of g.items) {
        if (it.children && it.children.some(c => pathMatches(pathname, c.to))) s.add(it.id);
      }
    }
    return s;
  }, [pathname]);

  return (
    <aside ref={asideRef} className={open ? 'sb sb-open' : 'sb'}>
      <div className="sb-brand">
        <div className="sb-mark"><ShieldCheck size={22} strokeWidth={2} /></div>
        <div>
          <div className="sb-title">{t('brandName')}</div>
          <div className="sb-sub">{t('brandSub')}</div>
        </div>
      </div>

      {NAV_GROUPS.map(group => {
        const isPinned = !!group.pinned;
        // Group is "shown" if: pinned, OR contains active route, OR user
        // hasn't collapsed it. Active route always wins so the user
        // can see where they are.
        const userCollapsed = collapsedGroups.has(group.id);
        const isExpanded = isPinned || autoExpandGroups.has(group.id) || !userCollapsed;
        return (
          <div key={group.id}>
            <button
              type="button"
              className={cn('sb-section', !isPinned && 'sb-section-toggle', isExpanded && 'sb-section-open')}
              onClick={() => { if (!isPinned) toggleGroup(group.id); }}
              disabled={isPinned}
              aria-expanded={isExpanded}
              aria-controls={`sb-group-${group.id}`}
            >
              {!isPinned && (
                <ChevronRight
                  size={11}
                  strokeWidth={2.5}
                  className={cn('sb-chevron', isExpanded && 'sb-chevron-open')}
                />
              )}
              <span>{t(group.titleKey)}</span>
            </button>
            {isExpanded && (
              <div id={`sb-group-${group.id}`}>
                {group.items.map(it => (
                  <NavRowWithChildren
                    key={it.id}
                    item={it}
                    t={t}
                    badge={badgeFor(it.id)}
                    pathname={pathname}
                    search={search}
                    expandedItems={expandedItems}
                    autoExpandItems={autoExpandItems}
                    onToggleChildren={toggleItem}
                  />
                ))}
              </div>
            )}
          </div>
        );
      })}
    </aside>
  );
}

interface NavBadge { value: number; title: string; tone?: 'danger'; }

/** A nav row that knows how to render its children indented underneath
 *  when expanded. Parent's own NavLink is unchanged; children render
 *  with a vertical guide line + reduced size so the hierarchy reads at
 *  a glance. */
function NavRowWithChildren({
  item, t, badge, pathname, search, expandedItems, autoExpandItems, onToggleChildren,
}: {
  item: NavItem;
  t: (k: string) => string;
  badge: NavBadge | null;
  pathname: string;
  search: string;
  expandedItems: Set<string>;
  autoExpandItems: Set<string>;
  onToggleChildren: (id: string) => void;
}) {
  const hasChildren = !!item.children && item.children.length > 0;
  const childrenUseQuery = !!item.children?.some(c => c.to.includes('?'));
  const childrenOpen = hasChildren && (
    expandedItems.has(item.id) || autoExpandItems.has(item.id) || pathMatches(pathname, item.to)
  );

  return (
    <>
      <div className="sb-item-wrap">
        <NavLink to={item.to} className={({ isActive }) => cn('sb-item', isActive && 'active', hasChildren && 'sb-item-has-children')}>
          <item.Icon size={18} strokeWidth={1.8} />
          <span>{t(item.labelKey)}</span>
          {badge && (
            <span className={cn('sb-badge', badge.tone === 'danger' && 'sb-badge-danger')}
                  title={badge.title}>
              {badge.value}
            </span>
          )}
        </NavLink>
        {hasChildren && (
          <button
            type="button"
            className={cn('sb-item-toggle', childrenOpen && 'sb-item-toggle-open')}
            aria-label={childrenOpen ? 'Collapse subitems' : 'Expand subitems'}
            onClick={(e) => { e.preventDefault(); onToggleChildren(item.id); }}
          >
            <ChevronRight size={13} strokeWidth={2.4} />
          </button>
        )}
      </div>
      {hasChildren && childrenOpen && (
        <div className="sb-children">
          {item.children!.map(child => {
            // When a parent's children use query-string tabs (NDI), match on
            // path+query so only the active tab highlights — not all of them.
            const forced = childrenUseQuery
              ? queryAwareChildActive(child.to, pathname, search)
              : null;
            return (
              <NavLink key={child.id} to={child.to}
                className={({ isActive }) => cn('sb-item sb-item-child', (forced ?? isActive) && 'active')}>
                <child.Icon size={14} strokeWidth={1.8} />
                <span>{t(child.labelKey)}</span>
              </NavLink>
            );
          })}
        </div>
      )}
    </>
  );
}

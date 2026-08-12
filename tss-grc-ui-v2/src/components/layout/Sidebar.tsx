import { useEffect, useMemo, useRef, useState } from 'react';
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
         Cloud, ChevronRight, ScrollText, Archive, GraduationCap, Download,
         Calculator, Radar, Spline, Gauge, Sparkles, HelpCircle,
         Gavel,
         type LucideIcon } from 'lucide-react';
import { NavLink, useLocation } from 'react-router-dom';
import { useI18n } from '../../lib/i18n';
import { cn } from '../../lib/cn';
import { capaTaskApi, notificationApi, workflowSubmissionApi } from '../../lib/api';
import { filterNavByEntitlement, useEntitlement } from '../../lib/entitlement';
import { canExport } from '../../lib/auth';

interface NavItem {
  id: string;
  to: string;
  Icon: LucideIcon;
  labelKey: string;
  /** Sub-items rendered indented under the parent when the parent's path
   *  prefix is on the active route or the user has expanded the parent. */
  children?: NavItem[];
  /**
   * Optional non-entitlement visibility check — a *permission*, not a licence.
   * Applied by `filterNavByEntitlement` alongside the SKU gate. Use it only where
   * the server would actually refuse: hiding a control the server permits is the
   * defect `canExport`'s own comment warns about, pointing the other way.
   */
  gate?: () => boolean;
}
interface NavGroup {
  id: string;
  titleKey: string;
  items: NavItem[];
  /** Always expanded — Home/Workspace never collapses. */
  pinned?: boolean;
}

/**
 * Sidebar — domain-pillar IA modelled on the global GRC market leaders
 * (ServiceNow IRM, RSA Archer, IBM OpenPages, MetricStream, OneTrust,
 * AuditBoard, Diligent).
 *
 * <p><b>Design principles (committee of GRC subject-matter experts):</b>
 * <ol>
 *   <li><b>Domain pillars</b> ordered by the Three Lines of Defense ×
 *       frequency. A pinned <i>My Work</i> cockpit sits at the top and
 *       <i>Administration</i> at the very bottom — every other section is a
 *       collapsible accountable-owner domain (CRO, CCO, CAE, CISO, DPO,
 *       BCM, TPRM, Ethics, Regulatory, Sustainability, AI Officer).</li>
 *   <li><b>Dashboard-first</b> — each pillar opens with its dashboard /
 *       overview, then the register / workspace, then the tools.</li>
 *   <li><b>Progressive disclosure</b> — analytics, lifecycle views and
 *       sub-registers nest under a "hub" parent (one level) so each pillar
 *       stays scannable instead of dumping a 16-row flat list.</li>
 *   <li><b>Consistent naming</b> — domain nouns that match the leaders
 *       ("Risk", "Compliance", "Internal Audit", "Cyber &amp; IT Risk",
 *       "Resilience &amp; Continuity").</li>
 *   <li>Active route auto-expands its group + its parent item's children;
 *       the user's manual collapse/expand survives navigation (localStorage),
 *       and scroll position persists natively (the shell mounts this once).</li>
 * </ol>
 *
 * <p><b>Pillar order:</b><br>
 * My Work → Risk Management → Compliance &amp; Policy → Internal Audit →
 * Cyber &amp; IT Risk → Privacy &amp; Data Protection → Third-Party Risk →
 * Resilience &amp; Continuity → Ethics &amp; Investigations →
 * Regulatory Affairs → ESG &amp; Climate → AI &amp; Model Risk →
 * Insights &amp; Reporting → Administration.
 */
const NAV_GROUPS: NavGroup[] = [
  // ─── 0. My Work (pinned) ────────────────────────────────────────────
  // The universal "what's on me today" cockpit — tasks, approvals, queues.
  // Pinned because every role hits these every session.
  {
    id: 'my-work', titleKey: 'navMyWork', pinned: true,
    items: [
      { id: 'dashboard',     to: '/dashboard',             Icon: LayoutDashboard, labelKey: 'itemDashboard' },
      { id: 'workflow-hub',  to: '/workflows',             Icon: Workflow,        labelKey: 'wfHubSidebar' },
      { id: 'wf-submissions',to: '/workflows/submissions', Icon: ClipboardCheck,  labelKey: 'wfSubmissionsSidebar' },
      { id: 'wf-requests',   to: '/workflows/requests',    Icon: Send,            labelKey: 'wfRequestsSidebar' },
      { id: 'approvals',     to: '/approvals',             Icon: CheckCheck,      labelKey: 'approvalsSidebar' },
      { id: 'signatures',    to: '/signatures',            Icon: PenLine,         labelKey: 'sigSidebar' },
      { id: 'inbox',         to: '/intake-inbox',          Icon: Inbox,           labelKey: 'inboxTitle' },
      { id: 'notifications', to: '/notifications',         Icon: Bell,            labelKey: 'inboxNotifSidebar' },
      { id: 'calendar',      to: '/calendar',              Icon: Calendar,        labelKey: 'calendarSidebar' },
      { id: 'lc-overview',   to: '/lifecycle-overview',    Icon: Activity,        labelKey: 'loSidebar' },
    ],
  },

  // ─── 1. Risk Management (CRO) ───────────────────────────────────────
  {
    id: 'risk', titleKey: 'navRisk',
    items: [
      { id: 'risk-dash', to: '/risk/dashboard',     Icon: LayoutDashboard, labelKey: 'ermDashSidebar' },
      { id: 'risk',      to: '/risk',               Icon: AlertTriangle,   labelKey: 'modRisk' },
      { id: 'kri',       to: '/risk/kri/dashboard',  Icon: Gauge,          labelKey: 'kriSidebar' },
      { id: 'emerging',  to: '/emerging-risk',      Icon: Telescope,       labelKey: 'emrSidebar' },
      {
        id: 'incidents', to: '/incidents', Icon: AlertOctagon, labelKey: 'incidentsTitle',
        children: [
          { id: 'rca', to: '/rca', Icon: Microscope, labelKey: 'rcaSidebar' },
        ],
      },
      // CE-0: `changes` used to sit here. Change Enablement is its own discipline with its own
      // authority (CAB), not a risk technique — see the group below and §6c of the plan.
      { id: 'cpty',      to: '/cpty/dashboard',     Icon: Users,           labelKey: 'cpty.sidebar' },
      // Risk-analysis techniques nested under one hub (ISO 31010).
      {
        id: 'risk-analysis', to: '/risk-scenarios', Icon: Sigma, labelKey: 'scnSidebar',
        children: [
          { id: 'bowtie',    to: '/risk/bowtie', Icon: GitBranch,   labelKey: 'bowtieSidebar' },
          { id: 'fair-calc', to: '/risk/fair',   Icon: Calculator,  labelKey: 'fairCalcSidebar' },
        ],
      },
      { id: 'risk-lc',   to: '/risk-lifecycle',     Icon: GitBranch,       labelKey: 'rlcSidebar' },
    ],
  },

  // ─── 1b. Change Enablement (CAB) ────────────────────────────────────
  // CE-0. Change was nested inside Risk Management, which is the wrong reading of what it is: a
  // change is authorised by the CAB under its own policy, and the backend was never under risk
  // (module/change_mgmt is its own package). The nav was the only place the two were conflated.
  //
  // ⚠ Only the entries that resolve TODAY are listed — nav-to-nowhere is the F2-02 defect this
  // programme spent two slices removing, and `navChangeGroup.test.ts` now holds the invariant
  // directly: every row here must have a matching <Route> in App.tsx. CE-13 gave the CAB queue one,
  // so it has a row; the decision register is still a download inside the governance tab.
  {
    id: 'change', titleKey: 'navChange',
    items: [
      { id: 'changes',   to: '/changes',                Icon: GitBranch, labelKey: 'ceNavRegister' },
      // The calendar and freeze/blackout windows (FSC) are a tab of the same page, so this is a
      // query link — the pattern the NDI group established. `queryAwareActive` below is what
      // keeps only one of the two rows highlighted.
      { id: 'change-gov', to: '/changes?tab=governance', Icon: Calendar,  labelKey: 'ceNavCalendarFsc' },
      // CE-13 — the row CE-0 deliberately withheld: "the CAB queue and decision register are
      // deliberately absent until S7/CE-13 give them routes", because nav-to-nowhere is the F2-02
      // defect that slice removed from this very group. It now has a route, so it gets a row.
      { id: 'change-cab', to: '/changes/cab',            Icon: Gavel,     labelKey: 'cabNavQueue' },
    ],
  },

  // ─── 2. Compliance & Policy (CCO) ───────────────────────────────────
  {
    id: 'compliance', titleKey: 'navCompliancePolicy',
    items: [
      { id: 'compliance',   to: '/compliance',             Icon: ClipboardCheck, labelKey: 'modCompliance' },
      { id: 'obligations',  to: '/compliance/obligations', Icon: FileCheck2,     labelKey: 'obligationRegisterSidebar' },
      // Controls hub — library + the two mapping tools nested.
      {
        id: 'ctrl-library', to: '/controls/library', Icon: ShieldCheck, labelKey: 'ctrlLibrarySidebar',
        children: [
          { id: 'crosswalk',   to: '/control-crosswalk', Icon: GitBranch, labelKey: 'cwSidebar' },
          { id: 'ctrl-ai-map', to: '/controls/ai-mapper', Icon: Bot,      labelKey: 'aiCtrlMapSidebar' },
        ],
      },
      { id: 'ccm-dash',     to: '/ccm/dashboard',          Icon: Activity,       labelKey: 'ccmDashSidebar' },
      { id: 'posture-hub',  to: '/compliance/posture',     Icon: BarChart3,      labelKey: 'postureHubSidebar' },
      { id: 'fw-packs',     to: '/framework-packs',        Icon: Layers,         labelKey: 'fpSidebar' },
      // Policy hub — management + templates.
      {
        id: 'pol-mgmt', to: '/policy-management', Icon: ShieldCheck, labelKey: 'polMgmtSidebar',
        children: [
          { id: 'pol-tpl', to: '/policy-templates', Icon: BookOpen, labelKey: 'ptSidebar' },
        ],
      },
      { id: 'attestations', to: '/attestations',           Icon: FileCheck2,     labelKey: 'campaignsTitle' },
      { id: 'evidence',     to: '/evidence',               Icon: Files,          labelKey: 'evidenceTitle' },
      { id: 'grc-expert',   to: '/expert',                 Icon: GraduationCap,  labelKey: 'expert.sidebar' },
      // Regulatory intelligence hub — library + change + horizon scanning.
      {
        id: 'reg-intel', to: '/regulatory/library', Icon: Landmark, labelKey: 'regLibrarySidebar',
        children: [
          { id: 'reg-change',  to: '/regulatory-change', Icon: Newspaper, labelKey: 'regChangeSidebar' },
          { id: 'reg-horizon', to: '/regulatory/horizon', Icon: Compass,  labelKey: 'regHorizonSidebar' },
        ],
      },
    ],
  },

  // ─── 3. Internal Audit (CAE — Line 3) ───────────────────────────────
  {
    id: 'audit', titleKey: 'navAudit',
    items: [
      { id: '3lod',        to: '/governance/three-lines', Icon: ShieldCheck,   labelKey: '3lodSidebar' },
      { id: 'audit-ws',    to: '/audit-workspace',        Icon: Stamp,         labelKey: 'modAuditWorkspace' },
      { id: 'ia-universe', to: '/audit/ia/universe',      Icon: Telescope,     labelKey: 'iaUniverseSidebar' },
      { id: 'ia-plan',     to: '/audit/ia/plans',         Icon: CalendarDays,  labelKey: 'iaPlanSidebar' },
      { id: 'audit-lc',    to: '/audit-lifecycle',        Icon: BookOpen,      labelKey: 'alcSidebar' },
      { id: 'ev-chain',    to: '/evidence-chain',         Icon: Fingerprint,   labelKey: 'echSidebar' },
    ],
  },

  // ─── 4. Cyber & IT Risk (CISO) ──────────────────────────────────────
  {
    id: 'cyber', titleKey: 'navCyber',
    items: [
      { id: 'cyber-dash', to: '/cyber/dashboard',      Icon: LayoutDashboard, labelKey: 'cyberDashSidebar' },
      { id: 'cyber',      to: '/cyber',                Icon: LockKeyhole,     labelKey: 'modCyber' },
      {
        id: 'socops', to: '/socops', Icon: Siren, labelKey: 'socopsSidebar',
        children: [
          { id: 'socops-events',  to: '/socops/events',          Icon: Siren,       labelKey: 'socopsEventsSidebar' },
          { id: 'socops-playbks', to: '/socops/playbooks',       Icon: BookOpen,    labelKey: 'socopsPlaybooksSidebar' },
          { id: 'socops-pentest', to: '/socops/pentests',        Icon: ShieldCheck, labelKey: 'socopsPentestsSidebar' },
          { id: 'socops-regexam', to: '/socops/regulator-exams', Icon: LockKeyhole, labelKey: 'socopsRegulatorsSidebar' },
          { id: 'socops-ot',      to: '/socops/ot-assets',       Icon: Server,      labelKey: 'socopsOtSidebar' },
          // F2-07b: these eight were fully built — real API wiring, tables, detail links — and
          // reachable from NOWHERE in the UI. Their sibling /socops/awareness was in the nav,
          // which is what shows the omission was an oversight rather than a decision: someone
          // added the routes and the pages and wired only one of nine into the sidebar.
          // Labels are each page's own bilingual title, so the nav matches the screen.
          { id: 'socops-appsec',  to: '/socops/appsec',            Icon: ShieldCheck, labelKey: 'socopsAppsecSidebar' },
          { id: 'socops-dlp',     to: '/socops/dlp',               Icon: ScanSearch,  labelKey: 'socopsDlpSidebar' },
          { id: 'socops-forensic',to: '/socops/forensics',         Icon: ScanSearch,  labelKey: 'socopsForensicsSidebar' },
          { id: 'socops-iamjml',  to: '/socops/iam-jml',           Icon: Users,       labelKey: 'socopsIamJmlSidebar' },
          { id: 'socops-zt',      to: '/socops/zt-maturity',       Icon: TrendingUp,  labelKey: 'socopsZtSidebar' },
          { id: 'socops-brand',   to: '/socops/brand-monitoring',  Icon: ScanSearch,  labelKey: 'socopsBrandSidebar' },
          { id: 'socops-ins',     to: '/socops/cyber-insurance',   Icon: LockKeyhole, labelKey: 'socopsInsuranceSidebar' },
          { id: 'socops-madd',    to: '/socops/ma-due-diligence',  Icon: ShieldCheck, labelKey: 'socopsMaDdSidebar' },
        ],
      },
      {
        id: 'vuln-sla', to: '/cyber/vuln-sla/dashboard', Icon: TrendingUp, labelKey: 'vulnSlaDashSidebar',
        children: [
          { id: 'vuln-sla-pol', to: '/cyber/vuln-sla/policies', Icon: Sliders, labelKey: 'vulnSlaPolicySidebar' },
        ],
      },
      { id: 'asm',         to: '/cyber/attack-surface', Icon: Network,    labelKey: 'asmSidebar' },
      { id: 'assets',      to: '/assets',               Icon: Server,     labelKey: 'assetsSidebar' },
      { id: 'cloud-svc',   to: '/cyber/cloud-services', Icon: Cloud,      labelKey: 'cloudServicesSidebar' },
      { id: 'ti-correlate',to: '/cyber/threat-intel',   Icon: Radar,      labelKey: 'threatIntelSidebar' },
      { id: 'det-lib',     to: '/socops/detections',    Icon: ScanSearch, labelKey: 'detectionLibrarySidebar' },
      { id: 'fw-ctrls',    to: '/framework-controls',   Icon: BookOpen,   labelKey: 'fwcSidebar' },
      { id: 'cyb-pdpl',    to: '/cyber-pdpl-bridge',    Icon: Workflow,   labelKey: 'cpbSidebar' },
      { id: 'cyber-lc',    to: '/cyber-lifecycle',      Icon: ShieldCheck, labelKey: 'cycSidebar' },
    ],
  },

  // ─── 5. Privacy & Data Protection (DPO / CDO) ───────────────────────
  // De-bloated: the daily Data-Officer toolkit and the NDMO/NDI National
  // Data Governance tree are each nested under a single hub instead of
  // competing as two extra top-level groups.
  {
    id: 'privacy', titleKey: 'navPrivacy',
    items: [
      { id: 'pdpl-dash',  to: '/privacy/dashboard',     Icon: LayoutDashboard, labelKey: 'pdplDashSidebar' },
      { id: 'privacy',    to: '/privacy',               Icon: Fingerprint,     labelKey: 'modPdpl' },
      // F2-198 — points at the ROPA tab of the real register (privacy.processing_activities,
      // 11 rows) instead of the retired /ropa page, which rendered 9 invented activities.
      { id: 'ropa',       to: '/privacy?tab=ropa',      Icon: Files,           labelKey: 'ropaSidebar' },
      { id: 'data-flow',  to: '/privacy/data-flow',     Icon: Spline,          labelKey: 'dataFlowSidebar' },
      { id: 'subp',       to: '/privacy/subprocessors', Icon: Share2,          labelKey: 'subpSidebar' },
      { id: 'proc-dd',    to: '/processor-assessments', Icon: ClipboardCheck,  labelKey: 'procSidebar' },
      { id: 'dsa',        to: '/data-sharing',          Icon: Share2,          labelKey: 'dsaSidebar' },
      { id: 'data-lc',    to: '/data-lifecycle',        Icon: Compass,         labelKey: 'pdplExtSidebar' },
      // Saudi Data-Officer daily toolkit (PDPL + NDMO + SDAIA).
      {
        id: 'data-officer', to: '/data-officer/dashboard', Icon: UserCog, labelKey: 'navDataOfficer',
        children: [
          { id: 'do-dsr',       to: '/data-officer/dsr',       Icon: Inbox,       labelKey: 'dataOfficer.dsr' },
          { id: 'do-consents',  to: '/data-officer/consents',  Icon: ShieldCheck, labelKey: 'dataOfficer.consents' },
          { id: 'do-dpia',      to: '/data-officer/dpia',      Icon: ScrollText,  labelKey: 'dataOfficer.dpia' },
          { id: 'do-breaches',  to: '/data-officer/breaches',  Icon: Siren,       labelKey: 'dataOfficer.breaches' },
          { id: 'do-notices',   to: '/data-officer/notices',   Icon: BookOpen,    labelKey: 'dataOfficer.notices' },
          { id: 'do-retention', to: '/data-officer/retention', Icon: Archive,     labelKey: 'dataOfficer.retention' },
          { id: 'do-ndr',       to: '/data-officer/ndr',       Icon: ScrollText,  labelKey: 'dataOfficer.ndr' },
          { id: 'do-catalog',   to: '/data-officer/catalog',   Icon: Database,    labelKey: 'dataOfficer.catalog' },
          { id: 'do-quality',   to: '/data-officer/quality',   Icon: Sigma,       labelKey: 'dataOfficer.quality' },
          { id: 'do-ethics',    to: '/data-officer/ethics',    Icon: Scale,       labelKey: 'dataOfficer.ethics' },
        ],
      },
      // National Data Governance — NDMO Data Classification + SDAIA NDI.
      {
        id: 'national-data', to: '/dataclass/dashboard', Icon: Database, labelKey: 'navNationalData',
        children: [
          { id: 'dataclass-assets',  to: '/dataclass/assets',   Icon: Files,        labelKey: 'dataclass.assets' },
          { id: 'dataclass-flows',   to: '/dataclass/flows',    Icon: Share2,       labelKey: 'dataclass.flows' },
          { id: 'ndi-home',          to: '/ndi',                Icon: Database,     labelKey: 'ndiSidebar' },
          { id: 'ndi-cycles',        to: '/ndi?tab=cycles',     Icon: Activity,     labelKey: 'ndi.cycles' },
          { id: 'ndi-framework',     to: '/ndi?tab=framework',  Icon: BookOpen,     labelKey: 'ndi.framework' },
          { id: 'ndi-maturity',      to: '/ndi?tab=maturity',   Icon: TrendingUp,   labelKey: 'ndi.maturity' },
          { id: 'ndi-compliance',    to: '/ndi?tab=compliance', Icon: CheckCheck,   labelKey: 'ndi.compliance' },
          { id: 'ndi-evidence',      to: '/ndi?tab=evidence',   Icon: Files,        labelKey: 'ndi.evidence' },
          { id: 'ndi-gaps',          to: '/ndi?tab=gaps',       Icon: AlertOctagon, labelKey: 'ndi.gaps' },
          { id: 'ndi-submission',    to: '/ndi?tab=submission', Icon: Stamp,        labelKey: 'ndi.submission' },
        ],
      },
      { id: 'privacy-lc', to: '/privacy-lifecycle',     Icon: UserCog,         labelKey: 'prcSidebar' },
    ],
  },

  // ─── 6. Third-Party Risk (TPRM) ─────────────────────────────────────
  {
    id: 'third-party', titleKey: 'navThirdParty',
    items: [
      { id: 'scm-dash',     to: '/scm/dashboard',    Icon: LayoutDashboard, labelKey: 'scmDashSidebar' },
      { id: 'tprm',         to: '/tprm',             Icon: Workflow,        labelKey: 'modThirdParty' },
      { id: 'outsourcing',  to: '/tprm/outsourcing', Icon: Briefcase,       labelKey: 'outsourcingSidebar' },
      {
        id: 'outsourcing-cloud', to: '/outsourcing/dashboard', Icon: Cloud,
        labelKey: 'outsourcingCloud.sidebar',
        children: [
          { id: 'outsourcing-arr', to: '/outsourcing/arrangements',   Icon: Briefcase, labelKey: 'outsourcingCloud.arrangements' },
          { id: 'outsourcing-svc', to: '/outsourcing/cloud-services', Icon: Cloud,     labelKey: 'outsourcingCloud.cloudServices' },
        ],
      },
      { id: 'scm',          to: '/scm',                 Icon: Truck,          labelKey: 'scmEyebrow' },
      { id: 'srp',          to: '/supplier-resilience', Icon: ShieldCheck,    labelKey: 'srpSidebar' },
      { id: 'clauses',      to: '/contract-clauses',    Icon: Stamp,          labelKey: 'cclSidebar' },
      { id: 'fourth-party', to: '/fourth-party',        Icon: Network,        labelKey: 'f4pSidebar' },
      { id: 'tpc',          to: '/third-party-cyber',   Icon: Bug,            labelKey: 'tpcSidebar' },
      { id: 'res-tests',    to: '/resilience-tests',    Icon: Activity,       labelKey: 'srtSidebar' },
      { id: 'vendor-bcp',   to: '/tprm/bcp-campaigns',  Icon: ClipboardCheck, labelKey: 'vbcpSidebar' },
      { id: 'vendor-portal',to: '/portal/vendor',       Icon: Briefcase,      labelKey: 'vpSidebar' },
    ],
  },

  // ─── 7. Resilience & Continuity (BCM Manager) ───────────────────────
  {
    id: 'resilience', titleKey: 'navResilience',
    items: [
      { id: 'bcm-dash',      to: '/bcm/dashboard', Icon: LayoutDashboard, labelKey: 'bcmDashSidebar' },
      { id: 'bcm',           to: '/bcm',           Icon: ShieldCheck,     labelKey: 'bcmWorkspaceSidebar' },
      { id: 'bcm-readiness', to: '/bcm/readiness', Icon: Activity,        labelKey: 'bcmReadinessSidebar' },
      {
        id: 'bia-wizard', to: '/bcm/bia', Icon: ClipboardCheck, labelKey: 'biaWizardSidebar',
        children: [
          { id: 'bia-quantitative', to: '/bcm/bia-quantitative', Icon: Sigma,      labelKey: 'biaQuantSidebar' },
          { id: 'strategy-roi',     to: '/bcm/strategy-roi',     Icon: TrendingUp, labelKey: 'strategyRoiSidebar' },
          { id: 'mtpd-inh',         to: '/bcm/mtpd-inheritance', Icon: GitBranch,  labelKey: 'mtpdInhSidebar' },
        ],
      },
      // Plans hub — BCP builder + DR plans + exercise scenario library.
      {
        id: 'bcp-builder', to: '/bcm/plans', Icon: FileText, labelKey: 'bcpBuilderSidebar',
        children: [
          { id: 'scenario-library', to: '/bcm/scenarios', Icon: BookOpen, labelKey: 'scenarioLibrarySidebar' },
          { id: 'dr-plans',         to: '/dr-plans',      Icon: Server,   labelKey: 'drpSidebar' },
        ],
      },
      {
        id: 'crisis-room', to: '/bcm/crisis-room', Icon: Siren, labelKey: 'crisisRoomSidebar',
        children: [
          { id: 'crisis-mobile', to: '/crisis/mobile',       Icon: Siren,    labelKey: 'crisisMobileSidebar' },
          { id: 'crisis-comms',  to: '/bcm/comms/templates', Icon: Megaphone, labelKey: 'crisisCommsSidebar' },
          { id: 'siem-bridge',   to: '/bcm/siem-events',     Icon: Activity, labelKey: 'siemBridgeSidebar' },
        ],
      },
      { id: 'opres-dash',       to: '/opres/dashboard',        Icon: Shield,        labelKey: 'opres.sidebar' },
      { id: 'rrp-dash',         to: '/rrp/dashboard',          Icon: ShieldAlert,   labelKey: 'rrp.sidebar' },
      { id: 'bcm-cascade',      to: '/bcm/dependency-cascade', Icon: Network,       labelKey: 'cascadeSidebar' },
      { id: 'bcm-dr-trend',     to: '/bcm/dr-test-trend',      Icon: BarChart3,     labelKey: 'drTrendSidebar' },
      { id: 'bcm-remediation',  to: '/bcm/remediation',        Icon: AlertOctagon,  labelKey: 'bcmRemediationSidebar' },
      { id: 'bcm-compliance',   to: '/bcm/compliance',         Icon: Stamp,         labelKey: 'bcmComplianceSidebar' },
      { id: 'bcm-mgmt-reviews', to: '/bcm/management-reviews', Icon: ClipboardList, labelKey: 'bcmMgmtReviewsSidebar' },
      { id: 'bcm-reports',      to: '/bcm/reports',            Icon: BarChart3,     labelKey: 'bcmReportsSidebar' },
    ],
  },

  // ─── 8. Ethics & Investigations (Ombudsperson / Ethics Officer) ─────
  {
    id: 'ethics', titleKey: 'navEthicsInvestigations',
    items: [
      { id: 'ethics',     to: '/ethics',          Icon: ShieldAlert,   labelKey: 'modEthics' },
      {
        id: 'wb-cases', to: '/ethics/whistleblower/cases', Icon: Megaphone, labelKey: 'whistleblowerSidebar',
        children: [
          { id: 'wb-retal', to: '/ethics/whistleblower/retaliation', Icon: ShieldCheck, labelKey: 'wbRetaliationSidebar' },
        ],
      },
      { id: 'coi',        to: '/ethics/coi',      Icon: Scale,         labelKey: 'coi.title' },
      { id: 'complaints', to: '/complaints',      Icon: MessageSquare, labelKey: 'complaints.sidebar' },
      { id: 'fraud',      to: '/fraud/dashboard', Icon: ShieldX,       labelKey: 'fraud.sidebar' },
    ],
  },

  // ─── 9. Regulatory Affairs (KSA regulator gateway) ──────────────────
  // Gains the regulator-facing surfaces (NCA ECC filings, inspections,
  // mandatory reporting) that were previously filed under Internal Audit.
  {
    id: 'reg-affairs', titleKey: 'navSaudiRegulators',
    items: [
      { id: 'authorities', to: '/authorities',             Icon: Landmark,  labelKey: 'modAuthorities' },
      { id: 'sanctions',   to: '/aml/sanctions/dashboard', Icon: ScanSearch, labelKey: 'sanctionsSidebar' },
      { id: 'sharia',      to: '/sharia/dashboard',        Icon: Moon,       labelKey: 'sharia.sidebar' },
      { id: 'openbanking', to: '/openbanking/dashboard',   Icon: Banknote,   labelKey: 'ob.sidebar' },
      { id: 'hrsd',        to: '/authority/HRSD',          Icon: Users,      labelKey: 'modHrsd' },
      { id: 'moc',         to: '/authority/MOC',           Icon: Building2,  labelKey: 'modMoc' },
      // F2-199 — was a group whose header pointed at `/nca-ecc-export` (a fabricated
      // regulator submission document, retired) with the real filings register as its only
      // child. Collapsed to the one real destination; the old `ncaSidebar` label read
      // "NCA ECC export", naming the thing that no longer exists.
      { id: 'nca-ecc',     to: '/nca-ecc/filings',        Icon: ShieldCheck, labelKey: 'ncaFilingsSidebar' },
      { id: 'reginspect',   to: '/reginspect/dashboard',   Icon: ClipboardList, labelKey: 'reginspect.sidebar' },
      { id: 'regreporting', to: '/regreporting/dashboard', Icon: CalendarDays,  labelKey: 'regreporting.sidebar' },
    ],
  },

  // ─── 10. ESG & Climate (Sustainability Officer) ─────────────────────
  {
    id: 'esg', titleKey: 'navEsgClimate',
    items: [
      { id: 'esg',           to: '/esg',                     Icon: Leaf,           labelKey: 'esgSidebar' },
      { id: 'green-finance', to: '/green-finance',           Icon: Landmark,       labelKey: 'greenFinanceSidebar' },
      { id: 'tcfd-scn',      to: '/esg/tcfd-scenarios',      Icon: CloudLightning, labelKey: 'tcfdScenariosSidebar' },
      { id: 'fin-emissions', to: '/esg/financed-emissions',  Icon: Factory,        labelKey: 'financedEmissionsSidebar' },
      { id: 'climate-rpt',   to: '/esg/climate-report',      Icon: FileSearch,     labelKey: 'climateReportSidebar' },
    ],
  },

  // ─── 11. AI & Model Risk (AI Officer / Model Validator) ─────────────
  {
    id: 'ai-model-risk', titleKey: 'navAiGovernance',
    items: [
      { id: 'ai-models',      to: '/ai/models',      Icon: Bot,      labelKey: 'aiModelRegistrySidebar' },
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

  // ─── 12. Insights & Reporting (board + cross-cutting outputs) ───────
  // Folds the former 2-item Audit Committee group into the platform's
  // "what came out of the system" surface — board pack, scheduled reports,
  // CAPA, intake catalog, knowledge base, communications.
  {
    id: 'insights', titleKey: 'navInsights',
    items: [
      { id: 'ac-dash',        to: '/audit-committee/dashboard', Icon: LayoutDashboard, labelKey: 'acDashSidebar' },
      { id: 'rcf-pivot',      to: '/governance/pivot/risk/1',   Icon: GitBranch,       labelKey: 'rcfPivotSidebar' },
      // F2-42 — the pack is built from the live aggregate, guarded by
      // BOARD:EXPORT (8 roles after ADR-017 D-10a). Offering the entry to the
      // other 51 roles would be a link whose only outcome is a refusal.
      { id: 'board-pack',     to: '/board-pack',                Icon: FileText,        labelKey: 'boardPackSidebar',
        gate: () => canExport('BOARD') },
      { id: 'reports-centre', to: '/reports/centre',            Icon: Files,           labelKey: 'reportsCentreSidebar' },
      { id: 'schedules',      to: '/reports/schedules',         Icon: Calendar,        labelKey: 'rsTitle' },
      { id: 'capa',           to: '/capa',                      Icon: Wrench,          labelKey: 'modCapa' },
      { id: 'intake',         to: '/intake',                    Icon: Send,            labelKey: 'intakeCatalogTitle' },
      { id: 'kb',             to: '/knowledge-base',            Icon: BookOpen,        labelKey: 'kbSidebar' },
      { id: 'comms',          to: '/communications',            Icon: Send,            labelKey: 'modCommunications' },
    ],
  },

  // ─── 13. Administration (Platform Admin) ────────────────────────────
  {
    id: 'administration', titleKey: 'navAdministration',
    items: [
      { id: 'admin',       to: '/admin',             Icon: UserCog,      labelKey: 'adminTitle' },
      { id: 'licensing',   to: '/admin/licensing',   Icon: KeyRound,     labelKey: 'licensingSidebar' },
      {
        id: 'committees', to: '/committees', Icon: LandmarkIcon, labelKey: 'comSidebar',
        children: [
          { id: 'com-catalog', to: '/committees/catalog', Icon: BookOpen, labelKey: 'comCatalogSidebar' },
        ],
      },
      { id: 'sso',         to: '/sso',               Icon: LockKeyhole,  labelKey: 'ssoSidebar' },
      { id: 'wf-versions', to: '/workflow-versions', Icon: Layers,       labelKey: 'wvSidebar' },
      { id: 'connectors',  to: '/connectors',        Icon: Plug,         labelKey: 'connectorsTitle' },
      // F2-142 — the 'Notification dispatch / Channels / Outbox' group is gone. All three
      // rendered client-side fakes, and they duplicated a capability that already works: the
      // real server-backed inbox is the 'notifications' entry above.
      { id: 'audit-log',   to: '/audit-log',         Icon: ScrollText,   labelKey: 'auditLogTitle' },
      { id: 'access-gov',  to: '/access-governance', Icon: KeyRound,     labelKey: 'accessGovTitle' },
      { id: 'export-hist', to: '/export-history',    Icon: Download,     labelKey: 'exportHistoryTitle' },
      { id: 'sessions',    to: '/account/sessions',  Icon: KeyRound,     labelKey: 'sessionsTitle' },
      { id: 'issues',      to: '/issues',            Icon: ClipboardList, labelKey: 'issuesTitle' },
      { id: 'file-upload', to: '/file-upload',       Icon: FileCheck2,   labelKey: 'fuSidebar' },
      { id: 'onboarding',  to: '/onboarding',        Icon: Compass,      labelKey: 'onbSidebar' },
      { id: 'help-topics', to: '/admin/help-topics', Icon: HelpCircle,   labelKey: 'helpTopicsSidebar' },
      { id: 'settings',    to: '/settings',          Icon: Settings,     labelKey: 'itemSettings' },
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

/** Active-state for a row whose sibling set uses query-string tabs
 *  (e.g. /ndi?tab=cycles). NavLink matches on pathname only and ignores
 *  the query string, so without this every NDI tab — plus the bare /ndi
 *  link — highlighted simultaneously. A query row is active iff the path
 *  matches AND all of its query params equal the current location's; a
 *  bare-path row (no query) is active only when no query is present.
 *
 *  CE-0 lifted this from children to rows of either level: the Change group lists `/changes` and
 *  `/changes?tab=governance` as **top-level** siblings, and both highlighted at once — the same wart
 *  one level up. Applied only where a sibling set actually mixes query and bare paths, so no
 *  existing group's behaviour changes. */
function queryAwareActive(to: string, pathname: string, search: string,
                          siblings: string[] = []): boolean {
  const qIdx = to.indexOf('?');
  const path = qIdx >= 0 ? to.slice(0, qIdx) : to;
  if (pathname !== path && !pathname.startsWith(path + '/')) return false;
  // CE-13 — a row wins its prefix match only if no SIBLING claims a longer one.
  //
  // The prefix rule above exists so a detail route keeps its section highlighted. But CE-13 added
  // `/changes/cab` beside `/changes`, and on that page all three rows in the group lit up at once:
  // the register by prefix, the calendar because it also has no conflicting query, and the CAB queue
  // exactly. That is CE-0's own wart at a third level — one location, three highlighted rows — and the
  // narrow fix is the same shape: yield the prefix match to whichever sibling matches more of the path.
  if (pathname !== path) {
    const deeper = siblings.some(s => {
      const sPath = s.indexOf('?') >= 0 ? s.slice(0, s.indexOf('?')) : s;
      return sPath.length > path.length
        && (pathname === sPath || pathname.startsWith(sPath + '/'));
    });
    if (deeper) return false;
  }
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

  // Sidebar scroll position persists natively now: AppLayout mounts this
  // component ONCE and keeps it mounted across navigation, so the scroll
  // container is the same DOM node every time and retains its scrollTop on
  // its own. The previous sessionStorage save/restore existed only to paper
  // over the per-page-<Shell> remount; with the persistent shell it became
  // unnecessary AND harmful — restoring on every route change overrode the
  // native scroll (and, because the save half stuck at 0, snapped the nav to
  // the top) — so it has been removed.

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

  // ── F2-04b: hide what the tenant has not licensed ────────────────────
  // The SKU comes from the destination path (lib/entitlement.tsx), not from the
  // nav group: pillars are a UX grouping and several mix SKUs on purpose — the
  // Risk pillar lists SOCOPS incidents and RCA, the Privacy pillar lists
  // DATA_AI_GOV's NDI. Grouping by pillar would hide the wrong things.
  //
  // An emptied group is dropped along with its header. Leaving "ESG & Climate"
  // in place with nothing under it advertises precisely the module we are
  // hiding, and reads as a bug rather than a licence boundary.
  //
  // Fails open: `isEnabled(null)` is true, and so is every SKU while the
  // enabled-modules list is still loading — so the nav never flickers down to a
  // stub on a slow network, and a licensing outage cannot hide the product.
  const { isEnabled } = useEntitlement();
  const visibleGroups = useMemo(
    () => filterNavByEntitlement<NavItem, NavGroup>(NAV_GROUPS, isEnabled),
    [isEnabled],
  );

  // Auto-expand sets — derived, not stored. Computed every render
  // from the active route, then OR'd with the user's manual sets.
  const autoExpandGroups = useMemo(() => {
    const s = new Set<string>();
    for (const g of visibleGroups) if (groupContainsActive(g, pathname)) s.add(g.id);
    return s;
  }, [pathname, visibleGroups]);
  const autoExpandItems = useMemo(() => {
    const s = new Set<string>();
    for (const g of visibleGroups) {
      for (const it of g.items) {
        if (it.children && it.children.some(c => pathMatches(pathname, c.to))) s.add(it.id);
      }
    }
    return s;
  }, [pathname, visibleGroups]);

  return (
    <aside ref={asideRef} className={open ? 'sb sb-open' : 'sb'}>
      <div className="sb-brand">
        <div className="sb-mark"><ShieldCheck size={22} strokeWidth={2} /></div>
        <div>
          <div className="sb-title">{t('brandName')}</div>
          <div className="sb-sub">{t('brandSub')}</div>
        </div>
      </div>

      {visibleGroups.map(group => {
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
                    siblingsUseQuery={group.items.some(s => s.to.includes('?'))}
                    siblingPaths={group.items.map(s => s.to)}
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
  item, t, badge, pathname, search, siblingsUseQuery, siblingPaths, expandedItems, autoExpandItems,
  onToggleChildren,
}: {
  item: NavItem;
  t: (k: string) => string;
  badge: NavBadge | null;
  pathname: string;
  search: string;
  /** CE-0: true when this row's own group mixes query-tab and bare paths, so its highlight has to
   *  respect the query the way children already did. */
  siblingsUseQuery: boolean;
  /** Every `to` in this row's sibling set, so a prefix match can yield to a deeper sibling. */
  siblingPaths: string[];
  expandedItems: Set<string>;
  autoExpandItems: Set<string>;
  onToggleChildren: (id: string) => void;
}) {
  const hasChildren = !!item.children && item.children.length > 0;
  const childrenUseQuery = !!item.children?.some(c => c.to.includes('?'));
  const childrenOpen = hasChildren && (
    expandedItems.has(item.id) || autoExpandItems.has(item.id) || pathMatches(pathname, item.to)
  );
  const forcedSelf = siblingsUseQuery
    ? queryAwareActive(item.to, pathname, search, siblingPaths)
    : null;

  return (
    <>
      <div className="sb-item-wrap">
        <NavLink to={item.to} className={({ isActive }) => cn('sb-item', (forcedSelf ?? isActive) && 'active', hasChildren && 'sb-item-has-children')}>
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
              ? queryAwareActive(child.to, pathname, search, item.children!.map(c => c.to))
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

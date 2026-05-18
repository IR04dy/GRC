import { ShieldCheck, LayoutDashboard, AlertTriangle, LockKeyhole, RefreshCw,
         Fingerprint, ClipboardCheck, Workflow, AlertOctagon, Files, UserCog, Truck,
         GitBranch, ShieldAlert, Calendar, Settings, Landmark, Stamp, Send, Wrench,
         Inbox, Plug, FileCheck2, Users, Building2, CheckCheck, Server, FileText,
         Newspaper, Briefcase, Activity, Leaf, Database, Layers, Compass, BookOpen, PenLine, Microscope,
         Sigma, Telescope, Landmark as LandmarkIcon,
         Network, Share2, Bug, BarChart3, KeyRound, Siren, ClipboardCheck,
         type LucideIcon } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { useI18n } from '../../lib/i18n';
import { cn } from '../../lib/cn';

interface NavItem {
  id: string;
  to: string;
  Icon: LucideIcon;
  labelKey: string;
}
interface NavGroup { id: string; titleKey: string; items: NavItem[]; }

/**
 * Sidebar — market-leader-aligned navigation (Sprint 4 final).
 *
 * Pattern lifted from industry-standard GRC tooling, industry-standard tooling, industry-standard tooling, industry-standard tooling:
 *   1. "My Work" at the top — universal, role-agnostic landing
 *   2. Per-domain verticals in frequency order (Risk → Compliance → Audit → ...)
 *   3. Cross-cutting tools and reports near the bottom
 *   4. Administration at the very bottom
 *   5. Each group ≤10 items (cognitive-load cap that all 4 leaders respect)
 *
 * Order rationale:
 *   - Risk Management is the highest-frequency GRC domain in every benchmark
 *   - Compliance/Policy is #2; Audit gets its own group per industry convention.
 *   - Privacy is its own vertical per industry convention (their flagship pattern)
 *   - Cyber + BCM + Third-Party each follow as standalone verticals
 *   - "Saudi Regulators" is OUR differentiator — kept visible, not buried in admin
 *   - Service Catalog + Knowledge live together (universal employee surface)
 *   - Reports + Communications + Board Pack live together (output surface)
 *   - Administration absorbs configuration + integrations + system settings
 */
const NAV_GROUPS: NavGroup[] = [
  // ─── 1. My Work ─────────────────────────────────────────────────────
  // Universal landing — what every authenticated user opens first.
  // Mirrors the industry pattern's "Workspace Manager" home + industry-standard tooling's "My Work".
  {
    id: 'my-work', titleKey: 'navMyWork',
    items: [
      { id: 'dashboard',     to: '/dashboard',            Icon: LayoutDashboard, labelKey: 'itemDashboard' },
      { id: 'lc-overview',   to: '/lifecycle-overview',   Icon: Activity,        labelKey: 'loSidebar' },
      { id: 'inbox',         to: '/intake-inbox',         Icon: Inbox,           labelKey: 'inboxTitle' },
      { id: 'approvals',     to: '/approvals',            Icon: CheckCheck,      labelKey: 'approvalsSidebar' },
      { id: 'signatures',    to: '/signatures',           Icon: PenLine,         labelKey: 'sigSidebar' },
      { id: 'notif-outbox',  to: '/notifications/outbox', Icon: Inbox,           labelKey: 'noxSidebar' },
    ],
  },

  // ─── 2. Risk Management ─────────────────────────────────────────────
  // Highest-frequency GRC domain (per industry research.).
  {
    id: 'risk', titleKey: 'navRisk',
    items: [
      { id: 'risk-dash', to: '/risk/dashboard',  Icon: LayoutDashboard, labelKey: 'ermDashSidebar' },
      { id: 'risk',      to: '/risk',           Icon: AlertTriangle, labelKey: 'modRisk' },
      { id: 'risk-lc',   to: '/risk-lifecycle', Icon: GitBranch,     labelKey: 'rlcSidebar' },
      { id: 'scenarios', to: '/risk-scenarios', Icon: Sigma,         labelKey: 'scnSidebar' },
      { id: 'emerging',  to: '/emerging-risk',  Icon: Telescope,     labelKey: 'emrSidebar' },
      { id: 'incidents', to: '/incidents',      Icon: AlertOctagon,  labelKey: 'incidentsTitle' },
      { id: 'rca',       to: '/rca',            Icon: Microscope,    labelKey: 'rcaSidebar' },
      { id: 'changes',   to: '/changes',        Icon: GitBranch,     labelKey: 'modChanges' },
    ],
  },

  // ─── 3. Compliance & Policy ────────────────────────────────────────
  // Second-highest frequency. Frameworks + policies + reg-change + crosswalks
  // + evidence + attestations all belong here per the industry-standard Policy and
  // Compliance application structure. Ethics + ESG fold in as compliance
  // sub-domains ((industry practice).).
  {
    id: 'compliance', titleKey: 'navCompliancePolicy',
    items: [
      { id: 'compliance',   to: '/compliance',        Icon: ClipboardCheck, labelKey: 'modCompliance' },
      { id: 'reg-library',  to: '/regulatory/library', Icon: Landmark,      labelKey: 'regLibrarySidebar' },
      { id: 'ctrl-library', to: '/controls/library',   Icon: ShieldCheck,   labelKey: 'ctrlLibrarySidebar' },
      { id: 'posture-hub',  to: '/compliance/posture', Icon: BarChart3,     labelKey: 'postureHubSidebar' },
      { id: 'fw-packs',     to: '/framework-packs',   Icon: Layers,         labelKey: 'fpSidebar' },
      { id: 'pol-tpl',      to: '/policy-templates',  Icon: BookOpen,       labelKey: 'ptSidebar' },
      { id: 'pol-mgmt',     to: '/policy-management', Icon: ShieldCheck,    labelKey: 'polMgmtSidebar' },
      { id: 'reg-change',   to: '/regulatory-change', Icon: Newspaper,      labelKey: 'regChangeSidebar' },
      { id: 'crosswalk',    to: '/control-crosswalk', Icon: GitBranch,      labelKey: 'cwSidebar' },
      { id: 'evidence',     to: '/evidence',          Icon: Files,          labelKey: 'evidenceTitle' },
      { id: 'attestations', to: '/attestations',      Icon: FileCheck2,     labelKey: 'campaignsTitle' },
      { id: 'ethics',       to: '/ethics',            Icon: ShieldAlert,    labelKey: 'modEthics' },
      { id: 'esg',          to: '/esg',               Icon: Leaf,           labelKey: 'esgSidebar' },
      { id: 'ndi',          to: '/ndi',               Icon: Database,       labelKey: 'ndiSidebar' },
    ],
  },

  // ─── 4. Audit ───────────────────────────────────────────────────────
  // Own top-level group per industry convention (Audit Management). + industry-standard tooling
  // (their flagship). NCA ECC Filing is here because it's an annual audit-style
  // self-assessment, not a continuous compliance activity.
  {
    id: 'audit', titleKey: 'navAudit',
    items: [
      { id: 'audit-ws',  to: '/audit-workspace',   Icon: Stamp,       labelKey: 'modAuditWorkspace' },
      { id: 'audit-lc',  to: '/audit-lifecycle',   Icon: BookOpen,    labelKey: 'alcSidebar' },
      { id: 'ev-chain',  to: '/evidence-chain',    Icon: Fingerprint, labelKey: 'echSidebar' },
      { id: 'nca-ecc',   to: '/nca-ecc-export',    Icon: ShieldCheck, labelKey: 'ncaSidebar' },
    ],
  },

  // ─── 5. Privacy ─────────────────────────────────────────────────────
  // Standalone vertical per industry convention.
  // PDPL, DSR, ROPA — three pillars.
  {
    id: 'privacy', titleKey: 'navPrivacy',
    items: [
      { id: 'pdpl-dash',  to: '/privacy/dashboard', Icon: LayoutDashboard, labelKey: 'pdplDashSidebar' },
      { id: 'privacy',    to: '/privacy',           Icon: Fingerprint, labelKey: 'modPdpl' },
      { id: 'privacy-lc', to: '/privacy-lifecycle', Icon: UserCog,     labelKey: 'prcSidebar' },
      { id: 'ropa',       to: '/ropa',              Icon: Files,       labelKey: 'ropaSidebar' },
      { id: 'data-lc',    to: '/data-lifecycle',    Icon: Compass,     labelKey: 'pdplExtSidebar' },
      { id: 'cyb-pdpl',   to: '/cyber-pdpl-bridge', Icon: Workflow,    labelKey: 'cpbSidebar' },
      { id: 'dsa',        to: '/data-sharing',         Icon: Share2,         labelKey: 'dsaSidebar' },
      { id: 'proc-dd',    to: '/processor-assessments', Icon: ClipboardCheck, labelKey: 'procSidebar' },
    ],
  },

  // ─── 6. Cybersecurity ───────────────────────────────────────────────
  // Standalone vertical per industry convention (IT and Cyber Risk). Assets folded
  // in as the asset register is cyber-owned in our domain model.
  {
    id: 'cyber', titleKey: 'navCyber',
    items: [
      { id: 'cyber-dash', to: '/cyber/dashboard',  Icon: LayoutDashboard, labelKey: 'cyberDashSidebar' },
      { id: 'cyber',    to: '/cyber',              Icon: LockKeyhole, labelKey: 'modCyber' },
      { id: 'cyber-lc', to: '/cyber-lifecycle',    Icon: ShieldCheck, labelKey: 'cycSidebar' },
      { id: 'fw-ctrls', to: '/framework-controls', Icon: BookOpen,    labelKey: 'fwcSidebar' },
      { id: 'assets',   to: '/assets',             Icon: Server,      labelKey: 'assetsSidebar' },
    ],
  },

  // ─── 7. Business Continuity ─────────────────────────────────────────
  // Standalone vertical per industry convention (Business Continuity Management).
  // BIA → Plans → Invocations → Crisis is a coherent operational arc.
  {
    id: 'bcm', titleKey: 'navBcm',
    items: [
      { id: 'bcm-dash', to: '/bcm/dashboard',     Icon: LayoutDashboard, labelKey: 'bcmDashSidebar' },
      { id: 'bcm-readiness', to: '/bcm/readiness', Icon: ShieldCheck,    labelKey: 'bcmReadinessSidebar' },
      { id: 'bcm-reports',   to: '/bcm/reports',   Icon: BarChart3,      labelKey: 'bcmReportsSidebar' },
      { id: 'crisis-room',   to: '/bcm/crisis-room', Icon: Siren,        labelKey: 'crisisRoomSidebar' },
      { id: 'bia-wizard',    to: '/bcm/bia',         Icon: ClipboardCheck, labelKey: 'biaWizardSidebar' },
      { id: 'bcm',      to: '/bcm',               Icon: RefreshCw,    labelKey: 'modBcm' },
      { id: 'bcm-lc',   to: '/bcm-lifecycle',     Icon: Activity,     labelKey: 'bclSidebar' },
      { id: 'bcm-deps', to: '/bcm-dependency',    Icon: GitBranch,    labelKey: 'bdSidebar' },
      { id: 'dr-plans', to: '/dr-plans',          Icon: Server,       labelKey: 'drpSidebar' },
      { id: 'crisis',   to: '/crisis-management', Icon: AlertOctagon, labelKey: 'crisisSidebar' },
    ],
  },

  // ─── 8. Third-Party Risk ────────────────────────────────────────────
  // Standalone per all four market leaders. SCM is supplier-side, vendor portal
  // is supplier-facing — both belong with TPRM.
  {
    id: 'third-party', titleKey: 'navThirdParty',
    items: [
      { id: 'scm-dash',      to: '/scm/dashboard',         Icon: LayoutDashboard, labelKey: 'scmDashSidebar' },
      { id: 'tprm',          to: '/tprm',                  Icon: Workflow,    labelKey: 'modThirdParty' },
      { id: 'vendor-portal', to: '/portal/vendor',         Icon: Briefcase,   labelKey: 'vpSidebar' },
      { id: 'scm',           to: '/scm',                   Icon: Truck,       labelKey: 'scmEyebrow' },
      { id: 'srp',           to: '/supplier-resilience',   Icon: ShieldCheck, labelKey: 'srpSidebar' },
      { id: 'clauses',       to: '/contract-clauses',      Icon: Stamp,       labelKey: 'cclSidebar' },
      { id: 'fourth-party',  to: '/fourth-party',          Icon: Network,     labelKey: 'f4pSidebar' },
      { id: 'tpc',           to: '/third-party-cyber',     Icon: Bug,         labelKey: 'tpcSidebar' },
      { id: 'res-tests',     to: '/resilience-tests',      Icon: Activity,    labelKey: 'srtSidebar' },
    ],
  },

  // ─── 9. Saudi Regulators ────────────────────────────────────────────
  // OUR differentiator. Market leaders don't have this group because they're
  // not Saudi-native — keeping it visible (not buried in admin) signals MENA
  // focus to buyers immediately.
  {
    id: 'saudi-regulators', titleKey: 'navSaudiRegulators',
    items: [
      { id: 'authorities', to: '/authorities',    Icon: Landmark,  labelKey: 'modAuthorities' },
      // DD-83: HRSD (Labor + GOSI + Nitaqat)
      { id: 'hrsd',        to: '/authority/HRSD', Icon: Users,     labelKey: 'modHrsd' },
      // DD-85: MOC (Tasattur + Consumer Protection + Commercial Registry)
      { id: 'moc',         to: '/authority/MOC',  Icon: Building2, labelKey: 'modMoc' },
    ],
  },

  // ─── 10. Service Catalog & Knowledge ────────────────────────────────
  // Cross-cutting employee surface. the industry pattern's "Service Catalog" is the
  // model. CAPA + Board Pack + Knowledge live here as universal outputs.
  {
    id: 'service-catalog', titleKey: 'navServiceCatalog',
    items: [
      { id: 'intake',     to: '/intake',            Icon: Send,     labelKey: 'intakeCatalogTitle' },
      { id: 'capa',       to: '/capa',              Icon: Wrench,   labelKey: 'modCapa' },
      { id: 'board-pack', to: '/board-pack',        Icon: FileText, labelKey: 'boardPackSidebar' },
      { id: 'kb',         to: '/knowledge-base',    Icon: BookOpen, labelKey: 'kbSidebar' },
      { id: 'comms',      to: '/communications',    Icon: Send,     labelKey: 'modCommunications' },
      { id: 'schedules',  to: '/reports/schedules', Icon: Calendar, labelKey: 'rsTitle' },
    ],
  },

  // ─── 11. Administration ─────────────────────────────────────────────
  // Bottom of nav per every market-leader pattern. Configuration of users,
  // workflows, integrations, channels, settings.
  {
    id: 'administration', titleKey: 'navAdministration',
    items: [
      { id: 'admin',         to: '/admin',                  Icon: UserCog,       labelKey: 'adminTitle' },
      { id: 'licensing',     to: '/admin/licensing',        Icon: KeyRound,      labelKey: 'licensingSidebar' },
      { id: 'committees',    to: '/committees',             Icon: LandmarkIcon,  labelKey: 'comSidebar' },
      { id: 'sso',           to: '/sso',                    Icon: LockKeyhole,   labelKey: 'ssoSidebar' },
      { id: 'wf-versions',   to: '/workflow-versions',      Icon: Layers,      labelKey: 'wvSidebar' },
      { id: 'connectors',    to: '/connectors',             Icon: Plug,        labelKey: 'connectorsTitle' },
      { id: 'notifications', to: '/notifications/dispatch', Icon: Send,        labelKey: 'ndSidebar' },
      { id: 'nc',            to: '/notifications/channels', Icon: Send,        labelKey: 'ncSidebar' },
      { id: 'file-upload',   to: '/file-upload',            Icon: FileCheck2,  labelKey: 'fuSidebar' },
      { id: 'onboarding',    to: '/onboarding',             Icon: Compass,     labelKey: 'onbSidebar' },
      { id: 'settings',      to: '/settings',               Icon: Settings,    labelKey: 'itemSettings' },
      // 'about' entry removed — it pointed at PlaceholderPage with no real
      // content. Re-add when there's actual content (license terms,
      // build/version info, support links).
    ],
  },
];

export function Sidebar({ open = false }: { open?: boolean }) {
  const { t } = useI18n();
  return (
    <aside className={open ? 'sb sb-open' : 'sb'}>
      <div className="sb-brand">
        <div className="sb-mark"><ShieldCheck size={22} strokeWidth={2} /></div>
        <div>
          <div className="sb-title">{t('brandName')}</div>
          <div className="sb-sub">{t('brandSub')}</div>
        </div>
      </div>

      {NAV_GROUPS.map(group => (
        <div key={group.id}>
          <div className="sb-section">{t(group.titleKey)}</div>
          {group.items.map(it => <NavRow key={it.id} item={it} t={t} />)}
        </div>
      ))}
    </aside>
  );
}

function NavRow({ item, t }: { item: NavItem; t: (k: string) => string }) {
  return (
    <NavLink to={item.to} className={({ isActive }) => cn('sb-item', isActive && 'active')}>
      <item.Icon size={18} strokeWidth={1.8} />
      <span>{t(item.labelKey)}</span>
    </NavLink>
  );
}

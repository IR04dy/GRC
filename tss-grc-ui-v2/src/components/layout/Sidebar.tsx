import { useEffect, useState } from 'react';
import { ShieldCheck, LayoutDashboard, AlertTriangle, LockKeyhole,
         Fingerprint, ClipboardCheck, Workflow, AlertOctagon, Files, UserCog, Truck,
         GitBranch, ShieldAlert, Calendar, Settings, Landmark, Stamp, Send, Wrench,
         Inbox, Plug, FileCheck2, Users, Building2, CheckCheck, Server, FileText,
         Newspaper, Briefcase, Activity, Leaf, Database, Layers, Compass, BookOpen, PenLine, Microscope,
         Sigma, Telescope, Landmark as LandmarkIcon,
         Network, Share2, Bug, BarChart3, KeyRound, Siren, Bell,
         type LucideIcon } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { useI18n } from '../../lib/i18n';
import { cn } from '../../lib/cn';
import { capaTaskApi, notificationApi, workflowSubmissionApi } from '../../lib/api';

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

  // ─── 2. Risk Management ─────────────────────────────────────────────
  // Highest-frequency GRC domain (per industry research.).
  {
    id: 'risk', titleKey: 'navRisk',
    items: [
      { id: 'risk-dash', to: '/risk/dashboard',  Icon: LayoutDashboard, labelKey: 'ermDashSidebar' },
      { id: 'risk',      to: '/risk',           Icon: AlertTriangle, labelKey: 'modRisk' },
      { id: 'risk-lc',   to: '/risk-lifecycle', Icon: GitBranch,     labelKey: 'rlcSidebar' },
      { id: 'scenarios', to: '/risk-scenarios', Icon: Sigma,         labelKey: 'scnSidebar' },
      { id: 'bowtie',    to: '/risk/bowtie',    Icon: GitBranch,     labelKey: 'bowtieSidebar' },
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
  //
  // SOC Operations (V150/V151) is the CISO-grade operational layer — SIEM
  // ingest, IR playbooks, threat intel, pentests, cert expiry, regulator
  // exams, Saudi seasonal overlays. Sits above the framework-and-controls
  // governance entries because the SOC dashboard is what the CISO opens
  // first, daily.
  {
    id: 'cyber', titleKey: 'navCyber',
    items: [
      { id: 'socops',         to: '/socops',                 Icon: Siren,           labelKey: 'socopsSidebar' },
      { id: 'socops-events',  to: '/socops/events',          Icon: Siren,           labelKey: 'socopsEventsSidebar' },
      { id: 'socops-playbks', to: '/socops/playbooks',       Icon: BookOpen,        labelKey: 'socopsPlaybooksSidebar' },
      { id: 'socops-pentest', to: '/socops/pentests',        Icon: ShieldCheck,     labelKey: 'socopsPentestsSidebar' },
      { id: 'socops-regexam', to: '/socops/regulator-exams', Icon: LockKeyhole,     labelKey: 'socopsRegulatorsSidebar' },
      { id: 'socops-ot',      to: '/socops/ot-assets',       Icon: Server,          labelKey: 'socopsOtSidebar' },
      { id: 'cyber-dash', to: '/cyber/dashboard',  Icon: LayoutDashboard, labelKey: 'cyberDashSidebar' },
      { id: 'cyber',    to: '/cyber',              Icon: LockKeyhole, labelKey: 'modCyber' },
      { id: 'cyber-lc', to: '/cyber-lifecycle',    Icon: ShieldCheck, labelKey: 'cycSidebar' },
      { id: 'fw-ctrls', to: '/framework-controls', Icon: BookOpen,    labelKey: 'fwcSidebar' },
      { id: 'assets',   to: '/assets',             Icon: Server,      labelKey: 'assetsSidebar' },
    ],
  },

  // ─── 7. Business Continuity ─────────────────────────────────────────
  // Consolidated per audit T1.1: 13 → 9 entries, ordered along the
  // ISO 22301 / SAMA OPRES operational arc:
  //   Workspace overview → Dashboard → Readiness cycles → BIA → DR plans
  //   → Crisis Room → Remediation → Compliance → Reports
  //
  // Removed from sidebar (still reachable from /bcm workspace hub):
  //   /crisis-management — NCEMA-tier invocation page (superseded by Crisis Room)
  //   /bcm-lifecycle     — legacy SAMA OPRES lifecycle (links from workspace)
  //   /bcm-dependency    — process dependency tree (links from workspace)
  //   /bcm/settings      — kept under workspace settings menu, not top-level
  {
    id: 'bcm', titleKey: 'navBcm',
    items: [
      { id: 'bcm',             to: '/bcm',               Icon: ShieldCheck,    labelKey: 'bcmWorkspaceSidebar' },
      { id: 'bcm-dash',        to: '/bcm/dashboard',     Icon: LayoutDashboard, labelKey: 'bcmDashSidebar' },
      { id: 'bcm-readiness',   to: '/bcm/readiness',     Icon: Activity,       labelKey: 'bcmReadinessSidebar' },
      { id: 'bia-wizard',      to: '/bcm/bia',           Icon: ClipboardCheck, labelKey: 'biaWizardSidebar' },
      { id: 'bia-quantitative', to: '/bcm/bia-quantitative', Icon: Sigma,         labelKey: 'biaQuantSidebar' },
      { id: 'bcp-builder',     to: '/bcm/plans',         Icon: FileText,       labelKey: 'bcpBuilderSidebar' },
      { id: 'dr-plans',        to: '/dr-plans',          Icon: Server,         labelKey: 'drpSidebar' },
      { id: 'crisis-room',     to: '/bcm/crisis-room',   Icon: Siren,          labelKey: 'crisisRoomSidebar' },
      { id: 'bcm-remediation', to: '/bcm/remediation',   Icon: AlertOctagon,   labelKey: 'bcmRemediationSidebar' },
      { id: 'bcm-compliance',  to: '/bcm/compliance',    Icon: Stamp,          labelKey: 'bcmComplianceSidebar' },
      { id: 'bcm-reports',     to: '/bcm/reports',       Icon: BarChart3,      labelKey: 'bcmReportsSidebar' },
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
      { id: 'com-catalog',   to: '/committees/catalog',     Icon: BookOpen,      labelKey: 'comCatalogSidebar' },
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

/** Sidebar inbox counters — one place to declare every "what's waiting
 *  on me?" number the nav can render.
 *
 *  <p>Each counter is backed by a cheap server-side aggregate (no row
 *  payload) so this stays a few-millisecond network cost per Sidebar
 *  mount regardless of tenant size. Counters refresh when the tab
 *  regains focus, so an approval/close done in another tab clears the
 *  badge here without a manual reload.
 *
 *  <p>Adding a new counter is three lines:
 *    1. add a CapaCountsDto-style endpoint + api helper
 *    2. add the field to {@link InboxCounts} and the {@code load()} fetch
 *    3. pass {@code counts.foo} to the right {@code NavRow}
 */
interface InboxCounts {
  /** OPEN workflow submissions (SUBMITTED + UNDER_REVIEW). */
  pendingReviews: number | null;
  /** Non-terminal CAPAs (excludes CLOSED + CANCELLED). */
  capaOpen: number | null;
  /** Open AND past-target-date CAPAs. */
  capaOverdue: number | null;
  /** Unread notifications addressed to the current user. */
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

export function Sidebar({ open = false }: { open?: boolean }) {
  const { t } = useI18n();
  const counts = useInboxCounts();

  /** Map a nav item id to the (badge value, tooltip, tone) it should
   *  render. Returning {@code null} hides the badge entirely (no zero
   *  badges, no API-error noise). */
  function badgeFor(id: string): { value: number; title: string; tone?: 'danger' } | null {
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
      // Overdue ratio drives a danger tone — surfaces "you have stale
      // remediation work" right in the nav without needing a tooltip.
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
          {group.items.map(it => <NavRow key={it.id} item={it} t={t} badge={badgeFor(it.id)} />)}
        </div>
      ))}
    </aside>
  );
}

interface NavBadge { value: number; title: string; tone?: 'danger'; }

function NavRow({ item, t, badge }: { item: NavItem; t: (k: string) => string; badge: NavBadge | null }) {
  return (
    <NavLink to={item.to} className={({ isActive }) => cn('sb-item', isActive && 'active')}>
      <item.Icon size={18} strokeWidth={1.8} />
      <span>{t(item.labelKey)}</span>
      {badge && (
        <span className={cn('sb-badge', badge.tone === 'danger' && 'sb-badge-danger')}
              title={badge.title}>
          {badge.value}
        </span>
      )}
    </NavLink>
  );
}

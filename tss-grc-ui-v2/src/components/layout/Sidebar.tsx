import { ShieldCheck, LayoutDashboard, Info, AlertTriangle, LockKeyhole, RefreshCw,
         Fingerprint, ClipboardCheck, Workflow, AlertOctagon, Files, UserCog, Truck,
         GitBranch, ShieldAlert, Calendar, Settings, Landmark, Stamp, Send, type LucideIcon } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { useI18n } from '../../lib/i18n';
import { cn } from '../../lib/cn';

interface NavItem { id: string; to: string; Icon: LucideIcon; labelKey: string; }
interface NavGroup { id: string; titleKey: string; items: NavItem[]; }

/**
 * Sidebar grouped by *role workspace* rather than a flat module list.
 * ServiceNow IRM pattern: a Risk Manager, an Auditor, and a CISO all
 * land in the same place but care about different first-screens.
 *
 *   Workspaces  →  Dashboard · Audit Workspace · Authorities
 *   Operate · Risk & Resilience  →  Risk · BCM · Incidents · Changes
 *   Operate · Compliance & Privacy  →  Compliance · Privacy · Ethics · Evidence
 *   Operate · Cybersecurity & Infrastructure  →  Cyber
 *   Operate · Third-Party & Supply Chain  →  TPRM · SCM
 *   Configure  →  Admin · Reports · Settings
 */
const NAV_GROUPS: NavGroup[] = [
  {
    id: 'workspaces', titleKey: 'navWorkspaces',
    items: [
      { id: 'dashboard',    to: '/dashboard',       Icon: LayoutDashboard, labelKey: 'itemDashboard' },
      { id: 'audit-ws',     to: '/audit-workspace', Icon: Stamp,           labelKey: 'modAuditWorkspace' },
      { id: 'authorities',  to: '/authorities',     Icon: Landmark,        labelKey: 'modAuthorities' },
      { id: 'comms',        to: '/communications',  Icon: Send,            labelKey: 'modCommunications' },
    ],
  },
  {
    id: 'risk-resilience', titleKey: 'navRiskResilience',
    items: [
      { id: 'risk',       to: '/risk',       Icon: AlertTriangle,  labelKey: 'modRisk' },
      { id: 'bcm',        to: '/bcm',        Icon: RefreshCw,      labelKey: 'modBcm' },
      { id: 'incidents',  to: '/incidents',  Icon: AlertOctagon,   labelKey: 'incidentsTitle' },
      { id: 'changes',    to: '/changes',    Icon: GitBranch,      labelKey: 'modChanges' },
    ],
  },
  {
    id: 'compliance-privacy', titleKey: 'navCompliancePrivacy',
    items: [
      { id: 'compliance', to: '/compliance', Icon: ClipboardCheck, labelKey: 'modCompliance' },
      { id: 'privacy',    to: '/privacy',    Icon: Fingerprint,    labelKey: 'modPdpl' },
      { id: 'ethics',     to: '/ethics',     Icon: ShieldAlert,    labelKey: 'modEthics' },
      { id: 'evidence',   to: '/evidence',   Icon: Files,          labelKey: 'evidenceTitle' },
    ],
  },
  {
    id: 'cyber-infra', titleKey: 'navCyberInfra',
    items: [
      { id: 'cyber',      to: '/cyber',      Icon: LockKeyhole,    labelKey: 'modCyber' },
    ],
  },
  {
    id: 'third-party-supply', titleKey: 'navThirdPartySupply',
    items: [
      { id: 'tprm',       to: '/tprm',       Icon: Workflow,       labelKey: 'modThirdParty' },
      { id: 'scm',        to: '/scm',        Icon: Truck,          labelKey: 'scmEyebrow' },
    ],
  },
  {
    id: 'configure', titleKey: 'navConfigure',
    items: [
      { id: 'admin',     to: '/admin',                Icon: UserCog,  labelKey: 'adminTitle' },
      { id: 'schedules', to: '/reports/schedules',    Icon: Calendar, labelKey: 'rsTitle' },
      { id: 'settings',  to: '/settings',             Icon: Settings, labelKey: 'itemSettings' },
      { id: 'about',     to: '/about',                Icon: Info,     labelKey: 'itemAbout' },
    ],
  },
];

export function Sidebar() {
  const { t } = useI18n();
  return (
    <aside className="sb">
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

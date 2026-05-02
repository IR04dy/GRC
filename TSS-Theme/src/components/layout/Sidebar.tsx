import { ShieldCheck, LayoutDashboard, Info, AlertTriangle, LockKeyhole, RefreshCw,
         Database, Fingerprint, ClipboardCheck, Search, Workflow, BookOpenCheck,
         Settings, type LucideIcon } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { useI18n } from '../../lib/i18n';
import { cn } from '../../lib/cn';

interface NavItem { id: string; to: string; Icon: LucideIcon; labelKey: string; }

const MAIN_ITEMS: NavItem[] = [
  { id: 'dashboard', to: '/dashboard', Icon: LayoutDashboard, labelKey: 'itemDashboard' },
  { id: 'about',     to: '/about',     Icon: Info,            labelKey: 'itemAbout' },
];

const MODULE_ITEMS: NavItem[] = [
  { id: 'risk',       to: '/risk',       Icon: AlertTriangle,  labelKey: 'modRisk' },
  { id: 'cyber',      to: '/cyber',      Icon: LockKeyhole,    labelKey: 'modCyber' },
  { id: 'bcm',        to: '/bcm',        Icon: RefreshCw,      labelKey: 'modBcm' },
  { id: 'data',       to: '/data',       Icon: Database,       labelKey: 'modData' },
  { id: 'pdpl',       to: '/pdpl',       Icon: Fingerprint,    labelKey: 'modPdpl' },
  { id: 'compliance', to: '/compliance', Icon: ClipboardCheck, labelKey: 'modCompliance' },
  { id: 'audit',      to: '/audit',      Icon: Search,         labelKey: 'modAudit' },
  { id: 'tprm',       to: '/tprm',       Icon: Workflow,       labelKey: 'modThirdParty' },
  { id: 'policy',     to: '/policy',     Icon: BookOpenCheck,  labelKey: 'modPolicy' },
];

const SETTINGS_ITEMS: NavItem[] = [
  { id: 'settings',  to: '/settings',  Icon: Settings,        labelKey: 'itemSettings' },
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

      <div className="sb-section">{t('navMain')}</div>
      {MAIN_ITEMS.map(it => <NavRow key={it.id} item={it} t={t} />)}

      <div className="sb-section">{t('navCoreModules')}</div>
      {MODULE_ITEMS.map(it => <NavRow key={it.id} item={it} t={t} />)}

      <div className="sb-section">{t('navSettings')}</div>
      {SETTINGS_ITEMS.map(it => <NavRow key={it.id} item={it} t={t} />)}
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

import { useEffect, useRef, useState } from 'react';
import { Search, Bell, Languages, LogOut, Sun, Moon, Monitor, Building2, Check, ChevronDown, Send } from 'lucide-react';
import { useI18n } from '../../lib/i18n';
import { getUser, clearSession, updateAccessTokenAndOrg } from '../../lib/auth';
import { logout, platformApi, ApiError, type OrganisationView } from '../../lib/api';
import { useNavigate } from 'react-router-dom';
import { Button } from '../ui/Button';
import { useTheme, type ThemeMode } from '../../lib/theme';
import { useToast } from '../../lib/toast';

export function Topbar({ title, sub, onOpenPalette }: { title: string; sub?: string; onOpenPalette?: () => void }) {
  const { t, lang, toggleLang } = useI18n();
  const navigate = useNavigate();
  const toast = useToast();
  const user = getUser();
  const initials = user?.username
    ? user.username.slice(0, 2).toUpperCase()
    : (lang === 'ar' ? 'AQ' : 'AQ');

  async function handleLogout() {
    await logout().catch(() => clearSession());
    navigate('/login', { replace: true });
  }

  return (
    <header className="topbar">
      <div>
        <h1 className="topbar-title">{title}</h1>
        {sub && <div className="topbar-sub">{sub}</div>}
      </div>

      <div className="topbar-right">
        <button type="button"
          className="search-box"
          onClick={() => onOpenPalette?.()}
          aria-label="Open command palette"
          style={{
            cursor: 'pointer',
            border: '1px solid var(--border)',
            background: 'var(--surface-1)',
            font: 'inherit',
            color: 'inherit',
          }}>
          <Search size={14} />
          <span style={{ flex: 1, color: 'var(--ink-3)', fontSize: 12 }}>{t('searchPlaceholder')}</span>
          <kbd>{lang === 'ar' ? 'Ctrl+K' : '⌘K'}</kbd>
        </button>

        <OrgSwitcher />

        <button
          type="button"
          className="icon-btn"
          title={t('newCommCta')}
          aria-label={t('newCommCta')}
          onClick={() => {
            const here = window.location.pathname;
            // Carry the current page as a source-record hint (best-effort).
            const hint = here.match(/^\/risk\/(\d+)/) ? `RISK:${RegExp.$1}`
                      : here.match(/^\/findings\/(\d+)/) ? `AUDIT_FINDING:${RegExp.$1}`
                      : here.match(/^\/changes/) ? '' /* no specific change in URL */
                      : '';
            const target = `/communications?new=1${hint ? `&source=${encodeURIComponent(hint)}` : ''}`;
            navigate(target);
          }}>
          <Send size={18} />
        </button>

        <button className="icon-btn" title={t('notifications')} aria-label={t('notifications')}
          onClick={() => toast.info(t('notifEmptyHint'))}>
          <Bell size={18} />
          <span className="dot" />
        </button>

        <ThemeToggle />

        <button className="lang-toggle" onClick={toggleLang} aria-label="Toggle language">
          <Languages size={14} />
          {t('langToggle')}
        </button>

        {user
          ? (
            <>
              <div className="avatar" title={user.username}>{initials}</div>
              <Button variant="ghost" size="sm" Icon={LogOut} onClick={handleLogout} aria-label={t('signOut')} />
            </>
          )
          : <div className="avatar">{initials}</div>}
      </div>
    </header>
  );
}

function ThemeToggle() {
  const { t } = useI18n();
  const { mode, setMode, effectiveMode } = useTheme();
  // Cycle: light → dark → system → light
  const next: Record<ThemeMode, ThemeMode> = { light: 'dark', dark: 'system', system: 'light' };
  const Icon = mode === 'system' ? Monitor : (effectiveMode === 'dark' ? Moon : Sun);
  const label = mode === 'system' ? t('themeModeSystem') : (mode === 'dark' ? t('themeModeDark') : t('themeModeLight'));
  return (
    <button
      className="icon-btn"
      title={`${t('themeMode')}: ${label}`}
      aria-label={`${t('themeMode')}: ${label}`}
      onClick={() => setMode(next[mode])}
    >
      <Icon size={16} />
    </button>
  );
}

function OrgSwitcher() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const user = getUser();
  const [open, setOpen] = useState(false);
  const [orgs, setOrgs] = useState<OrganisationView[]>([]);
  const [loading, setLoading] = useState(false);
  const ref = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!user) return;
    (async () => {
      setLoading(true);
      try { setOrgs(await platformApi.accessibleOrgs()); }
      catch { /* not signed in or backend down — silent */ }
      finally { setLoading(false); }
    })();
  }, [user?.organisationId]);

  // Click-outside to close
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  if (!user) return null;
  const current = orgs.find(o => o.isCurrent) ?? null;
  const canSwitch = orgs.length > 1;

  async function selectOrg(o: OrganisationView) {
    setOpen(false);
    if (o.isCurrent) return;
    try {
      const r = await platformApi.switchOrg(o.id);
      updateAccessTokenAndOrg(r.accessToken, r.organisationId);
      toast.success(`${t('orgSwitched')} → ${lang === 'ar' && o.nameAr ? o.nameAr : o.nameEn}`);
      // Hard reload so all data fetched on mount re-runs against the new tenant
      window.location.reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('orgSwitchFailed'));
    }
  }

  const label = current
    ? (lang === 'ar' && current.nameAr ? current.nameAr : current.shortCode)
    : (loading ? '…' : '—');

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        className="lang-toggle"
        onClick={() => canSwitch ? setOpen(o => !o) : undefined}
        disabled={!canSwitch}
        title={t('orgSwitcher')}
        aria-haspopup="menu"
        aria-expanded={open}
        style={{ cursor: canSwitch ? 'pointer' : 'default', opacity: canSwitch ? 1 : 0.7 }}
      >
        <Building2 size={14} />
        <span style={{ fontWeight: 600 }}>{label}</span>
        {canSwitch && <ChevronDown size={12} />}
      </button>
      {open && canSwitch && (
        <div role="menu" style={{
          position: 'absolute', top: 'calc(100% + 6px)',
          [lang === 'ar' ? 'left' : 'right']: 0,
          minWidth: 280, maxWidth: 360,
          background: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--r-md)',
          boxShadow: 'var(--shadow-3)',
          padding: 4, zIndex: 1000,
        }}>
          <div style={{ padding: '8px 10px', fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
            {t('orgSwitcher')}
          </div>
          {orgs.map(o => {
            const name = lang === 'ar' && o.nameAr ? o.nameAr : o.nameEn;
            return (
              <button key={o.id} role="menuitem" onClick={() => selectOrg(o)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 10,
                  width: '100%', padding: '8px 10px',
                  background: o.isCurrent ? 'var(--surface-2)' : 'transparent',
                  border: 'none', cursor: 'pointer',
                  borderRadius: 'var(--r-sm)',
                  color: 'var(--ink-1)', textAlign: lang === 'ar' ? 'right' : 'left',
                }}>
                <Building2 size={14} style={{ color: 'var(--ink-3)', flexShrink: 0 }} />
                <span style={{ flex: 1, minWidth: 0 }}>
                  <span style={{ display: 'block', fontWeight: 600, fontSize: 13 }}>{name}</span>
                  <span style={{ display: 'block', fontSize: 11, color: 'var(--ink-3)' }} className="mono">
                    {o.shortCode}{o.industrySector ? ` · ${o.industrySector}` : ''}
                  </span>
                </span>
                {o.isCurrent && <Check size={14} style={{ color: 'var(--success)', flexShrink: 0 }} />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

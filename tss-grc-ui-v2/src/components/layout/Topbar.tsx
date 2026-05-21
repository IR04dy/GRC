import { useEffect, useRef, useState } from 'react';
import { Search, Bell, Languages, LogOut, Sun, Moon, Monitor, Building2, Check, ChevronDown, Send, Menu } from 'lucide-react';
import { useI18n } from '../../lib/i18n';
import { getUser, clearSession, updateAccessTokenAndOrg } from '../../lib/auth';
import { logout, platformApi, ApiError, type OrganisationView } from '../../lib/api';
import { useNavigate } from 'react-router-dom';
import { Button } from '../ui/Button';
import { useTheme, type ThemeMode } from '../../lib/theme';
import { applyToDom as applyWhiteLabel } from '../../lib/whiteLabel';
import { useToast } from '../../lib/toast';
import { currentMode, MODE_LABEL, MODE_TONE, subscribeMode, type Mode } from '../../lib/productionMode';
import { unreadInApp, subscribeNotifs, markRead } from '../../lib/notificationOutbox';
import { getUser as getDirUser } from '../../lib/userDirectory';

export function Topbar({ title, sub, onOpenPalette, onToggleSidebar }: { title?: string; sub?: string; onOpenPalette?: () => void; onToggleSidebar?: () => void }) {
  const { t, lang, toggleLang } = useI18n();
  const navigate = useNavigate();
  const user = getUser();
  const initials = user?.username
    ? user.username.slice(0, 2).toUpperCase()
    : (lang === 'ar' ? 'AQ' : 'AQ');

  async function handleLogout() {
    await logout().catch(() => clearSession());
    // Re-apply white-label after the session is cleared. With no user
    // in sessionStorage the storage key resolves to "global" → empty →
    // DEFAULT_CONFIG → CSS variables snap back to the unbranded palette
    // for the login page.
    applyWhiteLabel();
    navigate('/login', { replace: true });
  }

  return (
    <header className="topbar">
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        {/* Sprint 4 §2.8 — hidden on desktop via CSS .sb-hamburger rule */}
        <button
          type="button"
          className="sb-hamburger"
          onClick={() => onToggleSidebar?.()}
          aria-label="Toggle sidebar"
          style={{
            display: 'none',
            background: 'transparent', border: 'none', cursor: 'pointer',
            padding: 6, color: 'var(--ink-1)', borderRadius: 'var(--r-sm)',
          }}
        >
          <Menu size={20} />
        </button>
        <div>
          <h1 className="topbar-title">{title}</h1>
          {sub && <div className="topbar-sub">{sub}</div>}
        </div>
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

        <ModeBadge />

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

        <NotificationBellButton />


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

function ModeBadge() {
  const { t, lang } = useI18n();
  const navigate = useNavigate();
  const [mode, setLocalMode] = useState<Mode>(() => currentMode());
  useEffect(() => subscribeMode(() => setLocalMode(currentMode())), []);
  const tone = MODE_TONE[mode];
  const label = lang === 'ar' ? MODE_LABEL[mode].ar : MODE_LABEL[mode].en;
  // Sprint 4 §3.3 culled /production-readiness. Route the badge click to
  // /admin (where mode + tenant settings now live) so this stops being a
  // dead link from every page in the platform.
  return (
    <button
      type="button"
      onClick={() => navigate('/admin')}
      title={t('modeBadgeTitle')}
      style={{
        padding: '4px 10px',
        borderRadius: 'var(--r-sm)',
        border: '1px solid var(--border)',
        background: tone === 'success' ? 'var(--success-bg)'
                  : tone === 'warning' ? 'var(--warning-bg)'
                  :                       'var(--surface-2)',
        color: tone === 'success' ? 'var(--success-fg)'
              : tone === 'warning' ? 'var(--warning-fg)'
              :                       'var(--ink-2)',
        fontSize: 11, fontWeight: 600,
        cursor: 'pointer',
        display: 'inline-flex', alignItems: 'center', gap: 4,
      }}>
      <span style={{
        width: 6, height: 6, borderRadius: '50%',
        background: 'currentColor', display: 'inline-block',
      }} />
      {label}
    </button>
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

// ─── Notification bell — Phase E ────────────────────────────────────────────

function NotificationBellButton() {
  const { t, lang } = useI18n();
  const navigate = useNavigate();
  const [, setTick] = useState(0);
  const [open, setOpen] = useState(false);
  useEffect(() => subscribeNotifs(() => setTick(x => x + 1)), []);
  const unread = unreadInApp();
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    if (open) document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [open]);

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button className="icon-btn" title={t('noxNotifBellTip')} aria-label={t('noxNotifBellTip')}
        onClick={() => setOpen(o => !o)}>
        <Bell size={18} />
        {unread.length > 0 && (
          <span style={{
            position: 'absolute', top: 2, insetInlineEnd: 2,
            minWidth: 16, height: 16, padding: '0 4px',
            background: 'var(--danger)', color: 'white',
            borderRadius: 8, fontSize: 9, fontWeight: 700,
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          }}>{unread.length > 9 ? '9+' : unread.length}</span>
        )}
      </button>
      {open && (
        <div style={{
          position: 'absolute', insetInlineEnd: 0, top: '100%', marginTop: 6,
          width: 360, maxWidth: '90vw', maxHeight: 480,
          background: 'var(--surface-1)', borderRadius: 'var(--r-md)',
          border: '1px solid var(--border)',
          boxShadow: '0 12px 32px rgba(0,0,0,0.15)',
          zIndex: 200, overflow: 'hidden',
          display: 'flex', flexDirection: 'column',
        }}>
          <div style={{
            padding: '10px 14px', borderBottom: '1px solid var(--border)',
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          }}>
            <div style={{ fontSize: 12, fontWeight: 700 }}>{t('noxNotifBellTip')}</div>
            <button onClick={() => { setOpen(false); navigate('/notifications/outbox'); }}
              style={{ fontSize: 11, color: 'var(--primary)', background: 'transparent', border: 'none', cursor: 'pointer', fontWeight: 600 }}>
              {t('noxTitle')} →
            </button>
          </div>
          <div style={{ flex: 1, overflow: 'auto' }}>
            {unread.length === 0 ? (
              <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
                —
              </div>
            ) : unread.slice(0, 8).map(n => {
              const recipient = getDirUser(n.recipientId);
              return (
                <button key={n.id} onClick={() => { markRead(n.id); if (n.link) { setOpen(false); navigate(n.link); } }}
                  style={{
                    width: '100%', padding: '10px 12px',
                    border: 'none', background: 'transparent',
                    borderBottom: '1px dashed var(--border)',
                    textAlign: 'start', cursor: 'pointer',
                    display: 'block',
                  }}
                  onMouseEnter={e => e.currentTarget.style.background = 'var(--primary-50)'}
                  onMouseLeave={e => e.currentTarget.style.background = 'transparent'}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 6 }}>
                    <span style={{ fontSize: 12, fontWeight: 600, flex: 1 }}>
                      {lang === 'ar' ? n.subjectAr : n.subjectEn}
                    </span>
                    <span style={{
                      fontSize: 9, color: 'white', padding: '1px 5px',
                      background: n.priority === 'P1' ? 'var(--danger)' : n.priority === 'P2' ? 'var(--warning)' : 'var(--ink-3)',
                      borderRadius: 3, fontWeight: 700,
                    }}>{n.priority}</span>
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2, lineHeight: 1.4 }}>
                    {(lang === 'ar' ? n.bodyAr : n.bodyEn).slice(0, 100)}
                  </div>
                  <div style={{ fontSize: 9, color: 'var(--ink-3)', marginTop: 4 }} className="mono">
                    {recipient ? (lang === 'ar' ? recipient.nameAr : recipient.nameEn) : n.recipientId} · {new Date(n.createdAt).toLocaleTimeString()}
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

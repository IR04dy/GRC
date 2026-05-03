import { Search, Bell, Languages, LogOut, Sun, Moon, Monitor } from 'lucide-react';
import { useI18n } from '../../lib/i18n';
import { getUser, clearSession } from '../../lib/auth';
import { logout } from '../../lib/api';
import { useNavigate } from 'react-router-dom';
import { Button } from '../ui/Button';
import { useTheme, type ThemeMode } from '../../lib/theme';

export function Topbar({ title, sub }: { title: string; sub?: string }) {
  const { t, lang, toggleLang } = useI18n();
  const navigate = useNavigate();
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
        <div className="search-box">
          <Search size={14} />
          <input placeholder={t('searchPlaceholder')} />
          <kbd>{lang === 'ar' ? 'Ctrl+K' : '⌘K'}</kbd>
        </div>

        <button className="icon-btn" title={t('notifications')} aria-label={t('notifications')}>
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

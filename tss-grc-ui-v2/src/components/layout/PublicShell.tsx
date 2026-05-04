import { type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ShieldCheck, Globe } from 'lucide-react';
import { useI18n } from '../../lib/i18n';

/**
 * Layout for public-facing portals: DSR, SpeakUp, VRP, Trust Center, etc.
 * Strips the authenticated Shell (no sidebar, no app navigation, no auth-required
 * search bar). Inspired by:
 *  - OneTrust DSR webform — minimal chrome, multi-language, branded header
 *  - NAVEX EthicsPoint — neutral hosting, anonymity-first, no analytics calls
 *  - Vanta Trust Center — public credentialing surface
 *
 * Public pages must NEVER:
 *  - Render the authenticated Shell sidebar
 *  - Call /api endpoints that require a JWT
 *  - Send telemetry tied to a logged-in user identity
 */
export function PublicShell({ title, sub, children }: {
  title: string;
  sub?: string;
  children: ReactNode;
}) {
  const { t, lang, setLang, dir } = useI18n();
  return (
    <div dir={dir} style={{
      minHeight: '100vh',
      background: 'var(--surface-2)',
      display: 'flex',
      flexDirection: 'column',
    }}>
      <header style={{
        background: 'var(--surface-1)',
        borderBottom: '1px solid var(--border)',
        padding: '14px 28px',
        display: 'flex', alignItems: 'center', gap: 14,
      }}>
        <div style={{
          width: 36, height: 36, borderRadius: 'var(--r-sm)',
          background: 'var(--primary-50)', color: 'var(--primary)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <ShieldCheck size={20} strokeWidth={2} />
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
            {t('brandName')}
          </div>
          <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-1)' }}>
            {t('publicPortalLabel')}
          </div>
        </div>
        <button onClick={() => setLang(lang === 'ar' ? 'en' : 'ar')}
          style={{
            display: 'inline-flex', alignItems: 'center', gap: 6,
            padding: '6px 12px', fontSize: 13,
            border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
            background: 'var(--surface-2)', color: 'var(--ink-1)', cursor: 'pointer',
          }}>
          <Globe size={14} />
          {lang === 'ar' ? 'English' : 'العربية'}
        </button>
      </header>

      <main style={{
        flex: 1,
        maxWidth: 880,
        width: '100%',
        margin: '0 auto',
        padding: '32px 24px',
      }}>
        <div style={{ marginBottom: 24 }}>
          <h1 style={{
            margin: 0, fontSize: 28, fontWeight: 700,
            color: 'var(--ink-1)', lineHeight: 1.2,
          }}>{title}</h1>
          {sub && (
            <p style={{
              marginTop: 8, fontSize: 14, color: 'var(--ink-2)',
              lineHeight: 1.6, maxWidth: 720,
            }}>{sub}</p>
          )}
        </div>
        {children}
      </main>

      <footer style={{
        background: 'var(--surface-1)',
        borderTop: '1px solid var(--border)',
        padding: '16px 28px',
        fontSize: 11, color: 'var(--ink-3)',
        display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8,
      }}>
        <div>{t('publicFooterPrivacy')}</div>
        <div style={{ display: 'flex', gap: 16 }}>
          <Link to="/portal/trust-center" style={{ color: 'var(--ink-2)', textDecoration: 'none' }}>
            {t('publicFooterTrustCenter')}
          </Link>
          <Link to="/portal/track" style={{ color: 'var(--ink-2)', textDecoration: 'none' }}>
            {t('publicFooterTracker')}
          </Link>
        </div>
      </footer>
    </div>
  );
}

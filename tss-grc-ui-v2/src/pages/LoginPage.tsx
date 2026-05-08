import { type FormEvent, useState } from 'react';
import { ShieldCheck, Languages, LogIn, ArrowRight } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useI18n } from '../lib/i18n';
import { login as apiLogin, ApiError } from '../lib/api';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { applyToDom as applyWhiteLabel } from '../lib/whiteLabel';

const FRAMEWORKS = ['NCA ECC', 'SDAIA', 'PDPL', 'SAMA', 'ISO 27001', 'ISO 22301', 'ISO 31000', 'COSO ERM'];

export function LoginPage() {
  const { t, toggleLang } = useI18n();
  const navigate = useNavigate();
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('Admin@12345');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await apiLogin(username.trim(), password);
      // Re-apply white-label now that the user (and therefore the tenant
      // scope key) is set in sessionStorage. Without this, the app
      // navigates to /risk still wearing the default palette because
      // applyWhiteLabel() at boot ran with no tenant context.
      applyWhiteLabel();
      navigate('/risk', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.status === 401 ? t('loginInvalid') : err.message);
      } else {
        setError(t('loginNetworkError'));
      }
    } finally {
      setSubmitting(false);
    }
  }

  function continueDemo() {
    navigate('/risk', { replace: true });
  }

  return (
    <div className="login-shell">
      <aside className="login-hero">
        <div className="login-hero-mark">
          <div className="sb-mark"><ShieldCheck size={20} /></div>
          {t('brandName')}
        </div>
        <div className="login-hero-content">
          <h1>{t('loginHero')}</h1>
          <p>{t('loginHeroSub')}</p>
          <div className="login-hero-frameworks">
            {FRAMEWORKS.map(f => <span key={f} className="fw-pill">{f}</span>)}
          </div>
        </div>
        <div style={{ position: 'relative', zIndex: 1, opacity: .7, fontSize: 11, fontFamily: 'var(--font-mono)', letterSpacing: '.08em' }}>
          {t('brandSub')}
        </div>
      </aside>

      <main className="login-form-side">
        <form className="login-form" onSubmit={onSubmit}>
          <div className="login-eyebrow">{t('loginEyebrow')}</div>
          <h2>{t('loginTitle')}</h2>
          <div className="login-sub">{t('loginSub')}</div>

          <Input
            id="username"
            label={t('loginUsername')}
            value={username}
            onChange={e => setUsername(e.currentTarget.value)}
            autoComplete="username"
            required
          />
          <Input
            id="password"
            label={t('loginPassword')}
            type="password"
            value={password}
            onChange={e => setPassword(e.currentTarget.value)}
            autoComplete="current-password"
            required
          />

          {error && <div className="login-error">{error}</div>}

          <Button type="submit" Icon={LogIn} disabled={submitting} size="lg">
            {submitting ? t('loginSubmitting') : t('loginSubmit')}
          </Button>

          <Button type="button" variant="ghost" Icon={ArrowRight} onClick={continueDemo}>
            {t('loginSkipDemo')}
          </Button>

          <Button type="button" variant="ghost" size="sm" Icon={Languages} onClick={toggleLang}>
            {t('langToggle')}
          </Button>

          <div className="login-hint"><b>{t('loginHint')}</b></div>
        </form>
      </main>
    </div>
  );
}

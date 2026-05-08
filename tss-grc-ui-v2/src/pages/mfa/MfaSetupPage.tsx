import { useEffect, useMemo, useState } from 'react';
import {
  ShieldCheck, Copy, RefreshCw, AlertOctagon, KeyRound, Smartphone, CheckCheck, Eye, EyeOff,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  generateSecret, buildOtpAuthUri, verifyTotp, generateTotp,
  generateRecoveryCodes, secondsLeftInWindow,
} from '../../lib/totp';
import {
  isEnrolled, getEnrolment, setEnrolment, clearEnrolment,
  type MfaEnrolment,
} from '../../lib/mfa';
import { getUser } from '../../lib/auth';

/**
 * MfaSetupPage — Sprint 6 production hardening.
 *
 * Self-service TOTP enrolment. Three-step ritual:
 *   1. Generate secret + render otpauth:// QR (for Authenticator apps)
 *      and the manual secret (for users who type it in).
 *   2. User enters the 6-digit code shown by their app; we verify against
 *      ±1 window (90s tolerance).
 *   3. On success, generate 10 single-use recovery codes + show them
 *      ONCE for the user to save.
 *
 * Already-enrolled users see status + can re-set or unenrol.
 */
export function MfaSetupPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const user = getUser();
  const userRef = user?.username ?? 'demo@tss.sa';
  const issuer = 'TSS GRC Platform';

  const [enrolled, setEnrolledState] = useState<boolean>(() => isEnrolled(userRef));
  const [existing, setExisting] = useState<MfaEnrolment | undefined>(() => getEnrolment(userRef));

  // ── Setup-flow state ───────────────────────────────────────────────
  const [secret, setSecret] = useState<string>('');
  const [code, setCode] = useState<string>('');
  const [verifying, setVerifying] = useState(false);
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [showSecret, setShowSecret] = useState(false);
  const [stepDone, setStepDone] = useState(false);

  // Generate fresh secret on first mount (or after re-set).
  useEffect(() => {
    if (!enrolled && !secret) setSecret(generateSecret());
  }, [enrolled, secret]);

  // Live countdown timer — re-renders every second.
  const [, forceTick] = useState(0);
  useEffect(() => {
    const id = window.setInterval(() => forceTick(x => x + 1), 1000);
    return () => window.clearInterval(id);
  }, []);
  const secondsLeft = secondsLeftInWindow();

  // For demo: surface the current valid code so the user can verify the
  // implementation works without an Authenticator app on hand. Hidden by
  // default behind an "eye" toggle.
  const [demoCode, setDemoCode] = useState('');
  useEffect(() => {
    if (!secret) return;
    let cancelled = false;
    generateTotp(secret).then(c => { if (!cancelled) setDemoCode(c); });
    return () => { cancelled = true; };
  }, [secret, secondsLeft]);

  const otpauthUri = useMemo(() => secret ? buildOtpAuthUri({
    secret, account: userRef, issuer,
  }) : '', [secret, userRef]);

  // QR-code SVG via a free public service. In production swap for a local
  // generator (e.g. `qrcode` npm package — see the production-readiness
  // checklist for the full list).
  const qrUrl = otpauthUri
    ? `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(otpauthUri)}`
    : '';

  async function verifyAndEnrol() {
    if (!secret || code.length < 6) return;
    setVerifying(true);
    try {
      const ok = await verifyTotp(secret, code);
      if (!ok) {
        toast.danger(t('mfaInvalidCode'));
        return;
      }
      const codes = generateRecoveryCodes();
      setRecoveryCodes(codes);
      const enrolment: MfaEnrolment = {
        userRef,
        secret,
        enrolledAt: new Date().toISOString(),
        recoveryCodes: codes,
        consumedRecoveryCodes: [],
      };
      setEnrolment(userRef, enrolment);
      setEnrolledState(true);
      setExisting(enrolment);
      setStepDone(true);
      toast.success(t('mfaEnrolledToast'));
    } finally {
      setVerifying(false);
    }
  }

  function unenrol() {
    if (!confirm(t('mfaUnenrolConfirm'))) return;
    clearEnrolment(userRef);
    setEnrolledState(false);
    setExisting(undefined);
    setSecret(generateSecret());
    setCode('');
    setRecoveryCodes([]);
    setStepDone(false);
    toast.info(t('mfaUnenrolledToast'));
  }

  function reroll() {
    setSecret(generateSecret());
    setCode('');
    setStepDone(false);
    setRecoveryCodes([]);
  }

  function copyToClipboard(text: string, label: string) {
    navigator.clipboard.writeText(text).then(() => {
      toast.success(t('mfaCopiedToast').replace('{what}', label));
    });
  }

  function downloadRecoveryCodes() {
    const blob = new Blob(
      [`${issuer} — Recovery codes\nUser: ${userRef}\nGenerated: ${new Date().toISOString()}\n\n${recoveryCodes.join('\n')}\n\nKeep these somewhere safe. Each code can be used once.\n`],
      { type: 'text/plain' },
    );
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `tss-mfa-recovery-${userRef}.txt`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    toast.success(t('mfaCodesDownloaded'));
  }

  // ── Already-enrolled view ──────────────────────────────────────────
  if (enrolled && existing && !stepDone) {
    return (
      <Shell title={t('mfaTitle')} sub={t('mfaSub')}>
        <PageHeader eyebrow={t('mfaEyebrow')} title={t('mfaTitle')} sub={t('mfaPageSub')} />
        <Card title={t('mfaStatusTitle')} Icon={ShieldCheck}
          action={<Badge tone="success">{t('mfaEnrolledBadge')}</Badge>}>
          <div style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.7 }}>
            {t('mfaEnrolledNarrative').replace('{when}', new Date(existing.enrolledAt ?? '').toLocaleString())}
          </div>
          <div style={{ marginTop: 16, display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
            <StatBox label={t('mfaStatRecovery')} value={`${existing.recoveryCodes.length} / ${existing.recoveryCodes.length + existing.consumedRecoveryCodes.length}`} />
            <StatBox label={t('mfaStatLastUsed')} value={existing.lastUsedAt ? new Date(existing.lastUsedAt).toLocaleDateString() : '—'} />
            <StatBox label={t('mfaStatUser')} value={existing.userRef} />
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
            <Button variant="secondary" Icon={RefreshCw} onClick={() => { unenrol(); }}>{t('mfaResetBtn')}</Button>
            <Button variant="ghost" Icon={AlertOctagon} onClick={unenrol}>{t('mfaUnenrolBtn')}</Button>
          </div>
        </Card>
      </Shell>
    );
  }

  // ── Recovery-codes view (post-enrolment) ───────────────────────────
  if (stepDone && recoveryCodes.length > 0) {
    return (
      <Shell title={t('mfaTitle')} sub={t('mfaSub')}>
        <PageHeader eyebrow={t('mfaEyebrow')} title={t('mfaTitle')} />
        <Card title={t('mfaRecoveryTitle')} Icon={KeyRound}
          action={<Badge tone="success">{t('mfaEnrolledBadge')}</Badge>}>
          <div style={{
            padding: 12, background: 'var(--warning-bg)', color: 'var(--warning-fg)',
            borderRadius: 'var(--r-sm)', fontSize: 12, lineHeight: 1.6, marginBottom: 16,
          }}>
            <strong>{t('mfaRecoveryWarning')}</strong>
          </div>
          <div style={{
            display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 8,
            padding: 12, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
            fontFamily: 'var(--font-mono)', fontSize: 14, fontWeight: 600,
          }}>
            {recoveryCodes.map(c => (
              <div key={c} style={{ padding: '6px 10px', background: 'var(--surface-1)', borderRadius: 'var(--r-sm)' }}>{c}</div>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
            <Button variant="primary" Icon={Copy} onClick={() => copyToClipboard(recoveryCodes.join('\n'), t('mfaRecoveryCodesLabel'))}>{t('mfaCopyAll')}</Button>
            <Button variant="secondary" onClick={downloadRecoveryCodes}>{t('mfaDownloadTxt')}</Button>
            <span style={{ flex: 1 }} />
            <Button variant="ghost" onClick={() => setStepDone(false)}>{t('mfaDoneBtn')}</Button>
          </div>
        </Card>
      </Shell>
    );
  }

  // ── Setup ritual ───────────────────────────────────────────────────
  return (
    <Shell title={t('mfaTitle')} sub={t('mfaSub')}>
      <PageHeader eyebrow={t('mfaEyebrow')} title={t('mfaTitle')} sub={t('mfaPageSub')} />

      <Card title={t('mfaStep1Title')} Icon={Smartphone}>
        <div style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6, marginBottom: 14 }}>
          {t('mfaStep1Narrative')}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '220px 1fr', gap: 24, alignItems: 'flex-start' }}>
          <div style={{
            padding: 10, background: 'white', border: '1px solid var(--border)',
            borderRadius: 'var(--r-md)', textAlign: 'center',
          }}>
            {qrUrl
              ? <img src={qrUrl} alt={t('mfaQrAlt')} style={{ width: 200, height: 200, display: 'block' }} />
              : <div style={{ width: 200, height: 200, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{t('mfaQrLoading')}</div>}
            <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 6 }}>{t('mfaQrHint')}</div>
          </div>
          <div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 6 }}>
              {t('mfaSecretLabel')}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
              <code style={{
                flex: 1, padding: '10px 12px',
                background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
                fontFamily: 'var(--font-mono)', fontSize: 14, letterSpacing: '0.06em',
                wordBreak: 'break-all',
              }}>
                {showSecret ? secret : secret.replace(/./g, '•')}
              </code>
              <Button variant="ghost" Icon={showSecret ? EyeOff : Eye} onClick={() => setShowSecret(s => !s)}>{showSecret ? t('mfaHide') : t('mfaShow')}</Button>
              <Button variant="secondary" Icon={Copy} onClick={() => copyToClipboard(secret, t('mfaSecretLabel'))}>{t('mfaCopy')}</Button>
            </div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
              {t('mfaIssuerLabel')}: <strong>{issuer}</strong> · {t('mfaAccountLabel')}: <strong>{userRef}</strong>
            </div>
            <details style={{ marginTop: 12, fontSize: 11, color: 'var(--ink-3)' }}>
              <summary style={{ cursor: 'pointer' }}>{t('mfaDevHelpSummary')}</summary>
              <div style={{ padding: 8, marginTop: 6, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)' }}>
                <div style={{ marginBottom: 4 }}>{t('mfaDevHelpNote')}</div>
                <div className="mono">
                  {t('mfaCurrentCode')}: <strong style={{ fontSize: 14, letterSpacing: '0.1em' }}>{demoCode}</strong>
                  {' · '}{t('mfaWindowLabel')}: {secondsLeft}s
                </div>
              </div>
            </details>
          </div>
        </div>
      </Card>

      <div style={{ marginTop: 16 }}>
        <Card title={t('mfaStep2Title')} Icon={CheckCheck}>
          <div style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6, marginBottom: 12 }}>
            {t('mfaStep2Narrative')}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
            <input
              value={code}
              onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder="123456"
              maxLength={6}
              inputMode="numeric"
              style={{
                fontSize: 24, fontFamily: 'var(--font-mono)', letterSpacing: '0.4em',
                padding: '10px 16px', width: 200, textAlign: 'center',
                border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                background: 'var(--surface-1)',
              }} />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 4 }}>
                {t('mfaWindowLabel')}: {secondsLeft}s
              </div>
              <div style={{
                height: 4, background: 'var(--surface-2)', borderRadius: 2, overflow: 'hidden',
              }}>
                <div style={{
                  width: `${(secondsLeft / 30) * 100}%`,
                  height: '100%',
                  background: secondsLeft <= 5 ? 'var(--danger)' : 'var(--primary)',
                  transition: 'width 1s linear',
                }} />
              </div>
            </div>
            <Button
              variant="primary"
              Icon={ShieldCheck}
              onClick={verifyAndEnrol}
              disabled={code.length < 6 || verifying}>
              {verifying ? t('mfaVerifying') : t('mfaVerifyBtn')}
            </Button>
          </div>
          <div style={{ marginTop: 14, display: 'flex', gap: 6, alignItems: 'center' }}>
            <Button variant="ghost" Icon={RefreshCw} onClick={reroll}>{t('mfaRerollBtn')}</Button>
            <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{t('mfaRerollHint')}</span>
          </div>
        </Card>
      </div>
    </Shell>
  );
}

function StatBox({ label, value }: { label: string; value: string }) {
  return (
    <div style={{
      padding: 12, background: 'var(--surface-2)',
      borderRadius: 'var(--r-sm)', border: '1px solid var(--border)',
    }}>
      <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{label}</div>
      <div style={{ fontSize: 14, fontWeight: 700, marginTop: 4, fontFamily: 'var(--font-mono)' }}>{value}</div>
    </div>
  );
}

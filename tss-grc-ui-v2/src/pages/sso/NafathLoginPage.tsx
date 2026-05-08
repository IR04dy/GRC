import { useEffect, useState } from 'react';
import { Smartphone, ShieldCheck, AlertOctagon, Clock, RefreshCw, Check, XCircle } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  initiateNafath, getNafathTransaction, simulateNafathApprove, simulateNafathReject,
  STATUS_TONE, STATUS_LABEL,
  type NafathTransaction,
} from '../../lib/sso';

/**
 * NafathLoginPage — Sprint 6 production hardening.
 *
 * Saudi citizen + Iqama-holder SSO via the national Nafath app. Three
 * stages:
 *   1. Enter national ID / Iqama
 *   2. App displays a 2-digit number; user picks the same number in the
 *      Nafath mobile app to approve. Expires in 60s.
 *   3. On approval the citizen is signed into the public-facing portal.
 *
 * The demo simulates the Nafath round-trip: a "Simulate approval"
 * button stands in for the user tapping their phone. Production swaps
 * for the real Nafath callback API.
 */
export function NafathLoginPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [nationalId, setNationalId] = useState('');
  const [tx, setTx] = useState<NafathTransaction | null>(null);
  const [ticker, setTicker] = useState(0);

  // Re-render every second so the countdown updates + we re-poll the tx.
  useEffect(() => {
    const id = window.setInterval(() => setTicker(x => x + 1), 1000);
    return () => window.clearInterval(id);
  }, []);

  // Auto-poll for status changes when there's an active transaction.
  useEffect(() => {
    if (!tx) return;
    const fresh = getNafathTransaction(tx.id);
    if (fresh && fresh.status !== tx.status) setTx(fresh);
  }, [tx, ticker]);

  function start() {
    const cleaned = nationalId.replace(/\D/g, '').slice(0, 10);
    if (cleaned.length !== 10) {
      toast.danger(t('nafathInvalidIdToast'));
      return;
    }
    const fresh = initiateNafath(cleaned);
    setTx(fresh);
    toast.success(t('nafathInitiatedToast'));
  }

  function simulateApprove() {
    if (!tx) return;
    const next = simulateNafathApprove(tx.id);
    if (next) setTx(next);
  }
  function simulateReject() {
    if (!tx) return;
    const next = simulateNafathReject(tx.id);
    if (next) setTx(next);
  }

  function reset() {
    setTx(null);
    setNationalId('');
  }

  // ── Render ────────────────────────────────────────────────────────
  if (!tx) {
    return (
      <Shell title={t('nafathTitle')} sub={t('nafathSub')}>
        <PageHeader eyebrow={t('nafathEyebrow')} title={t('nafathTitle')} sub={t('nafathPageSub')} />
        <Card title={t('nafathFormTitle')} Icon={ShieldCheck}>
          <div style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.7, marginBottom: 16 }}>
            {t('nafathFormNarrative')}
          </div>
          <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 14 }}>
            <input
              type="text"
              inputMode="numeric"
              maxLength={10}
              value={nationalId}
              onChange={e => setNationalId(e.target.value.replace(/\D/g, '').slice(0, 10))}
              placeholder="1XXXXXXXXX"
              style={{
                fontSize: 22, fontFamily: 'var(--font-mono)', letterSpacing: '0.2em',
                padding: '12px 16px', width: 240, textAlign: 'center',
                border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                background: 'var(--surface-1)',
              }} />
            <Button variant="primary" Icon={Smartphone} onClick={start}>
              {t('nafathInitiateBtn')}
            </Button>
          </div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', lineHeight: 1.5 }}>
            {t('nafathPrivacyNote')}
          </div>
        </Card>
      </Shell>
    );
  }

  // Active transaction view
  const elapsed = (Date.now() - new Date(tx.startedAt).getTime()) / 1000;
  const secondsLeft = Math.max(0, Math.round(tx.expiresInSeconds - elapsed));
  const isPending = tx.status === 'PENDING';

  return (
    <Shell title={t('nafathTitle')} sub={t('nafathSub')}>
      <PageHeader eyebrow={t('nafathEyebrow')} title={t('nafathTitle')} />
      <Card title={t('nafathTxTitle')} Icon={Smartphone}
        action={<Badge tone={STATUS_TONE[tx.status]}>{lang === 'ar' ? STATUS_LABEL[tx.status].ar : STATUS_LABEL[tx.status].en}</Badge>}>

        {/* Random number panel */}
        <div style={{
          padding: 24, borderRadius: 'var(--r-md)', textAlign: 'center',
          background: isPending ? 'var(--primary-50)'
                    : tx.status === 'APPROVED' ? 'var(--success-bg)'
                    : 'var(--warning-bg)',
          border: `2px solid ${isPending ? 'var(--primary)'
                               : tx.status === 'APPROVED' ? 'var(--success-fg)'
                               : 'var(--warning-fg)'}`,
        }}>
          {isPending && (
            <>
              <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>
                {t('nafathPickThisNumber')}
              </div>
              <div className="mono" style={{ fontSize: 64, fontWeight: 800, color: 'var(--primary)', letterSpacing: '0.05em' }}>
                {tx.randomNumber}
              </div>
              <div style={{ marginTop: 12, fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6 }}>
                {t('nafathPickInstruction')}
              </div>
            </>
          )}
          {tx.status === 'APPROVED' && (
            <>
              <Check size={48} style={{ color: 'var(--success-fg)', marginBottom: 8 }} />
              <div style={{ fontSize: 18, fontWeight: 700 }}>{t('nafathApprovedHeading')}</div>
              <div style={{ fontSize: 13, color: 'var(--ink-2)', marginTop: 6 }}>
                {t('nafathApprovedNarrative')}
              </div>
            </>
          )}
          {tx.status === 'REJECTED' && (
            <>
              <XCircle size={48} style={{ color: 'var(--danger)', marginBottom: 8 }} />
              <div style={{ fontSize: 18, fontWeight: 700 }}>{t('nafathRejectedHeading')}</div>
              <div style={{ fontSize: 13, color: 'var(--ink-2)', marginTop: 6 }}>
                {t('nafathRejectedNarrative')}
              </div>
            </>
          )}
          {tx.status === 'EXPIRED' && (
            <>
              <AlertOctagon size={48} style={{ color: 'var(--warning-fg)', marginBottom: 8 }} />
              <div style={{ fontSize: 18, fontWeight: 700 }}>{t('nafathExpiredHeading')}</div>
              <div style={{ fontSize: 13, color: 'var(--ink-2)', marginTop: 6 }}>
                {t('nafathExpiredNarrative')}
              </div>
            </>
          )}
        </div>

        {/* Countdown + transaction meta */}
        {isPending && (
          <div style={{ marginTop: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4, fontSize: 11, color: 'var(--ink-3)' }}>
              <Clock size={11} />
              {t('nafathExpiresIn')}: {secondsLeft}s
            </div>
            <div style={{ height: 4, background: 'var(--surface-2)', borderRadius: 2, overflow: 'hidden' }}>
              <div style={{
                width: `${(secondsLeft / tx.expiresInSeconds) * 100}%`,
                height: '100%',
                background: secondsLeft <= 10 ? 'var(--danger)' : 'var(--primary)',
                transition: 'width 1s linear',
              }} />
            </div>
          </div>
        )}

        <div style={{ marginTop: 16, display: 'flex', flexWrap: 'wrap', gap: 10, fontSize: 11, color: 'var(--ink-3)' }}>
          <div>
            <div style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('nafathTxId')}</div>
            <div className="mono">{tx.id}</div>
          </div>
          <div>
            <div style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('nafathNationalId')}</div>
            <div className="mono">••••••{tx.nationalId.slice(-4)}</div>
          </div>
          <div>
            <div style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('nafathStartedAt')}</div>
            <div className="mono">{tx.startedAt.slice(11, 19)}</div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
          {isPending && (
            <>
              <Button variant="primary" Icon={Check} onClick={simulateApprove}>{t('nafathSimulateApprove')}</Button>
              <Button variant="secondary" Icon={XCircle} onClick={simulateReject}>{t('nafathSimulateReject')}</Button>
            </>
          )}
          <span style={{ flex: 1 }} />
          <Button variant="ghost" Icon={RefreshCw} onClick={reset}>{t('nafathResetBtn')}</Button>
        </div>

        <div style={{
          marginTop: 16, padding: 10, background: 'var(--surface-2)',
          borderRadius: 'var(--r-sm)', fontSize: 11, color: 'var(--ink-3)', lineHeight: 1.6,
        }}>
          <strong>{t('nafathDemoNoteHeading')}:</strong> {t('nafathDemoNote')}
        </div>
      </Card>
    </Shell>
  );
}

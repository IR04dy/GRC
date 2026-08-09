import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, AlertCircle, CheckCircle2, Clock } from 'lucide-react';
import { PublicShell } from '../../components/layout/PublicShell';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import {
  trackByReferenceCode, INTAKE_CATALOG, INTAKE_STATUS_TONE, INTAKE_STATUS_LABEL,
  type IntakePublicStatus,
} from '../../lib/intake';

/**
 * Case-tracker — DD-55b.
 *
 * Anonymous reporter pastes their reference code (e.g. "DS-K7L9-Z4M2"
 * or "SU-X9P3-T2W7") and sees the current status, latest update from
 * the triager, and SLA countdown. No account needed.
 *
 * Modelled on:
 *  - industry-standard tooling DSR self-service tracker
 *  - NAVEX EthicsPoint case-status portal
 *
 * Privacy guardrails — F2-172 made these structural rather than editorial.
 *
 * This page used to read `useIntake()`, i.e. the browser's own localStorage store, so it
 * could only ever find a code the same browser had submitted and it rendered a full event
 * timeline that no server had produced. It now calls
 * `GET /api/v1/intake/track/{code}`, which is `permitAll` and answers with a deliberately
 * minimal projection: reference code, kind, status, acknowledgement and SLA date. No
 * submitter identity, no payload, no assignee, and **no event history** (SEC-FIN-001 — a
 * reference code is a weak, often-enumerable bearer, so the server treats it as authorising
 * a status check and nothing more).
 *
 * ⚠ So this page deliberately shows LESS than it used to. The timeline is gone because the
 * server does not send one to an unauthenticated caller, and reconstructing one on the client
 * is exactly the fabrication F2-171 removed. Widening the projection is a security decision
 * about SEC-FIN-001, not a rendering change.
 */
export function CaseTrackerPage() {
  const { t, lang } = useI18n();
  const [searchParams, setSearchParams] = useSearchParams();
  const [code, setCode] = useState(searchParams.get('code') ?? '');
  const [result, setResult] = useState<IntakePublicStatus | null | 'not-found' | 'error'>(null);
  const [busy, setBusy] = useState(false);

  async function run(raw: string) {
    setBusy(true);
    try {
      const found = await trackByReferenceCode(raw);
      setResult(found ?? 'not-found');
    } catch {
      // A transport failure is not "no such code". Telling a data subject their case does
      // not exist because the server was unreachable is the worse of the two wrong answers.
      setResult('error');
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    const initial = searchParams.get('code');
    if (initial) {
      setCode(initial);
      void run(initial);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function lookup(e?: React.FormEvent) {
    e?.preventDefault();
    const trimmed = code.trim().toUpperCase();
    if (!trimmed) return;
    setSearchParams({ code: trimmed }, { replace: true });
    void run(trimmed);
  }

  return (
    <PublicShell title={t('portalTrackTitle')} sub={t('portalTrackSub')}>
      <Card title={t('portalTrackFormTitle')} Icon={Search}>
        <form onSubmit={lookup} style={{ display: 'flex', gap: 8 }}>
          <input
            value={code}
            onChange={e => setCode(e.target.value)}
            placeholder="XX-XXXX-XXXX"
            style={{
              flex: 1, padding: '12px 14px', fontSize: 16, fontFamily: 'var(--font-mono)',
              letterSpacing: '0.05em', textTransform: 'uppercase',
              border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
              background: 'var(--surface-2)', color: 'var(--ink-1)',
            }}
          />
          <Button variant="primary" Icon={Search} disabled={busy} onClick={() => lookup()}>{t('portalTrackLookup')}</Button>
        </form>
      </Card>

      {result === 'error' && (
        <Card>
          <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start', padding: 16 }}>
            <AlertCircle size={20} color="var(--warning-fg)" style={{ flexShrink: 0, marginTop: 2 }} />
            <div style={{ fontSize: 13, color: 'var(--ink-1)', lineHeight: 1.6 }}>
              {lang === 'ar'
                ? 'تعذّر التحقق من حالة البلاغ الآن. هذا لا يعني أن الرقم المرجعي غير صحيح — يُرجى المحاولة لاحقاً.'
                : 'We could not check this case right now. That does not mean the reference code is wrong — please try again shortly.'}
            </div>
          </div>
        </Card>
      )}

      {result === 'not-found' && (
        <Card>
          <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start', padding: 16 }}>
            <AlertCircle size={20} color="var(--danger-fg)" style={{ flexShrink: 0, marginTop: 2 }} />
            <div>
              <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--danger-fg)' }}>
                {t('portalTrackNotFoundTitle')}
              </div>
              <div style={{ fontSize: 12, color: 'var(--ink-2)', marginTop: 6, lineHeight: 1.6 }}>
                {t('portalTrackNotFoundBody')}
              </div>
            </div>
          </div>
        </Card>
      )}

      {result && result !== 'not-found' && result !== 'error' && (
        <CaseStatusCard rec={result} t={t} lang={lang} />
      )}
    </PublicShell>
  );
}

function CaseStatusCard({ rec, t, lang }: { rec: IntakePublicStatus; t: (k: string) => string; lang: 'en' | 'ar' }) {
  const catalog = INTAKE_CATALOG[rec.kind];
  const slaDays = rec.slaDueAt
    ? Math.round((new Date(rec.slaDueAt).getTime() - Date.now()) / (24 * 60 * 60 * 1000))
    : null;
  const overdue = slaDays !== null && slaDays < 0 && rec.status !== 'PROMOTED' && rec.status !== 'CLOSED';

  return (
    <Card title={(lang === 'ar' ? catalog.titleAr : catalog.titleEn)}
      action={<Badge tone={INTAKE_STATUS_TONE[rec.status]} dot>{INTAKE_STATUS_LABEL[rec.status][lang]}</Badge>}>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 }}>
        <Stat label={t('portalTrackRefCode')} value={rec.referenceCode} mono />
        <Stat label={t('portalTrackSubmittedAt')} value={rec.acknowledgedAt.slice(0, 10)} mono />
        <Stat label={t('portalTrackSlaDue')}
          value={rec.slaDueAt?.slice(0, 10) ?? '—'}
          extra={overdue
            ? <Badge tone="danger">{t('capaOverdue')}</Badge>
            : slaDays !== null
              ? <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{slaDays}d {t('capaRemaining')}</span>
              : null} />
        <Stat label={t('portalTrackReference')} value={catalog.reference} />
      </div>

      {/* F2-172 — no timeline here, deliberately. The server's public projection carries no
          event history (SEC-FIN-001), and the list this replaced was rendered from the
          reporter's own browser store: it could only ever show events the same browser had
          written, and it looked identical to a real audit trail. Status + SLA is what a
          reference code authorises. */}
      <div style={{
        fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.6, padding: '10px 12px',
        background: 'var(--surface-2)', border: '1px dashed var(--border)',
        borderRadius: 'var(--r-sm)', marginBottom: 4,
      }}>
        {lang === 'ar'
          ? 'لأسباب تتعلق بالخصوصية، يُظهر هذا التتبّع العام الحالة والموعد النظامي فقط. للحصول على تفاصيل إضافية، استخدم قناة الاتصال المذكورة في إشعار الاستلام.'
          : 'For privacy reasons this public tracker shows the status and the regulatory deadline only. For anything further, use the contact route in your acknowledgement.'}
      </div>

      {rec.status === 'PROMOTED' && (
        <div style={{
          background: 'var(--success-bg)', border: '1px solid var(--success-fg)',
          padding: 12, borderRadius: 'var(--r-sm)', marginTop: 16,
          display: 'flex', gap: 10, alignItems: 'flex-start',
        }}>
          <CheckCircle2 size={18} color="var(--success-fg)" style={{ flexShrink: 0, marginTop: 1 }} />
          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--success-fg)' }}>
              {t('portalTrackPromotedTitle')}
            </div>
            <div style={{ fontSize: 12, color: 'var(--ink-2)', marginTop: 4 }}>
              {t('portalTrackPromotedBody')}
            </div>
          </div>
        </div>
      )}
      {rec.status === 'TRIAGING' && (
        <div style={{
          background: 'var(--warning-bg)', border: '1px solid var(--warning-fg)',
          padding: 12, borderRadius: 'var(--r-sm)', marginTop: 16,
          display: 'flex', gap: 10, alignItems: 'flex-start',
        }}>
          <Clock size={18} color="var(--warning-fg)" style={{ flexShrink: 0, marginTop: 1 }} />
          <div style={{ fontSize: 12, color: 'var(--ink-2)' }}>
            {t('portalTrackTriagingBody')}
          </div>
        </div>
      )}
    </Card>
  );
}

function Stat({ label, value, mono, extra }: { label: string; value: string; mono?: boolean; extra?: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
        {label}
      </div>
      <div style={{
        fontSize: 13, color: 'var(--ink-1)', fontWeight: 600, marginTop: 4,
        fontFamily: mono ? 'var(--font-mono)' : undefined,
      }}>{value}</div>
      {extra && <div style={{ marginTop: 4 }}>{extra}</div>}
    </div>
  );
}

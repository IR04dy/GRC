import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, AlertCircle, CheckCircle2, Clock } from 'lucide-react';
import { PublicShell } from '../../components/layout/PublicShell';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import {
  useIntake, INTAKE_CATALOG, INTAKE_STATUS_TONE, INTAKE_STATUS_LABEL,
  type IntakeRecord,
} from '../../lib/intake';

/**
 * Case-tracker — DD-55b.
 *
 * Anonymous reporter pastes their reference code (e.g. "DS-K7L9-Z4M2"
 * or "SU-X9P3-T2W7") and sees the current status, latest update from
 * the triager, and SLA countdown. No account needed.
 *
 * Modelled on:
 *  - OneTrust DSR self-service tracker
 *  - NAVEX EthicsPoint case-status portal
 *
 * Privacy guardrails:
 *  - We never reveal anything beyond status + public events.
 *  - For anonymous SpeakUp cases the triager's name isn't disclosed —
 *    only their role.
 */
export function CaseTrackerPage() {
  const { t, lang } = useI18n();
  const { findByReferenceCode } = useIntake();
  const [searchParams, setSearchParams] = useSearchParams();
  const [code, setCode] = useState(searchParams.get('code') ?? '');
  const [result, setResult] = useState<IntakeRecord | null | 'not-found'>(null);

  useEffect(() => {
    const initial = searchParams.get('code');
    if (initial) {
      setCode(initial);
      const found = findByReferenceCode(initial);
      setResult(found ?? 'not-found');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function lookup(e?: React.FormEvent) {
    e?.preventDefault();
    const trimmed = code.trim().toUpperCase();
    if (!trimmed) return;
    setSearchParams({ code: trimmed }, { replace: true });
    const found = findByReferenceCode(trimmed);
    setResult(found ?? 'not-found');
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
          <Button variant="primary" Icon={Search} onClick={() => lookup()}>{t('portalTrackLookup')}</Button>
        </form>
      </Card>

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

      {result && result !== 'not-found' && (
        <CaseStatusCard rec={result} t={t} lang={lang} />
      )}
    </PublicShell>
  );
}

function CaseStatusCard({ rec, t, lang }: { rec: IntakeRecord; t: (k: string) => string; lang: 'en' | 'ar' }) {
  const catalog = INTAKE_CATALOG[rec.kind];
  const slaDays = rec.slaDueAt
    ? Math.round((new Date(rec.slaDueAt).getTime() - Date.now()) / (24 * 60 * 60 * 1000))
    : null;
  const overdue = slaDays !== null && slaDays < 0 && rec.status !== 'PROMOTED' && rec.status !== 'CLOSED';
  // For anonymous cases hide the actor name — show role only.
  const showActor = (actor: string) => rec.submitterIsAnonymous ? actor.replace(/@.+/, '@***') : actor;

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

      <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>
        {t('portalTrackHistory')}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {rec.history.map((ev, i) => (
          <div key={i} style={{
            display: 'grid', gridTemplateColumns: '120px 130px 1fr', gap: 12,
            padding: '8px 0', borderBottom: i < rec.history.length - 1 ? '1px dashed var(--border)' : 'none',
            alignItems: 'flex-start',
          }}>
            <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
              {ev.at.slice(0, 16).replace('T', ' ')}
            </span>
            <Badge tone={INTAKE_STATUS_TONE[ev.status]}>
              {INTAKE_STATUS_LABEL[ev.status][lang]}
            </Badge>
            <div>
              {ev.note && <div style={{ fontSize: 13, color: 'var(--ink-1)', lineHeight: 1.5 }}>{ev.note}</div>}
              <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>{showActor(ev.actor)}</div>
            </div>
          </div>
        ))}
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

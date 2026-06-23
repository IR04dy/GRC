import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Inbox, ListChecks, AlertTriangle, Send,
  CheckCheck, X as XIcon, Lock,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  useIntake, INTAKE_CATALOG, INTAKE_STATUS_TONE, INTAKE_STATUS_LABEL,
  hoursUntilSla,
  type IntakeRecord, type IntakeKind,
} from '../../lib/intake';
import { computeChangeRisk } from '../../lib/changeRisk';
import { ChangeRiskPanel } from '../../components/intake/ChangeRiskPanel';

/**
 * Triager inbox — DD-58.
 *
 * Single workspace where every triager (DPO, CSIRT, Risk Officer, Audit
 * Lead, Ethics Cte, Policy Officer, TPRM Lead) sees the intake records
 * routed to them, organised by SLA urgency.
 *
 * Architecture: industry-standard GRC tooling "Integrated Risk Center" — every actor
 * sees their queue, from any kind, in one place.
 */
type TabId = 'open' | 'overdue' | 'promoted' | 'closed' | 'all';

const KIND_TONE: Record<string, 'info' | 'warning' | 'danger' | 'accent' | 'primary' | 'neutral' | 'success'> = {
  DSR_REQUEST: 'info', BREACH_REPORT: 'danger', VRP_DISCLOSURE: 'warning',
  SPEAKUP: 'danger', SECURITY_INCIDENT: 'warning', PRIVACY_BREACH: 'danger',
  RISK_ISSUE: 'accent', CONTROL_OBSERVATION: 'accent', POLICY_EXCEPTION: 'warning',
  VENDOR_ONBOARDING: 'primary', DPIA_INITIATION: 'info',
  BIA_SUBMISSION: 'info', AUDIT_REQUEST: 'info', CHANGE_REQUEST: 'primary',
  ETHICS_DISCLOSURE: 'warning', CONTROL_ATTESTATION: 'success',
};

export function IntakeInboxPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const { items, triage } = useIntake();
  const [tab, setTab] = useState<TabId>('open');
  const [filterKind, setFilterKind] = useState<IntakeKind | 'ALL'>('ALL');
  const [triageTarget, setTriageTarget] = useState<IntakeRecord | null>(null);

  // Bucketing
  const open = items.filter(r => r.status === 'SUBMITTED' || r.status === 'TRIAGING');
  const overdue = open.filter(r => hoursUntilSla(r) < 0);
  const promoted = items.filter(r => r.status === 'PROMOTED');
  const closed = items.filter(r => r.status === 'REJECTED' || r.status === 'CLOSED');

  const visible = useMemo(() => {
    let pool: IntakeRecord[];
    switch (tab) {
      case 'open':     pool = open; break;
      case 'overdue':  pool = overdue; break;
      case 'promoted': pool = promoted; break;
      case 'closed':   pool = closed; break;
      default:         pool = items;
    }
    if (filterKind !== 'ALL') pool = pool.filter(r => r.kind === filterKind);
    // Sort by SLA urgency for open/overdue, by recency otherwise
    if (tab === 'open' || tab === 'overdue') {
      pool = [...pool].sort((a, b) => hoursUntilSla(a) - hoursUntilSla(b));
    } else {
      pool = [...pool].sort((a, b) => b.acknowledgedAt.localeCompare(a.acknowledgedAt));
    }
    return pool;
  }, [tab, filterKind, items, open, overdue, promoted, closed]);

  const tabs: TabItem<TabId>[] = [
    { id: 'open',     label: `${t('inboxTabOpen')}${open.length ? ' · ' + open.length : ''}` },
    { id: 'overdue',  label: `${t('inboxTabOverdue')}${overdue.length ? ' · ' + overdue.length : ''}` },
    { id: 'promoted', label: `${t('inboxTabPromoted')}${promoted.length ? ' · ' + promoted.length : ''}` },
    { id: 'closed',   label: `${t('inboxTabClosed')}${closed.length ? ' · ' + closed.length : ''}` },
    { id: 'all',      label: `${t('inboxTabAll')}${items.length ? ' · ' + items.length : ''}` },
  ];

  return (
    <Shell title={t('inboxTitle')} sub={t('inboxSub')}>
      <PageHeader
        eyebrow={t('inboxEyebrow')}
        title={t('inboxTitle')}
        sub={t('inboxPageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Inbox}         label={t('inboxKpiOpen')}     value={open.length}     tone="primary" onClick={() => setTab('open')}     active={tab === 'open'} />
        <Kpi Icon={AlertTriangle} label={t('inboxKpiOverdue')}  value={overdue.length}  tone="danger"  onClick={() => setTab('overdue')}  active={tab === 'overdue'} />
        <Kpi Icon={CheckCheck}    label={t('inboxKpiPromoted')} value={promoted.length} tone="success" onClick={() => setTab('promoted')} active={tab === 'promoted'} />
        <Kpi Icon={ListChecks}    label={t('inboxKpiTotal')}    value={items.length}    tone="info"    onClick={() => setTab('all')}      active={tab === 'all'} />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      <Card title={tabs.find(x => x.id === tab)?.label}
        action={<div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <select value={filterKind} onChange={e => setFilterKind(e.target.value as IntakeKind | 'ALL')}
            style={{
              padding: '6px 8px', fontSize: 12,
              border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
              background: 'var(--surface-2)', color: 'var(--ink-1)',
            }}>
            <option value="ALL">{t('inboxFilterAllKinds')}</option>
            {Object.values(INTAKE_CATALOG).map(c => (
              <option key={c.kind} value={c.kind}>{lang === 'ar' ? c.titleAr : c.titleEn}</option>
            ))}
          </select>
          <Badge tone="primary">{visible.length}</Badge>
        </div>}>
        {visible.length === 0
          ? <EmptyState Icon={Inbox} title={t('inboxEmpty')} sub={t('inboxEmptyBody')} />
          : <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {visible.map(r => <InboxRow key={r.id} rec={r} onTriage={() => setTriageTarget(r)} t={t} lang={lang} />)}
            </div>
        }
      </Card>

      {triageTarget && (
        <TriageModal
          rec={triageTarget}
          onClose={() => setTriageTarget(null)}
          onDecide={(decision, extra) => {
            const result = triage(triageTarget.id, decision, 'demo.triager@tss.sa', extra);
            if (decision === 'PROMOTE' && result) {
              toast.success(`${t('inboxToastPromoted')} → ${result.entityType} #${result.recordId}`);
            } else {
              toast.success(t(decision === 'REJECT' ? 'inboxToastRejected' : decision === 'CLOSE' ? 'inboxToastClosed' : 'inboxToastPromoted'));
            }
            setTriageTarget(null);
          }}
        />
      )}
    </Shell>
  );
}

function InboxRow({ rec, onTriage, t, lang }: {
  rec: IntakeRecord;
  onTriage: () => void;
  t: (k: string) => string;
  lang: 'en' | 'ar';
}) {
  const catalog = INTAKE_CATALOG[rec.kind];
  const slaHours = hoursUntilSla(rec);
  const overdue = slaHours < 0 && (rec.status === 'SUBMITTED' || rec.status === 'TRIAGING');
  const slaDays = Math.round(slaHours / 24);

  // Anonymity-aware identity column
  const identity = rec.submitterIsAnonymous
    ? <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--ink-3)' }}>
        <Lock size={12} /> {t('inboxAnonymous')}
      </div>
    : <div>
        <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-2)' }}>{rec.submitterName ?? '—'}</div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{rec.submitterEmail ?? rec.submitterPhone ?? '—'}</div>
      </div>;

  // Pull a one-line "headline" from the payload
  const headline = (() => {
    const p = rec.payload as Record<string, unknown>;
    if (rec.kind === 'DSR_REQUEST') return `${p.requestType} — ${p.scope}`;
    if (rec.kind === 'SPEAKUP') return `${p.category} — ${p.summary}`;
    if (rec.kind === 'SECURITY_INCIDENT') return `${p.category} — ${p.summary}`;
    if (rec.kind === 'POLICY_EXCEPTION') return `${p.policyTitle ?? p.policyCode} — ${p.exceptionScope}`;
    if (rec.kind === 'VRP_DISCLOSURE') return `${p.category} on ${p.affectedUrl ?? '—'}`;
    return JSON.stringify(p).slice(0, 100);
  })();

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '110px 150px 1fr 110px 130px 130px 120px',
      gap: 10,
      padding: '12px 14px',
      background: 'var(--surface-2)',
      border: '1px solid var(--border)',
      borderRadius: 'var(--r-md)',
      alignItems: 'center',
    }}>
      <span className="mono" style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-2)' }}>{rec.referenceCode}</span>
      <Badge tone={KIND_TONE[rec.kind] ?? 'neutral'}>{lang === 'ar' ? catalog.titleAr : catalog.titleEn}</Badge>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 13, color: 'var(--ink-1)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {String(headline)}
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
          {catalog.reference}
        </div>
      </div>
      <Badge tone={INTAKE_STATUS_TONE[rec.status]} dot>
        {INTAKE_STATUS_LABEL[rec.status][lang]}
      </Badge>
      <div>
        <div className="mono" style={{ fontSize: 12, color: overdue ? 'var(--danger-fg)' : 'var(--ink-2)', fontWeight: overdue ? 700 : 500 }}>
          {rec.slaDueAt?.slice(0, 10) ?? '—'}
        </div>
        <div style={{ fontSize: 11, color: overdue ? 'var(--danger-fg)' : 'var(--ink-3)' }}>
          {overdue ? `${Math.abs(slaDays)}d ${t('capaOverdue')}` : `${slaDays}d ${t('capaRemaining')}`}
        </div>
      </div>
      {identity}
      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        {(rec.status === 'SUBMITTED' || rec.status === 'TRIAGING') && (
          <Button variant="primary" size="sm" Icon={Send} onClick={onTriage}>{t('inboxTriage')}</Button>
        )}
        {rec.status === 'PROMOTED' && rec.promotedTo && (
          <PromotedDeeplink target={rec.promotedTo} />
        )}
      </div>
    </div>
  );
}

function PromotedDeeplink({ target }: { target: NonNullable<IntakeRecord['promotedTo']> }) {
  const { t } = useI18n();
  // Map RegisterTarget → user-facing route
  const route: Partial<Record<typeof target.entityType, string>> = {
    INCIDENT:    '/incidents',
    DSR:         '/privacy?tab=dsr',
    BREACH:      '/privacy?tab=breaches',
    RISK:        '/risk',
    VENDOR:      '/tprm',
    CAPA:        '/capa',
    DPIA:        '/privacy?tab=dpia',
    BIA:         '/bcm?tab=bia',
    ENGAGEMENT:  '/audit-workspace',
    CHANGE:      '/changes',
    EXCEPTION:   '/compliance',
    POLICY:      '/compliance?tab=policies',
    FINDING:     '/compliance?tab=findings',
    ETHICS_CASE: '/ethics',
    CONTROL_TEST: '/compliance?tab=controls',
  };
  const href = route[target.entityType];
  if (!href) return <Badge tone="success">#{target.recordId}</Badge>;
  return (
    <Link to={href}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 4,
        padding: '4px 10px', fontSize: 12, fontWeight: 600,
        color: 'var(--success-fg)', background: 'var(--success-bg)',
        borderRadius: 'var(--r-sm)', textDecoration: 'none',
      }}
      title={t('inboxPromotedDeeplinkTitle')}>
      #{target.recordId} →
    </Link>
  );
}

function TriageModal({ rec, onClose, onDecide }: {
  rec: IntakeRecord;
  onClose: () => void;
  onDecide: (decision: 'PROMOTE' | 'REJECT' | 'CLOSE', extra?: Partial<IntakeRecord>) => void;
}) {
  const { t, lang } = useI18n();
  const [reason, setReason] = useState('');
  const catalog = INTAKE_CATALOG[rec.kind];

  // DD-72: when triaging a CHANGE_REQUEST, surface the same auto-computed
  // risk score / classification / CAB gate the submitter saw on the ack
  // screen — keeps the audit narrative consistent end-to-end.
  const changeRisk = rec.kind === 'CHANGE_REQUEST'
    ? computeChangeRisk(rec.payload as Record<string, unknown>)
    : null;

  return (
    <Modal open onClose={onClose} size="lg" title={`${rec.referenceCode} — ${lang === 'ar' ? catalog.titleAr : catalog.titleEn}`}
      footer={<>
        <div style={{ flex: 1 }} />
        <Button variant="ghost" onClick={onClose}>{t('actionCancel')}</Button>
        <Button variant="ghost" Icon={XIcon} onClick={() => onDecide('REJECT', { rejectionReason: reason })}>{t('inboxReject')}</Button>
        <Button variant="primary" Icon={CheckCheck} onClick={() => onDecide('PROMOTE', { rejectionReason: reason })}>{t('inboxPromote')}</Button>
      </>}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ background: 'var(--surface-2)', padding: 12, borderRadius: 'var(--r-sm)', fontSize: 13 }}>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{t('inboxTriageReference')}</div>
          <div style={{ marginTop: 4 }}>{catalog.reference}</div>
        </div>

        {changeRisk && <ChangeRiskPanel risk={changeRisk} t={t} lang={lang} />}

        <div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }}>{t('inboxTriagePayload')}</div>
          <pre style={{
            margin: 0, padding: 12, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
            fontSize: 12, color: 'var(--ink-1)', whiteSpace: 'pre-wrap', wordBreak: 'break-word',
            fontFamily: 'var(--font-mono)', maxHeight: 220, overflow: 'auto',
          }}>{JSON.stringify(rec.payload, null, 2)}</pre>
        </div>

        <div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }}>{t('inboxTriageNote')}</div>
          <textarea value={reason} onChange={e => setReason(e.target.value)}
            placeholder={t('inboxTriageNotePh')}
            rows={3}
            style={{
              width: '100%', padding: '8px 10px', fontSize: 13,
              border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
              background: 'var(--surface-2)', color: 'var(--ink-1)', resize: 'vertical',
            }} />
        </div>

        <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
          {t('inboxTriageFootnote')}
        </div>
      </div>
    </Modal>
  );
}

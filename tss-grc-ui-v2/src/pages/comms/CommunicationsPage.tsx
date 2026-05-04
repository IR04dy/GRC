import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  Send, Inbox, CheckCheck, AlertOctagon, Users, Megaphone,
  Mail, Phone, ExternalLink, Building2, MessageSquare, FileSignature,
  CalendarClock, Landmark,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Kpi } from '../../components/ui/Kpi';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import {
  STAKEHOLDERS, PROCESS_RACI, STATUS_TONE_RACI,
  type Stakeholder, type CommChannel,
} from '../../lib/stakeholders';
import {
  NOTIFICATION_MATRIX, COMMS_LOG_BY_RECORD, formatDeadline,
  type CommState, type CommLogEntry, type NotificationSpec,
} from '../../lib/notificationMatrix';
import { generatePeriodicPending, findPeriodicFiling } from '../../lib/notificationScheduler';
import { useCommsMutations } from '../../lib/useCommsMutations';
import { AcknowledgeModal } from '../../components/comms/AcknowledgeModal';
import { ComposeModal } from '../../components/comms/ComposeModal';
import { NewCommunicationModal } from '../../components/comms/NewCommunicationModal';
import {
  useCommunications, FORWARD_TRANSITIONS, STATUS_TONE_COMM, STATUS_LABEL_COMM,
  type Communication, type CommStatus,
} from '../../lib/communications';
import { Plus, FileText, ArrowRight } from 'lucide-react';

type TabId = 'pending' | 'sent' | 'lifecycle' | 'matrix' | 'directory' | 'raci';

const CHANNEL_ICONS: Record<CommChannel, LucideIcon> = {
  PORTAL:            ExternalLink,
  EMAIL:             Mail,
  PHONE:             Phone,
  SECURE_MSG:        FileSignature,
  FORMAL_LETTER:     FileSignature,
  IN_PERSON:         Building2,
  TEAMS_SLACK:       MessageSquare,
  PUBLIC_DISCLOSURE: Megaphone,
};

const STATE_BADGE: Record<CommState, { tone: BadgeTone; en: string; ar: string }> = {
  PENDING:      { tone: 'warning', en: 'Pending',     ar: 'بانتظار' },
  IN_FLIGHT:    { tone: 'info',    en: 'In flight',   ar: 'قيد الإرسال' },
  SENT:         { tone: 'success', en: 'Sent',        ar: 'أُرسل' },
  ACKNOWLEDGED: { tone: 'success', en: 'Acknowledged',ar: 'تم التأكيد' },
  OVERDUE:      { tone: 'danger',  en: 'Overdue',     ar: 'متأخر' },
};

interface PendingRow {
  recordKey: string;
  spec: NotificationSpec;
  logEntry: CommLogEntry;
  /** Hours remaining (or negative if overdue). */
  remainingHours: number;
}

interface SentRow {
  recordKey: string;
  spec: NotificationSpec;
  logEntry: CommLogEntry;
}

/**
 * /communications — cross-record stakeholder + notification workspace.
 *
 * Five lanes:
 *   1. Pending notifications — every active SLA across the platform,
 *      sorted by urgency
 *   2. Sent log — chain of custody trail of comms already dispatched
 *   3. Notification matrix — the canonical "if X event then notify Y
 *      within Z hours" lookup, browseable by event kind
 *   4. Stakeholder directory — every internal + external stakeholder
 *      with their primary channel
 *   5. RACI register — every workflow stage with its canonical R/A/C/I
 *      assignments and standards anchor
 *
 * ServiceNow IRM "Notification Engine" + "Stakeholder Management" home.
 */
export function CommunicationsPage() {
  const { t, lang } = useI18n();
  const [tab, setTab] = useState<TabId>('pending');
  const nowMs = Date.now();
  const muts = useCommsMutations();

  // Modal state for "Mark sent" / acknowledge flow.
  const [ackTarget, setAckTarget] = useState<{ recordKey: string; entry: CommLogEntry; spec: NotificationSpec } | null>(null);
  // Modal state for "Compose" flow.
  const [composeTarget, setComposeTarget] = useState<{ recordKey: string; entry: CommLogEntry; spec: NotificationSpec; recordTitle?: string } | null>(null);
  // Modal state for "New Communication" — entry point for ad-hoc + lifecycle-managed comms.
  const [newCommOpen, setNewCommOpen] = useState(false);
  const [newCommSource, setNewCommSource] = useState<string | undefined>(undefined);
  const [searchParams, setSearchParams] = useSearchParams();

  // Auto-open the modal when arriving via the Topbar "New Comm" entry
  // (?new=1[&source=KIND:ID]). Pre-fills the source-record key when given.
  useEffect(() => {
    if (searchParams.get('new') === '1') {
      setNewCommSource(searchParams.get('source') ?? undefined);
      setNewCommOpen(true);
      // Clean up so refreshing doesn't re-open the modal.
      const next = new URLSearchParams(searchParams);
      next.delete('new'); next.delete('source');
      setSearchParams(next, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  // Communication-record store (DD-32 — first-class entity with lifecycle).
  const comms = useCommunications();

  // Flatten all comm-log entries with their record keys + spec lookup.
  // Includes both the static seed (event-driven) and the scheduler output
  // (calendar-driven periodic filings). Mutations are applied on top so
  // an auditor's "mark sent" survives a reload.
  const allEntries = useMemo(() => {
    const out: Array<{ recordKey: string; logEntry: CommLogEntry; spec: NotificationSpec }> = [];
    // Seed: event-driven entries
    for (const [recordKey, entries] of Object.entries(COMMS_LOG_BY_RECORD)) {
      for (const logEntry of entries) {
        const spec = NOTIFICATION_MATRIX.find(s => s.id === logEntry.specId);
        if (spec) out.push({ recordKey, logEntry: muts.apply(recordKey, logEntry), spec });
      }
    }
    // Scheduler: calendar-driven periodic filings
    for (const { recordKey, entry } of generatePeriodicPending()) {
      const spec = NOTIFICATION_MATRIX.find(s => s.id === entry.specId);
      if (spec) out.push({ recordKey, logEntry: muts.apply(recordKey, entry), spec });
    }
    return out;
  }, [muts]);

  const pending = useMemo<PendingRow[]>(() => {
    return allEntries
      .filter(e => e.logEntry.state === 'PENDING' || e.logEntry.state === 'IN_FLIGHT' || e.logEntry.state === 'OVERDUE')
      .map(e => {
        const detectedMs = new Date(e.logEntry.eventDetectedAt).getTime();
        const deadlineMs = detectedMs + e.spec.withinHours * 3600_000;
        const remainingHours = Math.round((deadlineMs - nowMs) / 3600_000);
        // Auto-flip PENDING → OVERDUE when deadline has passed.
        const effective: CommLogEntry = (e.logEntry.state === 'PENDING' && remainingHours < 0)
          ? { ...e.logEntry, state: 'OVERDUE' }
          : e.logEntry;
        return { recordKey: e.recordKey, spec: e.spec, logEntry: effective, remainingHours };
      })
      .sort((a, b) => a.remainingHours - b.remainingHours);
  }, [allEntries, nowMs]);

  const sent = useMemo<SentRow[]>(() => {
    return allEntries
      .filter(e => e.logEntry.state === 'SENT' || e.logEntry.state === 'ACKNOWLEDGED')
      .sort((a, b) => (b.logEntry.sentAt ?? '').localeCompare(a.logEntry.sentAt ?? ''))
      .map(e => ({ recordKey: e.recordKey, spec: e.spec, logEntry: e.logEntry }));
  }, [allEntries]);

  const overdueCount = pending.filter(p => p.remainingHours < 0).length;
  const dueSoon24h   = pending.filter(p => p.remainingHours >= 0 && p.remainingHours <= 24).length;
  const sentLast7d   = sent.filter(s => {
    if (!s.logEntry.sentAt) return false;
    const ageDays = (nowMs - new Date(s.logEntry.sentAt).getTime()) / 86400_000;
    return ageDays <= 7;
  }).length;

  const lifecycleCount = comms.items.filter(c => c.status !== 'CLOSED' && c.status !== 'RETRACTED').length;

  const tabs: TabItem<TabId>[] = [
    { id: 'pending',   label: `${t('commsTabPending')} (${pending.length})` },
    { id: 'sent',      label: `${t('commsTabSent')} (${sent.length})` },
    { id: 'lifecycle', label: `${t('commsTabLifecycle')} (${comms.items.length})${lifecycleCount > 0 ? ' • ' + lifecycleCount : ''}` },
    { id: 'matrix',    label: t('commsTabMatrix') },
    { id: 'directory', label: t('commsTabDirectory') },
    { id: 'raci',      label: t('commsTabRaci') },
  ];

  return (
    <Shell title={t('modCommunications')} sub={t('commsSub')}>
      <PageHeader
        eyebrow={t('commsEyebrow')}
        title={t('commsTitle')}
        sub={t('commsHeaderSub')}
        actions={
          <Button variant="primary" Icon={Plus} onClick={() => setNewCommOpen(true)}>
            {t('newCommCta')}
          </Button>
        }
      />

      <div className="kpi-grid">
        <Kpi Icon={Inbox}         label={t('commsKpiPending')}    value={pending.length}        tone="warning" />
        <Kpi Icon={AlertOctagon}  label={t('commsKpiOverdue')}    value={overdueCount}          tone="danger" />
        <Kpi Icon={CalendarClock} label={t('commsKpiDueSoon')}    value={dueSoon24h}            tone="accent" />
        <Kpi Icon={CheckCheck}    label={t('commsKpiSent7d')}     value={sentLast7d}            tone="success" />
        <Kpi Icon={Users}         label={t('commsKpiStakeholders')} value={STAKEHOLDERS.length} tone="primary" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'pending' && (
        <Card title={t('commsTabPending')} Icon={Inbox}
          action={<Badge tone={overdueCount > 0 ? 'danger' : 'warning'}>{pending.length}</Badge>}>
          {pending.length === 0
            ? <EmptyState Icon={CheckCheck} title={t('commsPendingEmpty')} sub={t('commsPendingEmptySub')} />
            : <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {pending.map(p => {
                  const periodic = findPeriodicFiling(p.recordKey);
                  const recordTitle = periodic ? (lang === 'ar' ? periodic.titleAr : periodic.titleEn) : undefined;
                  return (
                    <PendingRowCmp key={`${p.recordKey}-${p.spec.id}-${p.logEntry.id}`}
                      row={p} lang={lang} t={t}
                      onCompose={() => setComposeTarget({ recordKey: p.recordKey, entry: p.logEntry, spec: p.spec, recordTitle })}
                      onMarkSent={() => setAckTarget({ recordKey: p.recordKey, entry: p.logEntry, spec: p.spec })} />
                  );
                })}
              </div>}
        </Card>
      )}

      {tab === 'sent' && (
        <Card title={t('commsTabSent')} Icon={CheckCheck} action={<Badge tone="success">{sent.length}</Badge>}>
          {sent.length === 0
            ? <EmptyState Icon={CheckCheck} title={t('commsSentEmpty')} />
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {sent.map(s => <SentRowCmp key={`${s.recordKey}-${s.logEntry.id}`} row={s} lang={lang} />)}
              </div>}
        </Card>
      )}

      {tab === 'lifecycle' && (
        <LifecycleView
          comms={comms.items}
          onAdvance={(id, to) => comms.advance(id, to, 'GRC Operator')}
          onRemove={comms.remove}
          onNewClick={() => setNewCommOpen(true)}
          lang={lang} t={t} />
      )}

      {tab === 'matrix' && <NotificationMatrixView lang={lang} t={t} />}
      {tab === 'directory' && <StakeholderDirectory lang={lang} t={t} />}
      {tab === 'raci' && <RaciRegisterView lang={lang} />}

      <AcknowledgeModal
        open={!!ackTarget}
        spec={ackTarget?.spec ?? null}
        recordKey={ackTarget?.recordKey ?? null}
        existing={ackTarget?.entry}
        onClose={() => setAckTarget(null)}
        onAcknowledge={(update) => {
          if (!ackTarget) return;
          muts.upsert({
            recordKey: ackTarget.recordKey,
            entryId: ackTarget.entry.id,
            state: update.state,
            receiptRef: update.receiptRef || undefined,
            noteEn: update.note || ackTarget.entry.noteEn,
            sentAt: new Date().toISOString(),
          });
          setAckTarget(null);
        }} />

      <ComposeModal
        open={!!composeTarget}
        spec={composeTarget?.spec ?? null}
        recordKey={composeTarget?.recordKey ?? null}
        entry={composeTarget?.entry ?? null}
        recordTitle={composeTarget?.recordTitle}
        onClose={() => setComposeTarget(null)}
        onSend={(update) => {
          if (!composeTarget) return;
          muts.upsert({
            recordKey: composeTarget.recordKey,
            entryId: composeTarget.entry.id,
            state: update.state,
            receiptRef: composeTarget.entry.receiptRef,
            noteEn: `${update.subject}\n\n${update.body}`,
            sentAt: new Date().toISOString(),
          });
          setComposeTarget(null);
        }} />

      <NewCommunicationModal
        open={newCommOpen}
        presetSourceRecordKey={newCommSource}
        onClose={() => { setNewCommOpen(false); setNewCommSource(undefined); }}
        onCreate={(init) => comms.create(init)} />
    </Shell>
  );
}

// ── Lifecycle view (Communication records — DD-32) ─────────────────────────
function LifecycleView({
  comms, onAdvance, onRemove, onNewClick, lang, t,
}: {
  comms: Communication[];
  onAdvance: (id: string, to: CommStatus) => void;
  onRemove: (id: string) => void;
  onNewClick: () => void;
  lang: 'en' | 'ar';
  t: (k: string) => string;
}) {
  if (comms.length === 0) {
    return (
      <Card title={t('commsTabLifecycle')} Icon={FileText}
        action={<Button variant="ghost" Icon={Plus} size="sm" onClick={onNewClick}>{t('newCommCta')}</Button>}>
        <EmptyState Icon={FileText}
          title={t('commsLifecycleEmpty')}
          sub={t('commsLifecycleEmptySub')} />
      </Card>
    );
  }

  // Group by status (Drafts / In review / Approved / Sent / Acknowledged / Closed / Retracted)
  const order: CommStatus[] = ['DRAFT', 'IN_REVIEW', 'APPROVED', 'SENT', 'ACKNOWLEDGED', 'CLOSED', 'RETRACTED'];
  const grouped = new Map<CommStatus, Communication[]>();
  for (const status of order) grouped.set(status, []);
  for (const c of comms) grouped.get(c.status)?.push(c);

  return (
    <Card title={t('commsTabLifecycle')} Icon={FileText}
      action={<Button variant="ghost" Icon={Plus} size="sm" onClick={onNewClick}>{t('newCommCta')}</Button>}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {order.filter(s => (grouped.get(s) ?? []).length > 0).map(status => {
          const list = grouped.get(status) ?? [];
          return (
            <div key={status}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                <Badge tone={STATUS_TONE_COMM[status]} dot>{lang === 'ar' ? STATUS_LABEL_COMM[status].ar : STATUS_LABEL_COMM[status].en}</Badge>
                <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{list.length}</span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {list.map(c => <CommRow key={c.id} c={c} onAdvance={onAdvance} onRemove={onRemove} lang={lang} t={t} />)}
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

function CommRow({ c, onAdvance, onRemove, lang, t }: {
  c: Communication;
  onAdvance: (id: string, to: CommStatus) => void;
  onRemove: (id: string) => void;
  lang: 'en' | 'ar';
  t: (k: string) => string;
}) {
  const recipient = STAKEHOLDERS.find(s => s.code === c.recipientCode);
  const Icon = CHANNEL_ICONS[c.channel];
  const transitions = FORWARD_TRANSITIONS[c.status] ?? [];
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '32px 1fr auto',
      gap: 10, alignItems: 'flex-start',
      padding: '10px 12px',
      borderRadius: 'var(--r-sm)',
      border: '1px solid var(--border)',
      background: 'var(--surface-1)',
    }}>
      <div style={{
        width: 30, height: 30, borderRadius: 'var(--r-sm)',
        background: 'var(--surface-2)',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        color: 'var(--primary-700)',
      }}>
        <Icon size={14} />
      </div>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>{c.subject}</div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
          → {lang === 'ar' ? recipient?.nameAr : recipient?.nameEn}
          {' · '}<span className="mono">{c.channel.replace(/_/g, ' ')}</span>
          {c.sourceRecordKey && <> · <span className="mono">{c.sourceRecordKey}</span></>}
          {c.sourceSpecId && <> · <span className="mono">{c.sourceSpecId}</span></>}
        </div>
        <div style={{ fontSize: 10, color: 'var(--ink-4)', marginTop: 4 }}>
          {t('commsLifecycleCreated')} {c.createdAt.replace('T', ' ').slice(0, 16)} {t('commsLifecycleBy')} {c.createdBy}
          {c.reviewedAt && <> · {t('commsLifecycleReviewed')} {c.reviewedAt.replace('T', ' ').slice(0, 16)}</>}
          {c.approvedAt && <> · {t('commsLifecycleApproved')} {c.approvedAt.replace('T', ' ').slice(0, 16)}</>}
          {c.sentAt && <> · {t('commsLifecycleSent')} {c.sentAt.replace('T', ' ').slice(0, 16)}</>}
          {c.receiptRef && <> · ref <span className="mono">{c.receiptRef}</span></>}
        </div>
      </div>
      <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
        {transitions.map(to => (
          <Button key={to} variant={to === 'RETRACTED' ? 'ghost' : 'secondary'} size="sm"
            Icon={to === 'RETRACTED' ? undefined : ArrowRight}
            onClick={() => onAdvance(c.id, to)}>
            {lang === 'ar' ? STATUS_LABEL_COMM[to].ar : STATUS_LABEL_COMM[to].en}
          </Button>
        ))}
        {(c.status === 'DRAFT' || c.status === 'RETRACTED') && (
          <Button variant="ghost" size="sm" onClick={() => onRemove(c.id)}>
            {t('actionDelete') ?? 'Delete'}
          </Button>
        )}
      </div>
    </div>
  );
}

// ── Pending row ────────────────────────────────────────────────────────────
function PendingRowCmp({
  row, lang, t, onMarkSent, onCompose,
}: {
  row: PendingRow;
  lang: 'en' | 'ar';
  t: (k: string) => string;
  onMarkSent: () => void;
  onCompose: () => void;
}) {
  const stakeholder = STAKEHOLDERS.find(s => s.code === row.spec.toStakeholderCode);
  const Icon = CHANNEL_ICONS[row.spec.channel];
  const overdue = row.remainingHours < 0;
  const slaText = overdue
    ? `${formatDeadline(-row.remainingHours, lang)} ${lang === 'ar' ? 'متأخر' : 'overdue'}`
    : `${formatDeadline(row.remainingHours, lang)} ${lang === 'ar' ? 'متبقٍ' : 'left'}`;
  const periodic = findPeriodicFiling(row.recordKey);

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '32px 1fr auto auto auto auto auto',
      gap: 10, alignItems: 'center',
      padding: '8px 10px',
      borderRadius: 'var(--r-sm)',
      border: '1px solid var(--border)',
      borderInlineStartWidth: 3,
      borderInlineStartColor: overdue ? 'var(--danger)' : row.remainingHours <= 4 ? 'var(--accent-700)' : 'var(--primary)',
      background: 'var(--surface-1)',
    }}>
      <div style={{
        width: 30, height: 30, borderRadius: 'var(--r-sm)',
        background: 'var(--surface-2)',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        color: 'var(--primary-700)',
      }}>
        <Icon size={14} />
      </div>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)',
                      whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {periodic
            ? (lang === 'ar' ? periodic.titleAr : periodic.titleEn)
            : (lang === 'ar' ? row.spec.descriptionAr : row.spec.descriptionEn)}
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
          <span className="mono">{row.recordKey}</span>
          {' · → '}
          {lang === 'ar' ? stakeholder?.nameAr : stakeholder?.nameEn}
          {' · '}
          <span className="mono">{row.spec.regulatoryRef}</span>
          {periodic && <> · <Badge tone="neutral">{t('commsScheduledBadge')}</Badge></>}
        </div>
        {row.logEntry.noteEn && (
          <div style={{ fontSize: 11, color: 'var(--ink-2)', marginTop: 2, fontStyle: 'italic' }}>
            {row.logEntry.noteEn}
          </div>
        )}
      </div>
      <Badge tone="info">{formatDeadline(row.spec.withinHours, lang)}</Badge>
      <span className="mono" style={{
        fontSize: 11, fontWeight: 700,
        color: overdue ? 'var(--danger-fg)' : row.remainingHours <= 4 ? 'var(--accent-700)' : 'var(--ink-2)',
      }}>{slaText}</span>
      <Badge tone={STATE_BADGE[row.logEntry.state].tone} dot>
        {lang === 'ar' ? STATE_BADGE[row.logEntry.state].ar : STATE_BADGE[row.logEntry.state].en}
      </Badge>
      <Button variant="ghost" size="sm" Icon={Send} onClick={onCompose}>
        {t('commsComposeCta')}
      </Button>
      <Button variant="ghost" size="sm" Icon={CheckCheck} onClick={onMarkSent}>
        {t('commsAckCta')}
      </Button>
    </div>
  );
}

// ── Sent row ───────────────────────────────────────────────────────────────
function SentRowCmp({ row, lang }: { row: SentRow; lang: 'en' | 'ar' }) {
  const stakeholder = STAKEHOLDERS.find(s => s.code === row.spec.toStakeholderCode);
  const Icon = CHANNEL_ICONS[row.spec.channel];
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '32px 1fr auto auto',
      gap: 10, alignItems: 'center',
      padding: '8px 10px',
      borderBottom: '1px dashed var(--border)',
    }}>
      <div style={{
        width: 28, height: 28, borderRadius: 'var(--r-sm)',
        background: 'var(--surface-2)',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        color: 'var(--success-fg)',
      }}>
        <Icon size={13} />
      </div>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
          {lang === 'ar' ? row.spec.descriptionAr : row.spec.descriptionEn}
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
          <span className="mono">{row.recordKey}</span>
          {' · '}
          {lang === 'ar' ? stakeholder?.nameAr : stakeholder?.nameEn}
          {row.logEntry.receiptRef && (
            <>
              {' · '}<span className="mono">ref: {row.logEntry.receiptRef}</span>
            </>
          )}
        </div>
      </div>
      <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
        {row.logEntry.sentAt?.replace('T', ' ').slice(0, 16) ?? '—'}
      </span>
      <Badge tone={STATE_BADGE[row.logEntry.state].tone} dot>
        {lang === 'ar' ? STATE_BADGE[row.logEntry.state].ar : STATE_BADGE[row.logEntry.state].en}
      </Badge>
    </div>
  );
}

// ── Notification matrix view (browse by event kind) ───────────────────────
function NotificationMatrixView({ lang, t }: { lang: 'en' | 'ar'; t: (k: string) => string }) {
  const grouped = useMemo(() => {
    const m = new Map<string, NotificationSpec[]>();
    for (const spec of NOTIFICATION_MATRIX) {
      const list = m.get(spec.eventKind) ?? [];
      list.push(spec);
      m.set(spec.eventKind, list);
    }
    return m;
  }, []);

  return (
    <Card title={t('commsTabMatrix')} Icon={Send}
      action={<Badge tone="primary">{NOTIFICATION_MATRIX.length}</Badge>}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {Array.from(grouped.entries()).map(([eventKind, specs]) => (
          <div key={eventKind}>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', fontWeight: 700,
                          textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4 }}>
              {eventKind.replace(/_/g, ' ')}
              <Badge tone="neutral">{specs.length}</Badge>
            </div>
            {specs.map(spec => {
              const stakeholder = STAKEHOLDERS.find(s => s.code === spec.toStakeholderCode);
              const Icon = CHANNEL_ICONS[spec.channel];
              return (
                <div key={spec.id} style={{
                  display: 'grid',
                  gridTemplateColumns: '24px 1fr 110px 70px',
                  gap: 10, alignItems: 'center',
                  padding: '6px 0', borderBottom: '1px dashed var(--border)',
                  fontSize: 12,
                }}>
                  <Icon size={13} style={{ color: 'var(--ink-3)' }} />
                  <div>
                    <div style={{ fontWeight: 600, color: 'var(--ink-1)' }}>
                      → {lang === 'ar' ? stakeholder?.nameAr : stakeholder?.nameEn}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 1 }}>
                      <span className="mono">{spec.regulatoryRef}</span>
                      {' · '}
                      {lang === 'ar' ? spec.descriptionAr : spec.descriptionEn}
                    </div>
                  </div>
                  <Badge tone={spec.triggerSeverity === 'CRITICAL' ? 'danger' : spec.triggerSeverity === 'HIGH_OR_CRITICAL' ? 'warning' : 'info'}>
                    {spec.triggerSeverity.replace(/_/g, ' ')}
                  </Badge>
                  <Badge tone={spec.mandatory ? 'primary' : 'neutral'}>
                    {formatDeadline(spec.withinHours, lang)}
                  </Badge>
                </div>
              );
            })}
          </div>
        ))}
      </div>
    </Card>
  );
}

// ── Stakeholder directory ──────────────────────────────────────────────────
function StakeholderDirectory({ lang, t }: { lang: 'en' | 'ar'; t: (k: string) => string }) {
  const grouped = useMemo(() => {
    const m = new Map<Stakeholder['type'], Stakeholder[]>();
    for (const s of STAKEHOLDERS) {
      const list = m.get(s.type) ?? [];
      list.push(s);
      m.set(s.type, list);
    }
    return m;
  }, []);

  const TYPE_LABEL: Record<Stakeholder['type'], { en: string; ar: string }> = {
    INTERNAL_BOARD:     { en: 'Board + governance committees', ar: 'المجلس واللجان' },
    INTERNAL_EXEC:      { en: 'Executive officers',            ar: 'القيادات التنفيذية' },
    INTERNAL_FUNCTION:  { en: 'Function leads',                ar: 'مسؤولو الوظائف' },
    INTERNAL_TEAM:      { en: 'Operational teams',             ar: 'الفرق التشغيلية' },
    EXTERNAL_REGULATOR: { en: 'Saudi regulators',              ar: 'الجهات السعودية' },
    EXTERNAL_AUDITOR:   { en: 'External audit firms',          ar: 'مكاتب التدقيق الخارجية' },
    EXTERNAL_VENDOR:    { en: 'Critical vendors',              ar: 'الموردون الحرجون' },
    EXTERNAL_CUSTOMER:  { en: 'Customers + data subjects',     ar: 'العملاء وأصحاب البيانات' },
    EXTERNAL_PUBLIC:    { en: 'Public + press',                ar: 'الجمهور والصحافة' },
  };

  return (
    <Card title={t('commsTabDirectory')} Icon={Users}
      action={<Badge tone="primary">{STAKEHOLDERS.length}</Badge>}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {Array.from(grouped.entries()).map(([type, list]) => (
          <div key={type}>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', fontWeight: 700,
                          textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }}>
              {lang === 'ar' ? TYPE_LABEL[type].ar : TYPE_LABEL[type].en}
              {' '}<Badge tone="neutral">{list.length}</Badge>
            </div>
            <div style={{ display: 'grid',
                          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                          gap: 6 }}>
              {list.map(s => {
                const Icon = s.channel ? CHANNEL_ICONS[s.channel] : Users;
                const linkAuthority = s.type === 'EXTERNAL_REGULATOR' && s.authorityCode;
                const inner = (
                  <div style={{
                    display: 'grid',
                    gridTemplateColumns: '28px 1fr',
                    gap: 8, alignItems: 'center',
                    padding: '8px 10px',
                    borderRadius: 'var(--r-sm)',
                    border: '1px solid var(--border)',
                    background: 'var(--surface-1)',
                    minWidth: 0,
                  }}>
                    <div style={{
                      width: 26, height: 26, borderRadius: 'var(--r-sm)',
                      background: 'var(--surface-2)',
                      display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                      color: 'var(--primary-700)',
                    }}>
                      <Icon size={13} />
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)',
                                    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {lang === 'ar' ? s.nameAr : s.nameEn}
                      </div>
                      {s.titleEn && (
                        <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 1,
                                      whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {lang === 'ar' ? s.titleAr : s.titleEn}
                        </div>
                      )}
                      {s.contactChannel && (
                        <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 1,
                                      whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} className="mono">
                          {s.contactChannel}
                        </div>
                      )}
                    </div>
                  </div>
                );
                return linkAuthority
                  ? <Link key={s.code} to={`/authority/${s.authorityCode}`} style={{ textDecoration: 'none', color: 'inherit' }}>
                      {inner}
                    </Link>
                  : <div key={s.code}>{inner}</div>;
              })}
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
}

// ── RACI register ──────────────────────────────────────────────────────────
function RaciRegisterView({ lang }: { lang: 'en' | 'ar' }) {
  const grouped = useMemo(() => {
    const m = new Map<typeof PROCESS_RACI[number]['module'], typeof PROCESS_RACI>();
    for (const p of PROCESS_RACI) {
      const list = m.get(p.module) ?? [];
      list.push(p);
      m.set(p.module, list);
    }
    return m;
  }, []);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {Array.from(grouped.entries()).map(([module, list]) => (
        <Card key={module} title={module} Icon={Landmark}
          action={<Badge tone="primary">{list.length}</Badge>}>
          {list.map(p => (
            <div key={p.processKey} style={{ padding: '8px 0', borderBottom: '1px dashed var(--border)' }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                {lang === 'ar' ? p.titleAr : p.titleEn}
              </div>
              <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }} className="mono">
                {p.standardRefs.join(' · ')}
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8, marginTop: 6 }}>
                {(['A', 'R', 'C', 'I'] as const).map(role => {
                  const codes = p.raci.filter(r => r.role === role).map(r => {
                    const s = STAKEHOLDERS.find(x => x.code === r.stakeholderCode);
                    return lang === 'ar' ? (s?.nameAr ?? r.stakeholderCode) : (s?.nameEn ?? r.stakeholderCode);
                  });
                  return (
                    <div key={role}>
                      <Badge tone={STATUS_TONE_RACI[role] as BadgeTone}>{role}</Badge>
                      <div style={{ fontSize: 11, color: 'var(--ink-2)', marginTop: 4 }}>
                        {codes.length === 0 ? '—' : codes.join(', ')}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
        </Card>
      ))}
    </div>
  );
}

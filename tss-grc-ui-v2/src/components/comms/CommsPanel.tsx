import { Link } from 'react-router-dom';
import {
  Users, Send, Clock, AlertOctagon, CheckCheck, Mail, Phone, ExternalLink,
  Building2, MessageSquare, Megaphone, FileSignature, Bell, Smartphone, MessageCircle,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge, type BadgeTone } from '../ui/Badge';
import { EmptyState } from '../ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import {
  PROCESS_RACI, STAKEHOLDERS, STATUS_TONE_RACI, RACI_LABEL_LONG,
  type Stakeholder, type ProcessRaci, type RaciRole, type CommChannel,
} from '../../lib/stakeholders';
import {
  notificationsForEvent, formatDeadline, COMMS_LOG_BY_RECORD,
  type EventKind, type NotificationSpec, type CommState, type CommLogEntry,
} from '../../lib/notificationMatrix';

/**
 * Cross-cutting "Stakeholders & Comms" panel — embedded on every entity
 * detail page. Renders three sections:
 *
 *   1. RACI table — canonical Responsible / Accountable / Consulted /
 *      Informed assignments for the workflow stage(s) this entity is in
 *   2. Required notifications — what regulators / committees must be told,
 *      with statutory deadlines and live SLA clocks
 *   3. Communications log — chain-of-custody trail of comms already sent
 *
 * industry-standard GRC tooling "Stakeholder Management" + "Notification Engine" pattern.
 */
export function CommsPanel({
  module, eventKind, severity, eventDetectedAt, recordKey, now,
}: {
  /** Filters PROCESS_RACI to the relevant module(s). */
  module: ProcessRaci['module'];
  /** Drives the notifications matrix lookup. Omit to skip notifications. */
  eventKind?: EventKind;
  severity?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  /** ISO timestamp the event was detected — drives the SLA clock. */
  eventDetectedAt?: string;
  /** "{KIND}:{id}" composite key joined to COMMS_LOG_BY_RECORD. */
  recordKey?: string;
  /** Override "now" for deterministic deadline rendering (default Date.now). */
  now?: Date;
}) {
  const { lang } = useI18n();
  const racis = PROCESS_RACI.filter(p => p.module === module);

  const specs = eventKind && severity
    ? notificationsForEvent(eventKind, severity)
    : [];
  const log = recordKey ? (COMMS_LOG_BY_RECORD[recordKey] ?? []) : [];
  const logBySpec = new Map<string, CommLogEntry>();
  for (const e of log) logBySpec.set(e.specId, e);

  const nowMs = (now ?? new Date()).getTime();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* RACI table */}
      <Card title={lang === 'ar' ? 'مصفوفة المسؤوليات (RACI)' : 'RACI matrix'} Icon={Users}
        action={<Badge tone="primary">{racis.length}</Badge>}>
        {racis.length === 0
          ? <EmptyState Icon={Users}
              title={lang === 'ar' ? 'لا توجد مصفوفة مسؤوليات' : 'No RACI defined'}
              sub={lang === 'ar' ? 'لم تُحدَّد مصفوفة المسؤوليات لهذه الوحدة بعد.' : 'No RACI registered for this module yet.'} />
          : racis.map(p => <RaciRow key={p.processKey} process={p} lang={lang} />)
        }
      </Card>

      {/* Required notifications + comms log */}
      {specs.length > 0 && (
        <Card title={lang === 'ar' ? 'الإشعارات المطلوبة' : 'Required notifications'} Icon={Send}
          action={<Badge tone={specs.length > 0 ? 'warning' : 'success'}>{specs.length}</Badge>}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {specs.map(spec => (
              <NotificationRow key={spec.id}
                spec={spec}
                logEntry={logBySpec.get(spec.id)}
                eventDetectedAt={eventDetectedAt}
                nowMs={nowMs}
                lang={lang} />
            ))}
          </div>
        </Card>
      )}

      {/* If a record has comm-log entries that aren't in the live spec list
          (e.g. one-off comms, ad hoc), surface them too so the audit trail
          stays complete. */}
      {log.length > 0 && log.some(e => !specs.find(s => s.id === e.specId)) && (
        <Card title={lang === 'ar' ? 'سلسلة العهدة (إضافي)' : 'Comms log — additional'} Icon={MessageSquare}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {log.filter(e => !specs.find(s => s.id === e.specId)).map(e => (
              <div key={e.id} style={{ fontSize: 12, color: 'var(--ink-2)', padding: '4px 0' }}>
                <span className="mono" style={{ color: 'var(--ink-3)' }}>{e.sentAt ?? e.eventDetectedAt}</span>
                {' · '}{e.specId}{' · '}{e.state}
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}

// ── RACI row ───────────────────────────────────────────────────────────────
function RaciRow({ process, lang }: { process: ProcessRaci; lang: 'en' | 'ar' }) {
  const grouped: Record<RaciRole, Stakeholder[]> = { A: [], R: [], C: [], I: [] };
  for (const a of process.raci) {
    const s = STAKEHOLDERS.find(x => x.code === a.stakeholderCode);
    if (s) grouped[a.role].push(s);
  }
  return (
    <div style={{ padding: '10px 0', borderBottom: '1px dashed var(--border)' }}>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 10 }}>
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
            {lang === 'ar' ? process.titleAr : process.titleEn}
          </div>
          {process.standardRefs.length > 0 && (
            <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }} className="mono">
              {process.standardRefs.join(' · ')}
            </div>
          )}
          {process.noteEn && (
            <div style={{ fontSize: 11, color: 'var(--ink-2)', marginTop: 4, fontStyle: 'italic' }}>
              {process.noteEn}
            </div>
          )}
        </div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginTop: 8 }}>
        {(['A', 'R', 'C', 'I'] as RaciRole[]).map(role => (
          <div key={role}>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 10, color: 'var(--ink-3)',
                          textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 700 }}>
              <Badge tone={STATUS_TONE_RACI[role] as BadgeTone}>{role}</Badge>
              <span>{lang === 'ar' ? RACI_LABEL_LONG[role].ar : RACI_LABEL_LONG[role].en}</span>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2, marginTop: 4 }}>
              {grouped[role].length === 0
                ? <span style={{ fontSize: 11, color: 'var(--ink-4)' }}>—</span>
                : grouped[role].map(s => (
                  <div key={s.code} style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 11, color: 'var(--ink-1)' }}>
                    <ChannelIcon channel={s.channel} />
                    <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {lang === 'ar' ? s.nameAr : s.nameEn}
                    </span>
                  </div>
                ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ── Notification row ───────────────────────────────────────────────────────
function NotificationRow({
  spec, logEntry, eventDetectedAt, nowMs, lang,
}: {
  spec: NotificationSpec;
  logEntry?: CommLogEntry;
  eventDetectedAt?: string;
  nowMs: number;
  lang: 'en' | 'ar';
}) {
  const stakeholder = STAKEHOLDERS.find(s => s.code === spec.toStakeholderCode);
  if (!stakeholder) return null;

  const state = resolveState(logEntry, spec, eventDetectedAt, nowMs);
  const slaInfo = computeSla(spec, eventDetectedAt, logEntry, nowMs, lang);

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '32px 1fr auto auto auto',
      gap: 10, alignItems: 'center',
      padding: '8px 10px',
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
        <ChannelIcon channel={spec.channel} />
      </div>

      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)',
                      whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {lang === 'ar' ? spec.descriptionAr : spec.descriptionEn}
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
          {lang === 'ar' ? stakeholder.nameAr : stakeholder.nameEn}
          {' · '}
          <span className="mono">{spec.regulatoryRef}</span>
        </div>
        {logEntry?.noteEn && (
          <div style={{ fontSize: 11, color: 'var(--ink-2)', marginTop: 4, fontStyle: 'italic' }}>
            {logEntry.noteEn}
          </div>
        )}
        {logEntry?.receiptRef && (
          <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }} className="mono">
            ref: {logEntry.receiptRef}
          </div>
        )}
      </div>

      <Badge tone="info">{formatDeadline(spec.withinHours, lang)}</Badge>

      {slaInfo
        ? <span className="mono" style={{ fontSize: 11, color: slaInfo.color, fontWeight: 700, minWidth: 70, textAlign: 'end' }}>
            {slaInfo.text}
          </span>
        : <span style={{ minWidth: 70 }} />}

      <StateBadge state={state} lang={lang} />
    </div>
  );
}

// ── Helpers ────────────────────────────────────────────────────────────────
function resolveState(
  logEntry: CommLogEntry | undefined,
  spec: NotificationSpec,
  eventDetectedAt: string | undefined,
  nowMs: number,
): CommState {
  if (logEntry) return logEntry.state;
  // No log entry — derive from deadline.
  if (!eventDetectedAt) return 'PENDING';
  const detectedMs = new Date(eventDetectedAt).getTime();
  const deadlineMs = detectedMs + spec.withinHours * 3600_000;
  if (nowMs > deadlineMs) return 'OVERDUE';
  return 'PENDING';
}

function computeSla(
  spec: NotificationSpec,
  eventDetectedAt: string | undefined,
  logEntry: CommLogEntry | undefined,
  nowMs: number,
  lang: 'en' | 'ar',
): { text: string; color: string } | null {
  if (!eventDetectedAt) return null;
  const detectedMs = new Date(eventDetectedAt).getTime();
  const deadlineMs = detectedMs + spec.withinHours * 3600_000;

  if (logEntry?.state === 'SENT' || logEntry?.state === 'ACKNOWLEDGED') {
    if (!logEntry.sentAt) return null;
    const sentMs = new Date(logEntry.sentAt).getTime();
    const usedHours = Math.round((sentMs - detectedMs) / 3600_000);
    return {
      text: `${lang === 'ar' ? 'سُلِّم بعد' : 'sent in'} ${formatDeadline(usedHours, lang)}`,
      color: sentMs <= deadlineMs ? 'var(--success-fg)' : 'var(--danger-fg)',
    };
  }

  const remainingHours = Math.round((deadlineMs - nowMs) / 3600_000);
  if (remainingHours < 0) {
    return {
      text: `${formatDeadline(-remainingHours, lang)} ${lang === 'ar' ? 'متأخر' : 'overdue'}`,
      color: 'var(--danger-fg)',
    };
  }
  return {
    text: `${formatDeadline(remainingHours, lang)} ${lang === 'ar' ? 'متبقٍ' : 'left'}`,
    color: remainingHours <= 4 ? 'var(--accent-700)' : 'var(--ink-2)',
  };
}

const STATE_BADGE: Record<CommState, { tone: BadgeTone; en: string; ar: string }> = {
  PENDING:      { tone: 'warning', en: 'Pending',     ar: 'بانتظار' },
  IN_FLIGHT:    { tone: 'info',    en: 'In flight',   ar: 'قيد الإرسال' },
  SENT:         { tone: 'success', en: 'Sent',        ar: 'أُرسل' },
  ACKNOWLEDGED: { tone: 'success', en: 'Acknowledged',ar: 'تم التأكيد' },
  OVERDUE:      { tone: 'danger',  en: 'Overdue',     ar: 'متأخر' },
};

function StateBadge({ state, lang }: { state: CommState; lang: 'en' | 'ar' }) {
  const def = STATE_BADGE[state];
  return <Badge tone={def.tone} dot>{lang === 'ar' ? def.ar : def.en}</Badge>;
}

const CHANNEL_ICONS: Record<CommChannel, LucideIcon> = {
  PORTAL:            ExternalLink,
  EMAIL:             Mail,
  PHONE:             Phone,
  SECURE_MSG:        FileSignature,
  FORMAL_LETTER:     FileSignature,
  IN_PERSON:         Building2,
  TEAMS_SLACK:       MessageSquare,
  PUBLIC_DISCLOSURE: Megaphone,
  IN_APP:            Bell,
  SMS:               Smartphone,
  WHATSAPP:          MessageCircle,
};

function ChannelIcon({ channel }: { channel?: CommChannel }) {
  if (!channel) return null;
  const Icon = CHANNEL_ICONS[channel];
  return <Icon size={12} style={{ color: 'var(--ink-3)', flexShrink: 0 }} aria-hidden />;
}

// Re-export so callers can compose nicely.
export { Link, AlertOctagon, CheckCheck, Clock };

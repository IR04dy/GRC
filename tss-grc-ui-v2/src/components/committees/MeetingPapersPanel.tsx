import { useEffect, useMemo, useState } from 'react';
import { FileText, Clock, Eye, CheckCircle2, Plus } from 'lucide-react';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { FormModal, type FieldDef } from '../ui/FormModal';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import { getUser } from '../../lib/auth';
import {
  listMeetingPapers, meetingPaperStats, markPaperRead, markPaperLate, circulatePaper,
  type MeetingPaper, type MeetingPaperStats, type ConfidentialityLevel,
} from '../../lib/committees';

const CONF_TONE: Record<ConfidentialityLevel, 'neutral' | 'info' | 'warning' | 'danger'> = {
  PUBLIC: 'neutral', INTERNAL: 'info', CONFIDENTIAL: 'warning', RESTRICTED: 'danger',
};
const CONF_LABEL: Record<ConfidentialityLevel, { en: string; ar: string }> = {
  PUBLIC:       { en: 'Public',       ar: 'عام' },
  INTERNAL:     { en: 'Internal',     ar: 'داخلي' },
  CONFIDENTIAL: { en: 'Confidential', ar: 'سري' },
  RESTRICTED:   { en: 'Restricted',   ar: 'مقيّد' },
};

interface ReadReceipt { userId?: number; readAt?: string; }

function parseReceipts(json: string | null): ReadReceipt[] {
  if (!json) return [];
  try { const a = JSON.parse(json); return Array.isArray(a) ? a as ReadReceipt[] : []; } catch { return []; }
}

/**
 * Board / committee pre-read tracking — surfaces the V154 meeting-papers API
 * (`/api/v1/committees/papers/*`) that previously had no UI. Lists the papers
 * circulated for a meeting with director read-receipts and the late-paper flag,
 * and exposes the two governance actions:
 *   • Mark read  — append the current user's read-receipt (pre-read discipline)
 *   • Flag late  — record that a paper was circulated after the agreed deadline
 */
export function MeetingPapersPanel({ meetingId, isAr }: { meetingId: number; isAr: boolean }) {
  const toast = useToast();
  const me = getUser()?.userId ?? null;
  const [papers, setPapers] = useState<MeetingPaper[] | null>(null);
  const [stats, setStats] = useState<MeetingPaperStats | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [lateTarget, setLateTarget] = useState<MeetingPaper | null>(null);
  const [circulateOpen, setCirculateOpen] = useState(false);

  async function load() {
    const [p, s] = await Promise.all([
      listMeetingPapers(meetingId).catch(() => [] as MeetingPaper[]),
      meetingPaperStats(meetingId).catch(() => null),
    ]);
    setPapers(p);
    setStats(s);
  }
  useEffect(() => { void load(); }, [meetingId]); // eslint-disable-line react-hooks/exhaustive-deps

  async function onMarkRead(p: MeetingPaper) {
    if (!me) { toast.danger(isAr ? 'تعذّر تحديد المستخدم الحالي' : 'Could not resolve current user'); return; }
    setBusyId(p.id);
    try {
      await markPaperRead(p.id, me);
      toast.success(isAr ? `سُجِّل اطلاعك على ${p.paperCode}` : `Read receipt recorded · ${p.paperCode}`);
      await load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : String(e));
    } finally { setBusyId(null); }
  }

  async function onMarkLate(reason: string) {
    if (!lateTarget) return;
    setBusyId(lateTarget.id);
    try {
      await markPaperLate(lateTarget.id, reason);
      toast.warning(isAr ? `وُسِم ${lateTarget.paperCode} كورقة متأخرة` : `Flagged late · ${lateTarget.paperCode}`);
      setLateTarget(null);
      await load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : String(e));
    } finally { setBusyId(null); }
  }

  const lateFields: FieldDef[] = useMemo(() => [
    { key: 'reason', label: isAr ? 'سبب تأخّر تعميم الورقة' : 'Reason the paper was circulated late',
      kind: 'text', required: true, maxLength: 300,
      placeholder: isAr ? 'مثال: عُمّمت بعد الموعد بـ٢٤ ساعة' : 'e.g. circulated 24h after the deadline' },
  ], [isAr]);

  // Circulate a board paper into the pre-read pack. The backend stamps the
  // circulation time and auto-flags it late when issued <24h before the
  // meeting, so the form only collects the descriptive fields.
  const circulateFields: FieldDef[] = useMemo(() => [
    { key: 'paperCode', label: isAr ? 'رمز الورقة' : 'Paper code', kind: 'text', required: true, span: 6, maxLength: 64,
      placeholder: 'e.g. PAP-2026-AUD-014' },
    { key: 'agendaItemSeq', label: isAr ? 'بند جدول الأعمال' : 'Agenda item #', kind: 'number', span: 6, min: 1 },
    { key: 'titleEn', label: isAr ? 'العنوان (إنجليزي)' : 'Title (EN)', kind: 'text', required: true, span: 12, maxLength: 255 },
    { key: 'titleAr', label: isAr ? 'العنوان (عربي)' : 'Title (AR)', kind: 'text', span: 12, maxLength: 255 },
    { key: 'authorName', label: isAr ? 'المُعِدّ' : 'Author', kind: 'text', span: 6, maxLength: 128 },
    // F2-428 — 120, not 128. `author_role` is NVARCHAR(120) while every other column on this
    // entity is a round 80/255/500, so the 128 here mirrored `authorName` on the line above
    // rather than the column underneath. FormModal enforces maxLength client-side, so
    // 121-128 characters were accepted here and refused by the INSERT: the circulate route
    // binds the entity with no @Valid, so there is no @Size to turn that into a 400 naming
    // the field — it surfaced as SQL 8152, an opaque 500. An Arabic role title (house rule 7:
    // Arabic text is not shorter) is exactly where it bites. A cap mirrors its column, never
    // its neighbour. `authorName` above stays at 128 over a 255 column on purpose: a cap that
    // is TIGHTER than its column is protective, and widening it is a product call, not a fix.
    { key: 'authorRole', label: isAr ? 'صفة المُعِدّ' : 'Author role', kind: 'text', span: 6, maxLength: 120 },
    { key: 'confidentiality', label: isAr ? 'السرية' : 'Confidentiality', kind: 'select', required: true, span: 12,
      options: (['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'] as ConfidentialityLevel[])
        .map(c => ({ value: c, label: isAr ? CONF_LABEL[c].ar : CONF_LABEL[c].en })) },
  ], [isAr]);
  const circulateInitial = useMemo(() => ({
    paperCode: '', agendaItemSeq: '', titleEn: '', titleAr: '',
    authorName: '', authorRole: '', confidentiality: 'INTERNAL',
  }), []);

  async function onCirculate(v: Record<string, string>) {
    const paperCode = String(v.paperCode ?? '').trim();
    const titleEn = String(v.titleEn ?? '').trim();
    if (!paperCode) { toast.danger(isAr ? 'رمز الورقة مطلوب' : 'Paper code is required'); return; }
    if (!titleEn)   { toast.danger(isAr ? 'العنوان الإنجليزي مطلوب' : 'English title is required'); return; }
    const opt = (s?: string) => { const x = String(s ?? '').trim(); return x ? x : undefined; };
    const seqRaw = String(v.agendaItemSeq ?? '').trim();
    try {
      await circulatePaper({
        meetingId,
        paperCode,
        titleEn,
        titleAr: opt(v.titleAr) ?? null,
        authorName: opt(v.authorName) ?? null,
        authorRole: opt(v.authorRole) ?? null,
        agendaItemSeq: seqRaw ? Number(seqRaw) : null,
        confidentiality: (v.confidentiality as ConfidentialityLevel) || 'INTERNAL',
      });
      toast.success(isAr ? `عُمّمت الورقة ${paperCode}` : `Paper ${paperCode} circulated`);
      setCirculateOpen(false);
      await load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تعميم الورقة' : 'Failed to circulate the paper'));
    }
  }

  const circulateBtn = (
    <Button size="sm" variant="primary" Icon={Plus} onClick={() => setCirculateOpen(true)}>
      {isAr ? 'تعميم ورقة' : 'Circulate paper'}
    </Button>
  );

  const circulateModal = (
    <FormModal
      open={circulateOpen}
      size="md"
      title={isAr ? 'تعميم ورقة اجتماع' : 'Circulate meeting paper'}
      fields={circulateFields}
      initial={circulateInitial}
      submitLabel={isAr ? 'تعميم' : 'Circulate'}
      onClose={() => setCirculateOpen(false)}
      onSubmit={onCirculate}
    />
  );

  if (papers && papers.length === 0) {
    return (
      <div style={{ marginTop: 10 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
          <strong style={{ fontSize: 13 }}>{isAr ? 'أوراق الاجتماع (القراءة المسبقة)' : 'Meeting papers (pre-read)'}</strong>
          {circulateBtn}
        </div>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 4 }}>
          {isAr ? 'لا توجد أوراق معمّمة لهذا الاجتماع.' : 'No papers circulated for this meeting.'}
        </div>
        {circulateModal}
      </div>
    );
  }

  return (
    <div style={{ marginTop: 10 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginBottom: 6 }}>
        <FileText size={14} />
        <strong style={{ fontSize: 13 }}>{isAr ? 'أوراق الاجتماع (القراءة المسبقة)' : 'Meeting papers (pre-read)'}</strong>
        {stats && (
          <span style={{ display: 'inline-flex', gap: 6, marginInlineStart: 4 }}>
            <Badge tone="info">{isAr ? `الإجمالي ${stats.totalPapers}` : `${stats.totalPapers} total`}</Badge>
            <Badge tone="success">{isAr ? `بإيصالات ${stats.papersWithReceipts}` : `${stats.papersWithReceipts} acknowledged`}</Badge>
            <Badge tone={stats.latePapers > 0 ? 'warning' : 'neutral'}>{isAr ? `متأخرة ${stats.latePapers}` : `${stats.latePapers} late`}</Badge>
          </span>
        )}
        <span style={{ marginInlineStart: 'auto' }}>{circulateBtn}</span>
      </div>

      {!papers
        ? <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
        : (
          <table className="grc-table" style={{ width: '100%', fontSize: 12 }}>
            <thead>
              <tr>
                <th>{isAr ? 'الرمز' : 'Code'}</th>
                <th>{isAr ? 'العنوان' : 'Title'}</th>
                <th>{isAr ? 'المُعِدّ' : 'Author'}</th>
                <th>{isAr ? 'عُمِّمت' : 'Circulated'}</th>
                <th>{isAr ? 'السرية' : 'Confidentiality'}</th>
                <th>{isAr ? 'إيصالات القراءة' : 'Read receipts'}</th>
                <th>{isAr ? 'الحالة' : 'Status'}</th>
                <th style={{ textAlign: isAr ? 'left' : 'right' }}>{isAr ? 'إجراءات' : 'Actions'}</th>
              </tr>
            </thead>
            <tbody>
              {papers.map(p => {
                const receipts = parseReceipts(p.readReceiptsJson);
                const meRead = me != null && receipts.some(r => r.userId === me);
                const busy = busyId === p.id;
                const conf = p.confidentiality;
                return (
                  <tr key={p.id}>
                    <td className="mono">{p.paperCode}</td>
                    <td>{isAr && p.titleAr ? p.titleAr : p.titleEn}</td>
                    <td>{p.authorName ?? '—'}{p.authorRole ? <span style={{ color: 'var(--ink-3)' }}> · {p.authorRole}</span> : null}</td>
                    <td className="mono">{p.circulatedAt ? p.circulatedAt.slice(0, 10) : '—'}</td>
                    <td><Badge tone={CONF_TONE[conf] ?? 'neutral'}>{isAr ? CONF_LABEL[conf]?.ar : CONF_LABEL[conf]?.en}</Badge></td>
                    <td>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                        <Eye size={12} /> {receipts.length}
                        {meRead && <CheckCircle2 size={12} color="var(--success)" />}
                      </span>
                    </td>
                    <td>
                      {p.latePaper
                        ? <Badge tone="warning" title={p.latePaperReason ?? undefined}><Clock size={11} style={{ display: 'inline', marginInlineEnd: 2 }} />{isAr ? 'متأخرة' : 'Late'}</Badge>
                        : <Badge tone="success">{isAr ? 'في الموعد' : 'On time'}</Badge>}
                    </td>
                    <td style={{ textAlign: isAr ? 'left' : 'right', whiteSpace: 'nowrap' }}>
                      <Button size="sm" variant="ghost" Icon={Eye} disabled={busy || meRead}
                        onClick={() => void onMarkRead(p)}>
                        {meRead ? (isAr ? 'مقروءة' : 'Read') : (isAr ? 'تسجيل القراءة' : 'Mark read')}
                      </Button>
                      {' '}
                      <Button size="sm" variant="secondary" Icon={Clock} disabled={busy || !!p.latePaper}
                        onClick={() => setLateTarget(p)}>
                        {isAr ? 'تأخّر' : 'Flag late'}
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}

      <FormModal<{ reason: string }>
        open={lateTarget != null}
        size="sm"
        title={isAr ? `وسم ورقة متأخرة — ${lateTarget?.paperCode ?? ''}` : `Flag paper late — ${lateTarget?.paperCode ?? ''}`}
        fields={lateFields}
        initial={{ reason: '' }}
        submitLabel={isAr ? 'وسم كمتأخرة' : 'Flag late'}
        onClose={() => setLateTarget(null)}
        onSubmit={async (v) => { await onMarkLate(v.reason.trim()); return {}; }}
      />

      {circulateModal}
    </div>
  );
}

import { useEffect, useState, type FormEvent } from 'react';
import { BookOpen, AlertOctagon, Wrench, RefreshCw, Plus, AlertTriangle } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listLessons, createLesson, transitionLesson,
  listGaps, createGap, transitionGap,
  listCapas, createCapa, transitionCapa, flagOverdueCapas, remediationStats,
  gapStatusTone, severityTone, capaStatusTone,
  type BcmLessonLearned, type BcmGap, type BcmCorrectiveAction,
  type LessonStatus, type GapStatus, type CapaStatus, type LessonSource,
} from '../../lib/bcmCycle';

/**
 * BCM Remediation workspace — independent audit Tier-1 #5.
 *
 * <p>Single page with three tabs (Lessons / Gaps / CAPAs) so the backend
 * tables that BCM-3 shipped finally have a UI. The audit flagged that all
 * three had services + controllers + tests + repositories but no register
 * page anywhere — users had to make REST calls by hand.
 *
 * <p>The CAPA tab also exposes the "flag overdue" admin action which calls
 * {@code BcmRemediationService.flagOverdueCapas} — the scheduler entry that
 * flips eligible IN_PROGRESS rows past their due date to OVERDUE.
 */
type TabId = 'lessons' | 'gaps' | 'capas';

const LESSON_SOURCES: LessonSource[] = ['INCIDENT', 'CRISIS', 'EXERCISE', 'AUDIT', 'MANUAL'];
const GAP_SOURCES = ['BIA', 'BCP', 'DRP', 'EXERCISE', 'CALL_TREE', 'AUDIT', 'RISK', 'MANUAL'] as const;
const SEVERITIES: BcmGap['severity'][] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const PRIORITIES: BcmCorrectiveAction['priority'][] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

const LESSON_NEXT: Record<LessonStatus, LessonStatus[]> = {
  DRAFT:        ['UNDER_REVIEW'],
  UNDER_REVIEW: ['APPROVED', 'REJECTED'],
  REJECTED:     ['DRAFT'],
  APPROVED:     ['CLOSED'],
  CLOSED:       [],
};
const GAP_NEXT: Record<GapStatus, GapStatus[]> = {
  OPEN:          ['ASSIGNED', 'ACCEPTED_RISK'],
  ASSIGNED:      ['IN_PROGRESS', 'OPEN'],
  IN_PROGRESS:   ['UNDER_REVIEW'],
  UNDER_REVIEW:  ['CLOSED', 'IN_PROGRESS'],
  CLOSED:        ['REOPENED'],
  ACCEPTED_RISK: ['OPEN'],
  REOPENED:      ['IN_PROGRESS'],
};
const CAPA_NEXT: Record<CapaStatus, CapaStatus[]> = {
  OPEN:               ['ASSIGNED'],
  ASSIGNED:           ['IN_PROGRESS'],
  IN_PROGRESS:        ['EVIDENCE_SUBMITTED', 'OVERDUE'],
  EVIDENCE_SUBMITTED: ['UNDER_REVIEW'],
  UNDER_REVIEW:       ['CLOSED', 'IN_PROGRESS'],
  CLOSED:             ['REOPENED'],
  OVERDUE:            ['IN_PROGRESS', 'EVIDENCE_SUBMITTED'],
  REOPENED:           ['IN_PROGRESS'],
};

export function BcmRemediationPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [tab, setTab] = useState<TabId>('gaps');
  const [lessons, setLessons] = useState<BcmLessonLearned[]>([]);
  const [gaps, setGaps]       = useState<BcmGap[]>([]);
  const [capas, setCapas]     = useState<BcmCorrectiveAction[]>([]);
  const [stats, setStats]     = useState<{ openGaps: number; criticalGaps: number; openCapas: number; overdueCapas: number; closedCapas: number; } | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  async function reload() {
    // The shared `api` helper already resolves to the parsed JSON body
    // (it is NOT axios-style), so callers must NOT chain `.data` — the
    // earlier `.then(r => r.data)` made every list `undefined` and blanked
    // the page on first render (same class of bug fixed across other BCM libs).
    //
    // Per-call .catch() so a partial backend failure (e.g. missing-table
    // 503 on a fresh dev DB) doesn't blank the whole page. Each panel
    // degrades independently to its empty default.
    const [l, g, c, s] = await Promise.all([
      listLessons().catch(() => []),
      listGaps().catch(() => []),
      listCapas().catch(() => []),
      remediationStats().catch(() => null),
    ]);
    setLessons(Array.isArray(l) ? l : []);
    setGaps(Array.isArray(g) ? g : []);
    setCapas(Array.isArray(c) ? c : []);
    setStats(s ?? null);
  }
  useEffect(() => { void reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function moveLesson(l: BcmLessonLearned, to: LessonStatus) {
    try { await transitionLesson(l.id, to); toast.success(isAr ? `الدرس → ${to}` : `Lesson → ${to}`); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }
  async function moveGap(g: BcmGap, to: GapStatus) {
    try { await transitionGap(g.id, to); toast.success(isAr ? `الفجوة → ${to}` : `Gap → ${to}`); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }
  async function moveCapa(c: BcmCorrectiveAction, to: CapaStatus) {
    // CLOSE requires evidence — prompt for evidence id
    let evidenceId: number | undefined;
    if (to === 'CLOSED') {
      const raw = window.prompt(isAr ? 'رقم الدليل لإثبات الإغلاق' : 'Evidence ID to close');
      if (!raw) return;
      evidenceId = Number(raw);
      if (!Number.isFinite(evidenceId) || evidenceId <= 0) {
        toast.warning(isAr ? 'رقم غير صالح' : 'Invalid id'); return;
      }
    }
    try { await transitionCapa(c.id, to, evidenceId); toast.success(isAr ? `الإجراء → ${to}` : `CAPA → ${to}`); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }

  async function runFlagOverdue() {
    try {
      const out = await flagOverdueCapas();
      const n = out?.flippedToOverdue ?? 0;
      toast.success(isAr ? `تم تحديث ${n} إجراءً متأخراً` : `Flagged ${n} CAPAs as overdue`);
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }

  const tabs: TabItem<TabId>[] = [
    { id: 'lessons', label: isAr ? `دروس مستفادة (${lessons.length})` : `Lessons (${lessons.length})`,    Icon: BookOpen },
    { id: 'gaps',    label: isAr ? `فجوات (${gaps.length})`             : `Gaps (${gaps.length})`,         Icon: AlertOctagon },
    { id: 'capas',   label: isAr ? `إجراءات تصحيحية (${capas.length})`  : `CAPAs (${capas.length})`,       Icon: Wrench },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'فجوات الاستمرارية والإجراءات التصحيحية' : 'BCM remediation'}
        sub={isAr
          ? 'إدارة دروس مستفادة وفجوات وإجراءات تصحيحية BCM-3 من واجهة واحدة.'
          : 'Lessons learned, gaps, and corrective actions from BCM-3 in one workspace.'}
        eyebrow={isAr ? 'استمرارية الأعمال' : 'Business Continuity'}
        actions={<>
          <Button variant="ghost" onClick={() => void runFlagOverdue()}>
            <RefreshCw size={14} /> {isAr ? 'وسم المتأخرة' : 'Flag overdue'}
          </Button>
          <Button variant="primary" onClick={() => setShowCreate(true)}>
            <Plus size={14} /> {isAr ? 'إضافة' : 'New'}
          </Button>
        </>}
      />

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(170px,1fr))', gap: 12, marginBottom: 14 }}>
        <Kpi Icon={AlertOctagon}  label={isAr ? 'فجوات مفتوحة'   : 'Open gaps'}      value={stats?.openGaps ?? 0}     tone="warning" />
        <Kpi Icon={AlertTriangle} label={isAr ? 'فجوات حرجة'     : 'Critical gaps'}  value={stats?.criticalGaps ?? 0} tone="danger" />
        <Kpi Icon={Wrench}        label={isAr ? 'إجراءات مفتوحة' : 'Open CAPAs'}     value={stats?.openCapas ?? 0}    tone="info" />
        <Kpi Icon={RefreshCw}     label={isAr ? 'متأخرة'         : 'Overdue CAPAs'}  value={stats?.overdueCapas ?? 0} tone="danger" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      <div style={{ marginTop: 12 }}>
        {tab === 'lessons' && <LessonsTab lessons={lessons} onMove={moveLesson} isAr={isAr} />}
        {tab === 'gaps'    && <GapsTab gaps={gaps} onMove={moveGap} isAr={isAr} />}
        {tab === 'capas'   && <CapasTab capas={capas} onMove={moveCapa} isAr={isAr} />}
      </div>

      {showCreate && <CreateModal tab={tab} onClose={() => setShowCreate(false)}
                                  onCreated={() => { setShowCreate(false); void reload(); }} isAr={isAr} />}
    </Shell>
  );
}

// ─── Tab content ──────────────────────────────────────────────

function LessonsTab({ lessons, onMove, isAr }: {
  lessons: BcmLessonLearned[]; onMove: (l: BcmLessonLearned, to: LessonStatus) => void; isAr: boolean;
}) {
  if (lessons.length === 0) return <Card title={isAr ? 'الدروس المستفادة' : 'Lessons learned'}>
    <InlineEmpty text={isAr ? 'لا توجد دروس بعد' : 'No lessons yet'} />
  </Card>;
  return (
    <Card title={isAr ? 'الدروس المستفادة' : 'Lessons learned'}>
      <div style={{ overflowX: 'auto' }}>
        <table className="data-table" style={{ width: '100%', fontSize: 13 }}>
          <thead><tr>
            <th style={{ textAlign: 'start' }}>{isAr ? 'العنوان' : 'Title'}</th>
            <th style={{ textAlign: 'start' }}>{isAr ? 'المصدر' : 'Source'}</th>
            <th style={{ textAlign: 'start' }}>{isAr ? 'الحالة' : 'Status'}</th>
            <th style={{ textAlign: 'end' }}>{isAr ? 'إجراءات' : 'Actions'}</th>
          </tr></thead>
          <tbody>
            {lessons.map(l => (
              <tr key={l.id}>
                <td>{l.titleEn}</td>
                <td><Badge tone="info">{l.sourceType}</Badge></td>
                <td><Badge tone="neutral">{l.status}</Badge></td>
                <td style={{ textAlign: 'end' }}>
                  {LESSON_NEXT[l.status].map(s => (
                    <Button key={s} variant="ghost" onClick={() => onMove(l, s)}>{s}</Button>
                  ))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

function GapsTab({ gaps, onMove, isAr }: {
  gaps: BcmGap[]; onMove: (g: BcmGap, to: GapStatus) => void; isAr: boolean;
}) {
  if (gaps.length === 0) return <Card title={isAr ? 'الفجوات' : 'Gaps'}>
    <InlineEmpty text={isAr ? 'لا توجد فجوات' : 'No gaps yet'} />
  </Card>;
  return (
    <Card title={isAr ? 'سجل الفجوات' : 'Gaps register'}>
      <div style={{ overflowX: 'auto' }}>
        <table className="data-table" style={{ width: '100%', fontSize: 13 }}>
          <thead><tr>
            <th style={{ textAlign: 'start' }}>{isAr ? 'العنوان' : 'Title'}</th>
            <th style={{ textAlign: 'start' }}>{isAr ? 'المصدر' : 'Source'}</th>
            <th style={{ textAlign: 'start' }}>{isAr ? 'الشدة' : 'Severity'}</th>
            <th style={{ textAlign: 'start' }}>{isAr ? 'الحالة' : 'Status'}</th>
            <th style={{ textAlign: 'start' }}>{isAr ? 'تاريخ الاستحقاق' : 'Due'}</th>
            <th style={{ textAlign: 'end' }}>{isAr ? 'إجراءات' : 'Actions'}</th>
          </tr></thead>
          <tbody>
            {gaps.map(g => (
              <tr key={g.id}>
                <td>{g.titleEn}</td>
                <td><Badge tone="info">{g.source}</Badge></td>
                <td><Badge tone={severityTone(g.severity)}>{g.severity}</Badge></td>
                <td><Badge tone={gapStatusTone(g.status)}>{g.status}</Badge></td>
                <td className="mono">{g.dueDate ?? '—'}</td>
                <td style={{ textAlign: 'end' }}>
                  {GAP_NEXT[g.status].map(s => (
                    <Button key={s} variant="ghost" onClick={() => onMove(g, s)}>{s}</Button>
                  ))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

function CapasTab({ capas, onMove, isAr }: {
  capas: BcmCorrectiveAction[]; onMove: (c: BcmCorrectiveAction, to: CapaStatus) => void; isAr: boolean;
}) {
  const lang = isAr ? 'ar' : 'en';
  if (capas.length === 0) return <Card title={isAr ? 'الإجراءات التصحيحية' : 'CAPAs'}>
    <InlineEmpty text={isAr ? 'لا توجد إجراءات تصحيحية' : 'No CAPAs yet'} />
  </Card>;
  return (
    <Card title={isAr ? 'سجل الإجراءات التصحيحية' : 'CAPA register'}>
      <div style={{ overflowX: 'auto' }}>
        <table className="data-table" style={{ width: '100%', fontSize: 13 }}>
          <thead><tr>
            <th style={{ textAlign: 'start' }}>{isAr ? 'العنوان' : 'Title'}</th>
            <th style={{ textAlign: 'start' }}>{isAr ? 'الأولوية' : 'Priority'}</th>
            <th style={{ textAlign: 'start' }}>{isAr ? 'التقدّم' : 'Progress'}</th>
            <th style={{ textAlign: 'start' }}>{isAr ? 'الحالة' : 'Status'}</th>
            <th style={{ textAlign: 'start' }}>{isAr ? 'تاريخ الاستحقاق' : 'Due'}</th>
            <th style={{ textAlign: 'end' }}>{isAr ? 'إجراءات' : 'Actions'}</th>
          </tr></thead>
          <tbody>
            {capas.map(c => (
              <tr key={c.id}>
                <td>{c.titleEn}</td>
                <td><Badge tone={severityTone(c.priority)}>{c.priority}</Badge></td>
                <td className="mono">{c.progressPercent}%</td>
                <td><Badge tone={capaStatusTone(c.status)}>{statusLabel(c.status, lang)}</Badge></td>
                <td className="mono">{c.dueDate ?? '—'}</td>
                <td style={{ textAlign: 'end' }}>
                  {CAPA_NEXT[c.status].map(s => (
                    <Button key={s} variant="ghost" onClick={() => onMove(c, s)}>{s}</Button>
                  ))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

// ─── Create modal ────────────────────────────────────────────

function CreateModal({ tab, onClose, onCreated, isAr }: {
  tab: TabId; onClose: () => void; onCreated: () => void; isAr: boolean;
}) {
  const toast = useToast();
  const [titleEn, setTitleEn]     = useState('');
  const [titleAr, setTitleAr]     = useState('');
  const [description, setDescription] = useState('');
  const [sourceType, setSourceType]   = useState<LessonSource>('MANUAL');
  const [gapSource, setGapSource]     = useState<typeof GAP_SOURCES[number]>('MANUAL');
  const [severity, setSeverity]       = useState<BcmGap['severity']>('MEDIUM');
  const [priority, setPriority]       = useState<BcmCorrectiveAction['priority']>('MEDIUM');
  const [dueDate, setDueDate]         = useState('');
  const [gapId, setGapId]             = useState<number | undefined>();

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!titleEn.trim()) { toast.warning(isAr ? 'العنوان مطلوب' : 'Title required'); return; }
    try {
      if (tab === 'lessons') {
        await createLesson({ titleEn, titleAr, description, sourceType });
      } else if (tab === 'gaps') {
        await createGap({ titleEn, titleAr, description, source: gapSource, severity,
                          dueDate: dueDate || undefined });
      } else {
        await createCapa({ titleEn, titleAr, actionPlan: description, gapId, priority,
                           dueDate: dueDate || undefined });
      }
      toast.success(isAr ? 'تم الإنشاء' : 'Created');
      onCreated();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
                  display: 'grid', placeItems: 'center', zIndex: 999 }} onClick={onClose}>
      <form onClick={e => e.stopPropagation()} onSubmit={submit} style={{
        background: 'var(--surface)', borderRadius: 8, padding: 20, width: 'min(520px, 92vw)',
        display: 'grid', gap: 10,
      }}>
        <h3 style={{ margin: 0 }}>
          {tab === 'lessons' ? (isAr ? 'درس مستفاد جديد' : 'New lesson')
           : tab === 'gaps'  ? (isAr ? 'فجوة جديدة' : 'New gap')
                              : (isAr ? 'إجراء تصحيحي جديد' : 'New CAPA')}
        </h3>
        <Field label={isAr ? 'العنوان (إنجليزي)' : 'Title (EN)'} required>
          <input value={titleEn} onChange={e => setTitleEn(e.target.value)} required style={inputStyle} />
        </Field>
        <Field label={isAr ? 'العنوان (عربي)' : 'Title (AR)'}>
          <input value={titleAr} onChange={e => setTitleAr(e.target.value)} style={inputStyle} />
        </Field>
        <Field label={tab === 'capas' ? (isAr ? 'خطة الإجراء' : 'Action plan') : (isAr ? 'الوصف' : 'Description')}>
          <textarea value={description} onChange={e => setDescription(e.target.value)}
                    style={{ ...inputStyle, minHeight: 70 }} />
        </Field>
        {tab === 'lessons' && (
          <Field label={isAr ? 'المصدر' : 'Source'}>
            <select value={sourceType} onChange={e => setSourceType(e.target.value as LessonSource)} style={inputStyle}>
              {LESSON_SOURCES.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </Field>
        )}
        {tab === 'gaps' && (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <Field label={isAr ? 'المصدر' : 'Source'}>
              <select value={gapSource} onChange={e => setGapSource(e.target.value as typeof GAP_SOURCES[number])} style={inputStyle}>
                {GAP_SOURCES.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
            </Field>
            <Field label={isAr ? 'الشدة' : 'Severity'}>
              <select value={severity} onChange={e => setSeverity(e.target.value as BcmGap['severity'])} style={inputStyle}>
                {SEVERITIES.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
            </Field>
          </div>
        )}
        {tab === 'capas' && (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <Field label={isAr ? 'رقم الفجوة (اختياري)' : 'Gap id (optional)'}>
              <input type="number" value={gapId ?? ''} onChange={e => setGapId(e.target.value ? Number(e.target.value) : undefined)} style={inputStyle} />
            </Field>
            <Field label={isAr ? 'الأولوية' : 'Priority'}>
              <select value={priority} onChange={e => setPriority(e.target.value as BcmCorrectiveAction['priority'])} style={inputStyle}>
                {PRIORITIES.map(p => <option key={p} value={p}>{p}</option>)}
              </select>
            </Field>
          </div>
        )}
        {tab !== 'lessons' && (
          <Field label={isAr ? 'تاريخ الاستحقاق' : 'Due date'}>
            <input type="date" value={dueDate} onChange={e => setDueDate(e.target.value)} style={inputStyle} />
          </Field>
        )}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button variant="ghost" type="button" onClick={onClose}>{isAr ? 'إلغاء' : 'Cancel'}</Button>
          <Button variant="primary" type="submit">{isAr ? 'إنشاء' : 'Create'}</Button>
        </div>
      </form>
    </div>
  );
}

function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <label style={{ display: 'grid', gap: 4, fontSize: 12 }}>
      <span className="text-muted">{label}{required && ' *'}</span>
      {children}
    </label>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '6px 10px', border: '1px solid var(--border)', borderRadius: 4,
  fontSize: 13, background: 'var(--surface)', width: '100%',
};

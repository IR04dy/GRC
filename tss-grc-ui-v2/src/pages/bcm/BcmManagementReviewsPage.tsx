import { useEffect, useMemo, useState } from 'react';
import {
  ClipboardList, RefreshCw, Plus, ArrowRight, Users, CalendarDays,
  Gavel, ListChecks, Activity, CheckCircle2, Archive,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  bcmManagementReviewApi, nextMrStatus, mrStatusLabel, mrAdvanceLabel,
  MR_STATUS_TONE, parseDecisions, parseActions, parseAttendees, parseAgenda,
  parseKpiSnapshot,
  type BcmManagementReview, type BcmManagementReviewCreate,
} from '../../lib/bcmManagementReviews';

/**
 * BCM Management Reviews — ISO 22301 Clause 9.3.
 *
 * Top management reviews the BCMS at planned intervals. This register surfaces
 * the orphaned /api/v1/bcm/management-reviews endpoints: list, create, and the
 * DRAFT -> IN_PROGRESS -> APPROVED -> ARCHIVED lifecycle, with a detail view of
 * the review inputs, KPI snapshot, decisions and improvement actions.
 */
export function BcmManagementReviewsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [rows, setRows] = useState<BcmManagementReview[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const list = await bcmManagementReviewApi.list();
      const sorted = [...list].sort((a, b) => (b.reviewDate ?? '').localeCompare(a.reviewDate ?? ''));
      setRows(sorted);
      setFailed(false);
      setSelectedId(prev => prev ?? (sorted[0]?.id ?? null));
    } catch (e) {
      setFailed(true);
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل المراجعات' : 'Failed to load reviews'));
    } finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const counts = useMemo(() => ({
    total: rows.length,
    open: rows.filter(r => r.status === 'DRAFT' || r.status === 'IN_PROGRESS').length,
    approved: rows.filter(r => r.status === 'APPROVED').length,
    archived: rows.filter(r => r.status === 'ARCHIVED').length,
  }), [rows]);

  const selected = rows.find(r => r.id === selectedId) ?? null;

  async function advance(r: BcmManagementReview) {
    const to = nextMrStatus(r.status);
    if (!to) return;
    setBusyId(r.id);
    try {
      const updated = await bcmManagementReviewApi.transition(r.id, to);
      setRows(prev => prev.map(x => x.id === updated.id ? updated : x));
      toast.success(isAr ? `${updated.reviewCode} → ${mrStatusLabel(to, true)}` : `${updated.reviewCode} → ${mrStatusLabel(to, false)}`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الانتقال' : 'Transition failed'));
    } finally { setBusyId(null); }
  }

  async function submitCreate(v: BcmManagementReviewCreate): Promise<void> {
    try {
      const created = await bcmManagementReviewApi.create({
        reviewDate: v.reviewDate,
        chairpersonName: v.chairpersonName,
        inputsSummaryEn: v.inputsSummaryEn?.trim() || undefined,
        notesEn: v.notesEn?.trim() || undefined,
      });
      toast.success(isAr ? `تم إنشاء المراجعة ${created.reviewCode}` : `Review ${created.reviewCode} created`);
      setCreateOpen(false);
      setSelectedId(created.id);
      await load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الإنشاء' : 'Create failed'));
    }
  }

  const createFields: FieldDef[] = [
    { kind: 'date', key: 'reviewDate', label: isAr ? 'تاريخ المراجعة *' : 'Review date *', span: 6 },
    { kind: 'text', key: 'chairpersonName', label: isAr ? 'رئيس الجلسة *' : 'Chairperson *', required: true, span: 6 },
    { kind: 'textarea', key: 'inputsSummaryEn', label: isAr ? 'ملخّص المدخلات' : 'Inputs summary', rows: 3, span: 12 },
    { kind: 'textarea', key: 'notesEn', label: isAr ? 'ملاحظات' : 'Notes', rows: 2, span: 12 },
  ];
  const today = new Date().toISOString().slice(0, 10);
  const createInitial = { reviewDate: today, chairpersonName: '', inputsSummaryEn: '', notesEn: '' };

  return (
    <Shell title={isAr ? 'مراجعات الإدارة (BCM)' : 'BCM management reviews'}
      sub={isAr ? 'مراجعة الإدارة العليا لنظام استمرارية الأعمال — أيزو ٢٢٣٠١ بند ٩٫٣' : 'Top-management review of the BCMS — ISO 22301 §9.3'}>
      <PageHeader
        eyebrow={isAr ? 'استمرارية الأعمال · الحوكمة' : 'Business continuity · Governance'}
        title={isAr ? 'مراجعات الإدارة' : 'Management reviews'}
        sub={isAr
          ? 'سجل مراجعات الإدارة لنظام استمرارية الأعمال وفق أيزو ٢٢٣٠١ بند ٩٫٣ — المدخلات والقرارات وإجراءات التحسين عبر دورة حياة معتمدة.'
          : 'BCMS management-review register per ISO 22301 §9.3 — inputs, decisions and improvement actions through an approval lifecycle.'}
        actions={
          <div style={{ display: 'inline-flex', gap: 8 }}>
            <Button variant="ghost" size="sm" Icon={RefreshCw} onClick={() => void load()} disabled={loading}>
              {isAr ? 'تحديث' : 'Refresh'}
            </Button>
            <Button variant="primary" size="sm" Icon={Plus} onClick={() => setCreateOpen(true)}>
              {isAr ? 'مراجعة جديدة' : 'New review'}
            </Button>
          </div>
        }
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={ClipboardList} label={isAr ? 'إجمالي المراجعات' : 'Total reviews'} value={counts.total} tone="primary" to="/bcm" />
        <Kpi Icon={Activity}      label={isAr ? 'مفتوحة' : 'Open'}                   value={counts.open} tone={counts.open > 0 ? 'warning' : 'neutral'} to="/bcm" />
        <Kpi Icon={CheckCircle2}  label={isAr ? 'معتمدة' : 'Approved'}               value={counts.approved} tone="success" to="/bcm" />
        <Kpi Icon={Archive}       label={isAr ? 'مؤرشفة' : 'Archived'}               value={counts.archived} tone="info" to="/bcm" />
      </div>

      <Card title={isAr ? 'السجل' : 'Register'} Icon={ClipboardList}>
        {loading && rows.length === 0
          ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
          : failed && rows.length === 0
            ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'السجل غير متاح حالياً' : 'Register unavailable right now'}</div>
            : rows.length === 0
              ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'لا توجد مراجعات بعد' : 'No reviews yet'}</div>
              : <table className="grc-table">
                  <thead><tr>
                    <th>{isAr ? 'الرمز' : 'Code'}</th>
                    <th>{isAr ? 'التاريخ' : 'Date'}</th>
                    <th>{isAr ? 'رئيس الجلسة' : 'Chairperson'}</th>
                    <th style={{ textAlign: 'center' }}>{isAr ? 'قرارات' : 'Decisions'}</th>
                    <th style={{ textAlign: 'center' }}>{isAr ? 'إجراءات' : 'Actions'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th style={{ textAlign: 'end' }}>{isAr ? '' : ''}</th>
                  </tr></thead>
                  <tbody>
                    {rows.map(r => {
                      const to = nextMrStatus(r.status);
                      const isSel = r.id === selectedId;
                      return (
                        <tr key={r.id} onClick={() => setSelectedId(r.id)}
                          style={{ cursor: 'pointer', background: isSel ? 'var(--primary-50)' : undefined }}>
                          <td className="mono text-2xs" style={{ fontWeight: 600 }}>{r.reviewCode}</td>
                          <td className="text-2xs">{r.reviewDate}</td>
                          <td className="text-2xs">{r.chairpersonName}</td>
                          <td style={{ textAlign: 'center' }}>{parseDecisions(r).length || '—'}</td>
                          <td style={{ textAlign: 'center' }}>{parseActions(r).length || '—'}</td>
                          <td><Badge tone={MR_STATUS_TONE[r.status] ?? 'neutral'} dot>{mrStatusLabel(r.status, isAr)}</Badge></td>
                          <td style={{ textAlign: 'end' }}>
                            {to && (
                              <Button size="sm" variant="secondary" Icon={ArrowRight}
                                disabled={busyId === r.id}
                                onClick={(e) => { e.stopPropagation(); void advance(r); }}>
                                {mrAdvanceLabel(r.status, isAr)}
                              </Button>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>}
      </Card>

      {selected && <ReviewDetail review={selected} isAr={isAr} />}

      <FormModal<BcmManagementReviewCreate>
        open={createOpen}
        title={isAr ? 'مراجعة إدارة جديدة' : 'New management review'}
        fields={createFields}
        initial={createInitial}
        onClose={() => setCreateOpen(false)}
        onSubmit={submitCreate}
        submitLabel={isAr ? 'إنشاء' : 'Create'}
      />
    </Shell>
  );
}

function ReviewDetail({ review, isAr }: { review: BcmManagementReview; isAr: boolean }) {
  const decisions = parseDecisions(review);
  const actions = parseActions(review);
  const attendees = parseAttendees(review);
  const agenda = parseAgenda(review);
  const kpi = parseKpiSnapshot(review);
  const inputs = (isAr ? review.inputsSummaryAr : review.inputsSummaryEn) || review.inputsSummaryEn;
  const notes = (isAr ? review.notesAr : review.notesEn) || review.notesEn;

  return (
    <div style={{ marginTop: 16 }}>
      <Card title={`${review.reviewCode} · ${review.reviewDate}`} Icon={Gavel}
        action={review.approvedBy
          ? <Badge tone="success">{isAr ? 'اعتمدها' : 'Approved by'} {review.approvedBy}</Badge>
          : undefined}>

        {inputs && (
          <Section title={isAr ? 'ملخّص المدخلات' : 'Inputs summary'} Icon={ListChecks}>
            <p style={{ margin: 0, fontSize: 13, lineHeight: 1.6, color: 'var(--ink-2)' }}>{inputs}</p>
          </Section>
        )}

        {kpi && (
          <Section title={isAr ? 'لقطة المؤشرات' : 'KPI snapshot'} Icon={Activity}>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {kpi.avgReadinessScore != null && <KpiChip label={isAr ? 'الجاهزية' : 'Readiness'} value={`${kpi.avgReadinessScore}/100`} tone="info" />}
              {kpi.openGaps != null && <KpiChip label={isAr ? 'فجوات مفتوحة' : 'Open gaps'} value={kpi.openGaps} tone={kpi.openGaps > 0 ? 'warning' : 'success'} />}
              {kpi.criticalGaps != null && <KpiChip label={isAr ? 'فجوات حرجة' : 'Critical gaps'} value={kpi.criticalGaps} tone={kpi.criticalGaps > 0 ? 'danger' : 'success'} />}
              {kpi.exercisesCompleted != null && <KpiChip label={isAr ? 'تمارين' : 'Exercises'} value={kpi.exercisesCompleted} tone="neutral" />}
              {kpi.drTestsLast12mo != null && <KpiChip label={isAr ? 'اختبارات تعافٍ' : 'DR tests'} value={kpi.drTestsLast12mo} tone="neutral" />}
            </div>
          </Section>
        )}

        {decisions.length > 0 && (
          <Section title={isAr ? 'القرارات' : 'Decisions'} Icon={Gavel}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {decisions.map((d, i) => (
                <div key={i} style={{ padding: '8px 10px', borderRadius: 'var(--r-sm)', background: 'var(--surface-2)', border: '1px solid var(--border)' }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>{d.decision}</div>
                  {d.rationale && <div style={{ fontSize: 11.5, color: 'var(--ink-3)', marginTop: 2 }}>{d.rationale}</div>}
                  <div style={{ display: 'flex', gap: 6, marginTop: 6, flexWrap: 'wrap' }}>
                    {d.owner && <Badge tone="info">{d.owner}</Badge>}
                    {d.dueDate && <Badge tone="neutral"><CalendarDays size={10} style={{ marginInlineEnd: 4 }} />{d.dueDate}</Badge>}
                  </div>
                </div>
              ))}
            </div>
          </Section>
        )}

        {actions.length > 0 && (
          <Section title={isAr ? 'إجراءات التحسين' : 'Improvement actions'} Icon={ListChecks}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {actions.map((a, i) => (
                <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12.5, color: 'var(--ink-2)' }}>
                  <ArrowRight size={12} style={{ color: 'var(--ink-4)', flexShrink: 0 }} />
                  <span style={{ flex: 1 }}>{a.action}</span>
                  {a.owner && <Badge tone="info">{a.owner}</Badge>}
                  {a.dueDate && <Badge tone="neutral">{a.dueDate}</Badge>}
                </div>
              ))}
            </div>
          </Section>
        )}

        {attendees.length > 0 && (
          <Section title={isAr ? 'الحضور' : 'Attendees'} Icon={Users}>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {attendees.map((p, i) => (
                <Badge key={i} tone="neutral">{p.name}{p.role ? ` · ${p.role}` : ''}</Badge>
              ))}
            </div>
          </Section>
        )}

        {agenda.length > 0 && (
          <Section title={isAr ? 'جدول الأعمال' : 'Agenda'} Icon={ListChecks}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {agenda.map((g, i) => (
                <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: 'var(--ink-3)' }}>
                  <span style={{ width: 18, height: 18, borderRadius: '50%', background: 'var(--surface-2)', color: 'var(--ink-3)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, fontWeight: 700, flexShrink: 0 }}>{i + 1}</span>
                  <span style={{ flex: 1, color: 'var(--ink-2)' }}>{g.item}</span>
                  {g.presenter && <span style={{ fontSize: 11 }}>{g.presenter}</span>}
                  {g.minutes != null && <span className="mono text-2xs">{g.minutes}m</span>}
                </div>
              ))}
            </div>
          </Section>
        )}

        {notes && (
          <Section title={isAr ? 'ملاحظات' : 'Notes'} Icon={ListChecks}>
            <p style={{ margin: 0, fontSize: 12.5, lineHeight: 1.6, color: 'var(--ink-3)' }}>{notes}</p>
          </Section>
        )}
      </Card>
    </div>
  );
}

function Section({ title, Icon, children }: { title: string; Icon: typeof Gavel; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
        <Icon size={13} style={{ color: 'var(--ink-4)' }} />
        <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{title}</span>
      </div>
      {children}
    </div>
  );
}

function KpiChip({ label, value, tone }: { label: string; value: string | number; tone: 'info' | 'warning' | 'danger' | 'success' | 'neutral' }) {
  return (
    <div style={{ padding: '6px 10px', borderRadius: 'var(--r-sm)', background: 'var(--surface-2)', border: '1px solid var(--border)' }}>
      <div style={{ fontSize: 10, color: 'var(--ink-4)', textTransform: 'uppercase', letterSpacing: '0.03em' }}>{label}</div>
      <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--ink-1)' }}>
        <Badge tone={tone} dot>{value}</Badge>
      </div>
    </div>
  );
}

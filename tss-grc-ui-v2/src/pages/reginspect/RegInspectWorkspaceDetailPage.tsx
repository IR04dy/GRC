import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  FileText, ArrowLeft, ChevronRight, Plus, Send, Lock,
  CheckCircle2, AlertTriangle, Clock,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import {
  reginspectApi,
  INSPECTION_STATUS_LABEL, INSPECTION_STATUS_TONE, REGULATOR_LABEL,
  DR_STATUS_LABEL, DR_STATUS_TONE, UPDATE_TYPE_LABEL,
  nextAllowed,
  type InspectionResponse, type DocumentRequestResponse,
  type InspectionUpdateResponse, type InspectionStatus,
} from '../../lib/reginspect';

/**
 * Workspace detail — /reginspect/inspections/:id.
 *
 * The day-to-day workbench. Status header + state-machine action
 * panel. KPIs for DRs. Three sections: Document Requests (inbox
 * with inline create + per-row submit), Timeline (chronological
 * events), Metadata (scope + dates + inspector).
 */
export function RegInspectWorkspaceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { lang } = useI18n();
  const inspectionId = id ? parseInt(id, 10) : 0;

  const [insp, setInsp] = useState<InspectionResponse | null>(null);
  const [drs, setDrs] = useState<DocumentRequestResponse[]>([]);
  const [timeline, setTimeline] = useState<InspectionUpdateResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  // Inline new-DR form.
  const [showNewDr, setShowNewDr] = useState(false);
  const [drTitleEn, setDrTitleEn] = useState('');
  const [drTitleAr, setDrTitleAr] = useState('');
  const [drDescEn, setDrDescEn] = useState('');
  const [drDescAr, setDrDescAr] = useState('');
  const [drDueDate, setDrDueDate] = useState('');

  // Submit DR modal state.
  const [submitDrId, setSubmitDrId] = useState<number | null>(null);
  const [submitEvidence, setSubmitEvidence] = useState('');
  const [submitNotesEn, setSubmitNotesEn] = useState('');
  const [submitNotesAr, setSubmitNotesAr] = useState('');

  const reload = async () => {
    if (!inspectionId) return;
    setLoading(true); setError(null);
    try {
      const [i, drList, tl] = await Promise.all([
        reginspectApi.getInspection(inspectionId),
        reginspectApi.listDr(inspectionId),
        reginspectApi.timeline(inspectionId),
      ]);
      setInsp(i);
      setDrs(drList);
      setTimeline(tl);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل الورشة' : 'Failed to load workspace'));
    } finally { setLoading(false); }
  };

  useEffect(() => { reload(); /* eslint-disable-next-line */ }, [inspectionId]);

  const editable = insp != null && insp.status !== 'CLOSED';

  const onTransition = async (to: InspectionStatus) => {
    if (!insp) return;
    const confirmMsg = to === 'CLOSED'
      ? (lang === 'ar'
          ? 'هل أنت متأكّد من إغلاق الورشة؟ لن يكون قابلاً للتعديل بعد ذلك.'
          : 'Close this inspection? It will become read-only.')
      : (lang === 'ar'
          ? `الانتقال إلى ${INSPECTION_STATUS_LABEL[to].ar}؟`
          : `Move to ${INSPECTION_STATUS_LABEL[to].en}?`);
    if (!confirm(confirmMsg)) return;
    setPending(true);
    try {
      await reginspectApi.transition(insp.id, { toStatus: to });
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تنفيذ الإجراء' : 'Transition failed'));
    } finally { setPending(false); }
  };

  const onCreateDr = async () => {
    if (!insp || !drTitleEn.trim() || !drTitleAr.trim() || !drDueDate) return;
    setPending(true);
    try {
      await reginspectApi.createDr(insp.id, {
        titleEn: drTitleEn.trim(),
        titleAr: drTitleAr.trim(),
        descriptionEn: drDescEn || undefined,
        descriptionAr: drDescAr || undefined,
        dueDate: drDueDate,
      });
      setShowNewDr(false);
      setDrTitleEn(''); setDrTitleAr(''); setDrDescEn('');
      setDrDescAr(''); setDrDueDate('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إنشاء الطلب' : 'Failed to create DR'));
    } finally { setPending(false); }
  };

  const onSubmitDr = async () => {
    if (!submitDrId || !submitEvidence.trim()) return;
    setPending(true);
    try {
      await reginspectApi.submitDr(submitDrId, {
        evidenceReference: submitEvidence.trim(),
        submissionNotesEn: submitNotesEn || undefined,
        submissionNotesAr: submitNotesAr || undefined,
      });
      setSubmitDrId(null);
      setSubmitEvidence(''); setSubmitNotesEn(''); setSubmitNotesAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر التقديم' : 'Failed to submit DR'));
    } finally { setPending(false); }
  };

  if (loading) {
    return (
      <Shell title="—" sub="—">
        <div style={{ padding: 24, fontSize: 13, opacity: 0.7 }}>
          {lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}
        </div>
      </Shell>
    );
  }

  if (!insp) {
    return (
      <Shell title="—" sub="—">
        <div style={{ padding: 24, color: 'var(--danger)' }}>
          {error ?? (lang === 'ar' ? 'الورشة غير موجودة' : 'Workspace not found')}
        </div>
      </Shell>
    );
  }

  const title = lang === 'ar' ? insp.titleAr : insp.titleEn;
  const next = nextAllowed(insp.status);

  return (
    <Shell title={title} sub={insp.externalReference}>
      <div style={{ marginBottom: 8 }}>
        <Link to="/reginspect/inspections"
              style={{ color: 'var(--ink-3)', textDecoration: 'none', fontSize: 12 }}>
          <ArrowLeft size={12} style={{ verticalAlign: 'middle', marginRight: 4 }}/>
          {lang === 'ar' ? 'كل الورش' : 'All inspections'}
        </Link>
      </div>

      <PageHeader
        eyebrow={`${REGULATOR_LABEL[insp.regulator][lang === 'ar' ? 'ar' : 'en']} · ${insp.externalReference}`}
        title={title}
        sub={lang === 'ar' ? insp.scopeAr || '—' : insp.scopeEn || '—'}
        actions={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center',
                        flexWrap: 'wrap' }}>
            <Badge tone={INSPECTION_STATUS_TONE[insp.status]}>
              {INSPECTION_STATUS_LABEL[insp.status][lang === 'ar' ? 'ar' : 'en']}
            </Badge>
            {next.map(to => (
              <Button key={to} variant={to === 'CLOSED' ? 'secondary' : 'primary'}
                Icon={to === 'CLOSED' ? Lock : ChevronRight}
                disabled={pending}
                onClick={() => onTransition(to)}>
                → {INSPECTION_STATUS_LABEL[to][lang === 'ar' ? 'ar' : 'en']}
              </Button>
            ))}
          </div>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      {/* KPIs */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 12, marginBottom: 16,
      }}>
        <Kpi Icon={FileText} tone="warning"
          label={lang === 'ar' ? 'طلبات مفتوحة' : 'Open DRs'}
          value={insp.openDrCount ?? 0} />
        <Kpi Icon={AlertTriangle} tone="danger"
          label={lang === 'ar' ? 'طلبات متأخّرة' : 'Overdue'}
          value={insp.overdueDrCount ?? 0} />
        <Kpi Icon={CheckCircle2} tone="success"
          label={lang === 'ar' ? 'طلبات مُقدَّمة' : 'Submitted'}
          value={insp.submittedDrCount ?? 0} />
        <Kpi Icon={Clock} tone="info"
          label={lang === 'ar' ? 'تاريخ الإغلاق' : 'Exit date'}
          value={insp.exitDate
            ? new Date(insp.exitDate).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')
            : '—'} />
      </div>

      {/* Document Requests */}
      <Card padded
        title={lang === 'ar' ? 'طلبات الوثائق' : 'Document Requests'}
        action={editable && (
          <Button onClick={() => setShowNewDr(s => !s)}
            variant="primary" size="sm" Icon={Plus}>
            {showNewDr
              ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
              : (lang === 'ar' ? 'طلب جديد' : 'New DR')}
          </Button>
        )}>

        {showNewDr && (
          <div style={{ marginBottom: 12, padding: 12,
                        border: '1px solid var(--border)',
                        borderRadius: 6, background: 'var(--surface-hover)' }}>
            <div style={{ display: 'grid',
                          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                          gap: 8 }}>
              <input type="text" placeholder={lang === 'ar' ? 'العنوان (إنجليزي)' : 'Title (EN)'}
                value={drTitleEn} onChange={e => setDrTitleEn(e.target.value)}
                style={{ padding: 6, border: '1px solid var(--border)',
                         borderRadius: 4, fontSize: 13 }} />
              <input type="text" dir="rtl"
                placeholder={lang === 'ar' ? 'العنوان (عربي)' : 'Title (AR)'}
                value={drTitleAr} onChange={e => setDrTitleAr(e.target.value)}
                style={{ padding: 6, border: '1px solid var(--border)',
                         borderRadius: 4, fontSize: 13 }} />
              <input type="date"
                value={drDueDate} onChange={e => setDrDueDate(e.target.value)}
                style={{ padding: 6, border: '1px solid var(--border)',
                         borderRadius: 4, fontSize: 13 }} />
            </div>
            <div style={{ display: 'grid',
                          gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                          gap: 8, marginTop: 8 }}>
              <textarea rows={2} placeholder={lang === 'ar' ? 'الوصف (إنجليزي)' : 'Description (EN)'}
                value={drDescEn} onChange={e => setDrDescEn(e.target.value)}
                style={{ padding: 6, border: '1px solid var(--border)',
                         borderRadius: 4, fontSize: 13, fontFamily: 'inherit' }} />
              <textarea rows={2} dir="rtl"
                placeholder={lang === 'ar' ? 'الوصف (عربي)' : 'Description (AR)'}
                value={drDescAr} onChange={e => setDrDescAr(e.target.value)}
                style={{ padding: 6, border: '1px solid var(--border)',
                         borderRadius: 4, fontSize: 13, fontFamily: 'inherit' }} />
            </div>
            <div style={{ marginTop: 8 }}>
              <Button onClick={onCreateDr}
                disabled={pending || !drTitleEn.trim() || !drTitleAr.trim() || !drDueDate}
                variant="primary" size="sm">
                {lang === 'ar' ? 'فتح الطلب' : 'Create DR'}
              </Button>
            </div>
          </div>
        )}

        {drs.length === 0 ? (
          <div style={{ padding: 24, textAlign: 'center', fontSize: 13, opacity: 0.7 }}>
            {lang === 'ar' ? 'لا توجد طلبات وثائق بعد.' : 'No document requests yet.'}
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {drs.map(dr => (
              <div key={dr.id} style={{
                border: '1px solid var(--border)',
                borderRadius: 6, padding: 12,
                background: dr.overdue ? 'rgba(220,38,38,0.05)' : 'var(--card-bg)',
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8,
                              marginBottom: 6, flexWrap: 'wrap' }}>
                  <code style={{ fontSize: 11, opacity: 0.7 }}>{dr.reference}</code>
                  <Badge tone={DR_STATUS_TONE[dr.status]}>
                    {DR_STATUS_LABEL[dr.status][lang === 'ar' ? 'ar' : 'en']}
                  </Badge>
                  {dr.overdue && (
                    <Badge tone="danger">
                      {lang === 'ar' ? 'متأخّر' : 'OVERDUE'}
                    </Badge>
                  )}
                  <span style={{ marginInlineStart: 'auto', fontSize: 12, opacity: 0.7 }}>
                    {lang === 'ar' ? 'تاريخ الاستحقاق: ' : 'Due: '}
                    {dr.dueDate
                      ? new Date(dr.dueDate)
                          .toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')
                      : '—'}
                  </span>
                </div>
                <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4 }}>
                  {lang === 'ar' ? dr.titleAr : dr.titleEn}
                </div>
                {(lang === 'ar' ? dr.descriptionAr : dr.descriptionEn) && (
                  <div style={{ fontSize: 12, opacity: 0.8, marginBottom: 8,
                                whiteSpace: 'pre-wrap' }}>
                    {lang === 'ar' ? dr.descriptionAr : dr.descriptionEn}
                  </div>
                )}
                {dr.status === 'SUBMITTED' && (
                  <div style={{ fontSize: 11, opacity: 0.7 }}>
                    {lang === 'ar' ? 'الدليل: ' : 'Evidence: '}
                    <code>{dr.evidenceReference}</code>
                  </div>
                )}
                {editable && dr.status !== 'SUBMITTED' && (
                  <div style={{ marginTop: 8 }}>
                    <Button onClick={() => setSubmitDrId(dr.id)}
                      variant="primary" size="sm" Icon={Send}>
                      {lang === 'ar' ? 'تقديم الطلب' : 'Submit'}
                    </Button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </Card>

      {/* Submit DR modal */}
      {submitDrId != null && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 1000,
        }} onClick={() => setSubmitDrId(null)}>
          <div onClick={e => e.stopPropagation()}
               style={{ background: 'var(--card-bg)', borderRadius: 8,
                        padding: 20, width: 480, maxWidth: '90vw' }}>
            <h3 style={{ marginTop: 0, fontSize: 16 }}>
              {lang === 'ar' ? 'تقديم طلب الوثائق' : 'Submit Document Request'}
            </h3>
            <label style={{ display: 'flex', flexDirection: 'column', gap: 4,
                            marginBottom: 8 }}>
              <span style={{ fontSize: 12, fontWeight: 600 }}>
                {lang === 'ar' ? 'مرجع الدليل (مطلوب)' : 'Evidence reference (required)'}
              </span>
              <input type="text" maxLength={512}
                value={submitEvidence}
                onChange={e => setSubmitEvidence(e.target.value)}
                placeholder={lang === 'ar'
                  ? 'مثال: شاريبوينت/INSP-2026/Q2/DR-001…'
                  : 'e.g. SharePoint/INSP-2026/Q2/DR-001…'}
                style={{ padding: 6, border: '1px solid var(--border)',
                         borderRadius: 4, fontSize: 13 }} />
            </label>
            <label style={{ display: 'flex', flexDirection: 'column', gap: 4,
                            marginBottom: 8 }}>
              <span style={{ fontSize: 12, fontWeight: 600 }}>
                {lang === 'ar' ? 'ملاحظات التقديم (إنجليزي)' : 'Submission notes (EN)'}
              </span>
              <textarea rows={3} maxLength={4000}
                value={submitNotesEn} onChange={e => setSubmitNotesEn(e.target.value)}
                style={{ padding: 6, border: '1px solid var(--border)',
                         borderRadius: 4, fontSize: 13, fontFamily: 'inherit' }} />
            </label>
            <label style={{ display: 'flex', flexDirection: 'column', gap: 4,
                            marginBottom: 12 }}>
              <span style={{ fontSize: 12, fontWeight: 600 }}>
                {lang === 'ar' ? 'ملاحظات التقديم (عربي)' : 'Submission notes (AR)'}
              </span>
              <textarea rows={3} maxLength={4000} dir="rtl"
                value={submitNotesAr} onChange={e => setSubmitNotesAr(e.target.value)}
                style={{ padding: 6, border: '1px solid var(--border)',
                         borderRadius: 4, fontSize: 13, fontFamily: 'inherit' }} />
            </label>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <Button onClick={() => setSubmitDrId(null)} variant="secondary">
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
              <Button onClick={onSubmitDr}
                disabled={pending || !submitEvidence.trim()}
                variant="primary" Icon={Send}>
                {lang === 'ar' ? 'تقديم' : 'Submit'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Timeline */}
      <div style={{ marginTop: 16 }}>
        <Card padded
          title={lang === 'ar' ? 'سجل الأحداث' : 'Timeline'}>
          {timeline.length === 0 ? (
            <div style={{ padding: 16, textAlign: 'center', fontSize: 13,
                          opacity: 0.7 }}>
              {lang === 'ar' ? 'لا توجد أحداث.' : 'No events yet.'}
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {timeline.map(u => (
                <div key={u.id} style={{
                  display: 'grid',
                  gridTemplateColumns: '170px 140px 1fr',
                  gap: 12,
                  padding: 8,
                  borderInlineStart: '3px solid var(--primary)',
                  background: 'var(--surface-hover)',
                  borderRadius: 4,
                }}>
                  <span style={{ fontSize: 12, opacity: 0.7 }}>
                    {new Date(u.occurredAt)
                      .toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
                  </span>
                  <Badge tone="info">
                    {UPDATE_TYPE_LABEL[u.updateType][lang === 'ar' ? 'ar' : 'en']}
                  </Badge>
                  <span style={{ fontSize: 13 }}>
                    {u.statusAfter && (
                      <Badge tone={INSPECTION_STATUS_TONE[u.statusAfter]}
                             className="m-r-6">
                        → {INSPECTION_STATUS_LABEL[u.statusAfter][lang === 'ar' ? 'ar' : 'en']}
                      </Badge>
                    )}
                    {lang === 'ar' ? u.noteAr || u.noteEn : u.noteEn || u.noteAr}
                  </span>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>
    </Shell>
  );
}

import { useEffect, useMemo, useState, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  AlertOctagon, AlertTriangle, CheckCircle2, FileQuestion, GitBranch,
  ChevronLeft, FileCheck2, X, Wrench,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { Modal } from '../../components/ui/Modal';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { tlabel } from '../../lib/labels';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { ApiError } from '../../lib/api';
import {
  getAssessmentDetail, publishAssessment, createCapaForFinding,
  filterFindings,
  type MtpdAssessment, type MtpdFinding,
  type FindingType, type Severity, type DependencyStrength,
  FINDING_TYPE_LABELS, SEVERITY_LABELS, DEPENDENCY_STRENGTH_LABELS,
  findingTypeTone, severityTone,
} from '../../lib/mtpdInheritance';

const SEVERITIES: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const FINDING_TYPES: FindingType[] = ['BREACH', 'WARNING', 'OK', 'NO_ATTESTATION'];
const STRENGTHS: DependencyStrength[] = ['TIGHT', 'LOOSE', 'TRANSIENT'];

/**
 * Slice 10-E — MTPD Inheritance assessment detail.
 *
 * <p>Three sections:
 * <ul>
 *   <li>KPI strip: total processes / breaches / warnings / no-attestation
 *       / OK + Publish button when still in draft.</li>
 *   <li>Filter chips for severity / finding-type / dependency-strength
 *       — OK rows hidden by default.</li>
 *   <li>Findings table sorted by severity. Row click opens a detail
 *       drawer with the full recommendation text and a "Create CAPA"
 *       button gated on no existing capa_task_id.</li>
 * </ul>
 */
export function MtpdInheritanceDetailPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const navigate = useNavigate();
  const params = useParams();
  const toast = useToast();
  const dialog = useDialog();

  const assessmentId = useMemo(() => {
    const v = params.assessmentId;
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
  }, [params.assessmentId]);

  const [assessment, setAssessment] = useState<MtpdAssessment | null>(null);
  const [findings, setFindings] = useState<MtpdFinding[]>([]);
  const [loading, setLoading] = useState(false);
  const [drawer, setDrawer] = useState<MtpdFinding | null>(null);

  // Default filter set — hide OK rows (they're the boring majority).
  const [severityFilter, setSeverityFilter] = useState<Set<Severity>>(new Set());
  const [typeFilter, setTypeFilter] = useState<Set<FindingType>>(
    new Set<FindingType>(['BREACH', 'WARNING', 'NO_ATTESTATION'])
  );
  const [strengthFilter, setStrengthFilter] = useState<Set<DependencyStrength>>(new Set());

  const reload = useCallback(async () => {
    if (assessmentId == null) return;
    setLoading(true);
    try {
      const detail = await getAssessmentDetail(assessmentId);
      setAssessment(detail.assessment);
      setFindings(Array.isArray(detail.findings) ? detail.findings : []);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل التقييم' : 'Failed to load assessment'));
    } finally {
      setLoading(false);
    }
  }, [assessmentId, isAr, toast]);

  useEffect(() => { void reload(); }, [reload]);

  // ─── Publish ─────────────────────────────────────────────────────────
  async function handlePublish() {
    if (!assessment) return;
    const ok = await dialog.confirm({
      title: isAr ? 'نشر هذا التقييم للجهات الرقابية؟' : 'Publish this assessment for audit?',
      body: isAr
        ? 'النشر يُتيح للمراجع الداخلي ولجنة المراجعة وفِرَق الرقابة الاطلاع على هذا التقييم. لا يمكن إلغاء النشر بعد ذلك.'
        : 'Publishing makes this assessment visible to internal audit, the Audit Committee, and regulator-tier reviewers. Cannot be reverted.',
    });
    if (!ok) return;
    try {
      await publishAssessment(assessment.id);
      toast.success(isAr ? 'تم النشر' : 'Published');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل النشر' : 'Publish failed'));
    }
  }

  // ─── CAPA creation ──────────────────────────────────────────────────
  async function handleCreateCapa(f: MtpdFinding) {
    if (f.capaTaskId != null) {
      toast.warning(isAr ? 'يوجد إجراء تصحيحي مرتبط بالفعل' : 'CAPA already linked');
      return;
    }
    const owner = await dialog.prompt({
      title: isAr ? 'المسؤول عن الإجراء التصحيحي' : 'CAPA owner',
      placeholder: isAr ? 'مثال: مدير استمرارية الأعمال' : 'e.g. BCM Manager',
      initial: 'BCM Manager',
      required: true,
    });
    if (owner === null) return;
    try {
      const res = await createCapaForFinding(f.id, { owner });
      toast.success(isAr ? `تم إنشاء الإجراء ${res.capaExternalId}`
                         : `CAPA ${res.capaExternalId} created`);
      await reload();
      // Refresh drawer if open.
      setDrawer(prev => prev && prev.id === f.id ? { ...prev, capaTaskId: res.capaTaskId, capaExternalId: res.capaExternalId, capaCreatedAt: res.capaCreatedAt } : prev);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل إنشاء الإجراء' : 'CAPA creation failed'));
    }
  }

  // ─── Filter helpers ─────────────────────────────────────────────────
  function toggle<T>(set: Set<T>, key: T): Set<T> {
    const next = new Set(set);
    if (next.has(key)) next.delete(key); else next.add(key);
    return next;
  }

  const visibleFindings = useMemo(
    () => filterFindings(findings, severityFilter, typeFilter, strengthFilter),
    [findings, severityFilter, typeFilter, strengthFilter],
  );

  if (assessmentId == null) {
    return (
      <Shell>
        <PageHeader title={isAr ? 'تقييم غير صالح' : 'Invalid assessment'} />
      </Shell>
    );
  }

  return (
    <Shell>
      <PageHeader
        eyebrow={isAr ? 'وراثة المهلة القصوى' : 'MTPD Inheritance'}
        title={assessment?.assessmentCode ?? (isAr ? 'جارٍ التحميل…' : 'Loading…')}
        sub={assessment
          ? (isAr
              ? `${assessment.cycleYear}${assessment.cycleQuarter ? ' / ' + assessment.cycleQuarter : ''} — حُسب في ${new Date(assessment.computedAt).toLocaleString('ar-SA')}`
              : `${assessment.cycleYear}${assessment.cycleQuarter ? ' / ' + assessment.cycleQuarter : ''} — computed ${new Date(assessment.computedAt).toLocaleString('en-GB')}`)
          : undefined}
        actions={
          <div className="cluster">
            <Button variant="secondary" Icon={ChevronLeft}
                    onClick={() => navigate('/bcm/mtpd-inheritance')}>
              {isAr ? 'رجوع' : 'Back'}
            </Button>
            {assessment && !assessment.isPublished && (
              <Button variant="primary" Icon={FileCheck2}
                      onClick={() => void handlePublish()}>
                {isAr ? 'نشر للجهات الرقابية' : 'Publish for audit'}
              </Button>
            )}
          </div>
        }
      />

      {assessment && (
        <div className="kpi-grid-sm" style={{ marginBottom: 'var(--space-md)' }}>
          <Kpi
            Icon={GitBranch}
            label={isAr ? 'العمليات المفحوصة' : 'Processes scanned'}
            value={String(assessment.totalProcesses)}
          />
          <Kpi
            Icon={AlertOctagon}
            label={isAr ? 'خروقات' : 'Breaches'}
            value={String(assessment.totalBreaches)}
            tone="danger"
          />
          <Kpi
            Icon={AlertTriangle}
            label={isAr ? 'تحذيرات' : 'Warnings'}
            value={String(assessment.totalWarnings)}
            tone="warning"
          />
          <Kpi
            Icon={FileQuestion}
            label={isAr ? 'فجوات إقرار' : 'No-attestation gaps'}
            value={String(assessment.totalNoAttestation)}
            tone="neutral"
          />
          <Kpi
            Icon={CheckCircle2}
            label={isAr ? 'سليمة' : 'OK'}
            value={String(assessment.totalOk)}
            tone="success"
          />
        </div>
      )}

      {assessment?.isPublished && (
        <Card>
          <div style={{ color: 'var(--info-fg)', display: 'flex', alignItems: 'center', gap: 8 }}>
            <FileCheck2 size={16} />
            {isAr
              ? `مَنشورة للجهات الرقابية في ${assessment.publishedAt ? new Date(assessment.publishedAt).toLocaleString('ar-SA') : '—'}`
              : `Published for audit on ${assessment.publishedAt ? new Date(assessment.publishedAt).toLocaleString('en-GB') : '—'}`}
          </div>
        </Card>
      )}

      {/* Filter chips */}
      <Card title={isAr ? 'تصفية' : 'Filters'}>
        <div className="stack" style={{ gap: 12 }}>
          <div>
            <strong style={{ fontSize: 12, color: 'var(--ink-2)' }}>
              {isAr ? 'الخطورة' : 'Severity'}
            </strong>
            <div className="cluster" style={{ gap: 6, marginTop: 6 }}>
              {SEVERITIES.map(s => (
                <button
                  key={s}
                  className={`chip ${severityFilter.has(s) ? 'chip-active' : ''}`}
                  onClick={() => setSeverityFilter(prev => toggle(prev, s))}>
                  <Badge tone={severityTone(s)}>
                    {tlabel(SEVERITY_LABELS, s, isAr)}
                  </Badge>
                </button>
              ))}
            </div>
          </div>
          <div>
            <strong style={{ fontSize: 12, color: 'var(--ink-2)' }}>
              {isAr ? 'نوع النتيجة' : 'Finding type'}
            </strong>
            <div className="cluster" style={{ gap: 6, marginTop: 6 }}>
              {FINDING_TYPES.map(t => (
                <button
                  key={t}
                  className={`chip ${typeFilter.has(t) ? 'chip-active' : ''}`}
                  onClick={() => setTypeFilter(prev => toggle(prev, t))}>
                  <Badge tone={findingTypeTone(t)}>
                    {tlabel(FINDING_TYPE_LABELS, t, isAr)}
                  </Badge>
                </button>
              ))}
            </div>
          </div>
          <div>
            <strong style={{ fontSize: 12, color: 'var(--ink-2)' }}>
              {isAr ? 'قوة الاعتماد' : 'Dependency strength'}
            </strong>
            <div className="cluster" style={{ gap: 6, marginTop: 6 }}>
              {STRENGTHS.map(s => (
                <button
                  key={s}
                  className={`chip ${strengthFilter.has(s) ? 'chip-active' : ''}`}
                  onClick={() => setStrengthFilter(prev => toggle(prev, s))}>
                  {tlabel(DEPENDENCY_STRENGTH_LABELS, s, isAr)}
                </button>
              ))}
            </div>
          </div>
        </div>
      </Card>

      <Card
        title={isAr ? 'النتائج' : 'Findings'}
        Icon={AlertOctagon}
      >
        {loading && findings.length === 0 && (
          <p style={{ color: 'var(--ink-3)' }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</p>
        )}
        {!loading && findings.length === 0 && (
          <InlineEmpty
            text={isAr ? 'لم تُسجَّل نتائج في هذه الدورة.' : 'No findings recorded for this cycle.'}
          />
        )}
        {!loading && findings.length > 0 && visibleFindings.length === 0 && (
          <InlineEmpty
            text={isAr ? 'لا توجد نتائج مطابقة لمرشّحات التصفية الحالية.' : 'No findings match the active filters.'}
          />
        )}
        {visibleFindings.length > 0 && (
          <div className="table-responsive">
            <table className="table">
              <thead>
                <tr>
                  <th>{isAr ? 'الخطورة' : 'Severity'}</th>
                  <th>{isAr ? 'النتيجة' : 'Finding'}</th>
                  <th>{isAr ? 'العملية' : 'Process'}</th>
                  <th>{isAr ? 'المورّد' : 'Vendor'}</th>
                  <th>{isAr ? 'مهلة العملية' : 'Process MTPD'}</th>
                  <th>{isAr ? 'مهلة المورّد' : 'Vendor MTPD'}</th>
                  <th>{isAr ? 'الفرق' : 'Variance'}</th>
                  <th>{isAr ? 'القوة' : 'Strength'}</th>
                  <th>{isAr ? 'إجراء تصحيحي' : 'CAPA'}</th>
                </tr>
              </thead>
              <tbody>
                {visibleFindings.map(f => (
                  <tr key={f.id}
                      style={{ cursor: 'pointer' }}
                      onClick={() => setDrawer(f)}>
                    <td>
                      <Badge tone={severityTone(f.severity)}>
                        {tlabel(SEVERITY_LABELS, f.severity, isAr)}
                      </Badge>
                    </td>
                    <td>
                      <Badge tone={findingTypeTone(f.findingType)}>
                        {tlabel(FINDING_TYPE_LABELS, f.findingType, isAr)}
                      </Badge>
                    </td>
                    <td>
                      <strong>
                        {isAr && f.businessProcessNameAr ? f.businessProcessNameAr : f.businessProcessNameEn}
                      </strong>
                    </td>
                    <td>{f.vendorName}</td>
                    <td>{f.processMtpdHours != null ? `${f.processMtpdHours}${isAr ? ' س' : 'h'}` : '—'}</td>
                    <td>{f.vendorAttestedMtpdHours != null ? `${f.vendorAttestedMtpdHours}${isAr ? ' س' : 'h'}` : '—'}</td>
                    <td style={{ color: (f.varianceHours ?? 0) > 0 ? 'var(--danger-fg)' : 'inherit', fontWeight: (f.varianceHours ?? 0) > 0 ? 600 : 400 }}>
                      {f.varianceHours == null ? '—'
                        : (f.varianceHours > 0 ? `+${f.varianceHours}` : String(f.varianceHours)) + (isAr ? ' س' : 'h')}
                    </td>
                    <td>{tlabel(DEPENDENCY_STRENGTH_LABELS, f.dependencyStrength, isAr)}</td>
                    <td>
                      {f.capaExternalId
                        ? <Badge tone="info">{f.capaExternalId}</Badge>
                        : <span style={{ color: 'var(--ink-3)' }}>—</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Detail drawer */}
      {drawer && (
        <Modal
          open={!!drawer}
          title={`${tlabel(FINDING_TYPE_LABELS, drawer.findingType, isAr)} — ${isAr && drawer.businessProcessNameAr ? drawer.businessProcessNameAr : drawer.businessProcessNameEn} ↔ ${drawer.vendorName}`}
          onClose={() => setDrawer(null)}
        >
          <div className="stack" style={{ gap: 12 }}>
            <div className="cluster" style={{ gap: 8 }}>
              <Badge tone={severityTone(drawer.severity)}>
                {tlabel(SEVERITY_LABELS, drawer.severity, isAr)}
              </Badge>
              <Badge tone={findingTypeTone(drawer.findingType)}>
                {tlabel(FINDING_TYPE_LABELS, drawer.findingType, isAr)}
              </Badge>
              <Badge tone="neutral">
                {tlabel(DEPENDENCY_STRENGTH_LABELS, drawer.dependencyStrength, isAr)}
              </Badge>
            </div>
            <table className="table">
              <tbody>
                <tr>
                  <th>{isAr ? 'مهلة العملية' : 'Process MTPD'}</th>
                  <td>{drawer.processMtpdHours != null ? `${drawer.processMtpdHours}${isAr ? ' س' : 'h'}` : '—'}</td>
                </tr>
                <tr>
                  <th>{isAr ? 'مهلة المورّد المُقَرّة' : 'Vendor attested MTPD'}</th>
                  <td>{drawer.vendorAttestedMtpdHours != null ? `${drawer.vendorAttestedMtpdHours}${isAr ? ' س' : 'h'}` : '—'}</td>
                </tr>
                <tr>
                  <th>{isAr ? 'الفرق' : 'Variance'}</th>
                  <td style={{ color: (drawer.varianceHours ?? 0) > 0 ? 'var(--danger-fg)' : 'inherit', fontWeight: 600 }}>
                    {drawer.varianceHours == null ? '—'
                      : (drawer.varianceHours > 0 ? `+${drawer.varianceHours}` : String(drawer.varianceHours)) + (isAr ? ' س' : 'h')}
                  </td>
                </tr>
                {drawer.sourceCampaignCode && (
                  <tr>
                    <th>{isAr ? 'مصدر الإقرار' : 'Source attestation'}</th>
                    <td>{drawer.sourceCampaignCode}
                      {drawer.sourceSubmittedAt
                        ? ` — ${new Date(drawer.sourceSubmittedAt).toLocaleDateString(isAr ? 'ar-SA' : 'en-GB')}`
                        : ''}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
            {drawer.recommendationEn && (
              <div>
                <strong style={{ fontSize: 13, color: 'var(--ink-2)' }}>
                  {isAr ? 'التوصية' : 'Recommendation'}
                </strong>
                <p style={{ marginTop: 4, fontSize: 13, lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>
                  {isAr && drawer.recommendationAr ? drawer.recommendationAr : drawer.recommendationEn}
                </p>
              </div>
            )}
            {drawer.capaExternalId && (
              <div style={{ background: 'var(--info-bg)', padding: 10, borderRadius: 6 }}>
                {isAr
                  ? `إجراء تصحيحي مرتبط: ${drawer.capaExternalId}${drawer.capaCreatedAt ? ' — ' + new Date(drawer.capaCreatedAt).toLocaleString('ar-SA') : ''}`
                  : `Linked CAPA: ${drawer.capaExternalId}${drawer.capaCreatedAt ? ' — ' + new Date(drawer.capaCreatedAt).toLocaleString('en-GB') : ''}`}
              </div>
            )}
            <div className="cluster" style={{ justifyContent: 'flex-end', gap: 8 }}>
              <Button variant="secondary" Icon={X} onClick={() => setDrawer(null)}>
                {isAr ? 'إغلاق' : 'Close'}
              </Button>
              {!drawer.capaTaskId && (
                <Button variant="primary" Icon={Wrench} onClick={() => void handleCreateCapa(drawer)}>
                  {isAr ? 'إنشاء إجراء تصحيحي' : 'Create CAPA'}
                </Button>
              )}
            </div>
          </div>
        </Modal>
      )}
    </Shell>
  );
}

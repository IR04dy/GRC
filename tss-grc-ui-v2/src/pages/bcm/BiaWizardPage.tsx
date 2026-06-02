import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardCheck, ChevronLeft, ChevronRight, FileCheck2, Cog, AlertOctagon, Wrench } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { tlabel } from '../../lib/labels';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listBiaCatalog, listBiaAssessments, biaStats,
  getBiaAssessment, createBiaAssessment, patchBiaAssessment, transitionBiaAssessment,
  calculateBiaCriticality,
  CRITICALITY_LABELS, DIMENSION_LABELS, criticalityTone, biaStatusTone,
  parseTimeBandMatrix, stringifyTimeBandMatrix,
  type BcmBiaAssessment,
} from '../../lib/biaAssessments';
import { generatePlanFromBia } from '../../lib/bcpPlans';

/**
 * BIA Wizard + Register — independent audit Tier-1 #3.
 *
 * <p>Two modes on one route:
 * <ul>
 *   <li><b>Register view</b> — list of BIAs with status / criticality badges and
 *       quick actions (open, approve, generate BCP, generate DRP).</li>
 *   <li><b>Wizard view</b> — 4-step assessment wizard for one BIA:
 *     <ol>
 *       <li>Scope (service/process, owners, description)</li>
 *       <li>Recovery targets (MTPD, RTO, RPO, MBCO)</li>
 *       <li>Impact matrix (time bands × dimensions, score 1..5 per cell)</li>
 *       <li>Resources + workaround + review/submit</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <p>The impact-matrix step lets the user score each (time-band × dimension)
 * cell on a 1..5 scale; the criticality is calculated server-side on save and
 * re-displayed as the user moves through the wizard. RTO &gt; MTPD is rejected
 * by the backend on approval — the wizard surfaces that inline.
 */
type Mode = 'register' | 'wizard';

export function BiaWizardPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [mode, setMode] = useState<Mode>('register');
  const [items, setItems] = useState<BcmBiaAssessment[]>([]);
  const [stats, setStats] = useState<Record<string, number>>({});
  const [catalog, setCatalog] = useState<{ timeBands: string[]; impactDimensions: string[] }>({ timeBands: [], impactDimensions: [] });
  const [loading, setLoading] = useState(false);

  // Wizard state
  const [editing, setEditing] = useState<BcmBiaAssessment | null>(null);
  const [step, setStep] = useState(1);
  const [matrix, setMatrix] = useState<Record<string, Record<string, number>>>({});

  async function reload() {
    setLoading(true);
    // Per-call .catch() — render whatever loaded successfully. A blank
    // catalog still lets the user view existing BIAs; a missing stats
    // endpoint shouldn't hide the list.
    const [page, st, cat] = await Promise.all([
      listBiaAssessments(0, 100).catch(() => ({ content: [] as BcmBiaAssessment[], totalElements: 0 })),
      biaStats().catch(() => ({} as Record<string, number>)),
      listBiaCatalog().catch(() => ({ timeBands: [] as string[], impactDimensions: [] as string[] })),
    ]);
    setItems(page.content);
    setStats(st);
    setCatalog(cat);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  function openNew() {
    setEditing({
      id: 0, biaCode: '',
      manualWorkaroundAvailable: false,
      approvalStatus: 'DRAFT',
    } as BcmBiaAssessment);
    setMatrix({});
    setStep(1);
    setMode('wizard');
  }

  async function openExisting(id: number) {
    try {
      const e = await getBiaAssessment(id);
      setEditing(e);
      setMatrix(parseTimeBandMatrix(e.impactTimeBandsJson) as Record<string, Record<string, number>>);
      setStep(1);
      setMode('wizard');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function saveStep(): Promise<BcmBiaAssessment | null> {
    if (!editing) return null;
    const body: Partial<BcmBiaAssessment> = {
      cycleId: editing.cycleId,
      serviceId: editing.serviceId,
      processId: editing.processId,
      department: editing.department,
      biaOwnerId: editing.biaOwnerId,
      description: editing.description,
      mtpdHours: editing.mtpdHours,
      rtoHours: editing.rtoHours,
      rpoHours: editing.rpoHours,
      mbcoDescription: editing.mbcoDescription,
      minStaff: editing.minStaff,
      minTechText: editing.minTechText,
      minFacilityText: editing.minFacilityText,
      minSupplierText: editing.minSupplierText,
      manualWorkaroundAvailable: editing.manualWorkaroundAvailable,
      manualWorkaroundDescription: editing.manualWorkaroundDescription,
      peakPeriodsText: editing.peakPeriodsText,
      impactTimeBandsJson: stringifyTimeBandMatrix(matrix),
    };
    try {
      let saved: BcmBiaAssessment;
      if (editing.id && editing.id > 0) {
        saved = await patchBiaAssessment(editing.id, body);
      } else {
        saved = await createBiaAssessment(body);
        toast.success(isAr ? `تم إنشاء ${saved.biaCode}` : `${saved.biaCode} created`);
      }
      setEditing(saved);
      setMatrix(parseTimeBandMatrix(saved.impactTimeBandsJson) as Record<string, Record<string, number>>);
      return saved;
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الحفظ' : 'Save failed'));
      return null;
    }
  }

  async function nextStep() {
    const saved = await saveStep();
    if (saved) setStep(Math.min(4, step + 1));
  }

  async function recalculate() {
    if (!editing?.id) return;
    try {
      const saved = await calculateBiaCriticality(editing.id);
      setEditing(saved);
      toast.success(isAr
        ? `الأهمية: ${tlabel(CRITICALITY_LABELS, saved.overallCriticality ?? 'LOW', true)}`
        : `Criticality: ${tlabel(CRITICALITY_LABELS, saved.overallCriticality ?? 'LOW', false)}`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function submitForReview() {
    if (!editing?.id) return;
    const saved = await saveStep();
    if (!saved) return;
    try {
      const out = await transitionBiaAssessment(saved.id, 'UNDER_REVIEW');
      setEditing(out);
      toast.success(isAr ? 'تم الإرسال للمراجعة' : 'Sent for review');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function approve() {
    if (!editing?.id) return;
    try {
      const out = await transitionBiaAssessment(editing.id, 'APPROVED');
      setEditing(out);
      toast.success(isAr ? `تم الاعتماد — الأهمية ${out.overallCriticality}` : `Approved — criticality ${out.overallCriticality}`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function generateBcp() {
    if (!editing?.id) return;
    const name = window.prompt(isAr ? 'اسم خطة BCP الجديدة' : 'New BCP name',
      `BCP from ${editing.biaCode}`);
    if (!name) return;
    try {
      // Use the new structured-plan endpoint (T1.4b) instead of the legacy
      // 6-field BcmPlan stub. Creates a multi-section plan + section catalogue.
      const created = await generatePlanFromBia(editing.id, 'BCP', name);
      toast.success(isAr ? `تم إنشاء ${created.planCode}` : `${created.planCode} created`);
      // Refresh the BIA so the linked-BCP indicator updates
      const refreshed = await getBiaAssessment(editing.id);
      setEditing(refreshed);
      // Open the builder so the author can immediately fill sections
      navigate(`/bcm/plans?openPlan=${created.id}`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function generateDrp() {
    if (!editing?.id) return;
    const name = window.prompt(isAr ? 'اسم خطة DRP الجديدة' : 'New DRP name',
      `DRP from ${editing.biaCode}`);
    if (!name) return;
    try {
      const created = await generatePlanFromBia(editing.id, 'DRP', name);
      toast.success(isAr ? `تم إنشاء ${created.planCode}` : `${created.planCode} created`);
      const refreshed = await getBiaAssessment(editing.id);
      setEditing(refreshed);
      navigate(`/bcm/plans?openPlan=${created.id}`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  function backToRegister() {
    setMode('register');
    setEditing(null);
    void reload();
  }

  // ─── Render ──────────────────────────────────────────────────

  if (mode === 'register') {
    return (
      <Shell>
        <PageHeader
          title={isAr ? 'تحليل الأثر على الأعمال (BIA)' : 'Business Impact Analysis (BIA)'}
          sub={isAr
            ? 'سجّل تأثير التعطل عبر فواصل زمنية وأبعاد متعددة، احسب الأهمية، ووَلِّد خطط BCP / DRP من الـBIA المعتمد.'
            : 'Score disruption impact across time bands and dimensions, calculate criticality, and generate BCP / DRP from approved BIA.'}
          eyebrow={isAr ? 'استمرارية الأعمال' : 'Business Continuity'}
          actions={<Button variant="primary" onClick={openNew}>
            <ClipboardCheck size={14} /> {isAr ? 'بدء BIA جديد' : 'New BIA'}
          </Button>}
        />

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(160px,1fr))', gap: 12, marginBottom: 14 }}>
          <Kpi Icon={ClipboardCheck} label={isAr ? 'إجمالي' : 'Total BIA'} value={items.length} tone="info" />
          <Kpi Icon={FileCheck2}     label={isAr ? 'معتمد' : 'Approved'}    value={stats.status_approved ?? 0} tone="success" />
          <Kpi Icon={AlertOctagon}   label={isAr ? 'سيادي'   : 'Sovereign'}  value={stats.crit_sovereign ?? 0} tone="danger" />
          <Kpi Icon={AlertOctagon}   label={isAr ? 'حرج'     : 'Critical'}   value={stats.crit_critical ?? 0}  tone="danger" />
        </div>

        <Card title={isAr ? 'سجل تحليل الأثر' : 'BIA register'}>
          {loading && <div className="text-muted" style={{ fontSize: 11 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
          {items.length === 0 ? (
            <InlineEmpty text={isAr ? 'لا توجد تحليلات BIA بعد' : 'No BIA assessments yet'} />
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="data-table" style={{ width: '100%', fontSize: 13 }}>
                <thead>
                  <tr>
                    <th style={{ textAlign: 'start' }}>{isAr ? 'الكود' : 'Code'}</th>
                    <th style={{ textAlign: 'start' }}>{isAr ? 'الحالة' : 'Status'}</th>
                    <th style={{ textAlign: 'start' }}>{isAr ? 'الأهمية' : 'Criticality'}</th>
                    <th style={{ textAlign: 'start' }}>MTPD</th>
                    <th style={{ textAlign: 'start' }}>RTO</th>
                    <th style={{ textAlign: 'start' }}>RPO</th>
                    <th style={{ textAlign: 'start' }}>BCP</th>
                    <th style={{ textAlign: 'start' }}>DRP</th>
                    <th style={{ textAlign: 'end' }}>{isAr ? 'إجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map(b => (
                    <tr key={b.id}>
                      <td className="mono">{b.biaCode}</td>
                      <td><Badge tone={biaStatusTone(b.approvalStatus)}>{b.approvalStatus}</Badge></td>
                      <td>
                        {b.overallCriticality
                          ? <Badge tone={criticalityTone(b.overallCriticality)}>
                              {tlabel(CRITICALITY_LABELS, b.overallCriticality, isAr)}
                            </Badge>
                          : <span className="text-muted">—</span>}
                      </td>
                      <td className="mono">{b.mtpdHours ?? '—'}h</td>
                      <td className="mono">{b.rtoHours ?? '—'}h</td>
                      <td className="mono">{b.rpoHours ?? '—'}h</td>
                      <td>{b.generatedBcpId ? <span className="mono">#{b.generatedBcpId}</span> : <span className="text-muted">—</span>}</td>
                      <td>{b.generatedDrpId ? <span className="mono">#{b.generatedDrpId}</span> : <span className="text-muted">—</span>}</td>
                      <td style={{ textAlign: 'end' }}>
                        <Button variant="ghost" onClick={() => void openExisting(b.id)}>{isAr ? 'فتح' : 'Open'}</Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </Shell>
    );
  }

  // ── Wizard mode ──
  if (!editing) return null;
  const locked = editing.approvalStatus === 'APPROVED' || editing.approvalStatus === 'ACTIVE' || editing.approvalStatus === 'ARCHIVED';

  return (
    <Shell>
      <PageHeader
        title={editing.biaCode
          ? `${editing.biaCode}`
          : (isAr ? 'تحليل أثر جديد' : 'New BIA')}
        sub={isAr ? 'معالج تحليل الأثر — أربع خطوات' : 'BIA wizard — four steps'}
        eyebrow={isAr ? 'استمرارية الأعمال' : 'Business Continuity'}
        actions={<Button variant="ghost" onClick={backToRegister}>
          <ChevronLeft size={14} /> {isAr ? 'عودة للسجل' : 'Back to register'}
        </Button>}
      />

      {/* Step indicator */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 14, flexWrap: 'wrap', alignItems: 'center' }}>
        {[1, 2, 3, 4].map(s => (
          <Badge key={s} tone={s === step ? 'primary' : (s < step ? 'success' : 'neutral')}>
            {s}. {stepLabel(s, isAr)}
          </Badge>
        ))}
        <div style={{ marginInlineStart: 'auto', display: 'flex', gap: 6 }}>
          {editing.overallCriticality && (
            <Badge tone={criticalityTone(editing.overallCriticality)}>
              {isAr ? 'الأهمية' : 'Criticality'}: {tlabel(CRITICALITY_LABELS, editing.overallCriticality, isAr)}
            </Badge>
          )}
          <Badge tone={biaStatusTone(editing.approvalStatus)}>{editing.approvalStatus}</Badge>
        </div>
      </div>

      {locked && (
        <div style={{ background: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 6,
                      padding: '8px 12px', marginBottom: 12, fontSize: 12 }}>
          {isAr ? 'هذا التحليل مقفل. للتعديل، استخدم إجراء "طلب تغييرات".' : 'This BIA is locked. Use "request changes" to reopen for edits.'}
        </div>
      )}

      <div style={{ display: 'grid', gap: 14 }}>
        {step === 1 && <Step1Scope editing={editing} onChange={setEditing} isAr={isAr} locked={locked} />}
        {step === 2 && <Step2Recovery editing={editing} onChange={setEditing} isAr={isAr} locked={locked} />}
        {step === 3 && <Step3ImpactMatrix matrix={matrix} setMatrix={setMatrix}
                                          catalog={catalog} isAr={isAr} locked={locked} />}
        {step === 4 && <Step4Resources editing={editing} onChange={setEditing} isAr={isAr} locked={locked} />}

        <div style={{ display: 'flex', gap: 8, justifyContent: 'space-between', flexWrap: 'wrap' }}>
          <Button variant="ghost" disabled={step === 1} onClick={() => setStep(step - 1)}>
            <ChevronLeft size={14} /> {isAr ? 'السابق' : 'Previous'}
          </Button>
          <div style={{ display: 'flex', gap: 8 }}>
            {step === 3 && (
              <Button variant="secondary" disabled={locked || !editing.id} onClick={recalculate}>
                <Cog size={14} /> {isAr ? 'إعادة حساب الأهمية' : 'Recalculate criticality'}
              </Button>
            )}
            {step < 4
              ? <Button variant="primary" disabled={locked} onClick={() => void nextStep()}>
                  {isAr ? 'حفظ والتالي' : 'Save & next'} <ChevronRight size={14} />
                </Button>
              : <>
                  <Button variant="secondary" disabled={locked} onClick={() => void saveStep()}>
                    {isAr ? 'حفظ' : 'Save'}
                  </Button>
                  {editing.approvalStatus === 'DRAFT' && (
                    <Button variant="primary" disabled={!editing.id} onClick={() => void submitForReview()}>
                      {isAr ? 'إرسال للمراجعة' : 'Submit for review'}
                    </Button>
                  )}
                  {editing.approvalStatus === 'UNDER_REVIEW' && (
                    <Button variant="primary" onClick={() => void approve()}>
                      {isAr ? 'اعتماد' : 'Approve'}
                    </Button>
                  )}
                  {(editing.approvalStatus === 'APPROVED' || editing.approvalStatus === 'ACTIVE') && (
                    <>
                      <Button variant="secondary" disabled={!!editing.generatedBcpId} onClick={() => void generateBcp()}>
                        <Wrench size={14} /> {isAr ? 'إنشاء BCP' : 'Generate BCP'}
                      </Button>
                      <Button variant="secondary" disabled={!!editing.generatedDrpId} onClick={() => void generateDrp()}>
                        <Wrench size={14} /> {isAr ? 'إنشاء DRP' : 'Generate DRP'}
                      </Button>
                    </>
                  )}
                </>}
          </div>
        </div>
      </div>
    </Shell>
  );
}

// ─── Step components ──────────────────────────────────────────

function stepLabel(s: number, isAr: boolean) {
  if (s === 1) return isAr ? 'النطاق' : 'Scope';
  if (s === 2) return isAr ? 'أهداف التعافي' : 'Recovery targets';
  if (s === 3) return isAr ? 'مصفوفة الأثر' : 'Impact matrix';
  return isAr ? 'الموارد والمراجعة' : 'Resources & review';
}

function Step1Scope({ editing, onChange, isAr, locked }: {
  editing: BcmBiaAssessment; onChange: (e: BcmBiaAssessment) => void; isAr: boolean; locked: boolean;
}) {
  return (
    <Card title={isAr ? 'الخطوة 1 — النطاق' : 'Step 1 — Scope'}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(220px,1fr))', gap: 10 }}>
        <Field label={isAr ? 'رقم الخدمة (soft FK)' : 'Service ID (soft FK)'}>
          <input type="number" value={editing.serviceId ?? ''} disabled={locked}
                 onChange={e => onChange({ ...editing, serviceId: numeric(e.target.value) })}
                 style={inputStyle} />
        </Field>
        <Field label={isAr ? 'رقم العملية (soft FK)' : 'Process ID (soft FK)'}>
          <input type="number" value={editing.processId ?? ''} disabled={locked}
                 onChange={e => onChange({ ...editing, processId: numeric(e.target.value) })}
                 style={inputStyle} />
        </Field>
        <Field label={isAr ? 'الإدارة' : 'Department'}>
          <input value={editing.department ?? ''} disabled={locked}
                 onChange={e => onChange({ ...editing, department: e.target.value })}
                 style={inputStyle} />
        </Field>
        <Field label={isAr ? 'مالك BIA' : 'BIA owner ID'}>
          <input type="number" value={editing.biaOwnerId ?? ''} disabled={locked}
                 onChange={e => onChange({ ...editing, biaOwnerId: numeric(e.target.value) })}
                 style={inputStyle} />
        </Field>
      </div>
      <Field label={isAr ? 'الوصف' : 'Description'}>
        <textarea value={editing.description ?? ''} disabled={locked}
                  onChange={e => onChange({ ...editing, description: e.target.value })}
                  style={{ ...inputStyle, minHeight: 80 }} />
      </Field>
      <div className="text-muted" style={{ fontSize: 11, marginTop: 8 }}>
        {isAr ? 'مطلوب على الأقل رقم خدمة أو رقم عملية.' : 'At least one of service ID or process ID is required.'}
      </div>
    </Card>
  );
}

function Step2Recovery({ editing, onChange, isAr, locked }: {
  editing: BcmBiaAssessment; onChange: (e: BcmBiaAssessment) => void; isAr: boolean; locked: boolean;
}) {
  const rtoTooHigh = editing.rtoHours != null && editing.mtpdHours != null && editing.rtoHours > editing.mtpdHours;
  return (
    <Card title={isAr ? 'الخطوة 2 — أهداف التعافي' : 'Step 2 — Recovery targets'}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))', gap: 10 }}>
        <Field label={isAr ? 'MTPD (ساعات)' : 'MTPD (hours)'}>
          <input type="number" min={0} value={editing.mtpdHours ?? ''} disabled={locked}
                 onChange={e => onChange({ ...editing, mtpdHours: numeric(e.target.value) })}
                 style={inputStyle} />
        </Field>
        <Field label={isAr ? 'RTO (ساعات)' : 'RTO (hours)'}>
          <input type="number" min={0} value={editing.rtoHours ?? ''} disabled={locked}
                 onChange={e => onChange({ ...editing, rtoHours: numeric(e.target.value) })}
                 style={inputStyle} />
        </Field>
        <Field label={isAr ? 'RPO (ساعات)' : 'RPO (hours)'}>
          <input type="number" min={0} value={editing.rpoHours ?? ''} disabled={locked}
                 onChange={e => onChange({ ...editing, rpoHours: numeric(e.target.value) })}
                 style={inputStyle} />
        </Field>
      </div>
      {rtoTooHigh && (
        <div style={{ marginTop: 8, padding: '6px 10px', borderRadius: 4,
                      background: 'var(--danger-bg, #fbeaea)', color: 'var(--danger, #c8362c)', fontSize: 12 }}>
          {isAr ? `RTO (${editing.rtoHours}) لا يجوز أن يتجاوز MTPD (${editing.mtpdHours}). سيرفض الاعتماد.`
                : `RTO (${editing.rtoHours}) cannot exceed MTPD (${editing.mtpdHours}). Approval will be rejected.`}
        </div>
      )}
      <Field label={isAr ? 'وصف MBCO (الحد الأدنى لاستمرارية الأعمال)' : 'MBCO (Minimum Business Continuity Objective)'}>
        <textarea value={editing.mbcoDescription ?? ''} disabled={locked}
                  onChange={e => onChange({ ...editing, mbcoDescription: e.target.value })}
                  style={{ ...inputStyle, minHeight: 80 }} />
      </Field>
    </Card>
  );
}

function Step3ImpactMatrix({ matrix, setMatrix, catalog, isAr, locked }: {
  matrix: Record<string, Record<string, number>>;
  setMatrix: (m: Record<string, Record<string, number>>) => void;
  catalog: { timeBands: string[]; impactDimensions: string[] };
  isAr: boolean; locked: boolean;
}) {
  const setCell = (band: string, dim: string, v: number) => {
    const next = { ...matrix };
    next[band] = { ...(next[band] ?? {}), [dim]: v };
    if (v === 0) delete next[band][dim];
    setMatrix(next);
  };
  return (
    <Card title={isAr ? 'الخطوة 3 — مصفوفة الأثر' : 'Step 3 — Impact matrix'}>
      <div className="text-muted" style={{ fontSize: 11, marginBottom: 8 }}>
        {isAr
          ? 'سجّل درجة الأثر (1..5) لكل خانة (فترة زمنية × بُعد). صفر يعني لا يوجد أثر.'
          : 'Score 1..5 per cell (time band × dimension). Zero = no impact.'}
      </div>
      <div style={{ overflowX: 'auto' }}>
        <table className="data-table" style={{ width: '100%', fontSize: 12 }}>
          <thead>
            <tr>
              <th style={{ textAlign: 'start' }}>{isAr ? 'فترة زمنية' : 'Time band'}</th>
              {catalog.impactDimensions.map(d => (
                <th key={d} style={{ textAlign: 'center', minWidth: 60 }}>
                  {isAr ? DIMENSION_LABELS[d]?.ar : DIMENSION_LABELS[d]?.en}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {catalog.timeBands.map(band => (
              <tr key={band}>
                <td className="mono">{band}</td>
                {catalog.impactDimensions.map(dim => (
                  <td key={dim} style={{ textAlign: 'center' }}>
                    <select value={matrix[band]?.[dim] ?? 0} disabled={locked}
                            onChange={e => setCell(band, dim, Number(e.target.value))}
                            style={{ width: 50, padding: '2px 4px' }}>
                      {[0, 1, 2, 3, 4, 5].map(n => <option key={n} value={n}>{n}</option>)}
                    </select>
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

function Step4Resources({ editing, onChange, isAr, locked }: {
  editing: BcmBiaAssessment; onChange: (e: BcmBiaAssessment) => void; isAr: boolean; locked: boolean;
}) {
  return (
    <Card title={isAr ? 'الخطوة 4 — الموارد والبدائل اليدوية' : 'Step 4 — Resources & manual workaround'}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(200px,1fr))', gap: 10 }}>
        <Field label={isAr ? 'الحد الأدنى للموظفين' : 'Min staff count'}>
          <input type="number" min={0} value={editing.minStaff ?? ''} disabled={locked}
                 onChange={e => onChange({ ...editing, minStaff: numeric(e.target.value) })}
                 style={inputStyle} />
        </Field>
        <Field label={isAr ? 'البديل اليدوي متاح؟' : 'Manual workaround available'}>
          <select value={editing.manualWorkaroundAvailable ? 'yes' : 'no'} disabled={locked}
                  onChange={e => onChange({ ...editing, manualWorkaroundAvailable: e.target.value === 'yes' })}
                  style={inputStyle}>
            <option value="no">{isAr ? 'لا' : 'No'}</option>
            <option value="yes">{isAr ? 'نعم' : 'Yes'}</option>
          </select>
        </Field>
      </div>
      <Field label={isAr ? 'الحد الأدنى للتقنية' : 'Minimum technology'}>
        <textarea value={editing.minTechText ?? ''} disabled={locked}
                  onChange={e => onChange({ ...editing, minTechText: e.target.value })}
                  style={{ ...inputStyle, minHeight: 60 }} />
      </Field>
      <Field label={isAr ? 'الحد الأدنى للمنشآت' : 'Minimum facility'}>
        <textarea value={editing.minFacilityText ?? ''} disabled={locked}
                  onChange={e => onChange({ ...editing, minFacilityText: e.target.value })}
                  style={{ ...inputStyle, minHeight: 60 }} />
      </Field>
      <Field label={isAr ? 'الحد الأدنى للمورّدين' : 'Minimum supplier dependency'}>
        <textarea value={editing.minSupplierText ?? ''} disabled={locked}
                  onChange={e => onChange({ ...editing, minSupplierText: e.target.value })}
                  style={{ ...inputStyle, minHeight: 60 }} />
      </Field>
      <Field label={isAr ? 'وصف البديل اليدوي' : 'Manual workaround description'}>
        <textarea value={editing.manualWorkaroundDescription ?? ''} disabled={locked}
                  onChange={e => onChange({ ...editing, manualWorkaroundDescription: e.target.value })}
                  style={{ ...inputStyle, minHeight: 60 }} />
      </Field>
      <Field label={isAr ? 'فترات الذروة' : 'Peak periods'}>
        <input value={editing.peakPeriodsText ?? ''} disabled={locked}
               onChange={e => onChange({ ...editing, peakPeriodsText: e.target.value })}
               style={inputStyle} placeholder={isAr ? 'مثال: نهاية الشهر، رمضان، الحج' : 'e.g. month-end, Ramadan, Hajj'} />
      </Field>
    </Card>
  );
}

// ─── Helpers ───────────────────────────────────────────────────

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'grid', gap: 4, fontSize: 12, marginTop: 8 }}>
      <span className="text-muted">{label}</span>
      {children}
    </label>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '6px 10px', border: '1px solid var(--border)', borderRadius: 4,
  fontSize: 13, background: 'var(--surface)', width: '100%',
};

function numeric(v: string): number | undefined {
  if (v == null || v === '') return undefined;
  const n = Number(v);
  return Number.isFinite(n) ? n : undefined;
}

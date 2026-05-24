import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft, ShieldAlert, Activity, FileSearch, ClipboardCheck,
  BadgeAlert, Database, PowerOff, AlertTriangle, ListChecks,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { isAuthenticated } from '../../lib/auth';
import { ApiError } from '../../lib/api';
import {
  mlModelApi,
  approvalGateMissing, ML_NEXT_STAGES,
  ML_STAGE_LABEL, ML_STAGE_TONE,
  ML_TIER_LABEL, ML_TIER_TONE,
  ML_DOMAIN_LABEL,
  ML_AUDIT_TYPE_LABEL, ML_AUDIT_STATUS_LABEL, ML_AUDIT_STATUS_TONE,
  ML_DRIFT_TYPE_LABEL, ML_DRIFT_SEVERITY_LABEL, ML_DRIFT_SEVERITY_TONE,
  ML_COMPLIANCE_LABEL, ML_COMPLIANCE_TONE,
  type MlModelResponse, type MlLifecycleStage,
  type MlModelAuditResponse, type MlAuditType, type MlAuditStatus,
  type DriftObservationResponse,
} from '../../lib/aiGovernance';
import type { LucideIcon } from 'lucide-react';

/**
 * AI/ML Model detail page — Sprint 11.
 *
 * Tabbed: Overview / Provenance / Compliance / Audits / Drift / Activity.
 * Lifecycle transitions are gated by the same {@code approvalGateMissing()}
 * helper that the server-side {@code MlModelService} enforces — the
 * disabled button + reason show the user *why* before they try and fail.
 */

type Tab = 'overview' | 'provenance' | 'compliance' | 'audits' | 'drift' | 'activity';

export function AiModelDetailPage() {
  const { lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const { externalId = '' } = useParams<{ externalId: string }>();
  const demoMode = !isAuthenticated();

  const [model, setModel] = useState<MlModelResponse | null>(null);
  const [audits, setAudits] = useState<MlModelAuditResponse[]>([]);
  const [drift, setDrift] = useState<DriftObservationResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [tab, setTab] = useState<Tab>('overview');
  const [showAuditModal, setShowAuditModal] = useState(false);
  const [showDecomModal, setShowDecomModal] = useState(false);

  async function load() {
    if (demoMode || !externalId) return;
    setLoading(true);
    try {
      const [m, a, d] = await Promise.all([
        mlModelApi.get(externalId),
        mlModelApi.listAudits(externalId),
        mlModelApi.listDrift(externalId),
      ]);
      setModel(m);
      setAudits(a);
      setDrift(d);
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Failed to load model');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [externalId]);

  async function doTransition(toStage: MlLifecycleStage) {
    if (!model) return;
    try {
      const updated = await mlModelApi.transition(model.externalId, toStage);
      setModel(updated);
      toast.success(lang === 'ar' ? `تم الانتقال إلى ${ML_STAGE_LABEL[toStage].ar}` : `Moved to ${ML_STAGE_LABEL[toStage].en}`);
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Transition rejected');
    }
  }

  async function ackDrift(d: DriftObservationResponse) {
    if (!model) return;
    try {
      const updated = await mlModelApi.acknowledgeDrift(model.externalId, d.externalId);
      setDrift(drift.map(x => x.externalId === d.externalId ? updated : x));
      toast.success(lang === 'ar' ? 'تم الإقرار' : 'Acknowledged');
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Acknowledgement failed');
    }
  }

  const gateMissing = useMemo(() => model ? approvalGateMissing(model) : [], [model]);
  const unackDrift = drift.filter(d => !d.isAcknowledged);

  if (demoMode) {
    return (
      <Shell title={lang === 'ar' ? 'سجل نماذج الذكاء الاصطناعي' : 'AI/ML Model Registry'}>
        <EmptyState Icon={ShieldAlert}
          title={lang === 'ar' ? 'وضع العرض' : 'Demo mode'}
          sub={lang === 'ar' ? 'سجّل الدخول لعرض التفاصيل.' : 'Sign in to view model detail.'} />
      </Shell>
    );
  }
  if (loading || !model) {
    return (
      <Shell title={lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}>
        <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
      </Shell>
    );
  }

  return (
    <Shell title={`${model.modelCode} — ${lang === 'ar' && model.titleAr ? model.titleAr : model.titleEn}`}>
      <div style={{ marginBottom: 8 }}>
        <Button variant="ghost" size="sm" Icon={ArrowLeft} onClick={() => navigate('/ai/models')}>
          {lang === 'ar' ? 'الرجوع إلى السجل' : 'Back to registry'}
        </Button>
      </div>
      <PageHeader
        eyebrow={lang === 'ar' ? 'حوكمة الذكاء الاصطناعي' : 'AI Governance'}
        title={lang === 'ar' && model.titleAr ? model.titleAr : model.titleEn}
        sub={`${model.modelCode} · ${ML_DOMAIN_LABEL[model.businessDomain][lang === 'ar' ? 'ar' : 'en']}`}
        actions={
          <>
            <Badge tone={ML_TIER_TONE[model.riskTier]} dot>
              {ML_TIER_LABEL[model.riskTier][lang === 'ar' ? 'ar' : 'en']}
            </Badge>
            <Badge tone={ML_STAGE_TONE[model.lifecycleStage]} dot>
              {ML_STAGE_LABEL[model.lifecycleStage][lang === 'ar' ? 'ar' : 'en']}
            </Badge>
            {model.lifecycleStage !== 'DECOMMISSIONED' && (
              <Button variant="ghost" Icon={PowerOff} onClick={() => setShowDecomModal(true)}>
                {lang === 'ar' ? 'خارج الخدمة' : 'Decommission'}
              </Button>
            )}
          </>
        }
      />

      {/* Drift dashboard tile */}
      {unackDrift.length > 0 && (
        <Card padded>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
            <BadgeAlert size={18} color="var(--warning-fg)" />
            <div style={{ fontWeight: 700 }}>
              {lang === 'ar'
                ? `${unackDrift.length} ملاحظة انحراف لم تُقَر`
                : `${unackDrift.length} unacknowledged drift observation${unackDrift.length === 1 ? '' : 's'}`}
            </div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {unackDrift.slice(0, 3).map(d => (
              <div key={d.externalId} style={{
                display: 'flex', alignItems: 'center', gap: 12,
                padding: '8px 12px', background: 'var(--warning-bg)',
                borderRadius: 4, fontSize: 13,
              }}>
                <Badge tone={ML_DRIFT_SEVERITY_TONE[d.severity]} dot>
                  {ML_DRIFT_SEVERITY_LABEL[d.severity][lang === 'ar' ? 'ar' : 'en']}
                </Badge>
                <span>{ML_DRIFT_TYPE_LABEL[d.observationType][lang === 'ar' ? 'ar' : 'en']}</span>
                <code style={{ background: 'var(--surface)', padding: '2px 6px', borderRadius: 3 }}>
                  {d.metricName} = {String(d.metricValue)}
                </code>
                <span style={{ flex: 1, color: 'var(--text-muted)' }}>
                  {lang === 'ar' && d.notesAr ? d.notesAr : (d.notesEn ?? '')}
                </span>
                <Button size="sm" variant="primary" onClick={() => ackDrift(d)}>
                  {lang === 'ar' ? 'إقرار' : 'Acknowledge'}
                </Button>
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* Tabs */}
      <Card padded>
        <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid var(--border)', marginBottom: 16 }}>
          <TabButton id="overview"   label={lang === 'ar' ? 'نظرة عامة' : 'Overview'}        active={tab} setActive={setTab} Icon={ListChecks} />
          <TabButton id="provenance" label={lang === 'ar' ? 'المصدر' : 'Provenance'}         active={tab} setActive={setTab} Icon={Database} />
          <TabButton id="compliance" label={lang === 'ar' ? 'الامتثال' : 'Compliance'}        active={tab} setActive={setTab} Icon={ShieldAlert} />
          <TabButton id="audits"     label={lang === 'ar' ? 'التدقيقات' : 'Audits'}           active={tab} setActive={setTab} Icon={ClipboardCheck} count={audits.length} />
          <TabButton id="drift"      label={lang === 'ar' ? 'الانحراف' : 'Drift'}             active={tab} setActive={setTab} Icon={Activity} count={drift.length} />
          <TabButton id="activity"   label={lang === 'ar' ? 'سجل النشاط' : 'Activity'}        active={tab} setActive={setTab} Icon={FileSearch} />
        </div>

        {tab === 'overview' && <OverviewTab model={model} lang={lang} gateMissing={gateMissing} onTransition={doTransition} />}
        {tab === 'provenance' && <ProvenanceTab model={model} lang={lang} />}
        {tab === 'compliance' && <ComplianceTab model={model} lang={lang} />}
        {tab === 'audits' && (
          <AuditsTab
            audits={audits} lang={lang}
            onAdd={() => setShowAuditModal(true)} />
        )}
        {tab === 'drift' && (
          <DriftTab drift={drift} lang={lang} onAck={ackDrift} />
        )}
        {tab === 'activity' && (
          <div style={{ padding: 24, color: 'var(--text-muted)', textAlign: 'center' }}>
            {lang === 'ar'
              ? 'سجل النشاط (F7) متاح في صفحة سجل التدقيق العام.'
              : 'Activity timeline (F7 audit log) available on the platform audit log page.'}
          </div>
        )}
      </Card>

      {showAuditModal && (
        <RecordAuditModal
          modelExternalId={model.externalId}
          onClose={() => setShowAuditModal(false)}
          onSaved={a => { setAudits([a, ...audits]); setShowAuditModal(false); load(); }}
        />
      )}
      {showDecomModal && (
        <DecommissionModal
          modelExternalId={model.externalId}
          onClose={() => setShowDecomModal(false)}
          onSaved={m => { setModel(m); setShowDecomModal(false); }}
        />
      )}
    </Shell>
  );
}

// ─── Tab button ────────────────────────────────────────────────────

interface TabButtonProps {
  id: Tab;
  label: string;
  active: Tab;
  setActive: (t: Tab) => void;
  Icon: LucideIcon;
  count?: number;
}

function TabButton({ id, label, active, setActive, Icon, count }: TabButtonProps) {
  const isActive = active === id;
  return (
    <button
      type="button"
      onClick={() => setActive(id)}
      style={{
        display: 'flex', alignItems: 'center', gap: 6,
        padding: '8px 14px', cursor: 'pointer',
        background: 'transparent', border: 'none',
        borderBottom: isActive ? '2px solid var(--accent)' : '2px solid transparent',
        color: isActive ? 'var(--accent)' : 'var(--text)',
        fontWeight: isActive ? 600 : 400, fontSize: 13,
      }}
    >
      <Icon size={14} />
      {label}
      {count != null && (
        <span style={{
          fontSize: 11, padding: '1px 6px',
          background: 'var(--surface-2)', borderRadius: 8, color: 'var(--text-muted)',
        }}>{count}</span>
      )}
    </button>
  );
}

// ─── Overview tab ──────────────────────────────────────────────────

interface OverviewProps {
  model: MlModelResponse;
  lang: 'en' | 'ar';
  gateMissing: string[];
  onTransition: (s: MlLifecycleStage) => void;
}

function OverviewTab({ model, lang, gateMissing, onTransition }: OverviewProps) {
  const nextStages = ML_NEXT_STAGES[model.lifecycleStage];
  return (
    <div style={{ display: 'grid', gap: 16, gridTemplateColumns: '2fr 1fr' }}>
      <div>
        <h3 style={{ marginTop: 0 }}>
          {lang === 'ar' ? 'وصف النموذج' : 'Model description'}
        </h3>
        <p style={{ whiteSpace: 'pre-wrap' }}>
          {lang === 'ar' && model.descriptionAr ? model.descriptionAr : (model.descriptionEn ?? '—')}
        </p>
        <Field label={lang === 'ar' ? 'نوع النموذج' : 'Model type'} value={model.modelType} />
        <Field label={lang === 'ar' ? 'الإصدار' : 'Version'} value={model.versionLabel ?? '—'} />
        <Field label={lang === 'ar' ? 'إطار العمل' : 'Framework'} value={model.framework ?? '—'} />
        <Field label={lang === 'ar' ? 'الاستضافة' : 'Hosting'} value={model.hosting ?? '—'} />
        <Field label={lang === 'ar' ? 'زمن الاستدلال (مللي ثانية)' : 'Inference latency (ms)'} value={model.inferenceLatencyMs?.toString() ?? '—'} />
      </div>
      <div>
        <h3 style={{ marginTop: 0 }}>
          {lang === 'ar' ? 'الانتقال إلى مرحلة' : 'Transition'}
        </h3>
        {nextStages.length === 0 ? (
          <p style={{ color: 'var(--text-muted)' }}>
            {lang === 'ar' ? 'لا توجد انتقالات متاحة من هذه المرحلة.' : 'No transitions available from this stage.'}
          </p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {nextStages.map(s => {
              const blocked = s === 'APPROVED_FOR_PROD' && gateMissing.length > 0;
              return (
                <Button
                  key={s}
                  variant="ghost"
                  disabled={blocked}
                  onClick={() => onTransition(s)}
                  title={blocked
                    ? (lang === 'ar' ? `محظور — ينقص: ${gateMissing.join(', ')}` : `Blocked — missing: ${gateMissing.join(', ')}`)
                    : undefined}
                >
                  → {ML_STAGE_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                  {blocked && <AlertTriangle size={12} style={{ marginInlineStart: 6 }} />}
                </Button>
              );
            })}
          </div>
        )}
        {gateMissing.length > 0 && (
          <div style={{
            marginTop: 12, padding: 10, fontSize: 12,
            background: 'var(--warning-bg)', color: 'var(--warning-fg)',
            border: '1px solid var(--warning-border)', borderRadius: 4,
          }}>
            <strong>{lang === 'ar' ? 'بوابة الاعتماد:' : 'Approval gate:'}</strong>
            <br />
            {lang === 'ar'
              ? `النماذج ذات الخطورة ${ML_TIER_LABEL[model.riskTier].ar} لا يمكن اعتمادها للإنتاج حتى تكتمل:`
              : `${ML_TIER_LABEL[model.riskTier].en}-tier models cannot enter APPROVED_FOR_PROD until you complete:`}
            <ul style={{ margin: '4px 0 0 16px', paddingInlineStart: 16 }}>
              {gateMissing.map(k => <li key={k}><code>{k}</code></li>)}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', gap: 12, padding: '6px 0', borderBottom: '1px solid var(--border)' }}>
      <div style={{ minWidth: 160, color: 'var(--text-muted)', fontSize: 12 }}>{label}</div>
      <div style={{ fontSize: 13 }}>{value}</div>
    </div>
  );
}

// ─── Provenance tab ────────────────────────────────────────────────

function ProvenanceTab({ model, lang }: { model: MlModelResponse; lang: 'en' | 'ar' }) {
  return (
    <div>
      <h3 style={{ marginTop: 0 }}>
        {lang === 'ar' ? 'مصدر بيانات التدريب' : 'Training data provenance'}
      </h3>
      <p style={{ whiteSpace: 'pre-wrap' }}>
        {lang === 'ar' && model.trainingDataProvenanceAr
          ? model.trainingDataProvenanceAr
          : (model.trainingDataProvenanceEn ?? '—')}
      </p>
      <Field label={lang === 'ar' ? 'حجم البيانات (سجلات)' : 'Data size (records)'}
             value={model.trainingDataSizeRecords?.toLocaleString() ?? '—'} />
      <Field label={lang === 'ar' ? 'بداية الفترة' : 'Period start'}
             value={model.trainingDataPeriodStart ?? '—'} />
      <Field label={lang === 'ar' ? 'نهاية الفترة' : 'Period end'}
             value={model.trainingDataPeriodEnd ?? '—'} />
      <Field label={lang === 'ar' ? 'عدد السمات' : 'Feature count'}
             value={model.featureCount?.toString() ?? '—'} />
      <Field label={lang === 'ar' ? 'بيانات خارجية' : 'External dataset used'}
             value={model.externalDatasetUsed ? (lang === 'ar' ? 'نعم' : 'Yes') : (lang === 'ar' ? 'لا' : 'No')} />
      {model.externalDatasetUsed && model.externalDatasetProvenance && (
        <Field label={lang === 'ar' ? 'مصدر البيانات الخارجية' : 'External dataset provenance'}
               value={model.externalDatasetProvenance} />
      )}
    </div>
  );
}

// ─── Compliance tab ────────────────────────────────────────────────

function ComplianceTab({ model, lang }: { model: MlModelResponse; lang: 'en' | 'ar' }) {
  return (
    <div style={{ display: 'grid', gap: 16, gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))' }}>
      <ComplianceCard
        title="SDAIA AI Ethics (2023)"
        status={model.sdaiaAiEthicsCompliance}
        lang={lang} />
      <ComplianceCard
        title="SAMA AI Governance (Dec 2024)"
        status={model.samaAiPrinciplesCompliance}
        lang={lang} />
      <div>
        <h3 style={{ marginTop: 0 }}>{lang === 'ar' ? 'الأعلام التنظيمية' : 'Regulatory flags'}</h3>
        <Field label={lang === 'ar' ? 'تقييم الأثر PDPL' : 'PDPL DPIA'}
               value={model.hasPdplDpia ? '✓' : '—'} />
        <Field label={lang === 'ar' ? 'تدقيق العدالة' : 'Fairness audit'}
               value={model.hasFairnessAudit ? `✓ (${model.lastFairnessAuditDate ?? ''})` : '—'} />
        <Field label={lang === 'ar' ? 'وثيقة قابلية التفسير' : 'Explainability doc'}
               value={model.hasExplainabilityDoc ? '✓' : '—'} />
        <Field label={lang === 'ar' ? 'عالي الخطورة EU AI Act' : 'EU AI Act high-risk'}
               value={model.isHighRiskEuAiAct ? '✓ (Annex III)' : '—'} />
      </div>
    </div>
  );
}

function ComplianceCard({ title, status, lang }: { title: string; status: MlModelResponse['sdaiaAiEthicsCompliance']; lang: 'en' | 'ar' }) {
  return (
    <Card padded>
      <div style={{ fontSize: 12, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.6 }}>
        {title}
      </div>
      <div style={{ marginTop: 8 }}>
        <Badge tone={ML_COMPLIANCE_TONE[status]} dot>
          {ML_COMPLIANCE_LABEL[status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      </div>
    </Card>
  );
}

// ─── Audits tab ────────────────────────────────────────────────────

function AuditsTab({ audits, lang, onAdd }: {
  audits: MlModelAuditResponse[];
  lang: 'en' | 'ar';
  onAdd: () => void;
}) {
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <h3 style={{ margin: 0 }}>
          {lang === 'ar' ? 'تدقيقات النموذج' : 'Model audits'}
        </h3>
        <Button variant="primary" onClick={onAdd}>
          {lang === 'ar' ? 'تدقيق جديد' : 'Record audit'}
        </Button>
      </div>
      {audits.length === 0
        ? <EmptyState Icon={ClipboardCheck}
            title={lang === 'ar' ? 'لا توجد تدقيقات بعد' : 'No audits yet'}
            sub={lang === 'ar' ? 'سجّل أول تدقيق عدالة أو قابلية تفسير.' : 'Record the first fairness or explainability audit.'} />
        : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: 'var(--surface-2)', textAlign: 'start' }}>
                <th style={{ padding: 8 }}>{lang === 'ar' ? 'النوع' : 'Type'}</th>
                <th style={{ padding: 8 }}>{lang === 'ar' ? 'التاريخ' : 'Date'}</th>
                <th style={{ padding: 8 }}>{lang === 'ar' ? 'النتيجة' : 'Status'}</th>
                <th style={{ padding: 8 }}>{lang === 'ar' ? 'المنهجية' : 'Methodology'}</th>
                <th style={{ padding: 8 }}>CAPA</th>
              </tr>
            </thead>
            <tbody>
              {audits.map(a => (
                <tr key={a.externalId} style={{ borderBottom: '1px solid var(--border)' }}>
                  <td style={{ padding: 8 }}>
                    <Badge tone="info">{ML_AUDIT_TYPE_LABEL[a.auditType][lang === 'ar' ? 'ar' : 'en']}</Badge>
                  </td>
                  <td style={{ padding: 8, fontFamily: 'monospace', fontSize: 12 }}>{a.auditDate}</td>
                  <td style={{ padding: 8 }}>
                    <Badge tone={ML_AUDIT_STATUS_TONE[a.status]} dot>
                      {ML_AUDIT_STATUS_LABEL[a.status][lang === 'ar' ? 'ar' : 'en']}
                    </Badge>
                  </td>
                  <td style={{ padding: 8, fontSize: 13 }}>
                    {(lang === 'ar' && a.methodologyAr ? a.methodologyAr : a.methodologyEn ?? '').slice(0, 80)}
                    {((lang === 'ar' && a.methodologyAr ? a.methodologyAr : a.methodologyEn ?? '').length > 80) ? '…' : ''}
                  </td>
                  <td style={{ padding: 8, fontFamily: 'monospace', fontSize: 12 }}>
                    {a.capaTaskId ?? '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )
      }
    </div>
  );
}

// ─── Drift tab ─────────────────────────────────────────────────────

function DriftTab({ drift, lang, onAck }: {
  drift: DriftObservationResponse[];
  lang: 'en' | 'ar';
  onAck: (d: DriftObservationResponse) => void;
}) {
  if (drift.length === 0) {
    return <EmptyState Icon={Activity}
      title={lang === 'ar' ? 'لا توجد ملاحظات انحراف' : 'No drift observations'}
      sub={lang === 'ar' ? 'تظهر ملاحظات الانحراف هنا تلقائياً عند رصدها.' : 'Drift observations appear here automatically when detected.'} />;
  }
  return (
    <table style={{ width: '100%', borderCollapse: 'collapse' }}>
      <thead>
        <tr style={{ background: 'var(--surface-2)', textAlign: 'start' }}>
          <th style={{ padding: 8 }}>{lang === 'ar' ? 'وقت الرصد' : 'Observed'}</th>
          <th style={{ padding: 8 }}>{lang === 'ar' ? 'النوع' : 'Type'}</th>
          <th style={{ padding: 8 }}>{lang === 'ar' ? 'الشدة' : 'Severity'}</th>
          <th style={{ padding: 8 }}>{lang === 'ar' ? 'المقياس' : 'Metric'}</th>
          <th style={{ padding: 8 }}>{lang === 'ar' ? 'القيمة' : 'Value'}</th>
          <th style={{ padding: 8 }}>{lang === 'ar' ? 'الأساس' : 'Baseline'}</th>
          <th style={{ padding: 8 }}>{lang === 'ar' ? 'الحدّ' : 'Threshold'}</th>
          <th style={{ padding: 8 }}>{lang === 'ar' ? 'الحالة' : 'Status'}</th>
        </tr>
      </thead>
      <tbody>
        {drift.map(d => (
          <tr key={d.externalId} style={{ borderBottom: '1px solid var(--border)' }}>
            <td style={{ padding: 8, fontFamily: 'monospace', fontSize: 12 }}>
              {d.observedAt?.slice(0, 16)}
            </td>
            <td style={{ padding: 8 }}>
              {ML_DRIFT_TYPE_LABEL[d.observationType][lang === 'ar' ? 'ar' : 'en']}
            </td>
            <td style={{ padding: 8 }}>
              <Badge tone={ML_DRIFT_SEVERITY_TONE[d.severity]} dot>
                {ML_DRIFT_SEVERITY_LABEL[d.severity][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
            </td>
            <td style={{ padding: 8, fontFamily: 'monospace', fontSize: 12 }}>{d.metricName}</td>
            <td style={{ padding: 8, fontFamily: 'monospace', fontSize: 12 }}>{String(d.metricValue)}</td>
            <td style={{ padding: 8, fontFamily: 'monospace', fontSize: 12 }}>
              {d.baselineValue == null ? '—' : String(d.baselineValue)}
            </td>
            <td style={{ padding: 8, fontFamily: 'monospace', fontSize: 12 }}>
              {d.driftThreshold == null ? '—' : String(d.driftThreshold)}
            </td>
            <td style={{ padding: 8 }}>
              {d.isAcknowledged ? (
                <Badge tone="success">{lang === 'ar' ? 'مُقَر' : 'Acknowledged'}</Badge>
              ) : (
                <Button size="sm" variant="primary" onClick={() => onAck(d)}>
                  {lang === 'ar' ? 'إقرار' : 'Acknowledge'}
                </Button>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

// ─── Record-audit modal ────────────────────────────────────────────

const AUDIT_METRIC_HELPERS: Record<MlAuditType, string[]> = {
  FAIRNESS:       ['demographic_parity_diff', 'equal_opportunity_diff', 'disparate_impact_ratio'],
  BIAS:           ['statistical_parity_diff', 'group_fairness_score'],
  EXPLAINABILITY: ['reason_code_coverage', 'avg_shap_features_surfaced'],
  ROBUSTNESS:     ['adversarial_accuracy', 'noise_robustness_score'],
  PRIVACY:        ['pii_leak_rate', 'membership_inference_auc'],
  SECURITY:       ['model_inversion_score', 'extraction_resistance'],
  PERFORMANCE:    ['accuracy', 'f1_score', 'auc'],
};

function RecordAuditModal({ modelExternalId, onClose, onSaved }: {
  modelExternalId: string;
  onClose: () => void;
  onSaved: (a: MlModelAuditResponse) => void;
}) {
  const { lang } = useI18n();
  const toast = useToast();
  const [auditType, setAuditType] = useState<MlAuditType>('FAIRNESS');
  const [auditDate, setAuditDate] = useState(new Date().toISOString().slice(0, 10));
  const [status, setStatus] = useState<MlAuditStatus>('PASSED');
  const [methodologyEn, setMethodologyEn] = useState('');
  const [findingsEn, setFindingsEn] = useState('');
  const [metricsJson, setMetricsJson] = useState('{}');
  const [auditorExternalParty, setAuditorExternalParty] = useState('');
  const [busy, setBusy] = useState(false);

  async function save() {
    try {
      setBusy(true);
      const out = await mlModelApi.recordAudit(modelExternalId, {
        auditType,
        auditDate,
        status,
        methodologyEn: methodologyEn || undefined,
        findingsEn: findingsEn || undefined,
        metricsJson,
        auditorExternalParty: auditorExternalParty || undefined,
      });
      toast.success(lang === 'ar' ? 'تم تسجيل التدقيق' : 'Audit recorded');
      onSaved(out);
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Could not record audit');
    } finally {
      setBusy(false);
    }
  }

  const helpers = AUDIT_METRIC_HELPERS[auditType];

  return (
    <Modal open onClose={onClose} title={lang === 'ar' ? 'تسجيل تدقيق جديد' : 'Record audit'} size="lg"
      footer={<>
        <Button variant="ghost" onClick={onClose}>{lang === 'ar' ? 'إلغاء' : 'Cancel'}</Button>
        <Button variant="primary" disabled={busy} onClick={save}>
          {lang === 'ar' ? 'حفظ' : 'Save'}
        </Button>
      </>}>
      <div style={{ display: 'grid', gap: 14 }}>
        <Row>
          <Label>{lang === 'ar' ? 'نوع التدقيق' : 'Audit type'}</Label>
          <Select value={auditType} onChange={v => setAuditType(v as MlAuditType)}
            options={(Object.keys(ML_AUDIT_TYPE_LABEL) as MlAuditType[]).map(k => ({
              value: k, label: ML_AUDIT_TYPE_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
            }))} />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'التاريخ' : 'Date'}</Label>
          <input type="date" value={auditDate} onChange={e => setAuditDate(e.target.value)}
            style={inputStyle} />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'النتيجة' : 'Status'}</Label>
          <Select value={status} onChange={v => setStatus(v as MlAuditStatus)}
            options={(Object.keys(ML_AUDIT_STATUS_LABEL) as MlAuditStatus[]).map(k => ({
              value: k, label: ML_AUDIT_STATUS_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
            }))} />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'الجهة المُدقِّقة (خارجية)' : 'External auditor'}</Label>
          <input value={auditorExternalParty}
            onChange={e => setAuditorExternalParty(e.target.value)}
            placeholder={lang === 'ar' ? '(اختياري) — KPMG / EY / …' : '(optional) — KPMG / EY / …'}
            style={inputStyle} />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'المنهجية' : 'Methodology'}</Label>
          <textarea value={methodologyEn} onChange={e => setMethodologyEn(e.target.value)}
            rows={3} style={{ ...inputStyle, fontFamily: 'inherit' }} />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'النتائج' : 'Findings'}</Label>
          <textarea value={findingsEn} onChange={e => setFindingsEn(e.target.value)}
            rows={3} style={{ ...inputStyle, fontFamily: 'inherit' }} />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'المقاييس (JSON)' : 'Metrics (JSON)'}</Label>
          <div style={{ flex: 1 }}>
            <textarea value={metricsJson} onChange={e => setMetricsJson(e.target.value)}
              rows={4} style={{ ...inputStyle, fontFamily: 'monospace' }} />
            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
              {lang === 'ar' ? 'مفاتيح شائعة:' : 'Common keys:'} {helpers.map(h => <code key={h} style={{ marginInlineEnd: 6 }}>{h}</code>)}
            </div>
          </div>
        </Row>
      </div>
    </Modal>
  );
}

// ─── Decommission modal ────────────────────────────────────────────

function DecommissionModal({ modelExternalId, onClose, onSaved }: {
  modelExternalId: string;
  onClose: () => void;
  onSaved: (m: MlModelResponse) => void;
}) {
  const { lang } = useI18n();
  const toast = useToast();
  const [reasonEn, setReasonEn] = useState('');
  const [reasonAr, setReasonAr] = useState('');
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [busy, setBusy] = useState(false);

  async function save() {
    if (!reasonEn.trim()) {
      toast.danger(lang === 'ar' ? 'السبب مطلوب' : 'Reason is required');
      return;
    }
    try {
      setBusy(true);
      const out = await mlModelApi.decommission(modelExternalId, {
        reasonEn, reasonAr: reasonAr || undefined, decommissionDate: date,
      });
      toast.success(lang === 'ar' ? 'تم إخراج النموذج من الخدمة' : 'Model decommissioned');
      onSaved(out);
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Decommission failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open onClose={onClose}
      title={lang === 'ar' ? 'إخراج النموذج من الخدمة' : 'Decommission model'}
      size="md"
      footer={<>
        <Button variant="ghost" onClick={onClose}>{lang === 'ar' ? 'إلغاء' : 'Cancel'}</Button>
        <Button variant="danger" disabled={busy} onClick={save}>
          {lang === 'ar' ? 'تأكيد الإخراج' : 'Confirm decommission'}
        </Button>
      </>}>
      <div style={{ display: 'grid', gap: 14 }}>
        <Row>
          <Label>{lang === 'ar' ? 'تاريخ الإخراج' : 'Decommission date'}</Label>
          <input type="date" value={date} onChange={e => setDate(e.target.value)} style={inputStyle} />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'السبب (EN)' : 'Reason (EN)'} *</Label>
          <textarea value={reasonEn} onChange={e => setReasonEn(e.target.value)}
            rows={3} style={{ ...inputStyle, fontFamily: 'inherit' }} />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'السبب (AR)' : 'Reason (AR)'}</Label>
          <textarea value={reasonAr} onChange={e => setReasonAr(e.target.value)}
            rows={3} style={{ ...inputStyle, fontFamily: 'inherit' }} />
        </Row>
      </div>
    </Modal>
  );
}

// ─── Tiny form primitives ─────────────────────────────────────────

const inputStyle: React.CSSProperties = {
  flex: 1,
  padding: '6px 8px',
  border: '1px solid var(--border)',
  borderRadius: 4,
  background: 'var(--surface)',
  color: 'var(--text)',
  fontSize: 13,
};

function Row({ children }: { children: React.ReactNode }) {
  return <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>{children}</div>;
}
function Label({ children }: { children: React.ReactNode }) {
  return (
    <div style={{
      minWidth: 160, fontSize: 12, paddingTop: 6,
      color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.4,
    }}>{children}</div>
  );
}
function Select({ value, onChange, options }: {
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string }[];
}) {
  return (
    <select value={value} onChange={e => onChange(e.target.value)} style={inputStyle}>
      {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  );
}

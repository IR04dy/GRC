import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  ShieldAlert, Activity, FileSearch, ClipboardCheck,
  BadgeAlert, Database, PowerOff, AlertTriangle, ListChecks, FileCheck2,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Field } from '../../components/ui/Field';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { DriftChart } from '../../components/viz/DriftChart';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { isAuthenticated } from '../../lib/auth';
import { ApiError, controlLinksApi, type ControlEvidenceDto } from '../../lib/api';
import { EvidenceReadinessPanel } from '../../components/ui/EvidenceReadinessPanel';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import {
  mlModelApi,
  approvalGateMissing, ML_NEXT_STAGES,
  ML_STAGE_LABEL, ML_STAGE_TONE,
  ML_TIER_LABEL, ML_TIER_TONE,
  ML_DOMAIN_LABEL,
  ML_AUDIT_TYPE_LABEL, ML_AUDIT_STATUS_LABEL, ML_AUDIT_STATUS_TONE,
  ML_DRIFT_TYPE_LABEL, ML_DRIFT_SEVERITY_LABEL, ML_DRIFT_SEVERITY_TONE,
  ML_COMPLIANCE_LABEL, ML_COMPLIANCE_TONE,
  ML_OVERSIGHT_LABEL, ML_RESIDENCY_LABEL, ML_TRANSFER_BASIS_LABEL,
  type MlModelResponse, type MlLifecycleStage,
  type MlModelAuditResponse, type MlAuditType, type MlAuditStatus,
  type MlDriftType, type MlDriftSeverity,
  type DriftObservationResponse,
} from '../../lib/aiGovernance';
import '../../styles/pages/ai-model-detail.css';

/**
 * AI/ML Model detail page — Sprint 11.
 *
 * Tabbed: Overview / Provenance / Compliance / Audits / Drift / Activity.
 * Lifecycle transitions are gated by the same {@code approvalGateMissing()}
 * helper that the server-side {@code MlModelService} enforces — the
 * disabled button + reason show the user *why* before they try and fail.
 */

type Tab = 'overview' | 'provenance' | 'compliance' | 'evidence' | 'audits' | 'drift' | 'activity';

export function AiModelDetailPage() {
  const { lang } = useI18n();
  const toast = useToast();
  const { externalId = '' } = useParams<{ externalId: string }>();
  const demoMode = !isAuthenticated();

  const [model, setModel] = useState<MlModelResponse | null>(null);
  const [audits, setAudits] = useState<MlModelAuditResponse[]>([]);
  const [drift, setDrift] = useState<DriftObservationResponse[]>([]);
  const [controlEv, setControlEv] = useState<ControlEvidenceDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [tab, setTab] = useState<Tab>('overview');
  const [showAuditModal, setShowAuditModal] = useState(false);
  const [showDecomModal, setShowDecomModal] = useState(false);
  const [showDriftModal, setShowDriftModal] = useState(false);

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
      controlLinksApi.evidence('AI_MODEL', m.id).then(setControlEv).catch(() => setControlEv([]));
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

  const gateMissing = useMemo(() => model ? approvalGateMissing(model, audits) : [], [model, audits]);
  const unackDrift = drift.filter(d => !d.isAcknowledged);
  // The forward lifecycle step (first non-decommission transition) is the
  // record's primary action — promote it to the header as `primary` instead
  // of leaving it as a ghost buried in the Overview tab (review DT4).
  const forwardStage = model
    ? (ML_NEXT_STAGES[model.lifecycleStage] ?? []).find(s => s !== 'DECOMMISSIONED') ?? null
    : null;
  const forwardBlocked = forwardStage === 'APPROVED_FOR_PROD' && gateMissing.length > 0;

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
      <PageHeader
        backTo="/ai/models"
        eyebrow={lang === 'ar' ? 'حوكمة الذكاء الاصطناعي' : 'AI Governance'}
        title={lang === 'ar' && model.titleAr ? model.titleAr : model.titleEn}
        sub={`${model.modelCode} · ${ML_DOMAIN_LABEL[model.businessDomain][lang === 'ar' ? 'ar' : 'en']}`}
        badges={
          <>
            <Badge tone={ML_TIER_TONE[model.riskTier]} dot>
              {ML_TIER_LABEL[model.riskTier][lang === 'ar' ? 'ar' : 'en']}
            </Badge>
            <Badge tone={ML_STAGE_TONE[model.lifecycleStage]} dot>
              {ML_STAGE_LABEL[model.lifecycleStage][lang === 'ar' ? 'ar' : 'en']}
            </Badge>
          </>
        }
        actions={
          model.lifecycleStage !== 'DECOMMISSIONED' ? (
            <>
              {forwardStage && (
                <Button
                  variant="primary"
                  disabled={forwardBlocked}
                  onClick={() => doTransition(forwardStage)}
                  title={forwardBlocked
                    ? (lang === 'ar' ? `محظور — ينقص: ${gateMissing.join(', ')}` : `Blocked — missing: ${gateMissing.join(', ')}`)
                    : undefined}
                >
                  → {ML_STAGE_LABEL[forwardStage][lang === 'ar' ? 'ar' : 'en']}
                </Button>
              )}
              <Button variant="danger" Icon={PowerOff} onClick={() => setShowDecomModal(true)}>
                {lang === 'ar' ? 'خارج الخدمة' : 'Decommission'}
              </Button>
            </>
          ) : undefined
        }
      />

      <div style={{ marginTop: 12 }}>
        <Card>
          <AttachmentsField entityType="AI_MODEL" entityId={model.id} />
        </Card>
      </div>
      <div style={{ marginTop: 12 }}>
        <Card>
          <ControlMappingPicker entityType="AI_MODEL" entityId={model.id} evidence={false} />
        </Card>
      </div>

      {/* Drift dashboard tile */}
      {unackDrift.length > 0 && (
        <Card padded>
          <div className="aimd-drift-head">
            <BadgeAlert size={18} color="var(--warning-fg)" />
            <div className="aimd-drift-head-title">
              {lang === 'ar'
                ? `${unackDrift.length} ملاحظة انحراف لم تُقَر`
                : `${unackDrift.length} unacknowledged drift observation${unackDrift.length === 1 ? '' : 's'}`}
            </div>
          </div>
          <div className="aimd-drift-list">
            {unackDrift.slice(0, 3).map(d => (
              <div key={d.externalId} className="aimd-drift-row">
                <Badge tone={ML_DRIFT_SEVERITY_TONE[d.severity]} dot>
                  {ML_DRIFT_SEVERITY_LABEL[d.severity][lang === 'ar' ? 'ar' : 'en']}
                </Badge>
                <span>{ML_DRIFT_TYPE_LABEL[d.observationType][lang === 'ar' ? 'ar' : 'en']}</span>
                <code className="aimd-drift-code">
                  {d.metricName} = {String(d.metricValue)}
                </code>
                <span className="aimd-drift-notes">
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
        <Tabs<Tab>
          active={tab}
          onChange={setTab}
          items={[
            { id: 'overview',   label: lang === 'ar' ? 'نظرة عامة' : 'Overview',       Icon: ListChecks },
            { id: 'provenance', label: lang === 'ar' ? 'المصدر' : 'Provenance',        Icon: Database },
            { id: 'compliance', label: lang === 'ar' ? 'الامتثال' : 'Compliance',       Icon: ShieldAlert },
            { id: 'evidence',   label: lang === 'ar' ? 'جاهزية الأدلة' : 'Evidence',    Icon: FileCheck2,     count: controlEv.length },
            { id: 'audits',     label: lang === 'ar' ? 'التدقيقات' : 'Audits',          Icon: ClipboardCheck, count: audits.length },
            { id: 'drift',      label: lang === 'ar' ? 'الانحراف' : 'Drift',            Icon: Activity,       count: drift.length },
            { id: 'activity',   label: lang === 'ar' ? 'سجل النشاط' : 'Activity',       Icon: FileSearch },
          ] satisfies TabItem<Tab>[]}
        />

        {tab === 'overview' && <OverviewTab model={model} lang={lang} gateMissing={gateMissing} onTransition={doTransition} />}
        {tab === 'provenance' && <ProvenanceTab model={model} lang={lang} />}
        {tab === 'compliance' && <ComplianceTab model={model} lang={lang} />}
        {tab === 'evidence' && <EvidenceReadinessPanel entityType="AI_MODEL" entityId={model.id} />}
        {tab === 'audits' && (
          <AuditsTab
            audits={audits} lang={lang}
            onAdd={() => setShowAuditModal(true)} />
        )}
        {tab === 'drift' && (
          <DriftTab drift={drift} lang={lang} onAck={ackDrift} onAdd={() => setShowDriftModal(true)} />
        )}
        {tab === 'activity' && (
          <div className="aimd-activity-empty">
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
      {showDriftModal && (
        <RecordDriftModal
          modelExternalId={model.externalId}
          onClose={() => setShowDriftModal(false)}
          onSaved={d => { setDrift([d, ...drift]); setShowDriftModal(false); load(); }}
        />
      )}
    </Shell>
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
  // Decommission is the header's danger action (confirmation modal) — keep it
  // out of this list so there's one decommission path, not an unconfirmed one.
  const nextStages = (ML_NEXT_STAGES[model.lifecycleStage] ?? []).filter(s => s !== 'DECOMMISSIONED');
  return (
    <div className="aimd-overview-grid">
      <div>
        <h3 className="aimd-h3">
          {lang === 'ar' ? 'وصف النموذج' : 'Model description'}
        </h3>
        <p className="aimd-desc">
          {lang === 'ar' && model.descriptionAr ? model.descriptionAr : (model.descriptionEn ?? '—')}
        </p>
        <Field variant="inline" label={lang === 'ar' ? 'نوع النموذج' : 'Model type'} value={model.modelType} />
        <Field variant="inline" label={lang === 'ar' ? 'الإصدار' : 'Version'} value={model.versionLabel ?? '—'} />
        <Field variant="inline" label={lang === 'ar' ? 'إطار العمل' : 'Framework'} value={model.framework ?? '—'} />
        <Field variant="inline" label={lang === 'ar' ? 'الاستضافة' : 'Hosting'} value={model.hosting ?? '—'} />
        <Field variant="inline" label={lang === 'ar' ? 'زمن الاستدلال (مللي ثانية)' : 'Inference latency (ms)'} value={model.inferenceLatencyMs?.toString() ?? '—'} />
        <Field variant="inline"
          label={lang === 'ar' ? 'الإشراف البشري' : 'Human oversight'}
          value={model.humanOversight ? ML_OVERSIGHT_LABEL[model.humanOversight][lang === 'ar' ? 'ar' : 'en'] : '—'} />
        {(lang === 'ar' ? model.humanOversightNotesAr : model.humanOversightNotesEn) && (
          <p className="aimd-muted" style={{ marginTop: 4, fontSize: 12 }}>
            {lang === 'ar' ? model.humanOversightNotesAr : model.humanOversightNotesEn}
          </p>
        )}
      </div>
      <div>
        <h3 className="aimd-h3">
          {lang === 'ar' ? 'الانتقال إلى مرحلة' : 'Transition'}
        </h3>
        {nextStages.length === 0 ? (
          <p className="aimd-muted">
            {lang === 'ar' ? 'لا توجد انتقالات متاحة من هذه المرحلة.' : 'No transitions available from this stage.'}
          </p>
        ) : (
          <div className="aimd-trans-col">
            {nextStages.map(s => {
              const blocked = s === 'APPROVED_FOR_PROD' && gateMissing.length > 0;
              return (
                <Button
                  key={s}
                  variant="secondary"
                  disabled={blocked}
                  onClick={() => onTransition(s)}
                  title={blocked
                    ? (lang === 'ar' ? `محظور — ينقص: ${gateMissing.join(', ')}` : `Blocked — missing: ${gateMissing.join(', ')}`)
                    : undefined}
                >
                  → {ML_STAGE_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                  {blocked && <AlertTriangle size={12} className="ms-xs" />}
                </Button>
              );
            })}
          </div>
        )}
        {gateMissing.length > 0 && (
          <div className="aimd-gate-warn">
            <strong>{lang === 'ar' ? 'بوابة الاعتماد:' : 'Approval gate:'}</strong>
            <br />
            {lang === 'ar'
              ? `النماذج ذات الخطورة ${ML_TIER_LABEL[model.riskTier].ar} لا يمكن اعتمادها للإنتاج حتى تكتمل:`
              : `${ML_TIER_LABEL[model.riskTier].en}-tier models cannot enter APPROVED_FOR_PROD until you complete:`}
            <ul className="aimd-gate-list">
              {gateMissing.map(k => <li key={k}><code>{k}</code></li>)}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}

// ─── Provenance tab ────────────────────────────────────────────────

function ProvenanceTab({ model, lang }: { model: MlModelResponse; lang: 'en' | 'ar' }) {
  return (
    <div>
      <h3 className="aimd-h3">
        {lang === 'ar' ? 'مصدر بيانات التدريب' : 'Training data provenance'}
      </h3>
      <p className="aimd-desc">
        {lang === 'ar' && model.trainingDataProvenanceAr
          ? model.trainingDataProvenanceAr
          : (model.trainingDataProvenanceEn ?? '—')}
      </p>
      <Field variant="inline" label={lang === 'ar' ? 'حجم البيانات (سجلات)' : 'Data size (records)'}
             value={model.trainingDataSizeRecords?.toLocaleString() ?? '—'} />
      <Field variant="inline" label={lang === 'ar' ? 'بداية الفترة' : 'Period start'}
             value={model.trainingDataPeriodStart ?? '—'} />
      <Field variant="inline" label={lang === 'ar' ? 'نهاية الفترة' : 'Period end'}
             value={model.trainingDataPeriodEnd ?? '—'} />
      <Field variant="inline" label={lang === 'ar' ? 'عدد السمات' : 'Feature count'}
             value={model.featureCount?.toString() ?? '—'} />
      <Field variant="inline" label={lang === 'ar' ? 'بيانات خارجية' : 'External dataset used'}
             value={model.externalDatasetUsed ? (lang === 'ar' ? 'نعم' : 'Yes') : (lang === 'ar' ? 'لا' : 'No')} />
      {model.externalDatasetUsed && model.externalDatasetProvenance && (
        <Field variant="inline" label={lang === 'ar' ? 'مصدر البيانات الخارجية' : 'External dataset provenance'}
               value={model.externalDatasetProvenance} />
      )}
    </div>
  );
}

// ─── Compliance tab ────────────────────────────────────────────────

function ComplianceTab({ model, lang }: { model: MlModelResponse; lang: 'en' | 'ar' }) {
  return (
    <div className="aimd-comp-grid">
      <ComplianceCard
        title="SDAIA AI Ethics (2023)"
        status={model.sdaiaAiEthicsCompliance}
        lang={lang} />
      <ComplianceCard
        title="SAMA AI Governance (draft)"
        status={model.samaAiPrinciplesCompliance}
        lang={lang} />
      <div>
        <h3 className="aimd-h3">{lang === 'ar' ? 'الأعلام التنظيمية' : 'Regulatory flags'}</h3>
        <Field variant="inline" label={lang === 'ar' ? 'تقييم الأثر PDPL' : 'PDPL DPIA'}
               value={model.hasPdplDpia ? '✓' : '—'} />
        <Field variant="inline" label={lang === 'ar' ? 'تدقيق العدالة' : 'Fairness audit'}
               value={model.hasFairnessAudit ? `✓ (${model.lastFairnessAuditDate ?? ''})` : '—'} />
        <Field variant="inline" label={lang === 'ar' ? 'وثيقة قابلية التفسير' : 'Explainability doc'}
               value={model.hasExplainabilityDoc ? '✓' : '—'} />
        <Field variant="inline" label={lang === 'ar' ? 'عالي الخطورة EU AI Act' : 'EU AI Act high-risk'}
               value={model.isHighRiskEuAiAct ? '✓ (Annex III)' : '—'} />
        <Field variant="inline" label={lang === 'ar' ? 'الإشراف البشري' : 'Human oversight'}
               value={model.humanOversight ? ML_OVERSIGHT_LABEL[model.humanOversight][lang === 'ar' ? 'ar' : 'en'] : '—'} />
        <Field variant="inline" label={lang === 'ar' ? 'موطن البيانات' : 'Data residency'}
               value={model.dataResidency ? ML_RESIDENCY_LABEL[model.dataResidency][lang === 'ar' ? 'ar' : 'en'] : '—'} />
        <Field variant="inline" label={lang === 'ar' ? 'أساس النقل عبر الحدود (PDPL م.29)' : 'Cross-border basis (PDPL Art.29)'}
               value={model.crossBorderTransferBasis ? ML_TRANSFER_BASIS_LABEL[model.crossBorderTransferBasis][lang === 'ar' ? 'ar' : 'en'] : '—'} />
      </div>
    </div>
  );
}

function ComplianceCard({ title, status, lang }: { title: string; status: MlModelResponse['sdaiaAiEthicsCompliance']; lang: 'en' | 'ar' }) {
  return (
    <Card padded>
      <div className="aimd-comp-eyebrow">
        {title}
      </div>
      <div className="aimd-comp-badge">
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
      <div className="aimd-audits-head">
        <h3 className="aimd-audits-head-title">
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
          <table className="aimd-table">
            <thead>
              <tr>
                <th>{lang === 'ar' ? 'النوع' : 'Type'}</th>
                <th>{lang === 'ar' ? 'التاريخ' : 'Date'}</th>
                <th>{lang === 'ar' ? 'النتيجة' : 'Status'}</th>
                <th>{lang === 'ar' ? 'المنهجية' : 'Methodology'}</th>
                <th>CAPA</th>
              </tr>
            </thead>
            <tbody>
              {audits.map(a => (
                <tr key={a.externalId}>
                  <td>
                    <Badge tone="info">{ML_AUDIT_TYPE_LABEL[a.auditType][lang === 'ar' ? 'ar' : 'en']}</Badge>
                  </td>
                  <td className="aimd-cell-mono">{a.auditDate}</td>
                  <td>
                    <Badge tone={ML_AUDIT_STATUS_TONE[a.status]} dot>
                      {ML_AUDIT_STATUS_LABEL[a.status][lang === 'ar' ? 'ar' : 'en']}
                    </Badge>
                  </td>
                  <td className="aimd-cell-text">
                    {(lang === 'ar' && a.methodologyAr ? a.methodologyAr : a.methodologyEn ?? '').slice(0, 80)}
                    {((lang === 'ar' && a.methodologyAr ? a.methodologyAr : a.methodologyEn ?? '').length > 80) ? '…' : ''}
                  </td>
                  <td className="aimd-cell-mono">
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

function DriftTab({ drift, lang, onAck, onAdd }: {
  drift: DriftObservationResponse[];
  lang: 'en' | 'ar';
  onAck: (d: DriftObservationResponse) => void;
  onAdd: () => void;
}) {
  // Dominant metric → the drift-over-time chart (the watsonx-class hero).
  const byMetric = new Map<string, DriftObservationResponse[]>();
  for (const d of drift) {
    const k = d.metricName ?? '—';
    const arr = byMetric.get(k) ?? [];
    arr.push(d); byMetric.set(k, arr);
  }
  const dominant = [...byMetric.entries()].sort((a, b) => b[1].length - a[1].length)[0];
  const head = (
    <div className="aimd-audits-head">
      <h3 className="aimd-audits-head-title">
        {lang === 'ar' ? 'ملاحظات الانحراف' : 'Drift observations'}
      </h3>
      <Button variant="primary" onClick={onAdd}>
        {lang === 'ar' ? 'تسجيل انحراف' : 'Record drift'}
      </Button>
    </div>
  );
  if (drift.length === 0) {
    return (
      <div>
        {head}
        <EmptyState Icon={Activity}
          title={lang === 'ar' ? 'لا توجد ملاحظات انحراف' : 'No drift observations'}
          sub={lang === 'ar' ? 'سجّل ملاحظة انحراف يدوياً، أو تظهر تلقائياً عند رصدها.' : 'Record a drift observation manually, or they appear automatically when detected.'} />
      </div>
    );
  }
  return (
    <div>
      {head}
      {dominant && dominant[1].length >= 2 && (
        <div style={{ marginBottom: 14 }}>
          <div style={{ font: '700 12px var(--font-sans)', color: 'var(--ink-2)', marginBottom: 6 }}>
            {lang === 'ar' ? `انحراف المقياس: ${dominant[0]}` : `Metric drift: ${dominant[0]}`}
          </div>
          <DriftChart ar={lang === 'ar'} metricName={dominant[0]}
            points={dominant[1].map(d => ({
              observedAt: d.observedAt, value: d.metricValue,
              baseline: d.baselineValue, threshold: d.driftThreshold,
              severity: d.severity, acknowledged: d.isAcknowledged,
            }))} />
        </div>
      )}
      <table className="aimd-table">
      <thead>
        <tr>
          <th>{lang === 'ar' ? 'وقت الرصد' : 'Observed'}</th>
          <th>{lang === 'ar' ? 'النوع' : 'Type'}</th>
          <th>{lang === 'ar' ? 'الشدة' : 'Severity'}</th>
          <th>{lang === 'ar' ? 'المقياس' : 'Metric'}</th>
          <th>{lang === 'ar' ? 'القيمة' : 'Value'}</th>
          <th>{lang === 'ar' ? 'الأساس' : 'Baseline'}</th>
          <th>{lang === 'ar' ? 'الحدّ' : 'Threshold'}</th>
          <th>{lang === 'ar' ? 'الحالة' : 'Status'}</th>
        </tr>
      </thead>
      <tbody>
        {drift.map(d => (
          <tr key={d.externalId}>
            <td className="aimd-cell-mono">
              {d.observedAt?.slice(0, 16)}
            </td>
            <td>
              {ML_DRIFT_TYPE_LABEL[d.observationType][lang === 'ar' ? 'ar' : 'en']}
            </td>
            <td>
              <Badge tone={ML_DRIFT_SEVERITY_TONE[d.severity]} dot>
                {ML_DRIFT_SEVERITY_LABEL[d.severity][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
            </td>
            <td className="aimd-cell-mono">{d.metricName}</td>
            <td className="aimd-cell-mono">{String(d.metricValue)}</td>
            <td className="aimd-cell-mono">
              {d.baselineValue == null ? '—' : String(d.baselineValue)}
            </td>
            <td className="aimd-cell-mono">
              {d.driftThreshold == null ? '—' : String(d.driftThreshold)}
            </td>
            <td>
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
    </div>
  );
}

// ─── Record-audit modal ────────────────────────────────────────────

const AUDIT_METRIC_HELPERS: Record<MlAuditType, string[]> = {
  AIA:            ['fundamental_rights_score', 'affected_population', 'residual_risk_rating'],
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
      <div className="aimd-form">
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
            className="aimd-input" />
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
            className="aimd-input" />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'المنهجية' : 'Methodology'}</Label>
          <textarea value={methodologyEn} onChange={e => setMethodologyEn(e.target.value)}
            rows={3} className="aimd-input aimd-textarea" />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'النتائج' : 'Findings'}</Label>
          <textarea value={findingsEn} onChange={e => setFindingsEn(e.target.value)}
            rows={3} className="aimd-input aimd-textarea" />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'المقاييس (JSON)' : 'Metrics (JSON)'}</Label>
          <div className="aimd-metrics-wrap">
            <textarea value={metricsJson} onChange={e => setMetricsJson(e.target.value)}
              rows={4} className="aimd-input aimd-textarea--mono" />
            <div className="aimd-metrics-hint">
              {lang === 'ar' ? 'مفاتيح شائعة:' : 'Common keys:'} {helpers.map(h => <code key={h} className="aimd-metrics-key">{h}</code>)}
            </div>
          </div>
        </Row>
      </div>
    </Modal>
  );
}

// ─── Record-drift modal ────────────────────────────────────────────

// Common metric names per drift family — clickable hints that prefill the
// metric-name field (mirrors AUDIT_METRIC_HELPERS for the audit modal).
const DRIFT_METRIC_HELPERS: Record<MlDriftType, string[]> = {
  DATA_DRIFT:              ['psi', 'kl_divergence', 'feature_drift_score'],
  CONCEPT_DRIFT:           ['prediction_drift', 'concept_shift_score'],
  PERFORMANCE_DEGRADATION: ['accuracy', 'auc', 'f1_score', 'precision'],
  FAIRNESS_REGRESSION:     ['demographic_parity_diff', 'equal_opportunity_diff'],
};

function RecordDriftModal({ modelExternalId, onClose, onSaved }: {
  modelExternalId: string;
  onClose: () => void;
  onSaved: (d: DriftObservationResponse) => void;
}) {
  const { lang } = useI18n();
  const toast = useToast();
  const [observationType, setObservationType] = useState<MlDriftType>('DATA_DRIFT');
  const [severity, setSeverity] = useState<MlDriftSeverity>('MODERATE');
  const [observedAt, setObservedAt] = useState(new Date().toISOString().slice(0, 10));
  const [metricName, setMetricName] = useState('');
  const [metricValue, setMetricValue] = useState('');
  const [baselineValue, setBaselineValue] = useState('');
  const [driftThreshold, setDriftThreshold] = useState('');
  const [notesEn, setNotesEn] = useState('');
  const [notesAr, setNotesAr] = useState('');
  const [busy, setBusy] = useState(false);

  async function save() {
    if (!metricName.trim()) {
      toast.danger(lang === 'ar' ? 'اسم المقياس مطلوب' : 'Metric name is required');
      return;
    }
    const mv = Number(metricValue);
    if (metricValue.trim() === '' || Number.isNaN(mv)) {
      toast.danger(lang === 'ar' ? 'القيمة المرصودة مطلوبة ويجب أن تكون رقماً' : 'Observed value is required and must be numeric');
      return;
    }
    try {
      setBusy(true);
      // Stamp the observed date at noon to dodge any midnight TZ rollover.
      const out = await mlModelApi.recordDrift(modelExternalId, {
        observedAt: `${observedAt}T12:00:00`,
        observationType,
        severity,
        metricName: metricName.trim(),
        metricValue: mv,
        baselineValue: baselineValue.trim() === '' ? undefined : Number(baselineValue),
        driftThreshold: driftThreshold.trim() === '' ? undefined : Number(driftThreshold),
        notesEn: notesEn.trim() || undefined,
        notesAr: notesAr.trim() || undefined,
      });
      toast.success(lang === 'ar' ? 'تم تسجيل ملاحظة الانحراف' : 'Drift observation recorded');
      onSaved(out);
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Could not record drift');
    } finally {
      setBusy(false);
    }
  }

  const helpers = DRIFT_METRIC_HELPERS[observationType];

  return (
    <Modal open onClose={onClose}
      title={lang === 'ar' ? 'تسجيل ملاحظة انحراف' : 'Record drift observation'} size="lg"
      footer={<>
        <Button variant="ghost" onClick={onClose}>{lang === 'ar' ? 'إلغاء' : 'Cancel'}</Button>
        <Button variant="primary" disabled={busy} onClick={save}>
          {lang === 'ar' ? 'حفظ' : 'Save'}
        </Button>
      </>}>
      <div className="aimd-form">
        <Row>
          <Label>{lang === 'ar' ? 'نوع الانحراف' : 'Drift type'}</Label>
          <Select value={observationType} onChange={v => setObservationType(v as MlDriftType)}
            options={(Object.keys(ML_DRIFT_TYPE_LABEL) as MlDriftType[]).map(k => ({
              value: k, label: ML_DRIFT_TYPE_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
            }))} />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'الشدة' : 'Severity'}</Label>
          <Select value={severity} onChange={v => setSeverity(v as MlDriftSeverity)}
            options={(Object.keys(ML_DRIFT_SEVERITY_LABEL) as MlDriftSeverity[]).map(k => ({
              value: k, label: ML_DRIFT_SEVERITY_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
            }))} />
        </Row>
        {severity === 'CRITICAL' && (
          <div className="aimd-gate-warn">
            <AlertTriangle size={13} className="me-xs" />
            {lang === 'ar'
              ? 'الشدة الحرجة تفتح بلاغاً تلقائياً وتُعيد النموذج إلى مرحلة المراجعة (REVISION).'
              : 'Critical severity auto-opens an Issue and returns the model to REVISION.'}
          </div>
        )}
        <Row>
          <Label>{lang === 'ar' ? 'تاريخ الرصد' : 'Observed date'}</Label>
          <input type="date" value={observedAt} onChange={e => setObservedAt(e.target.value)} className="aimd-input" />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'اسم المقياس' : 'Metric name'} *</Label>
          <div className="aimd-metrics-wrap">
            <input value={metricName} onChange={e => setMetricName(e.target.value)}
              placeholder={helpers[0]} className="aimd-input" />
            <div className="aimd-metrics-hint">
              {lang === 'ar' ? 'مقاييس شائعة:' : 'Common metrics:'}{' '}
              {helpers.map(h => (
                <code key={h} className="aimd-metrics-key" style={{ cursor: 'pointer' }}
                  onClick={() => setMetricName(h)}>{h}</code>
              ))}
            </div>
          </div>
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'القيمة المرصودة' : 'Observed value'} *</Label>
          <input type="number" step="any" value={metricValue} onChange={e => setMetricValue(e.target.value)}
            className="aimd-input" />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'القيمة الأساس' : 'Baseline value'}</Label>
          <input type="number" step="any" value={baselineValue} onChange={e => setBaselineValue(e.target.value)}
            placeholder={lang === 'ar' ? '(اختياري)' : '(optional)'} className="aimd-input" />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'حدّ الانحراف' : 'Drift threshold'}</Label>
          <input type="number" step="any" value={driftThreshold} onChange={e => setDriftThreshold(e.target.value)}
            placeholder={lang === 'ar' ? '(اختياري)' : '(optional)'} className="aimd-input" />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'ملاحظات (EN)' : 'Notes (EN)'}</Label>
          <textarea value={notesEn} onChange={e => setNotesEn(e.target.value)}
            rows={2} className="aimd-input aimd-textarea" />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'ملاحظات (AR)' : 'Notes (AR)'}</Label>
          <textarea value={notesAr} onChange={e => setNotesAr(e.target.value)}
            rows={2} dir="rtl" className="aimd-input aimd-textarea" />
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
      <div className="aimd-form">
        <Row>
          <Label>{lang === 'ar' ? 'تاريخ الإخراج' : 'Decommission date'}</Label>
          <input type="date" value={date} onChange={e => setDate(e.target.value)} className="aimd-input" />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'السبب (EN)' : 'Reason (EN)'} *</Label>
          <textarea value={reasonEn} onChange={e => setReasonEn(e.target.value)}
            rows={3} className="aimd-input aimd-textarea" />
        </Row>
        <Row>
          <Label>{lang === 'ar' ? 'السبب (AR)' : 'Reason (AR)'}</Label>
          <textarea value={reasonAr} onChange={e => setReasonAr(e.target.value)}
            rows={3} className="aimd-input aimd-textarea" />
        </Row>
      </div>
    </Modal>
  );
}

// ─── Tiny form primitives ─────────────────────────────────────────

function Row({ children }: { children: React.ReactNode }) {
  return <div className="aimd-form-row">{children}</div>;
}
function Label({ children }: { children: React.ReactNode }) {
  return (
    <div className="aimd-form-label">{children}</div>
  );
}
function Select({ value, onChange, options }: {
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string }[];
}) {
  return (
    <select value={value} onChange={e => onChange(e.target.value)} className="aimd-input">
      {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  );
}

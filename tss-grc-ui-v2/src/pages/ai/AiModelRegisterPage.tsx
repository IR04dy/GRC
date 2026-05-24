import { useEffect, useMemo, useState } from 'react';
import {
  Bot, Cpu, Shield, AlertTriangle, ShieldAlert,
  Activity, CheckCircle2, BadgeAlert,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { isAuthenticated } from '../../lib/auth';
import { ApiError } from '../../lib/api';
import {
  mlModelApi,
  aggregateMlRegistry,
  ML_STAGE_LABEL, ML_STAGE_TONE,
  ML_TIER_LABEL, ML_TIER_TONE,
  ML_DOMAIN_LABEL,
  ML_COMPLIANCE_TONE, ML_COMPLIANCE_LABEL,
  type MlModelResponse, type MlLifecycleStage, type MlRiskTier, type MlBusinessDomain,
  type MlComplianceStatus,
} from '../../lib/aiGovernance';

/**
 * AI/ML Model Registry — Sprint 11.
 *
 * Tri-track regulator surface (SDAIA AI Ethics 2023, SAMA AI
 * Governance Principles Dec-2024, EU AI Act Article 16) for every
 * model the bank has in production. Saudi-native page title:
 * **سجل نماذج الذكاء الاصطناعي**  — not the literal
 * "سجل نماذج التعلم الآلي".
 */

const STAGES_ORDER: ('ALL' | MlLifecycleStage)[] = [
  'ALL', 'PROPOSED', 'DEVELOPMENT', 'VALIDATION', 'APPROVED_FOR_PROD',
  'IN_PRODUCTION', 'MONITORING', 'REVISION', 'DECOMMISSIONED',
];
const TIERS_ORDER: ('ALL' | MlRiskTier)[] = ['ALL', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

export function AiModelRegisterPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<MlModelResponse[]>([]);
  const [unackDrift, setUnackDrift] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Filter state.
  const [filterStage, setFilterStage] = useState<'ALL' | MlLifecycleStage>('ALL');
  const [filterTier,  setFilterTier]  = useState<'ALL' | MlRiskTier>('ALL');
  const [filterDomain, setFilterDomain] = useState<'ALL' | MlBusinessDomain>('ALL');
  const [filterSdaia, setFilterSdaia]   = useState<'ALL' | MlComplianceStatus>('ALL');
  const [filterEuAi, setFilterEuAi]     = useState<'ALL' | 'YES' | 'NO'>('ALL');

  async function load() {
    if (demoMode) {
      setRows([]);
      setUnackDrift(0);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const list = await mlModelApi.list();
      setRows(list);
      // Approximate the unacknowledged-drift count by summing the per-model
      // drift list — there's no tenant-wide aggregator endpoint yet, but
      // the in-production list is short enough that this is fine.
      let unack = 0;
      const prodish = list.filter(m =>
        m.lifecycleStage === 'IN_PRODUCTION' ||
        m.lifecycleStage === 'MONITORING' ||
        m.lifecycleStage === 'REVISION');
      for (const m of prodish) {
        try {
          const drifts = await mlModelApi.listDrift(m.externalId);
          unack += drifts.filter(d => !d.isAcknowledged).length;
        } catch {
          // ignore — count is best-effort
        }
      }
      setUnackDrift(unack);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load AI/ML models');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  // Filter the rows in-memory — the dataset is small (registry, not transactions).
  const filtered = useMemo(() => rows.filter(m => {
    if (filterStage !== 'ALL' && m.lifecycleStage !== filterStage) return false;
    if (filterTier  !== 'ALL' && m.riskTier !== filterTier) return false;
    if (filterDomain !== 'ALL' && m.businessDomain !== filterDomain) return false;
    if (filterSdaia !== 'ALL' && m.sdaiaAiEthicsCompliance !== filterSdaia) return false;
    if (filterEuAi === 'YES' && !m.isHighRiskEuAiAct) return false;
    if (filterEuAi === 'NO' && m.isHighRiskEuAiAct) return false;
    return true;
  }), [rows, filterStage, filterTier, filterDomain, filterSdaia, filterEuAi]);

  const stats = useMemo(() => aggregateMlRegistry(rows, unackDrift), [rows, unackDrift]);

  const cols: Column<MlModelResponse>[] = [
    {
      header: lang === 'ar' ? 'الرمز' : 'Code',
      cell: r => r.modelCode,
      mono: true, width: '170px',
    },
    {
      header: lang === 'ar' ? 'النموذج' : 'Model',
      cell: r => (
        <div className="text-bold">
          {lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}
        </div>
      ),
    },
    {
      header: lang === 'ar' ? 'المجال' : 'Domain', width: '180px',
      cell: r => (
        <Badge tone="info">{ML_DOMAIN_LABEL[r.businessDomain][lang === 'ar' ? 'ar' : 'en']}</Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'مستوى الخطورة' : 'Risk tier', width: '130px', align: 'center',
      cell: r => (
        <Badge tone={ML_TIER_TONE[r.riskTier]} dot>
          {ML_TIER_LABEL[r.riskTier][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'المرحلة' : 'Stage', width: '160px',
      cell: r => (
        <Badge tone={ML_STAGE_TONE[r.lifecycleStage]} dot>
          {ML_STAGE_LABEL[r.lifecycleStage][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: 'SDAIA', width: '120px', align: 'center',
      cell: r => (
        <Badge tone={ML_COMPLIANCE_TONE[r.sdaiaAiEthicsCompliance]}>
          {ML_COMPLIANCE_LABEL[r.sdaiaAiEthicsCompliance][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: 'SAMA', width: '120px', align: 'center',
      cell: r => (
        <Badge tone={ML_COMPLIANCE_TONE[r.samaAiPrinciplesCompliance]}>
          {ML_COMPLIANCE_LABEL[r.samaAiPrinciplesCompliance][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: 'EU AI', width: '90px', align: 'center',
      cell: r => r.isHighRiskEuAiAct
        ? <Badge tone="warning">{lang === 'ar' ? 'عالي' : 'High'}</Badge>
        : <span>—</span>,
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'سجل نماذج الذكاء الاصطناعي' : 'AI/ML Model Registry'}
      sub={lang === 'ar' ? 'SDAIA · البنك المركزي · نظام الذكاء الاصطناعي الأوروبي' : 'SDAIA · SAMA · EU AI Act'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'حوكمة الذكاء الاصطناعي' : 'AI Governance'}
        title={lang === 'ar' ? 'سجل نماذج الذكاء الاصطناعي' : 'AI/ML Model Registry'}
        sub={lang === 'ar'
          ? 'جرد مركزي لكل نموذج ذكاء اصطناعي أو تعلم آلي في الإنتاج، مع المصدر وتدقيق العدالة ومراقبة الانحراف، وفق مبادئ سدايا لأخلاقيات الذكاء الاصطناعي ومبادئ البنك المركزي السعودي لحوكمة الذكاء الاصطناعي والمادة 16 من نظام الذكاء الاصطناعي الأوروبي.'
          : 'Central inventory of every AI/ML model in production, with provenance, fairness audits, and drift monitoring, per SDAIA AI Ethics Principles, SAMA AI Governance Principles, and EU AI Act Article 16.'}
        actions={demoMode ? <Badge tone="warning">{lang === 'ar' ? 'وضع العرض' : 'Demo mode'}</Badge> : null}
      />

      <div className="kpi-grid-sm">
        <Kpi Icon={Bot}          label={lang === 'ar' ? 'إجمالي النماذج' : 'Total models'}
             value={stats.total} tone="primary" />
        <Kpi Icon={Activity}     label={lang === 'ar' ? 'في الإنتاج' : 'In production'}
             value={stats.inProduction} tone="success" />
        <Kpi Icon={ShieldAlert}  label={lang === 'ar' ? 'حرج/مرتفع' : 'Critical / High'}
             value={stats.highOrCritical} tone="warning" />
        <Kpi Icon={AlertTriangle} label={lang === 'ar' ? 'بدون تدقيق عدالة' : 'No fairness audit'}
             value={stats.missingFairnessAudit}
             tone={stats.missingFairnessAudit > 0 ? 'danger' : 'success'} />
        <Kpi Icon={BadgeAlert}   label={lang === 'ar' ? 'انحراف غير مُقَرّ' : 'Unacknowledged drift'}
             value={stats.unacknowledgedDrift}
             tone={stats.unacknowledgedDrift > 0 ? 'warning' : 'success'} />
        <Kpi Icon={Cpu}          label={lang === 'ar' ? 'EU AI Act عالي الخطورة' : 'EU AI Act high-risk'}
             value={stats.euAiActHighRisk} tone="accent" />
        <Kpi Icon={CheckCircle2} label={lang === 'ar' ? 'مُمتثل لسدايا' : 'SDAIA compliant'}
             value={stats.sdaiaCompliant} tone="success" />
        <Kpi Icon={Shield}       label={lang === 'ar' ? 'مُمتثل للبنك المركزي' : 'SAMA compliant'}
             value={stats.samaCompliant} tone="success" />
      </div>

      <Card padded>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <FilterSelect
            label={lang === 'ar' ? 'المرحلة' : 'Stage'}
            value={filterStage}
            onChange={v => setFilterStage(v as 'ALL' | MlLifecycleStage)}
            options={STAGES_ORDER.map(s => ({
              value: s,
              label: s === 'ALL'
                ? (lang === 'ar' ? 'الكل' : 'All')
                : ML_STAGE_LABEL[s][lang === 'ar' ? 'ar' : 'en'],
            }))} />
          <FilterSelect
            label={lang === 'ar' ? 'الخطورة' : 'Tier'}
            value={filterTier}
            onChange={v => setFilterTier(v as 'ALL' | MlRiskTier)}
            options={TIERS_ORDER.map(t => ({
              value: t,
              label: t === 'ALL'
                ? (lang === 'ar' ? 'الكل' : 'All')
                : ML_TIER_LABEL[t][lang === 'ar' ? 'ar' : 'en'],
            }))} />
          <FilterSelect
            label={lang === 'ar' ? 'المجال' : 'Domain'}
            value={filterDomain}
            onChange={v => setFilterDomain(v as 'ALL' | MlBusinessDomain)}
            options={[
              { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
              ...(Object.keys(ML_DOMAIN_LABEL) as MlBusinessDomain[]).map(d => ({
                value: d,
                label: ML_DOMAIN_LABEL[d][lang === 'ar' ? 'ar' : 'en'],
              })),
            ]} />
          <FilterSelect
            label="SDAIA"
            value={filterSdaia}
            onChange={v => setFilterSdaia(v as 'ALL' | MlComplianceStatus)}
            options={[
              { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
              { value: 'COMPLIANT',     label: lang === 'ar' ? 'مُمتثل' : 'Compliant' },
              { value: 'PARTIAL',       label: lang === 'ar' ? 'امتثال جزئي' : 'Partial' },
              { value: 'NON_COMPLIANT', label: lang === 'ar' ? 'غير مُمتثل' : 'Non-compliant' },
              { value: 'NOT_ASSESSED',  label: lang === 'ar' ? 'لم يُقيَّم' : 'Not assessed' },
            ]} />
          <FilterSelect
            label="EU AI Act"
            value={filterEuAi}
            onChange={v => setFilterEuAi(v as 'ALL' | 'YES' | 'NO')}
            options={[
              { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
              { value: 'YES', label: lang === 'ar' ? 'عالي الخطورة' : 'High-risk only' },
              { value: 'NO',  label: lang === 'ar' ? 'غير عالي الخطورة' : 'Not high-risk' },
            ]} />
        </div>
      </Card>

      <Card padded={false}>
        {error && (
          <div style={{
            padding: 12, fontSize: 13,
            color: 'var(--danger-fg)',
            background: 'var(--danger-bg)',
            borderBottom: '1px solid var(--danger-border)',
          }}>{error}</div>
        )}
        {loading
          ? <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : <DataTable
              columns={cols}
              rows={filtered}
              rowHref={r => `/ai/models/${r.externalId}`}
              empty={<EmptyState
                Icon={Bot}
                title={lang === 'ar' ? 'لا توجد نماذج بعد' : 'No models yet'}
                sub={lang === 'ar'
                  ? 'سيظهر هنا كل نموذج ذكاء اصطناعي أو تعلم آلي يخدم قراراً بنكياً منظَّماً، مع المصدر وتدقيق العدالة وحالة الامتثال.'
                  : 'Every AI/ML model serving a regulated banking decision will appear here, with provenance, fairness audits, and compliance status.'} />}
            />
        }
      </Card>
    </Shell>
  );
}

interface FilterSelectProps {
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string }[];
}

function FilterSelect({ label, value, onChange, options }: FilterSelectProps) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 140 }}>
      <label style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.4 }}>
        {label}
      </label>
      <select
        value={value}
        onChange={e => onChange(e.target.value)}
        style={{
          padding: '6px 8px',
          border: '1px solid var(--border)',
          borderRadius: 4,
          background: 'var(--surface)',
          color: 'var(--text)',
          fontSize: 13,
        }}
      >
        {options.map(o => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
    </div>
  );
}

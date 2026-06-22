import { useEffect, useMemo, useState } from 'react';
import { Database, Globe2, ShieldAlert, Clock, FileSignature } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { isAuthenticated } from '../../lib/auth';
import { ApiError } from '../../lib/api';
import {
  subprocessorsApi,
  LIFECYCLE_LABEL, LIFECYCLE_TONE,
  SAFEGUARD_LABEL, SAFEGUARD_TONE,
  AUTH_TYPE_LABEL,
  VOLUME_LABEL,
  daysUntilReview,
  type SubprocessorResponse, type LifecycleStage,
  type CrossBorderSafeguardType,
} from '../../lib/subprocessors';

/**
 * PDPL Sub-processor Register — Sprint 11.
 *
 * <p>Saudi-native title: "سجل معالجي البيانات الفرعيين".
 * Operator-natural Arabic — not a literal translation of "sub-processor
 * register".
 *
 * <p>Anchors PDPL Article 30 (Implementing Regulation Article 39) +
 * SDAIA Cross-Border Data Transfer Regulations 2023.
 */
export function SubprocessorsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<SubprocessorResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [filterLifecycle, setFilterLifecycle] = useState<'ALL' | LifecycleStage>('ALL');
  const [filterCountry, setFilterCountry] = useState<'ALL' | 'SA' | 'NON_SA'>('ALL');
  const [filterSafeguard, setFilterSafeguard] = useState<'ALL' | CrossBorderSafeguardType>('ALL');

  async function load() {
    if (demoMode) { setRows([]); return; }
    setLoading(true);
    setError(null);
    try {
      const r = await subprocessorsApi.list();
      setRows(r);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load sub-processors');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  // ─── KPIs ──────────────────────────────────────────────────────────

  const kpis = useMemo(() => {
    const total = rows.length;
    const approved = rows.filter(r => r.lifecycleStage === 'APPROVED').length;
    const crossBorder = rows.filter(r => r.countryOfProcessing !== 'SA' && r.lifecycleStage === 'APPROVED').length;
    const overdue = rows.filter(r => {
      if (!r.nextReviewDueAt) return false;
      const d = daysUntilReview(r.nextReviewDueAt);
      return d !== null && d < 0 && r.lifecycleStage !== 'RETIRED';
    }).length;
    const noDpa = rows.filter(r => !r.hasDpaInPlace && r.lifecycleStage === 'APPROVED').length;
    return { total, approved, crossBorder, overdue, noDpa };
  }, [rows]);

  // ─── Filtering ─────────────────────────────────────────────────────

  const filtered = useMemo(() => rows.filter(r => {
    if (filterLifecycle !== 'ALL' && r.lifecycleStage !== filterLifecycle) return false;
    if (filterCountry === 'SA' && r.countryOfProcessing !== 'SA') return false;
    if (filterCountry === 'NON_SA' && r.countryOfProcessing === 'SA') return false;
    if (filterSafeguard !== 'ALL' && r.crossBorderSafeguardType !== filterSafeguard) return false;
    return true;
  }), [rows, filterLifecycle, filterCountry, filterSafeguard]);

  // ─── Columns ───────────────────────────────────────────────────────

  const cols: Column<SubprocessorResponse>[] = [
    {
      header: lang === 'ar' ? 'الرمز' : 'Code', width: '150px',
      cell: r => <code style={{ fontSize: 12 }}>{r.subprocessorCode}</code>,
    },
    {
      header: lang === 'ar' ? 'الاسم النظامي' : 'Legal name',
      cell: r => (
        <span style={{ fontWeight: 500 }}>
          {lang === 'ar' && r.subProcessorLegalNameAr ? r.subProcessorLegalNameAr : r.subProcessorLegalName}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الدولة' : 'Country', width: '80px', align: 'center',
      cell: r => <code style={{ fontSize: 12 }}>{r.countryOfProcessing}</code>,
    },
    {
      header: lang === 'ar' ? 'ضمانة النقل عبر الحدود' : 'Cross-border safeguard',
      width: '220px',
      cell: r => r.crossBorderSafeguardType ? (
        <Badge tone={SAFEGUARD_TONE[r.crossBorderSafeguardType]}>
          {SAFEGUARD_LABEL[r.crossBorderSafeguardType][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ) : <span style={{ color: 'var(--ink-3)', fontSize: 12 }}>—</span>,
    },
    {
      header: lang === 'ar' ? 'الإذن' : 'Authorisation', width: '130px', align: 'center',
      cell: r => (
        <Badge tone="info">
          {AUTH_TYPE_LABEL[r.authorisationType][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'دورة الحياة' : 'Lifecycle', width: '160px', align: 'center',
      cell: r => (
        <Badge tone={LIFECYCLE_TONE[r.lifecycleStage]} dot>
          {LIFECYCLE_LABEL[r.lifecycleStage][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'حجم البيانات' : 'Volume', width: '110px', align: 'center',
      cell: r => (
        <Badge tone={r.dataVolumeBand === 'VERY_HIGH' || r.dataVolumeBand === 'HIGH' ? 'warning' : 'neutral'}>
          {VOLUME_LABEL[r.dataVolumeBand][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'المراجعة القادمة' : 'Next review', width: '150px', align: 'center',
      cell: r => {
        const d = daysUntilReview(r.nextReviewDueAt);
        if (d === null) return <span style={{ color: 'var(--ink-3)', fontSize: 12 }}>—</span>;
        if (d < 0) return <Badge tone="danger">{lang === 'ar' ? `متأخر ${-d} يوماً` : `${-d}d overdue`}</Badge>;
        if (d < 30) return <Badge tone="warning">{lang === 'ar' ? `بعد ${d} يوماً` : `in ${d}d`}</Badge>;
        return <span style={{ fontSize: 12 }}>{lang === 'ar' ? `بعد ${d} يوماً` : `in ${d}d`}</span>;
      },
    },
  ];

  // ─── Render ────────────────────────────────────────────────────────

  return (
    <Shell
      title={lang === 'ar' ? 'سجل معالجي البيانات الفرعيين' : 'Sub-processor register'}
      sub={lang === 'ar' ? 'الخصوصية وحماية البيانات' : 'Privacy'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'الخصوصية' : 'Privacy'}
        title={lang === 'ar' ? 'سجل معالجي البيانات الفرعيين' : 'Sub-processor register'}
        sub={lang === 'ar'
          ? 'سجلّ حي لكل معالجي البيانات الفرعيين، يخدم متطلبات المادة 30 من نظام حماية البيانات الشخصية ولوائح النقل عبر الحدود الصادرة عن سدايا.'
          : 'Live register of every sub-processor under PDPL Article 30 and SDAIA Cross-Border Data Transfer Regulations 2023.'}
        actions={demoMode ? <Badge tone="warning">{lang === 'ar' ? 'وضع العرض' : 'Demo mode'}</Badge> : null}
      />

      {/* KPI tiles */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
        <Kpi Icon={Database} tone="primary"
             label={lang === 'ar' ? 'إجمالي المعالجين' : 'Total sub-processors'}
             value={kpis.total}
             onClick={() => { setFilterLifecycle('ALL'); setFilterCountry('ALL'); setFilterSafeguard('ALL'); }}
             active={filterLifecycle === 'ALL' && filterCountry === 'ALL' && filterSafeguard === 'ALL'} />
        <Kpi Icon={FileSignature} tone="success"
             label={lang === 'ar' ? 'مُعتَمد' : 'Approved'}
             value={kpis.approved}
             onClick={() => setFilterLifecycle('APPROVED')}
             active={filterLifecycle === 'APPROVED'} />
        <Kpi Icon={Globe2} tone="info"
             label={lang === 'ar' ? 'خارج المملكة (مُعتَمد)' : 'Cross-border (approved)'}
             value={kpis.crossBorder} to="/privacy/data-flow" />
        <Kpi Icon={Clock} tone={kpis.overdue > 0 ? 'danger' : 'neutral'}
             label={lang === 'ar' ? 'مراجعات متأخّرة' : 'Overdue reviews'}
             value={kpis.overdue} />
        <Kpi Icon={ShieldAlert} tone={kpis.noDpa > 0 ? 'warning' : 'neutral'}
             label={lang === 'ar' ? 'مُعتَمد بلا اتفاقية معالجة بيانات' : 'Approved without DPA'}
             value={kpis.noDpa} />
      </div>

      <Card padded>
        {/* Filters */}
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: 12 }}>
          <FilterSelect
            label={lang === 'ar' ? 'دورة الحياة' : 'Lifecycle'}
            value={filterLifecycle}
            onChange={v => setFilterLifecycle(v as 'ALL' | LifecycleStage)}
            options={[
              { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
              ...(['PROPOSED', 'UNDER_DPO_REVIEW', 'APPROVED', 'SUSPENDED', 'RETIRED'] as LifecycleStage[]).map(k => ({
                value: k, label: LIFECYCLE_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
              })),
            ]} />
          <FilterSelect
            label={lang === 'ar' ? 'الموقع الجغرافي' : 'Location'}
            value={filterCountry}
            onChange={v => setFilterCountry(v as 'ALL' | 'SA' | 'NON_SA')}
            options={[
              { value: 'ALL',    label: lang === 'ar' ? 'الكل' : 'All' },
              { value: 'SA',     label: lang === 'ar' ? 'داخل المملكة' : 'Inside KSA' },
              { value: 'NON_SA', label: lang === 'ar' ? 'خارج المملكة' : 'Outside KSA' },
            ]} />
          <FilterSelect
            label={lang === 'ar' ? 'ضمانة النقل' : 'Safeguard'}
            value={filterSafeguard}
            onChange={v => setFilterSafeguard(v as 'ALL' | CrossBorderSafeguardType)}
            options={[
              { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
              ...(['KSA_RESIDENT', 'SCC_PLUS_TIA', 'ADEQUACY_DECISION', 'EXPLICIT_CONSENT', 'OTHER'] as CrossBorderSafeguardType[]).map(k => ({
                value: k, label: SAFEGUARD_LABEL[k][lang === 'ar' ? 'ar' : 'en'],
              })),
            ]} />
        </div>

        {error && (
          <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
        )}

        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={cols}
            rows={filtered}
            rowHref={r => `/privacy/subprocessors/${r.externalId}`}
            empty={<EmptyState
              Icon={Database}
              title={lang === 'ar' ? 'لا يوجد معالجون فرعيون' : 'No sub-processors'}
              sub={lang === 'ar'
                ? 'سيظهر هنا كل معالج بيانات فرعي مُسجَّل تحت سجل المادة 30.'
                : 'Sub-processors registered under PDPL Article 30 will appear here.'} />}
          />
        )}
      </Card>
    </Shell>
  );
}

// ─── Local helpers ──────────────────────────────────────────────────

interface FilterSelectProps {
  label: string; value: string; onChange: (v: string) => void;
  options: { value: string; label: string }[];
}
function FilterSelect({ label, value, onChange, options }: FilterSelectProps) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 160 }}>
      <label style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: 0.4 }}>
        {label}
      </label>
      <select
        value={value} onChange={e => onChange(e.target.value)}
        style={{
          padding: '6px 8px',
          border: '1px solid var(--border)',
          borderRadius: 4,
          background: 'var(--surface)',
          color: 'var(--ink-1)',
          fontSize: 13,
        }}
      >
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </div>
  );
}

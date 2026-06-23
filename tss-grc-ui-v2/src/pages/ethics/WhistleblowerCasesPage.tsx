import { useEffect, useMemo, useState } from 'react';
import { AlertOctagon, MessageCircle, ShieldAlert, UserCheck, UserX } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  whistleblowerApi,
  STATUS_LABEL, STATUS_TONE,
  SEVERITY_LABEL, SEVERITY_TONE,
  CATEGORY_LABEL,
  type CaseResponse, type CaseStatus, type Severity, type Category,
} from '../../lib/whistleblower';

/**
 * Internal triage page — /ethics/whistleblower/cases.
 *
 * <p>Read is narrow per regulator convention (Ethics Officer,
 * Compliance Officer, Internal Auditor, Audit Committee Member,
 * Platform Admin). Triage / investigation / close is the smaller
 * Ethics + Compliance + Platform Admin subset.
 *
 * <p>Saudi-native title: "خط الإبلاغ عن المخالفات" (operator-natural,
 * not the literal "خط ساخن").
 */
export function WhistleblowerCasesPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<CaseResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [filterStatus, setFilterStatus] = useState<'ALL' | CaseStatus>('ALL');
  const [filterSeverity, setFilterSeverity] = useState<'ALL' | Severity>('ALL');
  const [filterCategory, setFilterCategory] = useState<'ALL' | Category>('ALL');

  async function load() {
    if (demoMode) { setRows([]); return; }
    setLoading(true);
    setError(null);
    try {
      const r = await whistleblowerApi.list();
      setRows(r);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل البلاغات' : 'Failed to load reports'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  const kpis = useMemo(() => {
    const total = rows.length;
    const newOnes = rows.filter(r => r.status === 'RECEIVED' || r.status === 'TRIAGE').length;
    const investigating = rows.filter(r => r.status === 'UNDER_INVESTIGATION').length;
    const seniorExec = rows.filter(r => r.involvesSeniorExecutive
      && !r.status.startsWith('CLOSED_')).length;
    const critical = rows.filter(r => r.severity === 'CRITICAL' && !r.status.startsWith('CLOSED_')).length;
    const anon = rows.filter(r => r.isAnonymous).length;
    return { total, newOnes, investigating, seniorExec, critical, anon };
  }, [rows]);

  const filtered = useMemo(() => rows.filter(r => {
    if (filterStatus !== 'ALL' && r.status !== filterStatus) return false;
    if (filterSeverity !== 'ALL' && r.severity !== filterSeverity) return false;
    if (filterCategory !== 'ALL' && r.category !== filterCategory) return false;
    return true;
  }), [rows, filterStatus, filterSeverity, filterCategory]);

  const cols: Column<CaseResponse>[] = [
    {
      header: lang === 'ar' ? 'الرقم' : 'Code', width: '120px',
      cell: r => <code style={{ fontSize: 12, fontWeight: 500 }}>{r.caseCode}</code>,
    },
    {
      header: lang === 'ar' ? 'الموضوع' : 'Subject',
      cell: r => (
        <div>
          <div style={{ fontWeight: 500 }}>
            {lang === 'ar' ? r.subjectAr : r.subjectEn}
          </div>
          {r.involvesSeniorExecutive && (
            <div style={{ fontSize: 11, color: 'var(--danger)', marginTop: 2,
              display: 'inline-flex', alignItems: 'center', gap: 4 }}>
              <ShieldAlert size={11} />
              {lang === 'ar' ? 'مسؤول تنفيذي رفيع' : 'Senior executive flagged'}
            </div>
          )}
        </div>
      ),
    },
    {
      header: lang === 'ar' ? 'الفئة' : 'Category', width: '170px',
      cell: r => <span style={{ fontSize: 12 }}>{CATEGORY_LABEL[r.category][lang === 'ar' ? 'ar' : 'en']}</span>,
    },
    {
      header: lang === 'ar' ? 'الأهمّية' : 'Severity', width: '110px',
      cell: r => (
        <Badge tone={SEVERITY_TONE[r.severity]}>
          {SEVERITY_LABEL[r.severity][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '180px',
      cell: r => (
        <Badge tone={STATUS_TONE[r.status]}>
          {STATUS_LABEL[r.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'المُبلِّغ' : 'Reporter', width: '130px',
      cell: r => r.isAnonymous
        ? <span style={{ fontSize: 12, color: 'var(--ink-3)', fontStyle: 'italic' }}>
            {lang === 'ar' ? 'مجهول' : 'Anonymous'}
          </span>
        : <span style={{ fontSize: 12 }}>{r.reporterName || r.reporterEmail || '—'}</span>,
    },
    {
      header: lang === 'ar' ? 'تاريخ الإرسال' : 'Submitted', width: '130px',
      cell: r => <span style={{ fontSize: 12 }}>
        {new Date(r.submittedAt).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
      </span>,
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'خط الإبلاغ عن المخالفات' : 'Whistleblower Hotline'}
      sub={lang === 'ar'
        ? 'إدارة بلاغات المخالفات الأخلاقية وفق المادة 28 من لوائح حوكمة الشركات (البنك المركزي) والمادة 87 (هيئة السوق المالية). البلاغات سرّيّة بالكامل ومحميّة من الانتقام.'
        : 'Manage ethical-violation reports per SAMA Corporate Governance Art.28 + CMA Listed-Companies CG Art.87. All reports are strictly confidential and protected from retaliation.'}
    >
      <PageHeader
        title={lang === 'ar' ? 'سجلّ البلاغات' : 'Cases register'}
        sub={lang === 'ar' ? 'استعراض كافة البلاغات الواردة عبر القنوات المتاحة' : 'All reports received across channels'}
      />

      {/* KPIs */}
      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 12, marginBottom: 20,
      }}>
        <Kpi Icon={MessageCircle} tone="primary"
          label={lang === 'ar' ? 'إجمالي البلاغات' : 'Total reports'}
          value={kpis.total}
          onClick={() => { setFilterStatus('ALL'); setFilterSeverity('ALL'); setFilterCategory('ALL'); }}
          active={filterStatus === 'ALL' && filterSeverity === 'ALL' && filterCategory === 'ALL'} />
        <Kpi Icon={AlertOctagon} tone="info"
          label={lang === 'ar' ? 'وارد / فرز' : 'New / triage'}
          value={kpis.newOnes}
          onClick={() => setFilterStatus('TRIAGE')}
          active={filterStatus === 'TRIAGE'} />
        <Kpi Icon={UserCheck} tone="warning"
          label={lang === 'ar' ? 'قيد التحقيق' : 'Under investigation'}
          value={kpis.investigating}
          onClick={() => setFilterStatus('UNDER_INVESTIGATION')}
          active={filterStatus === 'UNDER_INVESTIGATION'} />
        <Kpi Icon={ShieldAlert} tone="danger"
          label={lang === 'ar' ? 'تورّط مسؤول تنفيذي' : 'Senior exec flagged'}
          value={kpis.seniorExec}
          to="/ethics" />
        <Kpi Icon={AlertOctagon} tone="danger"
          label={lang === 'ar' ? 'أهمّية حرجة' : 'Critical severity'}
          value={kpis.critical}
          onClick={() => setFilterSeverity('CRITICAL')}
          active={filterSeverity === 'CRITICAL'} />
        <Kpi Icon={UserX} tone="neutral"
          label={lang === 'ar' ? 'بلاغات مجهولة الهوية' : 'Anonymous reports'}
          value={kpis.anon}
          to="/ethics" />
      </div>

      {/* Filters */}
      <div style={{ marginBottom: 14 }}>
      <Card padded>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
          <FilterSelect label={lang === 'ar' ? 'الحالة' : 'Status'}
            value={filterStatus}
            onChange={v => setFilterStatus(v as 'ALL' | CaseStatus)}
            options={[
              { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
              ...(Object.keys(STATUS_LABEL) as CaseStatus[]).map(s => ({
                value: s, label: STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en'],
              })),
            ]} />
          <FilterSelect label={lang === 'ar' ? 'الأهمّية' : 'Severity'}
            value={filterSeverity}
            onChange={v => setFilterSeverity(v as 'ALL' | Severity)}
            options={[
              { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
              ...(Object.keys(SEVERITY_LABEL) as Severity[]).map(s => ({
                value: s, label: SEVERITY_LABEL[s][lang === 'ar' ? 'ar' : 'en'],
              })),
            ]} />
          <FilterSelect label={lang === 'ar' ? 'الفئة' : 'Category'}
            value={filterCategory}
            onChange={v => setFilterCategory(v as 'ALL' | Category)}
            options={[
              { value: 'ALL', label: lang === 'ar' ? 'الكل' : 'All' },
              ...(Object.keys(CATEGORY_LABEL) as Category[]).map(c => ({
                value: c, label: CATEGORY_LABEL[c][lang === 'ar' ? 'ar' : 'en'],
              })),
            ]} />
        </div>
      </Card>
      </div>

      {/* Table */}
      <Card padded>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : error ? (
          <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>
        ) : (
          <DataTable
            columns={cols}
            rows={filtered}
            rowHref={r => `/ethics/whistleblower/cases/${r.id}`}
            empty={<EmptyState
              Icon={MessageCircle}
              title={lang === 'ar' ? 'لا توجد بلاغات' : 'No reports'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض البلاغات.' : 'Log in to view cases.')
                : (lang === 'ar' ? 'لم يتم استلام أي بلاغات بعد.' : 'No reports submitted yet.')} />}
          />
        )}
      </Card>
    </Shell>
  );
}

function FilterSelect({ label, value, onChange, options }: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string }[];
}) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
      <span style={{ color: 'var(--ink-3)', fontWeight: 500 }}>{label}</span>
      <select value={value} onChange={e => onChange(e.target.value)} style={{
        padding: '6px 10px', fontSize: 13,
        border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
        background: 'var(--surface-1)', color: 'var(--ink-1)', minWidth: 160,
      }}>
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </label>
  );
}

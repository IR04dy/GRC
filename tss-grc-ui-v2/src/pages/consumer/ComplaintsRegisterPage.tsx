import { useEffect, useMemo, useState } from 'react';
import { AlertOctagon, AlertTriangle, CheckCircle2, ListChecks, MessageSquare, Send } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  complaintsApi,
  CATEGORY_LABEL, SEVERITY_LABEL, SEVERITY_TONE,
  STATUS_LABEL, STATUS_TONE,
  slaBreachKind,
  type ComplaintResponse, type ComplaintStatus, type Category, type Severity,
} from '../../lib/complaints';

/**
 * Internal triage page — /complaints.
 *
 * <p>Customer Complaints register. Read is gated to PLATFORM_ADMIN,
 * CONSUMER_PROTECTION_OFFICER, and COMPLIANCE_OFFICER on the backend.
 *
 * <p>SLA-breach row tint: SAMA Banking Consumer Protection Principles
 * mandates a 5-working-day acknowledgement SLA and a 20-working-day
 * resolution SLA. The register highlights rows whose due timestamp has
 * elapsed against the live status.
 */
export function ComplaintsRegisterPage() {
  const { lang, t } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<ComplaintResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [filterStatus, setFilterStatus] = useState<'ALL' | ComplaintStatus>('ALL');
  const [filterCategory, setFilterCategory] = useState<'ALL' | Category>('ALL');
  const [filterSeverity, setFilterSeverity] = useState<'ALL' | Severity>('ALL');

  async function load() {
    if (demoMode) { setRows([]); return; }
    setLoading(true);
    setError(null);
    try {
      const data = await complaintsApi.list();
      setRows(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل الشكاوى' : 'Failed to load complaints'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  const now = useMemo(() => new Date(), [rows]);

  const kpis = useMemo(() => {
    const total = rows.length;
    const open = rows.filter(r =>
      r.status !== 'RESOLVED' && r.status !== 'CLOSED' && r.status !== 'REJECTED').length;
    const ackBreach = rows.filter(r => slaBreachKind(r, now) === 'ACK').length;
    const resolutionBreach = rows.filter(r => slaBreachKind(r, now) === 'RESOLUTION').length;
    const sama = rows.filter(r => r.status === 'ESCALATED_TO_SAMA').length;
    return { total, open, ackBreach, resolutionBreach, sama };
  }, [rows, now]);

  const filtered = useMemo(() => rows.filter(r => {
    if (filterStatus !== 'ALL' && r.status !== filterStatus) return false;
    if (filterCategory !== 'ALL' && r.category !== filterCategory) return false;
    if (filterSeverity !== 'ALL' && r.severity !== filterSeverity) return false;
    return true;
  }), [rows, filterStatus, filterCategory, filterSeverity]);

  function daysSince(iso?: string): number | undefined {
    if (!iso) return undefined;
    const ms = now.getTime() - new Date(iso).getTime();
    if (Number.isNaN(ms)) return undefined;
    return Math.max(0, Math.floor(ms / 86_400_000));
  }

  function fmtDate(iso?: string): string {
    if (!iso) return '—';
    try {
      const d = new Date(iso);
      return d.toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB', {
        year: 'numeric', month: 'short', day: 'numeric',
      });
    } catch { return iso; }
  }

  const cols: Column<ComplaintResponse>[] = [
    {
      header: t('complaints.complaintNo'), width: '140px',
      cell: r => {
        const kind = slaBreachKind(r, now);
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <code style={{ fontSize: 12, fontWeight: 500 }}>{r.complaintNo}</code>
            {kind && (
              <Badge tone="danger"
                title={kind === 'ACK'
                  ? t('complaints.ackSlaBreach')
                  : t('complaints.resolutionSlaBreach')}>
                <AlertTriangle size={10} />
              </Badge>
            )}
          </div>
        );
      },
    },
    {
      header: t('complaints.submittedOn'), width: '140px',
      cell: r => <span style={{ fontSize: 12, color: 'var(--ink-2)' }}>{fmtDate(r.submittedOn)}</span>,
    },
    {
      header: t('complaints.category'),
      cell: r => (
        <Badge tone="neutral">
          {CATEGORY_LABEL[r.category]?.[lang === 'ar' ? 'ar' : 'en'] ?? r.category}
        </Badge>
      ),
    },
    {
      header: t('complaints.severity'), width: '110px',
      cell: r => (
        <Badge tone={SEVERITY_TONE[r.severity]}>
          {SEVERITY_LABEL[r.severity]?.[lang === 'ar' ? 'ar' : 'en'] ?? r.severity}
        </Badge>
      ),
    },
    {
      header: t('complaints.status'), width: '160px',
      cell: r => (
        <Badge tone={STATUS_TONE[r.status]} dot>
          {STATUS_LABEL[r.status]?.[lang === 'ar' ? 'ar' : 'en'] ?? r.status}
        </Badge>
      ),
    },
    {
      header: t('complaints.assignedTo'), width: '140px',
      cell: r => <span style={{ fontSize: 12, color: 'var(--ink-2)' }}>{r.assignedTo ?? '—'}</span>,
    },
    {
      header: t('complaints.daysOpen'), width: '100px', align: 'end',
      cell: r => {
        const d = daysSince(r.submittedOn);
        return <span style={{ fontSize: 12, color: 'var(--ink-2)' }}>{d ?? '—'}</span>;
      },
    },
  ];

  return (
    <Shell>
      <PageHeader
        eyebrow={t('complaints.eyebrow')}
        title={t('complaints.title')}
        sub={t('complaints.sub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={MessageSquare} label={t('complaints.kpiTotal')} value={kpis.total} tone="primary" />
        <Kpi Icon={ListChecks} label={t('complaints.kpiOpen')} value={kpis.open} tone="info" />
        <Kpi Icon={AlertTriangle} label={t('complaints.kpiAckBreach')} value={kpis.ackBreach} tone="warning" />
        <Kpi Icon={AlertOctagon} label={t('complaints.kpiResolutionBreach')} value={kpis.resolutionBreach} tone="danger" />
        <Kpi Icon={Send} label={t('complaints.kpiSamaEscalated')} value={kpis.sama} tone="accent" />
      </div>

      <Card padded>
        {/* Filters */}
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
          <FilterSelect label={t('complaints.status')} value={filterStatus}
            onChange={v => setFilterStatus(v as 'ALL' | ComplaintStatus)}
            options={[
              { value: 'ALL', label: t('complaints.filtersAll') },
              ...(Object.keys(STATUS_LABEL) as ComplaintStatus[]).map(s => ({
                value: s, label: STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en'],
              })),
            ]} />
          <FilterSelect label={t('complaints.category')} value={filterCategory}
            onChange={v => setFilterCategory(v as 'ALL' | Category)}
            options={[
              { value: 'ALL', label: t('complaints.filtersAll') },
              ...(Object.keys(CATEGORY_LABEL) as Category[]).map(c => ({
                value: c, label: CATEGORY_LABEL[c][lang === 'ar' ? 'ar' : 'en'],
              })),
            ]} />
          <FilterSelect label={t('complaints.severity')} value={filterSeverity}
            onChange={v => setFilterSeverity(v as 'ALL' | Severity)}
            options={[
              { value: 'ALL', label: t('complaints.filtersAll') },
              ...(Object.keys(SEVERITY_LABEL) as Severity[]).map(s => ({
                value: s, label: SEVERITY_LABEL[s][lang === 'ar' ? 'ar' : 'en'],
              })),
            ]} />
        </div>

        {error && (
          <div style={{
            padding: '10px 14px', marginBottom: 12, borderRadius: 'var(--r-sm)',
            background: 'var(--danger-bg)', color: 'var(--danger-fg)', fontSize: 13,
          }}>{error}</div>
        )}

        {loading ? (
          <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>
            {lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}
          </div>
        ) : (
          <DataTable
            columns={cols}
            rows={filtered}
            rowHref={r => `/complaints/${r.id}`}
            empty={<EmptyState
              Icon={MessageSquare}
              title={t('complaints.searchEmpty')}
              sub={lang === 'ar'
                ? 'لا توجد شكاوى مطابقة. يمكنك تجربة فلتر مختلف.'
                : 'No complaints match. Try a different filter.'}
            />}
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
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 180 }}>
      <span style={{ fontSize: 11, color: 'var(--ink-3)', fontWeight: 500,
        textTransform: 'uppercase', letterSpacing: '0.04em' }}>{label}</span>
      <select value={value} onChange={e => onChange(e.target.value)}
        style={{
          padding: '6px 10px', fontSize: 13,
          border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
          background: 'var(--surface-1)', color: 'var(--ink-1)',
        }}>
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </label>
  );
}

// Suppress unused-import warnings when no CheckCircle2 is used yet.
void CheckCircle2;

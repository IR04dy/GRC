import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldAlert, Plus } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Select } from '../../components/ui/Select';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  fraudApi,
  CASE_STATUS_LABEL, CASE_STATUS_TONE,
  SEVERITY_LABEL, SEVERITY_TONE,
  SAMA_CATEGORY_LABEL,
  type CaseResponse, type CaseStatus, type Severity,
  type TypologyResponse, type Page,
} from '../../lib/fraud';

type StatusFilter = 'ALL' | CaseStatus;
type SeverityFilter = 'ALL' | Severity;

/**
 * Fraud Cases Register — /fraud/cases.
 *
 * Server-paginated list of all fraud cases, with client-side filter
 * by status + severity over the current page. Clicking a row opens
 * the detail page. The "New case" CTA navigates to /fraud/cases/new
 * (stub route — wired in a follow-up PR).
 */
export function FraudCasesPage() {
  const { lang } = useI18n();
  const navigate = useNavigate();
  const demoMode = !isAuthenticated();

  const [page, setPage] = useState<Page<CaseResponse> | null>(null);
  const [pageIdx, setPageIdx] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Typology lookup — keyed by code — to render SAMA category badge.
  const [typologies, setTypologies] = useState<Record<string, TypologyResponse>>({});

  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [severityFilter, setSeverityFilter] = useState<SeverityFilter>('ALL');

  useEffect(() => {
    if (demoMode) { setPage(null); return; }
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const r = await fraudApi.listCases(pageIdx, 25);
        if (!cancelled) setPage(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar' ? 'تعذّر تحميل قضايا الاحتيال' : 'Failed to load fraud cases'));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageIdx]);

  // Load typology catalog once for SAMA category lookup.
  useEffect(() => {
    if (demoMode) return;
    let cancelled = false;
    (async () => {
      try {
        const list = await fraudApi.listTypologies();
        if (cancelled) return;
        const map: Record<string, TypologyResponse> = {};
        list.forEach(t => { map[t.code] = t; });
        setTypologies(map);
      } catch {/* non-fatal — leave map empty */}
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const sarFmt = new Intl.NumberFormat(lang === 'ar' ? 'ar-SA' : 'en-SA', {
    style: 'currency', currency: 'SAR', maximumFractionDigits: 0,
  });

  const rows = page?.content ?? [];

  const filtered = useMemo(() => rows.filter(r => {
    if (statusFilter !== 'ALL' && r.status !== statusFilter) return false;
    if (severityFilter !== 'ALL' && r.severity !== severityFilter) return false;
    return true;
  }), [rows, statusFilter, severityFilter]);

  const statusOptions: Array<{ value: StatusFilter; label: string }> = [
    { value: 'ALL',            label: lang === 'ar' ? 'كل الحالات' : 'All statuses' },
    { value: 'DETECTED',       label: CASE_STATUS_LABEL.DETECTED[lang === 'ar' ? 'ar' : 'en'] },
    { value: 'INVESTIGATING',  label: CASE_STATUS_LABEL.INVESTIGATING[lang === 'ar' ? 'ar' : 'en'] },
    { value: 'CONFIRMED',      label: CASE_STATUS_LABEL.CONFIRMED[lang === 'ar' ? 'ar' : 'en'] },
    { value: 'CLEARED',        label: CASE_STATUS_LABEL.CLEARED[lang === 'ar' ? 'ar' : 'en'] },
    { value: 'FALSE_POSITIVE', label: CASE_STATUS_LABEL.FALSE_POSITIVE[lang === 'ar' ? 'ar' : 'en'] },
    { value: 'CLOSED',         label: CASE_STATUS_LABEL.CLOSED[lang === 'ar' ? 'ar' : 'en'] },
  ];

  const severityOptions: Array<{ value: SeverityFilter; label: string }> = [
    { value: 'ALL',      label: lang === 'ar' ? 'كل المستويات' : 'All severities' },
    { value: 'CRITICAL', label: SEVERITY_LABEL.CRITICAL[lang === 'ar' ? 'ar' : 'en'] },
    { value: 'HIGH',     label: SEVERITY_LABEL.HIGH[lang === 'ar' ? 'ar' : 'en'] },
    { value: 'MEDIUM',   label: SEVERITY_LABEL.MEDIUM[lang === 'ar' ? 'ar' : 'en'] },
    { value: 'LOW',      label: SEVERITY_LABEL.LOW[lang === 'ar' ? 'ar' : 'en'] },
  ];

  const cols: Column<CaseResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'External Ref', width: '140px', mono: true,
      cell: c => <span style={{ fontSize: 12 }}>{c.externalReference}</span>,
    },
    {
      header: lang === 'ar' ? 'الموضوع' : 'Subject',
      cell: c => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? c.subjectAr : c.subjectEn}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'النمط' : 'Typology', width: '150px',
      cell: c => <code style={{ fontSize: 12 }}>{c.typologyCodeSnapshot}</code>,
    },
    {
      header: lang === 'ar' ? 'تصنيف ساما' : 'SAMA Category', width: '130px',
      cell: c => {
        const t = typologies[c.typologyCodeSnapshot];
        if (!t) return <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>—</span>;
        return (
          <span style={{ fontSize: 12 }}>
            {SAMA_CATEGORY_LABEL[t.samaCategoryCode][lang === 'ar' ? 'ar' : 'en']}
          </span>
        );
      },
    },
    {
      header: lang === 'ar' ? 'الخطورة' : 'Severity', width: '110px',
      cell: c => (
        <Badge tone={SEVERITY_TONE[c.severity]}>
          {SEVERITY_LABEL[c.severity][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px',
      cell: c => (
        <Badge tone={CASE_STATUS_TONE[c.status]}>
          {CASE_STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الخسارة المقدّرة' : 'Estimated Loss', width: '150px', align: 'end',
      cell: c => (
        <span style={{ fontSize: 12, fontFamily: 'var(--font-mono)' }}>
          {c.estimatedLossSar != null ? sarFmt.format(c.estimatedLossSar) : '—'}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'تاريخ الرصد' : 'Detected', width: '120px',
      cell: c => (
        <span style={{ fontSize: 12 }}>
          {new Date(c.detectedAt).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
        </span>
      ),
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'قضايا الاحتيال' : 'Fraud Cases'}
      sub={lang === 'ar'
        ? 'سجل قضايا الاحتيال — رصد وتحقيق وإغلاق وفق إطار ساما لمكافحة الاحتيال.'
        : 'Fraud case register — detection, investigation, and closure per SAMA Anti-Fraud Framework.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'مكافحة الاحتيال' : 'Fraud Risk'}
        title={lang === 'ar' ? 'قضايا الاحتيال' : 'Fraud Cases'}
        sub={page
          ? (lang === 'ar'
              ? `${page.totalElements} قضية في السجل`
              : `${page.totalElements} cases on file`)
          : undefined}
        actions={(
          <Button variant="primary" Icon={Plus} onClick={() => navigate('/fraud/cases/new')}>
            {lang === 'ar' ? 'قضية جديدة' : 'New case'}
          </Button>
        )}
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {/* Filters */}
      <Card padded title={lang === 'ar' ? 'الفلاتر' : 'Filters'}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: 12,
        }}>
          <Select label={lang === 'ar' ? 'الحالة' : 'Status'}
            value={statusFilter}
            onChange={e => setStatusFilter(e.target.value as StatusFilter)}
            options={statusOptions} />
          <Select label={lang === 'ar' ? 'الخطورة' : 'Severity'}
            value={severityFilter}
            onChange={e => setSeverityFilter(e.target.value as SeverityFilter)}
            options={severityOptions} />
        </div>
      </Card>

      {/* Cases table */}
      <div style={{ marginTop: 16 }}>
        <Card padded>
          {loading ? (
            <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          ) : (
            <>
              <DataTable
                columns={cols}
                rows={filtered}
                rowHref={c => `/fraud/cases/${c.id}`}
                empty={<EmptyState
                  Icon={ShieldAlert}
                  title={lang === 'ar' ? 'لا توجد قضايا' : 'No cases'}
                  sub={demoMode
                    ? (lang === 'ar' ? 'سجّل دخولك لعرض قضايا الاحتيال.' : 'Log in to view fraud cases.')
                    : (rows.length > 0
                        ? (lang === 'ar' ? 'لا توجد سجلات مطابقة للفلتر.' : 'No records match the current filter.')
                        : (lang === 'ar' ? 'لم تُسجَّل أي قضية احتيال بعد.' : 'No fraud cases recorded yet.'))} />}
              />

              {/* Server-side pager */}
              {page && page.totalPages > 1 && (
                <div style={{
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  padding: '12px 4px 4px', fontSize: 12, color: 'var(--ink-3)',
                }}>
                  <span>
                    {lang === 'ar' ? 'صفحة' : 'Page'} {page.number + 1} / {page.totalPages}
                    {' · '}
                    {page.totalElements} {lang === 'ar' ? 'سجل' : 'rows'}
                  </span>
                  <div style={{ display: 'flex', gap: 6 }}>
                    <Button size="sm" variant="ghost"
                      disabled={page.number <= 0}
                      onClick={() => setPageIdx(p => Math.max(0, p - 1))}>
                      {lang === 'ar' ? '◀ السابق' : '◀ Prev'}
                    </Button>
                    <Button size="sm" variant="ghost"
                      disabled={page.number + 1 >= page.totalPages}
                      onClick={() => setPageIdx(p => p + 1)}>
                      {lang === 'ar' ? 'التالي ▶' : 'Next ▶'}
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </Card>
      </div>
    </Shell>
  );
}

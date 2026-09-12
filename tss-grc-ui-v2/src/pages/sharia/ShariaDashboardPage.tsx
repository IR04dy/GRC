import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Users, ScrollText, ShieldAlert, FileWarning, Package, Banknote,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  shariaApi,
  RESOLUTION_STATUS_LABEL, RESOLUTION_STATUS_TONE,
  FINDING_STATUS_LABEL, FINDING_STATUS_TONE,
  NC_STATUS_LABEL, NC_STATUS_TONE,
  PRODUCT_STATUS_LABEL, PRODUCT_STATUS_TONE,
  SEVERITY_LABEL, SEVERITY_TONE,
  SUBJECT_AREA_LABEL,
  type DashboardResponse, type ResolutionResponse,
  type AuditFindingResponse, type NcEventResponse,
  type ResolutionStatus, type ProductStatus,
} from '../../lib/sharia';

/**
 * Sprint 20 PR #3 — Sharia Governance dashboard.
 * SAMA Sharia Governance + AAOIFI + IFSB-10. The single workspace
 * the Sharia Officer + SSB Chair + Sharia Auditor open every morning.
 */
export function ShariaDashboardPage() {
  const { lang } = useI18n();
  const nav = useNavigate();
  const demoMode = !isAuthenticated();

  const [data, setData] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) return;
    let cancelled = false;
    (async () => {
      setLoading(true); setError(null);
      try {
        const r = await shariaApi.dashboard();
        if (!cancelled) setData(r);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar' ? 'تعذّر تحميل لوحة الحوكمة الشرعية'
                            : 'Failed to load Sharia dashboard'));
        }
      } finally { if (!cancelled) setLoading(false); }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const resCols: Column<ResolutionResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', width: '130px', mono: true,
      cell: r => <span style={{ fontSize: 12 }}>{r.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: r => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? r.titleAr : r.titleEn}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'الموضوع' : 'Subject', width: '120px',
      cell: r => (
        <Badge tone="info">
          {SUBJECT_AREA_LABEL[r.subjectArea][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px',
      cell: r => (
        <Badge tone={RESOLUTION_STATUS_TONE[r.status]}>
          {RESOLUTION_STATUS_LABEL[r.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
  ];

  const findCols: Column<AuditFindingResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', width: '130px', mono: true,
      cell: f => <span style={{ fontSize: 12 }}>{f.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'النتيجة' : 'Finding',
      cell: f => <span style={{ fontWeight: 500, fontSize: 13 }}>
        {lang === 'ar' ? f.titleAr : f.titleEn}
      </span>,
    },
    {
      header: lang === 'ar' ? 'الخطورة' : 'Severity', width: '100px',
      cell: f => (
        <Badge tone={SEVERITY_TONE[f.severity]}>
          {SEVERITY_LABEL[f.severity][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '130px',
      cell: f => (
        <Badge tone={FINDING_STATUS_TONE[f.status]}>
          {FINDING_STATUS_LABEL[f.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
  ];

  const ncCols: Column<NcEventResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', width: '130px', mono: true,
      cell: n => <span style={{ fontSize: 12 }}>{n.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'الحدث' : 'Event',
      cell: n => <span style={{ fontWeight: 500, fontSize: 13 }}>
        {lang === 'ar' ? n.titleAr : n.titleEn}
      </span>,
    },
    {
      header: lang === 'ar' ? 'دخل غير شرعي (ر.س)' : 'SNCI (SAR)', width: '160px',
      cell: n => <strong style={{ fontSize: 12 }}>
        {Number(n.snciAmountSar ?? 0).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-US')}
      </strong>,
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '140px',
      cell: n => (
        <Badge tone={NC_STATUS_TONE[n.status]}>
          {NC_STATUS_LABEL[n.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'الحوكمة الشرعية' : 'Sharia Governance'}
      sub={lang === 'ar'
        ? 'سجل الهيئة الشرعية، قراراتها، مراجعة شرعية داخلية، وتطهير الدخل غير الشرعي.'
        : 'SSB register, resolutions, internal Sharia audit, and SNCI purification.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'الحوكمة الشرعية'
                                : 'Sharia Governance'}
        title={lang === 'ar' ? 'لوحة الحوكمة الشرعية'
                              : 'Sharia Governance Dashboard'}
        sub={lang === 'ar'
          ? 'الهيئة الشرعية، القرارات، المراجعة الداخلية، اعتماد المنتجات'
          : 'SSB, resolutions, internal audit, product approvals'}
        actions={
          <>
            <Button onClick={() => nav('/sharia/resolutions')}
                    variant="primary" Icon={ScrollText}>
              {lang === 'ar' ? 'القرارات' : 'Resolutions'}
            </Button>
            <Button onClick={() => nav('/sharia/nc-events')}
                    variant="secondary" Icon={FileWarning}>
              {lang === 'ar' ? 'المخالفات الشرعية' : 'NC events'}
            </Button>
          </>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 12, marginBottom: 16,
      }}>
        <Kpi Icon={Users} tone="success"
          label={lang === 'ar' ? 'أعضاء الهيئة النشطون' : 'Active SSB members'}
          value={data?.activeScholars ?? 0}
          to="/sharia/scholars" />
        <Kpi Icon={ScrollText} tone="info"
          label={lang === 'ar' ? 'قرارات نافذة' : 'Active resolutions'}
          value={data?.activeResolutions ?? 0}
          to="/sharia/resolutions" />
        <Kpi Icon={ShieldAlert} tone={(data?.openFindingsHigh ?? 0) > 0 ? 'danger' : 'warning'}
          label={lang === 'ar' ? 'نتائج عالية الخطورة' : 'HIGH findings open'}
          value={data?.openFindingsHigh ?? 0}
          to="/sharia/audit-findings" />
        <Kpi Icon={FileWarning} tone={(data?.ncEventsPendingPurification ?? 0) > 0 ? 'danger' : 'success'}
          label={lang === 'ar' ? 'مخالفات قيد التطهير' : 'Pending purification'}
          value={data?.ncEventsPendingPurification ?? 0}
          to="/sharia/nc-events" />
        <Kpi Icon={Banknote} tone="warning"
          label={lang === 'ar' ? 'دخل غير شرعي معلَّق (ر.س)' : 'SNCI outstanding (SAR)'}
          value={(data?.snciOutstandingSar ?? 0).toLocaleString(
            lang === 'ar' ? 'ar-SA' : 'en-US')}
          to="/sharia/nc-events" />
        <Kpi Icon={Package} tone="info"
          label={lang === 'ar' ? 'منتجات قيد الاعتماد' : 'Products in review'}
          value={data?.productsInReview ?? 0}
          to="/sharia/products" />
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
        gap: 12, marginBottom: 16,
      }}>
        <Card padded
          title={lang === 'ar' ? 'القرارات حسب الحالة' : 'Resolutions by status'}>
          {loading ? <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty> : (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {Object.entries(data?.resolutionsByStatus ?? {}).map(([k, v]) => (
                <Badge key={k} tone={RESOLUTION_STATUS_TONE[k as ResolutionStatus] ?? 'neutral'}>
                  {RESOLUTION_STATUS_LABEL[k as ResolutionStatus]?.[lang === 'ar' ? 'ar' : 'en'] ?? k}: {v}
                </Badge>
              ))}
              {Object.keys(data?.resolutionsByStatus ?? {}).length === 0 && (
                <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>
                  {lang === 'ar' ? 'لا قرارات بعد.' : 'No resolutions yet.'}
                </span>
              )}
            </div>
          )}
        </Card>

        <Card padded
          title={lang === 'ar' ? 'النتائج المفتوحة حسب الخطورة' : 'Open findings by severity'}>
          {loading ? <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty> : (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {Object.entries(data?.findingsBySeverity ?? {}).map(([k, v]) => (
                <Badge key={k} tone={SEVERITY_TONE[k as keyof typeof SEVERITY_LABEL] ?? 'neutral'}>
                  {SEVERITY_LABEL[k as keyof typeof SEVERITY_LABEL]?.[lang === 'ar' ? 'ar' : 'en'] ?? k}: {v}
                </Badge>
              ))}
              {Object.keys(data?.findingsBySeverity ?? {}).length === 0 && (
                <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>
                  {lang === 'ar' ? 'لا نتائج مفتوحة.' : 'No open findings.'}
                </span>
              )}
            </div>
          )}
        </Card>

        <Card padded
          title={lang === 'ar' ? 'المنتجات حسب الحالة' : 'Products by status'}>
          {loading ? <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty> : (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {Object.entries(data?.productsByStatus ?? {}).map(([k, v]) => (
                <Badge key={k} tone={PRODUCT_STATUS_TONE[k as ProductStatus] ?? 'neutral'}>
                  {PRODUCT_STATUS_LABEL[k as ProductStatus]?.[lang === 'ar' ? 'ar' : 'en'] ?? k}: {v}
                </Badge>
              ))}
              {Object.keys(data?.productsByStatus ?? {}).length === 0 && (
                <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>
                  {lang === 'ar' ? 'لا منتجات بعد.' : 'No products yet.'}
                </span>
              )}
            </div>
          )}
        </Card>
      </div>

      <Card padded
        title={lang === 'ar' ? 'أحدث القرارات الشرعية' : 'Recent resolutions'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={resCols}
            rows={data?.recentResolutions ?? []}
            empty={<EmptyState Icon={ScrollText}
              title={lang === 'ar' ? 'لا قرارات' : 'No resolutions yet'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض القرارات.' : 'Log in to view resolutions.')
                : (lang === 'ar'
                  ? 'انتقل إلى القرارات لمسوّدة فتوى جديدة.'
                  : 'Go to Resolutions to draft a new fatwa.')} />}
          />
        )}
      </Card>

      <div style={{ marginTop: 16, display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: 12 }}>
        <Card padded
          title={lang === 'ar' ? 'نتائج عالية الخطورة' : 'HIGH-severity findings'}>
          {loading ? (
            <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          ) : (
            <DataTable
              columns={findCols}
              rows={data?.openHighFindings ?? []}
              empty={<EmptyState Icon={ShieldAlert}
                title={lang === 'ar' ? 'لا نتائج عالية الخطورة' : 'No HIGH findings'}
                sub={lang === 'ar'
                  ? 'لا توجد نتائج عالية الخطورة بانتظار اللجنة.'
                  : 'No HIGH findings pending Board Audit Committee.'} />}
            />
          )}
        </Card>

        <Card padded
          title={lang === 'ar' ? 'مخالفات شرعية مفتوحة' : 'Open NC events'}>
          {loading ? (
            <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          ) : (
            <DataTable
              columns={ncCols}
              rows={data?.openNcEvents ?? []}
              empty={<EmptyState Icon={FileWarning}
                title={lang === 'ar' ? 'لا مخالفات مفتوحة' : 'No open NC events'}
                sub={lang === 'ar'
                  ? 'لا دخل غير شرعي بانتظار التطهير.'
                  : 'No SNCI pending purification.'} />}
            />
          )}
        </Card>
      </div>
    </Shell>
  );
}

import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertTriangle, GitMerge, Plus, ShieldCheck, ClipboardCheck } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { bowtieApi, ApiError, type BowTieAnalysisResponse } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';

/**
 * Risk Bow-Tie Analysis — list page (ISO 31010).
 *
 * Sprint 11 P1 GRC. Shows every bow-tie in the tenant ordered by
 * assessment date, with one-click drill into the visual detail page.
 */

const STATUS_TONES: Record<string, BadgeTone> = {
  DRAFT: 'neutral',
  IN_REVIEW: 'warning',
  APPROVED: 'success',
  SUPERSEDED: 'info',
  ARCHIVED: 'danger',
};

const RATING_TONES: Record<string, BadgeTone> = {
  CRITICAL: 'danger',
  HIGH: 'warning',
  MEDIUM: 'accent',
  LOW: 'success',
};

function effectivenessTone(score?: number): BadgeTone {
  if (score == null) return 'neutral';
  if (score >= 70) return 'success';
  if (score >= 50) return 'accent';
  if (score >= 30) return 'warning';
  return 'danger';
}

export function RiskBowTiePage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<BowTieAnalysisResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    if (demoMode) {
      // Demo mode — no backend connection, surface an empty register
      // with explanatory copy. The seeded BOWTIE-2026-001 lives only in
      // the live DB (V164 seed) so demo users see what's possible without
      // hitting the API.
      setRows([]);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const list = await bowtieApi.list();
      setRows(list);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load bow-ties');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  async function startCreate() {
    try {
      const created = await bowtieApi.create({
        topEventTitleEn: lang === 'ar' ? 'حدث جديد' : 'New top event',
        riskCategory: 'OPERATIONAL',
        assessmentDate: new Date().toISOString().slice(0, 10),
      });
      toast.success(lang === 'ar' ? 'تم إنشاء التحليل' : 'Bow-tie created');
      navigate(`/risk/bowtie/${created.externalId}`);
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Could not create bow-tie');
    }
  }

  // KPIs.
  const approved = rows.filter(r => r.status === 'APPROVED').length;
  const draft    = rows.filter(r => r.status === 'DRAFT' || r.status === 'IN_REVIEW').length;
  const critical = rows.filter(r => r.overallResidualRating === 'CRITICAL' || r.overallResidualRating === 'HIGH').length;

  const cols: Column<BowTieAnalysisResponse>[] = [
    { header: lang === 'ar' ? 'الرمز' : 'Code', cell: r => r.bowtieCode, mono: true, width: '160px' },
    { header: lang === 'ar' ? 'حدث أعلى' : 'Top event',
      cell: r => (
        <div className="text-bold">
          {lang === 'ar' && r.topEventTitleAr ? r.topEventTitleAr : r.topEventTitleEn}
        </div>
      ) },
    { header: lang === 'ar' ? 'الفئة' : 'Category', width: '140px',
      cell: r => r.riskCategory ? <Badge tone="info">{r.riskCategory}</Badge> : <span>—</span> },
    { header: lang === 'ar' ? 'التقييم المتبقي' : 'Residual', align: 'center', width: '120px',
      cell: r => r.overallResidualRating
        ? <Badge tone={RATING_TONES[r.overallResidualRating] ?? 'neutral'} dot>{r.overallResidualRating}</Badge>
        : <span>—</span> },
    { header: lang === 'ar' ? 'فعالية الضوابط' : 'Effectiveness', align: 'center', width: '140px',
      cell: r => r.overallControlEffectiveness != null
        ? <Badge tone={effectivenessTone(r.overallControlEffectiveness)}>{r.overallControlEffectiveness.toFixed(1)}%</Badge>
        : <span>—</span> },
    { header: lang === 'ar' ? 'الحالة' : 'Status', width: '130px',
      cell: r => <Badge tone={STATUS_TONES[r.status] ?? 'neutral'} dot>{r.status}</Badge> },
    { header: lang === 'ar' ? 'تاريخ التقييم' : 'Assessed', mono: true, width: '120px',
      cell: r => r.assessmentDate ?? '—' },
    { header: lang === 'ar' ? 'المراجعة القادمة' : 'Next review', mono: true, width: '120px',
      cell: r => r.nextReviewAt ?? '—' },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'تحليل الفيونكة' : 'Bow-tie analysis'}
      sub="ISO 31010"
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'تحليل المخاطر' : 'Risk analysis'}
        title={lang === 'ar' ? 'تحليل الفيونكة (ISO 31010)' : 'Bow-tie analysis (ISO 31010)'}
        sub={lang === 'ar'
          ? 'يربط كل حدث أعلى بمسارات التهديد على اليسار وآثاره على اليمين، مع سلاسل الحواجز للضوابط الوقائية والمخففة.'
          : 'Links each top event to its threat pathways on the left and its consequence pathways on the right, with control-barrier chains for both preventive and mitigating sides.'}
        actions={
          <>
            {demoMode && <Badge tone="warning">{t('demoMode')}</Badge>}
            <Button variant="primary" Icon={Plus} onClick={startCreate}>
              {lang === 'ar' ? 'تحليل جديد' : 'New bow-tie'}
            </Button>
          </>
        }
      />

      <div className="kpi-grid-sm">
        <Kpi Icon={GitMerge}       label={lang === 'ar' ? 'إجمالي' : 'Total'}        value={rows.length} tone="primary" />
        <Kpi Icon={ShieldCheck}    label={lang === 'ar' ? 'معتمدة' : 'Approved'}     value={approved}    tone="success" />
        <Kpi Icon={ClipboardCheck} label={lang === 'ar' ? 'مسودات' : 'In progress'}  value={draft}       tone="warning" />
        <Kpi Icon={AlertTriangle}  label={lang === 'ar' ? 'متبقّي مرتفع' : 'High residual'} value={critical} tone="danger" />
      </div>

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
              rows={rows}
              rowHref={r => `/risk/bowtie/${r.externalId}`}
              empty={<EmptyState
                Icon={GitMerge}
                title={lang === 'ar' ? 'لا توجد تحليلات بعد' : 'No bow-ties yet'}
                sub={lang === 'ar'
                  ? 'ابدأ تحليلاً جديداً للحدث الأعلى لربطه بالتهديدات والآثار والضوابط.'
                  : 'Start a new top-event analysis to map threats, consequences, and barrier-controls.'} />}
            />
        }
      </Card>
    </Shell>
  );
}

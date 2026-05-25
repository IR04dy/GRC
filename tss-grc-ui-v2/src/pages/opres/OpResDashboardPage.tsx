import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Shield, BookOpen, Gauge, Repeat, BookText, Plus,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  opresApi, PILLAR_LABEL, STATUS_LABEL, STATUS_TONE, MATURITY_TONE,
  MATURITY_LABEL, type AssessmentResponse, type PillarResponse,
  type PillarCode,
} from '../../lib/opres';

/**
 * Operational Resilience Dashboard — /opres/dashboard.
 *
 * SAMA Operational Resilience Framework (Sept 2024). Surfaces the
 * current annual cycle: 5 KPI tiles (assessments by state +
 * latest avg current/target maturity), the 5-pillar heatmap from
 * the most recent assessment, and a CTA into the cell editor.
 */
const PILLAR_ICON: Record<PillarCode, typeof Shield> = {
  GOVERNANCE:      Shield,
  IBS_MAPPING:     BookOpen,
  TOLERANCES:      Gauge,
  MAPPING_TESTING: Repeat,
  LESSONS:         BookText,
};

export function OpResDashboardPage() {
  const { lang } = useI18n();
  const nav = useNavigate();
  const demoMode = !isAuthenticated();

  const [pillars, setPillars] = useState<PillarResponse[]>([]);
  const [assessments, setAssessments] = useState<AssessmentResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const [pl, list] = await Promise.all([
          opresApi.listPillars().catch(() => [] as PillarResponse[]),
          opresApi.listAssessments(0, 10).catch(() => ({
            content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
          })),
        ]);
        if (!cancelled) {
          setPillars(pl);
          setAssessments(list.content);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message :
            (lang === 'ar' ? 'تعذّر تحميل لوحة الصمود التشغيلي'
                           : 'Failed to load Op-Res dashboard'));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const latest = assessments[0];
  const inProgress = assessments.filter(a => a.status === 'IN_PROGRESS').length;
  const approved   = assessments.filter(a => a.status === 'APPROVED').length;
  const drafts     = assessments.filter(a => a.status === 'DRAFT').length;

  // Build a pillar-keyed view of the latest assessment's cells.
  const cellByPillar = useMemo(() => {
    const m = new Map<PillarCode, { cur?: number; tgt?: number }>();
    if (!latest) return m;
    for (const c of latest.cells) {
      m.set(c.pillarCodeSnapshot, {
        cur: c.currentScore ?? undefined,
        tgt: c.targetScore  ?? undefined,
      });
    }
    return m;
  }, [latest]);

  const sortedPillars = [...pillars].sort((a, b) => a.sortOrder - b.sortOrder);

  return (
    <Shell
      title={lang === 'ar'
        ? 'لوحة الصمود التشغيلي'
        : 'Operational Resilience Dashboard'}
      sub={lang === 'ar'
        ? 'بحسب إطار الصمود التشغيلي 2024 لدى ساما — ٥ ركائز × ٥ مستويات نضج.'
        : 'SAMA Operational Resilience Framework 2024 — 5 pillars × 5 maturity levels.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'الصمود التشغيلي · المرحلة ١٤'
                                : 'Operational Resilience · Sprint 14'}
        title={lang === 'ar' ? 'لوحة الصمود التشغيلي'
                              : 'Operational Resilience Dashboard'}
        sub={lang === 'ar' ? 'بحسب إطار الصمود التشغيلي 2024 لدى ساما'
                            : 'SAMA Operational Resilience Framework 2024'}
        actions={
          <Button onClick={() => nav('/opres/assessments')}
                  variant="primary" Icon={Plus}>
            {lang === 'ar' ? 'فتح التقييمات' : 'Open Assessments'}
          </Button>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      {/* KPIs */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 12,
        marginBottom: 16,
      }}>
        <Kpi Icon={Shield} tone="info"
          label={lang === 'ar' ? 'تقييمات قيد التعبئة' : 'In Progress'}
          value={inProgress}
          to="/opres/assessments" />
        <Kpi Icon={BookOpen} tone="warning"
          label={lang === 'ar' ? 'مسوّدات' : 'Drafts'}
          value={drafts}
          to="/opres/assessments" />
        <Kpi Icon={Gauge} tone="success"
          label={lang === 'ar' ? 'مُعتمَدة' : 'Approved'}
          value={approved}
          to="/opres/assessments" />
        <Kpi Icon={Repeat} tone="neutral"
          label={lang === 'ar' ? 'متوسّط النضج الحالي' : 'Avg Current Maturity'}
          value={latest?.avgCurrentScore != null
            ? Number(latest.avgCurrentScore).toFixed(1)
            : '—'} />
        <Kpi Icon={BookText} tone="info"
          label={lang === 'ar' ? 'متوسّط النضج المستهدف' : 'Avg Target Maturity'}
          value={latest?.avgTargetScore != null
            ? Number(latest.avgTargetScore).toFixed(1)
            : '—'} />
      </div>

      {/* Latest assessment heatmap */}
      <Card padded
        title={lang === 'ar' ? 'أحدث تقييم — خريطة النضج بحسب الركائز'
                              : 'Latest Assessment — Pillar Maturity Heatmap'}
        action={latest
          ? <Badge tone={STATUS_TONE[latest.status]}>
              {STATUS_LABEL[latest.status][lang === 'ar' ? 'ar' : 'en']}
              {' · '}FY{latest.fiscalYear}
            </Badge>
          : undefined}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : !latest ? (
          <EmptyState
            Icon={Shield}
            title={lang === 'ar' ? 'لا يوجد تقييم بعد' : 'No assessment yet'}
            sub={demoMode
              ? (lang === 'ar' ? 'سجّل دخولك لعرض التقييمات.' : 'Log in to view assessments.')
              : (lang === 'ar'
                ? 'افتح تقييماً جديداً للسنة المالية الحالية لتبدأ تعبئة الركائز الخمس.'
                : 'Open a new assessment for the current fiscal year to start filling in the 5 pillars.')} />
        ) : (
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: 12,
          }}>
            {sortedPillars.map(p => {
              const cell = cellByPillar.get(p.code);
              const cur = cell?.cur;
              const tgt = cell?.tgt;
              const gap = (cur != null && tgt != null) ? (tgt - cur) : null;
              const Icon = PILLAR_ICON[p.code] || Shield;
              return (
                <div key={p.id}
                     onClick={() => nav(`/opres/assessments/${latest.id}`)}
                     style={{
                       border: '1px solid var(--card-border)',
                       borderRadius: 8,
                       padding: 12,
                       cursor: 'pointer',
                       background: 'var(--card-bg)',
                     }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8,
                                marginBottom: 6 }}>
                    <Icon size={16} style={{ opacity: 0.7 }} />
                    <span style={{ fontWeight: 600, fontSize: 13 }}>
                      {PILLAR_LABEL[p.code][lang === 'ar' ? 'ar' : 'en']}
                    </span>
                  </div>
                  {p.samaReference && (
                    <div style={{ fontSize: 11, opacity: 0.6, marginBottom: 6 }}>
                      SAMA Op-Res {p.samaReference}
                    </div>
                  )}
                  <div style={{ display: 'flex', gap: 8, marginTop: 6,
                                alignItems: 'center', flexWrap: 'wrap' }}>
                    {cur != null ? (
                      <Badge tone={MATURITY_TONE[cur]}>
                        {lang === 'ar' ? 'الحالي' : 'Current'}: {cur} ·{' '}
                        {MATURITY_LABEL[cur][lang === 'ar' ? 'ar' : 'en']}
                      </Badge>
                    ) : (
                      <Badge tone="neutral">
                        {lang === 'ar' ? 'لم تُعبَّأ' : 'Not yet scored'}
                      </Badge>
                    )}
                    {tgt != null && (
                      <Badge tone="info">
                        {lang === 'ar' ? 'المستهدف' : 'Target'}: {tgt}
                      </Badge>
                    )}
                    {gap != null && gap > 0 && (
                      <Badge tone={gap >= 2 ? 'danger' : 'warning'}>
                        {lang === 'ar' ? `فجوة ${gap}` : `Gap ${gap}`}
                      </Badge>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Card>
    </Shell>
  );
}

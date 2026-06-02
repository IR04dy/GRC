import { useEffect, useState } from 'react';
import { AlertTriangle, ClipboardCheck, FileText, ShieldCheck, Timer, Building2, BookOpen, Bell } from 'lucide-react';
import { DomainDashboardShell, type DomainDashboardKpi, type DomainDashboardWidget } from '../../components/dashboard/DomainDashboardShell';
import { DashboardLoading, DashboardError } from '../../components/dashboard/DashboardLoadingState';
import { Badge } from '../../components/ui/Badge';
import { LineChart } from '../../components/charts/LineChart';
import { RadialGauge } from '../../components/viz/RadialGauge';
import { GAUGE_MD } from '../../lib/gaugeSizes';
import { useI18n } from '../../lib/i18n';
import { bcmApi, dashboardApi } from '../../lib/api';
import { useDashboardLoader } from '../../lib/useDashboardLoader';
import { boardPackSection, type BoardPackSection } from '../../lib/bcmBoardPack';
import { regulatorCountdownSummary, type CountdownSummary } from '../../lib/bcmRegulatorCountdown';

/**
 * BCM Dashboard — task #36.
 *
 * Per Saudi GRC prompt §30.2: critical services, BIA/BCP/DRP
 * completion, RTO/RPO compliance, exercise success, open BCM gaps.
 * Pulls from `/api/v1/bcm/stats` + lists; falls back to a
 * representative scaffold while the backend is being wired.
 */
interface BcmStatsShape {
  criticalServiceCount?: number;
  biaCompletionPercent?: number;
  bcpCompletionPercent?: number;
  drpCompletionPercent?: number;
  rtoCompliancePercent?: number;
  rpoCompliancePercent?: number;
  exerciseSuccessRate?: number;
  failedExercises?: number;
  openGaps?: number;
  overdueCapas?: number;
  upcomingExercises?: number;
}

export function BcmDashboardPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const title   = isAr ? 'لوحة استمرارية الأعمال' : 'BCM dashboard';
  const eyebrow = isAr ? 'استمرارية الأعمال' : 'Business continuity';

  const { data, state, errMsg, retry } = useDashboardLoader<BcmStatsShape>(
    () => bcmApi.stats() as Promise<BcmStatsShape>);

  // Supplementary Sprint-7 tiles — load in parallel; failures degrade silently.
  const [boardPack, setBoardPack] = useState<BoardPackSection | null>(null);
  const [countdowns, setCountdowns] = useState<CountdownSummary | null>(null);
  const [trend, setTrend] = useState<Array<{ week: string; exercises: number }>>([]);
  useEffect(() => {
    boardPackSection().then(setBoardPack).catch(() => setBoardPack(null));
    regulatorCountdownSummary().then(setCountdowns).catch(() => setCountdowns(null));
    dashboardApi.kpiTrend('bcm_exercises', 12)
      .then(pts => setTrend(pts.map(p => ({ week: p.label, exercises: p.value }))))
      .catch(() => setTrend([]));
  }, []);

  if (state === 'loading') return <DashboardLoading title={title} eyebrow={eyebrow} />;
  if (state === 'error' || !data) {
    return <DashboardError title={title} eyebrow={eyebrow} errMsg={errMsg} onRetry={retry} />;
  }
  const stats = data;

  const kpis: DomainDashboardKpi[] = [
    { Icon: Building2, label: isAr ? 'خدمات حرجة' : 'Critical services', value: stats.criticalServiceCount ?? 0, tone: 'primary' },
    { Icon: ClipboardCheck, label: isAr ? 'اكتمال BIA' : 'BIA completion', value: `${stats.biaCompletionPercent ?? 0}%`,
      tone: tonePct(stats.biaCompletionPercent) },
    { Icon: FileText, label: isAr ? 'اكتمال BCP' : 'BCP completion', value: `${stats.bcpCompletionPercent ?? 0}%`,
      tone: tonePct(stats.bcpCompletionPercent) },
    { Icon: ShieldCheck, label: isAr ? 'اكتمال DRP' : 'DRP completion', value: `${stats.drpCompletionPercent ?? 0}%`,
      tone: tonePct(stats.drpCompletionPercent) },
    { Icon: Timer, label: isAr ? 'الالتزام بـ RTO' : 'RTO compliance', value: `${stats.rtoCompliancePercent ?? 0}%`,
      tone: tonePct(stats.rtoCompliancePercent) },
    { Icon: AlertTriangle, label: isAr ? 'فجوات مفتوحة' : 'Open BCM gaps', value: stats.openGaps ?? 0,
      tone: (stats.openGaps ?? 0) > 0 ? 'warning' : 'success' },
  ];

  const readinessBands = [
    { from: 0,  to: 60,  color: 'var(--danger)' },
    { from: 60, to: 85,  color: 'var(--warning)' },
    { from: 85, to: 101, color: 'var(--success)' },
  ];
  const widgets: DomainDashboardWidget[] = [
    {
      id: 'programme-readiness',
      title: isAr ? 'جاهزية برنامج الاستمرارية' : 'Continuity programme readiness',
      body: (
        <div style={{ display: 'flex', justifyContent: 'space-around', alignItems: 'center', flexWrap: 'wrap', gap: 8, paddingTop: 4 }}>
          {[
            { label: 'BIA', v: stats.biaCompletionPercent ?? 0 },
            { label: 'BCP', v: stats.bcpCompletionPercent ?? 0 },
            { label: 'DRP', v: stats.drpCompletionPercent ?? 0 },
          ].map(g => (
            <RadialGauge key={g.label} value={g.v} max={100} {...GAUGE_MD}
              caption={g.label} unit="%" rtl={isAr} bands={readinessBands}
              aria-label={`${g.label} ${isAr ? 'اكتمال' : 'completion'} ${g.v}%`} />
          ))}
        </div>
      ),
      href: '/bcm',
    },
    {
      id: 'exercise-cadence',
      title: isAr ? 'وتيرة التمارين المنفّذة — 12 أسبوع' : 'Exercises conducted · 12 weeks',
      body: (
        <LineChart
          data={trend}
          xKey="week"
          height={200}
          series={[{ key: 'exercises', name: isAr ? 'تمارين منفّذة (تراكمي)' : 'Conducted (cumulative)', color: 'var(--primary)' }]}
          emptyState={isAr ? 'لا توجد بيانات اتجاه' : 'No trend data yet'} />
      ),
      href: '/bcm?tab=exercises',
    },
    {
      id: 'rto-rpo',
      title: isAr ? 'الالتزام بأهداف الاستعادة' : 'Recovery objectives compliance',
      body: (
        <ul className="text-xs" style={{ marginInlineStart: 0, paddingInlineStart: 18, lineHeight: 1.8 }}>
          <li>{isAr ? 'الالتزام بـ RTO' : 'RTO compliance'}: <strong>{stats.rtoCompliancePercent}%</strong></li>
          <li>{isAr ? 'الالتزام بـ RPO' : 'RPO compliance'}: <strong>{stats.rpoCompliancePercent}%</strong></li>
        </ul>
      ),
      href: '/bcm',
    },
    {
      id: 'exercises',
      title: isAr ? 'التمارين والاختبارات' : 'Exercises & testing',
      badge: (stats.failedExercises ?? 0) > 0 ? String(stats.failedExercises) : undefined,
      badgeTone: (stats.failedExercises ?? 0) > 0 ? 'warning' : 'success',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'نسبة نجاح التمارين' : 'Success rate'}: <strong>{stats.exerciseSuccessRate}%</strong></div>
          <div>{isAr ? 'تمارين فاشلة' : 'Failed exercises'}: <Badge tone="warning">{stats.failedExercises}</Badge></div>
          <div>{isAr ? 'تمارين قادمة' : 'Upcoming'}: <Badge tone="info">{stats.upcomingExercises}</Badge></div>
        </div>
      ),
      href: '/bcm?tab=exercises',
    },
    {
      id: 'gaps',
      title: isAr ? 'فجوات BCM والإجراءات التصحيحية' : 'BCM gaps + corrective actions',
      badge: String((stats.openGaps ?? 0) + (stats.overdueCapas ?? 0)),
      badgeTone: 'warning',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'فجوات مفتوحة' : 'Open gaps'}: <strong>{stats.openGaps}</strong></div>
          <div>{isAr ? 'CAPAs متأخرة' : 'Overdue CAPAs'}: <Badge tone="danger">{stats.overdueCapas}</Badge></div>
        </div>
      ),
      href: '/bcm?tab=gaps',
    },
    // Sprint-7 Slice 7-H — Board Pack section snapshot. Degrades to a
    // single-line hint if the aggregator endpoint is unavailable.
    {
      id: 'board-pack',
      title: isAr ? 'حزمة المجلس — لقطة BCM' : 'Board pack — BCM snapshot',
      badge: boardPack
        ? `${Math.round(boardPack.readiness.averageScore)}%`
        : undefined,
      badgeTone: boardPack && boardPack.readiness.averageScore >= 70
        ? 'success'
        : boardPack ? 'warning' : undefined,
      body: boardPack ? (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>
            <BookOpen size={12} style={{ verticalAlign: 'middle', marginInlineEnd: 4 }} />
            {isAr ? 'جاهزية الاستمرارية' : 'Continuity readiness'}:{' '}
            <strong>{boardPack.readiness.averageScore.toFixed(1)}%</strong>{' '}
            <span className="text-muted">
              ({boardPack.readiness.biaCount} BIA{boardPack.readiness.biaCount === 1 ? '' : 's'})
            </span>
          </div>
          <div>
            {isAr ? 'تمارين أخيرة' : 'Recent exercises'}:{' '}
            <strong>{boardPack.recentExercises.length}</strong>
          </div>
          <div>
            {isAr ? 'CAPAs نشطة' : 'Active CAPAs'}:{' '}
            <Badge tone={boardPack.capa.overdueCount > 0 ? 'danger' : 'info'}>
              {boardPack.capa.activeOpenCount}
              {boardPack.capa.overdueCount > 0
                ? ` (${boardPack.capa.overdueCount} ${isAr ? 'متأخر' : 'overdue'})`
                : ''}
            </Badge>
          </div>
          {boardPack.lastAar && (
            <div>
              {isAr ? 'آخر AAR' : 'Last AAR'}:{' '}
              <strong>{isAr ? (boardPack.lastAar.titleAr ?? boardPack.lastAar.titleEn) : boardPack.lastAar.titleEn}</strong>
            </div>
          )}
        </div>
      ) : (
        <div className="text-xs text-muted">
          {isAr
            ? 'لقطة حزمة المجلس قيد التحضير.'
            : 'Board-pack snapshot warming up.'}
        </div>
      ),
      href: '/bcm/readiness',
    },
    // Sprint-7 Slice 7-H — Regulator countdown timers.
    {
      id: 'regulator-countdowns',
      title: isAr ? 'مؤقتات إخطار الجهات التنظيمية' : 'Regulator notification countdowns',
      badge: countdowns
        ? String(countdowns.breachedCount + countdowns.nearDeadlineCount)
        : undefined,
      badgeTone: countdowns && countdowns.breachedCount > 0
        ? 'danger'
        : countdowns && countdowns.nearDeadlineCount > 0
          ? 'warning'
          : countdowns && countdowns.totalRows > 0
            ? 'success'
            : undefined,
      body: countdowns ? (
        countdowns.totalRows === 0 ? (
          <div className="text-xs text-muted">
            {isAr ? 'لا توجد حوادث نشطة تتطلب إخطاراً.' : 'No active events requiring notification.'}
          </div>
        ) : (
          <div className="text-xs" style={{ lineHeight: 1.8 }}>
            <div>
              <Bell size={12} style={{ verticalAlign: 'middle', marginInlineEnd: 4 }} />
              {isAr ? 'متجاوز للمهلة' : 'Breached'}:{' '}
              <Badge tone="danger">{countdowns.breachedCount}</Badge>
            </div>
            <div>
              {isAr ? 'قرب الموعد' : 'Near deadline'}:{' '}
              <Badge tone="warning">{countdowns.nearDeadlineCount}</Badge>
            </div>
            <div>
              {isAr ? 'في الوقت' : 'On track'}:{' '}
              <Badge tone="info">{countdowns.onTrackCount}</Badge>
            </div>
            <div>
              {isAr ? 'تم الإخطار' : 'Notified'}:{' '}
              <Badge tone="success">{countdowns.notifiedCount}</Badge>
            </div>
          </div>
        )
      ) : (
        <div className="text-xs text-muted">
          {isAr ? 'تحميل المؤقتات…' : 'Loading countdowns…'}
        </div>
      ),
      href: '/bcm/crisis-room',
    },
    {
      id: 'next-actions',
      title: isAr ? 'الخطوات المقترحة' : 'Suggested next actions',
      body: (
        <ul className="text-xs" style={{ paddingInlineStart: 18, lineHeight: 1.8 }}>
          {(stats.biaCompletionPercent ?? 0) < 80 && (
            <li>{isAr ? 'استكمال BIA للخدمات الحرجة المتبقية' : 'Complete BIA for remaining critical services'}</li>
          )}
          {(stats.bcpCompletionPercent ?? 0) < 80 && (
            <li>{isAr ? 'إصدار BCP لكل خدمة معتمدة BIA' : 'Publish BCP for every BIA-approved service'}</li>
          )}
          {(stats.failedExercises ?? 0) > 0 && (
            <li>{isAr ? 'متابعة التمارين الفاشلة وإغلاق فجواتها' : 'Investigate failed exercises and close their gaps'}</li>
          )}
          {(stats.overdueCapas ?? 0) > 0 && (
            <li>{isAr ? 'تصعيد الإجراءات التصحيحية المتأخرة' : 'Escalate overdue corrective actions'}</li>
          )}
          {(stats.bcpCompletionPercent ?? 0) >= 80 && (stats.failedExercises ?? 0) === 0
            && (stats.overdueCapas ?? 0) === 0 && (
              <li className="text-muted">{isAr ? 'وضع BCM جيد — تابع روتين المراجعة.' : 'BCM posture healthy — keep the review rhythm.'}</li>
          )}
        </ul>
      ),
    },
  ];

  return (
    <DomainDashboardShell
      title={isAr ? 'لوحة استمرارية الأعمال' : 'BCM dashboard'}
      sub={isAr ? 'نظرة سريعة على جاهزية استمرارية الأعمال' : 'Snapshot of business-continuity readiness'}
      eyebrow={isAr ? 'الاستمرارية' : 'Resilience'}
      kpis={kpis}
      widgets={widgets}
    />
  );
}

function tonePct(v?: number): 'success' | 'warning' | 'danger' | 'info' {
  const n = v ?? 0;
  if (n >= 85) return 'success';
  if (n >= 60) return 'info';
  if (n >= 30) return 'warning';
  return 'danger';
}

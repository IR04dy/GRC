import { useEffect, useState } from 'react';
import { AlertTriangle, ClipboardCheck, FileText, ShieldCheck, Timer, Building2, BookOpen, Bell, Check } from 'lucide-react';
import { DomainDashboardShell, type DomainDashboardKpi, type DomainDashboardWidget } from '../../components/dashboard/DomainDashboardShell';
import { DashboardLoading, DashboardError } from '../../components/dashboard/DashboardLoadingState';
import { Badge } from '../../components/ui/Badge';
import { LineChart } from '../../components/charts/LineChart';
import { RadialGauge } from '../../components/viz/RadialGauge';
import { GAUGE_MD } from '../../lib/gaugeSizes';
import { ragBands, ragTonePercent as tonePct } from '../../lib/ragColors';
import { useI18n } from '../../lib/i18n';
import { bcmApi, dashboardApi } from '../../lib/api';
import { useDashboardLoader } from '../../lib/useDashboardLoader';
import { boardPackSection, type BoardPackSection } from '../../lib/bcmBoardPack';
import { regulatorCountdownSummary, type CountdownSummary } from '../../lib/bcmRegulatorCountdown';

/**
 * BCM Dashboard — task #36.
 *
 * Per Saudi GRC prompt §30.2: critical services, BIA/BCP/DRP
 * completion, exercise success, open BCM gaps. RTO/RPO compliance is NOT shown as a
 * figure: nothing computes it (F2-457), so it renders as an explicit absence.
 * Pulls from `/api/v1/bcm/stats` + lists; falls back to a
 * representative scaffold while the backend is being wired.
 */
interface BcmStatsShape {
  criticalServiceCount?: number;
  biaCompletionPercent?: number;
  bcpCompletionPercent?: number;
  drpCompletionPercent?: number;
  // F2-457: rtoCompliancePercent / rpoCompliancePercent are deliberately ABSENT. The
  // server used to send the exercise completion rate under both names; it now sends
  // neither, and declaring a field the wire never carries is the shape that produced
  // F2-211's em-dash-on-every-row defect.
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

  // UX-008 — tooltip for an unavailable/uninitialised metric, so a "—" tile
  // reads as "not yet measured" and never collapses to a misleading 0.
  const notComputed = isAr ? 'لم يُحتسب بعد' : 'Not yet computed';
  // F2-457 — distinct from notComputed on purpose. UX-008's three states are a value,
  // a metric not yet measured, and one the platform cannot measure at all; RTO/RPO
  // compliance is the third, because no exercise records an achieved recovery time.
  const notMeasured = isAr
    ? 'لا يُسجَّل أي تمرين زمن الاستعادة الفعلي، فلا يمكن حساب الالتزام'
    : 'No exercise records an achieved recovery time, so compliance cannot be computed';

  const kpis: DomainDashboardKpi[] = [
    { Icon: Building2, label: isAr ? 'خدمات حرجة' : 'Critical services', value: stats.criticalServiceCount,
      tone: 'primary', emptyHint: notComputed },
    { Icon: ClipboardCheck, label: isAr ? 'اكتمال BIA' : 'BIA completion',
      value: stats.biaCompletionPercent == null ? null : `${stats.biaCompletionPercent}%`,
      tone: tonePct(stats.biaCompletionPercent), emptyHint: notComputed },
    { Icon: FileText, label: isAr ? 'اكتمال BCP' : 'BCP completion',
      value: stats.bcpCompletionPercent == null ? null : `${stats.bcpCompletionPercent}%`,
      tone: tonePct(stats.bcpCompletionPercent), emptyHint: notComputed },
    { Icon: ShieldCheck, label: isAr ? 'اكتمال DRP' : 'DRP completion',
      value: stats.drpCompletionPercent == null ? null : `${stats.drpCompletionPercent}%`,
      tone: tonePct(stats.drpCompletionPercent), emptyHint: notComputed },
    // F2-457: kept as an explicit em-dash rather than removed. UX-008's convention exists
    // so a metric the platform cannot compute stays VISIBLE as absent; deleting the tile
    // would quietly drop the question instead of answering it.
    { Icon: Timer, label: isAr ? 'الالتزام بـ RTO' : 'RTO compliance',
      value: null, emptyHint: notMeasured },
    // Open BCM gaps — zero here is genuinely good ("clean"), so a real 0 gets a
    // confirming ✓ + success tint, while a missing metric shows the no-data dash.
    { Icon: AlertTriangle, label: isAr ? 'فجوات مفتوحة' : 'Open BCM gaps', value: stats.openGaps,
      tone: (stats.openGaps ?? 0) > 0 ? 'warning' : 'success',
      zeroConfirm: true, emptyHint: notComputed },
  ];

  const readinessBands = ragBands();
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
      // F2-457: retitled with the figures. This read 'Recovery objectives compliance'
      // over <strong>{stats.rtoCompliancePercent}%</strong> -- unguarded, so once the
      // server stopped sending the key it would have rendered 'undefined%'. Retiring a
      // figure means retiring the sentence that advertised it (F2-207).
      title: isAr ? 'أهداف الاستعادة' : 'Recovery objectives',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr
            ? 'RTO و RPO مسجّلان على كل خطة وتحليل أثر أعمال'
            : 'RTO and RPO targets are recorded on each plan and BIA'}</div>
          <div title={notMeasured} aria-label={notMeasured}>
            {isAr ? 'الالتزام بها' : 'Compliance against them'}: <strong>—</strong>
          </div>
        </div>
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
          {/* UX-008 — a genuine 0 reads as "clean" (✓), a missing metric as the
              no-data em-dash, so the two never look identical in the record list. */}
          <div>{isAr ? 'فجوات مفتوحة' : 'Open gaps'}: <MetricCount value={stats.openGaps} zeroGood hint={notComputed} /></div>
          <div>{isAr ? 'CAPAs متأخرة' : 'Overdue CAPAs'}: <MetricCount value={stats.overdueCapas} zeroGood danger hint={notComputed} /></div>
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
          {boardPack.evidence && (
            <div>
              {isAr ? 'طلبات الأدلة' : 'Evidence requests'}:{' '}
              <Badge tone={boardPack.evidence.overdueCount > 0 || boardPack.evidence.rejectedCount > 0 ? 'danger' : 'info'}>
                {boardPack.evidence.activeOpenCount}
                {boardPack.evidence.overdueCount > 0 ? ` (${boardPack.evidence.overdueCount} ${isAr ? 'متأخر' : 'overdue'})` : ''}
                {boardPack.evidence.rejectedCount > 0 ? ` · ${boardPack.evidence.rejectedCount} ${isAr ? 'مرفوض' : 'rejected'}` : ''}
              </Badge>
            </div>
          )}
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

/**
 * Inline metric for the recent-records / gaps list (UX-008). Distinguishes:
 *   • no-data (null/undefined) → the existing `.empty-inline-compact` em-dash
 *     with a "not yet computed" tooltip;
 *   • a genuine, good zero (`zeroGood`) → a confirming ✓ + the number in
 *     success tone, so "0 open gaps" reads as clean rather than unmeasured;
 *   • any other value → bold in the caller's tone.
 */
function MetricCount({ value, zeroGood, danger, hint }: {
  value: number | null | undefined;
  zeroGood?: boolean;
  danger?: boolean;
  hint: string;
}) {
  if (value == null) {
    return (
      <span className="empty-inline-compact" title={hint} aria-label={hint}
        style={{ display: 'inline', color: 'var(--ink-4)', fontWeight: 700 }}>—</span>
    );
  }
  if (zeroGood && value === 0) {
    return (
      <strong style={{ color: 'var(--success-fg)', display: 'inline-flex', alignItems: 'center', gap: 3 }}>
        <Check size={13} strokeWidth={3} aria-hidden /> 0
      </strong>
    );
  }
  return <strong style={danger ? { color: 'var(--danger-fg)' } : undefined}>{value}</strong>;
}

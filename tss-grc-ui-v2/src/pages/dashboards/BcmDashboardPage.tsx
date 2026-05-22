import { AlertTriangle, ClipboardCheck, FileText, ShieldCheck, Timer, Building2 } from 'lucide-react';
import { DomainDashboardShell, type DomainDashboardKpi, type DomainDashboardWidget } from '../../components/dashboard/DomainDashboardShell';
import { DashboardLoading, DashboardError } from '../../components/dashboard/DashboardLoadingState';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { bcmApi } from '../../lib/api';
import { useDashboardLoader } from '../../lib/useDashboardLoader';

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

  const widgets: DomainDashboardWidget[] = [
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

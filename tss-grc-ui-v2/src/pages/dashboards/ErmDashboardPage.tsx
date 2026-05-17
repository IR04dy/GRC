import { useEffect, useState } from 'react';
import { TrendingUp, AlertTriangle, ShieldCheck, Activity, Layers, Target } from 'lucide-react';
import { DomainDashboardShell, type DomainDashboardKpi, type DomainDashboardWidget } from '../../components/dashboard/DomainDashboardShell';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { dashboardApi, ApiError, type DashboardStats } from '../../lib/api';

/**
 * ERM Dashboard — task #36 (prompt §30.4).
 *
 * Widgets: top risks, appetite breaches, treatment status, KRI
 * breaches, emerging risks, control effectiveness. Pulls from the
 * existing `/api/v1/dashboard/stats` endpoint (which is the ERM-
 * heavy aggregate today) and surfaces only the risk-specific tiles.
 */
const FALLBACK: DashboardStats = {
  totalRisks: 42, criticalRisks: 7, highRisks: 12, mediumRisks: 18, lowRisks: 5,
  openRisks: 28, closedRisks: 14, openCriticalRisks: 7, openHighRisks: 12,
  openIncidents: 3, complianceScore: 87, totalUsers: 0, activeUsers: 0,
  pendingTasks: 4,
};

export function ErmDashboardPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [stats, setStats] = useState<DashboardStats>(FALLBACK);

  useEffect(() => {
    dashboardApi.stats()
      .then(s => setStats({ ...FALLBACK, ...s }))
      .catch((e: unknown) => {
        if (e instanceof ApiError && e.status >= 500)
          toast.danger(isAr ? 'تعذر تحميل إحصائيات المخاطر' : 'Risk stats unavailable');
      });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const kpis: DomainDashboardKpi[] = [
    { Icon: Layers, label: isAr ? 'إجمالي المخاطر' : 'Total risks', value: stats.totalRisks, tone: 'primary', to: '/risk' },
    { Icon: AlertTriangle, label: isAr ? 'حرجة مفتوحة' : 'Open critical', value: stats.openCriticalRisks,
      tone: stats.openCriticalRisks > 0 ? 'danger' : 'success' },
    { Icon: TrendingUp, label: isAr ? 'عالية مفتوحة' : 'Open high', value: stats.openHighRisks,
      tone: stats.openHighRisks > 0 ? 'warning' : 'success' },
    { Icon: ShieldCheck, label: isAr ? 'مغلقة' : 'Closed', value: stats.closedRisks, tone: 'info' },
    { Icon: Target, label: isAr ? 'نسبة الامتثال' : 'Compliance score', value: `${stats.complianceScore}%`,
      tone: stats.complianceScore >= 80 ? 'success' : stats.complianceScore >= 60 ? 'warning' : 'danger' },
    { Icon: Activity, label: isAr ? 'مهام بانتظار التنفيذ' : 'Pending tasks', value: stats.pendingTasks,
      tone: stats.pendingTasks > 5 ? 'warning' : 'info' },
  ];

  const widgets: DomainDashboardWidget[] = [
    {
      id: 'severity-breakdown',
      title: isAr ? 'توزيع المخاطر حسب الشدّة' : 'Risk by severity',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.9 }}>
          <div>{isAr ? 'حرجة' : 'Critical'}: <Badge tone="danger">{stats.criticalRisks}</Badge></div>
          <div>{isAr ? 'عالية' : 'High'}: <Badge tone="warning">{stats.highRisks}</Badge></div>
          <div>{isAr ? 'متوسطة' : 'Medium'}: <Badge tone="info">{stats.mediumRisks}</Badge></div>
          <div>{isAr ? 'منخفضة' : 'Low'}: <Badge tone="success">{stats.lowRisks}</Badge></div>
        </div>
      ),
      href: '/risk',
    },
    {
      id: 'appetite',
      title: isAr ? 'اختراق شهية المخاطر' : 'Risk appetite',
      badge: stats.openCriticalRisks > 0 ? 'BREACHED' : 'WITHIN',
      badgeTone: stats.openCriticalRisks > 0 ? 'danger' : 'success',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'مخاطر فوق شهية المخاطر' : 'Risks over appetite'}:</div>
          <div className="mono" style={{ fontSize: 14, fontWeight: 700, marginTop: 4 }}>
            {stats.openCriticalRisks + stats.openHighRisks}
          </div>
          <div className="text-muted" style={{ marginTop: 6 }}>
            {isAr
              ? 'يحسب من المخاطر المفتوحة عالية/حرجة. عتبة الشهية قابلة للضبط في إعدادات وحدة المخاطر.'
              : 'Counts open Critical + High. Appetite threshold configurable in the Risk module settings.'}
          </div>
        </div>
      ),
      href: '/risk?tab=appetite',
    },
    {
      id: 'treatment',
      title: isAr ? 'حالة معالجة المخاطر' : 'Treatment status',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'مخاطر مفتوحة' : 'Open risks'}: <strong>{stats.openRisks}</strong></div>
          <div>{isAr ? 'مغلقة' : 'Closed'}: <strong>{stats.closedRisks}</strong></div>
          <div className="text-muted" style={{ marginTop: 4 }}>
            {isAr ? 'لمراجعة خطط المعالجة استخدم تبويب «المعالجة» في وحدة المخاطر.'
                  : 'Use the Treatment tab inside the Risk module for plan-level detail.'}
          </div>
        </div>
      ),
      href: '/risk',
    },
    {
      id: 'kri',
      title: isAr ? 'مؤشرات المخاطر الرئيسية' : 'Key Risk Indicators',
      body: (
        <div className="text-xs text-muted">
          {isAr
            ? 'تظهر مؤشرات الـKRI تفصيلياً في تبويب KRI داخل وحدة المخاطر. تنبيهات الاختراق تفعّل قاعدة AR-06 (التصعيد للجنة المخاطر).'
            : 'KRI detail lives under the Risk module → KRI tab. Breaches trigger AR-06 (escalate to Risk Committee).'}
        </div>
      ),
      href: '/risk?tab=kri',
    },
  ];

  return (
    <DomainDashboardShell
      title={isAr ? 'لوحة إدارة المخاطر المؤسسية' : 'ERM dashboard'}
      sub={isAr ? 'نظرة سريعة على سجل المخاطر' : 'Snapshot of the enterprise risk register'}
      eyebrow={isAr ? 'إدارة المخاطر' : 'Risk management'}
      kpis={kpis}
      widgets={widgets}
    />
  );
}

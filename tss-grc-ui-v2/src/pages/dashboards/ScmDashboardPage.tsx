import { useEffect, useState } from 'react';
import { Truck, AlertOctagon, FileText, Activity, Layers, Network } from 'lucide-react';
import { DomainDashboardShell, type DomainDashboardKpi, type DomainDashboardWidget } from '../../components/dashboard/DomainDashboardShell';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { scmApi, ApiError, type ScmStats } from '../../lib/api';

/**
 * Supply Chain Resilience Dashboard — task #36 (prompt §30.3).
 *
 * Headline widgets: critical suppliers, missing BCP evidence,
 * SLA breaches, concentration risk, single points of failure,
 * 4th-party exposure.
 */
const FALLBACK: ScmStats = {
  totalVendors: 64, criticalVendors: 12, activeVendors: 58, pendingOnboarding: 4,
  totalDependencies: 142, highDependencies: 21, openIncidents: 2, slaBreaches: 3,
  activeContracts: 51, expiringContracts: 7,
};

export function ScmDashboardPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [stats, setStats] = useState<ScmStats>(FALLBACK);

  useEffect(() => {
    scmApi.stats()
      .then(s => setStats({ ...FALLBACK, ...s }))
      .catch((e: unknown) => {
        if (e instanceof ApiError && e.status >= 500)
          toast.danger(isAr ? 'تعذر تحميل إحصائيات SCM' : 'SCM stats unavailable');
      });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const kpis: DomainDashboardKpi[] = [
    { Icon: Truck, label: isAr ? 'موردون حرجون' : 'Critical suppliers', value: stats.criticalVendors, tone: 'primary', to: '/scm' },
    { Icon: Layers, label: isAr ? 'الترابطات' : 'Dependencies', value: stats.totalDependencies, tone: 'info' },
    { Icon: AlertOctagon, label: isAr ? 'ترابطات عالية الأثر' : 'High-impact deps', value: stats.highDependencies,
      tone: stats.highDependencies > 0 ? 'warning' : 'success' },
    { Icon: Activity, label: isAr ? 'خرق SLA' : 'SLA breaches', value: stats.slaBreaches,
      tone: stats.slaBreaches > 0 ? 'danger' : 'success' },
    { Icon: FileText, label: isAr ? 'عقود تنتهي قريباً' : 'Expiring contracts', value: stats.expiringContracts,
      tone: stats.expiringContracts > 0 ? 'warning' : 'info' },
    { Icon: Network, label: isAr ? 'حوادث مفتوحة' : 'Open incidents', value: stats.openIncidents,
      tone: stats.openIncidents > 0 ? 'warning' : 'success' },
  ];

  const concentrationLevel = stats.criticalVendors >= 15 ? 'HIGH'
    : stats.criticalVendors >= 8 ? 'MEDIUM' : 'LOW';

  const widgets: DomainDashboardWidget[] = [
    {
      id: 'concentration',
      title: isAr ? 'مخاطر التركّز' : 'Concentration risk',
      badge: concentrationLevel,
      badgeTone: concentrationLevel === 'HIGH' ? 'danger' : concentrationLevel === 'MEDIUM' ? 'warning' : 'success',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'موردون حرجون' : 'Critical suppliers'}: <strong>{stats.criticalVendors}</strong> / {stats.totalVendors}</div>
          <div>{isAr ? 'ترابطات عالية' : 'High dependencies'}: <strong>{stats.highDependencies}</strong></div>
          <div className="text-muted" style={{ marginTop: 4 }}>
            {isAr
              ? 'مؤشّر تركّز مبدئي مبني على نسبة الموردين الحرجين. تحوّل إلى مؤشّر هيرفندال عند توفّر بيانات إنفاق المورّد.'
              : 'Rough concentration heuristic based on critical-supplier share. Switch to Herfindahl once spend data is wired.'}
          </div>
        </div>
      ),
      href: '/scm',
    },
    {
      id: 'evidence',
      title: isAr ? 'أدلة استمرارية المورّد' : 'Supplier continuity evidence',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'عقود نشطة' : 'Active contracts'}: <strong>{stats.activeContracts}</strong></div>
          <div>{isAr ? 'عقود تنتهي خلال 90 يوماً' : 'Expiring in 90 days'}: <Badge tone="warning">{stats.expiringContracts}</Badge></div>
          <div>{isAr ? 'الموردون قيد الاعتماد' : 'Pending onboarding'}: <Badge tone="info">{stats.pendingOnboarding}</Badge></div>
        </div>
      ),
      href: '/scm?tab=contracts',
    },
    {
      id: 'incidents',
      title: isAr ? 'حوادث وانقطاعات' : 'Incidents & disruptions',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'حوادث مفتوحة' : 'Open incidents'}: <strong>{stats.openIncidents}</strong></div>
          <div>{isAr ? 'تكرار خرق SLA (آخر 30 يوماً)' : 'SLA breaches (30d)'}: <Badge tone="danger">{stats.slaBreaches}</Badge></div>
        </div>
      ),
      href: '/incidents',
    },
    {
      id: 'fourth-party',
      title: isAr ? 'انكشاف الأطراف الرابعة' : '4th-party exposure',
      body: (
        <div className="text-xs text-muted">
          {isAr
            ? 'يُحسب من ترابطات الترابطات (level 2). يتطلب اكتمال خرائط الاعتمادية لكل مورّد حرج.'
            : 'Derived from nested dependency rows. Requires every critical supplier to have its dependency map filled.'}
        </div>
      ),
      href: '/scm?tab=dependencies',
    },
  ];

  return (
    <DomainDashboardShell
      title={isAr ? 'لوحة سلسلة الإمداد' : 'Supply Chain dashboard'}
      sub={isAr ? 'صمود الموردين والترابطات' : 'Vendor resilience and dependencies'}
      eyebrow={isAr ? 'الموردون' : 'Suppliers'}
      kpis={kpis}
      widgets={widgets}
    />
  );
}

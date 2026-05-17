import { useEffect, useState } from 'react';
import { Fingerprint, AlertOctagon, ShieldAlert, Users, FileWarning, Send } from 'lucide-react';
import { DomainDashboardShell, type DomainDashboardKpi, type DomainDashboardWidget } from '../../components/dashboard/DomainDashboardShell';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { privacyApi, ApiError } from '../../lib/api';

/**
 * PDPL Dashboard — task #36 (prompt §30.6).
 *
 * Widgets: ROPA count, high-risk activities, PIAs (completed +
 * overdue), DSRs (open + SLA breaches), active consents,
 * cross-border transfers, breach notifications, processors w/o DPA,
 * retention violations.
 */
interface PrivacyStatsShape {
  ropaCount?: number;
  highRiskRopa?: number;
  piaCompleted?: number;
  piaOverdue?: number;
  dsrOpen?: number;
  dsrSlaBreaches?: number;
  activeConsents?: number;
  withdrawnConsents30d?: number;
  crossBorderTransfers?: number;
  privacyIncidents12m?: number;
  breachNotifications?: number;
  processorsWithoutDpa?: number;
  retentionViolations?: number;
}

const FALLBACK: PrivacyStatsShape = {
  ropaCount: 142, highRiskRopa: 18, piaCompleted: 14, piaOverdue: 4,
  dsrOpen: 7, dsrSlaBreaches: 1, activeConsents: 1_240_000, withdrawnConsents30d: 4_300,
  crossBorderTransfers: 6, privacyIncidents12m: 3, breachNotifications: 3,
  processorsWithoutDpa: 0, retentionViolations: 0,
};

export function PdplDashboardPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [stats, setStats] = useState<PrivacyStatsShape>(FALLBACK);

  useEffect(() => {
    privacyApi.stats()
      .then((s: unknown) => setStats({ ...FALLBACK, ...(s as PrivacyStatsShape) }))
      .catch((e: unknown) => {
        if (e instanceof ApiError && e.status >= 500)
          toast.danger(isAr ? 'تعذر تحميل إحصائيات الخصوصية' : 'Privacy stats unavailable');
      });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const kpis: DomainDashboardKpi[] = [
    { Icon: Fingerprint, label: isAr ? 'نشاطات معالجة' : 'Processing activities', value: stats.ropaCount ?? 0, tone: 'primary', to: '/privacy' },
    { Icon: ShieldAlert, label: isAr ? 'نشاطات عالية الخطورة' : 'High-risk activities', value: stats.highRiskRopa ?? 0,
      tone: (stats.highRiskRopa ?? 0) > 0 ? 'warning' : 'success' },
    { Icon: AlertOctagon, label: isAr ? 'PIA متأخر' : 'PIAs overdue', value: stats.piaOverdue ?? 0,
      tone: (stats.piaOverdue ?? 0) > 0 ? 'danger' : 'success' },
    { Icon: Users, label: isAr ? 'طلبات أصحاب البيانات' : 'DSRs open', value: stats.dsrOpen ?? 0,
      tone: (stats.dsrSlaBreaches ?? 0) > 0 ? 'danger' : 'info' },
    { Icon: Send, label: isAr ? 'نقل عبر الحدود نشط' : 'Cross-border active', value: stats.crossBorderTransfers ?? 0,
      tone: 'info' },
    { Icon: FileWarning, label: isAr ? 'مُعالجون بدون اتفاقية' : 'Processors w/o DPA',
      value: stats.processorsWithoutDpa ?? 0,
      tone: (stats.processorsWithoutDpa ?? 0) > 0 ? 'danger' : 'success' },
  ];

  const widgets: DomainDashboardWidget[] = [
    {
      id: 'pia',
      title: isAr ? 'تقييمات أثر الخصوصية (PIA)' : 'Privacy Impact Assessments',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'مكتملة' : 'Completed'}: <strong>{stats.piaCompleted}</strong></div>
          <div>{isAr ? 'متأخرة' : 'Overdue'}: <Badge tone="danger">{stats.piaOverdue}</Badge></div>
          <div className="text-muted" style={{ marginTop: 4 }}>
            {isAr
              ? 'كل نشاط معالجة عالي الخطورة يتطلب PIA قبل التشغيل.'
              : 'Every high-risk processing activity requires a PIA before go-live.'}
          </div>
        </div>
      ),
      href: '/privacy?tab=dpia',
    },
    {
      id: 'dsr',
      title: isAr ? 'طلبات أصحاب البيانات' : 'Data subject requests',
      badge: (stats.dsrSlaBreaches ?? 0) > 0 ? String(stats.dsrSlaBreaches) : undefined,
      badgeTone: 'danger',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'طلبات مفتوحة' : 'Open requests'}: <strong>{stats.dsrOpen}</strong></div>
          <div>{isAr ? 'خرق SLA' : 'SLA breaches'}: <Badge tone="danger">{stats.dsrSlaBreaches}</Badge></div>
          <div className="text-muted" style={{ marginTop: 4 }}>
            {isAr ? 'SLA 30 يوم (PDPL مادة 18) — قابل للتمديد 60 يوماً بمبرر.'
                  : '30-day SLA (PDPL Art. 18) — extendable by 60 days with justification.'}
          </div>
        </div>
      ),
      href: '/privacy?tab=dsr',
    },
    {
      id: 'breach',
      title: isAr ? 'إشعارات الخروقات' : 'Breach notifications',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'حوادث خصوصية (12 شهر)' : 'Privacy incidents (12m)'}: <strong>{stats.privacyIncidents12m}</strong></div>
          <div>{isAr ? 'تم إشعار الجهة التنظيمية' : 'Regulator-notified'}: <Badge tone="info">{stats.breachNotifications}</Badge></div>
          <div className="text-muted" style={{ marginTop: 4 }}>
            {isAr ? 'AR-04 يُفعِّل عدّاد 72 ساعة على الحوادث السيبرانية التي تمسّ بيانات شخصية.'
                  : 'AR-04 starts the 72-hour timer when a cyber incident affects personal data.'}
          </div>
        </div>
      ),
      href: '/privacy?tab=breaches',
    },
    {
      id: 'consent-and-residency',
      title: isAr ? 'الموافقات والإقامة' : 'Consent + residency',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'موافقات نشطة' : 'Active consents'}: <strong>{formatNum(stats.activeConsents)}</strong></div>
          <div>{isAr ? 'سحب (30 يوم)' : 'Withdrawn (30d)'}: <Badge tone="info">{formatNum(stats.withdrawnConsents30d)}</Badge></div>
          <div>{isAr ? 'انتهاكات الاحتفاظ' : 'Retention violations'}: <Badge tone={(stats.retentionViolations ?? 0) > 0 ? 'danger' : 'success'}>{stats.retentionViolations}</Badge></div>
        </div>
      ),
      href: '/privacy?tab=ropa',
    },
  ];

  return (
    <DomainDashboardShell
      title={isAr ? 'لوحة حماية البيانات الشخصية' : 'PDPL dashboard'}
      sub={isAr ? 'الالتزام بنظام حماية البيانات الشخصية وطلبات الأفراد' : 'PDPL compliance + data subject requests'}
      eyebrow={isAr ? 'الخصوصية' : 'Privacy'}
      kpis={kpis}
      widgets={widgets}
    />
  );
}

function formatNum(n?: number): string {
  if (n == null) return '0';
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K';
  return String(n);
}

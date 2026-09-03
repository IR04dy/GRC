import { useEffect, useState } from 'react';
import { Fingerprint, AlertOctagon, ShieldAlert, Users, FileWarning, Send } from 'lucide-react';
import { DomainDashboardShell, type DomainDashboardKpi, type DomainDashboardWidget } from '../../components/dashboard/DomainDashboardShell';
import { DashboardLoading, DashboardError } from '../../components/dashboard/DashboardLoadingState';
import { Badge } from '../../components/ui/Badge';
import { LineChart } from '../../components/charts/LineChart';
import { RadialGauge } from '../../components/viz/RadialGauge';
import { GAUGE_MD } from '../../lib/gaugeSizes';
import { ragBands } from '../../lib/ragColors';
import { useI18n } from '../../lib/i18n';
import { privacyApi, dashboardApi } from '../../lib/api';
import { useDashboardLoader } from '../../lib/useDashboardLoader';

/**
 * PDPL Dashboard — task #36 (prompt §30.6).
 *
 * Widgets: ROPA count, high-risk activities, PIAs (completed +
 * overdue), DSRs (open + past their statutory deadline), active
 * consents, cross-border transfers, SDAIA breach reports.
 *
 * F2-458: "processors w/o DPA" and "retention violations" were in that list and are not
 * measured by this module - both were a server-side literal 0L rendered as a green tile.
 * Retiring a figure means retiring the sentence that advertised it (F2-207).
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
  /**
   * F2-458: breaches whose SDAIA notification has actually been POSTED
   * (`sdaia_notification_status` in SUBMITTED / ACKNOWLEDGED). It used to be the count of
   * breaches at lifecycle status NOTIFIED, which is a different question and answered 4
   * where the honest answer is 0 in both databases.
   */
  breachNotifications?: number;
  // F2-458: `processorsWithoutDpa` and `retentionViolations` are deliberately ABSENT. The
  // server published both as a literal 0L for controls this module does not track, and this
  // page rendered them `> 0 ? danger : success` - so an untracked control showed as a green
  // tile asserting compliance. Declaring a field the wire never carries is the shape that
  // produced F2-211's em-dash-on-every-row defect; the surfaces below render UX-008's
  // em-dash with a reason instead.
}

export function PdplDashboardPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const title   = isAr ? 'لوحة نظام حماية البيانات' : 'PDPL dashboard';
  const eyebrow = isAr ? 'الخصوصية' : 'Privacy';

  const { data, state, errMsg, retry } = useDashboardLoader<PrivacyStatsShape>(
    () => privacyApi.stats() as Promise<PrivacyStatsShape>);

  const [trend, setTrend] = useState<Array<{ week: string; dsr: number; breaches: number }>>([]);
  useEffect(() => {
    Promise.all([
      dashboardApi.kpiTrend('dsr', 12).catch(() => []),
      dashboardApi.kpiTrend('breaches', 12).catch(() => []),
    ]).then(([d, b]) =>
      setTrend(d.map((p, i) => ({ week: p.label, dsr: p.value, breaches: b[i]?.value ?? 0 }))),
    );
  }, []);

  if (state === 'loading') return <DashboardLoading title={title} eyebrow={eyebrow} />;
  if (state === 'error' || !data) {
    return <DashboardError title={title} eyebrow={eyebrow} errMsg={errMsg} onRetry={retry} />;
  }
  const stats = data;

  // UX-008 distinguishes a figure that is COMING from one that is NOT, and the difference is
  // the whole point of using it here: nothing in this module tracks either control, so
  // "not yet computed" would promise a number that never arrives (F2-457).
  const notTracked = isAr
    ? 'لا تُتابع هذه الوحدة هذا الضابط بعد، فلا يمكن قياسه'
    : 'This module does not track this control yet, so it cannot be measured';

  const kpis: DomainDashboardKpi[] = [
    { Icon: Fingerprint, label: isAr ? 'نشاطات معالجة' : 'Processing activities', value: stats.ropaCount ?? 0, tone: 'primary', to: '/privacy' },
    { Icon: ShieldAlert, label: isAr ? 'نشاطات عالية الخطورة' : 'High-risk activities', value: stats.highRiskRopa ?? 0,
      tone: (stats.highRiskRopa ?? 0) > 0 ? 'warning' : 'success' },
    { Icon: AlertOctagon, label: isAr ? 'PIA متأخر' : 'PIAs overdue', value: stats.piaOverdue ?? 0,
      tone: (stats.piaOverdue ?? 0) > 0 ? 'danger' : 'success' },
    // F2-458: the tone reads `dsrSlaBreaches`, which now counts open requests past their
    // recorded statutory deadline rather than the ones somebody happened to escalate. The
    // expression is unchanged and became correct when the figure did - which is why the
    // measurement mattered more than the predicate: with 12 of 12 open requests past due
    // and 2 escalated, this tile was `danger` for the wrong reason, and would have read a
    // neutral `info` for a tenant that never escalates anything.
    { Icon: Users, label: isAr ? 'طلبات أصحاب البيانات' : 'DSRs open', value: stats.dsrOpen ?? 0,
      tone: (stats.dsrSlaBreaches ?? 0) > 0 ? 'danger' : 'info' },
    { Icon: Send, label: isAr ? 'نقل عبر الحدود نشط' : 'Cross-border active', value: stats.crossBorderTransfers ?? 0,
      tone: 'info' },
    { Icon: FileWarning, label: isAr ? 'مُعالجون بدون اتفاقية' : 'Processors w/o DPA',
      value: null, emptyHint: notTracked },
  ];

  const widgets: DomainDashboardWidget[] = [
    {
      id: 'privacy-trend',
      title: isAr ? 'اتجاه الطلبات والخروقات — 12 أسبوع' : 'DSR + breach trend · 12 weeks',
      body: (
        <LineChart
          data={trend}
          xKey="week"
          height={200}
          series={[
            { key: 'dsr', name: isAr ? 'طلبات مفتوحة' : 'Open DSRs', color: 'var(--primary)' },
            { key: 'breaches', name: isAr ? 'خروقات مفتوحة' : 'Open breaches', color: 'var(--danger)' },
          ]}
          emptyState={isAr ? 'لا توجد بيانات اتجاه' : 'No trend data yet'} />
      ),
      href: '/privacy?tab=dsr',
    },
    {
      id: 'pia',
      title: isAr ? 'تقييمات أثر الخصوصية (PIA)' : 'Privacy Impact Assessments',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'مكتملة' : 'Completed'}: <strong>{stats.piaCompleted ?? 0}</strong></div>
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
      body: (() => {
        const open = stats.dsrOpen ?? 0;
        // F2-458: this was `Math.min(stats.dsrSlaBreaches ?? 0, open)`. The clamp was a
        // defence against two figures describing different populations - and it was the
        // wrong defence, because it hid the divergence instead of surfacing it. The server's
        // count now ANDs the open predicate, so the breached set is a subset of `open` by
        // construction and there is nothing left to clamp. F2-457's rule: a cap on a ratio's
        // numerator is a bug report about the numerator.
        const breaches = stats.dsrSlaBreaches ?? 0;
        const slaHealth = open > 0 ? Math.round(((open - breaches) / open) * 100) : 100;
        return (
          <div style={{ display: 'flex', gap: 14, alignItems: 'center', flexWrap: 'wrap' }}>
            <RadialGauge value={slaHealth} max={100} {...GAUGE_MD} unit="%" rtl={isAr}
              caption={isAr ? 'ضمن SLA' : 'within SLA'}
              aria-label={`${isAr ? 'صحة SLA لطلبات أصحاب البيانات' : 'DSR SLA health'} ${slaHealth}%`}
              bands={ragBands()} />
            <div className="text-xs" style={{ lineHeight: 1.8, minWidth: 130 }}>
              <div>{isAr ? 'طلبات مفتوحة' : 'Open requests'}: <strong>{open}</strong></div>
              <div>{isAr ? 'متجاوزة المهلة' : 'Past deadline'}: <Badge tone="danger">{breaches}</Badge></div>
              <div className="text-muted" style={{ marginTop: 4 }}>
                {isAr ? 'SLA 30 يوم (PDPL مادة 18) — قابل للتمديد 60 يوماً بمبرر.'
                      : '30-day SLA (PDPL Art. 18) — extendable by 60 days with justification.'}
              </div>
            </div>
          </div>
        );
      })(),
      href: '/privacy?tab=dsr',
    },
    {
      id: 'breach',
      title: isAr ? 'إشعارات الخروقات' : 'Breach notifications',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'حوادث خصوصية (12 شهر)' : 'Privacy incidents (12m)'}: <strong>{stats.privacyIncidents12m ?? 0}</strong></div>
          {/* F2-458: this reads the SDAIA submission column, not the breach lifecycle status.
              The label is the one an operator judges a statutory obligation by, so it may not
              be answered from a state an operator sets on the record itself. */}
          <div>{isAr ? 'أُبلغت سدايا' : 'Reported to SDAIA'}: <Badge tone={(stats.breachNotifications ?? 0) > 0 ? 'info' : 'warning'}>{stats.breachNotifications ?? 0}</Badge></div>
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
          {/* F2-458: was a `> 0 ? danger : success` Badge over a server-side literal 0L, so
              a control this module does not track rendered as a green all-clear. */}
          <div title={notTracked} aria-label={notTracked}>
            {isAr ? 'انتهاكات الاحتفاظ' : 'Retention violations'}: <strong>&mdash;</strong>
          </div>
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

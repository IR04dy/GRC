import { useEffect, useState } from 'react';
import { Shield, AlertTriangle, Bug, Activity, ServerCog, FileWarning, RefreshCw } from 'lucide-react';
import { DomainDashboardShell, type DomainDashboardKpi, type DomainDashboardWidget } from '../../components/dashboard/DomainDashboardShell';
import { Badge } from '../../components/ui/Badge';
import { Skeleton } from '../../components/ui/Skeleton';
import { LineChart } from '../../components/charts/LineChart';
import { RadialGauge } from '../../components/viz/RadialGauge';
import { useI18n } from '../../lib/i18n';
import { cyberApi, dashboardApi, type CyberDashboardDto } from '../../lib/api';

/**
 * NCA Cybersecurity Dashboard — task #36 (prompt §30.5).
 *
 * Headline widgets: per-framework scores (ECC / CSCC / CCC / OTCC),
 * open cyber gaps, critical non-compliances, overdue remediation,
 * critical vulnerabilities, evidence expiry.
 *
 * <p>Wired to {@code GET /api/v1/cyber/dashboard} which returns a
 * {@link CyberDashboardDto} computed server-side. Loading state shows
 * a skeleton row; error state surfaces a retry button so the
 * dashboard never silently stays blank.
 */
type LoadState = 'idle' | 'loading' | 'ready' | 'error';

export function CyberDashboardPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [stats, setStats] = useState<CyberDashboardDto | null>(null);
  const [trend, setTrend] = useState<Array<{ week: string; incidents: number }>>([]);
  const [state, setState] = useState<LoadState>('idle');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading');
    setErrMsg(null);
    try {
      const data = await cyberApi.dashboard();
      setStats(data);
      dashboardApi.kpiTrend('incidents', 12)
        .then(pts => setTrend(pts.map(p => ({ week: p.label, incidents: p.value }))))
        .catch(() => setTrend([]));
      setState('ready');
    } catch (e) {
      setErrMsg((e as Error)?.message ?? String(e));
      setState('error');
    }
  }

  useEffect(() => { void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, []);

  if (state === 'loading' || state === 'idle') {
    return (
      <DomainDashboardShell
        title={isAr ? 'لوحة الأمن السيبراني' : 'Cybersecurity dashboard'}
        sub={isAr ? 'جارٍ التحميل…' : 'Loading…'}
        eyebrow={isAr ? 'الأمن السيبراني' : 'Cybersecurity'}
        kpis={[]}
        widgets={[{
          id: 'loading',
          title: '',
          body: (
            <div className="cyber-dashboard__skeleton">
              <Skeleton height={20} />
              <Skeleton height={20} />
              <Skeleton height={20} />
              <Skeleton height={20} />
            </div>
          ),
        }]}
      />
    );
  }

  if (state === 'error' || !stats) {
    return (
      <DomainDashboardShell
        title={isAr ? 'لوحة الأمن السيبراني' : 'Cybersecurity dashboard'}
        sub={isAr ? 'تعذّر تحميل البيانات' : 'Could not load dashboard'}
        eyebrow={isAr ? 'الأمن السيبراني' : 'Cybersecurity'}
        kpis={[]}
        widgets={[{
          id: 'error',
          title: isAr ? 'خطأ' : 'Error',
          body: (
            <div className="cyber-dashboard__error">
              <div className="text-muted text-xs">
                {errMsg ?? (isAr ? 'تحقق من اتصال الخادم' : 'Check backend connectivity')}
              </div>
              <button type="button" className="btn btn-sm btn-outline mt-2" onClick={() => void load()}>
                <RefreshCw size={14} /> {isAr ? 'إعادة المحاولة' : 'Retry'}
              </button>
            </div>
          ),
        }]}
      />
    );
  }

  // Open gaps = non-compliant + partial (anything that isn't fully compliant).
  // The backend doesn't ship a single "gaps" count so we derive it from the
  // status breakdown.
  const openGaps = (stats.nonCompliantControls ?? 0) + (stats.partialControls ?? 0);

  const kpis: DomainDashboardKpi[] = [
    { Icon: Shield, label: isAr ? 'الالتزام الإجمالي NCA' : 'Overall NCA score', value: `${stats.overallScore ?? 0}%`,
      tone: tonePct(stats.overallScore), to: '/cyber' },
    { Icon: AlertTriangle, label: isAr ? 'فجوات مفتوحة' : 'Open gaps', value: openGaps,
      tone: openGaps > 0 ? 'warning' : 'success' },
    { Icon: FileWarning, label: isAr ? 'عدم امتثال' : 'Non-compliances', value: stats.nonCompliantControls ?? 0,
      tone: (stats.nonCompliantControls ?? 0) > 0 ? 'danger' : 'success' },
    { Icon: Bug, label: isAr ? 'ثغرات حرجة' : 'Critical vulns', value: stats.criticalVulnerabilities ?? 0,
      tone: (stats.criticalVulnerabilities ?? 0) > 0 ? 'danger' : 'success' },
    { Icon: Activity, label: isAr ? 'حوادث مفتوحة' : 'Open incidents', value: stats.openIncidents ?? 0,
      tone: (stats.openIncidents ?? 0) > 0 ? 'warning' : 'success' },
    { Icon: ServerCog, label: isAr ? 'أدلة على وشك الانتهاء' : 'Expiring evidence (60d)', value: stats.expiringEvidence60d ?? 0,
      tone: (stats.expiringEvidence60d ?? 0) > 0 ? 'warning' : 'info' },
  ];

  const widgets: DomainDashboardWidget[] = [
    {
      id: 'incident-trend',
      title: isAr ? 'اتجاه الحوادث — 12 أسبوع' : 'Incident trend · 12 weeks',
      body: (
        <LineChart
          data={trend}
          xKey="week"
          height={200}
          series={[{ key: 'incidents', name: isAr ? 'حوادث مفتوحة' : 'Open incidents', color: 'var(--danger)' }]}
          emptyState={isAr ? 'لا توجد بيانات اتجاه' : 'No trend data yet'} />
      ),
      href: '/cyber',
    },
    {
      id: 'frameworks',
      title: isAr ? 'النتائج حسب الإطار' : 'Score by framework',
      body: (
        <div className="cyber-dashboard__frameworks">
          <div style={{ display: 'flex', justifyContent: 'space-around', flexWrap: 'wrap', gap: 8, paddingTop: 4 }}>
            {[
              { code: 'ECC v2', pct: stats.eccScore ?? 0 },
              { code: 'CSCC', pct: stats.csccScore ?? 0 },
              { code: 'CCC', pct: stats.cccScore ?? 0 },
              { code: 'OTCC', pct: stats.otccScore ?? 0 },
            ].map(f => (
              <RadialGauge key={f.code} value={f.pct} max={100} size={96} thickness={9}
                caption={f.code} unit="%" rtl={isAr}
                aria-label={`${f.code} ${isAr ? 'الالتزام' : 'compliance'} ${f.pct}%`}
                bands={[
                  { from: 0, to: 60, color: 'var(--danger)' },
                  { from: 60, to: 85, color: 'var(--warning)' },
                  { from: 85, to: 101, color: 'var(--success)' },
                ]} />
            ))}
          </div>
          <div className="cyber-dashboard__hint text-muted">
            {isAr
              ? 'الـ0٪ يعني أن دورة التقييم لم تُفتح بعد لذلك الإطار.'
              : 'A 0% ring means the assessment cycle hasn’t opened yet for that framework.'}
          </div>
        </div>
      ),
      href: '/cyber',
    },
    {
      id: 'gaps',
      title: isAr ? 'الفجوات والمعالجة' : 'Gaps + remediation',
      badge: String(openGaps + (stats.overdueActionPlans ?? 0)),
      badgeTone: 'warning',
      body: (
        <div className="cyber-dashboard__lines text-xs">
          <div>{isAr ? 'فجوات مفتوحة' : 'Open gaps'}: <strong>{openGaps}</strong></div>
          <div>{isAr ? 'خطط معالجة متأخرة' : 'Overdue action plans'}: <Badge tone="danger">{stats.overdueActionPlans ?? 0}</Badge></div>
          <div>{isAr ? 'ثغرات متأخرة' : 'Overdue vulns'}: <Badge tone="danger">{stats.overdueVulnerabilities ?? 0}</Badge></div>
        </div>
      ),
      href: '/cyber?tab=gaps',
    },
    {
      id: 'vulns',
      title: isAr ? 'الثغرات' : 'Vulnerabilities',
      body: (
        <div className="cyber-dashboard__lines text-xs">
          <div>{isAr ? 'ثغرات حرجة مفتوحة' : 'Open critical vulns'}: <Badge tone="danger">{stats.criticalVulnerabilities ?? 0}</Badge></div>
          <div>{isAr ? 'إجمالي مفتوحة' : 'Total open'}: <strong>{stats.openVulnerabilities ?? 0}</strong></div>
          <div className="cyber-dashboard__hint text-muted">
            {isAr
              ? 'تنبيه AR-13 يصعّد لمدير الأمن السيبراني عند خرق SLA المعالجة.'
              : 'AR-13 escalates to the cybersecurity manager when a remediation SLA is breached.'}
          </div>
        </div>
      ),
      href: '/cyber/vulns/v2',
    },
    {
      id: 'evidence',
      title: isAr ? 'الأدلة' : 'Evidence',
      body: (
        <div className="cyber-dashboard__lines text-xs">
          <div>{isAr ? 'تنتهي خلال 60 يوماً' : 'Expiring in 60 days'}: <Badge tone="warning">{stats.expiringEvidence60d ?? 0}</Badge></div>
          <div>{isAr ? 'منتهية الصلاحية' : 'Already expired'}: <Badge tone="danger">{stats.expiredEvidence ?? 0}</Badge></div>
          <div className="cyber-dashboard__hint text-muted">
            {isAr ? 'قاعدة AR-09 تُشعر مالك الدليل ومالك الضابط آلياً.'
                  : 'AR-09 fires a notification to the evidence + control owner.'}
          </div>
        </div>
      ),
      href: '/evidence',
    },
  ];

  return (
    <DomainDashboardShell
      title={isAr ? 'لوحة الأمن السيبراني' : 'Cybersecurity dashboard'}
      sub={isAr ? 'الالتزام بمتطلبات NCA والثغرات والحوادث' : 'NCA compliance, vulnerabilities, and incidents'}
      eyebrow={isAr ? 'الأمن السيبراني' : 'Cybersecurity'}
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

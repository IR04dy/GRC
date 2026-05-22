import { useEffect, useState } from 'react';
import { Siren, AlertTriangle, Activity, Landmark, RefreshCw, Clock } from 'lucide-react';
import { DomainDashboardShell, type DomainDashboardKpi, type DomainDashboardWidget }
  from '../../components/dashboard/DomainDashboardShell';
import { Badge } from '../../components/ui/Badge';
import { Skeleton } from '../../components/ui/Skeleton';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type SocOpsDashboard } from '../../lib/socops';

/**
 * SOC Operations Dashboard — the leadership-ops view a Saudi CISO opens
 * first. Built on top of the V150 SOC operations backbone (PR #97) and
 * the V151 automation rules (this PR).
 *
 * <p>Answers the four questions a CISO is asked at every executive
 * meeting:
 * <ol>
 *   <li>How fast are we detecting? — MTTD</li>
 *   <li>How fast are we responding? — MTTR</li>
 *   <li>What's open right now, and at what severity?</li>
 *   <li>What deadlines (SLA, regulator response, cert expiry) are coming?</li>
 * </ol>
 *
 * <p>Wires to {@code GET /api/v1/socops/dashboard} which aggregates 12
 * repositories server-side.
 */
type LoadState = 'idle' | 'loading' | 'ready' | 'error';

export function SocOpsDashboardPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [stats, setStats] = useState<SocOpsDashboard | null>(null);
  const [state, setState] = useState<LoadState>('idle');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading');
    setErrMsg(null);
    try {
      const data = await socopsApi.dashboard();
      setStats(data);
      setState('ready');
    } catch (e) {
      setErrMsg((e as Error)?.message ?? String(e));
      setState('error');
    }
  }

  useEffect(() => { void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, []);

  // ── Loading ──
  if (state === 'loading' || state === 'idle') {
    return (
      <DomainDashboardShell
        title={isAr ? 'لوحة عمليات الأمن السيبراني' : 'SOC Operations Dashboard'}
        sub={isAr ? 'جارٍ التحميل…' : 'Loading…'}
        eyebrow={isAr ? 'عمليات الأمن' : 'SOC Operations'}
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

  // ── Error ──
  if (state === 'error' || !stats) {
    return (
      <DomainDashboardShell
        title={isAr ? 'لوحة عمليات الأمن السيبراني' : 'SOC Operations Dashboard'}
        sub={isAr ? 'تعذّر تحميل البيانات' : 'Could not load dashboard'}
        eyebrow={isAr ? 'عمليات الأمن' : 'SOC Operations'}
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

  // ── Headline KPIs ──
  const kpis: DomainDashboardKpi[] = [
    { Icon: Siren, label: isAr ? 'أحداث حرجة مفتوحة' : 'Open CRITICAL events',
      value: stats.openEventsCritical,
      tone: stats.openEventsCritical > 0 ? 'danger' : 'success',
      to: '/socops/events' },
    { Icon: AlertTriangle, label: isAr ? 'أحداث عالية مفتوحة' : 'Open HIGH events',
      value: stats.openEventsHigh,
      tone: stats.openEventsHigh > 0 ? 'warning' : 'success',
      to: '/socops/events' },
    { Icon: Clock, label: isAr ? 'اقتراب SLA' : 'SLA approaching',
      value: stats.eventsApproachingSlaBreach,
      tone: stats.eventsApproachingSlaBreach > 0 ? 'warning' : 'success' },
    { Icon: Activity, label: 'MTTD',
      value: stats.mttdMinutes == null ? '—' : `${Math.round(stats.mttdMinutes)}m`,
      tone: 'info' },
    { Icon: Activity, label: 'MTTR',
      value: stats.mttrMinutes == null ? '—' : `${Math.round(stats.mttrMinutes)}m`,
      tone: 'info' },
    { Icon: Landmark, label: isAr ? 'تفتيشات تنظيمية مفتوحة' : 'Open regulator exams',
      value: stats.regulatorExamsOpen,
      tone: stats.regulatorExamsOpen > 0 ? 'warning' : 'success',
      to: '/socops/regulator-exams' },
  ];

  // ── Widgets ──
  const widgets: DomainDashboardWidget[] = [
    {
      id: 'overlays',
      title: isAr ? 'النوافذ الموسمية النشطة' : 'Active seasonal overlays',
      badge: String(stats.activeOverlays?.length ?? 0),
      badgeTone: (stats.activeOverlays?.length ?? 0) > 0 ? 'warning' : 'info',
      body: (
        <div className="cyber-dashboard__lines text-xs">
          {(stats.activeOverlays?.length ?? 0) === 0 ? (
            <div className="text-muted">
              {isAr ? 'لا توجد نوافذ نشطة حالياً (حج / عمرة / رمضان / يوم وطني)'
                    : 'No active windows now (Hajj / Umrah / Ramadan / National Day)'}
            </div>
          ) : (
            stats.activeOverlays.map(o => (
              <div key={o.overlayCode}>
                <strong>{isAr ? (o.titleAr ?? o.titleEn) : o.titleEn}</strong>
                {o.hijriLabel ? <span className="text-muted"> · {o.hijriLabel}</span> : null}
                <div className="mt-1">
                  {o.tighteningKris && <Badge tone="warning">KRI tightened</Badge>}
                  {o.tighteningSlas && <Badge tone="warning">SLA tightened</Badge>}
                </div>
              </div>
            ))
          )}
        </div>
      ),
      href: '/socops/seasonal-overlays',
    },
    {
      id: 'recent-events',
      title: isAr ? 'أحدث الأحداث الحرجة/العالية' : 'Recent CRITICAL/HIGH events',
      badge: String(stats.topRecentEvents?.length ?? 0),
      body: (
        <div className="cyber-dashboard__lines text-xs">
          {(stats.topRecentEvents?.length ?? 0) === 0 ? (
            <div className="text-muted">{isAr ? 'لا أحداث حديثة' : 'No recent events'}</div>
          ) : (
            stats.topRecentEvents.map(e => (
              <div key={e.id}>
                <Badge tone={e.severity === 'CRITICAL' ? 'danger' : 'warning'}>{e.severity}</Badge>
                {' '}{e.ruleName ?? '—'}
                <span className="text-muted"> · {e.sourceSystem}</span>
              </div>
            ))
          )}
        </div>
      ),
      href: '/socops/events',
    },
    {
      id: 'ir-playbooks',
      title: isAr ? 'أدلة الاستجابة' : 'IR playbooks',
      body: (
        <div className="cyber-dashboard__lines text-xs">
          <div>{isAr ? 'إجمالي الأدلة النشطة' : 'Active playbooks'}: <strong>{stats.playbookTotalCount}</strong></div>
          <div>{isAr ? 'إجمالي مرات التفعيل' : 'Total invocations'}: <strong>{stats.activePlaybookInvocations}</strong></div>
          <div className="text-muted mt-1">
            {isAr ? '٦ قوالب جاهزة: تصيد، فدية، احتيال بريد، DDoS، تهديد داخلي، تفعيل BCP'
                  : '6 templates ready: Phishing, Ransomware, BEC, DDoS, Insider, BCP-Activation'}
          </div>
        </div>
      ),
      href: '/socops/playbooks',
    },
    {
      id: 'ti',
      title: isAr ? 'الاستخبارات السيبرانية' : 'Threat intelligence',
      badge: String(stats.actionableTiUnactioned),
      badgeTone: stats.actionableTiUnactioned > 0 ? 'warning' : 'success',
      body: (
        <div className="cyber-dashboard__lines text-xs">
          <div>{isAr ? 'بانتظار الإجراء' : 'Unactioned'}: <Badge tone="warning">{stats.actionableTiUnactioned}</Badge></div>
          <div>{isAr ? 'الإجمالي' : 'Total'}: <strong>{stats.actionableTiTotal}</strong></div>
          <div className="text-muted mt-1">
            {isAr ? 'مصادر: Saudi-CERT · NCA · FS-ISAC · MISP'
                  : 'Sources: Saudi-CERT · NCA · FS-ISAC · MISP'}
          </div>
        </div>
      ),
      href: '/socops/threat-intel',
    },
    {
      id: 'scanners',
      title: isAr ? 'الماسحات' : 'Vulnerability scanners',
      body: (
        <div className="cyber-dashboard__lines text-xs">
          <div>{isAr ? 'تشغيلات آخر ٢٤س' : 'Runs in last 24h'}: <strong>{stats.scannerRunsLast24h}</strong></div>
          <div>{isAr ? 'ثغرات حرجة مفتوحة' : 'Critical findings open'}: <Badge tone="danger">{stats.scannerCriticalsOpen}</Badge></div>
        </div>
      ),
      href: '/socops/scanner-runs',
    },
    {
      id: 'pentests',
      title: isAr ? 'اختبارات الاختراق' : 'Penetration tests',
      body: (
        <div className="cyber-dashboard__lines text-xs">
          <div>{isAr ? 'في العمل الميداني' : 'In fieldwork'}: <strong>{stats.pentestsInFieldwork}</strong></div>
          <div>{isAr ? 'إعادة اختبار مستحقة' : 'Retest due'}: <Badge tone="warning">{stats.pentestsRetestDue}</Badge></div>
          <div className="text-muted mt-1">
            {isAr ? 'تاريخ كامل لمراجعات ساما'
                  : 'Full history queried by SAMA examiners'}
          </div>
        </div>
      ),
      href: '/socops/pentests',
    },
    {
      id: 'crypto',
      title: isAr ? 'الأصول التشفيرية' : 'Cryptographic assets',
      body: (
        <div className="cyber-dashboard__lines text-xs">
          <div>{isAr ? 'انتهاء خلال ٣٠ يوم' : 'Expiring in 30d'}: <Badge tone="danger">{stats.certsExpiring30d}</Badge></div>
          <div>{isAr ? 'انتهاء خلال ٩٠ يوم' : 'Expiring in 90d'}: <Badge tone="warning">{stats.certsExpiring90d}</Badge></div>
          <div>{isAr ? 'غير آمنة كموماً (PQC)' : 'Quantum-unsafe'}: <Badge tone="warning">{stats.certsQuantumUnsafe}</Badge></div>
        </div>
      ),
      href: '/socops/crypto-assets',
    },
    {
      id: 'sbom',
      title: isAr ? 'قائمة مكوّنات البرمجيات (SBOM)' : 'SBOM',
      body: (
        <div className="cyber-dashboard__lines text-xs">
          <div>{isAr ? 'إجمالي المكونات' : 'Total components'}: <strong>{stats.sbomComponentsTotal}</strong></div>
          <div>{isAr ? 'بها ثغرات معروفة' : 'With known CVEs'}: <Badge tone="danger">{stats.sbomComponentsWithCves}</Badge></div>
        </div>
      ),
      href: '/socops/sbom',
    },
    {
      id: 'awareness',
      title: isAr ? 'التوعية والتصيد المحاكي' : 'Awareness & phishing sims',
      body: (
        <div className="cyber-dashboard__lines text-xs">
          <div>{isAr ? 'حملات نشطة' : 'Running campaigns'}: <strong>{stats.awarenessCampaignsRunning}</strong></div>
          <div>{isAr ? 'آخر معدل نجاح' : 'Last pass rate'}: <Badge tone="info">{stats.awarenessLastPassRatePercent == null ? '—' : `${stats.awarenessLastPassRatePercent}%`}</Badge></div>
        </div>
      ),
      href: '/socops/awareness',
    },
    {
      id: 'regulator-exams',
      title: isAr ? 'تفتيشات الجهات التنظيمية' : 'Regulator examinations',
      badge: String(stats.regulatorExamsResponseDueSoon),
      badgeTone: stats.regulatorExamsResponseDueSoon > 0 ? 'warning' : 'success',
      body: (
        <div className="cyber-dashboard__lines text-xs">
          <div>{isAr ? 'مفتوحة' : 'Open'}: <strong>{stats.regulatorExamsOpen}</strong></div>
          <div>{isAr ? 'ردود مستحقة خلال ١٤ يوم' : 'Response due ≤14d'}: <Badge tone="warning">{stats.regulatorExamsResponseDueSoon}</Badge></div>
          <div className="text-muted mt-1">
            {Object.entries(stats.regulatorExamsByRegulator ?? {}).map(([reg, n]) => (
              <span key={reg} className="me-2">{reg}: <strong>{n}</strong></span>
            ))}
          </div>
        </div>
      ),
      href: '/socops/regulator-exams',
    },
    {
      id: 'upcoming-exams',
      title: isAr ? 'مواعيد ردود قادمة' : 'Upcoming exam response deadlines',
      body: (
        <div className="cyber-dashboard__lines text-xs">
          {(stats.upcomingExams?.length ?? 0) === 0 ? (
            <div className="text-muted">{isAr ? 'لا مواعيد قريبة' : 'No upcoming deadlines'}</div>
          ) : (
            stats.upcomingExams.map(e => (
              <div key={e.id}>
                <Badge tone="warning">{e.regulator}</Badge>{' '}
                {e.titleEn}
                <span className="text-muted"> · due {e.responseDueAt?.slice(0, 10)}</span>
              </div>
            ))
          )}
        </div>
      ),
      href: '/socops/regulator-exams',
    },
  ];

  return (
    <DomainDashboardShell
      title={isAr ? 'لوحة عمليات الأمن السيبراني' : 'SOC Operations Dashboard'}
      sub={isAr
        ? 'الأحداث، الاستجابة، الاستخبارات، التفتيش — نظرة قائد عمليات الأمن'
        : 'Events · Response · Intel · Examinations — the SOC leader’s view'}
      eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
      kpis={kpis}
      widgets={widgets}
    />
  );
}

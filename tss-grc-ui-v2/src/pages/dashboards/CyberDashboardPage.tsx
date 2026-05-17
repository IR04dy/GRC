import { useState } from 'react';
import { Shield, AlertTriangle, Bug, Activity, ServerCog, FileWarning } from 'lucide-react';
import { DomainDashboardShell, type DomainDashboardKpi, type DomainDashboardWidget } from '../../components/dashboard/DomainDashboardShell';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';

/**
 * NCA Cybersecurity Dashboard — task #36 (prompt §30.5).
 *
 * Headline widgets: per-framework scores (ECC / CSCC / CCC / OTCC),
 * open cyber gaps, critical non-compliances, overdue remediation,
 * critical vulnerabilities, evidence expiry.
 */
interface CyberStatsShape {
  overallScore?: number;
  eccScore?: number;
  csccScore?: number;
  cccScore?: number;
  otccScore?: number;
  openGaps?: number;
  criticalNonCompliances?: number;
  overdueRemediation?: number;
  criticalVulns?: number;
  openIncidents?: number;
  expiringEvidence60d?: number;
}

const FALLBACK: CyberStatsShape = {
  overallScore: 65, eccScore: 65, csccScore: 0, cccScore: 72, otccScore: 0,
  openGaps: 14, criticalNonCompliances: 3, overdueRemediation: 5,
  criticalVulns: 8, openIncidents: 2, expiringEvidence60d: 6,
};

export function CyberDashboardPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  // No dedicated /api/v1/cyber/stats endpoint yet — dashboard runs on
  // the seeded fallback until the backend ships a stats() aggregate.
  // Tracked as a follow-up in 11_Implementation_Backlog.md.
  const [stats] = useState<CyberStatsShape>(FALLBACK);

  const kpis: DomainDashboardKpi[] = [
    { Icon: Shield, label: isAr ? 'الالتزام الإجمالي NCA' : 'Overall NCA score', value: `${stats.overallScore ?? 0}%`,
      tone: tonePct(stats.overallScore), to: '/cyber' },
    { Icon: AlertTriangle, label: isAr ? 'فجوات مفتوحة' : 'Open gaps', value: stats.openGaps ?? 0,
      tone: (stats.openGaps ?? 0) > 0 ? 'warning' : 'success' },
    { Icon: FileWarning, label: isAr ? 'عدم امتثال حرج' : 'Critical NCs', value: stats.criticalNonCompliances ?? 0,
      tone: (stats.criticalNonCompliances ?? 0) > 0 ? 'danger' : 'success' },
    { Icon: Bug, label: isAr ? 'ثغرات حرجة' : 'Critical vulns', value: stats.criticalVulns ?? 0,
      tone: (stats.criticalVulns ?? 0) > 0 ? 'danger' : 'success' },
    { Icon: Activity, label: isAr ? 'حوادث مفتوحة' : 'Open incidents', value: stats.openIncidents ?? 0,
      tone: (stats.openIncidents ?? 0) > 0 ? 'warning' : 'success' },
    { Icon: ServerCog, label: isAr ? 'أدلة على وشك الانتهاء' : 'Expiring evidence (60d)', value: stats.expiringEvidence60d ?? 0,
      tone: (stats.expiringEvidence60d ?? 0) > 0 ? 'warning' : 'info' },
  ];

  const widgets: DomainDashboardWidget[] = [
    {
      id: 'frameworks',
      title: isAr ? 'النتائج حسب الإطار' : 'Score by framework',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.9 }}>
          <FrameworkRow code="ECC v2" pct={stats.eccScore} isAr={isAr} />
          <FrameworkRow code="CSCC" pct={stats.csccScore} isAr={isAr} />
          <FrameworkRow code="CCC" pct={stats.cccScore} isAr={isAr} />
          <FrameworkRow code="OTCC" pct={stats.otccScore} isAr={isAr} />
          <div className="text-muted" style={{ marginTop: 6 }}>
            {isAr
              ? 'الـ0٪ يعني أن دورة التقييم لم تُفتح بعد لذلك الإطار.'
              : 'A 0% bar means the assessment cycle hasn’t opened yet for that framework.'}
          </div>
        </div>
      ),
      href: '/cyber',
    },
    {
      id: 'gaps',
      title: isAr ? 'الفجوات والمعالجة' : 'Gaps + remediation',
      badge: String((stats.openGaps ?? 0) + (stats.overdueRemediation ?? 0)),
      badgeTone: 'warning',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'فجوات مفتوحة' : 'Open gaps'}: <strong>{stats.openGaps}</strong></div>
          <div>{isAr ? 'معالجة متأخرة' : 'Overdue remediation'}: <Badge tone="danger">{stats.overdueRemediation}</Badge></div>
        </div>
      ),
      href: '/cyber?tab=gaps',
    },
    {
      id: 'vulns',
      title: isAr ? 'الثغرات' : 'Vulnerabilities',
      body: (
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'ثغرات حرجة مفتوحة' : 'Open critical vulns'}: <Badge tone="danger">{stats.criticalVulns}</Badge></div>
          <div className="text-muted" style={{ marginTop: 4 }}>
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
        <div className="text-xs" style={{ lineHeight: 1.8 }}>
          <div>{isAr ? 'تنتهي خلال 60 يوماً' : 'Expiring in 60 days'}: <Badge tone="warning">{stats.expiringEvidence60d}</Badge></div>
          <div className="text-muted" style={{ marginTop: 4 }}>
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

function FrameworkRow({ code, pct, isAr }: { code: string; pct?: number; isAr: boolean }) {
  const value = pct ?? 0;
  const tone = tonePct(pct);
  void isAr;
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '70px 1fr 50px', gap: 6, alignItems: 'center' }}>
      <span className="mono">{code}</span>
      <div style={{ height: 8, borderRadius: 4, background: 'var(--surface-2)', overflow: 'hidden' }}>
        <div style={{
          width: `${value}%`, height: '100%',
          background: tone === 'success' ? 'var(--success, #2e9c5e)'
                    : tone === 'warning' ? 'var(--warning, #c47e00)'
                    : tone === 'danger'  ? 'var(--danger, #c8362c)'
                                         : 'var(--primary)',
        }} />
      </div>
      <span className="mono" style={{ textAlign: 'end' }}>{value}%</span>
    </div>
  );
}

function tonePct(v?: number): 'success' | 'warning' | 'danger' | 'info' {
  const n = v ?? 0;
  if (n >= 85) return 'success';
  if (n >= 60) return 'info';
  if (n >= 30) return 'warning';
  return 'danger';
}

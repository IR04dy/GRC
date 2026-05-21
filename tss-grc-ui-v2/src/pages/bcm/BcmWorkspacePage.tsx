import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  LayoutDashboard, Activity, ClipboardCheck, Server, Siren,
  AlertOctagon, Stamp, BarChart3, Settings, RefreshCw,
  Network, Megaphone, ArrowRight,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import { WorkflowQuickLaunch } from '../../components/workflows/WorkflowQuickLaunch';
import { ModuleSubmissionsPanel } from '../../components/workflows/ModuleSubmissionsPanel';
import { crisisEventStats } from '../../lib/crisisEvents';
import { biaStats } from '../../lib/biaAssessments';
import { remediationStats } from '../../lib/bcmCycle';
import { overallStats as complianceStats } from '../../lib/bcmCompliance';
import { tenantReadiness, scoreTone, type TenantScore } from '../../lib/bcmReadinessScore';
import type { LucideIcon } from 'lucide-react';

/**
 * BCM Workspace landing page — audit T1.1 consolidation hub.
 *
 * <p>Replaces the legacy tab-heavy {@code BcmPage} at {@code /bcm} with a
 * clean overview that:
 * <ul>
 *   <li>Shows a four-KPI strip pulling from the four new BCM-cycle services
 *       (BIAs / open gaps / active crises / non-compliant clauses).</li>
 *   <li>Surfaces all 12 BCM features as a card grid grouped by operational
 *       phase (Plan · Assess · Recover · Improve · Govern). Each card is a
 *       direct link to its dedicated page.</li>
 *   <li>Calls out the three legacy pages (BcmPage tabs, BcmLifecyclePage,
 *       BcmDependencyPage, CrisisManagementPage) as "(legacy)" with explicit
 *       pointers to their canonical replacements.</li>
 * </ul>
 *
 * <p>The legacy {@code BcmPage} is moved to {@code /bcm/legacy} so any deep
 * link still resolves — but it's no longer the default landing for the BCM
 * module, removing the audit's #1 complaint about navigation fragmentation.
 */
export function BcmWorkspacePage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [stats, setStats] = useState<{
    bias?: Record<string, number>;
    crises?: Record<string, number>;
    remediation?: { openGaps: number; criticalGaps: number; openCapas: number; overdueCapas: number; closedCapas: number };
    compliance?: Record<string, number>;
  }>({});
  const [readiness, setReadiness] = useState<TenantScore | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const [bias, crises, remediation, compliance, score] = await Promise.all([
          biaStats().catch(() => ({})),
          crisisEventStats().catch(() => ({})),
          remediationStats().catch(() => undefined),
          complianceStats().catch(() => ({})),
          tenantReadiness().catch(() => null),
        ]);
        setStats({ bias, crises, remediation, compliance });
        setReadiness(score);
      } catch (e) {
        toast.danger(e instanceof ApiError ? e.message
          : (isAr ? 'تعذّر تحميل البيانات' : 'Failed to load workspace stats'));
      }
    })();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'إدارة استمرارية الأعمال' : 'Business Continuity Management'}
        sub={isAr
          ? 'إطار ISO 22301 / SAMA OPRES / NCEMA — جميع الأدوات في مساحة عمل واحدة.'
          : 'ISO 22301 / SAMA OPRES / NCEMA framework — all the tools in one workspace.'}
        eyebrow={isAr ? 'استمرارية الأعمال' : 'BCM workspace'}
      />
      <WorkflowQuickLaunch modules="BCM" />
      <ModuleSubmissionsPanel module="BCM" />

      {/* KPI strip — pulls live data from the four new BCM-cycle services. */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(170px,1fr))', gap: 12, marginBottom: 16 }}>
        <Kpi Icon={ClipboardCheck} label={isAr ? 'تحليلات BIA معتمدة' : 'BIAs approved'}
             value={stats.bias?.status_approved ?? 0} tone="success" to="/bcm/bia" />
        <Kpi Icon={AlertOctagon}   label={isAr ? 'فجوات مفتوحة' : 'Open gaps'}
             value={stats.remediation?.openGaps ?? 0} tone="warning" to="/bcm/remediation" />
        <Kpi Icon={Siren}          label={isAr ? 'أزمات نشطة' : 'Active crises'}
             value={stats.crises?.active ?? 0} tone="danger" to="/bcm/crisis-room" />
        <Kpi Icon={Stamp}          label={isAr ? 'بنود غير ممتثلة' : 'Non-compliant clauses'}
             value={stats.compliance?.non_compliant ?? 0} tone="danger" to="/bcm/compliance" />
      </div>

      {readiness && readiness.biaCount > 0 && (
        <ReadinessTile readiness={readiness} isAr={isAr} />
      )}

      {/* Feature grid grouped by ISO 22301 operational phase. */}
      <PhaseGroup
        title={isAr ? 'القياس والتقييم' : 'Assess'}
        sub={isAr ? 'حدّد ما يجب حمايته وكيف' : 'Identify what to protect and how'}
        cards={[
          { Icon: ClipboardCheck, to: '/bcm/bia',           en: 'BIA wizard',          ar: 'معالج تحليل الأثر',
            desc: 'time-band × dimension matrix · MBCO · resources · workaround',
            descAr: 'مصفوفة فترة × بُعد · MBCO · موارد · بدائل يدوية' },
          { Icon: Network,        to: '/bcm-dependency',    en: 'Dependency map',       ar: 'خريطة التبعيات',
            desc: 'process → service → app → supplier · soft FK overlay',
            descAr: 'عملية → خدمة → تطبيق → مورّد · تراكب FK',
            tag: 'legacy' },
        ]} isAr={isAr} />

      <PhaseGroup
        title={isAr ? 'التخطيط والاستراتيجية' : 'Plan'}
        sub={isAr ? 'حدد استراتيجيات الاستمرارية والخطط' : 'Set continuity strategies and plans'}
        cards={[
          { Icon: Activity,       to: '/bcm/readiness',     en: 'Readiness cycles',     ar: 'دورات الجاهزية',
            desc: 'cycles · governance · risks · strategies · call tree',
            descAr: 'الدورات · الحوكمة · المخاطر · الاستراتيجيات · شجرة الاتصال' },
          { Icon: Server,         to: '/dr-plans',          en: 'DR plans',             ar: 'خطط التعافي',
            desc: 'failover · failback · test outcomes · auto-CAPA on fail',
            descAr: 'التحويل · العودة · نتائج الاختبار · CAPA تلقائي عند الفشل' },
          { Icon: RefreshCw,      to: '/bcm/legacy',        en: 'BCP register',         ar: 'سجل BCP',
            desc: 'legacy plan register · use Readiness cycles for new work',
            descAr: 'سجل الخطط القديم · استخدم دورات الجاهزية للعمل الجديد',
            tag: 'legacy' },
        ]} isAr={isAr} />

      <PhaseGroup
        title={isAr ? 'الاستجابة والتعافي' : 'Respond + Recover'}
        sub={isAr ? 'فعّل الخطط عند الأزمة' : 'Activate plans when a crisis hits'}
        cards={[
          { Icon: Siren,          to: '/bcm/crisis-room',   en: 'Crisis Room',          ar: 'غرفة الأزمات',
            desc: 'severity 1-5 · SitReps · decisions · regulator notifications',
            descAr: 'الشدة 1-5 · تقارير الموقف · القرارات · إخطارات الجهات' },
          { Icon: Megaphone,      to: '/bcm-lifecycle',     en: 'Invocation lifecycle', ar: 'دورة التفعيل',
            desc: 'SAMA OPRES STANDBY → ELEVATED → ACTIVATED → STAND_DOWN',
            descAr: 'STANDBY → ELEVATED → ACTIVATED → STAND_DOWN (SAMA OPRES)',
            tag: 'legacy' },
        ]} isAr={isAr} />

      <PhaseGroup
        title={isAr ? 'التحسين والامتثال' : 'Improve + Comply'}
        sub={isAr ? 'أغلق الحلقة بعد كل حدث' : 'Close the loop after every event'}
        cards={[
          { Icon: AlertOctagon,   to: '/bcm/remediation',   en: 'Remediation',          ar: 'فجوات وإجراءات',
            desc: 'lessons · gaps · CAPAs · overdue scheduler',
            descAr: 'دروس · فجوات · إجراءات · جدولة التأخر' },
          { Icon: Stamp,          to: '/bcm/compliance',    en: 'Compliance mapping',   ar: 'مطابقة الامتثال',
            desc: 'ISO 22301 / SAMA OPRES / DGA / NCEMA × evidence',
            descAr: 'ISO 22301 / SAMA / DGA / NCEMA × الأدلة' },
        ]} isAr={isAr} />

      <PhaseGroup
        title={isAr ? 'الحوكمة والتقارير' : 'Govern + Report'}
        sub={isAr ? 'لوحات تنفيذية وتقارير منظمة' : 'Executive dashboards + regulator-ready reports'}
        cards={[
          { Icon: LayoutDashboard, to: '/bcm/dashboard',    en: 'BCM dashboard',        ar: 'لوحة المعلومات',
            desc: 'critical services · BIA / BCP / DRP completion · RTO compliance',
            descAr: 'الخدمات الحرجة · إنجاز BIA / BCP / DRP · امتثال RTO' },
          { Icon: BarChart3,      to: '/bcm/reports',       en: 'BCM reports',          ar: 'التقارير',
            desc: '15 named reports · executive · audit-pack · readiness',
            descAr: '15 تقريراً · تنفيذي · حزمة تدقيق · جاهزية' },
          { Icon: Settings,       to: '/bcm/settings',      en: 'BCM settings',         ar: 'الإعدادات',
            desc: 'thresholds · approval matrix · framework versions',
            descAr: 'العتبات · مصفوفة الموافقات · إصدارات الأطر' },
        ]} isAr={isAr} />
    </Shell>
  );
}

// ─── Phase group + card helpers ──────────────────────────────────────

interface CardDef {
  Icon: LucideIcon;
  to: string;
  en: string;
  ar: string;
  desc: string;
  descAr: string;
  tag?: 'legacy' | 'new';
}

/**
 * Headline metric card: tenant readiness score (0..100) + per-criticality
 * histogram. Sits between the KPI strip and the feature grid because every
 * exec on this page wants to see the number first.
 */
function ReadinessTile({ readiness, isAr }: { readiness: TenantScore; isAr: boolean }) {
  const tone = scoreTone(readiness.averageScore);
  const colour = tone === 'success' ? 'var(--success, #2e9c5e)'
              : tone === 'info'    ? 'var(--info, #3a80c8)'
              : tone === 'warning' ? 'var(--warning, #c47e00)'
                                   : 'var(--danger, #c8362c)';
  return (
    <Card title={isAr ? 'مؤشر جاهزية الاستمرارية' : 'Continuity readiness score'}>
      <div style={{ display: 'flex', gap: 24, alignItems: 'center', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <div style={{ fontSize: 48, fontWeight: 700, color: colour, lineHeight: 1 }}>
            {readiness.averageScore.toFixed(1)}
            <span style={{ fontSize: 18, color: 'var(--text-muted)', fontWeight: 400 }}> / 100</span>
          </div>
          <div className="text-muted" style={{ fontSize: 11 }}>
            {isAr
              ? `استناداً إلى ${readiness.biaCount} تحليل أثر معتمد`
              : `Across ${readiness.biaCount} approved BIA${readiness.biaCount === 1 ? '' : 's'}`}
          </div>
        </div>
        <div style={{ height: 12, flex: 1, minWidth: 240, background: 'var(--surface-2)', borderRadius: 6, overflow: 'hidden' }}>
          <div style={{ width: `${readiness.averageScore}%`, height: '100%', background: colour }} />
        </div>
        <Link to="/bcm/readiness" style={{ textDecoration: 'none' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--primary)' }}>
            {isAr ? 'فتح لوحة الجاهزية' : 'Open readiness workspace'} <ArrowRight size={12} />
          </div>
        </Link>
      </div>
      {Object.keys(readiness.biasByCriticality).length > 0 && (
        <div style={{ marginTop: 12, display: 'flex', gap: 6, flexWrap: 'wrap', fontSize: 11 }}>
          <span className="text-muted">
            {isAr ? 'التوزيع حسب الأهمية:' : 'By criticality:'}
          </span>
          {Object.entries(readiness.biasByCriticality).map(([crit, count]) => (
            <span key={crit} style={{
              padding: '2px 8px', borderRadius: 4,
              border: '1px solid var(--border)', background: 'var(--surface-2)',
            }}>
              <span className="mono">{crit}</span>: {count}
            </span>
          ))}
        </div>
      )}
      <div className="text-muted" style={{ fontSize: 11, marginTop: 10 }}>
        {isAr
          ? 'النتيجة 0–100 لكل BIA معتمد، خمسة أبعاد ثنائية (BIA · BCP · DRP · اختبار التعافي · بديل يدوي).'
          : '0–100 per approved BIA across five binary dimensions (BIA · BCP · DRP · DR test · manual workaround).'}
      </div>
    </Card>
  );
}

function PhaseGroup({ title, sub, cards, isAr }: { title: string; sub: string; cards: CardDef[]; isAr: boolean }) {
  return (
    <Card title={title}>
      <div className="text-muted" style={{ fontSize: 11, marginBottom: 8 }}>{sub}</div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(260px,1fr))', gap: 10 }}>
        {cards.map(c => (
          <Link key={c.to} to={c.to} style={{
            border: '1px solid var(--border)', borderRadius: 6, padding: 12,
            textDecoration: 'none', color: 'inherit',
            display: 'grid', gap: 6, background: 'var(--surface)',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <c.Icon size={16} color="var(--primary)" />
              <strong style={{ fontSize: 13 }}>{isAr ? c.ar : c.en}</strong>
              {c.tag === 'legacy' && <Badge tone="neutral">{isAr ? 'قديم' : 'legacy'}</Badge>}
              {c.tag === 'new'    && <Badge tone="info">{isAr ? 'جديد' : 'new'}</Badge>}
              <ArrowRight size={12} style={{ marginInlineStart: 'auto' }} />
            </div>
            <div className="text-muted" style={{ fontSize: 11, lineHeight: 1.5 }}>
              {isAr ? c.descAr : c.desc}
            </div>
          </Link>
        ))}
      </div>
    </Card>
  );
}

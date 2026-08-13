import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity, ShieldCheck, Stamp, Fingerprint, AlertTriangle,
  TrendingUp, AlertOctagon, Clock, Wrench, ArrowRight,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { LifecyclePipeline, type PipelineStage, type PipelineCard, dueLabel } from '../../components/lifecycle/LifecyclePipeline';
import { loadTreatmentPlans, subscribeRiskLifecycle, aggregateRiskLifecycle } from '../../lib/riskLifecycle';
import { loadFindings, subscribeAuditLifecycle, aggregateAuditLifecycle } from '../../lib/auditLifecycle';
import { subscribePrivacy } from '../../lib/privacyLifecycle';
// F2-202 — the DSR lane is the one lane on this page fed by a real server register.
import { dsrApi, aggregateDsr, isPastDeadline, isOpen as isOpenDsr, type DsrCase } from '../../lib/privacyLcApi';
import { loadInvocations, subscribeBcm, aggregateBcm, pickPlanById } from '../../lib/bcmLifecycle';
import { loadVulns, loadAssets, subscribeCyber, aggregateCyber } from '../../lib/cyberLifecycle';

/**
 * Executive Lifecycle Overview — Phase 4 of the gap fill.
 *
 * Single screen showing ALL FIVE lifecycle pipelines stacked. Lets a CISO /
 * CRO see "everything in flight" without 5 page loads. Each pipeline is the
 * same kanban component as the per-workspace view, just compressed and
 * read-only at this level (with click-through to the workspace).
 */
export function LifecycleOverviewPage() {
  const { t } = useI18n();

  // Aggregate everything reactively.
  const [_t, setTick] = useState(0);
  useEffect(() => {
    const off1 = subscribeRiskLifecycle(() => setTick(x => x + 1));
    const off2 = subscribeAuditLifecycle(() => setTick(x => x + 1));
    const off3 = subscribePrivacy(() => setTick(x => x + 1));
    const off4 = subscribeBcm(() => setTick(x => x + 1));
    const off5 = subscribeCyber(() => setTick(x => x + 1));
    return () => { off1(); off2(); off3(); off4(); off5(); };
  }, []);

  const riskStats = useMemo(() => aggregateRiskLifecycle(), [_t]);
  const auditStats = useMemo(() => aggregateAuditLifecycle(), [_t]);
  const bcmStats = useMemo(() => aggregateBcm(), [_t]);
  const cyberStats = useMemo(() => aggregateCyber(), [_t]);

  // F2-202 — DSR is the one lane here whose rows come from a server. It loads asynchronously,
  // so it is `null` until the response lands and stays `null` if the request fails.
  const [dsrs, setDsrs] = useState<DsrCase[] | null>(null);
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    dsrApi.list().then(setDsrs).catch(() => setDsrs(null));
    const id = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(id);
  }, []);
  const dsrStats = useMemo(() => dsrs && aggregateDsr(dsrs, now), [dsrs, now]);

  /**
   * ⚠ `null` until the DSR register has been read, because this is a SUM across lanes and one
   * of its terms is now server-derived. Dropping the DSR term while keeping the other five
   * would render a smaller number in `success` green — a composite that reads as measured and
   * is quietly missing a statutory lane. F2-197's rule applies to totals, not just to tiles:
   * an incomplete sum is not a smaller risk.
   */
  const totalAtRisk = dsrStats === null ? null :
    riskStats.risksWithOpenBreach +
    auditStats.findingsCritical +
    dsrStats.pastDeadline +
    bcmStats.tier1Count +
    cyberStats.vulnsOverdue +
    cyberStats.socOpenP1;

  return (
    <Shell title={t('loTitle')} sub={t('loSub')}>
      <PageHeader
        eyebrow={t('loEyebrow')}
        title={t('loTitle')}
        sub={t('loPageSub')}
      />

      {/* Top-level KPIs */}
      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={AlertOctagon}     label={t('loKpiAtRisk')}
          value={totalAtRisk === null ? null : String(totalAtRisk)}
          emptyHint={t('loKpiAtRiskUnread')}
          tone={totalAtRisk === null ? 'neutral' : totalAtRisk > 0 ? 'danger' : 'success'} to="/lifecycle" />
        <Kpi Icon={TrendingUp}        label={t('loKpiRiskBreach')}    value={String(riskStats.risksWithOpenBreach)} tone={riskStats.risksWithOpenBreach > 0 ? 'danger' : 'success'} to="/risk-lifecycle" />
        <Kpi Icon={Stamp}              label={t('loKpiAuditCritical')} value={String(auditStats.findingsCritical)} tone={auditStats.findingsCritical > 0 ? 'danger' : 'success'} to="/audit-lifecycle" />
        <Kpi Icon={Fingerprint}        label={t('loKpiDsrOverdue')}
          value={dsrStats ? String(dsrStats.pastDeadline) : null}
          emptyHint={t('loKpiDsrUnread')}
          tone={dsrStats && dsrStats.pastDeadline > 0 ? 'danger' : 'neutral'} to="/privacy-lifecycle" />
        <Kpi Icon={Activity}            label={t('loKpiBcmActive')}     value={String(bcmStats.invocationsThisYear)} tone={bcmStats.activeInvocation ? 'danger' : 'success'} to="/bcm-lifecycle" />
        <Kpi Icon={AlertTriangle}      label={t('loKpiCyberCritical')} value={String(cyberStats.vulnsCritical)}    tone={cyberStats.vulnsCritical > 0 ? 'danger' : 'success'} to="/cyber-lifecycle" />
        <Kpi Icon={Clock}               label={t('loKpiBiaExpiring')}   value={String(bcmStats.biaExpiringSoon)}    tone={bcmStats.biaExpiringSoon > 0 ? 'warning' : 'success'} to="/bcm-lifecycle" />
        <Kpi Icon={Wrench}              label={t('loKpiCapaClosure')}   value={`${auditStats.closureRate}%`}        tone={auditStats.closureRate >= 80 ? 'success' : 'warning'} to="/audit-lifecycle" />
      </div>

      {/* Risk pipeline */}
      <PipelineSection
        link="/risk-lifecycle"
        title={t('loRiskTitle')}
        subtitle={t('loRiskSub')}
        Icon={TrendingUp}
        stages={[
          { code: 'DRAFT',       labelEn: 'Draft',        labelAr: 'مسوّدة',       tone: 'neutral' },
          { code: 'SUBMITTED',   labelEn: 'Submitted',    labelAr: 'مُقدَّمة',      tone: 'warning' },
          { code: 'APPROVED',    labelEn: 'Approved',     labelAr: 'مُعتمَدة',      tone: 'info' },
          { code: 'IN_PROGRESS', labelEn: 'In progress',  labelAr: 'قيد التنفيذ',  tone: 'primary' },
          { code: 'COMPLETED',   labelEn: 'Completed',    labelAr: 'مُنجَزة',        tone: 'success' },
        ]}
        cards={loadTreatmentPlans().map(p => ({
          id: p.id, status: p.status, refCode: p.id,
          titleEn: p.rationaleEn.slice(0, 60) + '…',
          titleAr: p.rationaleAr.slice(0, 60) + '…',
          badges: [{ label: `${p.costSarK}k SAR`, tone: 'primary' as const }],
        }))}
      />

      {/* Audit pipeline */}
      <PipelineSection
        link="/audit-lifecycle"
        title={t('loAuditTitle')}
        subtitle={t('loAuditSub')}
        Icon={Stamp}
        stages={[
          { code: 'OPEN',                  labelEn: 'Open',                 labelAr: 'مفتوحة',         tone: 'danger' },
          { code: 'MGMT_RESPONSE',         labelEn: 'Mgmt response',        labelAr: 'ردّ الإدارة',     tone: 'warning' },
          { code: 'IN_REMEDIATION',        labelEn: 'In remediation',       labelAr: 'قيد المعالجة',    tone: 'info' },
          { code: 'AWAITING_VERIFICATION', labelEn: 'Verification',          labelAr: 'بانتظار التحقق', tone: 'warning' },
          { code: 'CLOSED',                labelEn: 'Closed',                labelAr: 'مُغلَقة',        tone: 'success' },
        ]}
        cards={loadFindings().map(f => {
          const due = dueLabel(f.targetDate, () => '');
          return {
            id: f.id, status: f.status, refCode: f.refNumber,
            titleEn: f.titleEn.slice(0, 50) + (f.titleEn.length > 50 ? '…' : ''),
            titleAr: f.titleAr.slice(0, 50) + (f.titleAr.length > 50 ? '…' : ''),
            badges: [{ label: f.severity, tone: ((f.severity === 'CRITICAL' || f.severity === 'HIGH') ? 'danger' : f.severity === 'MEDIUM' ? 'warning' : 'info') as 'danger' | 'warning' | 'info' }],
            urgent: due?.urgent && f.status !== 'CLOSED',
            dueLabel: due?.label,
          };
        })}
      />

      {/* Privacy DSR pipeline */}
      <PipelineSection
        link="/privacy-lifecycle"
        title={t('loPrivacyTitle')}
        subtitle={t('loPrivacySub')}
        Icon={Fingerprint}
        stages={[
          { code: 'INTAKE',              labelEn: 'Intake',          labelAr: 'استلام',           tone: 'neutral' },
          { code: 'VERIFYING_IDENTITY',  labelEn: 'Verify',           labelAr: 'تحقّق',             tone: 'info' },
          { code: 'COLLECTING',          labelEn: 'Collecting',       labelAr: 'جمع',                tone: 'info' },
          { code: 'REDACTING',           labelEn: 'Redacting',         labelAr: 'إخفاء',             tone: 'warning' },
          { code: 'DELIVERED',           labelEn: 'Delivered',        labelAr: 'مُسلَّم',            tone: 'success' },
        ]}
        cards={(dsrs ?? []).map(d => {
          const due = dueLabel(d.slaDeadline, () => '');
          return {
            id: d.id, status: d.status, refCode: d.id,
            titleEn: `${d.subjectName} · ${d.type}`,
            titleAr: `${d.subjectName} · ${d.type}`,
            // Openness is the complement of the derived terminal set, not a `!==` chain.
            urgent: isPastDeadline(d, now) || (due?.urgent === true && isOpenDsr(d.status)),
            dueLabel: due?.label,
          };
        })}
      />

      {/* BCM Invocation pipeline */}
      <PipelineSection
        link="/bcm-lifecycle"
        title={t('loBcmTitle')}
        subtitle={t('loBcmSub')}
        Icon={Activity}
        stages={[
          { code: 'STANDBY',               labelEn: 'Standby',           labelAr: 'استعداد',           tone: 'neutral' },
          { code: 'DECLARED',              labelEn: 'Declared',          labelAr: 'مُعلَن',             tone: 'warning' },
          { code: 'INVOKED',               labelEn: 'Invoked',           labelAr: 'مُفعَّل',             tone: 'danger' },
          { code: 'STABILISED',            labelEn: 'Stabilised',         labelAr: 'مستقرّ',           tone: 'info' },
          { code: 'STAND_DOWN',            labelEn: 'Stand-down',         labelAr: 'مُنهى',             tone: 'success' },
          { code: 'POST_INCIDENT_REVIEW',  labelEn: 'PIR',                 labelAr: 'مراجعة',           tone: 'primary' },
        ]}
        cards={loadInvocations().map(inv => {
          const plan = pickPlanById(inv.planId);
          return {
            id: inv.id, status: inv.status, refCode: inv.id,
            titleEn: plan?.planNameEn ?? inv.planId.toString(),
            titleAr: plan?.planNameAr ?? inv.planId.toString(),
            badges: [{ label: inv.isReal ? 'REAL' : 'DRILL', tone: (inv.isReal ? 'danger' : 'neutral') as 'danger' | 'neutral' }],
          };
        })}
      />

      {/* Cyber Vuln pipeline */}
      <PipelineSection
        link="/cyber-lifecycle"
        title={t('loCyberTitle')}
        subtitle={t('loCyberSub')}
        Icon={ShieldCheck}
        stages={[
          { code: 'NEW',                   labelEn: 'New',                  labelAr: 'جديدة',                 tone: 'danger' },
          { code: 'TRIAGED',               labelEn: 'Triaged',              labelAr: 'مفروزة',                 tone: 'warning' },
          { code: 'OWNER_ASSIGNED',        labelEn: 'Owner',                labelAr: 'المالك',                  tone: 'info' },
          { code: 'IN_REMEDIATION',        labelEn: 'Remediating',          labelAr: 'معالجة',                  tone: 'warning' },
          { code: 'AWAITING_VERIFICATION', labelEn: 'Verification',          labelAr: 'تحقّق',                   tone: 'info' },
          { code: 'CLOSED',                labelEn: 'Closed',                labelAr: 'مُغلَقة',                 tone: 'success' },
        ]}
        cards={loadVulns().map(v => {
          const due = dueLabel(v.dueDate, () => '');
          const asset = loadAssets().find(a => a.id === v.assetId);
          return {
            id: v.id, status: v.status, refCode: v.cveId,
            titleEn: v.titleEn.slice(0, 50) + (v.titleEn.length > 50 ? '…' : ''),
            titleAr: v.titleAr.slice(0, 50) + (v.titleAr.length > 50 ? '…' : ''),
            subtitleEn: asset?.name,
            subtitleAr: asset?.name,
            badges: [{ label: `CVSS ${v.cvssScore}`, tone: (v.severity === 'CRITICAL' ? 'danger' : v.severity === 'HIGH' ? 'warning' : 'info') as 'danger' | 'warning' | 'info' }],
            urgent: due?.urgent && v.status !== 'CLOSED',
            dueLabel: due?.label,
          };
        })}
      />
    </Shell>
  );
}

// ─── Helper: a single pipeline section with header + click-through link ───

function PipelineSection({ link, title, subtitle, Icon, stages, cards }: {
  link: string;
  title: string;
  subtitle: string;
  Icon: typeof Activity;
  stages: PipelineStage[];
  cards: PipelineCard[];
}) {
  const { t } = useI18n();
  return (
    <div style={{ marginBottom: 16 }}>
      <Card title={title} Icon={Icon}
        action={
          <Link to={link} style={{ textDecoration: 'none' }}>
            <Badge tone="primary">{t('loOpenWorkspace')} <ArrowRight size={11} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineStart: 4 }} /></Badge>
          </Link>
        }>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
          {subtitle}
        </div>
        <LifecyclePipeline
          stages={stages}
          cards={cards}
          showAdvance={false}
        />
      </Card>
    </div>
  );
}

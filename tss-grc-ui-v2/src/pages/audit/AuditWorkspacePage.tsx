import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Briefcase, ClipboardCheck, FileCheck2, BookCopy,
  Stamp, Clock, ArrowRight, Flame, ShieldCheck, ScrollText,
  Activity, BarChart3, Layers, Compass,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { ModuleEngineRequestsPanel } from '../../components/workflows/ModuleEngineRequestsPanel';
import { RaiseRequestButton } from '../../components/workflow/RaiseRequestButton';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Kpi } from '../../components/ui/Kpi';
import { EmptyState } from '../../components/ui/EmptyState';
import { Donut } from '../../components/viz/Donut';
import { COMPLIANCE_FINDINGS, POLICIES, AUDIT_ENGAGEMENTS } from '../compliance/mockCompliance';
import { EVIDENCE, EVIDENCE_REQUESTS, EVIDENCE_REVIEWS } from '../evidence/mockEvidence';
import { useI18n } from '../../lib/i18n';
import { AUTHORITIES } from '../../lib/authorities';
import {
  RISK_ANCHORS, CYBER_ANCHORS, PRIVACY_ANCHORS, BCM_ANCHORS,
  TPRM_ANCHORS, SCM_ANCHORS, ETHICS_ANCHORS, INCIDENT_ANCHORS, CHANGE_ANCHORS,
} from '../../lib/moduleAnchors';
import {
  AUDIT_UNIVERSE, RISK_TONE_UNIVERSE,
  isAuditDueSoon, monthsOverdue,
} from '../../lib/auditUniverse';
import {
  AUDIT_PLANS, planCoverage,
  PLAN_STATUS_TONE, PLAN_STATUS_LABEL,
  PLAN_ROW_STATUS_TONE, PLAN_ROW_STATUS_LABEL,
} from '../../lib/auditPlan';
import {
  AUDITORS, INDEPENDENCE_TONE, INDEPENDENCE_LABEL, ROLE_LABEL,
  rotationStatus, ROTATION_TONE, ROTATION_LABEL,
} from '../../lib/auditors';
import {
  AUDIT_APPOINTMENTS, APPOINTMENT_STATUS_TONE, APPOINTMENT_STATUS_LABEL,
  feesByFirmYear,
} from '../../lib/auditAppointments';
import {
  AUDIT_COMMITTEE_MEMBERS, AUDIT_COMMITTEE_CHARTER,
  AUDIT_COMMITTEE_MEETINGS, MEETING_STATUS_TONE, MEETING_STATUS_LABEL, ROLE_LABEL_CTE,
  complianceCheck, meetingsHeldThisYear,
} from '../../lib/auditCommittee';
import { Users, CalendarDays, AlertOctagon, Banknote, FileSignature, Gavel, Scale } from 'lucide-react';
import '../../styles/pages/audit-workspace.css';

type WsTabId = 'inbox' | 'plan' | 'auditors' | 'appointments' | 'committee';

/**
 * Auditor Workspace — industry-standard GRC tooling "Audit Workspace" homepage.
 * What the auditor sees when they sign in. Cross-authority + cross-module.
 *
 * Six panels:
 *   1. KPI strip — sign-offs / docs / engagements / overdue
 *   2. CAPA sign-off queue — findings at INDEPENDENT_TESTED + AUDIT_SIGNED_OFF
 *   3. Document review queue — policies in LEGAL_REVIEW / EXEC_APPROVAL
 *      and evidence in PENDING_REVIEW
 *   4. My audit engagements — active engagements with progress
 *   5. Findings aging — escalation breakdown
 *   6. Recent reviews — chain-of-custody trail
 */
export function AuditWorkspacePage() {
  const { t, lang } = useI18n();

  // ─── Sign-off queue: findings ready for audit signoff or closure ────────
  const signoffQueue = useMemo(() =>
    COMPLIANCE_FINDINGS
      .filter(f => f.capaStage === 'INDEPENDENT_TESTED' || f.capaStage === 'AUDIT_SIGNED_OFF')
      .sort((a, b) => (a.dueDate ?? '').localeCompare(b.dueDate ?? ''))
  , []);
  const signoffCriticalCount = useMemo(
    () => signoffQueue.filter(f => f.severity === 'CRITICAL' || f.severity === 'HIGH').length,
    [signoffQueue],
  );

  // ─── Document review queue ─────────────────────────────────────────────
  const policyQueue = useMemo(() =>
    POLICIES.filter(p => p.status === 'LEGAL_REVIEW' || p.status === 'EXEC_APPROVAL' || p.status === 'REVIEW_DUE')
            .sort((a, b) => (a.nextReviewDate ?? '').localeCompare(b.nextReviewDate ?? ''))
  , []);

  const evidencePending = useMemo(() =>
    EVIDENCE.filter(e => e.status === 'PENDING_REVIEW')
  , []);

  const evidenceRequests = useMemo(() =>
    EVIDENCE_REQUESTS.filter(r => r.status === 'OPEN' || r.status === 'OVERDUE' || r.status === 'IN_PROGRESS')
  , []);

  // ─── Engagements ────────────────────────────────────────────────────────
  const myEngagements = useMemo(() =>
    AUDIT_ENGAGEMENTS.filter(e => e.status !== 'CLOSED')
                     .sort((a, b) => (b.fieldworkStartDate ?? '').localeCompare(a.fieldworkStartDate ?? ''))
  , []);

  // ─── Findings aging buckets ─────────────────────────────────────────────
  const today = new Date('2026-05-03');
  const aging = useMemo(() => {
    const buckets = { onTrack: 0, atRisk: 0, overdue: 0, escalated: 0 };
    for (const f of COMPLIANCE_FINDINGS) {
      if (f.status === 'CLOSED') continue;
      if (!f.dueDate) { buckets.onTrack += 1; continue; }
      const days = Math.round((new Date(f.dueDate).getTime() - today.getTime()) / 86400000);
      if (days < -30) buckets.escalated += 1;
      else if (days < 0)  buckets.overdue   += 1;
      else if (days < 14) buckets.atRisk    += 1;
      else                buckets.onTrack   += 1;
    }
    return buckets;
  }, [today]);

  // ─── Aggregated chain of custody (recent reviews) ──────────────────────
  const recentReviews = useMemo(() =>
    EVIDENCE_REVIEWS.slice().sort((a, b) => (b.reviewDate ?? '').localeCompare(a.reviewDate ?? ''))
  , []);

  const [tab, setTab] = useState<WsTabId>('inbox');
  const tabs: TabItem<WsTabId>[] = [
    { id: 'inbox',        label: t('audwsTabInbox') },
    { id: 'plan',         label: t('audwsTabPlan') },
    { id: 'auditors',     label: t('audwsTabAuditors') },
    { id: 'appointments', label: t('audwsTabAppointments') },
    { id: 'committee',    label: t('audwsTabCommittee') },
  ];

  return (
    <Shell title={t('audwsTitle')} sub={t('audwsSub')}>
      <PageHeader
        eyebrow={t('audwsEyebrow')}
        title={t('audwsHeader')}
        sub={t('audwsHeaderSub')}
        actions={<RaiseRequestButton sourceModule="AUDIT" />}
      />

      <div style={{ marginBottom: 16 }}><ModuleEngineRequestsPanel sourceModule="AUDIT" /></div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'plan' && <PlanAndUniverseTab t={t} lang={lang} />}
      {tab === 'auditors' && <AuditorsAndCalendarTab t={t} lang={lang} />}
      {tab === 'appointments' && <AppointmentsAndFeesTab t={t} lang={lang} />}
      {tab === 'committee' && <CommitteeTab t={t} lang={lang} />}

      {tab === 'inbox' && <>

      {/* KPI strip */}
      <div className="kpi-grid">
        <Kpi Icon={Stamp}
          label={signoffCriticalCount > 0
            ? `${t('audwsKpiSignoff')} · ${signoffCriticalCount} ${t('audwsCapaSeverityCrit')}`
            : t('audwsKpiSignoff')}
          value={signoffQueue.length}
          to="/capa"
          tone="primary" />
        <Kpi Icon={BookCopy}       label={t('audwsKpiPolicies')}     value={policyQueue.length}                       to="/policy-management" tone="info" />
        <Kpi Icon={FileCheck2}     label={t('audwsKpiEvidence')}     value={evidencePending.length}                   to="/evidence" tone="accent" />
        <Kpi Icon={Briefcase}      label={t('audwsKpiEngagements')}  value={myEngagements.length}                     to="/audit/ia/plans" tone="success" />
        <Kpi Icon={Flame}          label={t('audwsKpiEscalated')}    value={aging.escalated + aging.overdue}          to="/issues" tone="danger" />
      </div>

      {/* Aging donut + Engagements row */}
      <div className="dash-grid mt-md">
        <Card title={t('audwsAging')} Icon={Activity}
          action={<Badge tone={aging.escalated + aging.overdue > 0 ? 'danger' : 'success'}>
            {aging.onTrack + aging.atRisk + aging.overdue + aging.escalated} {t('audwsAgingCenterSub')}
          </Badge>}>
          <Donut
            segments={[
              { label: t('audwsAgingOnTrack'),   value: aging.onTrack,   color: 'var(--success)' },
              { label: t('audwsAgingAtRisk'),    value: aging.atRisk,    color: 'var(--cat-2)' },
              { label: t('audwsAgingOverdue'),   value: aging.overdue,   color: 'var(--accent-700)' },
              { label: t('audwsAgingEscalated'), value: aging.escalated, color: 'var(--danger)' },
            ].filter(s => s.value > 0)}
            centerLabel={String(aging.onTrack + aging.atRisk + aging.overdue + aging.escalated)}
            centerSub={t('audwsAgingCenterSub')} />
        </Card>

        <Card title={t('audwsEngagements')} Icon={Briefcase}
          action={
            myEngagements.length > 5
              ? <Link to="/audit-workspace?tab=engagements" className="audws-viewall-link">
                  {t('audwsEngagementsViewAll')} ({myEngagements.length}) →
                </Link>
              : <Badge tone="primary">{myEngagements.length}</Badge>
          }>
          {myEngagements.length === 0
            ? <EmptyState Icon={Briefcase} title={t('audwsEngagementsEmpty')} />
            : <div className="audws-row-list audws-row-list--gap-md">
                {myEngagements.slice(0, 5).map(e => (
                  <Link key={e.id} to={`/audit-workspace/engagement/${e.id}`} className="audws-row--engagement">
                    <div className="audws-row__top">
                      <div className="audws-row__main">
                        <div className="audws-row__title">
                          {e.titleEn}
                        </div>
                        <div className="audws-row__sub">
                          {e.engagementCode} · {e.auditorFirm} · lead: {e.leadAuditor}
                        </div>
                        <div className="audws-row__meta mono">
                          {e.fieldworkStartDate ?? '—'} → {e.fieldworkEndDate ?? '—'}
                        </div>
                      </div>
                      <div className="audws-row__badges">
                        <Badge tone="info">{e.auditType.replace(/_/g, ' ')}</Badge>
                        <Badge tone={e.status === 'PLANNED' ? 'info' : e.status === 'FIELDWORK' ? 'warning' : e.status === 'REPORT_ISSUED' ? 'accent' : 'success'}>
                          {e.status.replace(/_/g, ' ')}
                        </Badge>
                      </div>
                    </div>
                  </Link>
                ))}
              </div>}
        </Card>
      </div>

      {/* CAPA sign-off queue */}
      <Card title={t('audwsCapaQueue')} Icon={Stamp}
        action={<Badge tone={signoffQueue.length > 0 ? 'warning' : 'success'}>{signoffQueue.length} {t('audwsCapaAwaiting')}</Badge>}>
        {signoffQueue.length === 0
          ? <EmptyState Icon={ShieldCheck} title={t('audwsCapaInboxClear')} sub={t('audwsCapaInboxClearSub')} />
          : <div className="audws-row-list">
              {signoffQueue.map(f => (
                <Link key={f.id} to={`/findings/${f.id}`} className="audws-capa-row">
                  <div className="audws-row__main">
                    <div className="audws-row__title">
                      {f.title}
                    </div>
                    <div className="audws-row__sub">
                      {f.owner ?? '—'} · {f.clauseRef ?? '—'} · due {f.dueDate ?? '—'}
                    </div>
                  </div>
                  <Badge tone={f.severity === 'CRITICAL' ? 'danger' : f.severity === 'HIGH' ? 'warning' : 'info'}>
                    {f.severity}
                  </Badge>
                  <Badge tone={f.capaStage === 'AUDIT_SIGNED_OFF' ? 'accent' : 'primary'}>
                    {(f.capaStage ?? '').replace(/_/g, ' ')}
                  </Badge>
                  <span className="mono audws-capa-row__result">
                    {f.independentTestResult ?? '—'}
                  </span>
                  <ArrowRight size={14} className="audws-capa-row__arrow" />
                </Link>
              ))}
            </div>}
      </Card>

      {/* Document review queue: policies + evidence */}
      <div className="dash-grid mt-md">
        <Card title={t('audwsPolicyQueue')} Icon={BookCopy}
          action={<Badge tone="info">{policyQueue.length}</Badge>}>
          {policyQueue.length === 0
            ? <EmptyState Icon={BookCopy} title={t('audwsPolicyEmpty')} />
            : <div className="audws-row-list">
                {policyQueue.map(p => (
                  <Link key={p.id} to={`/policies/${p.id}`} className="audws-row audws-row--link">
                    <div className="audws-row__main">
                      <div className="audws-row__title">
                        {p.titleEn}
                      </div>
                      <div className="audws-row__sub">
                        {p.code} · v{p.version} · {p.category}
                      </div>
                    </div>
                    <Badge tone={p.status === 'LEGAL_REVIEW' ? 'info' : p.status === 'EXEC_APPROVAL' ? 'accent' : 'warning'}>
                      {p.status.replace(/_/g, ' ')}
                    </Badge>
                  </Link>
                ))}
              </div>}
        </Card>

        <Card title={t('audwsEvPending')} Icon={FileCheck2}
          action={<Badge tone="accent">{evidencePending.length}</Badge>}>
          {evidencePending.length === 0
            ? <EmptyState Icon={FileCheck2} title={t('audwsEvEmpty')} />
            : <div className="audws-row-list">
                {evidencePending.map(e => (
                  <div key={e.id} className="audws-row">
                    <div className="audws-row__main">
                      <div className="audws-row__title">
                        {e.titleEn}
                      </div>
                      <div className="audws-row__sub">
                        {e.evidenceCode} · {e.frameworkCode}
                      </div>
                    </div>
                    <Badge tone="warning">{(e.evidenceType ?? '').replace(/_/g, ' ')}</Badge>
                    <span className="mono audws-text-meta-tiny">
                      due {e.expiryDate ?? '—'}
                    </span>
                  </div>
                ))}
              </div>}
        </Card>
      </div>

      {/* Open evidence requests + recent reviews */}
      <div className="dash-grid mt-md">
        <Card title={t('audwsRequests')} Icon={ScrollText}
          action={<Badge tone="warning">{evidenceRequests.length}</Badge>}>
          {evidenceRequests.length === 0
            ? <EmptyState Icon={ScrollText} title={t('audwsRequestsEmpty')} />
            : <div className="audws-row-list">
                {evidenceRequests.slice(0, 6).map(r => (
                  <div key={r.id} className="audws-row">
                    <div className="audws-row__main">
                      <div className="audws-row__title">
                        {r.titleEn}
                      </div>
                      <div className="audws-row__sub">
                        {r.subControlCode ?? '—'} · {r.frameworkCode ?? '—'} · {r.ownerName}
                      </div>
                    </div>
                    <Badge tone={r.status === 'OVERDUE' ? 'danger' : r.status === 'IN_PROGRESS' ? 'info' : 'warning'}>
                      {r.status}
                    </Badge>
                    <span className="mono audws-text-meta-tiny">{r.dueDate ?? '—'}</span>
                  </div>
                ))}
              </div>}
        </Card>

        <Card title={t('audwsRecent')} Icon={ClipboardCheck}
          action={<Badge tone="success">{recentReviews.length}</Badge>}>
          {recentReviews.length === 0
            ? <EmptyState Icon={ClipboardCheck} title={t('audwsRecentEmpty')} />
            : <div className="audws-row-list">
                {recentReviews.slice(0, 6).map(r => (
                  <div key={r.id} className="audws-row">
                    <div className="audws-row__main">
                      <div className="audws-row__title">
                        {r.evidenceTitleEn ?? `Evidence #${r.evidenceId}`}
                      </div>
                      <div className="audws-row__sub">
                        {r.reviewerName} · <span className="mono">{r.reviewDate ?? '—'}</span>
                      </div>
                    </div>
                    <Badge tone={r.result === 'APPROVED' ? 'success' : 'warning'}>
                      {(r.result ?? '').replace(/_/g, ' ')}
                    </Badge>
                  </div>
                ))}
              </div>}
        </Card>
      </div>

      {/* Coverage donut */}
      <Card title={t('audwsCoverage')} Icon={BarChart3}>
        <div className="audws-coverage-hint">
          {t('audwsCoverageHint')}
        </div>
        <CoverageQuickLinks />
      </Card>
      </>}
    </Shell>
  );
}

// ── Plan & Universe tab (DD-35) ───────────────────────────────────────────
function PlanAndUniverseTab({ t, lang }: { t: (k: string) => string; lang: 'en' | 'ar' }) {
  // Most-current plan to show first
  const activePlan = AUDIT_PLANS.find(p => p.status === 'IN_FLIGHT')
    ?? AUDIT_PLANS.find(p => p.status === 'APPROVED')
    ?? AUDIT_PLANS[0];
  const cov = planCoverage(activePlan);

  // Universe entities sorted by overdue-ness
  const universeSorted = [...AUDIT_UNIVERSE].sort((a, b) => monthsOverdue(b) - monthsOverdue(a));
  const overdueCount  = universeSorted.filter(e => monthsOverdue(e) > 0).length;
  const dueSoonCount  = universeSorted.filter(e => isAuditDueSoon(e) && monthsOverdue(e) <= 0).length;

  return (
    <div className="audws-tab-stack">
      {/* Plan summary */}
      <Card title={`${t('audwsPlanTitle')} ${activePlan.year}`} Icon={Compass}
        action={<Badge tone={PLAN_STATUS_TONE[activePlan.status]} dot>{PLAN_STATUS_LABEL[activePlan.status][lang === 'ar' ? 'ar' : 'en']}</Badge>}>
        <div className="audws-plan-grid">
          <div>
            <div className="audws-plan-summary">
              {lang === 'ar' ? activePlan.summaryAr : activePlan.summaryEn}
            </div>
            {activePlan.approvedAt && (
              <div className="audws-plan-meta">
                {t('audwsPlanApprovedOn')} <span className="mono">{activePlan.approvedAt.slice(0, 10)}</span>
                {activePlan.approvedMinuteRef && <> · <span className="mono">{activePlan.approvedMinuteRef}</span></>}
              </div>
            )}
            <div className="kpi-grid audws-plan-kpis">
              <Kpi Icon={ClipboardCheck} label={t('audwsPlanCompleted')} value={cov.completed} to="/audit/ia/plans" tone="success" />
              <Kpi Icon={Activity}       label={t('audwsPlanInFlight')}  value={cov.inFlight}  to="/audit/ia/plans" tone="warning" />
              <Kpi Icon={Clock}          label={t('audwsPlanPlanned')}   value={cov.planned}   to="/audit/ia/plans" tone="info" />
              <Kpi Icon={Layers}         label={t('audwsPlanTotal')}     value={cov.total}     to="/audit/ia/plans" tone="primary" />
              <Kpi Icon={ShieldCheck}    label={`${t('audwsPlanCompletePct')}`}  value={`${cov.completedPct}%`} to="/audit/ia/plans" tone="success" />
            </div>
          </div>
          <Donut
            segments={[
              { label: t('audwsPlanCompleted'), value: cov.completed, color: 'var(--success)' },
              { label: t('audwsPlanInFlight'),  value: cov.inFlight,  color: 'var(--cat-2)' },
              { label: t('audwsPlanPlanned'),   value: cov.planned,   color: 'var(--primary)' },
              { label: t('audwsPlanDeferred'),  value: cov.deferred,  color: 'var(--accent-700)' },
              { label: t('audwsPlanCancelled'), value: cov.cancelled, color: 'var(--ink-4)' },
            ].filter(s => s.value > 0)}
            centerLabel={`${cov.completedPct}%`}
            centerSub={t('audwsPlanCompleteSub')} />
        </div>
      </Card>

      {/* Plan rows */}
      <Card title={`${t('audwsPlanRowsTitle')} (${activePlan.rows.length})`} Icon={Briefcase}>
        <div className="audws-plan-rows">
          {activePlan.rows.map(r => {
            const universeEntity = AUDIT_UNIVERSE.find(u => u.code === r.universeCode);
            return (
              <div key={r.id} className="audws-plan-row-grid">
                <Badge tone="primary">{r.plannedQuarter}</Badge>
                <div className="audws-row__main">
                  <div className="audws-text-strong audws-trim">
                    {universeEntity ? (lang === 'ar' ? universeEntity.nameAr : universeEntity.nameEn) : r.universeCode}
                  </div>
                  {r.rationaleEn && (
                    <div className="audws-row__sub">{r.rationaleEn}</div>
                  )}
                </div>
                <Badge tone="info">{r.framework.replace(/_/g, ' ')}</Badge>
                <span className="audws-text-meta-2">{r.plannedAuditor}</span>
                <span className="mono audws-text-meta">{r.plannedDays}d</span>
                <Badge tone={PLAN_ROW_STATUS_TONE[r.status]} dot>{PLAN_ROW_STATUS_LABEL[r.status][lang === 'ar' ? 'ar' : 'en']}</Badge>
                {r.linkedEngagementId
                  ? <Link to={`/audit-workspace/engagement/${r.linkedEngagementId}`} className="audws-viewall-link">
                      {t('audwsPlanOpenEngagement')} →
                    </Link>
                  : <span className="audws-text-dim">—</span>}
              </div>
            );
          })}
        </div>
      </Card>

      {/* Audit universe */}
      <Card title={`${t('audwsUniverseTitle')} (${AUDIT_UNIVERSE.length})`} Icon={Layers}
        action={
          <div className="audws-actions-inline">
            {overdueCount > 0 && <Badge tone="danger">{overdueCount} {t('audwsUniverseOverdue')}</Badge>}
            {dueSoonCount > 0 && <Badge tone="warning">{dueSoonCount} {t('audwsUniverseDueSoon')}</Badge>}
          </div>
        }>
        <div className="audws-plan-rows">
          {universeSorted.map(e => {
            const overdue = monthsOverdue(e);
            return (
              <div key={e.code} className="audws-universe-row-grid">
                <Badge tone="neutral">{e.type.replace(/_/g, ' ')}</Badge>
                <div className="audws-row__main">
                  <div className="audws-text-strong">
                    {lang === 'ar' ? e.nameAr : e.nameEn}
                  </div>
                  <div className="audws-text-meta mono">{e.code}</div>
                </div>
                <Badge tone={RISK_TONE_UNIVERSE[e.riskRating]}>{e.riskRating}</Badge>
                <span className="mono audws-text-meta">{e.cycleMonths}m {t('audwsUniverseCycle')}</span>
                <span className="mono audws-text-meta-2">
                  {e.lastAuditedAt ?? '—'}
                </span>
                {overdue > 0
                  ? <Badge tone="danger" dot>{overdue}m {t('audwsUniverseOverdue')}</Badge>
                  : isAuditDueSoon(e)
                    ? <Badge tone="warning" dot>{t('audwsUniverseDueSoon')}</Badge>
                    : <Badge tone="success" dot>{t('audwsUniverseOnTrack')}</Badge>}
              </div>
            );
          })}
        </div>
      </Card>
    </div>
  );
}

function CoverageQuickLinks() {
  const { t } = useI18n();
  const all = [
    ...RISK_ANCHORS, ...CYBER_ANCHORS, ...PRIVACY_ANCHORS, ...BCM_ANCHORS,
    ...TPRM_ANCHORS, ...SCM_ANCHORS, ...ETHICS_ANCHORS, ...INCIDENT_ANCHORS, ...CHANGE_ANCHORS,
  ];
  const counts = new Map<string, { total: number; implemented: number }>();
  for (const a of all) {
    const cur = counts.get(a.authorityCode) ?? { total: 0, implemented: 0 };
    cur.total += 1;
    if (a.controlStatus === 'IMPLEMENTED') cur.implemented += 1;
    counts.set(a.authorityCode, cur);
  }
  return (
    <div className="audws-coverage-strip">
      {AUTHORITIES.filter(a => counts.has(a.code))
        .map(a => {
          const s = counts.get(a.code)!;
          const pct = Math.round((s.implemented / s.total) * 100);
          return (
            <Link key={a.code} to={`/authority/${a.code}`}
              className="audws-coverage-chip"
              style={{ borderInlineStartColor: a.colorToken }}>
              <span className="audws-coverage-chip__code" style={{ color: a.colorToken }}>{a.code}</span>
              <span className="mono audws-text-meta-2">{s.total} {t('audwsCites')}</span>
              <Badge tone={pct >= 80 ? 'success' : pct >= 60 ? 'primary' : pct >= 40 ? 'warning' : 'danger'}>
                <Clock size={9} className="audws-coverage-chip__icon" />
                {pct}%
              </Badge>
            </Link>
          );
        })}
    </div>
  );
}

// ── Auditors & Calendar tab (DD-36) ───────────────────────────────────────
function AuditorsAndCalendarTab({ t, lang }: { t: (k: string) => string; lang: 'en' | 'ar' }) {
  // Snapshot rotation status for each auditor
  const auditorsWithStatus = AUDITORS.map(a => ({ a, ...rotationStatus(a) }));
  const overdue   = auditorsWithStatus.filter(x => x.status === 'PARTNER_OVERDUE' || x.status === 'FIRM_OVERDUE');
  const dueSoon   = auditorsWithStatus.filter(x => x.status === 'PARTNER_DUE_SOON' || x.status === 'FIRM_DUE_SOON');
  const breaches  = AUDITORS.filter(a => a.independence === 'INDEPENDENCE_BREACH');
  const nasFlags  = AUDITORS.filter(a => a.independence === 'NON_AUDIT_SERVICES');
  const internal  = AUDITORS.filter(a => a.role === 'INTERNAL').length;
  const external  = AUDITORS.length - internal;

  // Build calendar rows from the active plan, grouped by quarter
  const activePlan = AUDIT_PLANS.find(p => p.status === 'IN_FLIGHT') ?? AUDIT_PLANS[0];
  const byQuarter = new Map<string, typeof activePlan.rows>();
  for (const r of activePlan.rows) {
    const list = byQuarter.get(r.plannedQuarter) ?? [];
    list.push(r); byQuarter.set(r.plannedQuarter, list);
  }
  const quarters = ['Q1', 'Q2', 'Q3', 'Q4'] as const;

  return (
    <div className="audws-tab-stack">
      {/* KPI strip */}
      <div className="kpi-grid audws-kpi-5">
        <Kpi Icon={Users}        label={t('audwsAuditorsTotal')}    value={AUDITORS.length} to="/audit/ia/universe" tone="primary" />
        <Kpi Icon={ShieldCheck}  label={t('audwsAuditorsExternal')} value={external}        to="/audit/ia/universe" tone="info" />
        <Kpi Icon={AlertOctagon} label={t('audwsAuditorsRotOverdue')} value={overdue.length} to="/audit/ia/universe" tone="danger" />
        <Kpi Icon={Clock}        label={t('audwsAuditorsRotDueSoon')} value={dueSoon.length} to="/audit/ia/universe" tone="warning" />
        <Kpi Icon={Flame}        label={t('audwsAuditorsBreaches')} value={breaches.length} to="/audit/ia/universe" tone="danger" />
      </div>

      {/* Auditor register */}
      <Card title={`${t('audwsAuditorsRegisterTitle')} (${AUDITORS.length})`} Icon={Users}
        action={
          <div className="audws-actions-inline">
            {breaches.length > 0 && <Badge tone="danger">{breaches.length} {t('audwsAuditorsBreach')}</Badge>}
            {nasFlags.length > 0 && <Badge tone="warning">{nasFlags.length} {t('audwsAuditorsNAS')}</Badge>}
          </div>
        }>
        <div className="audws-plan-rows">
          {auditorsWithStatus.map(({ a, status, partnerYears, firmYears, rule }) => (
            <div key={a.code} className="audws-auditor-grid">
              <div className="audws-row__main">
                <div className="audws-text-name">
                  {lang === 'ar' ? a.firmAr : a.firmEn}
                </div>
                <div className="audws-row__sub">
                  {a.leadPartner} · {t('audwsAuditorsCountry')}: {a.country}
                  {a.licenseRef && <> · <span className="mono">{a.licenseRef}</span></>}
                </div>
                {a.noteEn && (
                  <div className="audws-text-italic">
                    {a.noteEn}
                  </div>
                )}
              </div>
              <Badge tone="info">{lang === 'ar' ? ROLE_LABEL[a.role].ar : ROLE_LABEL[a.role].en}</Badge>
              <span className="mono audws-text-meta-2">
                {t('audwsAuditorsPartnerY')}: {partnerYears}y / {rule.partnerYears}y
              </span>
              <span className="mono audws-text-meta-2">
                {rule.firmYears != null ? `${t('audwsAuditorsFirmY')}: ${firmYears}y / ${rule.firmYears}y` : '—'}
              </span>
              <Badge tone={ROTATION_TONE[status]} dot>
                {lang === 'ar' ? ROTATION_LABEL[status].ar : ROTATION_LABEL[status].en}
              </Badge>
              <Badge tone={INDEPENDENCE_TONE[a.independence]} dot>
                {lang === 'ar' ? INDEPENDENCE_LABEL[a.independence].ar : INDEPENDENCE_LABEL[a.independence].en}
              </Badge>
            </div>
          ))}
        </div>
      </Card>

      {/* Calendar (plan rows grouped by quarter) */}
      <Card title={`${t('audwsCalendarTitle')} ${activePlan.year}`} Icon={CalendarDays}>
        <div className="audws-calendar-grid">
          {quarters.map(q => {
            const list = byQuarter.get(q) ?? [];
            return (
              <div key={q} className="audws-calendar-col">
                <div className="audws-calendar-head">
                  <span>{q} {activePlan.year}</span>
                  <span>{list.length}</span>
                </div>
                <div className="audws-calendar-body">
                  {list.length === 0
                    ? <span className="audws-text-dim">—</span>
                    : list.map(r => (
                      <div key={r.id} className="audws-calendar-card"
                        data-tone={r.status === 'COMPLETED' ? 'success' :
                                   r.status === 'IN_FIELDWORK' ? 'warning' :
                                   r.status === 'REPORT' ? 'report' : undefined}>
                        <div className="audws-calendar-card__title">
                          {r.framework.replace(/_/g, ' ')}
                        </div>
                        <div className="audws-calendar-card__sub">
                          {r.plannedAuditor} · {r.plannedDays}d
                        </div>
                      </div>
                    ))}
                </div>
              </div>
            );
          })}
        </div>
      </Card>
    </div>
  );
}

// ── Appointments & Fees tab (DD-37) ───────────────────────────────────────
function AppointmentsAndFeesTab({ t, lang }: { t: (k: string) => string; lang: 'en' | 'ar' }) {
  const active   = AUDIT_APPOINTMENTS.filter(a => a.status === 'ACTIVE').length;
  const inFlight = AUDIT_APPOINTMENTS.filter(a => a.status !== 'ACTIVE' && a.status !== 'CLOSED').length;
  const fees2026 = feesByFirmYear(2026);
  const total2026 = Array.from(fees2026.values()).reduce((s, v) => s + v.total, 0);
  const audit2026 = Array.from(fees2026.values()).reduce((s, v) => s + v.audit, 0);
  const nonAudit2026 = Array.from(fees2026.values()).reduce((s, v) => s + v.nonAudit, 0);
  const ratioPct = audit2026 > 0 ? Math.round((nonAudit2026 / audit2026) * 100) : 0;

  return (
    <div className="audws-tab-stack">
      <div className="kpi-grid audws-kpi-5">
        <Kpi Icon={FileSignature} label={t('audwsAppActive')}     value={active}                     to="/audit/ia/universe" tone="success" />
        <Kpi Icon={Clock}         label={t('audwsAppInFlight')}   value={inFlight}                   to="/audit/ia/universe" tone="warning" />
        <Kpi Icon={Banknote}      label={t('audwsAppFeeTotal')}   value={`${(total2026 / 1_000_000).toFixed(2)}M`} to="/audit/ia/universe" tone="primary" />
        <Kpi Icon={ShieldCheck}   label={t('audwsAppFeeAudit')}   value={`${(audit2026 / 1_000_000).toFixed(2)}M`} to="/audit/ia/universe" tone="info" />
        <Kpi Icon={AlertOctagon}  label={t('audwsAppFeeNonAuditRatio')} value={`${ratioPct}%`}        to="/audit/ia/universe" tone={ratioPct > 30 ? 'danger' : ratioPct > 15 ? 'warning' : 'success'} />
      </div>

      <Card title={`${t('audwsAppTitle')} (${AUDIT_APPOINTMENTS.length})`} Icon={FileSignature}>
        <div className="audws-plan-rows">
          {AUDIT_APPOINTMENTS.map(a => (
            <div key={a.id} className="audws-appointment-wrap">
              <div className="audws-appointment-grid">
                <span className="mono audws-text-meta">{a.appointmentCode}</span>
                <div>
                  <div className="audws-text-name">
                    {lang === 'ar' ? a.titleAr : a.titleEn}
                  </div>
                  <div className="audws-row__sub">
                    {a.frameworks.join(' · ')} · {a.termYears}y · {a.candidates.length} {t('audwsAppCandidates')}
                  </div>
                  {a.noteEn && (
                    <div className="audws-text-italic">{a.noteEn}</div>
                  )}
                </div>
                <Badge tone={APPOINTMENT_STATUS_TONE[a.status]} dot>{APPOINTMENT_STATUS_LABEL[a.status][lang === 'ar' ? 'ar' : 'en']}</Badge>
                <span className="mono audws-text-meta audws-text-end">
                  {a.contractedFeeSar ? `${(a.contractedFeeSar / 1_000_000).toFixed(2)}M SAR` : '—'}
                </span>
                <span className="audws-text-meta-2">{a.selectedFirmCode ?? '—'}</span>
              </div>
              {a.candidates.length > 1 && (
                <div className="audws-appointment-candidates">
                  {a.candidates.map(c => (
                    <span key={c.firmCode} className="audws-candidate">
                      <span className={`mono audws-candidate__code${c.firmCode === a.selectedFirmCode ? ' audws-candidate__code--selected' : ''}`}>
                        {c.firmCode}
                      </span>
                      {c.totalScore != null && <> · {c.totalScore}/100</>}
                      {' · '}{(c.feeBidSar / 1_000_000).toFixed(2)}M
                      {c.independenceDeclared === false && <> · <span className="audws-candidate__danger">{t('audwsAppIndepConcern')}</span></>}
                    </span>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      </Card>

      <Card title={`${t('audwsFeesTitle')} 2026`} Icon={Banknote}>
        <div className="audws-plan-rows">
          {Array.from(fees2026.entries()).sort((a, b) => b[1].total - a[1].total).map(([firm, f]) => (
            <div key={firm} className="audws-fees-grid">
              <span className="mono audws-text-strong">{firm}</span>
              <div className="row-gap-sm" style={{ alignItems: 'center' }}>
                <Badge tone="success">{(f.audit / 1_000).toFixed(0)}k {t('audwsFeesAudit')}</Badge>
                {f.nonAudit > 0 && <Badge tone="warning">{(f.nonAudit / 1_000).toFixed(0)}k {t('audwsFeesNonAudit')}</Badge>}
              </div>
              <span className="mono audws-text-meta-2 audws-text-end">
                {(f.total / 1_000_000).toFixed(2)}M SAR
              </span>
              <span className="mono audws-text-meta audws-text-end">
                {f.ratio}% {t('audwsFeesRatioSuffix')}
              </span>
              {f.ratio > 30
                ? <Badge tone="danger">{t('audwsFeesIndepRisk')}</Badge>
                : f.ratio > 15
                  ? <Badge tone="warning">{t('audwsFeesIndepReview')}</Badge>
                  : <Badge tone="success">{t('audwsFeesIndepClean')}</Badge>}
            </div>
          ))}
        </div>
        <div className="audws-fees-note">
          {t('audwsFeesNote')}
        </div>
      </Card>
    </div>
  );
}

// ── Audit Committee tab (DD-38) ───────────────────────────────────────────
function CommitteeTab({ t, lang }: { t: (k: string) => string; lang: 'en' | 'ar' }) {
  const compliance = complianceCheck();
  const meetingsThisYear = meetingsHeldThisYear(2026);
  const upcoming = AUDIT_COMMITTEE_MEETINGS.filter(m => m.status === 'SCHEDULED').length;

  return (
    <div className="audws-tab-stack">
      {/* KPI strip */}
      <div className="kpi-grid audws-kpi-5">
        <Kpi Icon={Users}        label={t('audwsCteMembers')}      value={AUDIT_COMMITTEE_MEMBERS.filter(m => m.role !== 'SECRETARY').length} to="/audit-committee/dashboard" tone="primary" />
        <Kpi Icon={Scale}        label={t('audwsCteIndependent')}  value={AUDIT_COMMITTEE_MEMBERS.filter(m => m.role === 'CHAIR' || m.role === 'INDEPENDENT_MEMBER').length} to="/audit-committee/dashboard" tone="info" />
        <Kpi Icon={ShieldCheck}  label={t('audwsCteCompliance')}   value={compliance.compliant ? '✓' : '✗'} to="/audit-committee/dashboard" tone={compliance.compliant ? 'success' : 'danger'} />
        <Kpi Icon={CalendarDays} label={t('audwsCteMeetingsHeld')} value={meetingsThisYear} to="/audit-committee/dashboard" tone="success" />
        <Kpi Icon={Clock}        label={t('audwsCteMeetingsUpcoming')} value={upcoming} to="/audit-committee/dashboard" tone="warning" />
      </div>

      {/* Composition */}
      <Card title={t('audwsCteComposition')} Icon={Users}
        action={<Badge tone={compliance.compliant ? 'success' : 'danger'} dot>
          {compliance.compliant ? t('audwsCteCompliant') : t('audwsCteNonCompliant')}
        </Badge>}>
        {!compliance.compliant && (
          <div className="audws-compliance-banner">
            {compliance.reasons.map((r, i) => <div key={i}>• {r}</div>)}
          </div>
        )}
        {AUDIT_COMMITTEE_MEMBERS.map(m => (
          <div key={m.nameEn} className="audws-committee-grid">
            <div>
              <div className="audws-text-name">
                {lang === 'ar' ? m.nameAr : m.nameEn}
              </div>
              {m.noteEn && (
                <div className="audws-row__sub">{m.noteEn}</div>
              )}
            </div>
            <Badge tone={m.role === 'CHAIR' ? 'primary' : m.role === 'INDEPENDENT_MEMBER' ? 'info' : m.role === 'SECRETARY' ? 'neutral' : 'accent'}>
              {lang === 'ar' ? ROLE_LABEL_CTE[m.role].ar : ROLE_LABEL_CTE[m.role].en}
            </Badge>
            <span className="mono audws-text-meta">
              {m.appointedAt} → {m.termEndsAt}
            </span>
            {m.financialExpert
              ? <Badge tone="success">{t('audwsCteFinExpert')}</Badge>
              : <span className="audws-text-dim">—</span>}
            <span className="audws-text-meta">{m.role === 'SECRETARY' ? t('audwsCteNonVoting') : t('audwsCteVoting')}</span>
          </div>
        ))}
      </Card>

      {/* Charter */}
      <Card title={t('audwsCteCharter')} Icon={Gavel}
        action={<Badge tone="info">
          {t('audwsCteApprovedOn')} {AUDIT_COMMITTEE_CHARTER.approvedByBoardOn}
        </Badge>}>
        <div className="audws-charter-grid">
          <div>
            <div className="audws-charter-label">
              {t('audwsCteAuthority')}
            </div>
            <ul className="audws-charter-list">
              {(lang === 'ar' ? AUDIT_COMMITTEE_CHARTER.authorityAr : AUDIT_COMMITTEE_CHARTER.authorityEn).map((a, i) => (
                <li key={i}>{a}</li>
              ))}
            </ul>
          </div>
          <div>
            <div className="audws-charter-label">
              {t('audwsCteResponsibilities')}
            </div>
            <ul className="audws-charter-list">
              {(lang === 'ar' ? AUDIT_COMMITTEE_CHARTER.responsibilitiesAr : AUDIT_COMMITTEE_CHARTER.responsibilitiesEn).map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          </div>
        </div>
        <div className="audws-charter-rules">
          {t('audwsCteRules')}: ≥{AUDIT_COMMITTEE_CHARTER.minMembers} {t('audwsCteMembersWord')} · ≥{Math.round(AUDIT_COMMITTEE_CHARTER.minIndependentRatio * 100)}% {t('audwsCteIndependentWord')} · ≥{AUDIT_COMMITTEE_CHARTER.minMeetingsPerYear} {t('audwsCteMeetingsPerYear')}
        </div>
      </Card>

      {/* Meetings */}
      <Card title={`${t('audwsCteMeetingsTitle')} (${AUDIT_COMMITTEE_MEETINGS.length})`} Icon={CalendarDays}>
        {[...AUDIT_COMMITTEE_MEETINGS].sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt)).map(m => (
          <div key={m.id} className="audws-meeting-wrap">
            <div className="audws-meeting-grid">
              <span className="mono audws-text-meta">{m.meetingCode}</span>
              <span className="mono audws-text-meta-2">{m.scheduledAt.replace('T', ' ').slice(0, 16)}</span>
              <div>
                <div className="audws-text-name">
                  {lang === 'ar' ? m.titleAr : m.titleEn}
                </div>
                <div className="audws-row__sub" style={{ marginTop: 2 }}>
                  {m.attendeeCodes.length} {t('audwsCteAttendees')}
                  {m.minuteRef ? ' · ' + m.minuteRef : ''}
                </div>
              </div>
              <Badge tone={MEETING_STATUS_TONE[m.status]} dot>{MEETING_STATUS_LABEL[m.status][lang === 'ar' ? 'ar' : 'en']}</Badge>
              <span className="audws-text-meta">{m.agenda.length} {t('audwsCteAgendaItems')}</span>
            </div>
            {m.agenda.length > 0 && (
              <div className="audws-meeting-agenda">
                {m.agenda.map((a, i) => (
                  <div key={i} className="audws-meeting-agenda-item">
                    <div className="audws-text-strong">
                      {i + 1}. {lang === 'ar' ? a.titleAr : a.titleEn}
                      {a.ref && ' · ' + a.ref}
                    </div>
                    {(a.decisionEn ?? a.decisionAr) && (
                      <div className="audws-meeting-decision">
                        → {lang === 'ar' ? (a.decisionAr ?? a.decisionEn) : a.decisionEn}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
      </Card>
    </div>
  );
}

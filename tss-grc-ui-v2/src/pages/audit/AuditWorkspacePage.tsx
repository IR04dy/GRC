import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Briefcase, ClipboardCheck, FileCheck2, BookCopy,
  Stamp, Clock, ArrowRight, Flame, ShieldCheck, ScrollText,
  Activity, BarChart3, Layers, Compass,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
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
  PLAN_STATUS_TONE, PLAN_ROW_STATUS_TONE,
} from '../../lib/auditPlan';
import {
  AUDITORS, INDEPENDENCE_TONE, INDEPENDENCE_LABEL, ROLE_LABEL,
  rotationStatus, ROTATION_TONE, ROTATION_LABEL,
} from '../../lib/auditors';
import {
  AUDIT_APPOINTMENTS, APPOINTMENT_STATUS_TONE,
  feesByFirmYear,
} from '../../lib/auditAppointments';
import {
  AUDIT_COMMITTEE_MEMBERS, AUDIT_COMMITTEE_CHARTER,
  AUDIT_COMMITTEE_MEETINGS, MEETING_STATUS_TONE, ROLE_LABEL_CTE,
  complianceCheck, meetingsHeldThisYear,
} from '../../lib/auditCommittee';
import { Users, CalendarDays, AlertOctagon, Banknote, FileSignature, Gavel, Scale } from 'lucide-react';

type WsTabId = 'inbox' | 'plan' | 'auditors' | 'appointments' | 'committee';

/**
 * Auditor Workspace — ServiceNow IRM "Audit Workspace" homepage.
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
      />

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
          tone="primary" />
        <Kpi Icon={BookCopy}       label={t('audwsKpiPolicies')}     value={policyQueue.length}                       tone="info" />
        <Kpi Icon={FileCheck2}     label={t('audwsKpiEvidence')}     value={evidencePending.length}                   tone="accent" />
        <Kpi Icon={Briefcase}      label={t('audwsKpiEngagements')}  value={myEngagements.length}                     tone="success" />
        <Kpi Icon={Flame}          label={t('audwsKpiEscalated')}    value={aging.escalated + aging.overdue}          tone="danger" />
      </div>

      {/* Aging donut + Engagements row */}
      <div className="dash-grid" style={{ marginTop: 12 }}>
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
              ? <Link to="/audit-workspace?tab=engagements"
                  style={{ fontSize: 11, color: 'var(--primary)', textDecoration: 'none' }}>
                  {t('audwsEngagementsViewAll')} ({myEngagements.length}) →
                </Link>
              : <Badge tone="primary">{myEngagements.length}</Badge>
          }>
          {myEngagements.length === 0
            ? <EmptyState Icon={Briefcase} title={t('audwsEngagementsEmpty')} />
            : <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {myEngagements.slice(0, 5).map(e => (
                  <Link key={e.id} to={`/audit-workspace/engagement/${e.id}`} style={{
                    padding: '6px 8px', borderBottom: '1px dashed var(--border)',
                    textDecoration: 'none', color: 'inherit',
                    transition: 'background var(--dur-fast) var(--ease-standard)',
                  }}
                    onMouseEnter={(ev) => (ev.currentTarget as HTMLElement).style.background = 'var(--primary-50)'}
                    onMouseLeave={(ev) => (ev.currentTarget as HTMLElement).style.background = 'transparent'}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 10 }}>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontWeight: 600, fontSize: 13,
                                      whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {e.titleEn}
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 1 }}>
                          {e.engagementCode} · {e.auditorFirm} · lead: {e.leadAuditor}
                        </div>
                        <div style={{ fontSize: 10, color: 'var(--ink-4)', marginTop: 1 }} className="mono">
                          {e.fieldworkStartDate ?? '—'} → {e.fieldworkEndDate ?? '—'}
                        </div>
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 4, alignItems: 'flex-end' }}>
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
          : <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {signoffQueue.map(f => (
                <Link key={f.id} to={`/findings/${f.id}`} style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr auto auto auto auto',
                  gap: 10, alignItems: 'center',
                  padding: '8px 10px',
                  borderRadius: 'var(--r-sm)',
                  border: '1px solid var(--border)',
                  background: 'var(--surface-1)',
                  textDecoration: 'none', color: 'inherit',
                  transition: 'border-color var(--dur-fast) var(--ease-standard), background var(--dur-fast) var(--ease-standard)',
                }}
                  onMouseEnter={(e) => {
                    const el = e.currentTarget as HTMLElement;
                    el.style.borderColor = 'var(--primary-300)';
                    el.style.background  = 'var(--primary-50)';
                  }}
                  onMouseLeave={(e) => {
                    const el = e.currentTarget as HTMLElement;
                    el.style.borderColor = 'var(--border)';
                    el.style.background  = 'var(--surface-1)';
                  }}>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 600, fontSize: 13,
                                  whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {f.title}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 1 }}>
                      {f.owner ?? '—'} · {f.clauseRef ?? '—'} · due {f.dueDate ?? '—'}
                    </div>
                  </div>
                  <Badge tone={f.severity === 'CRITICAL' ? 'danger' : f.severity === 'HIGH' ? 'warning' : 'info'}>
                    {f.severity}
                  </Badge>
                  <Badge tone={f.capaStage === 'AUDIT_SIGNED_OFF' ? 'accent' : 'primary'}>
                    {(f.capaStage ?? '').replace(/_/g, ' ')}
                  </Badge>
                  <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                    {f.independentTestResult ?? '—'}
                  </span>
                  <ArrowRight size={14} style={{ color: 'var(--ink-3)' }} />
                </Link>
              ))}
            </div>}
      </Card>

      {/* Document review queue: policies + evidence */}
      <div className="dash-grid" style={{ marginTop: 12 }}>
        <Card title={t('audwsPolicyQueue')} Icon={BookCopy}
          action={<Badge tone="info">{policyQueue.length}</Badge>}>
          {policyQueue.length === 0
            ? <EmptyState Icon={BookCopy} title={t('audwsPolicyEmpty')} />
            : <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {policyQueue.map(p => (
                  <Link key={p.id} to={`/policies/${p.id}`} style={{
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10,
                    padding: '6px 8px', borderBottom: '1px dashed var(--border)',
                    textDecoration: 'none', color: 'inherit',
                  }}>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 600, fontSize: 13,
                                    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {p.titleEn}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
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
            : <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {evidencePending.map(e => (
                  <div key={e.id} style={{
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10,
                    padding: '6px 8px', borderBottom: '1px dashed var(--border)',
                  }}>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 600, fontSize: 13,
                                    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {e.titleEn}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                        {e.evidenceCode} · {e.frameworkCode}
                      </div>
                    </div>
                    <Badge tone="warning">{(e.evidenceType ?? '').replace(/_/g, ' ')}</Badge>
                    <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                      due {e.expiryDate ?? '—'}
                    </span>
                  </div>
                ))}
              </div>}
        </Card>
      </div>

      {/* Open evidence requests + recent reviews */}
      <div className="dash-grid" style={{ marginTop: 12 }}>
        <Card title={t('audwsRequests')} Icon={ScrollText}
          action={<Badge tone="warning">{evidenceRequests.length}</Badge>}>
          {evidenceRequests.length === 0
            ? <EmptyState Icon={ScrollText} title={t('audwsRequestsEmpty')} />
            : <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {evidenceRequests.slice(0, 6).map(r => (
                  <div key={r.id} style={{
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10,
                    padding: '6px 8px', borderBottom: '1px dashed var(--border)',
                  }}>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 600, fontSize: 13,
                                    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {r.titleEn}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                        {r.subControlCode ?? '—'} · {r.frameworkCode ?? '—'} · {r.ownerName}
                      </div>
                    </div>
                    <Badge tone={r.status === 'OVERDUE' ? 'danger' : r.status === 'IN_PROGRESS' ? 'info' : 'warning'}>
                      {r.status}
                    </Badge>
                    <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{r.dueDate ?? '—'}</span>
                  </div>
                ))}
              </div>}
        </Card>

        <Card title={t('audwsRecent')} Icon={ClipboardCheck}
          action={<Badge tone="success">{recentReviews.length}</Badge>}>
          {recentReviews.length === 0
            ? <EmptyState Icon={ClipboardCheck} title={t('audwsRecentEmpty')} />
            : <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {recentReviews.slice(0, 6).map(r => (
                  <div key={r.id} style={{
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10,
                    padding: '6px 8px', borderBottom: '1px dashed var(--border)',
                  }}>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 600, fontSize: 13,
                                    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {r.evidenceTitleEn ?? `Evidence #${r.evidenceId}`}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
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
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>
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
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Plan summary */}
      <Card title={`${t('audwsPlanTitle')} ${activePlan.year}`} Icon={Compass}
        action={<Badge tone={PLAN_STATUS_TONE[activePlan.status]} dot>{activePlan.status.replace(/_/g, ' ')}</Badge>}>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 16, alignItems: 'start' }}>
          <div>
            <div style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.5 }}>
              {lang === 'ar' ? activePlan.summaryAr : activePlan.summaryEn}
            </div>
            {activePlan.approvedAt && (
              <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 6 }}>
                {t('audwsPlanApprovedOn')} <span className="mono">{activePlan.approvedAt.slice(0, 10)}</span>
                {activePlan.approvedMinuteRef && <> · <span className="mono">{activePlan.approvedMinuteRef}</span></>}
              </div>
            )}
            <div className="kpi-grid" style={{ marginTop: 12, gridTemplateColumns: 'repeat(5, 1fr)' }}>
              <Kpi Icon={ClipboardCheck} label={t('audwsPlanCompleted')} value={cov.completed} tone="success" />
              <Kpi Icon={Activity}       label={t('audwsPlanInFlight')}  value={cov.inFlight}  tone="warning" />
              <Kpi Icon={Clock}          label={t('audwsPlanPlanned')}   value={cov.planned}   tone="info" />
              <Kpi Icon={Layers}         label={t('audwsPlanTotal')}     value={cov.total}     tone="primary" />
              <Kpi Icon={ShieldCheck}    label={`${t('audwsPlanCompletePct')}`}  value={`${cov.completedPct}%`} tone="success" />
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
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {activePlan.rows.map(r => {
            const universeEntity = AUDIT_UNIVERSE.find(u => u.code === r.universeCode);
            return (
              <div key={r.id} style={{
                display: 'grid',
                gridTemplateColumns: '70px 1fr 110px 130px 80px 100px 110px',
                gap: 10, alignItems: 'center',
                padding: '8px 0', borderBottom: '1px dashed var(--border)',
                fontSize: 12,
              }}>
                <Badge tone="primary">{r.plannedQuarter}</Badge>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: 600, color: 'var(--ink-1)',
                                whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {universeEntity ? (lang === 'ar' ? universeEntity.nameAr : universeEntity.nameEn) : r.universeCode}
                  </div>
                  {r.rationaleEn && (
                    <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 1 }}>{r.rationaleEn}</div>
                  )}
                </div>
                <Badge tone="info">{r.framework.replace(/_/g, ' ')}</Badge>
                <span style={{ fontSize: 11, color: 'var(--ink-2)' }}>{r.plannedAuditor}</span>
                <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{r.plannedDays}d</span>
                <Badge tone={PLAN_ROW_STATUS_TONE[r.status]} dot>{r.status.replace(/_/g, ' ')}</Badge>
                {r.linkedEngagementId
                  ? <Link to={`/audit-workspace/engagement/${r.linkedEngagementId}`}
                      style={{ fontSize: 11, color: 'var(--primary)', textDecoration: 'none' }}>
                      {t('audwsPlanOpenEngagement')} →
                    </Link>
                  : <span style={{ fontSize: 11, color: 'var(--ink-4)' }}>—</span>}
              </div>
            );
          })}
        </div>
      </Card>

      {/* Audit universe */}
      <Card title={`${t('audwsUniverseTitle')} (${AUDIT_UNIVERSE.length})`} Icon={Layers}
        action={
          <div style={{ display: 'inline-flex', gap: 6 }}>
            {overdueCount > 0 && <Badge tone="danger">{overdueCount} {t('audwsUniverseOverdue')}</Badge>}
            {dueSoonCount > 0 && <Badge tone="warning">{dueSoonCount} {t('audwsUniverseDueSoon')}</Badge>}
          </div>
        }>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {universeSorted.map(e => {
            const overdue = monthsOverdue(e);
            return (
              <div key={e.code} style={{
                display: 'grid',
                gridTemplateColumns: '110px 1fr 130px 80px 100px 100px',
                gap: 10, alignItems: 'center',
                padding: '8px 0', borderBottom: '1px dashed var(--border)',
                fontSize: 12,
              }}>
                <Badge tone="neutral">{e.type.replace(/_/g, ' ')}</Badge>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: 600, color: 'var(--ink-1)' }}>
                    {lang === 'ar' ? e.nameAr : e.nameEn}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)' }} className="mono">{e.code}</div>
                </div>
                <Badge tone={RISK_TONE_UNIVERSE[e.riskRating]}>{e.riskRating}</Badge>
                <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{e.cycleMonths}m {t('audwsUniverseCycle')}</span>
                <span className="mono" style={{ fontSize: 11, color: 'var(--ink-2)' }}>
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
    <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
      {AUTHORITIES.filter(a => counts.has(a.code))
        .map(a => {
          const s = counts.get(a.code)!;
          const pct = Math.round((s.implemented / s.total) * 100);
          return (
            <Link key={a.code} to={`/authority/${a.code}`} style={{
              display: 'inline-flex', alignItems: 'center', gap: 6,
              padding: '6px 10px',
              border: '1px solid var(--border)',
              borderInlineStartWidth: 3, borderInlineStartColor: a.colorToken,
              borderRadius: 'var(--r-sm)',
              background: 'var(--surface-1)',
              fontSize: 12,
              textDecoration: 'none', color: 'inherit',
              transition: 'background var(--dur-fast) var(--ease-standard)',
            }}
              onMouseEnter={(ev) => (ev.currentTarget as HTMLElement).style.background = 'var(--primary-50)'}
              onMouseLeave={(ev) => (ev.currentTarget as HTMLElement).style.background = 'var(--surface-1)'}
            >
              <span style={{ fontWeight: 700, color: a.colorToken }}>{a.code}</span>
              <span className="mono" style={{ fontSize: 11, color: 'var(--ink-2)' }}>{s.total} {t('audwsCites')}</span>
              <Badge tone={pct >= 80 ? 'success' : pct >= 60 ? 'primary' : pct >= 40 ? 'warning' : 'danger'}>
                <Clock size={9} style={{ marginInlineEnd: 3, verticalAlign: 'middle' }} />
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
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* KPI strip */}
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(5, 1fr)' }}>
        <Kpi Icon={Users}        label={t('audwsAuditorsTotal')}    value={AUDITORS.length} tone="primary" />
        <Kpi Icon={ShieldCheck}  label={t('audwsAuditorsExternal')} value={external}        tone="info" />
        <Kpi Icon={AlertOctagon} label={t('audwsAuditorsRotOverdue')} value={overdue.length} tone="danger" />
        <Kpi Icon={Clock}        label={t('audwsAuditorsRotDueSoon')} value={dueSoon.length} tone="warning" />
        <Kpi Icon={Flame}        label={t('audwsAuditorsBreaches')} value={breaches.length} tone="danger" />
      </div>

      {/* Auditor register */}
      <Card title={`${t('audwsAuditorsRegisterTitle')} (${AUDITORS.length})`} Icon={Users}
        action={
          <div style={{ display: 'inline-flex', gap: 6 }}>
            {breaches.length > 0 && <Badge tone="danger">{breaches.length} {t('audwsAuditorsBreach')}</Badge>}
            {nasFlags.length > 0 && <Badge tone="warning">{nasFlags.length} {t('audwsAuditorsNAS')}</Badge>}
          </div>
        }>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {auditorsWithStatus.map(({ a, status, partnerYears, firmYears, rule }) => (
            <div key={a.code} style={{
              display: 'grid',
              gridTemplateColumns: '1fr 130px 110px 110px 1fr 130px',
              gap: 10, alignItems: 'center',
              padding: '10px 0', borderBottom: '1px dashed var(--border)',
              fontSize: 12,
            }}>
              <div style={{ minWidth: 0 }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                  {lang === 'ar' ? a.firmAr : a.firmEn}
                </div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 1 }}>
                  {a.leadPartner} · {t('audwsAuditorsCountry')}: {a.country}
                  {a.licenseRef && <> · <span className="mono">{a.licenseRef}</span></>}
                </div>
                {a.noteEn && (
                  <div style={{ fontSize: 11, color: 'var(--ink-2)', marginTop: 4, fontStyle: 'italic' }}>
                    {a.noteEn}
                  </div>
                )}
              </div>
              <Badge tone="info">{lang === 'ar' ? ROLE_LABEL[a.role].ar : ROLE_LABEL[a.role].en}</Badge>
              <span className="mono" style={{ fontSize: 11, color: 'var(--ink-2)' }}>
                {t('audwsAuditorsPartnerY')}: {partnerYears}y / {rule.partnerYears}y
              </span>
              <span className="mono" style={{ fontSize: 11, color: 'var(--ink-2)' }}>
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
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
          {quarters.map(q => {
            const list = byQuarter.get(q) ?? [];
            return (
              <div key={q} style={{
                padding: '10px 12px',
                borderRadius: 'var(--r-sm)',
                border: '1px solid var(--border)',
                background: 'var(--surface-1)',
                minHeight: 200,
              }}>
                <div style={{ fontSize: 12, color: 'var(--ink-3)', fontWeight: 700,
                              textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8,
                              display: 'flex', justifyContent: 'space-between' }}>
                  <span>{q} {activePlan.year}</span>
                  <span>{list.length}</span>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {list.length === 0
                    ? <span style={{ fontSize: 11, color: 'var(--ink-4)' }}>—</span>
                    : list.map(r => (
                      <div key={r.id} style={{
                        padding: '6px 8px', borderRadius: 'var(--r-sm)',
                        background: 'var(--surface-2)',
                        borderInlineStartWidth: 3,
                        borderInlineStartStyle: 'solid',
                        borderInlineStartColor: r.status === 'COMPLETED' ? 'var(--success)' :
                                                r.status === 'IN_FIELDWORK' ? 'var(--cat-2)' :
                                                r.status === 'REPORT' ? 'var(--accent-700)' : 'var(--ink-4)',
                      }}>
                        <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--ink-1)' }}>
                          {r.framework.replace(/_/g, ' ')}
                        </div>
                        <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>
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
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(5, 1fr)' }}>
        <Kpi Icon={FileSignature} label={t('audwsAppActive')}     value={active}                     tone="success" />
        <Kpi Icon={Clock}         label={t('audwsAppInFlight')}   value={inFlight}                   tone="warning" />
        <Kpi Icon={Banknote}      label={t('audwsAppFeeTotal')}   value={`${(total2026 / 1_000_000).toFixed(2)}M`} tone="primary" />
        <Kpi Icon={ShieldCheck}   label={t('audwsAppFeeAudit')}   value={`${(audit2026 / 1_000_000).toFixed(2)}M`} tone="info" />
        <Kpi Icon={AlertOctagon}  label={t('audwsAppFeeNonAuditRatio')} value={`${ratioPct}%`}        tone={ratioPct > 30 ? 'danger' : ratioPct > 15 ? 'warning' : 'success'} />
      </div>

      <Card title={`${t('audwsAppTitle')} (${AUDIT_APPOINTMENTS.length})`} Icon={FileSignature}>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {AUDIT_APPOINTMENTS.map(a => (
            <div key={a.id} style={{
              padding: '10px 0', borderBottom: '1px dashed var(--border)',
            }}>
              <div style={{ display: 'grid', gridTemplateColumns: '110px 1fr 130px 90px 130px', gap: 10, alignItems: 'center' }}>
                <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{a.appointmentCode}</span>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                    {lang === 'ar' ? a.titleAr : a.titleEn}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 1 }}>
                    {a.frameworks.join(' · ')} · {a.termYears}y · {a.candidates.length} {t('audwsAppCandidates')}
                  </div>
                  {a.noteEn && (
                    <div style={{ fontSize: 11, color: 'var(--ink-2)', marginTop: 4, fontStyle: 'italic' }}>{a.noteEn}</div>
                  )}
                </div>
                <Badge tone={APPOINTMENT_STATUS_TONE[a.status]} dot>{a.status.replace(/_/g, ' ')}</Badge>
                <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)', textAlign: 'end' }}>
                  {a.contractedFeeSar ? `${(a.contractedFeeSar / 1_000_000).toFixed(2)}M SAR` : '—'}
                </span>
                <span style={{ fontSize: 11, color: 'var(--ink-2)' }}>{a.selectedFirmCode ?? '—'}</span>
              </div>
              {a.candidates.length > 1 && (
                <div style={{ marginTop: 6, paddingInlineStart: 10, fontSize: 11, color: 'var(--ink-3)' }}>
                  {a.candidates.map(c => (
                    <span key={c.firmCode} style={{ display: 'inline-block', marginInlineEnd: 12 }}>
                      <span className="mono" style={{ color: c.firmCode === a.selectedFirmCode ? 'var(--success-fg)' : 'var(--ink-3)', fontWeight: c.firmCode === a.selectedFirmCode ? 700 : 400 }}>
                        {c.firmCode}
                      </span>
                      {c.totalScore != null && <> · {c.totalScore}/100</>}
                      {' · '}{(c.feeBidSar / 1_000_000).toFixed(2)}M
                      {c.independenceDeclared === false && <> · <span style={{ color: 'var(--danger-fg)' }}>{t('audwsAppIndepConcern')}</span></>}
                    </span>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      </Card>

      <Card title={`${t('audwsFeesTitle')} 2026`} Icon={Banknote}>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {Array.from(fees2026.entries()).sort((a, b) => b[1].total - a[1].total).map(([firm, f]) => (
            <div key={firm} style={{
              display: 'grid',
              gridTemplateColumns: '110px 1fr 110px 110px 110px',
              gap: 10, alignItems: 'center',
              padding: '8px 0', borderBottom: '1px dashed var(--border)',
              fontSize: 12,
            }}>
              <span className="mono" style={{ fontWeight: 700 }}>{firm}</span>
              <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
                <Badge tone="success">{(f.audit / 1_000).toFixed(0)}k {t('audwsFeesAudit')}</Badge>
                {f.nonAudit > 0 && <Badge tone="warning">{(f.nonAudit / 1_000).toFixed(0)}k {t('audwsFeesNonAudit')}</Badge>}
              </div>
              <span className="mono" style={{ fontSize: 11, color: 'var(--ink-2)', textAlign: 'end' }}>
                {(f.total / 1_000_000).toFixed(2)}M SAR
              </span>
              <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)', textAlign: 'end' }}>
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
        <div style={{ fontSize: 10, color: 'var(--ink-4)', marginTop: 8, fontStyle: 'italic' }}>
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
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* KPI strip */}
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(5, 1fr)' }}>
        <Kpi Icon={Users}        label={t('audwsCteMembers')}      value={AUDIT_COMMITTEE_MEMBERS.filter(m => m.role !== 'SECRETARY').length} tone="primary" />
        <Kpi Icon={Scale}        label={t('audwsCteIndependent')}  value={AUDIT_COMMITTEE_MEMBERS.filter(m => m.role === 'CHAIR' || m.role === 'INDEPENDENT_MEMBER').length} tone="info" />
        <Kpi Icon={ShieldCheck}  label={t('audwsCteCompliance')}   value={compliance.compliant ? '✓' : '✗'} tone={compliance.compliant ? 'success' : 'danger'} />
        <Kpi Icon={CalendarDays} label={t('audwsCteMeetingsHeld')} value={meetingsThisYear} tone="success" />
        <Kpi Icon={Clock}        label={t('audwsCteMeetingsUpcoming')} value={upcoming} tone="warning" />
      </div>

      {/* Composition */}
      <Card title={t('audwsCteComposition')} Icon={Users}
        action={<Badge tone={compliance.compliant ? 'success' : 'danger'} dot>
          {compliance.compliant ? t('audwsCteCompliant') : t('audwsCteNonCompliant')}
        </Badge>}>
        {!compliance.compliant && (
          <div style={{ padding: '8px 12px', borderRadius: 'var(--r-sm)',
                        background: 'var(--danger-bg)', color: 'var(--danger-fg)',
                        fontSize: 12, marginBottom: 10 }}>
            {compliance.reasons.map((r, i) => <div key={i}>• {r}</div>)}
          </div>
        )}
        {AUDIT_COMMITTEE_MEMBERS.map(m => (
          <div key={m.nameEn} style={{
            display: 'grid',
            gridTemplateColumns: '1fr 130px 130px 110px 80px',
            gap: 10, alignItems: 'center',
            padding: '8px 0', borderBottom: '1px dashed var(--border)',
            fontSize: 12,
          }}>
            <div>
              <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                {lang === 'ar' ? m.nameAr : m.nameEn}
              </div>
              {m.noteEn && (
                <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 1 }}>{m.noteEn}</div>
              )}
            </div>
            <Badge tone={m.role === 'CHAIR' ? 'primary' : m.role === 'INDEPENDENT_MEMBER' ? 'info' : m.role === 'SECRETARY' ? 'neutral' : 'accent'}>
              {lang === 'ar' ? ROLE_LABEL_CTE[m.role].ar : ROLE_LABEL_CTE[m.role].en}
            </Badge>
            <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
              {m.appointedAt} → {m.termEndsAt}
            </span>
            {m.financialExpert
              ? <Badge tone="success">{t('audwsCteFinExpert')}</Badge>
              : <span style={{ fontSize: 11, color: 'var(--ink-4)' }}>—</span>}
            <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{m.role === 'SECRETARY' ? t('audwsCteNonVoting') : t('audwsCteVoting')}</span>
          </div>
        ))}
      </Card>

      {/* Charter */}
      <Card title={t('audwsCteCharter')} Icon={Gavel}
        action={<Badge tone="info">
          {t('audwsCteApprovedOn')} {AUDIT_COMMITTEE_CHARTER.approvedByBoardOn}
        </Badge>}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 700, marginBottom: 8 }}>
              {t('audwsCteAuthority')}
            </div>
            <ul style={{ margin: 0, paddingInlineStart: 18, fontSize: 12, color: 'var(--ink-1)', lineHeight: 1.6 }}>
              {(lang === 'ar' ? AUDIT_COMMITTEE_CHARTER.authorityAr : AUDIT_COMMITTEE_CHARTER.authorityEn).map((a, i) => (
                <li key={i}>{a}</li>
              ))}
            </ul>
          </div>
          <div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 700, marginBottom: 8 }}>
              {t('audwsCteResponsibilities')}
            </div>
            <ul style={{ margin: 0, paddingInlineStart: 18, fontSize: 12, color: 'var(--ink-1)', lineHeight: 1.6 }}>
              {(lang === 'ar' ? AUDIT_COMMITTEE_CHARTER.responsibilitiesAr : AUDIT_COMMITTEE_CHARTER.responsibilitiesEn).map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          </div>
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 10 }}>
          {t('audwsCteRules')}: ≥{AUDIT_COMMITTEE_CHARTER.minMembers} {t('audwsCteMembersWord')} · ≥{Math.round(AUDIT_COMMITTEE_CHARTER.minIndependentRatio * 100)}% {t('audwsCteIndependentWord')} · ≥{AUDIT_COMMITTEE_CHARTER.minMeetingsPerYear} {t('audwsCteMeetingsPerYear')}
        </div>
      </Card>

      {/* Meetings */}
      <Card title={`${t('audwsCteMeetingsTitle')} (${AUDIT_COMMITTEE_MEETINGS.length})`} Icon={CalendarDays}>
        {[...AUDIT_COMMITTEE_MEETINGS].sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt)).map(m => (
          <div key={m.id} style={{ padding: '10px 0', borderBottom: '1px dashed var(--border)' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '160px 110px 1fr 110px 130px', gap: 10, alignItems: 'center' }}>
              <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{m.meetingCode}</span>
              <span className="mono" style={{ fontSize: 11, color: 'var(--ink-2)' }}>{m.scheduledAt.replace('T', ' ').slice(0, 16)}</span>
              <div>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                  {lang === 'ar' ? m.titleAr : m.titleEn}
                </div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                  {m.attendeeCodes.length} {t('audwsCteAttendees')}
                  {m.minuteRef ? ' · ' + m.minuteRef : ''}
                </div>
              </div>
              <Badge tone={MEETING_STATUS_TONE[m.status]} dot>{m.status.replace(/_/g, ' ')}</Badge>
              <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{m.agenda.length} {t('audwsCteAgendaItems')}</span>
            </div>
            {m.agenda.length > 0 && (
              <div style={{ marginTop: 6, marginInlineStart: 170, fontSize: 11, color: 'var(--ink-2)' }}>
                {m.agenda.map((a, i) => (
                  <div key={i} style={{ padding: '4px 0', borderTop: i > 0 ? '1px dashed var(--border)' : 'none' }}>
                    <div style={{ fontWeight: 600, color: 'var(--ink-1)' }}>
                      {i + 1}. {lang === 'ar' ? a.titleAr : a.titleEn}
                      {a.ref && ' · ' + a.ref}
                    </div>
                    {(a.decisionEn ?? a.decisionAr) && (
                      <div style={{ marginTop: 2, fontStyle: 'italic', color: 'var(--ink-3)' }}>
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

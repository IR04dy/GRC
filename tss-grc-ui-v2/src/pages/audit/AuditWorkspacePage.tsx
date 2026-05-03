import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
  Briefcase, ClipboardCheck, FileCheck2, BookCopy,
  Stamp, Clock, ArrowRight, Flame, ShieldCheck, ScrollText,
  Activity, BarChart3,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
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
  const { t } = useI18n();

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

  return (
    <Shell title={t('audwsTitle')} sub={t('audwsSub')}>
      <PageHeader
        eyebrow={t('audwsEyebrow')}
        title={t('audwsHeader')}
        sub={t('audwsHeaderSub')}
      />

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
    </Shell>
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

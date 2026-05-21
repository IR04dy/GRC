import { useEffect, useMemo, useState } from 'react';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Link } from 'react-router-dom';
import {
  Inbox, ShieldCheck, Clock, AlertTriangle, Flame, Landmark,
  Check, X as XIcon, UserPlus, ArrowRight, ClipboardList,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  APPROVAL_REQUESTS, APPROVER_REGISTRY, APPROVAL_MODE_LABEL,
  pendingApprovers, approvalProgress, synthesiseApprovalWorkflows,
  type ApprovalRequest, type ApproverRole,
} from '../../lib/approvals';
import { fireFromApproval } from '../../lib/workflowChains';
import { deriveAllLive, type LiveWorkflowInstance } from '../../lib/sla';
import { ESCALATION_TONE } from '../../lib/workflows';
import { SlaCountdown } from '../../components/workflows/SlaCountdown';

/**
 * Approval Inbox — Sprint 5 cross-module approval queue.
 *
 * Generic primitive every module's "send for approval" path lands in.
 * Routing modes: single, parallel-any, parallel-all, sequential.
 * Each request has a Saudi regulator citation + an SLA tracked by the
 * Sprint 4 SLA engine. Decisions update the request in-place; sequential
 * routing surfaces the next approver automatically.
 *
 * KPI strip at top (total / due ≤5 / overdue / esc), then a two-column
 * layout: pending requests on the left (sorted by urgency), recent
 * decisions on the right.
 */
export function ApprovalInboxPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [requests, setRequests] = useState<ApprovalRequest[]>(APPROVAL_REQUESTS);
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(id);
  }, []);

  // Re-derive everything when requests change.
  const live: LiveWorkflowInstance[] = useMemo(
    () => deriveAllLive(synthesiseApprovalWorkflows(requests), undefined, now),
    [requests, now],
  );
  const liveById = useMemo(() => new Map(live.map(l => [l.id, l])), [live]);

  const pending = requests.filter(r => r.status === 'PENDING');
  const decided = requests.filter(r => r.status !== 'PENDING')
    .sort((a, b) => new Date(b.raisedAt).getTime() - new Date(a.raisedAt).getTime())
    .slice(0, 6);

  const dueSoon = live.filter(l => l.sla.tier === 'AT_RISK').length;
  const overdue = live.filter(l => l.sla.tier === 'OVERDUE').length;
  const escalated = live.filter(l => l.sla.tier === 'MANAGER_ESC' || l.sla.tier === 'DIRECTOR_ESC' || l.sla.tier === 'EXEC_ESC').length;

  // Sort pending by escalation severity, then by hoursRemaining ascending.
  const sortedPending = useMemo(() => {
    const tierOrder: Record<string, number> = {
      EXEC_ESC: 0, DIRECTOR_ESC: 1, MANAGER_ESC: 2, OVERDUE: 3, AT_RISK: 4, ON_TRACK: 5,
    };
    return [...pending].sort((a, b) => {
      const la = liveById.get(a.id);
      const lb = liveById.get(b.id);
      if (!la || !lb) return 0;
      const order = tierOrder[la.sla.tier] - tierOrder[lb.sla.tier];
      if (order !== 0) return order;
      return la.sla.daysRemaining - lb.sla.daysRemaining;
    });
  }, [pending, liveById]);

  // ─── Decision actions ────────────────────────────────────────────────
  // Why: compute the updated request OUTSIDE the React state updater so the
  // chain-fire side-effect doesn't depend on closure-capture inside a
  // potentially-replayed updater function (strict-mode + concurrent-mode
  // safety). The setter then becomes a pure id-match swap.
  function decide(req: ApprovalRequest, role: ApproverRole, decision: 'APPROVED' | 'REJECTED') {
    if (!pendingApprovers(req).includes(role)) return;

    const newDecisions = [...req.decisions, { role, decision, at: new Date().toISOString() }];
    let newStatus = req.status;
    if (decision === 'REJECTED') {
      newStatus = 'REJECTED';
    } else {
      const stillNeeded =
        req.mode === 'SEQUENTIAL'
          ? req.approverRoles.filter(r2 => !newDecisions.some(d => d.role === r2 && d.decision === 'APPROVED'))
          : req.mode === 'PARALLEL_ANY'
            ? []
            : req.approverRoles.filter(r2 => !newDecisions.some(d => d.role === r2 && d.decision === 'APPROVED'));
      if (stillNeeded.length === 0) newStatus = 'APPROVED';
    }
    const updated: ApprovalRequest = { ...req, decisions: newDecisions, status: newStatus };

    setRequests(prev => prev.map(r => r.id === req.id ? updated : r));

    const profile = APPROVER_REGISTRY[role];
    toast.success(
      decision === 'APPROVED'
        ? t('approvalToastApproved').replace('{role}', lang === 'ar' ? profile.titleAr : profile.titleEn)
        : t('approvalToastRejected').replace('{role}', lang === 'ar' ? profile.titleAr : profile.titleEn),
    );

    // Sprint 5 — when an approval moves to APPROVED, fire downstream chain
    // rules. The user sees a second toast with what was spawned.
    if (newStatus === 'APPROVED') {
      const fires = fireFromApproval(updated);
      if (fires.length > 0) {
        toast.info(t('approvalToastChainFired').replace('{n}', String(fires.length)));
      }
    }
  }

  function delegate(req: ApprovalRequest, role: ApproverRole) {
    const profile = APPROVER_REGISTRY[role];
    if (!profile.delegateRole) {
      toast.danger(t('approvalDelegateNoCover'));
      return;
    }
    const delegate = APPROVER_REGISTRY[profile.delegateRole];
    setRequests(prev => prev.map(r => {
      if (r.id !== req.id) return r;
      // Replace the role in approverRoles + record a DELEGATED decision.
      return {
        ...r,
        approverRoles: r.approverRoles.map(x => x === role ? profile.delegateRole! : x),
        decisions: [...r.decisions, { role, decision: 'DELEGATED', delegateTo: profile.delegateRole, at: new Date().toISOString() }],
      };
    }));
    toast.success(t('approvalToastDelegated').replace('{from}', lang === 'ar' ? profile.titleAr : profile.titleEn).replace('{to}', lang === 'ar' ? delegate.titleAr : delegate.titleEn));
  }

  return (
    <Shell title={t('approvalsTitle')} sub={t('approvalsSub')}>
      <PageHeader
        eyebrow={t('approvalsEyebrow')}
        title={t('approvalsTitle')}
        sub={t('approvalsPageSub')}
        actions={
          // Wizards (PRs #11-21) go through their own register, but a
          // reviewer landing here also wants the SUBMITTED / UNDER_REVIEW
          // workflow submissions queue. Cross-link rather than duplicate
          // the rows — different schema (approval_requests vs
          // workflow_submissions), same reviewer mindset.
          <Link to="/workflows/submissions">
            <Button variant="secondary" size="sm" Icon={ClipboardList}>
              {t('approvalsWorkflowSubmissionsLink')}
            </Button>
          </Link>
        }
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Inbox}        label={t('approvalsKpiPending')}    value={String(pending.length)}    tone="primary" />
        <Kpi Icon={Clock}         label={t('approvalsKpiDueSoon')}    value={String(dueSoon)}            tone="info" />
        <Kpi Icon={AlertTriangle} label={t('approvalsKpiOverdue')}    value={String(overdue)}            tone="warning" />
        <Kpi Icon={Flame}         label={t('approvalsKpiEscalated')}  value={String(escalated)}          tone="danger" />
        <Kpi Icon={ShieldCheck}   label={t('approvalsKpiDecided')}    value={String(requests.length - pending.length)} tone="success" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 16 }}>
        {/* ── Pending queue ───────────────────────────────────── */}
        <Card title={t('approvalsPendingTitle')} Icon={Inbox}
          action={<Badge tone="primary">{pending.length}</Badge>}>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {sortedPending.length === 0
              ? <InlineEmpty>{t('approvalsEmpty')}</InlineEmpty>
              : sortedPending.map(req => (
                  <ApprovalRow key={req.id} req={req}
                    sla={liveById.get(req.id)}
                    lang={lang} t={t}
                    onApprove={role => decide(req, role, 'APPROVED')}
                    onReject={role => decide(req, role, 'REJECTED')}
                    onDelegate={role => delegate(req, role)} />
                ))}
          </div>
        </Card>

        {/* ── Recent decisions ─────────────────────────────────── */}
        <Card title={t('approvalsRecentTitle')} Icon={ShieldCheck}>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {decided.length === 0
              ? <InlineEmpty>{t('approvalsRecentEmpty')}</InlineEmpty>
              : decided.map(req => (
                  <div key={req.id} style={{
                    padding: '10px 0', borderBottom: '1px dashed var(--border)',
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {lang === 'ar' ? req.titleAr : req.titleEn}
                        </div>
                        <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>
                          {req.module} · {req.sourceRef}
                        </div>
                      </div>
                      <Badge tone={req.status === 'APPROVED' ? 'success' : req.status === 'REJECTED' ? 'danger' : 'neutral'}>
                        {req.status}
                      </Badge>
                    </div>
                  </div>
                ))}
          </div>
        </Card>
      </div>
    </Shell>
  );
}

function ApprovalRow({
  req, sla, lang, t, onApprove, onReject, onDelegate,
}: {
  req: ApprovalRequest;
  sla: LiveWorkflowInstance | undefined;
  lang: 'en' | 'ar';
  t: (k: string) => string;
  onApprove: (role: ApproverRole) => void;
  onReject: (role: ApproverRole) => void;
  onDelegate: (role: ApproverRole) => void;
}) {
  const next = pendingApprovers(req);
  const progress = approvalProgress(req);
  const sevTier = sla?.sla.tier ?? 'ON_TRACK';
  const tone = ESCALATION_TONE[sevTier];

  return (
    <div style={{
      padding: '14px 0',
      borderBottom: '1px dashed var(--border)',
    }}>
      {/* Top row — title, mode, SLA */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
        <div style={{ minWidth: 0 }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <span style={{ fontSize: 14, fontWeight: 700, color: 'var(--ink-1)' }}>
              {lang === 'ar' ? req.titleAr : req.titleEn}
            </span>
            <Badge tone="neutral">{lang === 'ar' ? APPROVAL_MODE_LABEL[req.mode].ar : APPROVAL_MODE_LABEL[req.mode].en}</Badge>
            <Badge tone="primary">{req.module}</Badge>
            <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{req.sourceRef}</span>
          </div>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 4, lineHeight: 1.5 }}>
            {lang === 'ar' ? req.summaryAr : req.summaryEn}
          </div>
          {req.regulatorCitation && (
            <div style={{ display: 'inline-flex', gap: 4, alignItems: 'center', marginTop: 6, fontSize: 11, color: 'var(--ink-3)' }}>
              <Landmark size={11} /> {req.regulatorCitation}
            </div>
          )}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4, flexShrink: 0 }}>
          {sla && <SlaCountdown instance={sla} />}
          <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
            {progress.decided}/{progress.total} {t('approvalProgressLabel')}
          </span>
        </div>
      </div>

      {/* Approver chain */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 10, paddingInlineStart: 12, borderInlineStart: `2px solid var(--${tone}-fg, var(--border))` }}>
        {req.approverRoles.map((role, idx) => {
          const profile = APPROVER_REGISTRY[role];
          const decision = req.decisions.find(d => d.role === role);
          const isCurrent = next.includes(role);
          return (
            <div key={`${role}-${idx}`} style={{
              display: 'grid', gridTemplateColumns: '1fr auto', gap: 8, alignItems: 'center',
              padding: '6px 8px',
              borderRadius: 'var(--r-sm)',
              background: isCurrent ? 'var(--warning-bg)' : 'transparent',
            }}>
              <div>
                <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)' }}>
                  {lang === 'ar' ? profile.titleAr : profile.titleEn}
                </div>
                <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                  {lang === 'ar' ? profile.nameAr : profile.nameEn}
                  {decision?.commentEn && <> · {decision.commentEn}</>}
                </div>
              </div>
              <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                {decision
                  ? (
                    <Badge tone={
                      decision.decision === 'APPROVED' ? 'success'
                      : decision.decision === 'REJECTED' ? 'danger'
                      : 'warning'
                    }>{decision.decision}</Badge>
                  )
                  : isCurrent
                    ? (
                      <>
                        <Button variant="primary" Icon={Check} onClick={() => onApprove(role)}>{t('approvalActApprove')}</Button>
                        <Button variant="secondary" Icon={XIcon} onClick={() => onReject(role)}>{t('approvalActReject')}</Button>
                        {profile.delegateRole && (
                          <Button variant="secondary" Icon={UserPlus} onClick={() => onDelegate(role)}>{t('approvalActDelegate')}</Button>
                        )}
                      </>
                    )
                    : <Badge tone="neutral">{t('approvalAwaiting')}</Badge>
                }
              </div>
            </div>
          );
        })}
      </div>

      {req.href && (
        <div style={{ marginTop: 8 }}>
          <Link to={req.href} style={{ fontSize: 11, color: 'var(--primary)', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            {t('approvalOpenSource')} <ArrowRight size={11} />
          </Link>
        </div>
      )}
    </div>
  );
}

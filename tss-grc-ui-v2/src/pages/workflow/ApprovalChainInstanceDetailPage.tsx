import { useMemo } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  GitBranch, Clock, ArrowLeft, AlertTriangle, MessageSquare, UserCircle2,
  CheckCircle2, XCircle, Users, ListChecks,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import {
  SEED_INSTANCES, SEED_TEMPLATES, STATUS_LABEL, ACTION_LABEL, STEP_TYPE_LABEL,
  statusTone, actionTone,
  type ChainInstance, type ChainTemplate, type ChainStep,
} from '../../lib/approvalChains';

/**
 * Sprint 13 — Approval-chain instance detail with action timeline.
 *
 * <p>One canonical screen per in-flight (or completed) chain. Shows
 * the originator, the source entity, the ladder with current step
 * highlighted, and a chronological timeline of every actor decision
 * (APPROVED / REJECTED / DELEGATED / REQUESTED_CHANGES / RECUSED) plus
 * automatic ESCALATION_NOTE entries from the SLA sweeper.
 */
export function ApprovalChainInstanceDetailPage() {
  const { t, lang } = useI18n();
  const { id } = useParams<{ id: string }>();
  const instanceId = Number(id);

  const instance: ChainInstance | undefined = useMemo(
    () => SEED_INSTANCES.find(i => i.id === instanceId),
    [instanceId],
  );
  const template: ChainTemplate | undefined = useMemo(
    () => instance ? SEED_TEMPLATES.find(tpl => tpl.id === instance.templateId) : undefined,
    [instance],
  );

  if (!instance || !template) {
    return (
      <Shell title={t('approvalChainDetailTitle', 'Approval chain')}
             sub={t('approvalChainDetailSub', 'Action timeline')}>
        <PageHeader
          eyebrow={t('approvalChainEyebrow', 'Approval Chains · Sprint 13')}
          title={t('approvalChainDetailTitle', 'Approval chain')}
          sub={t('approvalChainNotFound', 'No chain instance found at this address.')}
          actions={<Link to="/workflows/chains/templates"><span className="btn btn--secondary btn--sm" style={{ display: 'inline-flex', gap: 4, alignItems: 'center' }}><ArrowLeft size={14} />{t('approvalChainBackToTemplates', 'Back to templates')}</span></Link>}
        />
        <Card>
          <InlineEmpty>{t('approvalChainNotFoundLong', 'The instance you requested is not in the demo dataset.')}</InlineEmpty>
        </Card>
      </Shell>
    );
  }

  const title = lang === 'ar' ? (instance.titleAr ?? template.titleAr) : (instance.titleEn ?? template.titleEn);
  const tone = statusTone(instance.status);

  // Group steps by step_order for parallel-aware rendering.
  const orders = Array.from(new Set(template.steps.map(s => s.stepOrder))).sort((a, b) => a - b);

  // Compute "actions per step" so the ladder can show progress.
  const actionsByStepId = useMemo(() => {
    const map = new Map<number, typeof instance.actions>();
    for (const a of instance.actions) {
      const existing = map.get(a.stepId) ?? [];
      existing.push(a);
      map.set(a.stepId, existing);
    }
    return map;
  }, [instance.actions]);

  return (
    <Shell title={t('approvalChainDetailTitle', 'Approval chain')} sub={template.templateCode}>
      <PageHeader
        eyebrow={t('approvalChainEyebrow', 'Approval Chains · Sprint 13')}
        title={title ?? template.templateCode}
        sub={template.templateCode + ' · ' + (instance.sourceEntityType ?? '') +
          (instance.sourceEntityId ? ' #' + instance.sourceEntityId : '')}
        actions={
          <Link to="/workflows/chains/templates">
            <span className="btn btn--secondary btn--sm" style={{ display: 'inline-flex', gap: 4, alignItems: 'center' }}>
              <ArrowLeft size={14} />
              {t('approvalChainBackToTemplates', 'Back to templates')}
            </span>
          </Link>
        }
      />

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
        <Card title={t('approvalChainDetailStatus', 'Status')} Icon={GitBranch}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <Badge tone={tone}>{lang === 'ar' ? STATUS_LABEL[instance.status].ar : STATUS_LABEL[instance.status].en}</Badge>
              <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>
                {t('approvalChainCurrentStep', 'Current step')}: <b>{instance.currentStepOrder}</b>
              </span>
            </div>
            <div style={{ fontSize: 12, color: 'var(--ink-2)' }}>
              <UserCircle2 size={12} style={{ marginInlineEnd: 4, verticalAlign: 'middle' }} />
              {t('approvalChainOriginator', 'Originator')}:{' '}
              <b>{lang === 'ar' ? (instance.originatorNameAr ?? '') : (instance.originatorNameEn ?? '')}</b>{' '}
              <span className="mono" style={{ color: 'var(--ink-3)' }}>#{instance.originatorUserId}</span>
            </div>
            <div style={{ fontSize: 12, color: 'var(--ink-2)' }}>
              <Clock size={12} style={{ marginInlineEnd: 4, verticalAlign: 'middle' }} />
              {t('approvalChainStarted', 'Started')}: {new Date(instance.startedAt).toLocaleString()}
            </div>
            {instance.completedAt && (
              <div style={{ fontSize: 12, color: 'var(--ink-2)' }}>
                <CheckCircle2 size={12} style={{ marginInlineEnd: 4, verticalAlign: 'middle' }} />
                {t('approvalChainCompleted', 'Completed')}: {new Date(instance.completedAt).toLocaleString()}
              </div>
            )}
          </div>
        </Card>

        <Card title={t('approvalChainDetailSource', 'Source')} Icon={ListChecks}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 12 }}>
            {instance.sourceWorkflowSubmissionId != null && (
              <div>
                <b>{t('approvalChainSourceSubmission', 'Workflow submission')}:</b>{' '}
                <span className="mono">#{instance.sourceWorkflowSubmissionId}</span>
              </div>
            )}
            {instance.sourceEntityType && (
              <div>
                <b>{t('approvalChainSourceEntity', 'Source entity')}:</b>{' '}
                <span className="mono">{instance.sourceEntityType}</span>
                {instance.sourceEntityId != null && (
                  <span className="mono"> #{instance.sourceEntityId}</span>
                )}
              </div>
            )}
            <div>
              <b>{t('approvalChainTemplate', 'Template')}:</b>{' '}
              <span className="mono">{template.templateCode}</span>
            </div>
            {template.appliesToModule && (
              <div>
                <b>{t('approvalChainModule', 'Module')}:</b>{' '}
                <span className="mono">{template.appliesToModule}</span>
              </div>
            )}
          </div>
        </Card>
      </div>

      {/* ── Ladder with current step highlight ─────────────────────── */}
      <Card title={t('approvalChainLadderTitle', 'Ladder progress')} Icon={Users}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {orders.map(order => {
            const rows = template.steps.filter(s => s.stepOrder === order);
            const isParallel = rows[0]?.stepType === 'PARALLEL';
            const isCurrent = order === instance.currentStepOrder
              && instance.status !== 'APPROVED' && instance.status !== 'REJECTED'
              && instance.status !== 'WITHDRAWN' && instance.status !== 'EXPIRED';
            const isPast = order < instance.currentStepOrder
              || instance.status === 'APPROVED';
            return (
              <div key={order} style={{
                border: '1px solid var(--border)',
                borderRadius: 'var(--r-sm)',
                padding: '10px 12px',
                background: isCurrent
                  ? 'var(--warning-bg)'
                  : isPast
                    ? 'var(--success-bg, var(--surface))'
                    : 'var(--surface)',
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 600 }}>
                  <Badge tone="accent">#{order}</Badge>
                  <Badge tone={isParallel ? 'accent' : 'neutral'}>
                    {lang === 'ar' ? STEP_TYPE_LABEL[rows[0].stepType].ar : STEP_TYPE_LABEL[rows[0].stepType].en}
                  </Badge>
                  {isCurrent && (
                    <Badge tone="warning">{t('approvalChainStepCurrent', 'Current')}</Badge>
                  )}
                  {isPast && !isCurrent && (
                    <Badge tone="success">{t('approvalChainStepDone', 'Complete')}</Badge>
                  )}
                </div>
                <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {rows.map(s => (
                    <LadderStepRow key={s.id}
                      step={s}
                      actions={actionsByStepId.get(s.id) ?? []}
                      lang={lang} t={t} />
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      </Card>

      {/* ── Timeline ──────────────────────────────────────────────── */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('approvalChainTimelineTitle', 'Action timeline')} Icon={Clock}>
          {instance.actions.length === 0
            ? <InlineEmpty>{t('approvalChainTimelineEmpty', 'No actions recorded yet — the chain is waiting on the first approver.')}</InlineEmpty>
            : (
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                {instance.actions.slice().sort((a, b) => new Date(a.decidedAt).getTime() - new Date(b.decidedAt).getTime()).map(a => {
                  const step = template.steps.find(s => s.id === a.stepId);
                  const stepLabel = step ? (lang === 'ar' ? step.titleAr : step.titleEn) : '';
                  return (
                    <div key={a.id} style={{
                      padding: '12px 0',
                      borderBottom: '1px dashed var(--border)',
                      display: 'grid',
                      gridTemplateColumns: 'auto 1fr auto',
                      gap: 10,
                      alignItems: 'flex-start',
                    }}>
                      <div style={{
                        background: 'var(--surface-2, var(--surface))',
                        borderRadius: '50%',
                        width: 28, height: 28,
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        flexShrink: 0,
                      }}>
                        {a.action === 'APPROVED' ? <CheckCircle2 size={16} color="var(--success-fg, var(--success))" />
                          : a.action === 'REJECTED' ? <XCircle size={16} color="var(--danger-fg, var(--danger))" />
                          : a.action === 'ESCALATION_NOTE' ? <AlertTriangle size={16} color="var(--warning-fg, var(--warning))" />
                          : <MessageSquare size={16} color="var(--ink-3)" />}
                      </div>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                          <Badge tone={actionTone(a.action)}>
                            {lang === 'ar' ? ACTION_LABEL[a.action].ar : ACTION_LABEL[a.action].en}
                          </Badge>
                          <span style={{ fontWeight: 600, fontSize: 13 }}>
                            {lang === 'ar' ? (a.actorNameAr ?? '') : (a.actorNameEn ?? '')}
                            {(!a.actorNameAr && !a.actorNameEn) && (
                              <span className="mono" style={{ color: 'var(--ink-3)' }}>#{a.actorUserId}</span>
                            )}
                          </span>
                          <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                            {t('approvalChainStepLabel', 'Step')} {a.stepOrder} · {stepLabel}
                          </span>
                        </div>
                        {(a.commentEn || a.commentAr) && (
                          <div style={{ marginTop: 6, fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.5 }}>
                            {lang === 'ar' ? (a.commentAr ?? '') : (a.commentEn ?? '')}
                          </div>
                        )}
                        {a.delegatedToUserId != null && (
                          <div style={{ marginTop: 4, fontSize: 11, color: 'var(--ink-3)' }}>
                            {t('approvalChainTimelineDelegatedTo', 'Delegated to user')}:{' '}
                            <span className="mono">#{a.delegatedToUserId}</span>
                          </div>
                        )}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)', whiteSpace: 'nowrap' }}>
                        {new Date(a.decidedAt).toLocaleString()}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
        </Card>
      </div>
    </Shell>
  );
}

function LadderStepRow({
  step, actions, lang, t,
}: {
  step: ChainStep;
  actions: { action: string; actorUserId: number; actorNameEn?: string; actorNameAr?: string }[];
  lang: 'en' | 'ar';
  t: (key: string, fallback?: string) => string;
}) {
  const approved = actions.find(a => a.action === 'APPROVED');
  const rejected = actions.find(a => a.action === 'REJECTED');
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '1fr auto',
      gap: 10,
      alignItems: 'center',
      padding: '6px 8px',
      borderInlineStart: '3px solid var(--primary-fg, var(--primary))',
      borderRadius: 'var(--r-sm)',
      background: 'var(--surface)',
    }}>
      <div>
        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
          {lang === 'ar' ? step.titleAr : step.titleEn}
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
          {step.requiredRole && <><b>{t('approvalChainStepRole', 'Role')}:</b> <span className="mono">{step.requiredRole}</span></>}
          {step.slaHours != null && <span style={{ marginInlineStart: 8 }}><b>{t('approvalChainStepSla', 'SLA')}:</b> {step.slaHours}h</span>}
        </div>
      </div>
      <div style={{ display: 'flex', gap: 4 }}>
        {rejected
          ? <Badge tone="danger">{t('approvalChainRejected', 'Rejected')}</Badge>
          : approved
            ? <Badge tone="success">{t('approvalChainApproved', 'Approved')}</Badge>
            : <Badge tone="neutral">{t('approvalChainPending', 'Pending')}</Badge>}
      </div>
    </div>
  );
}

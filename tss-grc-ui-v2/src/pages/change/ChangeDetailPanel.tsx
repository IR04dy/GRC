/**
 * S5c — the change record: status advance, CAB votes, committee referral, tasks, links,
 * comments and schedule conflicts (§11 `ChangeDetailPage`, still a tab until CE-13).
 *
 * <p>The largest panel of the workspace and the reason the split was worth doing: at
 * 1,036 lines the old file put this, the register, the board, the KPI strip and the link
 * editor in one component, so none of them could be reasoned about alone.
 */
import { useEffect, useMemo, useState } from 'react';
import {
  GitBranch, AlertTriangle, Clock, Send, Wrench, Stamp, CheckCheck, ArrowRight,
  Activity, ListChecks, MessageSquare, Plus, Trash2, FileDown, X as XIcon,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { FormModal } from '../../components/ui/FormModal';
import {
  changeApi, reportingApi, ApiError,
  type ChangeRequestDto, type ChangeApprovalDto, type ChangeTaskDto,
  type ChangeCommentDto, type ChangeConflictView, type ChangeReferralView,
} from '../../lib/api';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { getUser, canExport } from '../../lib/auth';
import { STATUS_TONE, TYPE_TONE, RISK_TONE } from './changeVocabulary';
import { mayTransitionTo, nextStatus } from './changeLifecycle';
import { Field, Block } from './ChangeFields';
import { ConditionsCard } from './ChangeConditionsCard';
import { ChangeEvidenceCard } from './ChangeEvidence';
import { PirCard } from './ChangePirCard';

// ─── Workflow detail panel ─────────────────────────────────────────────────
export function WorkflowPanel({ items, detailId, setDetailId, onTransition, onChanged, t, lang, toast }: {
  items: ChangeRequestDto[];
  detailId: number | null; setDetailId: (id: number | null) => void;
  onTransition: (c: ChangeRequestDto, target: string, notes?: string, outcome?: string) => void | Promise<void>;
  onChanged: () => void | Promise<void>;
  t: (k: string) => string; lang: string;
  toast: ReturnType<typeof useToast>;
}) {
  const change = items.find(c => c.id === detailId) ?? null;
  const [approvals, setApprovals] = useState<ChangeApprovalDto[]>([]);
  const [tasks, setTasks] = useState<ChangeTaskDto[]>([]);
  const [comments, setComments] = useState<ChangeCommentDto[]>([]);
  const [conflicts, setConflicts] = useState<ChangeConflictView[]>([]);
  const [referral, setReferral] = useState<ChangeReferralView | null>(null);   // Track D
  const [busyRef, setBusyRef] = useState(false);
  const [commentBody, setCommentBody] = useState('');
  const dialog = useDialog();
  // CH-8: real themed modals replace the old window.prompt / window.confirm flows.
  const [voteDecision, setVoteDecision] = useState<'APPROVE' | 'REJECT' | 'DEFER' | 'ABSTAIN' | null>(null);
  const [taskOpen, setTaskOpen] = useState(false);
  const voteInitial = useMemo(() => ({ approverName: getUser()?.username ?? '', approverRole: '', comments: '' }), [voteDecision]);
  const taskInitial = useMemo(() => ({ titleEn: '', titleAr: '', ownerName: '', dueDate: '' }), []);

  async function reload() {
    if (!change) { setApprovals([]); setTasks([]); setComments([]); setConflicts([]); setReferral(null); return; }
    try {
      const [a, k, c, cf, ref] = await Promise.all([
        changeApi.approvals(change.id),
        changeApi.tasks(change.id),
        changeApi.comments(change.id),
        changeApi.conflicts(change.id),
        changeApi.committeeReferral(change.id).catch(() => null),
      ]);
      setApprovals(a); setTasks(k); setComments(c); setConflicts(cf);
      setReferral(ref && (ref.referralId || ref.id) ? ref : null);
    } catch { /* silent */ }
  }
  useEffect(() => { void reload(); /* eslint-disable-next-line */ }, [detailId, items]);

  // Track D: route this change's CAB decision through a committee.
  async function referToCommittee() {
    setBusyRef(true);
    try { await changeApi.referToCommittee(change!.id); toast.success(lang === 'ar' ? 'تم التوجيه إلى اللجنة' : 'Referred to committee'); void reload(); }
    catch (e) { toast.danger(e instanceof Error ? e.message : 'Action failed'); }
    finally { setBusyRef(false); }
  }

  if (!change) {
    return (
      <Card title={t('changeTabWorkflow')}>
        <EmptyState Icon={ListChecks} title={t('changeWorkflowSelectTitle')} sub={t('changeWorkflowSelectSub')} />
      </Card>
    );
  }

  function castVote(decision: 'APPROVE' | 'REJECT' | 'DEFER' | 'ABSTAIN') {
    setVoteDecision(decision);
  }
  async function submitVote(v: { approverName: string; approverRole: string; comments: string }) {
    if (!voteDecision) return;
    try {
      await changeApi.approve(change!.id, {
        approverName: v.approverName, approverRole: v.approverRole,
        decision: voteDecision, comments: v.comments,
      });
      toast.success(t('changeVoteRecorded'));
      setVoteDecision(null);
      void reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  function addTask() { setTaskOpen(true); }
  // CH-5 captures owner + due date so the SLA scheduler can act on the task;
  // CH-8 moves that capture out of chained window.prompt calls into a real modal.
  async function submitTask(v: { titleEn: string; titleAr: string; ownerName: string; dueDate: string }) {
    const dueAt = /^\d{4}-\d{2}-\d{2}$/.test(v.dueDate) ? `${v.dueDate}T17:00:00` : undefined;
    try {
      await changeApi.addTask(change!.id, { titleEn: v.titleEn, titleAr: v.titleAr, status: 'PENDING',
        ownerName: v.ownerName || undefined, dueAt });
      setTaskOpen(false);
      void reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function flipTask(task: ChangeTaskDto) {
    const next = task.status === 'PENDING' ? 'IN_PROGRESS' : task.status === 'IN_PROGRESS' ? 'DONE' : 'PENDING';
    try { await changeApi.updateTask(task.id!, { status: next }); void reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function deleteTask(task: ChangeTaskDto) {
    const ok = await dialog.confirm({ title: t('deleteBtn'), body: t('changeTaskDeleteConfirm'),
      destructive: true, submitLabel: t('deleteBtn') });
    if (!ok) return;
    try { await changeApi.deleteTask(task.id!); void reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function postComment() {
    if (!commentBody.trim()) return;
    try {
      // F2-248 — the author is not sent at all. The server takes it from the authenticated
      // principal, and `ChangeCommentCreateDto` has no field for it.
      //
      // ⚠ This line used to read `{ authorName: getUser()?.username ?? 'You', ... }` under a
      // comment claiming "CH-8: attribute the comment to the signed-in user, not a hardcoded
      // 'You'" — and the `?? 'You'` fallback it kept is in the PROD register, on a real row. A
      // comment that says the placeholder was removed, above the line that still supplies it,
      // is worse than no comment (F2-184).
      await changeApi.comment(change!.id, { body: commentBody });
      setCommentBody(''); void reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const next = nextStatus(change);

  return (
    <div className="changes-workflow-grid">
      <Card title={t('changeWorkflowList')} Icon={ListChecks} padded={false}>
        <div className="changes-list-pane">
          {items.map(c => (
            <button key={c.id} onClick={() => setDetailId(c.id)}
              className={`changes-list-item${c.id === detailId ? ' is-selected' : ''}`}>
              <div className="changes-list-item__head">
                <span className="mono">{c.changeCode ?? `#${c.id}`}</span>
                <Badge tone={STATUS_TONE[c.status] ?? 'neutral'} dot>{c.status.replace(/_/g, ' ')}</Badge>
              </div>
              <div className="changes-list-item__title">
                {lang === 'ar' && c.titleAr ? c.titleAr : c.titleEn}
              </div>
            </button>
          ))}
        </div>
      </Card>

      <div className="changes-detail-stack">
        <Card title={`${change.changeCode ?? '#' + change.id} · ${lang === 'ar' && change.titleAr ? change.titleAr : change.titleEn}`}
              Icon={GitBranch}
              action={<>
                <Badge tone={STATUS_TONE[change.status] ?? 'neutral'} dot>{change.status.replace(/_/g, ' ')}</Badge>
                <Badge tone={TYPE_TONE[change.changeType] ?? 'neutral'}>{change.changeType}</Badge>
                {change.riskLevel && <Badge tone={RISK_TONE[change.riskLevel] ?? 'neutral'}>{change.riskLevel}</Badge>}
              </>}>
          <div className="changes-detail-grid">
            <Field label={t('changeFldRequestedBy')} value={change.requestedByName} />
            <Field label={t('changeFldOwner')}       value={change.changeOwnerName} />
            <Field label={t('changeFldImplementer')} value={change.implementerName} />
            <Field label={t('changeFldCategory')}    value={change.category} />
            <Field label={t('changeFldScheduledStart')} value={change.scheduledStart ? new Date(change.scheduledStart).toLocaleString() : '—'} />
            <Field label={t('changeFldScheduledEnd')}   value={change.scheduledEnd   ? new Date(change.scheduledEnd).toLocaleString()   : '—'} />
          </div>
          <div className="changes-blocks">
            {change.descriptionEn && <Block label={t('changeFldDescription')} body={change.descriptionEn} />}
            {change.businessJustification && <Block label={t('changeFldJustification')} body={change.businessJustification} />}
            {change.impactSummary && <Block label={t('changeFldImpact')} body={change.impactSummary} />}
            {change.implementationPlan && <Block label={t('changeFldImplPlan')} body={change.implementationPlan} />}
            {change.rollbackPlan  && <Block label={t('changeFldRollback')} body={change.rollbackPlan} />}
            {change.testPlan      && <Block label={t('changeFldTestPlan')} body={change.testPlan} />}
          </div>

          <div className="changes-actions-row">
            {next && <Button variant="primary" Icon={ArrowRight} disabled={!mayTransitionTo(next)}
                             title={!mayTransitionTo(next) ? t('changeNeedsApprovePerm') : undefined}
                             onClick={() => onTransition(change, next)}>{t('changeAdvanceTo')} {next.replace(/_/g, ' ')}</Button>}
            {change.status === 'CAB_PENDING' && (
              <>
                <Button variant="secondary" Icon={CheckCheck} onClick={() => castVote('APPROVE')}>{t('changeVoteApprove')}</Button>
                <Button variant="ghost"     Icon={XIcon}      onClick={() => castVote('REJECT')}>{t('changeVoteReject')}</Button>
                <Button variant="ghost"     Icon={Clock}      onClick={() => castVote('DEFER')}>{t('changeVoteDefer')}</Button>
              </>
            )}
            {change.status === 'IMPLEMENTING' && (
              <Button variant="ghost" Icon={Wrench} onClick={() => onTransition(change, 'ROLLED_BACK')}>{t('changeRollback')}</Button>
            )}
{/* F2-27: same gate as the register menu — the CAB pack is a CHANGE:EXPORT artefact. */}
            {canExport('CHANGE') && (
              <Button variant="secondary" Icon={FileDown} onClick={async () => {
                try { await reportingApi.cabPackPdf(change!.id, lang); }
                catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Export failed'); }
              }}>{t('changeCabPack')}</Button>
            )}
          </div>
        </Card>

        {/* S8a/CE-9 — the change's own evidence: test results, back-out proof, implementation logs.
            Above the conditions register because a condition's artefact is usually one of these, and
            because G-9's complaint was about this change-level set. */}
        <ChangeEvidenceCard change={change} toast={toast} />

        {/* S7e/CE-5 — above the PIR, because a condition blocks implementation and the PIR
            comes after it. Reading order follows the lifecycle. */}
        <ConditionsCard change={change} onChanged={onChanged} toast={toast} />

        <PirCard change={change} onChanged={onChanged} toast={toast} />

        {conflicts.length > 0 && (
          <Card title={t('changeConflictsTitle')} Icon={AlertTriangle}>
            <div className="changes-conflicts-note">{t('changeConflictsSub')}</div>
            {conflicts.map(cf => (
              <div key={cf.id} className="changes-conflict-row">
                <span className="mono">{cf.changeCode ?? `#${cf.id}`}</span>
                <span className="changes-conflict-title">{cf.titleEn}</span>
                <span className="mono changes-conflict-when">
                  {cf.scheduledStart ? new Date(cf.scheduledStart).toLocaleDateString() : ''}
                </span>
                {cf.sameCategory && <Badge tone="warning">{t('changeConflictSameCat')}</Badge>}
                {cf.sharedEntities && cf.sharedEntities.length > 0 &&
                  <Badge tone="danger">{t('changeConflictShared')}: {cf.sharedEntities.length}</Badge>}
              </div>
            ))}
          </Card>
        )}

        <Card title={lang === 'ar' ? 'التوجيه إلى اللجنة (CAB/ECAB)' : 'Committee routing (CAB/ECAB)'}>
          {referral ? (
            <div className="changes-gov-sub" style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <div>
                {lang === 'ar' ? 'موجّه إلى اللجنة' : 'Routed to committee'} <span className="mono">#{referral.committeeId}</span>{' · '}
                <Badge tone="info">{referral.gate}</Badge>{' '}
                <Badge tone={referral.status === 'DECIDED' ? 'success' : 'neutral'}>{referral.status}</Badge>
                {referral.mode === 'RATIFICATION' && <Badge tone="warning">{lang === 'ar' ? 'مصادقة لاحقة' : 'ratification'}</Badge>}
              </div>
              {referral.resolutionId ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 3, fontSize: 12 }}>
                  <div>
                    {lang === 'ar' ? 'القرار' : 'Resolution'} <span className="mono">#{referral.resolutionId}</span>
                    {referral.resolutionOutcome && <>{' · '}<Badge tone={referral.resolutionOutcome === 'APPROVED' ? 'success' : referral.resolutionOutcome === 'REJECTED' ? 'danger' : 'warning'}>{referral.resolutionOutcome}</Badge></>}
                    {referral.voteFor != null && <span className="mono" style={{ color: 'var(--ink-3)' }}>{'  '}{lang === 'ar' ? 'مع' : 'for'} {referral.voteFor} · {lang === 'ar' ? 'ضد' : 'against'} {referral.voteAgainst} · {lang === 'ar' ? 'ممتنع' : 'abstain'} {referral.voteAbstain}</span>}
                  </div>
                  <div className="changes-gov-sub">
                    {lang === 'ar' ? 'الاجتماع' : 'Meeting'} {referral.meetingId ? <span className="mono">#{referral.meetingId}</span> : '—'}
                    {referral.meetingStatus && <>{' · '}{referral.meetingStatus.replace(/_/g, ' ')}</>}
                    {referral.quorumMet != null && <>{' · '}<Badge tone={referral.quorumMet ? 'success' : 'neutral'}>{referral.quorumMet ? (lang === 'ar' ? 'اكتمل النصاب' : 'quorum met') : (lang === 'ar' ? 'دون نصاب' : 'no quorum')}</Badge></>}
                  </div>
                </div>
              ) : <div className="changes-conflicts-note">{lang === 'ar' ? 'بانتظار رفع القرار في اجتماع اللجنة وربطه' : 'Awaiting the motion to be raised in a committee meeting and linked'}</div>}
            </div>
          ) : ['IMPACT_ASSESSED', 'CAB_PENDING'].includes(change.status) ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              <span className="changes-gov-sub">{lang === 'ar' ? 'وجّه قرار CAB إلى لجنة (نصاب + تصويت + محضر معتمد).' : 'Route the CAB decision to a committee (quorum + vote + approved minutes).'}</span>
              <Button size="sm" variant="primary" disabled={busyRef} onClick={referToCommittee}>
                {lang === 'ar' ? 'التوجيه إلى CAB/ECAB' : 'Refer to CAB/ECAB'}
              </Button>
            </div>
          ) : <div className="changes-conflicts-note">{lang === 'ar' ? 'لا ينطبق في هذه الحالة' : 'Not applicable in this state'}</div>}
        </Card>

        <Card title={t('changeApprovals')} Icon={Stamp}>
          {approvals.length === 0
            ? <div className="changes-empty-note">{t('changeNoApprovals')}</div>
            : approvals.map(a => (
                <div key={a.id} className="changes-approval-row">
                  <Badge tone={a.decision === 'APPROVE' ? 'success' : a.decision === 'REJECT' ? 'danger' : a.decision === 'DEFER' ? 'warning' : 'neutral'}>{a.decision}</Badge>
                  <div className="changes-approval-row__main">
                    <strong>{a.approverName}</strong>{a.approverRole && <span className="changes-approval-row__role"> · {a.approverRole}</span>}
                    {a.comments && <div className="changes-approval-row__comments">{a.comments}</div>}
                  </div>
                  {a.decidedAt && <span className="mono changes-approval-row__time">{new Date(a.decidedAt).toLocaleString()}</span>}
                </div>
              ))
          }
        </Card>

        <Card title={t('changeTasks')} Icon={ListChecks}
              action={<Button variant="secondary" Icon={Plus} onClick={addTask}>{t('changeAddTask')}</Button>}>
          {tasks.length === 0
            ? <div className="changes-empty-note">{t('changeNoTasks')}</div>
            : tasks.map(task => (
                <div key={task.id} className="changes-task-row">
                  <button onClick={() => flipTask(task)} title={t('changeTaskFlip')}
                          data-status={task.status}
                          className="changes-task-btn">
                    {task.status === 'DONE' ? <CheckCheck size={12} /> : task.status === 'IN_PROGRESS' ? <Activity size={12} /> : <Clock size={12} />}
                  </button>
                  <span className="changes-task-label" data-done={task.status === 'DONE' ? 'true' : 'false'}>
                    {lang === 'ar' && task.titleAr ? task.titleAr : task.titleEn}
                    {task.ownerName && <span className="changes-task-owner">· {task.ownerName}</span>}
                  </span>
                  <Badge tone={task.status === 'DONE' ? 'success' : task.status === 'IN_PROGRESS' ? 'info' : task.status === 'BLOCKED' ? 'danger' : 'neutral'}>{task.status.replace(/_/g, ' ')}</Badge>
                  <button onClick={() => deleteTask(task)} title={t('deleteBtn')} className="changes-task-del">
                    <Trash2 size={13} />
                  </button>
                </div>
              ))
          }
        </Card>

        <Card title={t('changeComments')} Icon={MessageSquare}>
          <div className="changes-comments-scroll">
            {comments.length === 0
              ? <div className="changes-empty-note">{t('changeNoComments')}</div>
              : comments.map(c => (
                  <div key={c.id} className="changes-comment-item">
                    <div className="changes-comment-meta">
                      <strong className="changes-comment-author">{c.authorName}</strong>
                      {/*
                        F2-248 — three states, and the middle two must not look alike (F2-209).
                        A row with an `authorId` was attributed by the server from the authenticated
                        principal. A row without one predates that and carries whatever the caller
                        typed — 45 of 45 dev rows and 46 of 46 prod rows, one of them literally
                        'You'. Blanking the name would let the unattributable rows read as the
                        tidiest (F2-197), so the name stays and the row says what it is.
                      */}
                      {c.authorId == null && (
                        <span role="status" className="changes-comment-unverified"
                              title={t('changeCommentUnverifiedHint')}
                              aria-label={t('changeCommentUnverifiedHint')}>
                          {t('changeCommentUnverified')}
                        </span>
                      )}
                      {c.createdAt && <span className="mono changes-comment-time">{new Date(c.createdAt).toLocaleString()}</span>}
                    </div>
                    <div className="changes-comment-body">{c.body}</div>
                  </div>
                ))
            }
          </div>
          <div className="changes-comment-form">
            <input type="text" value={commentBody} onChange={e => setCommentBody(e.target.value)}
                   placeholder={t('changeCommentPlaceholder')}
                   className="changes-comment-input" />
            <Button variant="primary" Icon={Send} onClick={postComment}>{t('changeCommentPost')}</Button>
          </div>
        </Card>

        <FormModal
          open={voteDecision !== null}
          title={`${t('changeVoteModalTitle')} · ${voteDecision ?? ''}`}
          size="md"
          fields={[
            { kind: 'text', key: 'approverName', label: t('changeApproverName'), required: true, span: 12 },
            { kind: 'text', key: 'approverRole', label: t('changeApproverRole'), span: 12 },
            { kind: 'textarea', key: 'comments', label: t('changeApproverComments'), rows: 3, span: 12 },
          ]}
          initial={voteInitial}
          submitLabel={t('changeVoteRecordBtn')}
          onClose={() => setVoteDecision(null)}
          onSubmit={submitVote}
        />
        <FormModal
          open={taskOpen}
          title={t('changeAddTask')}
          size="md"
          fields={[
            // ⚠ F2-239 — `titleAr` is REQUIRED, and this form had no input for it: `ChangeTask.titleAr`
            // is @NotBlank (F2-30, house rule 7), so POST /changes/{id}/tasks answered
            // 400 "titleAr: Field must not be blank" to every submission this modal made. Probed live.
            // House rule 7 says Arabic names are backend-mandatory and must be client-gated in forms —
            // this is that gate, and its absence is why "Add task" had never once created a task.
            { kind: 'text', key: 'titleEn', label: t('changeTaskTitle'), required: true, span: 6 },
            { kind: 'text', key: 'titleAr', label: t('changeTaskTitleAr'), required: true, span: 6 },
            { kind: 'text', key: 'ownerName', label: t('changeTaskOwner'), span: 6 },
            { kind: 'date', key: 'dueDate', label: t('changeTaskDueDate'), span: 6 },
          ]}
          initial={taskInitial}
          submitLabel={t('changeAddTask')}
          onClose={() => setTaskOpen(false)}
          onSubmit={submitTask}
        />
        <button onClick={() => onChanged()} className="changes-refresh-hidden">refresh-poke</button>
      </div>
    </div>
  );
}

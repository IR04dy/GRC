// WF-3 — workflow request detail: the request's facts, its recipients grouped by routing step,
// the SLA countdown, the append-only timeline (item 8), and the §6 lifecycle actions offered
// contextually by status. Actor authorization stays server-side — a 400/403/409 from an action
// simply surfaces as a toast.
import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  Send, CheckCircle2, XCircle, RotateCcw, Undo2, Flame, Lock, Unlock, History, Users,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  workflowRequestsApi, type WorkflowRequestDto, type WorkflowEventDto,
} from '../../lib/workflowRequests';
import { WF_STATUS_TONE } from './WorkflowRequestsPage';

const COUNTDOWN_TONE: Record<string, BadgeTone> = {
  ON_TRACK: 'success', DUE_TODAY: 'warning', OVERDUE: 'danger', NONE: 'neutral',
};
const EVENT_TONE: Record<string, BadgeTone> = {
  CREATED: 'neutral', SENT: 'info', RESPONDED: 'info', STEP_ADVANCED: 'info', REMINDED: 'warning',
  SUBMITTED: 'warning', REVIEWED: 'warning', RETURNED: 'warning', RESUBMITTED: 'warning',
  APPROVED: 'success', REJECTED: 'danger', ESCALATED: 'danger', OVERDUE: 'danger',
  CLOSED: 'neutral', REOPENED: 'info', BRANCHED: 'warning', EVIDENCE_BRIDGED: 'info',
};
const RECIPIENT_STATUS_TONE: Record<string, BadgeTone> = {
  PENDING: 'neutral', NOTIFIED: 'info', IN_PROGRESS: 'info',
  RESPONDED: 'success', APPROVED: 'success', REJECTED: 'danger', SKIPPED: 'neutral',
};

export function WorkflowRequestDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const toast = useToast();
  const [row, setRow] = useState<WorkflowRequestDto | null>(null);
  const [events, setEvents] = useState<WorkflowEventDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [note, setNote] = useState('');
  const [outcome, setOutcome] = useState('');   // WF-6 — respond outcome (may trigger a branch rule)
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    if (!id) return;
    setLoading(true);
    Promise.all([workflowRequestsApi.get(Number(id)), workflowRequestsApi.auditLog(Number(id))])
      .then(([r, ev]) => { setRow(r); setEvents(ev); })
      .catch(() => { setRow(null); setEvents([]); })
      .finally(() => setLoading(false));
  }, [id]);
  useEffect(() => { load(); }, [load]);

  async function run(action: (rid: number, note?: string) => Promise<WorkflowRequestDto>, label: string) {
    if (!row) return;
    setBusy(true);
    try {
      await action(row.id, note.trim() || undefined);
      toast.success(label);
      setNote('');
      setOutcome('');
      load();
    } catch (e) {
      toast.danger((e as Error)?.message ?? 'Action failed');
    } finally { setBusy(false); }
  }

  if (loading) {
    return <Shell title={ar ? 'طلب سير عمل' : 'Workflow request'}>
      <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{ar ? 'جارٍ التحميل…' : 'Loading…'}</div>
    </Shell>;
  }
  if (!row) {
    return <Shell title={ar ? 'طلب سير عمل' : 'Workflow request'}>
      <DetailHeader breadcrumbs={[{ label: ar ? 'طلبات سير العمل' : 'Workflow requests', to: '/workflows/requests' }, { label: `#${id}` }]}
        title={ar ? 'تعذّر التحميل' : 'Failed to load'} backTo="/workflows/requests" />
    </Shell>;
  }

  const s = row.status;
  const steps = [...new Set((row.recipients ?? []).map(r => r.stepNo))].sort((a, b) => a - b);
  const canRespond = ['SENT', 'IN_PROGRESS', 'REOPENED', 'ESCALATED', 'OVERDUE'].includes(s);
  const canReview = ['SUBMITTED', 'UNDER_REVIEW', 'RESUBMITTED'].includes(s);
  const canResubmit = ['RETURNED', 'REJECTED'].includes(s);

  return (
    <Shell title={row.requestCode}>
      <DetailHeader
        breadcrumbs={[{ label: ar ? 'طلبات سير العمل' : 'Workflow requests', to: '/workflows/requests' }, { label: row.requestCode }]}
        eyebrow={`${row.sourceModule} · ${row.requestType.replace(/_/g, ' ')}`}
        title={ar && row.titleAr ? row.titleAr : row.titleEn}
        sub={[row.initiatedByName ? `${ar ? 'أنشأه' : 'by'} ${row.initiatedByName}` : null,
              row.dueDate ? `${ar ? 'يستحق' : 'due'} ${row.dueDate}` : null].filter(Boolean).join(' · ')}
        backTo="/workflows/requests"
      />

      <div style={{ display: 'flex', gap: 8, alignItems: 'center', margin: '4px 0 14px', flexWrap: 'wrap' }}>
        <Badge tone={WF_STATUS_TONE[s] ?? 'neutral'} dot>{s}</Badge>
        <Badge tone={COUNTDOWN_TONE[row.countdownStatus] ?? 'neutral'}>
          {row.countdownStatus}{row.hoursRemaining != null && row.hoursRemaining > 0 ? ` · ${Math.floor(row.hoursRemaining / 24)}d ${row.hoursRemaining % 24}h` : ''}
        </Badge>
        <Badge tone={row.priority === 'HIGH' || row.priority === 'CRITICAL' ? 'danger' : 'neutral'}>{row.priority}</Badge>
        <Badge tone="neutral">{row.routingMode}{row.routingMode === 'PARALLEL' ? `/${row.completionRule}` : ''}</Badge>
        {row.escalatedAt && <Badge tone="danger">{ar ? 'مُصعَّد' : 'escalated'}</Badge>}
        {row.reminderCount > 0 && <Badge tone="warning">{(ar ? 'تذكيرات: ' : 'reminders: ') + row.reminderCount}</Badge>}
        {row.evidenceRequestId != null && (
          <Link to="/evidence" style={{ textDecoration: 'none' }}
            title={ar ? 'فُتح طلب دليل حقيقي في نظام الأدلة' : 'A real evidence request was opened in the evidence subsystem'}>
            <Badge tone="info">{(ar ? 'طلب الدليل ' : 'evidence ') + `REQ-${String(row.evidenceRequestId).padStart(5, '0')}`}</Badge>
          </Link>
        )}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 3fr) minmax(0, 2fr)', gap: 16, alignItems: 'start' }}>
        <div>
          <Card title={ar ? 'المستلمون حسب الخطوة' : 'Recipients by step'} Icon={Users} padded={false}>
            {steps.map(step => (
              <div key={step}>
                {row.routingMode === 'SEQUENTIAL' && (
                  <div style={{ padding: '6px 14px', fontSize: 11, fontWeight: 700, color: 'var(--ink-3)',
                    background: 'var(--surface-2)', display: 'flex', gap: 8, alignItems: 'center' }}>
                    {ar ? 'الخطوة' : 'STEP'} {step}
                    {row.currentStep === step && !['CLOSED', 'ARCHIVED', 'APPROVED'].includes(s) &&
                      <Badge tone="info">{ar ? 'الحالية' : 'current'}</Badge>}
                  </div>
                )}
                {(row.recipients ?? []).filter(r => r.stepNo === step).map(r => (
                  <div key={r.id} style={{ display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 10,
                    alignItems: 'center', padding: '9px 14px', borderBottom: '1px solid var(--border)' }}>
                    <Badge tone="neutral">{r.recipientType}</Badge>
                    <div style={{ fontSize: 12, minWidth: 0 }}>
                      <strong>{r.recipientLabel ?? r.recipientKey ?? `#${r.recipientId}`}</strong>
                      <span style={{ color: 'var(--ink-3)' }}> · {r.actorRole}</span>
                      {r.responseOutcome && <Badge tone="warning">{r.responseOutcome}</Badge>}
                      {r.response && <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{r.response}</div>}
                    </div>
                    <Badge tone={RECIPIENT_STATUS_TONE[r.status] ?? 'neutral'}>{r.status}</Badge>
                  </div>
                ))}
              </div>
            ))}
          </Card>

          <div style={{ marginTop: 16 }}>
            <Card title={ar ? 'سجل التدقيق (غير قابل للتعديل)' : 'Audit timeline (append-only)'} Icon={History} padded={false}>
              {events.length === 0 ? (
                <div style={{ padding: 16, fontSize: 12, color: 'var(--ink-3)' }}>—</div>
              ) : events.map((e, i) => (
                <div key={e.id} style={{ display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 10,
                  alignItems: 'center', padding: '8px 14px',
                  borderBottom: i < events.length - 1 ? '1px solid var(--border)' : 'none' }}>
                  <Badge tone={EVENT_TONE[e.eventType] ?? 'neutral'}>{e.eventType.replace(/_/g, ' ')}</Badge>
                  <div style={{ fontSize: 11, minWidth: 0 }}>
                    {e.fromStatus && e.toStatus && e.fromStatus !== e.toStatus && (
                      <span className="mono" style={{ color: 'var(--ink-3)' }}>{e.fromStatus} → {e.toStatus} · </span>
                    )}
                    {e.note}
                  </div>
                  <div style={{ textAlign: ar ? 'left' : 'right', fontSize: 10, color: 'var(--ink-3)' }}>
                    <div className="mono">{e.createdAt?.slice(0, 16).replace('T', ' ')}</div>
                    {e.actor && <div>{e.actor}</div>}
                  </div>
                </div>
              ))}
            </Card>
          </div>
        </div>

        <div>
          <Card title={ar ? 'الإجراءات' : 'Actions'} Icon={Send}>
            <Input label={ar ? 'ملاحظة (تُرفق بالإجراء)' : 'Note (attached to the action)'} value={note}
              onChange={e => setNote(e.target.value)} placeholder={ar ? 'اختياري…' : 'optional…'} />
            {canRespond && (row.branchRules?.length ?? 0) > 0 && (
              <div style={{ marginTop: 8 }}>
                <Input label={ar ? 'النتيجة (قد تُفعّل قاعدة تفرع)' : 'Outcome (may trigger a branch rule)'}
                  value={outcome} onChange={e => setOutcome(e.target.value)}
                  placeholder={[...new Set((row.branchRules ?? []).map(b => b.onOutcome))].join(' / ')} />
              </div>
            )}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 12 }}>
              {s === 'DRAFT' && (
                <Button size="sm" variant="primary" disabled={busy}
                  onClick={() => void run((rid) => workflowRequestsApi.send(rid), ar ? 'أُرسل' : 'Sent')}>
                  <Send size={13} /> {ar ? 'إرسال' : 'Send'}
                </Button>
              )}
              {canRespond && (
                <Button size="sm" variant="primary" disabled={busy}
                  onClick={() => void run((rid, n) => workflowRequestsApi.respond(rid, n, outcome.trim().toUpperCase() || undefined),
                    ar ? 'تم الرد' : 'Responded')}>
                  <CheckCircle2 size={13} /> {ar ? 'الرد / إنجاز' : 'Respond / complete'}
                </Button>
              )}
              {canReview && (<>
                <Button size="sm" variant="primary" disabled={busy}
                  onClick={() => void run((rid, n) => workflowRequestsApi.review(rid, 'APPROVE', n), ar ? 'اجتاز المراجعة' : 'Review passed')}>
                  <CheckCircle2 size={13} /> {ar ? 'مراجعة: قبول' : 'Review: approve'}
                </Button>
                <Button size="sm" variant="secondary" disabled={busy}
                  onClick={() => void run((rid, n) => workflowRequestsApi.review(rid, 'RETURN', n), ar ? 'أُعيد للتعديل' : 'Returned for changes')}>
                  <Undo2 size={13} /> {ar ? 'مراجعة: إعادة' : 'Review: return'}
                </Button>
                <Button size="sm" variant="secondary" disabled={busy}
                  onClick={() => void run(workflowRequestsApi.approve, ar ? 'اعتُمد' : 'Approved')}>
                  <CheckCircle2 size={13} /> {ar ? 'اعتماد' : 'Approve'}
                </Button>
                <Button size="sm" variant="danger" disabled={busy}
                  onClick={() => void run(workflowRequestsApi.reject, ar ? 'رُفض' : 'Rejected')}>
                  <XCircle size={13} /> {ar ? 'رفض' : 'Reject'}
                </Button>
              </>)}
              {canResubmit && (
                <Button size="sm" variant="primary" disabled={busy}
                  onClick={() => void run(workflowRequestsApi.resubmit, ar ? 'أُعيد التقديم' : 'Resubmitted')}>
                  <RotateCcw size={13} /> {ar ? 'إعادة تقديم' : 'Resubmit'}
                </Button>
              )}
              {!['DRAFT', 'CLOSED', 'ARCHIVED', 'ESCALATED'].includes(s) && (
                <Button size="sm" variant="secondary" disabled={busy}
                  onClick={() => void run(workflowRequestsApi.escalate, ar ? 'صُعِّد' : 'Escalated')}>
                  <Flame size={13} /> {ar ? 'تصعيد' : 'Escalate'}
                </Button>
              )}
              {!['CLOSED', 'ARCHIVED'].includes(s) && (
                <Button size="sm" variant="ghost" disabled={busy}
                  onClick={() => void run(workflowRequestsApi.close, ar ? 'أُغلق' : 'Closed')}>
                  <Lock size={13} /> {ar ? 'إغلاق' : 'Close'}
                </Button>
              )}
              {['CLOSED', 'APPROVED', 'REJECTED'].includes(s) && (
                <Button size="sm" variant="ghost" disabled={busy}
                  onClick={() => void run(workflowRequestsApi.reopen, ar ? 'أُعيد فتحه' : 'Reopened')}>
                  <Unlock size={13} /> {ar ? 'إعادة فتح' : 'Reopen'}
                </Button>
              )}
            </div>
            <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 10 }}>
              {ar ? 'الصلاحيات تُفرض في الخادم: المستلم المخوّل فقط يرد، والمراجع/المعتمد فقط يقرر.'
                  : 'Authorization is server-enforced: only the actionable recipient may respond; only the designated reviewer/approver may gate.'}
            </div>
          </Card>

          {(row.branchRules?.length ?? 0) > 0 && (
            <div style={{ marginTop: 16 }}>
              <Card title={ar ? 'قواعد التفرع الشرطي' : 'Conditional branch rules'}>
                {(row.branchRules ?? []).map((b, i) => (
                  <div key={i} style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 12,
                    padding: '6px 0', borderBottom: i < (row.branchRules?.length ?? 0) - 1 ? '1px solid var(--border)' : 'none' }}>
                    <Badge tone="neutral">{(ar ? 'الخطوة ' : 'step ') + b.stepNo}</Badge>
                    <span className="mono">{b.onOutcome}</span>
                    <span style={{ color: 'var(--ink-3)' }}>→</span>
                    <Badge tone={b.action === 'ESCALATE' ? 'danger' : b.action === 'CLOSE' ? 'neutral' : 'warning'}>
                      {b.action}{b.action === 'GOTO_STEP' && b.targetStep != null ? ` ${b.targetStep}` : ''}
                    </Badge>
                  </div>
                ))}
                <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 8 }}>
                  {ar ? 'ترد بنتيجة مطابقة فيتجاوز التوجيه مساره الافتراضي (القفز للأمام فقط — لا حلقات).'
                      : 'Responding with a matching outcome overrides the default routing (forward-only jumps — no cycles).'}
                </div>
              </Card>
            </div>
          )}

          <div style={{ marginTop: 16 }}>
            <Card title={ar ? 'التفاصيل' : 'Details'}>
              <dl style={{ display: 'grid', gridTemplateColumns: '140px 1fr', gap: '6px 12px', margin: 0, fontSize: 12 }}>
                <dt style={{ color: 'var(--ink-3)' }}>{ar ? 'مطلوب' : 'Required action'}</dt><dd style={{ margin: 0 }}>{row.requiredAction ?? row.requestType.replace(/_/g, ' ')}</dd>
                <dt style={{ color: 'var(--ink-3)' }}>{ar ? 'تذكير قبل' : 'Remind before'}</dt><dd style={{ margin: 0 }}>{row.reminderDaysBefore} {ar ? 'يوم' : 'days'}</dd>
                <dt style={{ color: 'var(--ink-3)' }}>{ar ? 'مسار التصعيد' : 'Escalation path'}</dt>
                <dd style={{ margin: 0 }}>
                  {(row.escalationTiers?.length ?? 0) > 0 ? (
                    <span style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                      {(row.escalationTiers ?? []).map((t, i) => (
                        <Badge key={i} tone={(row.escalationLevel ?? 0) >= (t.level ?? i + 1) ? 'danger' : 'neutral'}>
                          {`T${t.level ?? i + 1}: ${t.role ?? (t.userId ? `user #${t.userId}` : '?')}${t.afterDays ? ` +${t.afterDays}d` : ''}`}
                        </Badge>
                      ))}
                      {(row.escalationLevel ?? 0) > 0 && <span style={{ color: 'var(--ink-3)' }}>{(ar ? 'المستوى الحالي: ' : 'at tier ') + row.escalationLevel}</span>}
                    </span>
                  ) : (row.escalationRole ?? (row.escalationUserId ? `user #${row.escalationUserId}` : '—'))}
                </dd>
                <dt style={{ color: 'var(--ink-3)' }}>{ar ? 'شرط الإغلاق' : 'Closure'}</dt><dd style={{ margin: 0 }}>{row.closureCondition ?? '—'}</dd>
                {row.description && (<><dt style={{ color: 'var(--ink-3)' }}>{ar ? 'الوصف' : 'Description'}</dt><dd style={{ margin: 0 }}>{row.description}</dd></>)}
              </dl>
            </Card>
          </div>
        </div>
      </div>
    </Shell>
  );
}

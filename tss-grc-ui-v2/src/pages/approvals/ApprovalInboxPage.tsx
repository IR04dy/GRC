import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Inbox, ShieldCheck, Clock, AlertTriangle, HelpCircle,
  Check, X as XIcon, UserPlus, ArrowRight, ClipboardList, RefreshCw,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  approvalsApi, mayTransition, isOpen, slaState, daysRemaining,
  APPROVAL_STATUS_TONE, approvalStatusLabel, PRIORITY_TONE, SLA_TONE,
  type ApprovalRequest, type ApprovalTransition,
} from '../../lib/approvalsApi';

/**
 * Approval Inbox — the cross-module approval queue, read from the server.
 *
 * F2-201. This page used to render `lib/approvals.ts`'s 12 hard-coded requests and "decide"
 * them in React state: Approve produced a success toast, a second toast claiming downstream
 * chain rules had fired, and nothing persisted anywhere — so an approver believed they had
 * discharged a governance duty and a reload put the request back to PENDING. Meanwhile the
 * real register held 22 rows (12 PENDING) in dev and 21 (11 PENDING) in prod, none of them
 * visible here.
 *
 * ⚠ Deliberately simpler than what it replaced, because the server's model is simpler.
 * `approvals.requests` carries ONE `assigned_to` at a time plus `delegated_from`; there are no
 * parallel/sequential approver chains, no approver-role registry and no multi-approver
 * progress. The old page had all three. Re-creating them here would be inventing a machine
 * the platform does not run — the F2-199 shape, where a fabrication is more granular than the
 * real model and therefore looks more finished.
 */
export function ApprovalInboxPage() {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [rows, setRows] = useState<ApprovalRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [mineOnly, setMineOnly] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [decide, setDecide] = useState<{ req: ApprovalRequest; to: string } | null>(null);
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(id);
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // NB: no mock fallback on ANY path. `useApiList` prefers a demo corpus when the server
      // returns zero rows, which is the load-bearing fallback that hid a 500 for the whole of
      // F2-158's Role API. An empty inbox must read as empty.
      setRows(await (mineOnly ? approvalsApi.myInbox() : approvalsApi.list()));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل قائمة الاعتمادات' : 'Could not load the approval queue'));
      setRows([]);
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mineOnly]);

  useEffect(() => { void load(); }, [load]);

  const pending = useMemo(() => rows.filter(r => isOpen(r.status)), [rows]);
  const decided = useMemo(
    () => rows.filter(r => !isOpen(r.status))
      .sort((a, b) => new Date(b.decidedAt ?? b.requestedAt).getTime()
                    - new Date(a.decidedAt ?? a.requestedAt).getTime())
      .slice(0, 8),
    [rows],
  );

  // Every figure below is counted over the same `rows` the list renders, so a tile can never
  // disagree with the list it sits above (F2-197's rule).
  const overdue    = pending.filter(r => slaState(r, now) === 'OVERDUE').length;
  const dueSoon    = pending.filter(r => slaState(r, now) === 'DUE_SOON').length;
  const noDeadline = pending.filter(r => slaState(r, now) === 'NO_DEADLINE').length;

  /** SLA-first, then soonest deadline; rows with no deadline sort last, never first. */
  const sortedPending = useMemo(() => {
    const order: Record<string, number> = { OVERDUE: 0, DUE_SOON: 1, ON_TRACK: 2, NO_DEADLINE: 3 };
    return [...pending].sort((a, b) => {
      const d = order[slaState(a, now)] - order[slaState(b, now)];
      if (d !== 0) return d;
      const da = daysRemaining(a, now);
      const db = daysRemaining(b, now);
      if (da == null || db == null) return 0;
      return da - db;
    });
  }, [pending, now]);

  async function submitDecision(req: ApprovalRequest, to: string, values: Record<string, string>) {
    setBusyId(req.id);
    try {
      const body: ApprovalTransition = { status: to };
      if (values.decisionCommentEn?.trim()) body.decisionCommentEn = values.decisionCommentEn.trim();
      if (to === 'DELEGATED') {
        body.newAssignedTo = values.newAssignedTo?.trim();
        if (values.delegationReasonEn?.trim()) body.delegationReasonEn = values.delegationReasonEn.trim();
      }
      const updated = await approvalsApi.transition(req.id, body);
      // Replace in place from the SERVER's response — never from a locally-computed guess, so
      // the row on screen is the row in the database.
      setRows(prev => prev.map(x => x.id === updated.id ? updated : x));
      toast.success(`${isAr ? req.titleAr : req.titleEn} → ${approvalStatusLabel(updated.status, isAr)}`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الاعتماد' : 'Decision failed'));
    } finally {
      setBusyId(null);
      setDecide(null);
    }
  }

  const decideFields = (to: string): FieldDef[] => to === 'DELEGATED'
    ? [
        { kind: 'text', key: 'newAssignedTo', label: isAr ? 'إحالة إلى (اسم المستخدم)' : 'Delegate to (username)', required: true, span: 12, placeholder: 'so_officer' },
        { kind: 'textarea', key: 'delegationReasonEn', label: isAr ? 'سبب الإحالة' : 'Delegation reason', rows: 2, span: 12 },
      ]
    : [
        { kind: 'textarea', key: 'decisionCommentEn', label: isAr ? 'تعليق القرار' : 'Decision comment', rows: 3, span: 12 },
      ];

  return (
    <Shell title={t('approvalsTitle')} sub={t('approvalsSub')}>
      <PageHeader
        eyebrow={t('approvalsEyebrow')}
        title={t('approvalsTitle')}
        sub={t('approvalsPageSub')}
        actions={
          <div style={{ display: 'flex', gap: 8 }}>
            <Button variant="secondary" size="sm" Icon={Inbox}
              onClick={() => setMineOnly(m => !m)}>
              {mineOnly ? t('approvalsShowAll') : t('approvalsShowMine')}
            </Button>
            <Button variant="secondary" size="sm" Icon={RefreshCw} onClick={() => void load()}>
              {t('approvalsRefresh')}
            </Button>
            {/* Different table (workflow_submissions), same reviewer mindset — cross-link
                rather than merge the rows. */}
            <Link to="/workflows/submissions">
              <Button variant="secondary" size="sm" Icon={ClipboardList}>
                {t('approvalsWorkflowSubmissionsLink')}
              </Button>
            </Link>
          </div>
        }
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Inbox}         label={t('approvalsKpiPending')}  value={loading ? null : pending.length} tone="primary" to="/approvals"
          emptyHint={isAr ? 'جارٍ التحميل' : 'Loading'} />
        <Kpi Icon={AlertTriangle} label={t('approvalsKpiOverdue')}  value={loading ? null : overdue}
          tone={overdue > 0 ? 'danger' : 'success'} to="/approvals"
          emptyHint={isAr ? 'جارٍ التحميل' : 'Loading'} />
        <Kpi Icon={Clock}         label={t('approvalsKpiDueSoon')}  value={loading ? null : dueSoon}
          tone={dueSoon > 0 ? 'warning' : 'success'} to="/approvals"
          emptyHint={isAr ? 'جارٍ التحميل' : 'Loading'} />
        {/* `slaDeadline` is NULL on 10 of 22 rows in dev, so this is a real state and not a
            null stand-in. It is neutral on purpose: an unknown deadline is not health. */}
        <Kpi Icon={HelpCircle}    label={t('approvalsKpiNoDeadline')} value={loading ? null : noDeadline}
          tone="neutral" to="/approvals" emptyHint={isAr ? 'جارٍ التحميل' : 'Loading'} />
        <Kpi Icon={ShieldCheck}   label={t('approvalsKpiDecided')}  value={loading ? null : decided.length}
          tone="success" to="/approvals" emptyHint={isAr ? 'جارٍ التحميل' : 'Loading'} />
      </div>

      {error && (
        <Card title={t('approvalsErrorTitle')} Icon={AlertTriangle}>
          <div role="alert" style={{ fontSize: 13, color: 'var(--danger-fg)' }}>{error}</div>
        </Card>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 16 }}>
        <Card title={t('approvalsPendingTitle')} Icon={Inbox}
          action={<Badge tone="primary">{loading ? '—' : String(pending.length)}</Badge>}>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {loading
              ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
              : sortedPending.length === 0
                ? <InlineEmpty>{t('approvalsEmpty')}</InlineEmpty>
                : sortedPending.map(req => (
                    <ApprovalRow key={req.id} req={req} now={now} isAr={isAr} t={t}
                      busy={busyId === req.id}
                      onDecide={to => setDecide({ req, to })} />
                  ))}
          </div>
        </Card>

        <Card title={t('approvalsRecentTitle')} Icon={ShieldCheck}>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {loading
              ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
              : decided.length === 0
                ? <InlineEmpty>{t('approvalsRecentEmpty')}</InlineEmpty>
                : decided.map(req => (
                    <div key={req.id} style={{ padding: '10px 0', borderBottom: '1px dashed var(--border)' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {isAr ? req.titleAr : req.titleEn}
                          </div>
                          <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>
                            {req.entityType} · {req.entityId}
                            {req.decidedBy && <> · {req.decidedBy}</>}
                          </div>
                        </div>
                        <Badge tone={APPROVAL_STATUS_TONE[req.status] ?? 'neutral'}>
                          {approvalStatusLabel(req.status, isAr)}
                        </Badge>
                      </div>
                    </div>
                  ))}
          </div>
        </Card>
      </div>

      {decide && (
        <FormModal<Record<string, string>>
          open
          title={`${approvalStatusLabel(decide.to, isAr)} · ${isAr ? decide.req.titleAr : decide.req.titleEn}`}
          fields={decideFields(decide.to)}
          initial={decide.to === 'DELEGATED'
            ? { newAssignedTo: '', delegationReasonEn: '' }
            : { decisionCommentEn: '' }}
          size="md"
          onClose={() => setDecide(null)}
          onSubmit={values => submitDecision(decide.req, decide.to, values)}
          submitLabel={approvalStatusLabel(decide.to, isAr)}
        />
      )}
    </Shell>
  );
}

function ApprovalRow({
  req, now, isAr, t, busy, onDecide,
}: {
  req: ApprovalRequest;
  now: Date;
  isAr: boolean;
  t: (k: string) => string;
  busy: boolean;
  onDecide: (to: string) => void;
}) {
  const sla = slaState(req, now);
  const days = daysRemaining(req, now);

  return (
    <div style={{ padding: '14px 0', borderBottom: '1px dashed var(--border)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
        <div style={{ minWidth: 0 }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <span style={{ fontSize: 14, fontWeight: 700, color: 'var(--ink-1)' }}>
              {isAr ? req.titleAr : req.titleEn}
            </span>
            <Badge tone={PRIORITY_TONE[req.priority] ?? 'neutral'}>{req.priority}</Badge>
            <Badge tone="primary">{req.entityType}</Badge>
            <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{req.entityId}</span>
          </div>
          {(isAr ? req.summaryAr : req.summaryEn) && (
            <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 4, lineHeight: 1.5 }}>
              {isAr ? req.summaryAr : req.summaryEn}
            </div>
          )}
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 6 }}>
            {t('approvalAssignedTo')}: <strong>{req.assignedTo}</strong>
            {req.delegatedFrom && <> · {t('approvalDelegatedFrom')} {req.delegatedFrom}</>}
            {' · '}{t('approvalRaisedBy')} {req.requestedBy}
          </div>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4, flexShrink: 0 }}>
          <Badge tone={SLA_TONE[sla]}>
            {sla === 'NO_DEADLINE'
              ? t('approvalNoDeadline')
              : days != null && days < 0
                ? t('approvalOverdueBy').replace('{n}', String(Math.abs(days)))
                : t('approvalDueIn').replace('{n}', String(days ?? 0))}
          </Badge>
          <Badge tone={APPROVAL_STATUS_TONE[req.status] ?? 'neutral'}>
            {approvalStatusLabel(req.status, isAr)}
          </Badge>
        </div>
      </div>

      {/* Only the transitions the server's own table allows from this status. */}
      <div style={{ display: 'flex', gap: 6, marginTop: 10, flexWrap: 'wrap' }}>
        {mayTransition(req.status, 'APPROVED') && (
          <Button variant="primary" Icon={Check} disabled={busy}
            onClick={() => onDecide('APPROVED')}>{t('approvalActApprove')}</Button>
        )}
        {mayTransition(req.status, 'REJECTED') && (
          <Button variant="secondary" Icon={XIcon} disabled={busy}
            onClick={() => onDecide('REJECTED')}>{t('approvalActReject')}</Button>
        )}
        {mayTransition(req.status, 'DELEGATED') && (
          <Button variant="secondary" Icon={UserPlus} disabled={busy}
            onClick={() => onDecide('DELEGATED')}>{t('approvalActDelegate')}</Button>
        )}
        {mayTransition(req.status, 'PENDING') && (
          <Button variant="secondary" Icon={ArrowRight} disabled={busy}
            onClick={() => onDecide('PENDING')}>{t('approvalActReclaim')}</Button>
        )}
      </div>
    </div>
  );
}

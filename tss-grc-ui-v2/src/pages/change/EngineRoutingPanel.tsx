/**
 * S8h / CE-10 — workflow-engine adoption for change: who can actually be told, and routing a task
 * to somebody who can.
 *
 * **The number this card exists to show.** Measured on dev when it was built: 41 change tasks, 23 of
 * them overdue, 14 distinct owner labels, and **not one** resolving to a person the platform can
 * notify. CH-5's overdue sweep has been firing correctly into a void ever since it shipped, and no
 * screen said so. A card that only showed "23 overdue" would have looked like a workload problem.
 *
 * **What it refuses to do.** It never shows a reachable count without the unreachable one beside it,
 * it never offers an addressee the server would reject (the picker is server-supplied and contains
 * only addressees that reach someone), and when the engine is unavailable it says the verdicts are
 * *unknown* rather than drawing zeros — an absence of measurement is not a clean result.
 *
 * ⚠ There is no "approve" action here and there cannot be one. Change authorisation has exactly two
 * routes — recorded CAB votes, or a carried committee resolution — and the server refuses an
 * `APPROVAL`-typed engine request outright, because an approval-shaped request would sit in the audit
 * trail conferring no authority and contradict the unauthorised-change register.
 */
import { useEffect, useState } from 'react';
import {
  Workflow, UserCheck, UserX, Send, AlertTriangle, HelpCircle, Inbox, Clock,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { FormModal } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  changeApi, ApiError,
  type TaskAddressabilityDto, type OwnerVerdictDto, type EngineAddresseeDto,
} from '../../lib/api';
import { hasAnyRole } from '../../lib/auth';

/**
 * The request types a change may raise through the engine.
 *
 * ⚠ `APPROVAL` is absent and must stay absent — it mirrors `WorkflowPort.RAISABLE_TYPES`, and the
 * server refuses it. Adding it here would produce a button whose only outcome is a 400.
 */
export const ENGINE_REQUEST_TYPES = [
  'TASK', 'REVIEW', 'EVIDENCE_REQUEST', 'INFORMATION',
  'FOLLOW_UP', 'ACKNOWLEDGEMENT', 'REPORT_DISTRIBUTION',
] as const;

/** The one request type the change module may never raise. */
export const REFUSED_REQUEST_TYPE = 'APPROVAL';

/**
 * Tone per unreachable reason.
 *
 * `*_NOT_FOUND` and `LABEL_MATCHES_NOTHING` are `danger`: the name is wrong, and no amount of
 * appointing fixes it. `*_HAS_NO_MEMBERS` / `*_HELD_BY_NOBODY` are `warning`: the target is real and
 * empty, which one appointment fixes. Flattening them to one colour would lose the only distinction
 * that tells an operator what to do next.
 */
export const REASON_TONE: Record<string, BadgeTone> = {
  LABEL_MATCHES_NOTHING: 'danger',
  ROLE_NOT_FOUND: 'danger',
  DEPARTMENT_NOT_FOUND: 'danger',
  COMMITTEE_NOT_FOUND: 'danger',
  USER_NOT_FOUND: 'danger',
  ADDRESSEE_TYPE_UNKNOWN: 'danger',
  ROLE_HELD_BY_NOBODY: 'warning',
  DEPARTMENT_HAS_NO_MEMBERS: 'warning',
  COMMITTEE_HAS_NO_LINKED_USERS: 'warning',
  ENGINE_UNAVAILABLE: 'neutral',
};

/** Whether a reason describes a name that is simply wrong, rather than an empty-but-real target. */
export function isNameDefect(reason?: string | null): boolean {
  return !!reason && (reason.endsWith('_NOT_FOUND') || reason === 'LABEL_MATCHES_NOTHING'
    || reason === 'ADDRESSEE_TYPE_UNKNOWN');
}

/** An addressee's picker key — `type:id` or `type:key`, since ROLE has no id. */
export function addresseeKey(a: EngineAddresseeDto): string {
  return `${a.type}:${a.id ?? a.key ?? ''}`;
}

/** The raise form's shape. `slaHours` stays a string because the field yields one. */
interface RaiseForm {
  changeId: string;
  requestType: string;
  addressee: string;
  titleEn: string;
  titleAr?: string;
  dueDate?: string;
  slaHours?: string;
  priority: string;
  description?: string;
}

const RAISE_INITIAL: RaiseForm = {
  changeId: '', requestType: 'TASK', addressee: '', titleEn: '', titleAr: '',
  dueDate: '', slaHours: '', priority: 'MEDIUM', description: '',
};

export function EngineRoutingPanel({ toast }: { toast: ReturnType<typeof useToast> }) {
  const { t } = useI18n();

  const [report, setReport] = useState<TaskAddressabilityDto | null>(null);
  const [addressees, setAddressees] = useState<EngineAddresseeDto[]>([]);
  const [open, setOpen] = useState(false);
  /**
   * CHANGE:WRITE is held by 42 of 57 roles, so this gate is close to cosmetic — but PLATFORM_ADMIN
   * is honoured for the reason `canExport` gives: the evaluator bypasses in code for it, and hiding
   * a control the server would allow is the same defect pointing the other way.
   */
  const mayRoute = hasAnyRole('PLATFORM_ADMIN', 'CHANGE_WRITE');

  async function load() {
    try {
      const [r, a] = await Promise.all([
        changeApi.taskAddressability(),
        changeApi.engineAddressees().catch(() => [] as EngineAddresseeDto[]),
      ]);
      setReport(r);
      setAddressees(a);
    } catch { /* silent — a backend-down window surfaces elsewhere on this page */ }
  }
  useEffect(() => { void load(); }, []);

  async function raise(v: RaiseForm): Promise<void> {
    const picked = addressees.find(a => addresseeKey(a) === v.addressee);
    if (!picked) { toast.danger(t('ceAddresseePick')); return; }
    try {
      const ref = await changeApi.raiseEngineRequest(Number(v.changeId), {
        requestType: v.requestType,
        titleEn: v.titleEn.trim(),
        titleAr: v.titleAr?.trim() || undefined,
        description: v.description?.trim() || undefined,
        // The picker carries the resolved addressee, so the server receives the same triple it
        // offered rather than a label the UI re-derived.
        addresseeType: picked.type,
        addresseeId: picked.id ?? undefined,
        addresseeKey: picked.key ?? undefined,
        addresseeLabel: picked.label,
        dueDate: v.dueDate || undefined,
        slaHours: v.slaHours ? Number(v.slaHours) : undefined,
        priority: v.priority,
      });
      toast.success(`${t('ceRaised')} — ${ref.code}`);
      setOpen(false);
      void load();
    } catch (e) {
      // A 400 here is the reachability refusal doing its job, so surface the server's own wording —
      // it names the reason (ROLE_HELD_BY_NOBODY, DEPARTMENT_HAS_NO_MEMBERS…) and a generic
      // "Action failed" would throw away the only part the operator can act on.
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  const label = (o: OwnerVerdictDto) => o.matchedLabel || o.label;

  return (
    <Card title={t('ceTitle')} Icon={Workflow}>
      <p className="changes-gov-sub">{t('ceSub')}</p>

      {report && (
        <>
          <div className="changes-gov-actions" style={{ flexWrap: 'wrap', gap: 8 }}>
            <Badge tone="info">{t('ceOpenTasks')}: {report.openTasks}</Badge>
            <Badge tone={report.overdueOpen > 0 ? 'warning' : 'neutral'}>
              <Clock size={11} /> {t('ceOverdue')}: {report.overdueOpen}
            </Badge>
            {/* ⚠ Both counts, always. A reachable figure alone reads as coverage. */}
            <Badge tone={report.reachable > 0 ? 'success' : 'neutral'}>
              <UserCheck size={11} /> {t('ceReachable')}: {report.reachable}
            </Badge>
            <Badge tone={report.unreachable > 0 ? 'danger' : 'success'}>
              <UserX size={11} /> {t('ceUnreachable')}: {report.unreachable}
            </Badge>
            {report.unnamed > 0 && (
              <Badge tone="warning">{t('ceUnnamed')}: {report.unnamed}</Badge>
            )}
          </div>

          {report.engineAbsent
            ? (
              <p className="changes-kpi-hint">
                <HelpCircle size={12} /> <strong>{t('ceEngineAbsent')}</strong> {t('ceEngineAbsentHint')}
              </p>
            )
            : report.unreachable > 0 && (
              <p className="changes-kpi-hint">
                <AlertTriangle size={12} /> {t('ceUnreachableHint')}
              </p>
            )}

          {report.unnamed > 0 && (
            <p className="changes-kpi-hint">{t('ceUnnamedHint')}</p>
          )}

          {Object.keys(report.byReason).length > 0 && (
            <div className="changes-gov-actions" style={{ flexWrap: 'wrap', gap: 6, marginTop: 8 }}>
              {Object.entries(report.byReason).map(([reason, n]) => (
                <Badge key={reason} tone={REASON_TONE[reason] ?? 'neutral'}>
                  {t(`ceReason_${reason}`)}: {n}
                </Badge>
              ))}
            </div>
          )}

          {mayRoute && (
            <div className="changes-gov-actions" style={{ marginTop: 10 }}>
              <Button variant="primary" Icon={Send} disabled={addressees.length === 0}
                      onClick={() => setOpen(true)}>
                {t('ceRaise')}
              </Button>
              {addressees.length === 0 && (
                <span className="changes-kpi-hint">{t('ceNoAddressees')}</span>
              )}
            </div>
          )}
          {mayRoute && addressees.length > 0 && (
            /* Read before the modal opens, not inside it: both sentences are constraints on what the
               operator is about to do — the engine owns the clock, and it will not run one without a
               deadline. Buried in a form they would be read after the decision. */
            <div style={{ marginTop: 6 }}>
              <p className="changes-kpi-hint">{t('ceRaiseHint')}</p>
              <p className="changes-kpi-hint">{t('ceDeadlineHint')}</p>
            </div>
          )}
        </>
      )}

      {report && report.owners.length === 0 && (
        <EmptyState Icon={Inbox} title={t('ceEmptyTitle')} sub={t('ceEmptyHint')} />
      )}

      {report && report.owners.length > 0 && (
        <div className="changes-gov-table-wrap" style={{ marginTop: 12 }}>
          <table className="changes-gov-table">
            <thead>
              <tr>
                <th>{t('ceColOwner')}</th>
                <th>{t('ceColResolves')}</th>
                <th>{t('ceColVerdict')}</th>
                <th>{t('ceColTasks')}</th>
              </tr>
            </thead>
            <tbody>
              {report.owners.map(o => (
                <tr key={o.label}>
                  <td><strong>{o.label}</strong></td>
                  <td>
                    {o.matchedType
                      ? <>{t(`ceType_${o.matchedType}`)}
                          <div className="changes-kpi-hint">{label(o)}</div></>
                      /* Never blank: "matched nothing" and "we did not look" read the same in an
                         empty cell, and only one of them is the operator's problem. */
                      : <span className="changes-kpi-hint">{t('ceNoMatch')}</span>}
                  </td>
                  <td>
                    {o.reachable
                      ? <Badge tone="success">
                          <UserCheck size={11} /> {o.users} {t('ceUsers')}
                        </Badge>
                      : <Badge tone={REASON_TONE[o.reason ?? ''] ?? 'neutral'}>
                          <UserX size={11} /> {t(`ceReason_${o.reason}`)}
                        </Badge>}
                  </td>
                  <td>
                    {o.openTasks}
                    {o.overdueTasks > 0 && (
                      <div className="changes-kpi-hint">
                        {o.overdueTasks} {t('ceOverdueSuffix')}
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <FormModal
        open={open}
        size="md"
        title={t('ceRaiseTitle')}
        fields={[
          { kind: 'number', key: 'changeId', label: t('ceChangeId'), required: true, span: 6 },
          { kind: 'select', key: 'requestType', label: t('ceRequestType'), required: true, span: 6,
            options: ENGINE_REQUEST_TYPES.map(x => ({ value: x, label: t(`ceRequestType_${x}`) })) },
          /* Server-supplied and reach-filtered: the picker cannot offer what the server would
             refuse, so the 400 path exists as a backstop rather than as the normal experience. */
          { kind: 'select', key: 'addressee', label: t('ceAddressee'), required: true, span: 12,
            options: addressees.map(a => ({
              value: addresseeKey(a),
              label: `${t(`ceType_${a.type}`)} · ${a.label} (${a.users})`,
            })) },
          { kind: 'text', key: 'titleEn', label: t('ceTitleEn'), required: true, span: 12 },
          { kind: 'text', key: 'titleAr', label: t('ceTitleAr'), span: 12 },
          /* Required, because the server is. The engine refuses a response requirement with no
             deadline — the SLA clock, the reminders and the escalation ladder all measure from one —
             so an optional field here would just be a 400 waiting to happen. */
          { kind: 'date', key: 'dueDate', label: t('ceDueDate'), required: true, span: 4 },
          { kind: 'number', key: 'slaHours', label: t('ceSlaHours'), min: 1, max: 720, span: 4 },
          { kind: 'select', key: 'priority', label: t('cePriority'), span: 4,
            options: ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(x => ({ value: x, label: x })) },
          { kind: 'textarea', key: 'description', label: t('ceDescription'), rows: 3, span: 12 },
        ]}
        initial={RAISE_INITIAL}
        submitLabel={t('ceRaise')}
        onClose={() => setOpen(false)}
        onSubmit={raise}
      />
    </Card>
  );
}

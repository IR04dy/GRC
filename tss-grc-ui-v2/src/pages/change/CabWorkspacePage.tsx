/**
 * CE-13 — the CAB workspace.
 *
 * **What this screen is, and what it deliberately is not.** The CE plan asks for a "CAB workspace
 * (agenda builder, voting panel, minutes)". All three exist already, in the **committee** workspace,
 * because under ADR-017 D-2 the CAB *is* a committee — `committee_gate_map` names which one per
 * tenant (dev: CAB → IT Steering Committee, ECAB → Executive Committee). Drawing agendas, votes and
 * minutes a second time here would put two answers to "was this decision quorate?" on the platform,
 * which is the defect `CommitteePort` was written to prevent. So this screen composes and signposts:
 * it names the committee, shows the queue, and links out to the sitting.
 *
 * **What was missing, measured 2026-08-05.** The CAB queue (`awaitingDecision`) was reachable over no
 * HTTP route at all — its only caller was the agenda builder. Referral is manual, so `CAB_PENDING` is
 * a status a change wears rather than a request anybody received: two dev changes sat there with no
 * referral while the CAB's only two open referrals named change ids that exist in no organisation, and
 * the CAB's next sitting had a three-line agenda naming no change.
 *
 * **The three populations stay apart.** A change nobody referred, a referral nobody can table, and an
 * item the committee has are three different failures with three different remedies. Merging any two
 * produces a number a CAB secretary cannot act on — F2-94's distinction, third outing.
 */
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Gavel, CalendarClock, AlertTriangle, Users, Send, RefreshCw, ExternalLink, Ban, FileQuestion,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { changeApi, ApiError, type CabQueueDto, type CabGateDto, type CabPendingDto,
         type CabQueueRowDto } from '../../lib/api';
import { hasAnyRole } from '../../lib/auth';

/**
 * Tone by reason, ordered as the ladder is.
 *
 * ⚠ `TABLED` is the only good outcome and the only `success`. `GATE_UNMAPPED` is `danger` rather than
 * `warning` because nothing downstream can be done while it holds — with no committee named, a change
 * at that gate cannot even be referred.
 *
 * ⚠ The rung was first called `GATE_UNCONFIGURED` and keyed on the gate map's `configured` flag. The
 * live probe caught it labelling every row that way on a gate whose referral returned **201** and whose
 * agenda line appeared: `configured` folds three faults together (F2-96), and dev's CAB is the third —
 * correctly mapped, zero voting seats. So `mapped` (can refer) and `canDecide` (the approval will land)
 * are now separate, and only the first gates the ladder.
 */
export const CAB_REASON_TONE: Record<string, BadgeTone> = {
  GATE_UNMAPPED: 'danger',
  NOT_REFERRED: 'danger',
  SUBJECT_UNCITEABLE: 'warning',
  NO_SITTING_SCHEDULED: 'warning',
  NOT_TABLED: 'warning',
  TABLED: 'success',
};

/** The ladder, in the order a reader must fix it. Each rung is only reachable past the one above. */
export const CAB_REASON_LADDER = [
  'GATE_UNMAPPED', 'NOT_REFERRED', 'SUBJECT_UNCITEABLE',
  'NO_SITTING_SCHEDULED', 'NOT_TABLED', 'TABLED',
] as const;

export const CAB_SUBJECT_STATES = ['PRESENT', 'SUBJECT_NOT_STATED', 'SUBJECT_NOT_FOUND'] as const;

/**
 * True when the row cannot be tabled at all, whatever the committee does.
 *
 * Used to keep orphans out of the queue count rather than merely tinted differently: two dead rows
 * presented as "2 items awaiting decision" is a queue that lies about its own size.
 */
export function isOrphanRow(r: CabQueueRowDto): boolean {
  return r.subjectState !== 'PRESENT';
}

/** Only `NOT_REFERRED` is fixed by referring — the others need a different action or none. */
export function isReferable(p: CabPendingDto): boolean {
  return p.reason === 'NOT_REFERRED';
}

export function CabWorkspacePage() {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const dialog = useDialog();

  const [data, setData] = useState<CabQueueDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const canRefer = hasAnyRole('PLATFORM_ADMIN', 'CHANGE_WRITE');

  async function load() {
    setLoading(true);
    try { setData(await changeApi.cabQueue()); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('cabQueueLoadFailed')); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, []);

  /** Refer one change to the committee its gate names. Uses the existing guarded endpoint. */
  async function refer(p: CabPendingDto, gate: CabGateDto) {
    const ok = await dialog.confirm({
      title: t('cabReferConfirmTitle'),
      body: `${p.changeCode ?? `#${p.id}`} → ${gateCommittee(gate, isAr) ?? p.gate}`,
      submitLabel: t('cabReferAction'),
    });
    if (!ok) return;
    setBusyId(p.id);
    try {
      await changeApi.referToCommittee(p.id, gate.committeeId ?? undefined);
      toast.success(t('cabReferred'));
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : t('cabReferFailed')); }
    finally { setBusyId(null); }
  }

  const d = (s?: string) => (s ? new Date(s).toLocaleString() : '—');
  const title = (en?: string, ar?: string) => (isAr && ar ? ar : en) ?? '—';

  const orphanTotal = useMemo(
    () => (data?.gates ?? []).reduce((n, g) => n + g.queue.filter(isOrphanRow).length, 0),
    [data]);

  return (
    <Shell title={t('cabWorkspaceTitle')} sub="ITIL v4 · Change Advisory Board">
      <PageHeader
        eyebrow={t('cabWorkspaceEyebrow')}
        title={t('cabWorkspaceTitle')}
        sub={t('cabWorkspaceSub')}
        actions={
          <>
            {data?.engineAbsent && <Badge tone="danger">{t('cabEngineAbsent')}</Badge>}
            <Button variant="secondary" Icon={RefreshCw} onClick={load} disabled={loading}>
              {t('dashRefresh')}
            </Button>
            <Link to="/committees">
              <Button variant="secondary" Icon={ExternalLink}>{t('cabOpenCommittees')}</Button>
            </Link>
          </>
        }
      />

      {/* The engine's absence is stated before any number, because every number below it is unknown
          rather than zero — the rule S8h's addressability panel established. */}
      {data?.engineAbsent && (
        <Card>
          <div className="cab-alert">
            <AlertTriangle size={16} />
            <div>
              <strong>{t('cabEngineAbsent')}</strong>
              <p className="changes-kpi-hint">{t('cabEngineAbsentHint')}</p>
            </div>
          </div>
        </Card>
      )}

      <div className="kpi-grid">
        <Kpi Icon={Gavel}         label={t('cabKpiAwaiting')}    value={data?.awaiting ?? 0}    tone="primary" />
        <Kpi Icon={Send}          label={t('cabKpiNotReferred')} value={data?.notReferred ?? 0} tone="danger" />
        <Kpi Icon={CalendarClock} label={t('cabKpiNotTabled')}   value={data?.notTabled ?? 0}   tone="warning" />
        <Kpi Icon={FileQuestion}  label={t('cabKpiOrphans')}     value={orphanTotal}            tone="info" />
      </div>

      {loading && <Card><div className="changes-loading">{t('riskLoading')}</div></Card>}

      {!loading && (data?.gates ?? []).map(gate => (
        <Card key={gate.gate}
              title={`${gate.gate} · ${gateCommittee(gate, isAr) ?? t('cabNoCommittee')}`}
              Icon={Users}
              action={gate.committeeId != null && (
                <Link to={`/committees?committee=${gate.committeeId}`}>
                  <Button size="sm" variant="ghost" Icon={ExternalLink}>{t('cabOpenSitting')}</Button>
                </Link>
              )}>
          {/* A17-D2's sentence, rendered verbatim. Re-wording it here would mean the workspace and
              the 409 a user hits when approving explain the same fault two different ways. */}
          {/* Two facts, stated separately because they differ: whether anything can be referred
              here at all, and whether the committee named will actually carry the decision. The
              fault code is A17-D2's own — rendered through its label so the workspace and the 409
              a user hits when approving say the same thing. */}
          {!gate.mapped && (
            <div className="cab-fault">
              <Ban size={14} /> {t('cabGateUnmapped')}
            </div>
          )}
          {gate.mapped && !gate.canDecide && (
            <div className="cab-fault">
              <AlertTriangle size={14} />
              {t('cabCannotDecide')}
              {gate.fault && <span className="mono"> ({gate.fault})</span>}
            </div>
          )}

          {/* The sitting — identity and agenda coverage only. Quorum, votes and minutes stay in the
              committee workspace, which is the system of record for all three. */}
          {gate.mapped && (
            <div className="cab-sitting">
              {gate.sitting ? (
                <>
                  <span className="mono">{gate.sitting.code}</span>
                  <span>{title(gate.sitting.titleEn, gate.sitting.titleAr)}</span>
                  <Badge tone="info">{gate.sitting.status}</Badge>
                  <span className="changes-kpi-hint">{d(gate.sitting.scheduledAt)}</span>
                  <Badge tone={gate.sitting.tabledSubjects.length > 0 ? 'success' : 'warning'}>
                    {t('cabTabledCount')}: {gate.sitting.tabledSubjects.length} / {gate.sitting.agendaItems}
                  </Badge>
                </>
              ) : (
                <span className="cab-fault">
                  <CalendarClock size={14} /> {t('cabNoSitting')}
                </span>
              )}
            </div>
          )}

          {/* Awaiting a decision — the backlog, each row saying why it is or is not in front of them. */}
          <h4 className="cab-section">{t('cabAwaitingTitle')}</h4>
          <p className="changes-gov-sub">{t('cabAwaitingSub')}</p>
          {gate.awaiting.length === 0
            ? <div className="changes-empty-note">{t('cabAwaitingEmpty')}</div>
            : (
              <div className="changes-gov-table-wrap">
                <table className="changes-gov-table">
                  <thead>
                    <tr>
                      <th>{t('changeFldCode')}</th><th>{t('colTitle')}</th>
                      <th>{t('changeFldType')}</th><th>{t('changeFldRisk')}</th>
                      <th>{t('colStatus')}</th><th>{t('cabColReason')}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {gate.awaiting.map(p => (
                      <tr key={p.id}>
                        <td className="mono">{p.changeCode ?? `#${p.id}`}</td>
                        <td><Link to={`/changes?tab=workflow&change=${p.id}`}>{title(p.titleEn, p.titleAr)}</Link></td>
                        <td><Badge tone="neutral">{p.changeType}</Badge></td>
                        <td><Badge tone="neutral">{p.riskLevel}</Badge></td>
                        <td>{(p.status ?? '').replace(/_/g, ' ')}</td>
                        <td>
                          <Badge tone={CAB_REASON_TONE[p.reason] ?? 'neutral'} dot>
                            {t(`cabReason_${p.reason}` as never) || p.reason}
                          </Badge>
                        </td>
                        <td className="cab-row-action">
                          {/* Only the rung referral actually fixes offers the button. Offering it on
                              GATE_UNCONFIGURED would be a 500 waiting to be clicked, and on NOT_TABLED
                              it would create a second referral for an already-referred change. */}
                          {canRefer && isReferable(p) && gate.mapped && (
                            <Button size="sm" variant="primary" Icon={Send}
                                    disabled={busyId === p.id}
                                    onClick={() => refer(p, gate)}>
                              {t('cabReferAction')}
                            </Button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

          {/* Referrals the committee holds, including the ones it can never table. */}
          <h4 className="cab-section">{t('cabQueueTitle')}</h4>
          <p className="changes-gov-sub">{t('cabQueueSub')}</p>
          {gate.queue.length === 0
            ? <EmptyState Icon={Gavel} title={t('cabQueueEmptyTitle')} sub={t('cabQueueEmptySub')} />
            : (
              <div className="changes-gov-table-wrap">
                <table className="changes-gov-table">
                  <thead>
                    <tr>
                      <th>{t('cabColReferral')}</th><th>{t('changeFldCode')}</th>
                      <th>{t('colTitle')}</th><th>{t('colStatus')}</th>
                      <th>{t('cabColSubjectState')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {gate.queue.map(r => (
                      <tr key={r.referralId} className={isOrphanRow(r) ? 'cab-orphan' : undefined}>
                        <td className="mono">#{r.referralId}</td>
                        <td className="mono">{r.changeCode ?? '—'}</td>
                        <td>{title(r.subjectEn, r.subjectAr)}</td>
                        <td>{(r.referralStatus ?? '').replace(/_/g, ' ')}</td>
                        <td>
                          <Badge tone={isOrphanRow(r) ? 'danger' : 'success'} dot>
                            {t(`cabSubject_${r.subjectState}` as never) || r.subjectState}
                          </Badge>
                          {/* Stated per row, because "the change does not exist" and "the referral has
                              no label" look identical in a list and are fixed differently. */}
                          {r.subjectState === 'SUBJECT_NOT_FOUND' && (
                            <div className="changes-kpi-hint">{t('cabSubjectNotFoundHint')}</div>
                          )}
                          {r.subjectState === 'SUBJECT_NOT_STATED' && (
                            <div className="changes-kpi-hint">{t('cabSubjectNotStatedHint')}</div>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
        </Card>
      ))}
    </Shell>
  );
}

/** The committee's name in the reader's language, or null when the gate names none. */
function gateCommittee(g: CabGateDto, isAr: boolean): string | null {
  const name = isAr && g.committeeNameAr ? g.committeeNameAr : g.committeeNameEn;
  return name && name.trim() !== '' ? name : null;
}

import { useEffect, useMemo, useState, Fragment } from 'react';
import { Link } from 'react-router-dom';
import {
  Landmark, CheckCircle2, XCircle, Calendar, Play, FileCheck2, Users,
  ChevronRight, ChevronDown, Gavel, ClipboardList, ListChecks, RefreshCw,
  ExternalLink, AlertTriangle, ShieldAlert,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import {
  listCommittees, listMeetings, startMeeting, completeMeeting, approveMinutes, cancelMeeting,
  listResolutions, listActions, generateAnnualCalendar, populateAgenda,
  startAction, completeAction, cancelAction, deferAction,
  parseAgenda, parseAttendees, parseDecisions, parseActions, parseMembers,
  COM_STATUS_TONE, MTG_STATUS_TONE, OUTCOME_TONE, ACTION_STATUS_TONE, ACTION_PRIORITY_TONE,
  type Committee, type CommitteeMeeting, type Resolution, type ActionItemRecord,
} from '../../lib/committees';

type TabId = 'committees' | 'scheduled' | 'in_progress' | 'completed' | 'approved' | 'cancelled'
           | 'resolutions' | 'actions';

/** Cross-module navigation map for linked action items. The action item's
 *  linkedModule is one of RISK/INCIDENT/COMPLIANCE/AUDIT/BCM/CYBER/PRIVACY/SCM
 *  — we resolve it to the right detail route. */
const MODULE_LINK_BUILDERS: Record<string, (id: number | null, code: string | null) => string | null> = {
  RISK:       (id) => (id ? `/risk/${id}` : '/risk'),
  INCIDENT:   (id) => (id ? `/incidents` : '/incidents'),       // incident detail = /incidents (modal-based)
  COMPLIANCE: ()   => `/compliance`,
  AUDIT:      ()   => `/audit-lc`,
  BCM:        ()   => `/bcm`,
  CYBER:      ()   => `/cyber`,
  PRIVACY:    ()   => `/privacy`,
  SCM:        ()   => `/scm`,
  TPRM:       ()   => `/tprm`,
  POLICY:     ()   => `/policy`,
};

function moduleLink(module: string | null, id: number | null, _code: string | null): string | null {
  if (!module) return null;
  const builder = MODULE_LINK_BUILDERS[module.toUpperCase()];
  return builder ? builder(id, _code) : null;
}

export function CommitteesPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('committees');
  const [committees, setCommittees] = useState<Committee[]>([]);
  const [meetings, setMeetings] = useState<CommitteeMeeting[]>([]);
  const [resolutions, setResolutions] = useState<Resolution[]>([]);
  const [actions, setActions] = useState<ActionItemRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  useEffect(() => { void reload(); }, []);

  async function reload() {
    setLoading(true);
    // Per-call .catch() so partial backend failures don't blank the whole
    // page. A fresh dev DB without V154-V159 bootstrapped returns 503
    // ("Module not yet bootstrapped" from the global handler in PR #117),
    // which used to reject Promise.all and trigger the outer catch ->
    // toast.danger -> "unable to load data" on every tab. Now each tab
    // degrades to an empty list independently. Both endpoints already
    // had this guard for resolutions + actions; this commit extends it
    // to committees + meetings.
    const [c, m, r, a] = await Promise.all([
      listCommittees(undefined, 0, 200).catch(() => ({ content: [] as Committee[], totalElements: 0 })),
      listMeetings(undefined, 0, 200).catch(() => ({ content: [] as CommitteeMeeting[], totalElements: 0 })),
      listResolutions(0, 200).catch(() => ({ content: [] as Resolution[], totalElements: 0 })),
      listActions(0, 200).catch(() => ({ content: [] as ActionItemRecord[], totalElements: 0 })),
    ]);
    setCommittees(c.content);
    setMeetings(m.content);
    setResolutions(r.content);
    setActions(a.content);
    setLoading(false);
  }

  const stats = useMemo(() => ({
    activeCommittees: committees.filter(c => c.status === 'ACTIVE').length,
    upcoming:         meetings.filter(m => m.status === 'SCHEDULED').length,
    inProgress:       meetings.filter(m => m.status === 'IN_PROGRESS').length,
    completed:        meetings.filter(m => m.status === 'COMPLETED').length,
    approved:         meetings.filter(m => m.status === 'MINUTES_APPROVED').length,
    cancelled:        meetings.filter(m => m.status === 'CANCELLED').length,
    resolutions:      resolutions.length,
    openActions:      actions.filter(a => a.status === 'OPEN' || a.status === 'IN_PROGRESS').length,
    overdueActions:   actions.filter(a => a.status === 'OVERDUE').length,
  }), [committees, meetings, resolutions, actions]);

  const tabs: TabItem<TabId>[] = [
    { id: 'committees',  label: `${t('comTabCommittees')} (${committees.length})` },
    { id: 'scheduled',   label: `${t('comTabScheduled')} (${stats.upcoming})` },
    { id: 'in_progress', label: `${t('comTabInProgress')} (${stats.inProgress})` },
    { id: 'completed',   label: `${t('comTabCompleted')} (${stats.completed})` },
    { id: 'approved',    label: `${t('comTabApproved')} (${stats.approved})` },
    { id: 'cancelled',   label: `${t('comTabCancelled')} (${stats.cancelled})` },
    { id: 'resolutions', label: `${t('comTabResolutions')} (${resolutions.length})` },
    { id: 'actions',     label: `${t('comTabActionRegister')} (${stats.openActions + stats.overdueActions})` },
  ];

  const filteredMeetings = useMemo(() => {
    if (tab === 'committees' || tab === 'resolutions' || tab === 'actions') return [];
    if (tab === 'scheduled')   return meetings.filter(m => m.status === 'SCHEDULED');
    if (tab === 'in_progress') return meetings.filter(m => m.status === 'IN_PROGRESS');
    if (tab === 'completed')   return meetings.filter(m => m.status === 'COMPLETED');
    if (tab === 'approved')    return meetings.filter(m => m.status === 'MINUTES_APPROVED');
    if (tab === 'cancelled')   return meetings.filter(m => m.status === 'CANCELLED');
    return [];
  }, [tab, meetings]);

  const committeeNameFor = (committeeId: number): string => {
    const c = committees.find(x => x.id === committeeId);
    if (!c) return `#${committeeId}`;
    return lang === 'ar' && c.nameAr ? c.nameAr : c.nameEn;
  };

  function toggle(key: string) {
    const n = new Set(expanded);
    if (n.has(key)) n.delete(key); else n.add(key);
    setExpanded(n);
  }

  async function onStart(id: number) {
    setBusyId(id);
    try { await startMeeting(id); toast.success(t('comMtgStarted')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onComplete(id: number) {
    setBusyId(id);
    try { await completeMeeting(id); toast.success(t('comMtgCompleted')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onApproveMinutes(id: number) {
    setBusyId(id);
    try { await approveMinutes(id); toast.success(t('comMinutesApproved')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onCancel(id: number) {
    const reason = window.prompt(t('comCancelPrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await cancelMeeting(id, reason.trim()); toast.success(t('comMtgCancelled')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }

  // ── Annual calendar generation (per-committee) ───────────────────────
  async function onGenerateCalendar(committeeId: number) {
    const yearStr = window.prompt(t('comCalendarPrompt'), String(new Date().getFullYear() + 1));
    if (!yearStr) return;
    const year = parseInt(yearStr.trim(), 10);
    if (!Number.isFinite(year) || year < 2020 || year > 2100) {
      toast.danger(t('comActionFailed'));
      return;
    }
    setBusyId(committeeId);
    try {
      const res = await generateAnnualCalendar(committeeId, year);
      toast.success(t('comCalendarGenerated').replace('{n}', String(res.created)).replace('{year}', String(year)));
      await reload();
    } catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }

  // ── Agenda auto-populator (per-meeting) ──────────────────────────────
  async function onPopulateAgenda(meetingId: number) {
    setBusyId(meetingId);
    try {
      await populateAgenda(meetingId);
      toast.success(t('comAgendaPopulated'));
      await reload();
    } catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }

  // ── Action item lifecycle ────────────────────────────────────────────
  async function onActionStart(id: number) {
    setBusyId(id);
    try { await startAction(id); toast.success(t('comActStarted')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onActionComplete(id: number) {
    const notes = window.prompt(t('comActCompletePrompt'), '');
    if (notes == null) return;
    setBusyId(id);
    try { await completeAction(id, notes.trim()); toast.success(t('comActCompleted')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onActionDefer(id: number) {
    const newDate = window.prompt(t('comActDeferPrompt'), '');
    if (!newDate || !/^\d{4}-\d{2}-\d{2}$/.test(newDate.trim())) return;
    const reason = window.prompt(t('comActDeferReason'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await deferAction(id, newDate.trim(), reason.trim()); toast.success(t('comActDeferred')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onActionCancel(id: number) {
    const reason = window.prompt(t('comActCancelPrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await cancelAction(id, reason.trim()); toast.success(t('comActCancelled')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }

  return (
    <Shell title={t('comPageTitle')} sub={t('comPageSub')}>
      <PageHeader eyebrow={t('navAdministration')} title={t('comPageTitle')} sub={t('comPageSub')} />

      <div className="kpi-grid">
        <Kpi Icon={Landmark}    label={t('comKpiActive')}     value={stats.activeCommittees} tone="success" />
        <Kpi Icon={Calendar}    label={t('comKpiUpcoming')}   value={stats.upcoming}         tone="primary" />
        <Kpi Icon={Gavel}       label={t('comTabResolutions')} value={resolutions.length}    tone="info" />
        <Kpi Icon={ListChecks}  label={t('comTabActionRegister')} value={stats.openActions}  tone={stats.overdueActions > 0 ? 'danger' : 'warning'} />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'committees' && (
        <Card title={t('comTabCommittees')} Icon={Landmark}>
          {loading
            ? <EmptyState Icon={Landmark} title={t('loading')} />
            : committees.length === 0
              ? <EmptyState Icon={Landmark} title={t('comEmpty')} sub={t('comEmptyHint')} />
              : (
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th style={{ width: 28 }} />
                      <th>{t('comColCode')}</th>
                      <th>{t('comColName')}</th>
                      <th>{t('comColType')}</th>
                      <th>{t('comColChair')}</th>
                      <th>{t('comColCadence')}</th>
                      <th>{t('comColQuorum')}</th>
                      <th>{t('scnColStatus')}</th>
                      <th style={{ textAlign: 'right' }}>{t('scnColActions')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {committees.map(c => {
                      const k = `c-${c.id}`;
                      const open = expanded.has(k);
                      const members = parseMembers(c.membersJson);
                      return (
                        <Fragment key={c.id}>
                          <tr>
                            <td>
                              <button onClick={() => toggle(k)}
                                style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                                {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                              </button>
                            </td>
                            <td className="mono">{c.code}</td>
                            <td>{lang === 'ar' && c.nameAr ? c.nameAr : c.nameEn}</td>
                            <td>{c.committeeType.replace(/_/g, ' ')}</td>
                            <td>{c.chairpersonName ?? '—'}</td>
                            <td>{c.meetingCadence}</td>
                            <td className="mono">{c.quorumPct != null ? `${c.quorumPct}%` : '—'}</td>
                            <td><Badge tone={COM_STATUS_TONE[c.status]}>{statusLabel(c.status, lang)}</Badge></td>
                            <td style={{ textAlign: 'right' }}>
                              <Link to={`/committees/${c.id}/members`}
                                style={{ display:'inline-flex', alignItems:'center', gap:4, marginInlineEnd:8, fontSize:12, color:'var(--primary)' }}>
                                <Users size={12} />
                                {t('comMembers')}
                              </Link>
                              <Button size="sm" variant="secondary" Icon={Calendar}
                                disabled={busyId === c.id || c.status !== 'ACTIVE'}
                                onClick={() => onGenerateCalendar(c.id)}>
                                {t('comCalendarGenerate')}
                              </Button>
                            </td>
                          </tr>
                          {open && (
                            <tr>
                              <td colSpan={9} style={{ background: 'var(--muted)' }}>
                                <div style={{ padding: 12, fontSize: 13 }}>
                                  {c.descriptionEn && (
                                    <p style={{ marginBottom: 8 }}>
                                      {lang === 'ar' && c.descriptionAr ? c.descriptionAr : c.descriptionEn}
                                    </p>
                                  )}
                                  {c.charterEn && (
                                    <p style={{ marginBottom: 8 }}>
                                      <strong>{t('comCharter')}:</strong>{' '}
                                      {lang === 'ar' && c.charterAr ? c.charterAr : c.charterEn}
                                    </p>
                                  )}
                                  {members.length > 0 && (
                                    <div>
                                      <strong>{t('comMembers')} ({members.length})</strong>
                                      <ul style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                        {members.map((m, idx) => (
                                          <li key={idx}>
                                            {m.name} — {m.role}{m.votingMember ? ` · ${t('comVoting')}` : ''}
                                          </li>
                                        ))}
                                      </ul>
                                    </div>
                                  )}
                                  {c.dissolvedReason && (
                                    <p style={{ marginTop: 8, color: 'var(--danger)' }}>
                                      <strong>{t('comDissolved')}:</strong> {c.dissolvedReason}
                                    </p>
                                  )}
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              )}
        </Card>
      )}

      {(tab === 'scheduled' || tab === 'in_progress' || tab === 'completed' || tab === 'approved' || tab === 'cancelled') && (
        <Card title={t('comTabMeetings')} Icon={Calendar}>
          {loading
            ? <EmptyState Icon={Calendar} title={t('loading')} />
            : filteredMeetings.length === 0
              ? <EmptyState Icon={Calendar} title={t('comMtgEmpty')} />
              : (
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th style={{ width: 28 }} />
                      <th>{t('comMtgCode')}</th>
                      <th>{t('comMtgCommittee')}</th>
                      <th>{t('comMtgTitle')}</th>
                      <th>{t('comMtgScheduled')}</th>
                      <th>{t('scnColStatus')}</th>
                      <th style={{ textAlign: 'right' }}>{t('scnColActions')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredMeetings.map(m => {
                      const k = `m-${m.id}`;
                      const open = expanded.has(k);
                      const agenda = parseAgenda(m.agendaJson);
                      const attendees = parseAttendees(m.attendeesJson);
                      const decisions = parseDecisions(m.decisionsJson);
                      const acts = parseActions(m.actionItemsJson);
                      return (
                        <Fragment key={m.id}>
                          <tr>
                            <td>
                              <button onClick={() => toggle(k)}
                                style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                                {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                              </button>
                            </td>
                            <td className="mono">{m.meetingCode}</td>
                            <td>{m.committeeNameEn ?? m.committeeCode}</td>
                            <td>{lang === 'ar' && m.titleAr ? m.titleAr : m.titleEn}</td>
                            <td className="mono">{new Date(m.scheduledAt).toLocaleString()}</td>
                            <td><Badge tone={MTG_STATUS_TONE[m.status]}>{m.status.replace('_',' ')}</Badge></td>
                            <td style={{ textAlign: 'right' }}>
                              {m.status === 'SCHEDULED' && (
                                <>
                                  <Button size="sm" variant="secondary" Icon={RefreshCw}
                                    disabled={busyId === m.id} onClick={() => onPopulateAgenda(m.id)}>
                                    {t('comAgendaPopulate')}
                                  </Button>
                                  {' '}
                                  <Button size="sm" variant="primary" Icon={Play}
                                    disabled={busyId === m.id} onClick={() => onStart(m.id)}>
                                    {t('comStart')}
                                  </Button>
                                  {' '}
                                  <Button size="sm" variant="danger" Icon={XCircle}
                                    disabled={busyId === m.id} onClick={() => onCancel(m.id)}>
                                    {t('comCancel')}
                                  </Button>
                                </>
                              )}
                              {m.status === 'IN_PROGRESS' && (
                                <Button size="sm" variant="primary" Icon={CheckCircle2}
                                  disabled={busyId === m.id} onClick={() => onComplete(m.id)}>
                                  {t('comComplete')}
                                </Button>
                              )}
                              {m.status === 'COMPLETED' && (
                                <Button size="sm" variant="primary" Icon={FileCheck2}
                                  disabled={busyId === m.id} onClick={() => onApproveMinutes(m.id)}>
                                  {t('comApproveMinutes')}
                                </Button>
                              )}
                            </td>
                          </tr>
                          {open && (
                            <tr>
                              <td colSpan={7} style={{ background: 'var(--muted)' }}>
                                <div style={{ padding: 12, fontSize: 13 }}>
                                  {m.locationOrLink && (
                                    <p><strong>{t('comLocation')}:</strong> {m.locationOrLink}</p>
                                  )}
                                  {m.chairName && (
                                    <p><strong>{t('comChair')}:</strong> {m.chairName}
                                    {' · '}<strong>{t('comSecretary')}:</strong> {m.secretaryName}</p>
                                  )}
                                  {agenda.length > 0 && (
                                    <div style={{ marginTop: 8 }}>
                                      <strong>{t('comAgenda')}</strong>
                                      <ol style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                        {agenda.map(a => (
                                          <li key={a.seq}>
                                            {lang === 'ar' && a.topicAr ? a.topicAr : a.topicEn}
                                            {a.presenter && <span style={{ color:'var(--ink-3)' }}> — {a.presenter}</span>}
                                            {a.minutesAllocated && <span className="mono"> ({a.minutesAllocated}m)</span>}
                                          </li>
                                        ))}
                                      </ol>
                                    </div>
                                  )}
                                  {attendees.length > 0 && (
                                    <div style={{ marginTop: 8 }}>
                                      <strong>{t('comAttendees')}</strong>
                                      <ul style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                        {attendees.map((a, i) => (
                                          <li key={i}>
                                            <Users size={12} style={{ display:'inline', marginInlineEnd:4 }}/>
                                            {a.name} — {a.role}
                                            {' · '}
                                            {a.present === true ? '✓ present' : a.present === false ? '✗ absent' : '—'}
                                          </li>
                                        ))}
                                      </ul>
                                    </div>
                                  )}
                                  {m.minutesEn && (
                                    <p style={{ marginTop: 8 }}>
                                      <strong>{t('comMinutes')}:</strong>{' '}
                                      {lang === 'ar' && m.minutesAr ? m.minutesAr : m.minutesEn}
                                    </p>
                                  )}
                                  {decisions.length > 0 && (
                                    <div style={{ marginTop: 8 }}>
                                      <strong>{t('comDecisions')}</strong>
                                      <ol style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                        {decisions.map(d => (
                                          <li key={d.seq}>
                                            {lang === 'ar' && d.decisionAr ? d.decisionAr : d.decisionEn}
                                            {d.outcome && <Badge tone={d.outcome === 'APPROVED' ? 'success' : 'danger'}> {d.outcome}</Badge>}
                                            {d.voteFor != null && (
                                              <span style={{ color:'var(--ink-3)' }}>
                                                {' · '}{d.voteFor}/{(d.voteFor ?? 0) + (d.voteAgainst ?? 0) + (d.voteAbstain ?? 0)}
                                              </span>
                                            )}
                                          </li>
                                        ))}
                                      </ol>
                                    </div>
                                  )}
                                  {acts.length > 0 && (
                                    <div style={{ marginTop: 8 }}>
                                      <strong>{t('comActions')}</strong>
                                      <ol style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                        {acts.map(a => (
                                          <li key={a.seq}>
                                            {a.actionEn}
                                            {a.owner && <span style={{ color:'var(--ink-3)' }}> — {a.owner}</span>}
                                            {a.dueDate && <span className="mono"> · due {a.dueDate}</span>}
                                            {a.status && <Badge tone={a.status === 'COMPLETED' ? 'success' : a.status === 'IN_PROGRESS' ? 'primary' : 'info'}> {a.status}</Badge>}
                                          </li>
                                        ))}
                                      </ol>
                                    </div>
                                  )}
                                  {m.cancelReason && (
                                    <p style={{ marginTop: 8, color: 'var(--danger)' }}>
                                      <strong>{t('comCancelReason')}:</strong> {m.cancelReason}
                                    </p>
                                  )}
                                  {m.approverName && (
                                    <p style={{ marginTop: 8, color: 'var(--success)' }}>
                                      <strong>{t('comApprovedBy')}:</strong> {m.approverName}
                                    </p>
                                  )}
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              )}
        </Card>
      )}

      {/* ── Resolutions register ─────────────────────────────────────── */}
      {tab === 'resolutions' && (
        <Card title={t('comTabResolutions')} Icon={Gavel}>
          {loading
            ? <EmptyState Icon={Gavel} title={t('loading')} />
            : resolutions.length === 0
              ? <EmptyState Icon={Gavel} title={t('comResEmpty')} sub={t('comResEmptyHint')} />
              : (
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th style={{ width: 28 }} />
                      <th>{t('comResColCode')}</th>
                      <th>{t('comResColTitle')}</th>
                      <th>{t('comResColCommittee')}</th>
                      <th>{t('comResColOutcome')}</th>
                      <th>{t('comResColVote')}</th>
                      <th>{t('comResColEffectiveFrom')}</th>
                      <th>{t('comResColReviewBy')}</th>
                      <th>{t('comResColConfidentiality')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {resolutions.map(r => {
                      const k = `r-${r.id}`;
                      const open = expanded.has(k);
                      const totalVotes = r.voteFor + r.voteAgainst + r.voteAbstain;
                      const hasDissent = (r.dissentNotesEn ?? '').trim().length > 0
                                      || (r.dissentNotesAr ?? '').trim().length > 0;
                      return (
                        <Fragment key={r.id}>
                          <tr>
                            <td>
                              <button onClick={() => toggle(k)}
                                style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                                {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                              </button>
                            </td>
                            <td className="mono">{r.resolutionCode}</td>
                            <td>
                              {lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}
                              {hasDissent && (
                                <span title={t('comResDissent')} style={{ marginInlineStart: 6 }}>
                                  <AlertTriangle size={14} style={{ color: 'var(--warning)', display: 'inline' }} />
                                </span>
                              )}
                              {r.regulatorNotify === 1 && (
                                <span title={t('comResRegulatorNotify')} style={{ marginInlineStart: 4 }}>
                                  <ShieldAlert size={14} style={{ color: 'var(--danger)', display: 'inline' }} />
                                </span>
                              )}
                            </td>
                            <td>{committeeNameFor(r.committeeId)}</td>
                            <td><Badge tone={OUTCOME_TONE[r.outcome]}>{r.outcome}</Badge></td>
                            <td className="mono">{r.voteFor} / {r.voteAgainst} / {r.voteAbstain}
                              {totalVotes > 0 && <span style={{ color:'var(--ink-3)' }}> ({totalVotes})</span>}
                            </td>
                            <td className="mono">{r.effectiveFrom ?? '—'}</td>
                            <td className="mono">{r.reviewRequiredBy ?? '—'}</td>
                            <td><Badge tone={r.confidentiality === 'RESTRICTED' ? 'danger'
                                            : r.confidentiality === 'CONFIDENTIAL' ? 'warning'
                                            : r.confidentiality === 'INTERNAL' ? 'info' : 'neutral'}>
                                  {r.confidentiality}
                                </Badge></td>
                          </tr>
                          {open && (
                            <tr>
                              <td colSpan={9} style={{ background: 'var(--muted)' }}>
                                <div style={{ padding: 12, fontSize: 13 }}>
                                  <p style={{ marginBottom: 8 }}>
                                    {lang === 'ar' && r.bodyAr ? r.bodyAr : r.bodyEn}
                                  </p>
                                  {r.proposedBy && (
                                    <p><strong>{t('comResProposedBy')}:</strong> {r.proposedBy}
                                      {r.secondedBy && <>{' · '}<strong>{t('comResSecondedBy')}:</strong> {r.secondedBy}</>}
                                    </p>
                                  )}
                                  {hasDissent && (
                                    <p style={{ marginTop: 8, color: 'var(--warning)' }}>
                                      <strong>{t('comResDissent')}:</strong>{' '}
                                      {lang === 'ar' && r.dissentNotesAr ? r.dissentNotesAr : r.dissentNotesEn}
                                    </p>
                                  )}
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              )}
        </Card>
      )}

      {/* ── Action items register ────────────────────────────────────── */}
      {tab === 'actions' && (
        <Card title={t('comTabActionRegister')} Icon={ClipboardList}>
          {loading
            ? <EmptyState Icon={ClipboardList} title={t('loading')} />
            : actions.length === 0
              ? <EmptyState Icon={ClipboardList} title={t('comActEmpty')} sub={t('comActEmptyHint')} />
              : (
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th style={{ width: 28 }} />
                      <th>{t('comActColCode')}</th>
                      <th>{t('comActColTitle')}</th>
                      <th>{t('comActColOwner')}</th>
                      <th>{t('comActColDue')}</th>
                      <th>{t('comActColPriority')}</th>
                      <th>{t('comActColStatus')}</th>
                      <th>{t('comActColLink')}</th>
                      <th style={{ textAlign: 'right' }}>{t('scnColActions')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {actions.map(a => {
                      const k = `a-${a.id}`;
                      const open = expanded.has(k);
                      const link = moduleLink(a.linkedModule, a.linkedEntityId, a.linkedEntityCode);
                      const isOverdue = a.status === 'OVERDUE';
                      return (
                        <Fragment key={a.id}>
                          <tr style={isOverdue ? { background: 'rgba(220, 38, 38, 0.04)' } : undefined}>
                            <td>
                              <button onClick={() => toggle(k)}
                                style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                                {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                              </button>
                            </td>
                            <td className="mono">{a.actionCode}</td>
                            <td>{lang === 'ar' && a.titleAr ? a.titleAr : a.titleEn}</td>
                            <td>{a.ownerName}{a.ownerRole && <span style={{ color:'var(--ink-3)' }}> — {a.ownerRole}</span>}</td>
                            <td className="mono">{a.dueDate}</td>
                            <td><Badge tone={ACTION_PRIORITY_TONE[a.priority]}>{a.priority}</Badge></td>
                            <td><Badge tone={ACTION_STATUS_TONE[a.status]}>{a.status.replace('_',' ')}</Badge></td>
                            <td>
                              {a.linkedModule && a.linkedEntityCode && link ? (
                                <Link to={link} className="mono"
                                  style={{ display:'inline-flex', alignItems:'center', gap:4 }}>
                                  {a.linkedModule}:{a.linkedEntityCode}
                                  <ExternalLink size={11} />
                                </Link>
                              ) : a.linkedModule ? (
                                <span className="mono" style={{ color:'var(--ink-3)' }}>
                                  {a.linkedModule}{a.linkedEntityCode ? `:${a.linkedEntityCode}` : ''}
                                </span>
                              ) : <span style={{ color:'var(--ink-3)' }}>—</span>}
                            </td>
                            <td style={{ textAlign: 'right' }}>
                              {a.status === 'OPEN' && (
                                <Button size="sm" variant="primary" Icon={Play}
                                  disabled={busyId === a.id} onClick={() => onActionStart(a.id)}>
                                  {t('comActStart')}
                                </Button>
                              )}
                              {(a.status === 'OPEN' || a.status === 'IN_PROGRESS' || a.status === 'OVERDUE') && (
                                <>
                                  {' '}
                                  <Button size="sm" variant="primary" Icon={CheckCircle2}
                                    disabled={busyId === a.id} onClick={() => onActionComplete(a.id)}>
                                    {t('comActComplete')}
                                  </Button>
                                  {' '}
                                  <Button size="sm" variant="secondary" Icon={Calendar}
                                    disabled={busyId === a.id} onClick={() => onActionDefer(a.id)}>
                                    {t('comActDefer')}
                                  </Button>
                                  {' '}
                                  <Button size="sm" variant="danger" Icon={XCircle}
                                    disabled={busyId === a.id} onClick={() => onActionCancel(a.id)}>
                                    {t('comActCancelBtn')}
                                  </Button>
                                </>
                              )}
                            </td>
                          </tr>
                          {open && (
                            <tr>
                              <td colSpan={9} style={{ background: 'var(--muted)' }}>
                                <div style={{ padding: 12, fontSize: 13 }}>
                                  {(a.descriptionEn || a.descriptionAr) && (
                                    <p style={{ marginBottom: 8 }}>
                                      {lang === 'ar' && a.descriptionAr ? a.descriptionAr : a.descriptionEn}
                                    </p>
                                  )}
                                  <p style={{ color:'var(--ink-3)' }}>
                                    {committeeNameFor(a.committeeId)}
                                    {' · '} meeting #{a.meetingId}
                                    {a.carriedFromMeetingId && <> {' · '} carried from meeting #{a.carriedFromMeetingId}</>}
                                  </p>
                                  {a.completionNotesEn && (
                                    <p style={{ marginTop: 8, color: 'var(--success)' }}>
                                      <strong>Completion notes:</strong> {a.completionNotesEn}
                                      {a.completedAt && <span style={{ color:'var(--ink-3)' }}> · {new Date(a.completedAt).toLocaleString()}</span>}
                                    </p>
                                  )}
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              )}
        </Card>
      )}
    </Shell>
  );
}

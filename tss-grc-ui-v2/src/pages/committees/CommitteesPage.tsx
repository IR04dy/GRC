import { useEffect, useMemo, useState, Fragment } from 'react';
import {
  Landmark, CheckCircle2, XCircle, Calendar, Play, FileCheck2, Users,
  ChevronRight, ChevronDown,
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
  parseAgenda, parseAttendees, parseDecisions, parseActions, parseMembers,
  COM_STATUS_TONE, MTG_STATUS_TONE,
  type Committee, type CommitteeMeeting,
} from '../../lib/committees';

type TabId = 'committees' | 'scheduled' | 'in_progress' | 'completed' | 'approved' | 'cancelled';

export function CommitteesPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('committees');
  const [committees, setCommittees] = useState<Committee[]>([]);
  const [meetings, setMeetings] = useState<CommitteeMeeting[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  useEffect(() => { void reload(); }, []);

  async function reload() {
    setLoading(true);
    try {
      const [c, m] = await Promise.all([
        listCommittees(undefined, 0, 200),
        listMeetings(undefined, 0, 200),
      ]);
      setCommittees(c.content);
      setMeetings(m.content);
    } catch { toast.danger(t('comLoadFailed')); }
    finally { setLoading(false); }
  }

  const stats = useMemo(() => ({
    activeCommittees: committees.filter(c => c.status === 'ACTIVE').length,
    upcoming:         meetings.filter(m => m.status === 'SCHEDULED').length,
    inProgress:       meetings.filter(m => m.status === 'IN_PROGRESS').length,
    completed:        meetings.filter(m => m.status === 'COMPLETED').length,
    approved:         meetings.filter(m => m.status === 'MINUTES_APPROVED').length,
    cancelled:        meetings.filter(m => m.status === 'CANCELLED').length,
  }), [committees, meetings]);

  const tabs: TabItem<TabId>[] = [
    { id: 'committees',  label: `${t('comTabCommittees')} (${committees.length})` },
    { id: 'scheduled',   label: `${t('comTabScheduled')} (${stats.upcoming})` },
    { id: 'in_progress', label: `${t('comTabInProgress')} (${stats.inProgress})` },
    { id: 'completed',   label: `${t('comTabCompleted')} (${stats.completed})` },
    { id: 'approved',    label: `${t('comTabApproved')} (${stats.approved})` },
    { id: 'cancelled',   label: `${t('comTabCancelled')} (${stats.cancelled})` },
  ];

  const filteredMeetings = useMemo(() => {
    if (tab === 'committees') return [];
    if (tab === 'scheduled')   return meetings.filter(m => m.status === 'SCHEDULED');
    if (tab === 'in_progress') return meetings.filter(m => m.status === 'IN_PROGRESS');
    if (tab === 'completed')   return meetings.filter(m => m.status === 'COMPLETED');
    if (tab === 'approved')    return meetings.filter(m => m.status === 'MINUTES_APPROVED');
    if (tab === 'cancelled')   return meetings.filter(m => m.status === 'CANCELLED');
    return [];
  }, [tab, meetings]);

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

  return (
    <Shell title={t('comPageTitle')} sub={t('comPageSub')}>
      <PageHeader eyebrow={t('navAdministration')} title={t('comPageTitle')} sub={t('comPageSub')} />

      <div className="kpi-grid">
        <Kpi Icon={Landmark}   label={t('comKpiActive')}     value={stats.activeCommittees} tone="success" />
        <Kpi Icon={Calendar}   label={t('comKpiUpcoming')}   value={stats.upcoming}         tone="primary" />
        <Kpi Icon={Play}       label={t('comKpiInProgress')} value={stats.inProgress}       tone="warning" />
        <Kpi Icon={FileCheck2} label={t('comKpiApproved')}   value={stats.approved}         tone="success" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'committees' ? (
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
                          </tr>
                          {open && (
                            <tr>
                              <td colSpan={8} style={{ background: 'var(--muted)' }}>
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
      ) : (
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
                      const actions = parseActions(m.actionItemsJson);
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
                                            {a.presenter && <span style={{ color:'var(--muted-foreground)' }}> — {a.presenter}</span>}
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
                                              <span style={{ color:'var(--muted-foreground)' }}>
                                                {' · '}{d.voteFor}/{(d.voteFor ?? 0) + (d.voteAgainst ?? 0) + (d.voteAbstain ?? 0)}
                                              </span>
                                            )}
                                          </li>
                                        ))}
                                      </ol>
                                    </div>
                                  )}
                                  {actions.length > 0 && (
                                    <div style={{ marginTop: 8 }}>
                                      <strong>{t('comActions')}</strong>
                                      <ol style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                        {actions.map(a => (
                                          <li key={a.seq}>
                                            {a.actionEn}
                                            {a.owner && <span style={{ color:'var(--muted-foreground)' }}> — {a.owner}</span>}
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
    </Shell>
  );
}

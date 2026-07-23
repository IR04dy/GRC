import { useEffect, useState } from 'react';
import { Plus, Trash2, Users } from 'lucide-react';
import { Modal } from '../../components/ui/Modal';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  recordAttendance, getQuorum,
  type CommitteeMeeting, type AttendeeRow, type QuorumView,
} from '../../lib/committees';

/**
 * CH-14: record a committee meeting's attendance and see the DETERMINISTIC
 * quorum (present voting members vs the committee's quorum_pct). Minutes cannot
 * be approved for a meeting that did not reach quorum, so this is where the
 * secretary captures who attended (and any conflict-of-interest declaration).
 */
export function AttendanceModal({ meeting, onClose, onSaved }: {
  meeting: CommitteeMeeting;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { t } = useI18n();
  const toast = useToast();
  const [rows, setRows] = useState<AttendeeRow[]>([]);
  const [quorum, setQuorum] = useState<QuorumView | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getQuorum(meeting.id)
      .then(q => { setQuorum(q); if (q.attendees?.length) setRows(q.attendees); else seedRow(); })
      .catch(() => seedRow());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [meeting.id]);

  function seedRow() { setRows([{ memberName: '', memberRole: '', votingMember: true, present: true, coiDeclared: false }]); }
  function addRow() { setRows(r => [...r, { memberName: '', memberRole: '', votingMember: true, present: true, coiDeclared: false }]); }
  function update(i: number, patch: Partial<AttendeeRow>) { setRows(r => r.map((row, idx) => idx === i ? { ...row, ...patch } : row)); }
  function remove(i: number) { setRows(r => r.filter((_, idx) => idx !== i)); }

  async function save() {
    const clean = rows.filter(r => (r.memberName ?? '').trim());
    setSaving(true);
    try {
      const q = await recordAttendance(meeting.id, clean);
      setQuorum(q);
      toast.success(t('comAttendanceSaved'));
      onSaved();
    } catch (e) {
      toast.danger(e instanceof Error ? e.message : 'Action failed');
    } finally { setSaving(false); }
  }

  return (
    <Modal
      open
      title={`${t('comAttendanceQuorum')} · ${meeting.meetingCode}`}
      size="lg"
      onClose={onClose}
      footer={
        <div style={{ display: 'flex', gap: 8, justifyContent: 'space-between', width: '100%', alignItems: 'center' }}>
          <QuorumBadge quorum={quorum} t={t} />
          <div style={{ display: 'flex', gap: 8 }}>
            <Button variant="ghost" onClick={onClose}>{t('cancel')}</Button>
            <Button variant="primary" onClick={save} disabled={saving}>{saving ? t('saving') : t('save')}</Button>
          </div>
        </div>
      }
    >
      <div style={{ overflowX: 'auto' }}>
        <table className="com-attendance-table" style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
          <thead>
            <tr>
              <th style={thStyle}>{t('comAttendee')}</th>
              <th style={thStyle}>{t('comRole')}</th>
              <th style={{ ...thStyle, textAlign: 'center' }}>{t('comVoting')}</th>
              <th style={{ ...thStyle, textAlign: 'center' }}>{t('comPresent')}</th>
              <th style={{ ...thStyle, textAlign: 'center' }}>{t('comCoi')}</th>
              <th style={thStyle} />
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
              <tr key={i}>
                <td style={tdStyle}>
                  <input value={r.memberName ?? ''} onChange={e => update(i, { memberName: e.target.value })}
                    placeholder={t('comAttendee')} style={inputStyle} />
                </td>
                <td style={tdStyle}>
                  <input value={r.memberRole ?? ''} onChange={e => update(i, { memberRole: e.target.value })} style={inputStyle} />
                </td>
                <td style={{ ...tdStyle, textAlign: 'center' }}>
                  <input type="checkbox" checked={r.votingMember ?? true} onChange={e => update(i, { votingMember: e.target.checked })} />
                </td>
                <td style={{ ...tdStyle, textAlign: 'center' }}>
                  <input type="checkbox" checked={r.present ?? false} onChange={e => update(i, { present: e.target.checked })} />
                </td>
                <td style={{ ...tdStyle, textAlign: 'center' }}>
                  <input type="checkbox" checked={r.coiDeclared ?? false} onChange={e => update(i, { coiDeclared: e.target.checked })} />
                </td>
                <td style={{ ...tdStyle, textAlign: 'center' }}>
                  <button onClick={() => remove(i)} title={t('deleteBtn')}
                    style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: 'var(--danger-fg)' }}>
                    <Trash2 size={14} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div style={{ marginTop: 10 }}>
        <Button variant="ghost" size="sm" Icon={Plus} onClick={addRow}>{t('comAddAttendee')}</Button>
      </div>
    </Modal>
  );
}

function QuorumBadge({ quorum, t }: { quorum: QuorumView | null; t: (k: string) => string }) {
  if (!quorum) return <span style={{ fontSize: 12, color: 'var(--ink-3)' }}><Users size={13} /> —</span>;
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 12, color: 'var(--ink-2)' }}>
      <Users size={13} />
      {quorum.presentVoting}/{quorum.totalVoting} · {t('comQuorumRequired')} {quorum.requiredVoting} ({quorum.quorumPct}%)
      <Badge tone={quorum.quorumMet ? 'success' : 'danger'}>
        {quorum.quorumMet ? t('comQuorumMet') : t('comQuorumNotMet')}
      </Badge>
    </span>
  );
}

const thStyle: React.CSSProperties = { textAlign: 'start', padding: '6px 8px', borderBottom: '1px solid var(--border)', fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--ink-3)' };
const tdStyle: React.CSSProperties = { padding: '6px 8px', borderBottom: '1px solid var(--border)' };
const inputStyle: React.CSSProperties = { width: '100%', padding: '6px 8px', fontSize: 13, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)' };

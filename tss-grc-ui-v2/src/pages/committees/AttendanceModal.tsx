import { useEffect, useState } from 'react';
import { Plus, Trash2, Users, ShieldAlert, Link2Off } from 'lucide-react';
import { Modal } from '../../components/ui/Modal';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  recordAttendance, getQuorum, listActiveMembers,
  type CommitteeMeeting, type CommitteeMemberTerm, type AttendanceMark, type QuorumView,
} from '../../lib/committees';

/** One editable line: a roster seat's presence. Identity is fixed by the seat. */
interface SeatLine {
  termId: number;
  memberName: string;
  role: string;
  voting: boolean;
  linked: boolean;
  present: boolean;
  coiDeclared: boolean;
  coiNote: string;
}

/** A non-member who attended. Recorded for the minutes; never counts toward quorum. */
interface GuestLine {
  name: string;
  role: string;
  present: boolean;
  coiDeclared: boolean;
}

/**
 * Record a committee meeting's attendance and see the derived quorum.
 *
 * <b>F2-28 / DF-5 — this used to be a free-text form, and that was the defect.</b> The secretary
 * typed a name, ticked "voting" and ticked "present", and the quorum denominator was a count of
 * those rows: invent six voting attendees, mark four present, and a 67% committee is quorate. A
 * committee of seven where three attend and only those three get typed in read as 3/3.
 *
 * So attendance is now a <b>selection from the roster</b>. Every active seat is listed; the
 * secretary marks presence and any conflict of interest. Names and voting eligibility are the
 * roster's, not the form's. A seat left unticked is <em>absent</em> and still counts in the
 * denominator, which is what quorum means. Non-members who were in the room are recorded
 * explicitly as guests, so recording reality never requires inflating the denominator.
 *
 * Minutes cannot be approved for a meeting that did not reach quorum, and — since F2-39 —
 * cannot be approved by whoever recorded this sheet.
 */
export function AttendanceModal({ meeting, onClose, onSaved }: {
  meeting: CommitteeMeeting;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [seats, setSeats] = useState<SeatLine[]>([]);
  const [guests, setGuests] = useState<GuestLine[]>([]);
  const [quorum, setQuorum] = useState<QuorumView | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  // Once the minutes are approved the sheet is part of an e-signed record; the server refuses
  // to replace it, so the form is read-only rather than failing on save.
  const locked = meeting.status === 'MINUTES_APPROVED' || meeting.status === 'CANCELLED';

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([
      listActiveMembers(meeting.committeeId).catch(() => [] as CommitteeMemberTerm[]),
      getQuorum(meeting.id).catch(() => null as QuorumView | null),
    ]).then(([roster, q]) => {
      if (cancelled) return;
      setQuorum(q);
      const marked = new Map((q?.attendees ?? [])
        .filter(a => a.memberTermId != null)
        .map(a => [a.memberTermId as number, a]));
      setSeats(roster.map(s => {
        const prior = marked.get(s.id);
        return {
          termId: s.id,
          memberName: s.memberName,
          role: s.role,
          voting: s.isVotingMember === 1,
          linked: s.memberUserId != null,
          present: prior ? !!prior.present : false,
          coiDeclared: prior ? !!prior.coiDeclared : false,
          coiNote: prior?.coiNote ?? '',
        };
      }));
      setGuests((q?.attendees ?? []).filter(a => a.guest).map(a => ({
        name: a.memberName, role: a.memberRole ?? '',
        present: !!a.present, coiDeclared: !!a.coiDeclared,
      })));
    }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [meeting.id, meeting.committeeId]);

  function patchSeat(termId: number, patch: Partial<SeatLine>) {
    setSeats(rows => rows.map(r => r.termId === termId ? { ...r, ...patch } : r));
  }
  function addGuest() {
    setGuests(g => [...g, { name: '', role: '', present: true, coiDeclared: false }]);
  }
  function patchGuest(i: number, patch: Partial<GuestLine>) {
    setGuests(g => g.map((row, idx) => idx === i ? { ...row, ...patch } : row));
  }
  function removeGuest(i: number) {
    setGuests(g => g.filter((_, idx) => idx !== i));
  }

  // Live preview of the arithmetic the server will do, so the secretary sees the consequence of
  // a tick before saving. The server recomputes from the roster regardless — this is a mirror,
  // never the source.
  const votingSeats = seats.filter(s => s.voting).length;
  const presentVoting = seats.filter(s => s.voting && s.present).length;
  const pct = quorum?.quorumPct ?? 50;
  const required = votingSeats === 0 ? 0 : Math.max(1, Math.ceil(votingSeats * pct / 100));
  const wouldBeQuorate = votingSeats > 0 && presentVoting >= required;

  async function save() {
    const named = guests.filter(g => g.name.trim());
    const marks: AttendanceMark[] = [
      // Every seat is sent, present or not: an explicit absence is a better record than a
      // missing row, even though the server treats an unsent seat as absent anyway.
      ...seats.map(s => ({
        memberTermId: s.termId,
        present: s.present,
        coiDeclared: s.coiDeclared,
        coiNote: s.coiNote.trim() || null,
      })),
      ...named.map(g => ({
        guestName: g.name.trim(),
        guestRole: g.role.trim() || null,
        present: g.present,
        coiDeclared: g.coiDeclared,
      })),
    ];
    setSaving(true);
    try {
      const q = await recordAttendance(meeting.id, marks);
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
          <QuorumBadge presentVoting={presentVoting} votingSeats={votingSeats}
            required={required} pct={pct} met={wouldBeQuorate} source={quorum?.quorumSource} t={t} />
          <div style={{ display: 'flex', gap: 8 }}>
            <Button variant="ghost" onClick={onClose}>{t('cancel')}</Button>
            <Button variant="primary" onClick={save} disabled={saving || locked}>
              {saving ? t('saving') : t('save')}
            </Button>
          </div>
        </div>
      }
    >
      {/* Where quorum comes from, said once, on the screen where it is decided. */}
      <div style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.7, marginBottom: 10 }}>
        {isAr
          ? `النصاب يُحتسب من سجل أعضاء اللجنة: ${votingSeats} مقعداً مصوّتاً. المقعد غير المُعلَّم يُعدّ غائباً ويُحتسب في المقام — هذا هو معنى النصاب. الحضور من غير الأعضاء يُسجَّل كضيوف ولا يُحتسب.`
          : `Quorum is computed from the committee's roster: ${votingSeats} voting seat${votingSeats === 1 ? '' : 's'}. A seat left unticked is absent and still counts in the denominator — that is what quorum means. Non-members are recorded as guests and never count.`}
      </div>

      {quorum?.quorumSource === 'ASSERTED_LEGACY' && (
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start', fontSize: 12, marginBottom: 10,
                      padding: '8px 10px', borderRadius: 'var(--r-sm)',
                      background: 'var(--warning-50, var(--surface-2))', color: 'var(--ink-2)' }}>
          <ShieldAlert size={14} style={{ flexShrink: 0, marginTop: 2 }} />
          <span>{isAr
            ? 'نصاب هذا الاجتماع مُدخَل يدوياً وليس محتسباً (سجلات ما قبل التحديث). سجّل الحضور ليُحتسب من سجل الأعضاء.'
            : 'This meeting’s quorum was asserted, not computed (a pre-V319 record). Record attendance and it will be derived from the roster.'}</span>
        </div>
      )}

      {locked && (
        <div style={{ fontSize: 12, marginBottom: 10, color: 'var(--ink-3)' }}>
          {isAr
            ? 'تم اعتماد المحضر — ورقة الحضور جزء من سجل موقَّع إلكترونياً ولا يمكن تعديلها.'
            : 'The minutes are approved — the attendance sheet is part of an e-signed record and cannot be changed.'}
        </div>
      )}

      {/* ── the roster ── */}
      <div style={{ overflowX: 'auto' }}>
        <table className="com-attendance-table" style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
          <thead>
            <tr>
              <th style={thStyle}>{t('comAttendee')}</th>
              <th style={thStyle}>{t('comRole')}</th>
              <th style={{ ...thStyle, textAlign: 'center' }}>{t('comVoting')}</th>
              <th style={{ ...thStyle, textAlign: 'center' }}>{t('comPresent')}</th>
              <th style={{ ...thStyle, textAlign: 'center' }}>{t('comCoi')}</th>
              <th style={thStyle}>{isAr ? 'بيان تعارض المصالح' : 'COI note'}</th>
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td style={tdStyle} colSpan={6}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</td></tr>
            )}
            {!loading && seats.length === 0 && (
              <tr><td style={tdStyle} colSpan={6}>
                {isAr
                  ? 'لا يوجد أعضاء نشطون في سجل هذه اللجنة — عيّن أعضاء أولاً؛ اللجنة بلا مقاعد مصوّتة لا يمكن أن تحقّق النصاب.'
                  : 'This committee has no active roster members — appoint members first. A committee with no voting seats can never be quorate.'}
              </td></tr>
            )}
            {seats.map(s => (
              <tr key={s.termId} style={s.voting ? undefined : { opacity: 0.7 }}>
                <td style={tdStyle}>
                  {s.memberName}
                  {!s.linked && (
                    <span title={isAr
                      ? 'هذا المقعد غير مرتبط بمستخدم في المنصة، لذا لا يمكن للعضو التصويت بنفسه'
                      : 'This seat is not linked to a platform user, so the member cannot cast their own vote'}
                      style={{ marginInlineStart: 6, color: 'var(--ink-3)', verticalAlign: 'middle' }}>
                      <Link2Off size={12} />
                    </span>
                  )}
                </td>
                <td style={tdStyle}>{s.role}</td>
                <td style={{ ...tdStyle, textAlign: 'center' }}>
                  {/* Read-only: eligibility is the roster's, not the form's. */}
                  {s.voting
                    ? <Badge tone="info">{isAr ? 'مصوّت' : 'Voting'}</Badge>
                    : <span style={{ color: 'var(--ink-3)' }}>—</span>}
                </td>
                <td style={{ ...tdStyle, textAlign: 'center' }}>
                  <input type="checkbox" checked={s.present} disabled={locked}
                    aria-label={`${s.memberName} present`}
                    onChange={e => patchSeat(s.termId, { present: e.target.checked })} />
                </td>
                <td style={{ ...tdStyle, textAlign: 'center' }}>
                  <input type="checkbox" checked={s.coiDeclared} disabled={locked}
                    aria-label={`${s.memberName} conflict of interest`}
                    onChange={e => patchSeat(s.termId, { coiDeclared: e.target.checked })} />
                </td>
                <td style={tdStyle}>
                  <input value={s.coiNote} disabled={locked || !s.coiDeclared}
                    onChange={e => patchSeat(s.termId, { coiNote: e.target.value })}
                    placeholder={s.coiDeclared ? (isAr ? 'طبيعة التعارض' : 'Nature of the conflict') : ''}
                    style={inputStyle} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* ── guests: recorded, never counted ── */}
      <div style={{ marginTop: 16 }}>
        <div style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.05em',
                      color: 'var(--ink-3)', marginBottom: 6 }}>
          {isAr ? 'ضيوف (لا يُحتسبون في النصاب)' : 'Guests (never count toward quorum)'}
        </div>
        {guests.length > 0 && (
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <tbody>
              {guests.map((g, i) => (
                <tr key={i}>
                  <td style={tdStyle}>
                    <input value={g.name} disabled={locked}
                      onChange={e => patchGuest(i, { name: e.target.value })}
                      placeholder={isAr ? 'الاسم' : 'Name'} style={inputStyle} />
                  </td>
                  <td style={tdStyle}>
                    <input value={g.role} disabled={locked}
                      onChange={e => patchGuest(i, { role: e.target.value })}
                      placeholder={isAr ? 'الجهة / الصفة — مثال: مراجع خارجي' : 'Affiliation — e.g. External auditor'}
                      style={inputStyle} />
                  </td>
                  <td style={{ ...tdStyle, textAlign: 'center', width: 90 }}>
                    <label style={{ fontSize: 11, display: 'inline-flex', gap: 4, alignItems: 'center' }}>
                      <input type="checkbox" checked={g.present} disabled={locked}
                        onChange={e => patchGuest(i, { present: e.target.checked })} />
                      {t('comPresent')}
                    </label>
                  </td>
                  <td style={{ ...tdStyle, textAlign: 'center', width: 40 }}>
                    <button onClick={() => removeGuest(i)} disabled={locked} title={t('deleteBtn')}
                      style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: 'var(--danger-fg)' }}>
                      <Trash2 size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <div style={{ marginTop: 8 }}>
          <Button variant="ghost" size="sm" Icon={Plus} onClick={addGuest} disabled={locked}>
            {isAr ? 'إضافة ضيف' : 'Add guest'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

function QuorumBadge({ presentVoting, votingSeats, required, pct, met, source, t }: {
  presentVoting: number; votingSeats: number; required: number; pct: number;
  met: boolean; source?: string; t: (k: string) => string;
}) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 12, color: 'var(--ink-2)' }}>
      <Users size={13} />
      {presentVoting}/{votingSeats} · {t('comQuorumRequired')} {required} ({pct}%)
      <Badge tone={met ? 'success' : 'danger'}>
        {met ? t('comQuorumMet') : t('comQuorumNotMet')}
      </Badge>
      {source === 'ASSERTED_LEGACY' && <Badge tone="warning">asserted</Badge>}
    </span>
  );
}

const thStyle: React.CSSProperties = { textAlign: 'start', padding: '6px 8px', borderBottom: '1px solid var(--border)', fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--ink-3)' };
const tdStyle: React.CSSProperties = { padding: '6px 8px', borderBottom: '1px solid var(--border)' };
const inputStyle: React.CSSProperties = { width: '100%', padding: '6px 8px', fontSize: 13, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)' };

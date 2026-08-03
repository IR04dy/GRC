import { useEffect, useState } from 'react';
import { Vote } from 'lucide-react';
import { Modal } from '../../components/ui/Modal';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  recordResolutionVote, listResolutionVotes, OUTCOME_TONE,
  type Resolution, type ResolutionVote,
} from '../../lib/committees';

/**
 * CH-14: per-member resolution voting. Each member's vote
 * (FOR / AGAINST / ABSTAIN / RECUSED) upserts by name; the backend re-derives
 * the tally + outcome (RECUSED excluded) and forces RECUSED for any member who
 * declared a conflict of interest on the meeting attendance. This panel is the
 * FE surface for that engine — the create form only captures aggregate counts.
 */
const VOTE_OPTIONS = ['FOR', 'AGAINST', 'ABSTAIN', 'RECUSED'] as const;
const VOTE_TONE: Record<string, 'success' | 'danger' | 'neutral' | 'warning'> = {
  FOR: 'success', AGAINST: 'danger', ABSTAIN: 'neutral', RECUSED: 'warning',
};

export function ResolutionVotesModal({ resolution, onClose, onSaved }: {
  resolution: Resolution;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [votes, setVotes] = useState<ResolutionVote[]>([]);
  const [res, setRes] = useState<Resolution>(resolution);
  const [memberName, setMemberName] = useState('');
  const [memberRole, setMemberRole] = useState('');
  const [vote, setVote] = useState<string>('FOR');
  const [rationale, setRationale] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    listResolutionVotes(resolution.id).then(setVotes).catch(() => setVotes([]));
  }, [resolution.id]);

  async function record() {
    if (!memberName.trim()) { toast.danger(isAr ? 'اسم العضو مطلوب' : 'Member name is required'); return; }
    setSaving(true);
    try {
      const updated = await recordResolutionVote(resolution.id, {
        memberName: memberName.trim(),
        memberRole: memberRole.trim() || undefined,
        vote,
        rationale: rationale.trim() || undefined,
      });
      setRes(updated);
      setVotes(await listResolutionVotes(resolution.id));
      setMemberName(''); setMemberRole(''); setRationale(''); setVote('FOR');
      toast.success(isAr ? 'تم تسجيل التصويت' : 'Vote recorded');
      onSaved();
    } catch (e) {
      toast.danger(e instanceof Error ? e.message : 'Action failed');
    } finally { setSaving(false); }
  }

  const total = res.voteFor + res.voteAgainst + res.voteAbstain;
  // S7a — the shared map, not a third ternary. The inline one this replaces fell through to
  // `warning` for anything that was not APPROVED or REJECTED, so a motion that has merely been
  // PROPOSED would have rendered amber, as though something were wrong with it.
  const outcomeTone = OUTCOME_TONE[res.outcome] ?? 'neutral';

  return (
    <Modal
      open
      title={`${isAr ? 'التصويت الفردي' : 'Per-member votes'} · ${resolution.resolutionCode}`}
      size="lg"
      onClose={onClose}
      footer={
        <div style={{ display: 'flex', gap: 8, justifyContent: 'space-between', width: '100%', alignItems: 'center' }}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 12, color: 'var(--ink-2)' }}>
            <Vote size={13} />
            {isAr ? 'مع' : 'For'} {res.voteFor} · {isAr ? 'ضد' : 'Against'} {res.voteAgainst} · {isAr ? 'ممتنع' : 'Abstain'} {res.voteAbstain}
            {total > 0 && <span style={{ color: 'var(--ink-3)' }}>({total})</span>}
            <Badge tone={outcomeTone}>{res.outcome}</Badge>
          </span>
          <Button variant="ghost" onClick={onClose}>{isAr ? 'إغلاق' : 'Close'}</Button>
        </div>
      }
    >
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
          <thead>
            <tr>
              <th style={thStyle}>{isAr ? 'العضو' : 'Member'}</th>
              <th style={thStyle}>{isAr ? 'الدور' : 'Role'}</th>
              <th style={{ ...thStyle, textAlign: 'center' }}>{isAr ? 'التصويت' : 'Vote'}</th>
              <th style={thStyle}>{isAr ? 'المبرر' : 'Rationale'}</th>
            </tr>
          </thead>
          <tbody>
            {votes.length === 0 ? (
              <tr><td colSpan={4} style={{ ...tdStyle, textAlign: 'center', color: 'var(--ink-3)' }}>
                {isAr ? 'لا تصويتات مسجّلة بعد' : 'No votes recorded yet'}
              </td></tr>
            ) : votes.map(v => (
              <tr key={v.id}>
                <td style={tdStyle}>{v.memberName}</td>
                <td style={tdStyle}>{v.memberRole ?? '—'}</td>
                <td style={{ ...tdStyle, textAlign: 'center' }}>
                  <Badge tone={VOTE_TONE[v.vote] ?? 'neutral'}>{v.vote}</Badge>
                </td>
                <td style={tdStyle}>{v.rationale ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div style={{ marginTop: 12, display: 'grid', gridTemplateColumns: '1fr 1fr 130px', gap: 8 }}>
        <input value={memberName} onChange={e => setMemberName(e.target.value)}
          placeholder={isAr ? 'اسم العضو' : 'Member name'} style={inputStyle} />
        <input value={memberRole} onChange={e => setMemberRole(e.target.value)}
          placeholder={isAr ? 'الدور' : 'Role'} style={inputStyle} />
        <select value={vote} onChange={e => setVote(e.target.value)} style={inputStyle}>
          {VOTE_OPTIONS.map(o => <option key={o} value={o}>{o}</option>)}
        </select>
      </div>
      <div style={{ marginTop: 8, display: 'flex', gap: 8 }}>
        <input value={rationale} onChange={e => setRationale(e.target.value)}
          placeholder={isAr ? 'المبرر (اختياري)' : 'Rationale (optional)'} style={{ ...inputStyle, flex: 1 }} />
        <Button variant="primary" Icon={Vote} onClick={record} disabled={saving}>
          {saving ? (isAr ? 'جارٍ الحفظ…' : 'Saving…') : (isAr ? 'تسجيل التصويت' : 'Record vote')}
        </Button>
      </div>
      <p style={{ marginTop: 8, fontSize: 11, color: 'var(--ink-3)' }}>
        {isAr
          ? 'يُعاد احتساب النتيجة تلقائياً. العضو الذي أعلن تعارض مصالح في الحضور يُحوَّل إلى RECUSED ويُستبعد من العدّ.'
          : 'Outcome recomputes automatically. A member who declared a conflict of interest in attendance is forced to RECUSED and excluded from the tally.'}
      </p>
    </Modal>
  );
}

const thStyle: React.CSSProperties = { textAlign: 'start', padding: '6px 8px', borderBottom: '1px solid var(--border)', fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--ink-3)' };
const tdStyle: React.CSSProperties = { padding: '6px 8px', borderBottom: '1px solid var(--border)' };
const inputStyle: React.CSSProperties = { width: '100%', padding: '6px 8px', fontSize: 13, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)' };

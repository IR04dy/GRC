/**
 * S5c — the post-implementation review card (§11 `pir/`). CH-11: surfaced once a change
 * reaches an implemented/rolled-back state, editable until CLOSED. The backend enforces the
 * mandatory-retrospective gate (a NORMAL/EMERGENCY change cannot close without a PIR
 * summary) and stamps the review sign-off, so this panel never authors who/when.
 */
import { useEffect, useState } from 'react';
import { ClipboardCheck } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { changeApi, ApiError, type ChangeRequestDto } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { Field, Block } from './ChangeFields';

// ─── CH-11: post-implementation review (PIR) ───────────────────────────────
// Surfaced once a change reaches an implemented/rolled-back state. Editable
// until the change is CLOSED (a NORMAL/EMERGENCY change cannot close without a
// PIR summary — the backend enforces the mandatory-retrospective gate). The
// review sign-off (who/when) is stamped by the POST_REVIEW transition server-side.
export const PIR_STATES = ['IMPLEMENTED', 'POST_REVIEW', 'ROLLED_BACK', 'CLOSED'];
export function PirCard({ change, onChanged, toast }: {
  change: ChangeRequestDto;
  onChanged: () => void | Promise<void>;
  toast: ReturnType<typeof useToast>;
}) {
  const { t } = useI18n();
  const locked = change.status === 'CLOSED';
  const [summary, setSummary] = useState('');
  const [met, setMet] = useState<'' | 'true' | 'false'>('');
  const [lessons, setLessons] = useState('');
  const [followUp, setFollowUp] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setSummary(change.pirSummary ?? '');
    setMet(change.pirObjectivesMet === true ? 'true' : change.pirObjectivesMet === false ? 'false' : '');
    setLessons(change.pirLessons ?? '');
    setFollowUp(change.pirFollowUp ?? '');
  }, [change.id, change.pirSummary, change.pirObjectivesMet, change.pirLessons, change.pirFollowUp]);

  if (!PIR_STATES.includes(change.status)) return null;

  async function save() {
    setSaving(true);
    try {
      // ⚠ F2-105 — this must send the whole record, not just the PIR fields.
      // PUT /api/v1/changes/{id} binds the ChangeRequest entity with @Valid, and F2-30 put
      // @NotBlank on titleAr. The server's apply() is a partial merge (null fields are skipped), but
      // @Valid runs first and rejects a body with no titleAr — so this card 400'd on every save from
      // the day F2-30 landed, and "Follow-up actions" could not be recorded at all. That is why
      // pir_follow_up was empty in both databases: not unused, unreachable.
      // The register's edit form has always sent `{...c}` for the same reason; this now matches it,
      // which also means a future @NotBlank cannot silently break the card again. `status` is safe to
      // resend: apply() deliberately never copies it (CM-C3).
      await changeApi.update(change.id, {
        ...change,
        pirSummary: summary || undefined,
        pirObjectivesMet: met === '' ? undefined : met === 'true',
        pirLessons: lessons || undefined,
        pirFollowUp: followUp || undefined,
      });
      toast.success(t('changePirSaved'));
      await onChanged();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
    finally { setSaving(false); }
  }

  const signoff = change.pirReviewedAt
    ? <Badge tone="success">{t('changePirReviewed')} · {new Date(change.pirReviewedAt).toLocaleDateString()}</Badge>
    : <Badge tone="warning">{t('changePirPending')}</Badge>;

  if (locked) {
    return (
      <Card title={t('changePirTitle')} Icon={ClipboardCheck} action={signoff}>
        <div className="changes-blocks">
          {change.pirSummary && <Block label={t('changePirSummary')} body={change.pirSummary} />}
          <Field label={t('changePirObjectives')}
                 value={change.pirObjectivesMet == null ? t('changePirUnset') : change.pirObjectivesMet ? t('changePirYes') : t('changePirNo')} />
          {change.pirLessons && <Block label={t('changePirLessons')} body={change.pirLessons} />}
          {change.pirFollowUp && <Block label={t('changePirFollowUp')} body={change.pirFollowUp} />}
          {change.pirReviewedBy && <Field label={t('changePirReviewedBy')} value={change.pirReviewedBy} />}
        </div>
      </Card>
    );
  }

  return (
    <Card title={t('changePirTitle')} Icon={ClipboardCheck} action={signoff}>
      <div className="changes-pir-form">
        <label className="changes-block-label">{t('changePirSummary')}</label>
        <textarea rows={2} value={summary} onChange={e => setSummary(e.target.value)} className="changes-pir-input" />
        <label className="changes-block-label">{t('changePirObjectives')}</label>
        <select value={met} onChange={e => setMet(e.target.value as '' | 'true' | 'false')} className="changes-pir-input">
          <option value="">{t('changePirUnset')}</option>
          <option value="true">{t('changePirYes')}</option>
          <option value="false">{t('changePirNo')}</option>
        </select>
        <label className="changes-block-label">{t('changePirLessons')}</label>
        <textarea rows={2} value={lessons} onChange={e => setLessons(e.target.value)} className="changes-pir-input" />
        <label className="changes-block-label">{t('changePirFollowUp')}</label>
        <textarea rows={2} value={followUp} onChange={e => setFollowUp(e.target.value)} className="changes-pir-input" />
        {/* S7d/CE-7 — say what saving this does. The follow-up is no longer a note: the server
            opens a tracked CAPA carrying this text, owned, dated and reported overdue. A field that
            silently creates a governance record elsewhere is the F2-98 shape in reverse — there the
            badge announced something untrue, here the consequence went unannounced. */}
        <p className="changes-pir-hint">{t('changePirFollowUpHint')}</p>
        {change.pirReviewedBy && <Field label={t('changePirReviewedBy')} value={change.pirReviewedBy} />}
        <div className="changes-actions-row">
          <Button variant="primary" Icon={ClipboardCheck} onClick={save} disabled={saving}>{t('changePirSave')}</Button>
        </div>
      </div>
    </Card>
  );
}

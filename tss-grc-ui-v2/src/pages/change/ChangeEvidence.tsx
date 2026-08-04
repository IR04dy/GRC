/**
 * S8a / CE-9 — evidence on a change, and on the conditions attached to its approval.
 *
 * G-9's finding was that `rollback_plan` and `test_plan` were prose while every other governance
 * domain attached hashed, versioned, four-eyes-reviewed artefacts. So this attaches **existing**
 * evidence items: the picker lists what the evidence workspace holds, and nothing here uploads. A
 * change-module upload path would be a second door into the same store with none of the review.
 *
 * Two rules shape the display, and both come from the server refusing things:
 *
 *  1. **The status is shown, always.** `verified` is APPROVED and nothing else, so an attachment
 *     sitting in PENDING_REVIEW looks like evidence and does not count as any. Rendering it without
 *     its state would make the satisfy refusal unexplainable.
 *  2. **Satisfy is disabled until something is verified**, with the reason on screen. The server
 *     enforces it either way; discovering a governance rule through a toast is the thing this
 *     module has been fixing all along.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Paperclip, Plus, ShieldCheck, FileText } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { FormModal } from '../../components/ui/FormModal';
import {
  changeApi, evidenceApi, ApiError,
  type ChangeEvidenceRef, type ChangeRequestDto, type EvidenceItemDto,
} from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';

/** Tone per review state. Only APPROVED is success — that is the whole point of `verified`. */
export const EVIDENCE_TONE: Record<string, 'success' | 'warning' | 'danger' | 'neutral'> = {
  APPROVED: 'success',
  PENDING_REVIEW: 'warning',
  DRAFT: 'neutral',
  EXPIRED: 'danger',
  REJECTED: 'danger',
};

export type EvidenceSubject = 'change' | 'condition';

/** One place decides which endpoint a subject uses, so the two call sites cannot drift. */
export function evidenceCalls(subject: EvidenceSubject) {
  return subject === 'change'
    ? { list: changeApi.evidence, attach: changeApi.attachEvidence }
    : { list: changeApi.conditionEvidence, attach: changeApi.attachConditionEvidence };
}

export function useAttachedEvidence(subject: EvidenceSubject, subjectId: number | undefined) {
  const [items, setItems] = useState<ChangeEvidenceRef[]>([]);
  const load = useCallback(async () => {
    if (!subjectId) { setItems([]); return; }
    // A deployment without the evidence module returns []; a 403 for a reader without CHANGE:READ
    // should not blank the surrounding card either. Both land here as "nothing to show".
    try { setItems(await evidenceCalls(subject).list(subjectId)); }
    catch { setItems([]); }
  }, [subject, subjectId]);
  useEffect(() => { void load(); }, [load]);
  return { items, reload: load, verified: items.some(e => e.verified) };
}

/**
 * The change's own evidence pack — test results, back-out proof, implementation logs.
 *
 * Shown in **every** lifecycle state, deliberately. A test result attached during assessment is the
 * artefact the CAB should be reading when it votes; gating the card on a late status would mean the
 * evidence could only arrive after the decision it was meant to inform.
 */
export function ChangeEvidenceCard({ change, toast }: {
  change: ChangeRequestDto;
  toast: ReturnType<typeof useToast>;
}) {
  const { t } = useI18n();
  const { items, reload, verified } = useAttachedEvidence('change', change.id);

  return (
    <Card
      title={t('changeEvidenceTitle')}
      Icon={Paperclip}
      action={items.length > 0
        ? <Badge tone={verified ? 'success' : 'warning'} dot>
            {t(verified ? 'changeEvidenceSomeVerified' : 'changeEvidenceNoneVerified')}
          </Badge>
        : null}
    >
      <p className="changes-ev-sub">{t('changeEvidenceSub')}</p>
      <EvidenceAttachments subject="change" subjectId={change.id} items={items}
                           onChanged={reload} toast={toast} />
    </Card>
  );
}

/**
 * The list + attach control. Rendered as a card body on the change, and inline on a condition row.
 */
export function EvidenceAttachments({ subject, subjectId, items, onChanged, toast, compact }: {
  subject: EvidenceSubject;
  subjectId: number;
  items: ChangeEvidenceRef[];
  onChanged: () => void | Promise<void>;
  toast: ReturnType<typeof useToast>;
  compact?: boolean;
}) {
  const { t, lang } = useI18n();
  const [picking, setPicking] = useState(false);
  const [pool, setPool] = useState<EvidenceItemDto[]>([]);
  const [busy, setBusy] = useState(false);

  const attachedIds = useMemo(() => new Set(items.map(e => e.id)), [items]);

  async function openPicker() {
    setPicking(true);
    try {
      const page = await evidenceApi.items(0, 200);
      setPool(page.content ?? []);
    } catch { setPool([]); }
  }

  async function attach(v: Record<string, unknown>) {
    const evidenceId = Number(v.evidenceId);
    if (!evidenceId) { toast.danger(t('changeEvidencePickOne')); return; }
    setBusy(true);
    try {
      await evidenceCalls(subject).attach(subjectId, evidenceId, v.notes ? String(v.notes) : undefined);
      toast.success(t('changeEvidenceAttached'));
      setPicking(false);
      await onChanged();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
    finally { setBusy(false); }
  }

  // Already-attached items stay out of the picker: re-attaching is a no-op server-side (the adapter
  // is idempotent), so offering it would just look like a broken button.
  const options = pool
    .filter(e => !attachedIds.has(e.id))
    .map(e => ({
      value: String(e.id),
      label: `${e.evidenceCode ? e.evidenceCode + ' · ' : ''}`
           + `${lang === 'ar' && e.titleAr ? e.titleAr : e.titleEn}`
           + `${e.status ? ' [' + e.status + ']' : ''}`,
    }));

  return (
    <div className={compact ? 'changes-ev changes-ev--compact' : 'changes-ev'}>
      {items.length === 0
        ? <span className="changes-ev-none">{t('changeEvidenceNone')}</span>
        : <ul className="changes-ev-list">
            {items.map(e => (
              <li key={e.id} className="changes-ev-row" data-verified={e.verified ? '1' : '0'}>
                {e.verified ? <ShieldCheck size={14} /> : <FileText size={14} />}
                <span className="changes-ev-title">
                  {lang === 'ar' && e.titleAr ? e.titleAr : (e.titleEn ?? '#' + e.id)}
                </span>
                <Badge tone={EVIDENCE_TONE[e.status ?? ''] ?? 'neutral'} dot>
                  {e.status ?? '—'}
                </Badge>
                {e.fileName && <span className="changes-ev-file">{e.fileName}</span>}
                {/* The hash is why this is evidence and not a note. Truncated, title-attribute full. */}
                {e.sha256 && (
                  <code className="changes-ev-hash" title={e.sha256}>{e.sha256.slice(0, 12)}</code>
                )}
              </li>
            ))}
          </ul>}

      <Button variant="ghost" Icon={compact ? Plus : Paperclip} disabled={busy}
              onClick={() => void openPicker()}>
        {t('changeEvidenceAttach')}
      </Button>

      <FormModal
        open={picking}
        title={t('changeEvidenceAttachTitle')}
        submitLabel={t('changeEvidenceAttach')}
        initial={{ evidenceId: '', notes: '' }}
        fields={[
          {
            kind: 'select', key: 'evidenceId', label: t('changeEvidenceItem'), span: 12, required: true,
            options,
          },
          { kind: 'textarea', key: 'notes', label: t('changeEvidenceNotes'), rows: 2, span: 12 },
        ]}
        onSubmit={attach}
        onClose={() => setPicking(false)}
      />
      {picking && options.length === 0 && (
        <p className="changes-ev-hint">{t('changeEvidenceEmptyPool')}</p>
      )}
    </div>
  );
}

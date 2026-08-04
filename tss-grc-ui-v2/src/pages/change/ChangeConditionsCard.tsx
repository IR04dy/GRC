/**
 * S7e / CE-5 — the conditions register on a change.
 *
 * A CAB that approves "subject to the DR rehearsal being re-run" used to have one place to put that:
 * the decision-notes field, which nothing reads. These are the same sentences as records — imposed by
 * a named person, owned, dated, and **blocking**: the server refuses `SCHEDULED → IMPLEMENTING` while
 * any condition is OPEN, so the card says so rather than letting the user discover it as a toast.
 *
 * The action split mirrors the server's guards exactly: recording an obligation as met is operational
 * (`CHANGE:WRITE`); imposing one, or waiving it, is an approval act (`CHANGE:APPROVE`). The UI does
 * not gate on permission itself — a 403 is the server's answer — but the labels say which is which.
 */
import { useCallback, useEffect, useState } from 'react';
import { ShieldCheck, Plus, Check, Ban } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { FormModal } from '../../components/ui/FormModal';
import { changeApi, ApiError, type ChangeConditionDto, type ChangeRequestDto } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';

/** Which lifecycle states make the register worth showing — from approval to implementation. */
export const CONDITION_STATES = [
  'CAB_APPROVED', 'EMERGENCY_APPROVED', 'SCHEDULED', 'IMPLEMENTING',
  'IMPLEMENTED', 'ROLLED_BACK', 'POST_REVIEW', 'CLOSED',
];

const TONE: Record<string, 'warning' | 'success' | 'danger'> = {
  OPEN: 'warning',
  SATISFIED: 'success',
  WAIVED: 'danger',      // a waiver is a governance exception, not a green tick
};

export function ConditionsCard({ change, onChanged, toast }: {
  change: ChangeRequestDto;
  onChanged: () => void | Promise<void>;
  toast: ReturnType<typeof useToast>;
}) {
  const { t, lang } = useI18n();
  const [items, setItems] = useState<ChangeConditionDto[]>([]);
  const [imposeOpen, setImposeOpen] = useState(false);
  const [waiving, setWaiving] = useState<ChangeConditionDto | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try { setItems(await changeApi.conditions(change.id)); }
    catch { setItems([]); }
  }, [change.id]);

  useEffect(() => { void load(); }, [load]);

  if (!CONDITION_STATES.includes(change.status)) return null;

  const open = items.filter(c => c.status === 'OPEN');

  async function impose(v: Record<string, unknown>) {
    setBusy(true);
    try {
      await changeApi.addCondition(change.id, {
        textEn: String(v.textEn ?? ''),
        textAr: String(v.textAr ?? ''),
        ownerName: v.ownerName ? String(v.ownerName) : undefined,
        dueAt: v.dueAt ? new Date(String(v.dueAt)).toISOString() : undefined,
      });
      toast.success(t('changeCondImposed'));
      setImposeOpen(false);
      await load(); await onChanged();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
    finally { setBusy(false); }
  }

  async function satisfy(c: ChangeConditionDto) {
    setBusy(true);
    try {
      await changeApi.satisfyCondition(c.id, t('changeCondSatisfiedNote'));
      toast.success(t('changeCondSatisfied'));
      await load(); await onChanged();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
    finally { setBusy(false); }
  }

  async function waive(v: Record<string, unknown>) {
    if (!waiving) return;
    setBusy(true);
    try {
      // The reason is mandatory server-side (and in ck_cc_waiver_reason). Sending an empty string
      // would earn a 400 that reads like a client bug, so the form requires it too.
      await changeApi.waiveCondition(waiving.id, String(v.reason ?? ''));
      toast.success(t('changeCondWaived'));
      setWaiving(null);
      await load(); await onChanged();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
    finally { setBusy(false); }
  }

  return (
    <Card
      title={t('changeCondTitle')}
      Icon={ShieldCheck}
      action={<>
        {open.length > 0
          ? <Badge tone="warning" dot>{t('changeCondBlocking').replace('{n}', String(open.length))}</Badge>
          : items.length > 0
            ? <Badge tone="success" dot>{t('changeCondAllClear')}</Badge>
            : null}
        <Button variant="ghost" Icon={Plus} onClick={() => setImposeOpen(true)}>{t('changeCondImpose')}</Button>
      </>}
    >
      {items.length === 0
        ? <EmptyState Icon={ShieldCheck} title={t('changeCondNoneTitle')} sub={t('changeCondNoneSub')} />
        : <div className="changes-cond-list">
            {items.map(c => (
              <div key={c.id} className="changes-cond-row" data-status={c.status}>
                <div className="changes-cond-main">
                  <div className="changes-cond-text">{lang === 'ar' && c.textAr ? c.textAr : c.textEn}</div>
                  <div className="changes-cond-meta">
                    <Badge tone={TONE[c.status] ?? 'neutral'} dot>{t('changeCondStatus_' + c.status)}</Badge>
                    {c.ownerName && <span>{t('changeCondOwner')}: {c.ownerName}</span>}
                    {c.dueAt && <span>{t('changeCondDue')}: {new Date(c.dueAt).toLocaleDateString()}</span>}
                    {c.imposedByName && <span>{t('changeCondImposedBy')}: {c.imposedByName}</span>}
                    {/* Who closed it and why — the whole point of the register over a notes field. */}
                    {c.closedByName && <span>{t('changeCondClosedBy')}: {c.closedByName}</span>}
                  </div>
                  {c.closureNotes && <div className="changes-cond-notes">{c.closureNotes}</div>}
                </div>
                {c.status === 'OPEN' && (
                  <div className="changes-cond-actions">
                    <Button variant="ghost" Icon={Check} onClick={() => void satisfy(c)} disabled={busy}>
                      {t('changeCondSatisfy')}
                    </Button>
                    <Button variant="ghost" Icon={Ban} onClick={() => setWaiving(c)} disabled={busy}>
                      {t('changeCondWaive')}
                    </Button>
                  </div>
                )}
              </div>
            ))}
          </div>}

      {open.length > 0 && (
        <p className="changes-cond-hint">{t('changeCondGateHint')}</p>
      )}

      <FormModal
        open={imposeOpen}
        title={t('changeCondImposeTitle')}
        submitLabel={t('changeCondImpose')}
        initial={{ textEn: '', textAr: '', ownerName: '', dueAt: '' }}
        fields={[
          { kind: 'textarea', key: 'textEn', label: t('changeCondTextEn'), rows: 2, span: 12, required: true },
          { kind: 'textarea', key: 'textAr', label: t('changeCondTextAr'), rows: 2, span: 12, required: true },
          { kind: 'text', key: 'ownerName', label: t('changeCondOwner'), span: 6 },
          { kind: 'date', key: 'dueAt', label: t('changeCondDue'), span: 6 },
        ]}
        onSubmit={impose}
        onClose={() => setImposeOpen(false)}
      />

      <FormModal
        open={waiving != null}
        title={t('changeCondWaiveTitle')}
        submitLabel={t('changeCondWaive')}
        initial={{ reason: '' }}
        fields={[
          { kind: 'textarea', key: 'reason', label: t('changeCondWaiveReason'), rows: 3, span: 12, required: true },
        ]}
        onSubmit={waive}
        onClose={() => setWaiving(null)}
      />
    </Card>
  );
}

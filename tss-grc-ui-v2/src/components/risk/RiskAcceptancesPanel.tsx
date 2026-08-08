import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ClipboardCheck, Stamp, ShieldAlert, Undo2, XCircle } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge, type BadgeTone } from '../ui/Badge';
import { Button } from '../ui/Button';
import { InlineEmpty } from '../ui/InlineEmpty';
import { EmptyState } from '../ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { riskAcceptanceApi, ApiError, type RiskAcceptanceDto } from '../../lib/api';

/**
 * ERM epic E-A — the formal risk-acceptance record, on the risk that owns it.
 *
 * <h3>🔴 Why this panel exists (F2-164)</h3>
 * `riskAcceptanceApi` has five methods. Before this panel, **one** of them had a
 * caller: `create`, from the acceptance wizard. `listForRisk`, `approve`, `reject`
 * and `revoke` had none — so a submitted acceptance could be raised and then never
 * seen, never approved, and never revoked from any screen. Since it is *approval*
 * that flips the risk to `ACCEPTED`, the governed path could not actually accept a
 * risk, and the only route that could was `POST /risks/{id}/accept` — a bare
 * `RISK:WRITE` status flip with no justification, no expiry and no sign-off, which
 * also exempted the risk from the mandatory-treatment close gate. Retiring that
 * bypass without building this panel would have removed the only working path.
 *
 * <p>⚠ **F2-68(c)'s unused-export scan could not see this.** It resolves at the
 * granularity of the exported *object*: `riskAcceptanceApi` had a caller, so it
 * counted as reached, and four dead methods hid inside a live export. An
 * instrument's granularity is its blind spot — the same shape as F2-163, where a
 * per-`<Link>` route scan could not see a `rowHref` navigation surface.
 *
 * <p>Approve / reject / revoke are `RISK:APPROVE` server-side. They are rendered
 * unconditionally and the 403 is surfaced verbatim: a hidden button teaches nobody
 * why they cannot act, and the FE holds no permission list to gate on (only role
 * names, which is the surface F2-05 is unwinding).
 */
const STATUS_TONE: Record<string, BadgeTone> = {
  SUBMITTED: 'warning', APPROVED: 'success', REJECTED: 'danger',
  EXPIRED: 'neutral', REVOKED: 'accent',
};

const STATUS_AR: Record<string, string> = {
  SUBMITTED: 'مُقدَّم', APPROVED: 'معتمد', REJECTED: 'مرفوض',
  EXPIRED: 'منتهي', REVOKED: 'ملغى',
};

function fmt(d?: string | null): string {
  if (!d) return '—';
  const t = new Date(d);
  return isNaN(t.getTime()) ? '—' : t.toLocaleDateString();
}

/** An APPROVED acceptance still inside its time-box — what the close gate reads. */
function isLive(a: RiskAcceptanceDto): boolean {
  if (a.status !== 'APPROVED' || !a.expiresAt) return false;
  const exp = new Date(a.expiresAt);
  return !isNaN(exp.getTime()) && exp >= new Date(new Date().toDateString());
}

export function RiskAcceptancesPanel({ riskId, onChanged }: {
  riskId: number; onChanged?: () => void;
}) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const dialog = useDialog();

  const [rows, setRows] = useState<RiskAcceptanceDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await riskAcceptanceApi.listForRisk(riskId));
    } catch (e) {
      // No fallback array. F2-158: a client-side stand-in made a 500 invisible for
      // the entire life of an endpoint, because the screen always rendered rows.
      setError(e instanceof ApiError
        ? e.message
        : (isAr ? 'تعذّر تحميل طلبات القبول' : 'Could not load acceptance requests'));
    } finally {
      setLoading(false);
    }
  }, [riskId, isAr]);

  useEffect(() => { void load(); }, [load]);

  async function run(label: string, fn: () => Promise<unknown>, success: string) {
    setBusy(true);
    try {
      await fn();
      toast.success(success);
      await load();
      onChanged?.();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : label);
    } finally {
      setBusy(false);
    }
  }

  async function onApprove(a: RiskAcceptanceDto) {
    // Board sign-off is server-required when residual exceeds the appetite ceiling,
    // and the server refuses the approval without it — so ask for it here rather
    // than letting a 400 be the first the approver hears of it.
    let board: string | undefined;
    if (a.requiresBoard && !a.boardSignedBy) {
      const answer = await dialog.prompt({
        title: isAr ? 'اعتماد المجلس مطلوب' : 'Board sign-off required',
        helpText: isAr
          ? 'النتيجة المتبقية تتجاوز سقف الشهية، لذا يلزم اعتماد المجلس قبل الموافقة.'
          : 'Residual exceeds the appetite ceiling, so Board sign-off is required before approval.',
        label: isAr ? 'اسم الموقّع من المجلس' : 'Board signatory',
        required: true,
      });
      if (!answer) return;
      board = answer;
    }
    await run(
      isAr ? 'تعذّر اعتماد القبول' : 'Could not approve the acceptance',
      () => riskAcceptanceApi.approve(a.id, board),
      isAr
        ? `اعتُمد القبول #${a.id} — الخطر مقبول رسمياً حتى ${fmt(a.expiresAt)}`
        : `Acceptance #${a.id} approved — risk formally accepted until ${fmt(a.expiresAt)}`);
  }

  async function onReject(a: RiskAcceptanceDto) {
    const reason = await dialog.prompt({
      title: isAr ? 'رفض طلب القبول' : 'Reject acceptance request',
      label: isAr ? 'السبب' : 'Reason',
      required: true,
    });
    if (!reason) return;
    await run(
      isAr ? 'تعذّر رفض الطلب' : 'Could not reject the request',
      () => riskAcceptanceApi.reject(a.id, reason),
      isAr ? `رُفض الطلب #${a.id}` : `Request #${a.id} rejected`);
  }

  async function onRevoke(a: RiskAcceptanceDto) {
    const reason = await dialog.prompt({
      title: isAr ? 'إلغاء القبول' : 'Revoke acceptance',
      helpText: isAr
        ? 'يُعاد الخطر إلى المراجعة لإعادة التقييم.'
        : 'The risk is reopened for reassessment.',
      label: isAr ? 'السبب' : 'Reason',
      required: true,
    });
    if (!reason) return;
    await run(
      isAr ? 'تعذّر إلغاء القبول' : 'Could not revoke the acceptance',
      () => riskAcceptanceApi.revoke(a.id, reason),
      isAr ? `أُلغي القبول #${a.id} — أُعيد الخطر للمراجعة` : `Acceptance #${a.id} revoked — risk reopened`);
  }

  const live = rows.find(isLive);

  return (
    <Card padded Icon={ClipboardCheck}
      title={isAr ? 'القبول الرسمي للخطر' : 'Formal risk acceptance'}
      action={
        <Link to={`/workflows/erm/risk-acceptance/${riskId}`} className="link">
          <Button size="sm" variant="secondary" Icon={ClipboardCheck}>
            {isAr ? 'طلب قبول' : 'Request acceptance'}
          </Button>
        </Link>
      }>

      <p style={{ fontSize: 12, color: 'var(--ink-3)', margin: '0 0 12px' }}>
        {isAr
          ? 'قبول محدّد المدة مع مبرّر وضوابط تعويضية واعتماد رئيس المخاطر (والمجلس فوق سقف الشهية). القبول المعتمد الساري وحده يُعفي الخطر من شرط خطة المعالجة عند الإغلاق، وينتهي تلقائياً فيُعاد الخطر للمراجعة.'
          : 'A time-boxed acceptance with justification, compensating controls and CRO sign-off (plus Board sign-off above appetite). Only a live approved acceptance exempts the risk from the treatment-plan requirement at close, and it expires — reopening the risk for reassessment.'}
      </p>

      {loading && <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>}

      {!loading && error && (
        <InlineEmpty>
          <span style={{ color: 'var(--danger-fg)' }}>{error}</span>
        </InlineEmpty>
      )}

      {!loading && !error && rows.length === 0 && (
        <EmptyState
          Icon={ShieldAlert}
          title={isAr ? 'لا طلبات قبول لهذا الخطر' : 'No acceptance requests for this risk'}
          sub={isAr
            ? 'ارفع طلب قبول إذا كان قرار الإدارة هو الاحتفاظ بالخطر بدل معالجته.'
            : 'Raise one when the decision is to retain the risk rather than treat it.'} />
      )}

      {!loading && !error && rows.length > 0 && (
        <>
          {live && (
            <div style={{ marginBottom: 10 }}>
              <Badge tone="success" dot>
                {isAr ? `قبول ساري حتى ${fmt(live.expiresAt)}` : `Live acceptance until ${fmt(live.expiresAt)}`}
              </Badge>
            </div>
          )}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {rows.map(a => (
              <div key={a.id} style={{
                border: '1px solid var(--border)', borderRadius: 'var(--r-md)',
                background: 'var(--surface-2)', padding: '10px 12px',
              }}>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                  <span className="mono" style={{ fontWeight: 600 }}>#{a.id}</span>
                  <Badge tone={STATUS_TONE[a.status] ?? 'neutral'} dot>
                    {isAr ? (STATUS_AR[a.status] ?? a.status) : a.status}
                  </Badge>
                  {a.requiresBoard && (
                    <Badge tone="warning">
                      {isAr ? 'يتطلب المجلس' : 'Board required'}
                    </Badge>
                  )}
                  <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                    {isAr ? 'ينتهي' : 'expires'} {fmt(a.expiresAt)}
                    {a.residualScoreAtAcceptance != null && ` · ${isAr ? 'المتبقي' : 'residual'} ${a.residualScoreAtAcceptance}`}
                    {a.appetiteCeiling != null && ` / ${isAr ? 'السقف' : 'ceiling'} ${a.appetiteCeiling}`}
                  </span>
                  <span style={{ flex: 1 }} />
                  {a.status === 'SUBMITTED' && <>
                    <Button size="sm" variant="primary" Icon={Stamp} disabled={busy}
                      onClick={() => void onApprove(a)}>
                      {isAr ? 'اعتماد' : 'Approve'}
                    </Button>
                    <Button size="sm" variant="ghost" Icon={XCircle} disabled={busy}
                      onClick={() => void onReject(a)}>
                      {isAr ? 'رفض' : 'Reject'}
                    </Button>
                  </>}
                  {a.status === 'APPROVED' && (
                    <Button size="sm" variant="secondary" Icon={Undo2} disabled={busy}
                      onClick={() => void onRevoke(a)}>
                      {isAr ? 'إلغاء' : 'Revoke'}
                    </Button>
                  )}
                </div>

                {a.justificationEn && (
                  <p style={{ margin: '8px 0 0', fontSize: 12, color: 'var(--ink-2)' }}>
                    {isAr && a.justificationAr ? a.justificationAr : a.justificationEn}
                  </p>
                )}
                {(a.croSignedBy || a.boardSignedBy || a.approvedBy) && (
                  <p style={{ margin: '6px 0 0', fontSize: 11, color: 'var(--ink-3)' }}>
                    {a.croSignedBy && `${isAr ? 'رئيس المخاطر' : 'CRO'}: ${a.croSignedBy}`}
                    {a.boardSignedBy && ` · ${isAr ? 'المجلس' : 'Board'}: ${a.boardSignedBy}`}
                    {a.approvedBy && ` · ${isAr ? 'اعتمده' : 'approved by'} ${a.approvedBy} ${fmt(a.approvedAt)}`}
                  </p>
                )}
                {a.rejectionReason && (
                  <p style={{ margin: '6px 0 0', fontSize: 11, color: 'var(--danger-fg)' }}>
                    {a.rejectionReason}
                  </p>
                )}
              </div>
            ))}
          </div>
        </>
      )}
    </Card>
  );
}

import { useEffect, useState } from 'react';
import { ShieldAlert, ShieldCheck, RefreshCw } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  listGateAuthority, listEligibleAuthorities, setGateAuthority, clearGateAuthority,
  type GateAuthority, type EligibleCommittee, type AuthorityGate,
} from '../../lib/committees';

/**
 * ADR-017 D-2 — the screen a `change-authority-not-configured` refusal names.
 *
 * A tenant's CAB and ECAB are charter facts the platform must never invent, so a change
 * approval that the authorisation matrix routes to a committee now fails closed with a
 * 409 when no committee is named. That refusal is only legitimate if it is fixable from
 * inside the product: before this panel, the sole remedy was a DBA INSERT into
 * `admin.committee_gate_map`.
 *
 * `configured` comes from the server and deliberately is NOT "a committee is selected".
 * A gate pointing at a dissolved committee reports `configured: false` with
 * `fault: COMMITTEE_UNAVAILABLE`, because that is what the enforcement point sees — the
 * panel must not tell an administrator a gate is fine while approvals are being refused.
 */
export function ChangeAuthorityPanel() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [rows, setRows] = useState<GateAuthority[]>([]);
  const [eligible, setEligible] = useState<EligibleCommittee[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<AuthorityGate | null>(null);
  const [denied, setDenied] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const [g, e] = await Promise.all([listGateAuthority(), listEligibleAuthorities()]);
      setRows(g);
      setEligible(e);
      setDenied(false);
    } catch {
      // A persona without ADMIN:READ lands here. Hide the panel rather than leaving a
      // permanent error card on a screen they are otherwise entitled to use.
      setDenied(true);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  if (denied || (loading && rows.length === 0)) return null;

  async function assign(gate: AuthorityGate, value: string) {
    setBusy(gate);
    try {
      if (!value) {
        await clearGateAuthority(gate);
        toast.warning(isAr
          ? `أُلغيت سلطة ${gate} — لن يمكن اعتماد التغييرات الموجَّهة إليها`
          : `${gate} authority cleared — changes routed to it can no longer be approved`);
      } else {
        await setGateAuthority(gate, Number(value));
        toast.success(isAr ? `تم تعيين سلطة ${gate}` : `${gate} authority set`);
      }
      await load();
    } catch (e) {
      toast.danger(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(null);
    }
  }

  const gaps = rows.filter(r => !r.configured);

  const faultText = (r: GateAuthority): string =>
    r.fault === 'COMMITTEE_UNAVAILABLE'
      ? (isAr ? 'اللجنة المرتبطة محلولة أو غير موجودة — أعِد التعيين'
              : 'The mapped committee is dissolved or missing — re-point the gate')
      : (isAr ? 'لم تُسمَّ أي لجنة — لا يمكن اعتماد التغييرات التي تتطلب هذه السلطة'
              : 'No committee named — changes requiring this authority cannot be approved');

  return (
    <Card title={isAr ? 'سلطة اعتماد التغييرات (CAB / ECAB)' : 'Change approval authority (CAB / ECAB)'}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 10 }}>
        {isAr
          ? 'مصفوفة تفويض التغييرات تحدد السلطة المطلوبة لكل تغيير؛ هذه الشاشة تحدد اللجنة التي تمثل تلك السلطة.'
          : 'The change authorisation matrix says which authority must decide a change; this names the committee that is that authority.'}
      </div>

      {gaps.length > 0 && (
        <div style={{
          display: 'flex', alignItems: 'flex-start', gap: 8, fontSize: 12,
          background: 'var(--warning-bg)', border: '1px solid var(--warning)',
          borderRadius: 6, padding: '8px 10px', marginBottom: 10,
        }}>
          <ShieldAlert size={14} style={{ flexShrink: 0, marginTop: 1, color: 'var(--warning)' }} />
          <span>
            {isAr
              ? `${gaps.length} من بوابات السلطة غير مُهيَّأة. التغييرات الموجَّهة إليها تُرفَض (409) بدلًا من اعتمادها دون اللجنة التي تنص عليها المصفوفة.`
              : `${gaps.length} authority gate(s) unconfigured. Changes routed to them are refused (409) rather than approved without the authority the matrix names.`}
          </span>
        </div>
      )}

      <table className="grc-table" style={{ width: '100%', fontSize: 12 }}>
        <thead>
          <tr>
            <th style={{ width: 90 }}>{isAr ? 'البوابة' : 'Gate'}</th>
            <th>{isAr ? 'اللجنة' : 'Committee'}</th>
            <th style={{ width: 130 }}>{isAr ? 'الحالة' : 'Status'}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(r => (
            <tr key={r.gate}>
              <td>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontWeight: 600 }}>
                  {r.configured
                    ? <ShieldCheck size={13} style={{ color: 'var(--success)' }} />
                    : <ShieldAlert size={13} style={{ color: 'var(--warning)' }} />}
                  {r.gate}
                </span>
              </td>
              <td>
                <select
                  style={{ width: '100%', maxWidth: 420, fontSize: 12, padding: '4px 8px' }}
                  value={r.committeeId ? String(r.committeeId) : ''}
                  disabled={busy === r.gate}
                  onChange={ev => void assign(r.gate, ev.target.value)}
                >
                  <option value="">{isAr ? '— لم تُسمَّ لجنة —' : '— no committee named —'}</option>
                  {eligible.map(c => (
                    <option key={c.id} value={String(c.id)}>
                      {`${isAr && c.nameAr ? c.nameAr : c.nameEn} · ${c.committeeType}`}
                    </option>
                  ))}
                </select>
                {!r.configured && (
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 3 }}>{faultText(r)}</div>
                )}
              </td>
              <td>
                {r.configured
                  ? <Badge tone="success">{isAr ? 'مُهيَّأة' : 'Configured'}</Badge>
                  : <Badge tone="warning">{isAr ? 'غير مُهيَّأة' : 'Not configured'}</Badge>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {eligible.length === 0 && (
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 8 }}>
          {isAr
            ? 'لا توجد لجان في هذه المؤسسة بعد — أنشئ لجنة نشطة أولًا، ثم عيّنها كسلطة.'
            : 'This organisation has no committees yet — constitute an ACTIVE committee first, then name it as the authority.'}
        </div>
      )}

      <div style={{ marginTop: 8 }}>
        <Button variant="ghost" size="sm" Icon={RefreshCw} onClick={() => void load()} disabled={loading}>
          {isAr ? 'تحديث' : 'Refresh'}
        </Button>
      </div>
    </Card>
  );
}

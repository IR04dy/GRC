// Reusable in-house e-signature panel: shows an entity's sign-off trail and lets
// the current user add a signed attestation (intent + statement, hash-bound).
import { useCallback, useEffect, useState } from 'react';
import { PenLine, ShieldCheck } from 'lucide-react';
import { Button } from './Button';
import { Badge, type BadgeTone } from './Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { signaturesApi, SIGN_INTENTS, ApiError, type SignatureDto, type SignIntent } from '../../lib/api';

const INTENT_LABEL: Record<SignIntent, { en: string; ar: string; tone: BadgeTone }> = {
  APPROVE:     { en: 'Approved',     ar: 'اعتماد',   tone: 'success' },
  REVIEW:      { en: 'Reviewed',     ar: 'مراجعة',   tone: 'info' },
  ACKNOWLEDGE: { en: 'Acknowledged', ar: 'إقرار',    tone: 'primary' },
  ATTEST:      { en: 'Attested',     ar: 'تصديق',    tone: 'accent' },
  REJECT:      { en: 'Rejected',     ar: 'رفض',      tone: 'danger' },
};

export interface SignaturePanelProps {
  entityType: string;
  entityId: number;
  label?: string;
}

export function SignaturePanel({ entityType, entityId, label }: SignaturePanelProps) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const toast = useToast();
  const [items, setItems] = useState<SignatureDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [signing, setSigning] = useState(false);
  const [open, setOpen] = useState(false);
  const [intent, setIntent] = useState<SignIntent>('APPROVE');
  const [statement, setStatement] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    signaturesApi.list(entityType, entityId).then(setItems).catch(() => setItems([])).finally(() => setLoading(false));
  }, [entityType, entityId]);
  useEffect(() => { load(); }, [load]);

  async function submit() {
    setSigning(true);
    try {
      await signaturesApi.sign(entityType, entityId, { intent, statementEn: statement.trim() || undefined });
      toast.success(ar ? 'تم التوقيع' : 'Signed');
      setOpen(false); setStatement('');
      load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (ar ? 'تعذّر التوقيع' : 'Signing failed'));
    } finally { setSigning(false); }
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontWeight: 700 }}>
          <ShieldCheck size={15} /> {label ?? (ar ? 'سجل التوقيعات' : 'Sign-off trail')}
          <Badge tone="neutral">{items.length}</Badge>
        </span>
        {!open && (
          <Button size="sm" variant="primary" Icon={PenLine} onClick={() => setOpen(true)}>
            {ar ? 'توقيع' : 'Sign'}
          </Button>
        )}
      </div>

      {open && (
        <div style={{
          display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 10, padding: 10,
          border: '1px solid var(--primary-200, var(--border))', borderRadius: 8, background: 'var(--surface-2)',
        }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
            <select value={intent} onChange={e => setIntent(e.currentTarget.value as SignIntent)}
              aria-label={ar ? 'نوع التوقيع' : 'Intent'}
              style={{ padding: '6px 8px', borderRadius: 6, border: '1px solid var(--border)', fontSize: 12, background: 'var(--surface)' }}>
              {SIGN_INTENTS.map(i => <option key={i} value={i}>{ar ? INTENT_LABEL[i].ar : INTENT_LABEL[i].en}</option>)}
            </select>
            <input value={statement} onChange={e => setStatement(e.currentTarget.value)}
              placeholder={ar ? 'بيان التوقيع (اختياري)…' : 'Attestation statement (optional)…'}
              style={{ flex: 1, minWidth: 180, padding: '6px 8px', borderRadius: 6, border: '1px solid var(--border)', fontSize: 12, background: 'var(--surface)' }} />
          </div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
            {ar ? 'سيُسجَّل اسمك ودورك ووقت التوقيع مع بصمة تحقق (SHA-256).' : 'Your name, role and time are recorded with a SHA-256 integrity hash.'}
          </div>
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button size="sm" variant="ghost" disabled={signing} onClick={() => setOpen(false)}>{ar ? 'إلغاء' : 'Cancel'}</Button>
            <Button size="sm" variant="primary" Icon={PenLine} disabled={signing} onClick={() => { void submit(); }}>
              {signing ? (ar ? 'جارٍ…' : 'Signing…') : (ar ? 'توقيع' : 'Sign')}
            </Button>
          </div>
        </div>
      )}

      {loading ? (
        <div style={{ fontSize: 12, color: 'var(--ink-3)', padding: '8px 0' }}>{ar ? 'جارٍ التحميل…' : 'Loading…'}</div>
      ) : items.length === 0 ? (
        <div style={{ fontSize: 12, color: 'var(--ink-3)', padding: '8px 0' }}>{ar ? 'لا توجد توقيعات بعد.' : 'No signatures yet.'}</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {items.map(s => (
            <div key={s.id} style={{
              display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 10, alignItems: 'center',
              padding: '8px 10px', borderBottom: '1px solid var(--border)', fontSize: 12,
            }}>
              <Badge tone={INTENT_LABEL[s.intent]?.tone ?? 'neutral'}>{ar ? INTENT_LABEL[s.intent]?.ar : INTENT_LABEL[s.intent]?.en}</Badge>
              <div style={{ minWidth: 0 }}>
                {/* F2-277 — signerNameAr is null when the user has no Arabic name, which
                    falls back to the English snapshot rather than to the id. */}
                <span style={{ fontWeight: 600 }}>{(ar && s.signerNameAr ? s.signerNameAr
                  : s.signerName) ?? `#${s.signerId}`}</span>
                {s.signerRole && <span style={{ color: 'var(--ink-3)' }}> · {s.signerRole}</span>}
                {s.statementEn && <div style={{ color: 'var(--ink-3)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{s.statementEn}</div>}
                <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }} title={s.payloadHash}>#{s.payloadHash.slice(0, 16)}…</div>
              </div>
              <span className="mono" style={{ color: 'var(--ink-3)' }}>{s.signedAt?.replace('T', ' ').slice(0, 16)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

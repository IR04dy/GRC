// SOC-E — action history for one IOC. "Actioning" an IOC now records WHAT was done and links it
// to a concrete platform target (asset / vulnerability / detection rule), instead of a lone
// boolean flip. This panel renders that history on the threat-intel detail page and lets an
// analyst add a new action + target link inline.
import { useCallback, useEffect, useState } from 'react';
import { Crosshair, ArrowRight, Plus } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { socopsApi, type ThreatIntelAction } from '../../lib/socops';

const ACTION_TONE: Record<string, 'info' | 'success' | 'warning' | 'danger' | 'neutral'> = {
  BLOCK: 'danger', PATCH: 'warning', LINK_DETECTION: 'success', HUNT: 'info',
  SCAN: 'info', MONITOR: 'neutral', BRIEF: 'neutral', REVIEWED: 'neutral', ESCALATE: 'danger',
};
const ACTION_TYPES = ['BLOCK', 'SCAN', 'HUNT', 'MONITOR', 'PATCH', 'BRIEF', 'LINK_DETECTION', 'REVIEWED', 'ESCALATE'];
const TARGET_TYPES = ['NONE', 'ASSET', 'VULNERABILITY', 'DETECTION'];

export function ThreatIntelActionsPanel({ threatIntelId, onActioned }: { threatIntelId: number; onActioned?: () => void }) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const toast = useToast();
  const [rows, setRows] = useState<ThreatIntelAction[]>([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [actionType, setActionType] = useState('BLOCK');
  const [targetType, setTargetType] = useState('NONE');
  const [targetId, setTargetId] = useState('');
  const [notes, setNotes] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    socopsApi.threatIntelActions(threatIntelId)
      .then(setRows)
      .catch(() => setRows([]))
      .finally(() => setLoading(false));
  }, [threatIntelId]);

  useEffect(() => { load(); }, [load]);

  async function submit() {
    if (targetType !== 'NONE' && !targetId.trim()) {
      toast.danger(ar ? 'أدخل معرّف الهدف' : 'Enter a target id'); return;
    }
    setBusy(true);
    try {
      await socopsApi.threatIntelAction(threatIntelId, {
        actionType,
        targetType: targetType === 'NONE' ? undefined : (targetType as 'ASSET' | 'VULNERABILITY' | 'DETECTION'),
        targetId: targetType === 'NONE' ? undefined : Number(targetId),
        notes: notes.trim() || undefined,
      });
      toast.success(ar ? 'تم تسجيل الإجراء' : 'Action recorded');
      setOpen(false); setTargetId(''); setNotes('');
      load();
      onActioned?.();
    } catch (e) {
      toast.danger((e as Error)?.message ?? (ar ? 'فشل الإجراء' : 'Action failed'));
    } finally { setBusy(false); }
  }

  return (
    <div style={{ marginTop: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 700, marginBottom: 10 }}>
        <Crosshair size={16} /> {ar ? 'إجراءات المؤشر' : 'IOC actions'}
        <Badge tone="neutral">{rows.length}</Badge>
        <span style={{ marginInlineStart: 'auto' }}>
          <Button size="sm" variant={open ? 'ghost' : 'primary'} onClick={() => setOpen(o => !o)}>
            <Plus size={13} /> {ar ? 'إجراء + ربط هدف' : 'Action + link target'}
          </Button>
        </span>
      </div>

      {open && (
        <div style={{ marginBottom: 12 }}>
        <Card padded>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 10 }}>
            <Select label={ar ? 'نوع الإجراء' : 'Action'} value={actionType} onChange={e => setActionType(e.target.value)}
              options={ACTION_TYPES.map(v => ({ value: v, label: v }))} />
            <Select label={ar ? 'نوع الهدف' : 'Target type'} value={targetType} onChange={e => setTargetType(e.target.value)}
              options={TARGET_TYPES.map(v => ({ value: v, label: v }))} />
            {targetType !== 'NONE' && (
              <Input label={ar ? 'معرّف الهدف' : 'Target id'} type="number" value={targetId}
                onChange={e => setTargetId(e.target.value)} placeholder="#" />
            )}
            <Input label={ar ? 'ملاحظات' : 'Notes'} value={notes} onChange={e => setNotes(e.target.value)} />
          </div>
          <div style={{ marginTop: 10, display: 'flex', gap: 8 }}>
            <Button size="sm" variant="primary" onClick={() => void submit()} disabled={busy}>
              {busy ? (ar ? 'جارٍ…' : 'Saving…') : (ar ? 'تسجيل الإجراء' : 'Record action')}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setOpen(false)}>{ar ? 'إلغاء' : 'Cancel'}</Button>
          </div>
          <div style={{ marginTop: 6, fontSize: 11, color: 'var(--ink-3)' }}>
            {ar ? 'اربط المؤشر بأصل أو ثغرة أو قاعدة كشف موجودة (يتم التحقق من وجود الهدف).'
                : 'Link the IOC to an existing asset, vulnerability, or detection rule (target existence is validated).'}
          </div>
        </Card>
        </div>
      )}

      <Card padded={false}>
        {loading ? (
          <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 12 }}>{ar ? 'جارٍ التحميل…' : 'Loading…'}</div>
        ) : rows.length === 0 ? (
          <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 12 }}>{ar ? 'لا توجد إجراءات بعد.' : 'No actions yet.'}</div>
        ) : (
          <div>
            {rows.map((e, i) => (
              <div key={e.id} style={{
                display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 12, alignItems: 'center',
                padding: '10px 14px', borderBottom: i < rows.length - 1 ? '1px solid var(--border)' : 'none',
              }}>
                <Badge tone={ACTION_TONE[e.actionType] ?? 'neutral'}>{e.actionType}</Badge>
                <div style={{ fontSize: 12, minWidth: 0 }}>
                  {e.targetType ? (
                    <span style={{ fontWeight: 600 }}>
                      <Badge tone="info" className="me-1">{e.targetType}</Badge>
                      <ArrowRight size={11} style={{ verticalAlign: 'middle' }} /> {e.targetLabel ?? `#${e.targetId}`}
                    </span>
                  ) : <span style={{ color: 'var(--ink-3)' }}>{ar ? 'بدون هدف محدد' : 'no concrete target'}</span>}
                  {e.notes && <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{e.notes}</div>}
                </div>
                <div style={{ textAlign: ar ? 'left' : 'right', fontSize: 10, color: 'var(--ink-3)' }}>
                  <div className="mono">{e.actionedAt?.slice(0, 16).replace('T', ' ')}</div>
                  {e.actionedBy && <div>{ar ? 'بواسطة ' : 'by '}{e.actionedBy}</div>}
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}

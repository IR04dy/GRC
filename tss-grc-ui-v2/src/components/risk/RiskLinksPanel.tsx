import { useCallback, useEffect, useState } from 'react';
import { Link2, Plus, Trash2, X } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { EmptyState } from '../ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { riskLinkApi, RISK_LINK_TYPES, ApiError, type RiskLinkDto } from '../../lib/api';

/**
 * ERM epic E-G — typed register linkage panel. Replaces the mock links tab with
 * real, user-managed links from a risk to other register objects (objective /
 * process / service / asset / supplier / project / incident / control / …),
 * served by /api/v1/risk/risks/{id}/links.
 */
const TYPE_LABEL_AR: Record<string, string> = {
  OBJECTIVE: 'هدف', PROCESS: 'عملية', SERVICE: 'خدمة', ASSET: 'أصل', SUPPLIER: 'مورّد',
  PROJECT: 'مشروع', INCIDENT: 'حادثة', CONTROL: 'ضابط', OBLIGATION: 'التزام', OTHER: 'أخرى',
};

const inputStyle: React.CSSProperties = {
  padding: '6px 8px', borderRadius: 6, border: '1px solid var(--border)',
  fontSize: 12, background: 'var(--surface)', color: 'var(--ink-1)', width: '100%',
};

export function RiskLinksPanel({ riskId }: { riskId: number }) {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [items, setItems] = useState<RiskLinkDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [fType, setFType] = useState<string>('ASSET');
  const [fRef, setFRef] = useState('');
  const [fDesc, setFDesc] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    riskLinkApi.listForRisk(riskId)
      .then(setItems).catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, [riskId]);
  useEffect(() => { load(); }, [load]);

  async function add() {
    if (!fRef.trim()) { toast.warning(isAr ? 'المرجع مطلوب' : 'A target reference is required'); return; }
    setBusy(true);
    try {
      await riskLinkApi.create(riskId, { linkType: fType, targetRef: fRef.trim(), descriptionEn: fDesc.trim() || undefined });
      toast.success(isAr ? 'تمت إضافة الرابط' : 'Link added');
      setFRef(''); setFDesc(''); setShowForm(false); load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل إضافة الرابط' : 'Failed to add link'));
    } finally { setBusy(false); }
  }

  async function remove(l: RiskLinkDto) {
    try {
      await riskLinkApi.remove(l.id);
      toast.success(isAr ? 'تم حذف الرابط' : 'Link removed');
      load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  const typeLabel = (ty: string) => isAr ? (TYPE_LABEL_AR[ty] ?? ty) : ty;

  return (
    <Card title={t('detailTabLinks')} Icon={Link2}
      action={
        <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
          <Badge tone="primary">{items.length}</Badge>
          {!showForm && (
            <Button size="sm" variant="secondary" Icon={Plus} onClick={() => setShowForm(true)}>
              {isAr ? 'ربط' : 'Add link'}
            </Button>
          )}
        </span>
      }>
      {showForm && (
        <div style={{ border: '1px solid var(--border)', borderRadius: 8, padding: 12, marginBottom: 10,
                      background: 'var(--surface)', display: 'grid', gap: 8 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(120px,160px) 1fr', gap: 8 }}>
            <select style={inputStyle} value={fType} onChange={e => setFType(e.target.value)}>
              {RISK_LINK_TYPES.map(ty => <option key={ty} value={ty}>{typeLabel(ty)}</option>)}
            </select>
            <input style={inputStyle} value={fRef} onChange={e => setFRef(e.target.value)}
                   placeholder={isAr ? 'المرجع (مثال: نظام الدفع الأساسي)' : 'Reference (e.g. Core payment service)'} />
          </div>
          <input style={inputStyle} value={fDesc} onChange={e => setFDesc(e.target.value)}
                 placeholder={isAr ? 'وصف (اختياري)' : 'Description (optional)'} />
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button size="sm" variant="ghost" Icon={X} onClick={() => { setShowForm(false); setFRef(''); setFDesc(''); }}>
              {isAr ? 'إلغاء' : 'Cancel'}
            </Button>
            <Button size="sm" variant="primary" Icon={Plus} disabled={busy} onClick={() => void add()}>
              {busy ? (isAr ? 'جارٍ…' : 'Adding…') : (isAr ? 'إضافة' : 'Add')}
            </Button>
          </div>
        </div>
      )}

      {loading ? (
        <div className="text-muted" style={{ fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
      ) : items.length === 0 ? (
        !showForm && <EmptyState Icon={Link2} title={isAr ? 'لا روابط' : 'No links'} sub={t('detailLinksHint')} />
      ) : (
        <div className="risk-detail-rows">
          {items.map(l => (
            <div key={l.id} className="risk-detail-row-link" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <div className="risk-detail-link-group" style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <Badge tone="info">{typeLabel(l.linkType)}</Badge>
                <span className="risk-detail-cell-title--bold">{l.targetRef}</span>
                {l.descriptionEn && <span className="text-muted" style={{ fontSize: 12 }}>{l.descriptionEn}</span>}
              </div>
              <span style={{ marginInlineStart: 'auto' }}>
                <Button size="sm" variant="ghost" Icon={Trash2}
                        title={isAr ? 'حذف' : 'Remove'} onClick={() => void remove(l)} />
              </span>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}

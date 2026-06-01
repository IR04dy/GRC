// Reusable "evidence readiness" panel — for any entity that maps controls.
// For each mapped sub-control it shows the recommended evidence guidance and
// the org's live collection coverage (COVERED / PARTIAL / EXPIRED / GAP), and
// lets the user raise an evidence request inline. Backed by the generic
// /api/v1/control-links/{entityType}/{entityId}/evidence endpoint, so it works
// the same on an AI model, a TPRM vendor, or anything else with a control map.
import { useCallback, useEffect, useState } from 'react';
import { FileCheck2, Send } from 'lucide-react';
import { Badge } from './Badge';
import { Button } from './Button';
import { Modal } from './Modal';
import { EmptyState } from './EmptyState';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError, controlLinksApi, type ControlEvidenceDto, type EvidenceCoverage } from '../../lib/api';
import { raiseEvidenceRequest } from '../../lib/grcExpert';

const COVERAGE_TONE: Record<EvidenceCoverage, 'success' | 'warning' | 'danger' | 'accent'> = {
  COVERED: 'success', PARTIAL: 'warning', EXPIRED: 'accent', GAP: 'danger',
};
const COVERAGE_LABEL: Record<EvidenceCoverage, { en: string; ar: string }> = {
  COVERED: { en: 'Covered', ar: 'مغطّى' },
  PARTIAL: { en: 'Partial', ar: 'جزئي' },
  EXPIRED: { en: 'Expired', ar: 'منتهٍ' },
  GAP:     { en: 'Gap',     ar: 'فجوة' },
};

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '7px 9px', borderRadius: 6,
  border: '1px solid var(--border)', fontSize: 13, background: 'var(--surface)',
};

export interface EvidenceReadinessPanelProps {
  entityType: string;
  entityId: number;
}

export function EvidenceReadinessPanel({ entityType, entityId }: EvidenceReadinessPanelProps) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const [rows, setRows] = useState<ControlEvidenceDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [target, setTarget] = useState<ControlEvidenceDto | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    controlLinksApi.evidence(entityType, entityId)
      .then(setRows).catch(() => setRows([])).finally(() => setLoading(false));
  }, [entityType, entityId]);
  useEffect(() => { load(); }, [load]);

  if (loading) {
    return <div style={{ fontSize: 12, color: 'var(--ink-3)', padding: '10px 0' }}>{ar ? 'جارٍ التحميل…' : 'Loading…'}</div>;
  }
  if (rows.length === 0) {
    return <EmptyState Icon={FileCheck2}
      title={ar ? 'لا توجد ضوابط مرتبطة' : 'No linked controls'}
      sub={ar ? 'اربط الضوابط لعرض الأدلة الموصى بها وحالة جمعها.'
              : 'Map controls to see recommended evidence and its collection status.'} />;
  }
  const counts = rows.reduce<Record<string, number>>((a, r) => { a[r.coverage] = (a[r.coverage] || 0) + 1; return a; }, {});

  return (
    <div>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 12 }}>
        {(['GAP', 'PARTIAL', 'EXPIRED', 'COVERED'] as EvidenceCoverage[]).map(c => (
          <Badge key={c} tone={COVERAGE_TONE[c]}>
            {(ar ? COVERAGE_LABEL[c].ar : COVERAGE_LABEL[c].en)}: {counts[c] || 0}
          </Badge>
        ))}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {rows.map(r => (
          <div key={r.subControlId} style={{ border: '1px solid var(--border)', borderRadius: 8, padding: '10px 12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
                <Badge tone={COVERAGE_TONE[r.coverage]} dot>{ar ? COVERAGE_LABEL[r.coverage].ar : COVERAGE_LABEL[r.coverage].en}</Badge>
                <strong className="mono" style={{ fontSize: 12 }}>{r.frameworkCode} · {r.subControlCode}</strong>
                <span style={{ fontWeight: 600 }}>{ar ? (r.subControlNameAr || r.subControlNameEn) : (r.subControlNameEn || r.subControlNameAr)}</span>
              </span>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                  {ar ? `${r.approved}/${r.total} معتمد` : `${r.approved}/${r.total} approved`}
                  {r.expiringSoon > 0 && (ar ? ` · ${r.expiringSoon} ينتهي قريباً` : ` · ${r.expiringSoon} expiring`)}
                </span>
                <Button size="sm" variant="ghost" Icon={Send} onClick={() => setTarget(r)}>{ar ? 'طلب دليل' : 'Request'}</Button>
              </span>
            </div>
            {(ar ? r.evidenceGuidanceAr : r.evidenceGuidanceEn) && (
              <p style={{ margin: '6px 0 0', fontSize: 12, color: 'var(--ink-3)' }}>
                <FileCheck2 size={12} style={{ verticalAlign: '-2px', marginInlineEnd: 4 }} />
                {ar ? (r.evidenceGuidanceAr || r.evidenceGuidanceEn) : (r.evidenceGuidanceEn || r.evidenceGuidanceAr)}
              </p>
            )}
          </div>
        ))}
      </div>
      {target && <RaiseEvidenceModal row={target} onClose={() => setTarget(null)} />}
    </div>
  );
}

function RaiseEvidenceModal({ row, onClose }: { row: ControlEvidenceDto; onClose: () => void }) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const toast = useToast();
  const [title, setTitle] = useState(
    (ar ? (row.subControlNameAr || row.subControlNameEn) : (row.subControlNameEn || row.subControlNameAr)) || row.subControlCode || '');
  const [priority, setPriority] = useState<'LOW' | 'MEDIUM' | 'HIGH'>('MEDIUM');
  const [due, setDue] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit() {
    if (!title.trim()) { toast.danger(ar ? 'العنوان مطلوب' : 'Title is required'); return; }
    setBusy(true);
    try {
      await raiseEvidenceRequest({ titleEn: title.trim(), subControlId: row.subControlId, priority, dueDate: due || undefined });
      toast.success(ar ? 'تم إنشاء طلب الدليل' : 'Evidence request raised');
      onClose();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (ar ? 'تعذّر إنشاء الطلب' : 'Failed to raise request'));
    } finally { setBusy(false); }
  }
  return (
    <Modal open onClose={onClose} title={ar ? 'طلب دليل' : 'Request evidence'}
      footer={<>
        <Button variant="ghost" onClick={onClose}>{ar ? 'إلغاء' : 'Cancel'}</Button>
        <Button variant="primary" Icon={Send} disabled={busy} onClick={() => { void submit(); }}>
          {busy ? (ar ? 'جارٍ…' : 'Raising…') : (ar ? 'إرسال الطلب' : 'Raise request')}
        </Button>
      </>}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        <div className="mono" style={{ fontSize: 12, color: 'var(--ink-3)' }}>{row.frameworkCode} · {row.subControlCode}</div>
        <label style={{ fontSize: 12, fontWeight: 600 }}>{ar ? 'العنوان' : 'Title'}
          <input style={inputStyle} value={title} onChange={e => setTitle(e.target.value)} />
        </label>
        <label style={{ fontSize: 12, fontWeight: 600 }}>{ar ? 'الأولوية' : 'Priority'}
          <select style={inputStyle} value={priority} onChange={e => setPriority(e.target.value as 'LOW' | 'MEDIUM' | 'HIGH')}>
            <option value="LOW">{ar ? 'منخفضة' : 'Low'}</option>
            <option value="MEDIUM">{ar ? 'متوسطة' : 'Medium'}</option>
            <option value="HIGH">{ar ? 'عالية' : 'High'}</option>
          </select>
        </label>
        <label style={{ fontSize: 12, fontWeight: 600 }}>{ar ? 'تاريخ الاستحقاق' : 'Due date'}
          <input type="date" style={inputStyle} value={due} onChange={e => setDue(e.target.value)} />
        </label>
      </div>
    </Modal>
  );
}

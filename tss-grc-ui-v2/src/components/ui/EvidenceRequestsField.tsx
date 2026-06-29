// Reusable evidence-request field — drop into any detail/workspace surface to
// raise a real "request evidence from a department" against an entity and track
// it through its lifecycle. Wired to the generic evidence-request API
// (entityType/entityId anchor), so the backend EvidenceRequestSlaScheduler
// chases overdue items with reminders + escalation automatically.
//
// Mirrors AttachmentsField: same {entityType, entityId, label?} contract, same
// bare (Card-less) layout so it can sit inside a host Card.
import { useCallback, useEffect, useState } from 'react';
import { ClipboardList, Plus, Send, Trash2, X, AlarmClock } from 'lucide-react';
import { Button } from './Button';
import { Badge, type BadgeTone } from './Badge';
import { ConfirmDialog } from './ConfirmDialog';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  evidenceApi, departmentsApi, ApiError,
  type EvidenceRequestDto, type DepartmentDto,
} from '../../lib/api';

// Mirror of the backend request state machine (EvidenceRequestService).
const STATUS_NEXT: Record<string, string[]> = {
  OPEN:        ['IN_PROGRESS', 'SUBMITTED', 'REJECTED', 'CLOSED'],
  IN_PROGRESS: ['SUBMITTED', 'OPEN', 'REJECTED', 'CLOSED'],
  SUBMITTED:   ['APPROVED', 'REJECTED', 'IN_PROGRESS', 'CLOSED'],
  APPROVED:    ['CLOSED', 'OPEN'],
  REJECTED:    ['OPEN', 'IN_PROGRESS', 'CLOSED'],
  CLOSED:      ['OPEN'],
};

const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;

function statusTone(s?: string): BadgeTone {
  switch (s) {
    case 'IN_PROGRESS': return 'info';
    case 'SUBMITTED':   return 'primary';
    case 'APPROVED':    return 'success';
    case 'REJECTED':    return 'danger';
    case 'OPEN':
    case 'CLOSED':
    default:            return 'neutral';
  }
}

function priorityTone(p?: string): BadgeTone {
  switch (p) {
    case 'CRITICAL': return 'risk-crit';
    case 'HIGH':     return 'risk-high';
    case 'MEDIUM':   return 'warning';
    case 'LOW':      return 'neutral';
    default:         return 'neutral';
  }
}

/** Due-date countdown: overdue (danger) / due-soon (warning) / future (muted). */
function dueInfo(dueDate: string | undefined, status: string | undefined, isAr: boolean):
    { text: string; tone: BadgeTone } | null {
  if (!dueDate) return null;
  if (status === 'CLOSED' || status === 'APPROVED' || status === 'REJECTED') return null;
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const due = new Date(dueDate + 'T00:00:00');
  if (Number.isNaN(due.getTime())) return null;
  const days = Math.round((due.getTime() - today.getTime()) / 86_400_000);
  if (days < 0)  return { text: isAr ? `متأخر ${-days} يوم` : `${-days}d overdue`, tone: 'danger' };
  if (days === 0) return { text: isAr ? 'مستحق اليوم' : 'due today', tone: 'warning' };
  if (days <= 3)  return { text: isAr ? `خلال ${days} يوم` : `in ${days}d`, tone: 'warning' };
  return { text: isAr ? `خلال ${days} يوم` : `in ${days}d`, tone: 'neutral' };
}

const inputStyle: React.CSSProperties = {
  padding: '6px 8px', borderRadius: 6, border: '1px solid var(--border)',
  fontSize: 12, background: 'var(--surface)', color: 'var(--ink-1)', width: '100%',
};

export interface EvidenceRequestsFieldProps {
  entityType: string;
  entityId: number;
  /** Optional heading; defaults to a localized "Evidence requests". */
  label?: string;
}

export function EvidenceRequestsField({ entityType, entityId, label }: EvidenceRequestsFieldProps) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [items, setItems] = useState<EvidenceRequestDto[]>([]);
  const [departments, setDepartments] = useState<DepartmentDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [confirmDel, setConfirmDel] = useState<EvidenceRequestDto | null>(null);

  // form state
  const [fTitleEn, setFTitleEn] = useState('');
  const [fTitleAr, setFTitleAr] = useState('');
  const [fDeptId, setFDeptId] = useState<string>('');
  const [fAssignee, setFAssignee] = useState<string>('');
  const [fDue, setFDue] = useState('');
  const [fPriority, setFPriority] = useState<string>('MEDIUM');
  const [fDesc, setFDesc] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    evidenceApi.requestsByEntity(entityType, entityId)
      .then(setItems).catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, [entityType, entityId]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => { departmentsApi.list().then(setDepartments).catch(() => setDepartments([])); }, []);

  function resetForm() {
    setFTitleEn(''); setFTitleAr(''); setFDeptId(''); setFAssignee('');
    setFDue(''); setFPriority('MEDIUM'); setFDesc('');
  }

  async function create() {
    if (!fTitleEn.trim()) { toast.warning(isAr ? 'العنوان مطلوب' : 'Title required'); return; }
    setBusy(true);
    try {
      await evidenceApi.createRequest({
        titleEn: fTitleEn.trim(),
        titleAr: fTitleAr.trim() || undefined,
        descriptionEn: fDesc.trim() || undefined,
        entityType, entityId,
        departmentId: fDeptId ? Number(fDeptId) : undefined,
        assignedToId: fAssignee ? Number(fAssignee) : undefined,
        dueDate: fDue || undefined,
        priority: fPriority,
      });
      toast.success(isAr ? 'تم رفع طلب الدليل' : 'Evidence request raised');
      resetForm(); setShowForm(false); load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل إنشاء الطلب' : 'Failed to raise request'));
    } finally { setBusy(false); }
  }

  async function move(r: EvidenceRequestDto, to: string) {
    try {
      await evidenceApi.updateRequestStatus(r.id, to);
      toast.success(isAr ? `الطلب → ${to}` : `Request → ${to}`);
      load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function remove() {
    if (!confirmDel) return;
    try {
      await evidenceApi.deleteRequest(confirmDel.id);
      toast.success(isAr ? 'تم حذف الطلب' : 'Request deleted');
      setConfirmDel(null); load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
      setConfirmDel(null);
    }
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, marginBottom: 8 }}>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, fontWeight: 600 }}>
          <ClipboardList size={14} /> {label ?? (isAr ? 'طلبات الأدلة' : 'Evidence requests')}
          {items.length > 0 && <Badge tone="neutral">{items.length}</Badge>}
        </span>
        {!showForm && (
          <Button size="sm" variant="secondary" Icon={Plus} onClick={() => setShowForm(true)}>
            {isAr ? 'طلب دليل' : 'Request evidence'}
          </Button>
        )}
      </div>

      {/* Raise form */}
      {showForm && (
        <div style={{ border: '1px solid var(--border)', borderRadius: 8, padding: 12, marginBottom: 10,
                      background: 'var(--surface)' }}>
          <div style={{ display: 'grid', gap: 8 }}>
            <input style={inputStyle} placeholder={isAr ? 'عنوان الطلب (إنجليزي) *' : 'Request title (EN) *'}
                   value={fTitleEn} onChange={e => setFTitleEn(e.target.value)} />
            <input style={inputStyle} placeholder={isAr ? 'العنوان (عربي)' : 'Title (AR)'}
                   value={fTitleAr} onChange={e => setFTitleAr(e.target.value)} />
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(150px,1fr))', gap: 8 }}>
              <select style={inputStyle} value={fDeptId} onChange={e => setFDeptId(e.target.value)}>
                <option value="">{isAr ? '— الإدارة المسؤولة —' : '— owning department —'}</option>
                {departments.map(d => (
                  <option key={d.id} value={d.id}>{isAr && d.nameAr ? d.nameAr : d.nameEn}</option>
                ))}
              </select>
              <input style={inputStyle} type="number" min={1}
                     placeholder={isAr ? 'رقم المكلَّف (اختياري)' : 'Assignee user id (optional)'}
                     value={fAssignee} onChange={e => setFAssignee(e.target.value)} />
              <input style={inputStyle} type="date" value={fDue} onChange={e => setFDue(e.target.value)}
                     title={isAr ? 'تاريخ الاستحقاق' : 'Due date'} />
              <select style={inputStyle} value={fPriority} onChange={e => setFPriority(e.target.value)}>
                {PRIORITIES.map(p => <option key={p} value={p}>{p}</option>)}
              </select>
            </div>
            <textarea style={{ ...inputStyle, minHeight: 54 }}
                      placeholder={isAr ? 'الوصف / ما المطلوب رفعه' : 'Description / what to provide'}
                      value={fDesc} onChange={e => setFDesc(e.target.value)} />
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <Button size="sm" variant="ghost" Icon={X} onClick={() => { setShowForm(false); resetForm(); }}>
                {isAr ? 'إلغاء' : 'Cancel'}
              </Button>
              <Button size="sm" variant="primary" Icon={Send} disabled={busy} onClick={() => void create()}>
                {busy ? (isAr ? 'جارٍ…' : 'Raising…') : (isAr ? 'رفع الطلب' : 'Raise request')}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* List */}
      {loading ? (
        <div className="text-muted" style={{ fontSize: 11 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
      ) : items.length === 0 ? (
        !showForm && <div className="text-muted" style={{ fontSize: 11 }}>
          {isAr ? 'لا توجد طلبات أدلة بعد.' : 'No evidence requests yet.'}
        </div>
      ) : (
        <div style={{ display: 'grid', gap: 6 }}>
          {items.map(r => {
            const due = dueInfo(r.dueDate, r.status, isAr);
            const owner = r.assignedToName || r.departmentName
              || (r.departmentId ? `dept #${r.departmentId}` : null);
            return (
              <div key={r.id} style={{ border: '1px solid var(--border)', borderRadius: 6, padding: '8px 10px',
                                       display: 'grid', gap: 4, background: 'var(--surface)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  <span className="mono text-2xs text-muted">{r.requestCode}</span>
                  <span style={{ fontSize: 13, fontWeight: 600 }}>{isAr && r.titleAr ? r.titleAr : r.titleEn}</span>
                  <Badge tone={statusTone(r.status)}>{r.status}</Badge>
                  {r.priority && <Badge tone={priorityTone(r.priority)}>{r.priority}</Badge>}
                  {due && <Badge tone={due.tone}><AlarmClock size={11} /> {due.text}</Badge>}
                </div>
                {(owner || r.dueDate || r.descriptionEn) && (
                  <div className="text-2xs text-muted" style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                    {owner && <span>{isAr ? 'المسؤول:' : 'Owner:'} {owner}</span>}
                    {r.dueDate && <span>{isAr ? 'الاستحقاق:' : 'Due:'} {r.dueDate}</span>}
                    {r.descriptionEn && <span style={{ maxWidth: 360, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{r.descriptionEn}</span>}
                  </div>
                )}
                <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', alignItems: 'center' }}>
                  {(STATUS_NEXT[r.status ?? 'OPEN'] ?? []).map(to => (
                    <Button key={to} size="sm" variant="ghost" onClick={() => void move(r, to)}>{to}</Button>
                  ))}
                  <span style={{ marginInlineStart: 'auto' }}>
                    <Button size="sm" variant="ghost" Icon={Trash2}
                            title={isAr ? 'حذف' : 'Delete'} onClick={() => setConfirmDel(r)} />
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {confirmDel && (
        <ConfirmDialog
          open
          title={isAr ? 'حذف طلب الدليل؟' : 'Delete evidence request?'}
          message={`${confirmDel.requestCode} — ${confirmDel.titleEn}`}
          confirmLabel={isAr ? 'حذف' : 'Delete'}
          destructive
          onConfirm={() => void remove()}
          onCancel={() => setConfirmDel(null)}
        />
      )}
    </div>
  );
}

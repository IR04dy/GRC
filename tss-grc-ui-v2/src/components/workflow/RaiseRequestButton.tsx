// Workflow adoption — the reusable, engine-routed "Raise request" action that any module drops
// onto its page/record. Instead of a bespoke per-module Send/Request/Assign button, this opens
// the platform workflow request composer (§5 actor selector + §6 lifecycle), pre-anchored to the
// caller's source record (sourceModule/sourceRecordType/sourceRecordId) so the created
// WorkflowRequest is a real, tracked, routed, SLA'd object — not a dead button. Reuses the WF-8
// recipient selector (with owner suggestions for the module) and create→send.
import { useState } from 'react';
import { Send } from 'lucide-react';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { Input, Textarea } from '../ui/Input';
import { Select } from '../ui/Select';
import { WorkflowActorSelector, validateRecipients } from './WorkflowActorSelector';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  workflowRequestsApi, type WorkflowRequestDto, type WorkflowRecipientInput, type RoutingMode,
} from '../../lib/workflowRequests';

const REQUEST_TYPES = ['EVIDENCE_REQUEST', 'APPROVAL', 'REVIEW', 'TASK', 'ACKNOWLEDGEMENT',
  'INFORMATION', 'REPORT_DISTRIBUTION', 'FOLLOW_UP'];

interface Props {
  /** The module raising the request (RISK / CYBER / COMPLIANCE / …). Anchors + drives suggestions. */
  sourceModule: string;
  /** Optional anchor to the specific record the request is about. */
  sourceRecordType?: string;
  sourceRecordId?: number;
  defaultTitleEn?: string;
  defaultRequestType?: string;
  label?: string;
  size?: 'sm' | 'md';
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  onCreated?: (created: WorkflowRequestDto) => void;
}

/** A one-click entry point that raises a real workflow request through the platform engine. */
export function RaiseRequestButton({
  sourceModule, sourceRecordType, sourceRecordId, defaultTitleEn, defaultRequestType,
  label, size = 'sm', variant = 'secondary', onCreated,
}: Props) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [recipients, setRecipients] = useState<WorkflowRecipientInput[]>([]);
  const [form, setForm] = useState({
    titleEn: defaultTitleEn ?? '', titleAr: '', requestType: defaultRequestType ?? 'TASK',
    routingMode: 'PARALLEL' as RoutingMode, completionRule: 'ALL' as 'ALL' | 'ANY',
    dueDate: '', priority: 'MEDIUM', reminderDaysBefore: 3, escalationRole: '', description: '',
  });
  function set<K extends keyof typeof form>(k: K, v: (typeof form)[K]) { setForm(f => ({ ...f, [k]: v })); }

  function reset() {
    setForm({
      titleEn: defaultTitleEn ?? '', titleAr: '', requestType: defaultRequestType ?? 'TASK',
      routingMode: 'PARALLEL', completionRule: 'ALL', dueDate: '', priority: 'MEDIUM',
      reminderDaysBefore: 3, escalationRole: '', description: '',
    });
    setRecipients([]);
  }

  async function submit() {
    const recErr = validateRecipients(recipients, form.routingMode, ar);
    if (!form.titleEn.trim()) { toast.danger(ar ? 'العنوان مطلوب' : 'Title is required'); return; }
    if (recErr) { toast.danger(recErr); return; }
    if (!form.dueDate) { toast.danger(ar ? 'تاريخ الاستحقاق مطلوب' : 'Due date is required'); return; }
    if ((form.priority === 'HIGH' || form.priority === 'CRITICAL') && !form.escalationRole
        && !recipients.some(r => r.actorRole === 'ESCALATION')) {
      toast.danger(ar ? 'الأولوية العالية تتطلب مسار تصعيد' : 'HIGH/CRITICAL priority requires an escalation path');
      return;
    }
    setBusy(true);
    try {
      const created = await workflowRequestsApi.create({
        sourceModule, sourceRecordType, sourceRecordId,
        requestType: form.requestType, titleEn: form.titleEn, titleAr: form.titleAr || undefined,
        description: form.description || undefined, routingMode: form.routingMode,
        completionRule: form.routingMode === 'PARALLEL' ? form.completionRule : 'ALL',
        dueDate: form.dueDate, priority: form.priority as never,
        reminderDaysBefore: form.reminderDaysBefore, escalationRole: form.escalationRole || undefined,
        responseRequired: true, recipients,
      });
      await workflowRequestsApi.send(created.id);
      toast.success(ar ? `أُرسل الطلب ${created.requestCode}` : `Request ${created.requestCode} sent`);
      setOpen(false); reset(); onCreated?.(created);
    } catch (e) {
      toast.danger((e as Error)?.message ?? (ar ? 'فشل الإرسال' : 'Send failed'));
    } finally { setBusy(false); }
  }

  return (
    <>
      <Button size={size} variant={variant} onClick={() => setOpen(true)}>
        <Send size={13} /> {label ?? (ar ? 'رفع طلب' : 'Raise request')}
      </Button>

      <Modal open={open} size="lg"
        title={(ar ? 'طلب سير عمل جديد' : 'New workflow request') + ` · ${sourceModule}`}
        onClose={() => setOpen(false)}
        footer={<>
          <Button variant="ghost" onClick={() => setOpen(false)}>{ar ? 'إلغاء' : 'Cancel'}</Button>
          <Button variant="primary" onClick={() => void submit()} disabled={busy}>
            <Send size={14} /> {busy ? (ar ? 'جارٍ الإرسال…' : 'Sending…') : (ar ? 'إنشاء وإرسال' : 'Create & send')}
          </Button>
        </>}>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 10, marginBottom: 10 }}>
          <Input label={(ar ? 'العنوان (EN)' : 'Title (EN)') + ' *'} value={form.titleEn} onChange={e => set('titleEn', e.target.value)} />
          <Select label={ar ? 'نوع الطلب' : 'Request type'} value={form.requestType}
            onChange={e => set('requestType', e.target.value)}
            options={REQUEST_TYPES.map(t => ({ value: t, label: t.replace(/_/g, ' ') }))} />
        </div>
        <div style={{ marginBottom: 10 }}>
          <Input label={ar ? 'العنوان (AR)' : 'Title (AR)'} value={form.titleAr} onChange={e => set('titleAr', e.target.value)} dir="rtl" />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 10, marginBottom: 10 }}>
          <Select label={ar ? 'التوجيه' : 'Routing'} value={form.routingMode}
            onChange={e => set('routingMode', e.target.value as RoutingMode)}
            options={[{ value: 'PARALLEL', label: ar ? 'متوازٍ' : 'Parallel' },
                      { value: 'SEQUENTIAL', label: ar ? 'تسلسلي' : 'Sequential' },
                      { value: 'BROADCAST_ACK', label: ar ? 'تعميم بإقرار' : 'Broadcast + ack' }]} />
          {form.routingMode === 'PARALLEL' ? (
            <Select label={ar ? 'قاعدة الاكتمال' : 'Completion'} value={form.completionRule}
              onChange={e => set('completionRule', e.target.value as 'ALL' | 'ANY')}
              options={[{ value: 'ALL', label: ar ? 'استجابة الجميع' : 'ALL respond' },
                        { value: 'ANY', label: ar ? 'استجابة أي أحد' : 'ANY one responds' }]} />
          ) : <div />}
          <Input label={(ar ? 'الاستحقاق' : 'Due date') + ' *'} type="date" value={form.dueDate} onChange={e => set('dueDate', e.target.value)} />
          <Select label={ar ? 'الأولوية' : 'Priority'} value={form.priority}
            onChange={e => set('priority', e.target.value)}
            options={['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(p => ({ value: p, label: p }))} />
          <Input label={ar ? 'تذكير قبل (أيام)' : 'Remind before (days)'} type="number" min={0}
            value={form.reminderDaysBefore}
            onChange={e => set('reminderDaysBefore', e.target.value ? Number(e.target.value) : 0)} />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 10, marginBottom: 12 }}>
          <Input label={ar ? 'دور التصعيد' : 'Escalation role'} value={form.escalationRole} placeholder="CISO"
            onChange={e => set('escalationRole', e.target.value)} />
          <Textarea label={ar ? 'الوصف' : 'Description'} rows={2} value={form.description}
            onChange={e => set('description', e.target.value)} />
        </div>
        <WorkflowActorSelector recipients={recipients} onChange={setRecipients}
          routingMode={form.routingMode} sourceModule={sourceModule} />
        {sourceRecordId != null && (
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 8 }}>
            {(ar ? 'مرتبط بـ ' : 'Anchored to ') + `${sourceRecordType ?? sourceModule} #${sourceRecordId}`}
          </div>
        )}
      </Modal>
    </>
  );
}

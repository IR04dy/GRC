// §9 forms — a drawer-based, engine-routed request composer for the mid-weight GRC actions the
// remediation plan designed but the platform lacked (KRI-breach response, patch request, SOC
// containment, data-sharing, processor review). Each is a real, tracked, routed, SLA'd
// WorkflowRequest — anchored to its source record — captured through the GRCStructuredDrawer so
// the parent page stays in context. Reuses the WF-8 actor selector + the extracted routing/SLA
// selectors. Domain-specific fields are folded into the request description (the engine stays
// generic: request_type is a free string, so each form carries a distinct type — no new schema).
import { useState } from 'react';
import { Send, type LucideIcon } from 'lucide-react';
import { Button } from '../ui/Button';
import { GRCStructuredDrawer } from '../ui/GRCStructuredDrawer';
import { Input, Textarea } from '../ui/Input';
import { Select } from '../ui/Select';
import { WorkflowActorSelector, validateRecipients } from './WorkflowActorSelector';
import { WorkflowRoutingSelector } from './WorkflowRoutingSelector';
import { SLAAndCountdownSelector } from './SLAAndCountdownSelector';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  workflowRequestsApi, type WorkflowRequestDto, type WorkflowRecipientInput, type RoutingMode,
} from '../../lib/workflowRequests';

/** A domain-specific field captured into the request description. */
export interface DomainField {
  key: string;
  labelEn: string;
  labelAr: string;
  kind: 'text' | 'textarea' | 'select' | 'date' | 'number';
  options?: { value: string; labelEn: string; labelAr: string }[];
  required?: boolean;
  placeholder?: string;
  /** Span both columns of the field grid. */
  fullWidth?: boolean;
}

interface Props {
  sourceModule: string;
  sourceRecordType?: string;
  sourceRecordId?: number;
  /** Distinct engine request type, e.g. PATCH_REQUEST / CONTAINMENT / DATA_SHARING. */
  requestType: string;
  /** Drawer heading — the kind of request. */
  headingEn: string;
  headingAr: string;
  /** Prefill for the request's own title field. */
  defaultTitleEn?: string;
  /** Domain fields captured into the request description. */
  fields: DomainField[];
  defaultRouting?: RoutingMode;
  defaultPriority?: string;
  /** Trigger button. */
  label: string;
  labelAr: string;
  Icon?: LucideIcon;
  size?: 'sm' | 'md';
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  onCreated?: (r: WorkflowRequestDto) => void;
}

/** A one-click, engine-routed request form rendered in a context-preserving drawer. */
export function GRCRequestDrawer({
  sourceModule, sourceRecordType, sourceRecordId, requestType, headingEn, headingAr,
  defaultTitleEn, fields, defaultRouting = 'PARALLEL', defaultPriority = 'MEDIUM',
  label, labelAr, Icon = Send, size = 'sm', variant = 'secondary', onCreated,
}: Props) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [recipients, setRecipients] = useState<WorkflowRecipientInput[]>([]);
  const [domain, setDomain] = useState<Record<string, string>>({});
  const [form, setForm] = useState({
    titleEn: defaultTitleEn ?? '', titleAr: '',
    routingMode: defaultRouting, completionRule: 'ALL' as 'ALL' | 'ANY',
    dueDate: '', priority: defaultPriority, reminderDaysBefore: 3, escalationRole: '', description: '',
  });

  function reset() {
    setForm({
      titleEn: defaultTitleEn ?? '', titleAr: '', routingMode: defaultRouting, completionRule: 'ALL',
      dueDate: '', priority: defaultPriority, reminderDaysBefore: 3, escalationRole: '', description: '',
    });
    setDomain({});
    setRecipients([]);
  }

  /** Fold the domain fields + free-text notes into a structured request description. */
  function composeDescription(): string | undefined {
    const block = fields
      .map(f => { const v = (domain[f.key] ?? '').trim(); return v ? `${ar ? f.labelAr : f.labelEn}: ${v}` : null; })
      .filter(Boolean).join('\n');
    const notes = form.description.trim();
    const out = [block, notes].filter(Boolean).join('\n\n');
    return out || undefined;
  }

  async function submit() {
    if (!form.titleEn.trim()) { toast.danger(ar ? 'العنوان مطلوب' : 'Title is required'); return; }
    const missing = fields.find(f => f.required && !(domain[f.key] ?? '').trim());
    if (missing) { toast.danger((ar ? missing.labelAr : missing.labelEn) + (ar ? ' مطلوب' : ' is required')); return; }
    const recErr = validateRecipients(recipients, form.routingMode, ar);
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
        requestType, titleEn: form.titleEn, titleAr: form.titleAr || undefined,
        description: composeDescription(), routingMode: form.routingMode,
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

  function renderField(f: DomainField) {
    const label = (ar ? f.labelAr : f.labelEn) + (f.required ? ' *' : '');
    const v = domain[f.key] ?? '';
    const set = (val: string) => setDomain(d => ({ ...d, [f.key]: val }));
    const style = f.fullWidth || f.kind === 'textarea' ? { gridColumn: '1 / -1' } : undefined;
    if (f.kind === 'select') {
      return <div key={f.key} style={style}>
        <Select label={label} value={v} onChange={e => set(e.target.value)}
          options={[{ value: '', label: ar ? '—' : '—' },
            ...(f.options ?? []).map(o => ({ value: o.value, label: ar ? o.labelAr : o.labelEn }))]} />
      </div>;
    }
    if (f.kind === 'textarea') {
      return <div key={f.key} style={style}>
        <Textarea label={label} rows={2} value={v} placeholder={f.placeholder} onChange={e => set(e.target.value)} />
      </div>;
    }
    return <div key={f.key} style={style}>
      <Input label={label} type={f.kind === 'number' ? 'number' : f.kind === 'date' ? 'date' : 'text'}
        value={v} placeholder={f.placeholder} onChange={e => set(e.target.value)} />
    </div>;
  }

  const anchor = sourceRecordId != null
    ? (ar ? 'مرتبط بـ ' : 'Anchored to ') + `${sourceRecordType ?? sourceModule} #${sourceRecordId}`
    : undefined;

  return (
    <>
      <Button size={size} variant={variant} onClick={() => setOpen(true)}>
        <Icon size={13} /> {ar ? labelAr : label}
      </Button>

      <GRCStructuredDrawer open={open} width={540}
        title={(ar ? headingAr : headingEn) + ` · ${sourceModule}`}
        subtitle={anchor}
        onClose={() => setOpen(false)}
        footer={<>
          <Button variant="ghost" onClick={() => setOpen(false)}>{ar ? 'إلغاء' : 'Cancel'}</Button>
          <Button variant="primary" onClick={() => void submit()} disabled={busy}>
            <Send size={14} /> {busy ? (ar ? 'جارٍ الإرسال…' : 'Sending…') : (ar ? 'إنشاء وإرسال' : 'Create & send')}
          </Button>
        </>}>
        <div style={{ marginBottom: 10 }}>
          <Input label={(ar ? 'العنوان (EN)' : 'Title (EN)') + ' *'} value={form.titleEn}
            onChange={e => setForm(f => ({ ...f, titleEn: e.target.value }))} />
        </div>
        <div style={{ marginBottom: 10 }}>
          <Input label={ar ? 'العنوان (AR)' : 'Title (AR)'} value={form.titleAr} dir="rtl"
            onChange={e => setForm(f => ({ ...f, titleAr: e.target.value }))} />
        </div>
        {fields.length > 0 && (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 10, marginBottom: 10 }}>
            {fields.map(renderField)}
          </div>
        )}
        <WorkflowRoutingSelector routingMode={form.routingMode} completionRule={form.completionRule}
          onChange={patch => setForm(f => ({ ...f, ...patch }))} />
        <SLAAndCountdownSelector
          value={{ dueDate: form.dueDate, priority: form.priority,
            reminderDaysBefore: form.reminderDaysBefore, escalationRole: form.escalationRole }}
          onChange={patch => setForm(f => ({ ...f, ...patch }))} />
        <div style={{ marginBottom: 12 }}>
          <Textarea label={ar ? 'ملاحظات' : 'Notes'} rows={2} value={form.description}
            onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />
        </div>
        <WorkflowActorSelector recipients={recipients} onChange={setRecipients}
          routingMode={form.routingMode} sourceModule={sourceModule} />
      </GRCStructuredDrawer>
    </>
  );
}

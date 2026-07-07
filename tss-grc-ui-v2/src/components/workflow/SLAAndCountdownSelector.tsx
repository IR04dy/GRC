import { Input } from '../ui/Input';
import { Select } from '../ui/Select';
import { useI18n } from '../../lib/i18n';

export interface SlaValue {
  dueDate: string;
  priority: string;
  reminderDaysBefore: number;
  escalationRole: string;
}

interface Props {
  value: SlaValue;
  /** Patch callback — emits only changed keys so the caller can spread into form state. */
  onChange: (patch: Partial<SlaValue>) => void;
  /** Mark the due date required (default true). */
  dueRequired?: boolean;
}

/**
 * SLAAndCountdownSelector — the due date + priority + reminder-days + escalation-role block
 * that drives an engine request's SLA countdown, reminder sweep, and escalation path.
 * Extracted from the request composer so every actionable form applies SLA/escalation
 * consistently. HIGH/CRITICAL priority is expected to carry an escalation path (role here or
 * an ESCALATION recipient) — callers validate that on submit.
 */
export function SLAAndCountdownSelector({ value, onChange, dueRequired = true }: Props) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10, marginBottom: 10 }}>
      <Input label={(ar ? 'الاستحقاق' : 'Due date') + (dueRequired ? ' *' : '')} type="date"
        value={value.dueDate} onChange={e => onChange({ dueDate: e.target.value })} />
      <Select label={ar ? 'الأولوية' : 'Priority'} value={value.priority}
        onChange={e => onChange({ priority: e.target.value })}
        options={['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(p => ({ value: p, label: p }))} />
      <Input label={ar ? 'تذكير قبل (أيام)' : 'Remind before (days)'} type="number" min={0}
        value={value.reminderDaysBefore}
        onChange={e => onChange({ reminderDaysBefore: e.target.value ? Number(e.target.value) : 0 })} />
      <Input label={ar ? 'دور التصعيد' : 'Escalation role'} value={value.escalationRole} placeholder="CISO"
        onChange={e => onChange({ escalationRole: e.target.value })} />
    </div>
  );
}

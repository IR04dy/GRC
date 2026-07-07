import { Select } from '../ui/Select';
import { useI18n } from '../../lib/i18n';
import type { RoutingMode } from '../../lib/workflowRequests';

interface Props {
  routingMode: RoutingMode;
  completionRule: 'ALL' | 'ANY';
  /** Patch callback — emits only the changed key so the caller can spread it into form state. */
  onChange: (patch: { routingMode?: RoutingMode; completionRule?: 'ALL' | 'ANY' }) => void;
}

/**
 * WorkflowRoutingSelector — routing mode (SEQUENTIAL / PARALLEL / BROADCAST_ACK) plus,
 * for PARALLEL, the completion rule (ALL vs ANY). Extracted from the request composer so
 * every request/task-raising form across the platform routes through the engine identically.
 * Renders a 2-column grid (routing + completion, or a spacer when routing isn't PARALLEL).
 */
export function WorkflowRoutingSelector({ routingMode, completionRule, onChange }: Props) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 10, marginBottom: 10 }}>
      <Select label={ar ? 'التوجيه' : 'Routing'} value={routingMode}
        onChange={e => onChange({ routingMode: e.target.value as RoutingMode })}
        options={[{ value: 'PARALLEL', label: ar ? 'متوازٍ' : 'Parallel' },
                  { value: 'SEQUENTIAL', label: ar ? 'تسلسلي' : 'Sequential' },
                  { value: 'BROADCAST_ACK', label: ar ? 'تعميم بإقرار' : 'Broadcast + ack' }]} />
      {routingMode === 'PARALLEL' ? (
        <Select label={ar ? 'قاعدة الاكتمال' : 'Completion'} value={completionRule}
          onChange={e => onChange({ completionRule: e.target.value as 'ALL' | 'ANY' })}
          options={[{ value: 'ALL', label: ar ? 'استجابة الجميع' : 'ALL respond' },
                    { value: 'ANY', label: ar ? 'استجابة أي أحد' : 'ANY one responds' }]} />
      ) : <div />}
    </div>
  );
}

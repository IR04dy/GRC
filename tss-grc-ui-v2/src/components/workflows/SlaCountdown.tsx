import { useEffect, useState } from 'react';
import { Hourglass, AlertTriangle, AlertOctagon, Flame, ShieldCheck, Clock } from 'lucide-react';
import { Badge } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';
import { ESCALATION_TONE } from '../../lib/workflows';
import {
  deriveLiveSla, formatHijri,
  type LiveWorkflowInstance,
} from '../../lib/sla';
import type { WorkflowInstance } from '../../lib/workflows';

const TIER_ICON = {
  ON_TRACK:     ShieldCheck,
  AT_RISK:      Clock,
  OVERDUE:      AlertTriangle,
  MANAGER_ESC:  AlertOctagon,
  DIRECTOR_ESC: AlertOctagon,
  EXEC_ESC:     Flame,
} as const;

/**
 * Live-updating SLA badge — re-derives tier and remaining time every minute
 * so the page reflects "now", not the snapshot the mock data was authored at.
 * Uses the Saudi work calendar (Fri–Sat weekend) and surfaces Hijri due-date
 * in the tooltip.
 */
export function SlaCountdown({ instance, compact }: { instance: WorkflowInstance | LiveWorkflowInstance; compact?: boolean }) {
  const { t, lang } = useI18n();
  const [now, setNow] = useState<Date>(() => new Date());

  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(id);
  }, []);

  const live = 'sla' in instance ? instance : deriveLiveSla(instance, undefined, now);
  const Icon = TIER_ICON[live.sla.tier] ?? Hourglass;

  const remaining = live.sla.daysRemaining;
  const business = live.sla.businessDaysRemaining;

  const labelLeft =
    remaining < 0
      ? t('slaOverdueByDays').replace('{n}', String(-remaining))
      : remaining === 0
        ? t('slaDueToday')
        : t('slaDaysRemaining').replace('{n}', String(remaining));

  const businessLabel = business !== remaining
    ? ` · ${t('slaBusinessDays').replace('{n}', String(business))}`
    : '';

  const hijri = formatHijri(instance.dueDate ?? '', lang);

  if (compact) {
    return (
      <Badge tone={ESCALATION_TONE[live.sla.tier]}>
        <Icon size={10} style={{ marginInlineEnd: 4, verticalAlign: 'middle' }} />
        {labelLeft}
      </Badge>
    );
  }

  return (
    <div title={`${live.sla.reason} · ${t('slaHijriLabel')} ${hijri}`}
      style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      <Badge tone={ESCALATION_TONE[live.sla.tier]}>
        <Icon size={11} style={{ marginInlineEnd: 4, verticalAlign: 'middle' }} />
        {labelLeft}{businessLabel}
      </Badge>
      <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>
        {live.sla.percentBreach > 0
          ? `+${live.sla.percentBreach}%`
          : `${live.sla.percentConsumed}% ${t('slaConsumedSuffix')}`}
      </span>
    </div>
  );
}

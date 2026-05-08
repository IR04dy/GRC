import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { History } from 'lucide-react';
import { useI18n } from '../../lib/i18n';
import {
  POLICY_STAGES, STAGE_LABEL, STAGE_TONE, isStagePassed,
  type PolicyStage,
} from '../../lib/policyLifecycle';

/**
 * PolicyLifecycleStrip — Sprint 6.
 *
 * Visual progression for the 6-stage policy state machine. Highlights the
 * current stage, dims past stages with a checkmark, and greys out future
 * stages. RETIRED is shown as the terminal end-cap when active.
 */
export function PolicyLifecycleStrip({ current }: { current: PolicyStage }) {
  const { t, lang } = useI18n();

  // RETIRED is special — collapse the strip into a single "retired" state
  // so the board sees the policy is decommissioned without the noise of
  // pretending it's still moving through stages.
  const stages: PolicyStage[] = current === 'RETIRED'
    ? ['DRAFT', 'PUBLISHED', 'RETIRED']
    : POLICY_STAGES.filter(s => s !== 'RETIRED');

  return (
    <Card title={t('policyLifecycleStripTitle')} Icon={History}
      action={<Badge tone={STAGE_TONE[current]}>{lang === 'ar' ? STAGE_LABEL[current].ar : STAGE_LABEL[current].en}</Badge>}>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 12 }}>
        {t('policyLifecycleStripSub')}
      </div>
      <div style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${stages.length}, 1fr)`,
        gap: 8,
      }}>
        {stages.map(s => {
          const isCurrent = s === current;
          const passed = isStagePassed(s, current);
          return (
            <div key={s} style={{
              padding: 12,
              borderRadius: 'var(--r-sm)',
              background: isCurrent
                ? `var(--${STAGE_TONE[s]}-bg, var(--primary-50))`
                : passed
                  ? 'var(--success-bg)'
                  : 'var(--surface-2)',
              border: `1.5px solid ${isCurrent
                ? `var(--${STAGE_TONE[s]}-fg, var(--primary))`
                : passed
                  ? 'var(--success-fg)'
                  : 'var(--border)'}`,
              opacity: passed && !isCurrent ? 0.85 : 1,
              textAlign: 'center',
            }}>
              <div style={{
                fontSize: 11,
                fontWeight: 700,
                color: isCurrent
                  ? `var(--${STAGE_TONE[s]}-fg, var(--primary))`
                  : passed
                    ? 'var(--success-fg)'
                    : 'var(--ink-3)',
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
              }}>
                {passed && !isCurrent ? '✓ ' : ''}
                {lang === 'ar' ? STAGE_LABEL[s].ar : STAGE_LABEL[s].en}
              </div>
              {isCurrent && (
                <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 4 }}>
                  {t('policyLifecycleCurrentLabel')}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </Card>
  );
}

import { useMemo } from 'react';
import { Stamp, CheckCircle2, Clock, AlertTriangle } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';
import { buildMilestones, synthesiseEngagementWorkflow } from '../../lib/auditEngagement';
import { deriveLiveSla, formatHijri } from '../../lib/sla';
import { ESCALATION_TONE } from '../../lib/workflows';
import { SlaCountdown } from '../workflows/SlaCountdown';
import type { AuditEngagementDto } from '../../lib/api';

/**
 * EngagementMilestonePanel — Sprint 4 audit-engagement timeline with live
 * SLA on the *current* milestone (not just dates). Replaces the static
 * "milestone checklist" the existing detail page renders. Each row shows:
 *   • Bilingual label
 *   • Gregorian + Hijri date
 *   • Past/current/future status
 *   • For the current milestone: live SlaCountdown badge driven by the
 *     Sprint 4 SLA engine.
 */
export function EngagementMilestonePanel({ engagement }: { engagement: AuditEngagementDto }) {
  const { t, lang } = useI18n();

  const milestones = useMemo(() => buildMilestones(engagement), [engagement]);
  const currentSyn = useMemo(() => synthesiseEngagementWorkflow(engagement), [engagement]);
  const currentLive = currentSyn ? deriveLiveSla(currentSyn) : null;

  const passed = milestones.filter(m => m.isPast).length;
  const total = milestones.filter(m => m.date).length;

  return (
    <Card title={t('engMilestoneTitle')} Icon={Stamp}
      action={
        <div style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
          <Badge tone="primary">{passed} / {total}</Badge>
          {currentLive && (
            <Badge tone={ESCALATION_TONE[currentLive.sla.tier]}>
              {currentLive.sla.tier === 'OVERDUE' || currentLive.sla.tier.endsWith('_ESC')
                ? t('engBreachedShort').replace('{n}', String(-currentLive.sla.daysRemaining))
                : t('engRemainingShort').replace('{n}', String(Math.max(0, currentLive.sla.daysRemaining)))}
            </Badge>
          )}
        </div>
      }>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
        {t('engMilestoneSub')}
      </div>

      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {milestones.map((m, idx) => {
          const label = lang === 'ar' ? m.labelAr : m.labelEn;
          const dateLabel = m.date
            ? new Date(m.date).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB',
                { day: '2-digit', month: 'short', year: 'numeric' })
            : '—';
          const hijri = m.date ? formatHijri(m.date, lang) : '—';
          const Icon = m.isPast && !m.isCurrent ? CheckCircle2
                     : m.isCurrent ? Clock
                                   : AlertTriangle;
          const iconColour = m.isPast && !m.isCurrent ? 'var(--success-fg)'
                            : m.isCurrent ? 'var(--warning-fg)'
                            : 'var(--ink-3)';
          return (
            <div key={m.code} style={{
              display: 'grid',
              gridTemplateColumns: '32px 1fr auto',
              gap: 12, alignItems: 'center',
              padding: '10px 0',
              borderBottom: idx === milestones.length - 1 ? 'none' : '1px dashed var(--border)',
              background: m.isCurrent ? 'var(--warning-bg)' : 'transparent',
              borderRadius: m.isCurrent ? 'var(--r-sm)' : 0,
              paddingInline: m.isCurrent ? 10 : 0,
            }}>
              <Icon size={18} color={iconColour} />
              <div>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                  {label}
                </div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                  {dateLabel} · {t('engHijriLabel')} {hijri}
                </div>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
                {m.isCurrent && currentSyn ? (
                  <SlaCountdown instance={currentSyn} />
                ) : m.isPast ? (
                  <Badge tone="success">{t('engStatusDone')}</Badge>
                ) : (
                  <Badge tone="neutral">{t('engStatusUpcoming')}</Badge>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

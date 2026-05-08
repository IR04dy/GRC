import { useMemo } from 'react';
import { AlertOctagon, Flame, Clock, Landmark } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';
import { SAUDI_APPETITES, APPETITE_SEVERITY_TONE, appetiteSeverityLabel } from '../../lib/riskAppetiteSaudi';
import { synthesiseAllBreaches } from '../../lib/appetiteBreach';
import { SlaCountdown } from '../workflows/SlaCountdown';

/**
 * AppetiteBreachFeed — hero panel for the Risk → Appetite tab.
 *
 * Shows every Saudi appetite statement currently outside its tolerance band,
 * with severity, regulator citation, and a live SLA countdown driven by the
 * Sprint 4 SLA engine. The point: a board sees not just *that* the firm is
 * outside appetite, but how long until escalation.
 */
export function AppetiteBreachFeed() {
  const { t, lang } = useI18n();
  const breaches = useMemo(() => synthesiseAllBreaches(SAUDI_APPETITES), []);

  if (breaches.length === 0) {
    return (
      <Card title={t('appetiteBreachFeedTitle')} Icon={AlertOctagon}>
        <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
          {t('appetiteBreachFeedEmpty')}
        </div>
      </Card>
    );
  }

  // Sort: CRITICAL first, then EXCEEDED, then WARNING.
  const order = { CRITICAL: 0, EXCEEDED: 1, WARNING: 2, WITHIN_APPETITE: 3 } as const;
  const sorted = [...breaches].sort((a, b) => order[a.severity] - order[b.severity]);

  const counts = sorted.reduce(
    (acc, b) => {
      if (b.severity === 'CRITICAL') acc.critical++;
      else if (b.severity === 'EXCEEDED') acc.exceeded++;
      else if (b.severity === 'WARNING') acc.warning++;
      return acc;
    },
    { critical: 0, exceeded: 0, warning: 0 },
  );

  return (
    <Card title={t('appetiteBreachFeedTitle')} Icon={AlertOctagon}
      action={
        <div style={{ display: 'inline-flex', gap: 6 }}>
          {counts.critical > 0 && <Badge tone="danger"><Flame size={10} style={{ marginInlineEnd: 3, verticalAlign: 'middle' }}/>{counts.critical} {t('appetiteSevCritical')}</Badge>}
          {counts.exceeded > 0 && <Badge tone="warning"><AlertOctagon size={10} style={{ marginInlineEnd: 3, verticalAlign: 'middle' }}/>{counts.exceeded} {t('appetiteSevExceeded')}</Badge>}
          {counts.warning  > 0 && <Badge tone="info"><Clock size={10} style={{ marginInlineEnd: 3, verticalAlign: 'middle' }}/>{counts.warning} {t('appetiteSevWarning')}</Badge>}
        </div>
      }>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
        {t('appetiteBreachFeedSub')}
      </div>

      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {sorted.map(({ statement, severity, workflow }) => (
          <div key={statement.id} style={{
            padding: '12px 0',
            borderBottom: '1px dashed var(--border)',
            display: 'grid',
            gridTemplateColumns: '1fr auto',
            gap: 12,
            alignItems: 'flex-start',
          }}>
            <div style={{ minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                  {lang === 'ar' && statement.titleAr ? statement.titleAr : statement.titleEn}
                </span>
                <Badge tone={APPETITE_SEVERITY_TONE[severity]}>
                  {appetiteSeverityLabel(severity, lang)}
                </Badge>
                <Badge tone="neutral">{statement.appetiteLevel}</Badge>
              </div>
              <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4, lineHeight: 1.5 }}>
                {workflow.reason}
              </div>
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6, marginTop: 6, fontSize: 11, color: 'var(--ink-3)' }}>
                <Landmark size={11} />
                <span>{getCitation(statement.id)}</span>
              </div>
              <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
                {t('appetiteOwnerLabel')} {statement.approvedBy ?? '—'} · {t('appetiteNextStepLabel')} {workflow.nextStep}
              </div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 6 }}>
              <SlaCountdown instance={workflow} />
              <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                {workflow.entityRef}
              </span>
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
}

function getCitation(statementId: number): string {
  const s = SAUDI_APPETITES.find(x => x.id === statementId);
  if (!s) return '';
  return `${s.regulators.join(' · ')} — ${s.citation}`;
}

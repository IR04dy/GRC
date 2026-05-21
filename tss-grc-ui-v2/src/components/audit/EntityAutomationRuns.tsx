import { useEffect, useState } from 'react';
import { Zap } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge, type BadgeTone } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';
import { automationApi, type AutomationRuleRunDto } from '../../lib/api';

/**
 * Reusable F8 rule-run card — renders every {@code automation_rule_runs}
 * row for any tenant-scoped entity inline on its detail page.
 *
 * <p>Originally inline in WorkflowSubmissionDetailPage (PR #27); extracted
 * here so AarDetailPage, RiskDetailPage, etc. can drop it in with just an
 * {@code entityType} prop.
 *
 * <p>Backend endpoint:
 *   {@code GET /api/v1/automation/runs/for-entity?entityType=...&entityId=...}
 * Tenant-scoped, newest first, returns top 50.
 *
 * <p>Pairs with {@link EntityActivityTimeline} — render the two cards
 * side-by-side (timeline first, automation second) and the detail page
 * reads top-down as "who did what" + "what fired automatically".
 *
 * <p>Hides itself on empty / fetch error so an entity that hasn't
 * triggered any F8 rule doesn't show a phantom card.
 */
export interface EntityAutomationRunsProps {
  entityType: string;
  entityId: number;
  titleEn?: string;
  titleAr?: string;
}

const OUTCOME_TONE: Record<string, BadgeTone> = {
  SUCCESS: 'success',
  SKIPPED: 'neutral',
  FAILED:  'danger',
};

export function EntityAutomationRuns({
  entityType, entityId, titleEn, titleAr,
}: EntityAutomationRunsProps) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  const [runs, setRuns] = useState<AutomationRuleRunDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const rows = await automationApi.runsForEntity(entityType, entityId);
        if (!cancelled) setRuns(rows);
      } catch (e) {
        if (!cancelled) setError((e as Error).message);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, [entityType, entityId]);

  if (error || runs === null) return null;
  if (runs.length === 0) return null;

  return (
    <Card title={isAr ? (titleAr ?? 'إجراءات الأتمتة (F8)')
                      : (titleEn ?? 'Automation actions (F8)')}
          Icon={Zap}
          action={<Badge tone="neutral">{runs.length}</Badge>}>
      <ol style={{ listStyle: 'none', margin: 0, padding: 0,
                   display: 'flex', flexDirection: 'column', gap: 8 }}>
        {runs.map(r => (
          <li key={r.id} style={{
            display: 'grid',
            gridTemplateColumns: '160px 110px 1fr',
            gap: 12,
            padding: '8px 0',
            borderBottom: '1px dashed var(--border)',
            alignItems: 'baseline',
          }}>
            <span style={{ font: '500 12px var(--font-mono, var(--font-sans))',
                           color: 'var(--ink-3)' }}>
              {formatDate(r.firedAt)}
            </span>
            <Badge tone={OUTCOME_TONE[r.outcome] ?? 'neutral'}>{r.outcome}</Badge>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              <span style={{ font: '500 13px var(--font-sans)', color: 'var(--ink-1)' }}>
                {r.resultSummary ?? r.errorMessage ?? r.triggerEvent}
              </span>
              <span style={{ font: '500 11px var(--font-sans)', color: 'var(--ink-3)' }}>
                {r.triggerEvent} · rule #{r.ruleId}
              </span>
            </div>
          </li>
        ))}
      </ol>
    </Card>
  );
}

function formatDate(iso: string): string {
  try { return new Date(iso).toLocaleString(); } catch { return iso; }
}

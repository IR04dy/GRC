import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight, ClipboardCheck } from 'lucide-react';
import { useI18n } from '../../lib/i18n';
import {
  workflowSubmissionApi, type WorkflowSubmissionDto,
} from '../../lib/api';
import { Badge, type BadgeTone } from '../ui/Badge';
import { WORKFLOWS, type WorkflowModule } from '../../lib/workflowCatalog';

/**
 * Module-page recent-submissions widget.
 *
 * <p>Renders the latest five workflow_submissions for a given module bucket,
 * pulled from the V125 ledger via {@link workflowSubmissionApi.list}. Pairs
 * with {@code WorkflowQuickLaunch} — the chip strip lets a user create a
 * new submission; this panel surfaces what was already submitted so they
 * don't need to detour through {@code /workflows/submissions} to see it.
 *
 * <p>Each row links to the central register filtered by the wizard so a
 * one-click drill-through stays available. The whole panel hides itself
 * when the module has zero submissions to avoid empty noise on pages
 * where the user just hasn't run anything yet.
 */
export interface ModuleSubmissionsPanelProps {
  /** Catalog module bucket (matches `WorkflowModule` from workflowCatalog.ts). */
  module: WorkflowModule | WorkflowModule[];
  /** How many rows to render (default 5). */
  limit?: number;
  /** Optional override label EN — defaults to "Recent submissions". */
  titleEn?: string;
  titleAr?: string;
}

const STATUS_TONE: Record<string, BadgeTone> = {
  DRAFT:        'neutral',
  SUBMITTED:    'info',
  UNDER_REVIEW: 'warning',
  APPROVED:     'success',
  REJECTED:     'danger',
  CLOSED:       'neutral',
};

export function ModuleSubmissionsPanel({
  module, limit = 5, titleEn, titleAr,
}: ModuleSubmissionsPanelProps) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const wanted = Array.isArray(module) ? module : [module];

  const [items, setItems] = useState<WorkflowSubmissionDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        // The API accepts a single module string; fan-out for multi-module
        // pages (e.g. Compliance covers COMPLIANCE + GOVERNANCE) and merge
        // client-side, then sort by submitted_at desc.
        const results = await Promise.all(
          wanted.map(m => workflowSubmissionApi.list(undefined, m)),
        );
        if (cancelled) return;
        const merged = results.flat()
          .sort((a, b) => (b.submittedAt ?? '').localeCompare(a.submittedAt ?? ''))
          .slice(0, limit);
        setItems(merged);
      } catch (e) {
        if (!cancelled) setError((e as Error).message);
      }
    }
    void load();
    return () => { cancelled = true; };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wanted.join(','), limit]);

  // Hide entirely on first-load error or when there's nothing to show —
  // the module page shouldn't render an empty placeholder.
  if (error || (items !== null && items.length === 0)) return null;
  // Defer rendering until data arrives so we don't flash an empty card.
  if (items === null) return null;

  return (
    <div className="wf-submissions-panel" role="region"
         aria-label={isAr ? (titleAr ?? 'آخر السجلات') : (titleEn ?? 'Recent submissions')}>
      <div className="wf-submissions-panel-head">
        <ClipboardCheck size={14} strokeWidth={1.8}/>
        <span>{isAr ? (titleAr ?? 'آخر السجلات') : (titleEn ?? 'Recent submissions')}</span>
        <Link to="/workflows/submissions" className="wf-submissions-panel-more">
          {isAr ? 'عرض الكل' : 'View all'}
          <ChevronRight size={12} strokeWidth={2}/>
        </Link>
      </div>
      <ul className="wf-submissions-panel-list">
        {items.map(s => {
          const cat = WORKFLOWS.find(w => w.id === s.workflowId);
          const wizardLabel = isAr
            ? (cat?.titleAr ?? s.workflowId)
            : (cat?.titleEn ?? s.workflowId);
          return (
            <li key={s.id} className="wf-submissions-panel-row">
              <Link to={`/workflows/submissions?workflowId=${encodeURIComponent(s.workflowId)}`}
                    className="wf-submissions-panel-row-link"
                    title={s.summaryEn ?? s.titleEn}>
                <span className="wf-submissions-panel-id">#{s.id}</span>
                <span className="wf-submissions-panel-title">
                  <span className="wf-submissions-panel-wizard">{wizardLabel}</span>
                  <span className="wf-submissions-panel-headline">{s.titleEn}</span>
                </span>
                <Badge tone={STATUS_TONE[s.status] ?? 'neutral'}>{s.status}</Badge>
                <span className="wf-submissions-panel-date">{formatRelative(s.submittedAt, isAr)}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function formatRelative(iso: string, isAr: boolean): string {
  if (!iso) return '—';
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return iso;
  const diffSec = Math.round((Date.now() - then) / 1000);
  if (diffSec < 60)        return isAr ? 'الآن' : 'just now';
  if (diffSec < 3600)      return isAr ? `قبل ${Math.round(diffSec / 60)} د` : `${Math.round(diffSec / 60)}m ago`;
  if (diffSec < 86400)     return isAr ? `قبل ${Math.round(diffSec / 3600)} س` : `${Math.round(diffSec / 3600)}h ago`;
  if (diffSec < 86400 * 7) return isAr ? `قبل ${Math.round(diffSec / 86400)} ي` : `${Math.round(diffSec / 86400)}d ago`;
  try { return new Date(iso).toLocaleDateString(); } catch { return iso; }
}

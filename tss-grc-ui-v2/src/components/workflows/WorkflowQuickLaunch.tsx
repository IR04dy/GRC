import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight, Workflow as WorkflowIcon } from 'lucide-react';
import { useI18n } from '../../lib/i18n';
import { WORKFLOWS, type WorkflowModule } from '../../lib/workflowCatalog';

/**
 * WorkflowQuickLaunch — module-page chip strip that surfaces the wizards
 * that ship `status: 'AVAILABLE'` for a given module from the §7 catalog.
 *
 * <p>Designed to drop into any module page (Privacy, BCM, SCM, Cyber, ERM,
 * Compliance, Incident). Reads the canonical {@link WORKFLOWS} catalog so
 * it auto-updates when a new wizard lands — no per-page edits needed.
 *
 * <p>Renders a compact horizontal scroll strip with one chip per wizard.
 * Each chip is a `<Link>` straight to the wizard route. Optional `extra`
 * modules render appended (e.g. Privacy page can show PDPL + COMPLIANCE +
 * GOVERNANCE in one strip).
 */
export interface WorkflowQuickLaunchProps {
  /** Module(s) whose AVAILABLE wizards should be surfaced. */
  modules: WorkflowModule | WorkflowModule[];
  /** Optional override label EN — defaults to "Start a workflow". */
  titleEn?: string;
  /** Optional override label AR — defaults to "ابدأ معالجاً". */
  titleAr?: string;
  /** When true, hides the strip if there are no AVAILABLE wizards
   *  in the requested modules (caller can skip rendering entirely). */
  hideWhenEmpty?: boolean;
}

export function WorkflowQuickLaunch({
  modules, titleEn, titleAr, hideWhenEmpty,
}: WorkflowQuickLaunchProps) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const wanted = Array.isArray(modules) ? modules : [modules];

  const items = useMemo(() =>
    WORKFLOWS.filter(w => w.status === 'AVAILABLE'
      && w.route
      && wanted.includes(w.module)
    ),
  // eslint-disable-next-line react-hooks/exhaustive-deps
  [wanted.join(',')]);

  if (items.length === 0 && hideWhenEmpty) return null;

  return (
    <div className="wf-quick-launch" role="region"
         aria-label={isAr ? (titleAr ?? 'ابدأ معالجاً') : (titleEn ?? 'Start a workflow')}>
      <div className="wf-quick-launch-head">
        <WorkflowIcon size={14} strokeWidth={1.8} />
        <span>{isAr ? (titleAr ?? 'ابدأ معالجاً') : (titleEn ?? 'Start a workflow')}</span>
      </div>
      <div className="wf-quick-launch-strip">
        {items.length === 0 ? (
          <span className="wf-quick-launch-empty">
            {isAr ? 'لا توجد معالجات متاحة لهذه الوحدة بعد.'
                  : 'No wizards available for this module yet.'}
          </span>
        ) : items.map(w => (
          <Link key={w.id} to={w.route!} className="wf-quick-launch-chip"
                title={isAr ? w.descAr : w.descEn}>
            <w.Icon size={14} strokeWidth={1.8} />
            <span>{isAr ? w.titleAr : w.titleEn}</span>
            {typeof w.steps === 'number' && (
              <span className="wf-quick-launch-steps">
                {w.steps} {isAr ? 'خ' : 'st'}
              </span>
            )}
            <ChevronRight size={12} strokeWidth={2} className="wf-quick-launch-arrow"/>
          </Link>
        ))}
      </div>
    </div>
  );
}

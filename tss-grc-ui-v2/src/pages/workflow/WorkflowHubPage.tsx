import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Workflow, ChevronRight, ClipboardList } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { workflowSubmissionApi } from '../../lib/api';
import {
  WORKFLOWS, MODULE_LABEL, STATUS_LABEL,
  type WorkflowModule, type WorkflowStatus,
} from '../../lib/workflowCatalog';

/**
 * Workflow Hub — one page listing every wizard the platform ships.
 *
 * <p>Built on top of {@link WORKFLOWS} (the catalog).  Users filter by
 * module / status; cards link to the wizard route. PLANNED entries are
 * shown but disabled so prospects can see the roadmap.
 */
const STATUS_TONE: Record<WorkflowStatus, BadgeTone> = {
  AVAILABLE: 'success',
  PARTIAL:   'warning',
  PLANNED:   'neutral',
};

/** Aggregated counts per workflowId, computed once on mount. */
interface PerWizardCounts {
  total: number;
  open: number;       // SUBMITTED + UNDER_REVIEW
  approved: number;
}

export function WorkflowHubPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const navigate = useNavigate();
  const [moduleFilter, setModuleFilter] = useState<WorkflowModule | 'ALL'>('ALL');
  const [statusFilter, setStatusFilter] = useState<WorkflowStatus | 'ALL'>('ALL');
  const [query, setQuery] = useState('');

  // Per-wizard submission counts via a single tenant-scoped aggregate
  // query — the engine groups by workflowId in SQL so the Hub no longer
  // pays an O(rows) cost on every visit. The previous client-side
  // aggregation pulled every submission row even though the page only
  // renders one badge per wizard.
  const [submissionCounts, setSubmissionCounts] = useState<Record<string, PerWizardCounts>>({});
  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const rows = await workflowSubmissionApi.countsByWorkflow();
        if (cancelled) return;
        const acc: Record<string, PerWizardCounts> = {};
        rows.forEach(r => {
          acc[r.workflowId] = { total: r.total, open: r.open, approved: r.approved };
        });
        setSubmissionCounts(acc);
      } catch { /* keep counts empty — falls back to "—" badge */ }
    }
    void load();
    return () => { cancelled = true; };
  }, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return WORKFLOWS.filter(w => {
      if (moduleFilter !== 'ALL' && w.module !== moduleFilter) return false;
      if (statusFilter !== 'ALL' && w.status !== statusFilter) return false;
      if (q) {
        const hay = `${w.titleEn} ${w.titleAr} ${w.descEn} ${w.id} ${w.anchor ?? ''}`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });
  }, [moduleFilter, statusFilter, query]);

  const byModule = useMemo(() => {
    const map = new Map<WorkflowModule, typeof WORKFLOWS>();
    for (const w of filtered) {
      const arr = map.get(w.module) ?? [];
      arr.push(w);
      map.set(w.module, arr);
    }
    return Array.from(map.entries());
  }, [filtered]);

  const counts = useMemo(() => ({
    total: WORKFLOWS.length,
    available: WORKFLOWS.filter(w => w.status === 'AVAILABLE').length,
    partial:   WORKFLOWS.filter(w => w.status === 'PARTIAL').length,
    planned:   WORKFLOWS.filter(w => w.status === 'PLANNED').length,
  }), []);

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'مركز المهام والمعالجات' : 'Workflow Hub'}
        sub={isAr
          ? `${counts.available} معالج جاهز للاستخدام من إجمالي ${counts.total} عملية`
          : `${counts.available} of ${counts.total} workflows shipped — ${counts.partial} partial, ${counts.planned} on backlog`}
        eyebrow={isAr ? 'كتالوج المعالجات' : 'Wizards catalog'}
        actions={
          <Link to="/workflows/submissions">
            <Button variant="secondary" size="sm" Icon={ClipboardList}>
              {isAr ? 'سجلات المعالجات' : 'Submissions register'}
            </Button>
          </Link>
        }
      />

      {/* Filters */}
      <div className="wf-hub-filters">
        <input
          type="search"
          className="input"
          placeholder={isAr ? 'بحث…' : 'Search workflows…'}
          value={query}
          onChange={e => setQuery(e.target.value)}
          style={{ minWidth: 220 }}
        />

        <select className="input" value={moduleFilter}
                onChange={e => setModuleFilter(e.target.value as WorkflowModule | 'ALL')}>
          <option value="ALL">{isAr ? 'كل الوحدات' : 'All modules'}</option>
          {(Object.keys(MODULE_LABEL) as WorkflowModule[]).map(m => (
            <option key={m} value={m}>{isAr ? MODULE_LABEL[m].ar : MODULE_LABEL[m].en}</option>
          ))}
        </select>

        <select className="input" value={statusFilter}
                onChange={e => setStatusFilter(e.target.value as WorkflowStatus | 'ALL')}>
          <option value="ALL">{isAr ? 'كل الحالات' : 'All statuses'}</option>
          {(Object.keys(STATUS_LABEL) as WorkflowStatus[]).map(s => (
            <option key={s} value={s}>{isAr ? STATUS_LABEL[s].ar : STATUS_LABEL[s].en}</option>
          ))}
        </select>
      </div>

      {/* Per-module sections */}
      {byModule.length === 0 ? (
        <Card title={isAr ? 'لا نتائج' : 'No matching workflows'}>
          <p style={{ color: 'var(--ink-3)', padding: 8 }}>
            {isAr ? 'حاول تعديل المرشحات.' : 'Try adjusting the filters.'}
          </p>
        </Card>
      ) : byModule.map(([mod, items]) => (
        <Card key={mod}
              title={`${isAr ? MODULE_LABEL[mod].ar : MODULE_LABEL[mod].en} · ${items.length}`}
              Icon={Workflow}>
          <div className="wf-hub-grid">
            {items.map(w => {
              const disabled = w.status === 'PLANNED' || !w.route;
              const card = (
                <div className={'wf-hub-card ' + (disabled ? 'is-disabled' : '')}>
                  <div className="wf-hub-card-head">
                    <w.Icon size={22} strokeWidth={1.8} />
                    <Badge tone={STATUS_TONE[w.status]}>
                      {isAr ? STATUS_LABEL[w.status].ar : STATUS_LABEL[w.status].en}
                    </Badge>
                  </div>
                  <h3 className="wf-hub-card-title">
                    {isAr ? w.titleAr : w.titleEn}
                  </h3>
                  <p className="wf-hub-card-desc">
                    {isAr ? w.descAr : w.descEn}
                  </p>
                  <div className="wf-hub-card-meta">
                    {typeof w.steps === 'number' && (
                      <Badge tone="info">{w.steps} {isAr ? 'خطوات' : 'steps'}</Badge>
                    )}
                    {w.anchor && <Badge tone="neutral">{w.anchor}</Badge>}
                    {(() => {
                      const c = submissionCounts[w.id];
                      if (!c || c.total === 0) return null;
                      // Click → drill into Submissions register pre-filtered
                      // for this wizard (and to OPEN if any are in-flight,
                      // matching the warning tone). Stop propagation so the
                      // outer card link to the wizard route doesn't also fire.
                      const target = `/workflows/submissions?workflowId=${encodeURIComponent(w.id)}`
                                   + (c.open > 0 ? '&status=OPEN' : '');
                      const drillTitle = isAr
                        ? `عرض ${c.total} سجل في السجل`
                        : `View ${c.total} submission${c.total === 1 ? '' : 's'} in the register`;
                      return (
                        <span
                          role="link"
                          tabIndex={0}
                          title={drillTitle}
                          onClick={(e) => { e.stopPropagation(); e.preventDefault(); navigate(target); }}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter' || e.key === ' ') {
                              e.stopPropagation(); e.preventDefault(); navigate(target);
                            }
                          }}
                          style={{ cursor: 'pointer' }}
                        >
                          <Badge tone={c.open > 0 ? 'warning' : 'success'}>
                            {c.total} {isAr ? 'سجل' : 'submitted'}
                            {c.open > 0 ? ` · ${c.open} ${isAr ? 'مفتوحة' : 'open'}` : ''}
                          </Badge>
                        </span>
                      );
                    })()}
                  </div>
                  {!disabled && (
                    <div className="wf-hub-card-cta">
                      <Button variant="primary" size="sm">
                        {isAr ? 'بدء' : 'Start'}
                        <ChevronRight size={14} strokeWidth={2} />
                      </Button>
                    </div>
                  )}
                </div>
              );
              return disabled ? (
                <div key={w.id} className="wf-hub-card-wrap" aria-disabled="true">{card}</div>
              ) : (
                <Link key={w.id} to={w.route!} className="wf-hub-card-wrap">{card}</Link>
              );
            })}
          </div>
        </Card>
      ))}
    </Shell>
  );
}

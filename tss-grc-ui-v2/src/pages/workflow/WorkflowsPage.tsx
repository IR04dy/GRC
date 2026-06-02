import { useEffect, useState } from 'react';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Workflow, ChevronRight, ArrowRight, Hourglass, Filter, Download } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { workflowApi, type WorkflowDefinitionDto, type WorkflowDefinitionGraph } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import { WORKFLOW_DEFINITIONS, WORKFLOW_GRAPHS_BY_ID } from './mockWorkflowDefs';
import { downloadCsv, stampFilename, type ExportRow } from '../../lib/clientExport';
import { FilterModal, distinctValues, type FilterField } from '../../components/ui/FilterModal';
import { LiveSlaPulse } from '../../components/workflows/LiveSlaPulse';
import { ALL_WORKFLOWS } from '../../lib/mockWorkflows';
import { ChainCatalog } from '../../components/workflows/ChainCatalog';
import { WorkflowDesigner } from '../../components/workflows/WorkflowDesigner';
import { Pencil, Eye } from 'lucide-react';

/**
 * Workflows admin page — assessment §6.1 strategic flagship.
 * Lists every active workflow definition for this organisation and lets the
 * user drill into the state machine. The MVP is read-only; admin authoring
 * (drag-and-drop builder) is a follow-up tracked in
 * docs/diagrams/19-workflow-engine-roadmap.md.
 */
export function WorkflowsPage() {
  const { t } = useI18n();
  const toast = useToast();
  const [definitions, setDefinitions] = useState<WorkflowDefinitionDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [graph, setGraph] = useState<WorkflowDefinitionGraph | null>(null);
  const [graphLoading, setGraphLoading] = useState(false);
  const [designerMode, setDesignerMode] = useState(false);

  useEffect(() => {
    if (!isAuthenticated()) {
      setDefinitions(WORKFLOW_DEFINITIONS);
      if (activeId == null) setActiveId(WORKFLOW_DEFINITIONS[0]?.id ?? null);
      return;
    }
    setLoading(true);
    workflowApi.definitions()
      .then(d => {
        const list = d && d.length > 0 ? d : WORKFLOW_DEFINITIONS;
        setDefinitions(list);
        if (list.length > 0 && activeId == null) setActiveId(list[0].id);
      })
      .catch(() => {
        setDefinitions(WORKFLOW_DEFINITIONS);
        if (activeId == null) setActiveId(WORKFLOW_DEFINITIONS[0]?.id ?? null);
      })
      .finally(() => setLoading(false));
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (activeId == null) return;
    if (!isAuthenticated()) {
      setGraph(WORKFLOW_GRAPHS_BY_ID[activeId] ?? null);
      return;
    }
    setGraphLoading(true);
    workflowApi.graph(activeId)
      .then(g => setGraph(g ?? WORKFLOW_GRAPHS_BY_ID[activeId] ?? null))
      .catch(() => setGraph(WORKFLOW_GRAPHS_BY_ID[activeId] ?? null))
      .finally(() => setGraphLoading(false));
  }, [activeId]);

  const isDemo = !isAuthenticated();

  // ── DD-48: filter modal — workflow definitions ────────────────────
  const [filterOpen, setFilterOpen] = useState(false);
  const [advFilters, setAdvFilters] = useState<Record<string, string | undefined>>({});
  const filteredDefinitions = definitions.filter(d => {
    if (advFilters.targetModule && d.targetModule !== advFilters.targetModule) return false;
    if (advFilters.targetEntity && d.targetEntity !== advFilters.targetEntity) return false;
    if (advFilters.isActive && String(d.isActive) !== advFilters.isActive) return false;
    return true;
  });
  const wfFilterFields: FilterField[] = [
    { key: 'targetModule', label: 'Module', kind: 'select', options: distinctValues(definitions, 'targetModule') },
    { key: 'targetEntity', label: 'Entity', kind: 'select', options: distinctValues(definitions, 'targetEntity') },
    { key: 'isActive',     label: 'Status', kind: 'select', options: [{value:'true',label:'Active'},{value:'false',label:'Inactive'}] },
  ];
  const stateColour = (color: string | undefined) => {
    switch (color) {
      case 'success': return { bg: 'var(--success-bg)', fg: 'var(--success-fg)', border: 'var(--success-fg)' };
      case 'danger':  return { bg: 'var(--danger-bg)',  fg: 'var(--danger-fg)',  border: 'var(--danger-fg)' };
      case 'warning': return { bg: 'var(--accent-50)',  fg: 'var(--accent-700)', border: 'var(--accent-700)' };
      case 'accent':  return { bg: 'var(--accent-50)',  fg: 'var(--accent-700)', border: 'var(--accent-700)' };
      case 'primary': return { bg: 'var(--primary-50)', fg: 'var(--primary-700)', border: 'var(--primary-700)' };
      case 'info':    return { bg: 'var(--surface-2)',  fg: 'var(--ink-1)',      border: 'var(--ink-3)' };
      default:        return { bg: 'var(--surface-2)',  fg: 'var(--ink-1)',      border: 'var(--ink-3)' };
    }
  };

  return (
    <Shell title={t('modWorkflow') || 'Workflows'} sub="ISO 19011 · Configurable State Machines">
      <PageHeader
        eyebrow="Configurable Workflow Engine"
        title="Workflow Definitions"
        sub="State-machine workflows defined in data, not code. Once a module is migrated onto this engine, every transition becomes a configuration tweak."
        actions={
          <>
            {isDemo && <Badge tone="warning">Demo</Badge>}
            {graph && (
              <Button variant={designerMode ? 'primary' : 'secondary'}
                Icon={designerMode ? Eye : Pencil}
                onClick={() => setDesignerMode(m => !m)}>
                {designerMode ? t('designerExitMode') : t('designerEnterMode')}
              </Button>
            )}
            <Button variant="secondary" Icon={Filter} onClick={() => setFilterOpen(true)}>
              {t('actionFilter')}{Object.values(advFilters).filter(Boolean).length > 0 ? ` · ${Object.values(advFilters).filter(Boolean).length}` : ''}
            </Button>
            <Button variant="secondary" Icon={Download} onClick={() => {
              downloadCsv(filteredDefinitions as unknown as ExportRow[], stampFilename('workflow-definitions', 'csv'));
              toast.success(t('exportCsvDone'));
            }}>{t('actionExport')}</Button>
          </>
        }
      />

      {/* SLA runtime — Sprint 4. Re-derives every minute from real dueDate
          across all modules. Saudi business calendar, Hijri tooltips. */}
      <div style={{ marginBottom: 16 }}>
        <LiveSlaPulse instances={ALL_WORKFLOWS} />
      </div>

      {/* Cross-module chain orchestration — Sprint 5. Declarative rules
          fire downstream workflows / approvals / re-tests when a source
          event happens (approval, breach, finding closure, etc.). */}
      <div style={{ marginBottom: 16 }}>
        <ChainCatalog />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '300px 1fr', gap: 16 }}>
        {/* Definition list */}
        <Card padded={false}>
          <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--border)', fontSize: 12, fontWeight: 700, color: 'var(--ink-2)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            Definitions
          </div>
          {loading
            ? <InlineEmpty>Loading…</InlineEmpty>
            : definitions.length === 0
              ? <EmptyState Icon={Workflow} title="No workflows defined" sub="Reference workflows are seeded by the V_workflow_engine_ddl.sql migration." />
              : filteredDefinitions.map(d => (
                <button key={d.id}
                  onClick={() => setActiveId(d.id)}
                  style={{
                    width: '100%', textAlign: 'left',
                    padding: '12px 14px', display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                    background: activeId === d.id ? 'var(--primary-50)' : 'transparent',
                    borderBottom: '1px solid var(--border)', border: 'none', cursor: 'pointer',
                  }}>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 600, fontSize: 13, color: 'var(--ink-1)' }}>{d.nameEn}</div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }} className="mono">
                      {d.targetModule} · {d.code} · v{d.version}
                    </div>
                  </div>
                  <ChevronRight size={14} color="var(--ink-3)" />
                </button>
              ))}
        </Card>

        {/* Graph view OR Designer (Sprint 5 visual editor) */}
        {graph && designerMode
          ? <WorkflowDesigner graph={graph} onClose={() => setDesignerMode(false)} />
          : <Card padded>
          {graphLoading
            ? <InlineEmpty>Loading state machine…</InlineEmpty>
            : !graph
              ? <EmptyState Icon={Workflow} title="Pick a workflow to inspect" sub="The graph view shows every state and the transitions between them." />
              : (
                <>
                  <div style={{ marginBottom: 16 }}>
                    <div style={{ fontWeight: 700, fontSize: 16 }}>{graph.definition.nameEn}</div>
                    {graph.definition.nameAr && <div style={{ fontSize: 12, color: 'var(--ink-3)' }} dir="rtl">{graph.definition.nameAr}</div>}
                    {graph.definition.descriptionEn && <div style={{ fontSize: 12, color: 'var(--ink-2)', marginTop: 6, maxWidth: 720 }}>{graph.definition.descriptionEn}</div>}
                    <div style={{ marginTop: 8, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      <Badge tone="primary">Module: {graph.definition.targetModule}</Badge>
                      <Badge tone="info">Entity: {graph.definition.targetEntity}</Badge>
                      <Badge tone="neutral">v{graph.definition.version}</Badge>
                      <Badge tone="success">{graph.states.length} states</Badge>
                      <Badge tone="success">{graph.transitions.length} transitions</Badge>
                    </div>
                  </div>

                  {/* States row */}
                  <div style={{ marginBottom: 20 }}>
                    <div style={{ fontWeight: 700, fontSize: 12, marginBottom: 8, color: 'var(--ink-2)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                      States
                    </div>
                    <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                      {graph.states.map(s => {
                        const c = stateColour(s.uiColor);
                        return (
                          <div key={s.id} style={{
                            border: `1.5px solid ${c.border}`, borderRadius: 'var(--r-md)',
                            padding: '10px 14px', minWidth: 160, background: c.bg,
                          }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 6 }}>
                              <span className="mono" style={{ fontWeight: 700, fontSize: 12, color: c.fg }}>{s.code}</span>
                              {s.isInitial && <Badge tone="success">start</Badge>}
                              {s.isTerminal && <Badge tone="neutral">end</Badge>}
                            </div>
                            <div style={{ fontSize: 13, color: c.fg, marginTop: 2, fontWeight: 600 }}>{s.nameEn}</div>
                            {s.descriptionEn && <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 4 }}>{s.descriptionEn.slice(0, 80)}</div>}
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  {/* Transitions table */}
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 12, marginBottom: 8, color: 'var(--ink-2)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                      Transitions
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      {graph.transitions.map(tr => (
                        <div key={tr.id} style={{
                          display: 'grid', gridTemplateColumns: '160px 24px 160px 1fr auto', gap: 10,
                          padding: '8px 12px', borderRadius: 'var(--r-md)', alignItems: 'center',
                          background: 'var(--surface-2)', border: '1px solid var(--border)',
                        }}>
                          <span className="mono" style={{ fontSize: 12, fontWeight: 600 }}>{tr.fromStateCode}</span>
                          <ArrowRight size={14} color="var(--ink-3)" />
                          <span className="mono" style={{ fontSize: 12, fontWeight: 600 }}>{tr.toStateCode}</span>
                          <div>
                            <div style={{ fontWeight: 600, fontSize: 13 }}>{tr.actionNameEn}</div>
                            {tr.actionNameAr && <div style={{ fontSize: 11, color: 'var(--ink-3)' }} dir="rtl">{tr.actionNameAr}</div>}
                            <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{tr.actionCode}</span>
                          </div>
                          <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                            {tr.requiredRole && <Badge tone="primary">{tr.requiredRole}</Badge>}
                            {tr.slaHours != null && (
                              <Badge tone="warning">
                                <Hourglass size={10} style={{ marginRight: 3, verticalAlign: 'middle' }} />
                                {tr.slaHours}h SLA
                              </Badge>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </>
              )}
        </Card>}
      </div>
      <FilterModal open={filterOpen} fields={wfFilterFields} value={advFilters}
        onApply={v => { setAdvFilters(v); toast.success(t('filterModalApplied')); }}
        onClose={() => setFilterOpen(false)} />
    </Shell>
  );
}

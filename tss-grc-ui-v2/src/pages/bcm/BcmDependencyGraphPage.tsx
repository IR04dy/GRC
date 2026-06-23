import { useEffect, useMemo, useState } from 'react';
import { Network, AlertTriangle, Eye, Filter } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  fetchDependencyGraph, COLUMN_ORDER, NODE_FILL,
  type GraphPayload, type GraphNode, type GraphEdge, type NodeType,
} from '../../lib/bcmDependencyGraph';

/**
 * BCM Dependency Graph viz — audit Tier-2.
 *
 * <p>Replaces the legacy mock-data tree at {@code /bcm-dependency}. Two tabs:
 * <ul>
 *   <li><b>Graph</b> — SVG layered-column layout. Columns left → right are
 *       BIA · SERVICE · PROCESS · BCP · DRP · STRATEGY · CLOUD · SUPPLIER.
 *       Nodes within a column stack vertically. Edges are drawn as curves
 *       between connected nodes. Hover for label; click for detail drawer.</li>
 *   <li><b>Adjacency table</b> — every edge as a row (source · relationship
 *       · target), filterable by node type — auditor-friendly tabular view.</li>
 * </ul>
 *
 * <p>Pure SVG (no react-flow dep). Sufficient for the audit's "show this
 * during a demo" use case; production polish (zoom/pan, force layout) is a
 * future enhancement once the dataset grows past ~50 nodes.
 */
const NODE_WIDTH = 180;
const NODE_HEIGHT = 36;
const COL_GAP = 80;
const ROW_GAP = 14;
const COL_PAD = 24;
const TOP_PAD = 20;

export function BcmDependencyGraphPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [graph, setGraph] = useState<GraphPayload | null>(null);
  const [loading, setLoading] = useState(false);
  const [tab, setTab] = useState<'graph' | 'table'>('graph');
  const [selected, setSelected] = useState<GraphNode | null>(null);
  const [filterType, setFilterType] = useState<NodeType | ''>('');

  useEffect(() => {
    (async () => {
      setLoading(true);
      try {
        setGraph(await fetchDependencyGraph());
      } catch (e) {
        toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل تحميل الرسم' : 'Failed to load graph'));
      } finally {
        setLoading(false);
      }
    })();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Layout calculation: assign each node an (x, y) by column + position ──
  const layout = useMemo(() => {
    if (!graph) return { positions: new Map<string, {x:number;y:number}>(), width: 800, height: 400 };
    const byColumn: Record<string, GraphNode[]> = {};
    for (const t of COLUMN_ORDER) byColumn[t] = [];
    for (const n of graph.nodes) {
      if (byColumn[n.type]) byColumn[n.type].push(n);
    }
    // Drop empty columns from the rendered width but keep ordering
    const nonEmptyColumns = COLUMN_ORDER.filter(t => byColumn[t].length > 0);
    const positions = new Map<string, { x: number; y: number }>();
    let maxRows = 1;
    nonEmptyColumns.forEach((t, colIdx) => {
      const nodes = byColumn[t];
      maxRows = Math.max(maxRows, nodes.length);
      nodes.forEach((n, rowIdx) => {
        positions.set(n.id, {
          x: COL_PAD + colIdx * (NODE_WIDTH + COL_GAP),
          y: TOP_PAD + rowIdx * (NODE_HEIGHT + ROW_GAP),
        });
      });
    });
    const width = COL_PAD * 2 + nonEmptyColumns.length * NODE_WIDTH
      + Math.max(0, nonEmptyColumns.length - 1) * COL_GAP;
    const height = TOP_PAD * 2 + maxRows * (NODE_HEIGHT + ROW_GAP);
    return { positions, width, height, columns: nonEmptyColumns };
  }, [graph]);

  // ── Filtered edges (for adjacency table) ──
  const filteredEdges = useMemo(() => {
    if (!graph) return [];
    if (!filterType) return graph.edges;
    const nodeIdsOfType = new Set(graph.nodes.filter(n => n.type === filterType).map(n => n.id));
    return graph.edges.filter(e => nodeIdsOfType.has(e.source) || nodeIdsOfType.has(e.target));
  }, [graph, filterType]);

  const tabs: TabItem<'graph' | 'table'>[] = [
    { id: 'graph', label: isAr ? 'الرسم' : 'Graph',         Icon: Network },
    { id: 'table', label: isAr ? 'جدول الروابط' : 'Edges',  Icon: Filter },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'خريطة التبعيات' : 'BCM dependency graph'}
        sub={isAr
          ? 'تحليلات الأثر → الخدمات والعمليات → خطط BCP/DRP → الاستراتيجيات → الخدمات السحابية.'
          : 'BIAs → services/processes → BCP/DRP plans → strategies → cloud services.'}
        eyebrow={isAr ? 'استمرارية الأعمال' : 'Business Continuity'}
      />

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(160px,1fr))', gap: 12, marginBottom: 12 }}>
        <Kpi Icon={Network}       label={isAr ? 'العقد'        : 'Nodes'}                value={graph?.summary.nodeCount ?? 0}              tone="info" to="/bcm" />
        <Kpi Icon={Network}       label={isAr ? 'الروابط'      : 'Edges'}                value={graph?.summary.edgeCount ?? 0}              tone="info" onClick={() => setTab('table')} active={tab === 'table'} />
        <Kpi Icon={Eye}           label={isAr ? 'حرجة/سيادية'  : 'Critical / Sovereign'} value={graph?.summary.criticalServicesCount ?? 0}  tone="warning" to="/bcm/bia" />
        <Kpi Icon={AlertTriangle} label={isAr ? 'عدم تطابق RTO' : 'RTO mismatches'}       value={graph?.summary.rtoMismatchesCount ?? 0}     tone="danger" to="/bcm/bia" />
      </div>

      {!loading && graph && graph.nodes.length === 0 ? (
        <Card title={isAr ? 'لا توجد بيانات' : 'No data'}>
          <InlineEmpty text={isAr
            ? 'لا توجد بيانات لرسم الخريطة بعد. أنشئ تحليل أثر، خطط BCP/DRP، أو استراتيجيات لتعبئة الرسم.'
            : 'No data to draw the graph yet. Create BIAs, BCP/DRP plans, or strategies to populate it.'} />
        </Card>
      ) : (
        <>
          <div style={{ display: 'flex', gap: 8, marginBottom: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <Legend />
          </div>
          <Tabs items={tabs} active={tab} onChange={setTab} />

          <div style={{ marginTop: 12, display: 'grid',
                        gridTemplateColumns: selected ? '1fr 280px' : '1fr', gap: 12 }}>
            <Card title={tab === 'graph'
              ? (isAr ? 'الرسم' : 'Graph')
              : (isAr ? 'جدول الروابط' : 'Edges')}>
              {loading && <div className="text-muted" style={{ fontSize: 11 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
              {graph && tab === 'graph' && (
                <GraphSvg graph={graph} layout={layout} onNodeClick={setSelected} selectedId={selected?.id} />
              )}
              {graph && tab === 'table' && (
                <AdjacencyTable edges={filteredEdges} nodes={graph.nodes}
                                filterType={filterType} onFilterChange={setFilterType} isAr={isAr} />
              )}
            </Card>

            {selected && <NodeDrawer node={selected} edges={graph?.edges ?? []} nodes={graph?.nodes ?? []}
                                     onClose={() => setSelected(null)} isAr={isAr} />}
          </div>
        </>
      )}
    </Shell>
  );
}

// ─── SVG graph viz ────────────────────────────────────────────

function GraphSvg({ graph, layout, onNodeClick, selectedId }: {
  graph: GraphPayload;
  layout: { positions: Map<string, { x: number; y: number }>; width: number; height: number; columns?: NodeType[] };
  onNodeClick: (n: GraphNode) => void;
  selectedId?: string;
}) {
  return (
    <div style={{ overflow: 'auto', maxHeight: 600, border: '1px solid var(--border)', borderRadius: 4, background: 'var(--surface)' }}>
      <svg width={layout.width} height={layout.height} style={{ display: 'block' }}>
        {/* Edges first so they sit under nodes */}
        {graph.edges.map(e => {
          const s = layout.positions.get(e.source);
          const t = layout.positions.get(e.target);
          if (!s || !t) return null;
          const x1 = s.x + NODE_WIDTH, y1 = s.y + NODE_HEIGHT / 2;
          const x2 = t.x,               y2 = t.y + NODE_HEIGHT / 2;
          // Bezier curve with horizontal control points
          const cx = (x1 + x2) / 2;
          const path = `M ${x1} ${y1} C ${cx} ${y1}, ${cx} ${y2}, ${x2} ${y2}`;
          return (
            <g key={e.id}>
              <path d={path} stroke="#9aa6b2" strokeWidth="1.2" fill="none" />
              <title>{e.relationship}</title>
            </g>
          );
        })}
        {/* Nodes */}
        {graph.nodes.map(n => {
          const p = layout.positions.get(n.id);
          if (!p) return null;
          const fill = NODE_FILL[n.type];
          const mismatch = n.data?.rto_mismatch_vs_bia === true;
          return (
            <g key={n.id} transform={`translate(${p.x}, ${p.y})`} style={{ cursor: 'pointer' }}
               onClick={() => onNodeClick(n)}>
              <rect width={NODE_WIDTH} height={NODE_HEIGHT} rx="4" ry="4"
                    fill={fill} stroke={selectedId === n.id ? '#1a1a1a' : (mismatch ? '#c8362c' : 'transparent')}
                    strokeWidth={selectedId === n.id ? 2 : mismatch ? 2 : 0} opacity="0.9" />
              <text x={10} y={14} fill="#fff" fontSize="9" fontWeight="600">{n.type}</text>
              <text x={10} y={28} fill="#fff" fontSize="11" textLength={NODE_WIDTH - 20} lengthAdjust="spacingAndGlyphs">
                {n.label.length > 26 ? n.label.slice(0, 25) + '…' : n.label}
              </text>
              {mismatch && <text x={NODE_WIDTH - 14} y={14} fill="#fff" fontSize="11">⚠</text>}
              <title>{n.type}: {n.label}</title>
            </g>
          );
        })}
      </svg>
    </div>
  );
}

// ─── Adjacency table ──────────────────────────────────────────

function AdjacencyTable({ edges, nodes, filterType, onFilterChange, isAr }: {
  edges: GraphEdge[]; nodes: GraphNode[];
  filterType: NodeType | ''; onFilterChange: (t: NodeType | '') => void; isAr: boolean;
}) {
  const nodeMap = useMemo(() => new Map(nodes.map(n => [n.id, n])), [nodes]);
  return (
    <>
      <div style={{ marginBottom: 8, display: 'flex', gap: 8, alignItems: 'center' }}>
        <span style={{ fontSize: 12 }}>{isAr ? 'تصفية حسب النوع' : 'Filter by type'}:</span>
        <select value={filterType} onChange={e => onFilterChange(e.target.value as NodeType | '')}
                style={{ padding: '4px 8px', borderRadius: 4, border: '1px solid var(--border)', fontSize: 12 }}>
          <option value="">{isAr ? 'الكل' : 'All'}</option>
          {COLUMN_ORDER.map(t => <option key={t} value={t}>{t}</option>)}
        </select>
        <span className="text-muted" style={{ fontSize: 11 }}>{edges.length} {isAr ? 'رابط' : 'edges'}</span>
      </div>
      {edges.length === 0 ? <InlineEmpty text={isAr ? 'لا توجد روابط' : 'No edges'} /> : (
        <div style={{ overflowX: 'auto', maxHeight: 600 }}>
          <table className="data-table" style={{ width: '100%', fontSize: 12 }}>
            <thead><tr>
              <th style={{ textAlign: 'start' }}>{isAr ? 'المصدر' : 'Source'}</th>
              <th style={{ textAlign: 'start' }}>{isAr ? 'العلاقة' : 'Relationship'}</th>
              <th style={{ textAlign: 'start' }}>{isAr ? 'الهدف' : 'Target'}</th>
            </tr></thead>
            <tbody>
              {edges.map(e => {
                const s = nodeMap.get(e.source); const t = nodeMap.get(e.target);
                return (
                  <tr key={e.id}>
                    <td>{s ? <><Badge tone="neutral">{s.type}</Badge> <span className="mono">{s.label}</span></> : e.source}</td>
                    <td className="mono">{e.relationship}</td>
                    <td>{t ? <><Badge tone="neutral">{t.type}</Badge> <span className="mono">{t.label}</span></> : e.target}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

// ─── Side drawer ─────────────────────────────────────────────

function NodeDrawer({ node, edges, nodes, onClose, isAr }: {
  node: GraphNode; edges: GraphEdge[]; nodes: GraphNode[]; onClose: () => void; isAr: boolean;
}) {
  const nodeMap = useMemo(() => new Map(nodes.map(n => [n.id, n])), [nodes]);
  const outgoing = edges.filter(e => e.source === node.id);
  const incoming = edges.filter(e => e.target === node.id);
  return (
    <Card title={node.label} action={<Button variant="ghost" onClick={onClose}>×</Button>}>
      <Badge tone="info">{node.type}</Badge>
      <div className="text-muted mono" style={{ fontSize: 10, marginTop: 6 }}>{node.id}</div>

      <div style={{ marginTop: 10, fontSize: 12 }}>
        <div style={{ fontWeight: 600 }}>{isAr ? 'البيانات' : 'Data'}</div>
        <div style={{ marginTop: 4 }}>
          {Object.entries(node.data).map(([k, v]) => (
            <div key={k} style={{ display: 'flex', justifyContent: 'space-between', padding: '2px 0' }}>
              <span className="text-muted">{k}</span>
              <span className="mono">{String(v)}</span>
            </div>
          ))}
        </div>
      </div>

      {incoming.length > 0 && (
        <div style={{ marginTop: 10, fontSize: 12 }}>
          <div style={{ fontWeight: 600 }}>{isAr ? 'وارد' : 'Incoming'} ({incoming.length})</div>
          {incoming.slice(0, 6).map(e => (
            <div key={e.id} style={{ fontSize: 11, padding: '2px 0' }}>
              ← <span className="mono">{e.relationship}</span> {nodeMap.get(e.source)?.label ?? e.source}
            </div>
          ))}
        </div>
      )}
      {outgoing.length > 0 && (
        <div style={{ marginTop: 10, fontSize: 12 }}>
          <div style={{ fontWeight: 600 }}>{isAr ? 'صادر' : 'Outgoing'} ({outgoing.length})</div>
          {outgoing.slice(0, 6).map(e => (
            <div key={e.id} style={{ fontSize: 11, padding: '2px 0' }}>
              → <span className="mono">{e.relationship}</span> {nodeMap.get(e.target)?.label ?? e.target}
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}

// ─── Legend ──────────────────────────────────────────────────

function Legend() {
  return (
    <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap', fontSize: 11 }}>
      <span className="text-muted">Legend:</span>
      {COLUMN_ORDER.map(t => (
        <span key={t} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
          <span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: 2, background: NODE_FILL[t] }} />
          {t}
        </span>
      ))}
    </div>
  );
}

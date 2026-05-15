import { useEffect, useMemo, useRef, useState } from 'react';
import { RefreshCw, Layers, RotateCcw, GripVertical, X as XIcon, Plus, Maximize2, Minimize2, FileText, Presentation, ChevronDown, ChevronRight } from 'lucide-react';
import { useI18n } from '../lib/i18n';
import { Shell } from '../components/layout/Shell';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { ExportMenu } from '../components/ui/ExportMenu';
import { LivePulse } from '../components/ui/LivePulse';
import { type HeatCell } from '../components/viz/HeatMap';
import { dashboardApi, reportingApi, ApiError, type DashboardStats } from '../lib/api';
import { isAuthenticated } from '../lib/auth';
import { useToast } from '../lib/toast';
import { useLiveRefresh } from '../lib/useLiveRefresh';
import { WIDGETS, WIDGETS_BY_ID, type WidgetSize } from './dashboard/widgets';
import { useDashboardLayout } from './dashboard/useDashboardLayout';
import { ExecutiveDashboard } from './dashboard/ExecutiveDashboard';

// All entity-change topics broadcast by lifecycle services that move values
// inside the dashboard's stats response. Subscribing to all of them keeps
// the KPIs and charts current. New lifecycle services should append here.
const DASHBOARD_LIVE_TOPICS = [
  'changed.risk',
  'changed.treatment_plan',
  'changed.incident',
  'changed.vulnerability',
  'changed.audit_finding',
  'changed.dsr_case',
  'changed.bcm_invocation',
  'changed.compliance_assessment',
];

const FALLBACK_STATS: DashboardStats = {
  totalRisks: 42, criticalRisks: 7, highRisks: 12, mediumRisks: 18, lowRisks: 5,
  openRisks: 28, closedRisks: 14, totalUsers: 47, activeUsers: 42,
  openCriticalRisks: 7, openHighRisks: 12, openIncidents: 3,
  complianceScore: 87, pendingTasks: 4,
};
const FALLBACK_HEAT: HeatCell[] = [
  { l: 5, i: 5, count: 1 }, { l: 4, i: 5, count: 1 }, { l: 5, i: 4, count: 2 },
  { l: 3, i: 4, count: 3 }, { l: 4, i: 3, count: 4 }, { l: 2, i: 3, count: 5 },
  { l: 3, i: 2, count: 6 }, { l: 1, i: 2, count: 4 }, { l: 2, i: 1, count: 7 }, { l: 1, i: 1, count: 9 },
];

export function DashboardPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [stats, setStats] = useState<DashboardStats>(FALLBACK_STATS);
  const [heat, setHeat] = useState<HeatCell[]>(FALLBACK_HEAT);
  const [isDemo, setIsDemo] = useState<boolean>(!isAuthenticated());
  const [loading, setLoading] = useState<boolean>(isAuthenticated());
  const [customising, setCustomising] = useState(false);
  // Customizable widget grid below the fixed executive section — collapsed
  // by default so the first impression is the curated dashboard, not 13
  // configurable tiles. Power users expand it (or jump to "Customize") when
  // they want their own layout. State persists per session.
  const [widgetsOpen, setWidgetsOpen] = useState<boolean>(() => {
    if (typeof window === 'undefined') return false;
    return window.sessionStorage.getItem('grc.dashboardWidgetsOpen') === 'true';
  });
  function toggleWidgets() {
    const next = !widgetsOpen;
    setWidgetsOpen(next);
    try { window.sessionStorage.setItem('grc.dashboardWidgetsOpen', String(next)); } catch { /* noop */ }
  }
  const layout = useDashboardLayout();

  // Hold load() in a ref so the realtime subscription doesn't tear down on
  // every render (load is recreated each render; the ref keeps it stable).
  const loadRef = useRef<() => Promise<void>>(async () => {});
  loadRef.current = async function load() {
    if (!isAuthenticated()) { setIsDemo(true); setLoading(false); return; }
    setLoading(true);
    try {
      const [s, h] = await Promise.all([
        dashboardApi.stats().catch(() => null),
        dashboardApi.heatmap().catch(() => null),
      ]);
      if (s) { setStats(s); setIsDemo(false); }
      if (Array.isArray(h)) setHeat(h as HeatCell[]);
    } finally { setLoading(false); }
  };
  async function load() { return loadRef.current(); }

  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  // Sprint 4 §2.7 — keep dashboard data live. Whenever any lifecycle service
  // broadcasts an entity change, we re-fetch stats. The 600ms debounce lets a
  // bursty CAPA cascade or bulk import collapse into a single round-trip.
  const { pulse, refreshing } = useLiveRefresh({
    kinds: DASHBOARD_LIVE_TOPICS,
    onRefresh: () => loadRef.current(),
    waitMs: 600,
    enabled: !isDemo,
  });

  // Items currently in the layout, plus a list of widgets that can be added back
  const currentIds = new Set(layout.items.map(it => it.id));
  const addable = useMemo(() => WIDGETS.filter(w => !currentIds.has(w.id)), [layout.items]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <Shell title={t('itemDashboard')} sub={t('brandSub')}>
      {/* Minimal page header — the ExecutiveDashboard hero owns the
       * "wow" first impression below; the header carries only chrome
       * actions (refresh, exports, demo badge) and stays out of the way. */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        gap: 12, marginBottom: 4, flexWrap: 'wrap',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {isDemo && <Badge tone="warning">{t('demoMode')}</Badge>}
          {!isDemo && <LivePulse pulse={pulse} enabled={!isDemo} />}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <Button variant="ghost" Icon={RefreshCw} onClick={load} disabled={loading || refreshing}>
            {t('dashRefresh')}
          </Button>
          <Button variant="secondary" Icon={FileText} disabled={isDemo}
            onClick={async () => {
              try { await reportingApi.boardPackPdf(lang); toast.success(t('boardPackStarted')); }
              catch (e) { toast.danger(e instanceof ApiError ? e.message : t('boardPackFailed')); }
            }}>{t('boardPackPdfBtn')}</Button>
          <Button variant="secondary" Icon={Presentation} disabled={isDemo}
            onClick={async () => {
              try { await reportingApi.boardPackPptx(lang); toast.success(t('boardPackStarted')); }
              catch (e) { toast.danger(e instanceof ApiError ? e.message : t('boardPackFailed')); }
            }}>{t('boardPackPptxBtn')}</Button>
          <ExportMenu
            demoMode={isDemo} t={t} toast={toast}
            onPdf={() => reportingApi.riskRegisterPdf(lang)}
            onXlsx={() => reportingApi.riskRegisterXlsx()}
          />
        </div>
      </div>

      {/* Fixed executive dashboard — hero + KPI ribbon + heatmap + trends +
       * framework readiness + attention/deadlines/activity columns. This
       * is the canonical first-impression view; same for every user, no
       * setup. */}
      <ExecutiveDashboard stats={stats} heat={heat} />

      {/* Optional customizable widget grid — collapsed by default so it
       * doesn't compete with the executive section. Power users expand it
       * to add their own widgets / arrange layout / drag-and-drop. */}
      <div style={{ marginTop: 24 }}>
        <button
          type="button"
          onClick={toggleWidgets}
          style={{
            display: 'inline-flex', alignItems: 'center', gap: 8,
            background: 'transparent', border: 'none', cursor: 'pointer',
            padding: '6px 0',
            font: '700 13px var(--font-sans)', color: 'var(--ink-2)',
          }}>
          {widgetsOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          {t('dashCustomWidgetsToggle')}
          <span className="text-2xs text-muted" style={{ fontWeight: 500, marginInlineStart: 4 }}>
            ({layout.items.length})
          </span>
        </button>
        {widgetsOpen && (
          <>
            <div style={{
              display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap',
              marginTop: 8, marginBottom: 4,
            }}>
              <Button variant={customising ? 'primary' : 'secondary'} size="sm" Icon={Layers}
                onClick={() => setCustomising(c => !c)}>
                {customising ? t('dashDoneCustomising') : t('dashCustomise')}
              </Button>
              {customising && (
                <Button variant="ghost" size="sm" Icon={RotateCcw} onClick={() => layout.reset()}>
                  {t('dashResetLayout')}
                </Button>
              )}
              {customising && <Badge tone="accent" dot>{t('dashCustomising')}</Badge>}
            </div>

            {customising && addable.length > 0 && (
              <Card title={t('dashAddWidgets')} Icon={Plus}>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                  {addable.map(w => (
                    <button key={w.id} onClick={() => layout.add(w.id)} style={{
                      display: 'inline-flex', alignItems: 'center', gap: 6,
                      padding: '6px 10px', background: 'var(--surface-2)',
                      border: '1px solid var(--border)', borderRadius: 'var(--r-pill)',
                      cursor: 'pointer', fontSize: 12, color: 'var(--ink-1)',
                    }}>
                      <Plus size={12} /> {t(w.titleKey)}
                      <span style={{ marginInlineStart: 4, fontSize: 10, color: 'var(--ink-3)' }}>· {w.category}</span>
                    </button>
                  ))}
                </div>
              </Card>
            )}

            <DashboardGrid
              items={layout.items}
              ctx={{ stats, heat, t }}
              customising={customising}
              onMove={layout.move}
              onRemove={layout.remove}
              onResize={(id) => {
                const cur = layout.items.find(it => it.id === id);
                if (!cur) return;
                const next: WidgetSize = cur.size === 'sm' ? 'md' : cur.size === 'md' ? 'lg' : 'sm';
                layout.setSize(id, next);
              }}
            />
          </>
        )}
      </div>
    </Shell>
  );
}

function DashboardGrid({
  items, ctx, customising, onMove, onRemove, onResize,
}: {
  items: Array<{ id: string; size: WidgetSize }>;
  ctx: { stats: DashboardStats; heat: HeatCell[]; t: (k: string) => string };
  customising: boolean;
  onMove: (from: number, to: number) => void;
  onRemove: (id: string) => void;
  onResize: (id: string) => void;
}) {
  const [dragFrom, setDragFrom] = useState<number | null>(null);
  const [dragOver, setDragOver]  = useState<number | null>(null);

  function colSpan(size: WidgetSize): number {
    return size === 'sm' ? 3 : size === 'md' ? 6 : 12;
  }

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(12, minmax(0, 1fr))',
      gap: 16,
      marginTop: 12,
    }}>
      {items.map((it, idx) => {
        const widget = WIDGETS_BY_ID[it.id];
        if (!widget) return null;
        const isDragging = dragFrom === idx;
        const isDragOver = dragOver === idx && dragFrom != null && dragFrom !== idx;
        return (
          <div
            key={it.id}
            draggable={customising}
            onDragStart={() => setDragFrom(idx)}
            onDragEnd={() => { setDragFrom(null); setDragOver(null); }}
            onDragOver={(e) => { if (customising) { e.preventDefault(); setDragOver(idx); } }}
            onDragLeave={() => { if (dragOver === idx) setDragOver(null); }}
            onDrop={(e) => {
              if (!customising) return;
              e.preventDefault();
              if (dragFrom != null) onMove(dragFrom, idx);
              setDragFrom(null); setDragOver(null);
            }}
            style={{
              gridColumn: `span ${colSpan(it.size)}`,
              position: 'relative',
              opacity: isDragging ? 0.4 : 1,
              transform: isDragOver ? 'translateY(-2px)' : undefined,
              outline: isDragOver ? '2px dashed var(--accent-700)' : (customising ? '1px dashed var(--border-strong)' : 'none'),
              outlineOffset: customising ? '4px' : undefined,
              borderRadius: 'var(--r-md)',
              transition: 'transform var(--dur-fast) var(--ease-standard)',
            }}
          >
            {customising && <CustomiseOverlay onDelete={() => onRemove(it.id)} onResize={() => onResize(it.id)} size={it.size} t={ctx.t} />}
            {widget.render(ctx)}
          </div>
        );
      })}
      {items.length === 0 && (
        <div style={{ gridColumn: 'span 12', padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
          {ctx.t('dashEmptyLayout')}
        </div>
      )}
    </div>
  );
}

function CustomiseOverlay({ onDelete, onResize, size, t }: { onDelete: () => void; onResize: () => void; size: WidgetSize; t: (k: string) => string }) {
  return (
    <div style={{
      position: 'absolute', insetInlineEnd: 6, top: 6, zIndex: 5,
      display: 'flex', gap: 4,
    }}>
      <button title={t('dashGrip')} aria-label={t('dashGrip')}
        style={{ ...overlayBtn, cursor: 'grab' }}>
        <GripVertical size={12} />
      </button>
      <button title={t('dashResize')} aria-label={t('dashResize')} onClick={onResize}
        style={overlayBtn}>
        {size === 'lg' ? <Minimize2 size={12} /> : <Maximize2 size={12} />}
      </button>
      <button title={t('dashRemove')} aria-label={t('dashRemove')} onClick={onDelete}
        style={{ ...overlayBtn, color: 'var(--danger-fg)' }}>
        <XIcon size={12} />
      </button>
    </div>
  );
}

const overlayBtn: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
  width: 22, height: 22, padding: 0,
  background: 'var(--surface)', border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', cursor: 'pointer', color: 'var(--ink-2)',
  boxShadow: 'var(--shadow-1)',
};

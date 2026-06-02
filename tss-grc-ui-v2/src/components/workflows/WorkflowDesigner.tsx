import { useEffect, useMemo, useRef, useState } from 'react';
import { Plus, Save, RotateCcw, X as XIcon, Workflow, Trash2, Hourglass } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  loadOverlay, saveOverlay, resetOverlay, applyOverlay, autoLayout,
  hasUnsavedChanges, addState, removeState, addTransition, removeTransition,
  setPosition,
  type DesignerOverlay, type LayoutPosition,
} from '../../lib/workflowEditor';
import type { WorkflowDefinitionGraph, WorkflowStateDto } from '../../lib/api';

const NODE_W = 180;
const NODE_H = 88;
const CANVAS_PAD = 80;

/**
 * WorkflowDesigner — Sprint 5 visual workflow editor.
 *
 * SVG canvas where each state is a draggable node and transitions are
 * curved arrows. Add / remove buttons mutate a localStorage overlay so
 * the canonical mock seed stays untouched. the industry pattern Flow Designer pitch
 * scoped to a single screen.
 *
 *   Canvas: nodes positioned via auto-layout (BFS from initial state).
 *   Drag:   mouse-down on a node → drag → mouse-up writes overlay.
 *   Add:    bottom toolbar — "+ State" / "+ Transition".
 *   Save:   persists current overlay to localStorage.
 *   Reset:  drops the overlay (restores seed).
 */
export function WorkflowDesigner({ graph, onClose }: { graph: WorkflowDefinitionGraph; onClose?: () => void }) {
  const { t, lang } = useI18n();
  const toast = useToast();
  const defId = graph.definition.id;
  const [overlay, setOverlay] = useState<DesignerOverlay>(() => loadOverlay(defId));
  const [dragId, setDragId] = useState<number | null>(null);
  const [showAddState, setShowAddState] = useState(false);
  const [showAddTransition, setShowAddTransition] = useState(false);

  // Re-load when the user switches definitions.
  useEffect(() => { setOverlay(loadOverlay(defId)); }, [defId]);

  const effective = useMemo(() => applyOverlay(graph, overlay), [graph, overlay]);
  const layout = useMemo(() => autoLayout(effective.states, effective.transitions), [effective]);

  function getPos(s: WorkflowStateDto): LayoutPosition {
    return overlay.positions[String(s.id)] ?? layout[String(s.id)] ?? { x: 40, y: 40 };
  }

  // Compute SVG canvas extents.
  const dims = useMemo(() => {
    let maxX = 800, maxY = 400;
    for (const s of effective.states) {
      const p = getPos(s);
      if (p.x + NODE_W > maxX) maxX = p.x + NODE_W;
      if (p.y + NODE_H > maxY) maxY = p.y + NODE_H;
    }
    return { width: maxX + CANVAS_PAD, height: maxY + CANVAS_PAD };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [effective, overlay.positions]);

  // ─── Drag handling ──────────────────────────────────────────────
  const containerRef = useRef<HTMLDivElement>(null);
  const dragOffsetRef = useRef<{ dx: number; dy: number }>({ dx: 0, dy: 0 });

  function onNodeMouseDown(e: React.MouseEvent, s: WorkflowStateDto) {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const pos = getPos(s);
    dragOffsetRef.current = {
      dx: e.clientX - rect.left - pos.x,
      dy: e.clientY - rect.top - pos.y,
    };
    setDragId(s.id);
  }
  useEffect(() => {
    if (dragId == null) return;
    function onMove(e: MouseEvent) {
      if (!containerRef.current) return;
      const rect = containerRef.current.getBoundingClientRect();
      const x = Math.max(0, e.clientX - rect.left - dragOffsetRef.current.dx);
      const y = Math.max(0, e.clientY - rect.top - dragOffsetRef.current.dy);
      setOverlay(prev => setPosition(prev, dragId!, { x, y }));
    }
    function onUp() { setDragId(null); }
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
  }, [dragId]);

  // ─── Mutations ──────────────────────────────────────────────────
  function commitAddState(code: string, nameEn: string, nameAr: string) {
    if (!code.trim() || !nameEn.trim()) return;
    setOverlay(prev => addState(prev, defId, code.trim().toUpperCase(), nameEn.trim(), nameAr.trim()));
    setShowAddState(false);
    toast.success(t('designerStateAdded'));
  }
  function commitAddTransition(from: string, to: string, action: string, actionName: string, role: string, slaHours: string) {
    if (!from || !to || !action.trim() || !actionName.trim()) return;
    setOverlay(prev => addTransition(
      prev,
      from, to,
      action.trim().toUpperCase(),
      actionName.trim(),
      role.trim() || undefined,
      slaHours ? Number(slaHours) : undefined,
    ));
    setShowAddTransition(false);
    toast.success(t('designerTransitionAdded'));
  }

  function save() {
    saveOverlay(defId, overlay);
    toast.success(t('designerSaved'));
  }
  function reset() {
    resetOverlay(defId);
    setOverlay({ positions: {}, addedStates: [], removedStateIds: [], addedTransitions: [], removedTransitionIds: [] });
    toast.info(t('designerReset'));
  }

  const dirty = hasUnsavedChanges(overlay);

  return (
    <Card title={t('designerTitle')} Icon={Workflow}
      action={
        <div style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
          {dirty && <Badge tone="warning">{t('designerUnsaved')}</Badge>}
          <Button variant="secondary" Icon={RotateCcw} onClick={reset}>{t('designerReset')}</Button>
          <Button variant="primary" Icon={Save} onClick={save}>{t('designerSave')}</Button>
          {onClose && <Button variant="secondary" Icon={XIcon} onClick={onClose}>{t('designerClose')}</Button>}
        </div>
      }>

      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
        {t('designerSub')}
      </div>

      {/* ── Canvas ─────────────────────────────────────────────── */}
      <div ref={containerRef}
        style={{
          position: 'relative',
          width: '100%',
          height: dims.height,
          background:
            'linear-gradient(0deg, var(--surface-2) 1px, transparent 1px) 0 0 / 24px 24px,' +
            'linear-gradient(90deg, var(--surface-2) 1px, transparent 1px) 0 0 / 24px 24px,' +
            'var(--surface-1)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--r-md)',
          overflow: 'auto',
        }}>
        <svg width={dims.width} height={dims.height}
          style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
          <defs>
            <marker id="wf-arrow" viewBox="0 0 10 10" refX="9" refY="5"
              markerWidth="8" markerHeight="8" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--primary)" />
            </marker>
          </defs>
          {effective.transitions.map(tr => {
            const from = effective.states.find(s => s.code === tr.fromStateCode);
            const to = effective.states.find(s => s.code === tr.toStateCode);
            if (!from || !to) return null;
            const fp = getPos(from);
            const tp = getPos(to);
            const x1 = fp.x + NODE_W;
            const y1 = fp.y + NODE_H / 2;
            const x2 = tp.x;
            const y2 = tp.y + NODE_H / 2;
            // Cubic bezier — bend horizontally.
            const cx = (x1 + x2) / 2;
            const path = `M ${x1} ${y1} C ${cx} ${y1}, ${cx} ${y2}, ${x2} ${y2}`;
            return (
              <g key={tr.id}>
                <path d={path} fill="none" stroke="var(--primary)" strokeWidth={1.5}
                  markerEnd="url(#wf-arrow)" opacity={0.85} />
                {/* Mid-arc label container — clickable for delete */}
                <foreignObject x={cx - 60} y={(y1 + y2) / 2 - 16} width={120} height={32}
                  style={{ pointerEvents: 'auto' }}>
                  <div style={{
                    fontSize: 10, padding: '2px 6px',
                    background: 'var(--surface-1)', border: '1px solid var(--border)',
                    borderRadius: 'var(--r-sm)', display: 'inline-flex', alignItems: 'center', gap: 4,
                    boxShadow: 'var(--shadow-1)',
                    fontFamily: 'var(--font-mono)',
                  }}>
                    <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {tr.actionCode}
                      {tr.slaHours != null && <> · {tr.slaHours}h</>}
                    </span>
                    <button onClick={() => setOverlay(prev => removeTransition(prev, tr.id))}
                      title={t('designerRemoveTransition')}
                      style={{
                        border: 'none', background: 'transparent', cursor: 'pointer',
                        color: 'var(--danger)', display: 'inline-flex', padding: 0,
                      }}>
                      <Trash2 size={10} />
                    </button>
                  </div>
                </foreignObject>
              </g>
            );
          })}
        </svg>

        {/* Nodes (positioned divs above the SVG) */}
        {effective.states.map(s => {
          const p = getPos(s);
          return (
            <div key={s.id}
              onMouseDown={(e) => onNodeMouseDown(e, s)}
              style={{
                position: 'absolute', left: p.x, top: p.y,
                width: NODE_W, minHeight: NODE_H,
                background: 'var(--surface-1)', border: `2px solid ${s.uiColor === 'success' ? 'var(--success-fg)'
                  : s.uiColor === 'danger' ? 'var(--danger)'
                  : s.uiColor === 'warning' ? 'var(--warning-fg)'
                  : s.uiColor === 'accent' ? 'var(--accent-700)'
                  : 'var(--primary)'}`,
                borderRadius: 'var(--r-md)',
                padding: 10, cursor: dragId === s.id ? 'grabbing' : 'grab',
                userSelect: 'none', boxShadow: dragId === s.id ? 'var(--shadow-2)' : 'var(--shadow-1)',
                transition: dragId === s.id ? 'none' : 'box-shadow var(--dur-fast)',
              }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 6 }}>
                <span className="mono" style={{ fontSize: 10, fontWeight: 700 }}>{s.code}</span>
                <button onClick={(e) => {
                  e.stopPropagation();
                  setOverlay(prev => removeState(prev, s.id));
                }}
                  title={t('designerRemoveState')}
                  style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: 'var(--danger)', padding: 0 }}>
                  <Trash2 size={11} />
                </button>
              </div>
              <div style={{ fontSize: 12, fontWeight: 600, marginTop: 4 }}>
                {lang === 'ar' && s.nameAr ? s.nameAr : s.nameEn}
              </div>
              <div style={{ display: 'flex', gap: 4, marginTop: 6, flexWrap: 'wrap' }}>
                {s.isInitial && <Badge tone="success">start</Badge>}
                {s.isTerminal && <Badge tone="neutral">end</Badge>}
              </div>
            </div>
          );
        })}
      </div>

      {/* ── Bottom toolbar ─────────────────────────────────────── */}
      <div style={{ display: 'flex', gap: 8, marginTop: 12, flexWrap: 'wrap' }}>
        <Button variant="secondary" Icon={Plus} onClick={() => setShowAddState(true)}>
          {t('designerAddState')}
        </Button>
        <Button variant="secondary" Icon={Plus} onClick={() => setShowAddTransition(true)}>
          {t('designerAddTransition')}
        </Button>
        <span style={{ flex: 1 }} />
        <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
          <Hourglass size={11} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
          {t('designerStateCount').replace('{n}', String(effective.states.length))} ·
          {' '}{t('designerTransitionCount').replace('{n}', String(effective.transitions.length))}
        </span>
      </div>

      {/* ── Add-state form ─────────────────────────────────────── */}
      {showAddState && <AddStateForm
        onCancel={() => setShowAddState(false)}
        onSubmit={commitAddState}
        t={t} />}

      {/* ── Add-transition form ────────────────────────────────── */}
      {showAddTransition && <AddTransitionForm
        states={effective.states}
        onCancel={() => setShowAddTransition(false)}
        onSubmit={commitAddTransition}
        t={t} lang={lang} />}
    </Card>
  );
}

// ─── Inline forms ───────────────────────────────────────────────────────────

function AddStateForm({
  onCancel, onSubmit, t,
}: {
  onCancel: () => void;
  onSubmit: (code: string, nameEn: string, nameAr: string) => void;
  t: (k: string) => string;
}) {
  const [code, setCode] = useState('');
  const [nameEn, setNameEn] = useState('');
  const [nameAr, setNameAr] = useState('');
  return (
    <div style={{ marginTop: 12, padding: 12, border: '1px solid var(--border)', borderRadius: 'var(--r-md)', background: 'var(--surface-2)' }}>
      <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 8 }}>{t('designerAddStateTitle')}</div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr 2fr', gap: 8, marginBottom: 8 }}>
        <Input label={t('designerLblCode')} value={code} onChange={setCode} placeholder="ESCALATED" />
        <Input label={t('designerLblNameEn')} value={nameEn} onChange={setNameEn} placeholder="Escalated to manager" />
        <Input label={t('designerLblNameAr')} value={nameAr} onChange={setNameAr} placeholder="مُصعَّد للمدير" />
      </div>
      <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
        <Button variant="secondary" onClick={onCancel}>{t('designerCancel')}</Button>
        <Button variant="primary" onClick={() => onSubmit(code, nameEn, nameAr)}>{t('designerCommit')}</Button>
      </div>
    </div>
  );
}

function AddTransitionForm({
  states, onCancel, onSubmit, t, lang,
}: {
  states: ReadonlyArray<WorkflowStateDto>;
  onCancel: () => void;
  onSubmit: (from: string, to: string, action: string, actionName: string, role: string, sla: string) => void;
  t: (k: string) => string;
  lang: 'en' | 'ar';
}) {
  const [from, setFrom] = useState(states[0]?.code ?? '');
  const [to, setTo] = useState(states[1]?.code ?? '');
  const [action, setAction] = useState('');
  const [actionName, setActionName] = useState('');
  const [role, setRole] = useState('');
  const [sla, setSla] = useState('');
  return (
    <div style={{ marginTop: 12, padding: 12, border: '1px solid var(--border)', borderRadius: 'var(--r-md)', background: 'var(--surface-2)' }}>
      <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 8 }}>{t('designerAddTransitionTitle')}</div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 8 }}>
        <Select label={t('designerLblFrom')} value={from} onChange={setFrom}
          options={states.map(s => ({ value: s.code, label: lang === 'ar' && s.nameAr ? s.nameAr : s.nameEn }))} />
        <Select label={t('designerLblTo')} value={to} onChange={setTo}
          options={states.map(s => ({ value: s.code, label: lang === 'ar' && s.nameAr ? s.nameAr : s.nameEn }))} />
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr 1fr 1fr', gap: 8, marginBottom: 8 }}>
        <Input label={t('designerLblAction')} value={action} onChange={setAction} placeholder="ESCALATE" />
        <Input label={t('designerLblActionName')} value={actionName} onChange={setActionName} placeholder="Escalate to manager" />
        <Input label={t('designerLblRole')} value={role} onChange={setRole} placeholder="MANAGER" />
        <Input label={t('designerLblSla')} value={sla} onChange={setSla} placeholder="24" />
      </div>
      <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
        <Button variant="secondary" onClick={onCancel}>{t('designerCancel')}</Button>
        <Button variant="primary" onClick={() => onSubmit(from, to, action, actionName, role, sla)}>{t('designerCommit')}</Button>
      </div>
    </div>
  );
}

function Input({ label, value, onChange, placeholder }: { label: string; value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{label}</span>
      <input value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder}
        style={{ padding: '6px 8px', fontSize: 12, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-1)' }} />
    </label>
  );
}

function Select({ label, value, onChange, options }: { label: string; value: string; onChange: (v: string) => void; options: Array<{ value: string; label: string }> }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{label}</span>
      <select value={value} onChange={e => onChange(e.target.value)}
        style={{ padding: '6px 8px', fontSize: 12, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-1)' }}>
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </label>
  );
}

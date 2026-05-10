// Visual workflow editor overlay — Sprint 5.
//
// Promotes the read-only WorkflowsPage to ServiceNow Flow Designer parity.
// Stores edits as a localStorage overlay keyed by definition id; the
// canonical seed in mockWorkflowDefs.ts stays untouched, edits are
// additive on top so a refresh restores user changes without polluting
// the demo seed.

import type {
  WorkflowDefinitionGraph, WorkflowStateDto, WorkflowTransitionView,
} from './api';

// ─── Overlay model ──────────────────────────────────────────────────────────

export interface DesignerOverlay {
  /** State positions (logical units, default 200×120 grid). Keyed by state id. */
  positions: Record<string, { x: number; y: number }>;
  /** States added since seed. */
  addedStates: WorkflowStateDto[];
  /** State ids removed. */
  removedStateIds: number[];
  /** Transitions added since seed. */
  addedTransitions: WorkflowTransitionView[];
  /** Transition ids removed. */
  removedTransitionIds: number[];
}

export const EMPTY_OVERLAY: DesignerOverlay = {
  positions: {},
  addedStates: [],
  removedStateIds: [],
  addedTransitions: [],
  removedTransitionIds: [],
};

const KEY = (defId: number) => `grc.workflow.designer.${defId}.v1`;
const EVENT = (defId: number) => `grc.workflow.designer.${defId}.changed`;

export function loadOverlay(defId: number): DesignerOverlay {
  if (typeof window === 'undefined') return EMPTY_OVERLAY;
  try {
    const raw = window.localStorage.getItem(KEY(defId));
    if (!raw) return EMPTY_OVERLAY;
    const parsed = JSON.parse(raw);
    return {
      positions: parsed.positions ?? {},
      addedStates: parsed.addedStates ?? [],
      removedStateIds: parsed.removedStateIds ?? [],
      addedTransitions: parsed.addedTransitions ?? [],
      removedTransitionIds: parsed.removedTransitionIds ?? [],
    };
  } catch { return EMPTY_OVERLAY; }
}

export function saveOverlay(defId: number, ov: DesignerOverlay) {
  try { window.localStorage.setItem(KEY(defId), JSON.stringify(ov)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT(defId)));
}

export function resetOverlay(defId: number) {
  try { window.localStorage.removeItem(KEY(defId)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT(defId)));
}

export function subscribeOverlay(defId: number, cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT(defId), handler);
  const storageHandler = (e: StorageEvent) => { if (e.key === KEY(defId)) cb(); };
  window.addEventListener('storage', storageHandler);
  return () => {
    window.removeEventListener(EVENT(defId), handler);
    window.removeEventListener('storage', storageHandler);
  };
}

// ─── Apply overlay → effective graph ────────────────────────────────────────

export function applyOverlay(graph: WorkflowDefinitionGraph, ov: DesignerOverlay): WorkflowDefinitionGraph {
  const states = [
    ...graph.states.filter(s => !ov.removedStateIds.includes(s.id)),
    ...ov.addedStates,
  ];
  const transitions = [
    ...graph.transitions.filter(t => !ov.removedTransitionIds.includes(t.id)),
    ...ov.addedTransitions,
  ];
  return { definition: graph.definition, states, transitions };
}

export function hasUnsavedChanges(ov: DesignerOverlay): boolean {
  return ov.addedStates.length > 0
    || ov.removedStateIds.length > 0
    || ov.addedTransitions.length > 0
    || ov.removedTransitionIds.length > 0
    || Object.keys(ov.positions).length > 0;
}

// ─── Auto-layout ────────────────────────────────────────────────────────────
//
// Simple BFS-from-initial layout: states are placed in columns by their
// distance from the initial state. States with no incoming transitions
// (other than initial) are placed in the first column. Within a column,
// states stack vertically by sortOrder.

export interface LayoutPosition {
  x: number; y: number;
}

export function autoLayout(
  states: ReadonlyArray<WorkflowStateDto>,
  transitions: ReadonlyArray<WorkflowTransitionView>,
): Record<string, LayoutPosition> {
  const colW = 240;
  const rowH = 120;
  const padX = 40;
  const padY = 40;

  // Compute distance-from-initial via BFS.
  const initial = states.find(s => s.isInitial);
  const distance: Record<string, number> = {};
  if (initial) {
    distance[initial.code] = 0;
    const queue: string[] = [initial.code];
    while (queue.length > 0) {
      const cur = queue.shift()!;
      for (const t of transitions) {
        if (t.fromStateCode === cur && distance[t.toStateCode] == null) {
          distance[t.toStateCode] = distance[cur] + 1;
          queue.push(t.toStateCode);
        }
      }
    }
  }
  // Any unreached state — put after the deepest known state.
  const maxDist = Math.max(0, ...Object.values(distance));
  for (const s of states) {
    if (distance[s.code] == null) distance[s.code] = maxDist + 1;
  }

  // Column → list of states (sorted by sortOrder).
  const columns: Record<number, WorkflowStateDto[]> = {};
  for (const s of states) {
    const c = distance[s.code] ?? 0;
    if (!columns[c]) columns[c] = [];
    columns[c].push(s);
  }
  for (const c of Object.values(columns)) c.sort((a, b) => a.sortOrder - b.sortOrder);

  const out: Record<string, LayoutPosition> = {};
  for (const [colStr, list] of Object.entries(columns)) {
    const col = Number(colStr);
    list.forEach((s, i) => {
      out[String(s.id)] = {
        x: padX + col * colW,
        y: padY + i * rowH,
      };
    });
  }
  return out;
}

export function effectivePosition(
  state: WorkflowStateDto,
  ov: DesignerOverlay,
  fallback: Record<string, LayoutPosition>,
): LayoutPosition {
  return ov.positions[String(state.id)] ?? fallback[String(state.id)] ?? { x: 40, y: 40 };
}

// ─── Mutation helpers ───────────────────────────────────────────────────────

let _newStateId = -1000;
let _newTransId = -2000;

export function nextStateId(): number {
  _newStateId -= 1;
  return _newStateId;
}
export function nextTransitionId(): number {
  _newTransId -= 1;
  return _newTransId;
}

export function addState(ov: DesignerOverlay, definitionId: number, code: string, nameEn: string, nameAr?: string): DesignerOverlay {
  const id = nextStateId();
  const newState: WorkflowStateDto = {
    id, definitionId, code, nameEn,
    nameAr: nameAr ?? '',
    isInitial: false, isTerminal: false,
    sortOrder: 99, uiColor: 'info',
  };
  return {
    ...ov,
    addedStates: [...ov.addedStates, newState],
    positions: { ...ov.positions, [String(id)]: { x: 40 + 240 * 4, y: 40 + ov.addedStates.length * 120 } },
  };
}

export function removeState(ov: DesignerOverlay, stateId: number): DesignerOverlay {
  // Was it an added one? Remove from addedStates; otherwise mark removed.
  if (ov.addedStates.some(s => s.id === stateId)) {
    const newPositions = { ...ov.positions };
    delete newPositions[String(stateId)];
    return {
      ...ov,
      addedStates: ov.addedStates.filter(s => s.id !== stateId),
      positions: newPositions,
    };
  }
  return { ...ov, removedStateIds: [...ov.removedStateIds, stateId] };
}

export function addTransition(
  ov: DesignerOverlay, fromStateCode: string, toStateCode: string,
  actionCode: string, actionNameEn: string, requiredRole?: string, slaHours?: number,
): DesignerOverlay {
  const id = nextTransitionId();
  const newT: WorkflowTransitionView = {
    id, fromStateCode, toStateCode, actionCode, actionNameEn,
    requiredRole, slaHours,
  };
  return { ...ov, addedTransitions: [...ov.addedTransitions, newT] };
}

export function removeTransition(ov: DesignerOverlay, transitionId: number): DesignerOverlay {
  if (ov.addedTransitions.some(t => t.id === transitionId)) {
    return { ...ov, addedTransitions: ov.addedTransitions.filter(t => t.id !== transitionId) };
  }
  return { ...ov, removedTransitionIds: [...ov.removedTransitionIds, transitionId] };
}

export function setPosition(ov: DesignerOverlay, stateId: number, pos: LayoutPosition): DesignerOverlay {
  return { ...ov, positions: { ...ov.positions, [String(stateId)]: pos } };
}

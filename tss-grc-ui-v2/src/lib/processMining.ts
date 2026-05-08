// Process mining — Sprint 6 follow-on (A1).
//
// The WorkflowInstance data already records ageDays (time-in-current-stage)
// and slaDays (budget). That's enough to derive:
//   • stage-level metrics  — avg time-in-stage per workflow×stage
//   • stuck instances       — instances with ageDays > slaDays (descending)
//   • bottleneck heatmap    — workflow×stage matrix coloured by breach ratio
//   • workflow leaderboard  — % of instances within SLA per workflow
//
// In production this would consume `WorkflowActionLog` for true cycle-time
// analytics. The demo uses the in-flight snapshot which is enough to
// surface the bottleneck story.

import { ALL_WORKFLOWS } from './mockWorkflows';
import type { WorkflowInstance } from './workflows';

// ─── Stuck instances ───────────────────────────────────────────────────────

export interface StuckInstance {
  instance: WorkflowInstance;
  /** Days the instance is over its SLA budget (ageDays - slaDays). */
  daysOver: number;
  /** Breach ratio (ageDays / slaDays). > 1 means stuck. */
  breachRatio: number;
}

export function findStuckInstances(
  instances: ReadonlyArray<WorkflowInstance> = ALL_WORKFLOWS,
): StuckInstance[] {
  return instances
    .filter(i => i.slaDays > 0 && i.ageDays > 0)
    .map(i => {
      const ratio = i.ageDays / i.slaDays;
      return {
        instance: i,
        daysOver: Math.max(0, i.ageDays - i.slaDays),
        breachRatio: ratio,
      };
    })
    .filter(s => s.breachRatio > 1)
    .sort((a, b) => b.breachRatio - a.breachRatio);
}

// ─── Stage metrics ─────────────────────────────────────────────────────────

export interface StageMetric {
  workflow: string;
  stage: string;
  /** Number of instances currently sitting on this stage. */
  count: number;
  /** Average ageDays for instances on this stage. */
  avgAgeDays: number;
  /** Average slaDays for instances on this stage. */
  avgSlaDays: number;
  /** Average breach ratio (avgAgeDays / avgSlaDays). */
  avgBreach: number;
}

export function computeStageMetrics(
  instances: ReadonlyArray<WorkflowInstance> = ALL_WORKFLOWS,
): StageMetric[] {
  const groups = new Map<string, WorkflowInstance[]>();
  for (const i of instances) {
    const key = `${i.workflow}::${i.currentStage}`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(i);
  }
  const out: StageMetric[] = [];
  for (const [key, list] of groups.entries()) {
    const [workflow, stage] = key.split('::');
    const avgAgeDays = list.reduce((s, i) => s + i.ageDays, 0) / list.length;
    const avgSlaDays = list.reduce((s, i) => s + i.slaDays, 0) / list.length || 1;
    out.push({
      workflow, stage,
      count: list.length,
      avgAgeDays: Math.round(avgAgeDays * 10) / 10,
      avgSlaDays: Math.round(avgSlaDays * 10) / 10,
      avgBreach: Math.round((avgAgeDays / avgSlaDays) * 100) / 100,
    });
  }
  return out.sort((a, b) => b.avgBreach - a.avgBreach);
}

// ─── Workflow leaderboard ──────────────────────────────────────────────────

export interface WorkflowScore {
  workflow: string;
  total: number;
  /** Instances within SLA (ageDays <= slaDays). */
  withinSla: number;
  withinSlaPct: number;
  /** Average breach ratio across all instances of this workflow. */
  avgBreach: number;
  worstInstance?: WorkflowInstance;
}

export function workflowLeaderboard(
  instances: ReadonlyArray<WorkflowInstance> = ALL_WORKFLOWS,
): WorkflowScore[] {
  const groups = new Map<string, WorkflowInstance[]>();
  for (const i of instances) {
    if (!groups.has(i.workflow)) groups.set(i.workflow, []);
    groups.get(i.workflow)!.push(i);
  }
  const out: WorkflowScore[] = [];
  for (const [workflow, list] of groups.entries()) {
    const withinSla = list.filter(i => i.slaDays === 0 || i.ageDays <= i.slaDays).length;
    const withinSlaPct = Math.round((withinSla / list.length) * 100);
    const avgBreach = list.reduce((s, i) => s + (i.slaDays > 0 ? i.ageDays / i.slaDays : 0), 0) / list.length;
    const worst = [...list].sort((a, b) => (b.slaDays > 0 ? b.ageDays / b.slaDays : 0) - (a.slaDays > 0 ? a.ageDays / a.slaDays : 0))[0];
    out.push({
      workflow,
      total: list.length,
      withinSla,
      withinSlaPct,
      avgBreach: Math.round(avgBreach * 100) / 100,
      worstInstance: worst,
    });
  }
  // Worst breach first so the demo opens on the problem.
  return out.sort((a, b) => b.avgBreach - a.avgBreach);
}

// ─── Aggregate KPIs ────────────────────────────────────────────────────────

export interface ProcessKpis {
  totalInFlight: number;
  withinSla: number;
  stuck: number;
  withinSlaPct: number;
  avgAgeDays: number;
  avgBreach: number;
}

export function aggregateProcessKpis(
  instances: ReadonlyArray<WorkflowInstance> = ALL_WORKFLOWS,
): ProcessKpis {
  const total = instances.length;
  const withinSla = instances.filter(i => i.slaDays === 0 || i.ageDays <= i.slaDays).length;
  const stuck = total - withinSla;
  const avgAgeDays = total > 0
    ? Math.round((instances.reduce((s, i) => s + i.ageDays, 0) / total) * 10) / 10
    : 0;
  const avgBreach = total > 0
    ? Math.round((instances.reduce((s, i) => s + (i.slaDays > 0 ? i.ageDays / i.slaDays : 0), 0) / total) * 100) / 100
    : 0;
  return {
    totalInFlight: total,
    withinSla, stuck,
    withinSlaPct: total > 0 ? Math.round((withinSla / total) * 100) : 0,
    avgAgeDays,
    avgBreach,
  };
}

// ─── Heatmap colour helper ─────────────────────────────────────────────────

export function breachTone(ratio: number): 'success' | 'info' | 'warning' | 'danger' {
  if (ratio < 0.5) return 'success';
  if (ratio < 1.0) return 'info';
  if (ratio < 1.5) return 'warning';
  return 'danger';
}

export function breachBackgroundColor(ratio: number): string {
  // Linear interpolation from green → amber → red. Clamp at 2x.
  const r = Math.min(2, ratio);
  if (r < 1) {
    // 0..1 → green (oklch-ish). Lighter green at low ratios.
    return `rgba(34, 197, 94, ${0.08 + r * 0.18})`;
  }
  // 1..2 → amber to red.
  const t = r - 1;
  return `rgba(${220 + Math.round(35 * (1 - t))}, ${100 - Math.round(60 * t)}, ${50 - Math.round(40 * t)}, ${0.22 + t * 0.18})`;
}

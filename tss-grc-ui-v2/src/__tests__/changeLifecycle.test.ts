import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { DECISION_TARGETS, mayTransitionTo, nextStatus } from '../pages/change/changeLifecycle';
import type { ChangeRequestDto } from '../lib/api';

/**
 * S5c — the frontend holds a **second copy** of the change state machine, and until this file
 * nothing checked the two copies agreed.
 *
 * <p>`changeLifecycle.nextStatus` decides what the advance button on every board card and detail
 * panel offers. `ChangeManagementService.NORMAL_TRANSITIONS` decides what the server accepts. They
 * are written in different languages, in different repositories of knowledge, by different hands —
 * and a forward move present in the first and absent from the second is a button that always fails,
 * with a toast blaming the user's data.
 *
 * <p><b>Why a cross-language test rather than a comment.</b> The comment already existed: the old
 * monolith carried *"Mirrors `ChangeManagementService.DECISION_TARGETS`; if that set changes, this one
 * must follow"* — accurate, well-placed, and unable to fail. S5a's lesson applied to the frontend: a
 * prose claim about a boundary is not the boundary. Reading the Java source from a vitest is already
 * this suite's idiom (`navChangeGroup.test.ts` reads the blueprint markdown for the same reason).
 *
 * <p><b>Direction of enforcement.</b> Asymmetric on purpose. The frontend must never offer a move the
 * server refuses — that is a defect with a user-visible failure. The reverse (a legal move the UI does
 * not offer) is a product choice, so it is reported against a named allow-list instead of banned: the
 * UI has no reject control at all today, only a `CAB_REJECTED` label, so nothing can reach the rework
 * path. A *new* unoffered move fails the test and forces the question to be asked.
 */

const JAVA = join(
  __dirname, '..', '..', '..', 'tss-grc-backend', 'src', 'main', 'java', 'com', 'tss', 'grc',
  'module', 'change_mgmt', 'service', 'ChangeManagementService.java');

const java = readFileSync(JAVA, 'utf8');

/** `Map.entry("DRAFT", Set.of("SUBMITTED", …))` → `{DRAFT: Set{SUBMITTED, …}}`. */
function backendTransitions(): Map<string, Set<string>> {
  const table = new Map<string, Set<string>>();
  const re = /Map\.entry\(\s*"(\w+)"\s*,\s*Set\.(?:<String>)?of\(([^)]*)\)\s*\)/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(java)) !== null) {
    const targets = [...m[2].matchAll(/"(\w+)"/g)].map(x => x[1]);
    table.set(m[1], new Set(targets));
  }
  return table;
}

function backendDecisionTargets(): Set<string> {
  const m = /DECISION_TARGETS\s*=\s*[\s\S]{0,120}?Set\.of\(([^)]*)\)/.exec(java);
  expect(m, 'ChangeManagementService.DECISION_TARGETS not found — has the constant been renamed?')
    .not.toBeNull();
  return new Set([...m![1].matchAll(/"(\w+)"/g)].map(x => x[1]));
}

/** Only `status` and `changeType` are read by `nextStatus`; the rest of the DTO is irrelevant. */
function change(status: string, changeType = 'NORMAL'): ChangeRequestDto {
  return { id: 1, status, changeType } as ChangeRequestDto;
}

/**
 * Legal-but-not-offered moves, each with the reason the UI has no control for it.
 *
 * <p>Keyed on the exact `STATUS → TARGET` **pair**, not on the target alone. A target-keyed list
 * would excuse `ROLLED_BACK → CLOSED` and, with it, silently excuse any future move to CLOSED from
 * anywhere else — an allow-list that matches too much hides the next defect as effectively as a
 * scan that matches too little invents one. Adding a row here is a product decision.
 */
const NOT_OFFERED_BY_DESIGN: Record<string, string> = {
  // Every non-terminal status can be abandoned server-side. The register deletes a change rather
  // than cancelling one, and `nextStatus` returns the single *forward* move, so CANCELLED is never
  // a candidate from anywhere.
  'DRAFT → CANCELLED': 'the register deletes rather than cancels; nextStatus is forward-only',
  'SUBMITTED → CANCELLED': 'as DRAFT → CANCELLED',
  'IMPACT_ASSESSED → CANCELLED': 'as DRAFT → CANCELLED',
  'CAB_APPROVED → CANCELLED': 'as DRAFT → CANCELLED',
  'SCHEDULED → CANCELLED': 'as DRAFT → CANCELLED',
  'EMERGENCY_APPROVED → CANCELLED': 'as DRAFT → CANCELLED',

  // ⚠ The gap this test found on its first run, and the old monolith had already written it down:
  // *"the UI has no reject control at all today, only a `CAB_REJECTED` status label and column"*.
  // So a change can be approved in-platform but never rejected in-platform — the rejection has to
  // happen somewhere else and be recorded by hand. CE-13's decision panel is where the control
  // belongs, together with the rework path below.
  'CAB_PENDING → CAB_REJECTED': 'no reject control exists yet — CE-13 decision panel',
  'CAB_REJECTED → DRAFT': 'rework after rejection; unreachable while nothing can reject (CE-13)',

  // ROLLED_BACK is recorded as an *outcome* on the IMPLEMENTING transition, not reached by
  // advancing status, so the move exists without being a forward step.
  'IMPLEMENTING → ROLLED_BACK': 'chosen as an outcome on IMPLEMENTING, not by advancing',
  // And a rolled-back change is always routed through the PIR rather than closed directly.
  'ROLLED_BACK → CLOSED': 'the UI routes a rolled-back change through POST_REVIEW first',
};

describe('S5c — the frontend lifecycle agrees with the backend state machine', () => {
  const backend = backendTransitions();

  it('parsed the backend transition table at all', () => {
    // Anti-vacuity. A regex that matches nothing would make every assertion below pass, which is
    // exactly how four backend scans under-counted this month.
    expect(backend.size, 'no Map.entry(...) transitions parsed from ChangeManagementService')
      .toBeGreaterThanOrEqual(14);
    expect(backend.get('CAB_PENDING')).toEqual(new Set(['CAB_APPROVED', 'CAB_REJECTED']));
  });

  it('never offers a forward move the server would refuse', () => {
    const illegal: string[] = [];
    for (const [status, allowed] of backend) {
      for (const type of ['STANDARD', 'NORMAL', 'EMERGENCY']) {
        const target = nextStatus(change(status, type));
        if (target && !allowed.has(target))
          illegal.push(`${type}/${status} → ${target} (server allows: ${[...allowed].join(', ') || 'nothing'})`);
      }
    }
    expect(illegal,
      'The advance button offers these moves and ChangeManagementService.NORMAL_TRANSITIONS '
      + 'rejects them, so the click always fails:\n  ' + illegal.join('\n  '))
      .toEqual([]);
  });

  it('offers a move for every status the server can advance, or names why not', () => {
    const unoffered: string[] = [];
    for (const [status, allowed] of backend) {
      if (allowed.size === 0) continue;                       // terminal — nothing to offer
      const offered = new Set(
        ['STANDARD', 'NORMAL', 'EMERGENCY'].map(ty => nextStatus(change(status, ty))).filter(Boolean));
      for (const target of allowed)
        if (!offered.has(target) && !(`${status} → ${target}` in NOT_OFFERED_BY_DESIGN))
          unoffered.push(`${status} → ${target}`);
    }
    expect(unoffered,
      'The server accepts these transitions and no control reaches them. Either add the control or '
      + 'add the target to NOT_OFFERED_BY_DESIGN with the reason:\n  ' + unoffered.join('\n  '))
      .toEqual([]);
  });

  it('keeps NOT_OFFERED_BY_DESIGN honest — every excuse still describes a real gap', () => {
    // An allow-list nobody prunes stops describing the system and starts excusing it. If a control
    // for one of these lands (CE-13 adds the reject button), the entry must go in the same commit,
    // or the next genuinely-missing control inherits its excuse.
    const stale: string[] = [];
    for (const pair of Object.keys(NOT_OFFERED_BY_DESIGN)) {
      const [status, target] = pair.split(' → ');
      const allowed = backend.get(status);
      if (!allowed?.has(target)) { stale.push(`${pair} — the server no longer allows it`); continue; }
      const offered = ['STANDARD', 'NORMAL', 'EMERGENCY'].map(ty => nextStatus(change(status, ty)));
      if (offered.includes(target)) stale.push(`${pair} — the UI now offers it; delete this entry`);
    }
    expect(stale, 'NOT_OFFERED_BY_DESIGN entries that no longer describe a gap:\n  '
      + stale.join('\n  ')).toEqual([]);
  });

  it('reaches a terminal state from DRAFT without looping', () => {
    // The walk that matters operationally: a NORMAL change must be able to finish. A cycle here
    // would hang the board's advance button on the same card forever.
    const seen: string[] = [];
    let cur: string | null = 'DRAFT';
    while (cur && !seen.includes(cur)) { seen.push(cur); cur = nextStatus(change(cur)); }
    expect(cur, `nextStatus loops: ${seen.join(' → ')} → ${cur}`).toBeNull();
    expect(seen).toEqual([
      'DRAFT', 'SUBMITTED', 'IMPACT_ASSESSED', 'CAB_PENDING', 'CAB_APPROVED',
      'SCHEDULED', 'IMPLEMENTING', 'IMPLEMENTED', 'POST_REVIEW', 'CLOSED',
    ]);
  });

  it('routes an EMERGENCY change around the CAB, exactly as the server does', () => {
    expect(nextStatus(change('DRAFT', 'EMERGENCY'))).toBe('EMERGENCY_APPROVED');
    expect(backend.get('DRAFT')!.has('EMERGENCY_APPROVED')).toBe(true);
    expect(nextStatus(change('EMERGENCY_APPROVED', 'EMERGENCY'))).toBe('IMPLEMENTING');
  });
});

describe('S5c — the decision gate mirrors the server, not a guess at it', () => {
  it('holds exactly the backend DECISION_TARGETS', () => {
    expect([...DECISION_TARGETS].sort()).toEqual([...backendDecisionTargets()].sort());
  });

  it('withholds a decision move from a user without CHANGE:APPROVE, and allows operational ones', () => {
    // No user is stored in this test environment, so canApprove('CHANGE') is false — the
    // read-only case, which is the one worth pinning.
    for (const target of DECISION_TARGETS) expect(mayTransitionTo(target)).toBe(false);
    expect(mayTransitionTo('SUBMITTED')).toBe(true);
    expect(mayTransitionTo('IMPLEMENTING')).toBe(true);
    expect(mayTransitionTo(null)).toBe(false);
  });
});

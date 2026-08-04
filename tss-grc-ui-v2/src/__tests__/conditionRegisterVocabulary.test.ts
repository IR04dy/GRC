import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { CONDITION_STATES } from '../pages/change/ChangeConditionsCard';
import { en } from '../translations/en';
import { ar } from '../translations/ar';

/**
 * S7e / CE-5 — the frontend holds a **third** copy of the conditions vocabulary (after the migration's
 * `ck_cc_status` and `ChangeConditionStatus`), and F2-91 measured what happens then: a vocabulary
 * stated three times and checked nowhere was wrong in two of the three places it appeared.
 *
 * <p>These read the Java and the migration, so a fourth value — or a renamed one — fails here rather
 * than rendering as a blank badge on a governance record.
 */

const BACKEND = join(__dirname, '..', '..', '..', 'tss-grc-backend', 'src', 'main');
const STATUS_JAVA = join(BACKEND, 'java', 'com', 'tss', 'grc', 'module', 'change_mgmt',
                         'service', 'ChangeConditionStatus.java');
const CONTROLLER = join(BACKEND, 'java', 'com', 'tss', 'grc', 'module', 'change_mgmt',
                        'controller', 'ChangeManagementController.java');
const SERVICE = join(BACKEND, 'java', 'com', 'tss', 'grc', 'module', 'change_mgmt',
                     'service', 'ChangeManagementService.java');
const CARD = join(__dirname, '..', 'pages', 'change', 'ChangeConditionsCard.tsx');

/** `public static final String X = "VALUE";` → the set of published values. */
function backendStatuses(): string[] {
  const java = readFileSync(STATUS_JAVA, 'utf8');
  const out: string[] = [];
  const re = /public static final String \w+\s*=\s*"([A-Z_]+)"/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(java)) !== null) out.push(m[1]);
  return out;
}

describe('S7e/CE-5 — the conditions register vocabulary', () => {
  it('every backend status has a label in both languages', () => {
    const statuses = backendStatuses();
    expect(statuses, 'ChangeConditionStatus publishes no values — the scan is broken')
      .toEqual(expect.arrayContaining(['OPEN', 'SATISFIED', 'WAIVED']));
    expect(statuses).toHaveLength(3);
    for (const s of statuses) {
      for (const [lang, dict] of [['en', en], ['ar', ar]] as const) {
        const key = 'changeCondStatus_' + s;
        expect((dict as Record<string, string>)[key], `${key} missing from ${lang}`).toBeTruthy();
      }
    }
  });

  it('the card renders SATISFIED and WAIVED differently — a waiver is not a green tick', () => {
    // Collapsing them would make a governance breach indistinguishable from compliance. The backend
    // keeps them distinct in ck_cc_status; the UI must not undo that with one shared tone.
    const card = readFileSync(CARD, 'utf8');
    const tone = card.slice(card.indexOf('const TONE'), card.indexOf('};', card.indexOf('const TONE')));
    const satisfied = /SATISFIED:\s*'(\w+)'/.exec(tone);
    const waived = /WAIVED:\s*'(\w+)'/.exec(tone);
    expect(satisfied).not.toBeNull();
    expect(waived).not.toBeNull();
    expect(waived![1]).not.toBe(satisfied![1]);
    expect(waived![1]).toBe('danger');
  });

  it('the states that show the register are all states the server can actually be in', () => {
    // A card shown on a status the state machine cannot reach is dead UI; one missing a status the
    // register matters in hides a blocker. Read the real transition table.
    const java = readFileSync(SERVICE, 'utf8');
    const reachable = new Set<string>();
    const re = /Map\.entry\(\s*"(\w+)"\s*,\s*Set\.(?:<String>)?of\(([^)]*)\)\s*\)/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(java)) !== null) {
      reachable.add(m[1]);
      for (const t of m[2].split(',')) {
        const v = t.trim().replace(/"/g, '');
        if (v) reachable.add(v);
      }
    }
    expect(reachable.size, 'could not parse NORMAL_TRANSITIONS').toBeGreaterThan(10);
    for (const s of CONDITION_STATES) {
      expect(reachable.has(s), `${s} is not a reachable change status`).toBe(true);
    }
    // The register must be visible on the state the gate blocks, or the user meets the 409 with no
    // idea what produced it.
    expect(CONDITION_STATES).toContain('SCHEDULED');
    expect(CONDITION_STATES).toContain('CAB_APPROVED');
  });

  it('the client calls the endpoints the controller actually declares', () => {
    const controller = readFileSync(CONTROLLER, 'utf8');
    const api = readFileSync(join(__dirname, '..', 'lib', 'api.ts'), 'utf8');
    const block = api.slice(api.indexOf('conditions:      (id: number)'), api.indexOf('comments:    (id'));
    for (const path of ['/conditions', '/conditions/open', '/satisfy', '/waive']) {
      expect(block, `the client does not call ${path}`).toContain(path);
    }
    // And the guard split the endpoints encode: imposing/waiving are APPROVE, satisfying is WRITE.
    // Asserted server-side by ChangeConditionRegisterTest; asserted here because the FE labels the
    // two actions differently and a swap would mislabel a privilege.
    // Read FORWARD from the mapping: @PostMapping precedes @PreAuthorize, so a window taken
    // backwards lands in the previous handler's annotations. The first draft of this assertion did
    // exactly that and reported addCondition's guard as satisfy's.
    const guardAfter = (mapping: string) => {
      const at = controller.indexOf('@PostMapping("' + mapping + '")');
      expect(at, `no @PostMapping for ${mapping}`).toBeGreaterThan(-1);
      return controller.slice(at, at + 260);
    };
    expect(guardAfter('/conditions/{conditionId}/satisfy')).toContain("hasPermission('CHANGE','WRITE')");
    expect(guardAfter('/conditions/{conditionId}/waive')).toContain("hasPermission('CHANGE','APPROVE')");
    expect(guardAfter('/{id}/conditions')).toContain("hasPermission('CHANGE','APPROVE')");
  });

  it('the blocking badge and the gate hint both exist in both languages', () => {
    for (const [lang, dict] of [['en', en], ['ar', ar]] as const) {
      const d = dict as Record<string, string>;
      expect(d.changeCondBlocking, `changeCondBlocking missing from ${lang}`).toContain('{n}');
      expect(d.changeCondGateHint, `changeCondGateHint missing from ${lang}`).toBeTruthy();
      expect(d.changeCondWaiveReason, `changeCondWaiveReason missing from ${lang}`).toBeTruthy();
    }
    expect((ar as Record<string, string>).changeCondGateHint).toMatch(/[؀-ۿ]/);
    expect((ar as Record<string, string>).changeCondTitle)
      .not.toBe((en as Record<string, string>).changeCondTitle);
  });
});

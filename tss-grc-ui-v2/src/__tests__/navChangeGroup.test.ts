import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * CE-0 — Change Enablement is its own nav group, and the blueprint has to agree.
 *
 * <p>The nav change itself is one line moved. What needs a test is the thing that would undo it
 * silently: `UX_REDESIGN_BLUEPRINT.md` filed `change_mgmt` inside workspace 12, "Governance &
 * Administration", and Wave 4 **generates** navigation from that registry. So a correct sidebar plus
 * a stale blueprint means the next generator run deletes this group and re-files Change under an
 * admin catch-all — reversing the owner's direction with no code change to notice. A document and a
 * component disagreeing is not a documentation problem when one of them is the source of the other.
 *
 * <p>These are source-structure assertions rather than a render test on purpose: what CE-0 changed is
 * where Change *lives*, and that lives in the shape of two files. The highlight behaviour of the new
 * query-tab row is verified in the browser, where NavLink's own matching actually runs.
 */

const SRC = join(__dirname, '..');
const sidebar = readFileSync(join(SRC, 'components', 'layout', 'Sidebar.tsx'), 'utf8');
const blueprint = readFileSync(
  join(SRC, '..', '..', 'TSS-GRC-Platform-Bible', 'UX', 'UX_REDESIGN_BLUEPRINT.md'), 'utf8');

/** The `items: [...]` block of one NAV_GROUPS entry, by group id. */
function groupBody(id: string): string {
  const start = sidebar.indexOf(`id: '${id}', titleKey:`);
  expect(start, `nav group '${id}' not found — did the NAV_GROUPS shape change?`)
    .toBeGreaterThan(-1);
  // Up to the next group header. `titleKey` appears on groups only — items carry `labelKey` — so the
  // next occurrence is the boundary. (Walking back to the preceding `id: ` would land on the LAST
  // item of this group instead, which silently truncated the body and failed this test's first run.)
  const next = sidebar.indexOf('titleKey:', start + 'titleKey:'.length + 20);
  return sidebar.slice(start, next === -1 ? sidebar.length : next);
}

describe('CE-0 — Change Enablement is its own workspace', () => {
  it('has its own nav group', () => {
    expect(sidebar).toContain("id: 'change', titleKey: 'navChange'");
  });

  it('is no longer inside the Risk Management group', () => {
    const risk = groupBody('risk');
    expect(risk.includes("to: '/changes'"), 'the change register is back under Risk Management')
      .toBe(false);
    // The risk group must otherwise still be intact — a test that passes because the group vanished
    // would be worse than useless.
    expect(risk).toContain("to: '/risk'");
    expect(risk).toContain("to: '/risk-lifecycle'");
  });

  it('links the register and the calendar/freeze windows, and nothing that does not exist yet', () => {
    const change = groupBody('change');
    expect(change).toContain("to: '/changes'");
    expect(change).toContain("to: '/changes?tab=governance'");

    // CE-0's scope note: the CAB queue and decision register are S7/CE-13 and have no route. Linking
    // them now would be nav-to-nowhere — the F2-02 defect two slices of this programme just removed.
    for (const notYet of ['/changes/cab', '/cab', '/changes/decisions', '/change-decisions']) {
      expect(change.includes(`to: '${notYet}'`), `${notYet} has no route yet — nav-to-nowhere`)
        .toBe(false);
    }
  });

  it('the UX blueprint agrees — change_mgmt is not filed under Governance & Administration', () => {
    const row12 = blueprint.split('\n').find(l => l.includes('**Governance & Administration**'));
    expect(row12, 'blueprint workspace table changed shape').toBeTruthy();
    expect(row12!.includes('change_mgmt'),
      'The blueprint still files change_mgmt under Governance & Administration. Wave 4 generates the '
      + 'sidebar from this table, so leaving it here deletes the Change group and re-files Change '
      + 'under an admin catch-all.')
      .toBe(false);

    const own = blueprint.split('\n').find(l => l.includes('**Change Enablement**'));
    expect(own, 'the blueprint has no Change Enablement workspace row').toBeTruthy();
    expect(own!).toContain('change_mgmt');
  });
});

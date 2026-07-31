import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { I18nProvider } from '../lib/i18n';
import { ToastProvider } from '../lib/toast';
import { governanceApi, type GovMatrixDto, type GovRoleDto } from '../lib/api';
import {
  MatrixTab, matrixColumns, isActionEditable, ACTION_ORDER,
} from '../pages/platform/AccessGovernancePage';

/**
 * F2-63 — the permission matrix must display every action class the platform has.
 *
 * <p>The defect: `AccessGovernancePage` **declared** its columns —
 * `const ACTIONS = ['READ','WRITE','APPROVE','DELETE','TRIAGE']` — while the API returns
 * cells spanning **seven** actions. `EXPORT` (26 modules) and `BREAK_GLASS` (1) had no column,
 * so for role `CISO` the screen showed 26 of 40 grants and silently omitted **14 (35%)**: 13
 * `*:EXPORT` and 1 `IAM:BREAK_GLASS`. Nothing failed, because nothing asked. An administrator
 * opening the screen to answer *"which roles can extract our registers as a file?"* got no
 * answer — the column did not exist — on the one screen that presents itself as the
 * authoritative "who can do what" view.
 *
 * <h3>Why a declared column list is the wrong shape</h3>
 * The fix is structural, not a sixth and seventh string: {@link matrixColumns} **derives** the
 * columns from the response, so a catalogued action is displayed on the day it is catalogued.
 * These tests pin the property rather than the list — an action nobody has classified is
 * appended and rendered read-only (fail-closed), never dropped.
 *
 * <h3>The authority these tests measure against</h3>
 * The catalogue lives in `iam.permissions`, which a unit test cannot and should not reach. So
 * the fixture is generated from the set of action literals the backend actually **authorises
 * with** — every `hasPermission('MODULE','ACTION')` in `tss-grc-backend`. That set is the real
 * bar: the matrix must be able to show every action a guard can demand.
 *
 * <p>⚠ The first version of {@link GUARD_RE} was `hasPermission\('[A-Z_]+','([A-Z_]+)'\)` with no
 * `\s*`, and it found **six** actions — `hasPermission('INTAKE', 'TRIAGE')` carries a space, so
 * the scan would have reported that the platform has no TRIAGE and this test would have passed
 * while agreeing with the defect. Same lesson as `routeReachability.test.ts`: **a scan that
 * matches too little does not merely under-report, it certifies the bug.**
 */

// ── the measured authority: action literals the backend authorises with ──────

const BACKEND_JAVA = join(__dirname, '..', '..', '..', 'tss-grc-backend', 'src', 'main', 'java');

/** Both spacings. The missing `\s*` is what made the first draft of this scan agree with the bug. */
const GUARD_RE = /hasPermission\(\s*'[A-Z_]+'\s*,\s*'([A-Z_]+)'\s*\)/g;

function walkJava(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) walkJava(full, out);
    else if (name.endsWith('.java')) out.push(full);
  }
  return out;
}

function scanGuards(): { actions: Set<string>; sites: number; files: number } {
  const files = walkJava(BACKEND_JAVA);
  const actions = new Set<string>();
  let sites = 0;
  for (const f of files) {
    for (const m of readFileSync(f, 'utf8').matchAll(GUARD_RE)) { actions.add(m[1]); sites++; }
  }
  return { actions, sites, files: files.length };
}

/**
 * Measured live against the dev catalogue on 2026-07-31: `SELECT DISTINCT action FROM
 * iam.permissions` returns exactly these seven, in both `TSS_GRC` and `TSS_GRC_Deployment`.
 * Pinned so a scan that quietly stops finding one of them fails instead of shrinking.
 */
const CATALOGUE_ACTIONS_2026_07_31 = [
  'APPROVE', 'BREAK_GLASS', 'DELETE', 'EXPORT', 'READ', 'TRIAGE', 'WRITE',
];

// ── fixtures ────────────────────────────────────────────────────────────────

/** A tenant-owned role: `organisationId != null` is what makes the matrix editable at all. */
const TENANT_ROLE: GovRoleDto = {
  id: 900, roleName: 'REVIEW_TEST_ROLE', displayNameEn: 'Review test', displayNameAr: 'دور اختبار',
  systemRole: false, active: true, organisationId: 1,
};

/** One module per action plus a shared module, so every action has at least one live cell. */
function matrixOf(actions: string[], granted: string[] = []): GovMatrixDto {
  const modules = ['RISK', 'COMPLIANCE', 'IAM', 'INTAKE'];
  const cells = modules.flatMap(module => actions.map(action => ({
    module, action, permissionName: `${module}_${action}`,
    granted: granted.includes(`${module}/${action}`),
  })));
  return { roleId: TENANT_ROLE.id, roleName: TENANT_ROLE.roleName, systemRole: false, cells };
}

function renderMatrix() {
  return render(
    <I18nProvider>
      <ToastProvider>
        <MatrixTab roles={[TENANT_ROLE]} />
      </ToastProvider>
    </I18nProvider>,
  );
}

/** Pick the role from the selector — the matrix loads on selection, as it does for a user. */
async function selectRole() {
  const selects = await screen.findAllByRole('combobox');
  fireEvent.change(selects[0], { target: { value: String(TENANT_ROLE.id) } });
  return screen.findByTestId('matrix-table');
}

function renderedColumns(table: HTMLElement): string[] {
  return [...table.querySelectorAll('thead th[data-action]')]
    .map(th => th.getAttribute('data-action')!);
}

beforeEach(() => vi.restoreAllMocks());

// ── the scan is honest ──────────────────────────────────────────────────────

describe('F2-63 — the action authority is measured, not assumed', () => {
  it('the guard scan finds the backend and is not vacuous', () => {
    expect(existsSync(BACKEND_JAVA),
      `Backend sources not found at ${BACKEND_JAVA} — this test needs repointing`).toBe(true);
    const { actions, sites, files } = scanGuards();
    // Floors, not exact counts: guards are added constantly. A scan that matched nothing, or
    // one file, would otherwise make every assertion below true for the wrong reason.
    expect(files).toBeGreaterThan(1000);
    expect(sites).toBeGreaterThan(900);
    expect(actions.size).toBeGreaterThanOrEqual(7);
  });

  it('every action measured in the catalogue on 2026-07-31 is still authorised with', () => {
    const { actions } = scanGuards();
    const missing = CATALOGUE_ACTIONS_2026_07_31.filter(a => !actions.has(a));
    expect(missing,
      'These actions are catalogued but no `hasPermission()` guard demands them any more. Either '
      + 'a guard was lost, or the action is dead and should leave the catalogue — F2-13b\'s '
      + '"catalogued permission guarding no endpoint" anti-pattern:\n  ' + missing.join('\n  '))
      .toEqual([]);
  });
});

// ── the ratchet: no action may be nameless ──────────────────────────────────

describe('F2-63 — every authorised action has a column position and a policy', () => {
  it('no action the backend authorises with is unknown to the matrix', () => {
    const { actions } = scanGuards();
    const unplaced = [...actions].filter(a => !ACTION_ORDER.includes(a)).sort();
    expect(unplaced,
      'These actions guard live endpoints but have no entry in ACTION_POLICY, so the matrix will '
      + 'append them read-only rather than place them deliberately. Give each one a column '
      + 'position, bilingual label, and an explicit editable/disclosed-only decision:\n  '
      + unplaced.join('\n  ')).toEqual([]);
  });

  it('every placed action resolves an editability decision', () => {
    // isActionEditable() fails closed, so "unclassified" and "deliberately read-only" would look
    // identical from outside. This asserts the classification is total over ACTION_ORDER.
    for (const a of ACTION_ORDER) {
      expect(typeof isActionEditable(a), `${a} has no editability decision`).toBe('boolean');
    }
    // ...and that it is not uniformly one value, which would make the read-only tests vacuous.
    expect(ACTION_ORDER.some(isActionEditable)).toBe(true);
    expect(ACTION_ORDER.some(a => !isActionEditable(a))).toBe(true);
  });
});

// ── the floor: the rendered column set equals the catalogue's actions ───────

describe('F2-63 — no catalogued action renders invisible', () => {
  it('the rendered column set equals the distinct actions in the response', async () => {
    const actions = [...scanGuards().actions].sort();
    vi.spyOn(governanceApi, 'matrix').mockResolvedValue(matrixOf(actions));

    renderMatrix();
    const table = await selectRole();

    // Set equality, in both directions — this is the assertion the defect would have failed:
    // with the old declared list it rendered 5 of 7.
    expect([...renderedColumns(table)].sort()).toEqual(actions);
    for (const a of CATALOGUE_ACTIONS_2026_07_31) {
      expect(renderedColumns(table), `${a} has no column`).toContain(a);
    }
  });

  it('an action nobody has classified is still shown — appended, read-only, never dropped', async () => {
    // The regression this guards is the original one: the next action to be catalogued must
    // appear without anyone editing this screen.
    const actions = [...scanGuards().actions, 'SIMULATE'];
    vi.spyOn(governanceApi, 'matrix').mockResolvedValue(matrixOf(actions));

    renderMatrix();
    const table = await selectRole();

    const cols = renderedColumns(table);
    expect(cols).toContain('SIMULATE');
    expect(cols[cols.length - 1]).toBe('SIMULATE');           // appended after the known order
    expect(screen.getByLabelText('RISK/SIMULATE')).toBeDisabled();  // fails closed
  });

  it('known actions keep a legible order rather than the response order', () => {
    // Alphabetical would put BREAK_GLASS second and READ fifth; the grid is read left-to-right
    // as an escalation of authority, so the order is declared even though the set is derived.
    const cols = matrixColumns(
      ['EXPORT', 'READ', 'BREAK_GLASS', 'WRITE'].map(action => ({ action })),
    );
    expect(cols).toEqual(['READ', 'WRITE', 'EXPORT', 'BREAK_GLASS']);
  });
});

// ── the decision: disclosed ≠ grantable, and disclosed ≠ revoked ────────────

describe('F2-63 — the two governed action classes are disclosed but not grantable here', () => {
  it('EXPORT and BREAK_GLASS are read-only while ordinary actions on the same role are not', async () => {
    const actions = [...scanGuards().actions].sort();
    vi.spyOn(governanceApi, 'matrix').mockResolvedValue(matrixOf(actions));

    renderMatrix();
    await selectRole();

    // The anti-vacuity leg, and it matters: the role is tenant-owned, so if the policy were not
    // being applied these would ALL be enabled. Every global role is read-only wholesale, which
    // would make a disabled EXPORT prove nothing.
    expect(screen.getByLabelText('RISK/READ')).toBeEnabled();
    expect(screen.getByLabelText('RISK/WRITE')).toBeEnabled();

    expect(screen.getByLabelText('RISK/EXPORT')).toBeDisabled();
    expect(screen.getByLabelText('IAM/BREAK_GLASS')).toBeDisabled();

    // Read-only with a stated reason, not a silently inert checkbox.
    expect(screen.getByTestId('readonly-note-EXPORT')).toBeInTheDocument();
    expect(screen.getByTestId('readonly-note-BREAK_GLASS')).toBeInTheDocument();
  });

  it('save round-trips the read-only grants instead of revoking them', async () => {
    // The whole-set-replacement PUT is why this needs a test: a column the UI cannot edit must
    // still be sent back, or "not editable here" would silently mean "revoked on next save".
    const actions = [...scanGuards().actions].sort();
    const held = ['RISK/EXPORT', 'IAM/BREAK_GLASS', 'RISK/READ'];
    const loaded = matrixOf(actions, held);
    vi.spyOn(governanceApi, 'matrix').mockResolvedValue(loaded);
    const save = vi.spyOn(governanceApi, 'saveMatrix').mockResolvedValue(loaded);

    renderMatrix();
    await selectRole();
    fireEvent.click(screen.getByRole('button', { name: /حفظ|Save/ }));

    await waitFor(() => expect(save).toHaveBeenCalled());
    const sent = save.mock.calls[0][1];
    expect(sent).toEqual(expect.arrayContaining([
      { module: 'RISK', action: 'EXPORT' },
      { module: 'IAM', action: 'BREAK_GLASS' },
      { module: 'RISK', action: 'READ' },
    ]));
    expect(sent).toHaveLength(held.length);
  });
});

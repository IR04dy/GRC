/**
 * F2-203 — the internal-audit finding register must be server-backed, and there must be
 * exactly one copy of its lifecycle in the frontend.
 *
 * What this slice closed, and therefore what these legs are defending:
 *
 *   • `saveFinding` / `transitionFinding` wrote a module-level array and returned. An
 *     auditor closing a finding under IIA Standard 2500 saw it close, got a success toast,
 *     and a reload put it back — while `POST /api/audit-lc/findings/{id}/transition` had
 *     existed, guarded and event-emitting, the whole time.
 *   • The engagement picker was built from `AUDIT_ENGAGEMENTS`, a demoRows() corpus for a
 *     DIFFERENT register, whose codes appear in zero rows of the registers it filtered — so
 *     three tabs were empty for all eight selectable engagements.
 *   • Four `SEED_*` corpora stood in whenever a register returned empty, one of them naming
 *     an invented individual as the Audit Committee chair who approved the annual plan.
 *   • The lifecycle existed in FOUR places: the server's `ALLOWED`, an `onAdvance` lookup
 *     map, four hard-coded `if (f.status === …)` buttons, and a `transitionFinding` that
 *     accepted any target at all.
 *
 * ⚠ The load-bearing leg here is BEHAVIOURAL, not structural:
 * `noDecisionIsWrittenIntoLocalState`. A rewire that kept a local status write would look
 * identical on screen and be the same defect — that is F2-201's lesson, and it is the leg a
 * regression is most likely to trip.
 */
import { describe, it, expect } from 'vitest';
import { readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import {
  FINDING_ALLOWED, FINDING_MANAGED_STATUSES, FINDING_TERMINAL,
  FINDING_UNMANAGED_STATUSES, FINDING_STATUS_LABEL,
  nextFindingStatuses, isFindingStatusManaged, isFindingOpen, findingStatusLabel,
  PLAN_ALLOWED, PLAN_UNMANAGED_STATUSES, PLAN_STATUS_LABEL, PLAN_STATUS_TONE,
  nextPlanStatuses, isPlanStatusManaged, planStatusLabel,
  WP_ALLOWED, WP_UNMANAGED_STATUSES, WP_STATUS_LABEL, WP_STATUS_TONE,
  nextWorkingPaperStatuses, isWorkingPaperStatusManaged, workingPaperStatusLabel,
  CAPA_ALLOWED, CAPA_UNMANAGED_STATUSES, CAPA_STATUS_LABEL, CAPA_STATUS_TONE,
  nextCapaStatuses, isCapaStatusManaged, capaStatusLabel,
} from '../lib/auditLifecycle';

const SRC = join(__dirname, '..');
const LIB = join(SRC, 'lib', 'auditLifecycle.ts');
const PAGE = join(SRC, 'pages', 'audit-lifecycle', 'AuditLifecyclePage.tsx');
const TABS = join(SRC, 'pages', 'audit-lifecycle', 'AuditLifecycleTabs.tsx');

/**
 * Strip line and block comments in ONE left-to-right alternation.
 *
 * ⚠⚠ The order is load-bearing and getting it wrong silently SHRINKS the population, which
 * reads exactly like "the code is fine". Written the obvious way — block pattern first, then
 * line — a `//` prose comment containing a literal `/*` opens a block that runs to the next
 * closing delimiter; F2-198 lost 45% of `App.tsx` that way and reported 24 server-less pages
 * instead of 74. At a `//` an inner `/*` can never open a block, and at a `/*` a URL cannot
 * truncate it. `(?<!:)` keeps `https://` intact.
 */
function stripComments(src: string): string {
  return src.replace(/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g, '');
}

const libSrc = () => readFileSync(LIB, 'utf8');
const libCode = () => stripComments(libSrc());
const pageCode = () => stripComments(readFileSync(PAGE, 'utf8'));
const tabsCode = () => stripComments(readFileSync(TABS, 'utf8'));

describe('F2-203 — the finding register writes to the server', () => {
  it('createFinding and transitionFinding both POST to the real endpoints', () => {
    const code = libCode();
    // Assert the CALL SHAPE, not a substring. F2-194 had two assertions pass on a type name
    // and on an import statement respectively, so `toContain('getCcmDashboard')` was
    // satisfied by code that never called it.
    expect(code, 'createFinding must POST to /api/audit-lc/findings')
      .toMatch(/api\.post<[^>]*>\(\s*'\/api\/audit-lc\/findings'/);
    expect(code, 'transitionFinding must POST to /{id}/transition')
      .toMatch(/api\.post<[^>]*>\(\s*\n?\s*`\/api\/audit-lc\/findings\/\$\{encodeURIComponent\(id\)\}\/transition`/);
  });

  it('both replace the cached row FROM the server response', () => {
    const code = libCode();
    // `fromBackendFinding(await api.post(...))` — the row is the server's, and the local
    // array is only a cache of it.
    expect(code).toMatch(/const saved = fromBackendFinding\(await api\.post/g);
    expect((code.match(/fromBackendFinding\(await api\.post/g) ?? []).length,
      'both write paths must take the row back from the server').toBe(2);
  });

  it('no decision is written into local state', () => {
    // ⚠ THE LOAD-BEARING LEG. The retired code was
    //   `const updated = { ...f, status }; _findings = _findings.map(...)`
    // — a status the client chose, assigned locally, with nothing sent. A rewire that kept
    // that line would render identically and be the same defect.
    const code = libCode();
    expect(code, 'a finding status must never be computed client-side')
      .not.toMatch(/\{\s*\.\.\.\s*f\s*,\s*status\s*\}/);
    expect(code, 'the cache may only be assigned a row the server returned')
      .not.toMatch(/_findings\s*=\s*_findings\.map\([^)]*\?\s*updated\s*:/);
  });

  it('the four SEED_* corpora and their clock minters are gone', () => {
    const code = libCode();
    for (const seed of ['SEED_PLAN', 'SEED_WPS', 'SEED_FINDINGS', 'SEED_CAPAS']) {
      expect(code, `${seed} must not exist — an empty register reads as empty`)
        .not.toContain(seed);
    }
    // `const _now = Date.now()` at module load is what made the fabrication un-stale-able
    // (F2-194: a clock-anchored fabrication has no tell).
    expect(code).not.toMatch(/const _now\s*=\s*Date\.now\(\)/);
    expect(code).not.toMatch(/_daysAgo|_daysAhead/);
  });

  it('an unreachable register is distinguishable from an empty one', () => {
    expect(libCode()).toMatch(/export function auditLifecycleLoadFailed\(\)/);
    // The page must actually branch on it, not merely import it.
    expect(pageCode()).toMatch(/loadFailed\s*&&/);
  });

  it('the mock engagement corpus is not imported at all', () => {
    // F2-194's boundary is a FILENAME, so this asserts the specific import rather than
    // relying on `demoFallbackContract`'s /mock/i ratchet to notice.
    for (const [name, code] of [['lib', libSrc()], ['page', readFileSync(PAGE, 'utf8')],
                                ['tabs', readFileSync(TABS, 'utf8')]] as const) {
      expect(stripComments(code), `${name} must not import mockCompliance`)
        .not.toMatch(/from\s+'[^']*mockCompliance'/);
    }
  });

  it('the picker offers the codes the registers reference', () => {
    expect(libCode()).toMatch(/export function registerEngagementCodes\(\)/);
    expect(pageCode(), 'the page must build its picker from the register, not a catalogue')
      .toMatch(/registerEngagementCodes\s*\(/);
  });

  it('no invented identity is passed on any write', () => {
    const all = libCode() + pageCode() + tabsCode();
    // The three names the page used to send. The server stamps the principal for all three
    // roles now, so no client-side identity is needed anywhere on this screen.
    for (const invented of ['Khalid Al-Falih', 'Sara Al-Zahrani', 'Faisal Al-Otaibi']) {
      expect(all, `${invented} is not a real user of any tenant`).not.toContain(invented);
    }
    expect(all, "the literal 'Owner role' was being stored as a finding's owner")
      .not.toMatch(/'Owner role'/);
  });
});

describe('F2-203 — one lifecycle, consulted', () => {
  it('the terminal set is DERIVED from the machine, not typed twice', () => {
    // F2-196's rule. A second literal list is how EXTENDED came to be excluded from the
    // PDPL statutory breach sweep.
    expect(libCode(), 'derived through the ONE shared helper, not an inline filter per register')
      .toMatch(/FINDING_TERMINAL[^=]*=\s*\n?\s*terminalOf\(FINDING_ALLOWED\)/);
    expect(FINDING_TERMINAL).toEqual(['CLOSED']);
  });

  it('"open" is the complement of terminal', () => {
    expect(isFindingOpen('CLOSED')).toBe(false);
    for (const s of FINDING_MANAGED_STATUSES.filter(x => x !== 'CLOSED')) {
      expect(isFindingOpen(s), `${s} is not terminal, so it is open`).toBe(true);
    }
    // Deliberate: an unmanaged status counts as open. Nothing declares VERIFIED terminal, so
    // treating it as closed would be the platform blessing a synonym it has not adopted.
    for (const s of FINDING_UNMANAGED_STATUSES) expect(isFindingOpen(s)).toBe(true);
    // And a token from a future data fix — the column has no CHECK — is chased, not excused.
    expect(isFindingOpen('SOMETHING_NOBODY_DECLARED')).toBe(true);
  });

  it('nextFindingStatuses is empty for terminal AND for unmanaged rows', () => {
    expect(nextFindingStatuses('CLOSED')).toEqual([]);
    for (const s of FINDING_UNMANAGED_STATUSES) {
      expect(nextFindingStatuses(s), `${s} has no declared edge on the server either`).toEqual([]);
    }
    expect(nextFindingStatuses('OPEN')).toEqual(['MGMT_RESPONSE']);
    // Not a crash on an unheard-of token.
    expect(nextFindingStatuses('WHATEVER')).toEqual([]);
  });

  it('the page derives its buttons from the table instead of restating it', () => {
    const page = pageCode() + tabsCode();
    expect(page, 'advance buttons must come from nextFindingStatuses')
      .toMatch(/nextFindingStatuses\(f\.status\)\.map/);
    // The three restatements this slice deleted. Each of them could go stale against the
    // server independently, and one of them offered OPEN → CLOSED, which the server refuses.
    expect(page).not.toMatch(/f\.status === 'MGMT_RESPONSE' &&/);
    expect(page).not.toMatch(/f\.status === 'AWAITING_VERIFICATION' &&/);
    expect(page, 'the onAdvance lookup map was a fourth copy of the machine')
      .not.toMatch(/OPEN:\s*'MGMT_RESPONSE'/);
  });

  it('every managed status has an advance label key', () => {
    // Keyed on the TARGET, so adding an edge to FINDING_ALLOWED surfaces as a missing key
    // rather than as a silently absent button.
    const tabs = tabsCode();
    const block = tabs.match(/FINDING_ADVANCE_LABEL_KEY[^=]*=\s*\{([^}]*)\}/s);
    expect(block, 'the label map must exist in the tab views').not.toBeNull();
    for (const s of FINDING_MANAGED_STATUSES) {
      expect(block![1], `${s} has no advance label`).toContain(`${s}:`);
    }
  });
});

describe('F2-203 — the undeclared statuses are disclosed, not mapped away', () => {
  it('all three are labelled in both languages', () => {
    for (const s of FINDING_UNMANAGED_STATUSES) {
      expect(FINDING_STATUS_LABEL[s], `${s} must be labelled or the row crashes`).toBeDefined();
      expect(FINDING_STATUS_LABEL[s].en.length).toBeGreaterThan(0);
      expect(FINDING_STATUS_LABEL[s].ar.length).toBeGreaterThan(0);
      // Arabic, not an English fallback (house bilingual rule).
      expect(FINDING_STATUS_LABEL[s].ar).toMatch(/[؀-ۿ]/);
    }
  });

  it('the label accessor cannot throw on a status this build has never heard of', () => {
    // `FINDING_STATUS_LABEL[f.status][lang]` was indexed directly, which is a TypeError for
    // 7 of the 20 real rows. It never fired only because the broken picker filtered every
    // row away first — the empty tab was hiding a crash.
    expect(() => findingStatusLabel('WHATEVER_ARRIVES', 'en')).not.toThrow();
    expect(findingStatusLabel('WHATEVER_ARRIVES', 'en')).toBe('WHATEVER_ARRIVES');
    expect(findingStatusLabel('OPEN', 'ar')).toBe(FINDING_STATUS_LABEL.OPEN.ar);
    expect(pageCode() + tabsCode(), 'no direct indexing of the label map')
      .not.toMatch(/FINDING_STATUS_LABEL\[[^\]]+\]\[/);
  });

  it('none of the three is mapped onto a managed status', () => {
    // Collapsing IN_PROGRESS→IN_REMEDIATION, REMEDIATED→AWAITING_VERIFICATION,
    // VERIFIED→CLOSED would rewrite 7 live rows in two databases on the next transition.
    // That is an owner decision (F2-183), so the disagreement is displayed, not resolved.
    for (const s of FINDING_UNMANAGED_STATUSES) {
      expect(isFindingStatusManaged(s)).toBe(false);
      expect(Object.keys(FINDING_ALLOWED)).not.toContain(s);
    }
    // ⚠⚠ These two assertions used to match the WHOLE FILE, and fired the moment the sibling
    // machines were declared, because `CAPA_ALLOWED` legitimately has an `IN_PROGRESS:` edge.
    // The claim was only ever about the FINDING table. 🔑 A scan not scoped to the shape of its
    // claim invents findings — F2-192's own headline, committed here in the gate written for
    // F2-203, by me, a few screens below where this file quotes the rule. Scoped to the
    // declaring block now; the structural `it.each` leg below checks the same property from the
    // parsed table rather than from text, which is the check that cannot make this mistake.
    const findingBlock = libCode().match(/FINDING_ALLOWED[^=]*=\s*\{([\s\S]*?)\n\};/)?.[1];
    expect(findingBlock, 'the parse must find the block, not silently return nothing').toBeTruthy();
    for (const s of FINDING_UNMANAGED_STATUSES) {
      expect(findingBlock!, `${s} must not be given an edge in the FINDING machine`)
        .not.toMatch(new RegExp(`\\b${s}\\s*:`));
    }
  });

  it('the row says so on screen', () => {
    const tabs = tabsCode();
    expect(tabs).toMatch(/!isFindingStatusManaged\(f\.status\)/);
    expect(tabs, 'the disclosure must be announced, not just coloured').toMatch(/role="status"/);
  });
});

describe('F2-203 — the AC packet is an export and must not carry a fabrication', () => {
  it('the engagement section is measured from the registers', () => {
    const code = libCode();
    expect(code).toMatch(/function engagementProgressRows\(\)/);
    // Both emitters — the text packet and the branded XLSX/DOCX sheets — must use it.
    expect((code.match(/engagementProgressRows\(\)/g) ?? []).length,
      'declared once, used by both the packet and the sheets').toBeGreaterThanOrEqual(3);
  });

  it('a missing annual plan is stated, not substituted', () => {
    const code = libCode();
    // F2-197: an absence must read as unmeasured, never as an all-clear. The old code could
    // not reach this branch — SEED_PLAN supplied an approved plan with an invented approver.
    expect(code).toMatch(/No annual audit plan is recorded/);
    expect(code).toMatch(/لا توجد خطة تدقيق سنوية/);
  });

  it('the severity tally cannot put NaN into a committee document', () => {
    const code = libCode();
    expect(code).toMatch(/function tallySeverities\(/);
    expect(code, 'the ++ on a Record keyed by an undeclared token yields NaN')
      .not.toMatch(/sevCounts\[f\.severity\]\+\+/);
    expect(code).not.toMatch(/sev\[f\.severity\]\+\+/);
  });

  it('overdue is computed from the derived terminal set in both emitters', () => {
    const code = libCode();
    expect(code).toMatch(/function countOverdueFindings\(/);
    expect((code.match(/countOverdueFindings\(findings\)/g) ?? []).length).toBe(2);
    expect(code, "a second `status !== 'CLOSED'` literal is a restatement of the machine")
      .not.toMatch(/f\.status !== 'CLOSED'/);
  });
});

describe('F2-203 — the KPI strip is server-derived', () => {
  it('no engagement figure comes from a corpus length', () => {
    const code = libCode();
    expect(code).not.toMatch(/MOCK_ENGAGEMENTS\.length/);
    expect(code).toMatch(/totalEngagements:\s*_engagements\.length/);
  });

  it('in-flight is the complement of a declared terminal status, not an inclusion list', () => {
    const code = libCode();
    // The inclusion list dropped ENGAGED, MGMT_RESPONSE, OPENING_MEETING and REPORTING —
    // 5 of 12 reported where 10 are not closed. Same shape as F2-196's DSR count.
    expect(code).not.toMatch(/status === 'PLANNED' \|\| e\.status === 'FIELDWORK'/);
    expect(code).toMatch(/ENGAGEMENT_TERMINAL/);
    expect(code).toMatch(/isEngagementInFlight/);
  });
});

describe('F2-203 — the detectors themselves', () => {
  it('the comment stripper handles both orders, pinned in both directions', () => {
    // The exact shape that cost F2-198 45% of a file.
    const trap = 'const a = 1; // see /* this note\nconst b = 2;\n/* real block */\nconst c = 3;';
    const out = stripComments(trap);
    expect(out, 'a /* inside a // comment must not open a block').toContain('const b = 2;');
    expect(out).toContain('const c = 3;');
    expect(out, 'a real block comment must still be stripped').not.toContain('real block');
    // And a URL must not be truncated at its //.
    expect(stripComments("const u = 'https://x/y'; // gone")).toContain('https://x/y');
  });

  it('the assertions are checked against the pre-fix source as a control', () => {
    // 🔑 A green corpus scan passes both when the app is clean and when the regex has
    // rotted. This runs the two sharpest detectors against a synthetic copy of the code
    // this slice deleted, so a rotted pattern fails HERE rather than passing silently.
    const preFix = `
      export function transitionFinding(id: string, status: FindingStatus): Finding | null {
        const f = loadFindings().find(x => x.id === id);
        if (!f) return null;
        const updated = { ...f, status };
        _findings = _findings.map(x => x.id === id ? updated : x);
        emit();
        return updated;
      }
      const SEED_FINDINGS: Finding[] = [];
      const _now = Date.now();
    `;
    expect(preFix, 'the local-status detector must fire on the retired code')
      .toMatch(/\{\s*\.\.\.\s*f\s*,\s*status\s*\}/);
    expect(preFix).toMatch(/_findings\s*=\s*_findings\.map\([^)]*\?\s*updated\s*:/);
    expect(preFix).toContain('SEED_FINDINGS');
    expect(preFix).toMatch(/const _now\s*=\s*Date\.now\(\)/);
  });

  it('the files these legs read all exist', () => {
    // A path typo makes readFileSync throw, which is loud — but a leg that greps a file for
    // an ABSENCE would pass on an empty string, so assert existence explicitly.
    for (const f of [LIB, PAGE, TABS]) expect(existsSync(f), `${f} must exist`).toBe(true);
    expect(libCode().length).toBeGreaterThan(5000);
    expect(tabsCode().length).toBeGreaterThan(2000);
  });
});

/**
 * ⚠⚠ ALL FOUR registers, not just findings.
 *
 * The slice started as "wire the finding writes". Rendering the fixed page crashed with
 * "Cannot read properties of undefined (reading 'ar')" on the PLAN tab, and measuring found
 * the same defect in every register of the module: `audit_lc` has zero CHECK constraints and
 * each table holds status values its own service's vocabulary does not declare —
 * **19 of 46 rows in dev AND prod, none of which can be advanced by any route.**
 *
 * 🔑 The crash was hidden by an accident in each case: for findings a broken engagement picker
 * filtered every row away; for the annual plan `plans[0]` happened to select the DRAFT row;
 * for CAPAs the "overdue" branch of a ternary won whenever the row was also past its target
 * date. **F2-203's own plan-selection fix is what exposed the second one** — which is why
 * these legs exist rather than a note saying findings were the only instance.
 */
describe('F2-203 — the same treatment in all four registers', () => {
  const REGISTERS = [
    { name: 'annual plan',   table: PLAN_ALLOWED,    unmanaged: PLAN_UNMANAGED_STATUSES,
      label: PLAN_STATUS_LABEL as Record<string, { en: string; ar: string }>,
      tone: PLAN_STATUS_TONE as Record<string, string>,
      next: nextPlanStatuses, managed: isPlanStatusManaged, labeller: planStatusLabel,
      rows: '1 of 2' },
    { name: 'working paper', table: WP_ALLOWED,      unmanaged: WP_UNMANAGED_STATUSES,
      label: WP_STATUS_LABEL as Record<string, { en: string; ar: string }>,
      tone: WP_STATUS_TONE as Record<string, string>,
      next: nextWorkingPaperStatuses, managed: isWorkingPaperStatusManaged,
      labeller: workingPaperStatusLabel, rows: '8 of 12' },
    { name: 'CAPA',          table: CAPA_ALLOWED,    unmanaged: CAPA_UNMANAGED_STATUSES,
      label: CAPA_STATUS_LABEL as Record<string, { en: string; ar: string }>,
      tone: CAPA_STATUS_TONE as Record<string, string>,
      next: nextCapaStatuses, managed: isCapaStatusManaged, labeller: capaStatusLabel,
      rows: '3 of 12' },
    { name: 'finding',       table: FINDING_ALLOWED, unmanaged: FINDING_UNMANAGED_STATUSES,
      label: FINDING_STATUS_LABEL as Record<string, { en: string; ar: string }>,
      tone: { CLOSED: 'success' } as Record<string, string>,   // findings tone by predicate
      next: nextFindingStatuses, managed: isFindingStatusManaged, labeller: findingStatusLabel,
      rows: '7 of 20' },
  ];

  it.each(REGISTERS)('$name: the undeclared statuses are labelled bilingually', (r) => {
    expect(r.unmanaged.length, `${r.rows} rows carry one of these`).toBeGreaterThan(0);
    for (const s of r.unmanaged) {
      expect(r.label[s], `${s} unlabelled — indexing the map on it is a TypeError`).toBeDefined();
      expect(r.label[s].en.length).toBeGreaterThan(0);
      expect(r.label[s].ar, `${s} needs a real Arabic label, not an English fallback`)
        .toMatch(/[؀-ۿ]/);
    }
  });

  it.each(REGISTERS)('$name: the accessor cannot throw on an unheard-of token', (r) => {
    expect(() => r.labeller('NOBODY_DECLARED_THIS', 'en')).not.toThrow();
    expect(r.labeller('NOBODY_DECLARED_THIS', 'ar')).toBe('NOBODY_DECLARED_THIS');
  });

  it.each(REGISTERS)('$name: the undeclared statuses offer no transition', (r) => {
    for (const s of r.unmanaged) {
      expect(r.managed(s), `${s} must not be in the declared table`).toBe(false);
      expect(r.next(s), `${s} has no edge on the server either — offering one would 409`)
        .toEqual([]);
    }
  });

  it.each(REGISTERS)('$name: none of them is folded into the declared vocabulary', (r) => {
    // Collapsing the synonyms (APPROVED≈AC_APPROVED, REVIEWED≈APPROVED, VERIFIED≈CLOSED)
    // rewrites 19 live rows in two databases on the next transition. Owner decision (F2-183).
    for (const s of r.unmanaged) expect(Object.keys(r.table)).not.toContain(s);
  });

  it('every register discloses an undeclared status, in its own file, announced', () => {
    // ⚠⚠ TWO mutations survived the first version of this leg, and both were the leg's fault:
    //
    //   • It counted `role="status"` occurrences across BOTH files with a `>= 4` floor. There
    //     are five disclosures, so deleting one still satisfied the floor. 🔑 A count with a
    //     floor cannot see one member disappear while another exists — F2-197's "a ceiling
    //     tolerates the population it measures", in the other direction.
    //   • It asserted each guard token appeared in `pageCode() + tabsCode()`. Deleting the
    //     plan disclosure from the PAGE still passed, because `PacketTab` in the TABS file
    //     also calls `isPlanStatusManaged`. 🔑 A token asserted across concatenated files is
    //     satisfied by the other file — F2-185b's per-type-gate-over-a-per-site-defect, again.
    //
    // Per register, per file, and the guard must WRAP an announced badge — checked by looking
    // for `role="status"` inside a bounded window after the guard, not anywhere in the file.
    const DISCLOSURES = [
      { register: 'finding',       guard: 'isFindingStatusManaged',       code: tabsCode() },
      { register: 'CAPA',          guard: 'isCapaStatusManaged',          code: tabsCode() },
      { register: 'annual plan',   guard: 'isPlanStatusManaged',          code: pageCode() },
      { register: 'working paper', guard: 'isWorkingPaperStatusManaged',  code: pageCode() },
    ];
    for (const d of DISCLOSURES) {
      const at = d.code.indexOf(`!${d.guard}(`);
      expect(at, `${d.register}: no !${d.guard}( disclosure in the file that renders it`)
        .toBeGreaterThan(-1);
      expect(d.code.slice(at, at + 400),
        `${d.register}: the disclosure must be announced — role="status" within its block`)
        .toContain('role="status"');
    }
    // And exactly five, so adding a sixth register without a disclosure is visible too:
    // four registers plus PacketTab's second rendering of the plan badge.
    expect((pageCode() + tabsCode()).match(/role="status"/g)?.length,
      'four register disclosures plus the packet tab\'s plan badge').toBe(5);
  });

  it('NO label map is indexed directly anywhere on the page — structurally, not by name', () => {
    // ⚠⚠ The first version of this leg listed four map names and PASSED, while
    // `WP_TYPE_LABEL[wp.type][lang]` sat two hundred lines away in the same file — and
    // `working_papers.type` holds DESIGN_TEST / OPERATING_TEST / SAMPLE_TEST / FOLLOW_UP,
    // none of which that map declared: **11 of the 12 rows in prod**. The browser found it,
    // by crashing, after the gate went green.
    //
    // 🔑 A gate keyed on the names you already know about cannot see the one you did not list,
    // and the omission reads exactly like coverage. This is the SIXTH instance in this lineage
    // of a check satisfied by something other than its claim, and the first where the missing
    // piece was the population rather than the pattern. Keyed on the SHAPE now:
    // `SOMETHING[expr][lang]` is banned outright, whatever the map is called.
    const surfaces = pageCode() + tabsCode();
    const direct = surfaces.match(/\b[A-Z][A-Z0-9_]*\s*\[[^\]\n]+\]\s*\[\s*lang\s*\]/g) ?? [];
    expect(direct,
      'every label map must be read through an accessor that tolerates an undeclared token — '
      + 'audit_lc has no CHECK on any of these columns, so a value this build has never heard '
      + 'of can arrive from a data fix at any time, and MAP[x][lang] is a TypeError')
      .toEqual([]);
    // Positive control: the detector must actually fire on the shape it bans, or an empty
    // result means nothing.
    expect('const a = WP_TYPE_LABEL[wp.type][lang];'
      .match(/\b[A-Z][A-Z0-9_]*\s*\[[^\]\n]+\]\s*\[\s*lang\s*\]/g),
      'the detector must fire on the exact shape that crashed the page').toHaveLength(1);
  });

  it("working-paper TYPE is closed too, not just status", () => {
    // The register holds four types the union never declared. They carry no lifecycle, so
    // labelling them is the whole fix — there is nothing to disclose as unavailable.
    for (const t of ['DESIGN_TEST', 'OPERATING_TEST', 'SAMPLE_TEST', 'FOLLOW_UP']) {
      expect(libCode(), `${t} is in 11 of 12 prod rows and needs a label`).toContain(`${t}:`);
    }
    expect(libCode()).toMatch(/export function workingPaperTypeLabel\(/);
  });

  it('every tone lookup has a fallback, because an undeclared key yields undefined', () => {
    const surfaces = pageCode() + tabsCode();
    for (const tone of ['PLAN_STATUS_TONE', 'WP_STATUS_TONE', 'CAPA_STATUS_TONE']) {
      const uses = surfaces.match(new RegExp(`${tone}\\[[^\\]]+\\]`, 'g')) ?? [];
      expect(uses.length, `${tone} should be used`).toBeGreaterThan(0);
      for (const use of uses) {
        const at = surfaces.indexOf(use);
        expect(surfaces.slice(at, at + use.length + 16),
          `${use} needs a ?? fallback — an undeclared key gives tone={undefined}`)
          .toMatch(/\?\?/);
      }
    }
  });

  it('every advance button in every register comes from its table', () => {
    const surfaces = pageCode() + tabsCode();
    for (const fn of ['nextPlanStatuses', 'nextWorkingPaperStatuses',
                      'nextCapaStatuses', 'nextFindingStatuses']) {
      expect(surfaces, `${fn} must drive the buttons`).toMatch(new RegExp(`${fn}\\([^)]*\\)\\.map`));
    }
    // The eleven hard-coded `status === '…' &&` button conditions this replaced. Each could go
    // stale against the server independently, and on the three registers above every one of
    // them was dead on the rows that hold an undeclared status.
    for (const dead of ["plan.status === 'DRAFT' &&", "plan.status === 'AC_REVIEW' &&",
                        "plan.status === 'AC_APPROVED' &&", "wp.status === 'DRAFT' &&",
                        "wp.status === 'PEER_REVIEW' &&", "c.status === 'OPEN' &&",
                        "c.status === 'IN_PROGRESS' &&", "c.status === 'AWAITING_VERIFICATION' &&"]) {
      expect(surfaces, `restated machine: ${dead}`).not.toContain(dead);
    }
  });

  it('every terminal set is derived, and every table has one', () => {
    for (const r of REGISTERS) {
      const table = r.table as Record<string, readonly string[]>;
      const terminal = Object.keys(table).filter(s => table[s].length === 0);
      expect(terminal.length, `${r.name} must declare at least one terminal state`)
        .toBeGreaterThan(0);
    }
    expect(libCode(), 'terminalOf must be the single implementation')
      .toMatch(/function terminalOf</);
    expect(libCode(), 'and there must be one nextFrom, not four').toMatch(/function nextFrom</);
    expect((libCode().match(/return nextFrom\(/g) ?? []).length,
      'all four next*Statuses read the shared predicate').toBe(4);
  });

  it('the plan a packet reports is the one for the current year', () => {
    // ⚠ A mistake of mine, caught only by reading the rendered document: replacing `plans[0]`
    // with "most recent year first" built a Q3-2026 Audit Committee packet from the 2027 DRAFT
    // plan — the exact shadowing the replacement's own comment claimed to prevent.
    expect(libCode()).toMatch(/function pickCurrentPlan\(/);
    expect(libCode()).toMatch(/plans\.find\(p => p\.year === thisYear\)/);
    expect(pageCode(), 'and a quarter card must take its year from the row')
      .not.toMatch(/\{q\.label\} 2026/);
  });
});

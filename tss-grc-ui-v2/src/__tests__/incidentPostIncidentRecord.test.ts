import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * F2-212 — the incident post-incident record: not lost on edit, not invented on display.
 *
 * `PUT /api/v1/incidents/{id}` is a FULL REPLACE — `IncidentService.update` calls
 * `setLessonsLearned(req.lessonsLearned())` and the same for `lessonsLearnedAr`,
 * `resolutionDate` and `timelineJson`, unconditionally. So a field the client omits
 * arrives null and is WRITTEN null.
 *
 * `WireContractTest` (backend) checks that the TYPES stay in step. It cannot see the
 * mechanism that actually caused the loss: `incToReq` is what builds the payload, and
 * it can omit a field the type happily permits. That is this file's job — a type gate
 * and a call-site gate answer different questions, and only the second one would have
 * caught the defect as it existed.
 *
 * Measured before the fix, in dev and prod (44 live incidents, identical):
 *   • `lessons_learned` / `_ar` populated on 3, `resolution_date` on 6, `timeline_json` on 3
 *   • the register filtered `CLOSED || RECOVERED`; `RECOVERED` appears in ZERO rows and
 *     `RESOLVED` (10 rows) was missing, so **0 incidents carrying a real lesson were
 *     admitted** — every lesson shown was invented and every real one hidden
 *   • the invented text came from the severity band, and the CAPA citation was minted
 *     `CAPA-<1000+id>` against real references that look like `CAPA-2026-19E164FC4A7-00`
 */

const SRC = join(__dirname, '..');
const strip = (s: string) => s.replace(/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g, '');

const pageRaw = readFileSync(join(SRC, 'pages', 'incidents', 'IncidentsPage.tsx'), 'utf8');
const page = strip(pageRaw);
const api = strip(readFileSync(join(SRC, 'lib', 'api.ts'), 'utf8'));

/** The four fields the server persists unconditionally on every PUT. */
const AT_RISK = ['lessonsLearned', 'lessonsLearnedAr', 'resolutionDate', 'timelineJson'] as const;

function ifaceFields(name: string): string[] {
  const m = new RegExp(`export interface ${name}\\s*\\{([^}]*)\\}`, 's').exec(api);
  expect(m, `interface ${name} must be declared in lib/api.ts`).not.toBeNull();
  return [...m![1].matchAll(/(?:^|[;\n])\s*(\w+)\s*\??\s*:/g)].map(x => x[1]);
}

describe('F2-212 — the post-incident record survives a full-replace PUT', () => {
  it('incToReq passes through every field the request type can carry', () => {
    const m = /function incToReq\([\s\S]*?\n\}/.exec(page);
    expect(m, 'incToReq must exist — it is what builds the PUT payload').not.toBeNull();
    const body = m![1 - 1];
    for (const f of AT_RISK) {
      // Assert the ASSIGNMENT, not the mere presence of the word: `lessonsLearned:`
      // followed by something read off the incident. A field named in a comment, or
      // declared on the type and never assigned here, still arrives null.
      expect(new RegExp(`${f}\\s*:\\s*i\\.`).test(body),
        `incToReq does not carry \`${f}\` through. PUT is a full replace, so the next edit `
        + 'writes null over it — silent, irreversible loss of a governance record that no '
        + 'surface was even able to display.').toBe(true);
    }
  });

  it('both client types declare the fields, so the round-trip has something to carry', () => {
    for (const f of AT_RISK) {
      expect(ifaceFields('IncidentDto')).toContain(f);
      expect(ifaceFields('IncidentRequest')).toContain(f);
    }
  });

  it('the lessons register reads the server column, and invents nothing', () => {
    expect(/lesson:\s*i\.severity\s*===/.test(page),
      'the lesson text is being DERIVED from the severity band again. A lesson is a '
      + 'statement about what an organisation concluded; deriving it from a field crossing '
      + 'a threshold asserts a conclusion nobody reached (F2-209).').toBe(false);
    expect(/CAPA-\$\{/.test(page),
      'a CAPA reference is being minted client-side. Real references are issued by the CAPA '
      + 'register and look like `CAPA-2026-19E164FC4A7-00`; a minted one resolves to nothing.').toBe(false);
    expect(/localStorage/.test(page),
      'the page is persisting to localStorage again. `cyber.incidents.lessons_learned(_ar)` '
      + 'exists, is returned by IncidentResponse and is accepted for write by IncidentRequest — '
      + 'a browser store beside it is one operator\'s copy of a governance record.').toBe(false);
    expect(/lessonsLearnedAr\s*\|\|\s*i\.lessonsLearned|i\.lessonsLearned/.test(page),
      'the register must read the real column').toBe(true);
  });

  it('the register does not gate lessons behind an inclusion list of statuses', () => {
    // F2-196: an inclusion list over an open vocabulary silently drops whatever nobody
    // remembered. Here it dropped RESOLVED (10 rows) and admitted RECOVERED (0 rows),
    // so every incident that actually had a lesson was excluded. A lesson exists because
    // somebody wrote one — not because the incident reached a state this page lists.
    expect(/'CLOSED'\s*\|\|\s*i\.status === 'RECOVERED'/.test(page),
      'the CLOSED||RECOVERED filter is back; measured, it admitted 0 of the 3 incidents '
      + 'carrying a real lesson').toBe(false);
    expect(/RECOVERED/.test(page.split('const lessons')[1] ?? ''),
      'the lessons register must not name a status token at all').toBe(false);
  });

  it('SELF-TEST: the detectors fire on the exact pre-fix shapes', () => {
    const preFixDerive = "lesson: i.severity === 'CRITICAL' ? 'Add to tabletop scenarios next quarter' : 'x',";
    const preFixCapa = 'capa: `CAPA-${1000 + incident.id}`,';
    const preFixStore = "localStorage.setItem(LESSON_STORE_KEY, JSON.stringify(next));";
    const preFixFilter = ".filter(i => i.status === 'CLOSED' || i.status === 'RECOVERED')";
    const preFixReq = `function incToReq(i) {
      return { title: i.title, severity: i.severity, status: i.status };
    }`;
    expect(/lesson:\s*i\.severity\s*===/.test(preFixDerive)).toBe(true);
    expect(/CAPA-\$\{/.test(preFixCapa)).toBe(true);
    expect(/localStorage/.test(preFixStore)).toBe(true);
    expect(/'CLOSED'\s*\|\|\s*i\.status === 'RECOVERED'/.test(preFixFilter)).toBe(true);
    for (const f of AT_RISK) {
      expect(new RegExp(`${f}\\s*:\\s*i\\.`).test(preFixReq),
        `the incToReq detector must fire on the pre-fix body for ${f}`).toBe(false);
    }
    // …and must NOT fire on the fixed shapes, or the gate bans its own fix.
    expect(/lesson:\s*i\.severity\s*===/.test('lesson: i.lessonsLearned ?? ""')).toBe(false);
    expect(new RegExp('lessonsLearned\\s*:\\s*i\\.').test('lessonsLearned: i.lessonsLearned,')).toBe(true);
  });
});

/**
 * F2-212, part two — the two defects the BROWSER found after every assertion above
 * was already green. Fifth consecutive slice in this programme where that happens,
 * so they are pinned rather than merely fixed.
 *
 * (a) The lessons badge read `{n} closed` / `{n} مغلقة`. THE FIX ABOVE IS WHAT MADE
 *     THAT FALSE: while the register filtered CLOSED||RECOVERED the word described
 *     the predicate; removing the filter left a label asserting a state the rows do
 *     not hold — measured, all three lesson-bearing incidents are INVESTIGATING /
 *     OPEN / OPEN, so the badge was wrong for 3 of 3. F2-208's label-versus-predicate
 *     rule, applied per LANGUAGE, and arriving from the inside: when you remove a
 *     filter, read every label that was describing it.
 *
 * (b) "CAPA Themes (90d)" rendered five INLINE hard-coded themes with counts summing
 *     to 18. `capa.tasks` has 26 columns and records no theme, no root cause and no
 *     incident link; the whole live register is 16 rows in dev and 13 in prod, and
 *     exactly ONE CAPA carries `origin_type='incident'`. Three de-mocking gates were
 *     blind at once — `kpiProvenance` keys on the `<Kpi>` tag, `pageDataProvenance`
 *     asks whether the page calls a server (it does), and F2-194's census looks for
 *     an exported `const` array while this literal is inline in JSX.
 */
describe('F2-212 — what the browser found after the gates were green', () => {
  const en = readFileSync(join(SRC, 'translations', 'en.ts'), 'utf8');
  const ar = readFileSync(join(SRC, 'translations', 'ar.ts'), 'utf8');

  it('the lessons badge counts lessons and does not name a status', () => {
    // The retired key must be gone from BOTH dictionaries. Asserting only the page
    // would pass while a stale key sat in the translations waiting to be reused.
    for (const [name, dict] of [['en', en], ['ar', ar]] as const) {
      expect(/incidentsLessonsClosed/.test(dict),
        `${name}.ts still declares incidentsLessonsClosed — the badge counts incidents `
        + 'that have a lesson recorded, which is not the same set as the closed ones '
        + '(measured: 3 lessons, 0 of them on a CLOSED incident).').toBe(false);
    }
    expect(/incidentsLessonsRecorded/.test(page),
      'the lessons badge must use the count-describing label').toBe(true);
    // Per LANGUAGE (F2-208): neither value may claim a lifecycle state.
    const enVal = /incidentsLessonsRecorded:\s*'([^']*)'/.exec(en)?.[1] ?? '';
    const arVal = /incidentsLessonsRecorded:\s*'([^']*)'/.exec(ar)?.[1] ?? '';
    expect(enVal.length, 'en label must exist').toBeGreaterThan(0);
    expect(arVal.length, 'ar label must exist').toBeGreaterThan(0);
    expect(/closed|resolved|recovered/i.test(enVal),
      `the en badge label "${enVal}" names a lifecycle state; the predicate is `
      + '"has a lesson recorded", which is a different set').toBe(false);
    expect(/مغلق|مقفل|منتهي/.test(arVal),
      `the ar badge label "${arVal}" names a lifecycle state. Arabic is the primary `
      + 'language here (house rule 7), so this half is the one a user reads first — '
      + 'F2-208 found exactly this drift in the Arabic only.').toBe(false);
  });

  it('the CAPA card reads the real register, with no inline corpus', () => {
    expect(/theme:\s*'/.test(page),
      'an inline `{ theme: …, count: N }` corpus is back on the incidents page. '
      + '`capa.tasks` has no theme column, so a theme list can only be invented '
      + '(F2-209: no column at all means the field cannot be reconstructed).').toBe(false);
    // Assert the CALL, not the import — F2-194/F2-205: a substring check is satisfied
    // by the import statement, and a mutation deleting every call site survives it.
    expect(/useCapas\s*\(/.test(page),
      'the CAPA card must read the register from the server through useCapas()').toBe(true);
    expect(/incidentCapas/.test(page),
      'the card must render the CAPAs whose origin is an incident').toBe(true);
    // The retired title claimed a 90-day window nothing computes.
    for (const [name, dict] of [['en', en], ['ar', ar]] as const) {
      const title = new RegExp("incidentsCardCapa:\\s*'([^']*)'").exec(dict)?.[1] ?? '';
      expect(title.length, `${name} card title must exist`).toBeGreaterThan(0);
      expect(/90|٩٠/.test(title),
        `the ${name} CAPA card title "${title}" claims a 90-day window. Nothing on this `
        + 'page computes one, and a window is a second claim independent of the figures.').toBe(false);
    }
  });

  it('SELF-TEST: both detectors fire on the exact pre-fix shapes', () => {
    const preFixThemes = "{ theme: 'Phishing simulation cadence',  count: 7 },";
    const preFixBadge = "<Badge tone=\"primary\">{lessons.length} {t('incidentsLessonsClosed')}</Badge>";
    expect(/theme:\s*'/.test(preFixThemes)).toBe(true);
    expect(/incidentsLessonsClosed/.test(preFixBadge)).toBe(true);
    expect(/closed/i.test('closed')).toBe(true);
    expect(/مغلق/.test('مغلقة')).toBe(true);
    expect(/90|٩٠/.test('CAPA Themes (90d)')).toBe(true);
    expect(/90|٩٠/.test('مواضيع التصحيحات (90 يوم)')).toBe(true);
    // …and must not fire on the replacements, or the gate bans its own fix.
    expect(/closed|resolved|recovered/i.test('with a lesson recorded')).toBe(false);
    expect(/مغلق|مقفل|منتهي/.test('بدرس مسجَّل')).toBe(false);
    expect(/90|٩٠/.test('Corrective actions from incidents')).toBe(false);
    expect(/theme:\s*'/.test('const incidentCapas = capas.items.filter(...)')).toBe(false);
    // The call-shape check must reject a bare import, which is what F2-194 got wrong.
    expect(/useCapas\s*\(/.test("import { useCapas } from '../../lib/capa';")).toBe(false);
  });
});

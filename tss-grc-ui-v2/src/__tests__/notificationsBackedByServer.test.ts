import { describe, it, expect } from 'vitest';
import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

/**
 * F2-38 — the notification bell must read the server, and the invented people directory must shrink.
 *
 * The bell on every page of the product was backed by `lib/notificationOutbox`, a `localStorage`
 * store with zero network calls, and resolved each row's "recipient" through `lib/userDirectory` —
 * 146 lines of invented users (`usr-001`, `ceo@org.sa`, E.164 mobiles belonging to nobody). Meanwhile
 * `platform.notifications` held **445 real rows** served by a working `isAuthenticated()` endpoint the
 * UI had never called: `admin` alone had 57 unread of 75. CE-10 shipped the change engine's ability to
 * reach people and nobody could see it, because the bell read a different store.
 *
 * <b>What this guards, and what it cannot.</b> It pins the set of files importing the fabricated
 * directory so the number can only fall, and it asserts the bell's own module reaches the network.
 * It checks *direct* imports, like F2-137's guard: a file importing a wrapper that imports the
 * directory would slip past. Accepted for the same reason — the defect class is a component reaching
 * for `getUser()` itself.
 *
 * <b>⚠ The premise is measured, not assumed.</b> {@link matchesBothImportShapes} exists because
 * F2-137's guard shipped with the exact defect it was written to catch: its matcher was
 * `import[^;\n]*?from '…'`, which cannot cross a newline, so a multi-line import was invisible to it.
 * A matcher that silently sees nothing reports a clean zero, which is F2-79's class. Anchoring on
 * `from '…'` fixes it, and the test below proves the anchor sees both shapes.
 */
describe('F2-38 — the notification bell reads the server, and the invented directory shrinks', () => {
  const SRC = join(__dirname, '..');

  /** The fabricated people directory. Every import of it is a fabricated identity on a screen. */
  const FAKE_DIRECTORY = 'lib/userDirectory';

  /**
   * Files that still import the fabricated directory, measured 2026-08-06. **May only shrink.**
   *
   * Was **8** before this slice: `Topbar.tsx` left (the bell now reads the server) and
   * `UserPicker.tsx` was deleted outright — a type-ahead people picker with **zero** consumers, whose
   * only `<UserPicker` occurrence was inside its own doc comment. It was the obvious component to
   * reuse for the F2-26 committee seat picker and would have bound governance seats to invented
   * identities.
   *
   * The remaining six were one cluster, not six problems: three `localStorage` stores
   * (`activityLog`, `notificationOutbox`, `recordRegistry`) and the three surfaces that render them.
   *
   * **6 → 3 on 2026-08-06, when F2-142 was decided: retire.** `notificationOutbox` and everything that
   * rendered it are deleted, so three entries left this list by having their files removed rather than
   * by being rewired — and the *stale* half of this test is what reported all three, on the same run,
   * by name. What remains is `activityLog` + `recordRegistry` and the drawer that renders them.
   *
   * @see it('the fabricated delivery estate stays deleted') for what may not come back
   */
  const DIRECTORY_IMPORTERS: readonly string[] = [
    'components/lifecycle/ActivityFeedDrawer.tsx',
    'lib/activityLog.ts',
    'lib/recordRegistry.ts',
  ];

  /**
   * F2-142 — the five files retired on 2026-08-06, and why a deletion needs a test at all.
   *
   * <p>The filed finding named ONE page. Measuring found the same defect three times, and the second
   * instance was worse than the one filed: `lib/notificationDispatch` (341 lines) was an *"operator
   * console for the notification dispatch runtime"* naming real commercial providers — `AWS SES (KSA
   * region)`, `WhatsApp Business Cloud API`, `CITC-licensed Saudi SMS gateway` — with a `dailyQuota:
   * 50_000` and a `lastVerifiedAt` documented as *"when this channel was last verified by smoke-test"*
   * that was `_hoursAgo(6)` evaluated at module load. The screen therefore always said "verified 6
   * hours ago", about nothing.
   *
   * <p>The reason to pin the deletion rather than trust it: every one of these was plausible,
   * well-built and demo-ready, which is exactly why it survived so long. The cheapest way to undo this
   * slice is for someone to want a delivery screen for a demo and rebuild the nearest thing.
   */
  const RETIRED_BY_F2_142: readonly string[] = [
    'lib/notificationOutbox.ts',
    'lib/notificationDispatch.ts',
    'pages/notification-outbox/NotificationOutboxPage.tsx',
    'pages/notifications/NotificationDispatchPage.tsx',
    'pages/notifications-channels/NotificationChannelsPage.tsx',
  ];

  /**
   * Every `entity_type` value in the live data on 2026-08-06, with its row count.
   *
   * ⚠ **Two casings**, and a route map handling only one would silently produce no link for the
   * majority of rows — `EvidenceRequest` alone is 324 of 445. This list is the vocabulary a front-end
   * test can hold; it cannot see a *new* value appearing in production, which is the honest limit and
   * the reason the map returns `null` rather than guessing.
   */
  const LIVE_ENTITY_TYPES: readonly string[] = [
    'EvidenceRequest', 'WorkflowRequest', 'CorrectiveAction', 'VulnerabilitySlaFinding',
    'CyberEvidence', 'CyberActionPlan', 'ShariaNonComplianceEvent', 'ShariaAuditFinding',
    'BreakGlassEvent',
    'VENDOR', 'CHANGE', 'ENGAGEMENT', 'AML_ALERT', 'FINDING', 'KRI',
    'OUTSOURCING_ARRANGEMENT', 'POLICY',
  ];

  /**
   * Does `source` import the module named `moduleName`?
   *
   * Anchored on `from '…'` so it sees single-line and multi-line imports alike, and it matches the
   * module's **last path segment** rather than a prefix.
   *
   * <p>⚠ Both of those are scars. The `from` anchor is F2-137's bug (`import[^;\n]*?from` cannot
   * cross a newline). The segment match is this test's own first bug: it required the literal
   * `lib/userDirectory`, so it was blind to `'./userDirectory'` — the shape every file *inside*
   * `lib/` uses — and it missed **3 of the 6** offenders while reporting the list as clean. Requiring
   * the closing quote is what keeps `/notifications'` from matching `/notificationOutbox'`.
   */
  function importsModule(source: string, moduleName: string): boolean {
    const seg = moduleName.split('/').pop()!;
    return new RegExp(`from\\s+['"][^'"]*\\/${seg}['"]`).test(source);
  }

  /**
   * `source` with comments removed.
   *
   * Needed because the checks below ask whether a file *does* something, and a file that explains
   * what it replaced mentions the thing it does not do. `lib/notifications.ts` documents that it is
   * not a `localStorage` store, and the first version of that assertion failed on its own doc comment
   * — a guard matching prose invents defects just as readily as one matching too little.
   */
  function codeOnly(source: string): string {
    return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1');
  }

  function walk(dir: string, out: string[] = []): string[] {
    for (const name of readdirSync(dir)) {
      const full = join(dir, name);
      if (statSync(full).isDirectory()) {
        if (name === 'node_modules' || name === '__tests__') continue;
        walk(full, out);
      } else if (/\.(ts|tsx)$/.test(name) && !/\.test\.tsx?$/.test(name)) {
        out.push(full);
      }
    }
    return out;
  }

  function relative(abs: string): string {
    return abs.slice(SRC.length + 1).split('\\').join('/');
  }

  // ───────────────────────────────────────────────────────────────────────────────────────────

  it('the import matcher sees both single-line and multi-line imports', () => {
    const oneLine = `import { getUser } from '../../lib/userDirectory';`;
    const multiLine = [
      'import {',
      '  getUser,',
      '  searchUsers,',
      `} from '../../lib/userDirectory';`,
    ].join('\n');

    // F2-137's guard shipped with a matcher that failed this exact case, so the defect it was
    // written to catch was invisible to it. A matcher that sees nothing reports a clean zero.
    expect(importsModule(oneLine, FAKE_DIRECTORY),
      'the matcher cannot see a single-line import').toBe(true);
    expect(importsModule(multiLine, FAKE_DIRECTORY),
      'the matcher cannot cross a newline — this is F2-137\'s own bug, and it makes every count '
      + 'below a comfortable zero').toBe(true);

    // This test's OWN first bug: it required the literal `lib/userDirectory`, so every file inside
    // lib/ — which imports './userDirectory' — was invisible. It missed 3 of 6 offenders and called
    // the list clean.
    expect(importsModule(`import { getUser } from './userDirectory';`, FAKE_DIRECTORY),
      'the matcher cannot see a same-directory import, so it is blind to everything inside lib/').toBe(true);

    expect(importsModule(`import { api } from './api';`, FAKE_DIRECTORY),
      'the matcher matches an unrelated import, so it would invent offenders').toBe(false);
    // Prefix collision: notificationOutbox must not read as notifications.
    expect(importsModule(`import { x } from '../../lib/notificationOutbox';`, 'lib/notifications'),
      'the matcher treats notificationOutbox as notifications — the fake store would satisfy the '
      + 'assertion that the bell reads the real one').toBe(false);
  });

  it('no file outside the pinned list imports the fabricated user directory', () => {
    const offenders = walk(SRC)
      .filter(f => importsModule(readFileSync(f, 'utf8'), FAKE_DIRECTORY))
      .map(relative)
      .sort();

    const unexpected = offenders.filter(f => !DIRECTORY_IMPORTERS.includes(f));
    expect(unexpected,
      `${unexpected.join(', ')} imports ${FAKE_DIRECTORY}, which is 146 lines of invented users whose `
      + 'ids exist in no database (iam.users holds 7 real users; zero match usr-*/@org.sa). Bind to a '
      + 'real endpoint instead. Do not add to DIRECTORY_IMPORTERS — that list may only shrink.').toEqual([]);

    // Honest in both directions: a stale entry means someone fixed a file and the list now overstates
    // the debt, which makes the ratchet look tighter than it is.
    const stale = DIRECTORY_IMPORTERS.filter(f => !offenders.includes(f));
    expect(stale,
      `${stale.join(', ')} no longer imports the directory (or was renamed). Remove it from `
      + 'DIRECTORY_IMPORTERS so the list keeps measuring what it claims.').toEqual([]);
  });

  it('the fabricated delivery estate stays deleted', () => {
    const alive = RETIRED_BY_F2_142.filter(f => existsSync(join(SRC, f)));
    expect(alive,
      `${alive.join(', ')} is back. F2-142 retired these: they modelled multi-channel delivery — `
      + 'EMAIL / SMS / WHATSAPP / SLACK / TEAMS / NAFATH / TADAWUL_PORTAL — entirely in localStorage, '
      + 'with the browser calling markSent on its own messages. Naming Nafath and the Tadawul portal '
      + 'makes a screen that reports SENT a regulatory claim, not a cosmetic one. Real delivery needs '
      + 'server-side gateways and is an owner decision; a screen is not a substitute for one.').toEqual([]);

    // The routes must still resolve — retiring a page is not licence to 404 a link a user bookmarked.
    // F2-137's precedent: delete the fake, redirect the route at something true.
    const app = readFileSync(join(SRC, '..', 'src', 'App.tsx'), 'utf8');
    for (const route of ['/notifications/outbox', '/notifications/dispatch', '/notifications/channels']) {
      const row = app.split('\n').find(l => l.includes(`path="${route}"`));
      expect(row, `${route} has no route at all — a bookmark to it now 404s`).toBeTruthy();
      expect(row!.includes('RedirectPreservingQuery'),
        `${route} must redirect through RedirectPreservingQuery, not raw <Navigate> — since F2-83 the `
        + '`?org=` parameter is the only thing naming the tenant, and Navigate drops the query string').toBe(true);
    }

    // And the destination has to be real. InboxPage is server-paginated over platform.notifications.
    const inbox = readFileSync(join(SRC, 'pages/inbox/InboxPage.tsx'), 'utf8');
    expect(/notificationApi\./.test(codeOnly(inbox)),
      'the redirect target no longer calls notificationApi — retiring three fakes only helps if the '
      + 'page they point at reads the server').toBe(true);
  });

  it('no page rebuilds a client-side delivery lifecycle under a new name', () => {
    // The names are replaceable; the SHAPE is the tell. A module that owns a delivery status vocabulary
    // AND persists it locally is a delivery subsystem with no server, whatever it is called.
    //
    // ⚠ Comments are stripped first. This file already learned that once (see the localStorage check
    // below, whose first version failed on lib/notifications' own note about what it replaced) and the
    // F2-142 slice itself re-learned it twice inside ten minutes — a replacement comment explaining
    // what was removed necessarily contains the words being banned. There is no textual shape that
    // distinguishes code from a comment quoting code.
    const offenders = walk(SRC).filter(f => {
      const code = codeOnly(readFileSync(f, 'utf8'));
      const marksItselfSent = /\bmarkSent\b|\bmarkFailed\b/.test(code);
      const persistsLocally = /localStorage/.test(code);
      return marksItselfSent && persistsLocally;
    }).map(relative).sort();

    expect(offenders,
      `${offenders.join(', ')} calls markSent/markFailed against localStorage. That is F2-142 rebuilt: `
      + 'the tell for a fake delivery subsystem is not the fake data, it is that markSent exists at all '
      + '— nothing that really sends needs the sender to mark it sent. Delivery status belongs to '
      + 'whatever performed the delivery, which is a server.').toEqual([]);

    // Anti-vacuity: a walk that resolved nothing satisfies the assertion above.
    expect(walk(SRC).length).toBeGreaterThan(500);
  });

  it('the bell reads the server and not the browser', () => {
    const topbar = readFileSync(join(SRC, 'components/layout/Topbar.tsx'), 'utf8');

    expect(importsModule(topbar, 'lib/notifications'),
      'Topbar must import lib/notifications, the real API client').toBe(true);
    expect(importsModule(topbar, 'lib/notificationOutbox'),
      'Topbar imports lib/notificationOutbox again — that is the localStorage store with zero network '
      + 'calls, and it is on every page in the product').toBe(false);
    expect(importsModule(topbar, FAKE_DIRECTORY),
      'Topbar imports the fabricated directory again. It was resolving a "recipient" name for the '
      + 'person already looking at the screen: the inbox is per-user, so there is nobody to look up').toBe(false);
  });

  it('lib/notifications actually calls the API rather than describing it', () => {
    const path = join(SRC, 'lib/notifications.ts');
    expect(existsSync(path), 'lib/notifications.ts is missing').toBe(true);
    const src = readFileSync(path, 'utf8');

    for (const endpoint of [
      '/api/v1/notifications/unread-count',
      '/api/v1/notifications?page=',
      '/api/v1/notifications/${id}/read',
      '/api/v1/notifications/read-all',
    ]) {
      expect(src.includes(endpoint), `lib/notifications.ts never references ${endpoint}`).toBe(true);
    }

    // The point of the slice: a real client goes through the shared request helper.
    expect(/api\.(get|patch)</.test(src),
      'lib/notifications.ts declares endpoints but calls nothing through `api` — a module that names '
      + 'a URL without fetching it is what lib/notificationOutbox already was').toBe(true);
    expect(codeOnly(src).includes('localStorage'),
      'lib/notifications.ts touches localStorage in CODE; the whole point is that it does not. '
      + '(Mentioning it in a comment is fine — this check strips comments, because the first version '
      + 'of it failed on this module\'s own note about what it replaced.)').toBe(false);
  });

  it('the entity route map covers every live entity_type, in both casings', () => {
    const src = readFileSync(join(SRC, 'lib/notifications.ts'), 'utf8');

    const missing = LIVE_ENTITY_TYPES.filter(t => !new RegExp(`\\b${t}\\s*:`).test(src));
    expect(missing,
      `ENTITY_ROUTES does not cover ${missing.join(', ')}. Those values are present in `
      + 'platform.notifications today, so each one is a notification that renders without a link. '
      + 'entity_type carries CamelCase JPA names AND SCREAMING_CASE domain names — a map handling one '
      + 'casing silently drops the other, and EvidenceRequest alone is 324 of 445 rows.').toEqual([]);

    // KRI is the measurement worth pinning: its detail route is /risk/kri/:externalId and a
    // notification carries a numeric entity_id, so a record-level link would address the wrong row.
    expect(/KRI:\s*\{\s*path:\s*'\/risk\/kri\/dashboard',\s*record:\s*false/.test(src),
      'KRI must stay list-level. The detail route keys on externalId, not the numeric entityId a '
      + 'notification carries, so building /risk/kri/{entityId} lands on the wrong record or none. '
      + 'A link that goes somewhere wrong is worse than no link.').toBe(true);
  });
});

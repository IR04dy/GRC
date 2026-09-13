import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { ErrorBoundary } from '../components/ui/ErrorBoundary';

/*
 * ─────────────────────────────────────────────────────────────────────────────
 * JRN-12 — the last-resort crash surface speaks the user's language, and stops
 *          claiming it preserved what it is about to discard.
 *
 * `components/ui/ErrorBoundary.tsx` contained 0 `useI18n` and 0 `t(`: the card an operator meets
 * when a page crashes was hardcoded English on an Arabic-primary platform (house rule 7), on the
 * one screen a user is reading for guidance rather than for content. And its body sentence —
 * "The page state has been preserved — click reload to start a fresh session" — sat one line above
 * `window.location.reload()`, which discards exactly that state, contradicting itself inside its
 * own second clause (F2-184).
 *
 * ⚠⚠ THE SIZE COLUMN SAID "One component" AND THE STRUCTURE IS WHY THE OBVIOUS FIX IS WRONG.
 * This boundary is mounted ABOVE `I18nProvider` (App.tsx:380 wraps it), and it is a CLASS
 * component because `getDerivedStateFromError` has no hook equivalent — so `useI18n()` is
 * unavailable twice over, and calling it would throw INSIDE the crash card, i.e. crash the thing
 * whose whole job is to survive a crash. Moving the boundary inside the provider to "fix" that
 * would mean a crash in `ThemeProvider` or `I18nProvider` itself renders the white screen this
 * component exists to prevent. The copy is therefore inline and bilingual, reading the same
 * `readStoredLang()` the provider seeds itself from — one declaration, two readers (F2-217).
 *
 * 🔑 The third reason is the sharpest and is what makes "just move the strings into the dictionary"
 * actively harmful: `t()` resolves against a dictionary fetched as a SEPARATE CHUNK (PERF-FE-002),
 * and a failed chunk fetch is one of the very crashes this card handles (`CHUNK_ERROR_RE`). A card
 * whose own text depends on that fetch renders raw keys exactly when it is needed most.
 *
 * ⚠ The default-language leg is not decoration. `readStoredLang` defaults to Arabic when nothing
 * is stored, and that is the platform's stated primary language — a rewire that read the key but
 * defaulted to English would pass every other leg here and be wrong for every first-time user.
 *
 * ⚠ Every structural scan strips comments first, and here that is LOAD-BEARING rather than
 * habitual: the fix's own comment QUOTES the retired sentence in order to record what it replaced,
 * so a raw-text scan reads the epitaph as the corpse and fails on a correct tree
 * (F2-193 / F2-196 / F2-202 / F2-205).
 * ─────────────────────────────────────────────────────────────────────────────
 */

const SRC = join(__dirname, '..');
const read = (p: string) => readFileSync(join(SRC, p), 'utf8');
// One left-to-right alternation: at a `//` an inner `/*` can never open a block (F2-198/F2-211).
const STRIP = /(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g;
const strip = (s: string) => s.replace(STRIP, '');

const BOUNDARY = 'components/ui/ErrorBoundary.tsx';
const DETAIL = 'pages/risk/RiskDetailPage.tsx';
const EN = 'translations/en.ts';
const AR = 'translations/ar.ts';

const LANG_KEY = 'grc.lang';

/** A crash that is NOT a stale-chunk signature, so the card renders rather than auto-reloading. */
function Boom(): never {
  throw new Error('boom — synthetic render crash');
}

function renderCrash() {
  return render(
    <ErrorBoundary>
      <Boom />
    </ErrorBoundary>,
  );
}

describe('JRN-12 — the crash card speaks the reader’s language', () => {
  beforeEach(() => {
    window.localStorage.clear();
    // React logs the caught error; that is correct behaviour and pure noise here.
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });
  afterEach(() => vi.restoreAllMocks());

  it('renders Arabic, right-to-left, for an Arabic session', () => {
    window.localStorage.setItem(LANG_KEY, 'ar');
    renderCrash();
    const card = screen.getByRole('alert');
    expect(card).toHaveAttribute('dir', 'rtl');
    expect(card.textContent).toContain('تعذّر عرض هذه الصفحة');
    /*
     * ⚠⚠ Scoped to the BUTTON, and the mutation harness is what forced that. As a card-wide
     * `textContent` check this assertion SURVIVED a mutation that left the Arabic button reading
     * "Reload", because the Arabic body sentence contains "وإعادة التحميل" — so the card already
     * held the substring and the leg was satisfied by the body two lines above the element it
     * names. F2-194's substring-satisfied-by-something-else, in a leg written after quoting it.
     * The English pair escapes the same trap only by luck ("reloading" is not "Reload"), so it is
     * scoped the same way rather than left to a coincidence of capitalisation.
     */
    expect(screen.getByRole('button').textContent).toContain('إعادة التحميل');
    // The English card must not be what an Arabic reader gets.
    expect(card.textContent).not.toContain('Something broke on this page');
  });

  it('renders English, left-to-right, for an English session', () => {
    window.localStorage.setItem(LANG_KEY, 'en');
    renderCrash();
    const card = screen.getByRole('alert');
    expect(card).toHaveAttribute('dir', 'ltr');
    expect(card.textContent).toContain('Something broke on this page');
    expect(screen.getByRole('button').textContent).toContain('Reload');
    expect(card.textContent).not.toContain('تعذّر عرض هذه الصفحة');
  });

  it('defaults to Arabic when nothing is stored — the platform’s primary language', () => {
    // No localStorage entry at all: a first-time visitor. `readStoredLang` answers 'ar', and the
    // card must follow it rather than falling back to English on its own.
    renderCrash();
    const card = screen.getByRole('alert');
    expect(card).toHaveAttribute('dir', 'rtl');
    expect(card.textContent).toContain('تعذّر عرض هذه الصفحة');
  });

  it('does not claim it preserved state the reload discards', () => {
    window.localStorage.setItem(LANG_KEY, 'en');
    renderCrash();
    const card = screen.getByRole('alert');
    expect(card.textContent).not.toMatch(/has been preserved/i);
    // And it says what does happen, so the honesty is asserted rather than merely the absence of
    // the old claim — deleting the sentence outright would leave a card that says nothing.
    expect(card.textContent).toMatch(/not saved is lost/i);
  });

  it('still surfaces the underlying error message for triage', () => {
    // Anti-vacuity for the legs above: the card genuinely renders, and the developer-facing detail
    // stays in place. A card that rendered nothing would satisfy every `not.toContain` here.
    window.localStorage.setItem(LANG_KEY, 'en');
    renderCrash();
    expect(screen.getByRole('alert').textContent).toContain('synthetic render crash');
  });
});

describe('JRN-12 — the reasons the copy is inline, pinned so nobody "fixes" them', () => {
  it('the boundary takes no i18n context and loads no dictionary', () => {
    const src = strip(read(BOUNDARY));
    // It is mounted above the provider AND is a class — either alone makes this throw.
    expect(src).not.toContain('useI18n');
    // `t()` resolves against a separately-fetched chunk, and a failed chunk fetch is one of the
    // crashes this very card handles.
    expect(src).not.toMatch(/from\s+['"][^'"]*translations/);
  });

  it('it reads the stored language through i18n rather than spelling the key itself', () => {
    const src = strip(read(BOUNDARY));
    expect(src).toMatch(/import\s*\{[^}]*readStoredLang[^}]*\}\s*from\s+['"][^'"]*i18n['"]/);
    expect(src).toMatch(/readStoredLang\s*\(\s*\)/);
    // A second copy of the key and of the Arabic-primary default would be free to drift from the
    // provider's (F2-217) — so the literal must live in exactly one place, and not here.
    expect(src).not.toContain(LANG_KEY);
    expect(strip(read('lib/i18n.tsx'))).toContain(`const LANG_KEY = '${LANG_KEY}'`);
  });

  it('both languages carry all three strings — an empty half would render blank', () => {
    const src = strip(read(BOUNDARY));
    for (const key of ['title:', 'body:', 'reload:', 'dir:']) {
      // Twice: once under `en`, once under `ar`.
      const n = src.split(key).length - 1;
      expect(n, `COPY.${key} should appear for both languages`).toBe(2);
    }
  });
});

describe('JRN-12 — the risk detail error card, whose two inputs were both English', () => {
  it('neither of the error card’s inputs is a bare English literal', () => {
    const src = strip(read(DETAIL));
    // `:335` renders `error`, and `:105` is what SETS it — so fixing only the literal the finding
    // named would have left the card English in the case that actually happens (F2-215).
    expect(src).not.toContain("'Failed to load'");
    expect(src).not.toContain("{error ?? 'Not found'}");
    expect(src).toContain("t('riskDetailLoadFailed')");
    expect(src).toContain("t('riskDetailNotFound')");
  });

  it('both keys are declared in both dictionaries, and the Arabic is Arabic', () => {
    const en = read(EN);
    const ar = read(AR);
    for (const key of ['riskDetailLoadFailed', 'riskDetailNotFound']) {
      expect(en, `${key} missing from en`).toContain(`${key}:`);
      expect(ar, `${key} missing from ar`).toContain(`${key}:`);
    }
    // A key added to both files by copy-paste declares the English string twice and passes the
    // check above. Assert the Arabic half is actually Arabic script.
    for (const key of ['riskDetailLoadFailed', 'riskDetailNotFound']) {
      const line = ar.split('\n').find(l => l.trim().startsWith(`${key}:`))!;
      expect(line, `${key} is not in Arabic`).toMatch(/[؀-ۿ]/);
    }
  });
});

describe('JRN-12 — the scans read the files they claim to', () => {
  it('anti-vacuity: a path typo would make every structural leg above green', () => {
    expect(read(BOUNDARY)).toContain('ErrorBoundary');
    expect(read(DETAIL)).toContain('riskDetailNotFound');
    expect(strip(read(BOUNDARY)).length).toBeGreaterThan(1_500);
    expect(strip(read(DETAIL)).length).toBeGreaterThan(10_000);
  });

  it('the comment stripper is load-bearing, not habitual', () => {
    // The fix's own comment quotes the retired sentence in order to record what it replaced, so a
    // raw-text scan fails on a CORRECT tree — the strongest possible demonstration that stripping
    // is required rather than tidy (F2-193).
    const raw = read(BOUNDARY);
    expect(raw).toContain('has been preserved');
    expect(strip(raw)).not.toContain('has been preserved');
  });
});

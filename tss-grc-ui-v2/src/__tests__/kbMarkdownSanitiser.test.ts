import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { renderMarkdown } from '../pages/knowledge-base/KbArticlePage';

/**
 * F2-235 — the KB markdown renderer's URL-scheme allowlist, which had NO TEST OF ANY KIND.
 *
 * A KB article body is authored content rendered through `dangerouslySetInnerHTML`, so
 * `[click me](javascript:alert(1))` would execute with the reader's session if the scheme check
 * were ever weakened. The guard was added in commit 957b679e ("KB stored-XSS (#120)") and the file
 * carrying it has contained a RAW NUL BYTE ever since — which makes ripgrep classify it as binary,
 * so every content-mode sweep over the frontend has silently skipped it. **The one file in the
 * frontend most worth reading was the one no sweep could read**, and nothing asserted its
 * behaviour, so a regression here would have been invisible from both directions at once.
 *
 * Control characters below are built with `String.fromCharCode` rather than written as escapes, on
 * purpose: an escape is what collapsed into a raw byte in the first place, twice, in two languages.
 * A test file that pins this defect must not be able to contain it. `ScannableSourceContractTest`
 * enforces that tree-wide.
 */
describe('KB markdown renderer — URL scheme allowlist (F2-235)', () => {
  const NUL = String.fromCharCode(0);
  const TAB = String.fromCharCode(9);
  const UNIT_SEP = String.fromCharCode(31); // 0x1F — the range end that also collapsed

  const href = (html: string): string => (/href="([^"]*)"/.exec(html) ?? [, ''])[1] as string;

  describe('schemes that must be neutralised to an inert anchor', () => {
    const blocked: Array<[string, string]> = [
      ['javascript:alert(1)', 'the vector the guard was written for'],
      ['JavaScript:alert(1)', 'the check lowercases, so case is not an evasion'],
      ['jAvAsCrIpT:alert(1)', 'mixed case likewise'],
      [' javascript:alert(1)', 'a leading space is stripped before the scheme is read'],
      [TAB + 'javascript:alert(1)', 'a leading tab — the WebKit bypass named in the code comment'],
      [NUL + 'javascript:alert(1)', 'a leading NUL, which is what the collapsed escape covers'],
      [UNIT_SEP + 'javascript:alert(1)', 'the top of the stripped control range'],
      ['java' + TAB + 'script:alert(1)', 'a tab INSIDE the scheme — the URL parser removes it, '
        + 'and this is caught because the value still contains a colon'],
      ['data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==', 'data: renders markup'],
      ['vbscript:msgbox(1)', 'vbscript: is executable in legacy engines'],
      ['file:///c:/windows/system32/', 'file: reads the viewer machine'],
    ];

    it.each(blocked)('blocks %j — %s', (url) => {
      expect(href(renderMarkdown(`[click me](${url})`))).toBe('#');
    });

    it('renders the anchor inert rather than dropping the link text', () => {
      const html = renderMarkdown('[click me](javascript:alert(1))');
      expect(html).toContain('click me');
      expect(html).not.toMatch(/href="[^"]*javascript/i);
    });
  });

  describe('URLs a KB author legitimately writes', () => {
    const allowed = [
      'https://sama.gov.sa/en-US/Pages/default.aspx',
      'http://intranet.tss.sa/policies',
      'mailto:dpo@tss.sa',
      'tel:+966112345678',
      '/compliance?tab=obligations',
      '#section-3',
      'guides/business-continuity.md',
    ];

    it.each(allowed)('allows %j unchanged', (url) => {
      expect(href(renderMarkdown(`[link](${url})`))).toBe(url);
    });

    /**
     * These two exist because the mutation harness proved the obvious blocked-vector assertions do
     * NOT depend on the mechanisms they appear to test. Deleting the leading-control strip leaves
     * every `<TAB>javascript:` case still blocked — the colon rule does that work — so the strip's
     * only observable effect is on a LEGITIMATE url that arrives with leading whitespace, and the
     * same is true of `.toLowerCase()`. Without these two the strip and the case-fold could both be
     * deleted with the suite green.
     */
    it('a legitimate URL survives leading whitespace and control characters', () => {
      expect(href(renderMarkdown(`[link](${TAB}https://sama.gov.sa)`))).toBe(`${TAB}https://sama.gov.sa`);
      expect(href(renderMarkdown(`[link](${NUL}/compliance)`))).toBe(`${NUL}/compliance`);
    });

    it('an upper-case scheme is recognised, not rejected', () => {
      expect(href(renderMarkdown('[link](HTTPS://SAMA.GOV.SA)'))).toBe('HTTPS://SAMA.GOV.SA');
      expect(href(renderMarkdown('[link](MailTo:dpo@tss.sa)'))).toBe('MailTo:dpo@tss.sa');
    });
  });

  /**
   * The load-bearing half, and the reason this test drives the renderer instead of the private
   * predicate: the allowlist ALONE does not close this vector. Written with real HTML entities the
   * URL contains no ':' at all, so `!trimmed.includes(':')` — the relative-path fallback — accepts
   * it, and a browser decoding the attribute would get `javascript:alert(1)` back.
   *
   * What closes it is renderMarkdown's step 1 escaping `&` across the whole document BEFORE the
   * link regex runs in step 3. Reorder those two steps and the guard opens with no other change.
   */
  describe('the escape ordering that closes the entity-encoded scheme', () => {
    const ENTITY_JS = '&#106;avascript&#58;alert(1)';

    it('escapes the ampersands so one level of HTML decoding yields inert text', () => {
      const html = renderMarkdown(`[click me](${ENTITY_JS})`);

      expect(html, 'the & must already be &amp; by the time the href is built — otherwise the '
        + 'browser decodes the attribute back to a javascript: scheme')
        .toContain('&amp;#106;');
      expect(html, 'a decodable entity-encoded scheme must not reach the attribute')
        .not.toContain('href="&#106;');
    });

    it('the scheme allowlist by itself does NOT reject it — the ordering is what does', () => {
      // Documents WHY the assertion above is the one that matters. The entity form carries no
      // colon, so the allowlist's relative-path fallback treats it as a relative path and lets it
      // through; it is only inert because step 1 already neutralised the ampersands.
      expect(ENTITY_JS.includes(':')).toBe(false);
      expect(href(renderMarkdown(`[click me](${ENTITY_JS})`))).not.toBe('#');
    });

    it('step 1 escapes before step 3 links, asserted structurally', () => {
      const src = readFileSync(
        join(__dirname, '..', 'pages', 'knowledge-base', 'KbArticlePage.tsx'), 'utf8');
      const escapeAt = src.indexOf(".replace(/&/g, '&amp;')");
      const linkAt = src.indexOf('const safe = sanitiseUrl(rawUrl)');

      expect(escapeAt, 'the &-escape pass has been renamed or removed').toBeGreaterThan(-1);
      expect(linkAt, 'the link-rewriting pass has been renamed or removed').toBeGreaterThan(-1);
      expect(escapeAt, 'escaping must precede link rewriting: the behavioural assertions above '
        + 'cannot tell you WHY they pass, and this is why')
        .toBeLessThan(linkAt);
    });
  });

  describe('the body escape itself', () => {
    it('neutralises a raw script tag in the article body', () => {
      const html = renderMarkdown('Hello <script>alert(1)</script> world');
      expect(html).not.toContain('<script>');
      expect(html).toContain('&lt;script&gt;');
    });

    it('neutralises an img onerror payload', () => {
      const html = renderMarkdown('![x](y) <img src=z onerror=alert(1)>');
      expect(html).not.toMatch(/<img/i);
    });
  });
});

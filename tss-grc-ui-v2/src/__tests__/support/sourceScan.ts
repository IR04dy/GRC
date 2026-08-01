/**
 * Shared source-scanning helpers for the tests that read this repo's own source.
 *
 * <h3>Why this file exists — a defect in the previous helper</h3>
 * Several tests in this programme assert over source text (F2-07b route reachability, F2-42
 * board-pack provenance, F2-68(c) API-client reachability). Three of them needed to strip
 * comments first, because this codebase deliberately <em>names</em> what it replaced in prose —
 * and a regex that counts a comment as code reports a defect that is not there. That mistake has
 * now happened four times in this programme, so the counting is centralised here.
 *
 * <p>The one-line stripper those tests used — strip block comments with a lazy match, then strip
 * line comments — was itself unsound. A <b>line</b> comment that contains a block-OPEN marker (for
 * example {@code // Wraps /api/sustainability/evidence/} followed by a star) opens a phantom block
 * comment, which then runs to the next block-CLOSE marker anywhere later in the file. Measured
 * against the sound version below, that one-liner mangles <b>23 files</b>: it cut
 * {@code lib/sustainabilityEvidence.ts} from 6,626 bytes to 223, {@code lib/socops.ts} from 28,598
 * to 2,843, and {@code App.tsx} from 87,393 to 47,438.
 *
 * <p>⚠ The direction of that failure is what makes it dangerous. Over-stripping does not produce
 * loud false positives; it silently empties the haystack, so <em>absence</em> assertions pass for
 * the wrong reason and a scan looking for dead code finds less of it. The F2-68(c) scan below was
 * blind to an entire unimported API client until this was fixed — the very class it was written to
 * find. (The F2-42 assertions were re-measured and are unaffected: both files it reads come out
 * byte-identical under either stripper. That is luck, not correctness, which is the point.)
 */
import { readdirSync, statSync } from 'node:fs';
import { join, posix, sep } from 'node:path';

/**
 * Strings and template literals FIRST, then block comments, then line comments — one ordered
 * alternation, so each construct consumes its own text and cannot be reinterpreted inside another:
 *
 * <ul>
 *   <li>A block-OPEN marker inside a string is matched as part of the string and kept.</li>
 *   <li>A block-OPEN marker inside a {@code //} line is reached at the {@code //} first, so the
 *       line rule consumes it.</li>
 *   <li>A {@code //} inside a block comment is reached at the block-OPEN first, so the block rule
 *       consumes it.</li>
 * </ul>
 *
 * Known limitation, stated rather than hidden: a regular-expression <em>literal</em> containing a
 * quote or a comment marker (e.g. {@code /['"]/}) can still be misread as the start of a string.
 * That is rare in this codebase and fails toward keeping code, not deleting it.
 */
const TOKENS = /'(?:[^'\\\n]|\\.)*'|"(?:[^"\\\n]|\\.)*"|`(?:[^`\\]|\\.)*`|\/\*[\s\S]*?\*\/|\/\/[^\n]*/g;

/** Source with comments removed and string/template contents preserved. */
export function stripComments(src: string): string {
  return src.replace(TOKENS, m => (m[0] === '/' ? '' : m));
}

/** Every `.ts`/`.tsx` file under `dir`, skipping `node_modules` and `dist`. */
export function walkSources(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) {
      if (name === 'node_modules' || name === 'dist') continue;
      walkSources(full, out);
    } else if (/\.tsx?$/.test(name)) {
      out.push(full);
    }
  }
  return out;
}

/** Path relative to `root`, always with forward slashes, so assertions read the same on any OS. */
export function relPosix(root: string, file: string): string {
  return file.slice(root.length + 1).split(sep).join(posix.sep);
}

/** A test file — never counts as a caller: a client only a test calls is unreached by the product. */
export function isTestFile(file: string): boolean {
  return file.includes('__tests__') || /\.test\.tsx?$/.test(file) || file.includes(`${sep}test${sep}`);
}

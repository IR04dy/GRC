package com.tss.grc.config;

import com.tss.grc.common.util.ExternalIds;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-416 — <b>an identifier built with {@code String.format("%d", …)} is not ASCII on every
 * JVM.</b>
 *
 * <p>Java's {@code Formatter} applies the Number Localization Algorithm to the {@code 'd'},
 * {@code 'f'}, {@code 'e'}, {@code 'E'}, {@code 'g'} and {@code 'G'} conversions, using
 * {@link Locale#getDefault()} when no locale is passed. Measured on this JDK:
 *
 * <pre>
 *   locale   %05d      %02x   %.2f     %,d
 *   en-US    00042     ab     1.50     1,234,567
 *   ar-SA    ٠٠٠٤٢     ab     ١٫٥٠     ١٬٢٣٤٬٥٦٧
 *   tr-TR    00042     ab     1,50     1.234.567
 *   de-DE    00042     ab     1,50     1.234.567
 * </pre>
 *
 * <p>So on an Arabic-locale JVM — the single most likely regional setting for this platform's
 * market — every reference code the platform mints carried Arabic-Indic digits, and on any
 * comma-decimal locale every formatted decimal carried a comma. {@code %x}/{@code %X} are
 * <b>not</b> localised, which is why the SHA-256 hex chain in
 * {@code NdiSubmissionPackageService} was never at risk and is deliberately not in this
 * population.
 *
 * <p><b>Why it is a correctness question and not hardening.</b> The swept population is
 * overwhelmingly <em>identifiers and protocol strings</em>, not display text:
 * <ul>
 *   <li>the {@code Set-Cookie} header for the refresh token — {@code Max-Age=٦٠٤٨٠٠} is not a
 *       valid cookie attribute, so the browser drops the attribute and the refresh flow breaks;</li>
 *   <li>the three STOMP destinations in {@code RealtimeBroadcaster} — the server would publish to
 *       {@code /topic/org.١.changed.vulnerability} while the frontend subscribes to
 *       {@code /topic/org.1.changed.vulnerability}, so no realtime frame ever arrives and nothing
 *       reports an error (F2-205's shape, reached by a locale rather than by a handler);</li>
 *   <li>the JSON <b>number</b> fields of the CMA Articles 91-94 annex
 *       ({@code "reportingYear": ٢٠٢٦}) — invalid JSON for the downstream consumers the file's own
 *       comment names;</li>
 *   <li>and every {@code MAX}-seed reference minter, whose SQL recovers the ordinal with
 *       {@code TRY_CAST(SUBSTRING(reference, …) AS INT)}. {@code TRY_CAST} of an Arabic-Indic
 *       numeral is {@code NULL}, so {@code ISNULL(MAX(…), 0)} resets the seed to 0 and the minter
 *       re-issues ordinal 1 — F2-337 / F2-353 / F2-354 / F2-355's collision defect, resurrected by
 *       a locale.</li>
 * </ul>
 *
 * <p><b>Human prose is deliberately left on the default locale</b> and named below. On an
 * Arabic-primary platform, Arabic-Indic digits inside Arabic prose may well be what a reader
 * wants, so forcing {@code Locale.ROOT} there is a product decision rather than a fix (F2-188).
 * That is also why {@code DbSizeMonitorJob}'s ops alert was <em>reverted</em> after the sweep
 * touched it: it is the same kind of thing as the other six, and exempting six of seven prose
 * sites while converting the seventh would have made the classification incidental rather than
 * principled.
 *
 * <p>⚠ <b>The behavioural leg carries its own negative control, and that is not decoration.</b>
 * Whether {@code ar-SA} uses Arabic-Indic digits is a CLDR datum that has changed between JDK
 * releases. Without the control, a JDK on which {@code ar-SA} resolves to Latin digits would make
 * {@code assertThat(id).isAscii()} pass while proving nothing — a green that is indistinguishable
 * from a working guard (F2-235).
 */
class LocaleSensitiveFormatContractTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    private static final String CALL = "String.format(";
    private static final String FORMATTED = ".formatted(";
    private static final Pattern LOCALE_ARG = Pattern.compile("\\s*(?:java\\.util\\.)?Locale\\.\\w+\\s*,\\s*");

    /** The conversions the Number Localization Algorithm rewrites. Deliberately excludes x/X/s/n. */
    private static final Pattern LOCALE_SENSITIVE =
        Pattern.compile("%[-+ 0,(#]*[0-9]*(?:\\.[0-9]+)?[dfeEgG]");

    /**
     * Format strings that may keep the default locale, keyed on the literal as it appears in
     * source. Every entry is human prose; none is an identifier, a protocol string or a filename.
     */
    private static final Map<String, String> PROSE = new LinkedHashMap<>();
    static {
        PROSE.put("Reporting year: %d%n",
            "PROSE — the annex's human-readable markdown header. Its JSON sibling two methods "
            + "away IS Locale.ROOT, because that one is a JSON number a machine parses.");
        PROSE.put("   [%-12s] %-6s score=%2d  %s%n",
            "PROSE — a fixed-width row in the annex's climate table, read by a person.");
        PROSE.put("Cloned from cycle %d (FY%d). Scores + evidence reset for the new year.",
            "PROSE — an operator-facing note stored on the cloned NDI cycle.");
        PROSE.put("%s channel: %d delivered, %d failed (of %d).%s",
            "PROSE — the crisis-communication delivery summary a coordinator reads.");
        PROSE.put("DB size %d MB reached %s threshold (>=%d MB, %.2f%% of 10 GB Express cap)",
            "PROSE — an ops alert sentence. The F2-416 sweep touched this and it was REVERTED: "
            + "it is the same kind as the other six, and converting one prose site while "
            + "exempting six would make the classification incidental rather than principled.");
        PROSE.put("%s %d — %s",
            "PROSE — the sustainability report's display title, in English and in Arabic. Two "
            + "sites share this literal; the report CODE beside them is Locale.ROOT.");
        PROSE.put("WorkflowSubmission %d not found for this tenant",
            "PROSE — an exception message. Two sites share it. Note String#formatted has no "
            + "locale overload at all, so a machine-readable string may never be built with it.");
    }

    // ── the invariant ────────────────────────────────────────────────

    @Test
    void noIdentifierOrProtocolStringIsBuiltWithADefaultLocaleNumericConversion() throws IOException {
        Scan scan = scan();

        List<String> violations = scan.defaultLocaleSites.stream()
            .filter(s -> !PROSE.containsKey(s.literal))
            .map(s -> s.file + "  ::  " + s.literal)
            .toList();

        assertThat(violations)
            .as("String.format / String#formatted applies the Number Localization Algorithm to "
                + "%%d/%%f/%%e/%%E/%%g/%%G using the DEFAULT locale. On an ar-SA JVM these produce "
                + "Arabic-Indic digits, which breaks a Set-Cookie Max-Age, a STOMP destination, a "
                + "JSON number and every MAX-seed reference minter (TRY_CAST of a non-Latin "
                + "numeral is NULL). Pass Locale.ROOT, or add the literal to PROSE with a reason.")
            .isEmpty();
    }

    /**
     * The reverse direction (F2-199): a PROSE entry must still name a site the scan finds.
     * Without it, a stale exemption is indistinguishable from a live one, and a detector that has
     * stopped matching looks exactly like a clean tree.
     */
    @Test
    void everyProseExemptionStillNamesASiteTheScanFinds() throws IOException {
        Scan scan = scan();
        Map<String, Integer> found = new TreeMap<>();
        for (Site s : scan.defaultLocaleSites) {
            found.merge(s.literal, 1, Integer::sum);
        }
        List<String> stale = PROSE.keySet().stream().filter(k -> !found.containsKey(k)).toList();
        assertThat(stale)
            .as("a PROSE exemption naming no site is an assumption wearing a citation — either the "
                + "site gained a Locale (delete the entry) or the detector stopped matching")
            .isEmpty();

        assertThat(PROSE)
            .as("every exemption must carry a substantial reason, not a bare label")
            .allSatisfy((literal, reason) -> assertThat(reason).hasSizeGreaterThan(60));
    }

    /**
     * Anti-vacuity. Every floor sits above the largest <b>wrong</b> answer (F2-215), and the
     * unresolved count is asserted at a hard zero because "unresolved" reads identically to
     * "clean" (F2-206).
     */
    @Test
    void theScanResolvesTheWholePopulation() throws IOException {
        Scan scan = scan();

        assertThat(scan.filesScanned)
            .as("the walk must reach the whole main tree — a root that stops resolving reports a "
                + "clean tree")
            .isGreaterThan(1_000);

        assertThat(scan.unresolved)
            .as("a String.format whose format string is a VARIABLE cannot be classified, and an "
                + "unclassified site scores exactly like a safe one — read it and make the format "
                + "a literal, or the population is no longer measured")
            .isEmpty();

        assertThat(scan.withExplicitLocale)
            .as("the sweep must still be in place; a corpus with no Locale.ROOT at all means the "
                + "detector, not the code, is what changed")
            .isGreaterThan(70);

        assertThat(scan.localeSensitiveLiterals)
            .as("the detector must still recognise locale-sensitive conversions")
            .isGreaterThan(70);
    }

    /**
     * The detector fires on what it claims and not on anything else. {@code %x}, {@code %s} and
     * {@code %n} are NOT subject to the localization algorithm — measured, not assumed — so
     * including them would make this gate fail on correct code (F2-214).
     */
    @Test
    void theDetectorFiresOnLocaleSensitiveConversionsOnly() {
        for (String yes : new String[] {"%d", "%05d", "%,d", "%.2f", "%f", "%e", "%E", "%g", "%G",
                                        "ISS-%d-%06d", "  \\\"reportingYear\\\": %d,%n"}) {
            assertThat(LOCALE_SENSITIVE.matcher(yes).find())
                .as("must detect %s", yes).isTrue();
        }
        for (String no : new String[] {"%s", "%n", "%02x", "%04X", "%b", "%%",
                                       "/topic/org.%s.changed.%s", "no conversions at all"}) {
            assertThat(LOCALE_SENSITIVE.matcher(no).find())
                .as("must NOT detect %s", no).isFalse();
        }

        // The comment stripper must hide a javadoc EXAMPLE. ExternalIds' own documentation shows
        // the broken F2-48 generator, `String.format("PREFIX-%d-%s", …)`, inside a <pre> block —
        // reading it raw is F2-193's epitaph-as-corpse and fails on a correct tree.
        String doc = " * <pre>{@code\n"
                   + " *   String.format(\"PREFIX-%d-%s\", year, x);\n"
                   + " * }</pre>\n"
                   + "String.format(Locale.ROOT, \"%s-%d\", a, b);\n";
        String stripped = strip("/*" + doc + "*/\nString.format(\"LIVE-%d\", n);\n");
        assertThat(stripped).doesNotContain("PREFIX-%d-%s");
        assertThat(stripped).contains("LIVE-%d");
        assertThat(stripped.split("\n", -1).length)
            .as("stripping must preserve newlines so line numbers survive")
            .isEqualTo(("/*" + doc + "*/\nString.format(\"LIVE-%d\", n);\n").split("\n", -1).length);
    }

    // ── the behavioural leg ──────────────────────────────────────────

    /**
     * The shared external-id primitive must be ASCII under a hostile locale. {@link ExternalIds}
     * is the one F2-48 introduced to replace nine copy-pasted generators, so it is the single
     * point through which nine registers' identifiers pass.
     *
     * <p>⚠ The negative control is what makes the assertion mean anything: it proves the chosen
     * locale really does rewrite digits on this JDK. Without it a CLDR change would silently turn
     * this leg into a tautology (F2-235).
     */
    @Test
    void theSharedExternalIdPrimitiveIsAsciiUnderAHostileLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA"));

            assertThat(isAscii(String.format("%05d", 42)))
                .as("NEGATIVE CONTROL — if ar-SA formats digits as ASCII on this JDK then the "
                    + "assertion below is vacuous and this gate is proving nothing")
                .isFalse();

            String id = ExternalIds.generate("CAPA");
            assertThat(isAscii(id))
                .as("ExternalIds.generate produced a non-ASCII identifier under ar-SA: %s", id)
                .isTrue();
            assertThat(id).startsWith("CAPA-").matches("CAPA-\\d{4}-[0-9A-F]+");
        } finally {
            Locale.setDefault(original);
        }
    }

    private static boolean isAscii(String s) {
        return s.chars().allMatch(c -> c < 128);
    }

    // ── scanning ─────────────────────────────────────────────────────

    private record Site(String file, String literal) {}

    /** A string literal in the source: {@code start} is its opening quote, {@code end} one past its closing one. */
    private record Lit(int start, int end, String body) {}

    /** Index every string literal in one left-to-right character pass. Char literals are skipped. */
    private static List<Lit> literals(String code) {
        List<Lit> out = new ArrayList<>();
        int i = 0;
        int n = code.length();
        while (i < n) {
            char c = code.charAt(i);
            if (c == '\'') {                                  // char literal — skip, never a format
                i++;
                while (i < n) {
                    if (code.charAt(i) == '\\') { i += 2; continue; }
                    if (code.charAt(i) == '\'' || code.charAt(i) == '\n') { i++; break; }
                    i++;
                }
                continue;
            }
            if (c != '"') { i++; continue; }
            int start = i;
            StringBuilder body = new StringBuilder();
            i++;
            while (i < n) {
                char d = code.charAt(i);
                if (d == '\\' && i + 1 < n) { body.append(d).append(code.charAt(i + 1)); i += 2; continue; }
                if (d == '"' || d == '\n') { i++; break; }
                body.append(d);
                i++;
            }
            out.add(new Lit(start, i, body.toString()));
        }
        return out;
    }

    private static Lit literalStartingAt(List<Lit> lits, String code, int from) {
        int i = from;
        while (i < code.length() && Character.isWhitespace(code.charAt(i))) i++;
        for (Lit l : lits) if (l.start() == i) return l;
        return null;
    }

    private static Lit literalEndingAt(List<Lit> lits, int before) {
        for (Lit l : lits) if (l.end() == before) return l;
        return null;
    }

    private static String line(String code, int at) {
        int a = code.lastIndexOf('\n', at) + 1;
        int b = code.indexOf('\n', at);
        String s = code.substring(a, b < 0 ? code.length() : b).trim();
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    private record Scan(int filesScanned,
                        int withExplicitLocale,
                        int localeSensitiveLiterals,
                        List<Site> defaultLocaleSites,
                        List<String> unresolved) {}

    /**
     * Blank out comments with a CHARACTER SCANNER, preserving both length and newlines.
     *
     * <p>⚠ Deliberately <b>not</b> {@code GuardExpressions.stripComments}, and not the
     * one-alternation regex F2-198 / F2-211 recommend for a Python census: Java's engine recurses
     * once per iteration of {@code [\s\S]*?}, so that pattern throws {@code StackOverflowError} on
     * this tree's larger files. It did here, on the first run — three of the five legs errored
     * while the two that do not walk the tree passed, which is the shape to recognise.
     * {@code MeasuredBaselineFreshnessTest} keeps its own scanner for exactly this reason and this
     * follows that precedent; consolidating the two into {@code support/} is filed, because
     * {@code GuardExpressions} has twenty importers and the regex form is the one they use.
     *
     * <p>A scanner also gets something the regex cannot: a comment delimiter <em>inside a string
     * literal</em> is not a comment. That matters here, because the population being measured IS
     * string literals — and one of them is
     * {@code "grc_refresh=%s; …Path=/api/auth/refresh; …"}, whose {@code //}-free but slash-bearing
     * text a naive scanner could mangle.
     */
    private static String strip(String src) {
        char[] out = src.toCharArray();
        int i = 0;
        int n = out.length;
        while (i < n) {
            char c = out[i];
            if (c == '"' || c == '\'') {                 // a literal: skip it, do not blank it
                char quote = c;
                i++;
                while (i < n) {
                    if (out[i] == '\\') { i += 2; continue; }
                    if (out[i] == quote || out[i] == '\n') { i++; break; }
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && out[i + 1] == '/') {
                while (i < n && out[i] != '\n') { out[i] = ' '; i++; }
                continue;
            }
            if (c == '/' && i + 1 < n && out[i + 1] == '*') {
                out[i] = ' ';
                out[i + 1] = ' ';
                i += 2;
                while (i < n) {
                    if (out[i] == '*' && i + 1 < n && out[i + 1] == '/') {
                        out[i] = ' ';
                        out[i + 1] = ' ';
                        i += 2;
                        break;
                    }
                    if (out[i] != '\n') out[i] = ' ';
                    i++;
                }
                continue;
            }
            i++;
        }
        return new String(out);
    }

    private static Scan scan() throws IOException {
        int files = 0;
        int withLocale = 0;
        int lsLiterals = 0;
        List<Site> defaults = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(MAIN)) {
            List<Path> javas = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            for (Path p : javas) {
                files++;
                String code = strip(Files.readString(p).replace("\r\n", "\n"));
                String rel = MAIN.relativize(p).toString().replace('\\', '/');

                // One pass to index every string literal, so nothing below has to regex over a
                // whole file. ⚠ The first draft used `"((?:[^"\\]|\\.)*)"` and threw
                // StackOverflowError: Java's engine recurses once per iteration of an
                // alternation inside `*`, and over a file-length input that is thousands of
                // frames. Three legs errored while the two that do not walk the tree passed —
                // and my first diagnosis blamed the comment stripper, because the recorded
                // F2-198 trap has the identical symptom. It was the LITERAL pattern.
                List<Lit> lits = literals(code);

                for (int i = code.indexOf(CALL); i >= 0; i = code.indexOf(CALL, i + 1)) {
                    int argStart = i + CALL.length();
                    Matcher loc = LOCALE_ARG.matcher(code).region(argStart, code.length());
                    boolean hasLocale = loc.lookingAt();
                    if (hasLocale) {
                        withLocale++;
                        argStart = loc.end();
                    }
                    Lit lit = literalStartingAt(lits, code, argStart);
                    if (lit == null) {
                        unresolved.add(rel + "  ::  " + line(code, i));
                        continue;
                    }
                    if (!LOCALE_SENSITIVE.matcher(lit.body()).find()) continue;
                    lsLiterals++;
                    if (!hasLocale) defaults.add(new Site(rel, lit.body()));
                }

                // String#formatted has NO locale overload, so it is ALWAYS default-locale.
                for (int i = code.indexOf(FORMATTED); i >= 0; i = code.indexOf(FORMATTED, i + 1)) {
                    Lit lit = literalEndingAt(lits, i);
                    if (lit == null) continue;   // .formatted on a variable — not a literal site
                    if (!LOCALE_SENSITIVE.matcher(lit.body()).find()) continue;
                    lsLiterals++;
                    defaults.add(new Site(rel, lit.body()));
                }
            }
        }
        return new Scan(files, withLocale, lsLiterals, defaults, unresolved);
    }

    // ── F2-417: the same question asked of CASE FOLDING ──────────────

    /**
     * <b>No backend case conversion may use the ambient deployment locale.</b>
     *
     * <p>A hard zero with <b>no exemption map</b>, and the argument is that there is no site
     * where "whatever locale the server happens to be configured with" is the answer. A backend
     * case conversion means one of exactly two things:
     * <ul>
     *   <li>Unicode-default folding for a token, a comparison or a key → {@code Locale.ROOT};</li>
     *   <li>folding for a human in a <em>named</em> language → that language, passed explicitly.</li>
     * </ul>
     * The ambient locale is neither, so there is no correct code for this to fail on (F2-214) and
     * therefore nothing to exempt.
     *
     * <p>Under {@code tr-TR}, {@code "i".toUpperCase()} is {@code İ} (U+0130) and
     * {@code "I".toLowerCase()} is {@code ı} (U+0131) — and nearly every token in this platform's
     * closed vocabularies contains an {@code i}: {@code IN_PROGRESS}, {@code CRITICAL},
     * {@code HIGH}, {@code MITIGATING}, {@code ACTIVE}, {@code IDENTIFIED},
     * {@code INVESTIGATING}, {@code PRIVACY}, {@code EFFECTIVE}. So a bare conversion on a
     * vocabulary path yields a value the column's CHECK refuses and a case-sensitive
     * {@code .equals} cannot match (F2-179's whole family, reached by a locale).
     */
    @Test
    void noBackendCaseConversionUsesTheAmbientLocale() throws IOException {
        List<String> bare = new ArrayList<>();
        int withLocale = 0;
        int files = 0;

        try (Stream<Path> walk = Files.walk(MAIN)) {
            for (Path p : walk.filter(x -> x.toString().endsWith(".java")).sorted().toList()) {
                files++;
                String code = strip(Files.readString(p).replace("\r\n", "\n"));
                String rel = MAIN.relativize(p).toString().replace('\\', '/');
                // ONE detector, shared with the self-test below, so the corpus assertion and
                // the synthetic assertion cannot disagree about what a bare conversion is.
                for (String kind : bareCaseConversionsIn(code)) {
                    bare.add(rel + "  ::  ." + kind + "()");
                }
                for (String kind : new String[] {"toUpperCase", "toLowerCase"}) {
                    int at = -1;
                    while ((at = code.indexOf("." + kind + "(", at + 1)) >= 0) {
                        int j = at + kind.length() + 2;
                        while (j < code.length() && Character.isWhitespace(code.charAt(j))) j++;
                        if (j < code.length() && code.charAt(j) != ')') withLocale++;
                    }
                }
            }
        }

        // The walk must have reached the tree at all: with no files, nothing below means
        // anything (F2-235). This is the ONE floor that legitimately precedes the claim.
        assertThat(files)
            .as("the walk must reach the whole main tree — a root that stops resolving reports a "
                + "clean tree")
            .isGreaterThan(1_000);

        // ⚠ The primary claim comes BEFORE the remaining floors, and that ordering is the
        // finding rather than a style choice. The first draft asserted `withLocale > 200`
        // first; on the pre-fix tree it short-circuited at "116 is not greater than 200",
        // so a reader saw *the detector changed* instead of *182 bare sites*. It was also
        // F2-414's own lesson unapplied: `withLocale > 200` can only hold BECAUSE of the
        // sweep, so it re-asserted the fix rather than asserting the detector. The detector
        // is asserted by the synthetic self-test below instead.
        assertThat(bare)
            .as("String#toUpperCase()/toLowerCase() with no argument folds against "
                + "Locale.getDefault(). Under tr-TR an 'i' becomes 'İ', so a vocabulary token, a "
                + "switch label, a map key or a column value silently stops matching. Pass "
                + "Locale.ROOT for a token, or a named language for human text — never the "
                + "ambient deployment locale.")
            .isEmpty();

        assertThat(withLocale)
            .as("with the tree clean there must still be locale-bearing conversions to have "
                + "counted — zero here would mean the detector reaches nothing")
            .isPositive();
    }

    /**
     * The case detector distinguishes the two forms. A corpus assertion passes both when the
     * tree is clean and when the scan has rotted, so this drives the same walk logic over
     * synthetic input in both directions (F2-195) — and it asserts the DETECTOR rather than
     * re-asserting the fix, which is what the removed {@code withLocale > 200} floor did
     * (F2-414).
     */
    @Test
    void theCaseDetectorFiresOnTheBareFormAndNotOnTheLocaleBearingOne() {
        assertThat(bareCaseConversionsIn("String x = s.toUpperCase();")).containsExactly("toUpperCase");
        assertThat(bareCaseConversionsIn("String x = s.toLowerCase(  );")).containsExactly("toLowerCase");
        assertThat(bareCaseConversionsIn("String x = s.toUpperCase(Locale.ROOT);")).isEmpty();
        assertThat(bareCaseConversionsIn("String x = s.toLowerCase(Locale.ENGLISH);")).isEmpty();
        assertThat(bareCaseConversionsIn("String x = s.toUpperCase(java.util.Locale.ROOT);")).isEmpty();
        // A commented-out example must not count — the stripper is what makes that true, and
        // this class's own javadoc quotes `toUpperCase()` while explaining the rule (F2-193).
        assertThat(bareCaseConversionsIn(strip("// s.toUpperCase();\nString x = y;"))).isEmpty();
    }

    /** The bare-conversion detector, extracted so the self-test above can drive it. */
    private static List<String> bareCaseConversionsIn(String code) {
        List<String> out = new ArrayList<>();
        for (String kind : new String[] {"toUpperCase", "toLowerCase"}) {
            int at = -1;
            while ((at = code.indexOf("." + kind + "(", at + 1)) >= 0) {
                int j = at + kind.length() + 2;
                while (j < code.length() && Character.isWhitespace(code.charAt(j))) j++;
                if (j < code.length() && code.charAt(j) == ')') out.add(kind);
            }
        }
        return out;
    }

}

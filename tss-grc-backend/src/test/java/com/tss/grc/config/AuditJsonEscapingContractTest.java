package com.tss.grc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F2-446 — a value interpolated into a hand-built JSON document must be escaped.
 *
 * <p>F2-434 filed this as <i>"~19 sites build audit JSON by string concatenation with only 4 files
 * declaring an escape helper … measured harmless today because nothing parses those two
 * columns"</i>. Re-derived (F2-221) the population is <b>16 concatenation sites in 13 files</b>, of
 * which two interpolate nothing a caller can influence, and the honest per-value classification is
 * the finding rather than the count:
 *
 * <ul>
 *   <li><b>one site is reachable through ordinary caller input</b> — a break-glass activation's
 *       {@code incidentRef}, which is {@code @NotBlank @Size(100)} with <b>no {@code @Pattern}</b>;</li>
 *   <li><b>five interpolate an exception message</b>, and a Jackson message routinely contains a
 *       quote ({@code Unrecognized field "x"}) and a newline;</li>
 *   <li><b>seven are guarded upstream</b> — a role name is {@code @Pattern("[A-Z][A-Z0-9_]*")}, a
 *       permission CSV is catalogue codes, the rest are numeric. F2-182: a guard sits in a DTO
 *       annotation, which no setter-site scan can see. Converted anyway, so the hazard is
 *       <i>absent</i> rather than latent (F2-213) — a {@code @Pattern} can be relaxed, and
 *       reachability is a fact about today rather than a property of a control.</li>
 * </ul>
 *
 * <p>⚠⚠ <b>"Nothing parses those columns" is a weaker defence here than usual, and that is what
 * settled the scope.</b> {@code platform.audit_log} is hash-chained (F2-31), so a malformed value
 * written there <b>cannot be repaired later</b> without breaking the seal — F2-434 established it
 * and F2-445 measured six ledger rows that must be left corrupted for exactly that reason.
 *
 * <h2>What the detector does, and the one thing it cannot see</h2>
 *
 * <p>An interpolation sits <b>inside a JSON string</b> exactly when the string literal immediately
 * before it ends with an escaped quote — that is the opening quote of a JSON value. Local,
 * adjacency-based, and validated against the pre-fix tree rather than argued: it reports exactly
 * the fourteen quoted-position interpolations F2-446 converted, and zero afterwards.
 *
 * <p>⚠ It says nothing about a <b>numeric</b> position (an interpolation whose preceding literal
 * ends with {@code :} rather than a quote). A text value there would be a different defect —
 * {@code {"userId":oops}} — and deciding it needs the expression's TYPE, which a source scan does
 * not have (F2-182). Measured by reading: all seven numeric-position interpolations in this corpus
 * are {@code Long}/{@code int}. Said out loud, because a hard zero over a population the instrument
 * cannot see is the most reassuring possible artefact (F2-205).
 */
@DisplayName("F2-446 — no hand-built JSON interpolates an unescaped value")
class AuditJsonEscapingContractTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    // ⚠⚠ There is deliberately NO regex for a Java string literal here. F2-418
    //    measured that an alternation inside a * recurses once per character consumed and
    //    throws StackOverflowError over whole-file input, and it does: written that way this
    //    gate overflowed INTERMITTENTLY -- it passed on the runs used to validate the fix and
    //    fell over in the harness's post-restore run. literalSpans() below is a character
    //    scanner, and ESCAPED_QUOTE is the two characters a JSON value opener ends with.
    private static final String ESCAPED_QUOTE = "\\\"";

    /** A literal that opens a JSON object: its body starts a brace then an escaped quote. */
    private static final Pattern JSON_OPENER = Pattern.compile("\\{\\s*\\\\\"");

    /** The three JSON escape helpers F2-446 pointed at the primitive, and the CSV one it did not. */
    private static final Map<String, Boolean> ESC_HELPERS = Map.of(
        "config/ExportAuditInterceptor.java", true,
        "module/iam/governance/service/AccessEvidencePackageService.java", true,
        "module/reporting/service/RegulatorPackageService.java", true,
        // ⚠ NOT a JSON escaper. AnnexExportService.esc is a CSV escaper carrying an OWASP
        //   formula-injection guard (a cell beginning = + - @ executes in Excel/Sheets). Pinned
        //   false so a later "make these consistent" sweep deletes the guard and fails here
        //   instead — F2-430's precedent, where two charsets had to be pinned apart.
        "module/sust/annex/service/AnnexExportService.java", false);

    // ------------------------------------------------------------------ the corpus

    private record Site(String file, String expr, String before) {}

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> s = Files.walk(MAIN)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String rel(Path p) {
        return MAIN.relativize(p).toString().replace('\\', '/')
            .replaceFirst("^com/tss/grc/", "");
    }

    /**
     * Comments blanked, newlines kept — a comment that quotes the shape it replaced would
     * otherwise read as the shape itself (F2-193), and offsets taken from a stripper that
     * removes lines do not refer to the file (F2-244).
     */
    static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {            // a delimiter inside a literal is not a comment
                int j = i + 1;
                while (j < n && src.charAt(j) != c) {
                    j += src.charAt(j) == '\\' ? 2 : 1;
                }
                out.append(src, i, Math.min(j + 1, n));
                i = j + 1;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int j = src.indexOf("*/", i + 2);
                j = j < 0 ? n : j + 2;
                for (int k = i; k < j; k++) {
                    out.append(src.charAt(k) == '\n' ? '\n' : ' ');
                }
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * Every interpolation that lands inside a JSON string, in a statement that builds JSON.
     *
     * <p>Package-private and static so the synthetic self-test can drive it over input the real
     * corpus does not contain — on a clean corpus an empty list is what a working detector and a
     * dead one both produce (F2-235).
     */
    static List<Site> quotedInterpolations(String file, String rawSource) {
        String src = stripComments(rawSource);
        List<Site> out = new ArrayList<>();
        for (int[] stmt : statements(src)) {
            List<int[]> lits = literalSpans(src, stmt[0], stmt[1]);
            boolean buildsJson = lits.stream()
                .anyMatch(s -> JSON_OPENER.matcher(src.substring(s[0], s[1])).find());
            if (!buildsJson) {
                continue;
            }
            for (int k = 0; k < lits.size(); k++) {
                int[] lit = lits.get(k);
                String body = src.substring(lit[0] + 1, lit[1] - 1);
                if (!body.endsWith(ESCAPED_QUOTE)) {
                    continue;                       // a numeric position, or not a value opener
                }
                int from = lit[1];
                int to = k + 1 < lits.size() ? lits.get(k + 1)[0] : stmt[1];
                String between = src.substring(from, Math.max(from, to));
                String bare = between.replace("+", "").replace(".append(", "")
                    .replace(")", "").replace(",", "").replace("\n", "").replace("\r", "")
                    .trim();
                if (bare.isEmpty()) {
                    continue;                       // literals joined directly, no interpolation
                }
                out.add(new Site(file, String.join(" ", between.trim().split("\\s+")), body));
            }
        }
        return out;
    }

    /** {@code [start, end)} of every statement, split on a semicolon outside a literal. */
    private static List<int[]> statements(String src) {
        List<int[]> out = new ArrayList<>();
        int start = 0;
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {
                i = endOfLiteral(src, i);
            } else if (c == ';') {
                out.add(new int[] {start, i});
                i++;
                start = i;
            } else {
                i++;
            }
        }
        if (start < n) {
            out.add(new int[] {start, n});
        }
        return out;
    }

    /** {@code [start, end)} of every {@code "..."} literal in {@code [from, to)}. */
    private static List<int[]> literalSpans(String src, int from, int to) {
        List<int[]> out = new ArrayList<>();
        int i = from;
        while (i < to) {
            char c = src.charAt(i);
            if (c == '"') {
                int e = endOfLiteral(src, i);
                out.add(new int[] {i, Math.min(e, to)});
                i = e;
            } else if (c == '\'') {
                i = endOfLiteral(src, i);
            } else {
                i++;
            }
        }
        return out;
    }

    /** The index just past the literal opening at {@code i}. */
    private static int endOfLiteral(String src, int i) {
        char q = src.charAt(i);
        int j = i + 1;
        int n = src.length();
        while (j < n && src.charAt(j) != q) {
            j += src.charAt(j) == '\\' ? 2 : 1;
        }
        return Math.min(j + 1, n);
    }

    private static boolean escaped(String expr) {
        return expr.contains("JsonText.") || expr.contains("esc(");
    }

    // ------------------------------------------------------------------ the legs

    @Test
    @DisplayName("no interpolation inside a hand-built JSON string is unescaped")
    void everyQuotedInterpolationIsEscaped() throws IOException {
        List<String> bad = new ArrayList<>();
        int total = 0;
        for (Path p : javaFiles()) {
            String src = Files.readString(p, StandardCharsets.UTF_8);
            for (Site s : quotedInterpolations(rel(p), src)) {
                total++;
                if (!escaped(s.expr())) {
                    bad.add("%s :: after \"…%s\" -> %s".formatted(
                        s.file(), s.before().length() > 28
                            ? s.before().substring(s.before().length() - 28) : s.before(),
                        s.expr().length() > 90 ? s.expr().substring(0, 90) + "…" : s.expr()));
                }
            }
        }
        assertThat(bad)
            .as("""
                A hard zero with NO exemption map, and that is safe rather than strict (F2-214):
                JsonText.escape is always available and escaping is never wrong, so there is no
                correct code for this leg to fail on. The one caller-reachable member was a
                break-glass activation's incidentRef, on a ledger that cannot be repaired.""")
            .isEmpty();

        // ⚠ Anti-vacuity. A hard zero over a detector that stopped matching is indistinguishable
        //    from a clean tree (F2-235), and the wrong answer here is ZERO, so the floor has to
        //    sit above it (F2-215). Measured 14 after F2-446's conversion.
        assertThat(total)
            .as("the detector must still be finding the quoted positions it adjudicates")
            .isGreaterThanOrEqualTo(12);
    }

    @Test
    @DisplayName("the three JSON esc() helpers delegate — and the CSV one must not")
    void theEscapeHelpersAgreeOnOneContractExceptTheCsvOne() throws IOException {
        for (Map.Entry<String, Boolean> e : ESC_HELPERS.entrySet()) {
            Path p = MAIN.resolve("com/tss/grc").resolve(e.getKey());
            assertThat(Files.exists(p)).as("%s must exist", e.getKey()).isTrue();
            String src = stripComments(Files.readString(p, StandardCharsets.UTF_8));
            int i = src.indexOf("private static String esc(String");
            assertThat(i).as("%s must declare esc(String)", e.getKey()).isGreaterThanOrEqualTo(0);
            String body = src.substring(i, Math.min(src.length(), src.indexOf('}', i) + 1));
            if (e.getValue()) {
                assertThat(body)
                    .as("""
                        %s :: esc must delegate. Before F2-446 the three JSON copies were TWO
                        different functions — one left a raw newline (invalid JSON), two replaced
                        newlines with spaces — and neither handled any other C0 character.""",
                        e.getKey())
                    .contains("JsonText.escape(");
            } else {
                assertThat(body)
                    .as("""
                        %s :: esc is a CSV escaper, not a JSON one, and must stay that way. It
                        carries the OWASP formula-injection guard — a cell beginning = + - @
                        executes as a formula when a regulator opens the annex — so a later
                        "make these consistent" sweep would delete a control. One name, three
                        contracts, which is the finding (F2-419/F2-190).""", e.getKey())
                    .doesNotContain("JsonText.")
                    .contains("=+-@");
            }
        }
    }

    @Test
    @DisplayName("⚠ the one caller-reachable site is named, so a revert is legible")
    void theBreakGlassIncidentRefIsEscaped() throws IOException {
        Path p = MAIN.resolve("com/tss/grc/module/iam/governance/service/BreakGlassService.java");
        String src = stripComments(Files.readString(p, StandardCharsets.UTF_8));
        assertThat(src)
            .as("""
                incidentRef is @NotBlank @Size(100) with no @Pattern, interpolated into the
                newValueJson of a BREAK_GLASS audit row — the highest-severity row the ledger
                supports, and the one an emergency privilege escalation is reviewed from.""")
            .contains("JsonText.escape(incidentRef)");
    }

    @Test
    @DisplayName("⚠ the detector fires on the pre-fix shape and not on the fixed one")
    void theDetectorFiresOnTheShapeItClaimsToCatch() {
        String preFix = """
            audit.recordEvent(new AuditEventBuilder()
                .newValueJson("{\\"userId\\":" + activator + ",\\"role\\":\\"X\\""
                    + ",\\"incidentRef\\":\\"" + incidentRef + "\\"}")
                .message("CRITICAL: " + activatorUsername));
            """;
        List<Site> hits = quotedInterpolations("Synthetic.java", preFix);
        assertThat(hits).as("the quoted position must be found").hasSize(1);
        assertThat(hits.get(0).expr()).contains("incidentRef");
        assertThat(escaped(hits.get(0).expr())).as("and must read as unescaped").isFalse();

        String fixed = preFix.replace("+ incidentRef +", "+ JsonText.escape(incidentRef) +");
        List<Site> after = quotedInterpolations("Synthetic.java", fixed);
        assertThat(after).hasSize(1);
        assertThat(escaped(after.get(0).expr())).as("and escaped once converted").isTrue();

        // must NOT flag: a numeric position, a non-JSON string, or a comment quoting the shape
        assertThat(quotedInterpolations("S.java",
            "x.json(\"{\\\"userId\\\":\" + activator + \"}\");"))
            .as("a numeric position is outside the population, and the reason is stated")
            .isEmpty();
        assertThat(quotedInterpolations("S.java",
            "log.info(\"activated by \" + who + \" at \" + when);"))
            .as("a string that is not JSON must not be adjudicated")
            .isEmpty();
        assertThat(quotedInterpolations("S.java",
            "// .newValueJson(\"{\\\"ref\\\":\\\"\" + incidentRef + \"\\\"}\")\nint x = 1;"))
            .as("F2-193: a comment quoting the retired shape must not read as the shape")
            .isEmpty();
    }
}

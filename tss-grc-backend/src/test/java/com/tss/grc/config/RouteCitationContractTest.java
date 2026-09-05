package com.tss.grc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-464 — <b>a refusal whose whole job is to send the caller somewhere is worthless if the
 * somewhere is invented.</b>
 *
 * <p><b>The defect.</b> {@code TprmService#requireScoreIsNotQuestionnaireOwned} refuses a manual
 * score on an assessment a questionnaire owns and names the path that owns it — the message read
 * {@code "Change an answer via POST /api/tprm/assessments/{id}/responses"}, which has <b>no
 * {@code v1} segment</b> and routes nowhere. Probed live as {@code compliance}: that path is
 * <b>404</b> on GET, POST and DELETE, while {@code /api/v1/tprm/assessments/{id}/responses}
 * answers <b>200</b> on GET and <b>405</b> on DELETE — the path lives, the verb does not. The
 * refusal itself fires (409) for a real population: <b>7 of 19</b> vendor assessments carry a
 * {@code template_id} in dev and prod. 🔑 {@code QuestionnaireController}'s own javadoc, one file
 * away, has always spelled the route correctly.
 *
 * <p><b>Why nothing saw it.</b> {@link RequestMappingBindabilityContractTest} asks whether a
 * mapping can <em>bind</em>; {@code FrontendEndpointContractTest} asks whether every endpoint an
 * API client calls has a controller. Neither reads the routes the platform names in its own
 * <em>messages</em>, and those are the strings an operator is told to act on. F2-243 found one
 * such citation wrong ({@code /api/sustainability/evidence/link} against the real
 * {@code /api/sustainability/evidence-links}) and F2-302 found a refusal naming a mechanism the
 * register did not have — both by hand, and nobody counted the population. It is <b>37 citations
 * in 27 files</b>.
 *
 * <p><b>The discriminator is the verb, and it is structural.</b> Measured over
 * {@code src/main/java}: <b>354</b> string literals mention {@code /api/}, and <b>317</b> of them
 * are route <em>declarations</em> — a {@code @RequestMapping} value, a {@code requestMatchers}
 * pattern, a CORS matcher, an external system's base path. A declaration is a bare path; a
 * <b>citation carries an HTTP verb</b>, because it is telling a caller what to do. Keying on the
 * verb is what separates <em>go here</em> from <em>this lives here</em>, and it is why the hard
 * zero below can ship with <b>no exemption map</b>: a correct path is always available, so there
 * is no correct code for it to fail on (F2-214).
 *
 * <p><b>What this asserts</b> — stated so the next reader can ask what is NOT here (F2-454):
 * <ol>
 *   <li>every verb-prefixed route citation resolves to a mapped {@code (verb, path)};</li>
 *   <li>none names an existing path under a verb that path does not serve (measured 0, asserted
 *       so a first one surfaces rather than blending into a tolerance);</li>
 *   <li>the detector separates a citation from a declaration, in both directions;</li>
 *   <li>a citation interpolated through a nested expression is read whole;</li>
 *   <li>the comment stripping is load-bearing;</li>
 *   <li>the population is measured, with floors above the largest <em>wrong</em> answer.</li>
 * </ol>
 * It cannot see a citation with no verb, a citation assembled at run time from a variable, or a
 * claim about a <em>mechanism</em> rather than a route ({@code "which walks the machine"} — the
 * shape F2-302 found false). Those are said out loud rather than left implied, because a hard zero
 * over a population the instrument cannot see is the most reassuring possible artefact (F2-205).
 *
 * <p><b>One route resolver, two readers.</b> The mapped set comes from
 * {@link RequestMappingBindabilityContractTest#mappings()} rather than a second walk of
 * {@code target/classes}: two resolvers would be free to disagree about which classes ship and how
 * a class-level path joins a method-level one, and the disagreement would be invisible because
 * each set looks complete on its own (F2-190).
 */
class RouteCitationContractTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    /**
     * ⚠ The verb is the whole narrowing. Without it this matches the 317 declarations too and the
     * gate fails on correct code.
     */
    static final Pattern CITATION =
        Pattern.compile("\\b(GET|POST|PUT|PATCH|DELETE)\\s+(/api/[^\\s\"',;)]*)");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^}]*\\}");

    record Citation(String file, String verb, String path, String raw) {}

    // ─── source reading ────────────────────────────────────────────────────

    /**
     * Blank comments in ONE left-to-right pass, entering string literals first so a {@code //}
     * inside a URL cannot open a comment (F2-418) and a {@code /*} inside prose cannot swallow the
     * rest of the file (F2-198). Offsets and newlines are preserved.
     */
    static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {
                int j = literalEnd(src, i);
                out.append(src, i, j);
                i = j;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                int j = src.indexOf('\n', i);
                if (j < 0) j = n;
                out.append(" ".repeat(j - i));
                i = j;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int j = src.indexOf("*/", i + 2);
                j = j < 0 ? n : j + 2;
                for (int k = i; k < j; k++) out.append(src.charAt(k) == '\n' ? '\n' : ' ');
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int literalEnd(String s, int start) {
        char q = s.charAt(start);
        int j = start + 1, n = s.length();
        while (j < n) {
            char c = s.charAt(j);
            if (c == '\\') { j += 2; continue; }
            if (c == q) return j + 1;
            if (c == '\n') return j;          // unterminated — Java forbids it, so stop here
            j++;
        }
        return n;
    }

    private record Lit(int start, int end, String text, int depth) {}

    /** String literals of one statement, each with its parenthesis depth. */
    private static List<Lit> literals(String stmt) {
        List<Lit> out = new ArrayList<>();
        int depth = 0, i = 0, n = stmt.length();
        while (i < n) {
            char c = stmt.charAt(i);
            if (c == '"') {
                int j = literalEnd(stmt, i);
                String body = j - 1 > i + 1 ? stmt.substring(i + 1, j - 1) : "";
                out.add(new Lit(i, j, body.replace("\\\"", "\""), depth));
                i = j;
            } else if (c == '\'') {
                i = literalEnd(stmt, i);
            } else {
                if (c == '(') depth++;
                else if (c == ')') depth--;
                i++;
            }
        }
        return out;
    }

    /**
     * The literals of one statement, joined back into the messages they compose.
     *
     * <p>⚠⚠ Literals are grouped <b>by parenthesis depth</b>, and that is not tidiness. A citation
     * that interpolates an id through a nested expression —
     * {@code "…POST /api/v1/bcm/exercises/" + (id == null ? "{id}" : id) + "/complete, …"} — puts
     * a string literal <em>inside</em> the interpolation. Read flat, the message breaks at that
     * literal and the citation truncates to {@code /api/v1/bcm/exercises}, which <b>is a real
     * route</b> ({@code BcmController#createExercise}) — so the scan resolves it and reports
     * clean. 🔑 A truncated citation resolves to a shorter real route: the quiet direction, and
     * exactly the shape {@link #aCitationInterpolatedThroughANestedExpressionIsReadWhole()} pins.
     * Grouping by depth blanks the nested literal, so the gap between the two outer literals reads
     * as one interpolation and the message joins as {@code …/exercises/{}/complete}.
     */
    static List<String> messagesIn(String stmt) {
        List<Lit> lits = literals(stmt);
        if (lits.isEmpty()) return List.of();

        char[] blanked = stmt.toCharArray();
        for (Lit l : lits) Arrays.fill(blanked, l.start(), Math.min(l.end(), blanked.length), ' ');
        String gaps = new String(blanked);

        List<String> out = new ArrayList<>();
        for (int depth : new TreeSet<>(lits.stream().map(Lit::depth).toList())) {
            StringBuilder buf = null;
            Lit prev = null;
            for (Lit l : lits) {
                if (l.depth() != depth) continue;
                if (buf != null) {
                    String gap = gaps.substring(prev.end(), l.start());
                    if (gap.matches("\\s*\\+\\s*")) {            // "a" + "b"
                        buf.append(l.text());
                        prev = l;
                        continue;
                    }
                    if (gap.matches("\\s*\\+[^+]*\\+\\s*")) {    // "a" + expr + "b"
                        buf.append("{}").append(l.text());
                        prev = l;
                        continue;
                    }
                    out.add(buf.toString());
                }
                buf = new StringBuilder(l.text());
                prev = l;
            }
            if (buf != null) out.add(buf.toString());
        }
        return out;
    }

    private static List<String> statements(String code) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) == ';') {
                out.add(code.substring(start, i + 1));
                start = i + 1;
            }
        }
        out.add(code.substring(start));
        return out;
    }

    /** Extracted so the self-tests can drive it over synthetic input (F2-235). */
    static List<Citation> citationsIn(String file, String source) {
        List<Citation> out = new ArrayList<>();
        for (String stmt : statements(stripComments(source))) {
            for (String msg : messagesIn(stmt)) {
                Matcher m = CITATION.matcher(msg);
                while (m.find()) {
                    out.add(new Citation(file, m.group(1).toLowerCase(Locale.ROOT),
                        normalise(m.group(2)), m.group(2)));
                }
            }
        }
        return out;
    }

    static String normalise(String p) {
        String s = p;
        while (!s.isEmpty() && ".,:;)".indexOf(s.charAt(s.length() - 1)) >= 0) {
            s = s.substring(0, s.length() - 1);
        }
        s = PLACEHOLDER.matcher(s).replaceAll("{}").replaceAll("/{2,}", "/");
        if (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    // ─── the corpora ───────────────────────────────────────────────────────

    private static List<Citation> corpus() {
        List<Citation> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(SOURCES)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".java")).sorted().toList()) {
                out.addAll(citationsIn(SOURCES.relativize(p).toString().replace('\\', '/'),
                    Files.readString(p, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** normalised path -> the verbs that path serves. */
    private static Map<String, Set<String>> routes() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (RequestMappingBindabilityContractTest.Mapping m
                 : RequestMappingBindabilityContractTest.mappings()) {
            out.computeIfAbsent(normalise(m.path()), k -> new LinkedHashSet<>()).add(m.verb());
        }
        return out;
    }

    private static boolean serves(Map<String, Set<String>> routes, Citation c) {
        Set<String> verbs = routes.get(c.path());
        return verbs != null && (verbs.contains(c.verb()) || verbs.contains("any"));
    }

    // ─── legs ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("every route a runtime message names is a route this platform serves")
    void everyCitedRouteResolves() {
        Map<String, Set<String>> routes = routes();
        List<String> broken = corpus().stream()
            .filter(c -> !routes.containsKey(c.path()))
            .map(c -> c.verb().toUpperCase(Locale.ROOT) + " " + c.path() + "  (" + c.file() + ")")
            .toList();

        assertThat(broken)
            .as("""
                A refusal that names a route is telling a caller where to go, so the route has to \
                exist. Correct the citation against the controller's own @RequestMapping — never \
                add an exemption: a correct path is always available, so nothing here can be right \
                and still fail. F2-464's own member was a missing 'v1' segment, which routed to a \
                404 while the correct path answered 200.""")
            .isEmpty();
    }

    @Test
    @DisplayName("no message names an existing path under a verb that path does not serve")
    void noCitationNamesAPathUnderAVerbItDoesNotServe() {
        Map<String, Set<String>> routes = routes();
        List<String> wrongVerb = corpus().stream()
            .filter(c -> routes.containsKey(c.path()) && !serves(routes, c))
            .map(c -> c.verb().toUpperCase(Locale.ROOT) + " " + c.path()
                + " — that path serves " + routes.get(c.path()) + "  (" + c.file() + ")")
            .toList();

        assertThat(wrongVerb)
            .as("""
                Measured 0 today and asserted rather than folded into the leg above, so the first \
                one reports as what it is — a message sending the caller to a real path with the \
                wrong verb, which answers 405 rather than 404 and therefore reads as a permission \
                problem.""")
            .isEmpty();
    }

    @Test
    @DisplayName("the detector separates a route CITATION from a route DECLARATION")
    void theDetectorSeparatesACitationFromADeclaration() {
        // Must flag: a message telling a caller where to go.
        assertThat(citationsIn("F.java",
            "class F { void f() { throw new IllegalStateException(\"Move it via POST /api/v1/x/{id}/go instead.\"); } }"))
            .extracting(Citation::verb, Citation::path)
            .containsExactly(org.assertj.core.api.Assertions.tuple("post", "/api/v1/x/{}/go"));

        // Must NOT flag: the four declaration shapes that make up the other 317 occurrences.
        assertThat(citationsIn("D.java", """
            class D {
              @RequestMapping("/api/v1/x")
              void a() { }
              void b() { http.requestMatchers("/api/scm/**"); }
              void c() { source.registerCorsConfiguration("/api/**", cfg); }
              void d() { client.uri(props.getBaseUrl() + "/api/now/table/incident"); }
            }
            """))
            .as("A declaration is a bare path. Widening this to bare paths makes the hard zero "
                + "fail on every @RequestMapping in the platform — a gate failing on correct code.")
            .isEmpty();
    }

    @Test
    @DisplayName("a citation interpolated through a nested expression is read whole")
    void aCitationInterpolatedThroughANestedExpressionIsReadWhole() {
        String bcmShape = """
            class B { void f() {
              throw new IllegalArgumentException("Supply executedDate, or use POST /api/v1/bcm/exercises/"
                + (id == null ? "{id}" : id) + "/complete, which stamps it.");
            } }
            """;
        assertThat(citationsIn("B.java", bcmShape))
            .as("""
                Read flat, the nested ternary's own literal breaks the message and the citation \
                truncates to /api/v1/bcm/exercises — which IS a real route, so the scan resolves \
                it and reports clean. That is the quiet direction: a truncated citation never \
                fails, it silently names something else.""")
            .extracting(Citation::path)
            .containsExactly("/api/v1/bcm/exercises/{}/complete");

        // The plain interpolation must still join (F2-454: a concatenated value defeats a regex
        // that only ever sees the first literal).
        assertThat(citationsIn("C.java",
            "class C { void f() { throw new E(\"via POST /api/v1/y/\" + id + \"/z now.\"); } }"))
            .extracting(Citation::path)
            .containsExactly("/api/v1/y/{}/z");
    }

    @Test
    @DisplayName("a documented route is not a cited one, and a comment cannot split a citation")
    void proseIsNotAClaimAndCannotBreakOne() {
        String documented = """
            class J {
              /**
               * Routes:
               *   POST /api/v1/totally/made/up/{id}/retired
               */
              void f() { }
            }
            """;
        assertThat(citationsIn("J.java", documented))
            .as("""
                This platform documents its routes in javadoc and records a retirement by quoting \
                the path it replaced, so reading prose as a claim makes the hard zero fail on a \
                CORRECT tree (F2-193). The property that stops it is stronger than the comment \
                stripping: the detector reads STRING LITERALS, and a comment contains none — which \
                is also why the 317 declaration occurrences and every javadoc route inventory are \
                invisible to it by construction.""")
            .isEmpty();

        // ⚠ So what IS the stripping for? Statement and paren integrity. A ';' inside a comment
        // between two concatenated literals splits the statement, the two halves stop joining, and
        // the citation truncates to a shorter path — which, as BcmService showed, is usually a
        // real route. The answers on the live corpus happen not to move without the stripping,
        // measured; this case is what makes it load-bearing rather than untested defence (F2-450).
        assertThat(citationsIn("K.java",
            "class K { void f() { throw new E(\"Use POST /api/v1/real/\" /* the id; then */ "
                + "+ id + \"/go.\"); } }"))
            .as("a semicolon inside a comment must not split the message it sits in")
            .extracting(Citation::path)
            .containsExactly("/api/v1/real/{}/go");
    }

    @Test
    @DisplayName("the population is measured")
    void thePopulationIsMeasured() {
        List<Citation> cites = corpus();
        Map<String, Set<String>> routes = routes();

        // ⚠ The floors sit above the largest WRONG answer, not above zero (F2-215). The wrong
        // answers here are: the detector stopped matching (0 citations), the walk resolved nothing
        // (0 routes), or the source root moved (0 files).
        assertThat(cites).as("verb-prefixed route citations").hasSizeGreaterThanOrEqualTo(30);
        assertThat(cites.stream().map(Citation::file).distinct().count())
            .as("files citing a route").isGreaterThanOrEqualTo(20);
        assertThat(routes).as("distinct mapped paths").hasSizeGreaterThanOrEqualTo(1_500);
        assertThat(RequestMappingBindabilityContractTest.mappings())
            .as("mapped (verb, path) pairs — matches the figure F2-439 records independently")
            .hasSizeGreaterThanOrEqualTo(2_000);
    }

    @Test
    @DisplayName("F2-464 — the corrected citation names the route that owns the score")
    void theCorrectedCitationIsPinned() {
        List<Citation> tprm = corpus().stream()
            .filter(c -> c.file().endsWith("tprm/service/TprmService.java"))
            .toList();

        assertThat(tprm)
            .as("""
                The refusal exists to name the path that owns a questionnaire-scored assessment. \
                It cited /api/tprm/assessments/{id}/responses — no v1 segment, 404 on every verb.""")
            .extracting(Citation::verb, Citation::path)
            .contains(org.assertj.core.api.Assertions.tuple(
                "post", "/api/v1/tprm/assessments/{}/responses"));
    }
}

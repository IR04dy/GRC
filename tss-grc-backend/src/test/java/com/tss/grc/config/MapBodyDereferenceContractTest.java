package com.tss.grc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F2-447 — a {@code Map.get} result may not be dereferenced without a guard for that key.
 *
 * <p>F2-394 established the family: {@code body.get(k).toString()} raises a
 * {@code NullPointerException} the moment a caller omits {@code k}, and an NPE matches no handler
 * but {@code @ExceptionHandler(Exception.class)}, so the route answers an <b>opaque 500</b> where a
 * 400 naming the field is due. These routes bind a raw {@code Map} deliberately — {@code @Valid}
 * fires <b>before</b> {@code @PreAuthorize}, so a bean-validated field would 400 an unauthorized
 * caller before the 403 — which is why the remedy is a manual check rather than a DTO.
 *
 * <p>F2-431 measured it over <i>controllers</i>: 54 binding a raw {@code Map}, 17 dereferences, 14
 * guarded, and <b>2 of its 3 candidates were its own classifier</b> missing the
 * {@code == null ? null : …} polarity. F2-447 widened the sweep to all of {@code src/main/java} —
 * a {@code Map} payload also reaches services (F2-175's {@code SystemIntakePort} takes one) — and
 * the answer is <b>29 dereferences, 28 guarded, 1 real</b>: exactly the site F2-431 filed.
 *
 * <p>🔑 <b>{@code x instanceof T} IS a null check — for the THEN branch.</b> That is precisely why
 * the survivor read as guarded: {@code body.get("value") instanceof Number n ? … : new
 * BigDecimal(body.get("value").toString())} dereferences null in the ELSE. A classifier that stops
 * at "is there an instanceof on this key" clears it.
 *
 * <p>⚠ And my own widening reproduced F2-431's error in a new spelling: it reported
 * {@code CommitteeMeetingPapersService}'s {@code entry.get("userId").asLong()} as unguarded,
 * because that receiver is a Jackson {@code JsonNode} and its containment test is
 * {@code has(...)}, not {@code containsKey(...)}. <b>Five polarities, and a classifier that knows
 * four of them reports a false positive rather than a smaller population</b> — the loud direction,
 * which is the only reason it cost one reading.
 *
 * <p>⚙ A hard zero with <b>no exemption map</b>, safe by F2-214's test: a guard is always
 * available and adding one is never wrong, so there is no correct code for this leg to fail on.
 */
@DisplayName("F2-447 — no Map.get result is dereferenced without a guard for that key")
class MapBodyDereferenceContractTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** {@code recv.get("key").method(} — the dereference of a keyed lookup. */
    private static final Pattern DEREF =
        Pattern.compile("(\\w+)\\.get\\(\\s*\"([^\"]+)\"\\s*\\)\\s*\\.\\s*(\\w+)\\s*\\(");

    /**
     * The five containment/nullability polarities in this tree. ⚠ Written as a list because
     * knowing four of five is what produced F2-431's 2-of-3 false-positive rate and my own
     * {@code has(...)} miss — so each is named, and the self-test drives every one.
     */
    private static final List<String> GUARDS = List.of(
        "\\.get\\(\\s*\"%s\"\\s*\\)\\s*!=\\s*null",          // != null
        "containsKey\\(\\s*\"%s\"\\s*\\)",                     // Map.containsKey
        "\\bhas\\(\\s*\"%s\"\\s*\\)",                          // JsonNode.has
        "\\.get\\(\\s*\"%s\"\\s*\\)\\s*==\\s*null\\s*\\?",     // == null ? … : …
        "getOrDefault\\(\\s*\"%s\"");                          // a non-null default

    // ------------------------------------------------------------------ the scan

    record Site(String file, String recv, String key, String method, String statement) {}

    /**
     * Comments blanked, newlines kept: a comment quoting the shape it replaced would otherwise
     * read as the shape itself (F2-193), and this very slice's fix carries one.
     */
    static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {
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
     * Every keyed dereference in the source, and whether any polarity guards that key nearby.
     *
     * <p>Package-private and static so the self-test can drive it over input the corpus does not
     * contain — on a clean corpus an empty list is what a working detector and a dead one both
     * produce (F2-235).
     */
    static List<Site> unguarded(String file, String rawSource) {
        String src = stripComments(rawSource);
        List<Site> out = new ArrayList<>();
        Matcher m = DEREF.matcher(src);
        while (m.find()) {
            String raw = m.group(2).replace("\\", "\\\\").replace("$", "\\$");
            int lo = Math.max(0, m.start() - 900);
            String window = src.substring(lo, Math.min(src.length(), m.end() + 200));
            boolean guarded = GUARDS.stream().anyMatch(g ->
                Pattern.compile(String.format(g, raw)).matcher(window).find());
            if (guarded) {
                continue;
            }
            int a = Math.max(src.lastIndexOf(';', m.start()), src.lastIndexOf('{', m.start()));
            int b = src.indexOf(';', m.end());
            String stmt = src.substring(a + 1, b < 0 ? src.length() : b).trim()
                .replaceAll("\\s+", " ");
            out.add(new Site(file, m.group(1), m.group(2), m.group(3),
                stmt.length() > 200 ? stmt.substring(0, 200) + "…" : stmt));
        }
        return out;
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> s = Files.walk(MAIN)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String rel(Path p) {
        return MAIN.relativize(p).toString().replace('\\', '/')
            .replaceFirst("^com/tss/grc/", "");
    }

    // ------------------------------------------------------------------ the legs

    @Test
    @DisplayName("no keyed dereference is unguarded, anywhere in main")
    void noKeyedDereferenceIsUnguarded() throws IOException {
        List<String> bad = new ArrayList<>();
        int total = 0;
        for (Path p : javaFiles()) {
            String src = Files.readString(p, StandardCharsets.UTF_8);
            if (!src.contains(".get(")) {
                continue;
            }
            total += countDereferences(src);
            for (Site s : unguarded(rel(p), src)) {
                bad.add("%s :: %s.get(\"%s\").%s()  ->  %s"
                    .formatted(s.file(), s.recv(), s.key(), s.method(), s.statement()));
            }
        }
        assertThat(bad)
            .as("""
                An NPE here is an opaque 500 where a 400 naming the field is due (F2-394). A hard
                zero with no exemption map: a guard is always available, so there is no correct
                code for this leg to fail on (F2-214).""")
            .isEmpty();

        // ⚠ Anti-vacuity. The wrong answer for this leg is ZERO — a detector that stopped matching
        //    reports exactly what a clean tree does (F2-235) — so the floor sits above it (F2-215).
        //    Measured 29 dereference sites across 302 files that mention .get(.
        assertThat(total)
            .as("the detector must still be finding the keyed dereferences it adjudicates")
            .isGreaterThanOrEqualTo(25);
    }

    private static int countDereferences(String rawSource) {
        Matcher m = DEREF.matcher(stripComments(rawSource));
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    @Test
    @DisplayName("⚠ the one real member is named, so a revert is legible")
    void theAppetiteExposureRouteRefusesAnAbsentValue() throws IOException {
        Path p = MAIN.resolve("com/tss/grc/module/risk/controller/RiskAppetiteController.java");
        String src = stripComments(Files.readString(p, StandardCharsets.UTF_8));
        assertThat(src)
            .as("""
                the route that raises a CRO/board appetite-breach escalation. It read
                `body.get("value") instanceof Number n ? … : new BigDecimal(body.get("value")
                .toString())`, and the instanceof is what made it look guarded.""")
            .contains("requiredDecimal(body, \"value\")")
            .doesNotContain("new BigDecimal(body.get(\"value\").toString())");
        assertThat(src)
            .as("and the refusal must NAME the field, which is the whole point of the fix")
            .contains("key + \" is required\"", "key + \" must be a number\"");
    }

    @Test
    @DisplayName("⚠ the detector fires on the pre-fix shape and on none of the five polarities")
    void theDetectorFiresOnTheShapeItClaimsToCatch() {
        String preFix = "BigDecimal v = body.get(\"value\") instanceof Number n"
            + " ? new BigDecimal(n.toString())"
            + " : new BigDecimal(body.get(\"value\").toString());";
        assertThat(unguarded("S.java", preFix))
            .as("""
                🔑 instanceof IS a null check — for the THEN branch. The ELSE dereferences null,
                which is why F2-431's classifier and mine both had to be told the difference.""")
            .hasSize(1);

        assertThat(unguarded("S.java", "String s = requiredDecimal(body, \"value\").toString();"))
            .as("and not on the fixed form")
            .isEmpty();

        // must NOT flag any of the five polarities — a classifier that knows four of five reports
        // a false positive, which is how my own widening read a JsonNode's has(...) as unguarded
        assertThat(unguarded("S.java",
            "String s = body.get(\"k\") != null ? body.get(\"k\").toString() : null;"))
            .as("!= null").isEmpty();
        assertThat(unguarded("S.java",
            "if (body.containsKey(\"k\")) { x = body.get(\"k\").toString(); }"))
            .as("Map.containsKey").isEmpty();
        assertThat(unguarded("S.java",
            "if (entry.has(\"userId\") && entry.get(\"userId\").asLong() == id) { hit = true; }"))
            .as("JsonNode.has — the polarity my own widening missed").isEmpty();
        assertThat(unguarded("S.java",
            "String s = body.get(\"k\") == null ? null : body.get(\"k\").toString();"))
            .as("== null ? … : … — the polarity F2-431's classifier missed").isEmpty();
        assertThat(unguarded("S.java",
            "String s = body.getOrDefault(\"k\", \"\").toString();"))
            .as("getOrDefault with a non-null default").isEmpty();

        // and a comment quoting the retired shape must not read as the shape (F2-193)
        assertThat(unguarded("S.java",
            "// was: new BigDecimal(body.get(\"value\").toString())\nint x = 1;"))
            .as("F2-193: the epitaph is not the corpse").isEmpty();
    }
}

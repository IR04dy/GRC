package com.tss.grc.module.intake;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-172 — the server's intake catalogue is a mirror of the frontend's, or it is a second opinion.
 *
 * <h3>Why this test exists rather than a comment saying "keep these in sync"</h3>
 * The comment existed. {@code IntakeService} opened with <i>"Routing metadata … is mirrored from
 * frontend INTAKE_CATALOG so the two stay in sync"</i>, and the two maps it described covered
 * <b>16</b> of the frontend's <b>26</b> kinds. Every HRSD (DD-88) and MOC (DD-94) kind was absent,
 * so each would have been refused as {@code "Unknown intake kind"} the moment a client posted one.
 * Nothing noticed for the same reason F2-171 went unnoticed: no product code called the endpoint,
 * so the drift had no observable consequence until the wiring landed.
 *
 * <p>⚙ The transferable half: <b>two copies of a routing table, where only one side is exercised,
 * do not drift slowly — they drift completely and silently.</b> The gate is cheap; the comment was
 * free and worth exactly that.
 *
 * <h3>The direction of authority</h3>
 * The frontend catalogue is the source: it is what the forms, the tiles and the triager inbox all
 * read, and it is where a new kind is added. {@code IntakeCatalog.java} is generated from it. So a
 * failure here means "regenerate the Java", not "edit the TypeScript to match".
 */
class IntakeCatalogParityTest {

    /** Repo-root-relative: surefire runs with the backend module as its working directory. */
    private static final Path FRONTEND_CATALOG =
        Path.of("..", "tss-grc-ui-v2", "src", "lib", "intake.ts");

    private record FeEntry(String codePrefix, int slaHours, String defaultTarget,
                           String defaultAssignee, String titleEn, String titleAr) {}

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Parse {@code INTAKE_CATALOG} out of {@code intake.ts}.
     *
     * <p>⚠ Scoped to the {@code INTAKE_CATALOG} literal, not to the file. This programme has had
     * five scans invent defects by matching prose — a javadoc phrase read as a class declaration
     * (F2-170), a regex literal read as code (F2-165) — and {@code intake.ts} is 700 lines of which
     * most is types, seed records and hooks. A parser that wanders outside its literal reports
     * differences that are not there, and a parser that finds nothing reports agreement it has not
     * checked, which is why the floor below is not optional.
     */
    private static Map<String, FeEntry> frontendCatalog() {
        String src = read(FRONTEND_CATALOG);
        int start = src.indexOf("export const INTAKE_CATALOG");
        assertThat(start).as("INTAKE_CATALOG is gone from intake.ts — repoint this test").isGreaterThan(0);
        int end = src.indexOf("\n};", start);
        assertThat(end).as("could not find the end of the INTAKE_CATALOG literal").isGreaterThan(start);
        String body = src.substring(start, end);

        Map<String, FeEntry> out = new LinkedHashMap<>();
        Matcher entry = Pattern.compile("^  (\\w+):\\s*\\{(.*?)\\n  \\},",
            Pattern.DOTALL | Pattern.MULTILINE).matcher(body);
        while (entry.find()) {
            String blk = entry.group(2);
            out.put(entry.group(1), new FeEntry(
                str(blk, "codePrefix"), hours(blk), str(blk, "defaultTarget"),
                str(blk, "defaultAssignee"), str(blk, "titleEn"), str(blk, "titleAr")));
        }
        return out;
    }

    private static String str(String blk, String field) {
        Matcher m = Pattern.compile("\\b" + field + ":\\s*'((?:[^'\\\\]|\\\\.)*)'").matcher(blk);
        return m.find() ? m.group(1).replace("\\'", "'") : null;
    }

    /** {@code slaHours: 30 * 24} — the literal is an expression, so evaluate the products. */
    private static int hours(String blk) {
        Matcher m = Pattern.compile("\\bslaHours:\\s*([0-9*\\s]+),").matcher(blk);
        if (!m.find()) return -1;
        int product = 1;
        for (String factor : m.group(1).split("\\*")) product *= Integer.parseInt(factor.trim());
        return product;
    }

    @Test
    void theParserActuallyFindsTheCatalogue() {
        // F2-69's lesson, and the one that keeps re-earning its place: a scan that matches nothing
        // makes every comparison below vacuously true. 26 is the measured population on 2026-08-09.
        assertThat(frontendCatalog())
            .as("the INTAKE_CATALOG parser matched nothing or almost nothing — it has broken, and "
              + "every agreement asserted below would be an agreement about an empty set")
            .hasSizeGreaterThan(25);
    }

    @Test
    void everyFrontendKindIsCataloguedOnTheServerWithTheSameRouting() {
        Map<String, FeEntry> fe = frontendCatalog();
        Map<String, String> mismatches = new LinkedHashMap<>();

        for (var e : fe.entrySet()) {
            IntakeCatalog.Entry be = IntakeCatalog.all().get(e.getKey());
            if (be == null) {
                mismatches.put(e.getKey(), "missing from IntakeCatalog.java — submitting it 400s "
                    + "with \"Unknown intake kind\", and promoting it has no target");
                continue;
            }
            FeEntry f = e.getValue();
            if (!f.codePrefix().equals(be.codePrefix()))
                mismatches.put(e.getKey(), "codePrefix " + be.codePrefix() + " != " + f.codePrefix());
            else if (f.slaHours() != be.slaHours())
                mismatches.put(e.getKey(), "slaHours " + be.slaHours() + " != " + f.slaHours());
            else if (!f.defaultTarget().equals(be.defaultTarget()))
                mismatches.put(e.getKey(), "defaultTarget " + be.defaultTarget() + " != " + f.defaultTarget()
                    + " — the server decides the promotion target, so this one silently promotes "
                    + "into the wrong register");
            else if (!f.defaultAssignee().equals(be.defaultAssignee()))
                mismatches.put(e.getKey(), "defaultAssignee " + be.defaultAssignee() + " != " + f.defaultAssignee());
            else if (!f.titleEn().equals(be.titleEn()))
                mismatches.put(e.getKey(), "titleEn differs");
            else if (!f.titleAr().equals(be.titleAr()))
                mismatches.put(e.getKey(), "titleAr differs — this is the string every promoted "
                    + "record's mandatory Arabic title is built from, so a stale one is visible");
        }

        assertThat(mismatches)
            .as("IntakeCatalog.java has drifted from tss-grc-ui-v2/src/lib/intake.ts. The frontend "
              + "catalogue is the source — regenerate the Java rather than editing it by hand:\n  %s",
                mismatches)
            .isEmpty();
    }

    @Test
    void theServerCataloguesNothingTheFrontendDoesNot() {
        // The other direction, and not symmetric with the one above: a server-only kind is a kind
        // no form can produce, so it would sit in the catalogue looking supported forever.
        assertThat(IntakeCatalog.all().keySet())
            .as("these kinds exist only on the server — no intake form can produce them")
            .isSubsetOf(frontendCatalog().keySet());
    }

    @Test
    void everyCatalogueEntryIsComplete() {
        // Cheap, and it catches the failure mode of the generator rather than of the source: a
        // regex that misses one field emits a null, and a null titleAr becomes a null Arabic title
        // on a promoted record, which is a NOT NULL violation surfacing from another module.
        for (var e : IntakeCatalog.all().entrySet()) {
            assertThat(e.getValue().codePrefix()).as("%s codePrefix", e.getKey()).isNotBlank();
            assertThat(e.getValue().slaHours()).as("%s slaHours", e.getKey()).isPositive();
            assertThat(e.getValue().defaultTarget()).as("%s defaultTarget", e.getKey()).isNotBlank();
            assertThat(e.getValue().defaultAssignee()).as("%s defaultAssignee", e.getKey()).isNotBlank();
            assertThat(e.getValue().titleEn()).as("%s titleEn", e.getKey()).isNotBlank();
            assertThat(e.getValue().titleAr())
                .as("%s titleAr — promoted records build their mandatory Arabic title from this",
                    e.getKey())
                .isNotBlank()
                .containsPattern("[\\u0600-\\u06FF]");
        }
    }
}

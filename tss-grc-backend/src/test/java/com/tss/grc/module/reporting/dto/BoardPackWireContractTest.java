package com.tss.grc.module.reporting.dto;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-42 / ADR-017 D-5 — the board pack the server sends is the board pack the screen shows.
 *
 * <h3>The defect this prevents from returning</h3>
 * <p>The platform had <b>two</b> board packs. {@code /board-pack} rendered a client-side
 * {@code buildBoardPack()} assembled from eight fabricated sources — three named
 * {@code mockCompliance}, {@code mockPrivacy}, {@code mockWorkflows}, one a function called
 * {@code synthesiseAllBreaches()} — while {@link BoardPackAggregate} was built from live
 * repositories and served as PDF and PPTX. The screen and the download disagreed, and the download
 * was right. F2-42 consolidated onto the server aggregate.
 *
 * <p><b>Consolidating once does not keep them consolidated.</b> The next field added to this record
 * is invisible to the screen, and the screen has no way to notice: JSON simply carries a key nobody
 * reads. Repeat that a few times and the page is again a partial, quietly-stale view of the real
 * pack — which is how the divergence arose in the first place, one section at a time. So this test
 * makes the record's shape and the frontend's declared shape the same fact.
 *
 * <h3>What is and is not asserted</h3>
 * <p>Asserted: every top-level component of {@link BoardPackAggregate}, and every component of each
 * nested record, appears as a field in {@code lib/boardPackAggregate.ts}. That is a <em>coverage</em>
 * claim about the wire contract, deliberately not a rendering claim — whether a figure reaches a
 * pixel is verified in the browser, and a test that pretended to check it from here would be
 * asserting something it cannot see.
 *
 * <p>Not asserted: TypeScript types. Java {@code long} against {@code number} is not a comparison
 * this can make honestly, and the interesting failure is a <em>missing</em> field rather than a
 * mistyped one.
 *
 * <p>Reading across the module boundary follows {@code AttachmentEntityTypeCoverageTest} and
 * {@code FrontendSkuVocabularyTest}: the invariant genuinely spans the two codebases, and the
 * alternative is the drift. Skips when the frontend is absent, with an anti-vacuity assertion so a
 * skip cannot read as a pass.
 */
class BoardPackWireContractTest {

    private static final Path TS =
        Path.of("..", "tss-grc-ui-v2", "src", "lib", "boardPackAggregate.ts");

    private static String frontendSource() {
        Assumptions.assumeTrue(Files.isRegularFile(TS),
            "frontend sources not present next to the backend — skipping the board-pack wire check");
        try {
            return Files.readString(TS, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + TS.toAbsolutePath(), e);
        }
    }

    /** Every record component name reachable from the aggregate, including nested records. */
    private static Set<String> wireFields() {
        Set<String> names = new LinkedHashSet<>();
        collect(BoardPackAggregate.class, names, new ArrayList<>());
        return names;
    }

    private static void collect(Class<?> type, Set<String> into, List<Class<?>> seen) {
        if (!type.isRecord() || seen.contains(type)) {
            return;
        }
        seen.add(type);
        for (RecordComponent rc : type.getRecordComponents()) {
            into.add(rc.getName());
            Class<?> t = rc.getType();
            if (t.isRecord()) {
                collect(t, into, seen);
            } else if (List.class.isAssignableFrom(t)) {
                // List<RiskRow> — the element type is only in the generic signature.
                String generic = rc.getGenericType().getTypeName();
                int lt = generic.indexOf('<');
                int gt = generic.lastIndexOf('>');
                if (lt > 0 && gt > lt) {
                    try {
                        collect(Class.forName(generic.substring(lt + 1, gt)), into, seen);
                    } catch (ClassNotFoundException ignored) {
                        // A type we cannot resolve cannot be asserted about either.
                    }
                }
            }
        }
    }

    @Test
    void everyFieldTheServerSendsIsDeclaredByTheFrontend() {
        String ts = frontendSource();
        Set<String> missing = new TreeSet<>();
        for (String field : wireFields()) {
            // `  fieldName: ` or `  fieldName?: ` in an interface body.
            if (!ts.matches("(?s).*\\b" + field + "\\??\\s*:.*")) {
                missing.add(field);
            }
        }

        assertThat(missing)
            .as("These fields of BoardPackAggregate are absent from "
                + "tss-grc-ui-v2/src/lib/boardPackAggregate.ts, so the board pack on screen is a "
                + "partial view of the one in the PDF — the F2-42 divergence, restarting. Add them "
                + "to the interface and render them, or, if the field genuinely is not board "
                + "material, take it off the aggregate so the PDF stops carrying it too.")
            .isEmpty();
    }

    /**
     * Anti-vacuity, in both directions. An empty {@code wireFields()} — the likely outcome if the
     * record is refactored into classes and {@code isRecord()} stops matching — makes the assertion
     * above pass while checking nothing at all.
     */
    @Test
    void theReflectionWalkStillSeesTheWholeAggregate() {
        Set<String> fields = wireFields();
        System.out.println("  board-pack wire fields: " + fields.size() + " " + fields);

        assertThat(fields.size())
            .as("field floor — measured 2026-08-01 as 10 top-level components across 10 nested "
                + "records; a collapse here means the walk broke, not that the pack shrank")
            .isGreaterThanOrEqualTo(60);
        // Spot-check one field from each nesting shape: top-level record, nested record,
        // and a record reached only through a List<> generic parameter.
        assertThat(fields).contains("header", "executive", "sustainability");   // top-level
        assertThat(fields).contains("criticalRisks", "totalOpen", "amlSuspicion"); // nested
        assertThat(fields).contains("reference", "daysOpen", "amberThreshold");  // via List<>
    }

    /**
     * The frontend file must actually be a wire contract rather than a stub — a nearly-empty file
     * satisfies the coverage assertion for nothing.
     */
    @Test
    void theFrontendContractIsNonTriviallyPopulated() {
        String ts = frontendSource();
        assertThat(ts).contains("/api/v1/reporting/board-pack.json");
        assertThat(ts.split("\\binterface\\b").length - 1)
            .as("interface count in boardPackAggregate.ts")
            .isGreaterThanOrEqualTo(9);
    }
}

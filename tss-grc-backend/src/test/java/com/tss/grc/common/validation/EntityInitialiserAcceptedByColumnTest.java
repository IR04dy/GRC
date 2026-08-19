package com.tss.grc.common.validation;

import com.tss.grc.support.MigrationVocabularies;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-241 — <b>an entity field initialiser is a one-token vocabulary, and nothing was checking it
 * against the column's own CHECK.</b>
 *
 * <h2>The defect</h2>
 *
 * <p>{@code scm.vendor_assessments} declared {@code private String status = "DRAFT"} while
 * {@code CK_scm_assess_status} admits {@code PLANNED | IN_PROGRESS | COMPLETED | ESCALATED}. A create
 * that lets the initialiser stand sends {@code 'DRAFT'} and the database refuses it — <b>HTTP 500 on
 * a SAMA outsourcing supplier assessment</b>. {@code scm.slas} said {@code status = "ON_TRACK"}
 * against {@code CK_scm_sla_status}'s {@code NOT_MEASURED | AT_RISK | BREACHED | MET}.
 *
 * <p>⚠ <b>The database DEFAULT does not rescue either of them.</b> Both columns carry one
 * ({@code 'PLANNED'}, {@code 'NOT_MEASURED'}) and the entity maps both insertable, so Hibernate names
 * the column in the INSERT and sends the initialiser's value — F2-240 measured that on
 * {@code tprm.vendor_assessments.status}, whose DB default is also {@code 'PLANNED'}. 🔑 <b>The
 * DEFAULT is still the useful artefact, though: it is the schema author's own statement of which
 * token the initial state should be, so where the entity and the DEFAULT disagree in the direction
 * the CHECK refuses, the entity is the one that is wrong.</b> Both fixes took the column's own
 * default.
 *
 * <h2>Why no existing instrument could see it</h2>
 *
 * <p>The platform has four vocabulary/column instruments and this population sits outside every one,
 * because <b>a field initialiser is never DECLARED as a vocabulary</b>:
 *
 * <ul>
 *   <li>{@code ClosedVocabularyPinTest} narrows on the source string {@code ClosedVocabulary.of(}
 *       and reflects on that field type — a bare initialiser is neither.</li>
 *   <li>{@code HandRolledVocabularyMirrorTest} checks six mirrors named in a list.</li>
 *   <li>{@code StateMachineColumnMirrorTest} keys on a named machine constant; a one-token default
 *       is not a machine.</li>
 *   <li>{@code NeverFiringNullGuardContractTest} (F2-240) keys on <b>entity-bound</b> updates, and
 *       {@code scm}'s PUT binds {@code AssessmentDtos.Request} — a purpose-built DTO — so the whole
 *       register was outside it by construction.</li>
 * </ul>
 *
 * <p>Ninth appearance of F2-198's shape, and the narrowing here is none of the previous ones (a
 * filename, an allow-list, a module name, an {@code extends} clause, a field-name list, a claim
 * shape): it is <b>the absence of a declaration</b>. A value nobody wrote down as a vocabulary is
 * still a vocabulary to the database.
 *
 * <h2>Why this reads the constructed object rather than the source</h2>
 *
 * <p>The reconnaissance for this slice was a source regex and needed three hand-written cases — the
 * {@code @Column} on the line above, the {@code @Column} column-aligned on the <em>same</em> line
 * (this codebase does that, e.g. {@code NdiFrameworkItem}), and an initialiser that is a
 * <b>constant reference</b> rather than a literal ({@code ChangeModelStatus.DRAFT}). Constructing the
 * entity and reading the field cannot be fooled by any of them, and it also resolves the four
 * entities whose {@code @Table} carries no {@code schema=} — which the regex walker skipped
 * entirely, i.e. reported as clean. F2-187's rule: measure from the runtime artefact, not from a
 * pattern over the text.
 *
 * <h2>Why a hard zero with no exemption map is safe</h2>
 *
 * <p>There is no correct code for it to fail on (F2-214). A value the column refuses is either a
 * create that cannot succeed, or an initialiser that is never sent and therefore states something
 * false about the register; in both cases the fix is to write a value the column accepts, and every
 * column with a CHECK has at least one. This is the rare invariant where the defect is not a
 * judgement call.
 */
class EntityInitialiserAcceptedByColumnTest {

    /** One entity field whose constructed default meets a CHECK on its own column. */
    private record Comparison(String entity, String schema, String table, String column,
                              String field, Object value, boolean accepted, String admissible,
                              boolean numeric) {
        String site() {
            return schema + "." + table + "." + column + "  (" + entity + "#" + field + ")";
        }
    }

    /** Entities the scan reached but could not construct — a skipped one scores as a clean one. */
    private record Unprobeable(String entity, String why) {}

    private static List<Comparison> comparisons;
    private static List<Unprobeable> unprobeable;
    private static int entitiesProbed;
    private static int noTable;
    private static int notInsertable;
    private static int testFixtures;
    private static final Set<String> noTableNames = new TreeSet<>();

    private static synchronized void measure() {
        if (comparisons != null) {
            return;
        }
        comparisons = new ArrayList<>();
        unprobeable = new ArrayList<>();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> type;
            try {
                type = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                unprobeable.add(new Unprobeable(bd.getBeanClassName(), "not loadable: " + t));
                continue;
            }
            if (!ships(type)) {
                testFixtures++;
                continue;
            }
            Table table = type.getAnnotation(Table.class);
            if (table == null) {
                noTable++;                       // an implicit table name — nothing to address
                noTableNames.add(type.getName());
                continue;
            }
            Object instance;
            try {
                var ctor = type.getDeclaredConstructor();
                ctor.setAccessible(true);
                instance = ctor.newInstance();
            } catch (Throwable t) {
                unprobeable.add(new Unprobeable(type.getName(), "no usable no-arg constructor"));
                continue;
            }
            entitiesProbed++;
            for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()
                        || f.isAnnotationPresent(Transient.class)) {
                        continue;
                    }
                    Object value;
                    try {
                        f.setAccessible(true);
                        value = f.get(instance);
                    } catch (Throwable t) {
                        continue;
                    }
                    if (value == null) {
                        continue;                // no initialiser — nothing to compare
                    }
                    Column col = f.getAnnotation(Column.class);
                    String column = col != null && !col.name().isBlank()
                        ? col.name() : snake(f.getName());
                    if (col != null && !col.insertable()) {
                        // The database fills it and Hibernate never names it in the INSERT
                        // (F2-215's fourth filling mechanism), so a refused value cannot reach the
                        // column. Counted rather than silently dropped: if this number is ever
                        // non-zero, the exclusion is doing work somebody should look at.
                        notInsertable++;
                        continue;
                    }
                    record(type, table, column, f, value);
                }
            }
        }
        publishEvidence();
    }

    /**
     * The whole measured corpus, written where a reader can audit it.
     *
     * <p>House idiom from F2-217's {@code target/mass-assignment-risk.txt} and F2-218's
     * {@code target/server-stamped-fields.txt}: the numbers this test floors are only meaningful if
     * somebody can see what they counted. A floor that passes over a narrowed population is the
     * failure mode (F2-215), and the file is what makes the narrowing visible without re-deriving it.
     */
    private static void publishEvidence() {
        StringBuilder sb = new StringBuilder();
        sb.append("F2-241 — every entity field initialiser that meets a CHECK on its own column\n")
          .append("entities constructed and walked : ").append(entitiesProbed).append('\n')
          .append("@Entity with no @Table          : ").append(noTable)
          .append("   ").append(noTableNames).append('\n')
          .append("test fixtures excluded          : ").append(testFixtures)
          .append("   (resolved by code source, not by name)\n")
          .append("unprobeable (no no-arg ctor)    : ").append(unprobeable.size()).append('\n')
          .append("initialisers on insertable=false: ").append(notInsertable)
          .append("   (the database fills these; a refused value cannot reach the column)\n")
          .append("comparisons vs a vocabulary     : ")
          .append(comparisons.stream().filter(c -> !c.numeric()).count()).append('\n')
          .append("comparisons vs a numeric range  : ")
          .append(comparisons.stream().filter(Comparison::numeric).count()).append('\n')
          .append("REFUSED                         : ")
          .append(comparisons.stream().filter(c -> !c.accepted()).count()).append("\n\n");
        comparisons.stream()
            .map(c -> (c.accepted() ? "ok       " : "REFUSED  ") + c.site() + " = "
                + describe(c.value()) + "   admits " + c.admissible())
            .sorted().forEach(l -> sb.append(l).append('\n'));
        try {
            java.nio.file.Files.writeString(
                java.nio.file.Path.of("target", "entity-initialiser-checks.txt"), sb.toString());
        } catch (Exception ignored) {
            // target/ absent only if this runs outside a build; the assertions do not depend on it
        }
    }

    private static void record(Class<?> type, Table table, String column, Field f, Object value) {
        String schema = table.schema();
        String name = table.name();
        if (name.isBlank()) {
            return;
        }
        String token = value instanceof Enum<?> e ? e.name()
            : value instanceof String s ? s : null;
        if (token != null) {
            Set<String> admissible = MigrationVocabularies.vocabularyOn(schema, name, column);
            if (admissible.isEmpty()) {
                return;
            }
            comparisons.add(new Comparison(type.getSimpleName(), schema, name, column, f.getName(),
                token, accepts(admissible, token), admissible.toString(), false));
            return;
        }
        BigDecimal number = value instanceof BigDecimal b ? b
            : value instanceof Number n && !(value instanceof Double) && !(value instanceof Float)
                ? new BigDecimal(n.toString()) : null;
        if (number == null) {
            return;
        }
        for (MigrationVocabularies.Range r : MigrationVocabularies.rangesOn(schema, name, column)) {
            comparisons.add(new Comparison(type.getSimpleName(), schema, name, column, f.getName(),
                number, r.admits(number), r.interval(), true));
        }
    }

    /**
     * The comparison, extracted so the case-sensitivity claim can be driven directly.
     *
     * <p>⚠ <b>Case-sensitive on purpose, and this is the one place that is right.</b> The columns
     * collate case-INSENSITIVELY, so the database would accept {@code 'planned'} and store it
     * verbatim, after which every Java comparison against the token is false and the frontend's
     * label map misses it (F2-179). An initialiser is a Java literal in a file somebody can edit, so
     * it is the one member of this family where the answer is to write the canonical casing rather
     * than to coerce at a boundary.
     *
     * <p>It exists as a method because the earlier draft asserted the vocabulary SET instead
     * ({@code admissible.contains("planned")} is false whatever the gate does with it) — near the
     * claim rather than the claim, which is the failure this register has recorded most often.
     */
    private static boolean accepts(Set<String> admissible, String token) {
        return admissible.contains(token);
    }

    private static String snake(String n) {
        return n.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * Whether this entity is part of the shipped jar rather than a test fixture.
     *
     * <p>⚠ <b>Found by printing the names instead of the count.</b> The first draft reported "1
     * @Entity with no @Table" and the count alone was unremarkable; the name was
     * {@code ServerOwnedFieldsInvariantTest$SyntheticUnprobeableEntity} — F2-215's own synthetic
     * fixture. {@code ClassPathScanningCandidateComponentProvider} scans the whole classpath, so it
     * reaches {@code target/test-classes}, and a hard zero over that population would fail on a
     * fixture a later slice deliberately builds to be refused. 🔑 <b>A count cannot tell you its
     * members are the ones you meant</b> (F2-235), and this instrument's own population was wrong in
     * a way only the listing showed.
     *
     * <p>Decided by <b>code source</b>, not by a name ending in {@code Test}: a fixture can be a
     * nested class of anything, and F2-192 recorded that a {@code setClock}-shaped name test lets any
     * member escape by being renamed.
     */
    private static boolean ships(Class<?> type) {
        try {
            var src = type.getProtectionDomain().getCodeSource();
            if (src == null || src.getLocation() == null) {
                return true;                      // cannot tell — count it rather than skip it
            }
            String where = src.getLocation().getPath().replace('\\', '/');
            return !where.contains("/test-classes");
        } catch (Throwable t) {
            return true;
        }
    }

    // ── the invariant ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("no entity's field initialiser is a value its own column's CHECK refuses")
    void noInitialiserIsRefusedByItsOwnColumnCheck() {
        measure();
        List<String> refused = comparisons.stream().filter(c -> !c.accepted())
            .map(c -> c.site() + " = " + describe(c.value()) + "  but the column admits "
                + c.admissible())
            .sorted().toList();

        assertThat(refused).as("""
            An entity field initialiser the column's own CHECK refuses. Whichever way this is
            reached it is wrong, which is why there is no exemption map:

              * if a create path lets the initialiser stand, the INSERT is refused -> HTTP 500, and
                the column's DB DEFAULT does not rescue it because the entity maps the column
                insertable, so Hibernate names it and sends this value (F2-240);
              * if no create path can reach it, the initialiser states something about the register
                that the register cannot hold.

            Take the value from the column's own DEFAULT where it has one - that is the schema
            author's statement of which token the initial state should be. Do NOT widen the CHECK to
            admit the initialiser: F2-238 measured what that costs when the narrowing was the thing
            that was wrong, and here the narrowing is what the live rows already agree with.
            """).isEmpty();
    }

    @Test
    @DisplayName("the two F2-241 sites are named, so a revert fails a leg and not only a count")
    void theTwoFixedSitesAreAssertedByName() {
        // F2-235's rule: a count can be satisfied by the wrong members, so name them. These are the
        // literal pre-fix values, kept in the assertion text so the diff that reintroduces one is
        // searchable.
        assertThat(new com.tss.grc.module.scm.entity.VendorAssessment().getStatus())
            .as("was \"DRAFT\", which CK_scm_assess_status refuses; the column's own DEFAULT is "
              + "'PLANNED' and 11 of 15 live rows in dev and prod are COMPLETED")
            .isIn(MigrationVocabularies.vocabularyOn("scm", "vendor_assessments", "status"));

        assertThat(new com.tss.grc.module.scm.entity.Sla().getStatus())
            .as("was \"ON_TRACK\", which CK_scm_sla_status refuses; the column's own DEFAULT is "
              + "'NOT_MEASURED' and the 14 live rows in dev and prod are MET/AT_RISK/BREACHED")
            .isIn(MigrationVocabularies.vocabularyOn("scm", "slas", "status"));

        // …and the rating that had NO initialiser at all on a NOT NULL column, which made the same
        // create answer 500 for an omitted key rather than a refused one.
        assertThat(new com.tss.grc.module.scm.entity.VendorAssessment().getRiskRating())
            .as("scm.vendor_assessments.risk_rating is NOT NULL with DB default 'MEDIUM' and had no "
              + "entity-side filling mechanism, so ScmService#applyAssessment's `!= null` guard let "
              + "an omitted key reach the column as an explicit null")
            .isIn(MigrationVocabularies.vocabularyOn("scm", "vendor_assessments", "risk_rating"));
    }

    // ── anti-vacuity: the shapes a wrong answer would take ─────────────────────────────────────

    @Test
    @DisplayName("the measurement is wide enough that a narrowed scan fails instead of passing")
    void theComparisonPopulationIsFloored() {
        measure();
        long vocab = comparisons.stream().filter(c -> !c.numeric()).count();
        long numeric = comparisons.stream().filter(Comparison::numeric).count();
        Set<String> schemas = new TreeSet<>();
        comparisons.forEach(c -> schemas.add(c.schema()));

        // Measured at F2-241: 432 shipped entities, 170 vocabulary comparisons across 22 schemas,
        // 13 numeric. A floor has to sit above the largest WRONG answer, not above zero (F2-215) —
        // and the wrong answers here are all narrowings that still return a plausible number:
        // walking only the entity's own declared fields, resolving only explicit @Column names, or
        // reaching one module's package.
        //
        // ⚙ Those 432 are the same 432 CLAUDE.md records for the platform, which is the corroboration
        // that makes the entity half of this measurement trustworthy. The source-regex reconnaissance
        // that found the two defects resolved only 415 and saw 169 comparisons — it missed 17
        // entities and the constant-reference initialiser shape. 🔑 It found the same two defects, and
        // "found the same defects" is not "measured the same population": the 18-item delta happened
        // to hide nothing this time, which is luck rather than method (F2-213, F2-214).
        assertThat(entitiesProbed).as("entities constructed and walked").isGreaterThanOrEqualTo(380);
        assertThat(vocab).as("initialiser-vs-CHECK-vocabulary comparisons actually made")
            .isGreaterThanOrEqualTo(150);
        assertThat(numeric).as("""
            initialiser-vs-range comparisons. This is the leg that proves the range half is
            load-bearing rather than decorative: F2-230 established that a range is not a vocabulary,
            and excluding ranges here would have shipped an invariant whose population was half its
            claim - a value out of a range CHECK produces the identical refused INSERT.
            """).isGreaterThanOrEqualTo(10);
        assertThat(schemas).as("a scan that reached one module would still return a plausible count")
            .hasSizeGreaterThanOrEqualTo(18);
    }

    @Test
    @DisplayName("every entity the scan reached was actually constructed and read")
    void nothingIsSilentlySkipped() {
        measure();
        // F2-215: a skipped entity scores exactly like a clean one, and F2-206's probe could not
        // reach 34 entities because of a field TYPE and recorded it as an observation rather than
        // drawing the edge. Anything unconstructable is named here instead.
        assertThat(unprobeable).as("entities with a @Table that could not be instantiated").isEmpty();
        assertThat(noTable).as("""
            @Entity types with no @Table at all. Measured 0 across all 432 shipped entities, so this
            is an invariant rather than a tolerance: such a type takes an IMPLICIT table name, which
            gives this gate no (schema, table) to address a CHECK by, and it would therefore sit
            outside the invariant while looking covered. Give a new entity an explicit @Table.
            """).isZero();
        assertThat(testFixtures).as("""
            Entities under target/test-classes, excluded because they do not ship. Exactly one today
            (F2-215's SyntheticUnprobeableEntity). This is asserted rather than ignored because the
            exclusion is load-bearing: a later slice building a deliberately-refused fixture would
            otherwise fail the hard zero, and a hard zero that fails on a fixture trains the next
            reader to edit the assertion (F2-214).
            """).isEqualTo(1);
    }

    // ── the synthetic self-test: does the detector fire? ───────────────────────────────────────

    @Test
    @DisplayName("the comparison itself fires on a refused value and not on a legal one")
    void theDetectorDiscriminates() {
        // F2-195's rule: the corpus assertions above pass both when the tree is clean and when the
        // comparison has rotted, so the comparison is driven directly against known values. These
        // are the two literal pre-fix defects and their fixes.
        Set<String> assess = MigrationVocabularies.vocabularyOn("scm", "vendor_assessments", "status");
        assertThat(assess.contains("DRAFT")).as("the pre-fix value must still be refused").isFalse();
        assertThat(assess.contains("PLANNED")).as("the fix must be admitted").isTrue();

        Set<String> sla = MigrationVocabularies.vocabularyOn("scm", "slas", "status");
        assertThat(sla.contains("ON_TRACK")).isFalse();
        assertThat(sla.contains("NOT_MEASURED")).isTrue();

        // A refused NUMBER, which is the half no earlier instrument in this family has ever checked.
        var weight = MigrationVocabularies.rangesOn("ndi", "compliance_assessment", "weight");
        assertThat(weight).as("ck_ndi_comp_weight must resolve, or the range leg compares nothing")
            .isNotEmpty();
        assertThat(weight.get(0).admits(new BigDecimal("11")))
            .as("11 is outside [0, 10] and must be reported refused").isFalse();
        assertThat(weight.get(0).admits(BigDecimal.ONE)).isTrue();
    }

    @Test
    @DisplayName("case matters here, because the collation does not")
    void aLowerCaseVariantIsRefused() {
        // F2-179: the columns collate case-INSENSITIVELY, so the database would ACCEPT 'planned' and
        // store it verbatim, and every Java comparison against the token would then be false. This
        // gate therefore compares case-sensitively on purpose — an initialiser is a Java literal and
        // it is the one place the casing can be fixed rather than coerced.
        Set<String> assess = MigrationVocabularies.vocabularyOn("scm", "vendor_assessments", "status");
        assertThat(accepts(assess, "planned"))
            .as("the GATE's own comparison must refuse a lower-case initialiser even though the "
              + "database's collation would accept it — asserted against accepts(), not against the "
              + "vocabulary set, because set membership is true of both a case-sensitive and a "
              + "case-insensitive comparison and would therefore prove neither")
            .isFalse();
        assertThat(accepts(assess, "PLANNED")).isTrue();
    }

    private static String describe(Object v) {
        return v instanceof String s ? '"' + s + '"' : String.valueOf(v);
    }

    /** Kept for the failure message: the whole measured corpus, printed on demand. */
    @SuppressWarnings("unused")
    private static String dump() {
        Set<String> lines = new LinkedHashSet<>();
        comparisons.forEach(c -> lines.add((c.accepted() ? "ok   " : "REFUSED ") + c.site()
            + " = " + describe(c.value()) + " in " + c.admissible()));
        return String.join("\n", lines);
    }
}

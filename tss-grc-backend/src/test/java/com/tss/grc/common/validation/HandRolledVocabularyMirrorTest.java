package com.tss.grc.common.validation;

import com.tss.grc.support.MigrationVocabularies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * F2-182 — the hand-rolled {@code Set<String>} vocabularies this slice deliberately did <b>not</b>
 * convert, checked against the {@code CHECK} constraints they mirror.
 *
 * <p><b>Why this class exists, and it is the durable half of the finding.</b> F2-182's register row
 * listed ten columns where "a caller's casing reaches a CHECK-constrained column and a case-sensitive
 * Java comparison then decides something", and named two as control bypasses. Re-measured by reading
 * the code, <b>six of the ten were already closed</b> and neither claimed bypass was reachable. The
 * scan that produced the list classified a writer as open whenever the setter's argument came from
 * the request — and a guard can sit in any of at least four places it cannot see:
 *
 * <ol>
 *   <li>a conjunction in the <em>same</em> {@code if} ({@code NdiComplianceAssessmentService.update});</li>
 *   <li>a shared {@code validate()} called by every mutation path ({@code KriDefinitionService},
 *       {@code CmaDisclosureService}, {@code TcfdItemService});</li>
 *   <li>an {@code if} block wrapping the setter ({@code BcmRiskService});</li>
 *   <li>a bean-validation annotation on the DTO ({@code SgiCreateRequest}) — which, as the calendar
 *       case showed, may itself be on a class no endpoint binds.</li>
 * </ol>
 *
 * <p>🔑 <b>"Is this writer closed?" is a dataflow question, and a regex over setter call sites cannot
 * answer it.</b> The measurable, reliable question is the one this class asks instead: <b>does the
 * Java vocabulary agree with the column's constraint?</b> That is a comparison between two artefacts
 * rather than an inference about control flow, and it catches the drift that actually bites — the
 * {@code AttachmentAccessPolicy} shape, where a list nobody was maintaining silently diverged from the
 * thing it mirrored (F2-52, F2-177, three occurrences).
 *
 * <p>⚙ The six closed sites keep their hand-rolled {@code Set}s rather than being converted. Two of
 * them ({@code ndi}, {@code kri}) <em>were</em> converted, because their reads gate a control and
 * "unreachable today" is not a property of a control. The remaining four fail <b>closed</b> — they
 * refuse a legitimate action rather than permitting an illegitimate one — so converting them would be
 * churn on eight modules of write paths in one commit, which the plan's own row names as the shape of
 * change this programme has repeatedly got wrong. What they get instead is this pin.
 */
class HandRolledVocabularyMirrorTest {

    /**
     * (declaring class, static field, the constraint it mirrors) for every hand-rolled vocabulary
     * this slice measured and left in place.
     *
     * <p>⚠ Two of these are deliberately <b>subsets</b> of their constraint rather than equal to it,
     * and the reason is recorded per entry: a Java set that is narrower than the column is a policy
     * decision, one that is <em>wider</em> is a latent 409 the caller cannot act on. Only the second
     * is a defect, so the assertion is directional.
     */
    private record Mirror(String className, String field, String constraint, String note) {}

    private static final List<Mirror> MIRRORS = List.of(
        new Mirror("com.tss.grc.module.bcm.cycle.service.BcmRiskService", "EFFECTIVENESS",
            "ck_bcm_risk_ce",
            "guarded by an if-block around the setter; feeds the residual-risk switch, where an "
            + "unmatched value falls to the default and mis-scores rather than bypassing anything"),
        new Mirror("com.tss.grc.module.sust.cma.service.CmaDisclosureService", "STATUSES",
            "ck_cma_status",
            "validate() runs on create, update and transition; the three-way !equals gate fails "
            + "closed — it refuses a legitimate action rather than permitting an illegitimate one"),
        new Mirror("com.tss.grc.module.sust.tcfd.service.TcfdItemService", "STATUSES",
            "ck_tcfd_status", "identical shape to the CMA one, same fail-closed direction"),
        new Mirror("com.tss.grc.module.sust.sgi.service.SgiCommitmentService", "STATUSES",
            "ck_sgi_status", "lifecycle statuses; every writer passes a literal"),
        new Mirror("com.tss.grc.module.sust.sgi.service.SgiCommitmentService", "SECTORS",
            "ck_sgi_sector", "checked in validate() on both create and update"),
        new Mirror("com.tss.grc.module.risk.kri.service.KriDefinitionService", "FREQUENCIES",
            "CK_kri_def_frequency",
            "still hand-rolled: nothing reads frequency back with a case-sensitive comparison, so "
            + "there is no behaviour for a casing mismatch to change")
    );

    @Test
    @DisplayName("every hand-rolled vocabulary is a subset of the CHECK constraint it mirrors")
    void noHandRolledSetOffersATokenItsColumnWouldRefuse() throws Exception {
        List<String> problems = new ArrayList<>();
        int compared = 0;

        for (Mirror m : MIRRORS) {
            Set<String> java = readStaticSet(m.className(), m.field());
            Set<String> sql = MigrationVocabularies.valuesOf(m.constraint());
            compared++;

            Set<String> onlyInJava = new TreeSet<>(java);
            onlyInJava.removeAll(sql);
            if (!onlyInJava.isEmpty()) {
                problems.add(String.format(
                    "%s.%s offers %s, which %s does not allow — the service would accept the value "
                    + "and the INSERT would then fail with a 409 the caller cannot act on.%n    note: %s",
                    simple(m.className()), m.field(), onlyInJava, m.constraint(), m.note()));
            }
        }

        // Anti-vacuity: a reflection walk that finds nothing passes every assertion below it. This is
        // the leg F2-179's scan lacked when `\b(IN|=)\b` matched nothing and reported 0 of 389.
        org.assertj.core.api.Assertions.assertThat(compared)
            .as("the mirrors must actually have been read and compared")
            .isEqualTo(MIRRORS.size())
            .isGreaterThanOrEqualTo(6);

        org.assertj.core.api.Assertions.assertThat(problems).as(
            "A hand-rolled Set that is WIDER than its column is the F2-180 defect from the Java side. "
            + "Fix it by correcting the Set or widening the CHECK by migration — and if the site is "
            + "being touched anyway, prefer converting it to a ClosedVocabulary, which is checked in "
            + "both directions by ClosedVocabularyPinTest.%n%n%s",
            String.join("\n\n", problems))
            .isEmpty();
    }

    /**
     * The complement, reported rather than enforced: where the Java set is <em>narrower</em> than the
     * column, that is a deliberate policy choice and must stay visible so nobody "fixes" it by
     * widening the set to match the schema.
     */
    @Test
    @DisplayName("a Java set narrower than its column is policy, and is stated rather than failed")
    void narrowerThanTheColumnIsAChoiceNotADefect() throws Exception {
        for (Mirror m : MIRRORS) {
            Set<String> java = readStaticSet(m.className(), m.field());
            Set<String> sql = MigrationVocabularies.valuesOf(m.constraint());

            org.assertj.core.api.Assertions.assertThat(java).as(
                "%s.%s must not be empty — an empty set refuses every value and would make the "
                + "subset assertion above pass on nothing", simple(m.className()), m.field())
                .isNotEmpty();
            org.assertj.core.api.Assertions.assertThat(sql).as(
                "%s must parse to a real vocabulary, or the comparison is against an empty set",
                m.constraint())
                .isNotEmpty();

            Set<String> onlyInSql = new TreeSet<>(sql);
            onlyInSql.removeAll(java);
            if (!onlyInSql.isEmpty()) {
                System.out.printf(
                    "F2-182 · %s.%s is narrower than %s: the column also allows %s%n           (%s)%n",
                    simple(m.className()), m.field(), m.constraint(), onlyInSql, m.note());
            }
        }
    }

    /** Read a {@code private static Set<String>} without requiring the service to expose it. */
    @SuppressWarnings("unchecked")
    private static Set<String> readStaticSet(String className, String fieldName) throws Exception {
        Field f = Class.forName(className).getDeclaredField(fieldName);
        f.setAccessible(true);
        Object value = f.get(null);
        if (!(value instanceof Set<?>)) {
            throw new AssertionError(
                className + "." + fieldName + " is not a Set — this test's model of the site is stale, "
                + "which is itself worth knowing: it probably became a ClosedVocabulary, in which case "
                + "delete the entry here and let ClosedVocabularyPinTest own it.");
        }
        return (Set<String>) value;
    }

    private static String simple(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}

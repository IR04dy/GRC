package com.tss.grc.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-30 bilingual-invariant RATCHET — `CLAUDE.md` house rule 7 says *"user-visible records
 * carry `*_en` **and** `*_ar`; Arabic names are backend-mandatory"*. This test is the first
 * thing in the platform that checks it.
 *
 * <p><b>What was measured (2026-07-26, dev, all 3 tenants).</b> Pairing every entity field
 * ending {@code En} with its {@code Ar} sibling gives <b>574 pairs</b>, of which <b>157 were
 * asymmetric</b> — {@code *_en} mandatory, {@code *_ar} free. And <b>zero entities in the
 * platform carried a single {@code @NotBlank}</b> when this test was written. A documented
 * platform invariant that no layer checked, which is what the assessment's F-007 recorded and
 * what {@code ChangeRequest.titleEn nullable=false} beside a bare {@code titleAr} illustrates.
 *
 * <p><b>Why this is a ratchet and not an assertion of zero.</b> The constraint cannot simply
 * be added to all 157. Bean validation fires on <em>update</em> as well as insert, so
 * annotating a column that already holds blank Arabic makes every existing such record
 * <em>uneditable</em> until someone supplies the Arabic — and nothing can invent Arabic
 * governance text. Measured: <b>35 of the 157 pairs hold 293 blank-Arabic rows</b> between
 * them, worst {@code Risk.titleAr} at 66 of 132 and {@code RiskTreatment.descriptionAr} at 53
 * of 54. Those are enumerated in {@link #DATA_BLOCKED} with their counts so the debt is
 * visible and quantified rather than silently excused; the other 122 are enforceable whenever
 * their write paths are touched.
 *
 * <p><b>Take the reflection numbers, not a grep's.</b> A source-level regex over the entity
 * files reported 103 asymmetric pairs and 375 total; both were wrong, because it could not
 * associate an annotation with its field across javadoc and multi-line {@code @Column}
 * declarations. Reflection reads the annotations the JVM will actually enforce. The same
 * correction had to be made in F2-27 the same day, in the same direction — a grep under-counts
 * and reads as reassurance.
 *
 * <p>Re-measure the blank counts with (the offender lines print the table for exactly this):
 * <pre>
 *   SELECT COUNT(*), SUM(CASE WHEN ar IS NULL OR LTRIM(RTRIM(ar)) = '' THEN 1 ELSE 0 END)
 *     FROM schema.table;
 * </pre>
 *
 * @see com.tss.grc.security.ExportAuthorizationRatchetTest the sibling ratchet from F2-27
 */
class BilingualInvariantRatchetTest {

    /**
     * Asymmetric pairs remaining. **MUST only ever decrease.** 157 measured 2026-07-26 —
     * already net of tranche 1, which annotated 8 fields across 7 entities (so the pre-slice
     * number was 165), all measured at zero blank rows — {@code ChangeRequest.titleAr} (the starting point F2-30 names),
     * {@code ChangeTask.titleAr}, {@code ChangeFreezeWindow.nameAr}, {@code Committee.nameAr},
     * {@code CommitteeResolution.titleAr} + {@code bodyAr},
     * {@code CommitteeActionItem.titleAr} and {@code CommitteeMeetingPaper.titleAr} — the
     * change and committee governance backbone this branch has been hardening, so a board
     * resolution can no longer be recorded in English only.
     *
     * <p>Do NOT raise this to make a new entity pass. Add {@code @NotBlank} to the {@code *_ar}
     * field instead — and check the column's blank-row count first, because on a populated
     * column the constraint blocks edits to existing records.
     */
    private static final int BASELINE_ASYMMETRIC_PAIRS = 157;

    /**
     * The 35 pairs whose columns already hold blank Arabic, with the row counts measured
     * 2026-07-26 as {@code blank / total}. Annotating any of these strands live records, so
     * each needs a tenant-side data fix before the constraint can land — that is a product
     * task, not an engineering one, and it is why these are listed rather than annotated.
     *
     * <p>The concentration is not random. Blank-Arabic rows by schema: {@code risk} 130,
     * {@code sust} 74, {@code evidence} 49, {@code policy} 13, {@code privacy} 10, {@code bcm}
     * 6, then single digits. {@code risk} is the platform's oldest module and
     * {@code Risk.titleAr} alone is half the risk register — which is the useful signal: the
     * Arabic gap tracks module age, so the invariant was never enforced at any point, and the
     * newest modules conform only because their seed data happened to be bilingual.
     */
    private static final Map<String, String> DATA_BLOCKED = Map.ofEntries(
        Map.entry("Risk.titleAr", "66/132"),
        Map.entry("RiskTreatment.descriptionAr", "53/54"),
        Map.entry("EvidenceRequest.titleAr", "26/44"),
        Map.entry("EvidenceItem.titleAr", "23/104"),
        Map.entry("GhgEmissionRecord.sourceNameAr", "22/41"),
        Map.entry("CmaDisclosure.titleAr", "13/91"),
        Map.entry("PolicyDocument.titleAr", "13/51"),
        Map.entry("TcfdItem.titleAr", "8/85"),
        Map.entry("EmissionFactor.titleAr", "6/6"),
        Map.entry("RiskAcceptance.justificationAr", "5/5"),
        Map.entry("RiskAppetiteStatement.titleAr", "5/9"),
        Map.entry("CorrectiveAction.titleAr", "4/32"),
        Map.entry("PrivacyNotice.purposeAr", "4/7"),
        Map.entry("SgiCommitment.titleAr", "4/17"),
        Map.entry("BcpPlan.titleAr", "3/10"),
        Map.entry("ClimateRiskOpportunity.titleAr", "3/20"),
        Map.entry("ReportingYear.reportingEntityAr", "3/8"),
        Map.entry("SanctionsListEntry.primaryNameAr", "3/15"),
        Map.entry("SustainabilityEvidence.titleAr", "3/21"),
        Map.entry("User.fullNameAr", "3/7"),
        Map.entry("CrisisEvent.titleAr", "2/7"),
        Map.entry("CrossBorderTransfer.justificationAr", "2/9"),
        Map.entry("DisposalCertificate.scopeDescriptionAr", "2/7"),
        Map.entry("EsgMetric.metricNameAr", "2/26"),
        Map.entry("MaterialTopic.topicAr", "2/28"),
        Map.entry("PrivacyNotice.bodyAr", "2/7"),
        Map.entry("TcfdSectorImpact.sectorNameAr", "2/20"),
        Map.entry("Vendor.nameAr", "2/51"),
        Map.entry("BowTieAnalysis.topEventTitleAr", "1/2"),
        Map.entry("DisasterRecoveryPlan.titleAr", "1/17"),
        Map.entry("GreenInstrument.nameAr", "1/2"),
        Map.entry("Issue.titleAr", "1/13"),
        Map.entry("QuestionnaireQuestion.questionAr", "1/23"),
        Map.entry("RootCauseAnalysis.problemStatementAr", "1/12"),
        Map.entry("TcfdClimateScenario.titleAr", "1/8"));

    @Test
    void theBilingualAsymmetryOnlyEverShrinks() {
        List<String> asymmetric = scanAsymmetricPairs();
        Collections.sort(asymmetric);
        System.out.println("[F2-30 bilingual ratchet] asymmetric *_en/*_ar pairs = " + asymmetric.size());
        asymmetric.forEach(s -> System.out.println("  - " + s));

        assertThat(asymmetric.size())
            .as("An entity has a mandatory *_en field beside an optional *_ar field, which "
                + "breaks CLAUDE.md house rule 7 (Arabic names are backend-mandatory). Add "
                + "@NotBlank to the *_ar field — do not raise the baseline. Check the column's "
                + "blank-row count first: on a populated column the constraint also blocks "
                + "edits to existing records. Offenders are printed above.")
            .isLessThanOrEqualTo(BASELINE_ASYMMETRIC_PAIRS);
    }

    /**
     * The data-blocked list must stay honest in both directions: an entry that has since been
     * annotated is stale bookkeeping, and it would also mean someone annotated a column this
     * test claims holds blank rows.
     */
    @Test
    void theDataBlockedListDoesNotContainAlreadyFixedFields() {
        List<String> stale = new ArrayList<>();
        List<String> asymmetric = scanAsymmetricPairs();
        for (String key : DATA_BLOCKED.keySet()) {
            if (asymmetric.stream().noneMatch(a -> a.startsWith(key))) {
                stale.add(key + " (" + DATA_BLOCKED.get(key) + ")");
            }
        }
        System.out.println("[F2-30 bilingual ratchet] data-blocked entries now fixed = " + stale);
        assertThat(stale)
            .as("These fields are listed as blocked by blank Arabic data but are no longer "
                + "asymmetric — either the constraint landed (remove them from DATA_BLOCKED and "
                + "lower BASELINE_ASYMMETRIC_PAIRS) or the field was renamed or removed.")
            .isEmpty();
    }

    /** Guards against the scan silently finding nothing and the ratchet passing vacuously. */
    @Test
    void theScanStillSeesTheBilingualSurface() {
        int pairs = scanAllPairs().size();
        System.out.println("[F2-30 bilingual ratchet] *_en/*_ar field pairs discovered = " + pairs);
        assertThat(pairs)
            .as("The scan found %d en/ar field pairs; 574 were measured when F2-30 shipped. A "
                + "sharp drop means the detector broke, not that the fields left.", pairs)
            .isGreaterThanOrEqualTo(520);
    }

    /**
     * @return {@code Entity.fieldAr → schema.table.column} for each pair whose {@code *_en} is
     *     mandatory and {@code *_ar} is not. The physical location is part of the line because
     *     the first thing anyone hitting this failure needs is the blank-row count, and that
     *     needs the table.
     */
    static List<String> scanAsymmetricPairs() {
        List<String> out = new ArrayList<>();
        for (Class<?> c : entities()) {
            for (Field en : c.getDeclaredFields()) {
                if (!en.getName().endsWith("En")) {
                    continue;
                }
                String arName = en.getName().substring(0, en.getName().length() - 2) + "Ar";
                Field ar;
                try {
                    ar = c.getDeclaredField(arName);
                } catch (NoSuchFieldException e) {
                    continue;   // EN_ONLY — needs a migration, not an annotation
                }
                if (mandatory(en) && !mandatory(ar)) {
                    out.add(c.getSimpleName() + "." + arName + " -> " + physicalLocation(c, ar));
                }
            }
        }
        return out;
    }

    /** {@code schema.table.column} for a field, from its @Table / @Column annotations. */
    private static String physicalLocation(Class<?> c, Field f) {
        Table t = c.getAnnotation(Table.class);
        String schema = t == null || t.schema().isBlank() ? "dbo" : t.schema();
        String table = t == null || t.name().isBlank() ? c.getSimpleName() : t.name();
        Column col = f.getAnnotation(Column.class);
        String column = col == null || col.name().isBlank() ? f.getName() : col.name();
        return schema + "." + table + "." + column;
    }

    /** @return every {@code *_en}/{@code *_ar} pair, regardless of constraints. */
    static List<String> scanAllPairs() {
        List<String> out = new ArrayList<>();
        for (Class<?> c : entities()) {
            for (Field en : c.getDeclaredFields()) {
                if (!en.getName().endsWith("En")) {
                    continue;
                }
                String arName = en.getName().substring(0, en.getName().length() - 2) + "Ar";
                try {
                    c.getDeclaredField(arName);
                    out.add(c.getSimpleName() + "." + arName);
                } catch (NoSuchFieldException ignored) {
                    // not a pair
                }
            }
        }
        return out;
    }

    /**
     * Mandatory means either bean validation ({@code @NotBlank} / {@code @NotNull}) or the JPA
     * DDL contract ({@code @Column(nullable = false)}). Both are counted because the existing
     * {@code *_en} fields express it the second way and the fix expresses it the first —
     * treating only one as mandatory would report every newly-fixed pair as still broken.
     */
    private static boolean mandatory(Field f) {
        if (f.isAnnotationPresent(NotBlank.class) || f.isAnnotationPresent(NotNull.class)) {
            return true;
        }
        Column col = f.getAnnotation(Column.class);
        return col != null && !col.nullable();
    }

    private static List<Class<?>> entities() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> out = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            try {
                out.add(Class.forName(bd.getBeanClassName()));
            } catch (Throwable ignored) {
                // a class that cannot load cannot be asserted on
            }
        }
        return out;
    }
}

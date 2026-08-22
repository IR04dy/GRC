package com.tss.grc.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
     *
     * <p><b>⚠ 157 → 158 on 2026-08-06 (F2-38), and this is the one shape of increase that is not a
     * regression.</b> {@code Notification.messageAr} was added to the entity. The asymmetry it
     * reports is <b>not new</b> — {@code platform.notifications.message_ar} has existed since
     * {@code V1__core_schemas.sql} beside a {@code NOT NULL message_en}, and 42 of 445 rows hold
     * Arabic. What changed is that the column became <i>visible to this scan</i>, because
     * <b>this test reads entity declarations, and the column was mapped by nothing.</b>
     *
     * <p><b>Which makes 157 a lower bound, and that was measured rather than assumed.</b> There are
     * <b>631</b> live {@code *_ar} columns; <b>11</b> are mapped by no entity at all, and <b>5</b> of
     * those sit beside a {@code NOT NULL *_en} sibling — five rule-7 asymmetries this test cannot
     * count: {@code bcm_lc.drills.scenario_ar} (0/5 blank),
     * {@code reporting.report_definitions.report_name_ar} (0/20),
     * {@code risk_lc.kri_breaches.kri_name_ar} (0/12), plus
     * {@code privacy_lc.ropa_activities.purposes_ar} and {@code risk_lc.kri_escalation_log.step_ar}
     * on empty tables. So the true figure was at least <b>163</b>. All five hold <b>zero</b> blank
     * rows, so they are not data-blocked — they are <i>unreachable</i>, which is a different and
     * quieter problem than being unfixable.
     *
     * <p>🔑 <b>The transferable lesson, which this programme has now hit twice:</b> an instrument
     * that asks about <i>mapped</i> things cannot see <i>unmapped</i> ones, and its green reading is
     * not evidence of absence. F2-140 found the same shape in the schema ratchets — a table with no
     * JPA entity was invisible to a control asserting that every mapped table has DDL. <b>Mapping a
     * column made this number worse and the codebase better.</b>
     *
     * <p>⚠⚠ <b>F2-271 — that last sentence is the whole argument for retiring this constant, and it
     * was written here before the constant was retired.</b> A decrease-only ceiling on a number that
     * <i>gets worse when the codebase gets better</i> fails on the commit that improves things, and
     * this one's failure message then said <i>"Add &#64;NotBlank to the *_ar field — do not raise
     * the baseline"</i> — which on a populated column is what this very class warns makes every
     * existing record uneditable. So the instrument had <b>documented why its number was the wrong
     * number and stayed keyed on it</b>: the codebase holding the correct rule in prose beside the
     * place it was not applied (F2-195's shape).
     *
     * <p>The ceiling is now {@link #BASELINE_ARABIC_NOT_GUARANTEED}, the SUM of the asymmetric pairs
     * and the {@link #scanEnOnly() EN_ONLY} bucket this class used to {@code continue} past. This
     * constant is deleted rather than kept as a measurement, because an unused ceiling whose value
     * is stale (158, against a measured 159) answers the question a reader was about to ask
     * (F2-184).
     */

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
        // F2-38. Worst ratio in the list, and the constraint would do active harm: markRead() saves
        // the entity, so @NotBlank here makes 403 existing notifications impossible to mark as read.
        // The 39 call sites cannot supply Arabic either — the three notify methods take titleAr and
        // no Arabic body, so the signature itself is where the gap lives.
        Map.entry("Notification.messageAr", "403/445"),
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

    /**
     * F2-271 — the ceiling moved from the asymmetric pairs to <b>the sum</b>, because the old
     * question punished fixing the worse defect.
     *
     * <p>Closing an {@link #scanEnOnly() EN_ONLY} field means adding a NULLABLE Arabic sibling —
     * nullable because nothing can invent Arabic governance text for the rows that predate the
     * column, which is this class's own reason for being a ratchet. That makes the pair
     * <b>visible</b> to {@link #scanAsymmetricPairs()}, so {@code en_only} falls by one and
     * {@code asymmetric} rises by one. Measured on V387: <b>47 → 45</b> and <b>158 → 159</b>.
     *
     * <p>🔑 So a decrease-only ceiling on the asymmetric pairs alone <b>fails on the very commit
     * that fixes the worse half of house rule 7</b>, and its failure message read <i>"Add
     * &#64;NotBlank to the *_ar field — do not raise the baseline"</i>, which on a populated column
     * is precisely what this class warns makes every existing record uneditable. <b>The instrument
     * told you to break editing rather than let its count move.</b> F2-204's rule arriving from the
     * inside: when a decrease-only number has to go up, ask whether its QUESTION was wrong, and
     * re-scope rather than raise.
     *
     * <p>The sum cannot be gamed in either direction — it counts <i>fields where Arabic is not
     * guaranteed</i>:
     * <ul>
     *   <li>a new English-only column: {@code en_only} +1 → <b>fails</b>;</li>
     *   <li>a new mandatory-EN / optional-AR pair: {@code asymmetric} +1 → <b>fails</b>;</li>
     *   <li>adding a nullable Arabic sibling to a mandatory English one: −1 and +1 →
     *       <b>unchanged</b>, which is right, because the defect MOVED rather than went away;</li>
     *   <li>adding a symmetric pair, or annotating an Arabic column whose rows already hold
     *       Arabic: → <b>falls</b>.</li>
     * </ul>
     *
     * <p>Both components are still printed and asserted individually as measurements, so a reader
     * can see which half moved. What is a <em>ceiling</em> is the sum.
     */
    @Test
    void arabicIsNotGuaranteedOnFewerFieldsThanBefore() {
        List<String> asymmetric = scanAsymmetricPairs();
        List<String> enOnly = scanEnOnly();
        Collections.sort(asymmetric);
        System.out.println("[F2-30/F2-271 bilingual ratchet] asymmetric pairs = " + asymmetric.size()
            + ", EN_ONLY = " + enOnly.size() + ", sum = " + (asymmetric.size() + enOnly.size()));
        asymmetric.forEach(s -> System.out.println("  asym    - " + s));
        enOnly.forEach(s -> System.out.println("  EN_ONLY - " + s));

        assertThat(asymmetric.size() + enOnly.size())
            .as("%d entity fields do not guarantee Arabic (%d asymmetric pairs + %d EN_ONLY), "
                + "against a ceiling of %d. CLAUDE.md house rule 7: a user-visible record carries "
                + "*_en AND *_ar, and Arabic is the PRIMARY language. Do not raise this. The "
                + "ceiling is the SUM and not the pair count on purpose (F2-271): adding a nullable "
                + "Arabic sibling moves a field from EN_ONLY into the pair bucket, so a ceiling on "
                + "pairs alone FAILS on the commit that fixes the worse half of the rule, and its "
                + "old message told you to annotate a populated column - which blocks edits to "
                + "every existing row. To make this number FALL, add a symmetric pair or annotate "
                + "an Arabic column whose rows already hold Arabic; check the blank-row count "
                + "first. Offenders are printed above.",
                asymmetric.size() + enOnly.size(), asymmetric.size(), enOnly.size(),
                BASELINE_ARABIC_NOT_GUARANTEED)
            .isLessThanOrEqualTo(BASELINE_ARABIC_NOT_GUARANTEED);
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
                + "lower BASELINE_ARABIC_NOT_GUARANTEED) or the field was renamed or removed.")
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

    /**
     * F2-271 — the bucket {@link #scanAsymmetricPairs()} names in a comment and skips.
     *
     * <p>That method pairs each {@code *En} field with its {@code *Ar} sibling and, when the
     * sibling does not exist, does {@code continue;   // EN_ONLY — needs a migration, not an
     * annotation}. Correct, and it means **a field with no Arabic sibling at all is outside this
     * class's population by construction** — the narrowing is the <b>existence of the sibling</b>,
     * which is F2-198's shape at a new depth.
     *
     * <p>🔑 <b>EN_ONLY is the WORSE half of house rule 7.</b> An asymmetric pair lets Arabic be
     * supplied and merely fails to require it; a missing column means the Arabic <b>cannot be
     * recorded at all</b>, on a platform whose primary language is Arabic. And it needs a
     * migration, so it can never be closed by the annotation sweep the ratchet above measures.
     *
     * <p>⚠ This class already knew. Its {@link #ASYMMETRIC_PAIRS} javadoc records a
     * <i>regression</i> where {@code Notification.messageAr} was added and the asymmetry count
     * went UP, because *"the column became visible to this scan"* — i.e. an EN_ONLY field becoming
     * a pair. **The blind spot was understood, named, and never counted**, and five slices then
     * filed individual members by hand: F2-208 (`capa.tasks.progress_notes_*`), F2-209
     * (`privacy.data_breaches` has no `title_ar` or `description_ar`, on a PDPL statutory record),
     * F2-249 (`ethics.aml_alerts.analyst_name`), F2-251 (three `change_mgmt` people columns) and
     * F2-252 (`cyber.cyber_controls.framework_name`, where the sibling register carries `name_ar`).
     *
     * <p>Reported with the physical location because the fix is a migration and the first thing
     * anyone needs is the table — the same reason {@link #scanAsymmetricPairs()} prints it.
     */
    static List<String> scanEnOnly() {
        List<String> out = new ArrayList<>();
        for (Class<?> c : entities()) {
            for (Field en : c.getDeclaredFields()) {
                if (!en.getName().endsWith("En")) {
                    continue;
                }
                String arName = en.getName().substring(0, en.getName().length() - 2) + "Ar";
                try {
                    c.getDeclaredField(arName);
                } catch (NoSuchFieldException e) {
                    out.add(c.getSimpleName() + "." + en.getName() + " -> "
                            + physicalLocation(c, en));
                }
            }
        }
        Collections.sort(out);
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


    /**
     * EN_ONLY fields — a {@code *_en} column with no Arabic sibling anywhere on the entity.
     *
     * <p>Measured by {@link #scanEnOnly()} on this tree. <b>Decrease-only and with NO verdicts</b>
     * (F2-188): closing one is a migration plus a writer, and which of these is user-visible
     * governance text as against an internal label is a reading job, not something this number can
     * decide. Two shapes are certainly in here and want opposite treatment — a PDPL breach's
     * {@code title_en} must have Arabic beside it (house rule 7), while an internal
     * {@code source_system_en}-style label may legitimately be English-only.
     */
    /**
     * F2-271 — <b>the ceiling</b>: asymmetric pairs + EN_ONLY, i.e. every entity field where Arabic
     * is not guaranteed. <b>194</b> measured on this tree (159 + 35) after V387, V388 and V389 closed
     * five privacy registers and the six stopped-reason columns. Decrease-only; see
     * {@link #arabicIsNotGuaranteedOnFewerFieldsThanBefore()} for why the ceiling is the sum rather
     * than either component.
     */
    private static final int BASELINE_ARABIC_NOT_GUARANTEED = 194;

    /** Measured, not a ceiling — the sum above is the ceiling. */
    private static final int BASELINE_EN_ONLY = 35;

    @Test
    @DisplayName("F2-271: EN_ONLY fields are counted — the bucket the pair scan names and skips")
    void theEnOnlyBucketOnlyEverShrinks() throws IOException {
        List<String> enOnly = scanEnOnly();

        Path report = Path.of("target", "bilingual-en-only.txt");
        List<String> out = new ArrayList<>();
        out.add("# EN_ONLY: a *_en entity field with NO Arabic sibling - GENERATED by");
        out.add("# BilingualInvariantRatchetTest. Do not hand-edit, do not copy the count into");
        out.add("# prose (F2-265). NO VERDICTS: closing one is a migration plus a writer, and");
        out.add("# whether a given label is user-visible governance text is a reading job.");
        out.add("en_only=" + enOnly.size());
        out.add("asymmetric_pairs=" + scanAsymmetricPairs().size());
        out.add("all_pairs=" + scanAllPairs().size());
        out.add("");
        out.addAll(enOnly);
        Files.deleteIfExists(report);
        String body = String.join(System.lineSeparator(), out);
        Files.writeString(report, body, StandardCharsets.UTF_8);
        assertThat(Files.readString(report, StandardCharsets.UTF_8))
            .as("the report must be the one this run produced, not a leftover").isEqualTo(body);

        assertThat(enOnly.size())
            .as("%d entity fields end 'En' with NO Arabic sibling; the measured ceiling is %d. "
                + "House rule 7 says a user-visible record carries *_en AND *_ar, and Arabic is "
                + "the PRIMARY language - so this is the worse half of the rule: an asymmetric "
                + "pair merely fails to require Arabic, while a missing column means Arabic "
                + "cannot be recorded at all. Closing one needs a MIGRATION, which is why the "
                + "annotation ratchet above can never reach it. Full list: %s",
                enOnly.size(), BASELINE_EN_ONLY, report)
            .isLessThanOrEqualTo(BASELINE_EN_ONLY);
    }

    @Test
    @DisplayName("F2-271: EN_ONLY and the asymmetric pairs are disjoint by construction")
    void enOnlyAndAsymmetricPairsAreDisjoint() {
        // A field cannot be both: EN_ONLY means the Ar sibling does not exist, and an asymmetric
        // pair means it does. Asserted rather than assumed, because if this ever overlaps then one
        // of the two scans has stopped resolving the sibling and BOTH counts become meaningless -
        // and the failure would look like a population shrinking (F2-69).
        List<String> enOnlyFields = scanEnOnly().stream()
            .map(l -> l.substring(0, l.indexOf(" -> "))).toList();
        List<String> pairFields = scanAsymmetricPairs().stream()
            .map(l -> l.substring(0, l.indexOf(" -> "))).toList();
        for (String f : enOnlyFields) {
            String asAr = f.substring(0, f.length() - 2) + "Ar";
            assertThat(pairFields)
                .as("%s is reported EN_ONLY, but %s is also reported as an asymmetric pair - the "
                    + "two scans disagree about whether the sibling exists", f, asAr)
                .doesNotContain(asAr);
        }
        assertThat(enOnlyFields.size() + scanAllPairs().size())
            .as("anti-vacuity: every *En field must land in exactly one of the two buckets, so "
                + "their sum is the whole bilingual surface. A collapsed scan shows up here as a "
                + "small total rather than as a clean zero.")
            .isGreaterThan(400);
    }
}
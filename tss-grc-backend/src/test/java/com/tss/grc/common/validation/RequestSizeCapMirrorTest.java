package com.tss.grc.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.tss.grc.module.bcm.cycle.runtime.dto.ExerciseRuntimeDtos;
import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRun;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos;
import com.tss.grc.module.audit_lc.ia_plan.entity.IaAuditUniverseEntry;
import com.tss.grc.module.cyber.asm.dto.AsmDtos;
import com.tss.grc.module.cyber.vuln_sla.dto.VulnSlaDtos;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos;
import com.tss.grc.module.iam.governance.entity.AccessReviewCampaign;
import com.tss.grc.module.iam.governance.entity.SodViolation;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos;
import com.tss.grc.module.privacy.extensions.entity.PrivacyNotice;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos;
import com.tss.grc.module.risk.bowtie.entity.BowTieAnalysis;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos;
import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpAttestation;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos;
import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingMonitoringEvent;
import jakarta.persistence.Column;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import com.tss.grc.module.admin.committee.entity.CommitteeMeetingPaper;
import jakarta.persistence.Entity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * F2-185(b) — the number, not the annotation.
 *
 * <p>{@link InertValidationConstraintTest} proves a constraint is <i>enforced</i>. That is only half
 * the question F2-184 raised, and the quieter half is this one: <b>an enforced {@code @Size} cap has
 * still never been tested against the column it guards.</b> F2-184's rule for deciding what to do
 * with a dormant cap — cap == column → enable, column {@code NVARCHAR(MAX)} → delete the cap, cap
 * looser than the column → the annotation is what is wrong — was applied to ten sites by hand, from
 * measurements taken by hand. This test is what stops those measurements from becoming a comment.
 *
 * <p>Three of F2-184's ten recorded measurements were wrong when checked: two caps it recorded as
 * 1000 were 8000, {@code privacy_notices.archive_reason_en} was recorded as a case where the cap
 * would be looser than the column and is in fact an exact match, and two columns it called
 * "unconfirmed" exist under a different name than the DTO field ({@code approverName} lands in
 * {@code approved_by_name}). 🔑 <b>A measurement recorded as a guess is not a measurement</b>, and
 * the only reliable difference between the two is whether something re-checks it.
 *
 * <p>Why the mapping is DECLARED rather than derived: nothing in the code connects a request field to
 * a column. A service copies it across, sometimes renaming it, so there is no annotation to read and
 * no convention to trust. Same shape as {@code ClosedVocabularyPinTest} naming the CHECK it mirrors,
 * and {@code PromotionVocabularyTest} naming its target table — a declared edge that the build then
 * verifies beats a derived edge that is silently wrong.
 *
 * <p>⚠ This test cannot see a prefix. If a service concatenates something onto the value before it
 * reaches the column — which is precisely how the SoD override justification overflowed a column
 * whose length matched the cap exactly — cap == length is still true and the write still fails. That
 * is why {@link com.tss.grc.module.iam.governance.service.SodOverrideNoteTest} exists as a behaviour
 * test alongside this structural one.
 */
class RequestSizeCapMirrorTest {

    /**
     * A request field, the entity field its value ends up in, and the service that carries it there.
     * {@code why} is not decoration: it is the record of where the edge was verified, so the next
     * reader can re-check it without re-tracing the service.
     */
    private record Landing(Class<?> requestType, String component,
                           Class<?> entityType, String entityField, String why) {}

    private static final List<Landing> MIRRORED = List.of(
        new Landing(GovernanceDtos.CloseCampaignRequest.class, "exceptionNote",
            AccessReviewCampaign.class, "exceptionNote",
            "AccessReviewService#closeCampaign copies it onto the campaign being closed."),
        new Landing(GovernanceDtos.ApproveAccessRequest.class, "overrideJustification",
            SodViolation.class, "dispositionNote",
            "AccessRequestService#approve -> SodService#recordOverride. ⚠ Until F2-185(b) that "
            + "service prepended 24 characters, so cap == column was true and the insert still "
            + "overflowed — see SodOverrideNoteTest, which this test cannot replace."),
        new Landing(VendorBcpAttestationDtos.AttestationReviewRequest.class, "reviewerCommentEn",
            VendorBcpAttestation.class, "reviewerCommentEn",
            "VendorBcpAttestationService#transitionAttestation, all three review routes."),
        new Landing(VendorBcpAttestationDtos.AttestationReviewRequest.class, "reviewerCommentAr",
            VendorBcpAttestation.class, "reviewerCommentAr",
            "Same setter block as the English comment."),
        new Landing(OutsourcingDtos.MonitoringEventResolveRequest.class, "actionTakenEn",
            OutsourcingMonitoringEvent.class, "actionTakenEn",
            "OutsourcingService#resolveMonitoringEvent — the SAMA remediation record."),
        new Landing(OutsourcingDtos.MonitoringEventResolveRequest.class, "actionTakenAr",
            OutsourcingMonitoringEvent.class, "actionTakenAr",
            "Same, Arabic half; the bilingual rule means both are user-visible."),
        new Landing(PdplExtensionDtos.NoticeArchiveRequest.class, "archiveReasonEn",
            PrivacyNotice.class, "archiveReasonEn",
            "PrivacyNoticeService#archive line 130 — the one edge F2-184 measured as a mismatch, "
            + "and it is an exact match."),
        new Landing(PdplExtensionDtos.NoticeArchiveRequest.class, "archiveReasonAr",
            PrivacyNotice.class, "archiveReasonAr",
            "PrivacyNoticeService#archive — the Arabic half added by F2-272 (V388). The cap "
            + "mirrors the column exactly BECAUSE V388 asserts the two columns' widths match, "
            + "so a longer English archive reason cannot be untranslatable."),
        new Landing(ExerciseRuntimeDtos.StartRunRequest.class, "runCode",
            ExerciseRun.class, "runCode",
            "ExerciseRuntimeService#startRun line 176 stores req.runCode().trim() directly when "
            + "non-blank (else auto-generates). F2-350: the field had no @Size while every sibling "
            + "text field in the DTO did, so a > 64-char override 8152'd on the run_code insert. "
            + "The stored value is a trim() of the raw, so @Size(max = 64) on the raw never accepts "
            + "an overflow — it only turns that 500 into a 400 naming the field.")
        // F2-223 — the BowTieDtos.ApproveRequest#approverName landing is DELETED with its field.
        // `approvedByName` is now stamped from CurrentActor, so there is no caller-supplied value
        // for a cap to guard. This gate's own reverse leg is what required the entry to go in the
        // same commit (F2-185(b)), and it fired.
    );

    /**
     * Validated {@code @Size} caps whose value never reaches a column, with the reason each number is
     * nonetheless the right one. ⚠ Do NOT let this become the easy answer: "no column" is the shape
     * that hid F2-185(b)'s worst site, where the value reached nothing at all.
     */
    private static final Map<String, String> UNMIRRORED = Map.of(
        "com.tss.grc.module.cyber.asm.dto.AsmDtos$ShadowItBlockRequest#note",
            "No column: ShadowItService#block puts it in the F7 audit message, which is "
            + "NVARCHAR(MAX). The cap is 500 because the IDENTICAL field on ShadowItSanctionRequest "
            + "is already validated at 500 on the adjacent /sanction route — consistency with a "
            + "sibling, not a limit invented here.",
        "com.tss.grc.module.cyber.vuln_sla.dto.VulnSlaDtos$RemediatedRequest#note",
            "No column, same reason and same 500, mirroring the ASM pair above. F2-185(b): this "
            + "value used to reach NOTHING — the controller bound it and called a two-arg service "
            + "method — under a comment claiming the audit log captured it."
    );

    /**
     * The other direction of the same rule: a request field whose column is {@code NVARCHAR(MAX)}
     * must carry <b>no</b> {@code @Size}. These four components had {@code @Size(max = 8000)} caps
     * that had never run, and F2-185(b) deleted them rather than switching them on, because the
     * schema declined to bound a campaign's closing narrative and an 8,000-character limit that has
     * never been enforced is not a limit anyone chose.
     *
     * <p>Without this list the deletion is unprotected in the one direction that matters: re-adding
     * the cap <i>together with</i> {@code @Valid} would satisfy {@link InertValidationConstraintTest}
     * (the constraint is enforced) and never reach {@link #MIRRORED} (nobody declared a landing), so
     * a new restriction would land silently on a regulatory attestation record. Same reasoning as the
     * {@code coiNote} pin in {@code NestedRequestCascadeTest}.
     */
    private static final Map<String, String> UNBOUNDED_BY_SCHEMA = Map.of(
        "com.tss.grc.module.compliance.obligation.dto.ObligationDtos$CampaignCloseRequest",
            "closing_summary_en/_ar are NVARCHAR(MAX) — V184 lines 186-187.",
        "com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos$CampaignCloseRequest",
            "closing_summary_en/_ar are NVARCHAR(MAX) — V177 lines 94-95."
    );

    /** The types this slice enabled. Every @Size on them must be accounted for, above. */
    private static final List<Class<?>> COVERED = List.of(
        GovernanceDtos.CloseCampaignRequest.class,
        GovernanceDtos.ApproveAccessRequest.class,
        VendorBcpAttestationDtos.AttestationReviewRequest.class,
        OutsourcingDtos.MonitoringEventResolveRequest.class,
        PdplExtensionDtos.NoticeArchiveRequest.class,
        ExerciseRuntimeDtos.StartRunRequest.class,
        BowTieDtos.ApproveRequest.class,
        AsmDtos.ShadowItBlockRequest.class,
        VulnSlaDtos.RemediatedRequest.class);

    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void everyEnabledSizeCapEqualsTheColumnItGuards() throws Exception {
        List<String> problems = new ArrayList<>();

        for (Landing l : MIRRORED) {
            Integer cap = capOf(l.requestType(), l.component());
            if (cap == null) {
                problems.add("%s#%s declares no @Size — the mapping is stale."
                        .formatted(l.requestType().getSimpleName(), l.component()));
                continue;
            }
            Field ef = fieldOf(l.entityType(), l.entityField());
            if (ef == null) {
                problems.add("%s has no field '%s' — the entity was renamed under this mapping."
                        .formatted(l.entityType().getSimpleName(), l.entityField()));
                continue;
            }
            Column col = ef.getAnnotation(Column.class);
            if (col == null) {
                problems.add("%s#%s carries no @Column, so its length is Hibernate's default 255 "
                        + "and the cap of %d is unverifiable."
                        .formatted(l.entityType().getSimpleName(), l.entityField(), cap));
                continue;
            }
            String definition = col.columnDefinition() == null ? "" : col.columnDefinition().toUpperCase();
            if (definition.contains("MAX")) {
                // The trap this whole finding is about, in reverse: @Column(columnDefinition = ...)
                // leaves length() at its default 255, so a naive comparison would "fail" against a
                // number the schema never declared. An unbounded column means the cap is the thing
                // that is wrong — F2-184's coiNote branch.
                problems.add(("%s#%s is %s — an UNBOUNDED column. A @Size(%d) here is a limit the "
                        + "schema declined to impose: delete the cap rather than mirroring it.")
                        .formatted(l.entityType().getSimpleName(), l.entityField(),
                                col.columnDefinition(), cap));
                continue;
            }
            if (cap != col.length()) {
                problems.add(("%s#%s @Size(max = %d) vs %s.%s length %d — %s. %s")
                        .formatted(l.requestType().getSimpleName(), l.component(), cap,
                                l.entityType().getSimpleName(), l.entityField(), col.length(),
                                cap > col.length()
                                    ? "the CAP IS LOOSER than the column, so it protects nothing and "
                                      + "an accepted value still fails at the insert with SQL Server "
                                      + "8152"
                                    : "the cap is tighter than the column, which refuses input the "
                                      + "schema would have stored",
                                l.why()));
            }
        }

        assertThat(problems).as("""
            A @Size cap on a request field must equal the column its value lands in. Equality is \
            what makes enabling the cap safe: the database already refuses more, so the only change \
            is a 400 that names the field instead of a truncation 500 — no request that works today \
            can start failing. Anything else is a decision, not a mirror (F2-185(b)).

            %s""".formatted(String.join("\n  ", problems))).isEmpty();
    }

    @Test
    void everySizeCapOnTheseRequestTypesIsEitherMirroredOrExplained() {
        var declared = new TreeSet<String>();
        for (Landing l : MIRRORED) declared.add(key(l.requestType(), l.component()));
        declared.addAll(UNMIRRORED.keySet());

        var undeclared = new TreeSet<String>();
        for (Class<?> t : COVERED) {
            for (RecordComponent rc : componentsOf(t)) {
                if (capOf(t, rc.getName()) == null) continue;
                String k = key(t, rc.getName());
                if (!declared.contains(k)) undeclared.add(k);
            }
        }

        assertThat(undeclared).as("""
            These validated @Size caps have no recorded destination. Add a Landing (with the service \
            that carries the value) or an UNMIRRORED entry saying why there is no column — the point \
            of the list is that the number was checked against something, once, by someone.

            ⚠ "There is no column" is not a free pass: on VulnSlaDtos.RemediatedRequest#note it was \
            true because the value reached NOTHING AT ALL.

            %s""".formatted(String.join("\n  ", undeclared))).isEmpty();

        // The reverse direction — a mapping for a field that no longer declares a cap is a mapping
        // that has quietly stopped checking anything, which is how a ratchet rots into a comment.
        var stale = new TreeSet<String>();
        for (String k : UNMIRRORED.keySet()) {
            String type = k.substring(0, k.indexOf('#'));
            String comp = k.substring(k.indexOf('#') + 1);
            Class<?> c = COVERED.stream().filter(x -> x.getName().equals(type)).findFirst().orElse(null);
            if (c == null || capOf(c, comp) == null) stale.add(k);
        }
        assertThat(stale).as(
            "UNMIRRORED entries whose @Size is gone — delete the entry: %s", stale).isEmpty();
    }

    @Test
    void aRequestFieldWhoseColumnIsUnboundedCarriesNoCap() throws Exception {
        List<String> reintroduced = new ArrayList<>();
        for (var e : UNBOUNDED_BY_SCHEMA.entrySet()) {
            Class<?> t = Class.forName(e.getKey());
            for (RecordComponent rc : componentsOf(t)) {
                Integer cap = capOf(t, rc.getName());
                if (cap != null) {
                    reintroduced.add("%s#%s has @Size(max = %d) — %s"
                            .formatted(t.getSimpleName(), rc.getName(), cap, e.getValue()));
                }
            }
        }
        assertThat(reintroduced).as("""
            F2-185(b) deleted these caps because the column is NVARCHAR(MAX): the schema deliberately \
            leaves the field unbounded, so a cap is a product decision and not a repair. Putting one \
            back means deciding to restrict a closing narrative — do that on purpose, with a number \
            somebody chose, and record it here; do not let it arrive as a tidy-up.

            %s""".formatted(String.join("\n  ", reintroduced))).isEmpty();
    }

    @Test
    void theMappingItselfIsNotEmpty() {
        // Anti-vacuity. Both assertions above are "no problems found", which an empty MIRRORED list
        // satisfies perfectly — the failure mode that makes a green build meaningless.
        //
        // ⚠ 9 → 8 at F2-223, then 8 → 7 at F2-224, and both reasons are recorded rather than the
        // number being nudged. F2-223: the BowTie `approverName` cap went with its request field.
        // F2-224: `AttestationReviewRequest#reviewedByName` likewise — the vendor-BCP reviewer is
        // now stamped from the authenticated principal, so there is no caller value left to cap.
        // A population that FALLS after a fix is not automatically rot, but it is always worth
        // asking which (F2-213). Both times the answer was "the field is gone, not the check".
        // F2-350 then ADDED one — StartRunRequest#runCode → ExerciseRun.run_code(64) — so the
        // floor rises with the population it tracks (7 → 8); an increase needs no defence, but a
        // floor left below its population is a weaker anti-vacuity guard than it looks.
        assertThat(MIRRORED).hasSizeGreaterThanOrEqualTo(8);
        assertThat(COVERED).hasSizeGreaterThanOrEqualTo(8);
        Map<String, Integer> caps = new LinkedHashMap<>();
        for (Landing l : MIRRORED) caps.put(key(l.requestType(), l.component()), capOf(l.requestType(), l.component()));
        assertThat(caps.values()).allMatch(v -> v != null && v > 0);
    }

    // ═══ F2-425 — the same question, asked of a NUMBER ═══════════════════════════════════════
    //
    // A decimal(p,s) column holds at most (p-s) integer digits; a value with more raises SQL Server
    // 8115, the numeric twin of the 8152 the @Size family above produced. For a String the boundary
    // declares its cap with @Size and the legs above check it against @Column(length). For a
    // BigDecimal the cap is @DecimalMax and NOTHING checked it against the column's own ceiling --
    // measured: 102 BigDecimal components of request-bound records, 18 capped, 0 caps looser than
    // their column, and 13 uncapped landing in a column with fewer than 4 integer digits.
    //
    // Most of those 13 are harmless: a 0-100 score in a decimal(5,2) has 999 of room. What made the
    // IA audit universe the sharp one is that its column receives a DERIVED value -- residual =
    // inherent x (1 - controlEnvironmentScore) -- so the derivation's range is not bounded by either
    // input's own column, and an out-of-range strength takes the residual OUT of decimal(4,2) in one
    // direction and NEGATIVE in the other.

    /**
     * A numeric request field, the entity field its value lands in, and the cap that guards it.
     * {@code cap} is declared as well as asserted so that LOOSENING the annotation fails here
     * rather than silently passing the fits-the-column test.
     */
    private record NumericLanding(Class<?> requestType, String component,
                                  Class<?> entityType, String entityField,
                                  String cap, String why) {}

    private static final List<NumericLanding> NUMERIC_MIRRORED = List.of(
        new NumericLanding(IaDtos.UniverseCreateRequest.class, "inherentRiskScore",
            IaAuditUniverseEntry.class, "inherentRiskScore", "99.99",
            "IaAuditUniverseService#create copies it straight across. The cap IS the column's own "
            + "decimal(4,2) ceiling, which is F2-184's cap == column rule: the database already "
            + "refuses more, so enabling it can only turn an 8115 into a 400 naming the field."),
        new NumericLanding(IaDtos.UniverseUpdateRequest.class, "inherentRiskScore",
            IaAuditUniverseEntry.class, "inherentRiskScore", "99.99",
            "Same column via update(). Both records are listed because update() recomputes the "
            + "residual from whichever half the caller sent plus the STORED other half, so an "
            + "uncapped update path re-opens exactly what the create closes (F2-246)."),
        new NumericLanding(IaDtos.UniverseCreateRequest.class, "controlEnvironmentScore",
            IaAuditUniverseEntry.class, "controlEnvironmentScore", "1.00",
            "The load-bearing one. 1.00 is NOT this column's ceiling -- decimal(4,2) would hold "
            + "99.99 -- it is the 0-1 control-strength scale IaAuditUniverseService's own javadoc "
            + "declares, and it is what makes the DERIVED residual provably non-negative and no "
            + "larger than inherent. A cap at the column's ceiling would satisfy the leg below and "
            + "leave the finding open, which is why the expected value is declared here."),
        new NumericLanding(IaDtos.UniverseUpdateRequest.class, "controlEnvironmentScore",
            IaAuditUniverseEntry.class, "controlEnvironmentScore", "1.00",
            "Same, update path.")
    );

    /** The types F2-425 bounded. Every BigDecimal component on them must be accounted for above. */
    private static final List<Class<?>> NUMERIC_COVERED = List.of(
        IaDtos.UniverseCreateRequest.class,
        IaDtos.UniverseUpdateRequest.class);

    @Test
    void everyEnabledDecimalMaxFitsTheColumnItGuards() {
        List<String> problems = new ArrayList<>();

        for (NumericLanding l : NUMERIC_MIRRORED) {
            BigDecimal cap = numericCapOf(l.requestType(), l.component(), DecimalMax.class);
            if (cap == null) {
                problems.add("%s#%s declares no @DecimalMax — the mapping is stale, or the cap was "
                        + "removed. %s".formatted(l.requestType().getSimpleName(), l.component(),
                                l.why()));
                continue;
            }
            if (cap.compareTo(new BigDecimal(l.cap())) != 0) {
                problems.add(("%s#%s @DecimalMax is %s where this mapping expects %s. A cap change "
                        + "is a decision: make it here too, with the reason. %s")
                        .formatted(l.requestType().getSimpleName(), l.component(), cap.toPlainString(),
                                l.cap(), l.why()));
                continue;
            }
            if (numericCapOf(l.requestType(), l.component(), DecimalMin.class) == null) {
                problems.add(("%s#%s has an upper bound and no @DecimalMin. For a value the server "
                        + "then MULTIPLIES, the lower bound is not symmetry: a negative control "
                        + "strength inverts 1 - strength and pushes the residual ABOVE its column.")
                        .formatted(l.requestType().getSimpleName(), l.component()));
                continue;
            }
            Field ef = fieldOf(l.entityType(), l.entityField());
            if (ef == null) {
                problems.add("%s has no field '%s' — the entity was renamed under this mapping."
                        .formatted(l.entityType().getSimpleName(), l.entityField()));
                continue;
            }
            Column col = ef.getAnnotation(Column.class);
            if (col == null || col.precision() == 0) {
                problems.add(("%s#%s declares no @Column precision, so the column's ceiling is "
                        + "unverifiable and a cap of %s rests on nothing.")
                        .formatted(l.entityType().getSimpleName(), l.entityField(), cap.toPlainString()));
                continue;
            }
            BigDecimal ceiling = ceilingOf(col);
            if (cap.compareTo(ceiling) > 0) {
                problems.add(("%s#%s @DecimalMax(%s) vs %s.%s decimal(%d,%d), which holds at most "
                        + "%s — the CAP IS LOOSER THAN THE COLUMN, so an accepted value still fails "
                        + "at the insert with SQL Server 8115 and an opaque 500. %s")
                        .formatted(l.requestType().getSimpleName(), l.component(), cap.toPlainString(),
                                l.entityType().getSimpleName(), l.entityField(),
                                col.precision(), col.scale(), ceiling.toPlainString(), l.why()));
            }
        }

        assertThat(problems).as("""
            A @DecimalMax on a request field must FIT the column its value lands in — and where the \
            server derives another column from it, the cap that matters is the one the derivation's \
            range depends on, not the one the column would tolerate.

            %s""".formatted(String.join("\n  ", problems))).isEmpty();
    }

    @Test
    void everyNumericComponentOfTheseRequestTypesIsMirrored() {
        var declared = new TreeSet<String>();
        for (NumericLanding l : NUMERIC_MIRRORED) declared.add(key(l.requestType(), l.component()));

        var undeclared = new TreeSet<String>();
        for (Class<?> t : NUMERIC_COVERED) {
            for (RecordComponent rc : componentsOf(t)) {
                if (!BigDecimal.class.equals(rc.getType())) continue;
                String k = key(t, rc.getName());
                if (!declared.contains(k)) undeclared.add(k);
            }
        }

        assertThat(undeclared).as("""
            A BigDecimal component was added to a request type F2-425 bounded, and nothing says what \
            column it lands in or what bounds it. The reverse direction is the one that matters here: \
            these two records had a @Size on every String and NOTHING on their two numbers (F2-215's \
            two-of-N inside one record), so an unbounded number is exactly the shape that arrives \
            looking finished.

            %s""".formatted(String.join("\n  ", undeclared))).isEmpty();
    }

    @Test
    void theNumericMappingItselfIsNotEmpty() {
        // Anti-vacuity, same reason as theMappingItselfIsNotEmpty above: both numeric legs are
        // "no problems found", which an empty NUMERIC_MIRRORED satisfies perfectly.
        assertThat(NUMERIC_MIRRORED).hasSizeGreaterThanOrEqualTo(4);
        assertThat(NUMERIC_COVERED).hasSizeGreaterThanOrEqualTo(2);
        for (NumericLanding l : NUMERIC_MIRRORED) {
            assertThat(numericCapOf(l.requestType(), l.component(), DecimalMax.class))
                .as("%s#%s cap resolves", l.requestType().getSimpleName(), l.component())
                .isNotNull();
        }
    }

    // ─── F2-428: the third carrier — a form field's maxLength ───────────────────

    /**
     * A form cap and the column its value lands in.
     *
     * <p>F2-428 — the same question the two carriers above ask ("does a declared cap match the
     * column it guards?"), asked of the FRONTEND. {@code FormModal} enforces {@code maxLength}
     * client-side, so a cap LOOSER than its column means the form accepts text the server then
     * refuses. Where no {@code @Size} guards the write — and the committee-papers circulate route
     * binds the entity with no {@code @Valid}, so none does — that surfaces as SQL Server 8152, an
     * opaque 500, after the operator has written the text.
     *
     * <p>⚠ A cap TIGHTER than its column is the other direction and is NOT a defect: the form
     * refuses text the schema permits, which is protective. Whether to widen one is a product
     * decision (F2-184 gives a rule for a cap that is looser and none for one that is tighter), so
     * every leg here asserts {@code cap <= column} and never equality. {@code authorName} is
     * capped 128 over a 255 column on purpose and must keep passing.
     */
    private record FormLanding(String feFile, String key, Class<?> entity, String field, String why) {}

    /** A located form cap: where it is declared, and the number. */
    private record FormCap(String file, int line, String key, int cap) {}

    private static final Path FE_SRC = Path.of("..", "tss-grc-ui-v2", "src");

    /**
     * The one form resolved END TO END in F2-428, kept as a declared landing for the reason the
     * {@code MIRRORED} list above is declared: nothing in the code connects a form field to a
     * column, and {@link #noFormCapExceedsTheColumnItUniquelyResolvesTo} can only pair a key whose
     * name resolves to exactly one column length across all 421 entities. That derived leg covers
     * 78 of 184 caps; these six are covered whatever the name ambiguity, and they are the positive
     * control for the derived one.
     */
    private static final List<FormLanding> FORM_MIRRORED = List.of(
        new FormLanding("components/committees/MeetingPapersPanel.tsx", "paperCode",
            CommitteeMeetingPaper.class, "paperCode",
            "POST /api/v1/committees/papers/circulate binds the entity, so the form key IS the column"),
        new FormLanding("components/committees/MeetingPapersPanel.tsx", "titleEn",
            CommitteeMeetingPaper.class, "titleEn", "same route; cap 255 over a 500 column (tighter)"),
        new FormLanding("components/committees/MeetingPapersPanel.tsx", "titleAr",
            CommitteeMeetingPaper.class, "titleAr", "same route; cap 255 over a 500 column (tighter)"),
        new FormLanding("components/committees/MeetingPapersPanel.tsx", "authorName",
            CommitteeMeetingPaper.class, "authorName",
            "same route; cap 128 over a 255 column — TIGHTER on purpose, and the sibling whose "
            + "number authorRole was mirroring when it should have been reading its own column"),
        new FormLanding("components/committees/MeetingPapersPanel.tsx", "authorRole",
            CommitteeMeetingPaper.class, "authorRole",
            "F2-428's defect: cap was 128 over NVARCHAR(120), so FormModal accepted 121-128 "
            + "characters and the INSERT raised 8152. Now 120 — the column's own ceiling"),
        new FormLanding("components/committees/MeetingPapersPanel.tsx", "reason",
            CommitteeMeetingPaper.class, "latePaperReason",
            "POST /papers/{id}/mark-late binds LateRequest(String reason), which the service writes "
            + "to late_paper_reason; cap 300 over a 500 column (tighter)")
    );

    @Test
    void everyDeclaredFormCapFitsTheColumnItGuards() {
        List<FormCap> caps = formCaps();
        List<String> bad = new ArrayList<>();
        for (FormLanding l : FORM_MIRRORED) {
            Integer cap = caps.stream()
                .filter(c -> c.file().equals(l.feFile()) && c.key().equals(l.key()))
                .map(FormCap::cap)
                .findFirst().orElse(null);
            assertThat(cap)
                .as("""
                    %s declares no maxLength for `%s`, so this landing is stale. Delete the entry \
                    or re-derive it — a landing that resolves to nothing passes every assertion \
                    below it and reads exactly like a verified one.""".formatted(l.feFile(), l.key()))
                .isNotNull();
            Integer col = columnLengthOf(l.entity(), l.field());
            assertThat(col)
                .as("%s#%s declares no @Column(length) — the landing names the wrong field, or the "
                    + "column is NVARCHAR(MAX) and the cap should be deleted rather than mirrored",
                    l.entity().getSimpleName(), l.field())
                .isNotNull();
            if (cap > col) {
                bad.add("%s :: %s cap %d > %s.%s column %d — %s"
                    .formatted(l.feFile(), l.key(), cap, l.entity().getSimpleName(), l.field(),
                               col, l.why()));
            }
        }
        assertThat(bad).as("""
            A form cap exceeds the column its value lands in. FormModal enforces maxLength \
            client-side, so the form ACCEPTS what the column refuses: with no @Size on the write \
            path that is SQL 8152 — an opaque 500 — after the operator has typed the text. Lower \
            the cap to the column (F2-184: the database already refuses more, so this cannot break \
            a request that works today), never widen the column to match a form.
              %s""".formatted(String.join("\n  ", bad))).isEmpty();
    }

    @Test
    void noFormCapExceedsTheColumnItUniquelyResolvesTo() {
        Map<String, Set<Integer>> lengths = columnLengthsByFieldName();
        List<String> bad = new ArrayList<>();
        Set<String> declared = new java.util.HashSet<>();
        for (FormLanding l : FORM_MIRRORED) declared.add(l.feFile() + "::" + l.key());
        for (FormCap c : formCaps()) {
            if (declared.contains(c.file() + "::" + c.key())) continue;  // declared leg owns it
            Set<Integer> ls = lengths.get(c.key());
            if (ls == null || ls.size() != 1) continue;   // measured by the census leg below
            int col = ls.iterator().next();
            if (c.cap() > col) {
                bad.add("%s:%d :: %s cap %d > column %d".formatted(c.file(), c.line(), c.key(),
                                                                   c.cap(), col));
            }
        }
        assertThat(bad).as("""
            A form cap exceeds the ONLY column length its field name resolves to across every \
            @Entity. This is a hard zero with no exemption map: there is no correct reason for a \
            form to accept more characters than its column can hold, so there is no correct code \
            for this leg to fail on (F2-214). ⚠ The pairing is by FIELD NAME, so if it names \
            the wrong column, declare the real landing in FORM_MIRRORED and this leg skips it -- \
            resolve it by READING, never by relaxing the assertion (F2-246).
              %s""".formatted(String.join("\n  ", bad))).isEmpty();
    }

    @Test
    void theFormCapPopulationIsMeasuredAndItsBlindSpotIsNotEmpty() {
        assertThat(Files.isDirectory(FE_SRC))
            .as("the frontend tree is not at %s — this gate reads across the module boundary and "
                + "must FAIL rather than skip, because unresolved reads identically to clean "
                + "(F2-206)", FE_SRC.toAbsolutePath())
            .isTrue();

        List<FormCap> caps = formCaps();
        Map<String, Set<Integer>> lengths = columnLengthsByFieldName();

        // Anti-vacuity. Both legs above are "no problems found", which an empty scan satisfies
        // perfectly — and a floor must sit above the largest WRONG answer, which here is zero
        // (a rotted regex, or a frontend root that stopped resolving). F2-215.
        assertThat(caps).as("the frontend maxLength scan found nothing").hasSizeGreaterThanOrEqualTo(150);
        assertThat(lengths).as("the @Column(length) reflection found nothing")
            .hasSizeGreaterThanOrEqualTo(300);
        assertThat(FORM_MIRRORED).hasSizeGreaterThanOrEqualTo(6);

        long unique = caps.stream().filter(c -> {
            Set<Integer> ls = lengths.get(c.key());
            return ls != null && ls.size() == 1;
        }).count();
        long ambiguous = caps.stream().filter(c -> {
            Set<Integer> ls = lengths.get(c.key());
            return ls != null && ls.size() > 1;
        }).count();

        assertThat(unique).as("no cap pairs uniquely, so the hard-zero leg above measures nothing")
            .isGreaterThanOrEqualTo(60);

        // ⚠ The blind spot, asserted rather than implied. A hard zero over a population the
        // instrument cannot see is the most reassuring possible artefact (F2-205), so the
        // NON-EMPTINESS of the unresolved set is a claim about the resolver: `titleEn` alone is
        // declared with eight different lengths across the platform, so pairing it needs the
        // endpoint resolved per form, which this gate does not do. Do not delete this by
        // narrowing the scan until something actually resolves them — re-derive first.
        assertThat(ambiguous)
            .as("""
                No form cap resolves ambiguously any more. Either something now pairs a form field \
                to its endpoint (in which case widen the hard-zero leg to cover them and delete \
                this assertion deliberately), or the scan has stopped matching — and the second \
                looks exactly like the first.""")
            .isGreaterThanOrEqualTo(1);
    }

    @Test
    void theFormCapDetectorReadsTheKeyOfItsOwnObjectLiteral() {
        // A synthetic self-test, because both corpus legs pass equally over a clean tree and over a
        // dead regex (F2-235). The first draft of this detector took the nearest `key:` within 400
        // characters and so crossed object literals — it paired MeetingPapersPanel's `reason` with
        // BreakGlassEvent.reason, a table that panel has nothing to do with.
        String fixture = """
            const a: FieldDef[] = [
              { key: 'first',  kind: 'text', maxLength: 10 },
              { key: 'second', kind: 'text', maxLength: 20 },
            ];
            // { key: 'commented', kind: 'text', maxLength: 999 }
            const b = [{ key: 'third', kind: 'text', span: 6, maxLength: 30 }];
            """;
        List<FormCap> found = capsIn("fixture.tsx", fixture);
        assertThat(found).extracting(FormCap::key, FormCap::cap)
            .as("each cap must take the key of ITS OWN object literal, and a commented-out field "
                + "must not be read as a declaration (F2-193: a scan that reads the epitaph as the "
                + "corpse fails on a correct tree)")
            .containsExactly(org.assertj.core.api.Assertions.tuple("first", 10),
                             org.assertj.core.api.Assertions.tuple("second", 20),
                             org.assertj.core.api.Assertions.tuple("third", 30));
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private static String key(Class<?> c, String member) {
        return c.getName() + "#" + member;
    }

    private static RecordComponent[] componentsOf(Class<?> c) {
        return c.isRecord() ? c.getRecordComponents() : new RecordComponent[0];
    }

    /** The declared {@code @Size#max} on a record component, or null when it declares none. */
    private static Integer capOf(Class<?> type, String component) {
        for (RecordComponent rc : componentsOf(type)) {
            if (!rc.getName().equals(component)) continue;
            Size s = rc.getAnnotation(Size.class);
            if (s == null) {
                // ⚠ This branch is load-bearing, not defensive: jakarta.validation.constraints.Size
                // declares no RECORD_COMPONENT target, so javac propagates it to the backing field
                // and the accessor, and RecordComponent#getAnnotation ALWAYS answers null for it.
                // A reflection sweep that only asked the component would report every cap in this
                // file as absent — and "absent" is indistinguishable from "nothing to check here".
                Field f = fieldOf(type, component);
                if (f != null) s = f.getAnnotation(Size.class);
            }
            return s == null ? null : s.max();
        }
        return null;
    }

    /**
     * The declared {@code @DecimalMax} / {@code @DecimalMin} value on a record component.
     *
     * <p>Component first, then the backing field — the same fallback {@link #capOf} needs and for
     * the same reason: a constraint annotation without a {@code RECORD_COMPONENT} target is
     * propagated by javac to the field and the accessor, and {@code RecordComponent#getAnnotation}
     * then answers null for it. A sweep that asked only the component would report every cap here
     * as absent, and absent is indistinguishable from nothing-to-check-here (F2-185(b)).
     */
    private static BigDecimal numericCapOf(Class<?> type, String component,
                                           Class<? extends java.lang.annotation.Annotation> kind) {
        for (RecordComponent rc : componentsOf(type)) {
            if (!rc.getName().equals(component)) continue;
            String raw = valueOf(rc.getAnnotation(kind));
            if (raw == null) {
                Field f = fieldOf(type, component);
                if (f != null) raw = valueOf(f.getAnnotation(kind));
            }
            return raw == null ? null : new BigDecimal(raw);
        }
        return null;
    }

    private static String valueOf(java.lang.annotation.Annotation a) {
        if (a instanceof DecimalMax max) return max.value();
        if (a instanceof DecimalMin min) return min.value();
        return null;
    }

    /**
     * The largest value a {@code decimal(precision, scale)} column can hold: {@code (precision -
     * scale)} nines, then a point and {@code scale} nines. decimal(4,2) is 99.99 — which is why an
     * IA universe entry cannot store a score of 100 however reasonable the number looks.
     */
    private static BigDecimal ceilingOf(Column col) {
        int room = col.precision() - col.scale();
        String whole = room > 0 ? "9".repeat(room) : "0";
        return new BigDecimal(col.scale() > 0 ? whole + "." + "9".repeat(col.scale()) : whole);
    }

    private static Field fieldOf(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking the chain — entities inherit audited columns from base classes
            }
        }
        return null;
    }

    // ─── F2-428 helpers ─────────────────────────────────────────────────────────

    /** One left-to-right alternation: at a `//` an inner `/*` can never open a block (F2-198). */
    private static final Pattern TS_COMMENT =
        Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");
    private static final Pattern MAX_LENGTH = Pattern.compile("maxLength\\s*:\\s*(\\d+)");
    private static final Pattern FIELD_KEY = Pattern.compile("key\\s*:\\s*['\"](\\w+)['\"]");

    /** Blank a comment with spaces, keeping newlines so reported line numbers stay true. */
    private static String stripTs(String s) {
        Matcher m = TS_COMMENT.matcher(s);
        StringBuilder out = new StringBuilder();
        int at = 0;
        while (m.find()) {
            out.append(s, at, m.start());
            out.append(m.group().replaceAll("[^\\n]", " "));
            at = m.end();
        }
        return out.append(s.substring(at)).toString();
    }

    /** Every `maxLength: N` in the frontend, keyed by the `key:` of its own object literal. */
    private static List<FormCap> formCaps() {
        List<FormCap> out = new ArrayList<>();
        try (var walk = Files.walk(FE_SRC)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String nm = p.getFileName().toString();
                if (!nm.endsWith(".ts") && !nm.endsWith(".tsx")) continue;
                String raw = Files.readString(p);
                if (!raw.contains("maxLength")) continue;
                String rel = FE_SRC.relativize(p).toString().replace('\\', '/');
                out.addAll(capsIn(rel, raw));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read the frontend tree at " + FE_SRC, e);
        }
        return out;
    }

    private static List<FormCap> capsIn(String rel, String raw) {
        String s = stripTs(raw);
        List<FormCap> out = new ArrayList<>();
        Matcher m = MAX_LENGTH.matcher(s);
        while (m.find()) {
            int open = enclosingObject(s, m.start());
            if (open < 0) continue;
            Matcher k = FIELD_KEY.matcher(s.substring(open, m.start()));
            String key = null;
            while (k.find()) key = k.group(1);       // the LAST key before the cap, same literal
            if (key == null) continue;
            int line = (int) s.substring(0, m.start()).chars().filter(c -> c == '\n').count() + 1;
            out.add(new FormCap(rel, line, key, Integer.parseInt(m.group(1))));
        }
        return out;
    }

    /** The offset of the `{` opening the brace-balanced object literal containing {@code at}. */
    private static int enclosingObject(String s, int at) {
        int depth = 0;
        for (int i = at; i > 0; i--) {
            char c = s.charAt(i);
            if (c == '}') depth++;
            else if (c == '{') {
                if (depth == 0) return i;
                depth--;
            }
        }
        return -1;
    }

    /**
     * Every bounded String column in the platform, as {@code field name -> the lengths declared
     * for it}. Reflection, never a source regex: this codebase has twice under-counted a security
     * surface with one, and a field name is far more ambiguous than a class name — 15 entity simple
     * names are declared in two packages (F2-222 / F2-276), so anything multi-valued is left to the
     * census leg rather than guessed.
     */
    private static Map<String, Set<Integer>> columnLengthsByFieldName() {
        Map<String, Set<Integer>> out = new LinkedHashMap<>();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (ClassNotFoundException e) {
                continue;
            }
            // Exclude test fixtures by CODE SOURCE, never by a name ending in "Test": a name test
            // lets any member escape by being renamed (F2-192), and F2-241 recorded a scan of this
            // shape reaching target/test-classes and reporting its own fixture.
            var src = c.getProtectionDomain().getCodeSource();
            if (src != null && src.getLocation().toString().contains("test-classes")) continue;
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() != String.class) continue;
                Column col = f.getAnnotation(Column.class);
                if (col == null) continue;
                // ⚠ @Column(columnDefinition = "NVARCHAR(MAX)") still reports
                // length() == 255 through reflection -- F2-276 recorded that columnDefinition
                // is a CLAIM, not a constraint, because Hibernate only emits it when
                // generating DDL and this platform runs ddl-auto=none. A form capping such a
                // column at 500 would pair as 500 > 255 and read as a defect on CORRECT code,
                // which is the one failure a gate must never have (F2-214). The schema
                // deliberately leaves those unbounded (F2-184).
                if (col.columnDefinition().toUpperCase(java.util.Locale.ROOT)
                        .contains("MAX")) {
                    continue;
                }
                out.computeIfAbsent(f.getName(), k -> new TreeSet<>()).add(col.length());
            }
        }
        return out;
    }


    /** The declared {@code @Column(length)} on an entity field, or null when it declares none. */
    private static Integer columnLengthOf(Class<?> entity, String field) {
        Field f = fieldOf(entity, field);
        if (f == null) return null;
        Column col = f.getAnnotation(Column.class);
        return col == null ? null : col.length();
    }

}

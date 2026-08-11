package com.tss.grc.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

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
import jakarta.validation.constraints.Size;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

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
        new Landing(VendorBcpAttestationDtos.AttestationReviewRequest.class, "reviewedByName",
            VendorBcpAttestation.class, "reviewedByName",
            "Same setter block; F2-184 recorded this one as 'needs checking'."),
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
        new Landing(BowTieDtos.ApproveRequest.class, "approverName",
            BowTieAnalysis.class, "approvedByName",
            "BowTieAnalysisService#approve setApprovedByName — the field is RENAMED in transit, "
            + "which is why searching for the DTO's name found no column.")
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
        assertThat(MIRRORED).hasSizeGreaterThanOrEqualTo(9);
        assertThat(COVERED).hasSizeGreaterThanOrEqualTo(8);
        Map<String, Integer> caps = new LinkedHashMap<>();
        for (Landing l : MIRRORED) caps.put(key(l.requestType(), l.component()), capOf(l.requestType(), l.component()));
        assertThat(caps.values()).allMatch(v -> v != null && v > 0);
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
}

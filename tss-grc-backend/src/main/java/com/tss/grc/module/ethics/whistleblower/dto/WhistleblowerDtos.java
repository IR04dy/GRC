package com.tss.grc.module.ethics.whistleblower.dto;

import com.tss.grc.module.ethics.whistleblower.entity.WhistleblowerCase;
import com.tss.grc.module.ethics.whistleblower.entity.WhistleblowerCaseUpdate;
import com.tss.grc.module.ethics.whistleblower.entity.WhistleblowerRetaliationComplaint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO bundle for the Whistleblower module — Sprint 13.
 *
 * <p>Three response shapes:
 * <ul>
 *   <li>{@link CaseResponse} — full internal record, returned to the
 *       Ethics Officer / Compliance / Audit Committee surfaces.</li>
 *   <li>{@link PublicCaseResponse} — what the reporter sees on
 *       {@code /status/{token}} — strips internal user-ids and
 *       triage/investigator metadata so it can be served to an
 *       anonymous caller.</li>
 *   <li>{@link CaseSubmissionResponse} — returned ONCE on submission
 *       so the submitter captures the token (we don't recover it).</li>
 * </ul>
 */
public final class WhistleblowerDtos {

    private WhistleblowerDtos() {}

    // ─── Submit (public) ────────────────────────────────────────────

    public record CaseSubmissionRequest(
            String submissionChannel,        // PUBLIC_PORTAL / INTERNAL_PORTAL / EMAIL / PHONE / IN_PERSON / EXTERNAL_PARTNER
            String reporterType,             // EMPLOYEE / MANAGER / CONTRACTOR / VENDOR / CUSTOMER / EXTERNAL / ANONYMOUS
            String reporterName,
            String reporterEmail,
            String reporterPhone,
            String reporterEmployeeId,
            Boolean isAnonymous,
            @NotBlank String category,
            String severity,
            @NotBlank @Size(max = 255) String subjectEn,
            @NotBlank @Size(max = 255) String subjectAr,
            @NotBlank String narrativeEn,
            @NotBlank String narrativeAr,
            String partiesInvolved,
            LocalDate allegedPeriodStart,
            LocalDate allegedPeriodEnd,
            Boolean involvesSeniorExecutive
    ) {}

    /** Returned once on submit — captures the token so the reporter retains it. */
    public record CaseSubmissionResponse(
            Long id,
            String caseCode,
            UUID submissionToken,
            String status,
            LocalDateTime submittedAt,
            String retentionNoticeEn,
            String retentionNoticeAr
    ) {
        public static CaseSubmissionResponse from(WhistleblowerCase c) {
            return new CaseSubmissionResponse(
                    c.getId(), c.getCaseCode(), c.getSubmissionToken(),
                    c.getStatus(), c.getSubmittedAt(),
                    "Please save this tracking token. We cannot recover it for you.",
                    "يرجى حفظ رمز التتبّع — لا يمكننا استرجاعه لك."
            );
        }
    }

    /** Reporter-facing reply via the public-token path. */
    public record ReporterReplyRequest(
            @NotBlank String bodyEn,
            @NotBlank String bodyAr
    ) {}

    // ─── Internal: triage / investigation / close ───────────────────

    public record TriageRequest(
            @NotBlank String triageDecisionEn,
            @NotBlank String triageDecisionAr,
            String severity,
            Long assignedInvestigatorUserId,
            Boolean involvesSeniorExecutive
    ) {}

    public record InvestigationUpdateRequest(
            @NotBlank String bodyEn,
            @NotBlank String bodyAr,
            Boolean isVisibleToReporter,
            String updateType                 // INVESTIGATION_NOTE / FINDING_RECORDED
    ) {}

    public record CloseRequest(
            @NotBlank String terminalStatus,  // SUBSTANTIATED / UNSUBSTANTIATED / REFERRED / CLOSED_NO_ACTION / CLOSED_REMEDIATED
            @NotBlank String closureSummaryEn,
            @NotBlank String closureSummaryAr,
            Long linkedCapaTaskId
    ) {}

    // ─── Full responses ─────────────────────────────────────────────

    public record CaseResponse(
            Long id,
            String caseCode,
            UUID submissionToken,
            String submissionChannel,
            String reporterType,
            String reporterName,
            String reporterEmail,
            String reporterPhone,
            String reporterEmployeeId,
            Boolean isAnonymous,
            String category,
            String severity,
            String subjectEn,
            String subjectAr,
            String narrativeEn,
            String narrativeAr,
            String partiesInvolved,
            LocalDate allegedPeriodStart,
            LocalDate allegedPeriodEnd,
            Boolean involvesSeniorExecutive,
            String status,
            Long triageByUserId,
            LocalDateTime triageAt,
            String triageDecisionEn,
            String triageDecisionAr,
            Long assignedInvestigatorUserId,
            LocalDateTime investigationStartedAt,
            LocalDateTime closedAt,
            String closureSummaryEn,
            String closureSummaryAr,
            Long linkedCapaTaskId,
            LocalDateTime submittedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static CaseResponse from(WhistleblowerCase c) {
            return new CaseResponse(
                    c.getId(), c.getCaseCode(), c.getSubmissionToken(),
                    c.getSubmissionChannel(), c.getReporterType(),
                    c.getReporterName(), c.getReporterEmail(),
                    c.getReporterPhone(), c.getReporterEmployeeId(),
                    c.getIsAnonymous(),
                    c.getCategory(), c.getSeverity(),
                    c.getSubjectEn(), c.getSubjectAr(),
                    c.getNarrativeEn(), c.getNarrativeAr(),
                    c.getPartiesInvolved(),
                    c.getAllegedPeriodStart(), c.getAllegedPeriodEnd(),
                    c.getInvolvesSeniorExecutive(),
                    c.getStatus(),
                    c.getTriageByUserId(), c.getTriageAt(),
                    c.getTriageDecisionEn(), c.getTriageDecisionAr(),
                    c.getAssignedInvestigatorUserId(),
                    c.getInvestigationStartedAt(),
                    c.getClosedAt(),
                    c.getClosureSummaryEn(), c.getClosureSummaryAr(),
                    c.getLinkedCapaTaskId(),
                    c.getSubmittedAt(),
                    c.getCreatedAt(), c.getUpdatedAt()
            );
        }
    }

    /** Reporter-facing public response — internal user-ids stripped. */
    public record PublicCaseResponse(
            String caseCode,
            String status,
            String category,
            String severity,
            String subjectEn,
            String subjectAr,
            LocalDateTime submittedAt,
            LocalDateTime closedAt,
            java.util.List<PublicUpdateResponse> updates
    ) {}

    public record PublicUpdateResponse(
            String updateType,
            String bodyEn,
            String bodyAr,
            LocalDateTime postedAt,
            Boolean fromReporter
    ) {
        public static PublicUpdateResponse from(WhistleblowerCaseUpdate u) {
            return new PublicUpdateResponse(
                    u.getUpdateType(), u.getBodyEn(), u.getBodyAr(),
                    u.getCreatedAt(), u.getActorUserId() == null && "REPORTER_REPLY".equals(u.getUpdateType())
            );
        }
    }

    public record UpdateResponse(
            Long id,
            Long caseId,
            String updateType,
            Long actorUserId,
            Boolean isVisibleToReporter,
            String bodyEn,
            String bodyAr,
            String attachmentRefs,
            LocalDateTime createdAt
    ) {
        public static UpdateResponse from(WhistleblowerCaseUpdate u) {
            return new UpdateResponse(
                    u.getId(), u.getCaseId(), u.getUpdateType(),
                    u.getActorUserId(), u.getIsVisibleToReporter(),
                    u.getBodyEn(), u.getBodyAr(),
                    u.getAttachmentRefs(),
                    u.getCreatedAt()
            );
        }
    }

    // ─── Retaliation sub-flow ───────────────────────────────────────

    public record RetaliationComplaintRequest(
            Long relatedCaseId,
            Long claimantUserId,
            String claimantName,
            @NotBlank String claimedActor,
            @NotBlank String claimedAction,
            LocalDate claimedAtPeriodStart
    ) {}

    // F2-185(a): `RetaliationTransitionRequest(terminalStatus, assignedProtectorUserId)` was deleted
    // here along with its only consumer. The three wired endpoints encode the terminal state in the
    // path and already accept `protectorUserId` as a query parameter, so this record described a
    // capability that existed three times over. See WhistleblowerRetaliationService for why the
    // register's "the lifecycle cannot be closed through the API" grading was false.

    public record RetaliationComplaintResponse(
            Long id,
            Long organisationId,
            Long relatedCaseId,
            Long claimantUserId,
            String claimantName,
            String claimedActor,
            String claimedAction,
            LocalDate claimedAtPeriodStart,
            String status,
            Long assignedProtectorUserId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static RetaliationComplaintResponse from(WhistleblowerRetaliationComplaint r) {
            return new RetaliationComplaintResponse(
                    r.getId(), r.getOrganisationId(), r.getRelatedCaseId(),
                    r.getClaimantUserId(), r.getClaimantName(),
                    r.getClaimedActor(), r.getClaimedAction(),
                    r.getClaimedAtPeriodStart(),
                    r.getStatus(), r.getAssignedProtectorUserId(),
                    r.getCreatedAt(), r.getUpdatedAt()
            );
        }
    }
}

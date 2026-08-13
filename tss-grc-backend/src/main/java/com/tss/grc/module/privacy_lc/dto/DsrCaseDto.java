package com.tss.grc.module.privacy_lc.dto;

import com.tss.grc.module.privacy_lc.entity.DsrCase;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DsrCaseDto {
    private String id;
    @NotBlank @Pattern(regexp = "ACCESS|RECTIFICATION|ERASURE|OBJECTION|RESTRICTION|PORTABILITY|WITHDRAW_CONSENT")
    private String type;
    @NotBlank private String subjectName;
    @NotBlank @Size(min = 4, max = 10) private String nationalIdLast4;
    @Email @NotBlank private String contactEmail;
    private String status;
    private LocalDateTime filedAt;
    private LocalDateTime identityVerifiedAt;
    private String verificationMethod;
    private LocalDateTime dataCollectedAt;
    private String dataSummaryEn;
    private String dataSummaryAr;
    private LocalDateTime redactionCompletedAt;
    private String deliveryMethod;
    private LocalDateTime deliveredAt;
    private LocalDate slaDeadline;
    private Boolean extended;
    private String extensionReasonEn;
    private String extensionReasonAr;
    private String ropaIds;

    // F2-202. These three columns have existed since V17 and the DTO did not declare them,
    // so no client could ever see them — the F2-198 `crossBorderTransfer` shape, where a
    // field is already in the table and the wire merely never mentions it.
    //
    //   • `slaBreachedAt` is the stamp `PrivacyGapService.dsrSlaSweep()` writes when a case
    //     passes its PDPL Article 4 deadline. Without it a client can only compare the
    //     deadline against its own clock, which answers "is it late?" but never "has this
    //     platform RECORDED the breach?" — and those differ: F2-196 found two EXTENDED cases
    //     that were 4 and 11 days overdue with `sla_breached_at = NULL` because the sweep
    //     excluded them. A client-side comparison would have shown them as breached while
    //     the platform had escalated nothing.
    //   • `rejectionReason*` is the lawful basis for refusing a subject's request. PDPL
    //     Article 4 requires a refusal to be reasoned; with no field on the wire a rejected
    //     case arrived at the client with no reason at all.
    private LocalDateTime slaBreachedAt;
    private String rejectionReasonEn;
    private String rejectionReasonAr;

    public static DsrCaseDto from(DsrCase e) {
        return DsrCaseDto.builder()
            .id(e.getExternalId()).type(e.getType())
            .subjectName(e.getSubjectName()).nationalIdLast4(e.getNationalIdLast4()).contactEmail(e.getContactEmail())
            .status(e.getStatus()).filedAt(e.getFiledAt())
            .identityVerifiedAt(e.getIdentityVerifiedAt()).verificationMethod(e.getVerificationMethod())
            .dataCollectedAt(e.getDataCollectedAt())
            .dataSummaryEn(e.getDataSummaryEn()).dataSummaryAr(e.getDataSummaryAr())
            .redactionCompletedAt(e.getRedactionCompletedAt())
            .deliveryMethod(e.getDeliveryMethod()).deliveredAt(e.getDeliveredAt())
            .slaDeadline(e.getSlaDeadline()).extended(e.getExtended())
            .extensionReasonEn(e.getExtensionReasonEn()).extensionReasonAr(e.getExtensionReasonAr())
            .ropaIds(e.getRopaIds())
            .slaBreachedAt(e.getSlaBreachedAt())
            .rejectionReasonEn(e.getRejectionReasonEn()).rejectionReasonAr(e.getRejectionReasonAr())
            .build();
    }

    @Getter @Setter @NoArgsConstructor
    public static class TransitionRequest {
        @NotBlank @Pattern(regexp = "INTAKE|VERIFYING_IDENTITY|COLLECTING|REDACTING|DELIVERED|REJECTED|EXTENDED")
        private String status;
        private String verificationMethod;
        private String deliveryMethod;
        private String dataSummaryEn;
        private String dataSummaryAr;
        /**
         * F2-202. Recorded only on REJECTED. PDPL Article 4 requires a refusal to be
         * reasoned, and until this field existed the transition endpoint had no way to
         * carry one — the columns were writable by nothing.
         */
        private String rejectionReasonEn;
        private String rejectionReasonAr;
    }
}

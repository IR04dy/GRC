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
    }
}

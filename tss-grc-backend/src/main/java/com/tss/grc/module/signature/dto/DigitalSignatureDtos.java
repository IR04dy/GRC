package com.tss.grc.module.signature.dto;

import com.tss.grc.module.signature.entity.DigitalSignatureTransaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Wire types for {@link DigitalSignatureTransaction}. Kept in a single
 * file because they're small + cohesive (read response + 3 write payloads).
 */
public final class DigitalSignatureDtos {

    private DigitalSignatureDtos() { /* static holder */ }

    /** Outbound representation. */
    public record Response(
            Long id,
            String relatedEntityType, Long relatedEntityId, String relatedEntityLabel,
            Long signerUserId, String signerName, String signerEmail, String signerRole,
            Long initiatorUserId, String initiatorName,
            String signatureProvider, String purpose, String purposeAr,
            String signedDocumentHash, String certificateReference,
            String status, String rejectionReason,
            LocalDateTime signatureTimestamp, LocalDateTime expiryDate,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static Response from(DigitalSignatureTransaction d) {
            return new Response(
                    d.getId(),
                    d.getRelatedEntityType(), d.getRelatedEntityId(), d.getRelatedEntityLabel(),
                    d.getSignerUserId(), d.getSignerName(), d.getSignerEmail(), d.getSignerRole(),
                    d.getInitiatorUserId(), d.getInitiatorName(),
                    d.getSignatureProvider(), d.getPurpose(), d.getPurposeAr(),
                    d.getSignedDocumentHash(), d.getCertificateReference(),
                    d.getStatus(), d.getRejectionReason(),
                    d.getSignatureTimestamp(), d.getExpiryDate(),
                    d.getCreatedAt(), d.getUpdatedAt()
            );
        }
    }

    /** Request to open a new signature transaction. */
    public record CreateRequest(
            @NotBlank @Size(max = 50) String relatedEntityType,
            @NotNull Long relatedEntityId,
            @Size(max = 255) String relatedEntityLabel,
            Long signerUserId,
            @NotBlank @Size(max = 255) String signerName,
            @Size(max = 255) String signerEmail,
            @Size(max = 100) String signerRole,
            @Size(max = 50) String signatureProvider,
            @NotBlank @Size(max = 500) String purpose,
            @Size(max = 500) String purposeAr,
            LocalDateTime expiryDate
    ) { }

    /** Signer attests; document hash must accompany the SIGN action. */
    public record SignRequest(
            @NotBlank @Size(max = 64) String signedDocumentHash,
            @Size(max = 255) String certificateReference
    ) { }

    /** Signer declines; reason is mandatory per the table CHECK constraint. */
    public record RejectRequest(
            @NotBlank @Size(max = 500) String rejectionReason
    ) { }
}

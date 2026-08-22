package com.tss.grc.module.signature.dto;

import java.time.LocalDateTime;

public record SignatureResponse(
    Long id,
    String entityType,
    Long entityId,
    String intent,
    String statementEn,
    Long signerId,
    String signerName,
    /** F2-277 — the Arabic half of the derived signer label, from iam.users.full_name_ar. */
    String signerNameAr,
    String signerRole,
    String payloadHash,
    LocalDateTime signedAt
) {}

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
    String signerRole,
    String payloadHash,
    LocalDateTime signedAt
) {}

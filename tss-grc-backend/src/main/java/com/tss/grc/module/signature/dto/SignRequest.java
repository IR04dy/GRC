package com.tss.grc.module.signature.dto;

import jakarta.validation.constraints.NotBlank;

/** A request to sign an entity: the intent (APPROVE/REVIEW/…) and the statement agreed to. */
public record SignRequest(
    @NotBlank String intent,
    String statementEn
) {}

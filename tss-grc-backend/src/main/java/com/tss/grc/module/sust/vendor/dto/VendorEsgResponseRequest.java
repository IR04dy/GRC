package com.tss.grc.module.sust.vendor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * S-O — vendor ESG data submitted in response to a survey request. Drives the
 * SENT → RESPONDED transition; the service recomputes {@code esgScore} from
 * these fields (never a client-supplied score). Percentages are 0..100.
 */
public record VendorEsgResponseRequest(
        @PositiveOrZero BigDecimal reportedScope12,
        @Min(0) @Max(100) Integer renewablePct,
        @Min(0) @Max(100) Integer saudizationPct,
        Boolean hasNetZeroTarget,
        Boolean iso14001
) {}

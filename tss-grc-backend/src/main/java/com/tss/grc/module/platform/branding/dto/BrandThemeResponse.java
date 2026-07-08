package com.tss.grc.module.platform.branding.dto;

/**
 * Resolved brand theme for the current tenant — every field populated, with platform
 * defaults substituted for any unset override. {@code customised} is true when a stored
 * row exists (the tenant has saved at least one override).
 */
public record BrandThemeResponse(
    String orgNameEn,
    String orgNameAr,
    String taglineEn,
    String taglineAr,
    String primaryHex,
    String accentHex,
    String logoDataUrl,
    boolean customised
) {}

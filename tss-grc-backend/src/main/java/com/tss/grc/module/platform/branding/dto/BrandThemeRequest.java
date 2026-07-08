package com.tss.grc.module.platform.branding.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Upsert payload for the current tenant's brand theme. All fields optional — a null
 * field clears the override and the resolver falls back to the platform default.
 * Colours must be {@code #RRGGBB} (validated so a bad value can never reach the PDF
 * colour parser).
 */
public record BrandThemeRequest(
    @Size(max = 200) String orgNameEn,
    @Size(max = 200) String orgNameAr,
    @Size(max = 300) String taglineEn,
    @Size(max = 300) String taglineAr,
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "primaryHex must be #RRGGBB") String primaryHex,
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "accentHex must be #RRGGBB") String accentHex,
    String logoDataUrl
) {}

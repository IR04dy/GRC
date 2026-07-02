package com.tss.grc.module.sust.ghg.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GHG unit registry — S-J.
 *
 * <p>Converts {@code activityData × emissionFactor} into tCO₂e based on the
 * <em>mass</em> unit embedded in the emission-factor unit string (the part
 * before the {@code /}, e.g. {@code kgCO2e/L} → {@code kg}). Replaces the old
 * two-branch string heuristic ({@code kg*}→÷1000, else ×1) with an explicit,
 * documented registry so the calculator handles g / kg / t / kt / Mt uniformly
 * and is unit-testable.
 *
 * <p>Only the CO₂e mass unit matters for the tonnes conversion — the activity
 * side of the factor (per litre / per kWh / per km …) is dimensionless here
 * because {@code activityData} is already expressed in that activity unit.
 * Unrecognised units fall back to "the factor is already tCO₂e/activity-unit"
 * (multiplier 1), preserving the previous behaviour for exotic inputs.
 */
public final class GhgUnitRegistry {

    private GhgUnitRegistry() {}

    /** CO₂e mass unit prefix → multiplier to convert the raw product into tonnes. */
    private static final Map<String, BigDecimal> MASS_TO_TONNES = new LinkedHashMap<>();
    static {
        // Longer keys first so "kt"/"mt" match before "t", "kg" before "g".
        MASS_TO_TONNES.put("mt", new BigDecimal("1000000"));   // megatonne
        MASS_TO_TONNES.put("kt", new BigDecimal("1000"));      // kilotonne
        MASS_TO_TONNES.put("kg", new BigDecimal("0.001"));     // kilogram
        MASS_TO_TONNES.put("t",  BigDecimal.ONE);              // tonne (canonical)
        MASS_TO_TONNES.put("g",  new BigDecimal("0.000001")); // gram
    }

    /** True if the factor unit's mass prefix is one the registry recognises. */
    public static boolean isRecognised(String factorUnit) {
        return factorUnit != null && matchMultiplier(normalise(factorUnit)) != null;
    }

    /**
     * Convert {@code activityData × emissionFactor} to tCO₂e (scale 2, HALF_UP).
     * A null factor unit or an unrecognised mass prefix is treated as tonnes (×1).
     */
    public static BigDecimal toTco2e(BigDecimal activityData, BigDecimal emissionFactor, String factorUnit) {
        BigDecimal raw = activityData.multiply(emissionFactor);
        BigDecimal mult = factorUnit == null ? BigDecimal.ONE : matchMultiplier(normalise(factorUnit));
        if (mult == null) mult = BigDecimal.ONE;   // unknown → assume already tonnes
        return raw.multiply(mult).setScale(2, RoundingMode.HALF_UP);
    }

    /** e.g. "kgCO2e/L" → "kgco2e/l" → mass token "kg". */
    private static String normalise(String u) {
        return u.trim().toLowerCase();
    }

    private static BigDecimal matchMultiplier(String u) {
        // The mass unit is the leading token before "co2e" / "co2" / "/".
        for (Map.Entry<String, BigDecimal> e : MASS_TO_TONNES.entrySet()) {
            if (u.startsWith(e.getKey())) return e.getValue();
        }
        return null;
    }
}

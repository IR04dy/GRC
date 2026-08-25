package com.tss.grc.common.util;

/**
 * F2-369 — count comma-separated identifier lists correctly.
 *
 * <p>The idiom {@code csv.split(",").length} counts <b>1</b> for an EMPTY string, because
 * {@code "".split(",")} returns {@code [""]} — a one-element array holding the empty token — rather
 * than an empty array. So a denormalised count derived that way reads {@code 1} for a list of
 * {@code 0} ids. Measured on two regulator/governance-facing registers whose linked-ids field can
 * arrive as {@code ""}: {@code NcaEccFiling.capaCount} (the NCA ECC self-assessment return) and
 * {@code AuditEngagement.findingsCount} (an IIA engagement).
 *
 * <p>{@link #countNonBlank(String)} counts the non-blank tokens instead: {@code null}/blank → 0, and
 * interior blank tokens ({@code "a,,b"}) do not inflate the count.
 */
public final class CsvTokens {

    private CsvTokens() {}

    /** The number of non-blank comma-separated tokens in {@code csv} (0 for {@code null} or blank). */
    public static int countNonBlank(String csv) {
        if (csv == null || csv.isBlank()) {
            return 0;
        }
        int n = 0;
        for (String token : csv.split(",")) {
            if (!token.isBlank()) {
                n++;
            }
        }
        return n;
    }
}

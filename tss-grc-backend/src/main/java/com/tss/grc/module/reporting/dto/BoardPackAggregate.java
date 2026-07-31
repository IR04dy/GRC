package com.tss.grc.module.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * D-5 / F2-58 — the board pack as data, computed once and rendered many times.
 *
 * <h3>The defect this type exists to remove</h3>
 * <p>Before this record, {@code BoardPackService} (PDF) and {@code BoardPackPptxService} (PPTX)
 * each built the whole board pack independently — 35 and 32 repository call sites over 8 shared
 * repositories, with no shared aggregate of any kind. The PDF exposed only
 * {@code generatePdf(String) -> byte[]}, and every {@code render*} method queried and rendered in
 * one pass, so there was nothing for the deck to reuse and nothing for an API to return.
 *
 * <p>Two implementations of one document are free to drift, and they had. Measured before the
 * collapse:
 * <ul>
 *   <li><b>Different populations</b> — PDF top 20 risks, deck top 10.</li>
 *   <li><b>Different methods for the same number</b> — the deck derived {@code openFindings} by
 *       fetching 1,000 rows and filtering in Java; the PDF used a {@code COUNT} query. The deck
 *       issued a 1,000-row page purely to read {@code getTotalElements()}.</li>
 *   <li><b>Different queries for each KRI's latest reading</b> — the PDF used one
 *       {@code findLatestPerKriIn}; the deck ran a per-KRI finder <em>twice</em> per KRI (2N
 *       queries for the same answer).</li>
 *   <li><b>Different queries for change status</b> — the PDF one {@code GROUP BY}, the deck five
 *       {@code COUNT}s.</li>
 * </ul>
 * None of those produced a visibly wrong number on today's data, which is the point: a board pack
 * whose PDF and deck disagree fails silently, in a document nobody re-derives by hand.
 *
 * <h3>Bilingual by construction</h3>
 * <p>Every user-visible name is carried in both languages ({@code *En} / {@code *Ar}) and the
 * renderers choose, rather than the aggregate being built per-language. That is what makes one
 * build serve an Arabic PDF, an English deck and a language-neutral JSON response — and it follows
 * the platform rule that user-visible records carry both.
 *
 * <h3>Formatting lives in the renderers, not here</h3>
 * <p>Absent values are {@code null}, never {@code "—"}. The em-dash is a PDF/PPTX presentation
 * choice; a JSON consumer needs to distinguish "no residual score recorded" from a string. The one
 * exception is {@link RiskRow#reference()}, which is an identifier both renderers formatted
 * identically ({@code R-%05d}) and so is computed once.
 *
 * @param header             organisation identity and the instant/date the pack was built
 * @param executive          the KPI strip
 * @param topRisks           the register's highest inherent scores, ranked in SQL — see {@link #topRisks()}
 * @param aging              audit-finding escalation tiers, counted over every open finding
 * @param oldestOpenFindings the 15 oldest open findings, ordered and limited in SQL
 * @param kris               KRI band distribution plus the KRI table
 * @param frameworkCoverage  per-framework controls/policies/findings — {@code null} under {@link Scope#DECK}
 * @param changes            change-management snapshot
 * @param ethics             ethics and AML snapshot
 * @param sustainability     ESG snapshot — {@code null} under {@link Scope#DECK}
 */
public record BoardPackAggregate(
    Header header,
    Executive executive,
    List<RiskRow> topRisks,
    Aging aging,
    List<FindingRow> oldestOpenFindings,
    Kris kris,
    List<FrameworkRow> frameworkCoverage,
    Changes changes,
    Ethics ethics,
    Sustainability sustainability
) {

    /**
     * How much of the pack a consumer renders.
     *
     * <p>This exists because the deck genuinely renders a subset, and two of the sections it omits
     * are the expensive ones: {@link #frameworkCoverage()} costs three queries per active
     * framework, and {@link #sustainability()} calls the composed sustainability dashboard. Building
     * them for a consumer that never shows them would have traded the duplication fix for a
     * latency regression on an endpoint that has one today — so the collapse is explicit about who
     * needs what rather than silently making the deck slower.
     */
    public enum Scope {
        /** Everything — the PDF and the JSON aggregate. */
        FULL,
        /** The seven slides the deck renders; skips framework coverage and ESG. */
        DECK;

        public boolean includesFrameworks()      { return this == FULL; }
        public boolean includesSustainability()  { return this == FULL; }
    }

    /** @param asOf the date the aging tiers were computed against — the tiers are only meaningful with it */
    public record Header(
        Long organisationId,
        String organisationNameEn,
        String organisationNameAr,
        LocalDateTime generatedAt,
        LocalDate asOf
    ) {
        public String name(boolean ar) {
            return ar && organisationNameAr != null ? organisationNameAr : organisationNameEn;
        }
    }

    public record Executive(
        long totalRisks,
        long criticalRisks,
        long openRisks,
        long openFindings,
        long totalFindings,
        long totalKris,
        long totalControls,
        long totalEthicsCases,
        long strsFiled,
        long amlAlerts
    ) {}

    /**
     * @param reference the {@code R-00042} display id — formatted here because both renderers
     *                  formatted it identically, and an identifier that differs between the PDF and
     *                  the deck of one pack is a support call
     */
    public record RiskRow(
        Long id,
        String reference,
        String titleEn,
        String titleAr,
        Integer inherentScore,
        Integer residualScore,
        String status
    ) {
        public String title(boolean ar) {
            return ar && titleAr != null ? titleAr : titleEn;
        }
    }

    /**
     * Escalation tiers, disjoint and summing to {@link #totalOpen()}.
     *
     * <p>{@code closed} is deliberately outside that sum — the deck shows it as a sixth row and the
     * PDF does not show it at all, but it is not an escalation tier and must never be added to one.
     */
    public record Aging(
        long totalOpen,
        long onTrack,
        long manager,
        long director,
        long executive,
        long closed
    ) {}

    /** @param daysOpen days since creation, or {@code null} when the finding carries no timestamp */
    public record FindingRow(
        Long id,
        String title,
        String severity,
        String owner,
        Long daysOpen,
        String status
    ) {}

    /** @param rows every KRI, ordered by code — the deck renders the first 10, the PDF all of them */
    public record Kris(
        long green,
        long amber,
        long red,
        long noData,
        List<KriRow> rows
    ) {
        /** Total KRIs in the band distribution — the deck sizes its bar from this. */
        public long total() { return green + amber + red + noData; }
    }

    /** @param band the latest measurement's status, or {@code null} when the KRI has never been measured */
    public record KriRow(
        Long id,
        String code,
        String titleEn,
        String titleAr,
        String band,
        BigDecimal latestValue,
        BigDecimal greenThreshold,
        BigDecimal amberThreshold
    ) {
        public String title(boolean ar) {
            return ar && titleAr != null ? titleAr : titleEn;
        }
    }

    /**
     * @param fullPct percentage of mapped policies at {@code FULL} satisfaction, or {@code null}
     *                when no policy is mapped — {@code null} is not 0%, and the PDF renders it
     *                as an em-dash for exactly that reason
     */
    public record FrameworkRow(
        String code,
        String nameEn,
        String nameAr,
        long controls,
        long policies,
        Integer fullPct,
        long openFindings
    ) {
        public String name(boolean ar) {
            return ar && nameAr != null ? nameAr : nameEn;
        }
    }

    public record Changes(
        long total,
        long inFlight,
        long success,
        long rolledBack,
        long emergency
    ) {}

    public record Ethics(
        long totalCases,
        long open,
        long substantiated,
        long briberyOrKickback,
        long amlSuspicion,
        long retaliation,
        long strsFiled
    ) {}

    /**
     * ESG snapshot lifted out of the composed sustainability dashboard.
     *
     * <p>Every field is nullable and numeric-where-numeric. The dashboard returns an untyped
     * {@code Map<String,Object>}, so the builder narrows each value to a {@link Double} and leaves
     * {@code null} when it is absent — measured on live data, every field the board pack reads is a
     * number or missing. {@code available=false} means the dashboard call itself failed, which is
     * different from every metric being null and is why the flag exists: a board should not be
     * shown zeros for a subsystem that did not answer.
     */
    public record Sustainability(
        boolean available,
        Double esgScore,
        String esgBand,
        Double ghgTotal,
        Double scope3Total,
        Double cmaReadinessPct,
        Double tcfdAlignmentPct,
        int sgiOnTrack,
        int sgiTotal,
        Double openCapas,
        int topGapCount
    ) {}

    /**
     * Total data rows in this aggregate — what the export audit trail records as volume (F2-41).
     *
     * <p>Counts itemised rows only. The KPI strips are aggregates of the whole tenant and are not
     * rows that left the building; the itemised risk, finding, KRI and framework tables are.
     */
    public int rowCount() {
        return size(topRisks) + size(oldestOpenFindings)
             + (kris == null ? 0 : size(kris.rows()))
             + size(frameworkCoverage);
    }

    private static int size(List<?> l) { return l == null ? 0 : l.size(); }
}

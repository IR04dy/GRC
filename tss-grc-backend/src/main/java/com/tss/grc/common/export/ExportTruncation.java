package com.tss.grc.common.export;

import java.util.Locale;

/**
 * F2-451 — one answer to "how much of the register does this document actually contain?"
 *
 * <p>Every package and report emitter in this platform caps its fetch
 * ({@code PageRequest.of(0, MAX_ROWS)}) so an emitted document cannot grow without bound. That cap
 * is deliberate and correct. What was not correct is publishing {@code list.size()} of the capped
 * page as though it were the register's size: F2-415's rule is that <b>a capped list is fine to
 * RENDER and wrong to COUNT</b>, and on a document that leaves the platform (F2-199's harm ladder)
 * a windowed figure under an all-time label is a claim nothing downstream can check.
 *
 * <p>Measured 2026-09-03, {@code RegulatorPackageService} published
 * {@code "auditEntries": 1000} against {@code platform.audit_log} holding <b>12,470</b> rows for
 * organisation 1 in dev and <b>5,133</b> in prod — a figure 12x and 5x low, in the flattering
 * direction, inside the manifest a regulator's tooling parses.
 *
 * <p><b>Which cap bites first is predictable without measuring.</b> Of the seven capped figures in
 * this platform's emitters, exactly one truncates today, and it is the only one whose register grows
 * with every <i>write in the platform</i> rather than with the organisation's own governance work —
 * and {@code platform.audit_log} is append-only and hash-chained (F2-31), so it can never shrink.
 * Ask that of a cap before measuring it.
 *
 * <h2>Why this discloses rather than re-counting every figure</h2>
 *
 * A cover KPI strip counts <i>every</i> tile over the rows the document contains — Critical, High,
 * Open are all {@code stream().filter(...).count()} over the same capped page. Making only the
 * "Total" tile true would leave the cover <b>contradicting itself</b> (Total 1,200 beside a Critical
 * counted from 1,000 rows), which F2-203 records as worse than a figure with no provenance. So for a
 * rendered strip the honest fix is to let the tiles describe the document and <b>disclose</b> what
 * the document holds; for a machine-readable manifest, JSON can carry both, so it publishes the true
 * total <i>and</i> the included count.
 *
 * <p>The disclosure belongs <b>beside the figure</b>. {@code ExportService} already carries a cap
 * note — in a {@code ReportMetadata} scope row elsewhere on the page, reading {@code "All risks (max
 * 1000)"} — and that is weaker than it looks in two ways: it names the <b>cap</b> rather than the
 * <b>truncation</b>, so it cannot say whether anything was actually withheld, and the tile it
 * qualifies sits in a different artefact of the same document with the subtitle {@code "All
 * states"}.
 */
public final class ExportTruncation {

    private ExportTruncation() { }

    /**
     * True when the register holds more rows than the document contains.
     *
     * <p>{@code included} and {@code total} must come from the same {@link
     * org.springframework.data.domain.Page} — {@code getContent().size()} and
     * {@code getTotalElements()} — so they describe one query and cannot disagree about scope.
     */
    public static boolean isTruncated(long included, long total) {
        return total > included;
    }

    /**
     * Grouped digits, deliberately {@link Locale#ROOT}: F2-416 records that
     * {@code String.format("%,d", n)} with no locale emits Arabic-Indic digits on an {@code ar-SA}
     * JVM, and every other figure in these documents is {@code String.valueOf(int)}, i.e. ASCII.
     * A document that mixes the two numbering systems in one KPI strip is worse than either.
     */
    private static String num(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    /**
     * The disclosure, or {@code ""} when the document is complete — so a caller can append it
     * unconditionally and a complete document gains no noise.
     */
    public static String note(long included, long total, boolean arabic) {
        if (!isTruncated(included, total)) {
            return "";
        }
        return arabic
            ? "يعرض " + num(included) + " من " + num(total)
            : "showing " + num(included) + " of " + num(total);
    }

    /**
     * A KPI subtitle: the truncation note when the document is short of the register, otherwise
     * whatever the tile would have said.
     *
     * <p>This is the shape that puts the disclosure with the figure. Note that
     * {@code whenComplete} is <b>replaced</b> rather than appended to: the two subtitles this
     * platform uses in that slot read {@code "All states"} and {@code "Trail entries"}, and a
     * subtitle asserting completeness beside a truncated figure is the defect, not the fix.
     */
    public static String hint(long included, long total, boolean arabic, String whenComplete) {
        String n = note(included, total, arabic);
        if (!n.isEmpty()) {
            return n;
        }
        return whenComplete == null ? "" : whenComplete;
    }
}

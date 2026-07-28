package com.tss.grc.module.platform.export;

/**
 * F2-41 — how much data left the building, carried from the generator to the audit interceptor.
 *
 * <p>{@code ExportAuditInterceptor} records that an export happened — who, what, when, which format
 * and language — by inspecting the response's {@code Content-Disposition}. What it could not know is
 * the one thing a DLP or PDPL enquiry actually asks: <b>how many rows</b>. The interceptor sees a
 * byte stream; only the generator that built it knows it wrote 12 rows or 12,000.
 *
 * <p>So the generator deposits the count here and the interceptor collects it. A thread-local is the
 * right shape because the audit hook runs on the same request thread in {@code afterCompletion}, and
 * because it keeps the 40 file-returning endpoints free of per-controller plumbing — instrumenting
 * the two shared generators ({@link TabularExportService}, {@link RegisterExport}) covers every
 * caller of those at once.
 *
 * <p><b>Absent is not zero.</b> {@link #rowCount()} returns {@code null} when nothing recorded a
 * count, and the interceptor reports that as {@code "rowCount": null} with
 * {@code "rowCountSource": "UNINSTRUMENTED"} rather than writing {@code 0}. For a DLP enquiry
 * "exported nothing" and "we did not measure" are opposite answers, and a lying zero is worse than
 * an honest gap — the same reasoning as F2-40's tally provenance and F2-46's score provenance.
 *
 * <p><b>The interceptor must always {@link #clear()}</b>, including on the error path. Servlet
 * threads are pooled, so a leaked value would attribute one export's row count to whatever request
 * ran next on that thread — an audit trail that is confidently wrong.
 */
public final class ExportAuditContext {

    /** Set by a generator; read once by the interceptor, which then clears it. */
    private static final ThreadLocal<Counted> CURRENT = new ThreadLocal<>();

    private ExportAuditContext() {}

    /**
     * @param rows       number of data rows written (headers excluded)
     * @param sheets     number of sheets/sections, or 1 for a single-table export
     * @param generator  which generator measured it, so the audit row says where the number is from
     */
    public record Counted(long rows, int sheets, String generator) {}

    /** Record what a generator just wrote. Last write wins; a nested generator call is the outer one's. */
    public static void record(long rows, int sheets, String generator) {
        CURRENT.set(new Counted(rows, sheets, generator));
    }

    /** @return what was recorded on this thread, or {@code null} if this export is uninstrumented. */
    public static Counted rowCount() {
        return CURRENT.get();
    }

    /** Must be called by the interceptor on every request, success or failure. See the class note. */
    public static void clear() {
        CURRENT.remove();
    }
}

package com.tss.grc.module.sust.annex.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-391 — the markdown→HTML table renderer dropped the last column of every
 * pipe-terminated row.
 *
 * <p>{@code markdownToHtml} splits each table row with {@code t.split("\\|")}
 * (default limit 0, which discards trailing empty strings), while the loop bound
 * {@code cells.length - (t.endsWith("|") ? 1 : 0)} subtracts one more for the
 * trailing {@code "|"} — a correction that only balances if the trailing empty
 * cell were retained. So for a pipe-terminated row {@code "| a | b | c |"} the
 * array was {@code ["", " a ", " b ", " c "]} (length 4), the bound became 3, and
 * the loop stopped at {@code c}'s predecessor — dropping the last data cell. A
 * single-cell row rendered no cells at all. The same method already uses the
 * correct {@code md.split("\n", -1)} idiom on its line split; this gate drives the
 * (package-private) static renderer directly, no Spring or DB.
 *
 * <p>The rendered HTML is persisted on the report via
 * {@code SustainabilityReportService.setContentHtml(content.html())}, so a dropped
 * column (e.g. the evidence-package "Status" column, the ESG-report last column)
 * lands in a stored/emitted report.
 */
class ReportComposerServiceTest {

    /** The discriminating leg: the LAST column of a pipe-terminated multi-column
     *  table must render, and the columns that already worked must still render. */
    @Test
    void pipeTerminatedTableRow_rendersItsLastColumn() {
        String md = "| Code | Title | Status |\n"
                  + "|---|---|---|\n"
                  + "| EVID-001 | Backup Policy | APPROVED |\n";

        String html = ReportComposerService.markdownToHtml(md);

        // Pre-fix, the last cell of each pipe-terminated row was dropped.
        assertThat(html).contains("<td>Status</td>");     // last header cell
        assertThat(html).contains("<td>APPROVED</td>");   // last data cell
        // Regression: the cells that already rendered must keep rendering.
        assertThat(html).contains("<th>Code</th>")
                        .contains("<td>Title</td>")
                        .contains("<td>EVID-001</td>")
                        .contains("<td>Backup Policy</td>");
        // The separator row is still skipped (no dashed cell leaks through).
        assertThat(html).doesNotContain("---");
    }

    /** The extreme case: a single-column pipe-terminated row must render its one
     *  cell (pre-fix the loop ran zero times and the row was empty). */
    @Test
    void singleColumnRow_rendersItsOnlyCell() {
        String md = "| OnlyCell |\n";

        String html = ReportComposerService.markdownToHtml(md);

        assertThat(html).contains("OnlyCell");
    }

    /** Guard (non-discriminating): a row WITHOUT a trailing pipe already rendered
     *  all its cells, and the fix is a no-op there — split(-1) adds no trailing
     *  empty when the string does not end in the delimiter. */
    @Test
    void nonPipeTerminatedRow_isUnaffected() {
        String md = "| x | y\n";

        String html = ReportComposerService.markdownToHtml(md);

        assertThat(html).contains("<th>x</th>").contains("<td>y</td>");
    }
}

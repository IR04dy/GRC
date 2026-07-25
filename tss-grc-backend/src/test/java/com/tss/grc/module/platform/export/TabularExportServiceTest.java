package com.tss.grc.module.platform.export;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Export P5 — sheet-name sanitisation for the tabular XLSX exporter. Excel names must be ≤31 chars,
 * exclude {@code []:*?/\}, and be unique in the workbook.
 */
class TabularExportServiceTest {

    @Test
    void uniqueSheetName_stripsIllegalChars() {
        String n = TabularExportService.uniqueSheetName("BIA: RTO/RPO*?", new HashSet<>());
        assertThat(n).doesNotContain(":", "/", "*", "?", "[", "]", "\\");
        assertThat(n).startsWith("BIA");
    }

    @Test
    void uniqueSheetName_truncatesTo31() {
        String n = TabularExportService.uniqueSheetName("X".repeat(40), new HashSet<>());
        assertThat(n.length()).isLessThanOrEqualTo(31);
    }

    @Test
    void uniqueSheetName_dedupesCaseInsensitively() {
        Set<String> used = new HashSet<>();
        assertThat(TabularExportService.uniqueSheetName("Emissions", used)).isEqualTo("Emissions");
        assertThat(TabularExportService.uniqueSheetName("emissions", used)).isEqualTo("emissions (2)");
        assertThat(TabularExportService.uniqueSheetName("Emissions", used)).isEqualTo("Emissions (3)");
    }

    @Test
    void uniqueSheetName_blankFallsBackToSheet() {
        assertThat(TabularExportService.uniqueSheetName("   ", new HashSet<>())).isEqualTo("Sheet");
        assertThat(TabularExportService.uniqueSheetName(null, new HashSet<>())).isEqualTo("Sheet");
    }
}

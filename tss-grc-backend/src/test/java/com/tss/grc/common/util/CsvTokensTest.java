package com.tss.grc.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * F2-369 — a denormalised count derived from {@code csv.split(",").length} reads 1 for an EMPTY
 * string, because {@code "".split(",")} is {@code [""]} (one empty token) rather than an empty
 * array — so a list of 0 ids counts as 1. {@link CsvTokens#countNonBlank(String)} counts the
 * non-blank tokens instead.
 *
 * <p>The two sites this closes are on regulator/governance-facing registers — {@code
 * NcaEccFiling.capaCount} (the NCA ECC self-assessment return) and {@code
 * AuditEngagement.findingsCount} (an IIA engagement) — and both are proven by a source scan: they
 * call the helper and the {@code .split(",").length} idiom is gone. Hermetic (no Spring, no DB).
 */
class CsvTokensTest {

    // ── the count logic (load-bearing) ─────────────────────────────────────────

    @Test
    void anEmptyOrBlankListCountsZero() {
        assertThat(CsvTokens.countNonBlank(null)).isZero();
        assertThat(CsvTokens.countNonBlank(""))
                .as("the F2-369 defect: \"\".split(\",\") has length 1, so this used to be 1")
                .isZero();
        assertThat(CsvTokens.countNonBlank("   ")).isZero();
        assertThat(CsvTokens.countNonBlank(",")).isZero();
        assertThat(CsvTokens.countNonBlank(" , ")).isZero();
    }

    @Test
    void itCountsNonBlankTokens() {
        assertThat(CsvTokens.countNonBlank("a")).isEqualTo(1);
        assertThat(CsvTokens.countNonBlank("a,b")).isEqualTo(2);
        assertThat(CsvTokens.countNonBlank("a,b,c")).isEqualTo(3);
        assertThat(CsvTokens.countNonBlank(" a , b ")).isEqualTo(2);
        assertThat(CsvTokens.countNonBlank("a,,b"))
                .as("interior blank tokens must not inflate the count").isEqualTo(2);
        assertThat(CsvTokens.countNonBlank("a,b,")).isEqualTo(2);
    }

    // ── the two sites use the helper, and the broken idiom is gone (F2-205 wiring / F2-216 removal) ──

    @Test
    void bothDenormalisedCountSitesUseTheHelperNotTheBrokenSplit() throws IOException {
        assertThat(read("nca_ecc/service/NcaEccFilingService.java"))
                .as("the NCA ECC filing CAPA count must count via the helper")
                .contains("setCapaCount(CsvTokens.countNonBlank(")
                .doesNotContain(".split(\",\").length");
        assertThat(read("audit_eng/service/AuditEngagementService.java"))
                .as("the IIA engagement finding count must count via the helper")
                .contains("setFindingsCount(CsvTokens.countNonBlank(")
                .doesNotContain(".split(\",\").length");
    }

    /** Read a module source with comments stripped (F2-193 — read code, not the prose that explains it). */
    private static String read(String moduleRelative) throws IOException {
        Path p = Path.of("src/main/java/com/tss/grc/module/" + moduleRelative);
        assertThat(Files.exists(p)).as("%s must be readable for this scan", p).isTrue();
        return Files.readString(p).replaceAll("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/", " ");
    }
}

package com.tss.grc.module.reporting.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Export P1 (§15) — filename convention: module, type, LANG, date, version, safe chars. */
class ExportNamingTest {

    @Test
    void includesModuleTypeLangDateVersionAndExt() {
        String date = LocalDate.now().toString().replace("-", "");
        assertThat(ExportNaming.build("ERM", "RiskRegister", "ar", "pdf"))
            .isEqualTo("ERM_RiskRegister_AR_" + date + "_v1.pdf");
        assertThat(ExportNaming.build("ERM", "RiskRegister", "en", "pdf"))
            .isEqualTo("ERM_RiskRegister_EN_" + date + "_v1.pdf");
    }

    @Test
    void nonArabicLangDefaultsToEN_andVersionHonoured() {
        String date = LocalDate.now().toString().replace("-", "");
        assertThat(ExportNaming.build("BoardPack", "BoardPack", null, 3, "pptx"))
            .isEqualTo("BoardPack_BoardPack_EN_" + date + "_v3.pptx");
    }

    @Test
    void sanitizesUnsafeCharactersInModuleTypeAndExt() {
        String date = LocalDate.now().toString().replace("-", "");
        assertThat(ExportNaming.build("Cyber / SOC", "Evidence Pack!", "AR", ".XLSX"))
            .isEqualTo("Cyber-SOC_Evidence-Pack_AR_" + date + "_v1.xlsx");
    }
}

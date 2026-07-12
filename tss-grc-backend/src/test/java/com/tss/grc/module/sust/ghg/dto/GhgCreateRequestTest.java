package com.tss.grc.module.sust.ghg.dto;

import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-L — locks the create-DTO contract: {@code toEntity()} maps the business
 * fields and NEVER carries workflow / verification / value-source state (those
 * stay at the entity default — closing mass-assignment). Representative of the
 * pattern used by all nine Stack-A create DTOs.
 */
class GhgCreateRequestTest {

    @Test
    void toEntity_mapsBusinessFields_andLeavesWorkflowAtDefault() {
        GhgCreateRequest r = new GhgCreateRequest(
                (short) 2025, (short) 3, "Business travel", (short) 6, "Boiler A", "غلاية", "Ops", "Riyadh HQ",
                new BigDecimal("1000"), "L", new BigDecimal("2.5"), "kgCO2e/L", 7L,
                null, new BigDecimal("80"), "Bureau Veritas", "GHG Protocol", "notes",
                "ref.pdf", "Owner", "owner@x.com");

        GhgEmissionRecord e = r.toEntity();

        assertThat(e.getId()).isNull();                       // never client-set
        assertThat(e.getReportingYear()).isEqualTo((short) 2025);
        assertThat(e.getScope3Category()).isEqualTo((short) 6);
        assertThat(e.getSourceNameEn()).isEqualTo("Boiler A");
        assertThat(e.getEmissionFactorId()).isEqualTo(7L);
        assertThat(e.getCoveragePercent()).isEqualByComparingTo("80");
        // workflow / verification / value-source are NOT bindable — entity defaults hold
        assertThat(e.getStatus()).isEqualTo("DRAFT");
        assertThat(e.getVerified()).isFalse();
        assertThat(e.getValueSource()).isEqualTo("ASSERTED");
    }
}

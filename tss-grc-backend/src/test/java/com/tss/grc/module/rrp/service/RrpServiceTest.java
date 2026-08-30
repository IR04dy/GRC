package com.tss.grc.module.rrp.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.rrp.dto.RrpDtos.CreateIndicatorRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F2-396 — an RRP recovery indicator is a 3-band RAG whose GREEN/AMBER thresholds must be
 * strictly ordered per direction (HIGHER_IS_BETTER needs green &gt; amber; LOWER_IS_BETTER
 * needs green &lt; amber) for {@code computeBand} to produce correct bands. No write path
 * enforced that ordering, so an inverted pair silently mis-banded — a breaching SAMA recovery
 * indicator read GREEN and the {@code RRP_INDICATOR_THRESHOLD_BREACHED} escalation never fired.
 * {@code validateThresholdOrder} now guards the sole threshold writer, {@code addIndicator},
 * at its first line (mirroring VulnerabilitySlaPolicyService.validatePctOrder).
 *
 * <p>Hermetic: the rejection happens before any repository/tenant interaction, so the service
 * is built with null collaborators — the throw fires before any is used ([[F2-320]] / [[F2-325]]).
 */
class RrpServiceTest {

    private RrpService service;

    @BeforeEach void setUp() {
        service = new RrpService(null, null, null, null, null, null, null);
        TenantContext.setCurrentTenant(1L);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static CreateIndicatorRequest indicator(String direction, String green, String amber) {
        return CreateIndicatorRequest.builder()
                .direction(direction)
                .greenThreshold(new BigDecimal(green))
                .amberThreshold(new BigDecimal(amber))
                .build();
    }

    /** Discriminating: a HIGHER_IS_BETTER indicator with green &lt; amber (inverted) is rejected
     *  at addIndicator's first line. Pre-fix (guard call removed) the pair reaches the persistence
     *  path instead, so no IllegalArgumentException is thrown and this leg goes RED. */
    @Test void addIndicator_rejectsInvertedHigherIsBetterThresholds() {
        assertThatThrownBy(() -> service.addIndicator(1L, indicator("HIGHER_IS_BETTER", "11.0", "13.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greenThreshold")
                .hasMessageContaining("greater than");
    }

    /** Discriminating: a LOWER_IS_BETTER indicator with green &gt; amber (inverted) is rejected. */
    @Test void addIndicator_rejectsInvertedLowerIsBetterThresholds() {
        assertThatThrownBy(() -> service.addIndicator(1L, indicator("LOWER_IS_BETTER", "5.0", "3.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greenThreshold")
                .hasMessageContaining("less than");
    }

    /** Guard (non-discriminating): correctly-ordered thresholds (per the RrpIndicator worked
     *  examples — CET1 green 13 &gt; amber 11; NPL green 3 &lt; amber 5) and any null threshold pass. */
    @Test void validateThresholdOrder_acceptsCorrectlyOrderedAndNullThresholds() {
        assertThatCode(() -> {
            RrpService.validateThresholdOrder("HIGHER_IS_BETTER", new BigDecimal("13.0"), new BigDecimal("11.0"));
            RrpService.validateThresholdOrder("LOWER_IS_BETTER", new BigDecimal("3.0"), new BigDecimal("5.0"));
            RrpService.validateThresholdOrder("HIGHER_IS_BETTER", null, new BigDecimal("11.0"));
            RrpService.validateThresholdOrder("LOWER_IS_BETTER", new BigDecimal("3.0"), null);
        }).doesNotThrowAnyException();
    }
}

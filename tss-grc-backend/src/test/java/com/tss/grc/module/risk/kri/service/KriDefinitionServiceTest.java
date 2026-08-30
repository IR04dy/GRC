package com.tss.grc.module.risk.kri.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriDefinitionRequest;
import com.tss.grc.module.risk.kri.entity.KriDefinition;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.kri.repository.KriMeasurementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * F2-397 — a KRI's GREEN/AMBER thresholds must be strictly ordered per direction
 * (HIGHER_IS_BETTER: green &gt; amber; LOWER_IS_BETTER: green &lt; amber) for
 * {@link KriMeasurementService#evaluateStatus} to produce correct statuses. Neither create nor
 * update (both via {@code applyFields} + {@code validateEnums}) enforced it, so an inverted pair
 * silently mis-banded — a breaching KRI evaluated GREEN, opening no breach and never escalating.
 * {@code validateThresholdOrder} now guards the end of {@code applyFields}, validating the MERGED
 * entity (a partial update may set only one threshold — F2-292).
 *
 * <p>Hermetic: the rejection fires inside {@code applyFields}, before the {@code defRepo.save}, so
 * the collaborators are only stubbed where the loaded row is needed ([[F2-320]] / [[F2-325]]).
 */
@ExtendWith(MockitoExtension.class)
class KriDefinitionServiceTest {

    @Mock KriDefinitionRepository defRepo;
    @Mock KriMeasurementRepository measRepo;
    @Mock AuditLogService audit;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks KriDefinitionService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(1L); }

    @AfterEach void tearDown() { TenantContext.clear(); }

    /** Build a request that skips generateCode (kriCode provided) and skips validateEnums
     *  (category/unit/frequency null); only direction + the two thresholds matter. */
    private static KriDefinitionRequest req(String direction, String green, String amber) {
        return new KriDefinitionRequest(
                "KRI-T-001", null, null, null, null, null, null,
                direction,
                null,
                green == null ? null : new BigDecimal(green),
                amber == null ? null : new BigDecimal(amber),
                null, null, null, null, null, null, null, null, null);
    }

    /** Discriminating: create with a HIGHER_IS_BETTER inverted pair (green 11 &lt; amber 13) is
     *  rejected in applyFields. Pre-fix (guard call removed) the pair reaches {@code defRepo.save}. */
    @Test void create_rejectsInvertedHigherIsBetterThresholds() {
        assertThatThrownBy(() -> service.create(req("HIGHER_IS_BETTER", "11.0", "13.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greenThreshold")
                .hasMessageContaining("greater than");
    }

    /** Discriminating: create with a LOWER_IS_BETTER inverted pair (green 5 &gt; amber 3) is rejected. */
    @Test void create_rejectsInvertedLowerIsBetterThresholds() {
        assertThatThrownBy(() -> service.create(req("LOWER_IS_BETTER", "5.0", "3.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greenThreshold")
                .hasMessageContaining("less than");
    }

    /** Discriminating (F2-292 — validate the MERGED row): a partial update that sets ONLY green (5)
     *  inverts it against the stored amber (3) on a LOWER_IS_BETTER KRI, and is rejected — proving
     *  the guard reads the merged entity, not the request (whose amber is null). */
    @Test void update_rejectsPartialUpdateThatInvertsAgainstTheStoredThreshold() {
        KriDefinition stored = new KriDefinition();
        stored.setValueDirection("LOWER_IS_BETTER");
        stored.setGreenThreshold(new BigDecimal("2"));   // valid stored pair: 2 < 3
        stored.setAmberThreshold(new BigDecimal("3"));
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(anyLong(), any()))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.update(UUID.randomUUID(), req(null, "5.0", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("less than");
    }

    /** Guard (non-discriminating): correctly-ordered thresholds (HIGHER: green 13 &gt; amber 11;
     *  LOWER: green 3 &lt; amber 5) and any null threshold pass. */
    @Test void validateThresholdOrder_acceptsCorrectlyOrderedAndNullThresholds() {
        assertThatCode(() -> {
            KriDefinitionService.validateThresholdOrder("HIGHER_IS_BETTER", new BigDecimal("13.0"), new BigDecimal("11.0"));
            KriDefinitionService.validateThresholdOrder("LOWER_IS_BETTER", new BigDecimal("3.0"), new BigDecimal("5.0"));
            KriDefinitionService.validateThresholdOrder("LOWER_IS_BETTER", null, new BigDecimal("5.0"));
            KriDefinitionService.validateThresholdOrder("HIGHER_IS_BETTER", new BigDecimal("13.0"), null);
        }).doesNotThrowAnyException();
    }
}

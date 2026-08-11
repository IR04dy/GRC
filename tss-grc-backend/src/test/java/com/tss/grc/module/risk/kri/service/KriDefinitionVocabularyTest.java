package com.tss.grc.module.risk.kri.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriDefinitionRequest;
import com.tss.grc.module.risk.kri.entity.KriDefinition;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.kri.repository.KriMeasurementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-182 — the KRI definition writer, which had <b>no test class at all</b>.
 *
 * <p>⚙ <b>How this class came to exist is the point.</b> The mutation that removed the normalisation
 * from {@code KriDefinitionService.applyFields} <b>survived</b> — not because a test was weak, but
 * because {@code KriDefinitionService} had never been tested directly. The KRI module had
 * {@code KriMeasurementServiceTest} and {@code KriBreachServiceTest}, and the register half was
 * covered only incidentally through them. A mutation harness reports that as a survivor rather than as
 * "no coverage", which is exactly what makes it useful: <b>the gap was invisible to a green suite and
 * to a coverage number that nobody was reading per-class.</b>
 *
 * <p>What it locks: {@code value_direction} and {@code source_query_type} are normalised on write on
 * <em>both</em> the create and the update path — the two the finding's scan mis-classified — so the
 * inverting read in {@code KriMeasurementService.evaluateStatus} and the MANUAL skip in
 * {@code evaluateAll} can never see a casing the column would have accepted.
 */
@ExtendWith(MockitoExtension.class)
class KriDefinitionVocabularyTest {

    private static final Long ORG_ID = 1L;
    private static final UUID KRI_EX = UUID.randomUUID();

    @Mock private KriDefinitionRepository defRepo;
    @Mock private KriMeasurementRepository measRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    private KriDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new KriDefinitionService(defRepo, measRepo, audit, eventPublisher);
        TenantContext.setCurrentTenant(ORG_ID);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    /** A request whose direction / source casing is the variable under test. */
    private static KriDefinitionRequest req(String direction, String sourceType) {
        return new KriDefinitionRequest(
            "KRI-LIQUIDITY-LCR-002",                 // kriCode
            "Liquidity coverage ratio",               // titleEn
            "نسبة تغطية السيولة",                      // titleAr — bilingual rule, backend-mandatory
            null, null,                               // descriptionEn / Ar
            "LIQUIDITY",                              // category
            "PERCENTAGE",                             // measurementUnit
            direction,                                // valueDirection  ← under test
            new BigDecimal("140"),                    // targetValue
            new BigDecimal("130"),                    // greenThreshold
            new BigDecimal("120"),                    // amberThreshold
            new BigDecimal("110"),                    // redThreshold
            "MONTHLY",                                // frequency
            sourceType,                               // sourceQueryType ← under test
            null,                                     // sourceQuery
            1L, null, null, null,                     // owner / linked risk / framework / clause
            Boolean.TRUE);                            // isActive
    }

    private static KriDefinition existing() {
        KriDefinition d = new KriDefinition();
        d.setId(42L);
        d.setExternalId(KRI_EX);
        d.setOrganisationId(ORG_ID);
        d.setKriCode("KRI-LIQUIDITY-LCR-002");
        d.setValueDirection("LOWER_IS_BETTER");
        d.setSourceQueryType("SQL");
        return d;
    }

    @Test
    @DisplayName("create normalises the direction, so the evaluator can never read an inverting value")
    void createNormalisesTheDirection() {
        // no countByOrganisationIdAndKriCode stub: the request supplies a code, so generateCode()
        // is never reached — and Mockito's strict stubbing is right to say so
        when(defRepo.save(any(KriDefinition.class))).thenAnswer(inv -> {
            KriDefinition d = inv.getArgument(0);
            d.setId(42L);
            return d;
        });
        when(measRepo.findFirstByOrganisationIdAndKriIdAndIsDeletedFalseOrderByMeasuredAtDesc(
                ORG_ID, 42L)).thenReturn(Optional.empty());

        service.create(req("higher_is_better", "manual"));

        ArgumentCaptor<KriDefinition> captor = ArgumentCaptor.forClass(KriDefinition.class);
        verify(defRepo).save(captor.capture());
        assertThat(captor.getValue().getValueDirection()).isEqualTo("HIGHER_IS_BETTER");
        assertThat(captor.getValue().getSourceQueryType()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("update normalises them too — the path the finding's scan called open")
    void updateNormalisesTheDirection() {
        KriDefinition d = existing();
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, KRI_EX))
                .thenReturn(Optional.of(d));
        when(defRepo.save(any(KriDefinition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(measRepo.findFirstByOrganisationIdAndKriIdAndIsDeletedFalseOrderByMeasuredAtDesc(
                ORG_ID, 42L)).thenReturn(Optional.empty());

        service.update(KRI_EX, req("  Higher_Is_Better  ", "Metric_Api"));

        assertThat(d.getValueDirection()).as("mixed case and surrounding whitespace both handled")
            .isEqualTo("HIGHER_IS_BETTER");
        assertThat(d.getSourceQueryType()).isEqualTo("METRIC_API");
    }

    @Test
    @DisplayName("a direction the column could never hold is refused, naming the value")
    void anImpossibleDirectionIsRefused() {
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, KRI_EX))
                .thenReturn(Optional.of(existing()));

        assertThatThrownBy(() -> service.update(KRI_EX, req("BIGGER_IS_NICER", "MANUAL")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("value_direction")
            .hasMessageContaining("BIGGER_IS_NICER");

        verify(defRepo, never()).save(any());
    }

    @Test
    @DisplayName("an impossible source type is refused too")
    void anImpossibleSourceTypeIsRefused() {
        when(defRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, KRI_EX))
                .thenReturn(Optional.of(existing()));

        assertThatThrownBy(() -> service.update(KRI_EX, req("LOWER_IS_BETTER", "SPREADSHEET")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("source_query_type");

        verify(defRepo, never()).save(any());
    }

    @Test
    @DisplayName("the other enum checks that stayed in validateEnums still fire")
    void theRemainingValidatedEnumsAreUntouched() {
        // Negative control on the refactor: value_direction and source_query_type moved out of
        // validateEnums into applyFields, and category / unit / frequency deliberately did not.
        // If the move had taken them along, a nonsense category would now reach the column.
        KriDefinitionRequest bad = new KriDefinitionRequest(
            "KRI-X", "Telepathic incidents", "حوادث التخاطر", null, null,
            "TELEPATHY",                              // category — not in CATEGORIES
            "PERCENTAGE", "LOWER_IS_BETTER",
            null, null, null, null, "MONTHLY", "MANUAL", null,
            1L, null, null, null, Boolean.TRUE);

        assertThatThrownBy(() -> service.create(bad))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("category");
        verify(defRepo, never()).save(any());
    }
}

package com.tss.grc.module.sust.materiality.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.materiality.entity.MaterialTopic;
import com.tss.grc.module.sust.materiality.repository.MaterialTopicRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * MaterialTopicService — locks the S-Q double-materiality behaviour:
 * <ul>
 *   <li>the score is MAX(impact, financial), never the old average — a topic is
 *       as material as its stronger perspective;</li>
 *   <li>scoring writes both axes (and mirrors the legacy columns);</li>
 *   <li>out-of-range / null axes are rejected;</li>
 *   <li>the matrix classifies each topic as material-from-impact,
 *       material-from-financial, and/or double-material, with quadrant counts.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MaterialTopicServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private MaterialTopicRepository repo;
    @Mock private AuditLogService audit;
    @InjectMocks private MaterialTopicService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    @Test
    void score_isMaxOfBothAxes_notAverage() {
        MaterialTopic t = topic(3L, "DRAFT");
        when(repo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(t));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // impact 4.7, financial 2.0 → the OLD average was 3.35 (HIGH); double
        // materiality takes the MAX = 4.7 (CRITICAL).
        MaterialTopic out = service.score(3L, new BigDecimal("4.7"), new BigDecimal("2.0"));

        assertThat(out.getImpactMateriality()).isEqualByComparingTo("4.7");
        assertThat(out.getFinancialMateriality()).isEqualByComparingTo("2.0");
        assertThat(out.getMaterialityScore()).isEqualByComparingTo("4.7");   // max, not 3.35
        assertThat(out.getPriorityTier()).isEqualTo("CRITICAL");
        assertThat(out.getStatus()).isEqualTo("SCORED");
        // legacy columns mirrored
        assertThat(out.getStakeholderImportance()).isEqualByComparingTo("4.7");
        assertThat(out.getBusinessImpact()).isEqualByComparingTo("2.0");
    }

    @Test
    void score_materialFromFinancialAxisAlone_stillCritical() {
        MaterialTopic t = topic(4L, "DRAFT");
        when(repo.findByIdAndOrganisationId(4L, ORG_ID)).thenReturn(Optional.of(t));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MaterialTopic out = service.score(4L, new BigDecimal("1.0"), new BigDecimal("4.2"));
        assertThat(out.getMaterialityScore()).isEqualByComparingTo("4.2");
        assertThat(out.getPriorityTier()).isEqualTo("CRITICAL");
    }

    @Test
    void score_rejectsOutOfRangeAndNull() {
        MaterialTopic t = topic(5L, "DRAFT");
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(t));
        assertThatThrownBy(() -> service.score(5L, new BigDecimal("6"), new BigDecimal("3")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.score(5L, null, new BigDecimal("3")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_recomputesScoreFromAxes() {
        MaterialTopic req = topic(null, "DRAFT");
        req.setImpactMateriality(new BigDecimal("3.5"));
        req.setFinancialMateriality(new BigDecimal("1.0"));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MaterialTopic out = service.create(req);
        assertThat(out.getMaterialityScore()).isEqualByComparingTo("3.5");
        assertThat(out.getPriorityTier()).isEqualTo("HIGH");
        assertThat(out.getStatus()).isEqualTo("DRAFT");   // create never scores past DRAFT
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateMatrix_classifiesEachQuadrant() {
        MaterialTopic dbl  = scored(11L, "4.0", "4.0"); // double-material
        MaterialTopic imp  = scored(12L, "4.0", "1.0"); // impact only
        MaterialTopic fin  = scored(13L, "1.0", "4.0"); // financial only
        MaterialTopic none = scored(14L, "1.0", "1.0"); // neither
        when(repo.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2025))
            .thenReturn(List.of(dbl, imp, fin, none));

        Map<String, Object> m = service.generateMatrix((short) 2025);

        assertThat(m.get("impactMaterialCount")).isEqualTo(2);      // dbl + imp
        assertThat(m.get("financialMaterialCount")).isEqualTo(2);   // dbl + fin
        assertThat(m.get("doubleMaterialCount")).isEqualTo(1);      // dbl
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) m.get("matrix");
        assertThat(matrix).hasSize(4);
        Map<String, Object> dblEntry = matrix.stream()
            .filter(e -> Long.valueOf(11L).equals(e.get("id"))).findFirst().orElseThrow();
        assertThat(dblEntry.get("materialFromImpact")).isEqualTo(true);
        assertThat(dblEntry.get("materialFromFinancial")).isEqualTo(true);
        assertThat(dblEntry.get("doubleMaterial")).isEqualTo(true);
        assertThat(dblEntry).containsKeys("impactMateriality", "financialMateriality");
    }

    // ── helpers ──────────────────────────────────────────────────────
    private static MaterialTopic topic(Long id, String status) {
        MaterialTopic t = new MaterialTopic();
        t.setId(id);
        t.setOrganisationId(ORG_ID);
        t.setReportingYear((short) 2025);
        t.setCategory("ENVIRONMENTAL");
        t.setTopicEn("Climate change");
        t.setStatus(status);
        return t;
    }

    private static MaterialTopic scored(Long id, String impact, String financial) {
        MaterialTopic t = topic(id, "SCORED");
        t.setImpactMateriality(new BigDecimal(impact));
        t.setFinancialMateriality(new BigDecimal(financial));
        t.setPriorityTier("HIGH");
        return t;
    }
}

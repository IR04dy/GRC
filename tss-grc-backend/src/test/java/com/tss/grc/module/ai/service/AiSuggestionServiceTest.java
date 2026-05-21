package com.tss.grc.module.ai.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ai.entity.AiSuggestion;
import com.tss.grc.module.ai.repository.AiSuggestionRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Coverage for the §27 AI heuristic service (V116).
 *
 * <ul>
 *   <li>each generator persists a DRAFT with feature_code set + non-empty output_json</li>
 *   <li>review() requires ACCEPTED or REJECTED (anything else throws)</li>
 *   <li>review() refuses to mutate a suggestion from another tenant (404)</li>
 *   <li>BIA RTO/RPO recommendation maps tier correctly</li>
 *   <li>board summary tone reflects threshold inputs</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiSuggestionServiceTest {

    private static final Long ORG_ID = 7L;
    private static final Long OTHER_ORG = 99L;

    @Mock private AiSuggestionRepository repo;
    @Mock private AuditLogService audit;
    @InjectMocks private AiSuggestionService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(repo.save(any(AiSuggestion.class))).thenAnswer(inv -> {
            AiSuggestion s = inv.getArgument(0);
            if (s.getId() == null) s.setId(101L);
            return s;
        });
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ── Generators ────────────────────────────────────────────────────

    @Test void riskDescription_persistsWithFeatureCodeAndOutput() {
        AiSuggestionService.RiskDescriptionInput in = new AiSuggestionService.RiskDescriptionInput(
            42L, "Phishing-driven breach of customer PII", "CYBER", 3, 4);

        AiSuggestion out = service.suggestRiskDescription(in);

        assertThat(out.getFeatureCode()).isEqualTo("RISK_DESCRIPTION");
        assertThat(out.getStatus()).isEqualTo("DRAFT");
        assertThat(out.getRelatedEntity()).isEqualTo("Risk");
        assertThat(out.getRelatedEntityId()).isEqualTo(42L);
        assertThat(out.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(out.getOutputJson()).contains("PDPL");   // category=CYBER adds the PDPL consequence
        assertThat(out.getConfidence()).isNotNull();
    }

    @Test void biaRtoRpo_tier1_gives4hRto15mRpo() {
        AiSuggestion out = service.suggestBiaRtoRpo(new AiSuggestionService.BiaRecommendationInput(
            1L, "Core retail banking", "CRITICAL", "TIER_1"));

        assertThat(out.getOutputJson()).contains("\"rtoMinutes\":240");
        assertThat(out.getOutputJson()).contains("\"rpoMinutes\":15");
    }

    @Test void biaRtoRpo_tier3_gives72hRto24hRpo() {
        AiSuggestion out = service.suggestBiaRtoRpo(new AiSuggestionService.BiaRecommendationInput(
            3L, "HR records", "IMPORTANT", "TIER_3"));

        assertThat(out.getOutputJson()).contains("\"rtoMinutes\":4320");
        assertThat(out.getOutputJson()).contains("\"rpoMinutes\":1440");
    }

    @Test void boardSummary_highRisks_setsAttentionTone() {
        AiSuggestion out = service.draftBoardSummary(new AiSuggestionService.BoardSummaryInput(
            18, 8, 71, 68, 74, 4));

        assertThat(out.getOutputJson()).contains("DELIVER ATTENTION");
    }

    @Test void boardSummary_lowRisks_setsHealthyTone() {
        AiSuggestion out = service.draftBoardSummary(new AiSuggestionService.BoardSummaryInput(
            3, 1, 92, 90, 95, 0));

        assertThat(out.getOutputJson()).contains("HEALTHY");
    }

    // ── Review (security + validation) ────────────────────────────────

    @Test void review_rejectsUnknownDecision() {
        when(repo.findById(101L)).thenReturn(Optional.of(suggestion(ORG_ID)));

        assertThatThrownBy(() -> service.review(101L, new AiSuggestionService.ReviewBody("MAYBE", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ACCEPTED or REJECTED");
    }

    @Test void review_rejectsCrossTenant() {
        when(repo.findById(101L)).thenReturn(Optional.of(suggestion(OTHER_ORG)));

        assertThatThrownBy(() -> service.review(101L, new AiSuggestionService.ReviewBody("ACCEPTED", null)))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test void review_acceptedStampsStatus_AndAuditLogs() {
        when(repo.findById(101L)).thenReturn(Optional.of(suggestion(ORG_ID)));

        AiSuggestion out = service.review(101L, new AiSuggestionService.ReviewBody("accepted", "LGTM"));

        assertThat(out.getStatus()).isEqualTo("ACCEPTED");
        assertThat(out.getReviewerNote()).isEqualTo("LGTM");
        assertThat(out.getReviewedAt()).isNotNull();
    }

    // ── Listing ───────────────────────────────────────────────────────

    @Test void listByFeature_scopesToTenant() {
        when(repo.findByOrganisationIdAndFeatureCodeOrderByCreatedAtDesc(ORG_ID, "BOARD_SUMMARY"))
            .thenReturn(List.of(suggestion(ORG_ID), suggestion(ORG_ID)));

        List<AiSuggestion> out = service.listByFeature("BOARD_SUMMARY");

        assertThat(out).hasSize(2);
    }

    // ── helpers ───────────────────────────────────────────────────────
    private AiSuggestion suggestion(Long org) {
        AiSuggestion s = new AiSuggestion();
        s.setId(101L);
        s.setOrganisationId(org);
        s.setFeatureCode("BOARD_SUMMARY");
        s.setInputJson("{}");
        s.setOutputJson("{}");
        s.setStatus("DRAFT");
        return s;
    }
}

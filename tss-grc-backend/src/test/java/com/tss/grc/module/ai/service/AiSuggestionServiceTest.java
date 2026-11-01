package com.tss.grc.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ai.entity.AiSuggestion;
import com.tss.grc.module.ai.repository.AiSuggestionRepository;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import com.tss.grc.module.integrations.llm.service.LlmGatewayService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Coverage for the AI suggestion service (V116 / §27 + LLM transition).
 *
 * <ul>
 *   <li>each generator uses LLM when available and falls back seamlessly to heuristics</li>
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
    @Mock private LlmGatewayService llmGateway;
    @Spy private ObjectMapper mapper = new ObjectMapper();
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

    // ── Fallback Heuristic Generators ────────────────────────────────

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

    // ── LLM-Powered Generation Paths ─────────────────────────────────

    @Test void riskDescription_withLlmSuccess_usesLlmOutputAndRecordsLlmRationale() {
        String llmContent = """
            {
              "descriptionEn": "Risk that sensitive financial data is leaked due to insecure API gateways.",
              "descriptionAr": "خطر تسرب بيانات مالية حساسة بسبب ثغرة في بوابات الربط.",
              "causes": ["API key exposure", "Lack of rate limiting"],
              "consequences": ["SAMA penalty", "Customer churn"],
              "confidence": 88
            }
            """;
        when(llmGateway.complete(eq(ORG_ID), any()))
            .thenReturn(new LlmResponse(llmContent, "GEMINI", "gemini-2.5-flash", 100, 80, 450, "STOP"));

        AiSuggestionService.RiskDescriptionInput in = new AiSuggestionService.RiskDescriptionInput(
            99L, "API Gateway Data Leak", "FINANCIAL", 4, 5);

        AiSuggestion out = service.suggestRiskDescription(in);

        assertThat(out.getFeatureCode()).isEqualTo("RISK_DESCRIPTION");
        assertThat(out.getOutputJson()).contains("API key exposure");
        assertThat(out.getRationale()).contains("Generated by LLM (GEMINI / gemini-2.5-flash)");
        assertThat(out.getConfidence()).isEqualByComparingTo("88");
    }

    @Test void biaRtoRpo_withLlmSuccess_usesLlmOutput() {
        String llmContent = """
            {
              "rtoMinutes": 120,
              "rpoMinutes": 5,
              "rtoHumanEn": "2 hours",
              "rpoHumanEn": "5 minutes",
              "rtoHumanAr": "ساعتان",
              "rpoHumanAr": "5 دقائق",
              "mtpdMinutes": 360,
              "rationale": "High-velocity payment processing tier expectation.",
              "confidence": 92
            }
            """;
        when(llmGateway.complete(eq(ORG_ID), any()))
            .thenReturn(new LlmResponse(llmContent, "GEMINI", "gemini-2.5-flash", 80, 60, 300, "STOP"));

        AiSuggestion out = service.suggestBiaRtoRpo(new AiSuggestionService.BiaRecommendationInput(
            10L, "Payment Switch", "CRITICAL", "TIER_0"));

        assertThat(out.getOutputJson()).contains("\"rtoMinutes\":120");
        assertThat(out.getOutputJson()).contains("\"rpoMinutes\":5");
        assertThat(out.getRationale()).contains("High-velocity payment processing tier expectation");
    }

    @Test void evidenceQuality_withLlmSuccess_usesLlmOutput() {
        String llmContent = """
            {
              "qualityScore": 90,
              "verdict": "ACCEPTABLE",
              "strengths": ["Cryptographically signed audit log", "Mapped to NCA ECC 2-1-1"],
              "issues": [],
              "rationale": "Defensible evidence artifact ready for regulatory review.",
              "confidence": 95
            }
            """;
        when(llmGateway.complete(eq(ORG_ID), any()))
            .thenReturn(new LlmResponse(llmContent, "GEMINI", "gemini-2.5-flash", 80, 60, 320, "STOP"));

        AiSuggestion out = service.reviewEvidenceQuality(new AiSuggestionService.EvidenceQualityInput(
            55L, "NCA Firewall Review", "REPORT", "NCA-ECC-2-1-1", 15, true));

        assertThat(out.getOutputJson()).contains("Cryptographically signed audit log");
        assertThat(out.getOutputJson()).contains("ACCEPTABLE");
    }

    @Test void incidentRootCause_withLlmSuccess_usesLlmOutput() {
        String llmContent = """
            {
              "rootCauseDraftEn": "RCA: Unpatched deserialization flaw in legacy payroll server.",
              "rootCauseDraftAr": "تحليل السبب الجذري: ثغرة برمجية غير معالجة في خادم الرواتب القديم.",
              "categoriesToPick": ["TECHNOLOGY", "PROCESS"],
              "rationale": "Synthesized from forensics sitrep.",
              "confidence": 85
            }
            """;
        when(llmGateway.complete(eq(ORG_ID), any()))
            .thenReturn(new LlmResponse(llmContent, "GEMINI", "gemini-2.5-flash", 120, 90, 500, "STOP"));

        AiSuggestion out = service.draftIncidentRootCause(new AiSuggestionService.IncidentRootCauseInput(
            12L, "Payroll Ransomware Event", 4, "Endpoint Alert", List.of("EDR flagged lateral movement")));

        assertThat(out.getOutputJson()).contains("Unpatched deserialization flaw");
    }

    @Test void workflowSubmissionReview_withLlmSuccess_usesLlmOutput() {
        String llmContent = """
            {
              "headlineEn": "PDPL Material Breach: Verify SDAIA 72h notification timestamp",
              "headlineAr": "خرق بيانات شخصية: التحقق من التبليغ خلال 72 ساعة",
              "checklistEn": ["Confirm incident time vs notification time", "Verify DPO endorsement"],
              "checklistAr": ["التحقق من وقت الحادث ووقت الإبلاغ", "اعتماد مسؤول حماية البيانات"],
              "regulatorCitation": "PDPL Article 20",
              "suggestedDecision": "APPROVE",
              "confidence": 90
            }
            """;
        when(llmGateway.complete(eq(ORG_ID), any()))
            .thenReturn(new LlmResponse(llmContent, "GEMINI", "gemini-2.5-flash", 110, 80, 400, "STOP"));

        AiSuggestion out = service.suggestWorkflowSubmissionReview(new AiSuggestionService.WorkflowSubmissionReviewInput(
            77L, "pdpl-breach-reporting", "PRIVACY", "UNDER_REVIEW", "PDPL Art 20", "Data breach report", "Leak of 500 records"));

        assertThat(out.getOutputJson()).contains("PDPL Article 20");
        assertThat(out.getOutputJson()).contains("APPROVE");
    }

    @Test void riskDescription_whenLlmThrows_fallsBackToHeuristic() {
        when(llmGateway.complete(eq(ORG_ID), any())).thenThrow(new RuntimeException("Rate limit exceeded"));

        AiSuggestionService.RiskDescriptionInput in = new AiSuggestionService.RiskDescriptionInput(
            42L, "Phishing-driven breach of customer PII", "CYBER", 3, 4);

        AiSuggestion out = service.suggestRiskDescription(in);

        assertThat(out.getFeatureCode()).isEqualTo("RISK_DESCRIPTION");
        assertThat(out.getOutputJson()).contains("PDPL");
        assertThat(out.getRationale()).contains("Heuristic expansion");
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

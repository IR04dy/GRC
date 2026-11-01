package com.tss.grc.module.integrations.llm.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.integrations.llm.config.LlmProperties;
import com.tss.grc.module.integrations.llm.dto.LlmGatewayException;
import com.tss.grc.module.integrations.llm.dto.LlmRequest;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic, stateless offline {@link LlmClient} active when
 * {@code grc.integrations.llm.mode=STUB} (the default).
 *
 * <p>Emits schema-valid bilingual (English + Arabic) JSON grounded in the
 * control IDs supplied on {@link LlmRequest#groundingControlIds()} so unit,
 * contract, and E2E workflows run hermetically without network access or secrets.
 */
@Component
@ConditionalOnProperty(name = "grc.integrations.llm.mode", havingValue = "STUB", matchIfMissing = true)
public class StubLlmClient implements LlmClient {

    private final LlmProperties props;
    private final ObjectMapper objectMapper;

    public StubLlmClient(LlmProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        long start = System.currentTimeMillis();
        String feature = request.featureCode() != null ? request.featureCode().toUpperCase(Locale.ROOT) : "";
        Map<String, Object> payload = switch (feature) {
            case "RISK_DESCRIPTION" -> stubRiskDescription(request);
            case "BIA_RTO_RPO" -> stubBiaRtoRpo(request);
            case "EVIDENCE_QUALITY" -> stubEvidenceQuality(request);
            case "INCIDENT_ROOT_CAUSE" -> stubIncidentRootCause(request);
            case "BOARD_SUMMARY" -> stubBoardSummary(request);
            case "WORKFLOW_SUBMISSION_REVIEW" -> stubWorkflowSubmissionReview(request);
            default -> stubPolicyDraft(request);
        };

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new LlmGatewayException("STUB_SERIALIZATION_ERROR",
                "Failed to serialize stub LLM response", ex);
        }

        int inTok = estimateTokens(request.systemPrompt()) + estimateTokens(request.userPrompt());
        int outTok = estimateTokens(json);
        long latency = Math.max(1L, System.currentTimeMillis() - start);
        return new LlmResponse(
            json,
            "STUB",
            props.getModel(),
            inTok,
            outTok,
            latency,
            "STOP"
        );
    }

    private Map<String, Object> stubPolicyDraft(LlmRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("titleEn", "Information Security & Governance Policy");
        payload.put("titleAr", "سياسة أمن المعلومات والحوكمة");
        payload.put("summaryEn",
            "Establishes mandatory governance, risk management, and compliance controls aligned with "
            + "applicable Saudi regulatory frameworks.");
        payload.put("summaryAr",
            "تحدد هذه السياسة ضوابط الحوكمة وإدارة المخاطر والالتزام الإلزامية بما يتوافق مع الأطر "
            + "التنظيمية المعمول بها في المملكة العربية السعودية.");
        payload.put("contentMdEn",
            "## 1. Purpose\n"
            + "This policy defines mandatory security and compliance controls for the organisation.\n\n"
            + "## 2. Scope\n"
            + "Applies to all employees, contractors, information assets, and third-party processors.\n\n"
            + "## 3. Policy Statements\n"
            + "- Access to sensitive information assets shall follow least-privilege and segregation-of-duties principles.\n"
            + "- Control owners shall maintain documented evidence of operating effectiveness.\n"
            + "- Exceptions require formal risk assessment, compensating controls, and time-bound approval.\n\n"
            + "## 4. Roles & Responsibilities\n"
            + "- **Policy Owner:** Maintains and reviews this policy at least annually.\n"
            + "- **Compliance Function:** Monitors adherence and reports non-compliance.\n\n"
            + "## 5. Compliance & Review\n"
            + "Violations are handled under disciplinary procedures. Reviewed annually or upon major regulatory change.");
        payload.put("contentMdAr",
            "## ١. الغرض\n"
            + "تحدد هذه السياسة ضوابط الأمن والالتزام الإلزامية لحماية أصول المعلومات في الجهة.\n\n"
            + "## ٢. النطاق\n"
            + "تسري هذه السياسة على جميع الموظفين والمتعاقدين والأنظمة ومزودي الخدمات الخارجيين.\n\n"
            + "## ٣. بنود السياسة\n"
            + "- يخضع الوصول إلى الأصول الحساسة لمبدأ الحد الأدنى من الصلاحيات والفصل بين المهام.\n"
            + "- يلتزم ملاك الضوابط بالاحتفاظ بالأدلة المعتمدة التي تثبت فعالية التطبيق.\n"
            + "- تتطلب الاستثناءات تقييماً رسمياً للمخاطر وضوابط تعويضية وموافقة محددة المدة.\n\n"
            + "## ٤. الأدوار والمسؤوليات\n"
            + "- **مالك السياسة:** مسؤول عن صيانة هذه السياسة ومراجعتها دورياً.\n"
            + "- **إدارة الالتزام:** تتابع مستوى الامتثال وترفع تقارير عدم المطابقة.\n\n"
            + "## ٥. الالتزام والمراجعة\n"
            + "تُعالج المخالفات وفق الإجراءات المعتمدة، وتُراجع هذه السياسة سنوياً أو عند صدور تحديثات تنظيمية جوهرية.");
        payload.put("citedControlIds", request.groundingControlIds());
        payload.put("suggestedReviewCadence", "ANNUAL");
        payload.put("assumptions", List.of(
            "Generated by deterministic STUB LLM client for offline verification."
        ));
        return payload;
    }

    private Map<String, Object> stubRiskDescription(LlmRequest request) {
        String prompt = request.userPrompt() != null ? request.userPrompt() : "";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("descriptionEn", "Risk that the identified threat materialises, leading to operational, reputational, and regulatory impacts on the organisation.");
        payload.put("descriptionAr", "خطر يؤدي إلى آثار تشغيلية وسمعة وتنظيمية على المنشأة بما يتطلب تطبيق الضوابط الوقائية.");
        List<String> causes = new ArrayList<>(List.of(
            "Inadequate or unenforced controls in the affected environment",
            "Human error, training gap, or credential misplacement",
            "Third-party vendor dependency without verified redundancy"
        ));
        List<String> consequences = new ArrayList<>(List.of(
            "Service disruption affecting operations and customer commitments",
            "Regulatory non-compliance finding from the relevant Saudi authority",
            "Data exfiltration triggering PDPL Article 20 notification to SDAIA within 72 hours"
        ));
        payload.put("causes", causes);
        payload.put("consequences", consequences);
        payload.put("confidence", 85);
        return payload;
    }

    private Map<String, Object> stubBiaRtoRpo(LlmRequest request) {
        String p = request.userPrompt() != null ? request.userPrompt().toUpperCase(Locale.ROOT) : "";
        int rtoMinutes, rpoMinutes;
        String rtoHumanEn, rpoHumanEn, rtoHumanAr, rpoHumanAr, rationale;
        if (p.contains("CRITICAL") || p.contains("TIER_1") || p.contains("TIER 1") || p.contains("TIER: 1")) {
            rtoMinutes = 240;  rpoMinutes = 15;
            rtoHumanEn = "4 hours";    rpoHumanEn = "15 minutes";
            rtoHumanAr = "4 ساعات";    rpoHumanAr = "15 دقيقة";
            rationale = "Tier-1 / critical service. SAMA OPRES and NCEMA standard require RTO ≤ 4h and RPO near-zero.";
        } else if (p.contains("ESSENTIAL") || p.contains("TIER_2") || p.contains("TIER 2") || p.contains("TIER: 2")) {
            rtoMinutes = 1440; rpoMinutes = 240;
            rtoHumanEn = "24 hours";   rpoHumanEn = "4 hours";
            rtoHumanAr = "24 ساعة";    rpoHumanAr = "4 ساعات";
            rationale = "Tier-2 / essential service. RTO ≤ 24h and RPO ≤ 4h aligns with standard resilience baselines.";
        } else {
            rtoMinutes = 4320; rpoMinutes = 1440;
            rtoHumanEn = "72 hours";   rpoHumanEn = "24 hours";
            rtoHumanAr = "72 ساعة";    rpoHumanAr = "24 ساعة";
            rationale = "Tier-3 / supporting service. RTO ≤ 72h and RPO ≤ 24h aligns with ISO 22301 §8.2.2.";
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rtoMinutes", rtoMinutes);
        payload.put("rpoMinutes", rpoMinutes);
        payload.put("rtoHumanEn", rtoHumanEn);
        payload.put("rpoHumanEn", rpoHumanEn);
        payload.put("rtoHumanAr", rtoHumanAr);
        payload.put("rpoHumanAr", rpoHumanAr);
        payload.put("mtpdMinutes", rtoMinutes * 3);
        payload.put("rationale", rationale);
        payload.put("confidence", 80);
        return payload;
    }

    private Map<String, Object> stubEvidenceQuality(LlmRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("qualityScore", 85);
        payload.put("verdict", "ACCEPTABLE");
        payload.put("strengths", List.of(
            "Evidence artifact is current and within the active audit review window",
            "Item carries cryptographic hash and linkage to mandatory control requirement"
        ));
        payload.put("issues", List.of(
            "Verify dual sign-off is completed before submission to external regulator"
        ));
        payload.put("rationale", "Evidence artifact satisfies Saudi GRC evidentiary defensibility criteria.");
        payload.put("confidence", 85);
        return payload;
    }

    private Map<String, Object> stubIncidentRootCause(LlmRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rootCauseDraftEn",
            "Event narrative: Initial compromise occurred through a misconfigured service endpoint, "
            + "compounded by alerting delay in the SOC tier-1 queue. Containment succeeded once lateral movement "
            + "was blocked at network boundary controls.");
        payload.put("rootCauseDraftAr",
            "تحليل أولي للسبب الجذري: نتج الحادث عن ثغرة في الإعدادات مصحوبة بتأخر في التنبيه حتى تم عزل الحركة الجانبية.");
        payload.put("categoriesToPick", List.of("PEOPLE", "PROCESS", "TECHNOLOGY", "SUPPLIER", "EXTERNAL"));
        payload.put("rationale", "5-Whys structured root-cause synthesis generated from situation report timeline.");
        payload.put("confidence", 75);
        return payload;
    }

    private Map<String, Object> stubBoardSummary(LlmRequest request) {
        String p = request.userPrompt() != null ? request.userPrompt() : "";
        boolean attention = p.contains("Open High Risks: 18")
            || p.contains("DELIVER ATTENTION")
            || p.contains("High Risks: 1")
            || p.contains("NCA Compliance: 71%")
            || p.contains("PDPL Compliance: 68%");
        String tone = attention ? "DELIVER ATTENTION" : "HEALTHY";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summaryEn", "Executive GRC posture this period: " + tone + ".\n\n"
            + "- Enterprise risk register and corrective action status have been reviewed.\n"
            + "- Key regulatory indicators (NCA, PDPL, SAMA) monitored for quarterly compliance posture.\n\n"
            + "Recommended board decisions:\n"
            + "  1. Note overall GRC posture and risk-appetite alignment.\n"
            + "  2. Approve prioritized CAPA remediation resources.");
        payload.put("summaryAr", "الموقف التنفيذي للحوكمة والمخاطر: " + (attention ? "يتطلب الاهتمام" : "سليم")
            + ". تم استعراض سجل المخاطر وخطط المعالجة ونسب الامتثال للجهات التنظيمية.");
        payload.put("tone", tone);
        payload.put("rationale", "Board briefing synthesis derived from enterprise GRC indicators.");
        payload.put("confidence", 85);
        return payload;
    }

    private Map<String, Object> stubWorkflowSubmissionReview(LlmRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headlineEn", "Workflow submission review against applicable Saudi regulatory standards");
        payload.put("headlineAr", "مراجعة تقديم المعالج وفق المتطلبات والمعايير التنظيمية المعمول بها");
        payload.put("checklistEn", List.of(
            "Verify the regulatory anchor is aligned with the submission type and scope",
            "Spot-check payload details against mandatory statutory deadlines (e.g. 72-hour SDAIA clock)",
            "Confirm designated committee approvers have completed necessary sign-offs"
        ));
        payload.put("checklistAr", List.of(
            "التحقق من صحة المرجع التنظيمي ومطابقته لنطاق الطلب",
            "التأكد من الالتزام بالمهل النظامية للإبلاغ (مثل مهلة ٧٢ ساعة للهيئة)",
            "التحقق من اكتمال اعتمادات أعضاء اللجنة المعنية"
        ));
        payload.put("regulatorCitation", "SAMA / NCA / SDAIA");
        payload.put("suggestedDecision", "APPROVE");
        payload.put("rationale", "Review guidance generated for committee consideration.");
        payload.put("confidence", 80);
        return payload;
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.getBytes(StandardCharsets.UTF_8).length / 4);
    }
}

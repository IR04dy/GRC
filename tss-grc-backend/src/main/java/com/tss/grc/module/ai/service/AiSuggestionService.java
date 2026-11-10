package com.tss.grc.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ai.entity.AiSuggestion;
import com.tss.grc.module.ai.repository.AiSuggestionRepository;
import com.tss.grc.module.integrations.aigateway.service.AiGatewayService;
import com.tss.grc.module.integrations.llm.dto.LlmRequest;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AI-assisted features (Saudi GRC implementation prompt §27 / V116).
 *
 * <p>Persists each AI generation as an {@link AiSuggestion} draft so a human can
 * accept or reject the suggestion before it mutates any real record.
 *
 * <p>Each feature first asks {@link AiGatewayService} for a model-generated draft. When
 * that call throws (gateway disabled, over quota, unavailable, request blocked) or the
 * reply is not the expected JSON shape - which includes every STUB-mode reply - the
 * feature falls back to a rule-based heuristic and logs the fallback at WARN. Heuristic
 * output is not model output: it is keyword and threshold rules over the input, and its
 * rationale describes the rule. The record does not store which path produced it, so a
 * reviewer should read every suggestion as a draft to check, never as an assessment.
 */
@Service
@RequiredArgsConstructor
public class AiSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiSuggestionService.class);

    private final AiSuggestionRepository repo;
    private final AuditLogService audit;
    private final ObjectMapper mapper;
    private final AiGatewayService llmGateway;
    private final PlatformTransactionManager txManager;

    private Long org() {
        Long tenant = TenantContext.getCurrentTenant();
        return tenant != null ? tenant : 1L;
    }

    // ── DTOs ──────────────────────────────────────────────────────────

    public record RiskDescriptionInput(Long riskId, String titleEn,
                                       String categoryCode, Integer inherentLikelihood,
                                       Integer inherentImpact) {}
    public record BiaRecommendationInput(Long serviceId, String serviceTitle,
                                         String criticality, String tier) {}
    public record EvidenceQualityInput(Long evidenceId, String evidenceTitle,
                                       String evidenceType, String controlCode,
                                       Integer ageDays, Boolean hasSignature) {}
    public record IncidentRootCauseInput(Long crisisEventId, String title,
                                         Integer severity, String triggerSource,
                                         List<String> sitRepHighlights) {}
    public record BoardSummaryInput(Integer openHighRisks, Integer overdueCapa,
                                    Integer ncaCompliancePct, Integer pdplCompliancePct,
                                    Integer bcmReadinessScore, Integer activeIncidents) {}

    /** Input for the wizard-submission review-hint generator (PR #23).
     *  All fields nullable so the frontend can call this for any submission. */
    public record WorkflowSubmissionReviewInput(
        Long submissionId,
        String workflowId,
        String workflowModule,
        String status,
        String regulatoryAnchor,
        String titleEn,
        String summaryEn
    ) {}

    public record ReviewBody(String decision, String reviewerNote) {}

    // ══════════════════════════════════════════════════════════════════
    // §27 #1 — Risk description draft
    // ══════════════════════════════════════════════════════════════════

    public AiSuggestion suggestRiskDescription(RiskDescriptionInput in) {
        if (in == null) {
            in = new RiskDescriptionInput(null, "", null, null, null);
        }
        Long orgId = org();
        if (orgId != null && llmGateway != null) {
            try {
                String systemPrompt = """
                    You are an expert enterprise risk management (ERM) analyst specializing in Saudi regulatory compliance (NCA ECC, SAMA CSF/OPRES, PDPL).
                    Analyze the risk title, category, and inherent score. Return ONLY a JSON object with this exact structure:
                    {
                      "descriptionEn": "Risk statement in English following the format 'Risk that [event] occurs due to [causes], resulting in [impacts]'.",
                      "descriptionAr": "صياغة دقيقة واحترافية لبيان الخطر باللغة العربية تشمل الحدث والسبب والأثر.",
                      "causes": [
                        "Cause 1 in English",
                        "Cause 2 in English",
                        "Cause 3 in English",
                        "Cause 4 in English"
                      ],
                      "consequences": [
                        "Consequence 1 in English",
                        "Consequence 2 in English",
                        "Consequence 3 in English",
                        "Consequence 4 in English"
                      ],
                      "confidence": 85
                    }
                    Ensure the causes and consequences cite relevant Saudi regulatory aspects (e.g. PDPL Article 20 for privacy/cyber, SAMA OPRES for banking/operational risks) where appropriate.
                    """;

                String userPrompt = "Risk Title: " + safe(in.titleEn())
                    + "\nCategory: " + safe(in.categoryCode())
                    + "\nInherent Likelihood (1-5): " + in.inherentLikelihood()
                    + "\nInherent Impact (1-5): " + in.inherentImpact();

                LlmRequest req = new LlmRequest(
                    "RISK_DESCRIPTION",
                    "prompt-risk-description",
                    "1.0.0",
                    systemPrompt,
                    userPrompt,
                    true,
                    1200,
                    0.3,
                    List.of()
                );

                LlmResponse resp = llmGateway.complete(orgId, req);
                if (resp != null && resp.content() != null && !resp.content().isBlank()) {
                    Map<String, Object> output = parseJsonMap(resp.content());
                    if (output.containsKey("causes") && output.containsKey("consequences")) {
                        BigDecimal conf = extractConfidence(output, 85);
                        String rationale = "Generated by LLM (" + resp.provider() + " / " + resp.model() + ") grounded in Saudi ERM & regulatory context.";
                        return persist("RISK_DESCRIPTION", "Risk", in.riskId(), in, output, conf, rationale);
                    }
                }
                log.warn("LLM risk description suggestion reply had no usable draft, falling back to heuristic");
            } catch (Exception ex) {
                log.warn("LLM risk description suggestion unavailable ({}), falling back to heuristic", ex.getMessage());
            }
        }
        return fallbackRiskDescription(in, "Heuristic expansion using title + category.");
    }

    private AiSuggestion fallbackRiskDescription(RiskDescriptionInput in, String note) {
        String desc = "Risk that " + safe(in.titleEn) + " materialises, leading to operational, "
                    + "reputational, and regulatory impacts on the organisation.";

        List<String> causes = new ArrayList<>(List.of(
            "Inadequate or unenforced controls in the affected process",
            "Human error, gap in training, or insufficient awareness",
            "Third-party / supplier failure or dependency without alternates",
            "Process change introduced without risk review"));
        List<String> consequences = new ArrayList<>(List.of(
            "Service disruption affecting customers and revenue",
            "Regulatory finding from the relevant authority (SAMA / NCA / SDAIA)",
            "Reputational damage covered by local + regional media",
            "Internal investigation, remediation cost, and management attention"));
        if (in.categoryCode() != null && in.categoryCode().toUpperCase(Locale.ROOT).contains("CYBER")) {
            causes.add("Threat-actor exploitation of a known vulnerability");
            consequences.add("Data exfiltration triggering PDPL Article 20 (72-hour notification)");
        }
        if (in.categoryCode() != null && in.categoryCode().toUpperCase(Locale.ROOT).contains("PRIVACY")) {
            causes.add("Personal data processed without explicit consent or legal basis");
            consequences.add("Data-subject complaint to SDAIA and PDPL administrative fine");
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("descriptionEn", desc);
        output.put("descriptionAr", "خطر " + safe(in.titleEn) + " يؤدي إلى آثار تشغيلية وسمعة وتنظيمية على المنشأة.");
        output.put("causes", causes);
        output.put("consequences", consequences);
        int conf = 55 + Math.min(20, (in.titleEn == null ? 0 : in.titleEn.length() / 5));
        return persist("RISK_DESCRIPTION", "Risk", in.riskId(), in, output,
            BigDecimal.valueOf(conf), note);
    }

    // ══════════════════════════════════════════════════════════════════
    // §27 #3 — BIA RTO/RPO recommendation
    // ══════════════════════════════════════════════════════════════════

    public AiSuggestion suggestBiaRtoRpo(BiaRecommendationInput in) {
        Long orgId = org();
        if (orgId != null && llmGateway != null) {
            try {
                String systemPrompt = """
                    You are a business continuity (BCM) advisor specializing in Saudi resilience standards (NCEMA 7000, ISO 22301, SAMA BCM Framework).
                    Recommend target Recovery Time Objective (RTO) and Recovery Point Objective (RPO) based on service criticality and tier.
                    Return ONLY a JSON object with this exact structure:
                    {
                      "rtoMinutes": 240,
                      "rpoMinutes": 15,
                      "rtoHumanEn": "4 hours",
                      "rpoHumanEn": "15 minutes",
                      "rtoHumanAr": "4 ساعات",
                      "rpoHumanAr": "15 دقيقة",
                      "mtpdMinutes": 720,
                      "rationale": "Justification citing NCEMA 7000 or SAMA resilience expectations.",
                      "confidence": 80
                    }
                    """;

                String userPrompt = "Service Title: " + safe(in.serviceTitle())
                    + "\nCriticality: " + safe(in.criticality())
                    + "\nTier: " + safe(in.tier());

                LlmRequest req = new LlmRequest(
                    "BIA_RTO_RPO",
                    "prompt-bia-rto-rpo",
                    "1.0.0",
                    systemPrompt,
                    userPrompt,
                    true,
                    800,
                    0.2,
                    List.of()
                );

                LlmResponse resp = llmGateway.complete(orgId, req);
                if (resp != null && resp.content() != null && !resp.content().isBlank()) {
                    Map<String, Object> output = parseJsonMap(resp.content());
                    if (output.containsKey("rtoMinutes") && output.containsKey("rpoMinutes")) {
                        BigDecimal conf = extractConfidence(output, 80);
                        String rationale = extractRationale(output,
                            "Generated by LLM (" + resp.provider() + " / " + resp.model() + ") based on NCEMA/SAMA resilience guidelines.");
                        return persist("BIA_RTO_RPO", "Service", in.serviceId(), in, output, conf, rationale);
                    }
                }
                log.warn("LLM BIA suggestion reply had no usable draft, falling back to heuristic");
            } catch (Exception ex) {
                log.warn("LLM BIA suggestion unavailable ({}), falling back to heuristic", ex.getMessage());
            }
        }
        return fallbackBiaRtoRpo(in);
    }

    private AiSuggestion fallbackBiaRtoRpo(BiaRecommendationInput in) {
        String crit = String.valueOf(in.criticality()).toUpperCase(Locale.ROOT);
        String tier = String.valueOf(in.tier()).toUpperCase(Locale.ROOT);
        int rtoMinutes, rpoMinutes;
        String rationale;
        if (crit.startsWith("CRITICAL") || tier.contains("1")) {
            rtoMinutes = 240;   rpoMinutes = 15;
            rationale = "Tier-1 / critical Saudi service. NCEMA AAR template + SAMA OPRES expect RTO ≤ 4h, RPO near-zero.";
        } else if (crit.startsWith("ESSENTIAL") || tier.contains("2")) {
            rtoMinutes = 1440;  rpoMinutes = 240;
            rationale = "Tier-2 / essential. RTO ≤ 24h, RPO ≤ 4h is the industry norm for backend services.";
        } else {
            rtoMinutes = 4320;  rpoMinutes = 1440;
            rationale = "Tier-3 / supporting. RTO ≤ 72h, RPO ≤ 24h is acceptable per ISO 22301 §8.2.2.";
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("rtoMinutes", rtoMinutes);
        output.put("rpoMinutes", rpoMinutes);
        output.put("rtoHumanEn", humanDuration(rtoMinutes));
        output.put("rpoHumanEn", humanDuration(rpoMinutes));
        output.put("rtoHumanAr", humanDurationAr(rtoMinutes));
        output.put("rpoHumanAr", humanDurationAr(rpoMinutes));
        output.put("mtpdMinutes", rtoMinutes * 3);
        return persist("BIA_RTO_RPO", "Service", in.serviceId(), in, output,
            BigDecimal.valueOf(70), rationale);
    }

    // ══════════════════════════════════════════════════════════════════
    // §27 #8 — Evidence quality review
    // ══════════════════════════════════════════════════════════════════

    public AiSuggestion reviewEvidenceQuality(EvidenceQualityInput in) {
        Long orgId = org();
        if (orgId != null && llmGateway != null) {
            try {
                String systemPrompt = """
                    You are a senior compliance auditor evaluating evidence defensibility for Saudi regulators (NCA, SAMA, SDAIA).
                    Evaluate evidence attributes (age, digital signature, control linkage, artifact type).
                    Return ONLY a JSON object with this exact structure:
                    {
                      "qualityScore": 85,
                      "verdict": "ACCEPTABLE",
                      "strengths": [
                        "Strength 1 in English",
                        "Strength 2 in English"
                      ],
                      "issues": [
                        "Issue or gap 1 in English"
                      ],
                      "rationale": "Audit defensibility rationale.",
                      "confidence": 85
                    }
                    verdict must be one of: 'ACCEPTABLE' (score >= 75), 'NEEDS_IMPROVEMENT' (50..74), or 'INSUFFICIENT' (< 50).
                    """;

                String userPrompt = "Evidence Title: " + safe(in.evidenceTitle())
                    + "\nType: " + safe(in.evidenceType())
                    + "\nControl Code: " + safe(in.controlCode())
                    + "\nAge (Days): " + in.ageDays()
                    + "\nDigitally Signed: " + in.hasSignature();

                LlmRequest req = new LlmRequest(
                    "EVIDENCE_QUALITY",
                    "prompt-evidence-quality",
                    "1.0.0",
                    systemPrompt,
                    userPrompt,
                    true,
                    800,
                    0.2,
                    List.of()
                );

                LlmResponse resp = llmGateway.complete(orgId, req);
                if (resp != null && resp.content() != null && !resp.content().isBlank()) {
                    Map<String, Object> output = parseJsonMap(resp.content());
                    if (output.containsKey("qualityScore") && output.containsKey("verdict")) {
                        BigDecimal conf = extractConfidence(output, 85);
                        String rationale = extractRationale(output,
                            "Generated by LLM (" + resp.provider() + " / " + resp.model() + ") based on regulator evidentiary criteria.");
                        return persist("EVIDENCE_QUALITY", "EvidenceItem", in.evidenceId(), in, output, conf, rationale);
                    }
                }
                log.warn("LLM evidence quality review reply had no usable draft, falling back to heuristic");
            } catch (Exception ex) {
                log.warn("LLM evidence quality review unavailable ({}), falling back to heuristic", ex.getMessage());
            }
        }
        return fallbackEvidenceQuality(in);
    }

    private AiSuggestion fallbackEvidenceQuality(EvidenceQualityInput in) {
        List<String> issues = new ArrayList<>();
        List<String> strengths = new ArrayList<>();
        int score = 60;

        if (in.ageDays() != null && in.ageDays() > 365) {
            issues.add("Evidence is older than 12 months — review_due cadence will flag this soon");
            score -= 20;
        } else if (in.ageDays() != null && in.ageDays() > 180) {
            issues.add("Evidence is over 6 months old; refresh before the next audit window");
            score -= 10;
        } else {
            strengths.add("Evidence is current (<180 days old)");
            score += 10;
        }
        if (Boolean.TRUE.equals(in.hasSignature())) {
            strengths.add("Item carries a digital signature — defensible for board / regulator review");
            score += 15;
        } else {
            issues.add("No digital signature attached — sign before submitting to a regulator");
            score -= 5;
        }
        if (in.evidenceType() == null || in.evidenceType().isBlank()) {
            issues.add("Evidence type not classified (POLICY/PROCEDURE/REPORT/LOG/etc.)");
            score -= 10;
        }
        if (in.controlCode() == null || in.controlCode().isBlank()) {
            issues.add("Not linked to a specific control — regulators expect 1:1 control mapping");
            score -= 10;
        } else {
            strengths.add("Linked to control " + in.controlCode());
        }
        score = Math.max(0, Math.min(100, score));

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("qualityScore", score);
        output.put("strengths", strengths);
        output.put("issues", issues);
        output.put("verdict", score >= 75 ? "ACCEPTABLE" : (score >= 50 ? "NEEDS_IMPROVEMENT" : "INSUFFICIENT"));
        return persist("EVIDENCE_QUALITY", "EvidenceItem", in.evidenceId(), in, output,
            BigDecimal.valueOf(score),
            "Rule-based scoring on age, signature, type, and control linkage.");
    }

    // ══════════════════════════════════════════════════════════════════
    // §27 #13 — Incident root-cause draft
    // ══════════════════════════════════════════════════════════════════

    public AiSuggestion draftIncidentRootCause(IncidentRootCauseInput in) {
        Long orgId = org();
        if (orgId != null && llmGateway != null) {
            try {
                String systemPrompt = """
                    You are an incident response and crisis management expert specializing in 5-Whys root cause analysis aligned with NCA ECC and NIST 800-61.
                    Synthesize the crisis event metadata, trigger source, and timeline highlights.
                    Return ONLY a JSON object with this exact structure:
                    {
                      "rootCauseDraftEn": "Detailed 5-Whys narrative in English synthesizing timeline highlights, control bypass, detection lag, and containment runbook execution.",
                      "rootCauseDraftAr": "تحليل أولي متكامل للسبب الجذري باللغة العربية يلخص مجريات الحادث والعوامل المسببة والإجراءات التصحيحية الموصى بها.",
                      "categoriesToPick": ["PEOPLE", "PROCESS", "TECHNOLOGY", "SUPPLIER", "EXTERNAL"],
                      "rationale": "RCA synthesis methodology notes.",
                      "confidence": 75
                    }
                    """;

                String userPrompt = "Incident Title: " + safe(in.title())
                    + "\nSeverity (1-5): " + in.severity()
                    + "\nTrigger Source: " + safe(in.triggerSource())
                    + "\nSitRep Highlights: " + (in.sitRepHighlights() != null ? String.join("; ", in.sitRepHighlights()) : "None");

                LlmRequest req = new LlmRequest(
                    "INCIDENT_ROOT_CAUSE",
                    "prompt-incident-rca",
                    "1.0.0",
                    systemPrompt,
                    userPrompt,
                    true,
                    1200,
                    0.3,
                    List.of()
                );

                LlmResponse resp = llmGateway.complete(orgId, req);
                if (resp != null && resp.content() != null && !resp.content().isBlank()) {
                    Map<String, Object> output = parseJsonMap(resp.content());
                    if (output.containsKey("rootCauseDraftEn")) {
                        BigDecimal conf = extractConfidence(output, 75);
                        String rationale = extractRationale(output,
                            "Generated by LLM (" + resp.provider() + " / " + resp.model() + ") synthesizing incident sitreps and NCA ECC expectations.");
                        return persist("INCIDENT_ROOT_CAUSE", "CrisisEvent", in.crisisEventId(), in, output, conf, rationale);
                    }
                }
                log.warn("LLM incident RCA suggestion reply had no usable draft, falling back to heuristic");
            } catch (Exception ex) {
                log.warn("LLM incident RCA suggestion unavailable ({}), falling back to heuristic", ex.getMessage());
            }
        }
        return fallbackIncidentRootCause(in);
    }

    private AiSuggestion fallbackIncidentRootCause(IncidentRootCauseInput in) {
        StringBuilder narrative = new StringBuilder();
        narrative.append("Event: ").append(safe(in.title())).append(".\n\n");
        narrative.append("Apparent trigger: ").append(safe(in.triggerSource())).append(".\n\n");

        if (in.sitRepHighlights() != null && !in.sitRepHighlights().isEmpty()) {
            narrative.append("Timeline highlights from situation reports:\n");
            for (String h : in.sitRepHighlights()) {
                narrative.append("  • ").append(h).append("\n");
            }
            narrative.append("\n");
        }
        narrative.append("Probable contributing factors:\n")
                 .append("  1. Control(s) intended to prevent this event either did not exist, ")
                 .append("were not enforced, or were bypassed.\n")
                 .append("  2. Detection signals existed but did not reach the responder fast enough.\n")
                 .append("  3. Recovery / containment runbook either was missing or was not rehearsed.\n\n")
                 .append("Recommended root-cause categories (pick at least one before closing the AAR):\n")
                 .append("  • PEOPLE — training, awareness, role assignment\n")
                 .append("  • PROCESS — runbook, escalation, communication tree\n")
                 .append("  • TECHNOLOGY — control configuration, redundancy, monitoring\n")
                 .append("  • SUPPLIER — third-party dependency / SLA failure\n")
                 .append("  • EXTERNAL — force-majeure, regulatory change, nation-state event");

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("rootCauseDraftEn", narrative.toString());
        output.put("rootCauseDraftAr",
            "هذه مسودة آلية للسبب الجذري بناءً على بيانات الحدث؛ يجب على فريق الأزمة مراجعتها قبل اعتمادها.");
        output.put("categoriesToPick", List.of("PEOPLE","PROCESS","TECHNOLOGY","SUPPLIER","EXTERNAL"));
        int conf = 50;
        if (in.severity() != null && in.severity() >= 4) conf -= 10;
        if (in.sitRepHighlights() != null) conf += Math.min(20, in.sitRepHighlights().size() * 4);

        return persist("INCIDENT_ROOT_CAUSE", "CrisisEvent", in.crisisEventId(), in, output,
            BigDecimal.valueOf(conf),
            "5-Whys template stitched from event metadata + sitrep highlights. Always pair with human RCA.");
    }

    // ══════════════════════════════════════════════════════════════════
    // §27 #11 — Board summary drafting
    // ══════════════════════════════════════════════════════════════════

    public AiSuggestion draftBoardSummary(BoardSummaryInput in) {
        Long orgId = org();
        if (orgId != null && llmGateway != null) {
            try {
                String systemPrompt = """
                    You are an executive GRC advisor drafting the briefing for the Board Audit & Risk Committee.
                    Synthesize the enterprise posture indicators. Return ONLY a JSON object with this exact structure:
                    {
                      "summaryEn": "Executive narrative in English summarizing overall posture, high risks, overdue CAPAs, and 3 recommended board decisions.",
                      "summaryAr": "ملخص تنفيذي باللغة العربية موجه لمجلس الإدارة ولجنة المراجعة والمخاطر يوضح الموقف العام والتوصيات.",
                      "tone": "STEADY",
                      "rationale": "Assessment derived from operational risk indicators.",
                      "confidence": 85
                    }
                    tone must be one of: 'DELIVER ATTENTION' (if open high risks > 10 or compliance < 70%), 'STEADY' (if open high risks > 5 or compliance < 85%), or 'HEALTHY'.
                    """;

                String userPrompt = "Open High Risks: " + nullSafe(in.openHighRisks())
                    + "\nOverdue CAPAs: " + nullSafe(in.overdueCapa())
                    + "\nNCA Compliance: " + nullSafe(in.ncaCompliancePct()) + "%"
                    + "\nPDPL Compliance: " + nullSafe(in.pdplCompliancePct()) + "%"
                    + "\nBCM Readiness: " + nullSafe(in.bcmReadinessScore()) + "%"
                    + "\nActive Incidents: " + nullSafe(in.activeIncidents());

                LlmRequest req = new LlmRequest(
                    "BOARD_SUMMARY",
                    "prompt-board-summary",
                    "1.0.0",
                    systemPrompt,
                    userPrompt,
                    true,
                    1200,
                    0.3,
                    List.of()
                );

                LlmResponse resp = llmGateway.complete(orgId, req);
                if (resp != null && resp.content() != null && !resp.content().isBlank()) {
                    Map<String, Object> output = parseJsonMap(resp.content());
                    if (output.containsKey("summaryEn") && output.containsKey("tone")) {
                        BigDecimal conf = extractConfidence(output, 85);
                        String rationale = extractRationale(output,
                            "Generated by LLM (" + resp.provider() + " / " + resp.model() + ") synthesizing executive indicators.");
                        return persist("BOARD_SUMMARY", "Dashboard", null, in, output, conf, rationale);
                    }
                }
                log.warn("LLM board summary reply had no usable draft, falling back to heuristic");
            } catch (Exception ex) {
                log.warn("LLM board summary draft unavailable ({}), falling back to heuristic", ex.getMessage());
            }
        }
        return fallbackBoardSummary(in);
    }

    private AiSuggestion fallbackBoardSummary(BoardSummaryInput in) {
        int high = nullSafe(in.openHighRisks());
        int overdue = nullSafe(in.overdueCapa());
        int nca = nullSafe(in.ncaCompliancePct());
        int pdpl = nullSafe(in.pdplCompliancePct());
        int bcm = nullSafe(in.bcmReadinessScore());
        int incidents = nullSafe(in.activeIncidents());

        String tone = (high > 10 || nca < 70 || pdpl < 70 || bcm < 70) ? "DELIVER ATTENTION" :
                      (high > 5  || nca < 85 || pdpl < 85 || bcm < 85) ? "STEADY"           :
                      "HEALTHY";

        StringBuilder en = new StringBuilder();
        en.append("Executive GRC posture this period: ").append(tone).append(".\n\n");
        en.append("- Enterprise risk register holds ").append(high).append(" open HIGH / VERY_HIGH risks. ");
        en.append(high > 10 ? "Concentrated treatment focus is recommended.\n" : "Within risk appetite for the cycle.\n");
        en.append("- ").append(overdue).append(" corrective actions are overdue; ");
        en.append(overdue > 5 ? "this is the headline action item for the next executive review.\n"
                              : "all expected closures are on track.\n");
        en.append("- NCA compliance score: ").append(nca).append("% across the in-scope frameworks.\n");
        en.append("- PDPL compliance score: ").append(pdpl).append("% across the active processing register.\n");
        en.append("- BCM readiness score: ").append(bcm).append("% — measured against the current cycle.\n");
        en.append("- Active operational / cyber incidents: ").append(incidents).append(".\n\n");
        en.append("Recommended board decisions:\n");
        en.append("  1. Note the GRC posture and risk-appetite alignment.\n");
        en.append("  2. Approve the proposed treatment for the top HIGH risks (separate paper).\n");
        en.append("  3. Endorse the cyber + privacy investment line for the next quarter.\n");

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("summaryEn", en.toString());
        output.put("summaryAr",
            "مسودة آلية لملخص المجلس: تم احتسابها من لوحة القيادة الحالية للحوكمة والمخاطر والامتثال. يجب على أمين المجلس مراجعتها قبل النشر.");
        output.put("tone", tone);
        return persist("BOARD_SUMMARY", "Dashboard", null, in, output,
            BigDecimal.valueOf(80),
            "Thresholded rollup over six dashboard inputs.");
    }

    // ══════════════════════════════════════════════════════════════════
    // §27 #12 — Workflow-submission review hint (PR #23)
    // ══════════════════════════════════════════════════════════════════

    public AiSuggestion suggestWorkflowSubmissionReview(WorkflowSubmissionReviewInput in) {
        Long orgId = org();
        if (orgId != null && llmGateway != null) {
            try {
                String systemPrompt = """
                    You are a senior regulatory compliance officer reviewing a workflow submission before formal executive approval.
                    Analyze workflow ID, module, regulatory anchor, title, and summary.
                    Return ONLY a JSON object with this exact structure:
                    {
                      "headlineEn": "English headline summarizing the key regulatory gating question.",
                      "headlineAr": "عنوان تنفيذي باللغة العربية يلخص السؤال الرقابي الأساسي.",
                      "checklistEn": [
                        "Checklist verification item 1 in English",
                        "Checklist verification item 2 in English",
                        "Checklist verification item 3 in English"
                      ],
                      "checklistAr": [
                        "بند تحقق 1 باللغة العربية",
                        "بند تحقق 2 باللغة العربية",
                        "بند تحقق 3 باللغة العربية"
                      ],
                      "regulatorCitation": "Applicable regulator citation (e.g. PDPL Art 20, NCA ECC-1:2018 §3.2, ISO 22301 §9.3)",
                      "suggestedDecision": "APPROVE",
                      "rationale": "Evaluation justification.",
                      "confidence": 80
                    }
                    suggestedDecision must be 'APPROVE', 'REVIEW', or 'CLOSE'.
                    """;

                String userPrompt = "Workflow ID: " + safe(in.workflowId())
                    + "\nModule: " + safe(in.workflowModule())
                    + "\nStatus: " + safe(in.status())
                    + "\nRegulatory Anchor: " + safe(in.regulatoryAnchor())
                    + "\nTitle: " + safe(in.titleEn())
                    + "\nSummary: " + safe(in.summaryEn());

                LlmRequest req = new LlmRequest(
                    "WORKFLOW_SUBMISSION_REVIEW",
                    "prompt-workflow-review",
                    "1.0.0",
                    systemPrompt,
                    userPrompt,
                    true,
                    1000,
                    0.2,
                    List.of()
                );

                LlmResponse resp = llmGateway.complete(orgId, req);
                if (resp != null && resp.content() != null && !resp.content().isBlank()) {
                    Map<String, Object> output = parseJsonMap(resp.content());
                    if (output.containsKey("checklistEn") && output.containsKey("suggestedDecision")) {
                        BigDecimal conf = extractConfidence(output, 80);
                        String rationale = extractRationale(output,
                            "Generated by LLM (" + resp.provider() + " / " + resp.model() + ") based on workflow regulatory context.");
                        return persist("WORKFLOW_SUBMISSION_REVIEW", "WorkflowSubmission", in.submissionId(),
                            in, output, conf, rationale);
                    }
                }
                log.warn("LLM workflow review suggestion reply had no usable draft, falling back to heuristic");
            } catch (Exception ex) {
                log.warn("LLM workflow review suggestion unavailable ({}), falling back to heuristic", ex.getMessage());
            }
        }
        return fallbackWorkflowSubmissionReview(in);
    }

    private AiSuggestion fallbackWorkflowSubmissionReview(WorkflowSubmissionReviewInput in) {
        String workflowId = safe(in.workflowId()).toLowerCase(Locale.ROOT);
        String module     = safe(in.workflowModule()).toUpperCase(Locale.ROOT);
        String status     = safe(in.status()).toUpperCase(Locale.ROOT);
        String anchor     = safe(in.regulatoryAnchor());

        ReviewTemplate tpl = REVIEW_TEMPLATES.stream()
            .filter(t -> t.matches(workflowId, module))
            .findFirst()
            .map(ReviewMatcher::template)
            .orElse(GENERIC_REVIEW_TEMPLATE);

        String suggestedDecision = tpl.suggestedDecision();
        int conf = tpl.conf();

        if ("REJECTED".equals(status))          suggestedDecision = "CLOSE";
        else if ("APPROVED".equals(status))     suggestedDecision = "CLOSE";
        else if ("UNDER_REVIEW".equals(status)) suggestedDecision = "APPROVE";

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("headlineEn",         tpl.headlineEn());
        output.put("headlineAr",         tpl.headlineAr());
        output.put("checklistEn",        new ArrayList<>(tpl.checklistEn()));
        output.put("checklistAr",        new ArrayList<>(tpl.checklistAr()));
        output.put("regulatorCitation",  anchor.isBlank() ? null : anchor);
        output.put("suggestedDecision",  suggestedDecision);

        return persist("WORKFLOW_SUBMISSION_REVIEW", "WorkflowSubmission", in.submissionId(),
            in, output, BigDecimal.valueOf(conf),
            "Per-wizard heuristic checklist keyed off workflowId + module + status + anchor.");
    }

    private record ReviewTemplate(String headlineEn, String headlineAr,
                                  List<String> checklistEn, List<String> checklistAr,
                                  String suggestedDecision, int conf) {}

    private record ReviewMatcher(java.util.function.BiPredicate<String, String> predicate,
                                 ReviewTemplate template) {
        boolean matches(String workflowId, String module) {
            return predicate.test(workflowId, module);
        }
    }

    private static final ReviewTemplate GENERIC_REVIEW_TEMPLATE = new ReviewTemplate(
        "Review the wizard payload against the relevant Saudi regulator citation",
        "راجع حمولة المعالج وفق المرجع التنظيمي السعودي ذي الصلة",
        List.of(
            "Verify the regulatory anchor is correct for the chosen workflow.",
            "Spot-check the summary for completeness vs the payload JSON.",
            "Confirm the submitter role is in the right governance committee.",
            "Decide APPROVE / REJECT / UNDER_REVIEW with a reason note."),
        List.of(
            "تأكد من صحة المرجع التنظيمي لنوع المعالج المختار.",
            "راجع الملخص للتأكد من اكتماله مقارنة بحمولة JSON.",
            "تأكد من أن دور المُرسل ضمن لجنة الحوكمة الصحيحة.",
            "اتخذ قراراً (اعتماد/رفض/قيد المراجعة) مع سبب موثق."),
        "REVIEW", 50);

    private static final List<ReviewMatcher> REVIEW_TEMPLATES = List.of(
        new ReviewMatcher((wf, mod) -> wf.contains("pdpl-breach"), new ReviewTemplate(
            "Personal-data breach — verify the 72-hour SDAIA clock",
            "خرق بيانات شخصية — تحقق من عداد ٧٢ ساعة للهيئة",
            List.of(
                "Confirm discoveredAt + reportedToAuthority fields match the wizard input.",
                "If the breach is material, ensure SDAIA was filed within 72 hours of discovery.",
                "Spot-check whether containmentSummary references a closed crisis_event.",
                "Verify DPO approval before flipping status to NOTIFIED."),
            List.of(
                "تأكد من تطابق وقت الاكتشاف وحالة التبليغ مع مدخلات المعالج.",
                "إذا كان الخرق جوهرياً، تأكد من تبليغ الهيئة خلال ٧٢ ساعة من الاكتشاف.",
                "تحقق من ربط ملخص الاحتواء بحدث أزمة مغلق.",
                "اعتماد DPO قبل تحويل الحالة إلى مبلَّغ."),
            "APPROVE", 80)),
        new ReviewMatcher((wf, mod) -> wf.contains("nca-cyber-incident"), new ReviewTemplate(
            "NCA cyber incident — confirm severity + lessons learned",
            "حادث سيبراني NCA — تأكيد الشدة والدروس المستفادة",
            List.of(
                "Severity must align with NIST 800-61 severity scoring (1..5).",
                "All 9 lifecycle steps (detection → lessons) should have evidence linked.",
                "If NCA-reportable, verify CISO + BCM handoff happened before approval.",
                "Spot-check the lessonsLearned field — is there an actionable item?"),
            List.of(
                "الشدة يجب أن تتوافق مع تصنيف NIST 800-61 (١..٥).",
                "يجب ربط أدلة لكل خطوة من الخطوات التسعة (من الاكتشاف إلى الدروس).",
                "إذا كان قابلاً للإبلاغ، تحقق من اعتماد CISO وتسليم BCM قبل الاعتماد.",
                "راجع حقل الدروس المستفادة — هل يحتوي إجراء قابل للتنفيذ؟"),
            "APPROVE", 75)),
        new ReviewMatcher((wf, mod) -> wf.contains("erm-risk-acceptance"), new ReviewTemplate(
            "Risk acceptance — confirm above-appetite governance",
            "قبول الخطر — تأكد من حوكمة تجاوز الشهية",
            List.of(
                "If residualScore > appetiteUpper, the wizard flagged BOARD-required — verify boardSignedByName.",
                "Expiry date must be Hijri-aware; treat blank as a red flag.",
                "CRO sign-off is mandatory for HIGH-residual acceptances.",
                "Conditions field should describe the compensating control + monitoring cadence."),
            List.of(
                "إذا تجاوز الخطر المتبقي حد الشهية، فيلزم اعتماد المجلس — تحقق من الاسم.",
                "تاريخ الانتهاء يجب أن يدعم التقويم الهجري؛ الفراغ مؤشر خطر.",
                "اعتماد CRO إلزامي لمستويات المخاطر المرتفعة.",
                "حقل الشروط يجب أن يصف الضوابط المعوضة وتواتر المراقبة."),
            "REVIEW", 70)),
        new ReviewMatcher((wf, mod) -> wf.contains("pdpl-cross-border"), new ReviewTemplate(
            "Cross-border transfer — verify Saudi Data Office gating",
            "نقل عبر الحدود — تحقق من اشتراطات مكتب البيانات السعودي",
            List.of(
                "ADEQUACY / SCC / BCR / PUBLIC_INTEREST mechanisms require a SDO reference.",
                "If sensitivity ∈ {CHILDREN, BIOMETRIC} or volumeBand=GTE_1M, a DPIA must be linked.",
                "Review-period months must be ≤ 12 for STANDARD-sensitivity; ≤ 6 for sensitive.",
                "CISO sign-off needed for cross-border transfers off-Kingdom."),
            List.of(
                "آليات الكفاية / SCC / BCR / المصلحة العامة تحتاج مرجع مكتب البيانات.",
                "إذا كانت الحساسية للأطفال أو بيومترية أو ≥١ مليون شخص، يلزم DPIA مرتبط.",
                "فترة المراجعة يجب أن تكون ≤ ١٢ شهراً للعادي و ≤ ٦ للحساس.",
                "اعتماد CISO لازم للنقل خارج المملكة."),
            "REVIEW", 75)),
        new ReviewMatcher((wf, mod) -> wf.contains("governance-board-pack"), new ReviewTemplate(
            "Board pack — confirm approver chain + watermarks",
            "ملف المجلس — تأكد من سلسلة الاعتماد والعلامات المائية",
            List.of(
                "CRO + CISO + CEO approvals are non-negotiable before distribution.",
                "DPO sign-off is required if the pack includes personal-data KPIs.",
                "Watermark should be true for any audience outside the executive team.",
                "AI executive-summary must have been human-edited (aiSummaryAccepted=true)."),
            List.of(
                "اعتمادات CRO و CISO و CEO إلزامية قبل التوزيع.",
                "اعتماد DPO لازم إذا تضمن الملف مؤشرات بيانات شخصية.",
                "العلامة المائية تكون مفعّلة لأي جمهور خارج الإدارة التنفيذية.",
                "الملخص بالذكاء الاصطناعي يجب أن يكون قد روجع بشرياً."),
            "REVIEW", 65)),
        new ReviewMatcher((wf, mod) -> mod.equals("BCM"), new ReviewTemplate(
            "BCM submission — verify ISO 22301 §9.3 alignment",
            "بلاغ BCM — تأكد من توافقه مع ISO 22301 §9.3",
            List.of(
                "Exercise outcome must match RTO/RPO actual vs target.",
                "If actualRtoHours > target, a CAPA must exist.",
                "Peak-season lens (Hajj / Ramadan) flagged where relevant."),
            List.of(
                "نتيجة التمرين يجب أن تتطابق مع RTO/RPO الفعلي مقابل المستهدف.",
                "إذا تجاوز RTO المستهدف، يجب وجود CAPA.",
                "منظور موسم الذروة (الحج / رمضان) عند الاقتضاء."),
            "REVIEW", 60)));

    // ══════════════════════════════════════════════════════════════════
    // Listing + review
    // ══════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<AiSuggestion> listByFeature(String featureCode) {
        return repo.findByOrganisationIdAndFeatureCodeOrderByCreatedAtDesc(org(), featureCode);
    }

    @Transactional(readOnly = true)
    public List<AiSuggestion> listByEntity(String entity, Long entityId) {
        return repo.findByOrganisationIdAndRelatedEntityAndRelatedEntityIdOrderByCreatedAtDesc(
            org(), entity, entityId);
    }

    @Transactional
    public AiSuggestion review(Long id, ReviewBody body) {
        Long o = org();
        AiSuggestion s = repo.findById(id)
            .filter(r -> Objects.equals(r.getOrganisationId(), o))
            .orElseThrow(() -> new EntityNotFoundException("AI suggestion " + id + " not found"));
        String decision = body.decision() == null ? "" : body.decision().toUpperCase(Locale.ROOT);
        if (!"ACCEPTED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new IllegalArgumentException("decision must be ACCEPTED or REJECTED");
        }
        s.setStatus(decision);
        s.setReviewedAt(LocalDateTime.now());
        s.setReviewerNote(body.reviewerNote());
        AiSuggestion saved = repo.save(s);
        audit.log(decision, "AiSuggestion", id,
            "AI suggestion " + s.getFeatureCode() + " " + decision.toLowerCase(Locale.ROOT));
        return saved;
    }

    // ── helpers ──────────────────────────────────────────────────────

    /**
     * Saves the suggestion and its DRAFT audit row in one transaction. Every caller is a
     * generator in this class that keeps the model call outside any transaction (PERF-IO-003),
     * so this is always a self-call and an {@code @Transactional} here never applied: the two
     * writes committed separately. The template opens the boundary itself.
     */
    public AiSuggestion persist(String feature, String entity, Long entityId,
                                Object input, Object output,
                                BigDecimal confidence, String rationale) {
        return new TransactionTemplate(txManager).execute(status ->
            saveWithAudit(feature, entity, entityId, input, output, confidence, rationale));
    }

    private AiSuggestion saveWithAudit(String feature, String entity, Long entityId,
                                       Object input, Object output,
                                       BigDecimal confidence, String rationale) {
        AiSuggestion s = new AiSuggestion();
        s.setOrganisationId(org());
        s.setFeatureCode(feature);
        s.setRelatedEntity(entity);
        s.setRelatedEntityId(entityId);
        try {
            s.setInputJson(mapper.writeValueAsString(input));
            s.setOutputJson(mapper.writeValueAsString(output));
        } catch (Exception ex) {
            log.warn("Failed to serialise AI payload for {}: {}", feature, ex.getMessage());
            s.setInputJson("{}");
            s.setOutputJson("{\"error\":\"serialisation_failed\"}");
        }
        s.setConfidence(confidence);
        s.setRationale(rationale);
        AiSuggestion saved = repo.save(s);
        audit.log("DRAFT", "AiSuggestion", saved.getId(),
            "AI suggestion drafted: feature=" + feature
            + ", entity=" + entity + "#" + entityId
            + ", confidence=" + confidence);
        return saved;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty LLM response");
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }
        try {
            return mapper.readValue(cleaned, Map.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse JSON map from LLM response: " + ex.getMessage(), ex);
        }
    }

    private static BigDecimal extractConfidence(Map<String, Object> map, int defaultVal) {
        Object confObj = map.get("confidence");
        if (confObj instanceof Number num) {
            double d = num.doubleValue();
            d = Math.max(0.0, Math.min(100.0, d));
            return BigDecimal.valueOf(Math.round(d * 100.0) / 100.0);
        }
        return BigDecimal.valueOf(defaultVal);
    }

    private static String extractRationale(Map<String, Object> map, String defaultRationale) {
        Object ratObj = map.get("rationale");
        if (ratObj instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return defaultRationale;
    }

    private static String safe(String s)   { return s == null ? "" : s; }
    private static int nullSafe(Integer i) { return i == null ? 0 : i; }

    private static String humanDuration(int minutes) {
        if (minutes < 60)      return minutes + " minutes";
        if (minutes < 60 * 24) return (minutes / 60) + " hours";
        return (minutes / (60 * 24)) + " days";
    }
    private static String humanDurationAr(int minutes) {
        if (minutes < 60)      return minutes + " دقيقة";
        if (minutes < 60 * 24) return (minutes / 60) + " ساعة";
        return (minutes / (60 * 24)) + " يوم";
    }
}

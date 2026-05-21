package com.tss.grc.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ai.entity.AiSuggestion;
import com.tss.grc.module.ai.repository.AiSuggestionRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AI-assisted features (Saudi GRC implementation prompt §27).
 *
 * <p>Five heuristic generators, each persisted as an {@link AiSuggestion}
 * draft so a human can accept or reject the suggestion before it changes
 * any real record. The shape is identical to an LLM-backed implementation —
 * swap the private generator methods later without touching the API.
 */
@Service
@RequiredArgsConstructor
public class AiSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiSuggestionService.class);

    private final AiSuggestionRepository repo;
    private final AuditLogService        audit;
    private final ObjectMapper           mapper = new ObjectMapper();

    private Long org() { return TenantContext.getCurrentTenant(); }

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

    public record ReviewBody(String decision, String reviewerNote) {}

    // ══════════════════════════════════════════════════════════════════
    // §27 #1 — Risk description draft
    // ══════════════════════════════════════════════════════════════════
    @Transactional
    public AiSuggestion suggestRiskDescription(RiskDescriptionInput in) {
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
        if (in.categoryCode() != null && in.categoryCode().toUpperCase().contains("CYBER")) {
            causes.add("Threat-actor exploitation of a known vulnerability");
            consequences.add("Data exfiltration triggering PDPL Article 23 (72-hour notification)");
        }
        if (in.categoryCode() != null && in.categoryCode().toUpperCase().contains("PRIVACY")) {
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
            BigDecimal.valueOf(conf),
            "Heuristic expansion using title + category. Replace with LLM-backed version when AI gateway is wired.");
    }

    // ══════════════════════════════════════════════════════════════════
    // §27 #3 — BIA RTO/RPO recommendation
    // ══════════════════════════════════════════════════════════════════
    @Transactional
    public AiSuggestion suggestBiaRtoRpo(BiaRecommendationInput in) {
        // Mapping derived from typical Saudi service tiers + NCEMA expectations.
        // CRITICAL service in Tier-1 → RTO ≤ 4h, RPO ≤ 15min.
        // ESSENTIAL service in Tier-2 → RTO ≤ 24h, RPO ≤ 4h.
        // IMPORTANT / Tier-3 → RTO ≤ 72h, RPO ≤ 24h.
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
        output.put("mtpdMinutes", rtoMinutes * 3);  // Max tolerable period of disruption rule-of-thumb
        return persist("BIA_RTO_RPO", "Service", in.serviceId(), in, output,
            BigDecimal.valueOf(70), rationale);
    }

    // ══════════════════════════════════════════════════════════════════
    // §27 #8 — Evidence quality review
    // ══════════════════════════════════════════════════════════════════
    @Transactional
    public AiSuggestion reviewEvidenceQuality(EvidenceQualityInput in) {
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
    @Transactional
    public AiSuggestion draftIncidentRootCause(IncidentRootCauseInput in) {
        // 5-Whys-style narrative built from inputs.
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
        if (in.severity() != null && in.severity() >= 4) conf -= 10; // major crises need real RCA
        if (in.sitRepHighlights() != null) conf += Math.min(20, in.sitRepHighlights().size() * 4);

        return persist("INCIDENT_ROOT_CAUSE", "CrisisEvent", in.crisisEventId(), in, output,
            BigDecimal.valueOf(conf),
            "5-Whys template stitched from event metadata + sitrep highlights. Always pair with human RCA.");
    }

    // ══════════════════════════════════════════════════════════════════
    // §27 #11 — Board summary drafting
    // ══════════════════════════════════════════════════════════════════
    @Transactional
    public AiSuggestion draftBoardSummary(BoardSummaryInput in) {
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
            "Thresholded rollup over six dashboard inputs. Replace with LLM gateway for free-form summary.");
    }

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

    private AiSuggestion persist(String feature, String entity, Long entityId,
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

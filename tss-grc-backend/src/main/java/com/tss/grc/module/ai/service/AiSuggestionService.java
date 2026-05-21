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
    // §27 #12 — Workflow-submission review hint (PR #23)
    // ══════════════════════════════════════════════════════════════════
    //
    // PRs #11-22 built the §7 wizard surface end-to-end (catalog → wizards
    // → persistence → register → detail → audit → F8 automation). This
    // heuristic adds AI guidance per submission: when a reviewer opens
    // /workflows/submissions/:id, the frontend calls this to draft what
    // they should check + which controls to spot-verify + which Saudi
    // regulator citation matters.
    //
    // Output shape (consumed by WorkflowSubmissionDetailPage):
    //   { headlineEn, headlineAr, checklistEn[], checklistAr[],
    //     regulatorCitation, suggestedDecision }

    @Transactional
    public AiSuggestion suggestWorkflowSubmissionReview(WorkflowSubmissionReviewInput in) {
        String workflowId = safe(in.workflowId()).toLowerCase(Locale.ROOT);
        String module     = safe(in.workflowModule()).toUpperCase(Locale.ROOT);
        String status     = safe(in.status()).toUpperCase(Locale.ROOT);
        String anchor     = safe(in.regulatoryAnchor());

        // Per-wizard tailored checklists. Each branch builds the
        // checklistEn + checklistAr lists keyed off the specific
        // regulator expectations for that wizard.
        List<String> checklistEn = new ArrayList<>();
        List<String> checklistAr = new ArrayList<>();
        String headlineEn;
        String headlineAr;
        String suggestedDecision = "REVIEW";
        int conf = 60;

        if (workflowId.contains("pdpl-breach")) {
            headlineEn = "Personal-data breach — verify the 72-hour SDAIA clock";
            headlineAr = "خرق بيانات شخصية — تحقق من عداد ٧٢ ساعة للهيئة";
            checklistEn.add("Confirm discoveredAt + reportedToAuthority fields match the wizard input.");
            checklistEn.add("If the breach is material, ensure SDAIA was filed within 72 hours of discovery.");
            checklistEn.add("Spot-check whether containmentSummary references a closed crisis_event.");
            checklistEn.add("Verify DPO approval before flipping status to NOTIFIED.");
            checklistAr.add("تأكد من تطابق وقت الاكتشاف وحالة التبليغ مع مدخلات المعالج.");
            checklistAr.add("إذا كان الخرق جوهرياً، تأكد من تبليغ الهيئة خلال ٧٢ ساعة من الاكتشاف.");
            checklistAr.add("تحقق من ربط ملخص الاحتواء بحدث أزمة مغلق.");
            checklistAr.add("اعتماد DPO قبل تحويل الحالة إلى مبلَّغ.");
            suggestedDecision = "APPROVE";
            conf = 80;
        } else if (workflowId.contains("nca-cyber-incident")) {
            headlineEn = "NCA cyber incident — confirm severity + lessons learned";
            headlineAr = "حادث سيبراني NCA — تأكيد الشدة والدروس المستفادة";
            checklistEn.add("Severity must align with NIST 800-61 severity scoring (1..5).");
            checklistEn.add("All 9 lifecycle steps (detection → lessons) should have evidence linked.");
            checklistEn.add("If NCA-reportable, verify CISO + BCM handoff happened before approval.");
            checklistEn.add("Spot-check the lessonsLearned field — is there an actionable item?");
            checklistAr.add("الشدة يجب أن تتوافق مع تصنيف NIST 800-61 (١..٥).");
            checklistAr.add("يجب ربط أدلة لكل خطوة من الخطوات التسعة (من الاكتشاف إلى الدروس).");
            checklistAr.add("إذا كان قابلاً للإبلاغ، تحقق من اعتماد CISO وتسليم BCM قبل الاعتماد.");
            checklistAr.add("راجع حقل الدروس المستفادة — هل يحتوي إجراء قابل للتنفيذ؟");
            suggestedDecision = "APPROVE";
            conf = 75;
        } else if (workflowId.contains("erm-risk-acceptance")) {
            headlineEn = "Risk acceptance — confirm above-appetite governance";
            headlineAr = "قبول الخطر — تأكد من حوكمة تجاوز الشهية";
            checklistEn.add("If residualScore > appetiteUpper, the wizard flagged BOARD-required — verify boardSignedByName.");
            checklistEn.add("Expiry date must be Hijri-aware; treat blank as a red flag.");
            checklistEn.add("CRO sign-off is mandatory for HIGH-residual acceptances.");
            checklistEn.add("Conditions field should describe the compensating control + monitoring cadence.");
            checklistAr.add("إذا تجاوز الخطر المتبقي حد الشهية، فيلزم اعتماد المجلس — تحقق من الاسم.");
            checklistAr.add("تاريخ الانتهاء يجب أن يدعم التقويم الهجري؛ الفراغ مؤشر خطر.");
            checklistAr.add("اعتماد CRO إلزامي لمستويات المخاطر المرتفعة.");
            checklistAr.add("حقل الشروط يجب أن يصف الضوابط المعوضة وتواتر المراقبة.");
            conf = 70;
        } else if (workflowId.contains("pdpl-cross-border")) {
            headlineEn = "Cross-border transfer — verify Saudi Data Office gating";
            headlineAr = "نقل عبر الحدود — تحقق من اشتراطات مكتب البيانات السعودي";
            checklistEn.add("ADEQUACY / SCC / BCR / PUBLIC_INTEREST mechanisms require a SDO reference.");
            checklistEn.add("If sensitivity ∈ {CHILDREN, BIOMETRIC} or volumeBand=GTE_1M, a DPIA must be linked.");
            checklistEn.add("Review-period months must be ≤ 12 for STANDARD-sensitivity; ≤ 6 for sensitive.");
            checklistEn.add("CISO sign-off needed for cross-border transfers off-Kingdom.");
            checklistAr.add("آليات الكفاية / SCC / BCR / المصلحة العامة تحتاج مرجع مكتب البيانات.");
            checklistAr.add("إذا كانت الحساسية للأطفال أو بيومترية أو ≥١ مليون شخص، يلزم DPIA مرتبط.");
            checklistAr.add("فترة المراجعة يجب أن تكون ≤ ١٢ شهراً للعادي و ≤ ٦ للحساس.");
            checklistAr.add("اعتماد CISO لازم للنقل خارج المملكة.");
            conf = 75;
        } else if (workflowId.contains("governance-board-pack")) {
            headlineEn = "Board pack — confirm approver chain + watermarks";
            headlineAr = "ملف المجلس — تأكد من سلسلة الاعتماد والعلامات المائية";
            checklistEn.add("CRO + CISO + CEO approvals are non-negotiable before distribution.");
            checklistEn.add("DPO sign-off is required if the pack includes personal-data KPIs.");
            checklistEn.add("Watermark should be true for any audience outside the executive team.");
            checklistEn.add("AI executive-summary must have been human-edited (aiSummaryAccepted=true).");
            checklistAr.add("اعتمادات CRO و CISO و CEO إلزامية قبل التوزيع.");
            checklistAr.add("اعتماد DPO لازم إذا تضمن الملف مؤشرات بيانات شخصية.");
            checklistAr.add("العلامة المائية تكون مفعّلة لأي جمهور خارج الإدارة التنفيذية.");
            checklistAr.add("الملخص بالذكاء الاصطناعي يجب أن يكون قد روجع بشرياً.");
            conf = 65;
        } else if (module.equals("BCM")) {
            headlineEn = "BCM submission — verify ISO 22301 §9.3 alignment";
            headlineAr = "بلاغ BCM — تأكد من توافقه مع ISO 22301 §9.3";
            checklistEn.add("Exercise outcome must match RTO/RPO actual vs target.");
            checklistEn.add("If actualRtoHours > target, a CAPA must exist.");
            checklistEn.add("Peak-season lens (Hajj / Ramadan) flagged where relevant.");
            checklistAr.add("نتيجة التمرين يجب أن تتطابق مع RTO/RPO الفعلي مقابل المستهدف.");
            checklistAr.add("إذا تجاوز RTO المستهدف، يجب وجود CAPA.");
            checklistAr.add("منظور موسم الذروة (الحج / رمضان) عند الاقتضاء.");
            conf = 60;
        } else {
            // Generic fallback — every submission deserves at least these.
            headlineEn = "Review the wizard payload against the relevant Saudi regulator citation";
            headlineAr = "راجع حمولة المعالج وفق المرجع التنظيمي السعودي ذي الصلة";
            checklistEn.add("Verify the regulatory anchor is correct for the chosen workflow.");
            checklistEn.add("Spot-check the summary for completeness vs the payload JSON.");
            checklistEn.add("Confirm the submitter role is in the right governance committee.");
            checklistEn.add("Decide APPROVE / REJECT / UNDER_REVIEW with a reason note.");
            checklistAr.add("تأكد من صحة المرجع التنظيمي لنوع المعالج المختار.");
            checklistAr.add("راجع الملخص للتأكد من اكتماله مقارنة بحمولة JSON.");
            checklistAr.add("تأكد من أن دور المُرسل ضمن لجنة الحوكمة الصحيحة.");
            checklistAr.add("اتخذ قراراً (اعتماد/رفض/قيد المراجعة) مع سبب موثق.");
            conf = 50;
        }

        // Status-dependent suggestion.
        if ("REJECTED".equals(status))        suggestedDecision = "CLOSE";
        else if ("APPROVED".equals(status))   suggestedDecision = "CLOSE";
        else if ("UNDER_REVIEW".equals(status)) suggestedDecision = "APPROVE";

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("headlineEn",         headlineEn);
        output.put("headlineAr",         headlineAr);
        output.put("checklistEn",        checklistEn);
        output.put("checklistAr",        checklistAr);
        output.put("regulatorCitation",  anchor.isBlank() ? null : anchor);
        output.put("suggestedDecision",  suggestedDecision);

        return persist("WORKFLOW_SUBMISSION_REVIEW", "WorkflowSubmission", in.submissionId(),
            in, output, BigDecimal.valueOf(conf),
            "Per-wizard heuristic checklist keyed off workflowId + module + status + anchor. "
            + "Swap for an LLM-backed reviewer assistant when the AI gateway is wired.");
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

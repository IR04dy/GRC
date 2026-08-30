package com.tss.grc.module.tprm.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.validation.ServerOwnedState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.tprm.entity.QuestionnaireQuestion;
import com.tss.grc.module.tprm.entity.QuestionnaireResponse;
import com.tss.grc.module.tprm.entity.QuestionnaireTemplate;
import com.tss.grc.module.tprm.entity.VendorAssessment;
import com.tss.grc.module.tprm.repository.QuestionnaireQuestionRepository;
import com.tss.grc.module.tprm.repository.QuestionnaireResponseRepository;
import com.tss.grc.module.tprm.repository.QuestionnaireTemplateRepository;
import com.tss.grc.module.tprm.repository.VendorAssessmentRepository;
import com.tss.grc.module.tprm.repository.VendorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * TPRM Questionnaire engine — V81.
 *
 * <p>Owns three resources and the scoring math that turns answered
 * questions into a deterministic 0..100 risk score on the parent
 * VendorAssessment.
 *
 * <p>Template lifecycle: DRAFT → ACTIVE → ARCHIVED. Questions can
 * only be added/edited/deleted while the parent template is DRAFT.
 * Once ACTIVE, the question set is frozen so historical assessments
 * remain reproducible.
 *
 * <p>Scoring rubric:
 * <ul>
 *   <li>Each non-NA, mandatory + optional response contributes
 *       {@code scoredPoints × weight} to the numerator and
 *       {@code maxPoints × weight} to the denominator.</li>
 *   <li>Final score (%) = numerator / denominator × 100, rounded
 *       to 2 decimals.</li>
 *   <li>Risk rating band derived from template thresholds:
 *       ≤low_max → LOW; ≤medium_max → MEDIUM; ≤high_max → HIGH;
 *       otherwise → CRITICAL.</li>
 *   <li>Higher score = better posture by default. Set
 *       {@code invertScore = true} on a question to flip its
 *       contribution (useful for reverse-coded items).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class QuestionnaireService {

    private static final Logger log = LoggerFactory.getLogger(QuestionnaireService.class);

    /**
     * F2-220 — the state a template is CREATED in; ACTIVE is transitionTemplate()'s, which
     * refuses to activate a template with no questions.
     *
     * <p>⚠ {@code TEMPLATE_STATUSES}, the DRAFT/ACTIVE/ARCHIVED set that stood here, is gone
     * rather than left unread. Its only reader was the create's membership test — the very check
     * that admitted ACTIVE — and {@code transitionTemplate}'s switch is exhaustive, so an
     * unrecognised target already throws through {@code default -> false}. An inert construct is
     * worse than none, because it answers the question a reader was about to ask (F2-184). The
     * vocabulary is stated by the machine and by {@code ck_qt_status} on the column.
     */
    private static final String INITIAL_TEMPLATE_STATUS = "DRAFT";
    private static final Set<String> ANSWER_TYPES = Set.of("SCALE_1_5", "BOOLEAN", "SELECT", "TEXT");

    private final QuestionnaireTemplateRepository templates;
    private final QuestionnaireQuestionRepository questions;
    private final QuestionnaireResponseRepository responses;
    private final VendorAssessmentRepository assessments;
    private final VendorRepository vendorRepo;
    private final ObjectMapper objectMapper;

    private Long org() { return TenantContext.getCurrentTenant(); }

    /**
     * Load a {@link VendorAssessment} scoped to the caller's tenant (SEC-TPRM-002).
     *
     * <p>VendorAssessment carries no organisation_id, so tenant isolation is
     * derived through its parent vendor. We assert the vendor exists in the
     * current org via the org-scoped finder; a miss throws EntityNotFoundException
     * (→ 404), giving the same "not found" surface as a non-existent assessment
     * and preventing cross-tenant workflow/score mutation.
     */
    private VendorAssessment loadAssessmentInTenant(Long assessmentId) {
        VendorAssessment va = assessments.findById(assessmentId)
            .orElseThrow(() -> new EntityNotFoundException("VendorAssessment not found: " + assessmentId));
        vendorRepo.findByIdAndOrganisationId(va.getVendorId(), org())
            .orElseThrow(() -> new EntityNotFoundException("VendorAssessment not found: " + assessmentId));
        return va;
    }

    // ─── Templates ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QuestionnaireTemplate> listTemplates(String status) {
        Long o = org();
        return status != null
            ? templates.findByOrganisationIdAndStatusOrderByTemplateCodeAscVersionDesc(o, status)
            : templates.findByOrganisationIdOrderByTemplateCodeAscVersionDesc(o);
    }

    @Transactional(readOnly = true)
    public QuestionnaireTemplate getTemplate(Long id) {
        return templates.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Template not found: " + id));
    }

    @Transactional
    public QuestionnaireTemplate createTemplate(QuestionnaireTemplate body) {
        if (body.getTemplateCode() == null || body.getTemplateCode().isBlank())
            throw new IllegalArgumentException("templateCode is required");
        if (body.getNameEn() == null || body.getNameEn().isBlank())
            throw new IllegalArgumentException("nameEn is required");
        if (body.getVersion() == null) body.setVersion("1.0");
        if (templates.findByOrganisationIdAndTemplateCodeAndVersion(org(), body.getTemplateCode(), body.getVersion()).isPresent())
            throw new IllegalArgumentException("Template already exists: " + body.getTemplateCode() + " v" + body.getVersion());
        body.setId(null);
        body.setOrganisationId(org());
        // F2-220 — a DEFAULT, not a guard: the membership test admitted ACTIVE, so a template
        // could be created active with zero questions — the exact case transitionTemplate()
        // refuses ("Cannot activate a template with no questions") — and then be sent to
        // vendors as an assessment. activatedAt was never stamped either.
        body.setStatus(ServerOwnedState.requireInitial(
            body.getStatus(), INITIAL_TEMPLATE_STATUS, "a questionnaire template",
            "POST /api/v1/tprm/questionnaire-templates/{id}/transition"));
        body.setActivatedAt(null);
        body.setArchivedAt(null);
        validateThresholdOrder(body.getThresholdLowMax(), body.getThresholdMediumMax(), body.getThresholdHighMax());
        return templates.save(body);
    }

    @Transactional
    public QuestionnaireTemplate updateTemplate(Long id, QuestionnaireTemplate body) {
        QuestionnaireTemplate t = getTemplate(id);
        // Once ACTIVE, only descriptive metadata can change — question set + thresholds frozen.
        if (!"DRAFT".equals(t.getStatus())) {
            if (body.getNameEn() != null)         t.setNameEn(body.getNameEn());
            if (body.getNameAr() != null)         t.setNameAr(body.getNameAr());
            if (body.getDescriptionEn() != null)  t.setDescriptionEn(body.getDescriptionEn());
            if (body.getDescriptionAr() != null)  t.setDescriptionAr(body.getDescriptionAr());
            if (body.getNotes() != null)          t.setNotes(body.getNotes());
            return templates.save(t);
        }
        if (body.getNameEn() != null)             t.setNameEn(body.getNameEn());
        if (body.getNameAr() != null)             t.setNameAr(body.getNameAr());
        if (body.getDescriptionEn() != null)      t.setDescriptionEn(body.getDescriptionEn());
        if (body.getDescriptionAr() != null)      t.setDescriptionAr(body.getDescriptionAr());
        if (body.getCategory() != null)           t.setCategory(body.getCategory());
        if (body.getApplicableTo() != null)       t.setApplicableTo(body.getApplicableTo());
        if (body.getThresholdLowMax() != null)    t.setThresholdLowMax(body.getThresholdLowMax());
        if (body.getThresholdMediumMax() != null) t.setThresholdMediumMax(body.getThresholdMediumMax());
        if (body.getThresholdHighMax() != null)   t.setThresholdHighMax(body.getThresholdHighMax());
        if (body.getNotes() != null)              t.setNotes(body.getNotes());
        validateThresholdOrder(t.getThresholdLowMax(), t.getThresholdMediumMax(), t.getThresholdHighMax());
        return templates.save(t);
    }

    /**
     * F2-398 — a template's LOW/MEDIUM/HIGH thresholds must be non-decreasing for {@link #bandFor}
     * to produce correct risk bands: it tests {@code pct <= low ? LOW : pct <= medium ? MEDIUM :
     * pct <= high ? HIGH : CRITICAL}, so an inverted set (e.g. low 80 &gt; medium 60 &gt; high 30)
     * bands a genuinely CRITICAL vendor LOW (a pct of 70 → {@code 70 <= 80} → LOW). Neither
     * {@code createTemplate} nor {@code updateTemplate} checked it. Validates the MERGED thresholds
     * so a DRAFT partial update touching only one cannot invert against the stored others (F2-292).
     *
     * <p>Non-strict ({@code <=}, not the strict {@code <} of the 2-band RRP/KRI twins F2-396/F2-397):
     * a 3-band template's correctness needs only non-decreasing thresholds, and a metadata-only
     * update of a DRAFT template with a degenerate-but-coherent equal pair (an empty band) must not
     * be rejected (F2-214). Null-guarded — the entity defaults null thresholds to 30/60/80 at persist.
     * Mirrors the house pattern (VulnerabilitySlaPolicyService.validatePctOrder, F2-396/F2-397).
     */
    static void validateThresholdOrder(Integer low, Integer medium, Integer high) {
        if (low == null || medium == null || high == null) return;   // entity defaults 30/60/80 apply
        if (low > medium || medium > high) {
            throw new IllegalArgumentException(
                    "thresholds must be non-decreasing (thresholdLowMax <= thresholdMediumMax <= "
                            + "thresholdHighMax), got " + low + " / " + medium + " / " + high);
        }
    }

    /**
     * The register's declared edges, in ONE place. F2-303 — this was an inline
     * {@code switch} of {@code Set.of(...).contains(...)}, so the legal-target list was not
     * derivable at runtime and the refusal below could only say which move was rejected, never
     * which moves would be accepted. Terminal states are present with an <b>empty</b> set rather
     * than omitted, so the terminal set is derivable from the machine instead of typed out a
     * second time ([[F2-196]]).
     */
    static final Map<String, Set<String>> TEMPLATE_TRANSITIONS = Map.of(
        "DRAFT",    Set.of("ACTIVE", "ARCHIVED"),
        "ACTIVE",   Set.of("ARCHIVED"),
        "ARCHIVED", Set.of());

    @Transactional
    public QuestionnaireTemplate transitionTemplate(Long id, String to) {
        QuestionnaireTemplate t = getTemplate(id);
        String from = t.getStatus();
        Set<String> allowed = TEMPLATE_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) throw new IllegalTransitionException(from, to, allowed);
        if ("ACTIVE".equals(to)) {
            int qCount = questions.findByOrganisationIdAndTemplateIdOrderBySequenceOrderAsc(org(), id).size();
            if (qCount == 0) throw new IllegalArgumentException("Cannot activate a template with no questions");
            t.setActivatedAt(LocalDateTime.now());
        }
        if ("ARCHIVED".equals(to)) t.setArchivedAt(LocalDateTime.now());
        t.setStatus(to);
        return templates.save(t);
    }

    // ─── Questions ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QuestionnaireQuestion> listQuestions(Long templateId) {
        getTemplate(templateId);  // tenant guard
        return questions.findByOrganisationIdAndTemplateIdOrderBySequenceOrderAsc(org(), templateId);
    }

    @Transactional
    public QuestionnaireQuestion createQuestion(Long templateId, QuestionnaireQuestion body) {
        QuestionnaireTemplate t = getTemplate(templateId);
        if (!"DRAFT".equals(t.getStatus()))
            throw new IllegalStateException("Cannot add questions to a " + t.getStatus() + " template; clone to a new version first");
        if (body.getQuestionCode() == null || body.getQuestionCode().isBlank())
            throw new IllegalArgumentException("questionCode is required");
        if (body.getQuestionEn() == null) throw new IllegalArgumentException("questionEn is required");
        if (body.getAnswerType() == null) body.setAnswerType("SCALE_1_5");
        if (!ANSWER_TYPES.contains(body.getAnswerType()))
            throw new IllegalArgumentException("Invalid answerType: " + body.getAnswerType());
        body.setId(null);
        body.setOrganisationId(org());
        body.setTemplateId(templateId);
        return questions.save(body);
    }

    @Transactional
    public QuestionnaireQuestion updateQuestion(Long id, QuestionnaireQuestion body) {
        QuestionnaireQuestion q = questions.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Question not found: " + id));
        QuestionnaireTemplate t = getTemplate(q.getTemplateId());
        if (!"DRAFT".equals(t.getStatus()))
            throw new IllegalStateException("Cannot edit questions on a " + t.getStatus() + " template");
        if (body.getSectionCode() != null)    q.setSectionCode(body.getSectionCode());
        if (body.getSectionNameEn() != null)  q.setSectionNameEn(body.getSectionNameEn());
        if (body.getSectionNameAr() != null)  q.setSectionNameAr(body.getSectionNameAr());
        if (body.getQuestionEn() != null)     q.setQuestionEn(body.getQuestionEn());
        if (body.getQuestionAr() != null)     q.setQuestionAr(body.getQuestionAr());
        if (body.getAnswerType() != null)     q.setAnswerType(body.getAnswerType());
        if (body.getOptionsJson() != null)    q.setOptionsJson(body.getOptionsJson());
        if (body.getWeight() != null)         q.setWeight(body.getWeight());
        if (body.getInvertScore() != null)    q.setInvertScore(body.getInvertScore());
        if (body.getMandatory() != null)      q.setMandatory(body.getMandatory());
        if (body.getSequenceOrder() != null)  q.setSequenceOrder(body.getSequenceOrder());
        if (body.getGuidanceEn() != null)     q.setGuidanceEn(body.getGuidanceEn());
        if (body.getGuidanceAr() != null)     q.setGuidanceAr(body.getGuidanceAr());
        return questions.save(q);
    }

    @Transactional
    public void deleteQuestion(Long id) {
        QuestionnaireQuestion q = questions.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Question not found: " + id));
        QuestionnaireTemplate t = getTemplate(q.getTemplateId());
        if (!"DRAFT".equals(t.getStatus()))
            throw new IllegalStateException("Cannot delete questions on a " + t.getStatus() + " template");
        q.setIsDeleted(true);
        questions.save(q);
    }

    // ─── Assignment + responses ───────────────────────────────────

    /** Assign a template to a vendor assessment — initialises totals + workflow_status. */
    @Transactional
    public VendorAssessment assignTemplate(Long assessmentId, Long templateId) {
        Long o = org();
        VendorAssessment va = loadAssessmentInTenant(assessmentId);
        QuestionnaireTemplate t = getTemplate(templateId);
        if (!"ACTIVE".equals(t.getStatus()))
            throw new IllegalStateException("Can only assign an ACTIVE template (was " + t.getStatus() + ")");
        List<QuestionnaireQuestion> qs = questions.findByOrganisationIdAndTemplateIdOrderBySequenceOrderAsc(o, templateId);
        va.setTemplateId(templateId);
        va.setTotalQuestions(qs.size());
        va.setAnsweredCount(0);
        va.setWorkflowStatus("IN_PROGRESS");
        va.setComputedScorePct(null);
        return assessments.save(va);
    }

    @Transactional(readOnly = true)
    public List<QuestionnaireResponse> listResponses(Long assessmentId) {
        return responses.findByOrganisationIdAndVendorAssessmentIdOrderByQuestionId(org(), assessmentId);
    }

    /**
     * Save (create or update) a response. Computes scored_points +
     * weighted_points from the answer, then re-rolls the parent
     * assessment's score / rating / progress.
     */
    @Transactional
    public QuestionnaireResponse upsertResponse(Long assessmentId, QuestionnaireResponse body, String actor) {
        Long o = org();
        VendorAssessment va = loadAssessmentInTenant(assessmentId);
        if (body.getQuestionId() == null) throw new IllegalArgumentException("questionId is required");
        QuestionnaireQuestion q = questions.findByIdAndOrganisationId(body.getQuestionId(), o)
            .orElseThrow(() -> new EntityNotFoundException("Question not found: " + body.getQuestionId()));
        if (va.getTemplateId() != null && !Objects.equals(va.getTemplateId(), q.getTemplateId()))
            throw new IllegalArgumentException("Question " + q.getId() + " is on a different template than the assessment");

        QuestionnaireResponse existing = responses
            .findByOrganisationIdAndVendorAssessmentIdAndQuestionId(o, assessmentId, q.getId())
            .orElse(null);
        QuestionnaireResponse r = (existing != null) ? existing : new QuestionnaireResponse();
        r.setOrganisationId(o);
        r.setVendorAssessmentId(assessmentId);
        r.setQuestionId(q.getId());
        r.setTemplateId(q.getTemplateId());
        if (body.getAnswerValue() != null)     r.setAnswerValue(body.getAnswerValue());
        if (body.getAnswerNotesEn() != null)   r.setAnswerNotesEn(body.getAnswerNotesEn());
        if (body.getAnswerNotesAr() != null)   r.setAnswerNotesAr(body.getAnswerNotesAr());
        if (body.getEvidenceRef() != null)     r.setEvidenceRef(body.getEvidenceRef());
        if (body.getNotApplicable() != null)   r.setNotApplicable(body.getNotApplicable());
        if (body.getNaJustification() != null) r.setNaJustification(body.getNaJustification());
        if (body.getScoredPoints() != null)    r.setScoredPoints(body.getScoredPoints());  // manual override (TEXT)

        BigDecimal maxPoints = maxPointsFor(q);
        r.setMaxPoints(maxPoints);

        // F2-22b(iv) — bound the manual override.
        //
        // computeScoredPoints() validates every DERIVED path with care (SCALE_1_5 must be 1..5,
        // BOOLEAN must be a known token, SELECT must be a listed option), but the manual override
        // above skipped all of it: whatever number arrived was stored, while maxPoints came from
        // the QUESTION. Live-proven — scoredPoints=5 on a BOOLEAN question (max 1.00) gave a 500%
        // posture, so recomputeAssessment() produced computedScorePct = -400.00 and bandFor()
        // duly called it LOW, because -400 ≤ thresholdLowMax.
        //
        // This matters more than an out-of-range number in a column: it is the same defect as the
        // one this tranche closes, one layer in. Making computedScorePct READ_ONLY stops a caller
        // asserting the score directly, but it is worth nothing if the sanctioned path can drive
        // the same score anywhere. A derivation is only as trustworthy as its inputs.
        if (r.getScoredPoints() != null
            && (r.getScoredPoints().compareTo(BigDecimal.ZERO) < 0
                || r.getScoredPoints().compareTo(maxPoints) > 0)) {
            throw new IllegalArgumentException(
                "scoredPoints must be between 0 and " + maxPoints.stripTrailingZeros().toPlainString()
                + " for question " + q.getQuestionCode() + " (answerType " + q.getAnswerType()
                + "), received " + r.getScoredPoints().stripTrailingZeros().toPlainString()
                + ". An out-of-range answer skews the whole assessment: the weighted total is "
                + "divided by the questions' maxPoints, so over-scoring one answer moves "
                + "computedScorePct outside 0..100 and mis-bands the risk rating.");
        }

        if (Boolean.TRUE.equals(r.getNotApplicable())) {
            if (r.getNaJustification() == null || r.getNaJustification().isBlank())
                throw new IllegalArgumentException("naJustification is required when notApplicable = true");
            r.setScoredPoints(null);
            r.setWeightedPoints(null);
        } else {
            BigDecimal scored = (r.getScoredPoints() != null)
                ? r.getScoredPoints()
                : computeScoredPoints(q, r.getAnswerValue());
            r.setScoredPoints(scored);
            if (scored != null) {
                BigDecimal effective = Boolean.TRUE.equals(q.getInvertScore())
                    ? maxPoints.subtract(scored)
                    : scored;
                r.setWeightedPoints(effective.multiply(q.getWeight()).setScale(2, RoundingMode.HALF_UP));
            } else {
                r.setWeightedPoints(null);
            }
        }
        r.setAnsweredBy(actor);
        r.setAnsweredAt(LocalDateTime.now());

        QuestionnaireResponse saved = responses.save(r);
        recomputeAssessment(va);
        return saved;
    }

    /** Mark the assessment COMPLETED — refuses if any mandatory question is unanswered. */
    @Transactional
    public VendorAssessment markComplete(Long assessmentId) {
        Long o = org();
        VendorAssessment va = loadAssessmentInTenant(assessmentId);
        if (va.getTemplateId() == null)
            throw new IllegalStateException("Assessment has no template assigned");
        List<QuestionnaireQuestion> mandatory = questions
            .findByOrganisationIdAndTemplateIdOrderBySequenceOrderAsc(o, va.getTemplateId()).stream()
            .filter(QuestionnaireQuestion::getMandatory).toList();
        List<QuestionnaireResponse> rs = responses.findByOrganisationIdAndVendorAssessmentIdOrderByQuestionId(o, assessmentId);
        for (QuestionnaireQuestion q : mandatory) {
            Optional<QuestionnaireResponse> r = rs.stream()
                .filter(x -> Objects.equals(x.getQuestionId(), q.getId())).findFirst();
            if (r.isEmpty() || (r.get().getScoredPoints() == null && !Boolean.TRUE.equals(r.get().getNotApplicable())))
                throw new IllegalStateException("Mandatory question " + q.getQuestionCode() + " is unanswered");
        }
        recomputeAssessment(va);
        va.setWorkflowStatus("COMPLETED");
        return assessments.save(va);
    }

    @Transactional
    public VendorAssessment markReviewed(Long assessmentId) {
        VendorAssessment va = loadAssessmentInTenant(assessmentId);
        if (!"COMPLETED".equals(va.getWorkflowStatus()))
            throw new IllegalStateException("Only COMPLETED assessments can be REVIEWED");
        va.setWorkflowStatus("REVIEWED");
        return assessments.save(va);
    }

    // ─── Scoring math ─────────────────────────────────────────────

    private BigDecimal maxPointsFor(QuestionnaireQuestion q) {
        return switch (q.getAnswerType()) {
            case "SCALE_1_5" -> new BigDecimal("5.00");
            case "BOOLEAN"   -> new BigDecimal("1.00");
            case "SELECT"    -> new BigDecimal("5.00");   // SELECT honours optionsJson at maxPoints=5 by default
            case "TEXT"      -> new BigDecimal("5.00");   // manual scoring; reviewer sets scoredPoints
            default          -> new BigDecimal("5.00");
        };
    }

    private BigDecimal computeScoredPoints(QuestionnaireQuestion q, String answerValue) {
        if (answerValue == null || answerValue.isBlank()) return null;
        String v = answerValue.trim();
        switch (q.getAnswerType()) {
            case "SCALE_1_5" -> {
                try {
                    int n = Integer.parseInt(v);
                    if (n < 1 || n > 5) throw new IllegalArgumentException("SCALE_1_5 answer must be 1..5, got " + n);
                    return new BigDecimal(n).setScale(2, RoundingMode.HALF_UP);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("SCALE_1_5 answer must be numeric, got '" + v + "'");
                }
            }
            case "BOOLEAN" -> {
                String upper = v.toUpperCase(Locale.ROOT);
                if ("YES".equals(upper) || "TRUE".equals(upper) || "1".equals(upper)) return BigDecimal.ONE.setScale(2);
                if ("NO".equals(upper)  || "FALSE".equals(upper) || "0".equals(upper)) return BigDecimal.ZERO.setScale(2);
                throw new IllegalArgumentException("BOOLEAN answer must be YES/NO/TRUE/FALSE/1/0, got '" + v + "'");
            }
            case "SELECT" -> {
                // Look up the selected option's points in options_json
                // (simple {"v":n,...} format — keys are option values,
                // values are the points awarded for that option).
                if (q.getOptionsJson() == null) return null;
                Map<String, Object> options;
                try {
                    options = objectMapper.readValue(q.getOptionsJson(), new TypeReference<Map<String, Object>>() {});
                } catch (JsonProcessingException ex) {
                    throw new IllegalArgumentException("SELECT optionsJson is not valid JSON: " + ex.getOriginalMessage());
                }
                if (!options.containsKey(v)) throw new IllegalArgumentException("SELECT option not found: " + v);
                Object points = options.get(v);
                if (!(points instanceof Number n))
                    throw new IllegalArgumentException("SELECT option '" + v + "' has a non-numeric points value");
                return new BigDecimal(n.toString()).setScale(2, RoundingMode.HALF_UP);
            }
            case "TEXT" -> { return null; }   // requires reviewer to set scoredPoints manually
            default -> { return null; }
        }
    }

    /** Roll up answered_count, computed_score_pct, risk_rating. */
    private void recomputeAssessment(VendorAssessment va) {
        Long o = org();
        if (va.getTemplateId() == null) return;
        QuestionnaireTemplate t = templates.findByIdAndOrganisationId(va.getTemplateId(), o)
            .orElse(null);
        if (t == null) return;
        List<QuestionnaireResponse> rs = responses.findByOrganisationIdAndVendorAssessmentIdOrderByQuestionId(o, va.getId());
        BigDecimal num = BigDecimal.ZERO;
        BigDecimal den = BigDecimal.ZERO;
        int answered = 0;
        for (QuestionnaireResponse r : rs) {
            if (Boolean.TRUE.equals(r.getNotApplicable())) continue;
            if (r.getScoredPoints() == null) continue;
            answered++;
            QuestionnaireQuestion q = questions.findByIdAndOrganisationId(r.getQuestionId(), o).orElse(null);
            if (q == null) continue;
            BigDecimal effective = Boolean.TRUE.equals(q.getInvertScore())
                ? r.getMaxPoints().subtract(r.getScoredPoints())
                : r.getScoredPoints();
            num = num.add(effective.multiply(q.getWeight()));
            den = den.add(r.getMaxPoints().multiply(q.getWeight()));
        }
        va.setAnsweredCount(answered);
        if (den.signum() > 0) {
            // Posture pct (higher = better defences) → invert to a RISK pct
            // (higher = worse) so the template's threshold_*_max fields
            // (LOW ≤ 30, MEDIUM ≤ 60, HIGH ≤ 80) read naturally.
            BigDecimal posturePct = num.multiply(new BigDecimal(100))
                .divide(den, 2, RoundingMode.HALF_UP);
            BigDecimal riskPct = new BigDecimal(100).subtract(posturePct)
                .setScale(2, RoundingMode.HALF_UP);
            va.setComputedScorePct(riskPct);
            va.setRiskScore(riskPct);   // mirror to legacy free-form column
            va.setRiskRating(bandFor(t, riskPct.intValue()));
        } else {
            va.setComputedScorePct(null);
        }
        assessments.save(va);
        log.debug("[questionnaire] assessment {} answered={}/{} score={} rating={}",
            va.getId(), answered, va.getTotalQuestions(), va.getComputedScorePct(), va.getRiskRating());
    }

    private String bandFor(QuestionnaireTemplate t, int pct) {
        if (pct <= t.getThresholdLowMax())    return "LOW";
        if (pct <= t.getThresholdMediumMax()) return "MEDIUM";
        if (pct <= t.getThresholdHighMax())   return "HIGH";
        return "CRITICAL";
    }
}

package com.tss.grc.module.data_officer.service;

import com.tss.grc.module.data_officer.dto.DataOfficerDtos.*;
import com.tss.grc.module.data_officer.entity.Entities.*;
import com.tss.grc.module.data_officer.repository.BreachRepository;
import com.tss.grc.module.data_officer.repository.DpiaRepository;
import com.tss.grc.module.data_officer.repository.EthicsRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Privacy assurance / oversight slice of the Saudi Data Officer Toolkit —
 * Data Protection Impact Assessments, personal-data Breaches (PDPL Art.20 —
 * 72-hour SDAIA clock) and AI/data Ethics reviews.
 *
 * <p>Extracted from the former {@code DataOfficerService} god-class
 * (QUAL-ESG-001). Behaviour is unchanged.
 *
 * <pre>
 *   DPIA:   DRAFT → DPO_REVIEW → BUSINESS_REVIEW → APPROVED | APPROVED_WITH_CONDITIONS | REJECTED → ARCHIVED
 *   Breach: DETECTED → ASSESSING → NOTIFIED_SDAIA → NOTIFYING_SUBJECTS → CONTAINED → REMEDIATING → CLOSED
 *                                  ↘ FALSE_POSITIVE
 *   Ethics: REQUESTED → COMMITTEE_REVIEW → OPINION_DRAFTED → APPROVED | APPROVED_WITH_CONDITIONS | REJECTED | ESCALATED
 * </pre>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PrivacyAssuranceService {

    private static final Map<String, Set<String>> DPIA_ALLOWED = Map.of(
        "DRAFT",                       Set.of("DPO_REVIEW"),
        "DPO_REVIEW",                  Set.of("BUSINESS_REVIEW", "REJECTED", "DRAFT"),
        "BUSINESS_REVIEW",             Set.of("APPROVED", "APPROVED_WITH_CONDITIONS", "REJECTED", "DPO_REVIEW"),
        "APPROVED",                    Set.of("ARCHIVED"),
        "APPROVED_WITH_CONDITIONS",    Set.of("APPROVED", "ARCHIVED"),
        "REJECTED",                    Set.of("ARCHIVED"),
        "ARCHIVED",                    Set.of()
    );

    private static final Map<String, Set<String>> BREACH_ALLOWED = Map.ofEntries(
        Map.entry("DETECTED",            Set.of("ASSESSING", "FALSE_POSITIVE")),
        Map.entry("ASSESSING",           Set.of("NOTIFIED_SDAIA", "CONTAINED", "FALSE_POSITIVE")),
        Map.entry("NOTIFIED_SDAIA",      Set.of("NOTIFYING_SUBJECTS", "CONTAINED")),
        Map.entry("NOTIFYING_SUBJECTS",  Set.of("CONTAINED")),
        Map.entry("CONTAINED",           Set.of("REMEDIATING")),
        Map.entry("REMEDIATING",         Set.of("CLOSED")),
        Map.entry("CLOSED",              Set.of()),
        Map.entry("FALSE_POSITIVE",      Set.of())
    );

    private static final Map<String, Set<String>> ETHICS_ALLOWED = Map.of(
        "REQUESTED",                Set.of("COMMITTEE_REVIEW", "REJECTED"),
        "COMMITTEE_REVIEW",         Set.of("OPINION_DRAFTED", "REJECTED", "ESCALATED"),
        "OPINION_DRAFTED",          Set.of("APPROVED", "APPROVED_WITH_CONDITIONS", "REJECTED"),
        "APPROVED",                 Set.of(),
        "APPROVED_WITH_CONDITIONS", Set.of("APPROVED"),
        "REJECTED",                 Set.of(),
        "ESCALATED",                Set.of("COMMITTEE_REVIEW")
    );

    private final DpiaRepository dpiaRepo;
    private final BreachRepository breachRepo;
    private final EthicsRepository ethicsRepo;
    private final DataOfficerSupport support;

    // ════════════════════════════════════════════════════════ 3. DPIA

    public DpiaResponse createDpia(CreateDpiaRequest req) {
        Long o = DataOfficerSupport.org();
        if (dpiaRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("DPIA " + req.reference() + " already exists");
        Dpia d = new Dpia();
        d.setOrganisationId(o);
        d.setReference(req.reference());
        d.setTitleEn(req.titleEn()); d.setTitleAr(req.titleAr());
        d.setProcessingDescriptionEn(req.processingDescriptionEn());
        d.setProcessingDescriptionAr(req.processingDescriptionAr());
        d.setTriggerReason(req.triggerReason());
        d.setIsHighRisk(Boolean.TRUE.equals(req.isHighRisk()));
        d.setDataOwnerUserId(req.dataOwnerUserId());
        d.setDpoUserId(req.dpoUserId());
        d.setLinkedProcessingActivityId(req.linkedProcessingActivityId());
        d.setStatus("DRAFT");
        dpiaRepo.save(d);
        support.writeTimeline("DPIA", d.getId(), "DPIA_DRAFTED", "DRAFT",
            "DPIA drafted for " + req.titleEn(),
            "تم إعداد مسوّدة تقييم الأثر " + req.titleAr());
        support.emit("DPIA_DRAFTED", "DPIA", d.getId(),
            Map.of("reference", d.getReference(), "isHighRisk", d.getIsHighRisk(),
                   "trigger", d.getTriggerReason()));
        return DataOfficerMappers.toDpia(d);
    }

    public DpiaResponse transitionDpia(Long id, TransitionDpiaRequest req) {
        Dpia d = loadDpia(id);
        DataOfficerSupport.validate(DPIA_ALLOWED, d.getStatus(), req.toStatus(), "DPIA");
        d.setStatus(req.toStatus());
        LocalDateTime now = LocalDateTime.now();
        switch (req.toStatus()) {
            case "DPO_REVIEW" -> d.setSubmittedAt(now);
            case "BUSINESS_REVIEW" -> {
                d.setDpoOpinionAt(now);
                if (req.dpoOpinionEn() != null) d.setDpoOpinionEn(req.dpoOpinionEn());
                if (req.dpoOpinionAr() != null) d.setDpoOpinionAr(req.dpoOpinionAr());
            }
            case "APPROVED", "APPROVED_WITH_CONDITIONS", "REJECTED" -> {
                d.setDecidedAt(now);
                if (req.decisionRationaleEn() != null) d.setDecisionRationaleEn(req.decisionRationaleEn());
                if (req.decisionRationaleAr() != null) d.setDecisionRationaleAr(req.decisionRationaleAr());
                if (req.conditionsEn() != null) d.setConditionsEn(req.conditionsEn());
                if (req.conditionsAr() != null) d.setConditionsAr(req.conditionsAr());
                if (req.nextReviewDue() != null) d.setNextReviewDue(req.nextReviewDue());
            }
            default -> { /* ARCHIVED */ }
        }
        dpiaRepo.save(d);
        support.writeTimeline("DPIA", d.getId(), "DPIA_" + req.toStatus(), req.toStatus(),
            req.decisionRationaleEn(), req.decisionRationaleAr());
        support.emit("DPIA_" + req.toStatus(), "DPIA", d.getId(),
            Map.of("reference", d.getReference(), "isHighRisk", d.getIsHighRisk()));
        return DataOfficerMappers.toDpia(d);
    }

    public List<DpiaResponse> listDpia() {
        return dpiaRepo.findByOrganisationIdOrderByCreatedAtDesc(DataOfficerSupport.org()).stream()
            .map(DataOfficerMappers::toDpia).toList();
    }

    // ════════════════════════════════════════════════════════ 4. Breach

    public BreachResponse createBreach(CreateBreachRequest req) {
        Long o = DataOfficerSupport.org();
        if (breachRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("Breach " + req.reference() + " already exists");
        Breach b = new Breach();
        b.setOrganisationId(o);
        b.setReference(req.reference());
        b.setTitleEn(req.titleEn()); b.setTitleAr(req.titleAr());
        b.setDetectedAt(req.detectedAt());
        b.setDiscoveredAt(LocalDateTime.now());
        // PDPL Art.20 — 72-hour SDAIA notification clock from detection.
        b.setSdaiaDeadline(req.detectedAt().plusHours(72));
        b.setBreachType(req.breachType());
        b.setCause(req.cause());
        b.setSeverity(req.severity());
        b.setAffectedRecordCount(req.affectedRecordCount());
        b.setAffectedSubjectCount(req.affectedSubjectCount());
        b.setAffectedCategoriesJson(req.affectedCategoriesJson());
        b.setIsSpecialCategory(Boolean.TRUE.equals(req.isSpecialCategory()));
        b.setIsCrossBorder(Boolean.TRUE.equals(req.isCrossBorder()));
        b.setDescriptionEn(req.descriptionEn());
        b.setDescriptionAr(req.descriptionAr());
        b.setLinkedIncidentId(req.linkedIncidentId());
        b.setStatus("DETECTED");
        breachRepo.save(b);
        support.writeTimeline("BREACH", b.getId(), "BREACH_DETECTED", "DETECTED",
            "Breach detected: " + req.titleEn(),
            "تم اكتشاف خرق: " + req.titleAr());
        support.emit("BREACH_DETECTED", "BREACH", b.getId(), Map.of(
            "reference", b.getReference(), "severity", DataOfficerSupport.nullSafe(b.getSeverity()),
            "sdaiaDeadline", b.getSdaiaDeadline().toString(),
            "isSpecialCategory", b.getIsSpecialCategory()));
        return DataOfficerMappers.toBreach(b);
    }

    public BreachResponse transitionBreach(Long id, TransitionBreachRequest req) {
        Breach b = loadBreach(id);
        DataOfficerSupport.validate(BREACH_ALLOWED, b.getStatus(), req.toStatus(), "Breach");
        LocalDateTime now = LocalDateTime.now();
        b.setStatus(req.toStatus());
        if (req.severity() != null) b.setSeverity(req.severity());
        switch (req.toStatus()) {
            case "NOTIFIED_SDAIA" -> {
                b.setSdaiaNotifiedAt(now);
                if (req.sdaiaReference() != null) b.setSdaiaReference(req.sdaiaReference());
            }
            case "NOTIFYING_SUBJECTS" -> {
                b.setSubjectsNotifiedAt(now);
                if (req.subjectNotificationTemplateEn() != null) b.setSubjectNotificationTemplateEn(req.subjectNotificationTemplateEn());
                if (req.subjectNotificationTemplateAr() != null) b.setSubjectNotificationTemplateAr(req.subjectNotificationTemplateAr());
            }
            case "CONTAINED", "REMEDIATING" -> {
                if (req.containmentActionsEn() != null) b.setContainmentActionsEn(req.containmentActionsEn());
                if (req.containmentActionsAr() != null) b.setContainmentActionsAr(req.containmentActionsAr());
            }
            case "CLOSED", "FALSE_POSITIVE" -> {
                b.setClosedAt(now);
                if (req.closureSummaryEn() != null) b.setClosureSummaryEn(req.closureSummaryEn());
                if (req.closureSummaryAr() != null) b.setClosureSummaryAr(req.closureSummaryAr());
            }
            default -> { /* ASSESSING — no extra fields */ }
        }
        breachRepo.save(b);
        support.writeTimeline("BREACH", b.getId(), "BREACH_" + req.toStatus(), req.toStatus(),
            req.containmentActionsEn() != null ? req.containmentActionsEn() : req.closureSummaryEn(),
            req.containmentActionsAr() != null ? req.containmentActionsAr() : req.closureSummaryAr());
        support.emit("BREACH_" + req.toStatus(), "BREACH", b.getId(),
            Map.of("reference", b.getReference(),
                   "severity", DataOfficerSupport.nullSafe(b.getSeverity()),
                   "isSpecialCategory", b.getIsSpecialCategory()));
        return DataOfficerMappers.toBreach(b);
    }

    public List<BreachResponse> listBreaches() {
        return breachRepo.findByOrganisationIdOrderByCreatedAtDesc(DataOfficerSupport.org()).stream()
            .map(DataOfficerMappers::toBreach).toList();
    }

    // ════════════════════════════════════════════════════ 10. Ethics

    public EthicsResponse createEthics(CreateEthicsRequest req) {
        Long o = DataOfficerSupport.org();
        if (ethicsRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("Ethics review " + req.reference() + " already exists");
        EthicsReview e = new EthicsReview();
        e.setOrganisationId(o);
        e.setReference(req.reference());
        e.setSubjectType(req.subjectType());
        e.setSubjectRef(req.subjectRef());
        e.setTitleEn(req.titleEn()); e.setTitleAr(req.titleAr());
        e.setSummaryEn(req.summaryEn()); e.setSummaryAr(req.summaryAr());
        e.setRiskDimensionsJson(req.riskDimensionsJson());
        e.setStatus("REQUESTED");
        ethicsRepo.save(e);
        support.writeTimeline("ETHICS", e.getId(), "ETHICS_REQUESTED", "REQUESTED",
            "Ethics review requested: " + req.titleEn(),
            "طلب مراجعة أخلاقية: " + req.titleAr());
        support.emit("ETHICS_REQUESTED", "ETHICS", e.getId(),
            Map.of("reference", e.getReference(), "subjectType", DataOfficerSupport.nullSafe(e.getSubjectType())));
        return DataOfficerMappers.toEthics(e);
    }

    public EthicsResponse transitionEthics(Long id, TransitionEthicsRequest req) {
        EthicsReview e = ethicsRepo.findByOrganisationIdAndId(DataOfficerSupport.org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Ethics review " + id + " not found"));
        DataOfficerSupport.validate(ETHICS_ALLOWED, e.getStatus(), req.toStatus(), "Ethics");
        e.setStatus(req.toStatus());
        LocalDateTime now = LocalDateTime.now();
        switch (req.toStatus()) {
            case "COMMITTEE_REVIEW" -> {
                if (req.committeeMeetingAt() != null) e.setCommitteeMeetingAt(req.committeeMeetingAt());
            }
            case "OPINION_DRAFTED" -> {
                if (req.opinionEn() != null) e.setOpinionEn(req.opinionEn());
                if (req.opinionAr() != null) e.setOpinionAr(req.opinionAr());
            }
            case "APPROVED", "APPROVED_WITH_CONDITIONS", "REJECTED" -> {
                e.setDecidedAt(now);
                if (req.opinionEn() != null) e.setOpinionEn(req.opinionEn());
                if (req.opinionAr() != null) e.setOpinionAr(req.opinionAr());
                if (req.conditionsEn() != null) e.setConditionsEn(req.conditionsEn());
                if (req.conditionsAr() != null) e.setConditionsAr(req.conditionsAr());
                if (req.nextReviewDue() != null) e.setNextReviewDue(req.nextReviewDue());
            }
            default -> { /* ESCALATED */ }
        }
        ethicsRepo.save(e);
        support.writeTimeline("ETHICS", e.getId(), "ETHICS_" + req.toStatus(), req.toStatus(),
            req.opinionEn(), req.opinionAr());
        support.emit("ETHICS_" + req.toStatus(), "ETHICS", e.getId(),
            Map.of("reference", e.getReference()));
        return DataOfficerMappers.toEthics(e);
    }

    public List<EthicsResponse> listEthics() {
        return ethicsRepo.findByOrganisationIdOrderByCreatedAtDesc(DataOfficerSupport.org()).stream()
            .map(DataOfficerMappers::toEthics).toList();
    }

    // ─────────────────────────────────────────────────────── Internals

    private Dpia loadDpia(Long id) {
        return dpiaRepo.findByOrganisationIdAndId(DataOfficerSupport.org(), id)
            .orElseThrow(() -> new EntityNotFoundException("DPIA " + id + " not found"));
    }
    private Breach loadBreach(Long id) {
        return breachRepo.findByOrganisationIdAndId(DataOfficerSupport.org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Breach " + id + " not found"));
    }
}

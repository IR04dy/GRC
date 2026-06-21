package com.tss.grc.module.data_officer.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.data_officer.dto.DataOfficerDtos.*;
import com.tss.grc.module.data_officer.entity.Entities.*;
import com.tss.grc.module.data_officer.repository.*;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Sprint 33 — Saudi Data Officer Toolkit unified service.
 *
 * <p>Implements 10 PDPL/NDMO/SDAIA-mandated sub-modules in one service so
 * they share a single tenant-scoped event timeline and a consistent
 * state-machine validator. The state machines are:
 *
 * <pre>
 *   DSR:         RECEIVED → ID_VERIFIED → IN_PROGRESS → FULFILLED | PARTIAL | REJECTED
 *                                                       ↘ ESCALATED_TO_SDAIA → CLOSED
 *   Consent:     GRANTED ↔ WITHDRAWN; → EXPIRED on valid_until; → SUPERSEDED on re-consent
 *   DPIA:        DRAFT → DPO_REVIEW → BUSINESS_REVIEW → APPROVED | APPROVED_WITH_CONDITIONS | REJECTED → ARCHIVED
 *   Breach:      DETECTED → ASSESSING → NOTIFIED_SDAIA → NOTIFYING_SUBJECTS → CONTAINED → REMEDIATING → CLOSED
 *                                       ↘ FALSE_POSITIVE
 *   Notice:      DRAFT → IN_REVIEW → PUBLISHED → SUPERSEDED → RETIRED
 *   Retention:   ACTIVE → UNDER_REVIEW → ACTIVE; → RETIRED
 *   Disposal:    SCHEDULED → IN_PROGRESS → COMPLETED | FAILED; ON_HOLD ↔ SCHEDULED
 *   NDR:         DRAFT → INTERNAL_REVIEW → READY → SUBMITTED → ACCEPTED | REJECTED | REVISION_REQUESTED
 *   Ethics:      REQUESTED → COMMITTEE_REVIEW → OPINION_DRAFTED → APPROVED | APPROVED_WITH_CONDITIONS | REJECTED | ESCALATED
 * </pre>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DataOfficerService {

    // ─────────────────────────────────────────────────── State machines

    private static final Map<String, Set<String>> DSR_ALLOWED = Map.of(
        "RECEIVED",     Set.of("ID_VERIFIED", "REJECTED"),
        "ID_VERIFIED",  Set.of("IN_PROGRESS", "REJECTED"),
        "IN_PROGRESS",  Set.of("FULFILLED", "PARTIAL", "REJECTED", "ESCALATED_TO_SDAIA"),
        "FULFILLED",    Set.of("CLOSED"),
        "PARTIAL",      Set.of("CLOSED", "IN_PROGRESS"),
        "REJECTED",     Set.of("CLOSED"),
        "ESCALATED_TO_SDAIA", Set.of("CLOSED", "IN_PROGRESS"),
        "CLOSED",       Set.of()
    );

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

    private static final Map<String, Set<String>> NOTICE_ALLOWED = Map.of(
        "DRAFT",       Set.of("IN_REVIEW"),
        "IN_REVIEW",   Set.of("PUBLISHED", "DRAFT"),
        "PUBLISHED",   Set.of("SUPERSEDED", "RETIRED"),
        "SUPERSEDED",  Set.of("RETIRED"),
        "RETIRED",     Set.of()
    );

    private static final Map<String, Set<String>> DISPOSAL_ALLOWED = Map.of(
        "SCHEDULED",   Set.of("IN_PROGRESS", "ON_HOLD"),
        "IN_PROGRESS", Set.of("COMPLETED", "FAILED"),
        "ON_HOLD",     Set.of("SCHEDULED"),
        "COMPLETED",   Set.of(),
        "FAILED",      Set.of("SCHEDULED")
    );

    private static final Map<String, Set<String>> NDR_ALLOWED = Map.of(
        "DRAFT",                Set.of("INTERNAL_REVIEW"),
        "INTERNAL_REVIEW",      Set.of("READY", "DRAFT"),
        "READY",                Set.of("SUBMITTED"),
        "SUBMITTED",            Set.of("ACCEPTED", "REJECTED", "REVISION_REQUESTED"),
        "REVISION_REQUESTED",   Set.of("DRAFT"),
        "ACCEPTED",             Set.of(),
        "REJECTED",             Set.of("DRAFT")
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

    private final DoEventRepository eventRepo;
    private final DsrRepository dsrRepo;
    private final ConsentRepository consentRepo;
    private final DpiaRepository dpiaRepo;
    private final BreachRepository breachRepo;
    private final PrivacyNoticeRepository noticeRepo;
    private final RetentionRepository retentionRepo;
    private final DisposalJobRepository disposalRepo;
    private final NdrRepository ndrRepo;
    private final CatalogEntryRepository catalogRepo;
    private final CatalogLineageRepository lineageRepo;
    private final QualityRuleRepository qualityRuleRepo;
    private final QualityRunRepository qualityRunRepo;
    private final EthicsRepository ethicsRepo;
    private final ApplicationEventPublisher publisher;

    // ════════════════════════════════════════════════════════ 1. DSR

    public DsrResponse createDsr(CreateDsrRequest req) {
        Long o = org();
        if (dsrRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("DSR " + req.reference() + " already exists");
        DsrRequest d = new DsrRequest();
        d.setOrganisationId(o);
        d.setReference(req.reference());
        d.setRequestType(req.requestType());
        d.setChannel(req.channel());
        d.setRequesterNameEn(req.requesterNameEn());
        d.setRequesterNameAr(req.requesterNameAr());
        d.setRequesterNationalId(req.requesterNationalId());
        d.setRequesterEmail(req.requesterEmail());
        d.setRequesterPhone(req.requesterPhone());
        d.setRequestSummaryEn(req.requestSummaryEn());
        d.setRequestSummaryAr(req.requestSummaryAr());
        d.setAssignedUserId(req.assignedUserId());
        d.setStatus("RECEIVED");
        d.setReceivedAt(LocalDateTime.now());
        d.setSlaDeadline(LocalDateTime.now().plusDays(30));   // PDPL Art.10 — 30-day response window
        dsrRepo.save(d);
        timeline("DSR", d.getId(), "DSR_RECEIVED", "RECEIVED",
            "DSR " + req.requestType() + " received via " + nullSafe(req.channel()),
            "تم استلام طلب صاحب البيانات (" + req.requestType() + ")");
        emit("DSR_RECEIVED", "DSR", d.getId(), Map.of(
            "reference", d.getReference(), "requestType", d.getRequestType(),
            "slaDeadline", d.getSlaDeadline().toString()));
        return toDsr(d);
    }

    public DsrResponse transitionDsr(Long id, TransitionDsrRequest req) {
        DsrRequest d = loadDsr(id);
        validate(DSR_ALLOWED, d.getStatus(), req.toStatus(), "DSR");
        LocalDateTime now = LocalDateTime.now();
        d.setStatus(req.toStatus());
        switch (req.toStatus()) {
            case "ID_VERIFIED" -> {
                d.setIdVerifiedAt(now);
                if (req.idVerificationMethod() != null) d.setIdVerificationMethod(req.idVerificationMethod());
            }
            case "IN_PROGRESS" -> {
                if (req.linkedSystemsJson() != null) d.setLinkedSystemsJson(req.linkedSystemsJson());
            }
            case "FULFILLED", "PARTIAL" -> {
                d.setFulfilledAt(now);
                if (req.responseTextEn() != null) d.setResponseTextEn(req.responseTextEn());
                if (req.responseTextAr() != null) d.setResponseTextAr(req.responseTextAr());
                if (req.responseAttachmentEvidenceId() != null) d.setResponseAttachmentEvidenceId(req.responseAttachmentEvidenceId());
            }
            case "REJECTED" -> {
                if (req.rejectionReasonEn() != null) d.setRejectionReasonEn(req.rejectionReasonEn());
                if (req.rejectionReasonAr() != null) d.setRejectionReasonAr(req.rejectionReasonAr());
                if (req.rejectionBasis() != null) d.setRejectionBasis(req.rejectionBasis());
            }
            default -> { /* CLOSED / ESCALATED_TO_SDAIA — no extra fields */ }
        }
        dsrRepo.save(d);
        timeline("DSR", d.getId(), "DSR_" + req.toStatus(), req.toStatus(),
            req.responseTextEn() != null ? req.responseTextEn() : req.rejectionReasonEn(),
            req.responseTextAr() != null ? req.responseTextAr() : req.rejectionReasonAr());
        emit("DSR_" + req.toStatus(), "DSR", d.getId(),
            Map.of("reference", d.getReference(), "type", d.getRequestType()));
        return toDsr(d);
    }

    public List<DsrResponse> listDsr() {
        return dsrRepo.findByOrganisationIdOrderByCreatedAtDesc(org()).stream()
            .map(DataOfficerService::toDsr).toList();
    }

    public DsrResponse getDsr(Long id) { return toDsr(loadDsr(id)); }

    // ════════════════════════════════════════════════════════ 2. Consent

    public ConsentResponse recordConsent(CreateConsentRequest req) {
        Long o = org();
        if (consentRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("Consent " + req.reference() + " already exists");
        ConsentRecord c = new ConsentRecord();
        c.setOrganisationId(o);
        c.setReference(req.reference());
        c.setDataSubjectId(req.dataSubjectId());
        c.setPurposeCode(req.purposeCode());
        c.setPurposeLabelEn(req.purposeLabelEn());
        c.setPurposeLabelAr(req.purposeLabelAr());
        c.setNoticeVersionId(req.noticeVersionId());
        c.setChannel(req.channel());
        c.setConsentTextEn(req.consentTextEn());
        c.setConsentTextAr(req.consentTextAr());
        c.setGrantedAt(req.grantedAt() != null ? req.grantedAt() : LocalDateTime.now());
        c.setGrantedEvidence(req.grantedEvidence());
        c.setValidUntil(req.validUntil());
        c.setLegalBasis(req.legalBasis());
        c.setIsSpecialCategory(Boolean.TRUE.equals(req.isSpecialCategory()));
        c.setStatus("GRANTED");
        consentRepo.save(c);
        timeline("CONSENT", c.getId(), "CONSENT_GRANTED", "GRANTED",
            "Consent granted for purpose " + req.purposeCode(),
            "تم منح الموافقة للغرض " + req.purposeCode());
        emit("CONSENT_GRANTED", "CONSENT", c.getId(),
            Map.of("purposeCode", c.getPurposeCode(),
                   "isSpecialCategory", c.getIsSpecialCategory()));
        return toConsent(c);
    }

    public ConsentResponse withdrawConsent(Long id, WithdrawConsentRequest req) {
        ConsentRecord c = loadConsent(id);
        if (!"GRANTED".equals(c.getStatus()))
            throw new IllegalStateException("Only GRANTED consents can be withdrawn (was " + c.getStatus() + ")");
        c.setStatus("WITHDRAWN");
        c.setWithdrawnAt(LocalDateTime.now());
        c.setWithdrawnReason(req.reason());
        consentRepo.save(c);
        timeline("CONSENT", c.getId(), "CONSENT_WITHDRAWN", "WITHDRAWN",
            "Consent withdrawn: " + req.reason(), "تم سحب الموافقة: " + req.reason());
        emit("CONSENT_WITHDRAWN", "CONSENT", c.getId(),
            Map.of("purposeCode", c.getPurposeCode(), "reason", req.reason()));
        return toConsent(c);
    }

    public List<ConsentResponse> listConsents() {
        return consentRepo.findByOrganisationIdOrderByCreatedAtDesc(org()).stream()
            .map(DataOfficerService::toConsent).toList();
    }

    public List<ConsentResponse> listConsentsForSubject(String dataSubjectId) {
        return consentRepo.findByOrganisationIdAndDataSubjectIdOrderByGrantedAtDesc(org(), dataSubjectId)
            .stream().map(DataOfficerService::toConsent).toList();
    }

    // ════════════════════════════════════════════════════════ 3. DPIA

    public DpiaResponse createDpia(CreateDpiaRequest req) {
        Long o = org();
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
        timeline("DPIA", d.getId(), "DPIA_DRAFTED", "DRAFT",
            "DPIA drafted for " + req.titleEn(),
            "تم إعداد مسوّدة تقييم الأثر " + req.titleAr());
        emit("DPIA_DRAFTED", "DPIA", d.getId(),
            Map.of("reference", d.getReference(), "isHighRisk", d.getIsHighRisk(),
                   "trigger", d.getTriggerReason()));
        return toDpia(d);
    }

    public DpiaResponse transitionDpia(Long id, TransitionDpiaRequest req) {
        Dpia d = loadDpia(id);
        validate(DPIA_ALLOWED, d.getStatus(), req.toStatus(), "DPIA");
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
        timeline("DPIA", d.getId(), "DPIA_" + req.toStatus(), req.toStatus(),
            req.decisionRationaleEn(), req.decisionRationaleAr());
        emit("DPIA_" + req.toStatus(), "DPIA", d.getId(),
            Map.of("reference", d.getReference(), "isHighRisk", d.getIsHighRisk()));
        return toDpia(d);
    }

    public List<DpiaResponse> listDpia() {
        return dpiaRepo.findByOrganisationIdOrderByCreatedAtDesc(org()).stream()
            .map(DataOfficerService::toDpia).toList();
    }

    // ════════════════════════════════════════════════════════ 4. Breach

    public BreachResponse createBreach(CreateBreachRequest req) {
        Long o = org();
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
        timeline("BREACH", b.getId(), "BREACH_DETECTED", "DETECTED",
            "Breach detected: " + req.titleEn(),
            "تم اكتشاف خرق: " + req.titleAr());
        emit("BREACH_DETECTED", "BREACH", b.getId(), Map.of(
            "reference", b.getReference(), "severity", nullSafe(b.getSeverity()),
            "sdaiaDeadline", b.getSdaiaDeadline().toString(),
            "isSpecialCategory", b.getIsSpecialCategory()));
        return toBreach(b);
    }

    public BreachResponse transitionBreach(Long id, TransitionBreachRequest req) {
        Breach b = loadBreach(id);
        validate(BREACH_ALLOWED, b.getStatus(), req.toStatus(), "Breach");
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
        timeline("BREACH", b.getId(), "BREACH_" + req.toStatus(), req.toStatus(),
            req.containmentActionsEn() != null ? req.containmentActionsEn() : req.closureSummaryEn(),
            req.containmentActionsAr() != null ? req.containmentActionsAr() : req.closureSummaryAr());
        emit("BREACH_" + req.toStatus(), "BREACH", b.getId(),
            Map.of("reference", b.getReference(),
                   "severity", nullSafe(b.getSeverity()),
                   "isSpecialCategory", b.getIsSpecialCategory()));
        return toBreach(b);
    }

    public List<BreachResponse> listBreaches() {
        return breachRepo.findByOrganisationIdOrderByCreatedAtDesc(org()).stream()
            .map(DataOfficerService::toBreach).toList();
    }

    // ════════════════════════════════════════════════════ 5. Privacy Notice

    public NoticeResponse createNotice(CreateNoticeRequest req) {
        Long o = org();
        if (noticeRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("Notice " + req.reference() + " already exists");
        PrivacyNotice n = new PrivacyNotice();
        n.setOrganisationId(o);
        n.setReference(req.reference());
        n.setVersionLabel(req.versionLabel());
        n.setChannel(req.channel());
        n.setTitleEn(req.titleEn()); n.setTitleAr(req.titleAr());
        n.setBodyEn(req.bodyEn());   n.setBodyAr(req.bodyAr());
        n.setEffectiveFrom(req.effectiveFrom());
        n.setLegalReviewUserId(req.legalReviewUserId());
        n.setRequiresReConsent(Boolean.TRUE.equals(req.requiresReConsent()));
        n.setStatus("DRAFT");
        noticeRepo.save(n);
        timeline("NOTICE", n.getId(), "NOTICE_DRAFTED", "DRAFT",
            "Privacy notice " + req.versionLabel() + " drafted",
            "إعداد مسوّدة إشعار الخصوصية " + req.versionLabel());
        emit("NOTICE_DRAFTED", "NOTICE", n.getId(),
            Map.of("reference", n.getReference(), "channel", n.getChannel()));
        return toNotice(n);
    }

    public NoticeResponse transitionNotice(Long id, TransitionNoticeRequest req) {
        PrivacyNotice n = loadNotice(id);
        validate(NOTICE_ALLOWED, n.getStatus(), req.toStatus(), "Notice");
        n.setStatus(req.toStatus());
        if ("PUBLISHED".equals(req.toStatus())) n.setPublishedAt(LocalDateTime.now());
        if ("SUPERSEDED".equals(req.toStatus()) && req.supersededById() != null)
            n.setSupersededById(req.supersededById());
        noticeRepo.save(n);
        timeline("NOTICE", n.getId(), "NOTICE_" + req.toStatus(), req.toStatus(), null, null);
        emit("NOTICE_" + req.toStatus(), "NOTICE", n.getId(),
            Map.of("reference", n.getReference(), "channel", n.getChannel(),
                   "requiresReConsent", n.getRequiresReConsent()));
        return toNotice(n);
    }

    public List<NoticeResponse> listNotices() {
        return noticeRepo.findByOrganisationIdOrderByCreatedAtDesc(org()).stream()
            .map(DataOfficerService::toNotice).toList();
    }

    // ════════════════════════════════════════════════════ 6. Retention

    public RetentionRuleResponse createRetentionRule(CreateRetentionRuleRequest req) {
        Long o = org();
        if (retentionRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("Rule " + req.reference() + " already exists");
        RetentionRule r = new RetentionRule();
        r.setOrganisationId(o);
        r.setReference(req.reference());
        r.setDataCategory(req.dataCategory());
        r.setDescriptionEn(req.descriptionEn()); r.setDescriptionAr(req.descriptionAr());
        r.setRetentionMonths(req.retentionMonths());
        r.setLegalBasisEn(req.legalBasisEn());   r.setLegalBasisAr(req.legalBasisAr());
        r.setDisposalMethod(req.disposalMethod());
        r.setReviewCadenceMonths(req.reviewCadenceMonths());
        if (req.reviewCadenceMonths() != null)
            r.setNextReviewDue(LocalDate.now().plusMonths(req.reviewCadenceMonths()));
        r.setOwnerRole(req.ownerRole());
        r.setOwnerUserId(req.ownerUserId());
        r.setLinkedAssetRef(req.linkedAssetRef());
        r.setStatus("ACTIVE");
        retentionRepo.save(r);
        timeline("RETENTION", r.getId(), "RULE_REGISTERED", "ACTIVE",
            "Retention rule registered for " + req.dataCategory(),
            "تم تسجيل قاعدة الاحتفاظ للفئة " + req.dataCategory());
        return toRetention(r);
    }

    public List<RetentionRuleResponse> listRetentionRules() {
        return retentionRepo.findByOrganisationIdOrderByCreatedAtDesc(org()).stream()
            .map(DataOfficerService::toRetention).toList();
    }

    public DisposalJobResponse scheduleDisposal(CreateDisposalJobRequest req) {
        Long o = org();
        if (disposalRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("Disposal " + req.reference() + " already exists");
        // SEC-NDI-004: the referenced retention rule must belong to the caller's org.
        retentionRepo.findByOrganisationIdAndId(o, req.retentionRuleId())
            .orElseThrow(() -> new EntityNotFoundException("Retention rule " + req.retentionRuleId() + " not found"));
        DisposalJob j = new DisposalJob();
        j.setOrganisationId(o);
        j.setRetentionRuleId(req.retentionRuleId());
        j.setReference(req.reference());
        j.setScheduledFor(req.scheduledFor());
        j.setStatus("SCHEDULED");
        disposalRepo.save(j);
        timeline("DISPOSAL", j.getId(), "DISPOSAL_SCHEDULED", "SCHEDULED",
            "Disposal scheduled for " + req.scheduledFor(),
            "تمت جدولة التخلص بتاريخ " + req.scheduledFor());
        return toDisposal(j);
    }

    public DisposalJobResponse transitionDisposal(Long id, TransitionDisposalRequest req) {
        DisposalJob j = disposalRepo.findByOrganisationIdAndId(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Disposal " + id + " not found"));
        validate(DISPOSAL_ALLOWED, j.getStatus(), req.toStatus(), "Disposal");
        j.setStatus(req.toStatus());
        LocalDateTime now = LocalDateTime.now();
        switch (req.toStatus()) {
            case "IN_PROGRESS" -> j.setStartedAt(now);
            case "COMPLETED" -> {
                j.setCompletedAt(now);
                if (req.recordsDisposed() != null) j.setRecordsDisposed(req.recordsDisposed());
                if (req.evidenceHash() != null) j.setEvidenceHash(req.evidenceHash());
            }
            case "ON_HOLD" -> {
                if (req.legalHoldReason() != null) j.setLegalHoldReason(req.legalHoldReason());
            }
            default -> { /* FAILED / SCHEDULED */ }
        }
        disposalRepo.save(j);
        timeline("DISPOSAL", j.getId(), "DISPOSAL_" + req.toStatus(), req.toStatus(),
            req.legalHoldReason(), null);
        return toDisposal(j);
    }

    public List<DisposalJobResponse> listDisposals() {
        return disposalRepo.findByOrganisationIdOrderByScheduledForDesc(org()).stream()
            .map(DataOfficerService::toDisposal).toList();
    }

    // ════════════════════════════════════════════════════ 7. NDR

    public NdrResponse createNdr(CreateNdrRequest req) {
        Long o = org();
        if (ndrRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("NDR " + req.reference() + " already exists");
        NdrSubmission n = new NdrSubmission();
        n.setOrganisationId(o);
        n.setReference(req.reference());
        n.setCycleYear(req.cycleYear());
        n.setTitleEn(req.titleEn()); n.setTitleAr(req.titleAr());
        n.setStatus("DRAFT");
        n.setDatasetCount(0);
        ndrRepo.save(n);
        timeline("NDR", n.getId(), "NDR_DRAFTED", "DRAFT",
            "NDR submission cycle " + req.cycleYear() + " drafted",
            "إعداد مسوّدة تقديم السجل الوطني للبيانات " + req.cycleYear());
        return toNdr(n);
    }

    public NdrResponse transitionNdr(Long id, TransitionNdrRequest req) {
        NdrSubmission n = ndrRepo.findByOrganisationIdAndId(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("NDR " + id + " not found"));
        validate(NDR_ALLOWED, n.getStatus(), req.toStatus(), "NDR");
        LocalDateTime now = LocalDateTime.now();
        n.setStatus(req.toStatus());
        if (req.datasetCount() != null) n.setDatasetCount(req.datasetCount());
        switch (req.toStatus()) {
            case "SUBMITTED" -> {
                n.setSubmittedAt(now);
                if (req.submissionRef() != null) n.setSubmissionRef(req.submissionRef());
            }
            case "ACCEPTED" -> {
                n.setAcceptedAt(now);
                if (req.responseSummaryEn() != null) n.setResponseSummaryEn(req.responseSummaryEn());
                if (req.responseSummaryAr() != null) n.setResponseSummaryAr(req.responseSummaryAr());
            }
            case "REJECTED", "REVISION_REQUESTED" -> {
                if (req.responseSummaryEn() != null) n.setResponseSummaryEn(req.responseSummaryEn());
                if (req.responseSummaryAr() != null) n.setResponseSummaryAr(req.responseSummaryAr());
            }
            default -> { /* INTERNAL_REVIEW / READY / DRAFT */ }
        }
        ndrRepo.save(n);
        timeline("NDR", n.getId(), "NDR_" + req.toStatus(), req.toStatus(),
            req.responseSummaryEn(), req.responseSummaryAr());
        return toNdr(n);
    }

    public List<NdrResponse> listNdr() {
        return ndrRepo.findByOrganisationIdOrderByCycleYearDesc(org()).stream()
            .map(DataOfficerService::toNdr).toList();
    }

    // ════════════════════════════════════════════════════ 8. Catalog

    public CatalogEntryResponse createCatalogEntry(CreateCatalogEntryRequest req) {
        Long o = org();
        if (catalogRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("Catalog entry " + req.reference() + " already exists");
        CatalogEntry e = new CatalogEntry();
        e.setOrganisationId(o);
        e.setReference(req.reference());
        e.setDatasetNameEn(req.datasetNameEn()); e.setDatasetNameAr(req.datasetNameAr());
        e.setDescriptionEn(req.descriptionEn());  e.setDescriptionAr(req.descriptionAr());
        e.setDomain(req.domain());
        e.setSourceSystem(req.sourceSystem());
        e.setStorageLocation(req.storageLocation());
        e.setClassification(req.classification());
        e.setContainsPii(Boolean.TRUE.equals(req.containsPii()));
        e.setContainsSpecialCategory(Boolean.TRUE.equals(req.containsSpecialCategory()));
        e.setOwnerRole(req.ownerRole());
        e.setOwnerUserId(req.ownerUserId());
        e.setStewardUserId(req.stewardUserId());
        e.setCustodianTeam(req.custodianTeam());
        e.setRowCountApprox(req.rowCountApprox());
        e.setRefreshFrequency(req.refreshFrequency());
        catalogRepo.save(e);
        timeline("CATALOG", e.getId(), "CATALOG_REGISTERED", null,
            "Dataset " + req.datasetNameEn() + " registered",
            "تم تسجيل مجموعة البيانات " + nullSafe(req.datasetNameAr()));
        return toCatalogEntry(e);
    }

    public List<CatalogEntryResponse> listCatalog() {
        return catalogRepo.findByOrganisationIdOrderByCreatedAtDesc(org()).stream()
            .map(DataOfficerService::toCatalogEntry).toList();
    }

    public LineageResponse createLineage(CreateLineageRequest req) {
        Long o = org();
        // SEC-NDI-004: validate both endpoints belong to the caller's org before
        // persisting the edge — never trust caller-supplied FK ids. A 404 on miss
        // prevents planting lineage that points at another tenant's catalog ids.
        catalogRepo.findByOrganisationIdAndId(o, req.sourceEntryId())
            .orElseThrow(() -> new EntityNotFoundException("Source catalog entry " + req.sourceEntryId() + " not found"));
        catalogRepo.findByOrganisationIdAndId(o, req.targetEntryId())
            .orElseThrow(() -> new EntityNotFoundException("Target catalog entry " + req.targetEntryId() + " not found"));
        CatalogLineage l = new CatalogLineage();
        l.setOrganisationId(o);
        l.setSourceEntryId(req.sourceEntryId());
        l.setTargetEntryId(req.targetEntryId());
        l.setTransformLabel(req.transformLabel());
        lineageRepo.save(l);
        return new LineageResponse(l.getId(), l.getSourceEntryId(), l.getTargetEntryId(), l.getTransformLabel());
    }

    public List<LineageResponse> listLineage(Long sourceId) {
        return lineageRepo.findByOrganisationIdAndSourceEntryId(org(), sourceId).stream()
            .map(l -> new LineageResponse(l.getId(), l.getSourceEntryId(), l.getTargetEntryId(), l.getTransformLabel()))
            .toList();
    }

    // ════════════════════════════════════════════════════ 9. Quality

    public QualityRuleResponse createQualityRule(CreateQualityRuleRequest req) {
        Long o = org();
        if (qualityRuleRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("Quality rule " + req.reference() + " already exists");
        // SEC-NDI-004: the referenced catalog entry must belong to the caller's org.
        catalogRepo.findByOrganisationIdAndId(o, req.catalogEntryId())
            .orElseThrow(() -> new EntityNotFoundException("Catalog entry " + req.catalogEntryId() + " not found"));
        QualityRule r = new QualityRule();
        r.setOrganisationId(o);
        r.setReference(req.reference());
        r.setCatalogEntryId(req.catalogEntryId());
        r.setRuleNameEn(req.ruleNameEn()); r.setRuleNameAr(req.ruleNameAr());
        r.setDimension(req.dimension());
        r.setRuleExpression(req.ruleExpression());
        r.setThresholdPct(req.thresholdPct());
        r.setSeverity(req.severity());
        r.setIsActive(true);
        qualityRuleRepo.save(r);
        timeline("QUALITY", r.getId(), "RULE_REGISTERED", null,
            "Quality rule " + req.ruleNameEn() + " (" + req.dimension() + ")",
            "قاعدة جودة بيانات " + nullSafe(req.ruleNameAr()));
        return toQualityRule(r);
    }

    public List<QualityRuleResponse> listQualityRules() {
        return qualityRuleRepo.findByOrganisationIdOrderByCreatedAtDesc(org()).stream()
            .map(DataOfficerService::toQualityRule).toList();
    }

    public QualityRunResponse recordQualityRun(RecordQualityRunRequest req) {
        Long o = org();
        // SEC-NDI-004: the referenced quality rule must belong to the caller's org.
        qualityRuleRepo.findByOrganisationIdAndId(o, req.qualityRuleId())
            .orElseThrow(() -> new EntityNotFoundException("Quality rule " + req.qualityRuleId() + " not found"));
        QualityRun run = new QualityRun();
        run.setOrganisationId(o);
        run.setQualityRuleId(req.qualityRuleId());
        run.setRecordsChecked(req.recordsChecked());
        run.setRecordsFailed(req.recordsFailed());
        run.setPassPct(req.passPct());
        run.setBand(req.band());
        run.setNotesEn(req.notesEn());
        run.setNotesAr(req.notesAr());
        qualityRunRepo.save(run);
        // Emit RED-band signal so the dashboard + automation engine react.
        if ("RED".equals(req.band())) {
            emit("QUALITY_RUN_RED", "QUALITY_RULE", req.qualityRuleId(),
                Map.of("passPct", req.passPct() == null ? "0" : req.passPct().toPlainString()));
        }
        return toQualityRun(run);
    }

    public List<QualityRunResponse> listQualityRuns(Long ruleId) {
        if (ruleId != null) {
            return qualityRunRepo.findByOrganisationIdAndQualityRuleIdOrderByRunAtDesc(org(), ruleId)
                .stream().map(DataOfficerService::toQualityRun).toList();
        }
        return qualityRunRepo.findByOrganisationIdOrderByRunAtDesc(org()).stream()
            .map(DataOfficerService::toQualityRun).toList();
    }

    // ════════════════════════════════════════════════════ 10. Ethics

    public EthicsResponse createEthics(CreateEthicsRequest req) {
        Long o = org();
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
        timeline("ETHICS", e.getId(), "ETHICS_REQUESTED", "REQUESTED",
            "Ethics review requested: " + req.titleEn(),
            "طلب مراجعة أخلاقية: " + req.titleAr());
        emit("ETHICS_REQUESTED", "ETHICS", e.getId(),
            Map.of("reference", e.getReference(), "subjectType", nullSafe(e.getSubjectType())));
        return toEthics(e);
    }

    public EthicsResponse transitionEthics(Long id, TransitionEthicsRequest req) {
        EthicsReview e = ethicsRepo.findByOrganisationIdAndId(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Ethics review " + id + " not found"));
        validate(ETHICS_ALLOWED, e.getStatus(), req.toStatus(), "Ethics");
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
        timeline("ETHICS", e.getId(), "ETHICS_" + req.toStatus(), req.toStatus(),
            req.opinionEn(), req.opinionAr());
        emit("ETHICS_" + req.toStatus(), "ETHICS", e.getId(),
            Map.of("reference", e.getReference()));
        return toEthics(e);
    }

    public List<EthicsResponse> listEthics() {
        return ethicsRepo.findByOrganisationIdOrderByCreatedAtDesc(org()).stream()
            .map(DataOfficerService::toEthics).toList();
    }

    // ═══════════════════════════════════════════════ Timeline + Dashboard

    public List<EventResponse> timeline(String entityType, Long entityId) {
        return eventRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(org(), entityType, entityId)
            .stream().map(e -> new EventResponse(
                e.getId(), e.getEntityType(), e.getEntityId(),
                e.getOccurredAt(), e.getEventType(),
                e.getActorUserId(), e.getStatusAfter(),
                e.getNoteEn(), e.getNoteAr()
            )).toList();
    }

    public DashboardResponse dashboard() {
        Long o = org();
        LocalDateTime now = LocalDateTime.now();
        List<DsrRequest> allDsr = dsrRepo.findByOrganisationIdOrderByCreatedAtDesc(o);
        List<Breach> allBreach = breachRepo.findByOrganisationIdOrderByCreatedAtDesc(o);
        List<Dpia> allDpia = dpiaRepo.findByOrganisationIdOrderByCreatedAtDesc(o);

        long dsrOpen = allDsr.stream().filter(d -> !Set.of("FULFILLED","REJECTED","PARTIAL","CLOSED").contains(d.getStatus())).count();
        long dsrOverdue = dsrRepo.findOverdue(o, now).size();

        long consentActive = consentRepo.countByOrganisationIdAndStatus(o, "GRANTED");
        long consentWithdrawn30 = consentRepo.findByOrganisationIdOrderByCreatedAtDesc(o).stream()
            .filter(c -> "WITHDRAWN".equals(c.getStatus()))
            .filter(c -> c.getWithdrawnAt() != null && c.getWithdrawnAt().isAfter(now.minusDays(30)))
            .count();

        long dpiaOpen = allDpia.stream()
            .filter(d -> !Set.of("APPROVED","REJECTED","ARCHIVED").contains(d.getStatus())).count();
        int year = LocalDate.now().getYear();
        long dpiaApprovedYtd = allDpia.stream()
            .filter(d -> "APPROVED".equals(d.getStatus()) || "APPROVED_WITH_CONDITIONS".equals(d.getStatus()))
            .filter(d -> d.getDecidedAt() != null && d.getDecidedAt().getYear() == year)
            .count();

        long breachOpen = allBreach.stream()
            .filter(b -> !Set.of("CLOSED","FALSE_POSITIVE").contains(b.getStatus())).count();
        long breachOverdueSdaia = breachRepo.findOverdueSdaia(o, now).size();

        long noticesPublished = noticeRepo.findByOrganisationIdOrderByCreatedAtDesc(o).stream()
            .filter(n -> "PUBLISHED".equals(n.getStatus())).count();
        long noticesReConsent = noticeRepo.findByOrganisationIdOrderByCreatedAtDesc(o).stream()
            .filter(n -> "PUBLISHED".equals(n.getStatus()) && Boolean.TRUE.equals(n.getRequiresReConsent()))
            .count();

        long retentionDue = retentionRepo.findReviewsDue(o, LocalDate.now()).size();
        long disposalScheduled = disposalRepo.findByOrganisationIdAndStatusOrderByScheduledForAsc(o, "SCHEDULED").size();

        long ndrThisYearInt = ndrRepo.findByOrganisationIdAndCycleYear(o, year)
            .map(n -> switch (n.getStatus()) {
                case "ACCEPTED" -> 3L;
                case "SUBMITTED" -> 2L;
                case "READY" -> 1L;
                default -> 0L;
            }).orElse(0L);

        long catEntries = catalogRepo.findByOrganisationIdOrderByCreatedAtDesc(o).size();
        long catPii = catalogRepo.countByOrganisationIdAndContainsPii(o, true);

        long qRules = qualityRuleRepo.findByOrganisationIdOrderByCreatedAtDesc(o).size();
        long qRed30 = qualityRunRepo.findByOrganisationIdOrderByRunAtDesc(o).stream()
            .filter(r -> "RED".equals(r.getBand()))
            .filter(r -> r.getRunAt() != null && r.getRunAt().isAfter(now.minusDays(30)))
            .count();

        long ethicsOpen = ethicsRepo.findByOrganisationIdOrderByCreatedAtDesc(o).stream()
            .filter(e -> !Set.of("APPROVED","REJECTED").contains(e.getStatus())).count();

        Map<String, Long> dsrByStatus = allDsr.stream()
            .collect(Collectors.groupingBy(DsrRequest::getStatus, Collectors.counting()));
        Map<String, Long> breachBySev = allBreach.stream()
            .filter(b -> b.getSeverity() != null)
            .collect(Collectors.groupingBy(Breach::getSeverity, Collectors.counting()));
        Map<String, Long> dpiaByStatus = allDpia.stream()
            .collect(Collectors.groupingBy(Dpia::getStatus, Collectors.counting()));

        List<DsrResponse> recentDsr = allDsr.stream().limit(8).map(DataOfficerService::toDsr).toList();
        List<BreachResponse> recentBreach = allBreach.stream().limit(8).map(DataOfficerService::toBreach).toList();

        return new DashboardResponse(
            dsrOpen, dsrOverdue,
            consentActive, consentWithdrawn30,
            dpiaOpen, dpiaApprovedYtd,
            breachOpen, breachOverdueSdaia,
            noticesPublished, noticesReConsent,
            retentionDue, disposalScheduled,
            ndrThisYearInt,
            catEntries, catPii,
            qRules, qRed30,
            ethicsOpen,
            dsrByStatus, breachBySev, dpiaByStatus,
            recentDsr, recentBreach);
    }

    // ─────────────────────────────────────────────────────── Internals

    private DsrRequest loadDsr(Long id) {
        return dsrRepo.findByOrganisationIdAndId(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("DSR " + id + " not found"));
    }
    private ConsentRecord loadConsent(Long id) {
        return consentRepo.findByOrganisationIdAndId(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Consent " + id + " not found"));
    }
    private Dpia loadDpia(Long id) {
        return dpiaRepo.findByOrganisationIdAndId(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("DPIA " + id + " not found"));
    }
    private Breach loadBreach(Long id) {
        return breachRepo.findByOrganisationIdAndId(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Breach " + id + " not found"));
    }
    private PrivacyNotice loadNotice(Long id) {
        return noticeRepo.findByOrganisationIdAndId(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Notice " + id + " not found"));
    }

    private static void validate(Map<String, Set<String>> allowed, String from, String to, String label) {
        if (!allowed.getOrDefault(from, Set.of()).contains(to))
            throw new IllegalStateException(label + " cannot transition " + from + " → " + to);
    }

    private void timeline(String entityType, Long entityId, String eventType, String statusAfter,
                          String noteEn, String noteAr) {
        DoEvent e = new DoEvent();
        e.setOrganisationId(org());
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setOccurredAt(LocalDateTime.now());
        e.setEventType(eventType);
        e.setActorUserId(currentUserId());
        e.setStatusAfter(statusAfter);
        e.setNoteEn(noteEn);
        e.setNoteAr(noteAr);
        eventRepo.save(e);
    }

    private void emit(String trigger, String entityType, Long entityId, Map<String, Object> payload) {
        publisher.publishEvent(new DomainEvent(
            TenantContext.getCurrentTenant(),
            trigger, entityType, entityId, null,
            payload == null ? Map.of() : new HashMap<>(payload)));
    }

    private static Long org() { return TenantContext.requireOrganisationId(); }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof GrcUserDetails u)) return null;
        return u.getUserId();
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    // ─────────────────────────────────────────────────────── Mappers

    private static DsrResponse toDsr(DsrRequest d) {
        Long hoursLeft = null;
        if (d.getSlaDeadline() != null
                && !Set.of("FULFILLED","REJECTED","PARTIAL","CLOSED").contains(d.getStatus())) {
            hoursLeft = Duration.between(LocalDateTime.now(), d.getSlaDeadline()).toHours();
        }
        return new DsrResponse(
            d.getId(), d.getReference(), d.getRequestType(), d.getChannel(),
            d.getRequesterNameEn(), d.getRequesterNameAr(),
            d.getRequesterEmail(), d.getRequesterPhone(),
            d.getRequestSummaryEn(), d.getRequestSummaryAr(),
            d.getStatus(), d.getReceivedAt(), d.getSlaDeadline(),
            d.getIdVerifiedAt(), d.getIdVerificationMethod(),
            d.getFulfilledAt(), d.getRejectionReasonEn(), d.getRejectionReasonAr(),
            d.getRejectionBasis(), d.getAssignedUserId(),
            d.getResponseTextEn(), d.getResponseTextAr(),
            d.getResponseAttachmentEvidenceId(), hoursLeft);
    }

    private static ConsentResponse toConsent(ConsentRecord c) {
        return new ConsentResponse(
            c.getId(), c.getReference(), c.getDataSubjectId(),
            c.getPurposeCode(), c.getPurposeLabelEn(), c.getPurposeLabelAr(),
            c.getNoticeVersionId(), c.getChannel(),
            c.getConsentTextEn(), c.getConsentTextAr(),
            c.getGrantedAt(), c.getGrantedEvidence(),
            c.getWithdrawnAt(), c.getWithdrawnReason(),
            c.getValidUntil(), c.getStatus(), c.getLegalBasis(),
            c.getIsSpecialCategory());
    }

    private static DpiaResponse toDpia(Dpia d) {
        return new DpiaResponse(
            d.getId(), d.getReference(),
            d.getTitleEn(), d.getTitleAr(),
            d.getProcessingDescriptionEn(), d.getProcessingDescriptionAr(),
            d.getTriggerReason(), d.getIsHighRisk(), d.getStatus(),
            d.getDataOwnerUserId(), d.getDpoUserId(),
            d.getSubmittedAt(),
            d.getDpoOpinionEn(), d.getDpoOpinionAr(), d.getDpoOpinionAt(),
            d.getDecidedAt(),
            d.getDecisionRationaleEn(), d.getDecisionRationaleAr(),
            d.getConditionsEn(), d.getConditionsAr(),
            d.getNextReviewDue(), d.getLinkedProcessingActivityId());
    }

    private static BreachResponse toBreach(Breach b) {
        Long hours = null;
        if (b.getSdaiaDeadline() != null && b.getSdaiaNotifiedAt() == null
                && !Set.of("CLOSED","FALSE_POSITIVE").contains(b.getStatus())) {
            hours = Duration.between(LocalDateTime.now(), b.getSdaiaDeadline()).toHours();
        }
        return new BreachResponse(
            b.getId(), b.getReference(),
            b.getTitleEn(), b.getTitleAr(),
            b.getDetectedAt(), b.getDiscoveredAt(), b.getSdaiaDeadline(),
            b.getStatus(), b.getBreachType(), b.getCause(), b.getSeverity(),
            b.getAffectedRecordCount(), b.getAffectedSubjectCount(),
            b.getAffectedCategoriesJson(), b.getIsSpecialCategory(), b.getIsCrossBorder(),
            b.getDescriptionEn(), b.getDescriptionAr(),
            b.getContainmentActionsEn(), b.getContainmentActionsAr(),
            b.getSdaiaNotifiedAt(), b.getSdaiaReference(),
            b.getSubjectsNotifiedAt(),
            b.getSubjectNotificationTemplateEn(), b.getSubjectNotificationTemplateAr(),
            b.getClosedAt(), b.getClosureSummaryEn(), b.getClosureSummaryAr(),
            b.getLinkedIncidentId(), hours);
    }

    private static NoticeResponse toNotice(PrivacyNotice n) {
        return new NoticeResponse(
            n.getId(), n.getReference(), n.getVersionLabel(), n.getChannel(),
            n.getTitleEn(), n.getTitleAr(), n.getBodyEn(), n.getBodyAr(),
            n.getStatus(), n.getEffectiveFrom(), n.getPublishedAt(),
            n.getSupersededById(), n.getLegalReviewUserId(), n.getRequiresReConsent());
    }

    private static RetentionRuleResponse toRetention(RetentionRule r) {
        return new RetentionRuleResponse(
            r.getId(), r.getReference(), r.getDataCategory(),
            r.getDescriptionEn(), r.getDescriptionAr(), r.getRetentionMonths(),
            r.getLegalBasisEn(), r.getLegalBasisAr(),
            r.getDisposalMethod(), r.getReviewCadenceMonths(),
            r.getNextReviewDue(), r.getOwnerRole(), r.getOwnerUserId(),
            r.getLinkedAssetRef(), r.getStatus());
    }

    private static DisposalJobResponse toDisposal(DisposalJob j) {
        return new DisposalJobResponse(
            j.getId(), j.getReference(), j.getRetentionRuleId(),
            j.getScheduledFor(), j.getStartedAt(), j.getCompletedAt(),
            j.getStatus(), j.getRecordsDisposed(), j.getEvidenceHash(), j.getLegalHoldReason());
    }

    private static NdrResponse toNdr(NdrSubmission n) {
        return new NdrResponse(
            n.getId(), n.getReference(), n.getCycleYear(),
            n.getTitleEn(), n.getTitleAr(), n.getStatus(),
            n.getDatasetCount(), n.getSubmittedAt(), n.getSubmissionRef(),
            n.getAcceptedAt(), n.getResponseSummaryEn(), n.getResponseSummaryAr());
    }

    private static CatalogEntryResponse toCatalogEntry(CatalogEntry e) {
        return new CatalogEntryResponse(
            e.getId(), e.getReference(),
            e.getDatasetNameEn(), e.getDatasetNameAr(),
            e.getDescriptionEn(), e.getDescriptionAr(),
            e.getDomain(), e.getSourceSystem(), e.getStorageLocation(),
            e.getClassification(), e.getContainsPii(), e.getContainsSpecialCategory(),
            e.getOwnerRole(), e.getOwnerUserId(), e.getStewardUserId(), e.getCustodianTeam(),
            e.getRowCountApprox(), e.getRefreshFrequency(), e.getLastProfiledAt());
    }

    private static QualityRuleResponse toQualityRule(QualityRule r) {
        return new QualityRuleResponse(
            r.getId(), r.getReference(), r.getCatalogEntryId(),
            r.getRuleNameEn(), r.getRuleNameAr(),
            r.getDimension(), r.getRuleExpression(),
            r.getThresholdPct(), r.getSeverity(), r.getIsActive());
    }

    private static QualityRunResponse toQualityRun(QualityRun r) {
        return new QualityRunResponse(
            r.getId(), r.getQualityRuleId(), r.getRunAt(),
            r.getRecordsChecked(), r.getRecordsFailed(),
            r.getPassPct(), r.getBand(), r.getNotesEn(), r.getNotesAr());
    }

    private static EthicsResponse toEthics(EthicsReview e) {
        return new EthicsResponse(
            e.getId(), e.getReference(), e.getSubjectType(), e.getSubjectRef(),
            e.getTitleEn(), e.getTitleAr(),
            e.getSummaryEn(), e.getSummaryAr(),
            e.getRequestedAt(), e.getStatus(),
            e.getRiskDimensionsJson(), e.getCommitteeMeetingAt(),
            e.getOpinionEn(), e.getOpinionAr(),
            e.getConditionsEn(), e.getConditionsAr(),
            e.getDecidedAt(), e.getNextReviewDue());
    }
}

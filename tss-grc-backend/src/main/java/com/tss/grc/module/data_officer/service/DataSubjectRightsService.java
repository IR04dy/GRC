package com.tss.grc.module.data_officer.service;

import com.tss.grc.module.data_officer.dto.DataOfficerDtos.*;
import com.tss.grc.module.data_officer.entity.Entities.*;
import com.tss.grc.module.data_officer.repository.ConsentRepository;
import com.tss.grc.module.data_officer.repository.DsrRepository;
import com.tss.grc.module.data_officer.repository.PrivacyNoticeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data-subject-facing slice of the Saudi Data Officer Toolkit — Data Subject
 * Requests (PDPL Art.10), Consent records and Privacy Notices.
 *
 * <p>Extracted from the former {@code DataOfficerService} god-class
 * (QUAL-ESG-001). State machines, SLA clocks and event/timeline emission are
 * preserved verbatim; the shared timeline/emit/validate helpers now live in
 * {@link DataOfficerSupport}.
 *
 * <pre>
 *   DSR:    RECEIVED → ID_VERIFIED → IN_PROGRESS → FULFILLED | PARTIAL | REJECTED
 *                                                  ↘ ESCALATED_TO_SDAIA → CLOSED
 *   Consent:GRANTED ↔ WITHDRAWN; → EXPIRED on valid_until; → SUPERSEDED on re-consent
 *   Notice: DRAFT → IN_REVIEW → PUBLISHED → SUPERSEDED → RETIRED
 * </pre>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DataSubjectRightsService {

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

    private static final Map<String, Set<String>> NOTICE_ALLOWED = Map.of(
        "DRAFT",       Set.of("IN_REVIEW"),
        "IN_REVIEW",   Set.of("PUBLISHED", "DRAFT"),
        "PUBLISHED",   Set.of("SUPERSEDED", "RETIRED"),
        "SUPERSEDED",  Set.of("RETIRED"),
        "RETIRED",     Set.of()
    );

    private final DsrRepository dsrRepo;
    private final ConsentRepository consentRepo;
    private final PrivacyNoticeRepository noticeRepo;
    private final DataOfficerSupport support;

    // ════════════════════════════════════════════════════════ 1. DSR

    public DsrResponse createDsr(CreateDsrRequest req) {
        Long o = DataOfficerSupport.org();
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
        support.writeTimeline("DSR", d.getId(), "DSR_RECEIVED", "RECEIVED",
            "DSR " + req.requestType() + " received via " + DataOfficerSupport.nullSafe(req.channel()),
            "تم استلام طلب صاحب البيانات (" + req.requestType() + ")");
        support.emit("DSR_RECEIVED", "DSR", d.getId(), Map.of(
            "reference", d.getReference(), "requestType", d.getRequestType(),
            "slaDeadline", d.getSlaDeadline().toString()));
        return DataOfficerMappers.toDsr(d);
    }

    public DsrResponse transitionDsr(Long id, TransitionDsrRequest req) {
        DsrRequest d = loadDsr(id);
        DataOfficerSupport.validate(DSR_ALLOWED, d.getStatus(), req.toStatus(), "DSR");
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
        support.writeTimeline("DSR", d.getId(), "DSR_" + req.toStatus(), req.toStatus(),
            req.responseTextEn() != null ? req.responseTextEn() : req.rejectionReasonEn(),
            req.responseTextAr() != null ? req.responseTextAr() : req.rejectionReasonAr());
        support.emit("DSR_" + req.toStatus(), "DSR", d.getId(),
            Map.of("reference", d.getReference(), "type", d.getRequestType()));
        return DataOfficerMappers.toDsr(d);
    }

    public List<DsrResponse> listDsr() {
        return dsrRepo.findByOrganisationIdOrderByCreatedAtDesc(DataOfficerSupport.org()).stream()
            .map(DataOfficerMappers::toDsr).toList();
    }

    public DsrResponse getDsr(Long id) { return DataOfficerMappers.toDsr(loadDsr(id)); }

    // ════════════════════════════════════════════════════════ 2. Consent

    public ConsentResponse recordConsent(CreateConsentRequest req) {
        Long o = DataOfficerSupport.org();
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
        support.writeTimeline("CONSENT", c.getId(), "CONSENT_GRANTED", "GRANTED",
            "Consent granted for purpose " + req.purposeCode(),
            "تم منح الموافقة للغرض " + req.purposeCode());
        support.emit("CONSENT_GRANTED", "CONSENT", c.getId(),
            Map.of("purposeCode", c.getPurposeCode(),
                   "isSpecialCategory", c.getIsSpecialCategory()));
        return DataOfficerMappers.toConsent(c);
    }

    public ConsentResponse withdrawConsent(Long id, WithdrawConsentRequest req) {
        ConsentRecord c = loadConsent(id);
        if (!"GRANTED".equals(c.getStatus()))
            throw new IllegalStateException("Only GRANTED consents can be withdrawn (was " + c.getStatus() + ")");
        c.setStatus("WITHDRAWN");
        c.setWithdrawnAt(LocalDateTime.now());
        c.setWithdrawnReason(req.reason());
        consentRepo.save(c);
        support.writeTimeline("CONSENT", c.getId(), "CONSENT_WITHDRAWN", "WITHDRAWN",
            "Consent withdrawn: " + req.reason(), "تم سحب الموافقة: " + req.reason());
        support.emit("CONSENT_WITHDRAWN", "CONSENT", c.getId(),
            Map.of("purposeCode", c.getPurposeCode(), "reason", req.reason()));
        return DataOfficerMappers.toConsent(c);
    }

    public List<ConsentResponse> listConsents() {
        return consentRepo.findByOrganisationIdOrderByCreatedAtDesc(DataOfficerSupport.org()).stream()
            .map(DataOfficerMappers::toConsent).toList();
    }

    public List<ConsentResponse> listConsentsForSubject(String dataSubjectId) {
        return consentRepo.findByOrganisationIdAndDataSubjectIdOrderByGrantedAtDesc(DataOfficerSupport.org(), dataSubjectId)
            .stream().map(DataOfficerMappers::toConsent).toList();
    }

    // ════════════════════════════════════════════════════ 5. Privacy Notice

    public NoticeResponse createNotice(CreateNoticeRequest req) {
        Long o = DataOfficerSupport.org();
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
        support.writeTimeline("NOTICE", n.getId(), "NOTICE_DRAFTED", "DRAFT",
            "Privacy notice " + req.versionLabel() + " drafted",
            "إعداد مسوّدة إشعار الخصوصية " + req.versionLabel());
        support.emit("NOTICE_DRAFTED", "NOTICE", n.getId(),
            Map.of("reference", n.getReference(), "channel", n.getChannel()));
        return DataOfficerMappers.toNotice(n);
    }

    public NoticeResponse transitionNotice(Long id, TransitionNoticeRequest req) {
        PrivacyNotice n = loadNotice(id);
        DataOfficerSupport.validate(NOTICE_ALLOWED, n.getStatus(), req.toStatus(), "Notice");
        n.setStatus(req.toStatus());
        if ("PUBLISHED".equals(req.toStatus())) n.setPublishedAt(LocalDateTime.now());
        if ("SUPERSEDED".equals(req.toStatus()) && req.supersededById() != null)
            n.setSupersededById(req.supersededById());
        noticeRepo.save(n);
        support.writeTimeline("NOTICE", n.getId(), "NOTICE_" + req.toStatus(), req.toStatus(), null, null);
        support.emit("NOTICE_" + req.toStatus(), "NOTICE", n.getId(),
            Map.of("reference", n.getReference(), "channel", n.getChannel(),
                   "requiresReConsent", n.getRequiresReConsent()));
        return DataOfficerMappers.toNotice(n);
    }

    public List<NoticeResponse> listNotices() {
        return noticeRepo.findByOrganisationIdOrderByCreatedAtDesc(DataOfficerSupport.org()).stream()
            .map(DataOfficerMappers::toNotice).toList();
    }

    // ─────────────────────────────────────────────────────── Internals

    private DsrRequest loadDsr(Long id) {
        return dsrRepo.findByOrganisationIdAndId(DataOfficerSupport.org(), id)
            .orElseThrow(() -> new EntityNotFoundException("DSR " + id + " not found"));
    }
    private ConsentRecord loadConsent(Long id) {
        return consentRepo.findByOrganisationIdAndId(DataOfficerSupport.org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Consent " + id + " not found"));
    }
    private PrivacyNotice loadNotice(Long id) {
        return noticeRepo.findByOrganisationIdAndId(DataOfficerSupport.org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Notice " + id + " not found"));
    }
}

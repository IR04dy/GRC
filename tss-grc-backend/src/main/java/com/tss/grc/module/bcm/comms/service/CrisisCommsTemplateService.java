package com.tss.grc.module.bcm.comms.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.ApprovalTransitionRequest;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.TemplateRequest;
import com.tss.grc.module.bcm.comms.dto.CrisisCommsDtos.TemplateResponse;
import com.tss.grc.module.bcm.comms.entity.CrisisCommsTemplateV2;
import com.tss.grc.module.bcm.comms.repository.CrisisCommsTemplateV2Repository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Slice 9-E — Crisis Communications template service.
 *
 * <p>CRUD + approval state machine for the v2 template register.
 *
 * <h3>Approval state machine</h3>
 * <pre>
 *   DRAFT              → IN_REVIEW
 *   IN_REVIEW          → APPROVED_BY_LEGAL | DRAFT (send back)
 *   APPROVED_BY_LEGAL  → APPROVED_BY_CISO | IN_REVIEW (send back)
 *   APPROVED_BY_CISO   → RETIRED | IN_REVIEW (re-open for revision)
 *   RETIRED            → IN_REVIEW (re-instate)
 * </pre>
 *
 * <p>Every mutation writes an F7 audit row + emits an F8 domain event
 * ({@code CRISIS_COMMS_TEMPLATE_CREATED}, {@code _UPDATED}, {@code _TRANSITIONED}).
 */
@Service
@RequiredArgsConstructor
public class CrisisCommsTemplateService {

    public static final Set<String> AUDIENCES = Set.of(
        "BOARD", "REGULATOR", "CUSTOMERS", "STAFF", "PARTNERS", "MEDIA", "PUBLIC");

    public static final Set<String> SCENARIOS = Set.of(
        "RANSOMWARE", "DATA_BREACH", "OUTAGE", "NATURAL_DISASTER", "OT_INCIDENT",
        "REGULATORY_NOTIFICATION", "MEDIA_RESPONSE", "RECOVERY",
        "LESSONS_LEARNED", "GENERIC_MAJOR");

    public static final Set<String> APPROVAL_STATES = Set.of(
        "DRAFT", "IN_REVIEW", "APPROVED_BY_LEGAL", "APPROVED_BY_CISO", "RETIRED");

    private final CrisisCommsTemplateV2Repository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() { return TenantContext.requireOrganisationId(); }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TemplateResponse> list() {
        return repo.findByOrganisationIdAndIsDeletedFalseOrderByAudienceAscTemplateCodeAsc(org())
            .stream().map(CrisisCommsTemplateService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> listByAudience(String audience) {
        return repo.findByOrganisationIdAndAudienceAndIsDeletedFalseOrderByTemplateCodeAsc(org(), audience)
            .stream().map(CrisisCommsTemplateService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> findByAudienceAndScenario(String audience, String scenario) {
        return repo.findByOrganisationIdAndAudienceAndScenarioCategoryAndIsDeletedFalseOrderByTemplateCodeAsc(
                org(), audience, scenario)
            .stream().map(CrisisCommsTemplateService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> listByApprovalStatus(String approvalStatus) {
        return repo.findByOrganisationIdAndApprovalStatusAndIsDeletedFalseOrderByAudienceAscTemplateCodeAsc(
                org(), approvalStatus)
            .stream().map(CrisisCommsTemplateService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(UUID externalId) {
        return toResponse(loadOrThrow(externalId));
    }

    // ─── Writes ─────────────────────────────────────────────────────

    @Transactional
    public TemplateResponse create(TemplateRequest req) {
        validate(req);
        Long orgId = org();

        CrisisCommsTemplateV2 t = new CrisisCommsTemplateV2();
        t.setOrganisationId(orgId);
        applyRequest(t, req);
        t.setTemplateCode(req.templateCode() != null && !req.templateCode().isBlank()
            ? req.templateCode() : nextTemplateCode(req.audience()));
        if (repo.existsByOrganisationIdAndTemplateCodeAndIsDeletedFalse(orgId, t.getTemplateCode())) {
            throw new IllegalArgumentException("Template code already exists: " + t.getTemplateCode());
        }
        t.setApprovalStatus("DRAFT");

        CrisisCommsTemplateV2 saved = repo.save(t);
        audit.log("CREATE", "CrisisCommsTemplateV2", saved.getId(),
            "Crisis-comms template created: " + saved.getTemplateCode()
                + " (" + saved.getAudience() + " / " + saved.getScenarioCategory() + ")");
        emit("CRISIS_COMMS_TEMPLATE_CREATED", saved, null);
        return toResponse(saved);
    }

    @Transactional
    public TemplateResponse update(UUID externalId, TemplateRequest req) {
        CrisisCommsTemplateV2 t = loadOrThrow(externalId);
        if ("APPROVED_BY_CISO".equals(t.getApprovalStatus()) || "RETIRED".equals(t.getApprovalStatus())) {
            throw new IllegalStateException(
                "Cannot edit a " + t.getApprovalStatus() + " template — send back to IN_REVIEW first");
        }
        validate(req);
        applyRequest(t, req);
        t.setUpdatedAt(LocalDateTime.now());

        CrisisCommsTemplateV2 saved = repo.save(t);
        audit.log("UPDATE", "CrisisCommsTemplateV2", saved.getId(),
            "Crisis-comms template updated: " + saved.getTemplateCode());
        emit("CRISIS_COMMS_TEMPLATE_UPDATED", saved, null);
        return toResponse(saved);
    }

    @Transactional
    public TemplateResponse transition(UUID externalId, ApprovalTransitionRequest req) {
        CrisisCommsTemplateV2 t = loadOrThrow(externalId);
        String from = t.getApprovalStatus();
        String to = req.approvalStatus();
        if (!APPROVAL_STATES.contains(to)) {
            throw new IllegalArgumentException("Invalid approval state: " + to);
        }
        guardTransition(from, to);

        t.setApprovalStatus(to);
        LocalDateTime now = LocalDateTime.now();
        // F2-223 — both approval names are the authenticated principal. The block below already
        // clears them on re-open so that "a second approval requires a new APPROVED_BY_*
        // transition", and its comment calls the result an honest governance trail — but the name
        // written into that trail was whatever the caller typed. This is the state
        // CrisisCommsDispatchService enforces before it will send anything
        // ("only APPROVED_BY_CISO templates may be dispatched"), so the identity of the CISO who
        // approved it is the evidence behind that gate.
        String approver = CurrentActor.username();
        if ("APPROVED_BY_LEGAL".equals(to)) {
            t.setApprovedByLegalAt(now);
            t.setApprovedByLegalName(approver);
        } else if ("APPROVED_BY_CISO".equals(to)) {
            t.setApprovedByCisoAt(now);
            t.setApprovedByCisoName(approver);
        } else if ("DRAFT".equals(to) || "IN_REVIEW".equals(to)) {
            // Re-opening clears the relevant approval stamp so the
            // governance trail is honest — a "second approval" requires
            // a new APPROVED_BY_* transition.
            if ("DRAFT".equals(to)) {
                t.setApprovedByLegalAt(null);
                t.setApprovedByLegalName(null);
                t.setApprovedByCisoAt(null);
                t.setApprovedByCisoName(null);
            } else if ("APPROVED_BY_CISO".equals(from)) {
                // Re-open from CISO → IN_REVIEW: keep legal stamp, clear CISO stamp.
                t.setApprovedByCisoAt(null);
                t.setApprovedByCisoName(null);
            } else if ("APPROVED_BY_LEGAL".equals(from)) {
                // Re-open from legal → IN_REVIEW: clear legal stamp.
                t.setApprovedByLegalAt(null);
                t.setApprovedByLegalName(null);
            }
        }
        t.setUpdatedAt(now);

        CrisisCommsTemplateV2 saved = repo.save(t);
        audit.log("TRANSITION", "CrisisCommsTemplateV2", saved.getId(),
            "Template approval transition " + saved.getTemplateCode() + ": " + from + " → " + to
                + (approver != null ? " (by " + approver + ")" : ""));
        emit("CRISIS_COMMS_TEMPLATE_TRANSITIONED", saved, from);
        return toResponse(saved);
    }

    @Transactional
    public TemplateResponse archive(UUID externalId) {
        CrisisCommsTemplateV2 t = loadOrThrow(externalId);
        String from = t.getApprovalStatus();
        t.setIsDeleted(true);
        t.setDeletedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        CrisisCommsTemplateV2 saved = repo.save(t);
        audit.log("ARCHIVE", "CrisisCommsTemplateV2", saved.getId(),
            "Crisis-comms template archived: " + saved.getTemplateCode() + " (was " + from + ")");
        emit("CRISIS_COMMS_TEMPLATE_ARCHIVED", saved, from);
        return toResponse(saved);
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private CrisisCommsTemplateV2 loadOrThrow(UUID externalId) {
        return repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(org(), externalId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Crisis-comms template not found: " + externalId));
    }

    /**
     * Approval state machine guard.
     * <pre>
     *   DRAFT             → IN_REVIEW
     *   IN_REVIEW         → APPROVED_BY_LEGAL | DRAFT
     *   APPROVED_BY_LEGAL → APPROVED_BY_CISO | IN_REVIEW
     *   APPROVED_BY_CISO  → RETIRED | IN_REVIEW
     *   RETIRED           → IN_REVIEW
     * </pre>
     */
    static void guardTransition(String from, String to) {
        boolean ok = switch (Objects.requireNonNullElse(from, "")) {
            case "DRAFT"             -> "IN_REVIEW".equals(to);
            case "IN_REVIEW"         -> Set.of("APPROVED_BY_LEGAL", "DRAFT").contains(to);
            case "APPROVED_BY_LEGAL" -> Set.of("APPROVED_BY_CISO", "IN_REVIEW").contains(to);
            case "APPROVED_BY_CISO"  -> Set.of("RETIRED", "IN_REVIEW").contains(to);
            case "RETIRED"           -> "IN_REVIEW".equals(to);
            default                  -> false;
        };
        if (!ok) {
            throw new IllegalTransitionException(from, to, allowedFrom(from));
        }
    }

    private static Set<String> allowedFrom(String from) {
        return switch (Objects.requireNonNullElse(from, "")) {
            case "DRAFT"             -> Set.of("IN_REVIEW");
            case "IN_REVIEW"         -> Set.of("APPROVED_BY_LEGAL", "DRAFT");
            case "APPROVED_BY_LEGAL" -> Set.of("APPROVED_BY_CISO", "IN_REVIEW");
            case "APPROVED_BY_CISO"  -> Set.of("RETIRED", "IN_REVIEW");
            case "RETIRED"           -> Set.of("IN_REVIEW");
            default                  -> Set.of();
        };
    }

    private static void validate(TemplateRequest req) {
        if (req.audience() == null || !AUDIENCES.contains(req.audience())) {
            throw new IllegalArgumentException("Invalid audience: " + req.audience()
                + " (allowed: " + AUDIENCES + ")");
        }
        if (req.scenarioCategory() == null || !SCENARIOS.contains(req.scenarioCategory())) {
            throw new IllegalArgumentException("Invalid scenarioCategory: " + req.scenarioCategory()
                + " (allowed: " + SCENARIOS + ")");
        }
    }

    private static void applyRequest(CrisisCommsTemplateV2 t, TemplateRequest req) {
        t.setTitleEn(req.titleEn());
        t.setTitleAr(req.titleAr());
        t.setAudience(req.audience());
        t.setScenarioCategory(req.scenarioCategory());
        t.setSeverityBand(req.severityBand());
        t.setSubjectEn(req.subjectEn());
        t.setSubjectAr(req.subjectAr());
        t.setBodyEn(req.bodyEn());
        t.setBodyAr(req.bodyAr());
        t.setSmsBodyEn(req.smsBodyEn());
        t.setSmsBodyAr(req.smsBodyAr());
        t.setPlaceholdersJson(req.placeholdersJson());
        t.setRegulatoryAnchor(req.regulatoryAnchor());
    }

    private static String nextTemplateCode(String audience) {
        int year = Year.now().getValue();
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        String aud = audience.length() >= 3 ? audience.substring(0, 3) : audience;
        return String.format(Locale.ROOT, "CCT-%s-%04d-%s", aud, year, suffix);
    }

    // ─── F8 emission ────────────────────────────────────────────────

    private void emit(String trigger, CrisisCommsTemplateV2 t, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            t.getOrganisationId(), trigger, "CrisisCommsTemplateV2", t.getId(),
            "ccv2:" + t.getId(), payload(t, previousStatus)));
    }

    private static Map<String, Object> payload(CrisisCommsTemplateV2 t, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("templateId",        str(t.getId()));
        m.put("templateCode",      str(t.getTemplateCode()));
        m.put("audience",          str(t.getAudience()));
        m.put("scenarioCategory",  str(t.getScenarioCategory()));
        m.put("severityBand",      str(t.getSeverityBand()));
        m.put("approvalStatus",    str(t.getApprovalStatus()));
        m.put("previousApprovalStatus", str(previousStatus));
        return m;
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    // ─── Mapping ────────────────────────────────────────────────────

    static TemplateResponse toResponse(CrisisCommsTemplateV2 t) {
        return new TemplateResponse(
            t.getId(),
            t.getExternalId(),
            t.getOrganisationId(),
            t.getTemplateCode(),
            t.getTitleEn(),
            t.getTitleAr(),
            t.getAudience(),
            t.getScenarioCategory(),
            t.getSeverityBand(),
            t.getSubjectEn(),
            t.getSubjectAr(),
            t.getBodyEn(),
            t.getBodyAr(),
            t.getSmsBodyEn(),
            t.getSmsBodyAr(),
            t.getPlaceholdersJson(),
            t.getRegulatoryAnchor(),
            t.getApprovalStatus(),
            t.getApprovedByLegalAt(),
            t.getApprovedByLegalName(),
            t.getApprovedByCisoAt(),
            t.getApprovedByCisoName(),
            t.getCreatedAt(),
            t.getUpdatedAt()
        );
    }
}

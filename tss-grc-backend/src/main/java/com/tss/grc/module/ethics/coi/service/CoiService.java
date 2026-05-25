package com.tss.grc.module.ethics.coi.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.CampaignResponse;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.CreateCampaignRequest;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.DeclarationResponse;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.DisclosedItemDto;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.RecordRecusalRequest;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.RecusalEventResponse;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.ReviewDeclarationRequest;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.SubmitDeclarationRequest;
import com.tss.grc.module.ethics.coi.entity.CoiDeclaration;
import com.tss.grc.module.ethics.coi.entity.CoiDeclarationCampaign;
import com.tss.grc.module.ethics.coi.entity.CoiDisclosedItem;
import com.tss.grc.module.ethics.coi.entity.CoiRecusalEvent;
import com.tss.grc.module.ethics.coi.repository.CoiDeclarationCampaignRepository;
import com.tss.grc.module.ethics.coi.repository.CoiDeclarationRepository;
import com.tss.grc.module.ethics.coi.repository.CoiDisclosedItemRepository;
import com.tss.grc.module.ethics.coi.repository.CoiRecusalEventRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Sprint 13 — Conflict-of-Interest (COI) register service.
 *
 * <h3>Campaign status machine</h3>
 * <pre>
 *   DRAFT → OPEN → CLOSED → ARCHIVED
 * </pre>
 *
 * <h3>Declaration auto-flag rules</h3>
 * On submission the service walks the declarant's active disclosed
 * items and sets {@code reviewOutcome} as follows (in order — first
 * match wins, highest severity overrides):
 * <ol>
 *   <li>Any active item of type {@code RELATED_PARTY},
 *       {@code FAMILY_RELATIONSHIP}, or {@code POLITICAL_OFFICE}
 *       → {@code RECUSAL_REQUIRED}.</li>
 *   <li>Any active item with {@code financial_value_sar &gt;= 100,000}
 *       → {@code RECUSAL_REQUIRED}.</li>
 *   <li>Any active item of type {@code DIRECTORSHIP} or
 *       {@code OUTSIDE_EMPLOYMENT} → {@code MITIGATION_REQUIRED}.</li>
 *   <li>Any active item with {@code financial_value_sar &gt;= 50,000}
 *       → {@code MITIGATION_REQUIRED}.</li>
 *   <li>Otherwise {@code PENDING} — awaits ethics-officer review.</li>
 * </ol>
 *
 * <h3>F7 / F8 events</h3>
 * Every mutation writes an audit row via {@link AuditLogService} and
 * (for state-changing events) publishes a {@link DomainEvent} via
 * the application publisher. Event triggers:
 * <ul>
 *   <li>{@code COI_CAMPAIGN_OPENED}</li>
 *   <li>{@code COI_CAMPAIGN_CLOSED}</li>
 *   <li>{@code COI_DECLARATION_SUBMITTED}</li>
 *   <li>{@code COI_DECLARATION_REVIEWED}</li>
 *   <li>{@code COI_AUTO_FLAGGED_RECUSAL}</li>
 *   <li>{@code COI_AUTO_FLAGGED_MITIGATION}</li>
 *   <li>{@code COI_RECUSAL_RECORDED}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoiService {

    private static final Set<String> AUTO_RECUSAL_TYPES =
            Set.of("RELATED_PARTY", "FAMILY_RELATIONSHIP", "POLITICAL_OFFICE");
    private static final Set<String> AUTO_MITIGATION_TYPES =
            Set.of("DIRECTORSHIP", "OUTSIDE_EMPLOYMENT");

    private static final BigDecimal THRESHOLD_RECUSAL    = new BigDecimal("100000");
    private static final BigDecimal THRESHOLD_MITIGATION = new BigDecimal("50000");

    private static final Set<String> ALLOWED_OUTCOMES = Set.of(
            "PENDING", "CLEARED", "MITIGATION_REQUIRED", "RECUSAL_REQUIRED", "ESCALATED");

    private static final Map<String, Set<String>> CAMPAIGN_TRANSITIONS = Map.of(
            "DRAFT",    Set.of("OPEN"),
            "OPEN",     Set.of("CLOSED"),
            "CLOSED",   Set.of("ARCHIVED"),
            "ARCHIVED", Set.of()
    );

    private final CoiDeclarationCampaignRepository campaignRepo;
    private final CoiDeclarationRepository declarationRepo;
    private final CoiDisclosedItemRepository itemRepo;
    private final CoiRecusalEventRepository recusalRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    // ─── Campaign CRUD + lifecycle ──────────────────────────────────

    @Transactional
    public CampaignResponse createCampaign(CreateCampaignRequest req) {
        Long orgId = org();
        if (campaignRepo.existsByOrganisationIdAndCodeAndIsDeletedFalse(orgId, req.code())) {
            throw new IllegalArgumentException("Campaign code already in use: " + req.code());
        }
        CoiDeclarationCampaign c = new CoiDeclarationCampaign();
        c.setOrganisationId(orgId);
        c.setCode(req.code());
        c.setTitleEn(req.titleEn());
        c.setTitleAr(req.titleAr());
        c.setFiscalYear(req.fiscalYear());
        c.setOpensOn(req.opensOn());
        c.setClosesOn(req.closesOn());
        c.setScope(safeScope(req.scope()));
        c.setStatus("DRAFT");
        c.setTargetUserCount(req.targetUserCount() != null ? req.targetUserCount() : 0);
        c.setDeclaredUserCount(0);
        CoiDeclarationCampaign saved = campaignRepo.save(c);

        audit.log("COI_CAMPAIGN_CREATED", "CoiDeclarationCampaign", saved.getId(),
                "COI campaign created: " + saved.getCode() + " · scope=" + saved.getScope()
                        + " · FY=" + saved.getFiscalYear());
        return CampaignResponse.from(saved);
    }

    @Transactional
    public CampaignResponse openCampaign(Long id) {
        CoiDeclarationCampaign c = loadCampaign(id);
        String from = c.getStatus();
        guardCampaignTransition(from, "OPEN");
        c.setStatus("OPEN");
        CoiDeclarationCampaign saved = campaignRepo.save(c);

        audit.log("COI_CAMPAIGN_OPENED", "CoiDeclarationCampaign", saved.getId(),
                "COI campaign opened: " + saved.getCode());
        emit("COI_CAMPAIGN_OPENED", "CoiDeclarationCampaign", saved.getId(), saved.getCode(),
                Map.of("status", saved.getStatus(), "previousStatus", from,
                        "fiscalYear", String.valueOf(saved.getFiscalYear()),
                        "scope", saved.getScope()));
        return CampaignResponse.from(saved);
    }

    @Transactional
    public CampaignResponse closeCampaign(Long id) {
        CoiDeclarationCampaign c = loadCampaign(id);
        String from = c.getStatus();
        guardCampaignTransition(from, "CLOSED");
        c.setStatus("CLOSED");
        CoiDeclarationCampaign saved = campaignRepo.save(c);

        audit.log("COI_CAMPAIGN_CLOSED", "CoiDeclarationCampaign", saved.getId(),
                "COI campaign closed: " + saved.getCode());
        emit("COI_CAMPAIGN_CLOSED", "CoiDeclarationCampaign", saved.getId(), saved.getCode(),
                Map.of("status", saved.getStatus(), "previousStatus", from,
                        "declaredUserCount", String.valueOf(saved.getDeclaredUserCount()),
                        "targetUserCount", String.valueOf(saved.getTargetUserCount())));
        return CampaignResponse.from(saved);
    }

    @Transactional
    public CampaignResponse archiveCampaign(Long id) {
        CoiDeclarationCampaign c = loadCampaign(id);
        String from = c.getStatus();
        guardCampaignTransition(from, "ARCHIVED");
        c.setStatus("ARCHIVED");
        CoiDeclarationCampaign saved = campaignRepo.save(c);

        audit.log("COI_CAMPAIGN_ARCHIVED", "CoiDeclarationCampaign", saved.getId(),
                "COI campaign archived: " + saved.getCode());
        return CampaignResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> listCampaignsByOrg(Long orgId) {
        requireSameOrg(orgId);
        return campaignRepo.findByOrganisationIdAndIsDeletedFalse(orgId)
                .stream().map(CampaignResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CampaignResponse getCampaign(Long id) {
        return CampaignResponse.from(loadCampaign(id));
    }

    // ─── Declaration submission with auto-flag ──────────────────────

    @Transactional
    public DeclarationResponse submitDeclaration(SubmitDeclarationRequest req) {
        Long orgId = org();
        CoiDeclarationCampaign campaign = loadCampaign(req.campaignId());
        if (!"OPEN".equals(campaign.getStatus())) {
            throw new IllegalStateException(
                    "Cannot submit declaration — campaign " + campaign.getCode()
                            + " status is " + campaign.getStatus() + " (must be OPEN).");
        }

        CoiDeclaration d = new CoiDeclaration();
        d.setOrganisationId(orgId);
        d.setCampaignId(campaign.getId());
        d.setUserId(req.userId());
        d.setEmployeeNo(req.employeeNo());
        d.setFullNameEn(req.fullNameEn());
        d.setFullNameAr(req.fullNameAr());
        d.setPositionTitleEn(req.positionTitleEn());
        d.setPositionTitleAr(req.positionTitleAr());
        d.setDepartmentEn(req.departmentEn());
        d.setDepartmentAr(req.departmentAr());
        d.setDeclarationDate(LocalDateTime.now());
        d.setHasConflict(Boolean.TRUE.equals(req.hasConflict()));
        d.setHasPotentialConflict(Boolean.TRUE.equals(req.hasPotentialConflict()));
        d.setAttestationText(req.attestationText());
        d.setSubmittedOn(LocalDateTime.now());
        d.setReviewOutcome("PENDING");

        CoiDeclaration savedDecl = declarationRepo.save(d);

        // Persist disclosed items.
        List<CoiDisclosedItem> savedItems = new java.util.ArrayList<>();
        if (req.disclosedItems() != null) {
            for (DisclosedItemDto dto : req.disclosedItems()) {
                CoiDisclosedItem item = new CoiDisclosedItem();
                item.setDeclarationId(savedDecl.getId());
                item.setItemType(dto.itemType());
                item.setCounterpartyEn(dto.counterpartyEn());
                item.setCounterpartyAr(dto.counterpartyAr());
                item.setRelationshipDescriptionEn(dto.relationshipDescriptionEn());
                item.setRelationshipDescriptionAr(dto.relationshipDescriptionAr());
                item.setFinancialValueSar(dto.financialValueSar());
                item.setStartDate(dto.startDate());
                item.setEndDate(dto.endDate());
                item.setIsActive(dto.isActive() == null ? Boolean.TRUE : dto.isActive());
                item.setMitigationActionEn(dto.mitigationActionEn());
                item.setMitigationActionAr(dto.mitigationActionAr());
                savedItems.add(itemRepo.save(item));
            }
        }

        // Apply auto-flag rules.
        String autoOutcome = computeAutoOutcome(savedItems);
        savedDecl.setReviewOutcome(autoOutcome);
        CoiDeclaration finalDecl = declarationRepo.save(savedDecl);

        // Bump campaign counter.
        campaign.setDeclaredUserCount(
                Math.max(campaign.getDeclaredUserCount() == null ? 0 : campaign.getDeclaredUserCount(),
                        (int) declarationRepo.countByOrganisationIdAndCampaignIdAndIsDeletedFalse(
                                orgId, campaign.getId())));
        campaignRepo.save(campaign);

        // F7 audit.
        audit.log("COI_DECLARATION_SUBMITTED", "CoiDeclaration", finalDecl.getId(),
                "COI declaration submitted by " + finalDecl.getFullNameEn()
                        + " (" + finalDecl.getEmployeeNo() + ") · auto-outcome="
                        + finalDecl.getReviewOutcome() + " · items=" + savedItems.size());

        // F8 events.
        Map<String, Object> payload = new HashMap<>();
        payload.put("declarationId", String.valueOf(finalDecl.getId()));
        payload.put("campaignId", String.valueOf(campaign.getId()));
        payload.put("campaignCode", campaign.getCode());
        payload.put("employeeNo", String.valueOf(finalDecl.getEmployeeNo()));
        payload.put("fullName", finalDecl.getFullNameEn());
        payload.put("reviewOutcome", finalDecl.getReviewOutcome());
        payload.put("itemCount", String.valueOf(savedItems.size()));
        emit("COI_DECLARATION_SUBMITTED", "CoiDeclaration", finalDecl.getId(),
                campaign.getCode(), payload);

        if ("RECUSAL_REQUIRED".equals(autoOutcome)) {
            emit("COI_AUTO_FLAGGED_RECUSAL", "CoiDeclaration", finalDecl.getId(),
                    campaign.getCode(), payload);
        } else if ("MITIGATION_REQUIRED".equals(autoOutcome)) {
            emit("COI_AUTO_FLAGGED_MITIGATION", "CoiDeclaration", finalDecl.getId(),
                    campaign.getCode(), payload);
        }

        return DeclarationResponse.from(finalDecl, savedItems);
    }

    /**
     * Apply the COI auto-flag rules to a set of disclosed items.
     * <p>Package-private for unit testing.
     */
    static String computeAutoOutcome(List<CoiDisclosedItem> items) {
        if (items == null || items.isEmpty()) return "PENDING";

        boolean recusal = false;
        boolean mitigation = false;

        for (CoiDisclosedItem item : items) {
            if (!Boolean.TRUE.equals(item.getIsActive())) continue;
            String type = item.getItemType();
            BigDecimal value = item.getFinancialValueSar();

            if (type != null && AUTO_RECUSAL_TYPES.contains(type)) {
                recusal = true;
                continue;  // recusal already the worst-case
            }
            if (value != null && value.compareTo(THRESHOLD_RECUSAL) >= 0) {
                recusal = true;
                continue;
            }
            if (type != null && AUTO_MITIGATION_TYPES.contains(type)) {
                mitigation = true;
                continue;
            }
            if (value != null && value.compareTo(THRESHOLD_MITIGATION) >= 0) {
                mitigation = true;
            }
        }

        if (recusal) return "RECUSAL_REQUIRED";
        if (mitigation) return "MITIGATION_REQUIRED";
        return "PENDING";
    }

    // ─── Declaration review ─────────────────────────────────────────

    @Transactional
    public DeclarationResponse reviewDeclaration(Long id, ReviewDeclarationRequest req, Long actorUserId) {
        if (!ALLOWED_OUTCOMES.contains(req.reviewOutcome())) {
            throw new IllegalArgumentException(
                    "reviewOutcome must be one of " + ALLOWED_OUTCOMES);
        }
        CoiDeclaration d = loadDeclaration(id);
        String previous = d.getReviewOutcome();

        d.setReviewOutcome(req.reviewOutcome());
        d.setReviewNotesEn(req.reviewNotesEn());
        d.setReviewNotesAr(req.reviewNotesAr());
        d.setReviewedBy(actorUserId);
        d.setReviewedOn(LocalDateTime.now());
        CoiDeclaration saved = declarationRepo.save(d);

        audit.log("COI_DECLARATION_REVIEWED", "CoiDeclaration", saved.getId(),
                "COI declaration reviewed: " + saved.getFullNameEn()
                        + " · " + previous + " → " + saved.getReviewOutcome());

        emit("COI_DECLARATION_REVIEWED", "CoiDeclaration", saved.getId(),
                String.valueOf(saved.getId()),
                Map.of(
                        "declarationId", String.valueOf(saved.getId()),
                        "previousOutcome", String.valueOf(previous),
                        "reviewOutcome", saved.getReviewOutcome(),
                        "reviewedBy", String.valueOf(actorUserId),
                        "employeeNo", String.valueOf(saved.getEmployeeNo())
                ));

        List<CoiDisclosedItem> items = itemRepo.findByDeclarationIdAndIsDeletedFalse(saved.getId());
        return DeclarationResponse.from(saved, items);
    }

    // ─── Recusal events ─────────────────────────────────────────────

    @Transactional
    public RecusalEventResponse recordRecusal(RecordRecusalRequest req) {
        Long orgId = org();
        // If a declaration id is provided, validate it belongs to this tenant.
        if (req.declarationId() != null) {
            loadDeclaration(req.declarationId());
        }
        CoiRecusalEvent r = new CoiRecusalEvent();
        r.setOrganisationId(orgId);
        r.setDeclarationId(req.declarationId());
        r.setUserId(req.userId());
        r.setMeetingReference(req.meetingReference());
        r.setCommitteeCode(req.committeeCode());
        r.setRecusalDate(req.recusalDate() != null ? req.recusalDate() : LocalDateTime.now());
        r.setRecusalReasonEn(req.recusalReasonEn());
        r.setRecusalReasonAr(req.recusalReasonAr());
        r.setAgendaItems(req.agendaItems());
        r.setRecordedInMinutes(req.recordedInMinutes() == null ? Boolean.TRUE : req.recordedInMinutes());
        CoiRecusalEvent saved = recusalRepo.save(r);

        audit.log("COI_RECUSAL_RECORDED", "CoiRecusalEvent", saved.getId(),
                "COI recusal recorded: user=" + saved.getUserId()
                        + " · meeting=" + saved.getMeetingReference()
                        + " · committee=" + saved.getCommitteeCode());

        Map<String, Object> payload = new HashMap<>();
        payload.put("recusalId", String.valueOf(saved.getId()));
        payload.put("userId", String.valueOf(saved.getUserId()));
        payload.put("meetingReference", saved.getMeetingReference());
        payload.put("committeeCode", String.valueOf(saved.getCommitteeCode()));
        payload.put("declarationId", String.valueOf(saved.getDeclarationId()));
        payload.put("recordedInMinutes", String.valueOf(saved.getRecordedInMinutes()));
        emit("COI_RECUSAL_RECORDED", "CoiRecusalEvent", saved.getId(),
                saved.getMeetingReference(), payload);

        return RecusalEventResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<RecusalEventResponse> listRecusalsByUser(Long userId) {
        return recusalRepo.findByOrganisationIdAndUserIdOrderByRecusalDateDesc(org(), userId)
                .stream().map(RecusalEventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DeclarationResponse> listDeclarationsByCampaign(Long campaignId) {
        // Tenant validation via loadCampaign.
        CoiDeclarationCampaign campaign = loadCampaign(campaignId);
        List<CoiDeclaration> declarations =
                declarationRepo.findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByDeclarationDateDesc(
                        org(), campaign.getId());
        return declarations.stream()
                .map(d -> DeclarationResponse.from(d,
                        itemRepo.findByDeclarationIdAndIsDeletedFalse(d.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public DeclarationResponse getDeclaration(Long id) {
        CoiDeclaration d = loadDeclaration(id);
        return DeclarationResponse.from(d, itemRepo.findByDeclarationIdAndIsDeletedFalse(d.getId()));
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private CoiDeclarationCampaign loadCampaign(Long id) {
        return campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
                .orElseThrow(() -> new EntityNotFoundException("COI campaign not found: " + id));
    }

    private CoiDeclaration loadDeclaration(Long id) {
        return declarationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
                .orElseThrow(() -> new EntityNotFoundException("COI declaration not found: " + id));
    }

    private static Long org() {
        return TenantContext.requireOrganisationId();
    }

    private static void requireSameOrg(Long orgId) {
        Long current = TenantContext.requireOrganisationId();
        if (!Objects.equals(current, orgId)) {
            throw new IllegalArgumentException(
                    "Tenant mismatch — requested orgId " + orgId + " but current tenant is " + current);
        }
    }

    private static String safeScope(String s) {
        if (s == null || s.isBlank()) return "BOARD";
        return switch (s) {
            case "BOARD", "EXEC", "ALL_STAFF", "HIGH_RISK_ROLES" -> s;
            default -> "BOARD";
        };
    }

    private static void guardCampaignTransition(String from, String to) {
        Set<String> allowed = CAMPAIGN_TRANSITIONS.getOrDefault(
                Objects.requireNonNullElse(from, ""), Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
    }

    private void emit(String trigger, String entityType, Long entityId,
                       String correlationId, Map<String, Object> payload) {
        publisher.publishEvent(new DomainEvent(
                TenantContext.getCurrentTenant(),
                trigger, entityType, entityId, correlationId,
                payload == null ? Map.of() : payload));
    }
}

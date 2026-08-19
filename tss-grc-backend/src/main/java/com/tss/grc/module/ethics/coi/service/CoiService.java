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
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.iam.service.TenantUserDirectory;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import com.tss.grc.common.security.CurrentActor;
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
    /**
     * F2-244 — the COI registers name a <em>person</em> by {@code user_id} and nothing resolved it.
     *
     * <p>{@code ethics → iam} is an ordinary business edge, not an ADR-002 kernel inversion:
     * {@code UserRepository} is already read from ten modules outside {@code iam}
     * ({@code admin/committee}, {@code evidence}, {@code governance}, {@code risk},
     * {@code signature}, …), and {@code ModuleGraphRatchetTest}'s {@code KERNEL_ISH} set is only
     * {@code platform | integrations | dashboard}. Checked against the ratchet, not assumed.
     */
    private final UserRepository users;
    /** F2-249 — the one place a person reference is resolved against this tenant. */
    private final TenantUserDirectory directory;
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

    /**
     * F2-144 part (a) — who the declaration is FOR, decided by the server.
     *
     * <p>{@code submitDeclaration} took {@code userId} from the request body and stored it verbatim,
     * so nothing bound a declaration to the person filing it. That was latent only because
     * {@code SUBMIT_GUARD} recited two role names that existed in no role at the time
     * ({@code ETHICS_OFFICER} — since seated by V362 — and {@code USER}, which still does not exist),
     * leaving three administrators as the only callers — a lockout that happened to be
     * the sole thing preventing an unbound write. <b>Opening the guard first would have traded the
     * lockout for an impersonation path</b>, which is why this lands before part (b).
     *
     * <p>The rule keeps both real behaviours. An administrator legitimately files on someone's behalf
     * (that is all that has ever worked here), so a caller holding a COI management authority may name
     * any {@code userId}. Everyone else declares for themselves, and the body's {@code userId} is
     * <b>ignored rather than validated</b> — a rejection would leak whether another id exists, and
     * there is no reason for a self-declaration to carry one at all.
     *
     * <p>⚠ The name fields stay caller-supplied and that is now bounded rather than fixed: the row is
     * attributed to a server-chosen {@code user_id}, so a mislabelled name is a data-quality problem
     * against a known declarant, not an attestation attributed to someone who never made it.
     * Resolving names from {@code iam.users} is worth doing and is not this change.
     *
     * <p><b>F2-145(e) slice 6 — "a COI management authority" is now the permission, not three role
     * names.</b> This check recited {@code ROLE_BOARD_SECRETARY │ ROLE_COMPLIANCE_OFFICER │
     * ROLE_ETHICS_OFFICER}, which was exactly {@code CoiController}'s old {@code MANAGE_GUARD} minus the
     * admin. Converting the controller to {@code ETHICS:WRITE} and leaving this list behind would have
     * split one authority in two: a tenant granting {@code ETHICS:WRITE} to a fourth role would get
     * campaign management but silently not the right to file on an employee's behalf — the same
     * every-route-must-agree failure {@code AttachmentAccessPolicy} documents for attachments.
     * {@code ETHICS_WRITE} is the authority {@code ModulePermissionEvaluator} resolves and
     * {@code CustomUserDetailsService} mints, so the two are now the same fact. The population is
     * unchanged: V371 grants {@code ETHICS:WRITE} to precisely those three roles.
     */
    private Long declarantFor(SubmitDeclarationRequest req) {
        Long caller = CurrentActor.userId();
        boolean mayFileForOthers = CurrentActor.isPlatformAdmin() || hasAnyAuthority("ETHICS_WRITE");
        if (mayFileForOthers && req.userId() != null) {
            // F2-244 — this method was rigorous about the AUTHORITY to name somebody else and
            // silent about whether the person it named exists. Both are needed: the authority
            // check decides who MAY file for another, and only a resolution decides whether the
            // other is real. Returning req.userId() unresolved is why 8 of the 8 non-null
            // declaration user ids in dev and prod name no user at all.
            return requireOwnUser(req.userId(), "a conflict-of-interest declaration").getId();
        }
        if (caller == null) {
            throw new IllegalStateException(
                    "Cannot submit a conflict-of-interest declaration without an authenticated "
                    + "declarant — the declaration would be attributable to nobody.");
        }
        return caller;
    }

    /**
     * F2-244 — resolve a caller-supplied {@code user_id} inside the caller's tenant, or refuse.
     *
     * <p><b>A presence test on an id is not a check on the thing it names</b> (F2-243), and here
     * there was not even a presence test: {@code recordRecusal} stored {@code req.userId()}
     * verbatim <em>three lines below</em> a comment announcing that it validates the other
     * foreign key — <em>"If a declaration id is provided, validate it belongs to this tenant."</em>
     * F2-215's two-of-N at its shortest range: {@code declaration_id} got the code check
     * <b>and</b> the database constraint ({@code FK_coi_recusal_decl}), and {@code user_id} got
     * neither.
     *
     * <p>🔑 <b>Why this is not merely a dangling reference.</b> {@code CoiRecusalEvent} has
     * {@code user_id} NOT NULL and <b>no name column of any kind</b>, so the user id is the only
     * thing identifying <em>who recused</em> — and the entity's own javadoc says the subject is a
     * <em>"director, committee member, or senior executive"</em> whose recusal the Compliance
     * Officer reports under SAMA Article 30. Measured identical in dev and prod: <b>3 of 8
     * recusals and 8 of 8 non-null declarations name ids in the 1001–1007 range while
     * {@code MAX(iam.users.id)} is 8</b> — so the register's per-person query
     * ({@code GET /recusals/user/{userId}}) can never reach them from any real person.
     *
     * <p>⚠ <b>This can refuse a request that succeeds today, and that is the point.</b> There is
     * no upstream coercion and no FK, so F2-182's "nothing could break" argument does not apply
     * here — a caller typing 1007 gets 201 today and 400 after. But that 201 stores a recusal
     * attributed to nobody, and the Board Secretariat is a present operator who can correct a
     * 400 naming the id. This is not the public-intake case where a lost record is worse than a
     * mis-graded one (F2-179/F2-183): it is an internal governance register.
     *
     * <p>⚠ One message for "no such user" and "another tenant's user", copied deliberately from
     * {@link com.tss.grc.module.admin.committee.service.CommitteeMemberRegistryService}'s
     * {@code requireUserInTenant} — distinguishing them would let a caller enumerate other
     * tenants' user ids.
     *
     * <p>⚙ What it deliberately does <b>not</b> do: invent a way to name a director who has no
     * platform login. F2-26's committee roster already models that population properly
     * ({@code LINKED | EXTERNAL | UNLINKED} via {@code noPlatformUser}); this register has
     * neither a nullable id nor a name column, so recording an external director's recusal
     * truthfully needs a schema and form change. Filed as an owner decision rather than guessed
     * at here.
     *
     * <p>⚙ F2-249 — the predicate itself now lives in {@link TenantUserDirectory}. It was one of
     * four hand-written copies, and two of them disagreed on whether a blank {@code full_name_en}
     * falls back to the username; F2-190's rule is never to keep a second mechanism beside an
     * existing one. Everything above this line is still the reason THIS register requires a
     * resolvable user, which is a per-column decision the primitive deliberately does not make.
     */
    private User requireOwnUser(Long userId, String what) {
        return directory.require(userId, what);
    }

    private static boolean hasAnyAuthority(String... authorities) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication();
        if (auth == null) {
            return false;
        }
        Set<String> held = auth.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
        for (String a : authorities) {
            if (held.contains(a)) {
                return true;
            }
        }
        return false;
    }

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
        d.setUserId(declarantFor(req));
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

        // F2-228 — ROWVERSION rule: build the items and decide the auto-outcome BEFORE the
        // single save of the declaration.
        //
        // This used to save the declaration, insert the items to get their declarationId,
        // then set reviewOutcome and save the declaration a SECOND time. That is the shape
        // CLAUDE.md's ROWVERSION landmine describes, in its INSERT form: BaseEntity maps
        // row_version @Version insertable=false updatable=false over a SQL Server ROWVERSION,
        // so after the IDENTITY insert the in-memory version is null, and the follow-up
        // UPDATE ... WHERE row_version = null matched zero rows. Probed live: any declaration
        // that disclosed at least ONE item answered HTTP 409 and rolled back — a
        // conflict-of-interest register that could record everything EXCEPT a conflict.
        // A declaration with nothing to disclose worked, because computeAutoOutcome returns
        // PENDING, which is the value already set, so nothing was dirty.
        //
        // The items only need declarationId, and the outcome only reads item fields, so both
        // can be decided first. Nothing here needs the declaration's id (F2-175's rule).
        List<CoiDisclosedItem> items = new java.util.ArrayList<>();
        if (req.disclosedItems() != null) {
            for (DisclosedItemDto dto : req.disclosedItems()) {
                CoiDisclosedItem item = new CoiDisclosedItem();
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
                items.add(item);
            }
        }
        d.setReviewOutcome(computeAutoOutcome(items));

        CoiDeclaration finalDecl = declarationRepo.save(d);

        List<CoiDisclosedItem> savedItems = new java.util.ArrayList<>();
        for (CoiDisclosedItem item : items) {
            item.setDeclarationId(finalDecl.getId());
            savedItems.add(itemRepo.save(item));
        }

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

        // Read the outcome off the SAVED row, not a local copy of what we meant to store
        // (F2-223): the row is what an ethics reviewer will see.
        if ("RECUSAL_REQUIRED".equals(finalDecl.getReviewOutcome())) {
            emit("COI_AUTO_FLAGGED_RECUSAL", "CoiDeclaration", finalDecl.getId(),
                    campaign.getCode(), payload);
        } else if ("MITIGATION_REQUIRED".equals(finalDecl.getReviewOutcome())) {
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
        // F2-244 — resolve the SUBJECT of the recusal, not just the declaration it may cite.
        // The line below used to be `r.setUserId(req.userId())`, and the comment above about
        // validating the declaration is what made it read as covered.
        User subject = requireOwnUser(req.userId(), "a recusal");
        CoiRecusalEvent r = new CoiRecusalEvent();
        r.setOrganisationId(orgId);
        r.setDeclarationId(req.declarationId());
        r.setUserId(subject.getId());
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

        // F2-244 — the subject was resolved on the way in, so the response can name them without
        // a second lookup.
        return RecusalEventResponse.from(saved, subject);
    }

    @Transactional(readOnly = true)
    public List<RecusalEventResponse> listRecusalsByUser(Long userId) {
        List<CoiRecusalEvent> events =
                recusalRepo.findByOrganisationIdAndUserIdOrderByRecusalDateDesc(org(), userId);
        Map<Long, User> subjects = resolveSubjects(events);
        return events.stream()
                .map(e -> RecusalEventResponse.from(e, subjects.get(e.getUserId())))
                .toList();
    }

    /**
     * F2-244 — resolve the recusal subjects for a page of rows in one query, filtering each on the
     * row's own tenant.
     *
     * <p>⚠ The tenant filter is not decoration on a read either. {@code findAllById} is a bare
     * primary-key lookup with no tenant predicate — F2-243's filed plural-load shape — and the
     * exemplar for doing it right is already in the tree: {@code CommitteeMemberRegistryService}'s
     * seat-linkage report does exactly this {@code findAllById} + per-row org check. An
     * unresolvable id yields {@code null}, which the response reports as
     * {@code userResolved = false} rather than as a blank name.
     */
    private Map<Long, User> resolveSubjects(List<CoiRecusalEvent> events) {
        List<Long> ids = events.stream().map(CoiRecusalEvent::getUserId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, User> byId = new HashMap<>();
        if (ids.isEmpty()) return byId;
        for (User u : users.findAllById(ids)) {
            if (Objects.equals(u.getOrganisationId(), org()) && Boolean.TRUE.equals(u.getIsActive())) {
                byId.put(u.getId(), u);
            }
        }
        return byId;
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

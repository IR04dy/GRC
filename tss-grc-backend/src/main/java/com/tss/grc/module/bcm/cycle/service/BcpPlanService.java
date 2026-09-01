package com.tss.grc.module.bcm.cycle.service;

import java.util.Map;
import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.validation.ServerOwnedState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.*;
import com.tss.grc.module.bcm.cycle.repository.*;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

/**
 * BCP / DRP plan builder service — audit T1.4b.
 *
 * <h3>Plan lifecycle</h3>
 * <p>⚠ F2-305 — this documented <b>8 of the 9 edges</b> {@code TRANSITIONS} allows; the one it
 * omitted is {@code APPROVED -> DRAFT}, a reopen of an approved plan. Written as a complete table
 * that {@code TransitionRefusalContractTest} parses and compares against {@code TRANSITIONS}
 * edge-for-edge, so it cannot drift again.
 *
 * <pre>
 *   DRAFT        -> UNDER_REVIEW | CANCELLED
 *   UNDER_REVIEW -> APPROVED | DRAFT
 *   APPROVED     -> PUBLISHED | DRAFT
 *   PUBLISHED    -> ARCHIVED | SUPERSEDED
 *   ARCHIVED     -> (terminal)
 *   SUPERSEDED   -> (terminal)
 *   CANCELLED    -> (terminal)
 * </pre>
 *
 * <h3>Section catalogue (ISO 22301 §8.4.2)</h3>
 * <p>Each plan is created with a stable set of named sections; the user fills
 * the bodies. Adding ad-hoc sections is supported by POSTing a section with
 * a new {@code sectionKey}. Section order is fixed at creation but
 * configurable later via {@link #reorderSections} (not yet implemented).
 *
 * <h3>Versions</h3>
 * <p>{@link #approve} writes a snapshot of all sections to
 * {@link BcpVersion} as JSON, bumps {@code currentVersion}, and stamps
 * {@code approvedAt}. {@link #publish} can only fire on APPROVED; this is
 * deliberate — publishing without a recorded approval would skip the audit
 * trail every regulator wants.
 *
 * <h3>"Generate from BIA"</h3>
 * <p>{@link #generateFromBia} creates a new plan pre-populated with the
 * BIA's RTO/RPO/MTPD and a section catalogue. The MBCO narrative from the
 * BIA seeds the {@code overview} section so the plan author starts with
 * context rather than a blank page.
 *
 * <h3>F8 events emitted</h3>
 * <ul>
 *   <li>{@code BCM_PLAN_APPROVED}    — every approval</li>
 *   <li>{@code BCM_PLAN_PUBLISHED}   — every publication</li>
 *   <li>{@code BCM_PLAN_ARCHIVED}    — every archive (manual or supersede)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BcpPlanService {

    private static final ObjectMapper M = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Set<String> STATUSES = Set.of(
        "DRAFT", "UNDER_REVIEW", "APPROVED", "PUBLISHED", "ARCHIVED", "SUPERSEDED", "CANCELLED");
    /** F2-220 — the state a plan is CREATED in; APPROVED/PUBLISHED are transition()'s, and
     *  it snapshots the sections into a BcpVersion row on the way. */
    private static final String INITIAL_STATUS = "DRAFT";
    private static final Set<String> PLAN_TYPES = Set.of("BCP", "DRP");

    /**
     * Default BCP section catalogue per ISO 22301 §8.4.2 / spec §H. Order is
     * the conventional reading order an auditor expects in a plan document.
     */
    public static final List<SectionDef> BCP_SECTIONS = List.of(
        new SectionDef("overview",            "Plan overview",            "نظرة عامة"),
        new SectionDef("scope",               "Scope & objectives",       "النطاق والأهداف"),
        new SectionDef("roles",               "Roles & responsibilities", "الأدوار والمسؤوليات"),
        new SectionDef("activation_criteria", "Activation criteria",      "معايير التفعيل"),
        new SectionDef("recovery_priorities", "Recovery priorities",      "أولويات التعافي"),
        new SectionDef("recovery_steps",      "Recovery procedures",      "إجراءات التعافي"),
        new SectionDef("comms_plan",          "Communication plan",       "خطة الاتصالات"),
        new SectionDef("manual_workaround",   "Manual workaround",        "البديل اليدوي"),
        new SectionDef("resources",           "Resource requirements",    "متطلبات الموارد"),
        new SectionDef("supplier_escalation", "Supplier escalation",      "تصعيد المورّدين"),
        new SectionDef("return_to_normal",    "Return-to-normal steps",   "خطوات العودة للوضع الطبيعي"),
        new SectionDef("appendices",          "Appendices & evidence",    "الملاحق والأدلة")
    );

    /**
     * DRP catalogue per spec §I. Different shape — emphasis on technical
     * sequencing (failover / validation / rollback) rather than business
     * comms / workaround. Some sections overlap with BCP intentionally.
     */
    public static final List<SectionDef> DRP_SECTIONS = List.of(
        new SectionDef("overview",            "System overview",          "نظرة عامة على النظام"),
        new SectionDef("business_linkage",    "Business service linkage", "ربط بالخدمات التجارية"),
        new SectionDef("recovery_objectives", "Recovery objectives",      "أهداف التعافي"),
        new SectionDef("dependencies",        "Dependencies",             "التبعيات"),
        new SectionDef("backup_replication",  "Backup & replication",     "النسخ الاحتياطي والنسخ المتماثل"),
        new SectionDef("recovery_sequence",   "Recovery sequence",        "تسلسل التعافي"),
        new SectionDef("failover",            "Failover procedure",       "إجراء التحويل"),
        new SectionDef("validation",          "Validation checklist",     "قائمة التحقق"),
        new SectionDef("rollback",            "Rollback procedure",       "إجراء التراجع"),
        new SectionDef("contacts",            "Contacts & escalation",    "جهات الاتصال والتصعيد"),
        new SectionDef("test_results",        "Test results",             "نتائج الاختبار"),
        new SectionDef("evidence",            "Evidence",                 "الأدلة")
    );

    public record SectionDef(String key, String titleEn, String titleAr) {}

    /** Returned by {@link #diff} — JSON snapshot of two versions side by side. */
    public record VersionDiff(int from, int to, String fromJson, String toJson) {}

    private final BcpPlanRepository plans;
    private final BcpSectionRepository sections;
    private final BcpVersionRepository versions;
    private final BcmBiaAssessmentRepository bias;
    private final BcpPlanAcknowledgementRepository acks;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // ─── CRUD ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<BcpPlan> list(String planType, int page, int size) {
        Long org = TenantContext.getCurrentTenant();
        // F2-393: validate page/size up front — exactly as the no-planType branch's
        // PageRequest already does — so a negative page cannot reach the manual paging
        // slice below. Without this, `from = Math.min(page*size, all.size())` has no
        // lower-bound floor, so ?planType=BCP&page=-1 makes `from` negative and
        // all.subList(-N, 0) throws IndexOutOfBoundsException (an opaque 500), where the
        // no-planType branch returns a clean 400. PageRequest.of refuses page<0 / size<1
        // with an IllegalArgumentException the handler maps to 400 — consistent for both.
        PageRequest pageable = PageRequest.of(page, size);
        if (planType != null) {
            // Filter via the repo + manual paging slice (no pageable for this finder)
            List<BcpPlan> all = plans.findByOrganisationIdAndPlanTypeOrderByIdDesc(org, planType);
            int from = Math.min(page * size, all.size());
            int to   = Math.min(from + size, all.size());
            return new org.springframework.data.domain.PageImpl<>(
                all.subList(from, to), pageable, all.size());
        }
        return plans.findByOrganisationIdOrderByIdDesc(org, pageable);
    }

    @Transactional(readOnly = true)
    public BcpPlan get(Long id) {
        return plans.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<BcpSection> sectionsFor(Long planId) {
        Long org = TenantContext.getCurrentTenant();
        get(planId);                             // tenant guard
        return sections.findByOrganisationIdAndPlanIdOrderBySectionOrderAsc(org, planId);
    }

    @Transactional(readOnly = true)
    public List<BcpVersion> versionsFor(Long planId) {
        Long org = TenantContext.getCurrentTenant();
        get(planId);
        return versions.findByOrganisationIdAndPlanIdOrderByVersionNumberDesc(org, planId);
    }

    @Transactional
    public BcpPlan create(BcpPlan body) {
        validateOnCreate(body);
        body.setId(null);
        Long org = TenantContext.getCurrentTenant();
        body.setOrganisationId(org);
        // F2-220 — a DEFAULT with no vocabulary check at all, so a plan could be created
        // PUBLISHED: never reviewed, never approved, and with no BcpVersion snapshot, which is
        // the artefact recording what was approved. isLocked() then refuses to edit it.
        body.setStatus(ServerOwnedState.requireInitial(
            body.getStatus(), INITIAL_STATUS, "a continuity plan",
            "POST /api/v1/bcm/plans/{id}/transition"));
        body.setApprovedBy(null);
        body.setApprovedAt(null);
        body.setPublishedAt(null);      // transition()'s; see BcpPlan#publishedAt
        // F2-234 — F2-220 closed status/approvedBy/approvedAt/publishedAt on THIS create and left
        // currentVersion as a DEFAULT one line below them, so a caller could file a brand-new plan
        // claiming to be at v7 with no BcpVersion row behind it. snapshot() owns this counter: it
        // derives the next number from the versions table, so the first approval would silently
        // reduce 7 → 1. The version on a continuity plan is what a recovery team cites.
        // 🔑 F2-215: a reader who finds four fields scrubbed does not ask what the fifth is.
        body.setCurrentVersion(1);
        // F2-330 — the attestation trail (last_attested_at / _by_name / _by_id) is
        // PlanReviewService.attest()'s alone, and POST /{id}/attest is hasPermission('BCM',
        // 'APPROVE') — a HIGHER grant than the BCM:WRITE this create carries. Bound from the
        // entity and left unscrubbed, a BCM:WRITE holder could POST a brand-new plan already
        // stamped "last reviewed by X on Y": BcpPlan#lastAttestedAt's own comment says the UI
        // renders exactly that, and reviewState filters treat an attested plan as review-current
        // — so an unreviewed continuity plan passes the currency filter the APPROVE-gated attest
        // route exists to earn. Not a same-permission control-defeat (F2-329) — a privilege
        // boundary crossed by mass assignment.
        // 🔑 F2-215's two-of-N, a SIXTH time on this create: F2-220 scrubbed status/approvedBy/
        // approvedAt/publishedAt and F2-234 added currentVersion, each under a comment about the
        // field a reader stops looking for — and the attestation trail was the field after that.
        // Only lastAttestedAt is authority-shaped enough for the scan to name; _by_name/_by_id
        // are the same trail (F2-236) and forgeable in the same breath, so all three go together.
        body.setLastAttestedAt(null);
        body.setLastAttestedByName(null);
        body.setLastAttestedById(null);
        if (body.getPlanCode() == null || body.getPlanCode().isBlank()) {
            body.setPlanCode(nextCode(body.getPlanType()));
        }
        BcpPlan saved = plans.save(body);
        createDefaultSections(saved);
        audit.log("CREATE", "BcpPlan", saved.getId(),
            "Plan created: " + saved.getPlanCode() + " (" + saved.getPlanType() + ")");
        return saved;
    }

    @Transactional
    public BcpPlan update(Long id, BcpPlan patch) {
        BcpPlan existing = get(id);
        if (isLocked(existing.getStatus()))
            throw new IllegalArgumentException(
                "Cannot edit a " + existing.getStatus() + " plan — reopen via transition first");
        // F2-297 — was a hand-rolled copy of the rule F2-296 shared: tolerate an ECHO of the
        // stored status (the edit form round-trips the record) and refuse a CHANGE, which belongs
        // to transition(). Three things were wrong with the copy. The bare equals could not see
        // that the column collates case-insensitively, so a case-variant echo read as a change and
        // was refused (F2-179 — latent, 0 non-canonical values in either database). The message
        // named a JAVA METHOD a caller cannot call rather than the route (F2-210). And it sat over
        // an entity field initialised to "DRAFT", so an omitted status arrived as "DRAFT" and the
        // comparison fired for every plan that was neither DRAFT nor locked — see BcpPlan#status.
        ServerOwnedState.requireUnchanged(
            patch.getStatus(), existing.getStatus(), "status", "Plan " + id,
            "POST /api/v1/bcm/plans/" + id + "/transition, which walks the lifecycle and snapshots"
            + " the version being approved",
            null);

        if (patch.getTitleEn()           != null) existing.setTitleEn(patch.getTitleEn());
        if (patch.getTitleAr()           != null) existing.setTitleAr(patch.getTitleAr());
        if (patch.getServiceId()         != null) existing.setServiceId(patch.getServiceId());
        if (patch.getProcessId()         != null) existing.setProcessId(patch.getProcessId());
        if (patch.getRtoHours()          != null) existing.setRtoHours(patch.getRtoHours());
        if (patch.getRpoHours()          != null) existing.setRpoHours(patch.getRpoHours());
        if (patch.getMtpdHours()         != null) existing.setMtpdHours(patch.getMtpdHours());
        if (patch.getPlanOwnerId()       != null) existing.setPlanOwnerId(patch.getPlanOwnerId());
        if (patch.getResponseTeamRole()  != null) existing.setResponseTeamRole(patch.getResponseTeamRole());
        if (patch.getNextReviewDate()    != null) existing.setNextReviewDate(patch.getNextReviewDate());
        existing.setUpdatedAt(LocalDateTime.now());
        BcpPlan saved = plans.save(existing);
        audit.log("UPDATE", "BcpPlan", saved.getId(),
            "Plan updated: " + saved.getPlanCode());
        return saved;
    }

    // ─── Section editing ─────────────────────────────────────────

    @Transactional
    public BcpSection updateSection(Long sectionId, String bodyEn, String bodyAr, String editor) {
        Long org = TenantContext.getCurrentTenant();
        BcpSection s = sections.findByIdAndOrganisationId(sectionId, org)
            .orElseThrow(() -> new EntityNotFoundException("Section not found: " + sectionId));
        BcpPlan plan = get(s.getPlanId());
        if (isLocked(plan.getStatus()))
            throw new IllegalArgumentException(
                "Cannot edit sections of a " + plan.getStatus() + " plan — reopen the plan first");

        if (bodyEn != null) s.setBodyEn(bodyEn);
        if (bodyAr != null) s.setBodyAr(bodyAr);
        s.setLastEditedBy(editor);
        s.setLastEditedAt(LocalDateTime.now());
        s.setUpdatedAt(s.getLastEditedAt());
        BcpSection saved = sections.save(s);
        audit.log("UPDATE", "BcpSection", saved.getId(),
            "Section edited: " + plan.getPlanCode() + " / " + saved.getSectionKey());
        return saved;
    }

    @Transactional
    public BcpSection addSection(Long planId, BcpSection body) {
        Long org = TenantContext.getCurrentTenant();
        BcpPlan plan = get(planId);
        if (isLocked(plan.getStatus()))
            throw new IllegalArgumentException("Cannot add sections to a " + plan.getStatus() + " plan");
        if (body.getSectionKey() == null || body.getSectionKey().isBlank())
            throw new IllegalArgumentException("sectionKey is required");
        if (body.getSectionTitleEn() == null || body.getSectionTitleEn().isBlank())
            throw new IllegalArgumentException("sectionTitleEn is required");

        sections.findByOrganisationIdAndPlanIdAndSectionKey(org, planId, body.getSectionKey())
            .ifPresent(s -> { throw new IllegalArgumentException(
                "Section key already exists on this plan: " + s.getSectionKey()); });

        if (body.getSectionOrder() == null) {
            int max = sections.findByOrganisationIdAndPlanIdOrderBySectionOrderAsc(org, planId).stream()
                .mapToInt(BcpSection::getSectionOrder).max().orElse(0);
            body.setSectionOrder(max + 1);
        }
        body.setId(null);
        body.setOrganisationId(org);
        body.setPlanId(planId);
        // F2-234 — updateSection() stamps lastEditedAt from the server's clock and lastEditedBy
        // from the acting principal. A section that has just been ADDED has never been edited, so
        // it cannot carry either; accepting them let a caller claim an edit history for a record
        // with none. The CREATE is recorded by the audit row below, and BaseEntity's createdAt.
        // ⚠ The two provenance markers in this column ("template:<code>", "BIA generator", 21 of
        // 120 rows in dev and prod) are written by paths that build BcpSection directly —
        // BcmLibraryController and generateFromBia — never through addSection, whose only caller
        // is its own route. Checked, not assumed: clearing here cannot break either generator.
        body.setLastEditedAt(null);
        body.setLastEditedBy(null);
        BcpSection saved = sections.save(body);
        audit.log("CREATE", "BcpSection", saved.getId(),
            "Section added: " + plan.getPlanCode() + " / " + saved.getSectionKey());
        return saved;
    }

    // ─── State machine ───────────────────────────────────────────

    /**
     * The register's declared edges, in ONE place. F2-303 — this was an inline
     * {@code switch} of {@code Set.of(...).contains(...)}, so the legal-target list was not
     * derivable at runtime and the refusal below could only say which move was rejected, never
     * which moves would be accepted. Terminal states are present with an <b>empty</b> set rather
     * than omitted, so the terminal set is derivable from the machine instead of typed out a
     * second time ([[F2-196]]).
     */
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "DRAFT",        Set.of("UNDER_REVIEW", "CANCELLED"),
        "UNDER_REVIEW", Set.of("APPROVED", "DRAFT"),
        "APPROVED",     Set.of("PUBLISHED", "DRAFT"),
        "PUBLISHED",    Set.of("ARCHIVED", "SUPERSEDED"),
        "ARCHIVED",     Set.of(),
        "SUPERSEDED",   Set.of(),
        "CANCELLED",    Set.of());

    @Transactional
    public BcpPlan transition(Long id, String toStatus, String actor) {
        BcpPlan p = get(id);
        if (toStatus == null || !STATUSES.contains(toStatus))
            throw new IllegalArgumentException("status must be one of " + STATUSES);

        String from = p.getStatus();
        Set<String> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(toStatus))
            throw new IllegalTransitionException(from, toStatus, allowed);

        LocalDateTime now = LocalDateTime.now();
        p.setStatus(toStatus);

        if ("APPROVED".equals(toStatus)) {
            // Snapshot all sections into a new BcpVersion row
            snapshot(p, actor, "Approved");
            p.setApprovedBy(actor);
            p.setApprovedAt(now);
            eventPublisher.publishEvent(DomainEvent.of(
                p.getOrganisationId(), "BCM_PLAN_APPROVED", "BcpPlan", p.getId(),
                Map.of("plan_code", p.getPlanCode(),
                       "plan_type", p.getPlanType(),
                       "version",   p.getCurrentVersion())));
        }
        if ("PUBLISHED".equals(toStatus)) {
            p.setPublishedAt(now);
            eventPublisher.publishEvent(DomainEvent.of(
                p.getOrganisationId(), "BCM_PLAN_PUBLISHED", "BcpPlan", p.getId(),
                Map.of("plan_code", p.getPlanCode(),
                       "plan_type", p.getPlanType(),
                       "version",   p.getCurrentVersion())));
        }
        if ("ARCHIVED".equals(toStatus) || "SUPERSEDED".equals(toStatus)) {
            eventPublisher.publishEvent(DomainEvent.of(
                p.getOrganisationId(), "BCM_PLAN_ARCHIVED", "BcpPlan", p.getId(),
                Map.of("plan_code", p.getPlanCode(),
                       "plan_type", p.getPlanType(),
                       "reason",    toStatus)));
        }
        if ("DRAFT".equals(toStatus) && ("UNDER_REVIEW".equals(from) || "APPROVED".equals(from))) {
            // Reopen — bump the version pointer so the next approval is a fresh snapshot
            p.setCurrentVersion(p.getCurrentVersion() + 1);
        }
        p.setUpdatedAt(now);
        BcpPlan saved = plans.save(p);
        audit.log("TRANSITION", "BcpPlan", saved.getId(),
            "Plan " + saved.getPlanCode() + " " + from + " → " + toStatus);
        return saved;
    }

    /** Convenience wrapper used by reports/UX: same as {@code transition(id, "DRAFT")}. */
    @Transactional
    public BcpPlan reopen(Long id, String actor) {
        return transition(id, "DRAFT", actor);
    }

    // ─── Generate from BIA ───────────────────────────────────────

    /**
     * Spawn a new BCP or DRP from an APPROVED / ACTIVE BIA.
     * Copies RTO/RPO/MTPD onto the plan, creates the section catalogue, and
     * seeds the {@code overview} section with the BIA's MBCO narrative so
     * the author starts with context.
     */
    @Transactional
    public BcpPlan generateFromBia(Long biaId, String planType, String titleEn, String titleAr) {
        if (planType == null || !PLAN_TYPES.contains(planType))
            throw new IllegalArgumentException("planType must be BCP or DRP");
        BcmBiaAssessment bia = bias.findByIdAndOrganisationId(biaId, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("BIA not found: " + biaId));
        String bs = bia.getApprovalStatus();
        if (!"APPROVED".equals(bs) && !"ACTIVE".equals(bs))
            throw new IllegalArgumentException(
                "BIA must be APPROVED or ACTIVE to generate a plan (current=" + bs + ")");

        BcpPlan plan = new BcpPlan();
        plan.setPlanType(planType);
        plan.setSourceBiaId(bia.getId());
        plan.setServiceId(bia.getServiceId());
        plan.setProcessId(bia.getProcessId());
        plan.setRtoHours(bia.getRtoHours());
        plan.setRpoHours(bia.getRpoHours());
        plan.setMtpdHours(bia.getMtpdHours());
        plan.setTitleEn(titleEn != null && !titleEn.isBlank()
            ? titleEn : (planType + " from " + bia.getBiaCode()));
        plan.setTitleAr(titleAr);
        BcpPlan saved = create(plan);

        // Pre-populate the section bodies from the BIA's captured data so the
        // generated plan starts with real content (recovery objectives, MBCO,
        // minimum resources, manual workaround, suppliers, peak periods) rather
        // than an empty catalogue.
        seedSectionsFromBia(saved, bia);

        // Also update the BIA's generatedBcpId / generatedDrpId so the BIA register reflects linkage
        if ("BCP".equals(planType)) bia.setGeneratedBcpId(saved.getId());
        else                        bia.setGeneratedDrpId(saved.getId());
        bias.save(bia);

        audit.log("CUSTOM", "BcpPlan", saved.getId(),
            "Plan generated from BIA " + bia.getBiaCode() + " → " + saved.getPlanCode());
        return saved;
    }

    // ─── Versions + diff ─────────────────────────────────────────

    /**
     * Side-by-side JSON diff between two versions of the same plan. Returns
     * the two payloads pretty-printed so a UI can render them in twin
     * &lt;pre&gt; blocks. Real diff colouring is a frontend concern.
     */
    @Transactional(readOnly = true)
    public VersionDiff diff(Long planId, int fromVersion, int toVersion) {
        Long org = TenantContext.getCurrentTenant();
        get(planId);
        BcpVersion from = versions.findByOrganisationIdAndPlanIdAndVersionNumber(org, planId, fromVersion)
            .orElseThrow(() -> new EntityNotFoundException("Version " + fromVersion + " not found"));
        BcpVersion to = versions.findByOrganisationIdAndPlanIdAndVersionNumber(org, planId, toVersion)
            .orElseThrow(() -> new EntityNotFoundException("Version " + toVersion + " not found"));
        return new VersionDiff(fromVersion, toVersion, from.getBodyJson(), to.getBodyJson());
    }

    // ─── Stats ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Long> out = new LinkedHashMap<>();
        for (String s : STATUSES) {
            out.put("status_" + s.toLowerCase(Locale.ROOT),     plans.countByOrganisationIdAndStatus(org, s));
            out.put("bcp_"    + s.toLowerCase(Locale.ROOT),     plans.countByOrganisationIdAndPlanTypeAndStatus(org, "BCP", s));
            out.put("drp_"    + s.toLowerCase(Locale.ROOT),     plans.countByOrganisationIdAndPlanTypeAndStatus(org, "DRP", s));
        }
        return out;
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private void validateOnCreate(BcpPlan p) {
        if (p.getTitleEn() == null || p.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (p.getPlanType() == null || !PLAN_TYPES.contains(p.getPlanType()))
            throw new IllegalArgumentException("planType must be BCP or DRP");
    }

    private boolean isLocked(String status) {
        return "APPROVED".equals(status) || "PUBLISHED".equals(status)
            || "ARCHIVED".equals(status) || "SUPERSEDED".equals(status)
            || "CANCELLED".equals(status);
    }

    /**
     * Populate section bodies from a source BIA. Each BIA datum is routed to the
     * section where a plan author would expect it; sections with no BIA source
     * are left blank for the author. Bilingual scaffolding (EN + AR) is written
     * for the structured/numeric data; free-text fields keep the author's
     * original language in {@code bodyEn} and leave {@code bodyAr} for them.
     */
    private void seedSectionsFromBia(BcpPlan plan, BcmBiaAssessment bia) {
        final String code = bia.getBiaCode();
        final boolean drp = "DRP".equals(plan.getPlanType());

        // Recovery objectives — RTO/RPO/MTPD + criticality inherited from the BIA.
        StringBuilder en = new StringBuilder("Recovery targets inherited from BIA ").append(code).append(":\n");
        StringBuilder ar = new StringBuilder("أهداف التعافي الموروثة من تحليل الأثر ").append(code).append(":\n");
        if (bia.getMtpdHours() != null) { en.append("- MTPD: ").append(bia.getMtpdHours()).append(" h\n");
                                          ar.append("- أقصى فترة تعطل مقبولة (MTPD): ").append(bia.getMtpdHours()).append(" ساعة\n"); }
        if (bia.getRtoHours() != null)  { en.append("- RTO: ").append(bia.getRtoHours()).append(" h\n");
                                          ar.append("- زمن التعافي المستهدف (RTO): ").append(bia.getRtoHours()).append(" ساعة\n"); }
        if (bia.getRpoHours() != null)  { en.append("- RPO: ").append(bia.getRpoHours()).append(" h\n");
                                          ar.append("- نقطة الاسترجاع المستهدفة (RPO): ").append(bia.getRpoHours()).append(" ساعة\n"); }
        if (notBlank(bia.getOverallCriticality())) { en.append("- Criticality: ").append(bia.getOverallCriticality()).append("\n");
                                          ar.append("- مستوى الأهمية: ").append(bia.getOverallCriticality()).append("\n"); }
        writeSection(plan, drp ? "recovery_objectives" : "recovery_priorities", en.toString(), ar.toString());

        // MBCO narrative → BCP overview / DRP business-service linkage.
        if (notBlank(bia.getMbcoDescription())) {
            writeSection(plan, drp ? "business_linkage" : "overview",
                "MBCO (from BIA " + code + "):\n\n" + bia.getMbcoDescription(),
                "الحد الأدنى لاستمرارية الأعمال (من تحليل الأثر " + code + "):\n\n" + bia.getMbcoDescription());
        }

        // Minimum resource requirements → BCP resources / DRP dependencies.
        StringBuilder res = new StringBuilder();
        if (bia.getMinStaff() != null)            res.append("- Minimum staff: ").append(bia.getMinStaff()).append("\n");
        if (notBlank(bia.getMinTechText()))       res.append("- Minimum technology: ").append(bia.getMinTechText()).append("\n");
        if (notBlank(bia.getMinFacilityText()))   res.append("- Minimum facility: ").append(bia.getMinFacilityText()).append("\n");
        if (notBlank(bia.getMinSupplierText()))   res.append("- Minimum suppliers: ").append(bia.getMinSupplierText()).append("\n");
        if (res.length() > 0) {
            writeSection(plan, drp ? "dependencies" : "resources",
                "Minimum resource requirements (from BIA " + code + "):\n" + res, null);
        }

        // BCP-only sections.
        if (!drp) {
            String mw = Boolean.TRUE.equals(bia.getManualWorkaroundAvailable())
                ? (notBlank(bia.getManualWorkaroundDescription())
                    ? bia.getManualWorkaroundDescription()
                    : "Manual workaround available — see BIA " + code + ".")
                : "No manual workaround available (per BIA " + code + ").";
            writeSection(plan, "manual_workaround", mw, null);

            if (notBlank(bia.getMinSupplierText()))
                writeSection(plan, "supplier_escalation",
                    "Critical suppliers (from BIA " + code + "):\n" + bia.getMinSupplierText(), null);

            if (notBlank(bia.getPeakPeriodsText()))
                writeSection(plan, "scope",
                    "Peak / blackout periods (from BIA " + code + "): " + bia.getPeakPeriodsText(), null);
        }
    }

    /** Set a section's body by key, if that section exists on the plan. */
    private void writeSection(BcpPlan plan, String key, String bodyEn, String bodyAr) {
        sections.findByOrganisationIdAndPlanIdAndSectionKey(plan.getOrganisationId(), plan.getId(), key)
            .ifPresent(sec -> {
                sec.setBodyEn(bodyEn);
                if (bodyAr != null) sec.setBodyAr(bodyAr);
                sec.setLastEditedBy("BIA generator");
                sec.setLastEditedAt(LocalDateTime.now());
                sections.save(sec);
            });
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private void createDefaultSections(BcpPlan plan) {
        List<SectionDef> catalogue = "DRP".equals(plan.getPlanType()) ? DRP_SECTIONS : BCP_SECTIONS;
        int order = 1;
        for (SectionDef def : catalogue) {
            BcpSection s = new BcpSection();
            s.setOrganisationId(plan.getOrganisationId());
            s.setPlanId(plan.getId());
            s.setSectionKey(def.key());
            s.setSectionTitleEn(def.titleEn());
            s.setSectionTitleAr(def.titleAr());
            s.setSectionOrder(order++);
            sections.save(s);
        }
    }

    /**
     * Build a snapshot JSON payload and persist as a new {@link BcpVersion}.
     * Bumps {@code plan.currentVersion} based on the existing latest version
     * (defensive against gaps from skipped reopens).
     */
    private void snapshot(BcpPlan plan, String actor, String changeSummary) {
        Long org = plan.getOrganisationId();
        int latest = versions
            .findByOrganisationIdAndPlanIdOrderByVersionNumberDesc(org, plan.getId())
            .stream().findFirst().map(BcpVersion::getVersionNumber).orElse(0);
        int next = latest + 1;
        plan.setCurrentVersion(next);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plan", planSnapshotMap(plan));
        body.put("sections", sections
            .findByOrganisationIdAndPlanIdOrderBySectionOrderAsc(org, plan.getId())
            .stream().map(BcpPlanService::sectionSnapshotMap).toList());
        String json;
        try { json = M.writeValueAsString(body); }
        catch (JsonProcessingException e) { throw new RuntimeException("Snapshot serialisation failed", e); }

        BcpVersion v = new BcpVersion();
        v.setOrganisationId(org);
        v.setPlanId(plan.getId());
        v.setVersionNumber(next);
        v.setBodyJson(json);
        v.setApprovedBy(actor);
        v.setApprovedAt(LocalDateTime.now());
        v.setChangeSummary(changeSummary);
        versions.save(v);

        // Tier-2 #11: bumping the version invalidates older distribution acks.
        // Recipients who acknowledged v1 must re-acknowledge v2 — so we flip
        // PENDING/ACKNOWLEDGED/DECLINED rows for prior versions to STALE here.
        // No-op when the plan has no distribution history yet.
        acks.markStaleBelow(org, plan.getId(), next);
    }

    private static Map<String, Object> planSnapshotMap(BcpPlan p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planCode",        p.getPlanCode());
        m.put("titleEn",         p.getTitleEn());
        m.put("titleAr",         p.getTitleAr());
        m.put("planType",        p.getPlanType());
        m.put("rtoHours",        p.getRtoHours());
        m.put("rpoHours",        p.getRpoHours());
        m.put("mtpdHours",       p.getMtpdHours());
        m.put("planOwnerId",     p.getPlanOwnerId());
        m.put("responseTeamRole", p.getResponseTeamRole());
        m.put("nextReviewDate",  p.getNextReviewDate() == null ? null : p.getNextReviewDate().toString());
        return m;
    }

    private static Map<String, Object> sectionSnapshotMap(BcpSection s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sectionKey",      s.getSectionKey());
        m.put("sectionTitleEn",  s.getSectionTitleEn());
        m.put("sectionTitleAr",  s.getSectionTitleAr());
        m.put("sectionOrder",    s.getSectionOrder());
        m.put("bodyEn",          s.getBodyEn());
        m.put("bodyAr",          s.getBodyAr());
        return m;
    }

    private String nextCode(String planType) {
        Long org = TenantContext.getCurrentTenant();
        int year = Year.now().getValue();
        String prefix = "BCP".equals(planType) ? "BCP" : "DRP";
        // Cheap deterministic counter via current page-1 total
        long count = plans.findByOrganisationIdOrderByIdDesc(org, PageRequest.of(0, 1)).getTotalElements();
        return String.format(Locale.ROOT, "%s-%d-%06d", prefix, year, count + 1);
    }
}

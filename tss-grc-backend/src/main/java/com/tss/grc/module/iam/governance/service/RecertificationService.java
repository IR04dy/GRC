package com.tss.grc.module.iam.governance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.CreateCampaignRequest;
import com.tss.grc.module.iam.governance.entity.AccessReviewCampaign;
import com.tss.grc.module.iam.governance.entity.AccessReviewItem;
import com.tss.grc.module.iam.governance.repository.AccessRequestRepository;
import com.tss.grc.module.iam.governance.repository.AccessReviewCampaignRepository;
import com.tss.grc.module.iam.governance.repository.AccessReviewItemRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService.AuditEventBuilder;
import com.tss.grc.module.platform.service.NotificationService;
import com.tss.grc.module.reporting.service.PdfTheme;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RBAC Access-Governance P2 (plan §6.5) — access recertification campaigns.
 * Creation snapshots the org's live assignments into review items (scope:
 * PRIVILEGED / MODULE / ALL); decisions are CERTIFY / REVOKE / MODIFY — REVOKE
 * auto-executes by hard-deleting the {@code iam.user_roles} row. A reviewer cannot
 * disposition their own access (runtime SoD). Closure is blocked until every item
 * is decided, unless a GRC_ADMINISTRATOR closes with a documented exception.
 * Windows are stamped Hijri + Gregorian (NCA ECC 2-2 periodic-review evidence).
 */
@Service
@RequiredArgsConstructor
public class RecertificationService {

    private static final Logger log = LoggerFactory.getLogger(RecertificationService.class);

    private final AccessReviewCampaignRepository campaigns;
    private final AccessReviewItemRepository items;
    private final AccessRequestRepository assignments;
    private final SodService sod;
    private final AuditLogService audit;
    private final NotificationService notifications;

    public record CampaignView(AccessReviewCampaign campaign, long total, long pending) {}

    // ── campaigns ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CampaignView> list() {
        return campaigns.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(org()).stream()
            .map(c -> new CampaignView(c,
                items.countByCampaignIdAndIsDeletedFalse(c.getId()),
                items.countByCampaignIdAndDecisionAndIsDeletedFalse(c.getId(), "PENDING")))
            .toList();
    }

    @Transactional
    public CampaignView create(CreateCampaignRequest r) {
        Long org = org();
        if ("MODULE".equals(r.scope()) && (r.scopeModule() == null || r.scopeModule().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope MODULE requires scopeModule");
        }
        if (!r.windowEnd().isAfter(r.windowStart())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "windowEnd must be after windowStart");
        }
        AccessReviewCampaign c = new AccessReviewCampaign();
        c.setOrganisationId(org);
        c.setTitleEn(r.titleEn());
        c.setTitleAr(r.titleAr());
        c.setScope(r.scope());
        c.setScopeModule("MODULE".equals(r.scope()) ? r.scopeModule().toUpperCase() : null);
        c.setWindowStart(r.windowStart());
        c.setWindowEnd(r.windowEnd());
        c.setWindowHijri(PdfTheme.formatHijri(r.windowStart()) + " – " + PdfTheme.formatHijri(r.windowEnd()));
        c.setReviewerUserId(r.reviewerUserId());   // V294 — optional reviewer scoping
        AccessReviewCampaign saved = campaigns.save(c);

        int generated = items.generateItems(org, saved.getId(), saved.getScope(), saved.getScopeModule());
        audit.recordEvent(new AuditEventBuilder()
            .actionType("CREATE").sourceModule("IAM").entityType("AccessReviewCampaign")
            .entityId(saved.getId())
            .message("Recertification campaign created (" + saved.getScope() + "): "
                + saved.getTitleEn() + " — " + generated + " item(s)"));
        return new CampaignView(saved, generated, generated);
    }

    /** V294 (plan §6.3) — JML MOVER: a targeted USER-scoped mini-review of one user's grants. */
    @Transactional
    public AccessReviewCampaign moverMiniReview(Long org, Long userId, String username) {
        AccessReviewCampaign c = new AccessReviewCampaign();
        c.setOrganisationId(org);
        c.setTitleEn("MOVER access review — " + username);
        c.setTitleAr("مراجعة صلاحيات المنقول — " + username);
        c.setScope("USER");
        c.setScopeUserId(userId);
        c.setWindowStart(LocalDate.now());
        c.setWindowEnd(LocalDate.now().plusDays(14));
        c.setWindowHijri(PdfTheme.formatHijri(c.getWindowStart()) + " – " + PdfTheme.formatHijri(c.getWindowEnd()));
        AccessReviewCampaign saved = campaigns.save(c);
        int generated = items.generateItemsForUser(org, saved.getId(), userId);
        audit.recordEvent(new AuditEventBuilder()
            .actionType("CREATE").sourceModule("IAM").entityType("AccessReviewCampaign")
            .entityId(saved.getId())
            .message("MOVER mini-review opened for " + username + " (" + generated + " assignment(s))"));
        return saved;
    }

    // ── V294 cadence auto-scheduling (plan §6.5: quarterly privileged / semi-annual all) ──

    /** Daily 03:00 UTC: open the R1/R2 cadence campaigns when the period has none, and
     *  nudge GRC_ADMINISTRATOR about overdue open campaigns. Off-request-safe. */
    @Scheduled(cron = "0 0 3 * * *")
    public void cadenceScheduler() {
        for (Long org : assignments.distinctOrganisationIds()) {
            try {
                TenantContext.setCurrentTenant(org);
                autoScheduleForOrg(org);
            } catch (RuntimeException e) {
                log.warn("recertification cadence scheduler failed for org {}: {}", org, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Transactional
    public void autoScheduleForOrg(Long org) {
        LocalDate today = LocalDate.now();
        LocalDate quarterStart = today.withDayOfMonth(1).withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1);
        LocalDate halfStart = today.withDayOfMonth(1).withMonth(today.getMonthValue() <= 6 ? 1 : 7);
        int q = (today.getMonthValue() - 1) / 3 + 1;

        if (!campaigns.existsByOrganisationIdAndScopeAndWindowStartGreaterThanEqualAndIsDeletedFalse(
                org, "PRIVILEGED", quarterStart)) {
            openAutoCampaign(org, "PRIVILEGED",
                "Q" + q + "-" + today.getYear() + " privileged access review",
                "مراجعة الصلاحيات المميزة للربع " + q + " — " + today.getYear(), today, today.plusDays(21));
        }
        if (!campaigns.existsByOrganisationIdAndScopeAndWindowStartGreaterThanEqualAndIsDeletedFalse(
                org, "ALL", halfStart)) {
            openAutoCampaign(org, "ALL",
                "H" + (today.getMonthValue() <= 6 ? 1 : 2) + "-" + today.getYear() + " full access review",
                "المراجعة النصف سنوية الشاملة للصلاحيات — " + today.getYear(), today, today.plusDays(30));
        }
        // Overdue nudge (plan §6.5 SLA): open campaigns past their window with pending items.
        for (AccessReviewCampaign c : campaigns
                .findByOrganisationIdAndStatusAndWindowEndBeforeAndIsDeletedFalse(org, "OPEN", today)) {
            long pending = items.countByCampaignIdAndDecisionAndIsDeletedFalse(c.getId(), "PENDING");
            if (pending == 0) continue;
            notifications.broadcastToRole(org, "GRC_ADMINISTRATOR",
                "Access review overdue: " + c.getTitleEn(),
                "مراجعة صلاحيات متأخرة: " + (c.getTitleAr() == null ? c.getTitleEn() : c.getTitleAr()),
                pending + " item(s) still pending past the review window (" + c.getWindowEnd() + ").",
                "ACCESS_REVIEW", "AccessReviewCampaign", c.getId());
        }
    }

    private void openAutoCampaign(Long org, String scope, String titleEn, String titleAr,
                                  LocalDate start, LocalDate end) {
        AccessReviewCampaign c = new AccessReviewCampaign();
        c.setOrganisationId(org);
        c.setTitleEn(titleEn);
        c.setTitleAr(titleAr);
        c.setScope(scope);
        c.setWindowStart(start);
        c.setWindowEnd(end);
        c.setWindowHijri(PdfTheme.formatHijri(start) + " – " + PdfTheme.formatHijri(end));
        AccessReviewCampaign saved = campaigns.save(c);
        int generated = items.generateItems(org, saved.getId(), scope, null);
        audit.recordEvent(new AuditEventBuilder()
            .actionType("CREATE").sourceModule("IAM").entityType("AccessReviewCampaign")
            .entityId(saved.getId())
            .message("Cadence campaign auto-opened (" + scope + "): " + titleEn + " — " + generated + " item(s)"));
        notifications.broadcastToRole(org, "GRC_ADMINISTRATOR",
            "Access review opened: " + titleEn, "فُتحت مراجعة صلاحيات: " + titleAr,
            generated + " assignment(s) to disposition by " + end + ".",
            "ACCESS_REVIEW", "AccessReviewCampaign", saved.getId());
    }

    @Transactional(readOnly = true)
    public List<AccessReviewItem> listItems(Long campaignId) {
        mustCampaign(campaignId);
        return items.findByCampaignIdAndIsDeletedFalseOrderByUsernameAscRoleNameAsc(campaignId);
    }

    // ── item decisions ───────────────────────────────────────────────────

    @Transactional
    public AccessReviewItem decide(Long campaignId, Long itemId, String decision, String note, Long reviewer) {
        AccessReviewCampaign c = mustCampaign(campaignId);
        if (!"OPEN".equals(c.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign is closed");
        }
        AccessReviewItem item = items.findByOrganisationIdAndIdAndIsDeletedFalse(org(), itemId)
            .filter(i -> i.getCampaignId().equals(campaignId))
            .orElseThrow(() -> new EntityNotFoundException("Review item not found in campaign: " + itemId));
        if (!"PENDING".equals(item.getDecision())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Item already decided (" + item.getDecision() + ")");
        }
        if (reviewer != null && reviewer.equals(item.getUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Reviewers cannot certify their own access");
        }
        // V294 — reviewer scoping: a designated reviewer locks out everyone but
        // themselves and governance admins.
        if (c.getReviewerUserId() != null && !c.getReviewerUserId().equals(reviewer)
                && !sod.isGovernanceAdmin(reviewer)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "This campaign is scoped to a designated reviewer");
        }
        if ("MODIFY".equals(decision) && (note == null || note.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "MODIFY requires a note describing the required change");
        }

        if ("REVOKE".equals(decision)) {
            int removed = assignments.deleteAssignment(item.getUserId(), item.getRoleId());
            audit.recordEvent(new AuditEventBuilder()
                .actionType("ACCESS_REVOKE").sourceModule("IAM").entityType("UserRoleAssignment")
                .entityCode(item.getUsername())
                .message("Recertification REVOKE executed: role " + item.getRoleName()
                    + " removed from " + item.getUsername() + " (" + removed + " row)"));
        }
        item.setDecision(decision);
        item.setDecidedBy(reviewer);
        item.setDecidedAt(LocalDateTime.now());
        item.setNote(note);
        AccessReviewItem saved = items.save(item);
        audit.log("UPDATE", "AccessReviewItem", saved.getId(),
            "Review item " + saved.getUsername() + "×" + saved.getRoleName() + " → " + decision);
        return saved;
    }

    // ── closure ──────────────────────────────────────────────────────────

    @Transactional
    public CampaignView close(Long campaignId, String exceptionNote, Long closer) {
        AccessReviewCampaign c = mustCampaign(campaignId);
        if (!"OPEN".equals(c.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign already closed");
        }
        long pending = items.countByCampaignIdAndDecisionAndIsDeletedFalse(campaignId, "PENDING");
        if (pending > 0) {
            if (exceptionNote == null || exceptionNote.isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    pending + " item(s) still pending — closure requires 100% disposition, "
                        + "or a GRC_ADMINISTRATOR exception with a note");
            }
            if (!sod.isGovernanceAdmin(closer)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Exception closure requires GRC_ADMINISTRATOR");
            }
            c.setExceptionNote(exceptionNote);
        }
        c.setStatus("CLOSED");
        c.setClosedAt(LocalDateTime.now());
        c.setClosedBy(closer);
        AccessReviewCampaign saved = campaigns.save(c);
        audit.recordEvent(new AuditEventBuilder()
            .actionType("UPDATE").sourceModule("IAM").entityType("AccessReviewCampaign")
            .entityId(saved.getId())
            .message("Recertification campaign closed: " + saved.getTitleEn()
                + (pending > 0 ? " (EXCEPTION — " + pending + " pending)" : " (fully dispositioned)")));
        return new CampaignView(saved,
            items.countByCampaignIdAndIsDeletedFalse(campaignId), pending);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private AccessReviewCampaign mustCampaign(Long id) {
        return campaigns.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Campaign not found: " + id));
    }

    private static Long org() {
        return TenantContext.requireOrganisationId();
    }
}

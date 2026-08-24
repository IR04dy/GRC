package com.tss.grc.module.iam.governance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.CreateAccessRequest;
import com.tss.grc.module.iam.governance.entity.AccessRequest;
import com.tss.grc.module.iam.governance.entity.SodRule;
import com.tss.grc.module.iam.governance.repository.AccessRequestRepository;
import com.tss.grc.module.iam.repository.RoleRepository;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService.AuditEventBuilder;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RBAC Access-Governance P1 (plan §6.2/§6.3) — the access-request lifecycle:
 * {@code PENDING → APPROVED+PROVISIONED | REJECTED}, time-bound assignments,
 * the expiry sweep, and JML LEAVER processing.
 *
 * <p>Hard rules:
 * <ul>
 *   <li>4-eyes: approver ≠ requester AND approver ≠ beneficiary (409).</li>
 *   <li>A grant only reaches {@code iam.user_roles} through approval (or explicit
 *       admin revoke path) — never on request creation.</li>
 *   <li>Expiry/revocation hard-delete the join row (the @ManyToMany ignores
 *       soft-delete flags on join tables); history stays here + in the audit log.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AccessRequestService {

    private static final Logger log = LoggerFactory.getLogger(AccessRequestService.class);

    private final AccessRequestRepository requests;
    private final RoleRepository roles;
    private final UserRepository users;
    private final AuditLogService audit;
    private final SodService sod;
    private final RecertificationService recertification;
    private final com.tss.grc.module.platform.service.NotificationService notifications;
    private final com.tss.grc.module.workflow.requests.service.WorkflowRequestService workflowEngine;
    private final org.springframework.transaction.PlatformTransactionManager txManager;

    /** V294 (plan §6.2) — tenant-profile switch: with 4-eyes OFF the requester may also
     *  decide (beneficiary self-approval stays blocked). Default ON (Saudi profile). */
    @org.springframework.beans.factory.annotation.Value("${grc.iam.four-eyes-enabled:true}")
    private boolean fourEyesEnabled;

    // ── request lifecycle ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AccessRequest> list(String status) {
        Long org = org();
        return status == null || status.isBlank()
            ? requests.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(org)
            : requests.findByOrganisationIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(org, status.toUpperCase());
    }

    @Transactional
    public AccessRequest create(CreateAccessRequest r) {
        Long org = org();
        User beneficiary = users.findById(r.beneficiaryUserId())
            .filter(u -> org.equals(u.getOrganisationId()))
            .orElseThrow(() -> new EntityNotFoundException("Beneficiary not in this organisation: " + r.beneficiaryUserId()));
        Role role = roles.findById(r.roleId())
            .filter(x -> !Boolean.TRUE.equals(x.getIsDeleted()))
            .filter(x -> x.getOrganisationId() == null || x.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Role not found: " + r.roleId()));
        // F2-62: an inactive role confers no authorities, so requesting one would send a
        // maker-checker request through approval to provision a grant that does nothing.
        // Refused here rather than filtered into the "Role not found" above, because the
        // requester needs to know the role exists and must be activated.
        if (!role.grantsAuthority()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Role is inactive and grants no access: " + role.getRoleName());
        }
        if (r.validTo() != null && r.validFrom() != null && !r.validTo().isAfter(r.validFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "validTo must be after validFrom");
        }

        AccessRequest req = new AccessRequest();
        req.setOrganisationId(org);
        req.setBeneficiaryUserId(beneficiary.getId());
        req.setRoleId(role.getId());
        req.setValidFrom(r.validFrom());
        req.setValidTo(r.validTo());
        req.setJustification(r.justification());
        AccessRequest saved = requests.save(req);
        audit.log("CREATE", "AccessRequest", saved.getId(),
            "Access requested: role " + role.getRoleName() + " for " + beneficiary.getUsername());
        // Engine wiring runs AFTER COMMIT: outside the governance TX it can neither
        // poison it nor race the row's ROWVERSION (the linkage is stamped natively).
        final Long requestId = saved.getId();
        final String roleName = role.getRoleName(), beneficiaryName = beneficiary.getUsername();
        final String justification = saved.getJustification();
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override public void afterCommit() {
                    raiseEngineRequest(requestId, roleName, beneficiaryName, justification);
                }
            });
        return saved;
    }

    /**
     * V294 (plan §6.2) — workflow-engine visibility: every access request also raises an
     * anchored engine request (inbox / SLA / escalation for the approver population).
     * Runs AFTER the governance transaction committed (TransactionSynchronization), so an
     * engine failure can neither surface to the caller nor poison the core flow; the
     * engine's create and send each open their own transaction (calling them inside one
     * shared TX double-flushes the same ROWVERSION row — live-found); the back-link is
     * stamped natively, never via a second entity save.
     */
    private void raiseEngineRequest(Long requestId, String roleName, String beneficiaryName, String justification) {
        try {
            // Every call gets its own REQUIRES_NEW: afterCommit still has the COMPLETED
            // transaction context bound, so a plain @Transactional(REQUIRED) engine call
            // would join it and write without a live transaction (live-found: the created
            // request came back with a null id). Separate transactions also respect the
            // ROWVERSION single-flush rule between create and send.
            Long engineId = inNewTransaction(() ->
                workflowEngine.create(new com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.CreateRequest(
                    "IAM", "AccessRequest", requestId, "ACCESS_APPROVAL",
                    "Access request: " + roleName + " for " + beneficiaryName,
                    "طلب صلاحية: " + roleName + " للمستخدم " + beneficiaryName,
                    justification,
                    null, null, "Approve or reject in Access Governance",
                    java.time.LocalDate.now().plusDays(3), 72, "HIGH", null,
                    null, "GRC_ADMINISTRATOR", null,   // escalation path — the engine's HIGH-priority gate
                    Boolean.FALSE, Boolean.FALSE, null, null, null,
                    List.of(new com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RecipientDto(
                        "ROLE", null, "GRC_ADMINISTRATOR", "Access approvers", "ASSIGNEE", 1)),
                    null, null, null, null, null)).id());
            inNewTransaction(() -> workflowEngine.send(engineId));
            inNewTransaction(() -> requests.stampEngineRequestId(requestId, engineId));
        } catch (RuntimeException e) {
            log.warn("engine visibility request failed for access request {}: {}", requestId, e.getMessage());
        }
    }

    /** V294 — settle the anchored engine request once the governance decision commits. */
    private void settleEngineRequestAfterCommit(Long requestId, Long engineRequestId, String outcome) {
        if (engineRequestId == null) return;
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override public void afterCommit() {
                    try {
                        inNewTransaction(() ->
                            workflowEngine.respond(engineRequestId, "Access request " + outcome, outcome));
                    } catch (RuntimeException first) {
                        try {
                            inNewTransaction(() ->
                                workflowEngine.close(engineRequestId, "Access request " + outcome));
                        } catch (RuntimeException second) {
                            log.warn("engine settle failed for access request {} ({}): {}",
                                requestId, outcome, second.getMessage());
                        }
                    }
                }
            });
    }

    /**
     * New-transaction wrapper: for the post-commit native stamp, and (F2-322) for each organisation
     * of the expiry sweep, so one tenant's failure cannot discard the others' work.
     *
     * <p>⚠⚠ F2-322 — it runs <b>inline</b> when no transaction manager is present, which is the case
     * in a directly-constructed unit test. So <b>no unit test can verify the isolation this provides</b>:
     * with a null {@code txManager} the boundary does not exist and a failure propagates exactly as it
     * did before. {@code EmergencyRatificationSlaScheduler}'s own wrapper records the same trap in the
     * same words — <em>"the inline path is why the six unit tests could not catch the missing
     * transaction"</em> — which is why F2-322's legs are structural and say so.
     */
    private <T> T inNewTransaction(java.util.function.Supplier<T> work) {
        if (txManager == null) {
            return work.get();
        }
        var tt = new org.springframework.transaction.support.TransactionTemplate(txManager);
        tt.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tt.execute(status -> work.get());
    }

    @Transactional
    public AccessRequest approve(Long id, boolean override, String overrideJustification) {
        AccessRequest req = mustPending(id);
        Long approver = currentUserId();
        // Beneficiary self-approval is blocked unconditionally; requester==approver is the
        // 4-eyes rule and follows the tenant profile switch (V294, default ON).
        if (approver != null && approver.equals(req.getBeneficiaryUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Self-approval blocked: the beneficiary cannot approve their own access");
        }
        if (fourEyesEnabled && approver != null && approver.equals(req.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "4-eyes blocked: approver must differ from the requester");
        }
        // Preventive SoD check (plan §6.4): block grants that would newly complete a
        // conflicting pair — override requires GRC_ADMINISTRATOR + documented
        // justification, and still records the violation (source OVERRIDE).
        List<SodRule> conflicts = sod.grantConflicts(req.getOrganisationId(),
            req.getBeneficiaryUserId(), req.getRoleId());
        if (!conflicts.isEmpty()) {
            String codes = conflicts.stream().map(SodRule::getRuleCode)
                .reduce((a, b) -> a + ", " + b).orElse("");
            if (!override) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SoD conflict (" + codes + ") — approve with override=true and a "
                        + "justification (GRC_ADMINISTRATOR only) to grant anyway");
            }
            if (overrideJustification == null || overrideJustification.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "SoD override requires overrideJustification");
            }
            if (!sod.isGovernanceAdmin(approver)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "SoD override requires GRC_ADMINISTRATOR");
            }
        }
        // Provision FIRST (native insert), then mutate + save the entity exactly ONCE.
        // Saving twice around a native @Modifying query double-flushes the same row and
        // trips the SQL Server ROWVERSION optimistic lock (stale @Version at commit).
        int inserted = requests.insertAssignment(req.getBeneficiaryUserId(), req.getRoleId(),
            req.getCreatedBy(), req.getValidFrom(), req.getValidTo(), req.getJustification(), approver);
        if (!conflicts.isEmpty()) {
            String username = users.findById(req.getBeneficiaryUserId())
                .map(User::getUsername).orElse("user#" + req.getBeneficiaryUserId());
            sod.recordOverride(req.getOrganisationId(), req.getBeneficiaryUserId(), username,
                conflicts, overrideJustification, approver);
        }

        req.setStatus("PROVISIONED");
        req.setApprovedBy(approver);
        req.setDecidedAt(LocalDateTime.now());
        req.setProvisionedAt(LocalDateTime.now());
        AccessRequest saved = requests.save(req);

        audit.recordEvent(new AuditEventBuilder()
            .actionType("ACCESS_GRANT").sourceModule("IAM")
            .entityType("UserRoleAssignment").entityId(saved.getId())
            .newValueJson("{\"userId\":" + req.getBeneficiaryUserId() + ",\"roleId\":" + req.getRoleId()
                + ",\"validTo\":\"" + req.getValidTo() + "\",\"insertedRows\":" + inserted + "}")
            .message("Access request approved + provisioned (request " + saved.getId() + ")"));
        settleEngineRequestAfterCommit(saved.getId(), saved.getEngineRequestId(), "APPROVED");
        return saved;
    }

    @Transactional
    public AccessRequest reject(Long id, String reason) {
        AccessRequest req = mustPending(id);
        Long approver = currentUserId();
        if (fourEyesEnabled && approver != null && approver.equals(req.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Requester cannot decide their own request");
        }
        req.setStatus("REJECTED");
        req.setApprovedBy(approver);
        req.setDecidedAt(LocalDateTime.now());
        req.setRejectionReason(reason);
        AccessRequest saved = requests.save(req);
        audit.log("REJECT", "AccessRequest", saved.getId(), "Access request rejected: " + reason);
        settleEngineRequestAfterCommit(saved.getId(), saved.getEngineRequestId(), "REJECTED");
        return saved;
    }

    // ── revocation + sweeps ──────────────────────────────────────────────

    @Transactional
    public int revoke(Long userId, Long roleId) {
        Long org = org();
        users.findById(userId).filter(u -> org.equals(u.getOrganisationId()))
            .orElseThrow(() -> new EntityNotFoundException("User not in this organisation: " + userId));
        int removed = requests.deleteAssignment(userId, roleId);
        audit.recordEvent(new AuditEventBuilder()
            .actionType("ACCESS_REVOKE").sourceModule("IAM")
            .entityType("UserRoleAssignment")
            .oldValueJson("{\"userId\":" + userId + ",\"roleId\":" + roleId + "}")
            .message("Assignment revoked (" + removed + " row)"));
        return removed;
    }

    /** Hourly expiry sweep + manual trigger. Off-request-safe (audit guards internally).
     *  V294: per-org so each tenant's IAM_LEAD gets the revocation notification. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M")
    public void scheduledExpirySweep() {
        // ⚠⚠ F2-318 — the tenant is anchored per organisation before anything is audited.
        // `AuditLogService.recordEvent` does `org != null ? org : 1L` ("fall back to tenant 1 for
        // system events"), and this method runs off-request, so before this every ACCESS_REVOKE row
        // it wrote landed in organisation 1 whatever tenant's assignment had expired. 🔑 The tell was
        // in the message itself: the org was interpolated into the FREE TEXT — "(org N)" — because
        // the row's own tenant column was not carrying it. And `notifyExpiry(org, n)` on the next
        // line was already org-explicit, which is the same asymmetry F2-317 found (the event
        // org-explicit, the audit row beside it not) for the third time.
        //
        // ⚠ RESTORE rather than clear. Restoring is correct whether or not an ambient tenant exists;
        // clearing is only correct when one does not, so it has to be re-justified every time a
        // caller is added. `manualExpirySweep` twelve lines below is the on-request sibling and was
        // always correct, because a request sets the tenant for it.
        //
        // ⚠ The org is left in the message too. It is redundant now, and removing it would rewrite
        // what the fifteen existing rows say relative to new ones — an audit trail should not become
        // harder to compare because it got more correct.
        Long ambient = TenantContext.getCurrentTenant();
        try {
            for (Long org : requests.distinctOrganisationIds()) {
                try {
                    TenantContext.setCurrentTenant(org);
                    // ⚠⚠ F2-322 — each organisation gets its OWN transaction, which is what makes the
                    // catch below mean anything. This method used to be @Transactional, so `audit`
                    // joined one batch-wide transaction: a failure on org 2 marked it rollback-only
                    // and every organisation's work was discarded at commit, while the catch made it
                    // read as resilient. 🔑 F2-317 recorded that and declined to fix it because the
                    // change is structural, not a wrapper — this is that change.
                    inNewTransaction(() -> {
                        int n = requests.expireAssignmentsForOrg(org);
                        if (n > 0) {
                            audit.recordEvent(new AuditEventBuilder()
                                .actionType("ACCESS_REVOKE").sourceModule("IAM")
                                .entityType("UserRoleAssignment")
                                .message("Expiry sweep removed " + n
                                         + " time-bound assignment(s) (org " + org + ")"));
                            notifyExpiry(org, n);
                        }
                        return n;
                    });
                } catch (RuntimeException e) {
                    log.warn("access expiry sweep failed for org {}: {}", org, e.getMessage());
                }
            }
        } finally {
            TenantContext.setCurrentTenant(ambient);
        }
    }

    @Transactional
    public int manualExpirySweep() {
        Long org = org();
        int n = requests.expireAssignmentsForOrg(org);
        audit.recordEvent(new AuditEventBuilder()
            .actionType("ACCESS_REVOKE").sourceModule("IAM").entityType("UserRoleAssignment")
            .message("Manual expiry sweep removed " + n + " assignment(s)"));
        if (n > 0) notifyExpiry(org, n);
        return n;
    }

    /** V294 (plan §6.3) — in-app notification to the tenant's IAM_LEAD population. */
    private void notifyExpiry(Long org, int removed) {
        try {
            notifications.broadcastToRole(org, "IAM_LEAD",
                "Time-bound access expired",
                "انتهت صلاحيات وصول محددة المدة",
                removed + " assignment(s) auto-revoked past their validity window.",
                "ACCESS_LIFECYCLE", "UserRoleAssignment", null);
        } catch (RuntimeException e) {
            log.warn("expiry notification failed for org {}: {}", org, e.getMessage());
        }
    }

    /** JML LEAVER processing (plan §6.3): revoke everything for leavers, stamp the event.
     *  V294 adds MOVER handling: each mover gets a targeted USER-scoped mini-review. */
    @Transactional
    public int jmlSweep() {
        Long org = org();
        List<Object[]> leavers = requests.findUnprocessedLeavers(org);
        int processed = 0;
        for (Object[] row : leavers) {
            Long jmlId = ((Number) row[0]).longValue();
            Long userId = ((Number) row[1]).longValue();
            String username = String.valueOf(row[2]);
            int removed = requests.deleteAllAssignments(userId);
            requests.markLeaverProcessed(jmlId);
            processed++;
            audit.recordEvent(new AuditEventBuilder()
                .actionType("ACCESS_REVOKE").sourceModule("IAM")
                .entityType("UserRoleAssignment")
                .entityCode(username)
                .message("JML LEAVER processed: all " + removed + " assignment(s) revoked for " + username));
        }
        for (Object[] row : requests.findUnprocessedMovers(org)) {
            Long jmlId = ((Number) row[0]).longValue();
            Long userId = ((Number) row[1]).longValue();
            String username = String.valueOf(row[2]);
            recertification.moverMiniReview(org, userId, username);
            requests.markLeaverProcessed(jmlId);   // same processed-stamp column
            processed++;
        }
        return processed;
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private AccessRequest mustPending(Long id) {
        AccessRequest req = requests.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Access request not found: " + id));
        if (!"PENDING".equals(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Request already decided (" + req.getStatus() + ")");
        }
        return req;
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }

    private static Long org() {
        return TenantContext.requireOrganisationId();
    }
}

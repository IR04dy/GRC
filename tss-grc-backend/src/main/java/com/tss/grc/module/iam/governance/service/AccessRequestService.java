package com.tss.grc.module.iam.governance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.CreateAccessRequest;
import com.tss.grc.module.iam.governance.entity.AccessRequest;
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
        return saved;
    }

    @Transactional
    public AccessRequest approve(Long id) {
        AccessRequest req = mustPending(id);
        Long approver = currentUserId();
        if (approver != null && (approver.equals(req.getCreatedBy()) || approver.equals(req.getBeneficiaryUserId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "4-eyes blocked: approver must differ from both requester and beneficiary");
        }
        // Provision FIRST (native insert), then mutate + save the entity exactly ONCE.
        // Saving twice around a native @Modifying query double-flushes the same row and
        // trips the SQL Server ROWVERSION optimistic lock (stale @Version at commit).
        int inserted = requests.insertAssignment(req.getBeneficiaryUserId(), req.getRoleId(),
            req.getCreatedBy(), req.getValidFrom(), req.getValidTo(), req.getJustification(), approver);

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
        return saved;
    }

    @Transactional
    public AccessRequest reject(Long id, String reason) {
        AccessRequest req = mustPending(id);
        Long approver = currentUserId();
        if (approver != null && approver.equals(req.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Requester cannot decide their own request");
        }
        req.setStatus("REJECTED");
        req.setApprovedBy(approver);
        req.setDecidedAt(LocalDateTime.now());
        req.setRejectionReason(reason);
        AccessRequest saved = requests.save(req);
        audit.log("REJECT", "AccessRequest", saved.getId(), "Access request rejected: " + reason);
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

    /** Hourly expiry sweep + manual trigger. Off-request-safe (audit guards internally). */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M")
    @Transactional
    public void scheduledExpirySweep() {
        try {
            int n = requests.expireAssignments();
            if (n > 0) {
                audit.recordEvent(new AuditEventBuilder()
                    .actionType("ACCESS_REVOKE").sourceModule("IAM").entityType("UserRoleAssignment")
                    .message("Expiry sweep removed " + n + " time-bound assignment(s)"));
            }
        } catch (RuntimeException e) {
            log.warn("access expiry sweep failed: {}", e.getMessage());
        }
    }

    @Transactional
    public int manualExpirySweep() {
        int n = requests.expireAssignments();
        audit.recordEvent(new AuditEventBuilder()
            .actionType("ACCESS_REVOKE").sourceModule("IAM").entityType("UserRoleAssignment")
            .message("Manual expiry sweep removed " + n + " assignment(s)"));
        return n;
    }

    /** JML LEAVER processing (plan §6.3): revoke everything for leavers, stamp the event. */
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

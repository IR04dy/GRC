package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmCommunicationContact;
import com.tss.grc.module.bcm.cycle.repository.BcmCommunicationContactRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Communication contact + call-tree-test service — Sprint BCM-2.
 *
 * <p>Records emergency contacts and the most-recent call-tree test
 * result per contact. A FAILED call-tree test publishes a
 * {@code BCM_CALL_TREE_FAILED} domain event so an F8 automation rule
 * can auto-create a corrective action in BCM-3.
 */
@Service
@RequiredArgsConstructor
public class BcmCommunicationContactService {

    private static final Set<String> AVAILABILITIES = Set.of("AVAILABLE", "ON_LEAVE", "UNAVAILABLE", "BACKUP");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "ARCHIVED");
    private static final Set<String> TEST_RESULTS = Set.of("PASSED", "FAILED", "NOT_REACHED");

    private final BcmCommunicationContactRepository contacts;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;

    private Long org() { return TenantContext.getCurrentTenant(); }

    @Transactional(readOnly = true)
    public List<BcmCommunicationContact> list(Long planId, String status) {
        Long o = org();
        if (planId != null)
            return contacts.findByOrganisationIdAndRelatedPlanIdOrderByEscalationLevelAsc(o, planId);
        if (status != null && !status.isBlank())
            return contacts.findByOrganisationIdAndStatusOrderByEscalationLevelAscNameAsc(o, status);
        return contacts.findByOrganisationIdOrderByEscalationLevelAscNameAsc(o);
    }

    @Transactional(readOnly = true)
    public BcmCommunicationContact get(Long id) {
        return contacts.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Communication contact not found: " + id));
    }

    @Transactional
    public BcmCommunicationContact create(BcmCommunicationContact body) {
        if (body.getName() == null || body.getName().isBlank())
            throw new IllegalArgumentException("name is required");
        if (body.getEscalationLevel() == null) body.setEscalationLevel(1);
        if (body.getEscalationLevel() < 1 || body.getEscalationLevel() > 5)
            throw new IllegalArgumentException("escalationLevel must be 1..5");
        if (body.getAvailabilityStatus() == null) body.setAvailabilityStatus("AVAILABLE");
        if (!AVAILABILITIES.contains(body.getAvailabilityStatus()))
            throw new IllegalArgumentException("availabilityStatus must be one of " + AVAILABILITIES);
        if (body.getStatus() == null) body.setStatus("ACTIVE");
        if (!STATUSES.contains(body.getStatus()))
            throw new IllegalArgumentException("Invalid status: " + body.getStatus());
        if ((body.getPrimaryPhone() == null || body.getPrimaryPhone().isBlank())
            && (body.getEmail() == null || body.getEmail().isBlank()))
            throw new IllegalArgumentException("At least one of primary_phone or email is required");
        body.setId(null);
        body.setOrganisationId(org());
        BcmCommunicationContact saved = contacts.save(body);
        auditEvent(saved, "CREATE", "Created communication contact: " + saved.getName());
        return saved;
    }

    @Transactional
    public BcmCommunicationContact update(Long id, BcmCommunicationContact body) {
        BcmCommunicationContact c = get(id);
        if (body.getName() != null)             c.setName(body.getName());
        if (body.getRoleTitle() != null)        c.setRoleTitle(body.getRoleTitle());
        if (body.getDepartmentId() != null)     c.setDepartmentId(body.getDepartmentId());
        if (body.getPrimaryPhone() != null)     c.setPrimaryPhone(body.getPrimaryPhone());
        if (body.getSecondaryPhone() != null)   c.setSecondaryPhone(body.getSecondaryPhone());
        if (body.getEmail() != null)            c.setEmail(body.getEmail());
        if (body.getEscalationLevel() != null) {
            if (body.getEscalationLevel() < 1 || body.getEscalationLevel() > 5)
                throw new IllegalArgumentException("escalationLevel must be 1..5");
            c.setEscalationLevel(body.getEscalationLevel());
        }
        if (body.getRelatedPlanId() != null)    c.setRelatedPlanId(body.getRelatedPlanId());
        if (body.getAvailabilityStatus() != null) {
            if (!AVAILABILITIES.contains(body.getAvailabilityStatus()))
                throw new IllegalArgumentException("Invalid availability status");
            c.setAvailabilityStatus(body.getAvailabilityStatus());
        }
        if (body.getStatus() != null) {
            if (!STATUSES.contains(body.getStatus()))
                throw new IllegalArgumentException("Invalid status");
            c.setStatus(body.getStatus());
        }
        c.setUpdatedAt(LocalDateTime.now());
        return contacts.save(c);
    }

    /**
     * Record the outcome of a call-tree test. A FAILED result fires
     * a domain event so an F8 rule can auto-create a corrective
     * action (BCM-3 wiring).
     */
    @Transactional
    public BcmCommunicationContact recordTestResult(Long id, String result, String notes) {
        if (!TEST_RESULTS.contains(result))
            throw new IllegalArgumentException("result must be one of " + TEST_RESULTS);
        BcmCommunicationContact c = get(id);
        c.setLastTestResult(result);
        c.setLastTestedDate(LocalDate.now());
        c.setUpdatedAt(LocalDateTime.now());
        BcmCommunicationContact saved = contacts.save(c);
        auditEvent(saved, "CUSTOM",
            "Call-tree test " + result + " for contact " + saved.getId()
                + (notes == null || notes.isBlank() ? "" : " · " + notes));
        if ("FAILED".equals(result)) {
            events.publishEvent(DomainEvent.of(
                saved.getOrganisationId(),
                "BCM_CALL_TREE_FAILED",
                "BcmCommunicationContact",
                saved.getId(),
                Map.of(
                    "contact_name", saved.getName(),
                    "escalation_level", saved.getEscalationLevel(),
                    "related_plan_id", saved.getRelatedPlanId() == null ? -1 : saved.getRelatedPlanId()
                )
            ));
        }
        return saved;
    }

    /** Aggregate over the whole contact list — used by the dashboard. */
    @Transactional(readOnly = true)
    public Map<String, Long> testResultStats() {
        Long o = org();
        return Map.of(
            "passed",     contacts.countByOrganisationIdAndLastTestResult(o, "PASSED"),
            "failed",     contacts.countByOrganisationIdAndLastTestResult(o, "FAILED"),
            "notReached", contacts.countByOrganisationIdAndLastTestResult(o, "NOT_REACHED")
        );
    }

    @Transactional
    public void delete(Long id) {
        BcmCommunicationContact c = get(id);
        c.setIsDeleted(true);
        contacts.save(c);
        auditEvent(c, "DELETE", "Soft-deleted communication contact " + c.getId());
    }

    private void auditEvent(BcmCommunicationContact c, String action, String message) {
        audit.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType(action)
            .entityType("BcmCommunicationContact")
            .entityId(c.getId())
            .entityCode(c.getName())
            .sourceModule("bcm")
            .message(message));
    }
}

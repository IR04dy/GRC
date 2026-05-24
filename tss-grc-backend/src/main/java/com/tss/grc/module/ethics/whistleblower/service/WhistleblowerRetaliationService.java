package com.tss.grc.module.ethics.whistleblower.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.RetaliationComplaintRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.RetaliationComplaintResponse;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.RetaliationTransitionRequest;
import com.tss.grc.module.ethics.whistleblower.entity.WhistleblowerRetaliationComplaint;
import com.tss.grc.module.ethics.whistleblower.repository.WhistleblowerCaseRepository;
import com.tss.grc.module.ethics.whistleblower.repository.WhistleblowerRetaliationComplaintRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Sprint 13 — retaliation-protection sub-flow service.
 *
 * <h3>State machine</h3>
 * <pre>
 *   RECEIVED → VALIDATED → UPHELD | DISMISSED
 *           ↘ DISMISSED  (validation found no bona-fide claim)
 *   UPHELD    (terminal)
 *   DISMISSED (terminal)
 * </pre>
 *
 * <p>F8 events:
 * <ul>
 *   <li>{@code WHISTLEBLOWER_RETALIATION_CLAIMED}</li>
 *   <li>{@code WHISTLEBLOWER_RETALIATION_VALIDATED}</li>
 *   <li>{@code WHISTLEBLOWER_RETALIATION_UPHELD}</li>
 *   <li>{@code WHISTLEBLOWER_RETALIATION_DISMISSED}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhistleblowerRetaliationService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "RECEIVED",  Set.of("VALIDATED", "DISMISSED"),
            "VALIDATED", Set.of("UPHELD", "DISMISSED"),
            "UPHELD",    Set.of(),
            "DISMISSED", Set.of()
    );

    private final WhistleblowerRetaliationComplaintRepository repo;
    private final WhistleblowerCaseRepository caseRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    @Transactional(readOnly = true)
    public List<RetaliationComplaintResponse> list() {
        return repo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(org())
                .stream().map(RetaliationComplaintResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RetaliationComplaintResponse> listForCase(Long caseId) {
        return repo.findByOrganisationIdAndRelatedCaseIdAndIsDeletedFalseOrderByCreatedAtDesc(org(), caseId)
                .stream().map(RetaliationComplaintResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RetaliationComplaintResponse get(Long id) {
        return RetaliationComplaintResponse.from(loadOrThrow(id));
    }

    @Transactional
    public RetaliationComplaintResponse claim(RetaliationComplaintRequest req) {
        Long orgId = org();
        // Ensure the related case exists in this tenant.
        caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(orgId, req.relatedCaseId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Related whistleblower case not found: " + req.relatedCaseId()));

        WhistleblowerRetaliationComplaint r = new WhistleblowerRetaliationComplaint();
        r.setOrganisationId(orgId);
        r.setRelatedCaseId(req.relatedCaseId());
        r.setClaimantUserId(req.claimantUserId());
        r.setClaimantName(req.claimantName());
        r.setClaimedActor(req.claimedActor());
        r.setClaimedAction(req.claimedAction());
        r.setClaimedAtPeriodStart(req.claimedAtPeriodStart());
        r.setStatus("RECEIVED");

        WhistleblowerRetaliationComplaint saved = repo.save(r);

        audit.log("RETALIATION_CLAIMED", "WhistleblowerRetaliation", saved.getId(),
                "Retaliation claim received against actor='" + safe(saved.getClaimedActor())
                        + "' for case " + saved.getRelatedCaseId());

        emit("WHISTLEBLOWER_RETALIATION_CLAIMED", saved, null);
        return RetaliationComplaintResponse.from(saved);
    }

    @Transactional
    public RetaliationComplaintResponse validate(Long id, Long protectorUserId) {
        return transition(id, "VALIDATED", protectorUserId,
                "WHISTLEBLOWER_RETALIATION_VALIDATED",
                "Retaliation claim validated for bona-fide review");
    }

    @Transactional
    public RetaliationComplaintResponse uphold(Long id, Long protectorUserId) {
        return transition(id, "UPHELD", protectorUserId,
                "WHISTLEBLOWER_RETALIATION_UPHELD",
                "Retaliation claim upheld — disciplinary action triggered");
    }

    @Transactional
    public RetaliationComplaintResponse dismiss(Long id, Long protectorUserId) {
        return transition(id, "DISMISSED", protectorUserId,
                "WHISTLEBLOWER_RETALIATION_DISMISSED",
                "Retaliation claim dismissed — could not be substantiated");
    }

    @Transactional
    public RetaliationComplaintResponse transitionTo(Long id, RetaliationTransitionRequest req) {
        return transition(id, req.terminalStatus(), req.assignedProtectorUserId(),
                "WHISTLEBLOWER_RETALIATION_" + req.terminalStatus(),
                "Retaliation claim transitioned to " + req.terminalStatus());
    }

    private RetaliationComplaintResponse transition(Long id, String to, Long protectorUserId,
                                                     String trigger, String message) {
        WhistleblowerRetaliationComplaint r = loadOrThrow(id);
        String from = r.getStatus();
        Set<String> allowed = ALLOWED.getOrDefault(Objects.requireNonNullElse(from, ""), Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
        r.setStatus(to);
        if (protectorUserId != null) {
            r.setAssignedProtectorUserId(protectorUserId);
        }
        WhistleblowerRetaliationComplaint saved = repo.save(r);
        audit.log("RETALIATION_" + to, "WhistleblowerRetaliation", saved.getId(),
                message + " (from " + from + ")");
        emit(trigger, saved, from);
        return RetaliationComplaintResponse.from(saved);
    }

    private WhistleblowerRetaliationComplaint loadOrThrow(Long id) {
        return repo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
                .orElseThrow(() -> new EntityNotFoundException("Retaliation complaint not found: " + id));
    }

    private static Long org() { return TenantContext.requireOrganisationId(); }

    private static String safe(String s) { return s == null ? "" : s; }

    private void emit(String trigger, WhistleblowerRetaliationComplaint r, String previousStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("retaliationId", String.valueOf(r.getId()));
        payload.put("relatedCaseId", String.valueOf(r.getRelatedCaseId()));
        payload.put("status", r.getStatus());
        if (previousStatus != null) payload.put("previousStatus", previousStatus);
        publisher.publishEvent(new DomainEvent(
                r.getOrganisationId(), trigger,
                "WhistleblowerRetaliation", r.getId(),
                "retaliation:" + r.getId(), payload));
    }
}

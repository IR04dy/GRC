package com.tss.grc.module.ethics.whistleblower.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.RetaliationComplaintRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.RetaliationComplaintResponse;
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

    // ── F2-185(a): a fourth, generic transition path used to sit here, and it was deleted ──────
    // `transitionTo(id, RetaliationTransitionRequest)` took the terminal state as a request FIELD.
    // Nothing called it — no controller, no peer service, no test — and F2-184 noticed it only by
    // the accident of its DTO carrying an inert @NotBlank.
    //
    // ⚠ The register filed this as P2 "a retaliation complaint cannot be closed through the API at
    // all". That claim was FALSE, and measuring it is what settled the fix. The three endpoints
    // above are wired, guarded `ETHICS:PROTECT`, and `WhistleblowerRetaliationPage.tsx` renders a
    // button for each. The lifecycle was always finishable; what existed was a redundant FOURTH
    // way to finish it.
    //
    // So "wire it or delete it" was not the 50/50 the filing implied. Wiring it would have been
    // strictly worse than the three it duplicated:
    //   · its DTO added nothing — `terminalStatus` is what the path segment already encodes and
    //     `assignedProtectorUserId` is the `protectorUserId` query parameter they already accept;
    //   · it built the F8 trigger by concatenation, `"WHISTLEBLOWER_RETALIATION_" + terminalStatus`,
    //     correct only because ALLOWED happens to gate `to` down to the three legal upper-case
    //     tokens — one widened state machine away from emitting an event no rule subscribes to;
    //   · its audit line read "Retaliation claim transitioned to UPHELD" where `uphold()` writes
    //     "upheld — disciplinary action triggered". That sentence is the record a labour regulator
    //     reads, and the generic path degraded it for an identical state change.
    //
    // 🔑 A dark method is not automatically a missing capability. Sort by EFFECT, not by verb name
    // (F2-165's own correction): a dark method whose effect a live sibling already achieves is dead
    // code, and only one with no live sibling is lost capability. `DarkServiceMethodTest` holds the
    // rest of that population so the next one is adjudicated rather than discovered by accident.

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

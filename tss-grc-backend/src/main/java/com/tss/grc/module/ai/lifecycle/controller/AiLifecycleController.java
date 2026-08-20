package com.tss.grc.module.ai.lifecycle.controller;

import com.tss.grc.module.ai.lifecycle.dto.AiLifecycleDtos.*;
import com.tss.grc.module.ai.lifecycle.service.AiLifecycleService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** REST surface for Sprint 31 AI/ML Model Lifecycle. */
@RestController
@RequestMapping("/api/v1/ai-lifecycle")
@RequiredArgsConstructor
@RequireModule("DATA_AI_GOV")
public class AiLifecycleController {

    /**
     * F2-258 — the READ tier is deliberately STILL a role guard, and converting it would lose a
     * live role.
     *
     * <p>{@code AUDITOR} is named here, is held, and holds {@code AI:EXPORT} but <b>not</b>
     * {@code AI:READ} — measured in dev and prod. So {@code hasPermission('AI','READ')} would take
     * this list from five live roles to four and drop the examiner. That is F2-253's asymmetry:
     * correcting a role guard on the write side widens access and doing it on the read side can
     * narrow it, so the two halves are not one sweep. A gate leg fails the build if somebody
     * "finishes" this conversion (F2-254).
     */
    private static final String READ = "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER','AUDITOR','CISO','DPO','CIO','CRO')";

    /**
     * F2-258 — one permission for both the create and the decision, replacing two role guards that
     * between them admitted the platform administrator and nobody else.
     *
     * <p><b>The defect.</b> The creates were {@code hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER',
     * 'CIO','DPO')} and the transitions {@code hasAnyRole('PLATFORM_ADMIN','CRO','CIO','DPO')}.
     * {@code CIO}, {@code CRO} and {@code DPO} are held by <b>no user</b> in either database, so the
     * three {@code /transition} routes were administrator-only and the three creates admitted one
     * other role. Probed live before the change: {@code rmanager} and {@code compliance} both hold
     * {@code AI:WRITE} and both answered 403 on all three transitions, over 16 validations, 11
     * retraining events and 13 recertifications — and all three routes are wired to routed pages,
     * so the Move control on each of them failed for every non-administrator.
     *
     * <p><b>Why a role guard could not be repaired in place.</b> V373 was written for exactly these
     * routes — it names {@code AiLifecycleController#transition} — and chose to SEAT {@code CIO} and
     * {@code CRO} rather than convert, on the ground that four of the five modules reciting them
     * have no catalogued permissions at all. That is true of those four and not of this one:
     * {@code AI:READ}/{@code WRITE}/{@code EXPORT} have been catalogued since V313. Seating a role
     * makes a guard <i>satisfiable</i>, which is V373's own word; {@code hasAnyRole} tests for a
     * <i>granted authority</i>, so a role assigned to nobody still admits nobody (F2-253).
     *
     * <p><b>Why one constant and not two.</b> Deciding a validation is a different act from filing
     * one, and this controller no longer distinguishes them — so it says so with one name rather
     * than two identical strings. Merging retires nothing that existed: the two role sets already
     * overlapped on {@code CIO} and {@code DPO}, and no transition in {@code AiLifecycleService}
     * compares the acting principal against the record's author, so one person could always do
     * both. A real maker/checker bar needs an {@code AI:APPROVE} the catalogue does not define plus
     * an actor comparison; that is a V362-scale access decision and is filed, not smuggled in here.
     *
     * <p><b>Audience.</b> {@code AI:WRITE} is granted to 41 roles, 8 with a live holder in both
     * databases, and it is this module's own convention — {@code AiSuggestionController} has always
     * used it. Nobody named above is dropped: {@code DPO} already held it and V381 grants it to
     * {@code CIO} and {@code CRO} so their claim on these six routes survives the conversion.
     * {@code AI:READ} and {@code AI:WRITE} have identical grant sets, so there is no reader-only
     * role for this to admit; {@code AUDITOR} and {@code VIEWER} hold neither and are refused.
     */
    private static final String WRITE_GUARD = "hasPermission('AI','WRITE')";

    private final AiLifecycleService svc;

    // ──────── Validation ────────

    @GetMapping("/validations") @PreAuthorize(READ)
    public List<ValidationResponse> listValidations(@RequestParam(required = false) Long modelId) {
        return svc.listValidations(modelId);
    }

    @PostMapping("/validations") @PreAuthorize(WRITE_GUARD)
    public ResponseEntity<ValidationResponse> create(@Valid @RequestBody CreateValidationRequest req) {
        return ResponseEntity.ok(svc.createValidation(req));
    }

    @PostMapping("/validations/{id}/transition") @PreAuthorize(WRITE_GUARD)
    public ValidationResponse transition(@PathVariable Long id, @Valid @RequestBody TransitionValidationRequest req) {
        return svc.transitionValidation(id, req);
    }

    // ──────── Retraining ────────

    @GetMapping("/retrainings") @PreAuthorize(READ)
    public List<RetrainingResponse> listRetrainings(@RequestParam(required = false) Long modelId) {
        return svc.listRetrainings(modelId);
    }

    @PostMapping("/retrainings") @PreAuthorize(WRITE_GUARD)
    public ResponseEntity<RetrainingResponse> createRetraining(@Valid @RequestBody CreateRetrainingRequest req) {
        return ResponseEntity.ok(svc.createRetraining(req));
    }

    @PostMapping("/retrainings/{id}/transition") @PreAuthorize(WRITE_GUARD)
    public RetrainingResponse transitionRetraining(@PathVariable Long id, @Valid @RequestBody TransitionRetrainingRequest req) {
        return svc.transitionRetraining(id, req);
    }

    // ──────── Recertification ────────

    @GetMapping("/recertifications") @PreAuthorize(READ)
    public List<RecertificationResponse> listRecerts(@RequestParam(required = false) Long modelId) {
        return svc.listRecertifications(modelId);
    }

    @PostMapping("/recertifications") @PreAuthorize(WRITE_GUARD)
    public ResponseEntity<RecertificationResponse> createRec(@Valid @RequestBody CreateRecertificationRequest req) {
        return ResponseEntity.ok(svc.createRecertification(req));
    }

    @PostMapping("/recertifications/{id}/transition") @PreAuthorize(WRITE_GUARD)
    public RecertificationResponse transitionRec(@PathVariable Long id, @Valid @RequestBody TransitionRecertificationRequest req) {
        return svc.transitionRecertification(id, req);
    }

    // ──────── Timeline + dashboard ────────

    @GetMapping("/models/{id}/timeline") @PreAuthorize(READ)
    public List<EventResponse> timeline(@PathVariable Long id) { return svc.timeline(id); }

    @GetMapping("/dashboard") @PreAuthorize(READ)
    public DashboardResponse dashboard() { return svc.dashboard(); }
}

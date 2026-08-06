package com.tss.grc.module.bcm.controller;

import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.BiaQuantitativeRequest;
import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.BiaQuantitativeResponse;
import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.BoardApprovalRequest;
import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.CfoApprovalRequest;
import com.tss.grc.module.bcm.service.QuantitativeBiaService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Slice 9-C — REST surface for the quantitative BIA.
 *
 * <pre>
 *   GET    /api/v1/bcm/bia/quantitative
 *   GET    /api/v1/bcm/bia/quantitative/{externalId}
 *   POST   /api/v1/bcm/bia/quantitative
 *   PUT    /api/v1/bcm/bia/quantitative/{externalId}
 *   POST   /api/v1/bcm/bia/quantitative/{externalId}/submit
 *   POST   /api/v1/bcm/bia/quantitative/{externalId}/bcm-review
 *   POST   /api/v1/bcm/bia/quantitative/{externalId}/cfo-approve
 *   POST   /api/v1/bcm/bia/quantitative/{externalId}/board-approve
 *   DELETE /api/v1/bcm/bia/quantitative/{externalId}
 * </pre>
 *
 * <p>Role guard uses {@code hasAnyRole} (not {@code hasAnyAuthority})
 * because {@code CustomUserDetailsService} prefixes authorities with
 * {@code ROLE_}. This is the same shape PR #174 used to fix the socops
 * controllers — keep them aligned.
 */
@RestController
@RequestMapping("/api/v1/bcm/bia/quantitative")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM — Quantitative BIA (Slice 9-C)")
@SecurityRequirement(name = "bearerAuth")
// Class-level grant is the READ guard for `list` and `get` — the only two methods without their
// own @PreAuthorize. Mutating / approval verbs are narrowed per-method below so a read-only
// assurance role (AUDITOR) and RISK_MANAGER cannot create, edit, approve or archive a quantitative
// BIA (segregation of duties — ISO 22301 §5.3).
//
// ⚠ This comment used to say "every endpoint requires at least one of these roles". It does not:
// a method-level @PreAuthorize REPLACES the class-level one in Spring Security, it does not AND
// with it. So this list is a floor for exactly two methods, and for the other seven it is
// documentation of an intent nothing enforces. That matters here because F2-52's fix admits
// EXECUTIVE_REVIEWER to `board-approve` and EXECUTIVE_REVIEWER is not on this line — proven by
// live probe rather than by reading the framework's precedence rules.
@PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN','RISK_MANAGER','AUDITOR','CFO')")
public class BiaQuantitativeController {

    /**
     * The board sign-off tier — the last of three stages, and the only one that was broken.
     *
     * <h3>F2-52 — the last attrition lockout on the platform</h3>
     * It was {@code hasAnyRole('BOARD_MEMBER','PLATFORM_ADMIN')}, and {@code BOARD_MEMBER} is a row
     * in {@code iam.roles} in neither database. A role guard never reaches
     * {@code ModulePermissionEvaluator}, so it never reaches the PLATFORM_ADMIN bypass either: the
     * board approval of a quantitative BIA — the step that records a <b>board-minutes reference</b> —
     * could be performed by the platform administrator and nobody else. Measured live: dev holds one
     * quantitative BIA and it is sitting at {@code CFO_APPROVED}, <b>parked at exactly this stage</b>.
     *
     * <p>🔑 <b>{@code BCM:APPROVE} is the wrong target, and measuring its holders is what showed
     * that.</b> It resolves to {BCM_MANAGER, EXECUTIVE_REVIEWER, GRC_ADMINISTRATOR, ORG_ADMIN,
     * PLATFORM_ADMIN} — and {@code BCM_MANAGER} is the <em>first</em> stage of this same chain
     * ({@code bcm-review}). Converting to it would let one person perform the lead review and then
     * the board sign-off on their own submission, collapsing a three-stage escalation into one. The
     * status machine ({@code guardTransition}) enforces the <em>order</em> of the stages; nothing
     * enforces that they are different people, so the guard is the only separation there is.
     *
     * <p>{@code EXECUTIVE_REVIEWER} ("Executive Reviewer") mirrors the shape of the sibling
     * {@code cfo-approve} tier exactly — one domain role plus the administrator — overlaps neither
     * the BCM lead nor the CFO, and already holds {@code BCM:APPROVE}, so it is a role the platform
     * already trusts with BCM approvals.
     *
     * <p>⬜ What this does not fix: {@code boardApprove} records {@code boardApprovedAt} and
     * {@code boardMinutesRef} and <b>no approver at all</b>, while {@code cfoApprove} records a
     * caller-supplied {@code approverName}. Filed as F2-152 — a board approval with no signer, and a
     * CFO approval whose signer is whatever the request says.
     */
    private static final String BOARD_APPROVE_GUARD =
            "hasAnyRole('EXECUTIVE_REVIEWER','PLATFORM_ADMIN')";

    private final QuantitativeBiaService service;

    private static String actor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? "system" : a.getName();
    }

    @GetMapping
    public List<BiaQuantitativeResponse> list() {
        return service.list();
    }

    @GetMapping("/{externalId}")
    public BiaQuantitativeResponse get(@PathVariable UUID externalId) {
        return service.get(externalId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN')")
    public BiaQuantitativeResponse create(@RequestBody @Valid BiaQuantitativeRequest req) {
        return service.create(req);
    }

    @PutMapping("/{externalId}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN')")
    public BiaQuantitativeResponse update(@PathVariable UUID externalId,
                                          @RequestBody @Valid BiaQuantitativeRequest req) {
        return service.update(externalId, req);
    }

    @PostMapping("/{externalId}/submit")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN')")
    public BiaQuantitativeResponse submitForReview(@PathVariable UUID externalId) {
        return service.submitForReview(externalId);
    }

    @PostMapping("/{externalId}/bcm-review")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','CISO','PLATFORM_ADMIN')")
    public BiaQuantitativeResponse bcmLeadReview(@PathVariable UUID externalId) {
        return service.bcmLeadReview(externalId, actor());
    }

    @PostMapping("/{externalId}/cfo-approve")
    @PreAuthorize("hasAnyRole('CFO','PLATFORM_ADMIN')")
    public BiaQuantitativeResponse cfoApprove(@PathVariable UUID externalId,
                                              @RequestBody @Valid CfoApprovalRequest req) {
        return service.cfoApprove(externalId, req);
    }

    @PostMapping("/{externalId}/board-approve")
    @PreAuthorize(BOARD_APPROVE_GUARD)
    public BiaQuantitativeResponse boardApprove(@PathVariable UUID externalId,
                                                @RequestBody @Valid BoardApprovalRequest req) {
        return service.boardApprove(externalId, req.minutesRef());
    }

    @DeleteMapping("/{externalId}")
    @PreAuthorize("hasAnyRole('BCM_MANAGER','PLATFORM_ADMIN')")
    public ResponseEntity<BiaQuantitativeResponse> archive(@PathVariable UUID externalId) {
        return ResponseEntity.ok(service.archive(externalId));
    }
}

package com.tss.grc.module.ethics.whistleblower.controller;

import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.RetaliationComplaintRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.RetaliationComplaintResponse;
import com.tss.grc.module.ethics.whistleblower.service.WhistleblowerRetaliationService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST surface for the Whistleblower retaliation-protection sub-flow.
 *
 * <p>Owned by an <b>independent protector</b>. Authorisation is by permission, not by role name
 * (F2-145(e) slice 6): read is {@code ETHICS:READ_CASES} — the same case-file audience as the hotline
 * itself — and disposition is {@code ETHICS:PROTECT}, an action V371 created for these four endpoints
 * alone.
 *
 * <h3>⚠ Why PROTECT is not ETHICS:TRIAGE, and why the numbers said it could be</h3>
 * <p>A retaliation complaint is very often <em>about the handling of the underlying case</em>. Whoever
 * triaged and investigated that case must therefore not be the person who validates, upholds or dismisses
 * the complaint — the same self-review defect V367 removed from {@code sharia} three slices earlier, where
 * an auditor could approve what they audited. So the old guard named {@code COMPLIANCE_OFFICER} and
 * {@code PLATFORM_ADMIN} and deliberately <b>not</b> {@code ETHICS_OFFICER}, who owns triage next door.
 *
 * <p>Mapping this onto {@code ETHICS:WRITE} — which the catalogue's description invited — would have handed
 * disposition to {@code ETHICS_OFFICER} and {@code BOARD_SECRETARY}. <b>No user-population diff could have
 * objected:</b> both roles have zero active users, so the COI tier, the triage tier and this tier are the
 * same two people in dev and three different authorities in the guards. The count would have reported a
 * clean conversion while the independence control disappeared.
 *
 * <h3>The protector the javadoc promised and the guard never had</h3>
 * <p>This comment used to say write is "HR Director / Compliance Officer / Platform Admin". No role named
 * {@code HR_DIRECTOR} exists in {@code iam.roles} — the nearest real row is {@code HR_LEAD} — and the guard
 * never listed either, so the independent protector has always in practice been the compliance officer.
 * That gap is filed rather than fixed here: adding a role to {@code ETHICS:PROTECT} is a decision about who
 * is independent, and after this conversion it is a grant a tenant can make instead of a jar somebody has
 * to ship.
 */
@RestController
@RequestMapping("/api/v1/ethics/whistleblower/retaliation")
@RequiredArgsConstructor
@RequireModule("ETHICS")
@Tag(name = "Ethics — Whistleblower Retaliation Sub-flow (Sprint 13)")
@SecurityRequirement(name = "bearerAuth")
public class WhistleblowerRetaliationController {

    // ── Authorisation ────────────────────────────────────────────────────────
    // F2-145(e) slice 6. Converted from hasAnyRole('ETHICS_OFFICER','COMPLIANCE_OFFICER',
    // 'INTERNAL_AUDITOR','AUDIT_COMMITTEE_MEMBER','PLATFORM_ADMIN') on the reads and
    // hasAnyRole('COMPLIANCE_OFFICER','PLATFORM_ADMIN') on the four disposition endpoints. Populations
    // measured against iam.role_permissions and preserved exactly:
    //   READ_CASES  3  ETHICS_OFFICER COMPLIANCE_OFFICER INTERNAL_AUDITOR — shared with the hotline
    //   PROTECT     1  COMPLIANCE_OFFICER                                — the narrowest tier in ethics
    private static final String READ_GUARD    = "hasPermission('ETHICS','READ_CASES')";
    private static final String PROTECT_GUARD = "hasPermission('ETHICS','PROTECT')";

    private final WhistleblowerRetaliationService service;

    @GetMapping
    @PreAuthorize(READ_GUARD)
    public List<RetaliationComplaintResponse> list(@RequestParam(value = "caseId", required = false) Long caseId) {
        return caseId == null ? service.list() : service.listForCase(caseId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_GUARD)
    public RetaliationComplaintResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(PROTECT_GUARD)
    @Operation(summary = "Submit a retaliation claim linked to an existing whistleblower case.")
    public ResponseEntity<RetaliationComplaintResponse> claim(@Valid @RequestBody RetaliationComplaintRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.claim(req));
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize(PROTECT_GUARD)
    public RetaliationComplaintResponse validate(@PathVariable Long id,
                                                   @RequestParam(required = false) Long protectorUserId,
                                                   Authentication auth) {
        return service.validate(id, protectorUserId != null ? protectorUserId : userId(auth));
    }

    @PostMapping("/{id}/uphold")
    @PreAuthorize(PROTECT_GUARD)
    public RetaliationComplaintResponse uphold(@PathVariable Long id,
                                                 @RequestParam(required = false) Long protectorUserId,
                                                 Authentication auth) {
        return service.uphold(id, protectorUserId != null ? protectorUserId : userId(auth));
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize(PROTECT_GUARD)
    public RetaliationComplaintResponse dismiss(@PathVariable Long id,
                                                  @RequestParam(required = false) Long protectorUserId,
                                                  Authentication auth) {
        return service.dismiss(id, protectorUserId != null ? protectorUserId : userId(auth));
    }

    private static Long userId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails ud) return ud.getUserId();
        return null;
    }
}

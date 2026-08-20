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
     *
     * <p>⚠ <b>F2-261 — half of that paragraph was already false when F2-254 repeated it, and it had
     * been repeated into three artefacts.</b> F2-223 converted {@code cfoApprove} to
     * {@code CurrentActor.username()}, with a comment saying so, and the CFO half of F2-152 has been
     * closed ever since; only the board half was real. 🔑 <b>A claim about a consequence is the
     * least-verified part of a filing</b> (F2-192), and this one propagated from F2-152's filing into
     * this javadoc and then into the F2-254 block below without anybody opening the service.
     *
     * <h3>F2-254 — this guard is STILL administrator-only, and that is deliberate</h3>
     * {@code EXECUTIVE_REVIEWER} is a row in {@code iam.roles} in both databases and is held by
     * <b>no user</b> in either, so this expression admits {@code PLATFORM_ADMIN} and nobody else —
     * which is the same outcome F2-52 was written to fix. Measured, not reasoned: the one live
     * quantitative BIA in dev <em>and</em> prod is still parked at {@code CFO_APPROVED} with
     * {@code board_approved_at} NULL, exactly where the paragraph above found it.
     *
     * <p>🔑 <b>The paragraph above chose this role because it "already holds {@code BCM:APPROVE}",
     * which is a fact about GRANTS. Whether a guard admits anybody is a fact about
     * {@code iam.user_roles}.</b> A role's grant list tells you what the platform trusts it with;
     * only its holders tell you whether it is anybody. Three separate slices have now corrected a
     * ghost inside one of these expressions and left a zero-holder role standing beside it — F2-52
     * here and on {@code SubprocessorController}, and F2-161 on {@code ExerciseRuntimeController
     * #start}, whose comment removed {@code FACILITATOR} for "admitting nobody" and kept
     * {@code BCM_MANAGER}, which admits nobody.
     *
     * <p>⬜ <b>Not converted, because the blocker above is real and now measured.</b>
     * {@code hasPermission('BCM','APPROVE')} would land {@code cfo-approve} and
     * {@code board-approve} on one permission and let one principal take both tiers — and the
     * four-eyes bar that would make that safe is <b>not buildable today</b>:
     * {@code sys.columns} says {@code bcm.bia_quantitative} has {@code cfo_approver_name} (a
     * caller-supplied string) and <b>no {@code board_approved_by} column at all</b>, so there is
     * no identity for either tier to compare. Fixing F2-152 is the prerequisite, and choosing who
     * holds a financial versus a board approval is an owner decision plus a migration. Filed.
     *
     * <h3>F2-261 — the blocker had two halves and one of them is gone</h3>
     * The paragraph above is two claims stacked: <i>(a)</i> no identity exists to compare, and
     * <i>(b)</i> who may take a financial versus a board approval is an owner decision. 🔑 <b>Only
     * (a) was a blocker; (b) is a question, and stating them as one sentence made the whole thing
     * read as owner-gated.</b> F2-178's rule — re-measure a recorded blocker before believing its
     * estimate — and here the estimate was right about (b) and wrong about (a) being permanent.
     *
     * <p><b>(a) is closed.</b> V384 adds {@code board_approved_by}, {@code boardApprove} stamps it
     * from the authenticated principal and refuses without one, and
     * {@code QuantitativeBiaService.requireDifferentParty} now enforces that the board signatory is
     * neither the CFO approver nor the BCM lead reviewer — with the same bar one stage lower, so
     * "review it, then approve your own review as CFO" is refused too. So the sentence three
     * paragraphs up — <i>"nothing enforces that they are different people, so the guard is the only
     * separation there is"</i> — <b>is no longer true, and the guard is no longer the only
     * separation.</b> ⚠ The CFO half of the prerequisite needed nothing: F2-223 had already done it.
     *
     * <p><b>(b) is still an owner decision, and this slice deliberately does not take it.</b> A
     * board sign-off is a higher authority than a CFO sign-off; sharing one permission would let
     * anybody entitled to the financial tier take the board tier on any record they had not
     * personally CFO-approved. The four-eyes bar makes that <em>safe from self-approval</em>, which
     * is not the same as making it <em>right</em>. So both guards stay as they are, both remain
     * administrator-only in practice ({@code EXECUTIVE_REVIEWER} and {@code CFO} are held by no user
     * in either database), and §B2b keeps both rows — now with the technical prerequisite discharged
     * and only the authority question outstanding.
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
    // F2-254: STILL administrator-only and deliberately so - CFO is held by no user in either
    // database, and it holds only BCM:EXPORT, so it is not even a role the platform trusts with
    // a BCM approval. See BOARD_APPROVE_GUARD above: converting both tiers onto BCM:APPROVE
    // collapses a three-stage escalation, and the bar that would replace it needs an approver
    // identity this table does not have. Filed, not swept.
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

    // F2-254: was hasAnyRole('BCM_MANAGER','PLATFORM_ADMIN') and BCM_MANAGER is held by NO user
    // in dev or prod, so this admitted the platform administrator and nobody else. Audience diff
    // measured before converting (the V362 landmine): both names hold BCM:DELETE, so nobody is
    // dropped and GRC_ADMINISTRATOR is admitted. AUDITOR and VIEWER hold BCM:READ+EXPORT and NOT
    // DELETE, and RISK_MANAGER holds WRITE and not DELETE - probed, so no reader-only role and no
    // write-only role slips in. The READ guards on this controller keep their role lists: CISO is
    // named there and holds only BCM:EXPORT, so converting them would drop a live role.
    @DeleteMapping("/{externalId}")
    @PreAuthorize("hasPermission('BCM','DELETE')")
    public ResponseEntity<BiaQuantitativeResponse> archive(@PathVariable UUID externalId) {
        return ResponseEntity.ok(service.archive(externalId));
    }
}

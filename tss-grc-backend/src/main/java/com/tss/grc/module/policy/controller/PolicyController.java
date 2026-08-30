package com.tss.grc.module.policy.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.policy.entity.PolicyAttestation;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.entity.PolicyException;
import com.tss.grc.module.policy.service.PolicyManagementService;
import com.tss.grc.module.policy.service.PolicyManagementService.PolicyAggregate;
import com.tss.grc.module.attachment.dto.AttachmentResponse;
import com.tss.grc.module.attachment.service.AttachmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.tss.grc.module.admin.committee.entity.CommitteeReferral;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Policy Management REST endpoints.
 *
 * <pre>
 *   /api/policy/documents                       CRUD + transitions + new-version
 *   /api/policy/documents/expiry-sweep          POST → flip past-expiry PUBLISHED → EXPIRED
 *   /api/policy/aggregate                       KPI rollup
 *
 *   /api/policy/exceptions                      CRUD + transition
 *   /api/policy/exceptions/expiry-sweep         POST → flip past-expiry APPROVED → EXPIRED
 *
 *   /api/policy/attestations                    list + create + attest + decline
 * </pre>
 */
@RestController
@RequestMapping("/api/policy")
@RequiredArgsConstructor
@RequireModule("COMPLIANCE")
@Tag(name = "Policy Management")
public class PolicyController {

    private final PolicyManagementService service;
    private final AttachmentService attachments;

    private static String actor(Authentication auth) {
        return auth == null ? null : auth.getName();
    }

    // ─── Documents ─────────────────────────────────────────────────

    @GetMapping("/documents")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public List<PolicyDocument> list(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(defaultValue = "false") boolean latestOnly) {
        return service.list(status, category, latestOnly);
    }

    @GetMapping("/documents/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public PolicyDocument get(@PathVariable Long id) { return service.get(id); }

    @PostMapping("/documents")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public ResponseEntity<PolicyDocument> create(@RequestBody PolicyDocument body, Authentication auth) {
        return ResponseEntity.ok(service.create(body, actor(auth)));
    }

    @PutMapping("/documents/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public PolicyDocument update(@PathVariable Long id, @RequestBody PolicyDocument body) {
        return service.update(id, body);
    }

    @PostMapping("/documents/{id}/transition")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public PolicyDocument transition(@PathVariable Long id,
                                     @RequestBody Map<String, String> body,
                                     Authentication auth) {
        return service.transition(id, body.get("to"), actor(auth), isComplianceAdmin(auth));
    }

    // ── Track D (S5): route policy approval through the committee engine ──────
    @PostMapping("/documents/{id}/refer-to-committee")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public CommitteeReferral referToCommittee(@PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body, Authentication auth) {
        Long committeeId = (body != null && body.get("committeeId") != null)
            ? Long.valueOf(body.get("committeeId").toString()) : null;
        return service.referToCommittee(id, committeeId, actor(auth));
    }

    @GetMapping("/documents/{id}/committee")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Map<String, Object> policyCommitteeReferral(@PathVariable Long id) {
        return service.committeeReferral(id);
    }

    @PostMapping("/documents/{id}/committee/resolution")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public CommitteeReferral linkPolicyResolution(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        // F2-394: resolutionId is required; an absent key must be a clean 400, not
        // body.get("resolutionId").toString() -> NullPointerException -> opaque 500 (the sibling
        // committeeId extraction in referToCommittee already null-guards).
        Object raw = body == null ? null : body.get("resolutionId");
        if (raw == null) throw new IllegalArgumentException("resolutionId is required");
        return service.linkPolicyResolution(id, Long.valueOf(raw.toString()));
    }

    @PostMapping("/documents/{policyCode}/new-version")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public PolicyDocument newVersion(@PathVariable String policyCode,
                                     @RequestBody Map<String, String> body) {
        return service.newVersion(policyCode, body.get("newVersion"));
    }

    /** CH-12: field-level diff between two versions of a policy code. */
    @GetMapping("/documents/{policyCode}/diff")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Map<String, Object> diff(@PathVariable String policyCode,
                                    @RequestParam String from, @RequestParam String to) {
        return service.diffVersions(policyCode, from, to);
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documents/expiry-sweep")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public Map<String, Object> expirySweep() {
        List<Long> flipped = service.runExpirySweep();
        return Map.of("flippedCount", flipped.size(), "flippedIds", flipped);
    }

    @GetMapping("/aggregate")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public PolicyAggregate aggregate() { return service.aggregate(); }

    /**
     * C-11 — audit evidence-package manifest for one policy: the policy plus its
     * uploaded attachments (with sha256 + download links) and its control /
     * framework links, assembled into a single artefact an auditor can pull in
     * one call (the basis for an exportable evidence pack).
     */
    @GetMapping("/documents/{id}/evidence-package")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public EvidencePackage evidencePackage(@PathVariable Long id) {
        PolicyDocument p = service.get(id);
        List<AttachmentResponse> atts = attachments.list("PolicyDocument", id);
        return new EvidencePackage(p, atts.size(), atts,
            p.getRelatedFramework(), p.getRelatedControls(),
            service.resolveControlsForPolicy(id),
            LocalDateTime.now().toString());
    }

    /** Assembled audit evidence package for a policy (C-11 + C-12 resolved controls). */
    public record EvidencePackage(
        PolicyDocument policy,
        int attachmentCount,
        List<AttachmentResponse> attachments,
        String relatedFramework,
        String relatedControls,
        List<PolicyManagementService.ResolvedControl> resolvedControls,
        String generatedAt
    ) {}

    /** C-12 — resolved control cross-links for one policy (read-side; powers the
     *  "mapped controls" UI panel without pulling the full evidence package). */
    @GetMapping("/documents/{id}/resolved-controls")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public List<PolicyManagementService.ResolvedControl> resolvedControls(@PathVariable Long id) {
        return service.resolveControlsForPolicy(id);
    }

    /** C-12 — set/replace a policy's control FK links. Body: {"controlIds":[1,2,3]}. */
    @PutMapping("/documents/{id}/controls")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public List<PolicyManagementService.ResolvedControl> setControls(
            @PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        return service.setControlLinks(id, body.get("controlIds"));
    }

    // ─── Exceptions ────────────────────────────────────────────────

    @GetMapping("/exceptions")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public List<PolicyException> listExceptions(@RequestParam(required = false) Long policyId,
                                                @RequestParam(required = false) String status) {
        return service.listExceptions(policyId, status);
    }

    @GetMapping("/exceptions/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public PolicyException getException(@PathVariable Long id) { return service.getException(id); }

    @PostMapping("/exceptions")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public ResponseEntity<PolicyException> createException(@RequestBody PolicyException body) {
        return ResponseEntity.ok(service.createException(body));
    }

    @PutMapping("/exceptions/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public PolicyException updateException(@PathVariable Long id, @RequestBody PolicyException body) {
        return service.updateException(id, body);
    }

    @PostMapping("/exceptions/{id}/transition")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public PolicyException transitionException(@PathVariable Long id,
                                               @RequestBody Map<String, String> body,
                                               Authentication auth) {
        return service.transitionException(id, body.get("to"), actor(auth), body.get("reason"));
    }

    @DeleteMapping("/exceptions/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','DELETE')")
    public ResponseEntity<Void> deleteException(@PathVariable Long id) {
        service.deleteException(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exceptions/expiry-sweep")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public Map<String, Object> exceptionExpirySweep() {
        List<Long> flipped = service.runExceptionExpirySweep();
        return Map.of("flippedCount", flipped.size(), "flippedIds", flipped);
    }

    // ─── Attestations ──────────────────────────────────────────────

    @GetMapping("/attestations")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public List<PolicyAttestation> listAttestations(@RequestParam(required = false) Long policyId,
                                                    @RequestParam(required = false) String username,
                                                    @RequestParam(required = false) String status) {
        return service.listAttestations(policyId, username, status);
    }

    @PostMapping("/attestations")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public ResponseEntity<PolicyAttestation> createAttestation(@RequestBody PolicyAttestation body) {
        return ResponseEntity.ok(service.createAttestation(body));
    }

    @PostMapping("/attestations/{id}/attest")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public PolicyAttestation attest(@PathVariable Long id,
                                    @RequestBody(required = false) Map<String, String> body,
                                    Authentication auth) {
        // SEC-CMP-004: the attesting identity is the authenticated principal, never a
        // body field. Object-level ownership (caller == assignee) and the admin
        // override are enforced in the service.
        return service.attest(id, body == null ? null : body.get("evidenceRef"),
            actor(auth), isComplianceAdmin(auth));
    }

    @PostMapping("/attestations/{id}/decline")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public PolicyAttestation decline(@PathVariable Long id,
                                     @RequestBody Map<String, String> body,
                                     Authentication auth) {
        // SEC-CMP-004: only the assignee (or a compliance/platform admin) may decline.
        return service.decline(id, body.get("reason"), actor(auth), isComplianceAdmin(auth));
    }

    /** Compliance/platform-admin override for proxy attestation / campaign cleanup. */
    private static boolean isComplianceAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String role = ga.getAuthority();
            if ("ROLE_PLATFORM_ADMIN".equals(role) || "ROLE_COMPLIANCE_ADMIN".equals(role)) return true;
        }
        return false;
    }
}

package com.tss.grc.module.policy.controller;

import com.tss.grc.module.policy.entity.PolicyAttestation;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.entity.PolicyException;
import com.tss.grc.module.policy.service.PolicyService;
import com.tss.grc.module.policy.service.PolicyService.PolicyAggregate;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
@Tag(name = "Policy Management")
public class PolicyController {

    private final PolicyService service;

    private static String actor(Authentication auth) {
        return auth == null ? null : auth.getName();
    }

    // ─── Documents ─────────────────────────────────────────────────

    @GetMapping("/documents")
    @PreAuthorize("isAuthenticated()")
    public List<PolicyDocument> list(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(defaultValue = "false") boolean latestOnly) {
        return service.list(status, category, latestOnly);
    }

    @GetMapping("/documents/{id}")
    @PreAuthorize("isAuthenticated()")
    public PolicyDocument get(@PathVariable Long id) { return service.get(id); }

    @PostMapping("/documents")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PolicyDocument> create(@RequestBody PolicyDocument body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PutMapping("/documents/{id}")
    @PreAuthorize("isAuthenticated()")
    public PolicyDocument update(@PathVariable Long id, @RequestBody PolicyDocument body) {
        return service.update(id, body);
    }

    @PostMapping("/documents/{id}/transition")
    @PreAuthorize("isAuthenticated()")
    public PolicyDocument transition(@PathVariable Long id,
                                     @RequestBody Map<String, String> body,
                                     Authentication auth) {
        return service.transition(id, body.get("to"), actor(auth));
    }

    @PostMapping("/documents/{policyCode}/new-version")
    @PreAuthorize("isAuthenticated()")
    public PolicyDocument newVersion(@PathVariable String policyCode,
                                     @RequestBody Map<String, String> body) {
        return service.newVersion(policyCode, body.get("newVersion"));
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documents/expiry-sweep")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> expirySweep() {
        List<Long> flipped = service.runExpirySweep();
        return Map.of("flippedCount", flipped.size(), "flippedIds", flipped);
    }

    @GetMapping("/aggregate")
    @PreAuthorize("isAuthenticated()")
    public PolicyAggregate aggregate() { return service.aggregate(); }

    // ─── Exceptions ────────────────────────────────────────────────

    @GetMapping("/exceptions")
    @PreAuthorize("isAuthenticated()")
    public List<PolicyException> listExceptions(@RequestParam(required = false) Long policyId,
                                                @RequestParam(required = false) String status) {
        return service.listExceptions(policyId, status);
    }

    @GetMapping("/exceptions/{id}")
    @PreAuthorize("isAuthenticated()")
    public PolicyException getException(@PathVariable Long id) { return service.getException(id); }

    @PostMapping("/exceptions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PolicyException> createException(@RequestBody PolicyException body) {
        return ResponseEntity.ok(service.createException(body));
    }

    @PutMapping("/exceptions/{id}")
    @PreAuthorize("isAuthenticated()")
    public PolicyException updateException(@PathVariable Long id, @RequestBody PolicyException body) {
        return service.updateException(id, body);
    }

    @PostMapping("/exceptions/{id}/transition")
    @PreAuthorize("isAuthenticated()")
    public PolicyException transitionException(@PathVariable Long id,
                                               @RequestBody Map<String, String> body,
                                               Authentication auth) {
        return service.transitionException(id, body.get("to"), actor(auth), body.get("reason"));
    }

    @DeleteMapping("/exceptions/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteException(@PathVariable Long id) {
        service.deleteException(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exceptions/expiry-sweep")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> exceptionExpirySweep() {
        List<Long> flipped = service.runExceptionExpirySweep();
        return Map.of("flippedCount", flipped.size(), "flippedIds", flipped);
    }

    // ─── Attestations ──────────────────────────────────────────────

    @GetMapping("/attestations")
    @PreAuthorize("isAuthenticated()")
    public List<PolicyAttestation> listAttestations(@RequestParam(required = false) Long policyId,
                                                    @RequestParam(required = false) String username,
                                                    @RequestParam(required = false) String status) {
        return service.listAttestations(policyId, username, status);
    }

    @PostMapping("/attestations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PolicyAttestation> createAttestation(@RequestBody PolicyAttestation body) {
        return ResponseEntity.ok(service.createAttestation(body));
    }

    @PostMapping("/attestations/{id}/attest")
    @PreAuthorize("isAuthenticated()")
    public PolicyAttestation attest(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return service.attest(id, body == null ? null : body.get("evidenceRef"));
    }

    @PostMapping("/attestations/{id}/decline")
    @PreAuthorize("isAuthenticated()")
    public PolicyAttestation decline(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.decline(id, body.get("reason"));
    }
}

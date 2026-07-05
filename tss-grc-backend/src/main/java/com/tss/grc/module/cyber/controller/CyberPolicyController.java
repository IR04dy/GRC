package com.tss.grc.module.cyber.controller;

import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.service.PolicyManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CY-I — cyber policy lifecycle. Rather than build a second policy engine, the
 * cyber workspace is wired to the shared {@code module/policy}
 * {@link PolicyManagementService}: full lifecycle (DRAFT→REVIEW→APPROVED→
 * PUBLISHED→EXPIRED→ARCHIVED), versioning ({@code policy_code}+{@code version}
 * + {@code new-version}), review-date/cadence, segregation-of-duty on approval,
 * and file upload via the generic {@code /api/v1/attachments/PolicyDocument/{id}}
 * subsystem (the same one CY-A wired cyber evidence to).
 *
 * <p>This surface is scoped to the cyber category space ({@link #CYBER_CATEGORIES})
 * and CYBER-permission gated, so a CISO/analyst manages cyber policies without a
 * COMPLIANCE grant. New policies default to {@code CYBER}; the view also surfaces
 * the pre-existing INFORMATION_SECURITY / INCIDENT_RESPONSE policies.
 *
 * <pre>
 *   GET    /api/v1/cyber/policies[?status=&latestOnly=]
 *   GET    /api/v1/cyber/policies/{id}
 *   POST   /api/v1/cyber/policies
 *   PUT    /api/v1/cyber/policies/{id}
 *   POST   /api/v1/cyber/policies/{id}/transition        { "to": "REVIEW" }
 *   POST   /api/v1/cyber/policies/{policyCode}/new-version { "newVersion": "2.0" }
 *   DELETE /api/v1/cyber/policies/{id}
 *   (file upload: POST /api/v1/attachments/PolicyDocument/{id})
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/cyber/policies")
@RequiredArgsConstructor
@Tag(name = "Cyber Security — Policies")
@SecurityRequirement(name = "bearerAuth")
public class CyberPolicyController {

    /** Category space the cyber workspace owns. New cyber policies default to CYBER. */
    static final Set<String> CYBER_CATEGORIES = Set.of("CYBER", "INFORMATION_SECURITY", "INCIDENT_RESPONSE");

    private final PolicyManagementService policy;

    private static String actor(Authentication auth) { return auth == null ? null : auth.getName(); }

    /** PLATFORM_ADMIN / CISO may proxy-approve (waives the author-approves-own SoD guard). */
    private static boolean isCyberAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String r = ga.getAuthority();
            if ("ROLE_PLATFORM_ADMIN".equals(r) || "ROLE_CISO".equals(r)) return true;
        }
        return false;
    }

    /** Load a policy and 404 unless it lives in the cyber category space. */
    private PolicyDocument requireCyber(Long id) {
        PolicyDocument p = policy.get(id);   // tenant-scoped; 404 if missing
        if (p.getCategory() == null || !CYBER_CATEGORIES.contains(p.getCategory()))
            throw new EntityNotFoundException("Cyber policy not found: " + id);
        return p;
    }

    @GetMapping
    @PreAuthorize("hasPermission('CYBER','READ')")
    @Operation(summary = "List cyber policies (CYBER / INFORMATION_SECURITY / INCIDENT_RESPONSE). Optional status filter; latestOnly collapses to the newest version per code.")
    public List<PolicyDocument> list(@RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "false") boolean latestOnly) {
        return policy.list(null, null, latestOnly).stream()
                .filter(p -> p.getCategory() != null && CYBER_CATEGORIES.contains(p.getCategory()))
                .filter(p -> status == null || status.equalsIgnoreCase(p.getStatus()))
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('CYBER','READ')")
    public PolicyDocument get(@PathVariable Long id) { return requireCyber(id); }

    @PostMapping
    @PreAuthorize("hasPermission('CYBER','WRITE')")
    public ResponseEntity<PolicyDocument> create(@RequestBody PolicyDocument body) {
        // Keep cyber-created policies inside the cyber category space.
        if (body.getCategory() == null || !CYBER_CATEGORIES.contains(body.getCategory()))
            body.setCategory("CYBER");
        return ResponseEntity.status(HttpStatus.CREATED).body(policy.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('CYBER','WRITE')")
    public PolicyDocument update(@PathVariable Long id, @RequestBody PolicyDocument body) {
        requireCyber(id);
        // Never let an update move a cyber policy out of the cyber category space.
        if (body.getCategory() != null && !CYBER_CATEGORIES.contains(body.getCategory()))
            body.setCategory(null);   // ignore an out-of-scope re-category
        return policy.update(id, body);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('CYBER','WRITE')")
    @Operation(summary = "Advance the lifecycle: DRAFT→REVIEW→APPROVED→PUBLISHED (→EXPIRED|ARCHIVED). Publishing requires an effective date; the author cannot approve their own policy.")
    public PolicyDocument transition(@PathVariable Long id,
                                     @RequestBody Map<String, String> body,
                                     Authentication auth) {
        requireCyber(id);
        return policy.transition(id, body.get("to"), actor(auth), isCyberAdmin(auth));
    }

    @PostMapping("/{policyCode}/new-version")
    @PreAuthorize("hasPermission('CYBER','WRITE')")
    public PolicyDocument newVersion(@PathVariable String policyCode,
                                     @RequestBody Map<String, String> body) {
        boolean isCyberCode = policy.list(null, null, false).stream()
                .anyMatch(p -> policyCode.equals(p.getPolicyCode())
                        && p.getCategory() != null && CYBER_CATEGORIES.contains(p.getCategory()));
        if (!isCyberCode) throw new EntityNotFoundException("Cyber policy code not found: " + policyCode);
        return policy.newVersion(policyCode, body.get("newVersion"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('CYBER','WRITE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        requireCyber(id);
        policy.delete(id);
        return ResponseEntity.noContent().build();
    }
}

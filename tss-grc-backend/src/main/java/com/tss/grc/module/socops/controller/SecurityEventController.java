package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.service.SecurityEventService;
import com.tss.grc.module.socops.service.SocOpsExportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Browse + triage + resolve operations on ingested SIEM events.
 *
 * <pre>
 *   GET    /api/v1/socops/events[?page=&size=]
 *   GET    /api/v1/socops/events/:id
 *   POST   /api/v1/socops/events/:id/triage    { "assigneeUserId": 42 }
 *   POST   /api/v1/socops/events/:id/escalate  { "note": "..." }   → creates + links a cyber incident
 *   POST   /api/v1/socops/events/:id/resolve   { "resolution": "...", "falsePositive": false }
 *   DELETE /api/v1/socops/events/:id
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/socops/events")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — Security Events")
public class SecurityEventController {

    private final SecurityEventService svc;
    private final SocOpsExportService exportSvc;

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @GetMapping
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public Page<SecurityEvent> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return svc.list(PageRequest.of(page, Math.min(size, 200)));
    }

    /**
     * CY-F — export the security-event register as Excel or Arabic-capable PDF.
     *
     * <p>F2-27: CYBER:EXPORT is AND-ed in front of the role list. The {@code socops} module
     * has NO permissions of its own in {@code iam.permissions} — it is guarded entirely by
     * role names — so the SOC register could not be restricted through the catalogue at all.
     * CYBER:EXPORT is the right module (socops is the SOC arm of cyber) and all four roles
     * listed here already hold it, so this AND costs nobody access while making the export
     * administrable. Converting the residual role check to a permission is F2-05's scope.
     */
    @GetMapping("/export")
    // F2-61(c) / ADR-017 D-10c — CYBER:READ ANDed in. This handler was NOT a genuine exception to
    // the containment; it was exempt from the F2-61 ratchet only because a role list counted as
    // "narrowed", and the ratchet no longer accepts that on its own. Measured in both databases:
    // none of the four named roles holds CYBER:EXPORT without CYBER:READ, so the AND costs nobody
    // access — the same standard the role list itself was added under. The two handlers that ARE
    // exceptions (MlModelController#export, BcpBinderController#download) would each lose a named
    // role, are named in ExportAuthorizationRatchetTest, and carry an ADR-017 clause.
    @PreAuthorize("hasPermission('CYBER','EXPORT') and hasPermission('CYBER','READ') and "
        + "hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "XLSX") String format) {
        byte[] body = exportSvc.exportSecurityEvents(format);
        boolean pdf = "PDF".equalsIgnoreCase(format);
        String filename = "soc-security-events." + (pdf ? "pdf" : "xlsx");
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public SecurityEvent get(@PathVariable Long id) {
        return svc.get(id);
    }

    @PostMapping("/{id}/triage")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN')")
    public SecurityEvent triage(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long assignee = body == null ? null : numericId(body.get("assigneeUserId"));
        return svc.triage(id, assignee);
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN')")
    public SecurityEvent escalate(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String note = body == null ? null : (String) body.get("note");
        return svc.escalateToIncident(id, note);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN')")
    public SecurityEvent resolve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String resolution = body == null ? null : (String) body.get("resolution");
        boolean fp = body != null && Boolean.TRUE.equals(body.get("falsePositive"));
        return svc.resolve(id, resolution, fp);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CISO')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static Long numericId(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.valueOf(o.toString()); } catch (NumberFormatException e) { return null; }
    }
}

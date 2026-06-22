package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.service.SecurityEventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
 *   POST   /api/v1/socops/events/:id/triage   { "assigneeUserId": 42 }
 *   POST   /api/v1/socops/events/:id/resolve  { "resolution": "...", "falsePositive": false }
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

    @GetMapping
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public Page<SecurityEvent> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return svc.list(PageRequest.of(page, Math.min(size, 200)));
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

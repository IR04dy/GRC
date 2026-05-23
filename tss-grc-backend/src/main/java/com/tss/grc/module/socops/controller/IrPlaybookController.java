package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.IrPlaybook;
import com.tss.grc.module.socops.entity.IrPlaybookStep;
import com.tss.grc.module.socops.service.IrPlaybookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * <pre>
 *   GET  /api/v1/socops/playbooks
 *   GET  /api/v1/socops/playbooks/templates
 *   GET  /api/v1/socops/playbooks/:id
 *   GET  /api/v1/socops/playbooks/:id/steps
 *   POST /api/v1/socops/playbooks/clone    { "templateCode": "IR-PHISHING-01" }
 *   POST /api/v1/socops/playbooks
 *   POST /api/v1/socops/playbooks/:id/invoke
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/socops/playbooks")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — IR Playbooks")
public class IrPlaybookController {

    private final IrPlaybookService svc;

    @GetMapping
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public List<IrPlaybook> list() { return svc.list(); }

    @GetMapping("/templates")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN')")
    public List<IrPlaybook> templates() { return svc.templates(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public IrPlaybook get(@PathVariable Long id) { return svc.get(id); }

    @GetMapping("/{id}/steps")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public List<IrPlaybookStep> steps(@PathVariable Long id) { return svc.stepsOf(id); }

    @PostMapping("/clone")
    @PreAuthorize("hasAnyRole('CISO','PLATFORM_ADMIN')")
    public IrPlaybook clone(@RequestBody Map<String, String> body) {
        return svc.cloneFromTemplate(body.get("templateCode"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CISO','PLATFORM_ADMIN')")
    public IrPlaybook save(@RequestBody IrPlaybook body) { return svc.save(body); }

    @PostMapping("/{id}/invoke")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN')")
    public IrPlaybook invoke(@PathVariable Long id) { return svc.recordInvocation(id); }
}

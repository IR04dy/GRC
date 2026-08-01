package com.tss.grc.module.bcm.controller;

import com.tss.grc.module.bcm.entity.CrisisCommsAudience;
import com.tss.grc.module.bcm.entity.CrisisCommsDistribution;
import com.tss.grc.module.bcm.entity.CrisisCommsTemplate;
import com.tss.grc.module.bcm.service.CrisisCommsService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * BCM Crisis Communication endpoints — assessment §5.5 #5.
 * Templates / audiences / distributions live under /api/v1/bcm/comms.
 */
@RestController
@RequestMapping("/api/v1/bcm/comms")
@RequiredArgsConstructor
@RequireModule("BCM")
@Tag(name = "BCM — Crisis Communication")
@SecurityRequirement(name = "bearerAuth")
public class CrisisCommsController {

    private final CrisisCommsService service;

    @GetMapping("/templates")
    @PreAuthorize("hasPermission('BCM','READ')")
    public List<CrisisCommsTemplate> listTemplates() {
        return service.listTemplates();
    }

    @PostMapping("/templates")
    @PreAuthorize("hasPermission('BCM','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public CrisisCommsTemplate createTemplate(@RequestBody CrisisCommsTemplate t) {
        return service.createTemplate(t);
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public CrisisCommsTemplate updateTemplate(@PathVariable Long id, @RequestBody CrisisCommsTemplate t) {
        return service.updateTemplate(id, t);
    }

    @DeleteMapping("/templates/{id}")
    @PreAuthorize("hasPermission('BCM','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable Long id) {
        service.deleteTemplate(id);
    }

    @GetMapping("/audiences")
    @PreAuthorize("hasPermission('BCM','READ')")
    public List<CrisisCommsAudience> listAudiences() {
        return service.listAudiences();
    }

    @PostMapping("/audiences")
    @PreAuthorize("hasPermission('BCM','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public CrisisCommsAudience createAudience(@RequestBody CrisisCommsAudience a) {
        return service.createAudience(a);
    }

    @PutMapping("/audiences/{id}")
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public CrisisCommsAudience updateAudience(@PathVariable Long id, @RequestBody CrisisCommsAudience a) {
        return service.updateAudience(id, a);
    }

    @GetMapping("/distributions")
    @PreAuthorize("hasPermission('BCM','READ')")
    public List<CrisisCommsDistribution> listDistributions() {
        return service.listDistributions();
    }

    @PostMapping("/send")
    @PreAuthorize("hasPermission('BCM','WRITE')")
    @Operation(summary = "Send a template to an audience. body: {templateId, audienceId, planId?, placeholders?}")
    @ResponseStatus(HttpStatus.CREATED)
    public CrisisCommsDistribution send(@RequestBody Map<String, Object> body) {
        Long templateId = ((Number) body.get("templateId")).longValue();
        Long audienceId = ((Number) body.get("audienceId")).longValue();
        Long planId = body.get("planId") instanceof Number n ? n.longValue() : null;
        Long sentById = body.get("sentById") instanceof Number n ? n.longValue() : null;
        @SuppressWarnings("unchecked")
        Map<String, String> placeholders = body.get("placeholders") instanceof Map<?, ?> m
            ? (Map<String, String>) m : Map.of();
        return service.send(templateId, audienceId, planId, sentById, placeholders);
    }
}

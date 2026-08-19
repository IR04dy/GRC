package com.tss.grc.module.ethics.controller;

import com.tss.grc.module.ethics.dto.AssignAnalystRequest;
import com.tss.grc.module.ethics.entity.AmlAlert;
import com.tss.grc.module.ethics.service.AmlAlertService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ethics/aml-alerts")
@RequiredArgsConstructor
@RequireModule("ETHICS")
@Tag(name = "Ethics & Compliance — AML Transaction Alerts")
@SecurityRequirement(name = "bearerAuth")
public class AmlAlertController {

    private final AmlAlertService service;

    @GetMapping
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Page<AmlAlert> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String severity,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.list(status, type, severity, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public AmlAlert get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public AmlAlert create(@RequestBody AmlAlert body) { return service.create(body); }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public AmlAlert update(@PathVariable Long id, @RequestBody AmlAlert body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }

    /**
     * F2-249 — binds a narrow record instead of an untyped {@code Map}, so the two halves of an
     * assignment are declared rather than fished out by key. See {@link AssignAnalystRequest}.
     */
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public AmlAlert assign(@PathVariable Long id, @Valid @RequestBody AssignAnalystRequest body) {
        return service.assign(id, body);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public AmlAlert transition(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String target = body.get("target") != null ? body.get("target").toString() : null;
        if (target == null) throw new IllegalArgumentException("target is required");
        return service.transition(id, target, body);
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public Map<String, Object> analytics() { return service.analytics(); }
}

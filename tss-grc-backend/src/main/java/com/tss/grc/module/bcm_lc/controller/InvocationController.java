package com.tss.grc.module.bcm_lc.controller;

import com.tss.grc.module.bcm_lc.dto.InvocationDto;
import com.tss.grc.module.bcm_lc.service.InvocationService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/bcm-lc/invocations")
@RequiredArgsConstructor
@RequireModule("BCM")
public class InvocationController {

    private final InvocationService service;

    @GetMapping
    @PreAuthorize("hasPermission('BCM','READ')")
    public List<InvocationDto> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return activeOnly ? service.listActive() : service.listForOrg();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('BCM','READ')")
    public InvocationDto get(@PathVariable String id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public ResponseEntity<InvocationDto> create(@RequestBody @Valid InvocationDto req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public InvocationDto transition(@PathVariable String id, @RequestBody @Valid InvocationDto.TransitionRequest req) {
        return service.transition(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('BCM','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}

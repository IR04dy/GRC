package com.tss.grc.module.regchange.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.regchange.dto.RegulatoryChangeDto;
import com.tss.grc.module.regchange.service.RegulatoryChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/regchange/changes")
@RequiredArgsConstructor
@RequireModule("COMPLIANCE")
public class RegulatoryChangeController {

    private final RegulatoryChangeService service;

    @GetMapping
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public List<RegulatoryChangeDto> list(@RequestParam(required = false) String regulator) {
        return regulator != null ? service.byRegulator(regulator) : service.listForOrg();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public RegulatoryChangeDto get(@PathVariable String id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public ResponseEntity<RegulatoryChangeDto> create(@RequestBody @Valid RegulatoryChangeDto req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('COMPLIANCE','WRITE')")
    public RegulatoryChangeDto transition(@PathVariable String id,
                                          @RequestBody @Valid RegulatoryChangeDto.TransitionRequest req) {
        return service.transition(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('COMPLIANCE','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}

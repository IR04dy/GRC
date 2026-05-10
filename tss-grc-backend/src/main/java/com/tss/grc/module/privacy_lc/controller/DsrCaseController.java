package com.tss.grc.module.privacy_lc.controller;

import com.tss.grc.module.privacy_lc.dto.DsrCaseDto;
import com.tss.grc.module.privacy_lc.service.DsrCaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/privacy-lc/dsr-cases")
@RequiredArgsConstructor
public class DsrCaseController {

    private final DsrCaseService service;

    @GetMapping
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public List<DsrCaseDto> list(@RequestParam(defaultValue = "false") boolean overdueOnly) {
        return overdueOnly ? service.overdue() : service.listForOrg();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public DsrCaseDto get(@PathVariable String id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public ResponseEntity<DsrCaseDto> create(@RequestBody @Valid DsrCaseDto req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public DsrCaseDto transition(@PathVariable String id, @RequestBody @Valid DsrCaseDto.TransitionRequest req) {
        return service.transition(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('PRIVACY','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}

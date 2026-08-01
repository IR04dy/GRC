package com.tss.grc.module.nca_ecc.controller;

import com.tss.grc.module.nca_ecc.dto.NcaEccFilingDto;
import com.tss.grc.module.nca_ecc.service.NcaEccFilingService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/nca-ecc/filings")
@RequiredArgsConstructor
@RequireModule("COMPLIANCE")
public class NcaEccFilingController {

    private final NcaEccFilingService service;

    @GetMapping
    @PreAuthorize("hasPermission('CYBER','READ') or hasPermission('COMPLIANCE','READ')")
    public List<NcaEccFilingDto> list() { return service.listForOrg(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('CYBER','READ') or hasPermission('COMPLIANCE','READ')")
    public NcaEccFilingDto get(@PathVariable String id) { return service.get(id); }

    @GetMapping("/by-year/{year}")
    @PreAuthorize("hasPermission('CYBER','READ') or hasPermission('COMPLIANCE','READ')")
    public NcaEccFilingDto byYear(@PathVariable Integer year) { return service.byYear(year); }

    @PostMapping
    @PreAuthorize("hasPermission('CYBER','WRITE') or hasPermission('COMPLIANCE','WRITE')")
    public ResponseEntity<NcaEccFilingDto> create(@RequestBody @Valid NcaEccFilingDto req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('CYBER','WRITE') or hasPermission('COMPLIANCE','WRITE')")
    public NcaEccFilingDto transition(@PathVariable String id,
                                      @RequestBody @Valid NcaEccFilingDto.TransitionRequest req) {
        return service.transition(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}

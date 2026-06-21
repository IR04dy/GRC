package com.tss.grc.module.capa.controller;

import com.tss.grc.module.capa.dto.CapaTaskDto;
import com.tss.grc.module.capa.service.CapaTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/capa/tasks")
@RequiredArgsConstructor
public class CapaTaskController {

    private final CapaTaskService service;

    @GetMapping
    @PreAuthorize("hasPermission('CAPA','READ')")
    public List<CapaTaskDto> list(@RequestParam(defaultValue = "false") boolean overdueOnly,
                                  @RequestParam(required = false) String originType,
                                  @RequestParam(required = false) String originId) {
        if (overdueOnly) return service.overdue();
        if (originType != null && originId != null) return service.listForOrigin(originType, originId);
        return service.listForOrg();
    }

    /** Sidebar pending-counter shorthand — two longs ({@code open},
     *  {@code overdue}) for one cheap COUNT each. Avoids pulling N rows
     *  just to render "CAPA · 12" in the nav. */
    @GetMapping("/counts")
    @PreAuthorize("hasPermission('CAPA','READ')")
    public CapaTaskService.CapaCounts counts() {
        return service.counts();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('CAPA','READ')")
    public CapaTaskDto get(@PathVariable String id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('CAPA','WRITE')")
    public ResponseEntity<CapaTaskDto> create(@RequestBody @Valid CapaTaskDto req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('CAPA','WRITE')")
    public CapaTaskDto transition(@PathVariable String id,
                                  @RequestBody @Valid CapaTaskDto.TransitionRequest req) {
        return service.transition(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}

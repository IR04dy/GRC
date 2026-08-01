package com.tss.grc.module.cyber.controller;

import com.tss.grc.module.cyber.entity.CloudService;
import com.tss.grc.module.cyber.service.CloudServiceService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cyber/cloud-services")
@RequiredArgsConstructor
@RequireModule("CYBER")
@Tag(name = "Cloud Services (NCA CSCC)")
public class CloudServiceController {

    private final CloudServiceService service;

    @GetMapping
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public List<CloudService> list() { return service.list(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public CloudService get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public ResponseEntity<CloudService> create(@Valid @RequestBody CloudService body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('CYBER', 'WRITE')")
    public CloudService update(@PathVariable Long id, @RequestBody CloudService body) {
        return service.update(id, body);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasPermission('CYBER', 'READ')")
    public Map<String, Long> stats() { return service.stats(); }
}

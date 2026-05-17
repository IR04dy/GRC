package com.tss.grc.module.scm.resilience.controller;

import com.tss.grc.module.scm.resilience.entity.SupplierResilienceTest;
import com.tss.grc.module.scm.resilience.service.SupplierResilienceTestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/scm/resilience/tests")
@RequiredArgsConstructor
@Tag(name = "Supplier resilience tests")
public class SupplierResilienceTestController {

    private final SupplierResilienceTestService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<SupplierResilienceTest> list(@RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, size);
        return status == null ? service.list(p) : service.listByStatus(status, p);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public SupplierResilienceTest get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SupplierResilienceTest> create(@RequestBody SupplierResilienceTest req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("isAuthenticated()")
    public SupplierResilienceTest start(@PathVariable Long id) { return service.start(id); }

    @PostMapping("/{id}/complete")
    @PreAuthorize("isAuthenticated()")
    public SupplierResilienceTest complete(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.complete(id, body);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public SupplierResilienceTest approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.approve(id, body.getOrDefault("approverName", "admin"));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public SupplierResilienceTest cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.cancel(id, body.getOrDefault("reason", ""));
    }
}

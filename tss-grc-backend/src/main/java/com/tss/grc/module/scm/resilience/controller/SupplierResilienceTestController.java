package com.tss.grc.module.scm.resilience.controller;

import com.tss.grc.module.scm.resilience.dto.SupplierResilienceDtos.TestCreateRequest;
import com.tss.grc.module.scm.resilience.dto.SupplierResilienceDtos.TestResponse;
import com.tss.grc.module.scm.resilience.entity.SupplierResilienceTest;
import com.tss.grc.module.scm.resilience.service.SupplierResilienceTestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    @PreAuthorize("hasPermission('SCM','READ')")
    public Page<SupplierResilienceTest> list(@RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, size);
        return status == null ? service.list(p) : service.listByStatus(status, p);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SCM','READ')")
    public SupplierResilienceTest get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public ResponseEntity<TestResponse> create(@Valid @RequestBody TestCreateRequest req) {
        return ResponseEntity.status(201).body(TestResponse.from(service.create(req)));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public SupplierResilienceTest start(@PathVariable Long id) { return service.start(id); }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public SupplierResilienceTest complete(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.complete(id, body);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public SupplierResilienceTest approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.approve(id, body.getOrDefault("approverName", "admin"));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public SupplierResilienceTest cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.cancel(id, body.getOrDefault("reason", ""));
    }
}

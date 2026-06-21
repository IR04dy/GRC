package com.tss.grc.module.privacy.processor.controller;

import com.tss.grc.module.privacy.processor.entity.ProcessorAssessment;
import com.tss.grc.module.privacy.processor.service.ProcessorAssessmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/privacy/processor-assessments")
@RequiredArgsConstructor
@Tag(name = "Processor due diligence (PDPL Art. 28)")
public class ProcessorAssessmentController {

    private final ProcessorAssessmentService service;

    @GetMapping
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public Page<ProcessorAssessment> list(@RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, size);
        return status == null ? service.list(p) : service.listByStatus(status, p);
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public ProcessorAssessment get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public ResponseEntity<ProcessorAssessment> create(@RequestBody ProcessorAssessment req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PostMapping("/{id}/score")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public ProcessorAssessment score(@PathVariable Long id, @RequestBody Map<String, String> controls) {
        return service.scoreAndComplete(id, controls);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public ProcessorAssessment approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.approve(id, body.getOrDefault("approverName", "admin"));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public ProcessorAssessment reject(@PathVariable Long id) { return service.reject(id); }
}

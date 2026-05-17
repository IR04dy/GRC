package com.tss.grc.module.tprm.cyber.controller;

import com.tss.grc.module.tprm.cyber.entity.ThirdPartyCyberAssessment;
import com.tss.grc.module.tprm.cyber.service.ThirdPartyCyberAssessmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tprm/cyber-assessments")
@RequiredArgsConstructor
@Tag(name = "Third-party cyber risk")
public class ThirdPartyCyberAssessmentController {

    private final ThirdPartyCyberAssessmentService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<ThirdPartyCyberAssessment> list(@RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, size);
        return status == null ? service.list(p) : service.listByStatus(status, p);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ThirdPartyCyberAssessment get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ThirdPartyCyberAssessment> create(@RequestBody ThirdPartyCyberAssessment req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PostMapping("/{id}/posture")
    @PreAuthorize("isAuthenticated()")
    public ThirdPartyCyberAssessment recordPosture(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.recordPosture(id, body);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    public ThirdPartyCyberAssessment submit(@PathVariable Long id) { return service.submit(id); }

    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public ThirdPartyCyberAssessment approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.approve(id, body.getOrDefault("approverName", "admin"));
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize("isAuthenticated()")
    public ThirdPartyCyberAssessment retire(@PathVariable Long id) { return service.retire(id); }
}

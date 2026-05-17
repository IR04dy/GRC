package com.tss.grc.module.ndi.reports.controller;

import com.tss.grc.module.ndi.reports.entity.NdiReportDefinition;
import com.tss.grc.module.ndi.reports.service.NdiReportService;
import com.tss.grc.module.ndi.reports.service.NdiReportService.ReportResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ndi/reports")
@RequiredArgsConstructor
@Tag(name = "NDI — Reports")
public class NdiReportController {

    private final NdiReportService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<NdiReportDefinition> list(@RequestParam(required = false) String type) {
        return service.list(type);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public NdiReportDefinition get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NdiReportDefinition> create(@RequestBody NdiReportDefinition body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public NdiReportDefinition update(@PathVariable Long id, @RequestBody NdiReportDefinition body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/run")
    @PreAuthorize("isAuthenticated()")
    public ReportResult run(@PathVariable Long id, @RequestParam(required = false) Long cycleId) {
        return service.run(id, cycleId);
    }

    @PostMapping("/run-adhoc")
    @PreAuthorize("isAuthenticated()")
    public ReportResult runAdHoc(@RequestParam String type, @RequestParam(required = false) Long cycleId) {
        return service.runAdHoc(type, cycleId);
    }
}

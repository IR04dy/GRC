package com.tss.grc.module.reporting.controller;

import com.tss.grc.module.reporting.entity.ReportRun;
import com.tss.grc.module.reporting.entity.ReportSchedule;
import com.tss.grc.module.reporting.service.ReportScheduleService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reporting/schedules")
@RequiredArgsConstructor
@Tag(name = "Reporting — Scheduled reports (cron + email)")
@SecurityRequirement(name = "bearerAuth")
public class ReportScheduleController {

    private final ReportScheduleService service;

    @GetMapping
    @PreAuthorize("hasPermission('REPORTING','READ')")
    public Page<ReportSchedule> list(@PageableDefault(size = 50) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('REPORTING','READ')")
    public ReportSchedule get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('REPORTING','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportSchedule create(@RequestBody ReportSchedule body) { return service.create(body); }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('REPORTING','WRITE')")
    public ReportSchedule update(@PathVariable Long id, @RequestBody ReportSchedule body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('REPORTING','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }

    @PostMapping("/{id}/run-now")
    @PreAuthorize("hasPermission('REPORTING','WRITE')")
    public ReportRun runNow(@PathVariable Long id) { return service.runNow(id); }

    @GetMapping("/{id}/runs")
    @PreAuthorize("hasPermission('REPORTING','READ')")
    public List<ReportRun> runs(@PathVariable Long id) { return service.recentRunsForSchedule(id); }

    @GetMapping("/runs/recent")
    @PreAuthorize("hasPermission('REPORTING','READ')")
    public List<ReportRun> recentRuns() { return service.recentRuns(); }
}

package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.dto.IrPlaybookRunDetail;
import com.tss.grc.module.socops.entity.IrPlaybookRun;
import com.tss.grc.module.socops.service.IrPlaybookRunService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Runtime execution of IR playbooks (epic SOC-B). A run is created by
 * {@code POST /api/v1/socops/playbooks/{id}/invoke}; these endpoints then let the
 * SOC work it:
 *
 * <pre>
 *   GET  /api/v1/socops/playbook-runs                                → all runs (newest first)
 *   GET  /api/v1/socops/playbook-runs/:runId                         → run header + its tasks
 *   POST /api/v1/socops/playbook-runs/:runId/tasks/:taskId/start     → mark a task IN_PROGRESS
 *   POST /api/v1/socops/playbook-runs/:runId/tasks/:taskId/complete  → complete a task; advances the run
 * </pre>
 *
 * <p>Kept on a distinct base path (not nested under {@code /playbooks/{id}}) so the
 * run-by-id lookups never collide with {@code GET /playbooks/{id}}.
 */
@RestController
@RequestMapping("/api/v1/socops/playbook-runs")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — IR Playbook Runs")
public class IrPlaybookRunController {

    private final IrPlaybookRunService svc;

    @GetMapping
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public List<IrPlaybookRun> list() { return svc.listRuns(); }

    @GetMapping("/{runId}")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public IrPlaybookRunDetail get(@PathVariable Long runId) { return svc.getRunDetail(runId); }

    @PostMapping("/{runId}/tasks/{taskId}/start")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN')")
    public IrPlaybookRunDetail startTask(@PathVariable Long runId, @PathVariable Long taskId) {
        return svc.startTask(runId, taskId);
    }

    @PostMapping("/{runId}/tasks/{taskId}/complete")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN')")
    public IrPlaybookRunDetail completeTask(@PathVariable Long runId, @PathVariable Long taskId,
                                            @RequestBody(required = false) Map<String, String> body) {
        String note = body == null ? null : body.get("note");
        return svc.completeTask(runId, taskId, note);
    }
}

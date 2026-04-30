package com.tss.grc.module.bcm.controller;

import com.tss.grc.module.bcm.entity.BcmPlan;
import com.tss.grc.module.bcm.entity.BiaRecord;
import com.tss.grc.module.bcm.entity.CriticalProcess;
import com.tss.grc.module.bcm.service.BcmService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bcm")
@RequiredArgsConstructor
@Tag(name = "BCM")
@SecurityRequirement(name = "bearerAuth")
public class BcmController {

    private final BcmService bcmService;

    // ── BCM Plans ─────────────────────────────────────────────────────────────

    @GetMapping("/plans")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public Page<BcmPlan> listPlans(@PageableDefault(size = 20) Pageable pageable) {
        return bcmService.listPlans(pageable);
    }

    @GetMapping("/plans/{id}")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public BcmPlan getPlan(@PathVariable Long id) {
        return bcmService.getPlan(id);
    }

    @PostMapping("/plans")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public BcmPlan createPlan(@RequestBody BcmPlan req) {
        return bcmService.createPlan(req);
    }

    @PutMapping("/plans/{id}")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BcmPlan updatePlan(@PathVariable Long id, @RequestBody BcmPlan req) {
        return bcmService.updatePlan(id, req);
    }

    @DeleteMapping("/plans/{id}")
    @PreAuthorize("hasPermission('BCM', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(@PathVariable Long id) {
        bcmService.deletePlan(id);
    }

    // ── Critical Processes ────────────────────────────────────────────────────

    @GetMapping("/processes")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public Page<CriticalProcess> listProcesses(@PageableDefault(size = 20) Pageable pageable) {
        return bcmService.listProcesses(pageable);
    }

    @GetMapping("/processes/{id}")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public CriticalProcess getProcess(@PathVariable Long id) {
        return bcmService.getProcess(id);
    }

    @PostMapping("/processes")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public CriticalProcess createProcess(@RequestBody CriticalProcess req) {
        return bcmService.createProcess(req);
    }

    @PutMapping("/processes/{id}")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public CriticalProcess updateProcess(@PathVariable Long id, @RequestBody CriticalProcess req) {
        return bcmService.updateProcess(id, req);
    }

    @DeleteMapping("/processes/{id}")
    @PreAuthorize("hasPermission('BCM', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProcess(@PathVariable Long id) {
        bcmService.deleteProcess(id);
    }

    // ── BIA Records ───────────────────────────────────────────────────────────

    @GetMapping("/bia")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public List<BiaRecord> listBia(@RequestParam(required = false) Long processId) {
        return bcmService.listBia(processId);
    }

    @PostMapping("/bia")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public BiaRecord createBia(@RequestBody BiaRecord req) {
        return bcmService.createBia(req);
    }

    @PutMapping("/bia/{id}")
    @PreAuthorize("hasPermission('BCM', 'WRITE')")
    public BiaRecord updateBia(@PathVariable Long id, @RequestBody BiaRecord req) {
        return bcmService.updateBia(id, req);
    }

    @DeleteMapping("/bia/{id}")
    @PreAuthorize("hasPermission('BCM', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBia(@PathVariable Long id) {
        bcmService.deleteBia(id);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("hasPermission('BCM', 'READ')")
    public Map<String, Long> getStats() {
        return bcmService.getStats();
    }
}

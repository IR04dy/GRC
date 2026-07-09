package com.tss.grc.module.sust.finance.controller;

import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.CreateInstrumentRequest;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.CreateProjectRequest;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.InstrumentSummaryResponse;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.ProposeAllocationRequest;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.RejectAllocationRequest;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.TransitionInstrumentRequest;
import com.tss.grc.module.sust.finance.entity.GreenInstrument;
import com.tss.grc.module.sust.finance.entity.GreenProject;
import com.tss.grc.module.sust.finance.entity.InstrumentAllocation;
import com.tss.grc.module.sust.finance.service.GreenFinanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Green-Finance uplift G1 (plan §4/§8) — Green Sukuk & sustainable-finance register.
 * Reads = SUST READ; register mutations + allocation proposals = SUST WRITE;
 * allocation decisions (approve/reject) = SUST APPROVE (maker-checker; the service
 * additionally blocks self-approval).
 */
@RestController
@RequestMapping("/api/v1/sustainability/finance")
@RequiredArgsConstructor
@Tag(name = "Sustainability — Green finance (sukuk / use-of-proceeds)")
public class GreenFinanceController {

    private static final String READ_GUARD    = "hasPermission('SUST','READ')";
    private static final String WRITE_GUARD   = "hasPermission('SUST','WRITE')";
    private static final String APPROVE_GUARD = "hasPermission('SUST','APPROVE')";

    private final GreenFinanceService service;

    // ── instruments ─────────────────────────────────────────────────────

    @GetMapping("/instruments")
    @PreAuthorize(READ_GUARD)
    public List<GreenInstrument> listInstruments() { return service.listInstruments(); }

    @PostMapping("/instruments")
    @PreAuthorize(WRITE_GUARD)
    public GreenInstrument createInstrument(@Valid @RequestBody CreateInstrumentRequest r) {
        return service.createInstrument(r);
    }

    @PostMapping("/instruments/{id}/transition")
    @PreAuthorize(APPROVE_GUARD)
    public GreenInstrument transitionInstrument(@PathVariable Long id,
                                                @Valid @RequestBody TransitionInstrumentRequest r) {
        return service.transitionInstrument(id, r);
    }

    @GetMapping("/instruments/{id}/summary")
    @PreAuthorize(READ_GUARD)
    public InstrumentSummaryResponse summary(@PathVariable Long id) { return service.summary(id); }

    @GetMapping("/instruments/{id}/allocations")
    @PreAuthorize(READ_GUARD)
    public List<InstrumentAllocation> allocations(@PathVariable Long id) { return service.listAllocations(id); }

    // ── projects ────────────────────────────────────────────────────────

    @GetMapping("/projects")
    @PreAuthorize(READ_GUARD)
    public List<GreenProject> listProjects() { return service.listProjects(); }

    @PostMapping("/projects")
    @PreAuthorize(WRITE_GUARD)
    public GreenProject createProject(@Valid @RequestBody CreateProjectRequest r) {
        return service.createProject(r);
    }

    // ── use-of-proceeds ledger (maker-checker) ──────────────────────────

    @PostMapping("/allocations")
    @PreAuthorize(WRITE_GUARD)
    public InstrumentAllocation propose(@Valid @RequestBody ProposeAllocationRequest r) {
        return service.proposeAllocation(r);
    }

    @PostMapping("/allocations/{id}/approve")
    @PreAuthorize(APPROVE_GUARD)
    public InstrumentAllocation approve(@PathVariable Long id) { return service.approveAllocation(id); }

    @PostMapping("/allocations/{id}/reject")
    @PreAuthorize(APPROVE_GUARD)
    public InstrumentAllocation reject(@PathVariable Long id, @Valid @RequestBody RejectAllocationRequest r) {
        return service.rejectAllocation(id, r.reason());
    }
}

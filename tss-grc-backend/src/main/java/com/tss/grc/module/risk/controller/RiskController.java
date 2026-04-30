package com.tss.grc.module.risk.controller;

import com.tss.grc.module.risk.dto.RiskRequest;
import com.tss.grc.module.risk.dto.RiskResponse;
import com.tss.grc.module.risk.service.RiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/risk/risks")
@RequiredArgsConstructor
@Tag(name = "Risk Management")
@SecurityRequirement(name = "bearerAuth")
public class RiskController {

    private final RiskService riskService;

    @GetMapping
    @PreAuthorize("hasPermission('RISK', 'READ')")
    @Operation(summary = "List risks with optional status filter")
    public Page<RiskResponse> list(
        @RequestParam(required = false) String status,
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return riskService.list(status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('RISK', 'READ')")
    public RiskResponse get(@PathVariable Long id) {
        return riskService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public RiskResponse create(@Valid @RequestBody RiskRequest request) {
        return riskService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('RISK', 'WRITE')")
    public RiskResponse update(@PathVariable Long id, @Valid @RequestBody RiskRequest request) {
        return riskService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('RISK', 'DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        riskService.delete(id);
    }
}

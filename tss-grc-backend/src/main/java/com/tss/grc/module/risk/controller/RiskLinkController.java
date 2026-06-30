package com.tss.grc.module.risk.controller;

import com.tss.grc.module.risk.dto.RiskLinkDtos.CreateDto;
import com.tss.grc.module.risk.dto.RiskLinkDtos.Response;
import com.tss.grc.module.risk.service.RiskLinkService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Typed register linkage endpoints — ERM epic E-G.
 *
 * <pre>
 *   GET    /api/v1/risk/risks/{riskId}/links   list a risk's typed links
 *   POST   /api/v1/risk/risks/{riskId}/links   add a typed link
 *   DELETE /api/v1/risk/links/{id}             remove a link (soft delete)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
@Tag(name = "Risk register linkage (E-G)")
public class RiskLinkController {

    private final RiskLinkService service;

    @GetMapping("/risks/{riskId}/links")
    @PreAuthorize("hasPermission('RISK','READ')")
    public List<Response> list(@PathVariable Long riskId) {
        return service.listForRisk(riskId).stream().map(Response::from).toList();
    }

    @PostMapping("/risks/{riskId}/links")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public ResponseEntity<Response> create(@PathVariable Long riskId, @Valid @RequestBody CreateDto dto) {
        return ResponseEntity.ok(Response.from(service.create(riskId, dto)));
    }

    @DeleteMapping("/links/{id}")
    @PreAuthorize("hasPermission('RISK','WRITE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

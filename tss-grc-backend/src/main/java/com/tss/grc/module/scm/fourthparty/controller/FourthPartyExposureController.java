package com.tss.grc.module.scm.fourthparty.controller;

import com.tss.grc.module.scm.fourthparty.entity.FourthPartyExposure;
import com.tss.grc.module.scm.fourthparty.service.FourthPartyExposureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST surface for fourth-party exposures.
 *
 * <p>Path: {@code /api/scm/fourth-party}.
 */
@RestController
@RequestMapping("/api/scm/fourth-party")
@RequiredArgsConstructor
@Tag(name = "Fourth-party exposure")
@SecurityRequirement(name = "bearerAuth")
public class FourthPartyExposureController {

    private final FourthPartyExposureService service;

    @GetMapping
    @PreAuthorize("hasPermission('SCM','READ')")
    @Operation(summary = "List fourth-party exposures (paginated).")
    public Page<FourthPartyExposure> list(@RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "100") int size) {
        var pg = PageRequest.of(page, size);
        return status == null ? service.list(pg) : service.listByStatus(status, pg);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('SCM','READ')")
    public FourthPartyExposure get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public ResponseEntity<FourthPartyExposure> create(@RequestBody FourthPartyExposure req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public FourthPartyExposure update(@PathVariable Long id, @RequestBody FourthPartyExposure req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public FourthPartyExposure validate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.validate(id, body.getOrDefault("validatorName", "system"));
    }

    @PostMapping("/{id}/mitigate")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public FourthPartyExposure mitigate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.mitigate(id, body.getOrDefault("notesEn", ""));
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize("hasPermission('SCM','WRITE')")
    public FourthPartyExposure retire(@PathVariable Long id) {
        return service.retire(id);
    }
}

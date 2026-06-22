package com.tss.grc.module.privacy.dsa.controller;

import com.tss.grc.module.privacy.dsa.entity.DataSharingAgreement;
import com.tss.grc.module.privacy.dsa.service.DataSharingAgreementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/privacy/data-sharing")
@RequiredArgsConstructor
@Tag(name = "Data sharing agreements")
public class DataSharingAgreementController {

    private final DataSharingAgreementService service;

    @GetMapping
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    @Operation(summary = "List data sharing agreements.")
    public Page<DataSharingAgreement> list(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, Math.min(size, 200));
        return status == null ? service.list(p) : service.listByStatus(status, p);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public DataSharingAgreement get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public ResponseEntity<DataSharingAgreement> create(@RequestBody DataSharingAgreement req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public DataSharingAgreement submit(@PathVariable Long id) { return service.submit(id); }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public DataSharingAgreement activate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.activate(id, body.get("signerName"), body.get("counterpartySigner"));
    }

    @PostMapping("/{id}/expire")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public DataSharingAgreement expire(@PathVariable Long id) { return service.expire(id); }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public DataSharingAgreement terminate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.terminate(id, body.getOrDefault("reasonEn", ""));
    }
}

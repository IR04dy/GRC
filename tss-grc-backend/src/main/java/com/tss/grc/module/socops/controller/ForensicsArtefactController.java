package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.ForensicsArtefact;
import com.tss.grc.module.socops.service.ForensicsArtefactService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/socops/forensics")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — Forensics & Chain of Custody")
public class ForensicsArtefactController {

    private final ForensicsArtefactService svc;

    @GetMapping
    @PreAuthorize("hasAnyRole('CISO','FORENSICS_LEAD','IR_LEAD','PLATFORM_ADMIN','LEGAL_COUNSEL','AUDITOR')")
    public Page<ForensicsArtefact> list(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return svc.list(PageRequest.of(page, Math.min(size, 200)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CISO','FORENSICS_LEAD','IR_LEAD','PLATFORM_ADMIN','LEGAL_COUNSEL','AUDITOR')")
    public ForensicsArtefact get(@PathVariable Long id) { return svc.get(id); }

    @GetMapping("/by-case/{caseCode}")
    @PreAuthorize("hasAnyRole('CISO','FORENSICS_LEAD','IR_LEAD','PLATFORM_ADMIN','LEGAL_COUNSEL','AUDITOR')")
    public List<ForensicsArtefact> byCase(@PathVariable String caseCode) { return svc.byCase(caseCode); }

    @PostMapping
    @PreAuthorize("hasAnyRole('FORENSICS_LEAD','IR_LEAD','CISO','PLATFORM_ADMIN')")
    public ForensicsArtefact create(@RequestBody ForensicsArtefact body) { return svc.save(body); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FORENSICS_LEAD','IR_LEAD','CISO','PLATFORM_ADMIN')")
    public ForensicsArtefact update(@PathVariable Long id, @RequestBody ForensicsArtefact body) {
        body.setId(id);
        return svc.save(body);
    }

    /** Transfer chain-of-custody. Every transfer writes an audit-log
     *  entry tied to the artefact so the chain can be reconstructed
     *  verbatim in a courtroom. */
    @PostMapping("/{id}/transfer-custody")
    @PreAuthorize("hasAnyRole('FORENSICS_LEAD','IR_LEAD','CISO','LEGAL_COUNSEL')")
    public ForensicsArtefact transferCustody(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return svc.transferCustody(id,
            body.get("newCustodian"), body.get("newLocation"), body.get("notes"));
    }

    @PostMapping("/{id}/verify-integrity")
    @PreAuthorize("hasAnyRole('FORENSICS_LEAD','IR_LEAD','CISO','PLATFORM_ADMIN')")
    public ForensicsArtefact verify(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean verified = body == null || !Boolean.FALSE.equals(body.get("verified"));
        return svc.verifyIntegrity(id, verified);
    }
}

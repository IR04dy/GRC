package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.RegulatorExamination;
import com.tss.grc.module.socops.service.RegulatorExaminationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/socops/regulator-exams")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — Regulator Examinations")
public class RegulatorExaminationController {

    private final RegulatorExaminationService svc;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CISO','PLATFORM_ADMIN','AUDITOR','RISK_MANAGER','COMPLIANCE_OFFICER')")
    public Page<RegulatorExamination> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return svc.list(PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CISO','PLATFORM_ADMIN','AUDITOR','COMPLIANCE_OFFICER')")
    public RegulatorExamination get(@PathVariable Long id) { return svc.get(id); }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('CISO','PLATFORM_ADMIN','COMPLIANCE_OFFICER')")
    public RegulatorExamination save(@RequestBody RegulatorExamination body) { return svc.save(body); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CISO','PLATFORM_ADMIN','COMPLIANCE_OFFICER')")
    public RegulatorExamination update(@PathVariable Long id, @RequestBody RegulatorExamination body) {
        body.setId(id);
        return svc.save(body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id); return ResponseEntity.noContent().build();
    }
}

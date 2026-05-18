package com.tss.grc.module.privacy.controller;

import com.tss.grc.module.privacy.entity.ConsentRecord;
import com.tss.grc.module.privacy.service.ConsentRecordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/privacy/consents")
@RequiredArgsConstructor
@Tag(name = "Consent Records (PDPL)")
public class ConsentRecordController {

    public record WithdrawBody(String reason) {}
    public record SupersedeBody(Long supersededByConsentId) {}

    private final ConsentRecordService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<ConsentRecord> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ConsentRecord get(@PathVariable Long id) { return service.get(id); }

    @GetMapping("/by-subject/{subjectRef}")
    @PreAuthorize("isAuthenticated()")
    public List<ConsentRecord> history(@PathVariable String subjectRef) {
        return service.historyFor(subjectRef);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConsentRecord> grant(@Valid @RequestBody ConsentRecord body) {
        return ResponseEntity.ok(service.grant(body));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("isAuthenticated()")
    public ConsentRecord withdraw(@PathVariable Long id, @RequestBody WithdrawBody body) {
        return service.withdraw(id, body.reason());
    }

    @PostMapping("/{id}/supersede")
    @PreAuthorize("isAuthenticated()")
    public ConsentRecord supersede(@PathVariable Long id, @RequestBody SupersedeBody body) {
        return service.supersede(id, body.supersededByConsentId());
    }

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> stats() { return service.stats(); }
}

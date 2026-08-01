package com.tss.grc.module.privacy.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.privacy.entity.Dpia;
import com.tss.grc.module.privacy.entity.DpiaMitigation;
import com.tss.grc.module.privacy.entity.DpiaQuestion;
import com.tss.grc.module.privacy.entity.DpiaReview;
import com.tss.grc.module.privacy.service.DpiaService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/privacy/dpias")
@RequiredArgsConstructor
@RequireModule("PDPL")
@Tag(name = "Privacy — DPIA")
@SecurityRequirement(name = "bearerAuth")
public class DpiaController {

    private final DpiaService service;

    @GetMapping
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public Page<Dpia> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public Map<String, Object> get(@PathVariable Long id) {
        return service.getWithChildren(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public Dpia create(@RequestBody Dpia req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public Dpia update(@PathVariable Long id, @RequestBody Dpia req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('PRIVACY','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    // ── Workflow ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/submit-for-review")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public Dpia submitForReview(@PathVariable Long id) {
        return service.submitForReview(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public Dpia approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return service.approve(id, body == null ? Map.of() : body);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public Dpia reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.reject(id, body);
    }

    @PostMapping("/{id}/expire")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    public Dpia expire(@PathVariable Long id) {
        return service.expire(id);
    }

    // ── Children ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/questions")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public DpiaQuestion addQuestion(@PathVariable Long id, @RequestBody DpiaQuestion req) {
        return service.addQuestion(id, req);
    }

    @PostMapping("/{id}/mitigations")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public DpiaMitigation addMitigation(@PathVariable Long id, @RequestBody DpiaMitigation req) {
        return service.addMitigation(id, req);
    }

    @PostMapping("/{id}/reviews")
    @PreAuthorize("hasPermission('PRIVACY','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public DpiaReview addReview(@PathVariable Long id, @RequestBody DpiaReview req) {
        return service.addReview(id, req);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasPermission('PRIVACY','READ')")
    public Map<String, Long> stats() {
        return service.stats();
    }
}

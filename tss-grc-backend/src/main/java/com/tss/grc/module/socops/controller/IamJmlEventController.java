package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.IamJmlEvent;
import com.tss.grc.module.socops.service.IamJmlEventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/socops/iam-jml")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — IAM Joiner/Mover/Leaver")
public class IamJmlEventController {

    private final IamJmlEventService svc;

    @GetMapping
    @PreAuthorize("hasAnyRole('CISO','IAM_LEAD','PLATFORM_ADMIN','AUDITOR','HR_LEAD')")
    public Page<IamJmlEvent> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "50") int size) {
        return svc.list(PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CISO','IAM_LEAD','PLATFORM_ADMIN','AUDITOR')")
    public IamJmlEvent get(@PathVariable Long id) { return svc.get(id); }

    @GetMapping("/privileged-reviews-due")
    @PreAuthorize("hasAnyRole('CISO','IAM_LEAD','PLATFORM_ADMIN','AUDITOR')")
    public List<IamJmlEvent> reviewsDue(@RequestParam(defaultValue = "30") int days) {
        return svc.privilegedReviewsDueWithinDays(days);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('JML_INGEST','IAM_LEAD','PLATFORM_ADMIN','CISO')")
    public IamJmlEvent create(@RequestBody IamJmlEvent body) { return svc.save(body); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('IAM_LEAD','PLATFORM_ADMIN','CISO')")
    public IamJmlEvent update(@PathVariable Long id, @RequestBody IamJmlEvent body) {
        body.setId(id);
        return svc.save(body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id); return ResponseEntity.noContent().build();
    }
}

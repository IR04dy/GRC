package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.DlpEvent;
import com.tss.grc.module.socops.service.DlpEventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/socops/dlp")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — DLP Events")
public class DlpEventController {

    private final DlpEventService svc;

    @GetMapping
    @PreAuthorize("hasAnyRole('CISO','SECURITY_ANALYST','DLP_REVIEWER','PLATFORM_ADMIN','AUDITOR')")
    public Page<DlpEvent> list(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int size) {
        return svc.list(PageRequest.of(page, Math.min(size, 200)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CISO','SECURITY_ANALYST','DLP_REVIEWER','PLATFORM_ADMIN','AUDITOR')")
    public DlpEvent get(@PathVariable Long id) { return svc.get(id); }

    @PostMapping("/ingest")
    @PreAuthorize("hasAnyRole('DLP_INGEST','PLATFORM_ADMIN','CISO')")
    public DlpEvent ingest(@RequestBody DlpEvent body) { return svc.ingest(body); }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','DLP_REVIEWER','CISO','PLATFORM_ADMIN')")
    public DlpEvent review(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return svc.review(id, body.get("outcome"), body.get("notes"));
    }
}

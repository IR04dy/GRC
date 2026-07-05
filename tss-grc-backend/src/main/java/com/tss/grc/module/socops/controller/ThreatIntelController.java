package com.tss.grc.module.socops.controller;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.socops.entity.ThreatIntel;
import com.tss.grc.module.socops.entity.ThreatIntelAction;
import com.tss.grc.module.socops.service.ThreatIntelService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/socops/threat-intel")
@RequiredArgsConstructor
@RequireModule("SOCOPS")
@Tag(name = "SOC Operations — Threat Intelligence")
public class ThreatIntelController {

    private final ThreatIntelService svc;

    @GetMapping
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public Page<ThreatIntel> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "50") int size) {
        return svc.list(PageRequest.of(page, Math.min(size, 200)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public ThreatIntel get(@PathVariable Long id) { return svc.get(id); }

    @PostMapping
    @PreAuthorize("hasAnyRole('TI_INGEST','SECURITY_ANALYST','CISO','PLATFORM_ADMIN')")
    public ThreatIntel save(@RequestBody ThreatIntel body) { return svc.save(body); }

    /**
     * SOC-E — action an IOC. Body may carry {@code actionType} (BLOCK/SCAN/HUNT/…; defaults to
     * REVIEWED), and optionally a concrete link target {@code targetType} (ASSET/VULNERABILITY/
     * DETECTION) + {@code targetId}. Legacy callers that send only {@code {actioned, notes}} still
     * work — they record a REVIEWED action with no target.
     */
    @PostMapping("/{id}/action")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN')")
    public ThreatIntel action(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String actionType = body.get("actionType") == null ? null : body.get("actionType").toString();
        String targetType = body.get("targetType") == null ? null : body.get("targetType").toString();
        Long targetId = body.get("targetId") instanceof Number n ? n.longValue() : null;
        // Accept either "notes" (new) or "actionNotes" (legacy FE payload).
        Object notesRaw = body.getOrDefault("notes", body.get("actionNotes"));
        String notes = notesRaw == null ? null : notesRaw.toString();
        return svc.actionIoc(id, actionType, targetType, targetId, notes);
    }

    /** SOC-E — the concrete action history (with target links) for one IOC. */
    @GetMapping("/{id}/actions")
    @PreAuthorize("hasAnyRole('SECURITY_ANALYST','CISO','PLATFORM_ADMIN','AUDITOR')")
    public List<ThreatIntelAction> actions(@PathVariable Long id) { return svc.listActions(id); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CISO')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id); return ResponseEntity.noContent().build();
    }
}

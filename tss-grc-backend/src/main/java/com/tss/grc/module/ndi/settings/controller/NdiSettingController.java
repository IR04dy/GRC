package com.tss.grc.module.ndi.settings.controller;

import com.tss.grc.module.ndi.settings.entity.NdiSetting;
import com.tss.grc.module.ndi.settings.service.NdiSettingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ndi/settings")
@RequiredArgsConstructor
@Tag(name = "NDI — Settings")
public class NdiSettingController {

    // SEC-NDI-001: settings are config — reads open to NDI readers, all mutations
    // restricted (was isAuthenticated() only).
    private static final String READ  = "hasPermission('NDI','READ')";
    private static final String ADMIN = "hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')";

    private final NdiSettingService service;

    @GetMapping
    @PreAuthorize(READ)
    public List<NdiSetting> list() { return service.list(); }

    @GetMapping("/grouped")
    @PreAuthorize(READ)
    public Map<String, List<NdiSetting>> grouped() { return service.listGrouped(); }

    @GetMapping("/by-key/{key}")
    @PreAuthorize(READ)
    public NdiSetting getByKey(@PathVariable String key) { return service.getByKey(key); }

    @PutMapping("/by-key/{key}")
    @PreAuthorize(ADMIN)
    public NdiSetting setValue(@PathVariable String key, @RequestBody Map<String, String> body) {
        return service.setValue(key, body.get("value"));
    }

    @PostMapping("/by-key/{key}/reset")
    @PreAuthorize(ADMIN)
    public NdiSetting reset(@PathVariable String key) {
        return service.resetToDefault(key);
    }

    @PostMapping
    @PreAuthorize(ADMIN)
    public ResponseEntity<NdiSetting> create(@RequestBody NdiSetting body) {
        return ResponseEntity.ok(service.create(body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ADMIN)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

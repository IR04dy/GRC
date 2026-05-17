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

    private final NdiSettingService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<NdiSetting> list() { return service.list(); }

    @GetMapping("/grouped")
    @PreAuthorize("isAuthenticated()")
    public Map<String, List<NdiSetting>> grouped() { return service.listGrouped(); }

    @GetMapping("/by-key/{key}")
    @PreAuthorize("isAuthenticated()")
    public NdiSetting getByKey(@PathVariable String key) { return service.getByKey(key); }

    @PutMapping("/by-key/{key}")
    @PreAuthorize("isAuthenticated()")
    public NdiSetting setValue(@PathVariable String key, @RequestBody Map<String, String> body) {
        return service.setValue(key, body.get("value"));
    }

    @PostMapping("/by-key/{key}/reset")
    @PreAuthorize("isAuthenticated()")
    public NdiSetting reset(@PathVariable String key) {
        return service.resetToDefault(key);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NdiSetting> create(@RequestBody NdiSetting body) {
        return ResponseEntity.ok(service.create(body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

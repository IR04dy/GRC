package com.tss.grc.module.ndi.framework.controller;

import com.tss.grc.module.ndi.framework.entity.NdiFrameworkItem;
import com.tss.grc.module.ndi.framework.service.NdiFrameworkItemService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * NDI Framework Library REST endpoints —
 * {@code /api/ndi/framework/*}.
 *
 * <pre>
 *   GET    /                       list (filter by component / domain)
 *   GET    /aggregate              counts per component/domain + placeholder vs official
 *   GET    /:id                    fetch one
 *   POST   /                       create
 *   PUT    /:id                    update
 *   POST   /:id/mark-official      flip is_official_import=true, is_placeholder=false
 *   POST   /:id/mark-placeholder   flip is_official_import=false, is_placeholder=true
 *   POST   /import-json            bulk upsert; body { "items": [...] }
 *   DELETE /:id                    soft-delete
 * </pre>
 */
@RestController
@RequestMapping("/api/ndi/framework")
@RequiredArgsConstructor
@Tag(name = "NDI — Framework Library")
public class NdiFrameworkItemController {

    // SEC-NDI-001: granular module authorization (was isAuthenticated() only).
    private static final String READ    = "hasPermission('NDI','READ')";
    private static final String WRITE   = "hasPermission('NDI','WRITE')";
    private static final String APPROVE = "hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')";

    private final NdiFrameworkItemService service;

    @GetMapping
    @PreAuthorize(READ)
    public Page<NdiFrameworkItem> list(
            @RequestParam(required = false) String component,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var p = PageRequest.of(page, size);
        if (component != null) return service.listByComponent(component, p);
        if (domain != null)    return service.listByDomain(domain, p);
        return service.list(p);
    }

    @GetMapping("/aggregate")
    @PreAuthorize(READ)
    public Map<String, Object> aggregate() { return service.aggregate(); }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public NdiFrameworkItem get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<NdiFrameworkItem> create(@RequestBody NdiFrameworkItem req) {
        return ResponseEntity.status(201).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public NdiFrameworkItem update(@PathVariable Long id, @RequestBody NdiFrameworkItem req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/mark-official")
    @PreAuthorize(WRITE)
    public NdiFrameworkItem markOfficial(@PathVariable Long id) { return service.markOfficial(id); }

    @PostMapping("/{id}/mark-placeholder")
    @PreAuthorize(WRITE)
    public NdiFrameworkItem markPlaceholder(@PathVariable Long id) { return service.markPlaceholder(id); }

    /** Body: {@code { "items": [ { ... }, { ... } ] }}. */
    @PostMapping("/import-json")
    @PreAuthorize(WRITE)
    public Map<String, Object> importJson(@RequestBody Map<String, Object> body) {
        Object items = body == null ? null : body.get("items");
        if (!(items instanceof List<?> list)) {
            throw new IllegalArgumentException("Body must contain { items: [...] }");
        }
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) list;
        return service.importJson(rows);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(APPROVE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}

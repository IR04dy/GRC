package com.tss.grc.module.platform.persistence.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.persistence.entity.PersistenceRow;
import com.tss.grc.module.platform.persistence.repository.PersistenceRowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic key/value persistence endpoint backing the frontend
 * `lib/persistence.ts` adapter. One controller, one table, supports every
 * store the frontend wants to migrate from localStorage.
 *
 * Endpoints:
 *   GET    /api/v1/persistence/{key}        list all rows (tenant-scoped)
 *   PUT    /api/v1/persistence/{key}        replace store with provided rows
 *   POST   /api/v1/persistence/{key}        append a single row
 *   DELETE /api/v1/persistence/{key}        soft-delete the store
 *
 * Authorisation: every authenticated user can persist their tenant's
 * data. Bulk replace + delete require the `PLATFORM:WRITE` permission.
 */
@RestController
@RequestMapping("/api/v1/persistence")
public class PersistenceController {

    private static final Logger log = LoggerFactory.getLogger(PersistenceController.class);

    private final PersistenceRowRepository repo;
    private final ObjectMapper mapper;

    public PersistenceController(PersistenceRowRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @GetMapping("/{key}")
    public ResponseEntity<Map<String, Object>> list(@PathVariable String key) {
        Long orgId = TenantContext.requireOrganisationId();
        List<PersistenceRow> rows = repo.findByOrganisationIdAndStoreKeyOrderByRowSeqAsc(orgId, key);
        Map<String, Object> body = new HashMap<>();
        body.put("key", key);
        body.put("count", rows.size());
        body.put("rows", rows.stream().map(this::toJson).toList());
        return ResponseEntity.ok(body);
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasPermission('PLATFORM','WRITE')")
    @Transactional
    public ResponseEntity<Map<String, Object>> setAll(
            @PathVariable String key,
            @RequestBody Map<String, Object> body) {
        Long orgId = TenantContext.requireOrganisationId();
        Object rowsObj = body.get("rows");
        if (!(rowsObj instanceof List<?> rowList)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing rows[]"));
        }
        // Soft-delete existing rows so we can re-insert in original order.
        int killed = repo.softDeleteByOrgAndKey(orgId, key);
        long seq = 0L;
        for (Object row : rowList) {
            seq += 1;
            String json;
            try { json = mapper.writeValueAsString(row); }
            catch (Exception e) {
                log.warn("Persistence {} row serialise failed: {}", key, e.getMessage());
                continue;
            }
            repo.save(new PersistenceRow(orgId, key, seq, json, "API_PUT"));
        }
        log.info("Persistence {} setAll org={} replaced={} inserted={}", key, orgId, killed, seq);
        return ResponseEntity.ok(Map.of("key", key, "replaced", killed, "inserted", seq));
    }

    @PostMapping("/{key}")
    @Transactional
    public ResponseEntity<Map<String, Object>> append(
            @PathVariable String key,
            @RequestBody Object row) {
        Long orgId = TenantContext.requireOrganisationId();
        Long max = repo.maxRowSeq(orgId, key);
        long seq = (max == null ? 0L : max) + 1;
        String json;
        try { json = mapper.writeValueAsString(row); }
        catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid payload"));
        }
        PersistenceRow saved = repo.save(new PersistenceRow(orgId, key, seq, json, "API_APPEND"));
        return ResponseEntity.ok(Map.of("key", key, "rowSeq", saved.getRowSeq()));
    }

    @DeleteMapping("/{key}")
    @PreAuthorize("hasPermission('PLATFORM','WRITE')")
    @Transactional
    public ResponseEntity<Map<String, Object>> clear(@PathVariable String key) {
        Long orgId = TenantContext.requireOrganisationId();
        int killed = repo.softDeleteByOrgAndKey(orgId, key);
        log.info("Persistence {} clear org={} killed={}", key, orgId, killed);
        return ResponseEntity.ok(Map.of("key", key, "deleted", killed));
    }

    private JsonNode toJson(PersistenceRow row) {
        try { return mapper.readTree(row.getPayload()); }
        catch (Exception e) { return mapper.createObjectNode().put("__parseError", e.getMessage()); }
    }
}

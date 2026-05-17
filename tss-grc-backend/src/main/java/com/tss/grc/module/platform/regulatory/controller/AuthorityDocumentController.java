package com.tss.grc.module.platform.regulatory.controller;

import com.tss.grc.module.platform.regulatory.entity.AuthorityDocument;
import com.tss.grc.module.platform.regulatory.entity.AuthorityDocumentCitation;
import com.tss.grc.module.platform.regulatory.service.AuthorityDocumentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST surface for the Authority Documents catalog.
 *
 * <pre>
 *   GET    /api/v1/regulatory/authority-documents          list (filter: family, status)
 *   GET    /api/v1/regulatory/authority-documents/rollup   per-family counts
 *   GET    /api/v1/regulatory/authority-documents/:id      get one
 *   POST   /api/v1/regulatory/authority-documents          create
 *   PUT    /api/v1/regulatory/authority-documents/:id      update
 *   POST   /api/v1/regulatory/authority-documents/:id/transition  status walk
 *   DELETE /api/v1/regulatory/authority-documents/:id      soft delete
 *
 *   GET    /api/v1/regulatory/authority-documents/:id/citations    list citations
 *   POST   /api/v1/regulatory/authority-documents/:id/citations    create citation
 *   PUT    /api/v1/regulatory/citations/:id                        update citation
 *   DELETE /api/v1/regulatory/citations/:id                        delete citation
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/regulatory")
@RequiredArgsConstructor
@Tag(name = "Regulatory — Authority Documents (F1)")
public class AuthorityDocumentController {

    private final AuthorityDocumentService service;

    // ─── Documents ────────────────────────────────────────────────

    @GetMapping("/authority-documents")
    @PreAuthorize("isAuthenticated()")
    public List<AuthorityDocument> list(@RequestParam(required = false) String family,
                                         @RequestParam(required = false) String status) {
        return service.list(family, status);
    }

    @GetMapping("/authority-documents/rollup")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> rollup() { return service.rollupByFamily(); }

    @GetMapping("/authority-documents/{id}")
    @PreAuthorize("isAuthenticated()")
    public AuthorityDocument get(@PathVariable Long id) { return service.get(id); }

    @PostMapping("/authority-documents")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AuthorityDocument> create(@RequestBody AuthorityDocument body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PutMapping("/authority-documents/{id}")
    @PreAuthorize("isAuthenticated()")
    public AuthorityDocument update(@PathVariable Long id, @RequestBody AuthorityDocument body) {
        return service.update(id, body);
    }

    public record TransitionBody(String to, Long supersededById) {}

    @PostMapping("/authority-documents/{id}/transition")
    @PreAuthorize("isAuthenticated()")
    public AuthorityDocument transition(@PathVariable Long id, @RequestBody TransitionBody body) {
        return service.transition(id, body.to(), body.supersededById());
    }

    @DeleteMapping("/authority-documents/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Citations ────────────────────────────────────────────────

    @GetMapping("/authority-documents/{id}/citations")
    @PreAuthorize("isAuthenticated()")
    public List<AuthorityDocumentCitation> listCitations(@PathVariable Long id) {
        return service.listCitations(id);
    }

    @PostMapping("/authority-documents/{id}/citations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AuthorityDocumentCitation> createCitation(@PathVariable Long id,
                                                                     @RequestBody AuthorityDocumentCitation body) {
        return ResponseEntity.ok(service.createCitation(id, body));
    }

    @PutMapping("/citations/{id}")
    @PreAuthorize("isAuthenticated()")
    public AuthorityDocumentCitation updateCitation(@PathVariable Long id,
                                                     @RequestBody AuthorityDocumentCitation body) {
        return service.updateCitation(id, body);
    }

    @DeleteMapping("/citations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteCitation(@PathVariable Long id) {
        service.deleteCitation(id);
        return ResponseEntity.noContent().build();
    }
}

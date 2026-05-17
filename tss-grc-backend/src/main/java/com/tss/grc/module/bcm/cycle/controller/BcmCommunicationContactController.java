package com.tss.grc.module.bcm.cycle.controller;

import com.tss.grc.module.bcm.cycle.entity.BcmCommunicationContact;
import com.tss.grc.module.bcm.cycle.service.BcmCommunicationContactService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Communication contacts + call-tree-test endpoints — Sprint BCM-2.
 *
 * <pre>
 *   GET    /api/v1/bcm/contacts[?planId=&status=]
 *   GET    /api/v1/bcm/contacts/:id
 *   POST   /api/v1/bcm/contacts
 *   PUT    /api/v1/bcm/contacts/:id
 *   POST   /api/v1/bcm/contacts/:id/test-result   { "result": "PASSED|FAILED|NOT_REACHED", "notes": "…" }
 *   GET    /api/v1/bcm/contacts/test-stats
 *   DELETE /api/v1/bcm/contacts/:id
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/bcm")
@RequiredArgsConstructor
@Tag(name = "BCM — Communication Contacts (BCM-2)")
public class BcmCommunicationContactController {

    private final BcmCommunicationContactService service;

    @GetMapping("/contacts")
    @PreAuthorize("isAuthenticated()")
    public List<BcmCommunicationContact> list(@RequestParam(required = false) Long planId,
                                              @RequestParam(required = false) String status) {
        return service.list(planId, status);
    }

    @GetMapping("/contacts/{id}")
    @PreAuthorize("isAuthenticated()")
    public BcmCommunicationContact get(@PathVariable Long id) { return service.get(id); }

    @PostMapping("/contacts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BcmCommunicationContact> create(@RequestBody BcmCommunicationContact body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PutMapping("/contacts/{id}")
    @PreAuthorize("isAuthenticated()")
    public BcmCommunicationContact update(@PathVariable Long id, @RequestBody BcmCommunicationContact body) {
        return service.update(id, body);
    }

    public record TestResultBody(String result, String notes) {}

    @PostMapping("/contacts/{id}/test-result")
    @PreAuthorize("isAuthenticated()")
    public BcmCommunicationContact recordTestResult(@PathVariable Long id, @RequestBody TestResultBody body) {
        return service.recordTestResult(id, body.result(), body.notes());
    }

    @GetMapping("/contacts/test-stats")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> stats() { return service.testResultStats(); }

    @DeleteMapping("/contacts/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

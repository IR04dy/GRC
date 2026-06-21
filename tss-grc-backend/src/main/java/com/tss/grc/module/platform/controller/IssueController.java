package com.tss.grc.module.platform.controller;

import com.tss.grc.module.platform.entity.Issue;
import com.tss.grc.module.platform.service.IssueService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
@Tag(name = "Issues")
public class IssueController {

    /**
     * Body shape for {@link IssueController#transition} — keeps the transition
     * endpoint POSTable from any client without forcing a full Issue body when
     * all the caller wants to change is the status and resolution.
     */
    public record TransitionBody(String to, String resolution) {}

    private final IssueService service;

    @GetMapping
    @PreAuthorize("hasPermission('PLATFORM','READ')")
    public Page<Issue> list(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "50") int size) {
        return service.list(page, Math.min(size, 200));
    }

    @GetMapping("/by-source/{module}")
    @PreAuthorize("hasPermission('PLATFORM','READ')")
    public List<Issue> bySource(@PathVariable("module") String module) {
        return service.bySource(module);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('PLATFORM','READ')")
    public Issue get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("hasPermission('PLATFORM','WRITE')")
    public ResponseEntity<Issue> create(@Valid @RequestBody Issue body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission('PLATFORM','WRITE')")
    public Issue update(@PathVariable Long id, @RequestBody Issue body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('PLATFORM','WRITE')")
    public Issue transition(@PathVariable Long id, @RequestBody TransitionBody body) {
        return service.transition(id, body.to(), body.resolution());
    }

    @GetMapping("/stats")
    @PreAuthorize("hasPermission('PLATFORM','READ')")
    public Map<String, Long> stats() { return service.stats(); }
}

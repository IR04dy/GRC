package com.tss.grc.module.platform.controller;

import com.tss.grc.module.platform.entity.Department;
import com.tss.grc.module.platform.service.DepartmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST surface for the platform-level department directory.
 * <pre>
 *   GET    /api/v1/departments           list all
 *   GET    /api/v1/departments/{id}      one
 *   POST   /api/v1/departments           create
 *   PATCH  /api/v1/departments/{id}      partial update
 *   POST   /api/v1/departments/{id}/archive
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Tag(name = "Departments")
public class DepartmentController {

    private final DepartmentService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Department> list() { return service.list(); }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Department get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Department> create(@Valid @RequestBody Department body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Department update(@PathVariable Long id, @RequestBody Department body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("isAuthenticated()")
    public Department archive(@PathVariable Long id) { return service.archive(id); }
}

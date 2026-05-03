package com.tss.grc.module.bcm.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.entity.CriticalProcess;
import com.tss.grc.module.bcm.entity.ProcessDependency;
import com.tss.grc.module.bcm.repository.CriticalProcessRepository;
import com.tss.grc.module.bcm.repository.ProcessDependencyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * BIA dependency graph (M3) — returns nodes (critical processes) + edges
 * (process_dependencies) for the frontend SVG visualisation. Plus full CRUD
 * over the dependency edges so the graph stays in sync with reality.
 */
@RestController
@RequestMapping("/api/v1/bcm")
@RequiredArgsConstructor
@Tag(name = "BCM — BIA Dependency Graph")
@SecurityRequirement(name = "bearerAuth")
public class DependencyGraphController {

    private final CriticalProcessRepository processRepo;
    private final ProcessDependencyRepository depRepo;

    @GetMapping("/dependency-graph")
    @PreAuthorize("hasPermission('BCM','READ')")
    @Operation(summary = "Nodes (processes) + edges (dependencies) for the BIA dependency graph")
    public Map<String, Object> graph() {
        Long org = TenantContext.getCurrentTenant();
        List<CriticalProcess> processes = processRepo.findByOrganisationId(org);
        List<ProcessDependency> deps = depRepo.findByOrganisationId(org);

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (CriticalProcess p : processes) {
            Map<String, Object> n = new HashMap<>();
            n.put("id",            p.getId());
            n.put("nameEn",        p.getProcessNameEn());
            n.put("nameAr",        p.getProcessNameAr());
            n.put("criticality",   p.getCriticality());
            nodes.add(n);
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        for (ProcessDependency d : deps) {
            Map<String, Object> e = new HashMap<>();
            e.put("id",                d.getId());
            e.put("from",              d.getDependsOnProcessId());   // upstream → downstream direction
            e.put("to",                d.getProcessId());
            e.put("dependencyType",    d.getDependencyType());
            e.put("criticalityImpact", d.getCriticalityImpact());
            e.put("notes",             d.getNotes());
            edges.add(e);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("nodes", nodes);
        out.put("edges", edges);
        return out;
    }

    @GetMapping("/dependencies")
    @PreAuthorize("hasPermission('BCM','READ')")
    public List<ProcessDependency> list() {
        return depRepo.findByOrganisationId(TenantContext.getCurrentTenant());
    }

    @PostMapping("/dependencies")
    @PreAuthorize("hasPermission('BCM','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProcessDependency create(@RequestBody ProcessDependency body) {
        if (body.getProcessId() == null || body.getDependsOnProcessId() == null)
            throw new IllegalArgumentException("processId and dependsOnProcessId are required");
        if (body.getProcessId().equals(body.getDependsOnProcessId()))
            throw new IllegalArgumentException("A process cannot depend on itself");
        body.setOrganisationId(TenantContext.getCurrentTenant());
        if (body.getDependencyType() == null) body.setDependencyType("INTERNAL_PROCESS");
        return depRepo.save(body);
    }

    @PutMapping("/dependencies/{id}")
    @PreAuthorize("hasPermission('BCM','WRITE')")
    public ProcessDependency update(@PathVariable Long id, @RequestBody ProcessDependency body) {
        ProcessDependency existing = require(id);
        if (body.getDependencyType()    != null) existing.setDependencyType(body.getDependencyType());
        if (body.getCriticalityImpact() != null) existing.setCriticalityImpact(body.getCriticalityImpact());
        if (body.getNotes()             != null) existing.setNotes(body.getNotes());
        return depRepo.save(existing);
    }

    @DeleteMapping("/dependencies/{id}")
    @PreAuthorize("hasPermission('BCM','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ProcessDependency existing = require(id);
        existing.setIsDeleted(true);
        depRepo.save(existing);
    }

    private ProcessDependency require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return depRepo.findById(id)
            .filter(d -> d.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Dependency not found: " + id));
    }
}

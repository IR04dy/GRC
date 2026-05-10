package com.tss.grc.module.workflow.controller;

import com.tss.grc.module.workflow.entity.WorkflowDefinition;
import com.tss.grc.module.workflow.entity.WorkflowInstance;
import com.tss.grc.module.workflow.service.WorkflowEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Configurable workflow engine — assessment §6.1.
 * Read-only browsing for non-admin roles; instance lifecycle for any module
 * that's been migrated onto the engine.
 */
@RestController
@RequestMapping("/api/v1/workflow")
@RequiredArgsConstructor
@Tag(name = "Workflow Engine")
@SecurityRequirement(name = "bearerAuth")
public class WorkflowEngineController {

    private final WorkflowEngineService engine;

    @GetMapping("/definitions")
    @PreAuthorize("hasPermission('PLATFORM_ADMIN','READ') or hasPermission('RISK','READ') or hasPermission('BCM','READ') or hasPermission('SCM','READ') or hasPermission('COMPLIANCE','READ') or hasPermission('PRIVACY','READ') or hasPermission('INCIDENT','READ') or hasPermission('EVIDENCE','READ') or hasPermission('CYBER','READ')")
    @Operation(summary = "List all active workflow definitions for this organisation")
    public List<WorkflowDefinition> listDefinitions(@RequestParam(required = false) String targetModule) {
        return targetModule != null ? engine.listDefinitionsForModule(targetModule) : engine.listDefinitions();
    }

    @GetMapping("/definitions/{id}")
    @PreAuthorize("hasPermission('PLATFORM_ADMIN','READ') or hasPermission('RISK','READ') or hasPermission('BCM','READ') or hasPermission('SCM','READ') or hasPermission('COMPLIANCE','READ') or hasPermission('PRIVACY','READ') or hasPermission('INCIDENT','READ') or hasPermission('EVIDENCE','READ') or hasPermission('CYBER','READ')")
    @Operation(summary = "Get a workflow definition with its states and transitions for state-machine rendering")
    public Map<String, Object> getDefinition(@PathVariable Long id) {
        return engine.getDefinitionWithGraph(id);
    }

    @PostMapping("/instances")
    @PreAuthorize("hasPermission('PLATFORM_ADMIN','WRITE')")
    @Operation(summary = "Start a workflow instance for a target entity")
    public WorkflowInstance startInstance(@RequestBody Map<String, Object> body) {
        Long definitionId   = ((Number) body.get("definitionId")).longValue();
        String targetModule = (String) body.get("targetModule");
        String targetEntity = (String) body.get("targetEntity");
        Long targetEntityId = ((Number) body.get("targetEntityId")).longValue();
        Long ownerId        = body.get("ownerId") instanceof Number n ? n.longValue() : null;
        return engine.startInstance(definitionId, targetModule, targetEntity, targetEntityId, ownerId);
    }

    @GetMapping("/instances/{id}")
    @PreAuthorize("hasPermission('PLATFORM_ADMIN','READ')")
    public Map<String, Object> getInstance(@PathVariable Long id) {
        return engine.getInstance(id);
    }

    @PostMapping("/instances/{id}/transitions")
    @PreAuthorize("hasPermission('PLATFORM_ADMIN','WRITE')")
    @Operation(summary = "Execute a transition by action code on a running instance")
    public WorkflowInstance executeTransition(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String actionCode = (String) body.get("actionCode");
        Long executedById = body.get("executedById") instanceof Number n ? n.longValue() : null;
        String comments   = (String) body.get("comments");
        return engine.executeTransition(id, actionCode, executedById, comments);
    }

    @GetMapping("/instances/by-target")
    @PreAuthorize("hasPermission('PLATFORM_ADMIN','READ')")
    @Operation(summary = "Find the workflow instance for a specific target entity")
    public WorkflowInstance findInstance(@RequestParam String targetModule,
                                          @RequestParam String targetEntity,
                                          @RequestParam Long targetEntityId) {
        return engine.findInstanceForTarget(targetModule, targetEntity, targetEntityId);
    }
}

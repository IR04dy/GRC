package com.tss.grc.module.workflow.requests.controller;

import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.CreateRecipientList;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RecipientListResponse;
import com.tss.grc.module.workflow.requests.service.WorkflowRecipientListService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * WF-8 — CRUD for reusable recipient lists (distribution lists). Gated on the WORKFLOW module
 * permission like the rest of the engine; a saved list is expanded into a request's recipients
 * by {@code WorkflowRequestService.create(recipientListId=...)}.
 */
@RestController
@RequestMapping("/api/v1/workflows/recipient-lists")
@RequiredArgsConstructor
@Tag(name = "Workflow Engine — Recipient Lists")
public class WorkflowRecipientListController {

    private final WorkflowRecipientListService svc;

    @GetMapping
    @PreAuthorize("hasPermission('WORKFLOW','READ')")
    public List<RecipientListResponse> list() { return svc.list(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('WORKFLOW','READ')")
    public RecipientListResponse get(@PathVariable Long id) { return svc.getDetail(id); }

    @PostMapping
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipientListResponse create(@Valid @RequestBody CreateRecipientList body) { return svc.create(body); }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public RecipientListResponse update(@PathVariable Long id, @Valid @RequestBody CreateRecipientList body) {
        return svc.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { svc.delete(id); }
}

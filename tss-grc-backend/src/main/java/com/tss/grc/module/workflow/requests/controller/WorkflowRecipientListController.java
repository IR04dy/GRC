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

    /**
     * Soft-delete a saved distribution list.
     *
     * <p><b>Guard was {@code WORKFLOW:WRITE}.</b> `WORKFLOW:DELETE` was catalogued, held by
     * exactly one role (ORG_ADMIN), and named by no {@code @PreAuthorize} anywhere — one of
     * section C's "granted but guarding no endpoint" rows in {@code Appendix/GRANT_TIGHTNESS.md},
     * a checkbox that did nothing. This endpoint is what it was for.
     *
     * <p>Why the old guard was too wide: `WORKFLOW:WRITE` is held by **55 of 57 roles**, and
     * F2-13b deliberately left it that wide because after the approve/write split it means
     * *participate* — a SUPPLIER_USER answering a vendor attestation needs it. That population
     * includes VIEWER, REGULATOR_VIEWER, EXTERNAL_AUDITOR, SUPPLIER_USER and all eight
     * {@code *_INGEST} machine accounts. Participating in a workflow is not the same act as
     * deleting the shared list that 14 modules route requests through. **V325** makes the
     * permission satisfiable first (1 → 12 holders, derived: already holds some module's DELETE
     * *and* already holds `WORKFLOW:WRITE`), because lifting the guard alone would have left
     * deletion to ORG_ADMIN and broken every other administrator — the F2-13b lesson that
     * enforcing a permission is also a re-grant decision.
     *
     * <p>The delete is soft ({@code is_deleted = 1}) and audited, which bounds the damage but
     * does not make 55 the right number.
     *
     * <p>⚠ The sibling deletes in this module are <b>deliberately not</b> moved here:
     * {@code ApprovalChainController#deleteTemplate} (2 roles) and
     * {@code ApprovalRequestController#delete} (PLATFORM_ADMIN only) are already *tighter* than
     * 12, so repointing them at this permission would widen them. Their {@code hasAnyRole} form
     * is F2-05's conversion scope.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('WORKFLOW','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { svc.delete(id); }
}

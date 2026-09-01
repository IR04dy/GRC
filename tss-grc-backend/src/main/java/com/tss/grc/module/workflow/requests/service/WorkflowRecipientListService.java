package com.tss.grc.module.workflow.requests.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.CreateRecipientList;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RecipientDto;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RecipientListMemberDto;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RecipientListResponse;
import com.tss.grc.module.workflow.requests.entity.WorkflowRecipientList;
import com.tss.grc.module.workflow.requests.entity.WorkflowRecipientListMember;
import com.tss.grc.module.workflow.requests.repository.WorkflowRecipientListMemberRepository;
import com.tss.grc.module.workflow.requests.repository.WorkflowRecipientListRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;

/**
 * WF-8 — reusable recipient lists ("distribution lists", §2.2 / §5). An org composes a named
 * recipient set once; {@link WorkflowRequestService#create} can then expand it into a request's
 * recipients via {@code recipientListId} — so a requester "sends to a distribution list" instead
 * of rebuilding the same recipients each time. Org-scoped; audit-logged.
 */
@Service
@RequiredArgsConstructor
public class WorkflowRecipientListService {

    private final WorkflowRecipientListRepository listRepo;
    private final WorkflowRecipientListMemberRepository memberRepo;
    private final AuditLogService audit;

    @Transactional
    public RecipientListResponse create(CreateRecipientList dto) {
        Long org = TenantContext.getCurrentTenant();
        if (listRepo.existsByOrganisationIdAndName(org, dto.name().trim()))
            throw new IllegalArgumentException("A recipient list named '" + dto.name().trim() + "' already exists");

        WorkflowRecipientList list = new WorkflowRecipientList();
        list.setOrganisationId(org);
        list.setName(dto.name().trim());
        list.setDescription(dto.description());
        list.setIsActive((short) 1);
        list = listRepo.save(list);

        saveMembers(org, list.getId(), dto.members());
        audit.log("WORKFLOW_RECIPIENT_LIST_CREATED", "WorkflowRecipientList", list.getId(),
            list.getName() + " (" + (dto.members() == null ? 0 : dto.members().size()) + " member(s))");
        return toResponse(list);
    }

    @Transactional
    public RecipientListResponse update(Long id, CreateRecipientList dto) {
        Long org = TenantContext.getCurrentTenant();
        WorkflowRecipientList list = get(id, org);
        list.setName(dto.name().trim());
        list.setDescription(dto.description());
        listRepo.save(list);
        // Replace the member set (soft-delete old, insert new).
        memberRepo.findByOrganisationIdAndListIdOrderByStepNoAscIdAsc(org, id)
            .forEach(m -> { m.setIsDeleted(true); memberRepo.save(m); });
        saveMembers(org, id, dto.members());
        audit.log("WORKFLOW_RECIPIENT_LIST_UPDATED", "WorkflowRecipientList", id, list.getName());
        return toResponse(list);
    }

    @Transactional
    public void delete(Long id) {
        Long org = TenantContext.getCurrentTenant();
        WorkflowRecipientList list = get(id, org);
        list.setIsDeleted(true);
        listRepo.save(list);
        audit.log("WORKFLOW_RECIPIENT_LIST_DELETED", "WorkflowRecipientList", id, list.getName());
    }

    @Transactional(readOnly = true)
    public List<RecipientListResponse> list() {
        Long org = TenantContext.getCurrentTenant();
        return listRepo.findByOrganisationIdOrderByNameAsc(org).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RecipientListResponse getDetail(Long id) {
        return toResponse(get(id, TenantContext.getCurrentTenant()));
    }

    /** WF-8 — expand a saved list into request recipient inputs (used by WorkflowRequestService). */
    @Transactional(readOnly = true)
    public List<RecipientDto> expand(Long listId, Long org) {
        get(listId, org);   // validates existence + tenant
        return memberRepo.findByOrganisationIdAndListIdOrderByStepNoAscIdAsc(org, listId).stream()
            .map(m -> new RecipientDto(m.getRecipientType(), m.getRecipientId(), m.getRecipientKey(),
                m.getRecipientLabel(), m.getActorRole(), m.getStepNo()))
            .toList();
    }

    // ── helpers ────────────────────────────────────────────────────────────
    private void saveMembers(Long org, Long listId, List<RecipientListMemberDto> members) {
        if (members == null) return;
        for (RecipientListMemberDto in : members) {
            WorkflowRecipientListMember m = new WorkflowRecipientListMember();
            m.setOrganisationId(org);
            m.setListId(listId);
            m.setRecipientType(in.recipientType().trim().toUpperCase(Locale.ROOT));
            m.setRecipientId(in.recipientId());
            m.setRecipientKey(in.recipientKey());
            m.setRecipientLabel(in.recipientLabel());
            m.setActorRole(in.actorRole() == null || in.actorRole().isBlank()
                ? "ASSIGNEE" : in.actorRole().trim().toUpperCase(Locale.ROOT));
            m.setStepNo(in.stepNo() == null ? 1 : in.stepNo());
            memberRepo.save(m);
        }
    }

    private WorkflowRecipientList get(Long id, Long org) {
        return listRepo.findByIdAndOrganisationId(id, org)
            .orElseThrow(() -> new EntityNotFoundException("WorkflowRecipientList " + id));
    }

    private RecipientListResponse toResponse(WorkflowRecipientList list) {
        List<RecipientListMemberDto> members = memberRepo
            .findByOrganisationIdAndListIdOrderByStepNoAscIdAsc(list.getOrganisationId(), list.getId())
            .stream().map(m -> new RecipientListMemberDto(m.getId(), m.getRecipientType(),
                m.getRecipientId(), m.getRecipientKey(), m.getRecipientLabel(), m.getActorRole(), m.getStepNo()))
            .toList();
        return new RecipientListResponse(list.getId(), list.getName(), list.getDescription(),
            list.getIsActive() != null && list.getIsActive() == 1, members);
    }
}

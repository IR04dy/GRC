package com.tss.grc.module.scm.clauses.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.scm.clauses.dto.ContractClauseDtos.*;
import com.tss.grc.module.scm.clauses.entity.ContractClause;
import com.tss.grc.module.scm.clauses.repository.ContractClauseRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractClauseService {

    private final ContractClauseRepository repo;

    @Transactional(readOnly = true)
    public Page<ContractClause> list(String status, String category, Pageable pageable) {
        Long org = TenantContext.getCurrentTenant();
        if (category != null) {
            return repo.findByOrganisationIdAndCategoryOrderByClauseCodeAsc(org, category, pageable);
        }
        return status == null
                ? repo.findByOrganisationIdOrderByCategoryAscClauseCodeAsc(org, pageable)
                : repo.findByOrganisationIdAndStatusOrderByCategoryAscClauseCodeAsc(org, status, pageable);
    }

    @Transactional(readOnly = true)
    public ContractClause get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("ContractClause " + id));
    }

    @Transactional
    public ContractClause create(CreateRequest req) {
        ContractClause c = new ContractClause();
        c.setOrganisationId(TenantContext.getCurrentTenant());
        c.setClauseCode(req.clauseCode());
        c.setExternalId("CLS_" + UUID.randomUUID().toString().substring(0, 12));
        c.setTitleEn(req.titleEn());
        c.setTitleAr(req.titleAr());
        c.setCategory(req.category());
        c.setApplicableTo(req.applicableTo());
        c.setBodyEn(req.bodyEn());
        c.setBodyAr(req.bodyAr());
        c.setGuidanceEn(req.guidanceEn());
        c.setGuidanceAr(req.guidanceAr());
        c.setRegulatoryRefs(req.regulatoryRefs());
        if (req.riskLevel() != null) c.setRiskLevel(req.riskLevel());
        if (req.negotiability() != null) c.setNegotiability(req.negotiability());
        if (req.version() != null) c.setVersion(req.version());
        c.setOwner(req.owner());
        c.setTags(req.tags());
        c.setStatus("DRAFT");
        return repo.save(c);
    }

    @Transactional
    public ContractClause update(Long id, UpdateRequest req) {
        ContractClause c = get(id);
        if ("DEPRECATED".equals(c.getStatus())) {
            throw new IllegalTransitionException(c.getStatus(), "EDIT", Set.of("DRAFT", "APPROVED"));
        }
        if (req.titleEn() != null) c.setTitleEn(req.titleEn());
        if (req.titleAr() != null) c.setTitleAr(req.titleAr());
        if (req.category() != null) c.setCategory(req.category());
        if (req.applicableTo() != null) c.setApplicableTo(req.applicableTo());
        if (req.bodyEn() != null) c.setBodyEn(req.bodyEn());
        if (req.bodyAr() != null) c.setBodyAr(req.bodyAr());
        if (req.guidanceEn() != null) c.setGuidanceEn(req.guidanceEn());
        if (req.guidanceAr() != null) c.setGuidanceAr(req.guidanceAr());
        if (req.regulatoryRefs() != null) c.setRegulatoryRefs(req.regulatoryRefs());
        if (req.riskLevel() != null) c.setRiskLevel(req.riskLevel());
        if (req.negotiability() != null) c.setNegotiability(req.negotiability());
        if (req.version() != null) c.setVersion(req.version());
        if (req.owner() != null) c.setOwner(req.owner());
        if (req.tags() != null) c.setTags(req.tags());
        return c;
    }

    @Transactional
    public ContractClause approve(Long id, String approverName, Long approverId) {
        ContractClause c = get(id);
        if (!"DRAFT".equals(c.getStatus())) {
            throw new IllegalTransitionException(c.getStatus(), "APPROVED", Set.of("DRAFT"));
        }
        c.setStatus("APPROVED");
        c.setApprovedAt(LocalDateTime.now());
        c.setApprovedBy(approverId);
        c.setApproverName(approverName);
        return c;
    }

    @Transactional
    public ContractClause deprecate(Long id, DeprecateRequest req) {
        ContractClause c = get(id);
        if ("DEPRECATED".equals(c.getStatus())) {
            throw new IllegalTransitionException(c.getStatus(), "DEPRECATED", Set.of("DRAFT", "APPROVED"));
        }
        c.setStatus("DEPRECATED");
        c.setDeprecatedAt(LocalDateTime.now());
        c.setDeprecationReason(req.reason());
        return c;
    }
}

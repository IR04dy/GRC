package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.risk.dto.RiskRequest;
import com.tss.grc.module.risk.dto.RiskResponse;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskCategory;
import com.tss.grc.module.risk.repository.RiskCategoryRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class RiskService {

    private final RiskRepository riskRepository;
    private final RiskCategoryRepository riskCategoryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<RiskResponse> list(String status, Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        Page<Risk> page = (status != null && !status.isBlank())
            ? riskRepository.findByOrgAndStatus(orgId, status, pageable)
            : riskRepository.findByOrganisationId(orgId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RiskResponse get(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        Risk risk = riskRepository.findById(id)
            .filter(r -> r.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Risk not found: " + id));
        return toResponse(risk);
    }

    @Transactional
    public RiskResponse create(RiskRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        Risk risk = new Risk();
        risk.setOrganisationId(orgId);
        applyRequest(risk, req);
        risk.setIdentifiedDate(LocalDate.now());
        risk = riskRepository.save(risk);
        return toResponse(risk);
    }

    @Transactional
    public RiskResponse update(Long id, RiskRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        Risk risk = riskRepository.findById(id)
            .filter(r -> r.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Risk not found: " + id));
        applyRequest(risk, req);
        risk = riskRepository.save(risk);
        return toResponse(risk);
    }

    @Transactional
    public void delete(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        Risk risk = riskRepository.findById(id)
            .filter(r -> r.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Risk not found: " + id));
        risk.setIsDeleted(true);
        riskRepository.save(risk);
    }

    private void applyRequest(Risk risk, RiskRequest req) {
        risk.setTitleEn(req.titleEn());
        risk.setTitleAr(req.titleAr());
        risk.setDescriptionEn(req.descriptionEn());
        risk.setCategoryId(req.categoryId());
        risk.setOwnerId(req.ownerId());
        risk.setInherentLikelihood(req.inherentLikelihood());
        risk.setInherentImpact(req.inherentImpact());
        risk.setResidualLikelihood(req.residualLikelihood());
        risk.setResidualImpact(req.residualImpact());
        if (req.status() != null) risk.setStatus(req.status());
        risk.setReviewDate(req.reviewDate());
    }

    private RiskResponse toResponse(Risk r) {
        String categoryNameEn = null;
        String categoryNameAr = null;
        if (r.getCategoryId() != null) {
            RiskCategory cat = riskCategoryRepository.findById(r.getCategoryId()).orElse(null);
            if (cat != null) {
                categoryNameEn = cat.getNameEn();
                categoryNameAr = cat.getNameAr();
            }
        }

        String ownerFullNameEn = null;
        String ownerUsername = null;
        if (r.getOwnerId() != null) {
            User owner = userRepository.findById(r.getOwnerId()).orElse(null);
            if (owner != null) {
                ownerFullNameEn = owner.getFullNameEn();
                ownerUsername = owner.getUsername();
            }
        }

        String riskCode = "RISK-" + String.format("%04d", r.getId());

        return new RiskResponse(
            r.getId(), r.getExternalId(),
            riskCode,
            r.getTitleEn(), r.getTitleAr(), r.getDescriptionEn(),
            r.getCategoryId(), categoryNameEn,
            categoryNameEn, categoryNameAr,
            r.getOwnerId(), ownerFullNameEn,
            ownerFullNameEn, ownerUsername,
            r.getStatus(),
            r.getInherentLikelihood(), r.getInherentImpact(), r.getInherentScore(),
            r.getResidualLikelihood(), r.getResidualImpact(), r.getResidualScore(),
            r.getRiskLevel(),
            r.getIdentifiedDate(), r.getReviewDate(),
            r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}

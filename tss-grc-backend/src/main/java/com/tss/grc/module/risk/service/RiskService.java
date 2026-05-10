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
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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

    /** Optional — null in unit tests, autowired by Spring at runtime. */
    @Autowired(required = false)
    private BroadcastHelper bcast;
    private void emit(Long orgId, Long id, Object payload) {
        if (bcast != null) bcast.entity(orgId, "risk", id, payload);
    }

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
        RiskResponse out = toResponse(risk);
        emit(orgId, risk.getId(), out);
        return out;
    }

    @Transactional
    public RiskResponse update(Long id, RiskRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        Risk risk = riskRepository.findById(id)
            .filter(r -> r.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Risk not found: " + id));
        applyRequest(risk, req);
        risk = riskRepository.save(risk);
        RiskResponse out = toResponse(risk);
        emit(orgId, id, out);
        return out;
    }

    @Transactional
    public void delete(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        Risk risk = riskRepository.findById(id)
            .filter(r -> r.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Risk not found: " + id));
        risk.setIsDeleted(true);
        riskRepository.save(risk);
        emit(orgId, id, java.util.Map.of("id", id, "deleted", true));
    }

    // ── Risk lifecycle workflow (ISO 31000 / COSO ERM) ────────────────────────
    // State machine:
    //   OPEN → UNDER_REVIEW → MITIGATING → CLOSED
    //   OPEN | UNDER_REVIEW → ACCEPTED (formal risk acceptance)
    //   CLOSED → OPEN (reopen on recurrence)

    @Transactional
    public RiskResponse submitForReview(Long id) {
        Risk r = mustGet(id);
        require(r, "OPEN");
        r.setStatus("UNDER_REVIEW");
        RiskResponse out = toResponse(riskRepository.save(r));
        emit(r.getOrganisationId(), id, out);
        return out;
    }

    @Transactional
    public RiskResponse approve(Long id) {
        Risk r = mustGet(id);
        require(r, "UNDER_REVIEW");
        r.setStatus("MITIGATING");
        RiskResponse out = toResponse(riskRepository.save(r));
        emit(r.getOrganisationId(), id, out);
        return out;
    }

    @Transactional
    public RiskResponse accept(Long id) {
        Risk r = mustGet(id);
        require(r, "OPEN", "UNDER_REVIEW");
        r.setStatus("ACCEPTED");
        RiskResponse out = toResponse(riskRepository.save(r));
        emit(r.getOrganisationId(), id, out);
        return out;
    }

    @Transactional
    public RiskResponse close(Long id) {
        Risk r = mustGet(id);
        require(r, "MITIGATING", "ACCEPTED");
        r.setStatus("CLOSED");
        RiskResponse out = toResponse(riskRepository.save(r));
        emit(r.getOrganisationId(), id, out);
        return out;
    }

    @Transactional
    public RiskResponse reopen(Long id) {
        Risk r = mustGet(id);
        require(r, "CLOSED");
        r.setStatus("OPEN");
        RiskResponse out = toResponse(riskRepository.save(r));
        emit(r.getOrganisationId(), id, out);
        return out;
    }

    /**
     * Risk-incident retrospective: when an incident is logged for this risk,
     * bump the residual likelihood by 1 (capped at 5) so the heatmap reflects
     * the realised event. Returns the updated risk.
     */
    @Transactional
    public RiskResponse bumpLikelihoodFromIncident(Long id) {
        Risk r = mustGet(id);
        Integer current = r.getResidualLikelihood();
        int next = Math.min(5, (current == null ? 1 : current) + 1);
        r.setResidualLikelihood(next);
        return toResponse(riskRepository.save(r));
    }

    private Risk mustGet(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        return riskRepository.findById(id)
            .filter(r -> r.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Risk not found: " + id));
    }

    private void require(Risk r, String... allowed) {
        for (String s : allowed) if (s.equals(r.getStatus())) return;
        throw new IllegalArgumentException(
            "Risk must be in " + java.util.Arrays.toString(allowed) + " (was " + r.getStatus() + ")");
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

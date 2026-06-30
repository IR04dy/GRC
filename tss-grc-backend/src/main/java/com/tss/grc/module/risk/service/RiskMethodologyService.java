package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.risk.dto.MethodologyDtos.ConfigUpdateDto;
import com.tss.grc.module.risk.dto.MethodologyDtos.ImpactDimensionDto;
import com.tss.grc.module.risk.dto.MethodologyDtos.ImpactRatingsResponse;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskImpactRating;
import com.tss.grc.module.risk.entity.RiskMethodologyConfig;
import com.tss.grc.module.risk.repository.RiskImpactRatingRepository;
import com.tss.grc.module.risk.repository.RiskMethodologyConfigRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configurable methodology + multi-dimensional impact — ERM remediation epic E-F.
 *
 * <p>Owns the per-org {@link RiskMethodologyConfig} (active impact dimensions +
 * scale maxima, seeded with defaults) and the per-risk impact ratings. Impact
 * ratings are validated against the config (declared dimension + score ≤
 * {@code impactScaleMax}); the risk's inherent impact is then derived as the
 * worst (max) dimension — the standard "worst-case dimension drives the rating".
 */
@Service
@RequiredArgsConstructor
public class RiskMethodologyService {

    private final RiskMethodologyConfigRepository configRepo;
    private final RiskImpactRatingRepository impactRepo;
    private final RiskRepository risks;
    private final AuditLogService audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Methodology config ───────────────────────────────────────────

    @Transactional
    public RiskMethodologyConfig getOrSeed() {
        Long o = org();
        return configRepo.findByOrganisationId(o).orElseGet(() -> {
            RiskMethodologyConfig c = new RiskMethodologyConfig();
            c.setOrganisationId(o);   // defaults set on the entity
            return configRepo.save(c);
        });
    }

    @Transactional
    public RiskMethodologyConfig updateConfig(ConfigUpdateDto dto) {
        RiskMethodologyConfig c = getOrSeed();
        if (dto.impactDimensions() != null && !dto.impactDimensions().isEmpty()) {
            String csv = dto.impactDimensions().stream()
                .map(s -> s == null ? "" : s.trim().toUpperCase())
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
            if (!csv.isEmpty()) c.setImpactDimensionsCsv(csv);
        }
        if (dto.likelihoodScaleMax() != null && dto.likelihoodScaleMax() >= 2) c.setLikelihoodScaleMax(dto.likelihoodScaleMax());
        if (dto.impactScaleMax()     != null && dto.impactScaleMax()     >= 2) c.setImpactScaleMax(dto.impactScaleMax());
        c.setUpdatedAt(LocalDateTime.now());
        RiskMethodologyConfig saved = configRepo.save(c);
        audit.log("UPDATE", "RiskMethodologyConfig", saved.getId(),
            "Methodology updated: dims=" + saved.getImpactDimensionsCsv()
            + " Lmax=" + saved.getLikelihoodScaleMax() + " Imax=" + saved.getImpactScaleMax());
        return saved;
    }

    /** Declared impact dimensions for the current org (upper-cased, de-duped). */
    public Set<String> dimensions(RiskMethodologyConfig c) {
        return Arrays.stream(c.getImpactDimensionsCsv().split(","))
            .map(s -> s.trim().toUpperCase()).filter(s -> !s.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ── Multi-dimensional impact ─────────────────────────────────────

    @Transactional(readOnly = true)
    public ImpactRatingsResponse getImpactRatings(Long riskId) {
        Long o = org();
        RiskMethodologyConfig cfg = configRepo.findByOrganisationId(o).orElseGet(RiskMethodologyConfig::new);
        List<RiskImpactRating> rows = impactRepo.findByOrganisationIdAndRiskIdOrderByDimensionAsc(o, riskId);
        List<ImpactDimensionDto> dims = rows.stream()
            .map(r -> new ImpactDimensionDto(r.getDimension(), r.getScore())).toList();
        Integer overall = rows.stream().map(RiskImpactRating::getScore)
            .filter(Objects::nonNull).max(Integer::compareTo).orElse(null);
        return new ImpactRatingsResponse(dims, overall, cfg.getImpactScaleMax());
    }

    /**
     * Replace the risk's per-dimension impact scores and derive its inherent
     * impact as the worst (max) dimension. Validates every rating against the
     * org methodology (declared dimension + 1..impactScaleMax).
     */
    @Transactional
    public ImpactRatingsResponse setImpactRatings(Long riskId, List<ImpactDimensionDto> ratings) {
        Long o = org();
        Risk risk = risks.findById(riskId)
            .filter(r -> Objects.equals(r.getOrganisationId(), o))
            .orElseThrow(() -> new EntityNotFoundException("Risk " + riskId + " not found for tenant " + o));
        RiskMethodologyConfig cfg = getOrSeed();
        Set<String> allowed = dimensions(cfg);
        int max = cfg.getImpactScaleMax();

        // validate
        for (ImpactDimensionDto d : ratings) {
            String dim = d.dimension() == null ? "" : d.dimension().trim().toUpperCase();
            if (!allowed.contains(dim))
                throw new IllegalArgumentException("Unknown impact dimension '" + d.dimension()
                    + "' — expected one of " + allowed);
            if (d.score() == null || d.score() < 1 || d.score() > max)
                throw new IllegalArgumentException("Impact score for '" + dim + "' must be 1.." + max);
        }

        // replace-all: soft-delete existing, insert the provided set
        List<RiskImpactRating> existing = impactRepo.findByOrganisationIdAndRiskIdOrderByDimensionAsc(o, riskId);
        for (RiskImpactRating e : existing) { e.setIsDeleted(true); e.setUpdatedAt(LocalDateTime.now()); }
        impactRepo.saveAll(existing);

        Integer worst = null;
        for (ImpactDimensionDto d : ratings) {
            RiskImpactRating r = new RiskImpactRating();
            r.setOrganisationId(o);
            r.setRiskId(riskId);
            r.setDimension(d.dimension().trim().toUpperCase());
            r.setScore(d.score());
            impactRepo.save(r);
            worst = (worst == null) ? d.score() : Math.max(worst, d.score());
        }

        // worst dimension drives the risk's inherent impact → DB recomputes inherent_score + risk_level
        if (worst != null) {
            risk.setInherentImpact(worst);
            risks.save(risk);
        }
        audit.log("UPDATE", "Risk", riskId,
            "Impact dimensions set (" + ratings.size() + ") — worst dimension " + worst + " → inherent impact");
        return getImpactRatings(riskId);
    }
}

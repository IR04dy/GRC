package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.risk.dto.RiskLinkDtos.CreateDto;
import com.tss.grc.module.risk.entity.RiskLink;
import com.tss.grc.module.risk.repository.RiskLinkRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Typed register linkage — ERM remediation epic E-G.
 *
 * <p>CRUD for a risk's typed cross-references to other register objects.
 * Tenant-scoped throughout; the link type is validated against a fixed
 * vocabulary so the register stays queryable.
 */
@Service
@RequiredArgsConstructor
public class RiskLinkService {

    /** Accepted typed-link vocabulary (objective/process/service/asset/supplier/project + common cross-refs). */
    static final Set<String> TYPES = Set.of(
        "OBJECTIVE", "PROCESS", "SERVICE", "ASSET", "SUPPLIER",
        "PROJECT", "INCIDENT", "CONTROL", "OBLIGATION", "OTHER");

    private final RiskLinkRepository links;
    private final RiskRepository risks;
    private final AuditLogService audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    @Transactional(readOnly = true)
    public List<RiskLink> listForRisk(Long riskId) {
        return links.findByOrganisationIdAndRiskIdOrderByIdAsc(org(), riskId);
    }

    @Transactional
    public RiskLink create(Long riskId, CreateDto dto) {
        Long o = org();
        // Verify the risk belongs to the caller's tenant before bridging.
        risks.findById(riskId)
            .filter(r -> Objects.equals(r.getOrganisationId(), o))
            .orElseThrow(() -> new EntityNotFoundException("Risk " + riskId + " not found for tenant " + o));

        String type = dto.linkType() == null ? "" : dto.linkType().trim().toUpperCase();
        if (!TYPES.contains(type))
            throw new IllegalArgumentException("Unknown link type '" + dto.linkType()
                + "' — expected one of " + TYPES);

        RiskLink l = new RiskLink();
        l.setOrganisationId(o);
        l.setRiskId(riskId);
        l.setLinkType(type);
        l.setTargetRef(dto.targetRef());
        l.setTargetId(dto.targetId());
        l.setDescriptionEn(dto.descriptionEn());
        RiskLink saved = links.save(l);
        audit.log("LINK", "RiskLink", saved.getId(),
            "Risk #" + riskId + " ↔ " + type + " '" + saved.getTargetRef() + "'");
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        RiskLink l = links.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Risk link not found: " + id));
        l.setIsDeleted(true);
        l.setUpdatedAt(LocalDateTime.now());
        links.save(l);
        audit.log("UNLINK", "RiskLink", id,
            "Removed " + l.getLinkType() + " link '" + l.getTargetRef() + "' from risk #" + l.getRiskId());
    }
}

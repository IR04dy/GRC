package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
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
 * Typed register linkage — ERM remediation epic E-G (committee linkage added in E-I).
 *
 * <p>CRUD for a risk's typed cross-references to other register objects.
 * Tenant-scoped throughout; the link type is validated against a fixed
 * vocabulary so the register stays queryable. {@code COMMITTEE} and
 * {@code POLICY} links are additionally validated against their registers
 * (E-I — "committee linkage, reuse admin.committee" + risk-policy linkage):
 * when a {@code targetId} is supplied it must resolve to a live committee /
 * policy document in the caller's tenant, and the human {@code targetRef} is
 * auto-filled from that object's name/title.
 */
@Service
@RequiredArgsConstructor
public class RiskLinkService {

    /** Accepted typed-link vocabulary (register objects + governance committee + governing policy). */
    static final Set<String> TYPES = Set.of(
        "OBJECTIVE", "PROCESS", "SERVICE", "ASSET", "SUPPLIER",
        "PROJECT", "INCIDENT", "CONTROL", "OBLIGATION", "COMMITTEE", "POLICY", "OTHER");

    private final RiskLinkRepository links;
    private final RiskRepository risks;
    private final AuditLogService audit;
    /** E-I — governance committee register, for validating COMMITTEE links. */
    private final CommitteeRepository committees;
    /** E-I — policy register, for validating POLICY (governing-policy) links. */
    private final PolicyDocumentRepository policies;

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

        // E-I — a COMMITTEE / POLICY link with a target id must resolve to a live
        // object in the respective register; auto-fill the human reference from it.
        String targetRef = dto.targetRef();
        if (dto.targetId() != null) {
            if ("COMMITTEE".equals(type)) {
                Committee c = committees.findByIdAndOrganisationId(dto.targetId(), o)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Committee " + dto.targetId() + " not found in the governance register"));
                if (targetRef == null || targetRef.isBlank()) targetRef = c.getNameEn();
            } else if ("POLICY".equals(type)) {
                PolicyDocument p = policies.findByIdAndOrganisationId(dto.targetId(), o)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Policy " + dto.targetId() + " not found in the policy register"));
                if (targetRef == null || targetRef.isBlank()) targetRef = p.getTitleEn();
            }
        }
        // Every link needs a human reference — supplied, or auto-filled above from
        // the committee/policy register. (targetRef is optional on the wire so the
        // register auto-fill can satisfy it; the invariant is enforced here.)
        if (targetRef == null || targetRef.isBlank())
            throw new IllegalArgumentException(
                "targetRef is required (or supply a valid committee/policy targetId to auto-fill it)");

        RiskLink l = new RiskLink();
        l.setOrganisationId(o);
        l.setRiskId(riskId);
        l.setLinkType(type);
        l.setTargetRef(targetRef);
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

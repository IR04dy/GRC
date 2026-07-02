package com.tss.grc.module.sust.ghg.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.ghg.entity.EmissionFactor;
import com.tss.grc.module.sust.ghg.repository.EmissionFactorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Versioned emission-factor library service — S-J.
 *
 * <p>Creating a factor with a {@code factorCode} that already has an active
 * version <em>supersedes</em> it: the prior row is deactivated + stamped
 * {@code validTo=today}, and the new row is {@code version = prior + 1}. GHG
 * records resolve the current active version via {@link #resolveActive(String)}.
 */
@Service
@RequiredArgsConstructor
public class EmissionFactorService {

    private final EmissionFactorRepository repo;
    private final AuditLogService audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Reads ─────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<EmissionFactor> list(boolean activeOnly) {
        Long o = org();
        return activeOnly
            ? repo.findByOrganisationIdAndIsActiveTrueOrderByFactorCodeAsc(o)
            : repo.findByOrganisationIdOrderByFactorCodeAscVersionDesc(o);
    }

    @Transactional(readOnly = true)
    public EmissionFactor get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Emission factor " + id));
    }

    /** The active factor for a code, or a 400 if none — used by the GHG calculator. */
    @Transactional(readOnly = true)
    public EmissionFactor resolveActive(String factorCode) {
        return repo.findFirstByOrganisationIdAndFactorCodeAndIsActiveTrue(org(), factorCode)
            .orElseThrow(() -> new IllegalArgumentException(
                "No active emission factor for code '" + factorCode + "' in this tenant."));
    }

    // ── Writes ────────────────────────────────────────────────────────
    @Transactional
    public EmissionFactor create(EmissionFactor req) {
        Long o = org();
        req.setId(null);
        req.setOrganisationId(o);
        req.setIsDeleted(false);
        validate(req);

        // Supersede the current active version of this code, if any.
        int nextVersion = 1;
        var prior = repo.findFirstByOrganisationIdAndFactorCodeOrderByVersionDesc(o, req.getFactorCode());
        if (prior.isPresent()) {
            nextVersion = prior.get().getVersion() + 1;
            repo.findFirstByOrganisationIdAndFactorCodeAndIsActiveTrue(o, req.getFactorCode())
                .ifPresent(active -> {
                    active.setIsActive(false);
                    if (active.getValidTo() == null) active.setValidTo(LocalDate.now());
                    repo.save(active);
                });
        }
        req.setVersion(nextVersion);
        req.setIsActive(true);
        if (req.getValidFrom() == null) req.setValidFrom(LocalDate.now());
        req.setValidTo(null);

        EmissionFactor saved = repo.save(req);
        audit.log("CREATE", "EmissionFactor", saved.getId(),
            "Emission factor " + saved.getFactorCode() + " v" + saved.getVersion()
                + " = " + saved.getFactorValue() + " " + saved.getFactorUnit()
                + (nextVersion > 1 ? " (superseded v" + (nextVersion - 1) + ")" : ""));
        return saved;
    }

    /** Deactivate a factor (no active version remains for that code until a new one is added). */
    @Transactional
    public EmissionFactor deactivate(Long id) {
        EmissionFactor f = get(id);
        f.setIsActive(false);
        if (f.getValidTo() == null) f.setValidTo(LocalDate.now());
        EmissionFactor saved = repo.save(f);
        audit.log("UPDATE", "EmissionFactor", saved.getId(),
            "Emission factor " + saved.getFactorCode() + " v" + saved.getVersion() + " deactivated");
        return saved;
    }

    private void validate(EmissionFactor f) {
        if (f.getFactorCode() == null || f.getFactorCode().isBlank())
            throw new IllegalArgumentException("factorCode required");
        if (f.getScope() == null || f.getScope() < 1 || f.getScope() > 3)
            throw new IllegalArgumentException("scope must be 1, 2 or 3");
        if (f.getTitleEn() == null || f.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn required");
        if (f.getActivityUnit() == null || f.getActivityUnit().isBlank())
            throw new IllegalArgumentException("activityUnit required");
        if (f.getFactorUnit() == null || f.getFactorUnit().isBlank())
            throw new IllegalArgumentException("factorUnit required");
        if (f.getFactorValue() == null || f.getFactorValue().compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("factorValue must be >= 0");
    }
}

package com.tss.grc.module.privacy.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.privacy.entity.ConsentRecord;
import com.tss.grc.module.privacy.repository.ConsentRecordRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Consent record service — PDPL Art. 5 ledger management.
 *
 * <p>Append-mostly: you create, withdraw, or supersede. Edits to a granted
 * consent's legal terms are not supported — issue a new {@link ConsentRecord}
 * with bumped {@code version} and mark the previous one {@code SUPERSEDED}
 * via {@link #supersede}.
 *
 * <p>Withdrawal is one-way: once {@code withdrawnAt} is set, the record is
 * frozen. To re-collect consent, create a new row.
 */
@Service
@RequiredArgsConstructor
public class ConsentRecordService {

    private static final Set<String> LEGAL_BASES = Set.of(
        "CONSENT", "LEGITIMATE_INTEREST", "CONTRACT",
        "LEGAL_OBLIGATION", "VITAL_INTEREST", "PUBLIC_TASK");
    private static final Set<String> GRANTED_VIA = Set.of(
        "WEB_FORM", "PAPER", "IN_PERSON", "API", "EMAIL", "OTHER");
    private static final Set<String> STATUSES = Set.of(
        "ACTIVE", "EXPIRED", "WITHDRAWN", "SUPERSEDED");

    private final ConsentRecordRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public Page<ConsentRecord> list(int page, int size) {
        return repo.findByOrganisationIdOrderByIdDesc(
            TenantContext.getCurrentTenant(), PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public ConsentRecord get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("ConsentRecord not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ConsentRecord> historyFor(String dataSubjectRef) {
        return repo.findByOrganisationIdAndDataSubjectRefOrderByIdDesc(
            TenantContext.getCurrentTenant(), dataSubjectRef);
    }

    @Transactional
    public ConsentRecord grant(ConsentRecord body) {
        if (body.getDataSubjectRef() == null || body.getDataSubjectRef().isBlank())
            throw new IllegalArgumentException("dataSubjectRef is required");
        if (body.getPurposeCode() == null || body.getPurposeCode().isBlank())
            throw new IllegalArgumentException("purposeCode is required");
        if (body.getLegalBasis() != null && !LEGAL_BASES.contains(body.getLegalBasis()))
            throw new IllegalArgumentException("legalBasis must be one of " + LEGAL_BASES);
        if (body.getGrantedVia() != null && !GRANTED_VIA.contains(body.getGrantedVia()))
            throw new IllegalArgumentException("grantedVia must be one of " + GRANTED_VIA);

        body.setId(null);
        body.setOrganisationId(TenantContext.getCurrentTenant());
        body.setStatus("ACTIVE");
        if (body.getGrantedAt() == null) body.setGrantedAt(LocalDateTime.now());
        ConsentRecord saved = repo.save(body);
        audit.log("CREATE", "ConsentRecord", saved.getId(),
            "Consent granted: subject=" + saved.getDataSubjectRef()
                + " purpose=" + saved.getPurposeCode()
                + " basis=" + saved.getLegalBasis());
        return saved;
    }

    @Transactional
    public ConsentRecord withdraw(Long id, String reason) {
        ConsentRecord c = get(id);
        if (!"ACTIVE".equals(c.getStatus()))
            throw new IllegalArgumentException("Only ACTIVE consents can be withdrawn (current=" + c.getStatus() + ")");
        c.setStatus("WITHDRAWN");
        c.setWithdrawnAt(LocalDateTime.now());
        c.setWithdrawnReason(reason);
        ConsentRecord saved = repo.save(c);
        audit.log("CUSTOM", "ConsentRecord", saved.getId(),
            "Consent withdrawn: subject=" + saved.getDataSubjectRef()
                + " purpose=" + saved.getPurposeCode()
                + (reason == null ? "" : " reason=" + reason));
        return saved;
    }

    /**
     * Mark an existing consent as SUPERSEDED by a newly granted one. The
     * caller passes the ID of the new record; both rows live on, the older
     * one inactive. Used when the consent text is reissued (new terms,
     * new language, or new version bump).
     */
    @Transactional
    public ConsentRecord supersede(Long id, Long supersededByConsentId) {
        ConsentRecord c = get(id);
        if (!"ACTIVE".equals(c.getStatus()))
            throw new IllegalArgumentException("Only ACTIVE consents can be superseded");
        // Validate the successor exists in this tenant (otherwise we'd be linking to nothing).
        get(supersededByConsentId);
        c.setStatus("SUPERSEDED");
        ConsentRecord saved = repo.save(c);
        audit.log("CUSTOM", "ConsentRecord", saved.getId(),
            "Consent superseded by ConsentRecord#" + supersededByConsentId);
        return saved;
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Long> stats() {
        Long org = TenantContext.getCurrentTenant();
        java.util.Map<String, Long> out = new java.util.HashMap<>();
        for (String s : STATUSES) out.put(s.toLowerCase(), repo.countByOrganisationIdAndStatus(org, s));
        return out;
    }
}

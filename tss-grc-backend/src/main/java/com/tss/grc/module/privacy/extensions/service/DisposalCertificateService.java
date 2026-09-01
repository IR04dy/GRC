package com.tss.grc.module.privacy.extensions.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.DisposalCompleteRequest;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.DisposalCreate;
import com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos.DisposalVerifyRequest;
import com.tss.grc.module.privacy.extensions.entity.DisposalCertificate;
import com.tss.grc.module.privacy.extensions.repository.DisposalCertificateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Disposal certificate lifecycle.
 *
 * <pre>
 *   SCHEDULED  ──start()────▶ IN_PROGRESS
 *   IN_PROGRESS──complete()─▶ COMPLETED
 *   COMPLETED ──verify()───▶ VERIFIED   (terminal)
 *   (any non-terminal) ──cancel()─▶ CANCELLED (off-path terminal)
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class DisposalCertificateService {

    private final DisposalCertificateRepository repo;

    @Transactional(readOnly = true)
    public Page<DisposalCertificate> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByCreatedAtDesc(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public DisposalCertificate get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("DisposalCertificate " + id));
    }

    @Transactional
    public DisposalCertificate create(DisposalCreate body, Long createdBy) {
        DisposalCertificate d = new DisposalCertificate();
        d.setOrganisationId(TenantContext.getCurrentTenant());
        d.setExternalId("DC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        d.setRelatedEntityType(body.relatedEntityType());
        d.setRelatedEntityId(body.relatedEntityId());
        d.setRelatedEntityLabel(body.relatedEntityLabel());
        d.setRetentionRuleRef(body.retentionRuleRef());
        d.setScopeDescriptionEn(body.scopeDescriptionEn());
        d.setScopeDescriptionAr(body.scopeDescriptionAr());
        d.setRecordsCountEst(body.recordsCountEst());
        d.setDisposalMethod(body.disposalMethod());
        d.setMethodDetailsEn(body.methodDetailsEn());
        // F2-272 - V388's column and this line are one commit: a column no write path fills
        // is an inert column (F2-198's shape).
        d.setMethodDetailsAr(body.methodDetailsAr());
        d.setScheduledDate(body.scheduledDate() != null ? body.scheduledDate() : LocalDateTime.now());
        d.setOwnerRole(body.ownerRole());
        d.setOwnerName(body.ownerName());
        d.setCreatedBy(createdBy);
        d.setStatus("SCHEDULED");
        return repo.save(d);
    }

    @Transactional
    public DisposalCertificate start(Long id, Long executorId) {
        DisposalCertificate d = get(id);
        if (!"SCHEDULED".equals(d.getStatus())) {
            throw new IllegalTransitionException(d.getStatus(), "IN_PROGRESS", Set.of("SCHEDULED"));
        }
        d.setStatus("IN_PROGRESS");
        d.setExecutedBy(executorId);
        return repo.save(d);
    }

    @Transactional
    public DisposalCertificate complete(Long id, DisposalCompleteRequest body, Long executorId) {
        DisposalCertificate d = get(id);
        if (!"IN_PROGRESS".equals(d.getStatus())) {
            throw new IllegalTransitionException(d.getStatus(), "COMPLETED", Set.of("IN_PROGRESS"));
        }
        d.setStatus("COMPLETED");
        d.setExecutedAt(LocalDateTime.now());
        if (d.getExecutedBy() == null) d.setExecutedBy(executorId);
        // F2-223 — the executor's NAME is the same person as executedBy, which the controller
        // already resolved from the authenticated principal and passed in. Taking the id from
        // the principal and the name from the request body split one attribution across two
        // owners; the server's own clock on the line above is the proof that it considered
        // itself the recorder of this act.
        d.setExecutorName(CurrentActor.username());
        d.setEvidenceRef(body.evidenceRef());
        return repo.save(d);
    }

    @Transactional
    public DisposalCertificate verify(Long id, DisposalVerifyRequest body, Long verifierId) {
        DisposalCertificate d = get(id);
        if (!"COMPLETED".equals(d.getStatus())) {
            throw new IllegalTransitionException(d.getStatus(), "VERIFIED", Set.of("COMPLETED"));
        }
        d.setStatus("VERIFIED");
        d.setVerifiedAt(LocalDateTime.now());
        d.setVerifiedBy(verifierId);
        // F2-223 — see complete(): verifiedBy and verifierName are the same person, and the
        // controller resolves that person from the Authentication it already binds. A disposal
        // certificate is the PDPL Article 18 evidence that personal data was destroyed; the
        // party attesting to that may not be named by whoever calls the endpoint.
        d.setVerifierName(CurrentActor.username());
        d.setSignatureTxId(body.signatureTxId());
        return repo.save(d);
    }

    @Transactional
    public DisposalCertificate cancel(Long id) {
        DisposalCertificate d = get(id);
        if ("VERIFIED".equals(d.getStatus()) || "CANCELLED".equals(d.getStatus())) {
            throw new IllegalTransitionException(d.getStatus(), "CANCELLED",
                    Set.of("SCHEDULED", "IN_PROGRESS", "COMPLETED"));
        }
        d.setStatus("CANCELLED");
        return repo.save(d);
    }
}

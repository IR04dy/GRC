package com.tss.grc.module.signature.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.signature.dto.DigitalSignatureDtos.CreateRequest;
import com.tss.grc.module.signature.dto.DigitalSignatureDtos.RejectRequest;
import com.tss.grc.module.signature.dto.DigitalSignatureDtos.SignRequest;
import com.tss.grc.module.signature.entity.DigitalSignatureTransaction;
import com.tss.grc.module.signature.repository.DigitalSignatureTransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Digital signature lifecycle service.
 *
 * <p>State machine:
 * <pre>
 *   REQUESTED ──sign()──▶ SIGNED
 *             ──reject()─▶ REJECTED
 *             ──expire()─▶ EXPIRED   (scheduled or on read)
 * </pre>
 *
 * <p>SIGNED, REJECTED, and EXPIRED are terminal. The service refuses any
 * transition out of a terminal state with {@link IllegalTransitionException}.
 *
 * <p>Optional realtime hook (when {@code BroadcastHelper} is on the
 * classpath) emits {@code changed.digital_signature_transaction} so an
 * inbox UI can refresh without polling.
 */
@Service
@RequiredArgsConstructor
public class DigitalSignatureService {

    private final DigitalSignatureTransactionRepository repo;

    /** Pluggable broadcaster — null in unit tests / minimal profiles. */
    @Autowired(required = false)
    private com.tss.grc.platform.realtime.BroadcastHelper broadcaster;

    // ─── Reads ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DigitalSignatureTransaction> list(Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdOrderByCreatedAtDesc(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public DigitalSignatureTransaction get(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new EntityNotFoundException("DigitalSignatureTransaction " + id));
    }

    @Transactional(readOnly = true)
    public List<DigitalSignatureTransaction> inboxForUser(Long signerUserId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndSignerUserIdAndStatusOrderByCreatedAtDesc(
                orgId, signerUserId, "REQUESTED");
    }

    @Transactional(readOnly = true)
    public List<DigitalSignatureTransaction> historyForEntity(String entityType, Long entityId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc(
                orgId, entityType, entityId);
    }

    // ─── Writes ───────────────────────────────────────────────────────

    @Transactional
    public DigitalSignatureTransaction request(CreateRequest body, Long initiatorUserId, String initiatorName) {
        Long orgId = TenantContext.getCurrentTenant();
        DigitalSignatureTransaction d = new DigitalSignatureTransaction();
        d.setOrganisationId(orgId);
        d.setExternalId("DSIG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        d.setRelatedEntityType(body.relatedEntityType());
        d.setRelatedEntityId(body.relatedEntityId());
        d.setRelatedEntityLabel(body.relatedEntityLabel());
        d.setSignerUserId(body.signerUserId());
        d.setSignerName(body.signerName());
        d.setSignerEmail(body.signerEmail());
        d.setSignerRole(body.signerRole());
        d.setInitiatorUserId(initiatorUserId);
        d.setInitiatorName(initiatorName);
        d.setSignatureProvider(body.signatureProvider() == null ? "INTERNAL" : body.signatureProvider());
        d.setPurpose(body.purpose());
        d.setPurposeAr(body.purposeAr());
        d.setExpiryDate(body.expiryDate());
        d.setStatus("REQUESTED");

        DigitalSignatureTransaction saved = repo.save(d);
        broadcast("created", saved);
        return saved;
    }

    @Transactional
    public DigitalSignatureTransaction sign(Long id, SignRequest body) {
        DigitalSignatureTransaction d = get(id);
        requireTransitionable(d, "SIGNED");
        d.setStatus("SIGNED");
        d.setSignedDocumentHash(body.signedDocumentHash());
        d.setCertificateReference(body.certificateReference());
        d.setSignatureTimestamp(LocalDateTime.now());
        DigitalSignatureTransaction saved = repo.save(d);
        broadcast("signed", saved);
        return saved;
    }

    @Transactional
    public DigitalSignatureTransaction reject(Long id, RejectRequest body) {
        DigitalSignatureTransaction d = get(id);
        requireTransitionable(d, "REJECTED");
        d.setStatus("REJECTED");
        d.setRejectionReason(body.rejectionReason());
        DigitalSignatureTransaction saved = repo.save(d);
        broadcast("rejected", saved);
        return saved;
    }

    /**
     * Mark as EXPIRED. Called by a scheduled sweep or lazily when a stale
     * request is loaded past its expiry. Idempotent for already-terminal
     * rows.
     */
    @Transactional
    public DigitalSignatureTransaction expire(Long id) {
        DigitalSignatureTransaction d = get(id);
        if (!"REQUESTED".equals(d.getStatus())) return d;
        d.setStatus("EXPIRED");
        DigitalSignatureTransaction saved = repo.save(d);
        broadcast("expired", saved);
        return saved;
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private static final java.util.Set<String> TERMINAL_FROM_REQUESTED =
            java.util.Set.of("SIGNED", "REJECTED", "EXPIRED");

    private void requireTransitionable(DigitalSignatureTransaction d, String target) {
        if (!"REQUESTED".equals(d.getStatus())) {
            throw new IllegalTransitionException(d.getStatus(), target, TERMINAL_FROM_REQUESTED);
        }
    }

    private void broadcast(String op, DigitalSignatureTransaction d) {
        if (broadcaster == null) return;
        broadcaster.entity(
                d.getOrganisationId(),
                "digital_signature_transaction",
                d.getId(),
                java.util.Map.of("op", op, "status", d.getStatus()));
    }
}

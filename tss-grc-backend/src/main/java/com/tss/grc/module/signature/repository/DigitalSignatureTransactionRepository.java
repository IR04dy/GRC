package com.tss.grc.module.signature.repository;

import com.tss.grc.module.signature.entity.DigitalSignatureTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DigitalSignatureTransactionRepository
        extends JpaRepository<DigitalSignatureTransaction, Long> {

    Page<DigitalSignatureTransaction> findByOrganisationIdOrderByCreatedAtDesc(
            Long organisationId, Pageable pageable);

    Optional<DigitalSignatureTransaction> findByIdAndOrganisationId(Long id, Long organisationId);

    /** Inbox query — what's awaiting this signer. */
    List<DigitalSignatureTransaction> findByOrganisationIdAndSignerUserIdAndStatusOrderByCreatedAtDesc(
            Long organisationId, Long signerUserId, String status);

    /** All signatures attached to a particular entity instance. */
    List<DigitalSignatureTransaction> findByOrganisationIdAndRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc(
            Long organisationId, String relatedEntityType, Long relatedEntityId);
}

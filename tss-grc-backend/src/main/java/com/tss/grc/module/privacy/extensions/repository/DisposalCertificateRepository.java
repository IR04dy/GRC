package com.tss.grc.module.privacy.extensions.repository;

import com.tss.grc.module.privacy.extensions.entity.DisposalCertificate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisposalCertificateRepository extends JpaRepository<DisposalCertificate, Long> {

    Page<DisposalCertificate> findByOrganisationIdOrderByCreatedAtDesc(Long orgId, Pageable pageable);

    Optional<DisposalCertificate> findByIdAndOrganisationId(Long id, Long orgId);

    List<DisposalCertificate> findByOrganisationIdAndStatusOrderByCreatedAtDesc(Long orgId, String status);

    /** Disposal certs attached to a ROPA (related_entity_type = 'ropa'). */
    List<DisposalCertificate> findByOrganisationIdAndRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc(
            Long orgId, String entityType, String entityId);

    /** Counter for ROPA-attached disposal certificates. */
    int countByOrganisationIdAndRelatedEntityTypeAndRelatedEntityId(Long orgId, String entityType, String entityId);
}

package com.tss.grc.module.data_officer.repository;

import com.tss.grc.module.data_officer.entity.Entities.EthicsReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("doEthicsReviewRepository")
public interface EthicsRepository extends JpaRepository<EthicsReview, Long> {
    Optional<EthicsReview> findByOrganisationIdAndReference(Long organisationId, String reference);
    Optional<EthicsReview> findByOrganisationIdAndId(Long organisationId, Long id);
    List<EthicsReview> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
}

package com.tss.grc.module.sharia.repository;

import com.tss.grc.module.sharia.entity.ShariaProductApproval;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShariaProductApprovalRepository extends JpaRepository<ShariaProductApproval, Long> {

    Page<ShariaProductApproval> findByOrganisationIdOrderBySubmittedAtDesc(
            Long organisationId, Pageable pageable);

    Optional<ShariaProductApproval> findByIdAndOrganisationId(Long id, Long organisationId);

    List<ShariaProductApproval> findByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndStatusIn(
            Long organisationId, java.util.Collection<String> statuses);
}

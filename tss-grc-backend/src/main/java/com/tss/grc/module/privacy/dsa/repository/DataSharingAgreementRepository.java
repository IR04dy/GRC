package com.tss.grc.module.privacy.dsa.repository;

import com.tss.grc.module.privacy.dsa.entity.DataSharingAgreement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DataSharingAgreementRepository extends JpaRepository<DataSharingAgreement, Long> {
    Page<DataSharingAgreement> findByOrganisationIdOrderByEffectiveFromDescIdDesc(Long org, Pageable p);
    Page<DataSharingAgreement> findByOrganisationIdAndStatusOrderByEffectiveFromDescIdDesc(Long org, String status, Pageable p);
    Optional<DataSharingAgreement> findByIdAndOrganisationId(Long id, Long org);
}

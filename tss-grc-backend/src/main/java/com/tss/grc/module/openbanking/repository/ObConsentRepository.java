package com.tss.grc.module.openbanking.repository;

import com.tss.grc.module.openbanking.entity.ObConsent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ObConsentRepository extends JpaRepository<ObConsent, Long> {

    Page<ObConsent> findByOrganisationIdOrderByGrantedAtDesc(
            Long organisationId, Pageable pageable);

    Optional<ObConsent> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<ObConsent> findByConsentReferenceAndOrganisationId(
            String consentReference, Long organisationId);

    List<ObConsent> findByTppIdAndOrganisationIdOrderByGrantedAtDesc(
            Long tppId, Long organisationId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndTppIdAndStatus(
            Long organisationId, Long tppId, String status);
}

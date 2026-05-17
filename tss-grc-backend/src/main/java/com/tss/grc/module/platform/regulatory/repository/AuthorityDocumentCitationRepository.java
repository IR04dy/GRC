package com.tss.grc.module.platform.regulatory.repository;

import com.tss.grc.module.platform.regulatory.entity.AuthorityDocumentCitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthorityDocumentCitationRepository extends JpaRepository<AuthorityDocumentCitation, Long> {

    List<AuthorityDocumentCitation> findByOrganisationIdAndAuthorityDocumentIdOrderBySequenceOrderAsc(
        Long organisationId, Long authorityDocumentId);

    Optional<AuthorityDocumentCitation> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<AuthorityDocumentCitation> findByAuthorityDocumentIdAndCitationCode(
        Long authorityDocumentId, String citationCode);

    long countByOrganisationIdAndAuthorityDocumentId(Long organisationId, Long authorityDocumentId);
}

package com.tss.grc.module.platform.regulatory.repository;

import com.tss.grc.module.platform.regulatory.entity.AuthorityDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthorityDocumentRepository extends JpaRepository<AuthorityDocument, Long> {

    List<AuthorityDocument> findByOrganisationIdOrderByFamilyAscCodeAsc(Long organisationId);

    List<AuthorityDocument> findByOrganisationIdAndFamilyOrderByCodeAsc(Long organisationId, String family);

    List<AuthorityDocument> findByOrganisationIdAndStatusOrderByCodeAsc(Long organisationId, String status);

    Optional<AuthorityDocument> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<AuthorityDocument> findByOrganisationIdAndCodeAndVersion(Long organisationId, String code, String version);
}

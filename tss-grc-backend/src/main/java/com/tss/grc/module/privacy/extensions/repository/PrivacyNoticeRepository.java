package com.tss.grc.module.privacy.extensions.repository;

import com.tss.grc.module.privacy.extensions.entity.PrivacyNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrivacyNoticeRepository extends JpaRepository<PrivacyNotice, Long> {

    Page<PrivacyNotice> findByOrganisationIdOrderByCreatedAtDesc(Long orgId, Pageable pageable);

    Optional<PrivacyNotice> findByIdAndOrganisationId(Long id, Long orgId);

    /** Latest published notice for a code — drives the public-facing template. */
    Optional<PrivacyNotice> findFirstByOrganisationIdAndNoticeCodeAndStatusOrderByVersionDesc(
            Long orgId, String noticeCode, String status);

    List<PrivacyNotice> findByOrganisationIdAndNoticeCodeOrderByVersionDesc(Long orgId, String noticeCode);

    List<PrivacyNotice> findByOrganisationIdAndStatusOrderByPublishedAtDesc(Long orgId, String status);

    /**
     * Notices that link to a given ROPA code (via the linked_ropa_codes
     * CSV column). LIKE-based contains match — '%,ROPA-001,%' works too
     * because the column is wrapped on read; here we just match a
     * substring with comma delimiters factored out.
     */
    @org.springframework.data.jpa.repository.Query("""
        select n from PrivacyNotice n
         where n.organisationId = :orgId
           and ( n.linkedRopaCodes = :code
              or n.linkedRopaCodes like CONCAT(:code, ',%') ESCAPE '\\'
              or n.linkedRopaCodes like CONCAT('%,', :code) ESCAPE '\\'
              or n.linkedRopaCodes like CONCAT('%,', :code, ',%') ESCAPE '\\' )
         order by n.version desc
        """)
    List<PrivacyNotice> findByOrgAndLinkedRopa(
            @org.springframework.data.repository.query.Param("orgId") Long orgId,
            @org.springframework.data.repository.query.Param("code") String code);

    /** Counter variant for the chip on ROPA detail. */
    @org.springframework.data.jpa.repository.Query("""
        select count(n) from PrivacyNotice n
         where n.organisationId = :orgId
           and ( n.linkedRopaCodes = :code
              or n.linkedRopaCodes like CONCAT(:code, ',%') ESCAPE '\\'
              or n.linkedRopaCodes like CONCAT('%,', :code) ESCAPE '\\'
              or n.linkedRopaCodes like CONCAT('%,', :code, ',%') ESCAPE '\\' )
        """)
    int countByOrgAndLinkedRopa(
            @org.springframework.data.repository.query.Param("orgId") Long orgId,
            @org.springframework.data.repository.query.Param("code") String code);
}

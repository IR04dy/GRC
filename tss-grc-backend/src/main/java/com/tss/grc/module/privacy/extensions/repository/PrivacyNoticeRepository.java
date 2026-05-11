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
}

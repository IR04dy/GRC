package com.tss.grc.module.data_officer.repository;

import com.tss.grc.module.data_officer.entity.Entities.PrivacyNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("doPrivacyNoticeRepository")
public interface PrivacyNoticeRepository extends JpaRepository<PrivacyNotice, Long> {
    Optional<PrivacyNotice> findByOrganisationIdAndReference(Long organisationId, String reference);
    Optional<PrivacyNotice> findByOrganisationIdAndId(Long organisationId, Long id);
    List<PrivacyNotice> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);
    List<PrivacyNotice> findByOrganisationIdAndChannelOrderByPublishedAtDesc(Long organisationId, String channel);
}

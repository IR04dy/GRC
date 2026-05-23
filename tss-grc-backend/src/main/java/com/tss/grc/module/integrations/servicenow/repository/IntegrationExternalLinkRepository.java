package com.tss.grc.module.integrations.servicenow.repository;

import com.tss.grc.module.integrations.servicenow.entity.IntegrationExternalLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IntegrationExternalLinkRepository extends JpaRepository<IntegrationExternalLink, Long> {

    Optional<IntegrationExternalLink> findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
        Long organisationId, String integration, String grcModule, String grcRecordId);

    Optional<IntegrationExternalLink> findFirstByIntegrationAndExternalNumber(
        String integration, String externalNumber);
}

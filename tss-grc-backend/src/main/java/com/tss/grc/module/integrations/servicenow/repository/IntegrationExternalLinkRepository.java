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

    /**
     * Tenant-scoped resolution of an external link by natural key. The inbound
     * webhook MUST use this so it can never act on, mutate, or attribute events
     * to a row owned by another organisation (see SEC-INT-002).
     */
    Optional<IntegrationExternalLink> findFirstByOrganisationIdAndIntegrationAndExternalNumber(
        Long organisationId, String integration, String externalNumber);
}

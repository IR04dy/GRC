package com.tss.grc.module.bcm.comms.repository;

import com.tss.grc.module.bcm.comms.entity.CrisisCommsTemplateV2;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for {@link CrisisCommsTemplateV2} (Slice 9-E).
 *
 * <p>Every finder takes {@code organisationId} explicitly per the
 * platform's "no global Hibernate tenant filter" pattern (CLAUDE.md).
 * Soft-deleted rows are hidden by the entity's {@code @Where} clause.
 */
public interface CrisisCommsTemplateV2Repository extends JpaRepository<CrisisCommsTemplateV2, Long> {

    List<CrisisCommsTemplateV2> findByOrganisationIdAndIsDeletedFalseOrderByAudienceAscTemplateCodeAsc(Long organisationId);

    List<CrisisCommsTemplateV2> findByOrganisationIdAndAudienceAndIsDeletedFalseOrderByTemplateCodeAsc(
        Long organisationId, String audience);

    List<CrisisCommsTemplateV2> findByOrganisationIdAndAudienceAndScenarioCategoryAndIsDeletedFalseOrderByTemplateCodeAsc(
        Long organisationId, String audience, String scenarioCategory);

    List<CrisisCommsTemplateV2> findByOrganisationIdAndApprovalStatusAndIsDeletedFalseOrderByAudienceAscTemplateCodeAsc(
        Long organisationId, String approvalStatus);

    Optional<CrisisCommsTemplateV2> findByOrganisationIdAndExternalIdAndIsDeletedFalse(
        Long organisationId, UUID externalId);

    Optional<CrisisCommsTemplateV2> findByOrganisationIdAndIdAndIsDeletedFalse(
        Long organisationId, Long id);

    boolean existsByOrganisationIdAndTemplateCodeAndIsDeletedFalse(Long organisationId, String templateCode);
}

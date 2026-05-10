package com.tss.grc.module.bcm.repository;

import com.tss.grc.module.bcm.entity.CrisisCommsTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrisisCommsTemplateRepository extends JpaRepository<CrisisCommsTemplate, Long> {
    List<CrisisCommsTemplate> findByOrganisationIdAndIsActiveTrueOrderByAudienceRoleAscCodeAsc(Long organisationId);
}

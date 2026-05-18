package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BcmSettingsRepository extends JpaRepository<BcmSettings, Long> {
    Optional<BcmSettings> findByOrganisationId(Long organisationId);
}

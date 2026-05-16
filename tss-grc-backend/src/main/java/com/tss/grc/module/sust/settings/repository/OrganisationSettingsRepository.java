package com.tss.grc.module.sust.settings.repository;

import com.tss.grc.module.sust.settings.entity.OrganisationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganisationSettingsRepository extends JpaRepository<OrganisationSettings, Long> {

    Optional<OrganisationSettings> findByOrganisationId(Long organisationId);
}

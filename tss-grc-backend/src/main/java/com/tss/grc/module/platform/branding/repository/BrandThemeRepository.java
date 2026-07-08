package com.tss.grc.module.platform.branding.repository;

import com.tss.grc.module.platform.branding.entity.BrandTheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * One brand theme per organisation. The {@code @Where(is_deleted = 0)} on the entity
 * keeps this org-scoped finder from returning a soft-deleted row.
 */
public interface BrandThemeRepository extends JpaRepository<BrandTheme, Long> {

    Optional<BrandTheme> findByOrganisationId(Long organisationId);
}

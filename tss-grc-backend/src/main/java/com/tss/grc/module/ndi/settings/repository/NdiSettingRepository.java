package com.tss.grc.module.ndi.settings.repository;

import com.tss.grc.module.ndi.settings.entity.NdiSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NdiSettingRepository extends JpaRepository<NdiSetting, Long> {

    List<NdiSetting> findByOrganisationIdOrderByCategoryAscSettingKeyAsc(Long org);

    Optional<NdiSetting> findByOrganisationIdAndSettingKey(Long org, String key);

    Optional<NdiSetting> findByIdAndOrganisationId(Long id, Long org);
}

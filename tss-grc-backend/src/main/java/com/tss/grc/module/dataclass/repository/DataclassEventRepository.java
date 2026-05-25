package com.tss.grc.module.dataclass.repository;

import com.tss.grc.module.dataclass.entity.DataclassEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataclassEventRepository extends JpaRepository<DataclassEvent, Long> {
    List<DataclassEvent> findByOrganisationIdAndDataAssetIdOrderByOccurredAtDesc(Long org, Long id);
}

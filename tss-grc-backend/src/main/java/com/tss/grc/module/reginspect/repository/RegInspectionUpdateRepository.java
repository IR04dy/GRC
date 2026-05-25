package com.tss.grc.module.reginspect.repository;

import com.tss.grc.module.reginspect.entity.RegInspectionUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegInspectionUpdateRepository
        extends JpaRepository<RegInspectionUpdate, Long> {

    List<RegInspectionUpdate> findByInspectionIdAndOrganisationIdOrderByOccurredAtDesc(
            Long inspectionId, Long organisationId);
}

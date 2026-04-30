package com.tss.grc.module.privacy.repository;

import com.tss.grc.module.privacy.entity.ProcessingActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingActivityRepository extends JpaRepository<ProcessingActivity, Long> {
    Page<ProcessingActivity> findByOrganisationId(Long organisationId, Pageable pageable);
}

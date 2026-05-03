package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChangeTaskRepository extends JpaRepository<ChangeTask, Long> {
    List<ChangeTask> findByChangeRequestIdOrderBySortOrderAsc(Long changeRequestId);
}

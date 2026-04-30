package com.tss.grc.module.bcm.repository;

import com.tss.grc.module.bcm.entity.BiaRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BiaRecordRepository extends JpaRepository<BiaRecord, Long> {
    Page<BiaRecord> findByProcessId(Long processId, Pageable pageable);
    List<BiaRecord> findByProcessIdIn(List<Long> processIds);
}

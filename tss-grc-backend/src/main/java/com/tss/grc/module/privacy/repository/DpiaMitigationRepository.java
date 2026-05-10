package com.tss.grc.module.privacy.repository;

import com.tss.grc.module.privacy.entity.DpiaMitigation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DpiaMitigationRepository extends JpaRepository<DpiaMitigation, Long> {
    List<DpiaMitigation> findByDpiaIdOrderBySortOrder(Long dpiaId);
}

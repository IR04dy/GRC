package com.tss.grc.module.compliance.repository;

import com.tss.grc.module.compliance.entity.Framework;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FrameworkRepository extends JpaRepository<Framework, Long> {
    List<Framework> findByIsActiveTrueOrderByFamilyAscCodeAsc();
    Optional<Framework> findByCode(String code);
}

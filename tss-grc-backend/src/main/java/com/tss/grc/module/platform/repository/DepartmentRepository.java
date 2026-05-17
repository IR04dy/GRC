package com.tss.grc.module.platform.repository;

import com.tss.grc.module.platform.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByOrganisationIdOrderByCodeAsc(Long organisationId);

    Optional<Department> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<Department> findByOrganisationIdAndCode(Long organisationId, String code);

    List<Department> findByOrganisationIdAndParentIdOrderByCodeAsc(Long organisationId, Long parentId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}

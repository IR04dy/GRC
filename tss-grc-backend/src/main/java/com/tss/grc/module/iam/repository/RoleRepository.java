package com.tss.grc.module.iam.repository;

import com.tss.grc.module.iam.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoleRepository extends JpaRepository<Role, Long> {
    List<Role> findByOrganisationIdOrOrganisationIdIsNull(Long organisationId);
    List<Role> findByIsSystemRoleTrue();
}

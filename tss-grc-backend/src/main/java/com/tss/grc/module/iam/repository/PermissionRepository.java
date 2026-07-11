package com.tss.grc.module.iam.repository;

import com.tss.grc.module.iam.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** RBAC governance P0 — read access to the permission catalogue (module × action). */
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByModuleAndAction(String module, String action);

    List<Permission> findAllByOrderByModuleAscActionAsc();
}

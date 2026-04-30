package com.tss.grc.module.iam.repository;

import com.tss.grc.module.iam.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByUsernameAndOrganisationId(String username, Long orgId);
    Page<User> findByOrganisationId(Long organisationId, Pageable pageable);
    boolean existsByUsernameAndOrganisationId(String username, Long orgId);
    boolean existsByEmailAndOrganisationId(String email, Long orgId);
    long countByOrganisationIdAndIsActiveTrue(Long organisationId);

    @Query("""
        SELECT DISTINCT u FROM User u
        LEFT JOIN u.roles r
        WHERE u.organisationId = :orgId
          AND (:search IS NULL OR LOWER(u.fullNameEn) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:role IS NULL OR r.roleName = :role)
          AND (:enabled IS NULL OR u.isActive = :enabled)
        """)
    Page<User> findWithFilters(
        @Param("orgId") Long orgId,
        @Param("search") String search,
        @Param("role") String role,
        @Param("enabled") Boolean enabled,
        Pageable pageable
    );
}

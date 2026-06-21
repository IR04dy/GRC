package com.tss.grc.module.iam.repository;

import com.tss.grc.module.iam.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    // PERF-NQ-002: auth-path fetch finders. With roles/permissions now LAZY,
    // these pull the whole authorization graph in ONE query (avoiding the
    // per-role permission N+1) for CustomUserDetailsService.
    @EntityGraph(attributePaths = { "roles", "roles.permissions" })
    Optional<User> findWithRolesByUsername(String username);

    @EntityGraph(attributePaths = { "roles", "roles.permissions" })
    Optional<User> findWithRolesById(Long id);
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

    /**
     * Returns every active user in the tenant who holds the given role name. Used by
     * the F8 automation engine's NOTIFY / ESCALATE handlers to fan a single rule out
     * to all members of a role (e.g. "BCM_MANAGER") without paging.
     *
     * <p>Locked, inactive, and soft-deleted users are excluded — same filter the rest
     * of the platform uses for "who can act on this right now".
     */
    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN u.roles r
        WHERE u.organisationId = :orgId
          AND r.roleName = :role
          AND u.isActive = true
          AND (u.isLocked IS NULL OR u.isLocked = false)
        """)
    List<User> findActiveByOrganisationIdAndRoleName(
        @Param("orgId") Long orgId,
        @Param("role")  String role
    );
}

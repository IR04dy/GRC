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
    /**
     * Whether a username is already taken <b>anywhere on the platform, including soft-deleted
     * rows</b> — F2-418.
     *
     * <p>This mirrors {@code ix_users_username} exactly, and <b>both</b> narrowings the finders it
     * replaced carried are the point. V227 creates that index GLOBAL (key is {@code username}
     * alone, no {@code organisation_id}) and UNFILTERED (no {@code WHERE is_deleted = 0}), and its
     * own comment states both as deliberate: <em>"login authenticates by username with no tenant
     * context, so the uniqueness here is GLOBAL (username/email unique across the platform), NOT
     * per-organisation"</em> and <em>"keeps username/email globally unique even across soft-deleted
     * rows"</em>. The guard that stood here asked
     * {@code existsByUsernameAndOrganisationId(username, orgId)} — narrower on <b>both</b> axes, so
     * it answered "available" for exactly the rows the index was about to reject.
     *
     * <p>⚠ NATIVE on purpose, and that is not a style choice. A derived finder runs against the
     * entity, so it inherits {@link com.tss.grc.module.iam.entity.User}'s
     * {@code @Where(clause = "is_deleted = 0")} and cannot see the row the index counts —
     * F2-374's shape. {@code @Where} applies to entity queries only; native SQL bypasses it.
     *
     * <p>No {@code COLLATE}: the predicate is on the column, so it compares under the column's own
     * collation — which is the collation the unique index compares under. The guard and the index
     * therefore agree about case by construction rather than by coincidence (F2-179).
     */
    @Query(value = "SELECT COUNT(*) FROM iam.users WHERE username = :username", nativeQuery = true)
    long countByUsernameIncludingDeleted(@Param("username") String username);

    /** Email counterpart of {@link #countByUsernameIncludingDeleted} — mirrors {@code ix_users_email}. */
    @Query(value = "SELECT COUNT(*) FROM iam.users WHERE email = :email", nativeQuery = true)
    long countByEmailIncludingDeleted(@Param("email") String email);

    /**
     * As {@link #countByEmailIncludingDeleted}, excluding one row by id — for the update path,
     * where a user keeping their own email must not collide with themselves.
     */
    @Query(value = "SELECT COUNT(*) FROM iam.users WHERE email = :email AND id <> :id",
           nativeQuery = true)
    long countByEmailIncludingDeletedExcludingId(@Param("email") String email, @Param("id") Long id);
    long countByOrganisationIdAndIsActiveTrue(Long organisationId);

    @Query("""
        SELECT DISTINCT u FROM User u
        LEFT JOIN u.roles r
        WHERE u.organisationId = :orgId
          AND (:search IS NULL OR LOWER(u.fullNameEn) LIKE LOWER(CONCAT('%', :search, '%')) ESCAPE '\\'
               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) ESCAPE '\\'
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) ESCAPE '\\')
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

    /** WF-1 — active members of a department, for DEPARTMENT-addressed workflow requests. */
    List<User> findByOrganisationIdAndDepartmentIdAndIsActiveTrue(Long organisationId, Long departmentId);

    /**
     * SGI-B — resolve a free-text owner e-mail to an active tenant user so an
     * overdue-milestone sweep can ping the actual owner in-app. Case-insensitive;
     * returns empty when the e-mail maps to no active user (then the sweep falls
     * back to the role broadcast the auto-CAPA already performs).
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.organisationId = :orgId
          AND LOWER(u.email) = LOWER(:email)
          AND u.isActive = true
          AND (u.isLocked IS NULL OR u.isLocked = false)
        """)
    List<User> findActiveByOrganisationIdAndEmail(
        @Param("orgId") Long orgId,
        @Param("email") String email
    );
}

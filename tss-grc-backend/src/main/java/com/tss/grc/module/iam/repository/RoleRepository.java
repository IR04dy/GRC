package com.tss.grc.module.iam.repository;

import com.tss.grc.module.iam.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * F2-280 — the one predicate that decides whether a role is PRIVILEGED, for every reader.
     *
     * <p>There were two, in one package, and they disagreed. {@code
     * BreakGlassEventRepository#countPrivilegedHolders} — which feeds the <i>"Privileged
     * holders"</i> KPI on the break-glass dashboard — read the stored flag
     * {@code iam.roles.is_privileged}. {@code AccessReviewItemRepository#generateItems} with
     * scope {@code PRIVILEGED} — which populates the auto-scheduled quarterly NCA ECC 2-2 /
     * SAMA CSF 3.3.5 privileged access review — <em>derived</em> it from the grants
     * ({@code p.action IN ('APPROVE','DELETE')}), and said so in its own javadoc. Measured:
     * <b>12 roles disagreed in dev and 7 in prod</b>, {@code CISO} among them — held, granting
     * {@code IAM:BREAK_GLASS}, {@code ADMIN:APPROVE} and {@code AUDIT_LOG:WRITE}, inside the
     * quarterly review and absent from the KPI.
     *
     * <p>🔑 <b>Neither half is a copy of the other, which is why this is a UNION and not a
     * choice.</b> The stored flag carries a judgement the grants cannot express —
     * {@code EMERGENCY_RESPONDER} is the break-glass role, grants no APPROVE or DELETE, and is
     * privileged anyway — and the derived half covers roles a tenant creates, which the flag
     * never can, because {@code Role} did not map the column and {@code RoleUpsertRequest} has
     * no such field, so <b>every role created through the Access Governance console is 0 by
     * database default and only a migration could ever change it</b> (the flagged set is
     * byte-identical in dev and prod: the fourteen V272 seeded). Collapsing to either half
     * alone would have discarded real classification — F2-251/F2-257's rule, <i>ask whether
     * the label is a copy before reaching for the derive-from-the-parent fix</i>.
     *
     * <p>A union is also the safe direction for a privileged-access review: over-scoping costs
     * a reviewer's time, under-scoping is the finding an examiner writes up (F2-196 — an
     * unrecognised member should be chased, not excused).
     *
     * <p>⚠ Two traps, both recorded. It <b>leads with a newline</b>, because Java strips
     * <em>trailing</em> whitespace in a text block, so a caller writing {@code "… WHERE" +
     * PRIVILEGED_ROLE} would otherwise produce {@code WHERE(r.is_privileged} — the exact
     * mechanism that killed the app at context startup in {@code
     * CommitteeMemberTermRepository.CURRENT_SEAT_WINDOW}. And the inner aliases are
     * {@code rp_priv}/{@code p_priv} rather than {@code rp}/{@code p}, so splicing it into a
     * host query that already joins those names cannot collide.
     *
     * <p>The host must supply {@code iam.roles} under the alias {@code r}.
     * {@code PrivilegedRolePredicateSharingTest} fails the build if this clause is written
     * twice anywhere in main sources.
     */
    String PRIVILEGED_ROLE = """

        (r.is_privileged = 1 OR EXISTS (
              SELECT 1 FROM iam.role_permissions rp_priv
                JOIN iam.permissions p_priv ON p_priv.id = rp_priv.permission_id
               WHERE rp_priv.role_id = r.id AND p_priv.action IN ('APPROVE','DELETE')))""";

    List<Role> findByOrganisationIdOrOrganisationIdIsNull(Long organisationId);
    List<Role> findByIsSystemRoleTrue();

    /**
     * The ids of every role visible to this tenant that {@link #PRIVILEGED_ROLE} calls
     * privileged. One query rather than a per-role check, so the role list does not become
     * ~70 round trips, and one mechanism rather than two (F2-190).
     */
    @Query(value = """
        SELECT r.id FROM iam.roles r
         WHERE r.is_deleted = 0
           AND (r.organisation_id = :org OR r.organisation_id IS NULL)
           AND""" + PRIVILEGED_ROLE, nativeQuery = true)
    List<Long> privilegedRoleIds(@Param("org") Long org);

    // ── retired-role revive support (RBAC governance P0) ────────────────────
    // Role carries @Where(is_deleted = 0), so JPA can never see a soft-deleted
    // role — yet UQ_role_name(organisation_id, role_name) has no is_deleted
    // filter, so a retired role blocks re-creating the same name. These native
    // queries bypass @Where to find + revive the retired row instead.

    @Query(value = "SELECT TOP 1 id FROM iam.roles WHERE organisation_id = :org "
                 + "AND UPPER(role_name) = UPPER(:name) AND is_deleted = 1", nativeQuery = true)
    Optional<Long> findRetiredRoleId(@Param("org") Long org, @Param("name") String name);

    @Modifying
    @Query(value = "UPDATE iam.roles SET is_deleted = 0, deleted_at = NULL, deleted_by = NULL, "
                 + "is_active = 1 WHERE id = :id", nativeQuery = true)
    void reviveRole(@Param("id") Long id);

    /** A revived role starts with a clean grant slate. */
    @Modifying
    @Query(value = "DELETE FROM iam.role_permissions WHERE role_id = :id", nativeQuery = true)
    void clearRolePermissions(@Param("id") Long id);

    /**
     * F2-94 — every role name visible to the tenant (its own plus the global ones) paired
     * with the number of users who would actually receive a broadcast addressed to it.
     * One query, so a report over ~84 addressed role names does not become ~84 round trips.
     *
     * <p><b>The holder predicate is deliberately identical to
     * {@code UserRepository#findActiveByOrganisationIdAndRoleName}</b> — active, not locked,
     * not soft-deleted, same tenant. F2-62's defect was a report and an enforcement point
     * computing access separately and drifting apart, each half defensible alone. A role
     * absent from this result is not a role at all, which is a different fault from a role
     * present with zero holders, and the two have different remedies.
     *
     * @return rows of {@code [role_name (String), active_holders (Number)]}
     */
    /**
     * The platform role names held by one user. F2-95 — the only vocabulary in which
     * "did a CISO approve this change?" can be asked without inventing a fourth one:
     * `change_approvals.approver_role` is free text (7 distinct values in both databases,
     * <b>none</b> of them a platform role name), and committee seat titles are a third set.
     */
    @Query(value = """
        SELECT r.role_name
          FROM iam.user_roles ur
          JOIN iam.roles r ON r.id = ur.role_id
         WHERE ur.user_id = :userId AND r.is_deleted = 0
        """, nativeQuery = true)
    List<String> findRoleNamesByUserId(@Param("userId") Long userId);

    @Query(value = """
        SELECT ro.role_name AS role_name, COUNT(DISTINCT u.id) AS active_holders
          FROM iam.roles ro
          LEFT JOIN iam.user_roles ur ON ur.role_id = ro.id
          LEFT JOIN iam.users u ON u.id = ur.user_id
               AND u.organisation_id = :org
               AND u.is_active = 1
               AND ISNULL(u.is_deleted, 0) = 0
               AND (u.is_locked IS NULL OR u.is_locked = 0)
         WHERE ro.is_deleted = 0
           AND (ro.organisation_id = :org OR ro.organisation_id IS NULL)
         GROUP BY ro.role_name
        """, nativeQuery = true)
    List<Object[]> countActiveHoldersByRole(@Param("org") Long org);
}

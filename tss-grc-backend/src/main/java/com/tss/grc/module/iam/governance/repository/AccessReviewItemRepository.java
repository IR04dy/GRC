package com.tss.grc.module.iam.governance.repository;

import com.tss.grc.module.iam.governance.entity.AccessReviewItem;
import com.tss.grc.module.iam.repository.RoleRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * RBAC Access-Governance P2 — campaign review items. Generation is a native
 * insert-select snapshot of the org's live {@code iam.user_roles} assignments,
 * filtered by campaign scope (PRIVILEGED = {@link RoleRepository#PRIVILEGED_ROLE};
 * MODULE = the role grants anything on the given module; ALL = every assignment).
 *
 * <p>F2-280 — that first clause used to read <i>"the role grants APPROVE or DELETE
 * anywhere"</i>, which was an accurate description of a predicate this file owned alone while
 * {@code BreakGlassEventRepository} answered the same question from the stored
 * {@code iam.roles.is_privileged} flag. They disagreed on 12 roles in dev and 7 in prod. The
 * scope now means whatever the shared constant means, so this sentence cannot go stale
 * independently of the clause.
 */
public interface AccessReviewItemRepository extends JpaRepository<AccessReviewItem, Long> {

    List<AccessReviewItem> findByCampaignIdAndIsDeletedFalseOrderByUsernameAscRoleNameAsc(Long campaignId);

    Optional<AccessReviewItem> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    long countByCampaignIdAndDecisionAndIsDeletedFalse(Long campaignId, String decision);

    long countByCampaignIdAndIsDeletedFalse(Long campaignId);

    @Modifying
    @Query(value = """
        INSERT INTO iam.access_review_items
            (organisation_id, campaign_id, user_id, username, role_id, role_name, assignment_source, valid_to)
        SELECT :org, :campaignId, u.id, u.username, r.id, r.role_name, ur.source, ur.valid_to
        FROM iam.user_roles ur
        JOIN iam.users u ON u.id = ur.user_id AND u.organisation_id = :org AND u.is_deleted = 0
        JOIN iam.roles r ON r.id = ur.role_id AND r.is_deleted = 0
        WHERE (:scope = 'ALL')
           OR (:scope = 'MODULE' AND EXISTS (
                 SELECT 1 FROM iam.role_permissions rp
                 JOIN iam.permissions p ON p.id = rp.permission_id
                 WHERE rp.role_id = r.id AND p.module = :module))
           OR (:scope = 'PRIVILEGED' AND""" + RoleRepository.PRIVILEGED_ROLE + """
              )
        """, nativeQuery = true)
    int generateItems(@Param("org") Long org, @Param("campaignId") Long campaignId,
                      @Param("scope") String scope, @Param("module") String module);

    /** V294 — USER-scoped snapshot (JML MOVER mini-review): one user's assignments only. */
    @Modifying
    @Query(value = """
        INSERT INTO iam.access_review_items
            (organisation_id, campaign_id, user_id, username, role_id, role_name, assignment_source, valid_to)
        SELECT :org, :campaignId, u.id, u.username, r.id, r.role_name, ur.source, ur.valid_to
        FROM iam.user_roles ur
        JOIN iam.users u ON u.id = ur.user_id AND u.organisation_id = :org AND u.is_deleted = 0
        JOIN iam.roles r ON r.id = ur.role_id AND r.is_deleted = 0
        WHERE u.id = :userId
        """, nativeQuery = true)
    int generateItemsForUser(@Param("org") Long org, @Param("campaignId") Long campaignId,
                             @Param("userId") Long userId);
}

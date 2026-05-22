package com.tss.grc.module.platform.savedviews.repository;

import com.tss.grc.module.platform.savedviews.entity.SavedView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SavedViewRepository extends JpaRepository<SavedView, Long> {

    /** Lookup for the dropdown — "all my views for this page". */
    List<SavedView> findByOrganisationIdAndUserIdAndPageKeyOrderByNameAsc(
        Long organisationId, Long userId, String pageKey);

    /** Default-view lookup — fires on page mount when URL is clean. */
    Optional<SavedView> findFirstByOrganisationIdAndUserIdAndPageKeyAndIsDefaultTrue(
        Long organisationId, Long userId, String pageKey);

    Optional<SavedView> findByIdAndOrganisationIdAndUserId(
        Long id, Long organisationId, Long userId);

    /** Bulk clear-default — used when promoting a new view to default
     *  so the unique partial index doesn't get violated. */
    @Modifying
    @Query("""
        UPDATE SavedView v SET v.isDefault = false, v.updatedAt = CURRENT_TIMESTAMP
         WHERE v.organisationId = :orgId AND v.userId = :userId
           AND v.pageKey = :pageKey AND v.isDefault = true AND v.id <> :exceptId
        """)
    int clearDefaultsExcept(@Param("orgId") Long orgId,
                            @Param("userId") Long userId,
                            @Param("pageKey") String pageKey,
                            @Param("exceptId") Long exceptId);
}

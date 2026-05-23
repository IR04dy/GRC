package com.tss.grc.module.platform.push.repository;

import com.tss.grc.module.platform.push.entity.WebPushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Soft-delete-filtered access to {@link WebPushSubscription}. All finders
 * are org-scoped because the entity's {@code @Where} clause only excludes
 * archived rows — tenant isolation is still our responsibility.
 */
@Repository
public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, Long> {

    Optional<WebPushSubscription> findByIdAndOrganisationId(Long id, Long organisationId);

    List<WebPushSubscription> findByOrganisationIdAndUserIdOrderByCreatedAtDesc(
        Long organisationId, Long userId);

    List<WebPushSubscription> findByOrganisationIdAndUserIdAndEnabledOrderByCreatedAtDesc(
        Long organisationId, Long userId, Boolean enabled);

    /**
     * Lookup used by {@code register()} to decide between insert and update —
     * the unique key in SQL is (user_id, CHECKSUM(endpoint)) but Java sees
     * the raw {@code endpoint} string; we just compare straightforwardly.
     */
    Optional<WebPushSubscription> findByOrganisationIdAndUserIdAndEndpoint(
        Long organisationId, Long userId, String endpoint);

    /** Used by dispatch fan-out to resolve enabled subscriptions for a list of users. */
    List<WebPushSubscription> findByOrganisationIdAndUserIdInAndEnabled(
        Long organisationId, List<Long> userIds, Boolean enabled);
}

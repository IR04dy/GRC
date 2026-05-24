package com.tss.grc.module.auth.refresh_token.repository;

import com.tss.grc.module.auth.refresh_token.entity.RefreshTokenStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenStoreRepository extends JpaRepository<RefreshTokenStore, Long> {

    /** Verify-path lookup. UX_refresh_token_jti makes this a fast seek. */
    Optional<RefreshTokenStore> findByJti(String jti);

    /** "List my active sessions" — newest first. */
    List<RefreshTokenStore> findByOrganisationIdAndUserIdAndRevokedAtIsNullOrderByIssuedAtDesc(
            Long organisationId, Long userId);

    /** All sessions for a user, including revoked ones — for admin auditing. */
    List<RefreshTokenStore> findByOrganisationIdAndUserIdOrderByIssuedAtDesc(
            Long organisationId, Long userId);

    /**
     * Bulk-revoke every active session for a given user (logout everywhere /
     * suspected compromise). Returns the number of rows updated.
     *
     * <p>Note: the query intentionally does NOT filter by organisation_id —
     * the admin path that calls this already checked the caller's auth, and
     * we need to flush sessions across all tenants if a user has roles in
     * multiple orgs (rare but possible for platform admins).
     */
    @Modifying
    @Query("UPDATE RefreshTokenStore r SET r.revokedAt = :revokedAt, "
        +  "  r.revokedByUserId = :revokedBy, r.revokeReason = :reason, "
        +  "  r.updatedAt = :revokedAt, r.updatedBy = :revokedBy "
        +  "WHERE r.userId = :userId AND r.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId,
                          @Param("revokedAt") LocalDateTime revokedAt,
                          @Param("revokedBy") Long revokedBy,
                          @Param("reason") String reason);
}

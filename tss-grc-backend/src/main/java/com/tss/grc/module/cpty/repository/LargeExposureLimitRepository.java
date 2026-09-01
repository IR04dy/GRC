package com.tss.grc.module.cpty.repository;

import com.tss.grc.module.cpty.entity.LargeExposureLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LargeExposureLimitRepository
        extends JpaRepository<LargeExposureLimit, Long> {

    List<LargeExposureLimit> findByOrganisationIdOrderByUtilisationPctDesc(
            Long organisationId);

    /**
     * Every limit touching a counterparty, oldest first.
     *
     * <p>F2-423 — this was {@code findByCounterpartyIdAndOrganisationId} with no order, the
     * only one of four sibling {@code findByCounterpartyIdAndOrganisationId*} finders in
     * {@code module/cpty} that declared none ({@code CptyEvent}, {@code CreditRating} and
     * {@code RatingAction} all order theirs). Both consumers needed the order:
     * {@code listLimitsForCounterparty} rendered the list in whatever sequence the plan
     * yielded, and {@code upsertLimit} took {@code get(0)} of it to decide WHICH SAMA limit
     * row to overwrite. Measured 2026-09-01: 33 counterparty keys hold two rows each in dev
     * and prod, so that pick is exercised today; the plan returns id ASC on every sampled
     * key, so stating {@code OrderByIdAsc} changes no outcome that works now.
     */
    List<LargeExposureLimit> findByCounterpartyIdAndOrganisationIdOrderByIdAsc(
            Long counterpartyId, Long organisationId);

    /**
     * The limit row a COUNTERPARTY-scope upsert addresses: oldest row for the WHOLE key.
     *
     * <p>{@code Scope} is part of the key, and leaving it out is what let a COUNTERPARTY
     * upsert capture a GROUP row that happened to carry a {@code counterparty_id} — nothing
     * refuses such a request, and {@code upsertLimit} then rewrites the captured row's scope
     * and nulls its {@code group_reference}, destroying a consolidated group limit.
     *
     * <p>The scope is matched by the QUERY rather than by a Java {@code equals}, so it
     * compares under the column's own (case-insensitive) collation instead of exactly —
     * strictly wider than the Java filter it replaces, which is the safe direction here
     * (F2-179).
     */
    Optional<LargeExposureLimit> findFirstByOrganisationIdAndScopeAndCounterpartyIdOrderByIdAsc(
            Long organisationId, String scope, Long counterpartyId);

    /** The limit row a GROUP-scope upsert addresses: oldest row for the whole key. */
    Optional<LargeExposureLimit> findFirstByOrganisationIdAndScopeAndGroupReferenceOrderByIdAsc(
            Long organisationId, String scope, String groupReference);

    List<LargeExposureLimit> findByOrganisationIdAndBandIn(
            Long organisationId, java.util.Collection<String> bands);

    Optional<LargeExposureLimit> findByIdAndOrganisationId(Long id, Long organisationId);

    long countByOrganisationIdAndBand(Long organisationId, String band);
}

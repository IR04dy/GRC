package com.tss.grc.module.ndi.evidence.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.audit.service.NdiAuditPublisher;
import com.tss.grc.module.ndi.evidence.entity.NdiEvidenceItem;
import com.tss.grc.module.ndi.evidence.repository.NdiEvidenceItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * F2-218 — a WRITE-permission caller may not reach what the APPROVE route guards.
 *
 * <p>{@code approve()} carries two controls: {@code NDI:APPROVE} at the controller and a
 * maker/checker SoD check here (the evidence owner may not approve their own item), plus a reviewer
 * stamped from the authenticated principal and an audit transition row. Probed live 2026-08-15 as
 * {@code ranalyst} — who holds {@code NDI:WRITE} and not {@code NDI:APPROVE}:
 *
 * <pre>
 *   POST /api/ndi/evidence/1/approve                                    -> 403
 *   POST /api/ndi/evidence  {status: ACTIVE, reviewerName: "Nobody Who
 *        Reviewed This", reviewedAt: 2001-01-01, ownerName: "ranalyst"}  -> 200, all three stored
 *   PUT  /api/ndi/evidence/{id}  {status: ACTIVE}   (on a PENDING row)   -> 200, transition performed
 * </pre>
 *
 * <p>No audit row in either case. Both write paths are covered here, and the approve/reject routes
 * are asserted still to stamp what the write paths no longer accept — otherwise this slice would be
 * indistinguishable from one that closed the bypass by breaking the feature.
 */
@ExtendWith(MockitoExtension.class)
class NdiEvidenceCreateAuthorityTest {

    private static final Long ORG_ID = 1L;

    @Mock private NdiEvidenceItemRepository repo;
    @Mock private NdiAuditPublisher audit;
    private NdiEvidenceItemService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new NdiEvidenceItemService(repo, audit);
        lenient().when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static NdiEvidenceItem filed() {
        NdiEvidenceItem e = new NdiEvidenceItem();
        e.setTitleEn("Access review export");
        e.setEvidenceType("ATTESTATION");
        return e;
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void evidenceCannotBeFiledAlreadyApproved() {
        NdiEvidenceItem e = filed();
        e.setStatus("ACTIVE");

        assertThatThrownBy(() -> service.create(e))
            .as("ACTIVE is approve()'s to write, behind NDI:APPROVE and the SoD check. Filing "
                + "straight into it reaches the same end state with neither.")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("status")
            .hasMessageContaining("ACTIVE")
            .hasMessageContaining("approve");
    }

    @Test
    void aCreateNeverKeepsAReviewerOrAReviewDateTheCallerSupplied() {
        NdiEvidenceItem e = filed();
        e.setOwnerName("ranalyst");
        e.setReviewerName("Nobody Who Reviewed This");
        e.setReviewedAt(LocalDate.of(2001, 1, 1));

        NdiEvidenceItem saved = service.create(e);

        assertThat(saved.getReviewerName())
            .as("SEC-NDI-003 says the reviewer is the authenticated principal at approve/reject. "
                + "A create has been reviewed by nobody.")
            .isNull();
        assertThat(saved.getReviewedAt()).isNull();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getOwnerName())
            .as("the owner IS the caller's to state — this fix must not swallow it")
            .isEqualTo("ranalyst");
    }

    @Test
    void aCreateStillAcceptsAnOmittedOrExplicitlyPendingStatus() {
        assertThat(service.create(filed()).getStatus())
            .as("the create form sends no status at all, which is what made this safe to enforce")
            .isEqualTo("PENDING");

        NdiEvidenceItem explicit = filed();
        explicit.setStatus("PENDING");
        assertThat(service.create(explicit).getStatus()).isEqualTo("PENDING");
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void anUpdateCannotPerformThePendingToActiveApproval() {
        NdiEvidenceItem existing = filed();
        existing.setId(7L);
        existing.setStatus("PENDING");
        when(repo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(existing));

        NdiEvidenceItem patch = new NdiEvidenceItem();
        patch.setStatus("ACTIVE");
        patch.setReviewedAt(LocalDate.of(2001, 1, 1));
        patch.setTitleEn("edited title");

        NdiEvidenceItem saved = service.update(7L, patch);

        assertThat(saved.getStatus())
            .as("PUT /{id} performed exactly the transition POST /{id}/approve had just refused, "
                + "200, with no reviewer, no SoD check and no audit row")
            .isEqualTo("PENDING");
        assertThat(saved.getReviewedAt()).isNull();
        assertThat(saved.getTitleEn())
            .as("the edit itself must still work — this is a metadata route, not a no-op")
            .isEqualTo("edited title");
    }

    // ── the guarded routes still do their job ────────────────────────────────

    @Test
    void approveStillStampsTheReviewerAndEnforcesSegregationOfDuties() {
        NdiEvidenceItem pending = filed();
        pending.setId(9L);
        pending.setStatus("PENDING");
        pending.setOwnerName("some-other-owner");
        when(repo.findByIdAndOrganisationId(9L, ORG_ID)).thenReturn(Optional.of(pending));

        NdiEvidenceItem approved = service.approve(9L);

        assertThat(approved.getStatus()).isEqualTo("ACTIVE");
        assertThat(approved.getReviewedAt())
            .as("the route that IS allowed to approve must still record when")
            .isNotNull();
    }

    @Test
    void approveStillRefusesAnythingThatIsNotPending() {
        NdiEvidenceItem active = filed();
        active.setId(11L);
        active.setStatus("ACTIVE");
        when(repo.findByIdAndOrganisationId(11L, ORG_ID)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.approve(11L))
            .as("PENDING is the only state approve() acts on, and the create's guard is read from "
                + "the same constant so the two cannot drift")
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PENDING");
    }

    /**
     * The distinction the fix turns on: state the SERVER derives is still allowed to differ from
     * the initial one. Without this, "a create always lands in PENDING" reads as an equally valid —
     * and wrong — description of the change, and the next reader deletes Rule 6.
     */
    @Test
    void ruleSixStillFlipsAnExpiredItemOnTheRoutesThatCanReachActive() {
        NdiEvidenceItem pending = filed();
        pending.setId(13L);
        pending.setStatus("PENDING");
        pending.setOwnerName("some-other-owner");
        pending.setExpiryDate(LocalDate.now().minusDays(1));
        when(repo.findByIdAndOrganisationId(13L, ORG_ID)).thenReturn(Optional.of(pending));

        assertThat(service.approve(13L).getStatus())
            .as("approve() sets ACTIVE and then applies Rule 6, so evidence approved with an "
                + "expiry date already past is EXPIRED — server-derived, not caller-named")
            .isEqualTo("EXPIRED");
    }
}

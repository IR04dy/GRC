package com.tss.grc.module.compliance.obligation.service;

import com.tss.grc.module.compliance.obligation.entity.ObligationAttestationCampaign;
import com.tss.grc.module.compliance.obligation.repository.ObligationAttestationCampaignRepository;
import com.tss.grc.module.compliance.obligation.repository.ObligationAttestationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * F2-385 — the single writer of an obligation-attestation campaign's denormalized status rollups
 * ({@code total_in_progress / total_attested / total_attested_with_excs / total_overdue / total_na}).
 *
 * <p>It lives in its own component so both the campaign service ({@code closeCampaign}) and the
 * attestation service ({@code recordAttestation} / {@code reviewAttestation}) can recompute after a
 * child-status change without a service→service cycle: {@code ObligationCampaignService} already
 * injects {@code ObligationAttestationService} (for its escalation step), so having the attestation
 * service inject the campaign service back would be a circular constructor dependency. This
 * component depends only on the two repositories, so both services depend on it one-directionally
 * (F2-190 — one recompute, not a hand-rolled second copy in each path).
 *
 * <p>Synchronous, same-transaction: a caller saves the child attestation first, then calls this; the
 * count queries auto-flush the pending child write, so the rollup includes the just-recorded row.
 * The campaign is a different entity from the attestation, loaded-mutated-saved once, so there is no
 * double-flush of a {@code @Version} entity.
 */
@Component
@RequiredArgsConstructor
public class ObligationCampaignRollups {

    private final ObligationAttestationRepository attestationRepo;
    private final ObligationAttestationCampaignRepository campaignRepo;

    /** Recompute a campaign's status rollups from its live child attestation rows. */
    @Transactional
    public void recompute(ObligationAttestationCampaign c) {
        Long orgId = c.getOrganisationId();
        Long cid = c.getId();
        long inProgress = attestationRepo.countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
            orgId, cid, "IN_PROGRESS");
        long attested = attestationRepo.countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
            orgId, cid, "ATTESTED");
        long withExcs = attestationRepo.countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
            orgId, cid, "ATTESTED_WITH_EXCEPTIONS");
        long overdue = attestationRepo.countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
            orgId, cid, "OVERDUE");
        long na = attestationRepo.countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
            orgId, cid, "NOT_APPLICABLE");
        c.setTotalInProgress((int) inProgress);
        c.setTotalAttested((int) attested);
        c.setTotalAttestedWithExcs((int) withExcs);
        c.setTotalOverdue((int) overdue);
        c.setTotalNa((int) na);
        campaignRepo.save(c);
    }

    /**
     * Recompute the campaign named by {@code campaignId}, scoped to {@code orgId}. No-op when the
     * attestation has no campaign (ad-hoc / no-campaign cycle) or the campaign is absent — the
     * attestation paths call this after every record/review and many attestations carry no campaign.
     */
    @Transactional
    public void recompute(Long orgId, Long campaignId) {
        if (campaignId == null) return;
        campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(orgId, campaignId)
            .ifPresent(this::recompute);
    }
}

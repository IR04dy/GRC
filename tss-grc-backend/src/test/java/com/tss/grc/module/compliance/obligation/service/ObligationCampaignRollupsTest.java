package com.tss.grc.module.compliance.obligation.service;

import com.tss.grc.module.compliance.obligation.entity.ObligationAttestationCampaign;
import com.tss.grc.module.compliance.obligation.repository.ObligationAttestationCampaignRepository;
import com.tss.grc.module.compliance.obligation.repository.ObligationAttestationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * F2-385 — {@link ObligationCampaignRollups} is the single writer of an obligation campaign's
 * denormalized status rollups, shared by the campaign service (close) and the attestation service
 * (record / review) so a child-status change recomputes through one path without a service cycle.
 *
 * <p>Hermetic (Mockito, no DB — the recount is SQL COUNTs, stubbed; F2-320 / F2-325).
 */
@ExtendWith(MockitoExtension.class)
class ObligationCampaignRollupsTest {

    private static final Long ORG = 7L;
    private static final Long CID = 201L;

    @Mock private ObligationAttestationRepository attestationRepo;
    @Mock private ObligationAttestationCampaignRepository campaignRepo;

    private ObligationCampaignRollups rollups;

    @BeforeEach void setUp() {
        rollups = new ObligationCampaignRollups(attestationRepo, campaignRepo);
    }

    private static ObligationAttestationCampaign campaign() {
        ObligationAttestationCampaign c = new ObligationAttestationCampaign();
        c.setId(CID);
        c.setOrganisationId(ORG);
        // Stale post-open values the recompute must overwrite (the F2-385 drift: these stayed 0).
        c.setTotalInProgress(0);
        c.setTotalAttested(0);
        c.setTotalAttestedWithExcs(0);
        c.setTotalOverdue(0);
        c.setTotalNa(0);
        return c;
    }

    private void stubCounts(long inProg, long att, long withExc, long overdue, long na) {
        when(attestationRepo.countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
            ORG, CID, "IN_PROGRESS")).thenReturn(inProg);
        when(attestationRepo.countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
            ORG, CID, "ATTESTED")).thenReturn(att);
        when(attestationRepo.countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
            ORG, CID, "ATTESTED_WITH_EXCEPTIONS")).thenReturn(withExc);
        when(attestationRepo.countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
            ORG, CID, "OVERDUE")).thenReturn(overdue);
        when(attestationRepo.countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
            ORG, CID, "NOT_APPLICABLE")).thenReturn(na);
    }

    @Test void recomputeEntity_setsAllFiveTotalsFromTheLiveCounts() {
        ObligationAttestationCampaign c = campaign();
        stubCounts(2, 3, 1, 4, 5);

        rollups.recompute(c);

        assertThat(c.getTotalInProgress()).isEqualTo(2);
        assertThat(c.getTotalAttested()).isEqualTo(3);
        assertThat(c.getTotalAttestedWithExcs()).isEqualTo(1);
        assertThat(c.getTotalOverdue()).isEqualTo(4);
        assertThat(c.getTotalNa()).isEqualTo(5);
        verify(campaignRepo).save(c);
    }

    @Test void recomputeById_loadsOrgScopedThenRecomputes() {
        ObligationAttestationCampaign c = campaign();
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG, CID))
            .thenReturn(Optional.of(c));
        stubCounts(0, 6, 0, 0, 0);

        rollups.recompute(ORG, CID);

        assertThat(c.getTotalAttested()).isEqualTo(6);
        verify(campaignRepo).save(c);
    }

    @Test void recomputeById_nullCampaign_isNoOp() {
        // Ad-hoc attestation (no campaign) — the attestation paths call this unconditionally.
        rollups.recompute(ORG, null);
        verifyNoInteractions(campaignRepo);
        verifyNoInteractions(attestationRepo);
    }

    @Test void recomputeById_absentCampaign_doesNotSave() {
        when(campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG, CID))
            .thenReturn(Optional.empty());

        rollups.recompute(ORG, CID);

        verify(campaignRepo, never()).save(any());
        verifyNoInteractions(attestationRepo);
    }
}

package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.ForensicsArtefact;
import com.tss.grc.module.socops.entity.ForensicsCustodyEvent;
import com.tss.grc.module.socops.repository.ForensicsArtefactRepository;
import com.tss.grc.module.socops.repository.ForensicsCustodyEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-331 — the forensics chain-of-custody register. {@code integrity_status} / {@code
 * integrity_verified_at} are the {@link ForensicsArtefactService#verifyIntegrity} attestation,
 * owned by {@code POST /{id}/verify-integrity} (which emits {@code FORENSICS_INTEGRITY_LOST} and
 * audits the check). Two mass-assignment holes let a same-permission caller forge them:
 *
 * <ul>
 *   <li>the entity initialiser was {@code = "VERIFIED"} (F2-238, flattering direction), so a
 *       created artefact was born asserting a verification that never happened — measured latent
 *       (dev/prod both hold 4 artefacts, all legitimately VERIFIED with a timestamp) but live in
 *       code for the next API create; and</li>
 *   <li>{@code copyMutable} copied both fields from the request, so a {@code PUT} could flip a
 *       CORRUPTED artefact (court evidence flagged unusable) back to VERIFIED with no event.</li>
 * </ul>
 *
 * <p>F2-332 is the sibling of the second hole, one field-pair over: {@code copyMutable} also copied
 * {@code currentCustodian} / {@code currentLocation}, which are {@code transferCustody()}'s alone (it
 * appends an immutable custody-ledger row before moving the pointer). {@code copyMutable} runs only on
 * update, so a {@code PUT} moved court-bound evidence with no ledger row and no {@code
 * FORENSICS_CUSTODY_TRANSFER} audit; the fix stops copying the pair, and the create path (which seeds
 * the CAPTURE ledger row from them) is unaffected.
 *
 * <p>No live probe: {@code save}/{@code copyMutable} are a synchronous scrub with no DB/tenant/
 * row_version dimension, and the permission boundary is read from the controller's {@code
 * @PreAuthorize} annotations (F2-325..F2-330's standard). The register has no UI create/update
 * client, so both routes are API-only.
 */
@ExtendWith(MockitoExtension.class)
class ForensicsArtefactServiceTest {

    private static final Long ORG = 1L;

    @Mock private ForensicsArtefactRepository     repo;
    @Mock private ForensicsCustodyEventRepository ledger;
    @Mock private AuditLogService                 audit;
    @Mock private ApplicationEventPublisher       publisher;
    @InjectMocks private ForensicsArtefactService svc;

    @BeforeEach void setTenant() { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void clearTenant() { TenantContext.clear(); }

    private ForensicsArtefact minimal() {
        ForensicsArtefact a = new ForensicsArtefact();
        a.setArtefactCode("FA-001");
        a.setArtefactType("DISK_IMAGE");
        a.setTitleEn("Seized laptop image");
        a.setCapturedByHandler("handler");
        a.setSha256Hash("abc");
        return a;
    }

    /** A fresh artefact is UNVERIFIED — the "VERIFIED" birth default (F2-238) is gone. */
    @Test void create_isBornUnverified_notVerified() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ForensicsArtefact in = minimal();          // integrity_status omitted (no initialiser)
        ForensicsArtefact saved = svc.save(in);
        assertThat(saved.getIntegrityStatus()).isEqualTo("UNVERIFIED");
        assertThat(saved.getIntegrityVerifiedAt()).isNull();
    }

    /** A create may not assert the verifyIntegrity() attestation — a forged VERIFIED + timestamp
     *  is scrubbed to the server-owned initial state. */
    @Test void create_scrubsForgedIntegrityAttestation() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ForensicsArtefact in = minimal();
        in.setIntegrityStatus("VERIFIED");
        in.setIntegrityVerifiedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        ForensicsArtefact saved = svc.save(in);
        assertThat(saved.getIntegrityStatus()).isEqualTo("UNVERIFIED");
        assertThat(saved.getIntegrityVerifiedAt()).isNull();
    }

    /** An update may not flip a CORRUPTED artefact back to VERIFIED — that is verifyIntegrity()'s
     *  alone, and doing it via PUT bypassed the FORENSICS_INTEGRITY_LOST event and the audit row. */
    @Test void update_cannotFlipCorruptedToVerified() {
        LocalDateTime checkedAt = LocalDateTime.of(2026, 5, 1, 9, 0);
        ForensicsArtefact existing = minimal();
        existing.setId(7L);
        existing.setOrganisationId(ORG);
        existing.setIntegrityStatus("CORRUPTED");
        existing.setIntegrityVerifiedAt(checkedAt);
        when(repo.findByIdAndOrganisationId(eq(7L), eq(ORG))).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ForensicsArtefact patch = minimal();
        patch.setId(7L);
        patch.setIntegrityStatus("VERIFIED");
        patch.setIntegrityVerifiedAt(LocalDateTime.of(2026, 6, 1, 9, 0));
        ForensicsArtefact saved = svc.save(patch);

        assertThat(saved.getIntegrityStatus()).isEqualTo("CORRUPTED");
        assertThat(saved.getIntegrityVerifiedAt()).isEqualTo(checkedAt);
    }

    /** F2-332 — currentCustodian / currentLocation are transferCustody()'s alone (it APPENDS an
     *  immutable custody-ledger row before moving the pointer). A PUT that changed them moved
     *  court-bound evidence with no ledger row and no FORENSICS_CUSTODY_TRANSFER audit, so an update
     *  now leaves both untouched and writes NO custody event. */
    @Test void update_cannotMoveCustodyWithoutLedger() {
        ForensicsArtefact existing = minimal();
        existing.setId(9L);
        existing.setOrganisationId(ORG);
        existing.setCurrentCustodian("Eng. Held");
        existing.setCurrentLocation("Evidence Locker A");
        when(repo.findByIdAndOrganisationId(eq(9L), eq(ORG))).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ForensicsArtefact patch = minimal();
        patch.setId(9L);
        patch.setCurrentCustodian("Eng. Interloper");
        patch.setCurrentLocation("Somewhere Else");
        ForensicsArtefact saved = svc.save(patch);

        assertThat(saved.getCurrentCustodian()).isEqualTo("Eng. Held");
        assertThat(saved.getCurrentLocation()).isEqualTo("Evidence Locker A");
        verify(ledger, never()).save(any());   // an update appends no TRANSFER row
    }

    /** The create half of copyMutable's scrub must stay open: a create legitimately sets the
     *  initial custodian from the request AND seeds the CAPTURE ledger row from it. */
    @Test void create_keepsRequestCustodian_andSeedsCaptureLedger() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ForensicsArtefact in = minimal();
        in.setCurrentCustodian("Eng. Acquirer");
        in.setCurrentLocation("Evidence Locker A");
        ForensicsArtefact saved = svc.save(in);

        assertThat(saved.getCurrentCustodian()).isEqualTo("Eng. Acquirer");
        ArgumentCaptor<ForensicsCustodyEvent> cap = ArgumentCaptor.forClass(ForensicsCustodyEvent.class);
        verify(ledger).save(cap.capture());
        assertThat(cap.getValue().getEventType()).isEqualTo("CAPTURE");
        assertThat(cap.getValue().getToCustodian()).isEqualTo("Eng. Acquirer");
    }
}

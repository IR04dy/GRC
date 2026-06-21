package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.CryptoAsset;
import com.tss.grc.module.socops.entity.IamJmlEvent;
import com.tss.grc.module.socops.entity.MaDueDiligence;
import com.tss.grc.module.socops.entity.OtAsset;
import com.tss.grc.module.socops.repository.CryptoAssetRepository;
import com.tss.grc.module.socops.repository.IamJmlEventRepository;
import com.tss.grc.module.socops.repository.MaDueDiligenceRepository;
import com.tss.grc.module.socops.repository.OtAssetRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SEC-CYB-002 — cross-tenant row takeover via client-supplied id / mass
 * assignment. The socops {@code save()} methods must:
 * <ul>
 *   <li>On CREATE (no id): stamp organisationId from {@link TenantContext},
 *       never from the request body — a forged body org is ignored.</li>
 *   <li>On a write carrying an id: treat it as a tenant-scoped UPDATE — load via
 *       {@code findByIdAndOrganisationId}; a miss (row owned by another tenant,
 *       or absent) throws {@link EntityNotFoundException} → 404, so a forged id
 *       can never merge over / take over another tenant's row.</li>
 * </ul>
 *
 * <p>Pure Mockito — no Spring context, no DB. Covers {@link OtAssetService},
 * {@link CryptoAssetService}, {@link MaDueDiligenceService} and
 * {@link IamJmlEventService}; the remaining socops services share the same
 * create/update shape.
 */
@ExtendWith(MockitoExtension.class)
class SocOpsTenantSafetyTest {

    private static final Long ORG_ID  = 7L;
    private static final Long OTHER_ORG = 99L;

    @Mock private OtAssetRepository otRepo;
    @Mock private CryptoAssetRepository cryptoRepo;
    @Mock private MaDueDiligenceRepository ddRepo;
    @Mock private IamJmlEventRepository jmlRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private OtAssetService otService;
    private CryptoAssetService cryptoService;
    private MaDueDiligenceService ddService;
    private IamJmlEventService jmlService;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        otService = new OtAssetService(otRepo, audit, publisher);
        cryptoService = new CryptoAssetService(cryptoRepo, audit);
        ddService = new MaDueDiligenceService(ddRepo, audit, publisher);
        jmlService = new IamJmlEventService(jmlRepo, audit, publisher);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ─── CREATE (no id): org stamped from context, NOT the forged body org ──

    @Test void otAsset_create_stampsCallerOrgNotBodyOrg() {
        // Genuine create (no id) with a forged organisationId in the body — the
        // server must overwrite it with the caller's tenant from TenantContext.
        OtAsset in = new OtAsset();
        in.setOrganisationId(OTHER_ORG); // forged org in body — must be ignored
        in.setAssetCode("PLC-001");
        in.setTitleEn("New PLC");
        in.setPurdueLevel(2);
        in.setAssetClass("PLC");
        in.setCriticality("HIGH");
        in.setRemoteAccessEnabled(false);

        when(otRepo.save(any(OtAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        otService.save(in);

        ArgumentCaptor<OtAsset> cap = ArgumentCaptor.forClass(OtAsset.class);
        verify(otRepo).save(cap.capture());
        assertThat(cap.getValue().getId()).isNull();              // inserts, never merges
        assertThat(cap.getValue().getOrganisationId()).isEqualTo(ORG_ID); // not OTHER_ORG
        // Create path must not consult the scoped finder.
        verify(otRepo, never()).findByIdAndOrganisationId(any(), any());
    }

    @Test void cryptoAsset_create_stampsCallerOrgNotBodyOrg() {
        CryptoAsset in = new CryptoAsset();
        in.setOrganisationId(OTHER_ORG);
        in.setAssetCode("CERT-001");
        in.setAssetType("TLS");
        in.setTitleEn("New cert");
        in.setQuantumSafe(false);

        when(cryptoRepo.save(any(CryptoAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        cryptoService.save(in);

        ArgumentCaptor<CryptoAsset> cap = ArgumentCaptor.forClass(CryptoAsset.class);
        verify(cryptoRepo).save(cap.capture());
        assertThat(cap.getValue().getId()).isNull();
        assertThat(cap.getValue().getOrganisationId()).isEqualTo(ORG_ID);
        verify(cryptoRepo, never()).findByIdAndOrganisationId(any(), any());
    }

    // ─── UPDATE: row not owned by the caller's tenant → 404, never merged ─

    @Test void otAsset_update_rowNotOwnedByTenant_throwsAndNeverSaves() {
        // Body carries an id that resolves to a row in another tenant — the
        // scoped finder (id + caller's org) returns empty.
        OtAsset in = new OtAsset();
        in.setId(4242L);
        in.setAssetCode("PLC-001");
        in.setTitleEn("Attempted hijack");
        in.setPurdueLevel(2);
        in.setAssetClass("PLC");
        in.setCriticality("HIGH");
        in.setRemoteAccessEnabled(true);

        when(otRepo.findByIdAndOrganisationId(4242L, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otService.save(in))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("4242");

        // Crucially, the detached client entity is never persisted.
        verify(otRepo, never()).save(any(OtAsset.class));
    }

    @Test void cryptoAsset_update_rowNotOwnedByTenant_throwsAndNeverSaves() {
        CryptoAsset in = new CryptoAsset();
        in.setId(5151L);
        in.setAssetCode("CERT-001");
        in.setAssetType("TLS");
        in.setTitleEn("Attempted hijack");
        in.setQuantumSafe(false);

        when(cryptoRepo.findByIdAndOrganisationId(5151L, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cryptoService.save(in))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("5151");

        verify(cryptoRepo, never()).save(any(CryptoAsset.class));
    }

    // ─── MaDueDiligenceService — simple create/update save() ────────────────

    @Test void maDueDiligence_create_stampsCallerOrgNotBodyOrg() {
        MaDueDiligence in = new MaDueDiligence();
        in.setOrganisationId(OTHER_ORG); // forged org in body — must be ignored
        in.setEngagementCode("MA-001");
        in.setTitleEn("Project Falcon");
        in.setTargetEntityName("Target Co");
        in.setDealType("ACQUISITION");
        in.setPhase("PRE_LOI");

        when(ddRepo.save(any(MaDueDiligence.class))).thenAnswer(inv -> inv.getArgument(0));

        ddService.save(in);

        ArgumentCaptor<MaDueDiligence> cap = ArgumentCaptor.forClass(MaDueDiligence.class);
        verify(ddRepo).save(cap.capture());
        assertThat(cap.getValue().getId()).isNull();              // inserts, never merges
        assertThat(cap.getValue().getOrganisationId()).isEqualTo(ORG_ID); // not OTHER_ORG
        verify(ddRepo, never()).findByIdAndOrganisationId(any(), any());
    }

    @Test void maDueDiligence_update_rowNotOwnedByTenant_throwsAndNeverSaves() {
        MaDueDiligence in = new MaDueDiligence();
        in.setId(8080L);
        in.setEngagementCode("MA-001");
        in.setTitleEn("Attempted hijack");
        in.setTargetEntityName("Target Co");
        in.setDealType("ACQUISITION");
        in.setPhase("PRE_LOI");

        when(ddRepo.findByIdAndOrganisationId(8080L, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ddService.save(in))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("8080");

        verify(ddRepo, never()).save(any(MaDueDiligence.class));
    }

    @Test void maDueDiligence_update_ownedRow_copiesOntoManagedEntityNotDetachedBody() {
        // The genuinely-owned row, as it exists in the DB.
        MaDueDiligence managed = new MaDueDiligence();
        managed.setId(8080L);
        managed.setOrganisationId(ORG_ID);
        managed.setEngagementCode("MA-001");
        managed.setPhase("PRE_LOI");

        // Client body for the update carries a forged org and a mutated phase.
        MaDueDiligence in = new MaDueDiligence();
        in.setId(8080L);
        in.setOrganisationId(OTHER_ORG);  // forged org — must be ignored
        in.setEngagementCode("MA-001");
        in.setTargetEntityName("Target Co");
        in.setDealType("MERGER");
        in.setPhase("DUE_DILIGENCE");     // mutable change the caller is allowed to make

        when(ddRepo.findByIdAndOrganisationId(8080L, ORG_ID)).thenReturn(Optional.of(managed));
        when(ddRepo.save(any(MaDueDiligence.class))).thenAnswer(inv -> inv.getArgument(0));

        ddService.save(in);

        ArgumentCaptor<MaDueDiligence> cap = ArgumentCaptor.forClass(MaDueDiligence.class);
        verify(ddRepo).save(cap.capture());
        MaDueDiligence saved = cap.getValue();
        // The tenant-owned managed entity is persisted — never the detached client body.
        assertThat(saved).isSameAs(managed);
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);   // forged body org ignored
        assertThat(saved.getPhase()).isEqualTo("DUE_DILIGENCE");   // mutable field copied across
    }

    // ─── IamJmlEventService — create/update save() with domain events ───────

    @Test void iamJmlEvent_create_stampsCallerOrgNotBodyOrg() {
        IamJmlEvent in = new IamJmlEvent();
        in.setOrganisationId(OTHER_ORG); // forged org in body — must be ignored
        in.setEventCode("JML-001");
        in.setEventType("JOINER");
        in.setSubjectUsername("alice");
        in.setSourceSystem("AD");
        in.setOutcome("PENDING");
        in.setPrivileged(false);
        in.setSodViolationDetected(false);

        when(jmlRepo.save(any(IamJmlEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        jmlService.save(in);

        ArgumentCaptor<IamJmlEvent> cap = ArgumentCaptor.forClass(IamJmlEvent.class);
        verify(jmlRepo).save(cap.capture());
        assertThat(cap.getValue().getId()).isNull();
        assertThat(cap.getValue().getOrganisationId()).isEqualTo(ORG_ID);
        verify(jmlRepo, never()).findByIdAndOrganisationId(any(), any());
    }

    @Test void iamJmlEvent_update_rowNotOwnedByTenant_throwsAndNeitherSavesNorPublishes() {
        IamJmlEvent in = new IamJmlEvent();
        in.setId(3030L);
        in.setEventCode("JML-001");
        in.setEventType("LEAVER");
        in.setSubjectUsername("mallory");
        in.setSourceSystem("AD");
        in.setOutcome("COMPLETED");      // would normally fire the privileged-complete event
        in.setPrivileged(true);
        in.setSodViolationDetected(true);

        when(jmlRepo.findByIdAndOrganisationId(3030L, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jmlService.save(in))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("3030");

        verify(jmlRepo, never()).save(any(IamJmlEvent.class));
        // A rejected cross-tenant write must not leak any domain event either.
        verifyNoInteractions(publisher);
    }
}

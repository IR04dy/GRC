package com.tss.grc.module.ethics.coi.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.CampaignResponse;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.CreateCampaignRequest;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.DeclarationResponse;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.RecordRecusalRequest;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.RecusalEventResponse;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.ReviewDeclarationRequest;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.SubmitDeclarationRequest;
import com.tss.grc.module.ethics.coi.service.CoiService;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wiring tests for {@link CoiController} — Sprint 13.
 *
 * <p>The role guards declared via {@code @PreAuthorize} are enforced
 * by Spring Security at runtime; here we lock in the dispatch from
 * URL → service contract and the HTTP status codes for create paths.
 * A full @WebMvcTest with role-matrix verification is left for the
 * next slice once the security stub harness is in place for this
 * module.
 */
@ExtendWith(MockitoExtension.class)
class CoiControllerTest {

    private static final Long ORG_ID = 1L;

    @Mock private CoiService service;
    @InjectMocks private CoiController controller;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static UsernamePasswordAuthenticationToken ethicsAuth() {
        GrcUserDetails ud = new GrcUserDetails(
                42L, ORG_ID, "ethics-officer", "x", true, true,
                List.of(new SimpleGrantedAuthority("ROLE_ETHICS_OFFICER")));
        return new UsernamePasswordAuthenticationToken(ud, "x", ud.getAuthorities());
    }

    private static CampaignResponse stubCampaign(String status) {
        return new CampaignResponse(
                50L, ORG_ID, "COI-2026-ANNUAL",
                "FY2026 cycle", "دورة 2026", 2026,
                LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 28),
                status, "BOARD", 25, 8,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private static DeclarationResponse stubDeclaration(String outcome) {
        return new DeclarationResponse(
                100L, ORG_ID, 50L, 1001L, "BOD-001",
                "Ahmad Al-Otaibi", "أحمد العتيبي",
                "Board Member", "عضو", "Board", "مجلس",
                LocalDateTime.now(), false, false,
                "I attest", LocalDateTime.now(),
                null, null,
                outcome, null, null,
                List.of(),
                LocalDateTime.now(), LocalDateTime.now());
    }

    /**
     * F2-244 — this fixture already used {@code 1007L}, and that is not a coincidence: it was
     * copied from the seeded rows, where {@code user_id} 1001–1007 name nobody
     * ({@code MAX(iam.users.id)} is 8). It is kept, now declared for what it is —
     * {@code userResolved = false} with no name — so the controller path is exercised on the
     * <em>unresolvable</em> case, which is 11 of the 28 live COI rows in dev and prod. Serving a
     * blank name for these was what let the register's broken rows read as its tidiest (F2-197).
     */
    private static RecusalEventResponse stubRecusal() {
        return new RecusalEventResponse(
                500L, ORG_ID, null, 1007L,
                false, null, null,
                "BOD-2026-Q1-03", "BOD",
                LocalDateTime.now(),
                "Reason EN", "السبب", "4.2", true,
                LocalDateTime.now());
    }

    // ─── Campaigns ──────────────────────────────────────────────────

    @Test
    void createCampaign_returns201AndDispatches() {
        CreateCampaignRequest req = new CreateCampaignRequest(
                "COI-2026-ANNUAL", "FY2026 cycle", "دورة 2026",
                2026, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 28),
                "BOARD", 25);
        when(service.createCampaign(req)).thenReturn(stubCampaign("DRAFT"));

        ResponseEntity<CampaignResponse> resp = controller.createCampaign(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().status()).isEqualTo("DRAFT");
        verify(service).createCampaign(req);
    }

    @Test
    void listCampaigns_dispatchesWithOrgFromTenantContext() {
        when(service.listCampaignsByOrg(ORG_ID)).thenReturn(List.of(stubCampaign("OPEN")));

        List<CampaignResponse> out = controller.listCampaigns();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).status()).isEqualTo("OPEN");
        verify(service).listCampaignsByOrg(ORG_ID);
    }

    @Test
    void openCampaign_dispatches() {
        when(service.openCampaign(50L)).thenReturn(stubCampaign("OPEN"));
        CampaignResponse out = controller.openCampaign(50L);
        assertThat(out.status()).isEqualTo("OPEN");
        verify(service).openCampaign(50L);
    }

    @Test
    void closeCampaign_dispatches() {
        when(service.closeCampaign(50L)).thenReturn(stubCampaign("CLOSED"));
        CampaignResponse out = controller.closeCampaign(50L);
        assertThat(out.status()).isEqualTo("CLOSED");
        verify(service).closeCampaign(50L);
    }

    @Test
    void getCampaign_dispatches() {
        when(service.getCampaign(50L)).thenReturn(stubCampaign("OPEN"));
        CampaignResponse out = controller.getCampaign(50L);
        assertThat(out.id()).isEqualTo(50L);
        verify(service).getCampaign(50L);
    }

    @Test
    void listDeclarationsByCampaign_dispatches() {
        when(service.listDeclarationsByCampaign(50L))
                .thenReturn(List.of(stubDeclaration("CLEARED")));

        List<DeclarationResponse> out = controller.listDeclarationsByCampaign(50L);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).reviewOutcome()).isEqualTo("CLEARED");
        verify(service).listDeclarationsByCampaign(50L);
    }

    // ─── Declarations ───────────────────────────────────────────────

    @Test
    void submitDeclaration_returns201AndDispatches() {
        SubmitDeclarationRequest req = new SubmitDeclarationRequest(
                50L, 1001L, "BOD-001",
                "Ahmad Al-Otaibi", "أحمد العتيبي",
                "Board Member", "عضو", "Board", "مجلس",
                false, false, "I attest", List.of());
        when(service.submitDeclaration(req)).thenReturn(stubDeclaration("PENDING"));

        ResponseEntity<DeclarationResponse> resp = controller.submitDeclaration(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().reviewOutcome()).isEqualTo("PENDING");
        verify(service).submitDeclaration(req);
    }

    @Test
    void getDeclaration_dispatches() {
        when(service.getDeclaration(100L)).thenReturn(stubDeclaration("MITIGATION_REQUIRED"));
        DeclarationResponse out = controller.getDeclaration(100L);
        assertThat(out.reviewOutcome()).isEqualTo("MITIGATION_REQUIRED");
        verify(service).getDeclaration(100L);
    }

    @Test
    void reviewDeclaration_dispatchesActorUserIdFromAuthentication() {
        ReviewDeclarationRequest req = new ReviewDeclarationRequest(
                "CLEARED", "review notes EN", "ملاحظات");
        when(service.reviewDeclaration(eq(100L), eq(req), eq(42L)))
                .thenReturn(stubDeclaration("CLEARED"));

        DeclarationResponse out = controller.reviewDeclaration(100L, req, ethicsAuth());

        assertThat(out.reviewOutcome()).isEqualTo("CLEARED");
        verify(service).reviewDeclaration(eq(100L), eq(req), eq(42L));
    }

    // ─── Recusals ───────────────────────────────────────────────────

    @Test
    void recordRecusal_returns201AndDispatches() {
        RecordRecusalRequest req = new RecordRecusalRequest(
                null, 1007L, "BOD-2026-Q1-03", "BOD", null,
                "Reason EN", "السبب", "4.2", true);
        when(service.recordRecusal(req)).thenReturn(stubRecusal());

        ResponseEntity<RecusalEventResponse> resp = controller.recordRecusal(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().meetingReference()).isEqualTo("BOD-2026-Q1-03");
        verify(service).recordRecusal(req);
    }

    @Test
    void recusalsByUser_dispatches() {
        when(service.listRecusalsByUser(1007L)).thenReturn(List.of(stubRecusal()));

        List<RecusalEventResponse> out = controller.recusalsByUser(1007L);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).userId()).isEqualTo(1007L);
        verify(service).listRecusalsByUser(1007L);
    }
}

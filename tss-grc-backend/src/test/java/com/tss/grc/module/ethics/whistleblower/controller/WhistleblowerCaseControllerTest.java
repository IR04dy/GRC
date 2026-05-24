package com.tss.grc.module.ethics.whistleblower.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CaseResponse;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CaseSubmissionRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CaseSubmissionResponse;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CloseRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.PublicCaseResponse;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.PublicUpdateResponse;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.TriageRequest;
import com.tss.grc.module.ethics.whistleblower.service.WhistleblowerCaseService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wiring tests for {@link WhistleblowerCaseController} — Sprint 13.
 *
 * <p>Role guards are enforced at the proxy boundary by Spring Security
 * at runtime; here we lock the dispatch from URL → service contract
 * and verify that the public-portal endpoints don't require
 * authentication-bound state (no Authentication argument).
 */
@ExtendWith(MockitoExtension.class)
class WhistleblowerCaseControllerTest {

    private static final Long ORG_ID = 1L;

    @Mock private WhistleblowerCaseService service;
    @InjectMocks private WhistleblowerCaseController controller;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static UsernamePasswordAuthenticationToken ethicsAuth() {
        GrcUserDetails ud = new GrcUserDetails(
                42L, ORG_ID, "ethics-officer", "x", true, true,
                List.of(new SimpleGrantedAuthority("ROLE_ETHICS_OFFICER")));
        return new UsernamePasswordAuthenticationToken(ud, "x", ud.getAuthorities());
    }

    private static CaseResponse stubCase(Long id, String status) {
        return new CaseResponse(
                id, "WB-2026-001", UUID.randomUUID(),
                "PUBLIC_PORTAL", "ANONYMOUS",
                null, null, null, null, true,
                "FRAUD", "HIGH",
                "Suspected fraud", "اشتباه احتيال",
                "Narrative", "السرد",
                null, null, null, false,
                status, null, null, null, null,
                null, null, null, null, null, null,
                LocalDateTime.now(), LocalDateTime.now(), null
        );
    }

    private static CaseSubmissionResponse stubSubmissionResponse() {
        return new CaseSubmissionResponse(
                101L, "WB-2026-001", UUID.randomUUID(), "RECEIVED",
                LocalDateTime.now(),
                "Please save this tracking token.",
                "يرجى حفظ رمز التتبّع.");
    }

    @Test
    void publicSubmit_dispatchesAndReturns201() {
        when(service.submitCase(any(CaseSubmissionRequest.class))).thenReturn(stubSubmissionResponse());

        ResponseEntity<CaseSubmissionResponse> resp = controller.publicSubmit(new CaseSubmissionRequest(
                "PUBLIC_PORTAL", "ANONYMOUS",
                null, null, null, null, true,
                "FRAUD", "HIGH",
                "subject", "الموضوع", "narrative", "السرد",
                null, null, null, false));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().caseCode()).isEqualTo("WB-2026-001");
        assertThat(resp.getBody().submissionToken()).isNotNull();
        verify(service).submitCase(any(CaseSubmissionRequest.class));
    }

    @Test
    void publicStatus_byToken_dispatchesWithoutAuthArg() {
        UUID token = UUID.randomUUID();
        PublicCaseResponse expected = new PublicCaseResponse(
                "WB-2026-001", "TRIAGE", "FRAUD", "HIGH",
                "subject", "الموضوع",
                LocalDateTime.now(), null,
                List.of(new PublicUpdateResponse(
                        "TRIAGE_DECISION", "Visible", "مرئية", LocalDateTime.now(), false))
        );
        when(service.lookupByToken(token)).thenReturn(expected);

        PublicCaseResponse out = controller.publicStatus(token);

        assertThat(out.caseCode()).isEqualTo("WB-2026-001");
        assertThat(out.status()).isEqualTo("TRIAGE");
        assertThat(out.updates()).hasSize(1);
        // The signature must NOT include an Authentication param — public
        // route. Verified by dispatch success here; compile would catch.
        verify(service).lookupByToken(token);
    }

    @Test
    void triage_dispatchesActorUserIdFromAuthentication() {
        when(service.triage(eq(101L), any(TriageRequest.class), eq(42L)))
                .thenReturn(stubCase(101L, "TRIAGE"));

        controller.triage(101L,
                new TriageRequest("decision en", "قرار", "HIGH", null, null),
                ethicsAuth());

        verify(service).triage(eq(101L), any(TriageRequest.class), eq(42L));
    }

    @Test
    void close_dispatchesAndReturnsClosedCase() {
        when(service.close(eq(101L), any(CloseRequest.class), eq(42L)))
                .thenReturn(stubCase(101L, "CLOSED_REMEDIATED"));

        CaseResponse out = controller.close(101L,
                new CloseRequest("CLOSED_REMEDIATED", "remed en", "مُعالَج", 777L),
                ethicsAuth());

        assertThat(out.status()).isEqualTo("CLOSED_REMEDIATED");
        verify(service).close(eq(101L), any(CloseRequest.class), eq(42L));
    }

    @Test
    void list_filtersByStatus() {
        when(service.listByStatus("TRIAGE")).thenReturn(List.of(stubCase(1L, "TRIAGE")));
        List<CaseResponse> out = controller.list("TRIAGE", null);
        assertThat(out).hasSize(1);
        verify(service).listByStatus("TRIAGE");
    }

    @Test
    void list_filtersBySeverityWhenNoStatus() {
        when(service.listBySeverity("CRITICAL")).thenReturn(List.of(stubCase(2L, "TRIAGE")));
        List<CaseResponse> out = controller.list(null, "CRITICAL");
        assertThat(out).hasSize(1);
        verify(service).listBySeverity("CRITICAL");
    }
}

package com.tss.grc.module.privacy.subprocessor.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.LifecycleTransitionRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.MaterialChangeRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.PublicSubprocessorResponse;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorResponse;
import com.tss.grc.module.privacy.subprocessor.service.SubprocessorEventService;
import com.tss.grc.module.privacy.subprocessor.service.SubprocessorPublicListService;
import com.tss.grc.module.privacy.subprocessor.service.SubprocessorService;
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
import org.springframework.web.server.ResponseStatusException;
import com.tss.grc.security.GrcUserDetails;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SubprocessorController} — wiring smoke tests.
 *
 * <p>The {@code @PreAuthorize} role guards are enforced by Spring at
 * the proxy boundary; here we lock the dispatch from URL → service
 * contract. The {@code /public} endpoint is reached without
 * authentication (permitAll in SecurityConfig); the controller
 * happens to not annotate it, so we verify that path explicitly.
 */
@ExtendWith(MockitoExtension.class)
class SubprocessorControllerTest {

    private static final Long ORG_ID = 1L;
    private static final UUID EX = UUID.randomUUID();

    @Mock private SubprocessorService service;
    @Mock private SubprocessorEventService eventService;
    @Mock private SubprocessorPublicListService publicListService;
    @Mock private OrganisationRepository organisationRepository;
    @InjectMocks private SubprocessorController controller;

    private static Organisation activeOrg(Long id, String shortCode) {
        Organisation o = new Organisation();
        o.setId(id);
        o.setNameEn("Demo Bank");
        o.setShortCode(shortCode);
        o.setIsActive(true);
        return o;
    }

    @BeforeEach
    void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private static SubprocessorResponse stub(String code, String stage) {
        return new SubprocessorResponse(
                1L, EX, code, null,
                "Microsoft Saudi Arabia",
                "مايكروسوفت العربية السعودية",
                null,
                "SA", "Riyadh",
                null, null,
                "Cloud hosting",
                "استضافة سحابية",
                "CONTRACT",
                "GENERAL", "DPA-MS-2024",
                "KSA_RESIDENT", null, null, null, null,
                "HIGH",
                true, LocalDate.now().minusDays(30), LocalDate.now().plusYears(3),
                stage,
                null, null, null, null,
                null, null
        );
    }

    private static UsernamePasswordAuthenticationToken dpoAuth() {
        GrcUserDetails ud = new GrcUserDetails(
                99L, ORG_ID, "dpo", "x", true, true,
                List.of(new SimpleGrantedAuthority("ROLE_DPO")));
        return new UsernamePasswordAuthenticationToken(ud, "x", ud.getAuthorities());
    }

    @Test
    void list_dispatchesToService() {
        when(service.list()).thenReturn(List.of(stub("SUBP-2026-001", "APPROVED")));
        List<SubprocessorResponse> out = controller.list();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).subprocessorCode()).isEqualTo("SUBP-2026-001");
        verify(service).list();
    }

    @Test
    void create_returns201AndBody() {
        when(service.create(any(SubprocessorRequest.class))).thenReturn(stub("SUBP-2026-009", "PROPOSED"));

        SubprocessorRequest req = new SubprocessorRequest(
                null, null, "Microsoft Saudi Arabia", "مايكروسوفت", null,
                "SA", "Riyadh", null, null,
                "Cloud hosting", "استضافة سحابية", "CONTRACT", "GENERAL", null,
                "KSA_RESIDENT", null, null, null, null,
                "HIGH", true, LocalDate.now().minusDays(10), null
        );
        ResponseEntity<SubprocessorResponse> resp = controller.create(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().lifecycleStage()).isEqualTo("PROPOSED");
    }

    @Test
    void approve_dispatchesToDpoApprove() {
        when(service.dpoApprove(eq(EX), eq(99L))).thenReturn(stub("SUBP-2026-001", "APPROVED"));
        SubprocessorResponse out = controller.approve(EX, dpoAuth());
        assertThat(out.lifecycleStage()).isEqualTo("APPROVED");
        verify(service).dpoApprove(EX, 99L);
    }

    @Test
    void review_andMaterialChange_dispatchToService() {
        when(service.recordReview(eq(EX), eq(99L))).thenReturn(stub("SUBP-2026-001", "APPROVED"));
        controller.review(EX, dpoAuth());
        verify(service).recordReview(EX, 99L);

        when(service.materialChange(eq(EX), any(MaterialChangeRequest.class)))
                .thenReturn(stub("SUBP-2026-001", "UNDER_DPO_REVIEW"));
        SubprocessorResponse out = controller.materialChange(EX,
                new MaterialChangeRequest("Region change", "تغيير المنطقة"));
        assertThat(out.lifecycleStage()).isEqualTo("UNDER_DPO_REVIEW");
    }

    @Test
    void transition_dispatchesWithBody() {
        when(service.transitionLifecycle(eq(EX), any(LifecycleTransitionRequest.class)))
                .thenReturn(stub("SUBP-2026-001", "SUSPENDED"));
        SubprocessorResponse out = controller.transition(EX,
                new LifecycleTransitionRequest("SUSPENDED", "Vendor breach in adjacent tenant"));
        assertThat(out.lifecycleStage()).isEqualTo("SUSPENDED");
    }

    @Test
    void publicList_resolvesTenantByOpaqueShortCode() {
        // SEC-PRIV-003: the /public endpoint is permitAll, so the caller
        // names the tenant by its opaque short code (NOT an enumerable
        // numeric id). An active org resolves to its internal id and the
        // approved list is served.
        when(organisationRepository.findByShortCode("DEMOBANK"))
                .thenReturn(Optional.of(activeOrg(7L, "DEMOBANK")));
        when(publicListService.publicList(7L)).thenReturn(List.of(
                new PublicSubprocessorResponse(
                        "SUBP-2026-001", "Microsoft Saudi Arabia", "مايكروسوفت",
                        "SA", "Riyadh", "Cloud hosting", "استضافة سحابية", "KSA_RESIDENT")));

        List<PublicSubprocessorResponse> out = controller.publicList("DEMOBANK");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).subprocessorCode()).isEqualTo("SUBP-2026-001");
        verify(publicListService).publicList(7L);
    }

    @Test
    void publicList_rejectsMissingOrgParamWith404_andNeverDefaultsToOrg1() {
        // SEC-PRIV-003: a missing org code must 404, NOT silently fall
        // back to org 1 — that default was the enumeration vector.
        assertThatThrownBy(() -> controller.publicList(null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> controller.publicList("   "))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(publicListService, never()).publicList(any());
    }

    @Test
    void publicList_unknownOrgReturns404() {
        // An unknown short code is indistinguishable from one that has
        // not published a list — return 404, do not leak existence.
        when(organisationRepository.findByShortCode("GHOST"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.publicList("GHOST"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(publicListService, never()).publicList(any());
    }
}

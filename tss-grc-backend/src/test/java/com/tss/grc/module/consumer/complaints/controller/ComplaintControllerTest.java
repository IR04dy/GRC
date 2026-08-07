package com.tss.grc.module.consumer.complaints.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.AcknowledgeRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.AssignRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.ComplaintResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.EscalateToSamaRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.PublicTrackingResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.SubmitComplaintRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.SubmitComplaintResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.TransitionRequest;
import com.tss.grc.module.consumer.complaints.service.ComplaintService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wiring tests for {@link ComplaintController} — Sprint 13.
 *
 * <p>Asserts:
 * <ul>
 *   <li>Public endpoints dispatch without requiring a JWT-derived
 *       Authentication argument (signature check).</li>
 *   <li>Authenticated endpoints carry the role guard at the proxy
 *       level (asserted by reflection on {@code @PreAuthorize}).</li>
 *   <li>List filter routing — status / category passthrough.</li>
 *   <li>Acknowledge / transition / escalate / assign dispatch correctly.</li>
 * </ul>
 *
 * <p>Spring Security applies the role guard at runtime via the
 * generated proxy; here we use reflection to lock the
 * {@code @PreAuthorize} expressions so they can't silently widen.
 */
@ExtendWith(MockitoExtension.class)
class ComplaintControllerTest {

    private static final Long ORG_ID = 1L;

    @Mock private ComplaintService service;
    @InjectMocks private ComplaintController controller;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ──────────────────────────────────────────────────

    private static ComplaintResponse stub(Long id, String status) {
        return new ComplaintResponse(
                id, "CMP-2026-000001", UUID.randomUUID(),
                "WEB", "FRAUD_UNAUTHORIZED_TXN", null, "HIGH", status,
                "Khalid Al-Mutairi", "خالد المطيري",
                "NATIONAL_ID", "4823",
                "+966500000000", "k@example.sa", "Riyadh",
                null, "9999",
                "desc", "وصف",
                LocalDateTime.now(),
                null, null, LocalDateTime.now().plusDays(5),
                null, null, null, null, LocalDateTime.now().plusDays(20),
                null, null, null, null,
                BigDecimal.ZERO, null
        );
    }

    private static SubmitComplaintRequest publicReq() {
        return new SubmitComplaintRequest(
                "WEB", "FRAUD_UNAUTHORIZED_TXN", null, "HIGH",
                "Khalid Al-Mutairi", "خالد المطيري",
                "NATIONAL_ID", "4823",
                "+966500000000", "k@example.sa", "Riyadh",
                null, null,
                "desc", "وصف بالعربية"
        );
    }

    private static SubmitComplaintResponse stubSubmission() {
        return new SubmitComplaintResponse(
                "CMP-2026-000001", UUID.randomUUID(),
                LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusDays(20),
                "Please save this token.",
                "يرجى حفظ الرمز."
        );
    }

    // ─── Public endpoints ─────────────────────────────────────────

    @Test
    void publicSubmit_dispatchesAndReturns200() {
        when(service.submitPublicComplaint(any(SubmitComplaintRequest.class), any()))
                .thenReturn(stubSubmission());

        ResponseEntity<SubmitComplaintResponse> resp = controller.submitPublic(publicReq(), "STCB");

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().complaintNo()).isEqualTo("CMP-2026-000001");
        assertThat(resp.getBody().submissionToken()).isNotNull();
        verify(service).submitPublicComplaint(any(SubmitComplaintRequest.class), any());
    }

    @Test
    void publicTrack_dispatchesWithoutAuthArg() throws Exception {
        UUID token = UUID.randomUUID();
        PublicTrackingResponse expected = new PublicTrackingResponse(
                "CMP-2026-000001", "UNDER_INVESTIGATION", "FRAUD_UNAUTHORIZED_TXN", "HIGH",
                "K.A.", LocalDateTime.now(), null, null, null, List.of()
        );
        when(service.trackByToken(token)).thenReturn(expected);

        ResponseEntity<PublicTrackingResponse> resp = controller.trackByToken(token);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().complaintNo()).isEqualTo("CMP-2026-000001");
        verify(service).trackByToken(token);

        // The public endpoint signature must NOT take an Authentication.
        Method m = ComplaintController.class.getMethod("trackByToken", UUID.class);
        assertThat(java.util.Arrays.stream(m.getParameterTypes())
                .noneMatch(t -> t.getName().contains("Authentication"))).isTrue();
    }

    // ─── Role guard inspection (compile-locked) ───────────────────

    @Test
    void publicEndpoints_arePermitAll() throws Exception {
        // F2-83 added the ?org= short code: an anonymous caller carries no JWT, so nothing else
        // identifies the receiving tenant and this used to default to org 1.
        Method submit = ComplaintController.class.getMethod("submitPublic",
                SubmitComplaintRequest.class, String.class);
        Method track = ComplaintController.class.getMethod("trackByToken", UUID.class);

        assertThat(submit.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value())
                .contains("permitAll");
        assertThat(track.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value())
                .contains("permitAll");
    }

    /**
     * F2-145(e) slice 3 — the nine authenticated endpoints authorise by {@code CONSUMER} permission.
     *
     * <p>⚠ <b>This replaces a pin that could not have survived its own fix, and unlike the equivalents in
     * {@code sharia} and {@code openbanking} it carried no exit condition.</b> The old version looped
     * {@code getDeclaredMethods()}, skipped anything not in a name list, and asserted the expression
     * contained three role names. Two defects: it said nothing about when it should stop being true, so
     * the conversion reads as breakage rather than as progress; and <b>it was vacuous by construction</b>
     * — rename every method and the loop matches nothing, asserts nothing, and passes. That is the third
     * defect-pin of this programme and the first without the rescue sentence, which is the argument for
     * writing the exit condition into the assertion rather than trusting it to be remembered.
     *
     * <p>The tiering is population-preserving: the catalogue grants {@code CONSUMER} READ, WRITE
     * <em>and</em> APPROVE to exactly {@code {COMPLIANCE_OFFICER, CONSUMER_PROTECTION_OFFICER}}, the
     * guard's two surviving names, so no request changes outcome. {@code escalateToSama} is the single
     * APPROVE — the analogue of {@code ObController#notifySama} — and pinning it here is what stops a
     * tidy-by-verb sweep from flattening the module back.
     */
    @Test
    void authenticatedEndpoints_areGuardedByConsumerPermissionAtTheReviewedTier() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("list", "READ");
        expected.put("get", "READ");
        expected.put("listUpdates", "READ");
        expected.put("listReports", "READ");
        expected.put("acknowledge", "WRITE");
        expected.put("transition", "WRITE");
        expected.put("assign", "WRITE");
        expected.put("generateReport", "WRITE");
        expected.put("escalateToSama", "APPROVE");   // telling the regulator is the approval

        Map<String, String> actual = new TreeMap<>();
        for (Method m : ComplaintController.class.getDeclaredMethods()) {
            if (m.isSynthetic() || !expected.containsKey(m.getName())) continue;
            org.springframework.security.access.prepost.PreAuthorize ann =
                    m.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            assertThat(ann).as("Missing @PreAuthorize on " + m.getName()).isNotNull();
            actual.put(m.getName(), ann.value());
        }
        // Anti-vacuity — the flaw in the pin this replaces. A reflection read that resolves nothing
        // satisfies every per-entry assertion below.
        assertThat(actual)
                .as("all 9 authenticated endpoints must be present; a name change that empties this map "
                    + "would pass the tier assertions vacuously, which is how the previous pin worked")
                .hasSize(expected.size());

        expected.forEach((method, action) -> assertThat(actual.get(method))
                .as("%s must be guarded by CONSUMER:%s — the tiers are not uniform by verb: "
                    + "escalateToSama is the one APPROVE", method, action)
                .isEqualTo("hasPermission('CONSUMER','" + action + "')"));
    }

    // ─── Authenticated dispatch ───────────────────────────────────

    @Test
    void list_filtersByStatus_whenStatusParamProvided() {
        when(service.listByStatus("RESOLVED")).thenReturn(List.of(stub(1L, "RESOLVED")));
        ResponseEntity<List<ComplaintResponse>> resp = controller.list("RESOLVED", null);
        assertThat(resp.getBody()).hasSize(1);
        verify(service).listByStatus("RESOLVED");
    }

    @Test
    void list_filtersByCategory_whenCategoryParamProvided() {
        when(service.listByCategory("FRAUD_UNAUTHORIZED_TXN"))
                .thenReturn(List.of(stub(2L, "UNDER_INVESTIGATION")));
        ResponseEntity<List<ComplaintResponse>> resp = controller.list(null, "FRAUD_UNAUTHORIZED_TXN");
        assertThat(resp.getBody()).hasSize(1);
        verify(service).listByCategory("FRAUD_UNAUTHORIZED_TXN");
    }

    @Test
    void acknowledge_dispatchesToService() {
        when(service.acknowledge(anyLong(), any())).thenReturn(stub(11L, "ACKNOWLEDGED"));
        ResponseEntity<ComplaintResponse> resp = controller.acknowledge(11L,
                new AcknowledgeRequest("ok", "تمّ"));
        assertThat(resp.getBody().status()).isEqualTo("ACKNOWLEDGED");
        verify(service).acknowledge(anyLong(), any(AcknowledgeRequest.class));
    }

    @Test
    void transition_dispatchesToService() {
        when(service.transition(anyLong(), any(TransitionRequest.class)))
                .thenReturn(stub(12L, "RESOLVED"));
        ResponseEntity<ComplaintResponse> resp = controller.transition(12L,
                new TransitionRequest("RESOLVED", "ok", "تمّ", true,
                        "summary", "ملخص", new BigDecimal("100"), null, null));
        assertThat(resp.getBody().status()).isEqualTo("RESOLVED");
        verify(service).transition(anyLong(), any(TransitionRequest.class));
    }

    @Test
    void escalateToSama_dispatchesToService() {
        ComplaintResponse stub = stub(13L, "ESCALATED_TO_SAMA");
        when(service.escalateToSama(anyLong(), any(EscalateToSamaRequest.class))).thenReturn(stub);
        ResponseEntity<ComplaintResponse> resp = controller.escalateToSama(13L,
                new EscalateToSamaRequest("SAMA-CC-2026-0001", null, null));
        assertThat(resp.getBody().status()).isEqualTo("ESCALATED_TO_SAMA");
        verify(service).escalateToSama(anyLong(), any(EscalateToSamaRequest.class));
    }

    @Test
    void assign_dispatchesToService() {
        when(service.assign(anyLong(), any(AssignRequest.class))).thenReturn(stub(14L, "ACKNOWLEDGED"));
        ResponseEntity<ComplaintResponse> resp = controller.assign(14L,
                new AssignRequest("fraud.investigator.1"));
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        verify(service).assign(anyLong(), any(AssignRequest.class));
    }
}

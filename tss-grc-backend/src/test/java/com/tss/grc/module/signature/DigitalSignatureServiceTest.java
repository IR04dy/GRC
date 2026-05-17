package com.tss.grc.module.signature;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.signature.dto.DigitalSignatureDtos.CreateRequest;
import com.tss.grc.module.signature.dto.DigitalSignatureDtos.RejectRequest;
import com.tss.grc.module.signature.dto.DigitalSignatureDtos.SignRequest;
import com.tss.grc.module.signature.entity.DigitalSignatureTransaction;
import com.tss.grc.module.signature.repository.DigitalSignatureTransactionRepository;
import com.tss.grc.module.signature.service.DigitalSignatureService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * State machine contract for the digital-signature transaction:
 *   REQUESTED → SIGNED   (mandatory document hash)
 *   REQUESTED → REJECTED (mandatory rejection reason)
 *   REQUESTED → EXPIRED  (admin / scheduled sweep)
 *   SIGNED, REJECTED, EXPIRED → terminal
 *
 * Tenant scoping: a row from another organisation must surface as a 404
 * (EntityNotFoundException) — the repo method already filters on org id.
 */
@ExtendWith(MockitoExtension.class)
class DigitalSignatureServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long TX_ID = 42L;

    @Mock private DigitalSignatureTransactionRepository repo;
    @InjectMocks private DigitalSignatureService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static DigitalSignatureTransaction tx(String status) {
        DigitalSignatureTransaction d = new DigitalSignatureTransaction();
        d.setId(TX_ID);
        d.setOrganisationId(ORG_ID);
        d.setRelatedEntityType("risk.acceptance");
        d.setRelatedEntityId(100L);
        d.setSignerName("CRO Office");
        d.setPurpose("Risk acceptance for RSK-2026-001 above appetite");
        d.setStatus(status);
        return d;
    }

    @Test void request_creates_transaction_in_REQUESTED_state() {
        when(repo.save(any(DigitalSignatureTransaction.class)))
                .thenAnswer(inv -> {
                    DigitalSignatureTransaction d = inv.getArgument(0);
                    d.setId(TX_ID);
                    return d;
                });

        CreateRequest body = new CreateRequest(
                "risk.acceptance", 100L, "RSK-2026-001",
                7L, "CRO Office", "cro@example.sa", "RISK_MANAGER",
                "INTERNAL",
                "Risk acceptance above appetite", "اعتماد قبول المخاطر فوق الشهية",
                LocalDateTime.now().plusDays(7));

        DigitalSignatureTransaction saved = service.request(body, 1L, "admin");

        assertThat(saved.getStatus()).isEqualTo("REQUESTED");
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getExternalId()).startsWith("DSIG-");
        assertThat(saved.getRelatedEntityType()).isEqualTo("risk.acceptance");
        assertThat(saved.getInitiatorUserId()).isEqualTo(1L);
        assertThat(saved.getInitiatorName()).isEqualTo("admin");
    }

    @Test void sign_moves_REQUESTED_to_SIGNED_with_hash_and_timestamp() {
        when(repo.findByIdAndOrganisationId(TX_ID, ORG_ID)).thenReturn(Optional.of(tx("REQUESTED")));
        when(repo.save(any(DigitalSignatureTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        SignRequest body = new SignRequest("a".repeat(64), "cert:demo:001");
        DigitalSignatureTransaction signed = service.sign(TX_ID, body);

        assertThat(signed.getStatus()).isEqualTo("SIGNED");
        assertThat(signed.getSignedDocumentHash()).hasSize(64);
        assertThat(signed.getCertificateReference()).isEqualTo("cert:demo:001");
        assertThat(signed.getSignatureTimestamp()).isNotNull();
    }

    @Test void sign_refuses_when_already_terminal() {
        when(repo.findByIdAndOrganisationId(TX_ID, ORG_ID)).thenReturn(Optional.of(tx("SIGNED")));

        SignRequest body = new SignRequest("b".repeat(64), null);
        assertThatThrownBy(() -> service.sign(TX_ID, body))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void reject_requires_reason_and_terminates() {
        when(repo.findByIdAndOrganisationId(TX_ID, ORG_ID)).thenReturn(Optional.of(tx("REQUESTED")));
        when(repo.save(any(DigitalSignatureTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        RejectRequest body = new RejectRequest("Insufficient evidence — please re-submit");
        DigitalSignatureTransaction rejected = service.reject(TX_ID, body);

        assertThat(rejected.getStatus()).isEqualTo("REJECTED");
        assertThat(rejected.getRejectionReason()).isEqualTo("Insufficient evidence — please re-submit");
    }

    @Test void reject_refuses_when_not_REQUESTED() {
        when(repo.findByIdAndOrganisationId(TX_ID, ORG_ID)).thenReturn(Optional.of(tx("REJECTED")));

        RejectRequest body = new RejectRequest("Changed mind");
        assertThatThrownBy(() -> service.reject(TX_ID, body))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void expire_is_idempotent_on_terminal_states() {
        DigitalSignatureTransaction alreadySigned = tx("SIGNED");
        when(repo.findByIdAndOrganisationId(TX_ID, ORG_ID)).thenReturn(Optional.of(alreadySigned));

        DigitalSignatureTransaction result = service.expire(TX_ID);

        // Returns unchanged — no save, no exception.
        assertThat(result.getStatus()).isEqualTo("SIGNED");
    }

    @Test void expire_moves_REQUESTED_to_EXPIRED() {
        when(repo.findByIdAndOrganisationId(TX_ID, ORG_ID)).thenReturn(Optional.of(tx("REQUESTED")));
        when(repo.save(any(DigitalSignatureTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        DigitalSignatureTransaction result = service.expire(TX_ID);
        assertThat(result.getStatus()).isEqualTo("EXPIRED");
    }

    @Test void get_404s_when_id_belongs_to_other_tenant() {
        when(repo.findByIdAndOrganisationId(TX_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(TX_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }
}

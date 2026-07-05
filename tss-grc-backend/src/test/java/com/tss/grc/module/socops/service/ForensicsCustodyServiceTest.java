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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SOC-D — {@link ForensicsArtefactService} chain-of-custody ledger. A transfer
 * APPENDS an immutable row (from→to, next seq, artefact hash) and moves the
 * artefact's current-holder pointer; a new artefact seeds a CAPTURE row; a blank
 * new custodian is rejected before any ledger write.
 */
@ExtendWith(MockitoExtension.class)
class ForensicsCustodyServiceTest {

    private static final Long ORG = 1L;

    @Mock private ForensicsArtefactRepository repo;
    @Mock private ForensicsCustodyEventRepository ledger;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private ForensicsArtefactService svc;

    @BeforeEach void setTenant() { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void clearTenant() { TenantContext.clear(); }

    private static ForensicsArtefact artefact(Long id, String custodian) {
        ForensicsArtefact a = new ForensicsArtefact();
        a.setId(id);
        a.setOrganisationId(ORG);
        a.setArtefactCode("FOR-1");
        a.setArtefactType("MEMORY_DUMP");
        a.setTitleEn("t");
        a.setCapturedByHandler("Handler A");
        a.setCapturedAt(LocalDateTime.now());
        a.setCurrentCustodian(custodian);
        a.setCurrentLocation("Loc1");
        a.setSha256Hash("HASH256");
        return a;
    }

    @Test
    void transferCustody_appendsImmutableRow_andMovesPointer() {
        ForensicsArtefact a = artefact(10L, "Custodian A");
        when(repo.findByIdAndOrganisationId(10L, ORG)).thenReturn(Optional.of(a));
        when(ledger.maxSeqNo(ORG, 10L)).thenReturn(1);          // one prior row → next is 2
        when(ledger.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        svc.transferCustody(10L, "Custodian B", "Loc2", "handoff");

        ArgumentCaptor<ForensicsCustodyEvent> cap = ArgumentCaptor.forClass(ForensicsCustodyEvent.class);
        verify(ledger).save(cap.capture());
        ForensicsCustodyEvent e = cap.getValue();
        assertThat(e.getEventType()).isEqualTo("TRANSFER");
        assertThat(e.getFromCustodian()).isEqualTo("Custodian A");
        assertThat(e.getToCustodian()).isEqualTo("Custodian B");
        assertThat(e.getSeqNo()).isEqualTo(2);
        assertThat(e.getArtefactHash()).isEqualTo("HASH256");
        // pointer moved to the new holder
        assertThat(a.getCurrentCustodian()).isEqualTo("Custodian B");
        assertThat(a.getCurrentLocation()).isEqualTo("Loc2");
    }

    @Test
    void transferCustody_blankCustodian_rejectedBeforeLedgerWrite() {
        assertThatThrownBy(() -> svc.transferCustody(10L, "   ", "Loc", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(ledger, never()).save(any());
    }

    @Test
    void create_seedsCaptureRowAtSeqOne() {
        ForensicsArtefact fresh = artefact(null, null);   // new artefact, no custodian yet
        when(repo.save(any())).thenAnswer(inv -> { ForensicsArtefact x = inv.getArgument(0); x.setId(10L); return x; });
        when(ledger.maxSeqNo(eq(ORG), eq(10L))).thenReturn(0);
        when(ledger.save(any())).thenAnswer(inv -> inv.getArgument(0));

        svc.save(fresh);

        ArgumentCaptor<ForensicsCustodyEvent> cap = ArgumentCaptor.forClass(ForensicsCustodyEvent.class);
        verify(ledger).save(cap.capture());
        ForensicsCustodyEvent e = cap.getValue();
        assertThat(e.getEventType()).isEqualTo("CAPTURE");
        assertThat(e.getSeqNo()).isEqualTo(1);
        assertThat(e.getFromCustodian()).isNull();
        assertThat(e.getToCustodian()).isEqualTo("Handler A");   // falls back to capturedByHandler
    }

    @Test
    void getCustodyChain_returnsOrderedLedger() {
        ForensicsArtefact a = artefact(10L, "A");
        when(repo.findByIdAndOrganisationId(10L, ORG)).thenReturn(Optional.of(a));
        ForensicsCustodyEvent e1 = new ForensicsCustodyEvent(); e1.setSeqNo(1);
        when(ledger.findByOrganisationIdAndArtefactIdOrderBySeqNoAsc(ORG, 10L)).thenReturn(List.of(e1));

        assertThat(svc.getCustodyChain(10L)).extracting(ForensicsCustodyEvent::getSeqNo).containsExactly(1);
    }
}

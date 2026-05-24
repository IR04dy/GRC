package com.tss.grc.module.privacy.subprocessor.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorEventRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorEventResponse;
import com.tss.grc.module.privacy.subprocessor.entity.Subprocessor;
import com.tss.grc.module.privacy.subprocessor.entity.SubprocessorEvent;
import com.tss.grc.module.privacy.subprocessor.repository.SubprocessorEventRepository;
import com.tss.grc.module.privacy.subprocessor.repository.SubprocessorRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubprocessorEventServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long SUB_ID = 42L;
    private static final UUID SUB_EX = UUID.randomUUID();

    @Mock private SubprocessorRepository subRepo;
    @Mock private SubprocessorEventRepository eventRepo;
    @Mock private AuditLogService audit;

    private SubprocessorEventService service;

    @BeforeEach
    void setUp() {
        service = new SubprocessorEventService(eventRepo, subRepo, audit);
        TenantContext.setCurrentTenant(ORG_ID);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private static Subprocessor sub() {
        Subprocessor s = new Subprocessor();
        s.setId(SUB_ID);
        s.setExternalId(SUB_EX);
        s.setOrganisationId(ORG_ID);
        s.setSubprocessorCode("SUBP-2026-001");
        s.setSubProcessorLegalName("Microsoft");
        s.setCountryOfProcessing("SA");
        s.setLifecycleStage("APPROVED");
        return s;
    }

    @SuppressWarnings("unchecked")
    private static <T> org.mockito.stubbing.Answer<T> assignId(AtomicLong seq) {
        return inv -> {
            Object arg = inv.getArgument(0);
            try {
                var getId = arg.getClass().getMethod("getId");
                if (getId.invoke(arg) == null) {
                    var setId = arg.getClass().getMethod("setId", Long.class);
                    setId.invoke(arg, seq.incrementAndGet());
                }
            } catch (Exception ignored) {}
            return (T) arg;
        };
    }

    @Test
    void recordEvent_writesLedgerRow() {
        when(subRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(sub()));
        when(eventRepo.save(any(SubprocessorEvent.class))).thenAnswer(assignId(new AtomicLong(100)));

        SubprocessorEventRequest req = new SubprocessorEventRequest(
                "INCIDENT_REPORTED",
                "Vendor disclosed a sub-processor breach affecting 1.2M EU records",
                "كشف المورّد عن اختراق معالج فرعي يطال 1.2 مليون سجل في الاتحاد الأوروبي",
                true, LocalDate.now());
        SubprocessorEventResponse out = service.recordEvent(SUB_EX, req, 99L);

        assertThat(out.eventType()).isEqualTo("INCIDENT_REPORTED");
        assertThat(out.dataSubjectsNotified()).isTrue();
        ArgumentCaptor<SubprocessorEvent> cap = ArgumentCaptor.forClass(SubprocessorEvent.class);
        verify(eventRepo).save(cap.capture());
        assertThat(cap.getValue().getRecordedByUserId()).isEqualTo(99L);
        verify(audit).log(eq("EVENT_RECORDED"), eq("SubprocessorEvent"), anyLong(), anyString());
    }

    @Test
    void recordEvent_orphanSubprocessorThrows() {
        when(subRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordEvent(SUB_EX,
                new SubprocessorEventRequest("MATERIAL_CHANGE", "msg", null, null, null), 1L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listForSubprocessor_returnsEventsNewestFirst() {
        when(subRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SUB_EX))
                .thenReturn(Optional.of(sub()));
        SubprocessorEvent e1 = new SubprocessorEvent();
        e1.setId(1L); e1.setEventType("ADDED");
        SubprocessorEvent e2 = new SubprocessorEvent();
        e2.setId(2L); e2.setEventType("REVIEW_COMPLETED");
        when(eventRepo.findByOrganisationIdAndSubprocessorIdAndIsDeletedFalseOrderByEventDateDesc(
                ORG_ID, SUB_ID)).thenReturn(List.of(e2, e1));

        List<SubprocessorEventResponse> out = service.listForSubprocessor(SUB_EX);
        assertThat(out).extracting(SubprocessorEventResponse::eventType)
                .containsExactly("REVIEW_COMPLETED", "ADDED");
    }

    @Test
    void listAll_returnsEntireTenantLedger() {
        SubprocessorEvent e = new SubprocessorEvent();
        e.setId(1L); e.setEventType("ADDED");
        when(eventRepo.findByOrganisationIdAndIsDeletedFalseOrderByEventDateDesc(ORG_ID))
                .thenReturn(List.of(e));

        List<SubprocessorEventResponse> out = service.listAll();
        assertThat(out).hasSize(1);
    }
}

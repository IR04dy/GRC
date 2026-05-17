package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmCommunicationContact;
import com.tss.grc.module.bcm.cycle.repository.BcmCommunicationContactRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BcmCommunicationContactService — Sprint BCM-2 rule lock.
 *
 * <ul>
 *   <li>Create requires name + at least one contact channel (phone or email).</li>
 *   <li>escalationLevel ∈ [1,5]; availability/status enums validated.</li>
 *   <li>recordTestResult requires a valid result; FAILED publishes a
 *       BCM_CALL_TREE_FAILED domain event for the F8 engine.</li>
 *   <li>PASSED result does NOT publish the event.</li>
 *   <li>testResultStats returns counts across PASSED / FAILED / NOT_REACHED.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BcmCommunicationContactServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private BcmCommunicationContactRepository contacts;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher events;
    @InjectMocks private BcmCommunicationContactService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static BcmCommunicationContact contact() {
        BcmCommunicationContact c = new BcmCommunicationContact();
        c.setId(1L); c.setOrganisationId(ORG_ID);
        c.setName("BCM Manager");
        c.setEscalationLevel(3);
        c.setPrimaryPhone("+966-555-1111");
        c.setEmail("bcm.manager@demo");
        c.setStatus("ACTIVE");
        c.setAvailabilityStatus("AVAILABLE");
        return c;
    }

    @Test void create_requiresName() {
        BcmCommunicationContact c = contact(); c.setName(null);
        assertThatThrownBy(() -> service.create(c))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
    }

    @Test void create_requiresPhoneOrEmail() {
        BcmCommunicationContact c = contact();
        c.setPrimaryPhone(null); c.setEmail(null);
        assertThatThrownBy(() -> service.create(c))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("primary_phone");
    }

    @Test void create_escalationLevelOutOfRangeRejected() {
        BcmCommunicationContact c = contact(); c.setEscalationLevel(9);
        assertThatThrownBy(() -> service.create(c))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("escalationLevel");
    }

    @Test void create_invalidAvailabilityRejected() {
        BcmCommunicationContact c = contact(); c.setAvailabilityStatus("ON_HOLIDAY");
        assertThatThrownBy(() -> service.create(c))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("availabilityStatus");
    }

    @Test void recordTestResult_invalidValueRejected() {
        // The result enum is checked BEFORE the contact lookup, so we
        // intentionally don't stub findByIdAndOrganisationId here —
        // Mockito strict mode would flag it as an unused stubbing.
        assertThatThrownBy(() -> service.recordTestResult(1L, "MAYBE", null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("result");
    }

    @Test void recordTestResult_failedPublishesEvent() {
        BcmCommunicationContact c = contact();
        when(contacts.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        when(contacts.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordTestResult(1L, "FAILED", "no answer after 3 retries");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events, times(1)).publishEvent(ev.capture());
        DomainEvent e = ev.getValue();
        assertThat(e.triggerEvent()).isEqualTo("BCM_CALL_TREE_FAILED");
        assertThat(e.entityType()).isEqualTo("BcmCommunicationContact");
        assertThat(e.payload()).containsKey("contact_name");
        assertThat(c.getLastTestResult()).isEqualTo("FAILED");
        assertThat(c.getLastTestedDate()).isNotNull();
    }

    @Test void recordTestResult_passedDoesNotPublish() {
        BcmCommunicationContact c = contact();
        when(contacts.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        when(contacts.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordTestResult(1L, "PASSED", null);
        verify(events, never()).publishEvent(any());
        assertThat(c.getLastTestResult()).isEqualTo("PASSED");
    }

    @Test void testResultStats_aggregatesAllThreeBuckets() {
        when(contacts.countByOrganisationIdAndLastTestResult(ORG_ID, "PASSED")).thenReturn(5L);
        when(contacts.countByOrganisationIdAndLastTestResult(ORG_ID, "FAILED")).thenReturn(1L);
        when(contacts.countByOrganisationIdAndLastTestResult(ORG_ID, "NOT_REACHED")).thenReturn(2L);

        java.util.Map<String, Long> stats = service.testResultStats();
        assertThat(stats).containsEntry("passed", 5L)
                          .containsEntry("failed", 1L)
                          .containsEntry("notReached", 2L);
    }
}

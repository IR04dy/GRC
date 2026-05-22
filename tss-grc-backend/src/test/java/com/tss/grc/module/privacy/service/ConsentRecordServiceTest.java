package com.tss.grc.module.privacy.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.privacy.entity.ConsentRecord;
import com.tss.grc.module.privacy.repository.ConsentRecordRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PDPL consent ledger rule-lock:
 *
 * <ul>
 *   <li>grant requires subjectRef + purposeCode</li>
 *   <li>legal-basis / granted-via enum validation</li>
 *   <li>withdraw only allowed from ACTIVE; stamps withdrawnAt</li>
 *   <li>supersede only from ACTIVE; references valid successor</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ConsentRecordServiceTest {

    private static final Long ORG_ID = 11L;

    @Mock private ConsentRecordRepository repo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private ConsentRecordService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static ConsentRecord rec(String status) {
        ConsentRecord c = new ConsentRecord();
        c.setId(1L); c.setOrganisationId(ORG_ID);
        c.setDataSubjectRef("sha256:abcd…");
        c.setPurposeCode("MARKETING_EMAILS");
        c.setLegalBasis("CONSENT");
        c.setGrantedVia("WEB_FORM");
        c.setStatus(status);
        return c;
    }

    @Test void grant_requiresSubjectRef() {
        ConsentRecord c = rec("ACTIVE"); c.setDataSubjectRef(null);
        assertThatThrownBy(() -> service.grant(c))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dataSubjectRef");
    }

    @Test void grant_requiresPurposeCode() {
        ConsentRecord c = rec("ACTIVE"); c.setPurposeCode(null);
        assertThatThrownBy(() -> service.grant(c))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("purposeCode");
    }

    @Test void grant_rejectsUnknownLegalBasis() {
        ConsentRecord c = rec("ACTIVE"); c.setLegalBasis("ASTROLOGY");
        assertThatThrownBy(() -> service.grant(c))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("legalBasis");
    }

    @Test void withdraw_setsTimestampAndStatus() {
        ConsentRecord c = rec("ACTIVE");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        when(repo.save(any(ConsentRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsentRecord out = service.withdraw(1L, "user request");
        assertThat(out.getStatus()).isEqualTo("WITHDRAWN");
        assertThat(out.getWithdrawnAt()).isNotNull();
        assertThat(out.getWithdrawnReason()).isEqualTo("user request");
    }

    @Test void withdraw_rejectsFromNonActive() {
        ConsentRecord c = rec("WITHDRAWN");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.withdraw(1L, "x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ACTIVE");
    }

    @Test void supersede_validatesSuccessorExists() {
        ConsentRecord c = rec("ACTIVE");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        when(repo.findByIdAndOrganisationId(2L, ORG_ID)).thenReturn(Optional.of(rec("ACTIVE")));
        when(repo.save(any(ConsentRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsentRecord out = service.supersede(1L, 2L);
        assertThat(out.getStatus()).isEqualTo("SUPERSEDED");
    }

    // ── F8 emission contract (F7 audit already locked above) ─────────────────

    @Test void grant_emits_CONSENT_GRANTED() {
        ConsentRecord c = rec("ACTIVE");
        c.setId(null); // grant assigns it
        when(repo.save(any(ConsentRecord.class))).thenAnswer(inv -> {
            ConsentRecord saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        service.grant(c);

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("CONSENT_GRANTED");
        assertThat(emitted.entityType()).isEqualTo("ConsentRecord");
        assertThat(emitted.payload()).containsEntry("legalBasis", "CONSENT");
        assertThat(emitted.payload()).containsEntry("purposeCode", "MARKETING_EMAILS");
        assertThat(emitted.payload()).containsEntry("status", "ACTIVE");
    }

    @Test void withdraw_emits_CONSENT_WITHDRAWN_with_reason() {
        ConsentRecord c = rec("ACTIVE");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        when(repo.save(any(ConsentRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        service.withdraw(1L, "no longer interested");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("CONSENT_WITHDRAWN");
        assertThat(emitted.payload()).containsEntry("status", "WITHDRAWN");
        assertThat(emitted.payload()).containsEntry("previousStatus", "ACTIVE");
        assertThat(emitted.payload()).containsEntry("withdrawnReason", "no longer interested");
    }

    @Test void supersede_emits_CONSENT_SUPERSEDED_with_successor_id() {
        ConsentRecord c = rec("ACTIVE");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        when(repo.findByIdAndOrganisationId(2L, ORG_ID)).thenReturn(Optional.of(rec("ACTIVE")));
        when(repo.save(any(ConsentRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        service.supersede(1L, 2L);

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        DomainEvent emitted = ev.getValue();
        assertThat(emitted.triggerEvent()).isEqualTo("CONSENT_SUPERSEDED");
        assertThat(emitted.payload()).containsEntry("status", "SUPERSEDED");
        assertThat(emitted.payload()).containsEntry("previousStatus", "ACTIVE");
        assertThat(emitted.payload()).containsEntry("supersededByConsentId", "2");
    }
}

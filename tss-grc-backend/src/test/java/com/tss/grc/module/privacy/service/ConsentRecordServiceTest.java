package com.tss.grc.module.privacy.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.privacy.entity.ConsentRecord;
import com.tss.grc.module.privacy.repository.ConsentRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}

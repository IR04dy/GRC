package com.tss.grc.module.platform.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.entity.Issue;
import com.tss.grc.module.platform.repository.IssueRepository;
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
 * IssueService lock — minimal CRUD + state-machine + resolution rules.
 *
 * <ul>
 *   <li>create rejects missing titleEn / sourceModule</li>
 *   <li>transition enforces the legal-from-state set</li>
 *   <li>RESOLVED / CLOSED transitions require a valid resolution</li>
 *   <li>update may not bypass transition() for status changes</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    private static final Long ORG_ID = 9L;

    @Mock private IssueRepository repo;
    @Mock private AuditLogService audit;
    @InjectMocks private IssueService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Issue issue(String status) {
        Issue i = new Issue();
        i.setId(1L); i.setOrganisationId(ORG_ID);
        i.setIssueCode("ISS-2026-000001");
        i.setTitleEn("Sample");
        i.setSourceModule("audit");
        i.setSeverity("MEDIUM");
        i.setPriority("NORMAL");
        i.setStatus(status);
        return i;
    }

    @Test void create_requiresTitle() {
        Issue i = issue("OPEN"); i.setTitleEn(null);
        assertThatThrownBy(() -> service.create(i))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("titleEn");
    }

    @Test void create_requiresSourceModule() {
        Issue i = issue("OPEN"); i.setSourceModule(null);
        assertThatThrownBy(() -> service.create(i))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sourceModule");
    }

    @Test void transition_legalPath_OPENtoINPROGRESS() {
        Issue i = issue("OPEN");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(i));
        when(repo.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        Issue out = service.transition(1L, "IN_PROGRESS", null);
        assertThat(out.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test void transition_illegalPath_CLOSEDfromCLOSED_rejected() {
        Issue i = issue("CLOSED");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(i));

        assertThatThrownBy(() -> service.transition(1L, "OPEN", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Illegal transition");
    }

    @Test void transition_RESOLVED_requiresResolution() {
        Issue i = issue("IN_PROGRESS");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(i));

        assertThatThrownBy(() -> service.transition(1L, "RESOLVED", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("resolution");
    }

    @Test void transition_RESOLVED_setsResolvedAt() {
        Issue i = issue("IN_PROGRESS");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(i));
        when(repo.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        Issue out = service.transition(1L, "RESOLVED", "FIXED");
        assertThat(out.getStatus()).isEqualTo("RESOLVED");
        assertThat(out.getResolution()).isEqualTo("FIXED");
        assertThat(out.getResolvedAt()).isNotNull();
    }

    @Test void update_cannotChangeStatus() {
        Issue existing = issue("OPEN");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(existing));

        Issue patch = new Issue(); patch.setStatus("CLOSED");
        assertThatThrownBy(() -> service.update(1L, patch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("transition");
    }
}

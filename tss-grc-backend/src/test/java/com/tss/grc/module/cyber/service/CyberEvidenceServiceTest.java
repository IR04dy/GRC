package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberControl;
import com.tss.grc.module.cyber.entity.CyberEvidence;
import com.tss.grc.module.cyber.repository.CyberControlRepository;
import com.tss.grc.module.cyber.repository.CyberEvidenceRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CY-H — {@link CyberEvidenceService} input validation. A malformed evidence
 * body must be rejected with a 400 ({@link IllegalArgumentException}) before it
 * reaches the DB, never a 500 (missing title) or a silently-persisted nonsense
 * row (phantom control_id / off-lifecycle status).
 */
@ExtendWith(MockitoExtension.class)
class CyberEvidenceServiceTest {

    private static final Long ORG = 1L;

    @Mock private CyberEvidenceRepository evidenceRepo;
    @Mock private CyberControlRepository controlRepo;
    @InjectMocks private CyberEvidenceService service;

    @BeforeEach void setTenant() { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void clearTenant() { TenantContext.clear(); }

    private static CyberEvidence ev(String title, Long controlId, String status) {
        CyberEvidence e = new CyberEvidence();
        e.setTitleEn(title);
        e.setControlId(controlId);
        e.setStatus(status);
        return e;
    }

    @Test
    void save_blankTitle_rejectedBeforePersist() {
        assertThatThrownBy(() -> service.saveEvidence(ev("   ", null, "CURRENT"), ORG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("titleEn");
        verify(evidenceRepo, never()).save(any());
    }

    @Test
    void save_phantomControlId_rejectedBeforePersist() {
        when(controlRepo.findByIdAndOrganisationId(999999L, ORG)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.saveEvidence(ev("Policy PDF", 999999L, "CURRENT"), ORG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999999");
        verify(evidenceRepo, never()).save(any());
    }

    @Test
    void save_unknownStatus_rejectedBeforePersist() {
        assertThatThrownBy(() -> service.saveEvidence(ev("Policy PDF", null, "NONSENSE"), ORG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
        verify(evidenceRepo, never()).save(any());
    }

    @Test
    void save_validBody_trimsTitleAndNormalisesStatus() {
        when(controlRepo.findByIdAndOrganisationId(7L, ORG)).thenReturn(Optional.of(new CyberControl()));
        when(evidenceRepo.save(any(CyberEvidence.class))).thenAnswer(inv -> inv.getArgument(0));

        CyberEvidence saved = service.saveEvidence(ev("  Policy PDF  ", 7L, "current"), ORG);

        assertThat(saved.getTitleEn()).isEqualTo("Policy PDF");   // trimmed
        assertThat(saved.getStatus()).isEqualTo("CURRENT");        // normalised to upper-case
        assertThat(saved.getControlId()).isEqualTo(7L);
        assertThat(saved.getOrganisationId()).isEqualTo(ORG);
    }
}

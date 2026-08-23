package com.tss.grc.module.platform.regulatory.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.regulatory.entity.AuthorityDocument;
import com.tss.grc.module.platform.regulatory.entity.AuthorityDocumentCitation;
import com.tss.grc.module.platform.regulatory.repository.AuthorityDocumentCitationRepository;
import com.tss.grc.module.platform.regulatory.repository.AuthorityDocumentRepository;
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
 * AuthorityDocumentService — F1 lifecycle + create rules.
 *
 * <ul>
 *   <li>Create requires code, titleEn, family, jurisdiction, issuer.</li>
 *   <li>Duplicate (code, version) rejected.</li>
 *   <li>Lifecycle: DRAFT → ACTIVE → DEPRECATED → SUPERSEDED.</li>
 *   <li>SUPERSEDED requires supersededById; cannot self-supersede.</li>
 *   <li>Delete of ACTIVE refused.</li>
 *   <li>Citation cannot be added to a SUPERSEDED document.</li>
 *   <li>New documents default to is_placeholder = true.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuthorityDocumentServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private AuthorityDocumentRepository docs;
    @Mock private AuthorityDocumentCitationRepository citations;
    @InjectMocks private AuthorityDocumentService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static AuthorityDocument doc(String code, String status) {
        AuthorityDocument d = new AuthorityDocument();
        d.setId(1L);
        d.setOrganisationId(ORG_ID);
        d.setCode(code);
        d.setVersion("1.0");
        d.setFamily("NCA Cyber");
        d.setJurisdiction("KSA");
        d.setIssuer("NCA");
        d.setTitleEn("Essential Cybersecurity Controls");
        d.setStatus(status);
        d.setTranslationStatus("EN_ONLY");
        d.setIsPlaceholder(true);
        return d;
    }

    @Test void create_missingCodeRejected() {
        AuthorityDocument body = new AuthorityDocument();
        body.setTitleEn("x"); body.setFamily("f"); body.setJurisdiction("KSA"); body.setIssuer("X");
        assertThatThrownBy(() -> service.create(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("code");
    }

    @Test void create_duplicateCodeVersionRejected() {
        AuthorityDocument body = doc("NCA-ECC-V2", "ACTIVE");
        when(docs.findByOrganisationIdAndCodeAndVersion(ORG_ID, "NCA-ECC-V2", "1.0"))
            .thenReturn(Optional.of(body));
        assertThatThrownBy(() -> service.create(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
    }

    @Test void create_defaultsPlaceholderTrue() {
        AuthorityDocument body = doc("NEW-X", "ACTIVE");
        body.setIsPlaceholder(null);
        when(docs.findByOrganisationIdAndCodeAndVersion(any(), any(), any())).thenReturn(Optional.empty());
        when(docs.save(any())).thenAnswer(inv -> { AuthorityDocument x = inv.getArgument(0); x.setId(1L); return x; });

        AuthorityDocument saved = service.create(body);
        assertThat(saved.getIsPlaceholder()).isTrue();
    }

    @Test void transition_draftToActiveOk() {
        AuthorityDocument d = doc("X", "DRAFT");
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(d));
        when(docs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthorityDocument out = service.transition(1L, "ACTIVE", null);
        assertThat(out.getStatus()).isEqualTo("ACTIVE");
    }

    @Test void transition_supersededRequiresTarget() {
        AuthorityDocument d = doc("X", "ACTIVE");
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.transition(1L, "SUPERSEDED", null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("supersededById");
    }

    @Test void transition_cannotSupersedeSelf() {
        AuthorityDocument d = doc("X", "ACTIVE");
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.transition(1L, "SUPERSEDED", 1L))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("supersede itself");
    }

    @Test void transition_illegalSkipRejected() {
        AuthorityDocument d = doc("X", "DRAFT");
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(d));

        // F2-303 — 409, and it names ACTIVE as the only move a DRAFT instrument has, which is
        // what makes "no jumping" actionable rather than merely enforced.
        assertThatThrownBy(() -> service.transition(1L, "SUPERSEDED", 2L))
            .isInstanceOf(IllegalTransitionException.class)
            .hasMessageContaining("Illegal transition")
            .hasMessageContaining("ACTIVE");
    }

    @Test void delete_activeRefused() {
        AuthorityDocument d = doc("X", "ACTIVE");
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("ACTIVE");
    }

    @Test void createCitation_onSupersededDocRefused() {
        AuthorityDocument d = doc("X", "SUPERSEDED");
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(d));

        AuthorityDocumentCitation c = new AuthorityDocumentCitation();
        c.setCitationCode("1"); c.setTitleEn("Governance");

        assertThatThrownBy(() -> service.createCitation(1L, c))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("SUPERSEDED");
    }

    @Test void createCitation_inheritsParentPlaceholder() {
        AuthorityDocument d = doc("X", "ACTIVE");
        when(docs.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(d));
        when(citations.findByAuthorityDocumentIdAndCitationCode(1L, "1")).thenReturn(Optional.empty());
        when(citations.save(any())).thenAnswer(inv -> { AuthorityDocumentCitation x = inv.getArgument(0); x.setId(1L); return x; });

        AuthorityDocumentCitation c = new AuthorityDocumentCitation();
        c.setCitationCode("1"); c.setTitleEn("Governance");
        c.setIsPlaceholder(null);   // not set

        AuthorityDocumentCitation saved = service.createCitation(1L, c);
        assertThat(saved.getIsPlaceholder()).isTrue();    // inherited from parent doc
    }
}

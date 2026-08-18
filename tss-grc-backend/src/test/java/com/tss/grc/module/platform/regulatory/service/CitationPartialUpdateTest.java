package com.tss.grc.module.platform.regulatory.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.regulatory.entity.AuthorityDocument;
import com.tss.grc.module.platform.regulatory.entity.AuthorityDocumentCitation;
import com.tss.grc.module.platform.regulatory.repository.AuthorityDocumentCitationRepository;
import com.tss.grc.module.platform.regulatory.repository.AuthorityDocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-239 — {@code PUT /api/v1/regulatory/citations/{id}} must leave the keys it was not sent alone.
 *
 * <p>Every field here was guarded {@code if (body.getX() != null)} over an entity that INITIALISED
 * it, so the guard was true on every request and the stored value was overwritten with the
 * initialiser. The fixture below is built the way Jackson builds a request — {@code new
 * AuthorityDocumentCitation()} with only the keys the caller sent — which is the only fixture shape
 * that can see this defect. A test that sets every field first cannot.
 *
 * <p>Measured against the live register, dev = prod, 214 citations: 207 are not placeholders, 212
 * have a non-zero {@code sequence_order}, 207 are past {@code EN_ONLY}. The register's own edit form
 * sends six keys and omits exactly these three, so editing a requirement paragraph moved it to the
 * top of the regulation's tree, marked a transcribed requirement a placeholder, and recorded it
 * English-only — in the same request that carried its Arabic text.
 */
@ExtendWith(MockitoExtension.class)
class CitationPartialUpdateTest {

    private static final Long ORG = 1L;

    @Mock private AuthorityDocumentRepository docs;
    @Mock private AuthorityDocumentCitationRepository citations;
    @InjectMocks private AuthorityDocumentService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    /** A transcribed NCA ECC sub-control: real, ordered, Arabic-official. */
    private AuthorityDocumentCitation stored() {
        AuthorityDocumentCitation c = new AuthorityDocumentCitation();
        c.setId(7L);
        c.setOrganisationId(ORG);
        c.setAuthorityDocumentId(9L);
        c.setCitationCode("2-1-1");
        c.setTitleEn("Asset inventory");
        c.setTitleAr("سجل الأصول");
        c.setRequirementTextAr("يجب على الجهة الحفاظ على سجل كامل للأصول.");
        c.setSequenceOrder(300);
        c.setWeight(new BigDecimal("2.50"));
        c.setMandatory(false);
        c.setIsPlaceholder(false);
        c.setTranslationStatus("AR_OFFICIAL");
        return c;
    }

    /** Exactly what {@code RegulatoryLibraryPage} sends: six keys, none of the three below. */
    private static AuthorityDocumentCitation editFormBody() {
        AuthorityDocumentCitation b = new AuthorityDocumentCitation();
        b.setTitleEn("Asset inventory shall be maintained and reviewed");
        b.setTitleAr("يجب الحفاظ على سجل الأصول ومراجعته");
        b.setRequirementTextEn("The organisation shall maintain and review a complete asset inventory.");
        b.setRequirementTextAr("يجب على الجهة الحفاظ على سجل كامل للأصول ومراجعته.");
        b.setMandatory(false);
        b.setWeight(new BigDecimal("2.50"));
        return b;
    }

    private void stubUpdate(AuthorityDocumentCitation c) {
        when(citations.findByIdAndOrganisationId(7L, ORG)).thenReturn(Optional.of(c));
        when(citations.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("an edit that omits sequenceOrder leaves the paragraph where it is in the regulation")
    void omittedSequenceOrderIsPreserved() {
        AuthorityDocumentCitation c = stored();
        stubUpdate(c);
        AuthorityDocumentCitation saved = service.updateCitation(7L, editFormBody());
        assertThat(saved.getSequenceOrder())
            .as("the server orders citations by this column and the tree sorts by it, so a reset "
                + "moves an edited requirement to the top of the regulation")
            .isEqualTo(300);
    }

    @Test
    @DisplayName("an edit that omits isPlaceholder does not mark a transcribed requirement a placeholder")
    void omittedPlaceholderFlagIsPreserved() {
        AuthorityDocumentCitation c = stored();
        stubUpdate(c);
        assertThat(service.updateCitation(7L, editFormBody()).getIsPlaceholder()).isFalse();
    }

    @Test
    @DisplayName("an edit that omits translationStatus does not assert the Arabic text is absent")
    void omittedTranslationStatusIsPreserved() {
        AuthorityDocumentCitation c = stored();
        stubUpdate(c);
        AuthorityDocumentCitation saved = service.updateCitation(7L, editFormBody());
        assertThat(saved.getTranslationStatus())
            .as("the request that triggered the reset was CARRYING requirementTextAr, so EN_ONLY "
                + "contradicted the very edit that produced it")
            .isEqualTo("AR_OFFICIAL");
        assertThat(saved.getRequirementTextAr()).isNotBlank();
    }

    @Test
    @DisplayName("the keys the form DOES send are still applied")
    void sentKeysStillApply() {
        AuthorityDocumentCitation c = stored();
        stubUpdate(c);
        AuthorityDocumentCitation saved = service.updateCitation(7L, editFormBody());
        assertThat(saved.getTitleEn()).contains("reviewed");
        assertThat(saved.getTitleAr()).contains("ومراجعته");
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("a translation status the caller DOES send is normalised, not stored verbatim")
    void translationStatusIsNormalisedOnTheWayIn() {
        AuthorityDocumentCitation c = stored();
        stubUpdate(c);
        AuthorityDocumentCitation body = editFormBody();
        body.setTranslationStatus("ar_human_reviewed");
        assertThat(service.updateCitation(7L, body).getTranslationStatus())
            .as("the column collates case-INSENSITIVELY, so the CHECK accepts a lower-case token and "
                + "stores it verbatim, while every reader — badge, tone map, label lookup — compares "
                + "case-sensitively (F2-179)")
            .isEqualTo("AR_HUMAN_REVIEWED");
    }

    @Test
    @DisplayName("an out-of-vocabulary translation status is a 400 naming the field, not a 409 from the CHECK")
    void translationStatusOutsideTheVocabularyIsRefused() {
        AuthorityDocumentCitation c = stored();
        when(citations.findByIdAndOrganisationId(7L, ORG)).thenReturn(Optional.of(c));
        AuthorityDocumentCitation body = editFormBody();
        body.setTranslationStatus("AR_MACHINE");
        assertThatThrownBy(() -> service.updateCitation(7L, body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("translationStatus");
    }

    @Test
    @DisplayName("the create still owns every default the entity stopped initialising")
    void createStillSuppliesEveryDefault() {
        AuthorityDocument d = new AuthorityDocument();
        d.setId(9L);
        d.setOrganisationId(ORG);
        d.setStatus("ACTIVE");
        d.setIsPlaceholder(false);
        when(docs.findByIdAndOrganisationId(9L, ORG)).thenReturn(Optional.of(d));
        when(citations.findByAuthorityDocumentIdAndCitationCode(9L, "3-1")).thenReturn(Optional.empty());
        when(citations.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthorityDocumentCitation bare = new AuthorityDocumentCitation();
        bare.setCitationCode("3-1");
        bare.setTitleEn("Cryptography");
        AuthorityDocumentCitation saved = service.createCitation(9L, bare);

        // Every one of these columns is NOT NULL. If the create stops supplying one, the fix has
        // turned a silent reset into a 500 — and ddl-auto=none means no other test would notice.
        assertThat(saved.getDepth()).isZero();
        assertThat(saved.getSequenceOrder()).isZero();
        assertThat(saved.getWeight()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(saved.getMandatory()).isTrue();
        assertThat(saved.getTranslationStatus()).isEqualTo("EN_ONLY");
        assertThat(saved.getIsPlaceholder())
            .as("inherited from the parent document, which is the reasoning updateCitation used to "
                + "discard on every edit")
            .isFalse();
    }
}

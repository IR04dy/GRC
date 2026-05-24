package com.tss.grc.module.aml.sanctions.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.CheckResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.ScreenRequest;
import com.tss.grc.module.aml.sanctions.entity.SanctionsList;
import com.tss.grc.module.aml.sanctions.entity.SanctionsListEntry;
import com.tss.grc.module.aml.sanctions.entity.ScreeningCheck;
import com.tss.grc.module.aml.sanctions.entity.ScreeningHit;
import com.tss.grc.module.aml.sanctions.repository.SanctionsListEntryRepository;
import com.tss.grc.module.aml.sanctions.repository.SanctionsListRepository;
import com.tss.grc.module.aml.sanctions.repository.ScreeningCheckRepository;
import com.tss.grc.module.aml.sanctions.repository.ScreeningHitRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 13 — {@link SanctionsScreeningService} unit tests.
 *
 * <p>Covers the fuzzy-match scoring formula:
 * <ul>
 *   <li>exact normalized name → 100, EXACT_NAME</li>
 *   <li>token-sort normalized → 90,  FUZZY_NAME</li>
 *   <li>alias match           → 85,  ALIAS</li>
 *   <li>DOB boost +5, country boost +3, document boost +10</li>
 *   <li>Threshold 75 → POTENTIAL_HIT, below 75 → no hit</li>
 *   <li>No-hit run produces NO_HIT result and zero hit rows</li>
 *   <li>F8 events emitted: SANCTIONS_CHECK_PERFORMED + SANCTIONS_HIT_OPENED per hit</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SanctionsScreeningServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private ScreeningCheckRepository checkRepo;
    @Mock private ScreeningHitRepository hitRepo;
    @Mock private SanctionsListRepository listRepo;
    @Mock private SanctionsListEntryRepository entryRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private SanctionsScreeningService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new SanctionsScreeningService(checkRepo, hitRepo, listRepo, entryRepo, audit, publisher);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ────────────────────────────────────────────────────

    private static SanctionsListEntry indivEntry(Long id, String nameEn, String aliasesJson) {
        SanctionsListEntry e = new SanctionsListEntry();
        e.setId(id);
        e.setListId(1L);
        e.setEntryExternalId("E-" + id);
        e.setEntityType("INDIVIDUAL");
        e.setPrimaryNameEn(nameEn);
        e.setAliasesJson(aliasesJson);
        e.setSeverity("REVIEW_REQUIRED");
        return e;
    }

    private static SanctionsList list(String code) {
        SanctionsList l = new SanctionsList();
        l.setId(1L);
        l.setListCode(code);
        l.setIsActive(true);
        l.setOrganisationId(null);
        return l;
    }

    private static ScreenRequest manualReq(String nameEn, LocalDate dob, String country, String idDoc) {
        return new ScreenRequest(
            "CUSTOMER_ONBOARDING", "CUST-X", nameEn, null,
            dob, country, idDoc, "MANUAL", List.of("OFAC-SDN"));
    }

    // ─── score() unit tests (no DB) ──────────────────────────────────

    @Test void score_exactName_yields100() {
        var entry = indivEntry(1L, "Ali Hassan Mahmoud", null);
        var req = manualReq("Ali Hassan Mahmoud", null, null, null);
        var m = service.score(req, SanctionsScreeningService.normalise(req.subjectNameEn()),
            "", entry);
        assertThat(m).isNotNull();
        assertThat(m.score()).isEqualTo(100.0);
        assertThat(m.matchType()).isEqualTo("EXACT_NAME");
    }

    @Test void score_fuzzyTokenSort_yields90() {
        var entry = indivEntry(1L, "Mohammed Saeed Khalifa", null);
        // Reorder tokens + different case + diacritics
        var req = manualReq("Saeed Mohammed Khalifa", null, null, null);
        var m = service.score(req, SanctionsScreeningService.normalise(req.subjectNameEn()),
            "", entry);
        assertThat(m).isNotNull();
        assertThat(m.score()).isEqualTo(90.0);
        assertThat(m.matchType()).isEqualTo("FUZZY_NAME");
    }

    @Test void score_aliasMatch_yields85() {
        var entry = indivEntry(1L, "Abdul Rahman Al-Hakim",
            "[\"Abu Rahman\",\"A.R. Al-Hakim\"]");
        var req = manualReq("Abu Rahman", null, null, null);
        var m = service.score(req, SanctionsScreeningService.normalise(req.subjectNameEn()),
            "", entry);
        assertThat(m).isNotNull();
        assertThat(m.score()).isEqualTo(85.0);
        assertThat(m.matchType()).isEqualTo("ALIAS");
    }

    @Test void score_dobBoost_addsFive_promotesToComposite() {
        var entry = indivEntry(1L, "Mohammed Saeed Khalifa", null);
        entry.setDateOfBirth(LocalDate.of(1968, 9, 22));
        // Use a fuzzy-name match so base = 90, +5 dob = 95
        var req = manualReq("Saeed Mohammed Khalifa", LocalDate.of(1968, 9, 22), null, null);
        var m = service.score(req, SanctionsScreeningService.normalise(req.subjectNameEn()),
            "", entry);
        assertThat(m).isNotNull();
        assertThat(m.score()).isEqualTo(95.0);
        assertThat(m.matchType()).isEqualTo("COMPOSITE");
    }

    @Test void score_countryBoost_addsThree() {
        var entry = indivEntry(1L, "Mohammed Saeed Khalifa", null);
        entry.setCountry("LB");
        // Use a token-sort fuzzy match so base = 90; +country = 93
        var req = manualReq("Saeed Mohammed Khalifa", null, "LB", null);
        var m = service.score(req, SanctionsScreeningService.normalise(req.subjectNameEn()),
            "", entry);
        assertThat(m).isNotNull();
        assertThat(m.score()).isEqualTo(93.0);
        assertThat(m.matchType()).isEqualTo("COMPOSITE");
    }

    @Test void score_documentBoost_addsTen() {
        var entry = indivEntry(1L, "Saud Al-Qahtani", null);
        entry.setIdDocumentsJson("[{\"type\":\"national_id\",\"number\":\"1010101010\"}]");
        var req = manualReq("Saud Al-Qahtani", null, null, "1010101010");
        var m = service.score(req, SanctionsScreeningService.normalise(req.subjectNameEn()),
            "", entry);
        assertThat(m).isNotNull();
        // Exact name = 100, +10 doc, capped at 100
        assertThat(m.score()).isEqualTo(100.0);
        assertThat(m.matchType()).isEqualTo("COMPOSITE");
    }

    @Test void score_compositeFromAllBoosts_capsAt100() {
        var entry = indivEntry(1L, "Saud Al-Qahtani", null);
        entry.setDateOfBirth(LocalDate.of(1979, 3, 25));
        entry.setCountry("SA");
        entry.setIdDocumentsJson("[{\"type\":\"national_id\",\"number\":\"1010101010\"}]");
        var req = manualReq("Saud Al-Qahtani", LocalDate.of(1979, 3, 25), "SA", "1010101010");
        var m = service.score(req, SanctionsScreeningService.normalise(req.subjectNameEn()),
            "", entry);
        assertThat(m).isNotNull();
        // 100 + 5 + 3 + 10 → cap at 100
        assertThat(m.score()).isEqualTo(100.0);
        assertThat(m.matchType()).isEqualTo("COMPOSITE");
    }

    @Test void score_belowThreshold_returnsNull() {
        // Completely unrelated names → no name signal → null
        var entry = indivEntry(1L, "Random Listed Person", null);
        var req = manualReq("Totally Unrelated Customer", null, null, null);
        var m = service.score(req, SanctionsScreeningService.normalise(req.subjectNameEn()),
            "", entry);
        assertThat(m).isNull();
    }

    @Test void score_dobOnly_noNameSignal_returnsNull() {
        // DOB alone (no name match) must not trip the threshold — protects
        // against shared-DOB false positives.
        var entry = indivEntry(1L, "Completely Different Person", null);
        entry.setDateOfBirth(LocalDate.of(1979, 3, 25));
        var req = manualReq("Other Name", LocalDate.of(1979, 3, 25), null, null);
        var m = service.score(req, SanctionsScreeningService.normalise(req.subjectNameEn()),
            "", entry);
        assertThat(m).isNull();
    }

    @Test void normalise_stripsArabicDiacritics() {
        // Arabic harakat (vowels) should not block a match
        String withHarakat = "مُحَمَّد";
        String plain       = "محمد";
        assertThat(SanctionsScreeningService.normalise(withHarakat))
            .isEqualTo(SanctionsScreeningService.normalise(plain));
    }

    // ─── screen() integration-ish tests with mock repos ──────────────

    @Test void screen_noHit_persistsCheck_emitsCheckPerformed() {
        var req = manualReq("Clean Customer Person", null, null, null);
        when(listRepo.findActiveVisibleTo(ORG_ID)).thenReturn(List.of(list("OFAC-SDN")));
        when(entryRepo.findActiveByListCodesVisibleTo(any(), eqOrg()))
            .thenReturn(List.of(indivEntry(1L, "Designated Bad Person", null)));
        when(checkRepo.countByOrganisationIdAndIsDeletedFalse(ORG_ID)).thenReturn(0L);
        when(checkRepo.save(any(ScreeningCheck.class))).thenAnswer(inv -> {
            ScreeningCheck c = inv.getArgument(0);
            c.setId(500L);
            return c;
        });
        when(hitRepo.findByOrganisationIdAndCheckIdAndIsDeletedFalseOrderByMatchScoreDesc(ORG_ID, 500L))
            .thenReturn(List.of());

        CheckResponse out = service.screen(req);
        assertThat(out.result()).isEqualTo("NO_HIT");
        assertThat(out.hits()).isEmpty();
        assertThat(out.checkCode()).startsWith("SCR-");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues()).extracting(DomainEvent::triggerEvent)
            .contains("SANCTIONS_CHECK_PERFORMED");
    }

    @Test void screen_potentialHit_persistsHitsAndEmitsBothEvents() {
        var req = manualReq("Ali Hassan Mahmoud", null, null, null);
        when(listRepo.findActiveVisibleTo(ORG_ID)).thenReturn(List.of(list("OFAC-SDN")));
        when(entryRepo.findActiveByListCodesVisibleTo(any(), eqOrg())).thenReturn(List.of(
            indivEntry(11L, "Ali Hassan Mahmoud", null) // exact match → 100
        ));
        when(checkRepo.countByOrganisationIdAndIsDeletedFalse(ORG_ID)).thenReturn(0L);
        when(checkRepo.save(any(ScreeningCheck.class))).thenAnswer(inv -> {
            ScreeningCheck c = inv.getArgument(0);
            c.setId(500L);
            return c;
        });
        when(hitRepo.save(any(ScreeningHit.class))).thenAnswer(inv -> {
            ScreeningHit h = inv.getArgument(0);
            h.setId(901L);
            return h;
        });
        when(hitRepo.findByOrganisationIdAndCheckIdAndIsDeletedFalseOrderByMatchScoreDesc(ORG_ID, 500L))
            .thenReturn(List.of());
        when(entryRepo.findById(anyLong())).thenReturn(Optional.of(indivEntry(11L, "Ali Hassan Mahmoud", null)));
        when(listRepo.findById(anyLong())).thenReturn(Optional.of(list("OFAC-SDN")));

        CheckResponse out = service.screen(req);
        assertThat(out.result()).isEqualTo("POTENTIAL_HIT");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues()).extracting(DomainEvent::triggerEvent)
            .contains("SANCTIONS_CHECK_PERFORMED", "SANCTIONS_HIT_OPENED");
    }

    @Test void screen_belowThresholdEntry_dropped() {
        var req = manualReq("Some Customer Name", null, null, null);
        // The single candidate doesn't share any tokens
        when(listRepo.findActiveVisibleTo(ORG_ID)).thenReturn(List.of(list("OFAC-SDN")));
        when(entryRepo.findActiveByListCodesVisibleTo(any(), eqOrg())).thenReturn(List.of(
            indivEntry(11L, "Designated Bad Person", null)
        ));
        when(checkRepo.countByOrganisationIdAndIsDeletedFalse(ORG_ID)).thenReturn(5L);
        when(checkRepo.save(any(ScreeningCheck.class))).thenAnswer(inv -> {
            ScreeningCheck c = inv.getArgument(0);
            c.setId(600L);
            return c;
        });
        when(hitRepo.findByOrganisationIdAndCheckIdAndIsDeletedFalseOrderByMatchScoreDesc(ORG_ID, 600L))
            .thenReturn(List.of());

        CheckResponse out = service.screen(req);
        assertThat(out.result()).isEqualTo("NO_HIT");
        // SCR-2026-000006 expected on the 6th check
        assertThat(out.checkCode()).matches("SCR-\\d{4}-000006");
    }

    @Test void normalise_handlesNullAndEmpty() {
        assertThat(SanctionsScreeningService.normalise(null)).isEmpty();
        assertThat(SanctionsScreeningService.normalise("")).isEmpty();
        assertThat(SanctionsScreeningService.normalise("  ")).isEmpty();
    }

    @Test void parseAliases_extractsValuesFromJsonArray() {
        var list = SanctionsScreeningService.parseAliases("[\"alias one\", \"alias two\", \"alias three\"]");
        assertThat(list).containsExactly("alias one", "alias two", "alias three");
    }

    @Test void parseAliases_emptyOrMalformed_returnsEmpty() {
        assertThat(SanctionsScreeningService.parseAliases(null)).isEmpty();
        assertThat(SanctionsScreeningService.parseAliases("")).isEmpty();
        assertThat(SanctionsScreeningService.parseAliases("not-json")).isEmpty();
    }

    @Test void documentMatches_caseInsensitiveSubstring() {
        String idDocs = "[{\"type\":\"passport\",\"country\":\"SY\",\"number\":\"DEMO-12345\"}]";
        assertThat(SanctionsScreeningService.documentMatches("DEMO-12345", idDocs)).isTrue();
        assertThat(SanctionsScreeningService.documentMatches("demo-12345", idDocs)).isTrue();
        assertThat(SanctionsScreeningService.documentMatches("OTHER-999", idDocs)).isFalse();
        // Short strings (< 4 chars) shouldn't match noisily
        assertThat(SanctionsScreeningService.documentMatches("12", idDocs)).isFalse();
    }

    // Mockito eq() helper that compiles cleanly with diamond inference
    @SuppressWarnings("unused")
    private static Long eqOrg() { return org.mockito.ArgumentMatchers.eq(ORG_ID); }
}

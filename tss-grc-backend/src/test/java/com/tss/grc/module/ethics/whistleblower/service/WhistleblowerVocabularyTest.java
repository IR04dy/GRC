package com.tss.grc.module.ethics.whistleblower.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CaseSubmissionRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.TriageRequest;
import com.tss.grc.module.ethics.whistleblower.entity.WhistleblowerCase;
import com.tss.grc.module.ethics.whistleblower.repository.WhistleblowerCaseRepository;
import com.tss.grc.module.ethics.whistleblower.repository.WhistleblowerCaseUpdateRepository;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.tenancy.PublicPortalTenantResolver;
import com.tss.grc.support.MigrationVocabularies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-179 — the whistleblower portal's four constrained vocabularies, and the two things that went
 * wrong because they were compared case-sensitively behind a case-insensitive column.
 *
 * <p><b>This is the severe instance, and it was found by the sweep rather than by the finding.</b>
 * F2-179 was filed against {@code MlModelDriftService} (a P3: an escalation that does not fire). The
 * scoped measurement that followed — 347 closed vocabularies → 79 with an open writer → 25 also
 * compared in Java — landed on {@code submitCase}, which is <b>public and unauthenticated</b>, and
 * where the failure discloses a whistleblower's identity.
 *
 * <p><b>⚠ Why the existing suite was green over it, which is the reusable part.</b>
 * {@code WhistleblowerCaseServiceTest.submit_anonymousRequest_persistsTokenAndStripsContactFields}
 * has asserted exactly the right thing since it was written — {@code isAnonymous} true, all four
 * contact fields null. It passes because its fixture sets <b>both</b>
 * {@code reporterType = "ANONYMOUS"} <b>and</b> {@code isAnonymous = true}: the belt and the braces.
 * The defect lived in the braces alone. 🔑 <b>A fixture that satisfies a condition twice cannot
 * distinguish which half is load-bearing</b> — so the legs below deliberately supply one at a time.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhistleblowerVocabularyTest {

    private static final Long ORG_ID = 1L;

    @Mock private WhistleblowerCaseRepository caseRepo;
    @Mock private WhistleblowerCaseUpdateRepository updateRepo;
    @Mock private AuditLogService audit;
    @Mock private AuditLogRepository auditRepo;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private PublicPortalTenantResolver publicTenant;

    @InjectMocks private WhistleblowerCaseService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(publicTenant.resolve(any())).thenReturn(ORG_ID);
        when(caseRepo.countByOrganisationIdAndIsDeletedFalse(ORG_ID)).thenReturn(0L);
        when(caseRepo.save(any(WhistleblowerCase.class))).thenAnswer(inv -> {
            WhistleblowerCase c = inv.getArgument(0);
            c.setId(9_001L);
            return c;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── (a) the identity-disclosure defect ───────────────────────────────────────────

    @Test
    @DisplayName("a lower-case 'anonymous' reporter type does NOT cause the identity to be stored")
    void lowerCaseAnonymousReporterTypeIsStillAnonymous() {
        // The exact shape that failed: the reporter declares anonymity through reporterType and does
        // NOT set the isAnonymous flag. CK_wb_reporter_type accepted 'anonymous' (its collation is
        // case-insensitive), isAnonymousReporterType asked "ANONYMOUS".equals(<raw>) and got false,
        // and submitCase then persisted all four contact fields.
        WhistleblowerCase saved = submit(req("PUBLIC_PORTAL", "anonymous", null,
            "Khalid Al-Harbi", "khalid@bank.sa", "+966500000000", "EMP-77120",
            "CORRUPTION", "HIGH"));

        assertThat(saved.getIsAnonymous()).as(
            "keeping the reporter's identity out of the record is the one thing this module exists "
            + "to do, and the whole of the request that asked for it was the string 'anonymous'")
            .isTrue();
        assertThat(saved.getReporterName()).isNull();
        assertThat(saved.getReporterEmail()).isNull();
        assertThat(saved.getReporterPhone()).isNull();
        assertThat(saved.getReporterEmployeeId()).isNull();
        assertThat(saved.getReporterType()).as(
            "and the stored value is canonical, so every later reader agrees with this decision")
            .isEqualTo("ANONYMOUS");
    }

    @Test
    @DisplayName("the braces alone: reporterType ANONYMOUS with the isAnonymous flag absent")
    void reporterTypeAloneIsEnoughToBeAnonymous() {
        WhistleblowerCase saved = submit(req("PUBLIC_PORTAL", "ANONYMOUS", null,
            "Name", "e@x.sa", null, null, "FRAUD", "HIGH"));
        assertThat(saved.getIsAnonymous()).isTrue();
        assertThat(saved.getReporterName()).isNull();
    }

    @Test
    @DisplayName("the belt alone: the isAnonymous flag with no reporterType at all")
    void theFlagAloneIsStillHonoured() {
        WhistleblowerCase saved = submit(req("PUBLIC_PORTAL", null, true,
            "Name", "e@x.sa", null, null, "FRAUD", "HIGH"));
        assertThat(saved.getIsAnonymous()).isTrue();
        assertThat(saved.getReporterName()).isNull();
        assertThat(saved.getReporterType()).as("the computed default for a self-declared anonymous "
            + "submitter who did not say what they are")
            .isEqualTo("ANONYMOUS");
    }

    @Test
    @DisplayName("the fix does not over-anonymise: an identified reporter is still identified")
    void anIdentifiedReporterIsUnaffected() {
        // The failure mode of an over-eager fix: making everything anonymous would destroy the
        // module's ability to follow up with a reporter who wanted to be contacted.
        WhistleblowerCase saved = submit(req("internal_portal", "employee", false,
            "Sara Al-Otaibi", "sara@bank.sa", "+966511111111", "EMP-04421", "fraud", "high"));

        assertThat(saved.getIsAnonymous()).isFalse();
        assertThat(saved.getReporterName()).isEqualTo("Sara Al-Otaibi");
        assertThat(saved.getReporterEmail()).isEqualTo("sara@bank.sa");
        assertThat(saved.getReporterType()).isEqualTo("EMPLOYEE");
        assertThat(saved.getSubmissionChannel()).isEqualTo("INTERNAL_PORTAL");
        assertThat(saved.getCategory()).isEqualTo("FRAUD");
        assertThat(saved.getSeverity()).isEqualTo("HIGH");
    }

    // ── (b) the silent severity downgrade ────────────────────────────────────────────

    @Test
    @DisplayName("a lower-case CRITICAL severity is not silently downgraded to MEDIUM")
    void lowerCaseCriticalSeverityIsNotDowngraded() {
        WhistleblowerCase saved = submit(req("PUBLIC_PORTAL", "ANONYMOUS", true,
            null, null, null, null, "FINANCIAL_MISCONDUCT", "critical"));

        assertThat(saved.getSeverity()).as(
            "safeSeverity's switch was case-sensitive, so 'critical' fell to its default branch and "
            + "the report was filed MEDIUM — the reporter's own assessment of a financial-misconduct "
            + "allegation, discarded without a word anywhere")
            .isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("an unrecognised severity is still accepted as MEDIUM — the report must not be lost")
    void anUnrecognisedSeverityIsCoercedRatherThanRefused() {
        WhistleblowerCase saved = submit(req("PUBLIC_PORTAL", "ANONYMOUS", true,
            null, null, null, null, "SAFETY", "SEV1"));

        assertThat(saved.getSeverity()).as(
            "this behaviour is deliberately UNCHANGED. Converting it to a 400 would make submissions "
            + "that succeed today start failing, and on a whistleblowing channel a report that never "
            + "arrives is worse than one triaged at the wrong band. Whether it should be refused is a "
            + "product decision about the channel, not part of a casing fix.")
            .isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("triage re-grading normalises too — an officer's 'critical' is not reset to MEDIUM")
    void triageSeverityIsNormalised() {
        WhistleblowerCase existing = new WhistleblowerCase();
        existing.setId(9_002L);
        existing.setOrganisationId(ORG_ID);
        existing.setCaseCode("WB-2026-001");
        existing.setStatus("RECEIVED");
        existing.setSeverity("MEDIUM");
        when(caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 9_002L))
            .thenReturn(java.util.Optional.of(existing));

        service.triage(9_002L, new TriageRequest(
            "Escalating after initial review", "تصعيد بعد المراجعة الأولية",
            "critical", null, null), 5L);

        assertThat(existing.getSeverity()).isEqualTo("CRITICAL");
    }

    // ── (c) the category vocabulary ──────────────────────────────────────────────────

    @Test
    @DisplayName("a category outside the vocabulary is a 400 naming it, not a 409 about a foreign key")
    void anUnknownCategoryIsRefusedWithAUsefulMessage() {
        assertThatThrownBy(() -> submit(req("PUBLIC_PORTAL", "ANONYMOUS", true,
            null, null, null, null, "WHISTLE", "HIGH")))
            .as("before F2-180 this reached CK_wb_category and came back as a 409 saying 'either "
              + "something it refers to does not exist, or the record is still referenced elsewhere' "
              + "— a sentence about a mechanism this table does not use")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("category")
            .hasMessageContaining("WHISTLE")
            .hasMessageContaining("FRAUD");
    }

    @Test
    @DisplayName("the refusal message carries no schema — this endpoint answers anonymous callers")
    void theRefusalIsSafeForAPublicEndpoint() {
        try {
            submit(req("PUBLIC_PORTAL", "ANONYMOUS", true, null, null, null, null, "WHISTLE", "HIGH"));
            throw new AssertionError("expected a refusal");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                .doesNotContain("CK_wb_category")
                .doesNotContain("whistleblower_case")
                .doesNotContain("ethics.");
        }
    }

    // ── (d) the declared vocabularies match the migration ────────────────────────────

    @Test
    @DisplayName("all four vocabularies match the CHECK constraints they name")
    void theVocabulariesMatchTheSchema() {
        // ClosedVocabularyPinTest asserts this for every vocabulary in the platform; repeated here
        // because this class is where somebody editing these four will be looking, and a pin they do
        // not know exists does not stop them writing the wrong token first.
        assertThat(WhistleblowerCaseService.REPORTER_TYPE.tokens())
            .isEqualTo(MigrationVocabularies.valuesOf("CK_wb_reporter_type"));
        assertThat(WhistleblowerCaseService.SUBMISSION_CHANNEL.tokens())
            .isEqualTo(MigrationVocabularies.valuesOf("CK_wb_channel"));
        assertThat(WhistleblowerCaseService.CATEGORY.tokens())
            .isEqualTo(MigrationVocabularies.valuesOf("CK_wb_category"));
        assertThat(WhistleblowerCaseService.SEVERITY.tokens())
            .isEqualTo(MigrationVocabularies.valuesOf("CK_wb_severity"));

        assertThat(WhistleblowerCaseService.REPORTER_TYPE.tokens())
            .as("anti-vacuity: an empty vocabulary would satisfy every equality above at once")
            .contains(WhistleblowerCaseService.REPORTER_TYPE_ANONYMOUS)
            .hasSizeGreaterThanOrEqualTo(7);
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────

    private WhistleblowerCase submit(CaseSubmissionRequest r) {
        service.submitCase(r, null);
        ArgumentCaptor<WhistleblowerCase> captor = ArgumentCaptor.forClass(WhistleblowerCase.class);
        verify(caseRepo).save(captor.capture());
        return captor.getValue();
    }

    private static CaseSubmissionRequest req(String channel, String reporterType, Boolean anonymous,
                                            String name, String email, String phone, String empId,
                                            String category, String severity) {
        return new CaseSubmissionRequest(
            channel, reporterType, name, email, phone, empId, anonymous,
            category, severity,
            "Subject in English", "الموضوع بالعربية",
            "Narrative in English", "السرد بالعربية",
            "Parties involved", null, null, false);
    }
}

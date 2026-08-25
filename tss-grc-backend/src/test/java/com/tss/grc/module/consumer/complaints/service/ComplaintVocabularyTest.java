package com.tss.grc.module.consumer.complaints.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.SubmitComplaintRequest;
import com.tss.grc.module.consumer.complaints.entity.Complaint;
import com.tss.grc.module.consumer.complaints.entity.ComplaintAggregateReport;
import com.tss.grc.module.consumer.complaints.repository.ComplaintAggregateReportRepository;
import com.tss.grc.module.consumer.complaints.repository.ComplaintRepository;
import com.tss.grc.module.consumer.complaints.repository.ComplaintUpdateRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.tenancy.PublicPortalTenantResolver;
import com.tss.grc.support.MigrationVocabularies;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

/**
 * F2-183 — the four open vocabulary writers on the anonymous complaint intake.
 *
 * <p><b>What was wrong.</b> The whole {@code consumer} schema carried <b>zero</b> CHECK constraints,
 * so four columns were written from the request body with nothing narrowing them:
 *
 * <ul>
 *   <li>{@code channel} and {@code severity} went through case-<b>sensitive</b> {@code switch}
 *       statements whose default arm substituted {@code WEB} / {@code MEDIUM} — so
 *       {@code "email"} was stored as {@code WEB} and {@code "critical"} as {@code MEDIUM}. These are
 *       the two the register filed, and they are the {@code safe*} shape CLAUDE.md says to grep for.
 *   <li>{@code category} had <b>no check at all</b>, which the filing did not record. It is worse than
 *       the two above: arbitrary text reached the column, and {@code generateQuarterlyReport} buckets
 *       this column in a case-sensitive {@code HashMap} to pick the {@code topCategory} it reports to
 *       SAMA.
 *   <li>{@code customerIdType} likewise — nullable and decorative, but declared closed in three places.
 * </ul>
 *
 * <p>🔑 <b>Every test here asserts a behaviour a structural pin cannot see.</b>
 * {@code ClosedVocabularyPinTest} proves the Java sets equal V375's CHECKs; it cannot prove the
 * service <em>consults</em> them, which is precisely the {@code safeRole(role) { return role; }} decoy
 * shape. And {@link #aCaseVariantCategoryDoesNotSplitTheSamaReportBucket()} is the one that justifies
 * the finding's severity — the others are wrong values, that one is a wrong <em>answer</em>.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComplaintVocabularyTest {

    private static final Long ORG = 1L;

    @Mock private ComplaintRepository complaintRepo;
    @Mock private ComplaintUpdateRepository updateRepo;
    @Mock private ComplaintAggregateReportRepository reportRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private PublicPortalTenantResolver publicTenant;

    @InjectMocks private ComplaintService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── the submission path ───────────────────────────────────────────────────────────

    private static SubmitComplaintRequest submission(String channel, String category,
                                                     String severity, String idType) {
        return new SubmitComplaintRequest(
                channel, category, null, severity,
                "Fahad Al-Otaibi", "فهد العتيبي",
                idType, "1234", "+966500000000", "f@example.com", "Riyadh",
                "CARD-VISA", "9876",
                "Duplicate authorisation on a card payment.",
                "تفويض مزدوج على عملية بطاقة.");
    }

    private Complaint submitted(SubmitComplaintRequest req) {
        when(publicTenant.resolve(anyString())).thenReturn(ORG);
        when(complaintRepo.findTopByOrderByComplaintNoDesc()).thenReturn(Optional.empty());
        when(complaintRepo.existsByComplaintNo(anyString())).thenReturn(false);
        when(complaintRepo.save(any(Complaint.class))).thenAnswer(inv -> {
            Complaint c = inv.getArgument(0);
            c.setId(4242L);
            return c;
        });

        service.submitPublicComplaint(req, "tssbank");

        // atLeastOnce + the LAST captured value, because two tests below submit more than once and a
        // bare verify() would fail on the invocation count rather than on what was stored.
        ArgumentCaptor<Complaint> cap = ArgumentCaptor.forClass(Complaint.class);
        verify(complaintRepo, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        List<Complaint> saved = cap.getAllValues();
        return saved.get(saved.size() - 1);
    }

    /**
     * The two defects the register filed, stated as the payload that reproduces them. Before this
     * commit the expected column on the right was {@code WEB} and {@code MEDIUM}.
     */
    @ParameterizedTest(name = "channel \"{0}\" is stored as {1}")
    @CsvSource({
        "email,        EMAIL",
        "EMAIL,        EMAIL",
        "  branch  ,   BRANCH",
        "Sama_Referral,SAMA_REFERRAL",
        "social_media, SOCIAL_MEDIA",
    })
    void aChannelInAnyCasingLandsOnItsOwnToken(String sent, String stored) {
        assertThat(submitted(submission(sent, "CARDS_DISPUTES", "HIGH", "IQAMA")).getChannel())
                .isEqualTo(stored);
    }

    @ParameterizedTest(name = "severity \"{0}\" is stored as {1}")
    @CsvSource({
        "critical, CRITICAL",
        "CRITICAL, CRITICAL",
        "High,     HIGH",
        "  low  ,  LOW",
    })
    void aSeverityInAnyCasingLandsOnItsOwnBand(String sent, String stored) {
        assertThat(submitted(submission("WEB", "CARDS_DISPUTES", sent, "IQAMA")).getSeverity())
                .isEqualTo(stored);
    }

    @Test
    @DisplayName("an unrecognised channel or severity still coerces, because refusing loses the complaint")
    void anUnrecognisedTokenCoercesRatherThanRefusing() {
        // The line F2-179 drew on the whistleblower portal. `require` here would turn a submission
        // that succeeds today into a 400 on a SAMA-mandated anonymous intake channel.
        Complaint c = submitted(submission("carrier-pigeon", "CARDS_DISPUTES", "apocalyptic", "IQAMA"));
        assertThat(c.getChannel()).isEqualTo(ComplaintService.CHANNEL_DEFAULT);
        assertThat(c.getSeverity()).isEqualTo(ComplaintService.SEVERITY_DEFAULT);
    }

    @Test
    @DisplayName("silence keeps the documented defaults — no request that succeeds today starts failing")
    void blankValuesKeepTheDocumentedDefaults() {
        Complaint c = submitted(submission("", "CARDS_DISPUTES", null, "   "));
        assertThat(c.getChannel()).isEqualTo("WEB");
        assertThat(c.getSeverity()).isEqualTo("MEDIUM");
        assertThat(c.getCustomerIdType()).isNull();
    }

    @Test
    @DisplayName("a category outside the 13-code taxonomy becomes OTHER instead of being stored verbatim")
    void anUnknownCategoryBecomesTheTaxonomysOwnUnclassifiedCode() {
        // Before this commit the string below was stored as-is, in a NOT NULL NVARCHAR(64) with no
        // constraint, from an unauthenticated endpoint.
        assertThat(submitted(submission("WEB", "Bananas", "HIGH", "IQAMA")).getCategory())
                .isEqualTo("OTHER");
        assertThat(ComplaintService.CATEGORY.tokens()).contains(ComplaintService.CATEGORY_UNCLASSIFIED);
    }

    @Test
    @DisplayName("a category in the wrong case lands on its own code, not on OTHER")
    void aCaseVariantCategoryIsNotDemotedToOther() {
        // The distinction that matters: coercing a case-variant to OTHER would be a second defect
        // wearing the fix's clothes — the complaint would be filed as unclassifiable when the
        // submitter classified it correctly.
        assertThat(submitted(submission("WEB", "fraud_unauthorized_txn", "HIGH", null)).getCategory())
                .isEqualTo("FRAUD_UNAUTHORIZED_TXN");
    }

    @Test
    @DisplayName("an unrecognised customerIdType becomes null rather than a guessed document type")
    void anUnknownIdTypeBecomesNull() {
        assertThat(submitted(submission("WEB", "ATM_ISSUES", "LOW", "drivers-licence"))
                .getCustomerIdType()).isNull();
        assertThat(submitted(submission("WEB", "ATM_ISSUES", "LOW", "iqama"))
                .getCustomerIdType()).isEqualTo("IQAMA");
    }

    @Test
    @DisplayName("the status a submission starts at is a member of the column's vocabulary")
    void theInitialStatusIsInTheVocabulary() {
        assertThat(ComplaintService.STATUS.tokens())
                .contains(submitted(submission("WEB", "OTHER", "LOW", null)).getStatus());
    }

    // ── the SAMA-facing aggregate: the harm that makes this a P2 rather than hygiene ──

    /**
     * The sharpest instance in the finding. {@code generateQuarterlyReport} does
     * {@code byCategory.merge(c.getCategory(), 1L, Long::sum)} into a {@code HashMap}, which is
     * case-<b>sensitive</b>, and then reports the largest bucket to SAMA as {@code topCategory}.
     *
     * <p>The fixture is deliberately <b>3 versus 2</b>, not 2 versus 2. The first draft of this test
     * used a tie and failed non-deterministically: {@code max(comparingLong(getValue))} breaks a tie by
     * {@code HashMap} iteration order, so the category reported to SAMA on equal counts is arbitrary.
     * That is a real (pre-existing, separate) defect — filed as F2-186 — and a test that depends on it
     * proves nothing. With 3 versus 2 the correct answer is unambiguous, and the broken behaviour
     * splits the 3 into buckets of 2 and 1 so the rival category wins outright.
     *
     * <p>🔑 <b>A vocabulary mismatch does not always degrade to a default; over an aggregate it
     * selects a different answer</b> — the F2-182 KRI-inversion shape, one layer up.
     */
    @Test
    @DisplayName("a case-variant category does not split the SAMA report's top-category bucket")
    void aCaseVariantCategoryDoesNotSplitTheSamaReportBucket() {
        TenantContext.setCurrentTenant(ORG);

        // Three submissions of one category in three casings, plus two of a rival category. Broken:
        // {cards_disputes:1, Cards_Disputes:1, CARDS_DISPUTES:1, ATM_ISSUES:2} → ATM_ISSUES wins with
        // 2. Fixed: {CARDS_DISPUTES:3, ATM_ISSUES:2} → the category customers actually complained
        // about most, with the right count.
        List<Complaint> stored = List.of(
                stored(submitted(submission("web", "cards_disputes", "high", null))),
                stored(submitted(submission("Web", "Cards_Disputes", "High", null))),
                stored(submitted(submission("WEB", "CARDS_DISPUTES", "HIGH", null))),
                stored(submitted(submission("WEB", "ATM_ISSUES", "LOW", null))),
                stored(submitted(submission("WEB", "ATM_ISSUES", "LOW", null))));

        when(complaintRepo.findByOrganisationIdAndIsDeletedFalseOrderBySubmittedOnDesc(ORG))
                .thenReturn(stored);
        when(reportRepo.findByOrganisationIdAndReportPeriod(ORG, "2026-Q3"))
                .thenReturn(Optional.empty());
        when(reportRepo.save(any(ComplaintAggregateReport.class))).thenAnswer(i -> i.getArgument(0));

        service.generateQuarterlyReport("2026-Q3");

        ArgumentCaptor<ComplaintAggregateReport> cap =
                ArgumentCaptor.forClass(ComplaintAggregateReport.class);
        verify(reportRepo).save(cap.capture());

        assertThat(cap.getValue().getTopCategory())
                .as("with the casing bug the three CARDS_DISPUTES complaints were three buckets of 1 "
                  + "and ATM_ISSUES won with 2 — the regulator being told the wrong category is the "
                  + "harm, not the stored string")
                .isEqualTo("CARDS_DISPUTES");
        assertThat(cap.getValue().getTopCategoryCount())
                .as("all three must land in one bucket; anything less means a case-variant was still "
                  + "being counted as a category of its own")
                .isEqualTo(3);
    }

    /**
     * The aggregate now scopes to the report period's quarter (F2-368), so the fixture pins
     * {@code submittedOn} to a fixed instant inside 2026-Q3 rather than leaving it at the service's
     * {@code now()} — otherwise this test would pass only while run within that quarter.
     */
    private static final LocalDateTime IN_Q3_2026 = LocalDateTime.of(2026, 8, 15, 10, 0);

    private static Complaint stored(Complaint c) {
        c.setSubmittedOn(IN_Q3_2026);
        return c;
    }

    /** A complaint received at a chosen instant, built directly (no now()-stamping submit path). */
    private static Complaint received(LocalDateTime submittedOn, String category) {
        Complaint c = new Complaint();
        c.setOrganisationId(ORG);
        c.setCategory(category);
        c.setStatus("RECEIVED");
        c.setSubmittedOn(submittedOn);
        return c;
    }

    // ── F2-368: the quarterly report must cover its quarter ──────────────────────────

    @Test
    @DisplayName("F2-368: the quarterly report counts only complaints RECEIVED in that quarter")
    void theQuarterlyReportIsScopedToItsQuarter() {
        TenantContext.setCurrentTenant(ORG);
        // Two complaints in Q3-2026 (incl. the last instant before the quarter ends) and three
        // outside it: the first instant of Q4, a later Q4 one, and the last instant of Q2. The
        // Q3 report must count exactly 2 — the missing filter counted all 5.
        List<Complaint> all = List.of(
                received(LocalDateTime.of(2026, 7, 5, 9, 0), "CARDS_DISPUTES"),
                received(LocalDateTime.of(2026, 9, 30, 23, 0), "ATM_ISSUES"),
                received(LocalDateTime.of(2026, 10, 1, 0, 0), "FEES_CHARGES"),    // Q4 — endExclusive
                received(LocalDateTime.of(2026, 11, 15, 9, 0), "FEES_CHARGES"),   // Q4
                received(LocalDateTime.of(2026, 6, 30, 23, 0), "LOAN_FINANCING"));// Q2 — before start
        when(complaintRepo.findByOrganisationIdAndIsDeletedFalseOrderBySubmittedOnDesc(ORG))
                .thenReturn(all);
        when(reportRepo.findByOrganisationIdAndReportPeriod(ORG, "Q3-2026")).thenReturn(Optional.empty());
        when(reportRepo.save(any(ComplaintAggregateReport.class))).thenAnswer(i -> i.getArgument(0));

        service.generateQuarterlyReport("Q3-2026");

        ArgumentCaptor<ComplaintAggregateReport> cap =
                ArgumentCaptor.forClass(ComplaintAggregateReport.class);
        verify(reportRepo).save(cap.capture());
        assertThat(cap.getValue().getTotalReceived())
                .as("only complaints received within Q3-2026 [Jul 1 00:00, Oct 1 00:00) count")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("F2-368: both period spellings (Qn-YYYY and YYYY-Qn) resolve to the same half-open window")
    void bothPeriodSpellingsResolveToTheSameWindow() {
        ComplaintService.QuarterWindow a = ComplaintService.quarterWindow("Q3-2026");
        ComplaintService.QuarterWindow b = ComplaintService.quarterWindow("2026-Q3");
        assertThat(a.start()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(a.endExclusive()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThat(b).isEqualTo(a);
        assertThat(a.contains(LocalDateTime.of(2026, 9, 30, 23, 59))).isTrue();
        assertThat(a.contains(LocalDateTime.of(2026, 10, 1, 0, 0))).isFalse();
        assertThat(a.contains(null)).isFalse();
    }

    @Test
    @DisplayName("F2-368: a period that names no quarter is refused, not silently reported all-time")
    void aMalformedPeriodIsRefused() {
        assertThatThrownBy(() -> ComplaintService.quarterWindow("2026"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Qn-YYYY");
        assertThatThrownBy(() -> ComplaintService.quarterWindow("Q5-2026"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComplaintService.quarterWindow(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── the two lists that must not drift ────────────────────────────────────────────

    /**
     * The state machine and the column are two independent declarations of one vocabulary, and until
     * V375 the column had none — so nothing could have caught a divergence.
     *
     * <p>🔑 F2-177's lesson, applied before it costs anything: <b>what made the old arrangement
     * harmless was two lists happening to agree, and nobody was maintaining that.</b>
     */
    @Test
    @DisplayName("every state the machine names is a state the column accepts, and vice versa")
    void theStateMachineAndTheColumnAgreeInBothDirections() {
        Set<String> machine = new LinkedHashSet<>(ComplaintService.ALLOWED.keySet());
        ComplaintService.ALLOWED.values().forEach(machine::addAll);

        assertThat(new TreeSet<>(machine))
                .as("a state reachable in the machine but absent from CK_complaint_status is a "
                  + "transition that will 500 on the UPDATE; a state in the column that the machine "
                  + "never names is dead vocabulary. Both are drift.")
                .isEqualTo(new TreeSet<>(ComplaintService.STATUS.tokens()));
    }

    @Test
    @DisplayName("the timeline kinds the service writes are members of CK_cmpl_update_type")
    void everyUpdateTypeTheServiceWritesIsInTheVocabulary() {
        assertThat(ComplaintService.UPDATE_TYPE.tokens()).contains(
                ComplaintService.UPDATE_STATUS_CHANGE,
                ComplaintService.UPDATE_SAMA_NOTIFICATION,
                ComplaintService.UPDATE_INTERNAL_NOTE);
    }

    /**
     * V375 writes the status vocabulary three times — once per column that holds a status — and only
     * {@code CK_complaint_status} is named by a {@link com.tss.grc.common.validation.ClosedVocabulary},
     * so the pin cannot see the other two. Same for the category vocabulary on the report's
     * {@code top_category}. Without this, the timeline could accept a status the complaint cannot hold.
     */
    @Test
    @DisplayName("the repeated status and category vocabularies in V375 agree with the Java ones")
    void theConstraintsThatShareAVocabularyAgreeWithIt() {
        assertThat(MigrationVocabularies.valuesOf("CK_cmpl_update_from_status"))
                .isEqualTo(ComplaintService.STATUS.tokens());
        assertThat(MigrationVocabularies.valuesOf("CK_cmpl_update_to_status"))
                .isEqualTo(ComplaintService.STATUS.tokens());
        assertThat(MigrationVocabularies.valuesOf("CK_cmpl_report_top_category"))
                .as("top_category is derived from complaint.category by generateQuarterlyReport, so a "
                  + "category the complaint accepts and the report refuses would 500 the SAMA report")
                .isEqualTo(ComplaintService.CATEGORY.tokens());
    }

    /**
     * The columns this slice deliberately left open, asserted as a decision rather than left as an
     * absence. F2-184's lesson: a tolerated entry must carry its own reason, or the next reader cannot
     * tell a reviewed omission from an overlooked one.
     */
    @Test
    @DisplayName("subcategory and rootCauseCode are deliberately unconstrained — no vocabulary exists")
    void theTwoUnconstrainedColumnsHaveNoDeclaredVocabularyToMirror() {
        // If someone declares a vocabulary for either, this fails and points them at V375 — which is
        // the correct order: the Java set and the CHECK land together or neither does.
        assertThat(java.util.Arrays.stream(ComplaintService.class.getDeclaredFields())
                        .map(java.lang.reflect.Field::getName)
                        .filter(n -> n.contains("SUBCATEGORY") || n.contains("ROOT_CAUSE"))
                        .toList())
                .as("`subcategory` and `root_cause_code` carry no vocabulary in the entity, in V199's "
                  + "header or in lib/complaints.ts — dev holds 5 distinct free-text root causes. "
                  + "Constraining them is a product decision, not this finding's fix. If you are "
                  + "adding one, add the CHECK to a new migration in the same commit.")
                .isEmpty();
    }
}

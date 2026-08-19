package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.entity.AmlAlert;
import com.tss.grc.module.ethics.entity.KycSubject;
import com.tss.grc.module.ethics.repository.AmlAlertRepository;
import com.tss.grc.module.ethics.repository.KycSubjectRepository;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.iam.service.TenantUserDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-250 — an AML alert's subject reference, and the two controls the entity-bound PUT used to
 * be able to perform.
 *
 * <h2>The defect</h2>
 *
 * <p>{@code AmlAlertService} had one blanket {@code apply()} that copied every column a caller
 * sent, shared by {@code create} and {@code update}. Three consequences, all probed live as
 * {@code auditor} (COMPLIANCE:WRITE — the permission on every one of these routes, so there was
 * no escalation to find: the object-level controls were simply the only ones, and this route sat
 * beside them):
 *
 * <ol>
 *   <li>{@code subject_id} and {@code subject_name} were two independent null-guarded copies, so
 *       an id naming one KYC subject could be stored beside a label naming another, and another
 *       tenant's subject was storable because {@code FK_aml_subject} knows nothing about tenancy.</li>
 *   <li><b>It re-opened F2-249's fix</b>: {@code analystName} was writable and {@code analystId}
 *       was not, so a PUT could leave the id naming user 4 and change the label to somebody who
 *       was never assigned — <i>worse</i> than before F2-249, because now there is an id for the
 *       label to contradict.</li>
 *   <li>It bypassed the state machine: {@code status} was writable, so a create or a PUT could
 *       file a Suspicious Transaction Report without passing {@code /transition}.</li>
 * </ol>
 *
 * <p>⚙ Both directories are REAL over mocked repositories (F2-237): a mocked resolver returns
 * empty, and every refusal below would then pass while asserting nothing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmlSubjectReferenceTest {

    private static final Long ORG = 1L;
    private static final Long ALERT = 900L;
    private static final Long SUBJECT = 7L;
    private static final Long FOREIGN = 88L;
    private static final String SUBJECT_NAME = "BlueWave Trading Co. (KSA)";

    @Mock AmlAlertRepository repo;
    @Mock UserRepository users;
    @Mock KycSubjectRepository subjectRepo;

    AmlAlertService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        service = new AmlAlertService(repo, new TenantUserDirectory(users),
                new KycSubjectDirectory(subjectRepo));
        when(repo.save(any(AmlAlert.class))).thenAnswer(i -> i.getArgument(0));
        when(subjectRepo.findById(SUBJECT)).thenReturn(Optional.of(subject(SUBJECT, ORG, SUBJECT_NAME)));
        when(subjectRepo.findById(FOREIGN)).thenReturn(Optional.of(subject(FOREIGN, 2L, "Other Tenant Co.")));
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private static KycSubject subject(Long id, Long org, String name) {
        KycSubject s = new KycSubject();
        s.setId(id);
        s.setOrganisationId(org);
        s.setFullName(name);
        s.setSubjectCode("KYC-LE-0001");
        return s;
    }

    private static AmlAlert request() {
        AmlAlert a = new AmlAlert();
        a.setAlertType("TRANSACTION_MONITORING");
        a.setTitleEn("Structuring pattern");
        a.setDescription("Multiple sub-threshold transfers");
        return a;
    }

    private AmlAlert stored(String status, Long subjectId, String subjectName) {
        AmlAlert a = request();
        a.setId(ALERT);
        a.setOrganisationId(ORG);
        a.setStatus(status);
        a.setSubjectId(subjectId);
        a.setSubjectName(subjectName);
        when(repo.findById(ALERT)).thenReturn(Optional.of(a));
        return a;
    }

    // ── the subject reference ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an id resolves in-tenant and the label is DERIVED from what resolved")
    void anIdIsResolvedAndTheLabelDerived() {
        AmlAlert req = request();
        req.setSubjectId(SUBJECT);

        AmlAlert saved = service.create(req);

        assertThat(saved.getSubjectId()).isEqualTo(SUBJECT);
        assertThat(saved.getSubjectName())
                .as("the label must come from the row the id names — otherwise the two halves can "
                  + "describe different parties and the half on screen is the one with no vote "
                  + "(F2-245)")
                .isEqualTo(SUBJECT_NAME);
    }

    @Test
    @DisplayName("a label with no id is accepted and recorded UNLINKED")
    void aLabelOnlySubjectIsAcceptedAndLeftUnlinked() {
        AmlAlert req = request();
        req.setSubjectName("A counterparty nobody has onboarded");

        AmlAlert saved = service.create(req);

        assertThat(saved.getSubjectName()).isEqualTo("A counterparty nobody has onboarded");
        assertThat(saved.getSubjectId())
                .as("the schema keeps subject_id nullable beside a nullable subject_name, which is "
                  + "it stating that an unregistered party is representable (F2-246) — and all 8 "
                  + "label-only rows in dev and prod are that shape, so refusing would break every "
                  + "request that works today (F2-182)")
                .isNull();
    }

    @Test
    @DisplayName("a label contradicting the id is refused; an echo of the derived label is fine")
    void anEchoIsFineAndAContradictionIsNot() {
        AmlAlert echo = request();
        echo.setSubjectId(SUBJECT);
        echo.setSubjectName(SUBJECT_NAME);
        assertThat(service.create(echo).getSubjectId()).isEqualTo(SUBJECT);

        AmlAlert lying = request();
        lying.setSubjectId(SUBJECT);
        lying.setSubjectName("Some Entirely Different Company");
        assertThatThrownBy(() -> service.create(lying))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not name KYC subject");
    }

    @Test
    @DisplayName("the stored label is the DERIVED spelling, never the caller's casing")
    void theDerivedSpellingWins() {
        AmlAlert req = request();
        req.setSubjectId(SUBJECT);
        req.setSubjectName(SUBJECT_NAME.toLowerCase());

        // The echo comparison is case-INSENSITIVE because the column collates that way (F2-179),
        // so without this leg a mutation storing the caller's label would be unobservable: with
        // the contradiction check in place, the only label that reaches the setter IS an echo.
        assertThat(service.create(req).getSubjectName()).isEqualTo(SUBJECT_NAME);
    }

    @Test
    @DisplayName("another tenant's KYC subject cannot become the alert's subject")
    void aForeignTenantSubjectIsRefused() {
        AmlAlert req = request();
        req.setSubjectId(FOREIGN);

        assertThatThrownBy(() -> service.create(req))
                .as("FK_aml_subject refuses a NONEXISTENT id and a foreign key knows nothing about "
                  + "tenancy, so the constraint that looks like it protects the reference cannot "
                  + "(F2-242/F2-243)")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a subject in this organisation");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("editing only the label of an already-linked alert is refused, not silently unlinked")
    void aLabelEditOnALinkedAlertIsRefused() {
        stored("OPEN", SUBJECT, SUBJECT_NAME);
        AmlAlert patch = new AmlAlert();
        patch.setSubjectName("Somebody Else Entirely");

        assertThatThrownBy(() -> service.update(ALERT, patch))
                .as("an entity binding cannot tell subjectId:null from an absent key, so 'unlink' "
                  + "is not expressible here — treating a label edit as an unlink would quietly "
                  + "undo a resolved reference, which is the defect rather than the fix")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linked to KYC subject");
    }

    @Test
    @DisplayName("an unlinked alert's label stays editable, and an echo on a linked one is a no-op")
    void theRoutesThatMustKeepWorkingKeepWorking() {
        stored("OPEN", null, "Old label");
        AmlAlert rename = new AmlAlert();
        rename.setSubjectName("Corrected party name");
        assertThat(service.update(ALERT, rename).getSubjectName()).isEqualTo("Corrected party name");

        stored("OPEN", SUBJECT, SUBJECT_NAME);
        AmlAlert roundTrip = new AmlAlert();
        roundTrip.setSubjectId(SUBJECT);
        roundTrip.setSubjectName(SUBJECT_NAME);
        roundTrip.setSeverity("CRITICAL");
        AmlAlert saved = service.update(ALERT, roundTrip);
        assertThat(saved.getSubjectId()).isEqualTo(SUBJECT);
        assertThat(saved.getSeverity()).isEqualTo("CRITICAL");
    }

    // ── the two controls the PUT could perform ─────────────────────────────────────────────────

    @Test
    @DisplayName("a create lands in the machine's initial state and may not name another")
    void aCreateCannotNameAState() {
        assertThat(service.create(request()).getStatus())
                .isEqualTo(AmlAlertService.INITIAL_STATUS);

        AmlAlert filed = request();
        filed.setStatus("STR_FILED");
        assertThatThrownBy(() -> service.create(filed))
                .as("filing a Suspicious Transaction Report outright skips OPEN -> INVESTIGATING "
                  + "and every disposition the machine demands")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has to be reached through");

        AmlAlert echoed = request();
        echoed.setStatus(AmlAlertService.INITIAL_STATUS);
        assertThat(service.create(echoed).getStatus()).isEqualTo(AmlAlertService.INITIAL_STATUS);
    }

    @Test
    @DisplayName("the PUT may not change the status, and an echo keeps a whole-row form working")
    void theUpdateCannotPerformATransition() {
        stored("INVESTIGATING", null, null);
        AmlAlert jump = new AmlAlert();
        jump.setStatus("STR_FILED");
        assertThatThrownBy(() -> service.update(ALERT, jump))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/{id}/transition");

        stored("INVESTIGATING", null, null);
        AmlAlert echo = new AmlAlert();
        echo.setStatus("investigating");          // the collation is case-insensitive
        echo.setSeverity("LOW");
        assertThat(service.update(ALERT, echo).getStatus()).isEqualTo("INVESTIGATING");
    }

    @Test
    @DisplayName("the PUT may not change the recorded analyst — F2-249's fix, closed at this route too")
    void theUpdateCannotReopenTheAssignment() {
        AmlAlert row = stored("INVESTIGATING", null, null);
        row.setAnalystId(4L);
        row.setAnalystName("Fahad Al-Otaibi");

        AmlAlert forge = new AmlAlert();
        forge.setAnalystName("Somebody Who Was Never Assigned");
        assertThatThrownBy(() -> service.update(ALERT, forge))
                .as("the label was writable and the id was not, so this left an id naming user 4 "
                  + "beside a label naming somebody else — worse than pre-F2-249, because now "
                  + "there is an id for the label to contradict")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/{id}/assign");

        AmlAlert echo = new AmlAlert();
        echo.setAnalystName("Fahad Al-Otaibi");
        assertThat(service.update(ALERT, echo).getAnalystName()).isEqualTo("Fahad Al-Otaibi");
    }

    @Test
    @DisplayName("a create may not name an analyst at all — nothing has assigned one yet")
    void aCreateCannotNameAnAnalyst() {
        AmlAlert req = request();
        req.setAnalystName("Pre-assigned Analyst");
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nothing has set it on this record yet");
    }

    /**
     * The initial state is DERIVED from the machine, not typed a second time (F2-196/F2-217).
     * Asserted because a literal would drift from the machine silently, and because the
     * derivation self-checks: this is the assertion that the self-check resolves at all.
     */
    @Test
    @DisplayName("the initial state is the machine's only root, derived rather than declared twice")
    void theInitialStateIsDerivedFromTheMachine() {
        assertThat(AmlAlertService.INITIAL_STATUS).isEqualTo("OPEN");
        assertThat(AmlAlertService.TRANSITIONS.get(AmlAlertService.INITIAL_STATUS))
                .as("the root must have somewhere to go, or no alert could ever be worked")
                .isNotEmpty();
        assertThat(AmlAlertService.TRANSITIONS.values().stream()
                        .noneMatch(t -> t.contains(AmlAlertService.INITIAL_STATUS)))
                .as("and nothing may transition back into it")
                .isTrue();
    }
}

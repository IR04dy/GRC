package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.dto.AssignAnalystRequest;
import com.tss.grc.module.ethics.entity.AmlAlert;
import com.tss.grc.module.ethics.repository.AmlAlertRepository;
import com.tss.grc.module.iam.entity.User;
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
 * F2-249 — an AML alert's analyst reference, both halves.
 *
 * <h2>The defect</h2>
 *
 * <p>{@code ethics.aml_alerts} declares {@code analyst_id} beside {@code analyst_name}, and only
 * the label was ever written — from {@code body.get("analystName")}, which
 * {@code EthicsPage}'s {@code window.prompt} collected by asking a human to type it. Measured
 * identical in dev and prod: {@code analyst_id} NULL on <b>10 of 10</b> live rows, and <b>0 of the
 * 3 stored labels</b> resolve to any real user ({@code 'AML Analyst — Layla Al-Anazi'},
 * {@code 'Financial Crime Unit'}, {@code 'Head of FCC'}). Probed live as {@code auditor}: an alert
 * moved {@code OPEN -> INVESTIGATING} naming an analyst who does not exist, at HTTP 200.
 *
 * <p>🔑 {@code decidedByName} twenty lines below in the same service has taken the acting
 * principal from {@code CurrentActor} since F2-223 — which is exactly why this looked covered
 * (F2-215's two-of-N). F2-223 deleted this page's {@code amlDecidedByPrompt} and left the
 * {@code amlAssignPrompt} beside it.
 *
 * <h2>⚠ Why the label is not simply refused</h2>
 *
 * <p>Because the schema says an unresolvable assignee is representable — a nullable id beside a
 * nullable name (F2-246's rule) — and the live labels prove the register means it: assigning an
 * alert to the Financial Crime Unit is a real thing a Head of FCC does. A fix that refused them
 * would fail on correct usage, which is the one failure a gate or a fix must never have (F2-214).
 * What is closed is the <em>appearance</em> of a link.
 *
 * <p>⚙ The directory here is a REAL {@link TenantUserDirectory} over the mocked repository, not a
 * mock of it: a mocked resolver returns null and every assertion below would go green asserting
 * nothing (F2-237).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmlAnalystAssignmentTest {

    private static final Long ORG = 1L;
    private static final Long ALERT = 500L;
    private static final Long ANALYST = 4L;
    private static final Long FOREIGN = 77L;

    @Mock AmlAlertRepository repo;
    @Mock UserRepository users;
    @Mock com.tss.grc.module.ethics.repository.KycSubjectRepository subjectRepo;

    AmlAlertService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        // Both directories are REAL over their mocked repositories, never mocks of the
        // directories themselves: a mocked resolver returns empty and every refusal leg below
        // would go green asserting nothing (F2-237).
        service = new AmlAlertService(repo, new TenantUserDirectory(users),
                new KycSubjectDirectory(subjectRepo));
        when(repo.findById(ALERT)).thenReturn(Optional.of(alert()));
        when(repo.save(any(AmlAlert.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findById(ANALYST)).thenReturn(Optional.of(user(ANALYST, ORG, "Fahad Al-Otaibi")));
        when(users.findById(FOREIGN)).thenReturn(Optional.of(user(FOREIGN, 2L, "Other Tenant")));
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private static AmlAlert alert() {
        AmlAlert a = new AmlAlert();
        a.setId(ALERT);
        a.setOrganisationId(ORG);
        a.setStatus("OPEN");
        return a;
    }

    private static User user(Long id, Long org, String full) {
        User u = new User();
        u.setId(id);
        u.setOrganisationId(org);
        u.setIsActive(true);
        u.setUsername("u" + id);
        u.setFullNameEn(full);
        return u;
    }

    @Test
    @DisplayName("an id resolves in-tenant and the label is DERIVED from what resolved")
    void anIdIsResolvedAndTheLabelDerived() {
        AmlAlert saved = service.assign(ALERT, new AssignAnalystRequest(ANALYST, null));

        assertThat(saved.getAnalystId()).isEqualTo(ANALYST);
        assertThat(saved.getAnalystName())
                .as("the label must come from the row the id names, not from the caller — "
                  + "otherwise the two halves can describe different people and the half on "
                  + "screen is the one with no vote (F2-245)")
                .isEqualTo("Fahad Al-Otaibi");
    }

    @Test
    @DisplayName("an organisational label is accepted and recorded UNLINKED")
    void aTeamLabelIsAcceptedAndLeftUnlinked() {
        AmlAlert saved = service.assign(ALERT, new AssignAnalystRequest(null, "Financial Crime Unit"));

        assertThat(saved.getAnalystName()).isEqualTo("Financial Crime Unit");
        assertThat(saved.getAnalystId())
                .as("the id stays NULL, which is how the register distinguishes an assignment to a "
                  + "named person from one to a function. Nulling it is the disclosure, not a gap.")
                .isNull();
    }

    /**
     * F2-231/F2-234's echo-versus-change rule. A form that round-trips the whole row keeps
     * working; a label that says something else is refused, because the row would then assert two
     * different assignees.
     */
    @Test
    @DisplayName("an echo of the derived label is accepted; a contradicting one is refused")
    void anEchoIsFineAndAContradictionIsNot() {
        assertThat(service.assign(ALERT, new AssignAnalystRequest(ANALYST, "Fahad Al-Otaibi"))
                .getAnalystId()).isEqualTo(ANALYST);

        // ⚠ The echo comparison is case-INSENSITIVE, so a caller may echo the label in any
        // casing — and what gets STORED must still be the server's spelling. The mutation
        // `setAnalystName(label != null ? label : derived)` SURVIVED until this leg existed:
        // with the contradiction check in place the only label that reaches the setter is an
        // echo, so the mutation's only observable effect is the caller's casing winning. A
        // register whose spelling of a person's name depends on how a caller typed it is the
        // travelling-label defect at one character's depth (F2-179's collation family).
        assertThat(service.assign(ALERT, new AssignAnalystRequest(ANALYST, "fahad al-otaibi"))
                .getAnalystName())
                .as("the stored label must be the DERIVED spelling, never the echoed one")
                .isEqualTo("Fahad Al-Otaibi");

        assertThatThrownBy(() -> service.assign(ALERT,
                    new AssignAnalystRequest(ANALYST, "Somebody Else Entirely")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not name user");
    }

    @Test
    @DisplayName("another tenant's user cannot be made the analyst")
    void aForeignTenantUserIsRefused() {
        assertThatThrownBy(() -> service.assign(ALERT, new AssignAnalystRequest(FOREIGN, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an active user in this organisation");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("a nonexistent user id is refused, with the same message as a foreign one")
    void aNonexistentIdIsRefusedIndistinguishably() {
        when(users.findById(999999L)).thenReturn(Optional.empty());

        String nonexistent = catchMessage(() -> service.assign(ALERT,
                new AssignAnalystRequest(999999L, null)));
        String foreign = catchMessage(() -> service.assign(ALERT,
                new AssignAnalystRequest(FOREIGN, null)));

        assertThat(nonexistent.replace("999999", "X"))
                .as("distinguishing 'no such user' from 'another tenant's user' would let a caller "
                  + "enumerate foreign user ids from the error text — the reason "
                  + "CommitteeMemberRegistryService uses one message, carried over deliberately")
                .isEqualTo(foreign.replace(String.valueOf(FOREIGN), "X"));
    }

    @Test
    @DisplayName("an assignment naming nobody at all is refused")
    void anEmptyAssignmentIsRefused() {
        assertThatThrownBy(() -> service.assign(ALERT, new AssignAnalystRequest(null, "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs an assignee");
        verify(repo, never()).save(any());
    }

    /** The behaviour that already worked must keep working — the fix must not narrow the route. */
    @Test
    @DisplayName("assigning still moves an OPEN alert to INVESTIGATING")
    void assigningStillAdvancesTheAlert() {
        assertThat(service.assign(ALERT, new AssignAnalystRequest(ANALYST, null)).getStatus())
                .isEqualTo("INVESTIGATING");
    }

    /**
     * The structural closure, asserted rather than assumed (F2-233): a field the bound type does
     * not declare can never arrive. If somebody widens this record, the forgery becomes
     * expressible again and no behavioural assertion above would notice.
     */
    @Test
    @DisplayName("the request type declares exactly the two ways to name an assignee")
    void theRequestTypeDeclaresOnlyTheTwoHalves() {
        assertThat(java.util.Arrays.stream(AssignAnalystRequest.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList())
                .containsExactly("analystUserId", "analystName");
    }

    private static String catchMessage(Runnable r) {
        try {
            r.run();
            throw new AssertionError("expected a refusal");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }
}

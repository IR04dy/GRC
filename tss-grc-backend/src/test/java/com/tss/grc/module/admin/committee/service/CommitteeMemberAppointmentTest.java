package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.dto.MemberTermRequest;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberSkillRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <b>F2-25 — committee member appointment.</b>
 *
 * <p>This test exists because the finding it pins was originally <em>misdiagnosed</em>, and
 * the misdiagnosis would have produced a fix that did not fix anything. The register claimed
 * the endpoint never stamped {@code organisationId} and that a caller could therefore write a
 * roster row into another tenant. A review-council live probe disproved both:
 * {@code CommitteeMemberRegistryService.appoint()} has nulled {@code id} and stamped the
 * tenant since {@code 8373d6f8}. The real cause of the observed 500 was that
 * {@code appointed_at} and {@code term_starts_at} are {@code nullable = false} with no
 * default and no {@code @Valid} anywhere on the path — so a reasonable body died in the
 * database.
 *
 * <p>Hence the two groups below: the ones that pin **the actual bug** (mandatory dates,
 * in-tenant committee), and the ones that pin **the defence that already existed** so a
 * future refactor cannot quietly remove it. The second group is the regression test the
 * original defence never had — which is why nobody noticed it was already working.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitteeMemberAppointmentTest {

    private static final Long ORG = 1L;
    private static final Long COMMITTEE = 6L;

    @Mock CommitteeMemberTermRepository  terms;
    @Mock CommitteeRepository            committees;
    @Mock CommitteeMemberSkillRepository skills;
    @Mock UserRepository                 users;
    @Mock AuditLogService                audit;
    @Mock ApplicationEventPublisher      publisher;

    @InjectMocks CommitteeMemberRegistryService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        when(terms.save(any(CommitteeMemberTerm.class))).thenAnswer(i -> {
            CommitteeMemberTerm t = i.getArgument(0);
            if (t.getId() == null) t.setId(99L);
            return t;
        });
        Committee c = new Committee();
        c.setId(COMMITTEE);
        c.setOrganisationId(ORG);
        when(committees.findByIdAndOrganisationId(COMMITTEE, ORG)).thenReturn(Optional.of(c));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private MemberTermRequest req(Long committeeId) {
        return new MemberTermRequest(committeeId, "Layla Al-Otaibi", null, "Member",
            (short) 1, (short) 1, "No commercial relationship in 3 years",
            LocalDate.of(2026, 3, 1), "RES-BOARD-26Q1-04",
            LocalDate.of(2026, 3, 1), LocalDate.of(2029, 3, 1), 1);
    }

    // ── 1. the actual defect ────────────────────────────────────────────────

    /**
     * The whole point of F2-25. The dates are the two NOT NULL columns with no default; the
     * DTO makes them {@code @NotNull} so Spring rejects the body with a field-named 400
     * instead of the insert dying as a 500. Bean validation runs at the controller, so here
     * the service is simply proven to persist what it was given rather than defaulting.
     */
    @Test
    void the_mandatory_dates_are_persisted_exactly_as_supplied() {
        CommitteeMemberTerm saved = service.appoint(req(COMMITTEE));
        assertThat(saved.getAppointedAt()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(saved.getTermStartsAt()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(saved.getTermEndsAt()).isEqualTo(LocalDate.of(2029, 3, 1));
    }

    /**
     * Deliberately NOT defaulted to today. An appointment date is a governance fact that
     * feeds CMA term-limit counting and (after F2-28) the quorum denominator; stamping
     * "today" onto an appointment made at last month's board meeting fabricates provenance,
     * which is what V315/V316 refuse to do for votes and approvals. The DTO's {@code @NotNull}
     * is the enforcement; this test documents that the service adds no fallback.
     */
    @Test
    void the_service_does_not_invent_an_appointment_date() {
        MemberTermRequest noDates = new MemberTermRequest(COMMITTEE, "Nasser Al-Dosari", null,
            "Member", (short) 1, (short) 0, null, null, null, null, null, null);
        CommitteeMemberTerm saved = service.appoint(noDates);
        assertThat(saved.getAppointedAt()).isNull();
        assertThat(saved.getTermStartsAt()).isNull();
    }

    /** F2-25: a committee id from another tenant, or a typo, is refused with a 400-shaped error. */
    @Test
    void a_committee_outside_the_tenant_is_refused() {
        when(committees.findByIdAndOrganisationId(4242L, ORG)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.appoint(req(4242L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not exist in this organisation");
        verify(terms, never()).save(any());
    }

    // ── 2. the defence that already existed, now pinned ─────────────────────

    /** The tenant comes from {@code TenantContext}, never from the caller. */
    @Test
    void the_tenant_is_stamped_server_side() {
        assertThat(service.appoint(req(COMMITTEE)).getOrganisationId()).isEqualTo(ORG);
    }

    /**
     * {@code id} is nulled so {@code save()} is an INSERT rather than a {@code merge()} that
     * could overwrite an existing row. The DTO has no {@code id} field at all now, so this
     * pins the service-side half of a defence with two layers.
     */
    @Test
    void the_saved_row_is_an_insert_not_a_merge() {
        CommitteeMemberTerm saved = service.appoint(req(COMMITTEE));
        assertThat(saved.getId()).isEqualTo(99L);   // assigned by the repository, not the caller
    }

    // ── 3. defaults and side effects ────────────────────────────────────────

    @Test
    void renewal_status_and_term_number_default_when_omitted() {
        MemberTermRequest minimal = new MemberTermRequest(COMMITTEE, "CFO", null, "Member",
            null, null, null, LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 1, 1), null, null);
        CommitteeMemberTerm saved = service.appoint(minimal);
        assertThat(saved.getRenewalStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getTermNumber()).isEqualTo(1);
        assertThat(saved.getIsVotingMember()).isEqualTo((short) 1);   // entity default retained
    }

    @Test
    void the_member_name_and_role_are_trimmed() {
        MemberTermRequest padded = new MemberTermRequest(COMMITTEE, "  Sara Al-Qahtani  ", null,
            "  Chair  ", (short) 1, (short) 1, null, LocalDate.of(2026, 1, 1), null,
            LocalDate.of(2026, 1, 1), null, null);
        CommitteeMemberTerm saved = service.appoint(padded);
        assertThat(saved.getMemberName()).isEqualTo("Sara Al-Qahtani");
        assertThat(saved.getRole()).isEqualTo("Chair");
    }

    /**
     * F2-26 depends on this field surviving the DTO round-trip — and, since F2-26 shipped,
     * on it being validated: the user must be an active member of the caller's own tenant.
     * {@code CommitteeSeatLinkageTest} covers the rejection paths; this covers the happy one.
     */
    @Test
    void an_optional_platform_user_link_is_carried_through() {
        User u = new User();
        u.setId(8L);
        u.setUsername("so_officer");
        u.setOrganisationId(ORG);
        u.setIsActive(true);
        when(users.findById(8L)).thenReturn(Optional.of(u));

        MemberTermRequest linked = new MemberTermRequest(COMMITTEE, "CISO", 8L, "Member",
            (short) 1, (short) 0, null, LocalDate.of(2026, 1, 1), null,
            LocalDate.of(2026, 1, 1), null, null);
        assertThat(service.appoint(linked).getMemberUserId()).isEqualTo(8L);
    }

    @Test
    void the_appointment_is_audited() {
        service.appoint(req(COMMITTEE));
        verify(audit).log(org.mockito.ArgumentMatchers.eq("MEMBER_APPOINT"),
            org.mockito.ArgumentMatchers.eq("CommitteeMemberTerm"),
            org.mockito.ArgumentMatchers.eq(99L),
            org.mockito.ArgumentMatchers.contains("Layla Al-Otaibi"));
    }
}

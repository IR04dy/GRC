package com.tss.grc.module.platform.service;

import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.RoleRepository;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.entity.Notification;
import com.tss.grc.module.platform.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-94 — the fan-out must distinguish the two ways of reaching nobody, and must not
 * double-notify a user who holds two addressed roles.
 *
 * <p>For as long as {@code broadcastToRole} returned a bare {@code int}, "nobody active holds
 * CISO here" and "CTO is not a role in this tenant" were the same observable — {@code 0} —
 * and the engine's audit row read identically in both cases. One is fixed by a grant; the
 * other cannot be fixed by any grant. 143 audit rows in dev, and 164 in production, recorded
 * the difference as nothing at all.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationBroadcastFanoutTest {

    private static final Long ORG = 7L;

    @Mock private NotificationRepository notifications;
    @Mock private UserRepository users;
    @Mock private RoleRepository roles;
    @InjectMocks private NotificationService service;

    private static Role role(String name) {
        Role r = new Role();
        r.setRoleName(name);
        return r;
    }

    private static User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private void tenantHasRoles(String... names) {
        when(roles.findByOrganisationIdOrOrganisationIdIsNull(ORG))
            .thenReturn(java.util.Arrays.stream(names).map(NotificationBroadcastFanoutTest::role).toList());
    }

    private void holders(String roleName, Long... ids) {
        when(users.findActiveByOrganisationIdAndRoleName(ORG, roleName))
            .thenReturn(java.util.Arrays.stream(ids).map(NotificationBroadcastFanoutTest::user).toList());
    }

    private NotificationService.RoleFanout fanOut(String... addressed) {
        return service.broadcastToRoles(ORG, List.of(addressed), "t", "ت", "m",
            "ESCALATION", "ChangeRequest", 42L);
    }

    @Test
    void aNameThatIsNotARoleIsAConfigurationDefectNotAMissingGrant() {
        tenantHasRoles("CISO", "CHANGE_MANAGER");
        holders("CISO", 1L);
        holders("CHANGE_MANAGER");   // exists, nobody holds it

        NotificationService.RoleFanout f = fanOut("CTO", "CISO", "CHANGE_MANAGER");

        assertThat(f.delivered()).isEqualTo(1);
        assertThat(f.deliveredRoles()).containsExactly("CISO");
        assertThat(f.unheldRoles())
            .as("CHANGE_MANAGER exists here — the remedy is to grant it")
            .containsExactly("CHANGE_MANAGER");
        assertThat(f.unknownRoles())
            .as("CTO is not a role at all — no grant can fix it, so it must not be reported "
              + "as a missing grant")
            .containsExactly("CTO");
        assertThat(f.hasConfigurationDefect()).isTrue();
        assertThat(f.reachedNobody()).isFalse();

        // A role that does not exist must not be queried for holders — that query returning
        // empty is precisely what made the two faults indistinguishable.
        verify(users, never()).findActiveByOrganisationIdAndRoleName(ORG, "CTO");
    }

    @Test
    void aUserHoldingTwoAddressedRolesIsNotifiedOnce() {
        tenantHasRoles("CISO", "RISK_MANAGER");
        holders("CISO", 1L, 2L);
        holders("RISK_MANAGER", 2L, 3L);     // user 2 holds both

        NotificationService.RoleFanout f = fanOut("CISO", "RISK_MANAGER");

        assertThat(f.delivered())
            .as("three distinct people, not four notifications")
            .isEqualTo(3);
        verify(notifications, times(3)).save(any(Notification.class));
        assertThat(f.deliveredRoles())
            .as("RISK_MANAGER is held — classifying on the dedupe result would report a "
              + "satisfied audience as an unheld role")
            .containsExactly("CISO", "RISK_MANAGER");
        assertThat(f.unheldRoles()).isEmpty();
    }

    @Test
    void everyAddressedRoleUnreachableMeansReachedNobodyAndNothingWritten() {
        tenantHasRoles("CHANGE_MANAGER");
        holders("CHANGE_MANAGER");

        NotificationService.RoleFanout f = fanOut("LINE_MANAGER", "CHANGE_MANAGER");

        assertThat(f.reachedNobody()).isTrue();
        assertThat(f.delivered()).isZero();
        assertThat(f.unknownRoles()).containsExactly("LINE_MANAGER");
        assertThat(f.unheldRoles()).containsExactly("CHANGE_MANAGER");
        verify(notifications, never()).save(any(Notification.class));
    }

    @Test
    void roleNamesMatchCaseInsensitivelyAndBlanksAreNotAnAudience() {
        tenantHasRoles("CISO");
        holders("ciso", 1L);   // resolved with the caller's casing, not the catalogue's

        NotificationService.RoleFanout lower = fanOut("ciso");
        assertThat(lower.unknownRoles())
            .as("a rule author types CISO and the catalogue may hold Ciso — a casing "
              + "mismatch must not read as a nonexistent role")
            .isEmpty();

        NotificationService.RoleFanout blank = fanOut("", "   ");
        assertThat(blank.delivered()).isZero();
        assertThat(blank.unknownRoles())
            .as("a blank is not a role name and must not be reported as one")
            .isEmpty();
    }

    @Test
    void theSingleRoleEntryPointStillReturnsACountForItsElevenCallers() {
        tenantHasRoles("CISO");
        holders("CISO", 1L, 2L);

        int delivered = service.broadcastToRole(ORG, "CISO", "t", "ت", "m",
            "NOTIFICATION", "Risk", 1L);

        assertThat(delivered).isEqualTo(2);
        // Blank/null short-circuits without touching the repositories, as before.
        assertThat(service.broadcastToRole(ORG, "  ", "t", "ت", "m", "NOTIFICATION", "Risk", 1L))
            .isZero();
        assertThat(service.broadcastToRole(null, "CISO", "t", "ت", "m", "NOTIFICATION", "Risk", 1L))
            .isZero();
    }

    @Test
    void nothingAddressedDoesNotQueryAnything() {
        NotificationService.RoleFanout f = service.broadcastToRoles(ORG, List.of(), "t", "ت",
            "m", "NOTIFICATION", "Risk", 1L);

        assertThat(f.delivered()).isZero();
        assertThat(f.reachedNobody()).isTrue();
        assertThat(f.hasConfigurationDefect()).isFalse();
        verify(roles, never()).findByOrganisationIdOrOrganisationIdIsNull(anyLong());
        verify(users, never()).findActiveByOrganisationIdAndRoleName(anyLong(), anyString());
    }
}

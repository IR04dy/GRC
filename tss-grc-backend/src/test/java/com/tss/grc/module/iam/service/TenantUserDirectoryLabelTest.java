package com.tss.grc.module.iam.service;

import com.tss.grc.module.iam.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-277 — {@code labelFor} and {@code labelArFor} must not be the same method twice.
 *
 * <p>The English half falls back to the username so a register cell is never empty; the Arabic
 * half deliberately does NOT, because the username is language-neutral and already keeps the row
 * identifiable through its sibling. Returning it here would assert an Arabic name the directory
 * does not have, which is F2-271's rule that an empty Arabic value is honest and a manufactured
 * one is a fabricated governance record.
 *
 * <p>These are pure functions over an entity, so no mock is needed — and a directory whose
 * fallbacks are asserted by inspection rather than by test is exactly how F2-249 found two
 * hand-written copies of this line disagreeing about a blank name.
 */
class TenantUserDirectoryLabelTest {

    private static final TenantUserDirectory DIRECTORY = new TenantUserDirectory(null);

    private static User user(String en, String ar, String username) {
        User u = new User();
        u.setFullNameEn(en);
        u.setFullNameAr(ar);
        u.setUsername(username);
        return u;
    }

    @Test
    @DisplayName("both halves come from the user's own two name columns")
    void bothHalvesComeFromTheUsersOwnColumns() {
        User u = user("Sara Al-Otaibi", "سارة العتيبي", "salotaibi");
        assertThat(DIRECTORY.labelFor(u)).isEqualTo("Sara Al-Otaibi");
        assertThat(DIRECTORY.labelArFor(u)).isEqualTo("سارة العتيبي");
    }

    @Test
    @DisplayName("no Arabic name yields NULL, never the username")
    void noArabicNameYieldsNull() {
        User u = user("Sara Al-Otaibi", null, "salotaibi");
        assertThat(DIRECTORY.labelFor(u)).isEqualTo("Sara Al-Otaibi");
        assertThat(DIRECTORY.labelArFor(u))
                .as("3 of 7 dev users have no full_name_ar; the username is not an Arabic name")
                .isNull();
    }

    @Test
    @DisplayName("a BLANK Arabic name is treated as absent, like its English sibling")
    void aBlankArabicNameIsTreatedAsAbsent() {
        assertThat(DIRECTORY.labelArFor(user("Sara", "   ", "s")))
                .as("blank-safe, not merely null-safe — the asymmetry F2-249 found between two "
                  + "hand-written copies of the English half")
                .isNull();
    }

    @Test
    @DisplayName("the English half still falls back to the username, and the Arabic half does not")
    void theEnglishHalfStillFallsBackToTheUsername() {
        User u = user("  ", "  ", "salotaibi");
        assertThat(DIRECTORY.labelFor(u)).isEqualTo("salotaibi");
        assertThat(DIRECTORY.labelArFor(u)).isNull();
    }

    @Test
    @DisplayName("both halves are null-safe on an unresolvable user")
    void bothHalvesAreNullSafe() {
        assertThat(DIRECTORY.labelFor(null)).isNull();
        assertThat(DIRECTORY.labelArFor(null))
                .as("SignatureService clears the PAIR when an id does not resolve, which needs "
                  + "both methods to be null-safe rather than one")
                .isNull();
    }
}

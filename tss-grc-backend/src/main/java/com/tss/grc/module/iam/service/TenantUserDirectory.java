package com.tss.grc.module.iam.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * F2-249 — the one place a business register resolves a <b>person reference</b> against
 * {@code iam.users}, and the one place the display label for a resolved person is spelled.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Four hand-written copies of the same predicate were live when this was written, and no two
 * of them agreed on everything:
 *
 * <ul>
 *   <li>{@code CoiService.requireOwnUser} (F2-244) — id + tenant + active, one message</li>
 *   <li>{@code CommitteeMemberRegistryService.requireUserInTenant} — the same three filters, plus
 *       a seat-specific duplicate check that is genuinely its own</li>
 *   <li>{@code SignatureService.resolveSignerName} — the label half, blank-safe</li>
 *   <li>the committee registry's own {@code describeLinkage}/error text — the label half again,
 *       <b>null-safe but not blank-safe</b>, so a user whose {@code full_name_en} is the empty
 *       string renders as {@code ""} in one place and as their username in the other</li>
 * </ul>
 *
 * <p>That last divergence is the whole argument. F2-177's rule: <b>what made a duplication
 * harmless was two independent copies happening to agree, and nobody was maintaining that.</b>
 * F2-190's rule: never add a second mechanism beside an existing one — so this is not a fifth
 * spelling, it is the one the others now delegate to.
 *
 * <h2>Where it may live</h2>
 *
 * <p>In {@code module/iam}, because {@code iam} owns {@code users}. {@code X -> iam} is an
 * ordinary business edge, not a kernel inversion — {@code UserRepository} is already read from
 * ten modules outside {@code iam}, and {@code ModuleGraphRatchetTest}'s {@code KERNEL_ISH} is
 * only {@code platform}, {@code integrations} and {@code dashboard} (checked, not assumed —
 * F2-244 verified the same edge for {@code ethics}). Putting it in {@code common/} would have
 * been worse: that package sits <b>outside</b> the ratchet's population entirely, so the
 * dependency would be neither forbidden nor measured.
 *
 * <h2>⚠ What this deliberately does NOT decide</h2>
 *
 * <p>Whether a register <b>must</b> have a resolvable person. That is settled per column by the
 * schema (F2-246's rule): a table declaring a nullable id <em>beside</em> a name column is
 * stating that an unresolvable party is representable — {@code ethics.aml_alerts} holds
 * {@code 'Financial Crime Unit'} and {@code 'Head of FCC'} on live rows, which are legitimate
 * <em>organisational</em> assignments, and refusing them would be a fix that fails on correct
 * usage (F2-214). A table declaring only an id, with no name column at all, is stating the
 * opposite — which is why F2-244's recusal register refuses. So callers choose {@link #require}
 * or {@link #resolve}; this class does not choose for them.
 */
@Component
@RequiredArgsConstructor
public class TenantUserDirectory {

    private final UserRepository users;

    /**
     * The named user, if they are an active user of the <b>caller's own</b> tenant.
     *
     * @param userId the caller-supplied reference; {@code null} yields {@link Optional#empty()}
     */
    @Transactional(readOnly = true)
    public Optional<User> resolve(Long userId) {
        if (userId == null) return Optional.empty();
        Long org = TenantContext.getCurrentTenant();
        if (org == null) return Optional.empty();
        return users.findById(userId)
                .filter(u -> org.equals(u.getOrganisationId()))
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()));
    }

    /**
     * The named user, refusing rather than returning empty.
     *
     * <p>⚠ <b>One message for "no such user" and "another tenant's user"</b>, carried over from
     * {@code CommitteeMemberRegistryService} deliberately: distinguishing them would let a caller
     * enumerate other tenants' user ids from the error text.
     *
     * @param what what is being recorded, for the message — e.g. {@code "an AML analyst assignment"}
     * @throws IllegalArgumentException when the id resolves to nobody the caller may name
     */
    @Transactional(readOnly = true)
    public User require(Long userId, String what) {
        return resolve(userId).orElseThrow(() -> new IllegalArgumentException(
                "User " + userId + " is not an active user in this organisation, so " + what
              + " cannot be recorded against them. A reference that resolves to nobody leaves "
              + "the record attributable to nobody."));
    }

    /**
     * The display label for a resolved person — the value a {@code *_name} column beside a
     * {@code *_id} column should hold.
     *
     * <p>⚠ Blank-safe, not merely null-safe: a {@code full_name_en} of {@code ""} must fall back
     * to the username rather than rendering an empty register cell. Of the two live copies of
     * this line, one had that and one did not.
     *
     * <p>⚙ It is the <b>display name</b> and not the username on purpose. This is the label for
     * a person the record <em>refers to</em>, so it belongs in the same vocabulary as the
     * platform's one already-correct member of this family ({@code platform.signatures}, whose
     * {@code signer_name} holds {@code 'System Administrator'}). That is a different question
     * from F2-236's {@code *_by} <b>attribution</b> columns, which record the username of
     * whoever acted; do not unify the two.
     */
    public String labelFor(User u) {
        if (u == null) return null;
        String full = u.getFullNameEn();
        return (full != null && !full.isBlank()) ? full : u.getUsername();
    }

    /** {@link #require} then {@link #labelFor}, for the common "resolve and derive" pair. */
    @Transactional(readOnly = true)
    public String requireLabel(Long userId, String what) {
        return labelFor(require(userId, what));
    }
}

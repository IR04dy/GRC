package com.tss.grc.module.admin.committee.dto;

import jakarta.validation.constraints.Size;

/**
 * Set a roster seat's platform-user state (finding F2-26).
 *
 * <p><b>One endpoint, three states.</b> A seat is LINKED, EXTERNAL (declared to have no
 * platform login) or UNLINKED (a gap awaiting the secretariat). Two endpoints — "link" and
 * "declare external" — could disagree with each other and leave a seat both linked and
 * declared external; one atomic transition cannot. So this body expresses all three:
 *
 * <pre>
 *   {"memberUserId": 8}                                    -> LINKED
 *   {"noPlatformUser": true, "basisEn": "External auditor"} -> EXTERNAL
 *   {}                                                      -> UNLINKED (cleared)
 * </pre>
 *
 * <p><b>Why {@code memberUserId} is not {@code @NotNull}.</b> Clearing a link is a
 * legitimate act — a member leaves the company, an account is replaced — and it must be
 * reachable. An explicit {@code DELETE} sub-resource would be the other idiom; it was not
 * used because unlinking and re-linking are the same governance decision ("who holds this
 * seat now") and splitting them across verbs invites the two-writers race above.
 *
 * <p><b>{@code basisEn} is validated in the service, not here.</b> It is mandatory only
 * when {@code noPlatformUser} is true, which is a cross-field rule; expressing it as a
 * bean-validation annotation would need a class-level constraint whose message could not
 * name the field. The service raises a 400 that says which field is missing and why —
 * which is the whole point of F2-25's lesson about failures that cannot say what is wrong.
 */
public record LinkUserRequest(

        /** The platform user who holds this seat. NULL clears the link. Must be an active
         *  user in the caller's own organisation. */
        Long memberUserId,

        /** TRUE = declare that this seat will never have a platform login. Mutually
         *  exclusive with {@code memberUserId}; requires {@code basisEn}. */
        Boolean noPlatformUser,

        /** Why the seat has no platform user. Mandatory when {@code noPlatformUser} is
         *  set, ignored otherwise. */
        @Size(max = 300) String basisEn,

        /** Optional secretariat note recorded in the audit ledger alongside the
         *  before → after transition. Not persisted on the row: the row holds the current
         *  fact, the ledger holds the history of how it got there. */
        @Size(max = 500) String reason
) {

    /** True when the caller is asking for the EXTERNAL state. */
    public boolean declaresExternal() {
        return Boolean.TRUE.equals(noPlatformUser);
    }
}

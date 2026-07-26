package com.tss.grc.module.admin.committee.dto;

/**
 * A platform user the secretariat may attach to a committee seat (finding F2-26).
 *
 * <p><b>Why this exists instead of reusing the IAM user list.</b> The obvious picker source
 * is {@code GET /api/v1/admin/users}, guarded by {@code hasRole('PLATFORM_ADMIN') or
 * hasPermission('IAM','READ')}. Measured against the live grant table: of the three roles
 * that hold {@code ADMIN:WRITE} — the right that performs the link — {@code ORG_ADMIN} and
 * {@code GRC_ADMINISTRATOR} hold {@code IAM:READ} but <b>{@code BOARD_SECRETARY} does
 * not</b>. Building the picker on that endpoint would 403 for the one persona the committee
 * module exists to serve, while leaving them the right to perform the link they cannot
 * populate. That is the F2-23 failure mode exactly: a mechanism shipped without checking
 * the dependency it rests on.
 *
 * <p>So the picker is served by the committee module under its own {@code ADMIN:WRITE}
 * guard, and it returns the <b>minimum</b> needed to identify a colleague — no email, no
 * lock or MFA state, no role list, no last-login. Widening {@code IAM:READ} to the
 * secretariat would have disclosed all of that to obtain a name.
 *
 * @param id            {@code iam.users.id} — what gets written to {@code member_user_id}
 * @param username      login handle, shown as the disambiguator between similar names
 * @param fullNameEn    display name (English)
 * @param fullNameAr    display name (Arabic) — the registry UI is Arabic-primary
 * @param jobTitleEn    job title, which is how a charter names a seat ("CISO", "CRO")
 * @param heldSeats     how many ACTIVE seats this user already holds on the committee being
 *                      populated; > 0 means picking them again would double-count them in
 *                      the quorum denominator, so the UI can grey them out before the
 *                      service returns 409
 */
public record LinkableUser(
        Long id,
        String username,
        String fullNameEn,
        String fullNameAr,
        String jobTitleEn,
        long heldSeats
) {}

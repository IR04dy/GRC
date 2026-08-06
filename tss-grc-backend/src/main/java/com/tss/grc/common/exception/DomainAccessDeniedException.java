package com.tss.grc.common.exception;

import org.springframework.security.access.AccessDeniedException;

/**
 * A refusal the <b>domain</b> decided, and whose message is meant to reach the caller.
 *
 * <h3>Why this type exists (F2-80)</h3>
 * Two kinds of 403 were sharing one exception type and therefore one message policy, and the wrong
 * policy was the default. {@code GlobalExceptionHandler#handleAccessDenied} builds its detail from a
 * <b>literal</b> — {@code "You do not have permission to perform this action."} — and never reads
 * {@code ex.getMessage()}. That is correct for the denials Spring Security itself raises, where any
 * detail is a hint about the guard. It was wrong for the <b>23</b> places where a service threw
 * {@code AccessDeniedException} carrying a carefully written explanation: all 23 messages were
 * composed, and all 23 were discarded.
 *
 * <p>The cost is specific, not cosmetic. A policy owner refused by the segregation-of-duty gate
 * ({@code PolicyManagementService:413}) <i>has</i> the permission — the refusal is
 * <i>"the policy author/owner cannot approve their own policy"</i>, which is the control working as
 * sold against NCA ECC 2-2 and SAMA CSF 3.3.5. They were told instead to go and obtain access they
 * already hold. The four-eyes gates are the ones that most need to explain themselves, and they were
 * the ones explaining least.
 *
 * <h3>Why it extends {@link AccessDeniedException}</h3>
 * Three reasons, all about not breaking something to fix something:
 * <ul>
 *   <li>Existing {@code catch (AccessDeniedException)} blocks keep working.</li>
 *   <li>If one of these ever escapes past the {@code @RestControllerAdvice} to Spring Security's
 *       {@code ExceptionTranslationFilter}, it degrades to the generic 403 — <b>fail-safe</b>. A new
 *       unrelated type could have surfaced as a 500.</li>
 *   <li>The security semantics are unchanged. This alters what the caller is <i>told</i>, never what
 *       they are <i>allowed</i>. Nothing about it is a relaxation.</li>
 * </ul>
 *
 * <h3>⚠ The message is written for a possibly hostile reader</h3>
 * A message-bearing 403 is an information-disclosure surface by construction, which is why F2-80 was
 * an M and not a one-line change: passing {@code ex.getMessage()} straight through
 * {@code handleAccessDenied} would have shipped two real leaks —
 * {@code CommitteeResolutionService} interpolated {@code seat.getMemberName()} and
 * {@code PlanDistributionService} interpolated {@code row.getRecipientName()}, so an unauthorised
 * caller would have learned a colleague's name from a refusal. Both were reworded when they moved to
 * this type; they now name the <i>authority required</i> instead of the <i>person holding it</i>.
 *
 * <p>So, before throwing this: say what rule refused and what the caller could do instead. Do not
 * name other people, and do not echo internal identifiers the caller did not already send.
 * {@code AccessDeniedException} remains the right type whenever the honest answer really is just
 * "no".
 */
public class DomainAccessDeniedException extends AccessDeniedException {

    private static final long serialVersionUID = 1L;

    public DomainAccessDeniedException(String message) {
        super(message);
    }

    public DomainAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}

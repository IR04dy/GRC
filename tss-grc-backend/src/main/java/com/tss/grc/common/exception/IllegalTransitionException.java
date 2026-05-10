package com.tss.grc.common.exception;

import java.util.Set;

/**
 * Thrown by lifecycle services when a state-machine transition is rejected.
 *
 * Carries enough context for the client to render a meaningful error: the
 * {@code from} state, the requested {@code to} state, and the set of
 * {@code allowed} next states. {@link GlobalExceptionHandler} maps this to
 * HTTP 409 Conflict with a structured ProblemDetail body.
 *
 * Subclassing {@link IllegalStateException} keeps the contract that callers
 * which still treat this as "bad state, not bad input" continue to work.
 */
public class IllegalTransitionException extends IllegalStateException {

    private final String from;
    private final String to;
    private final Set<String> allowed;

    public IllegalTransitionException(String from, String to, Set<String> allowed) {
        super(String.format("Illegal transition: %s → %s (allowed: %s)", from, to, allowed));
        this.from = from;
        this.to = to;
        this.allowed = allowed;
    }

    public String getFrom() { return from; }
    public String getTo() { return to; }
    public Set<String> getAllowed() { return allowed; }
}

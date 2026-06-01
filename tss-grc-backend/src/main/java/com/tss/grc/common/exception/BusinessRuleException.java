package com.tss.grc.common.exception;

/**
 * A domain/business-rule violation that should be reported to the caller with a
 * specific, actionable message (HTTP 403). Distinct from Spring Security's
 * {@link org.springframework.security.access.AccessDeniedException}, whose
 * handler deliberately returns a generic "Access Denied" so authorization
 * internals aren't leaked.
 *
 * <p>Use this when the rule itself is the message the user needs — e.g.
 * segregation-of-duties ("you cannot approve evidence you submitted"), where a
 * clear explanation is the whole point of the block.
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}

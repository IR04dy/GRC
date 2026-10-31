package com.tss.grc.module.integrations.llm.dto;

/**
 * Typed runtime failure raised by the LLM gateway when inference is disabled,
 * over quota, misconfigured, or rejected by the upstream/local model runtime.
 */
public class LlmGatewayException extends RuntimeException {

    private final String errorCode;

    public LlmGatewayException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public LlmGatewayException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

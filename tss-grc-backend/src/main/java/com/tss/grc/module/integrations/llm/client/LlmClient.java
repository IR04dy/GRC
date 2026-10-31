package com.tss.grc.module.integrations.llm.client;

import com.tss.grc.module.integrations.llm.dto.LlmRequest;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;

/**
 * Provider-neutral transport abstraction for local and hosted LLM endpoints.
 */
public interface LlmClient {

    /**
     * Execute a completion call against the configured model provider.
     *
     * @param request sanitized completion request
     * @return structured response with content, token usage, latency, and finish reason
     */
    LlmResponse complete(LlmRequest request);
}

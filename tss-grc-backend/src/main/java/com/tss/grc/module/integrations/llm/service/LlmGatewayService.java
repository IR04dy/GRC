package com.tss.grc.module.integrations.llm.service;

import com.tss.grc.module.integrations.aigateway.service.AiGatewayService;
import com.tss.grc.module.integrations.llm.dto.LlmRequest;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import com.tss.grc.module.integrations.llm.dto.LlmStatusDto;
import org.springframework.stereotype.Service;

/**
 * @deprecated Superseded by {@link AiGatewayService} which connects to the
 * external AI Gateway proxy (LiteLLM) with caching, PII masking, and guardrails.
 * Retained as a delegate for backwards compatibility.
 */
@Service
@Deprecated
public class LlmGatewayService {

    private final AiGatewayService aiGatewayService;

    public LlmGatewayService(AiGatewayService aiGatewayService) {
        this.aiGatewayService = aiGatewayService;
    }

    public LlmStatusDto status(Long organisationId) {
        return aiGatewayService.status(organisationId);
    }

    public LlmResponse complete(Long organisationId, LlmRequest request) {
        return aiGatewayService.complete(organisationId, request);
    }
}

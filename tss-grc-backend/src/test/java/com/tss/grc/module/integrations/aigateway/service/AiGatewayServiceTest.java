package com.tss.grc.module.integrations.aigateway.service;

import com.tss.grc.module.integrations.aigateway.client.AiGatewayClient;
import com.tss.grc.module.integrations.aigateway.config.AiGatewayProperties;
import com.tss.grc.module.integrations.llm.dto.LlmGatewayException;
import com.tss.grc.module.integrations.llm.dto.LlmRequest;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import com.tss.grc.module.integrations.llm.dto.LlmStatusDto;
import com.tss.grc.module.integrations.llm.entity.LlmCallLog;
import com.tss.grc.module.integrations.llm.repository.LlmCallLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiGatewayServiceTest {

    private AiGatewayProperties props;
    private AiGatewayClient client;
    private LlmCallLogRepository callLogRepo;
    private PlatformTransactionManager txManager;
    private AiGatewayService service;

    @BeforeEach
    void setUp() {
        props = new AiGatewayProperties();
        props.setEnabled(true);
        props.setMode(AiGatewayProperties.Mode.LIVE);
        props.setBaseUrl("http://localhost:4000/v1");
        props.setModel("grc-default");
        props.setApiKey("test-key");
        props.setTenantDailyTokenQuota(10_000L);

        client = mock(AiGatewayClient.class);
        callLogRepo = mock(LlmCallLogRepository.class);
        txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        service = new AiGatewayService(props, client, callLogRepo, txManager);
    }

    private static LlmRequest sampleReq(String userPrompt) {
        return new LlmRequest("POLICY_DRAFT", "policy_draft_bilingual", "v1",
            "sys", userPrompt, true, null, null, List.of());
    }

    @Test
    void completesRequestAndPersistsAuditTelemetry() {
        when(callLogRepo.sumTokensSince(eq(1L), any(LocalDateTime.class))).thenReturn(500L);
        when(client.complete(eq(1L), any())).thenReturn(new LlmResponse(
            "{\"verdict\":\"APPROVED\"}", "AI_GATEWAY", "grc-default", 100, 50, 42L, "stop"
        ));

        LlmResponse resp = service.complete(1L, sampleReq("Draft security policy"));

        assertThat(resp).isNotNull();
        assertThat(resp.content()).contains("APPROVED");
        assertThat(resp.provider()).isEqualTo("AI_GATEWAY");

        ArgumentCaptor<LlmCallLog> logCaptor = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(callLogRepo).save(logCaptor.capture());
        LlmCallLog logged = logCaptor.getValue();
        assertThat(logged.getOrganisationId()).isEqualTo(1L);
        assertThat(logged.getProvider()).isEqualTo("AI_GATEWAY");
        assertThat(logged.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(logged.getInputTokens()).isEqualTo(100);
        assertThat(logged.getOutputTokens()).isEqualTo(50);
    }

    @Test
    void blocksExecutionWhenDisabled() {
        props.setEnabled(false);

        assertThatThrownBy(() -> service.complete(1L, sampleReq("hello")))
            .isInstanceOf(LlmGatewayException.class)
            .hasMessageContaining("disabled");

        verify(client, never()).complete(any(), any());
    }

    @Test
    void blocksExecutionWhenDailyQuotaExceeded() {
        when(callLogRepo.sumTokensSince(eq(1L), any(LocalDateTime.class))).thenReturn(15_000L);

        assertThatThrownBy(() -> service.complete(1L, sampleReq("Draft policy")))
            .isInstanceOf(LlmGatewayException.class)
            .hasMessageContaining("quota exceeded");

        verify(client, never()).complete(any(), any());
    }

    @Test
    void returnsAccurateStatusDto() {
        when(callLogRepo.sumTokensSince(eq(1L), any(LocalDateTime.class))).thenReturn(3500L);

        LlmStatusDto status = service.status(1L);

        assertThat(status.enabled()).isTrue();
        assertThat(status.provider()).isEqualTo("AI_GATEWAY");
        assertThat(status.model()).isEqualTo("grc-default");
        assertThat(status.tokensUsedLast24h()).isEqualTo(3500L);
        assertThat(status.dailyTokenQuota()).isEqualTo(10_000L);
    }
}

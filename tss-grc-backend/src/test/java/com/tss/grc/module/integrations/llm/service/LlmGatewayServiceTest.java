package com.tss.grc.module.integrations.llm.service;

import com.tss.grc.module.integrations.llm.client.LlmClient;
import com.tss.grc.module.integrations.llm.config.LlmProperties;
import com.tss.grc.module.integrations.llm.dto.LlmGatewayException;
import com.tss.grc.module.integrations.llm.dto.LlmRequest;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import com.tss.grc.module.integrations.llm.entity.LlmCallLog;
import com.tss.grc.module.integrations.llm.repository.LlmCallLogRepository;
import com.tss.grc.module.integrations.llm.sanitize.PromptSanitizer;
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

class LlmGatewayServiceTest {

    private LlmProperties props;
    private LlmClient client;
    private LlmCallLogRepository callLogRepo;
    private PlatformTransactionManager txManager;

    @BeforeEach
    void setUp() {
        props = new LlmProperties();
        props.setEnabled(true);
        props.setMode(LlmProperties.Mode.STUB);
        props.setTenantDailyTokenQuota(10_000L);

        client = mock(LlmClient.class);
        callLogRepo = mock(LlmCallLogRepository.class);
        txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private static LlmRequest sampleReq(String userPrompt) {
        return new LlmRequest("POLICY_DRAFT", "policy_draft_bilingual", "v1",
            "sys", userPrompt, true, null, null, List.of());
    }

    @Test
    void sanitizesPromptHashesDigestAndLogsSucceededCall() {
        PromptSanitizer maskingSanitizer = raw -> raw.replace("1099887766", "[NATIONAL_ID]");
        LlmGatewayService gateway = new LlmGatewayService(props, client, maskingSanitizer, callLogRepo, txManager);

        when(callLogRepo.sumTokensSince(eq(1L), any(LocalDateTime.class))).thenReturn(500L);
        when(client.complete(any())).thenReturn(new LlmResponse(
            "{\"ok\":true}", "STUB", "gemini-2.5-flash", 120, 80, 15L, "STOP"
        ));

        LlmResponse resp = gateway.complete(1L, sampleReq("Owner ID 1099887766"));
        assertThat(resp.content()).isEqualTo("{\"ok\":true}");

        ArgumentCaptor<LlmRequest> reqCap = ArgumentCaptor.forClass(LlmRequest.class);
        verify(client).complete(reqCap.capture());
        assertThat(reqCap.getValue().userPrompt()).isEqualTo("Owner ID [NATIONAL_ID]");

        ArgumentCaptor<LlmCallLog> logCap = ArgumentCaptor.forClass(LlmCallLog.class);
        verify(callLogRepo).save(logCap.capture());
        assertThat(logCap.getValue().getStatus()).isEqualTo("SUCCEEDED");
        assertThat(logCap.getValue().getPromptSha256()).hasSize(64);
        assertThat(logCap.getValue().getInputTokens()).isEqualTo(120);
        assertThat(logCap.getValue().getOutputTokens()).isEqualTo(80);
    }

    @Test
    void blocksAndLogsWhenDisabledOrOverDailyQuota() {
        PromptSanitizer passThrough = raw -> raw;
        LlmGatewayService gateway = new LlmGatewayService(props, client, passThrough, callLogRepo, txManager);

        props.setEnabled(false);
        assertThatThrownBy(() -> gateway.complete(1L, sampleReq("hello")))
            .isInstanceOf(LlmGatewayException.class)
            .extracting(e -> ((LlmGatewayException) e).getErrorCode())
            .isEqualTo("LLM_DISABLED");
        verify(client, never()).complete(any());

        props.setEnabled(true);
        when(callLogRepo.sumTokensSince(eq(1L), any(LocalDateTime.class))).thenReturn(10_000L);
        assertThatThrownBy(() -> gateway.complete(1L, sampleReq("hello")))
            .isInstanceOf(LlmGatewayException.class)
            .extracting(e -> ((LlmGatewayException) e).getErrorCode())
            .isEqualTo("QUOTA_EXCEEDED");
        verify(client, never()).complete(any());
    }
}

package com.tss.grc.module.integrations.aigateway.service;

import com.tss.grc.module.integrations.aigateway.client.AiGatewayClient;
import com.tss.grc.module.integrations.aigateway.config.AiGatewayProperties;
import com.tss.grc.module.integrations.llm.dto.LlmGatewayException;
import com.tss.grc.module.integrations.llm.dto.LlmRequest;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import com.tss.grc.module.integrations.llm.dto.LlmStatusDto;
import com.tss.grc.module.integrations.llm.entity.LlmCallLog;
import com.tss.grc.module.integrations.llm.repository.LlmCallLogRepository;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    private static final LlmResponse OK_RESPONSE =
        new LlmResponse("{\"verdict\":\"APPROVED\"}", "AI_GATEWAY", "grc-default", 100, 50, 42L, "stop");

    @Test
    void concurrentCallCannotPassOnTheSameStaleTotal() throws Exception {
        // 9,000 of 10,000 used: one call fits, and its reservation (2,048 output + prompt)
        // must stop a second call that reads the same ledger total while the first is running.
        when(callLogRepo.sumTokensSince(eq(1L), any(LocalDateTime.class))).thenReturn(9_000L);
        CountDownLatch inGateway = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        when(client.complete(eq(1L), any())).thenAnswer(inv -> {
            inGateway.countDown();
            finish.await(5, TimeUnit.SECONDS);
            return OK_RESPONSE;
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<LlmResponse> first = pool.submit(() -> service.complete(1L, sampleReq("Draft policy A")));
            assertThat(inGateway.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.complete(1L, sampleReq("Draft policy B")))
                .isInstanceOfSatisfying(LlmGatewayException.class,
                    ex -> assertThat(ex.getErrorCode()).isEqualTo("QUOTA_EXCEEDED"))
                .hasMessageContaining("reserved by calls in progress");

            finish.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).provider()).isEqualTo("AI_GATEWAY");
        } finally {
            finish.countDown();
            pool.shutdownNow();
        }
        verify(client, times(1)).complete(any(), any());
    }

    @Test
    void reservationIsReleasedWhetherTheCallFailsOrSucceeds() {
        when(callLogRepo.sumTokensSince(eq(1L), any(LocalDateTime.class))).thenReturn(9_000L);
        when(client.complete(eq(1L), any()))
            .thenThrow(new LlmGatewayException("GATEWAY_UNAVAILABLE", "down"))
            .thenReturn(OK_RESPONSE);

        assertThatThrownBy(() -> service.complete(1L, sampleReq("a")))
            .isInstanceOf(LlmGatewayException.class);
        service.complete(1L, sampleReq("b"));
        service.complete(1L, sampleReq("c"));

        verify(client, times(3)).complete(any(), any());
    }

    @Test
    void estimateCountsAboutThreePromptCharsPerTokenPlusTheOutputCeiling() {
        LlmRequest capped = new LlmRequest("F", "t", "v1", "abc", "abcdef", true, 100, null, List.of());
        LlmRequest uncapped = new LlmRequest("F", "t", "v1", "abc", "abcdef", true, null, null, List.of());

        assertThat(AiGatewayService.estimateTokens(capped)).isEqualTo(3 + 100);
        assertThat(AiGatewayService.estimateTokens(uncapped))
            .isEqualTo(3 + AiGatewayService.DEFAULT_OUTPUT_RESERVATION);
    }

    @Test
    void callLogWriteFailureIsLoggedAtErrorWithoutBreakingTheCall() {
        when(callLogRepo.sumTokensSince(eq(1L), any(LocalDateTime.class))).thenReturn(0L);
        when(client.complete(eq(1L), any())).thenReturn(OK_RESPONSE);
        when(callLogRepo.save(any())).thenThrow(new IllegalStateException("db down"));

        Logger logger = (Logger) LoggerFactory.getLogger(AiGatewayService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            LlmResponse resp = service.complete(1L, sampleReq("Draft policy"));

            assertThat(resp.provider()).isEqualTo("AI_GATEWAY");
            assertThat(appender.list).anySatisfy(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.ERROR);
                assertThat(e.getFormattedMessage())
                    .contains("llm_call_log")
                    .contains("status=SUCCEEDED")
                    .contains("tokens=150");
            });
        } finally {
            logger.detachAppender(appender);
        }
    }
}

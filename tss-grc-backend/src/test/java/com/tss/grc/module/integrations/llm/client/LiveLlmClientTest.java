package com.tss.grc.module.integrations.llm.client;

import com.tss.grc.module.integrations.llm.config.LlmProperties;
import com.tss.grc.module.integrations.llm.dto.LlmGatewayException;
import com.tss.grc.module.integrations.llm.dto.LlmRequest;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LiveLlmClientTest {

    private static LlmRequest sampleReq() {
        return new LlmRequest(
            "POLICY_DRAFT",
            "policy_draft_bilingual",
            "v1",
            "System rules",
            "Draft access control policy",
            true,
            4096,
            0.2,
            List.of(10L)
        );
    }

    @Test
    void geminiAdapterSendsApiKeyHeaderAndParsesUsageMetadata() {
        LlmProperties props = new LlmProperties();
        props.setMode(LlmProperties.Mode.LIVE);
        props.setProvider(LlmProperties.Provider.GEMINI);
        props.setGeminiBaseUrl("https://generativelanguage.googleapis.com/v1beta");
        props.setModel("gemini-1.5-flash");
        props.setGeminiApiKey("gem-test-key-123");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LiveLlmClient client = new LiveLlmClient(props, builder.build());

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("x-goog-api-key", "gem-test-key-123"))
            .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
            .andRespond(withSuccess("""
                {
                  "candidates": [{
                    "content": { "parts": [{ "text": "{\\"titleEn\\":\\"Access Policy\\"}" }] },
                    "finishReason": "STOP"
                  }],
                  "usageMetadata": {
                    "promptTokenCount": 420,
                    "candidatesTokenCount": 180
                  }
                }
                """, MediaType.APPLICATION_JSON));

        LlmResponse resp = client.complete(sampleReq());
        server.verify();

        assertThat(resp.provider()).isEqualTo("GEMINI");
        assertThat(resp.model()).isEqualTo("gemini-1.5-flash");
        assertThat(resp.content()).isEqualTo("{\"titleEn\":\"Access Policy\"}");
        assertThat(resp.inputTokens()).isEqualTo(420);
        assertThat(resp.outputTokens()).isEqualTo(180);
        assertThat(resp.finishReason()).isEqualTo("STOP");
    }

    @Test
    void localOpenAiAdapterWorksWithoutApiKeyForLocalOllamaOrVllm() {
        LlmProperties props = new LlmProperties();
        props.setMode(LlmProperties.Mode.LIVE);
        props.setProvider(LlmProperties.Provider.LOCAL_OPENAI);
        props.setBaseUrl("http://localhost:11434/v1/");
        props.setModel("qwen2.5:14b");
        props.setApiKey("");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LiveLlmClient client = new LiveLlmClient(props, builder.build());

        server.expect(requestTo("http://localhost:11434/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.model").value("qwen2.5:14b"))
            .andExpect(jsonPath("$.response_format.type").value("json_object"))
            .andRespond(withSuccess("""
                {
                  "model": "qwen2.5:14b",
                  "choices": [{
                    "message": { "role": "assistant", "content": "{\\"titleEn\\":\\"Local Policy\\"}" },
                    "finish_reason": "stop"
                  }],
                  "usage": { "prompt_tokens": 300, "completion_tokens": 150 }
                }
                """, MediaType.APPLICATION_JSON));

        LlmResponse resp = client.complete(sampleReq());
        server.verify();

        assertThat(resp.provider()).isEqualTo("LOCAL_OPENAI");
        assertThat(resp.model()).isEqualTo("qwen2.5:14b");
        assertThat(resp.inputTokens()).isEqualTo(300);
        assertThat(resp.outputTokens()).isEqualTo(150);
    }

    @Test
    void azureAndAnthropicAdaptersFormatProviderSpecificHeadersAndPaths() {
        LlmProperties azProps = new LlmProperties();
        azProps.setMode(LlmProperties.Mode.LIVE);
        azProps.setProvider(LlmProperties.Provider.AZURE_OPENAI);
        azProps.setBaseUrl("https://ksa-openai.openai.azure.com");
        azProps.setModel("gpt-4o-deploy");
        azProps.setAzureApiKey("az-key-999");

        RestClient.Builder azBuilder = RestClient.builder();
        MockRestServiceServer azServer = MockRestServiceServer.bindTo(azBuilder).build();
        LiveLlmClient azClient = new LiveLlmClient(azProps, azBuilder.build());

        azServer.expect(requestTo("https://ksa-openai.openai.azure.com/openai/deployments/gpt-4o-deploy/chat/completions?api-version=2024-10-21"))
            .andExpect(header("api-key", "az-key-999"))
            .andRespond(withSuccess("""
                {
                  "choices": [{ "message": { "content": "{\\"ok\\":true}" }, "finish_reason": "stop" }],
                  "usage": { "prompt_tokens": 50, "completion_tokens": 20 }
                }
                """, MediaType.APPLICATION_JSON));

        assertThat(azClient.complete(sampleReq()).content()).isEqualTo("{\"ok\":true}");
        azServer.verify();

        LlmProperties anProps = new LlmProperties();
        anProps.setMode(LlmProperties.Mode.LIVE);
        anProps.setProvider(LlmProperties.Provider.ANTHROPIC);
        anProps.setAnthropicBaseUrl("https://api.anthropic.com/v1");
        anProps.setModel("claude-3-5-sonnet-latest");
        anProps.setAnthropicApiKey("sk-ant-test");

        RestClient.Builder anBuilder = RestClient.builder();
        MockRestServiceServer anServer = MockRestServiceServer.bindTo(anBuilder).build();
        LiveLlmClient anClient = new LiveLlmClient(anProps, anBuilder.build());

        anServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
            .andExpect(header("x-api-key", "sk-ant-test"))
            .andExpect(header("anthropic-version", "2023-06-01"))
            .andRespond(withSuccess("""
                {
                  "content": [{ "type": "text", "text": "{\\"ok\\":true}" }],
                  "stop_reason": "end_turn",
                  "usage": { "input_tokens": 70, "output_tokens": 30 }
                }
                """, MediaType.APPLICATION_JSON));

        assertThat(anClient.complete(sampleReq()).outputTokens()).isEqualTo(30);
        anServer.verify();
    }

    @Test
    void retriesTransient5xxAndFailsFastOn4xx() {
        LlmProperties props = new LlmProperties();
        props.setMode(LlmProperties.Mode.LIVE);
        props.setProvider(LlmProperties.Provider.LOCAL_OPENAI);
        props.setBaseUrl("http://localhost:11434/v1");
        props.setMaxRetries(1);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LiveLlmClient client = new LiveLlmClient(props, builder.build());

        server.expect(requestTo("http://localhost:11434/v1/chat/completions"))
            .andRespond(withServiceUnavailable());
        server.expect(requestTo("http://localhost:11434/v1/chat/completions"))
            .andRespond(withSuccess("""
                { "choices": [{ "message": { "content": "{\\"recovered\\":true}" } }] }
                """, MediaType.APPLICATION_JSON));

        assertThat(client.complete(sampleReq()).content()).isEqualTo("{\"recovered\":true}");
        server.verify();

        server.reset();
        server.expect(requestTo("http://localhost:11434/v1/chat/completions"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("bad schema"));

        assertThatThrownBy(() -> client.complete(sampleReq()))
            .isInstanceOf(LlmGatewayException.class)
            .extracting(e -> ((LlmGatewayException) e).getErrorCode())
            .isEqualTo("PROVIDER_4XX");
        server.verify();
    }
}

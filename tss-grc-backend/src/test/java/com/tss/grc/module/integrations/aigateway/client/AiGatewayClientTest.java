package com.tss.grc.module.integrations.aigateway.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.integrations.aigateway.config.AiGatewayProperties;
import com.tss.grc.module.integrations.llm.dto.LlmGatewayException;
import com.tss.grc.module.integrations.llm.dto.LlmRequest;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

/**
 * Locks in that LIVE mode never answers with text no model produced, that an
 * upstream error body stays out of the exception, and that STUB mode returns a
 * labelled marker instead of fabricated content.
 */
class AiGatewayClientTest {

    private static final String COMPLETIONS_URI = "http://localhost:4000/v1/chat/completions";

    private AiGatewayProperties props;
    private MockRestServiceServer server;
    private AiGatewayClient client;

    @BeforeEach
    void setUp() {
        props = new AiGatewayProperties();
        props.setEnabled(true);
        props.setMode(AiGatewayProperties.Mode.LIVE);
        props.setBaseUrl("http://localhost:4000/v1");
        props.setModel("grc-default");
        props.setApiKey("test-key");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiGatewayClient(props, builder.build(), new ObjectMapper());
    }

    private static LlmRequest sampleReq(boolean jsonMode) {
        return new LlmRequest("POLICY_DRAFT", "policy_draft_bilingual", "v1",
            "sys", "Draft an access control policy as json", jsonMode, null, null, List.of());
    }

    @Test
    void liveTransportFailureThrowsGatewayUnavailable() {
        server.expect(requestTo(COMPLETIONS_URI))
            .andExpect(method(HttpMethod.POST))
            .andRespond(request -> {
                throw new ConnectException("Connection refused");
            });

        assertThatThrownBy(() -> client.complete(1L, sampleReq(true)))
            .isInstanceOfSatisfying(LlmGatewayException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("GATEWAY_UNAVAILABLE"))
            .hasCauseInstanceOf(ResourceAccessException.class)
            .hasRootCauseInstanceOf(ConnectException.class);

        server.verify();
    }

    @Test
    void liveHttpErrorKeepsUpstreamBodyOutOfTheException() {
        server.expect(requestTo(COMPLETIONS_URI))
            .andRespond(withServerError()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": \"upstream secret detail\"}"));

        assertThatThrownBy(() -> client.complete(1L, sampleReq(true)))
            .isInstanceOfSatisfying(LlmGatewayException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo("GATEWAY_ERROR"))
            .hasMessage("AI Gateway returned HTTP 500")
            .hasMessageNotContaining("secret");

        server.verify();
    }

    @Test
    void stubJsonReturnsLabelledMarkerWithoutCallingTheGateway() {
        props.setMode(AiGatewayProperties.Mode.STUB);

        LlmResponse resp = client.complete(1L, sampleReq(true));

        assertThat(resp.content()).isEqualTo(AiGatewayClient.STUB_JSON);
        assertThat(resp.provider()).isEqualTo("STUB");
        assertThat(resp.inputTokens()).isZero();
        assertThat(resp.outputTokens()).isZero();
        server.verify();
    }

    @Test
    void stubTextReturnsLabelledMarker() {
        props.setMode(AiGatewayProperties.Mode.STUB);

        LlmResponse resp = client.complete(1L, sampleReq(false));

        assertThat(resp.content()).isEqualTo(AiGatewayClient.STUB_TEXT);
        assertThat(resp.provider()).isEqualTo("STUB");
        server.verify();
    }
}

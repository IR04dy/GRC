package com.tss.grc.module.platform.help.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.help.dto.HelpDtos.HelpLookupResponse;
import com.tss.grc.module.platform.help.dto.HelpDtos.HelpTopicRequest;
import com.tss.grc.module.platform.help.dto.HelpDtos.HelpTopicResponse;
import com.tss.grc.module.platform.help.service.HelpTopicService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HelpController} — wiring smoke tests.
 *
 * <p>Locks dispatch from URL to service. {@code @PreAuthorize} role
 * guards are enforced by Spring at the proxy boundary.
 */
@ExtendWith(MockitoExtension.class)
class HelpControllerTest {

    private static final Long ORG_ID = 1L;
    private static final UUID EX = UUID.randomUUID();

    @Mock private HelpTopicService service;
    @InjectMocks private HelpController controller;

    @BeforeEach
    void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private static HelpTopicResponse stub(String code, String route) {
        return new HelpTopicResponse(
                1L, EX, ORG_ID, code,
                "Title", "العنوان",
                null, null,
                "## Body", "## النص",
                route, "RISK",
                "SAMA CSF 2.0", "https://sama.gov.sa",
                null, null,
                "INFO", 50,
                null, null
        );
    }

    @Test
    void lookup_dispatchesToService() {
        HelpLookupResponse rsp = new HelpLookupResponse("/risk", List.of(stub("HELP-RISK", "/risk")));
        when(service.lookupForRoute("/risk")).thenReturn(rsp);

        HelpLookupResponse out = controller.lookup("/risk");

        assertThat(out.topics()).hasSize(1);
        verify(service).lookupForRoute("/risk");
    }

    @Test
    void search_dispatchesToService() {
        when(service.searchTopics("risk")).thenReturn(List.of(stub("HELP-RISK", "/risk")));

        List<HelpTopicResponse> out = controller.search("risk");

        assertThat(out).hasSize(1);
        verify(service).searchTopics("risk");
    }

    @Test
    void create_returns201AndBody() {
        when(service.create(any(HelpTopicRequest.class))).thenReturn(stub("HELP-NEW", "/new"));

        HelpTopicRequest req = new HelpTopicRequest(
                "HELP-NEW", "Title", "العنوان", null, null,
                "## Body", "## النص", "/new", "RISK",
                null, null, null, null, "INFO", 50);

        ResponseEntity<HelpTopicResponse> resp = controller.create(req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().topicCode()).isEqualTo("HELP-NEW");
    }

    @Test
    void delete_returns204() {
        ResponseEntity<Void> resp = controller.delete(EX);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(EX);
    }

    @Test
    void trackView_returns202AndCallsService() {
        ResponseEntity<Void> resp = controller.trackView(EX, "/risk/register");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(service).trackView(eq(EX), eq("/risk/register"));
    }

    @Test
    void listAll_dispatchesToService() {
        when(service.listAll()).thenReturn(List.of(stub("HELP-A", "/a"), stub("HELP-B", "/b")));
        List<HelpTopicResponse> out = controller.listAll();
        assertThat(out).hasSize(2);
    }
}

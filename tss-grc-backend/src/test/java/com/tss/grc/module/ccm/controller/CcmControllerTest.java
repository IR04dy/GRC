package com.tss.grc.module.ccm.controller;

import com.tss.grc.module.ccm.dto.CcmDtos.TestDefinitionRequest;
import com.tss.grc.module.ccm.dto.CcmDtos.TestDefinitionResponse;
import com.tss.grc.module.ccm.dto.CcmDtos.TestRunResponse;
import com.tss.grc.module.ccm.service.CcmService;
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
 * Controller wiring smoke tests — the {@code @PreAuthorize} role guards
 * are enforced by Spring at the proxy boundary; here we lock the
 * dispatch from URL → service contract.
 */
@ExtendWith(MockitoExtension.class)
class CcmControllerTest {

    private static final UUID EX = UUID.randomUUID();

    @Mock private CcmService service;
    @InjectMocks private CcmController controller;

    private static TestDefinitionResponse stubDef() {
        return new TestDefinitionResponse(
                1L, EX, "CCM-AD-PRIVGROUP-001",
                "AD Domain Admins ≤ 5", "عدد مسؤولي النطاق لا يتجاوز 5",
                null, null,
                null,
                "LDAP_GROUP_MEMBERSHIP", "LDAP_AD",
                null, null,
                "CRITICAL", "0 0 6 * * *", 60,
                false, null, null, 0,
                null, null
        );
    }

    private static TestRunResponse stubRun() {
        return new TestRunResponse(
                1L, UUID.randomUUID(), 1L,
                "CCM-AD-PRIVGROUP-001",
                "AD Domain Admins ≤ 5",
                "عدد مسؤولي النطاق لا يتجاوز 5",
                "LDAP_GROUP_MEMBERSHIP", "LDAP_AD",
                "CRITICAL",
                null, 5,
                "PASS", "{\"members\":3}", null,
                "deadbeef".repeat(8),
                null
        );
    }

    @Test
    void listTests_dispatchesToService() {
        when(service.listDefinitions()).thenReturn(List.of(stubDef()));
        List<TestDefinitionResponse> out = controller.listTests();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).testCode()).isEqualTo("CCM-AD-PRIVGROUP-001");
        verify(service).listDefinitions();
    }

    @Test
    void enableTest_dispatchesToService() {
        when(service.enableTest(EX)).thenReturn(stubDef());
        TestDefinitionResponse out = controller.enableTest(EX);
        assertThat(out).isNotNull();
        verify(service).enableTest(EX);
    }

    @Test
    void runNow_dispatchesToService() {
        when(service.runTestNow(EX)).thenReturn(stubRun());
        TestRunResponse out = controller.runNow(EX);
        assertThat(out.status()).isEqualTo("PASS");
        verify(service).runTestNow(EX);
    }

    @Test
    void createTest_returns201AndBody() {
        when(service.createDefinition(any(TestDefinitionRequest.class))).thenReturn(stubDef());
        TestDefinitionRequest req = new TestDefinitionRequest(
                null, "AD Domain Admins ≤ 5", null, null, null, null,
                "LDAP_GROUP_MEMBERSHIP", "LDAP_AD",
                null, null, "CRITICAL", "0 0 6 * * *", 60
        );
        ResponseEntity<TestDefinitionResponse> resp = controller.createTest(req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void getRuns_passesLimitParam() {
        when(service.getRunsForTest(eq(EX), eq(25))).thenReturn(List.of(stubRun()));
        List<TestRunResponse> out = controller.getRuns(EX, 25);
        assertThat(out).hasSize(1);
        verify(service).getRunsForTest(EX, 25);
    }
}

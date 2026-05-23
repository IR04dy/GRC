package com.tss.grc.module.platform.push;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.push.controller.WebPushController;
import com.tss.grc.module.platform.push.dto.PushSubscriptionDtos.RegisterRequest;
import com.tss.grc.module.platform.push.dto.PushSubscriptionDtos.Response;
import com.tss.grc.module.platform.push.entity.WebPushSubscription;
import com.tss.grc.module.platform.push.repository.WebPushSubscriptionRepository;
import com.tss.grc.module.platform.push.service.WebPushService;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Slice 9-F — Slice-style controller test for the Web Push surface.
 * Drives the controller directly with a real {@link WebPushService} wired
 * to mocked repos / audit / publisher, mirroring how the CrowdStrike +
 * Splunk + ServiceNow controller tests work in this repo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebPushControllerTest {

    private static final Long ORG_ID  = 73L;
    private static final Long USER_ID = 4242L;

    @Mock private WebPushSubscriptionRepository repo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private WebPushController controller;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        var principal = new GrcUserDetails(USER_ID, ORG_ID, "commander",
            "x", true, true, List.of(new SimpleGrantedAuthority("ROLE_CRISIS_COMMANDER")));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));

        when(repo.save(any(WebPushSubscription.class))).thenAnswer(inv -> {
            WebPushSubscription s = inv.getArgument(0);
            if (s.getId() == null) s.setId(101L);
            return s;
        });

        WebPushService service = new WebPushService(repo, audit, publisher);
        controller = new WebPushController(service);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_returnsResponseWithoutEndpoint() {
        when(repo.findByOrganisationIdAndUserIdAndEndpoint(ORG_ID, USER_ID, "https://push.example/abc"))
            .thenReturn(Optional.empty());

        Response out = controller.register(
            new RegisterRequest("https://push.example/abc", "p", "a", "iPhone"));

        assertThat(out.id()).isEqualTo(101L);
        assertThat(out.deviceLabel()).isEqualTo("iPhone");
        // We expose userId for the "active devices" UI but never the endpoint.
        assertThat(out.userId()).isEqualTo(USER_ID);
    }

    @Test
    void list_returnsCurrentUsersDevices() {
        WebPushSubscription s = new WebPushSubscription();
        s.setId(101L); s.setOrganisationId(ORG_ID); s.setUserId(USER_ID);
        s.setEndpoint("https://push.example/a"); s.setP256dhKey("p"); s.setAuthKey("a");
        s.setDeviceLabel("iPad"); s.setEnabled(Boolean.TRUE); s.setIsDeleted(Boolean.FALSE);
        when(repo.findByOrganisationIdAndUserIdOrderByCreatedAtDesc(ORG_ID, USER_ID))
            .thenReturn(List.of(s));

        List<Response> out = controller.list();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).deviceLabel()).isEqualTo("iPad");
    }

    @Test
    void unregister_returns204() {
        WebPushSubscription s = new WebPushSubscription();
        s.setId(101L); s.setOrganisationId(ORG_ID); s.setUserId(USER_ID);
        s.setEndpoint("x"); s.setP256dhKey("p"); s.setAuthKey("a");
        s.setEnabled(Boolean.TRUE); s.setIsDeleted(Boolean.FALSE);
        when(repo.findByIdAndOrganisationId(101L, ORG_ID)).thenReturn(Optional.of(s));

        var resp = controller.unregister(101L);
        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }
}

package com.tss.grc.module.platform.push.controller;

import com.tss.grc.module.platform.push.dto.PushSubscriptionDtos.RegisterRequest;
import com.tss.grc.module.platform.push.dto.PushSubscriptionDtos.Response;
import com.tss.grc.module.platform.push.service.WebPushService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Slice 9-F — REST surface for browser PushManager subscriptions.
 *
 * <pre>
 *   POST   /api/v1/platform/push/subscriptions           — register a device
 *   GET    /api/v1/platform/push/subscriptions           — list the current user's devices
 *   DELETE /api/v1/platform/push/subscriptions/{id}      — unregister a device
 * </pre>
 *
 * <p>Authorisation is intentionally permissive — any authenticated user
 * can opt their own device into receiving notifications. Tenant + user
 * isolation is enforced inside {@link WebPushService}.
 */
@RestController
@RequestMapping("/api/v1/platform/push/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Platform — Web Push (Slice 9-F)")
@SecurityRequirement(name = "bearerAuth")
public class WebPushController {

    private final WebPushService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public Response register(@RequestBody @Valid RegisterRequest req) {
        return service.register(req);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Response> list() {
        return service.listForCurrentUser();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unregister(@PathVariable Long id) {
        service.unregister(id);
        return ResponseEntity.noContent().build();
    }
}

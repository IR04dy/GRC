package com.tss.grc.module.platform.controller;

import com.tss.grc.module.platform.dto.NotificationResponse;
import com.tss.grc.module.platform.service.NotificationService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal GrcUserDetails user) {
        return Map.of("count", notificationService.getUnreadCount(user.getUserId()));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<NotificationResponse> list(
        @AuthenticationPrincipal GrcUserDetails user,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return notificationService.listForUser(user.getUserId(), pageable);
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public void markRead(@PathVariable Long id, @AuthenticationPrincipal GrcUserDetails user) {
        notificationService.markAsRead(id, user.getUserId());
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public void markAllRead(@AuthenticationPrincipal GrcUserDetails user) {
        notificationService.markAllRead(user.getUserId());
    }
}

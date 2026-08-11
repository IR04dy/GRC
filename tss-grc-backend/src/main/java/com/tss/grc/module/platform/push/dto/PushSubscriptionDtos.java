package com.tss.grc.module.platform.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slice 9-F — DTOs for {@code /api/v1/platform/push/subscriptions}.
 *
 * <p>The {@link RegisterRequest} mirrors the JSON envelope produced by
 * the browser when it calls {@code JSON.stringify(pushSubscription)}.
 * The {@link Response} hides {@code endpoint} so a leaked list-API
 * response can't be replayed against the push service.
 */
public final class PushSubscriptionDtos {

    private PushSubscriptionDtos() {}

    /** Browser-side {@code PushSubscriptionJSON.toJSON()} shape + optional device label. */
    public record RegisterRequest(
        @NotBlank @Size(max = 2000) String endpoint,
        @NotBlank @Size(max = 255)  String p256dh,
        @NotBlank @Size(max = 255)  String auth,
        @Size(max = 255)            String deviceLabel
    ) {}

    /** Public view — endpoint deliberately omitted; we expose externalId for unsubscribe. */
    public record Response(
        Long id,
        UUID externalId,
        Long userId,
        String deviceLabel,
        Boolean enabled,
        LocalDateTime lastUsedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    /**
     * Outbound payload passed to {@code dispatchToUserIds(...)} — kept tiny on purpose.
     *
     * <p>F2-184 — its {@code @NotBlank}/{@code @Size} annotations are deleted. Nothing binds this
     * type from the wire; every instance is constructed in Java by a peer service, and bean
     * validation runs only against a {@code @Valid} parameter, so the annotations enforced nothing.
     * Contrast {@code SubscribeRequest} above, whose identical-looking annotations are real because
     * a controller binds it. <b>If these limits matter, they belong in {@code WebPushService} as a
     * check on the way out</b> — a push provider truncating a 300-character title is a runtime
     * concern, not a request-validation one.
     */
    public record DispatchPayload(
        String code,
        String titleAr,
        String titleEn,
        String severity,
        String deepLink
    ) {}
}

package com.tss.grc.module.platform.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
    Long id,
    String titleEn,
    String titleAr,
    String bodyEn,
    String bodyAr,
    String type,
    String severity,
    Boolean isRead,
    String linkUrl,
    LocalDateTime createdAt
) {}

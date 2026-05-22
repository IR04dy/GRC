package com.tss.grc.module.socops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Generic SIEM event ingest payload. Vendor-neutral — the connector
 * adapter on the SIEM side (Splunk add-on, QRadar app, Sentinel logic
 * app, etc.) maps that vendor's native schema to this shape.
 *
 * <p>The optional {@code rawPayload} carries the original alert JSON
 * so analysts can drill into vendor-specific details without us having
 * to model every field upstream.
 */
public record SiemEventIngestRequest(
    @NotBlank String externalId,
    @NotBlank String sourceSystem,
    @NotNull  LocalDateTime eventTime,
    @NotBlank String severity,
    String category,
    String ruleName,
    String sourceHost,
    String sourceIp,
    String destinationHost,
    String destinationIp,
    String username,
    Long assetId,
    String rawPayload
) {}

package com.tss.grc.module.ai.governance.controller;

import jakarta.persistence.EntityNotFoundException;

import java.util.UUID;

/**
 * Path-variable id parsing for the AI/ML registry controllers.
 *
 * <p>Model ids are external UUIDs. Binding the path variable as a raw
 * {@code UUID} makes Spring answer a malformed id (e.g. a bookmarked
 * {@code modelCode} such as {@code MODEL-2026-001}) with a generic
 * {@code 400 Invalid Parameter Type}. That reads as "bad request" when
 * the caller really just asked for something that does not exist — so we
 * bind as {@code String} and translate a parse failure into the same
 * {@code 404} a genuinely-missing model would produce.
 */
final class AiModelIds {

    private AiModelIds() {}

    static UUID parse(String externalId) {
        try {
            return UUID.fromString(externalId);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new EntityNotFoundException("AI model not found: " + externalId);
        }
    }
}

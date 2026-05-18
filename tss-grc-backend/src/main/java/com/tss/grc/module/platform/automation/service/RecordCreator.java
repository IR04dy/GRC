package com.tss.grc.module.platform.automation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tss.grc.module.platform.automation.event.DomainEvent;

/**
 * SPI for F8 CREATE_RECORD action handlers.
 *
 * <p>A {@link RecordCreator} bean tells {@link AutomationActionExecutor} which
 * entity type it can materialise (via {@link #entityType()}) and how to do it
 * (via {@link #create(JsonNode, DomainEvent)}). The executor looks up the
 * matching creator at rule-fire time; if none is registered for the requested
 * entity, the action falls back to the original audit-only behaviour.
 *
 * <p>This keeps the platform module free of dependencies on specific consuming
 * modules (BCM, PDPL, etc.) — each module registers its own creator(s) as
 * Spring beans, and the executor just discovers them by name.
 *
 * <p>Implementation contract:
 * <ul>
 *   <li>{@code entityType()} must return a stable, unique identifier that
 *       matches the {@code "entity"} key in rule action JSON
 *       (e.g. {@code "BcmCorrectiveAction"}).</li>
 *   <li>{@code create()} must be idempotent-safe — F8 may retry on transient
 *       failure. A common pattern is to derive a deterministic external key
 *       from {@code event.correlationId() + event.entityId()} and skip if
 *       the row already exists.</li>
 *   <li>{@code create()} must throw on validation failure (the executor will
 *       catch + log + record the run as FAILED).</li>
 *   <li>The return string is stored on the AutomationRuleRun summary, so make
 *       it human-readable: e.g. {@code "Created BcmCorrectiveAction#42"}.</li>
 * </ul>
 */
public interface RecordCreator {

    /** The entity-type identifier this creator handles. */
    String entityType();

    /**
     * Materialise the record. Receives the rule action JSON (with {@code fields},
     * {@code title_template}, etc.) and the source domain event so the creator
     * can pull payload data for templating.
     */
    String create(JsonNode action, DomainEvent event);
}

package com.tss.grc.module.integrations.crowdstrike.dto;

/**
 * Slim projection of a Falcon detection. Carries the subset of fields the
 * GRC platform needs to materialise a {@code socops.security_event} row and
 * an {@code integration_external_link} mapping.
 *
 * <p>Falcon's full detection envelope is much wider (behaviours, hashes,
 * device telemetry, parent process). The full JSON is captured in
 * {@code SecurityEvent.rawPayload}; this record is just the structured
 * portion we triage on.
 *
 * <p>Field naming follows Falcon's API verbatim so an analyst tailing a
 * dev log can grep for the same names they see in the Falcon console.
 *
 * @param detectionId        Falcon composite id — e.g. {@code ldt:<aid>:<seq>}.
 * @param deviceId           Falcon "aid" (agent id) — 32-char hex GUID
 *                           identifying the endpoint. Required for containment.
 * @param hostname           best-effort hostname Falcon resolved for the device.
 * @param severity           raw Falcon severity 1..100 (mapped to NCA ECC-2
 *                           buckets by the inbound service).
 * @param tactic             MITRE ATT&CK tactic name (e.g. "Defense Evasion").
 * @param technique          MITRE ATT&CK technique name.
 * @param mitreAttackId      MITRE ATT&CK id (e.g. T1078 or T1078.004).
 * @param detectionTimestamp ISO-8601 string Falcon stamped on the detection.
 * @param description        human-readable detection description from Falcon.
 */
public record FalconDetectionDto(
    String detectionId,
    String deviceId,
    String hostname,
    Integer severity,
    String tactic,
    String technique,
    String mitreAttackId,
    String detectionTimestamp,
    String description
) {}

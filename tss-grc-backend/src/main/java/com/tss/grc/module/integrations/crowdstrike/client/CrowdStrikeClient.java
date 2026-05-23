package com.tss.grc.module.integrations.crowdstrike.client;

import com.tss.grc.module.integrations.crowdstrike.dto.ContainmentResponse;
import com.tss.grc.module.integrations.crowdstrike.dto.FalconDetectionDto;

import java.util.List;

/**
 * Abstraction over the CrowdStrike Falcon API. Two implementations:
 *
 * <ul>
 *   <li>{@link StubCrowdStrikeClient} — in-memory simulator, default in dev/demo</li>
 *   <li>{@link LiveCrowdStrikeClient} — real HTTPS against {@code api.*.crowdstrike.com}</li>
 * </ul>
 *
 * <p>Selected by the {@code grc.integrations.crowdstrike.mode} property.
 *
 * <p>Method shape mirrors {@code SplunkClient} where it can — {@link #isHealthy()}
 * is the common probe; otherwise CrowdStrike's surface is bigger because the
 * adapter is bidirectional (pull detections + push containment).
 */
public interface CrowdStrikeClient {

    /**
     * Pull the next batch of detections newer than anything we've seen.
     * The implementation is responsible for tracking the high-water mark
     * across calls (the live client uses Falcon's
     * {@code /detects/queries/detects/v1} with a sort+filter cursor; the
     * stub returns deterministic batches based on its in-memory sequence).
     */
    List<FalconDetectionDto> pollDetections();

    /** Containment — isolate a Falcon-managed endpoint from the network. */
    ContainmentResponse containHost(String falconHostId, String reasonText);

    /** Lift containment — restore network connectivity to an isolated host. */
    ContainmentResponse liftContainment(String falconHostId);

    /** Hide a host from the Falcon console (used after decommission). */
    ContainmentResponse hideHost(String falconHostId);

    /**
     * True when Falcon is reachable and our OAuth token is valid. Cheap —
     * the stub always returns true; the live client probes
     * {@code GET /sensors/queries/installers/v1} with a {@code limit=1}.
     */
    boolean isHealthy();
}

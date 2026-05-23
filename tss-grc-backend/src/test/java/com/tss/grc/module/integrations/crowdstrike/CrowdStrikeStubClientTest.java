package com.tss.grc.module.integrations.crowdstrike;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.crowdstrike.client.StubCrowdStrikeClient;
import com.tss.grc.module.integrations.crowdstrike.config.CrowdStrikeProperties;
import com.tss.grc.module.integrations.crowdstrike.dto.ContainmentResponse;
import com.tss.grc.module.integrations.crowdstrike.dto.FalconDetectionDto;
import com.tss.grc.module.integrations.crowdstrike.dto.StubInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the stub CrowdStrike client's deterministic behaviour:
 *   - Detection ids follow {@code ldt:aid-fake-host-{n}:{seq}} with a
 *     per-org sequence counter.
 *   - Severities cycle 95 / 75 / 55 / 25 (one of each NCA bucket per batch).
 *   - A re-poll never re-emits the same detection id (stub-side dedupe).
 *   - Contain / lift record an invocation and return a 202.
 */
class CrowdStrikeStubClientTest {

    private StubCrowdStrikeClient stub;

    @BeforeEach
    void setUp() {
        CrowdStrikeProperties props = new CrowdStrikeProperties();
        props.setEnabled(true);
        stub = new StubCrowdStrikeClient(props);
        TenantContext.setCurrentTenant(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void pollDetections_returnsFourDeterministicDetections_perOrg() {
        List<FalconDetectionDto> batch = stub.pollDetections();
        assertThat(batch).hasSize(4);
        // First batch — sequences 1..4 against host-1..host-3 rotation
        assertThat(batch.get(0).detectionId()).isEqualTo("ldt:aid-fake-host-1:1");
        assertThat(batch.get(1).detectionId()).isEqualTo("ldt:aid-fake-host-2:2");
        assertThat(batch.get(2).detectionId()).isEqualTo("ldt:aid-fake-host-3:3");
        assertThat(batch.get(3).detectionId()).isEqualTo("ldt:aid-fake-host-1:4");
        // Severities cycle: 95 / 75 / 55 / 25 — one per NCA bucket
        assertThat(batch.get(0).severity()).isEqualTo(95);
        assertThat(batch.get(1).severity()).isEqualTo(75);
        assertThat(batch.get(2).severity()).isEqualTo(55);
        assertThat(batch.get(3).severity()).isEqualTo(25);
    }

    @Test
    void pollDetections_isDeduped_doesNotReemitSameIds_acrossPolls() {
        List<FalconDetectionDto> first = stub.pollDetections();
        List<FalconDetectionDto> second = stub.pollDetections();
        assertThat(first).hasSize(4);
        // second poll keeps incrementing the sequence; ids do not overlap with first
        assertThat(second).hasSize(4);
        assertThat(second.get(0).detectionId()).isEqualTo("ldt:aid-fake-host-2:5");
        assertThat(second).extracting(FalconDetectionDto::detectionId)
                          .doesNotContainAnyElementsOf(
                              first.stream().map(FalconDetectionDto::detectionId).toList());
    }

    @Test
    void pollDetections_isPerOrg_independent() {
        TenantContext.setCurrentTenant(1L);
        List<FalconDetectionDto> a = stub.pollDetections();
        TenantContext.setCurrentTenant(2L);
        List<FalconDetectionDto> b = stub.pollDetections();
        assertThat(a.get(0).detectionId()).isEqualTo("ldt:aid-fake-host-1:1");
        assertThat(b.get(0).detectionId()).isEqualTo("ldt:aid-fake-host-1:1");
    }

    @Test
    void containHost_recordsCall_andReturnsAccepted() {
        ContainmentResponse resp = stub.containHost("aid-host-9", "ransomware suspected");
        assertThat(resp.accepted()).isTrue();
        assertThat(resp.httpStatus()).isEqualTo(202);
        assertThat(resp.actionType()).isEqualTo("CONTAIN_HOST");
        assertThat(resp.targetHostId()).isEqualTo("aid-host-9");
        assertThat(resp.falconResourceId()).contains("aid-host-9");
        assertThat(resp.attemptCount()).isEqualTo(1);

        StubInvocation only = stub.getRecordedCalls().get(stub.getRecordedCalls().size() - 1);
        assertThat(only.method()).isEqualTo("CONTAIN");
        assertThat(only.targetId()).isEqualTo("aid-host-9");
        assertThat(only.payload()).containsEntry("reason", "ransomware suspected");
    }

    @Test
    void liftContainment_recordsCall_andReturnsAccepted() {
        ContainmentResponse resp = stub.liftContainment("aid-host-9");
        assertThat(resp.accepted()).isTrue();
        assertThat(resp.actionType()).isEqualTo("LIFT_CONTAINMENT");

        StubInvocation only = stub.getRecordedCalls().get(stub.getRecordedCalls().size() - 1);
        assertThat(only.method()).isEqualTo("LIFT");
        assertThat(only.targetId()).isEqualTo("aid-host-9");
    }

    @Test
    void isHealthy_alwaysTrue_andRecorded() {
        assertThat(stub.isHealthy()).isTrue();
        StubInvocation last = stub.getRecordedCalls().get(stub.getRecordedCalls().size() - 1);
        assertThat(last.method()).isEqualTo("HEALTH");
    }

    @Test
    void reset_clearsSequencesAndEmittedIds_andCalls() {
        stub.pollDetections();
        stub.reset();
        assertThat(stub.getRecordedCalls()).isEmpty();
        List<FalconDetectionDto> after = stub.pollDetections();
        assertThat(after.get(0).detectionId()).isEqualTo("ldt:aid-fake-host-1:1");
    }
}

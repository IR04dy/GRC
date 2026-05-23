package com.tss.grc.module.integrations.tenable;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.tenable.client.StubTenableClient;
import com.tss.grc.module.integrations.tenable.config.TenableProperties;
import com.tss.grc.module.integrations.tenable.dto.RecastResponse;
import com.tss.grc.module.integrations.tenable.dto.StubInvocation;
import com.tss.grc.module.integrations.tenable.dto.TenableFindingDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the stub Tenable client's deterministic behaviour:
 *   - Finding ids follow {@code TEN-FND-{seq}} with a per-org sequence counter.
 *   - Severities cycle CRITICAL / HIGH / MEDIUM / LOW / INFO — one per NCA bucket per batch.
 *   - A re-poll never re-emits the same (asset, plugin) pair (stub-side dedupe).
 *   - Recast / accept-risk / remove-recast record invocations and return 200.
 */
class TenableStubClientTest {

    private StubTenableClient stub;

    @BeforeEach
    void setUp() {
        TenableProperties props = new TenableProperties();
        props.setEnabled(true);
        stub = new StubTenableClient(props);
        TenantContext.setCurrentTenant(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void pollFindings_returnsFiveDeterministicFindings_perOrg() {
        List<TenableFindingDto> batch = stub.pollFindings();
        assertThat(batch).hasSize(5);
        // First batch — sequences 1..5
        assertThat(batch.get(0).pluginId()).isEqualTo("TEN-FND-1");
        assertThat(batch.get(1).pluginId()).isEqualTo("TEN-FND-2");
        assertThat(batch.get(2).pluginId()).isEqualTo("TEN-FND-3");
        assertThat(batch.get(3).pluginId()).isEqualTo("TEN-FND-4");
        assertThat(batch.get(4).pluginId()).isEqualTo("TEN-FND-5");
        // Severities cycle — one per NCA bucket
        assertThat(batch.get(0).severity()).isEqualTo("CRITICAL");
        assertThat(batch.get(1).severity()).isEqualTo("HIGH");
        assertThat(batch.get(2).severity()).isEqualTo("MEDIUM");
        assertThat(batch.get(3).severity()).isEqualTo("LOW");
        assertThat(batch.get(4).severity()).isEqualTo("INFO");
        // CVSS aligned with severity
        assertThat(batch.get(0).cvssV3Score()).isEqualTo(new BigDecimal("9.8"));
        assertThat(batch.get(4).cvssV3Score()).isEqualTo(new BigDecimal("0.0"));
        // CVE id populated
        assertThat(batch.get(0).cveId()).startsWith("CVE-");
    }

    @Test
    void pollFindings_isDeduped_doesNotReemitSameAssetPluginPair_acrossPolls() {
        List<TenableFindingDto> first = stub.pollFindings();
        List<TenableFindingDto> second = stub.pollFindings();
        assertThat(first).hasSize(5);
        // second poll keeps incrementing the sequence; ids do not overlap with first
        assertThat(second).hasSize(5);
        assertThat(second.get(0).pluginId()).isEqualTo("TEN-FND-6");
        assertThat(second).extracting(TenableFindingDto::pluginId)
                          .doesNotContainAnyElementsOf(
                              first.stream().map(TenableFindingDto::pluginId).toList());
    }

    @Test
    void pollFindings_isPerOrg_independent() {
        TenantContext.setCurrentTenant(1L);
        List<TenableFindingDto> a = stub.pollFindings();
        TenantContext.setCurrentTenant(2L);
        List<TenableFindingDto> b = stub.pollFindings();
        assertThat(a.get(0).pluginId()).isEqualTo("TEN-FND-1");
        assertThat(b.get(0).pluginId()).isEqualTo("TEN-FND-1");
    }

    @Test
    void recastSeverity_recordsCall_andReturnsAccepted() {
        RecastResponse resp = stub.recastSeverity(
            "TEN-FND-1", "fake-asset-1", "LOW",
            "Recasted because the plugin reports a vuln on an isolated test VLAN");
        assertThat(resp.accepted()).isTrue();
        assertThat(resp.httpStatus()).isEqualTo(200);
        assertThat(resp.actionType()).isEqualTo("RECAST_SEVERITY");
        assertThat(resp.targetPluginId()).isEqualTo("TEN-FND-1");
        assertThat(resp.targetAssetUuid()).isEqualTo("fake-asset-1");
        assertThat(resp.tenableRuleId()).contains("ten-fnd-1");
        assertThat(resp.attemptCount()).isEqualTo(1);

        StubInvocation only = stub.getRecordedCalls().get(stub.getRecordedCalls().size() - 1);
        assertThat(only.method()).isEqualTo("RECAST");
        assertThat(only.targetId()).isEqualTo("TEN-FND-1");
        assertThat(only.payload()).containsEntry("newSeverity", "LOW");
        assertThat(only.payload()).containsEntry("assetUuid", "fake-asset-1");
    }

    @Test
    void acceptRisk_recordsCall_andReturnsAccepted() {
        LocalDateTime exp = LocalDateTime.of(2026, 12, 31, 23, 59);
        RecastResponse resp = stub.acceptRisk(
            "TEN-FND-9", null,
            "Accepted by CISO until vendor patch ships — tracked as RISK-2026-114",
            exp);
        assertThat(resp.accepted()).isTrue();
        assertThat(resp.actionType()).isEqualTo("ACCEPT_RISK");
        assertThat(resp.targetAssetUuid()).isNull(); // global

        StubInvocation only = stub.getRecordedCalls().get(stub.getRecordedCalls().size() - 1);
        assertThat(only.method()).isEqualTo("ACCEPT_RISK");
        assertThat(only.payload()).containsEntry("expiresAt", exp.toString());
        assertThat(only.payload()).doesNotContainKey("assetUuid"); // global recast omits asset
    }

    @Test
    void removeRecast_recordsCall_andReturnsAccepted() {
        RecastResponse resp = stub.removeRecast("TEN-FND-7", "fake-asset-2");
        assertThat(resp.accepted()).isTrue();
        assertThat(resp.actionType()).isEqualTo("REMOVE_RECAST");

        StubInvocation only = stub.getRecordedCalls().get(stub.getRecordedCalls().size() - 1);
        assertThat(only.method()).isEqualTo("REMOVE_RECAST");
        assertThat(only.targetId()).isEqualTo("TEN-FND-7");
    }

    @Test
    void isHealthy_alwaysTrue_andRecorded() {
        assertThat(stub.isHealthy()).isTrue();
        StubInvocation last = stub.getRecordedCalls().get(stub.getRecordedCalls().size() - 1);
        assertThat(last.method()).isEqualTo("HEALTH");
    }

    @Test
    void reset_clearsSequencesAndEmittedIds_andCalls() {
        stub.pollFindings();
        stub.reset();
        assertThat(stub.getRecordedCalls()).isEmpty();
        List<TenableFindingDto> after = stub.pollFindings();
        assertThat(after.get(0).pluginId()).isEqualTo("TEN-FND-1");
    }
}

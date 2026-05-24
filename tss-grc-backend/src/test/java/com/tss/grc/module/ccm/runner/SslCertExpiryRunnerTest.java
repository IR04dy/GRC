package com.tss.grc.module.ccm.runner;

import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SslCertExpiryRunnerTest {

    private static ControlTestDefinition def(int minDays) {
        ControlTestDefinition d = new ControlTestDefinition();
        d.setTestCode("CCM-SSL-CERT-001");
        d.setTestType("SSL_CERT_EXPIRY");
        d.setTargetSystemType("CUSTOM");
        d.setTimeoutSeconds(30);
        d.setTargetConfigJson("{\"host\":\"banking.tss-bank.sa\",\"port\":443}");
        d.setExpectedConditionJson("{\"minDaysToExpiry\":" + minDays + "}");
        return d;
    }

    @Test
    void passWhenExpiryBeyondThreshold() {
        Instant notAfter = Instant.now().plus(120, ChronoUnit.DAYS);
        SslCertExpiryRunner runner = new SslCertExpiryRunner((host, port, ms) -> notAfter);
        ControlTestRunner.RunResult r = runner.execute(def(30));
        assertThat(r.status()).isEqualTo("PASS");
        assertThat(r.actualValueJson()).contains("\"host\":\"banking.tss-bank.sa\"");
    }

    @Test
    void failWhenExpiryWithinThreshold() {
        Instant notAfter = Instant.now().plus(10, ChronoUnit.DAYS);
        SslCertExpiryRunner runner = new SslCertExpiryRunner((host, port, ms) -> notAfter);
        ControlTestRunner.RunResult r = runner.execute(def(30));
        assertThat(r.status()).isEqualTo("FAIL");
        assertThat(r.failureReason()).contains("days");
    }

    @Test
    void errorOnUnreachableHost() {
        SslCertExpiryRunner runner = new SslCertExpiryRunner((host, port, ms) -> {
            throw new RuntimeException("Connection refused");
        });
        ControlTestRunner.RunResult r = runner.execute(def(30));
        assertThat(r.status()).isEqualTo("ERROR");
        assertThat(r.failureReason()).contains("Connection refused");
    }

    @Test
    void errorWhenHostMissing() {
        ControlTestDefinition d = def(30);
        d.setTargetConfigJson("{}");
        SslCertExpiryRunner runner = new SslCertExpiryRunner((host, port, ms) -> Instant.now());
        ControlTestRunner.RunResult r = runner.execute(d);
        assertThat(r.status()).isEqualTo("ERROR");
        assertThat(r.failureReason()).contains("host missing");
    }
}

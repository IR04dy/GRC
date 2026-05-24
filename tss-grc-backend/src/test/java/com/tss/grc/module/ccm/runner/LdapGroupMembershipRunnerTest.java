package com.tss.grc.module.ccm.runner;

import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LdapGroupMembershipRunnerTest {

    private static ControlTestDefinition def(int maxMembers) {
        ControlTestDefinition d = new ControlTestDefinition();
        d.setTestCode("CCM-AD-PRIVGROUP-001");
        d.setTestType("LDAP_GROUP_MEMBERSHIP");
        d.setTargetSystemType("LDAP_AD");
        d.setTargetConfigJson("{\"url\":\"ldap://test\",\"bindDn\":\"cn=svc\"}");
        d.setExpectedConditionJson("{\"groupDn\":\"CN=Domain Admins\",\"maxMembers\":" + maxMembers + "}");
        return d;
    }

    @Test
    void passWhenMembersBelowThreshold() {
        LdapGroupMembershipRunner runner = new LdapGroupMembershipRunner(true, (url, bind, group) -> 3);
        ControlTestRunner.RunResult r = runner.execute(def(5));
        assertThat(r.status()).isEqualTo("PASS");
        assertThat(r.actualValueJson()).contains("\"memberCount\":3");
    }

    @Test
    void failWhenMembersExceedThreshold() {
        LdapGroupMembershipRunner runner = new LdapGroupMembershipRunner(true, (url, bind, group) -> 11);
        ControlTestRunner.RunResult r = runner.execute(def(5));
        assertThat(r.status()).isEqualTo("FAIL");
        assertThat(r.failureReason()).contains("11 members");
    }

    @Test
    void errorOnConnectionFailure() {
        LdapGroupMembershipRunner runner = new LdapGroupMembershipRunner(true, (url, bind, group) -> {
            throw new RuntimeException("LDAP bind failed: refused");
        });
        ControlTestRunner.RunResult r = runner.execute(def(5));
        assertThat(r.status()).isEqualTo("ERROR");
        assertThat(r.failureReason()).contains("LDAP bind failed");
    }

    @Test
    void skippedWhenDisabled() {
        LdapGroupMembershipRunner runner = new LdapGroupMembershipRunner(false, (url, bind, group) -> 3);
        ControlTestRunner.RunResult r = runner.execute(def(5));
        assertThat(r.status()).isEqualTo("SKIPPED");
    }
}

package com.tss.grc.module.cyber.controller;

import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.service.PolicyManagementService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * CY-I — {@link CyberPolicyController} category scoping. The cyber surface wraps
 * the shared policy engine but must only ever expose / create policies in the
 * cyber category space, so a compliance / finance / HR policy can't leak in or
 * out through the cyber endpoints.
 */
@ExtendWith(MockitoExtension.class)
class CyberPolicyControllerTest {

    @Mock private PolicyManagementService policy;
    @InjectMocks private CyberPolicyController controller;

    private static PolicyDocument doc(long id, String category, String status) {
        PolicyDocument p = new PolicyDocument();
        p.setId(id);
        p.setPolicyCode("POL-" + id);
        p.setVersion("1.0");
        p.setTitleEn("Policy " + id);
        p.setCategory(category);
        p.setStatus(status);
        return p;
    }

    @Test
    void list_filtersToCyberCategories() {
        when(policy.list(isNull(), isNull(), eq(false))).thenReturn(List.of(
                doc(1, "CYBER", "DRAFT"),
                doc(2, "FINANCE", "PUBLISHED"),
                doc(3, "INFORMATION_SECURITY", "PUBLISHED"),
                doc(4, "INCIDENT_RESPONSE", "REVIEW"),
                doc(5, "HR", "DRAFT")));
        assertThat(controller.list(null, false))
                .extracting(PolicyDocument::getCategory)
                .containsExactlyInAnyOrder("CYBER", "INFORMATION_SECURITY", "INCIDENT_RESPONSE");
    }

    @Test
    void list_appliesStatusFilterWithinCyber() {
        when(policy.list(isNull(), isNull(), eq(false))).thenReturn(List.of(
                doc(1, "CYBER", "DRAFT"), doc(2, "CYBER", "PUBLISHED"), doc(3, "FINANCE", "PUBLISHED")));
        assertThat(controller.list("PUBLISHED", false))
                .extracting(PolicyDocument::getId).containsExactly(2L);
    }

    @Test
    void get_nonCyberPolicy_throws404() {
        when(policy.get(9L)).thenReturn(doc(9, "FINANCE", "PUBLISHED"));
        assertThatThrownBy(() -> controller.get(9L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void get_cyberPolicy_ok() {
        when(policy.get(1L)).thenReturn(doc(1, "CYBER", "DRAFT"));
        assertThat(controller.get(1L).getCategory()).isEqualTo("CYBER");
    }

    @Test
    void create_forcesCyberCategoryWhenMissing() {
        when(policy.create(any())).thenAnswer(inv -> inv.getArgument(0));
        PolicyDocument body = new PolicyDocument();
        body.setPolicyCode("X"); body.setTitleEn("t");   // category null
        assertThat(controller.create(body).getBody().getCategory()).isEqualTo("CYBER");
    }

    @Test
    void create_rewritesOutOfScopeCategoryToCyber() {
        when(policy.create(any())).thenAnswer(inv -> inv.getArgument(0));
        PolicyDocument body = new PolicyDocument();
        body.setPolicyCode("X"); body.setTitleEn("t"); body.setCategory("FINANCE");
        assertThat(controller.create(body).getBody().getCategory()).isEqualTo("CYBER");
    }

    @Test
    void create_keepsInScopeInfoSecCategory() {
        when(policy.create(any())).thenAnswer(inv -> inv.getArgument(0));
        PolicyDocument body = new PolicyDocument();
        body.setPolicyCode("X"); body.setTitleEn("t"); body.setCategory("INFORMATION_SECURITY");
        assertThat(controller.create(body).getBody().getCategory()).isEqualTo("INFORMATION_SECURITY");
    }
}

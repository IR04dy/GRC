package com.tss.grc.module.policy.drafting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.policy.drafting.service.PolicyDraftOutputValidator.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyDraftOutputValidatorTest {

    private final PolicyDraftOutputValidator validator =
        new PolicyDraftOutputValidator(new ObjectMapper());

    @Test
    void stripsMarkdownCodeFencesAndQuarantinesHallucinatedControlIds() {
        String raw = """
            ```json
            {
              "titleEn": "Cloud Security Policy",
              "titleAr": "سياسة أمن الحوسبة السحابية",
              "summaryEn": "Cloud controls.",
              "summaryAr": "ضوابط السحابة.",
              "contentMdEn": "## Purpose\\nSecure cloud workloads.",
              "contentMdAr": "## الغرض\\nحماية أعباء العمل السحابية.",
              "citedControlIds": [101, 999999, "102"],
              "suggestedReviewCadence": "biennial",
              "assumptions": ["NCA CCC-1 applies"]
            }
            ```
            """;

        ValidationResult res = validator.validateAndSanitize(raw, Set.of(101L, 102L));
        assertThat(res.content().titleEn()).isEqualTo("Cloud Security Policy");
        assertThat(res.content().titleAr()).isEqualTo("سياسة أمن الحوسبة السحابية");
        assertThat(res.content().citedControlIds()).containsExactly(101L, 102L);
        assertThat(res.hallucinatedCitations()).containsExactly(999999L);
        assertThat(res.content().suggestedReviewCadence()).isEqualTo("BIENNIAL");
    }

    @Test
    void rejectsMissingArabicTitleOrBody() {
        String missingArabic = """
            {
              "titleEn": "Access Policy",
              "titleAr": "   ",
              "contentMdEn": "## Purpose\\nEnglish only.",
              "contentMdAr": "## الغرض\\nنص عربي"
            }
            """;
        assertThatThrownBy(() -> validator.validateAndSanitize(missingArabic, Set.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("titleAr");
    }
}

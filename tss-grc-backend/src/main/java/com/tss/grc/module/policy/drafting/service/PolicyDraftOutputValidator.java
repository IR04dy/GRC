package com.tss.grc.module.policy.drafting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.policy.drafting.dto.PolicyDraftDtos.GeneratedPolicyContent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validates and normalises raw LLM output for policy drafting, stripping
 * accidental Markdown code fences (common on local quantized models) and
 * quarantining any control IDs not present in the tenant's real control set.
 */
@Component
public class PolicyDraftOutputValidator {

    private static final Set<String> ALLOWED_CADENCES = Set.of(
        "ANNUAL", "BIENNIAL", "TRIENNIAL", "AD_HOC"
    );

    private final ObjectMapper objectMapper;

    public PolicyDraftOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record ValidationResult(
        GeneratedPolicyContent content,
        List<Long> hallucinatedCitations
    ) {}

    public ValidationResult validateAndSanitize(String rawText, Set<Long> allowedControlIds) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("Model returned empty output");
        }
        String jsonText = stripCodeFences(rawText.trim());
        JsonNode root;
        try {
            root = objectMapper.readTree(jsonText);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Model output is not valid JSON: " + ex.getMessage(), ex);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Model output root must be a JSON object");
        }

        String titleEn = requireText(root, "titleEn", 255);
        String titleAr = requireText(root, "titleAr", 255);
        String summaryEn = optionalText(root, "summaryEn");
        String summaryAr = optionalText(root, "summaryAr");
        String contentMdEn = requireText(root, "contentMdEn", 0);
        String contentMdAr = requireText(root, "contentMdAr", 0);

        Set<Long> validIds = new LinkedHashSet<>();
        Set<Long> hallucinatedIds = new LinkedHashSet<>();
        Set<Long> allowed = allowedControlIds == null ? Set.of() : allowedControlIds;

        JsonNode citedNode = root.get("citedControlIds");
        if (citedNode != null && citedNode.isArray()) {
            for (JsonNode item : citedNode) {
                Long id = parseLong(item);
                if (id == null) {
                    continue;
                }
                if (allowed.contains(id)) {
                    validIds.add(id);
                } else {
                    hallucinatedIds.add(id);
                }
            }
        }

        String rawCadence = optionalText(root, "suggestedReviewCadence");
        String cadence = "ANNUAL";
        if (!rawCadence.isBlank()) {
            String upper = rawCadence.trim().toUpperCase(Locale.ROOT);
            if (ALLOWED_CADENCES.contains(upper)) {
                cadence = upper;
            }
        }

        List<String> assumptions = new ArrayList<>();
        JsonNode assumpNode = root.get("assumptions");
        if (assumpNode != null && assumpNode.isArray()) {
            for (JsonNode a : assumpNode) {
                if (a != null && a.isTextual() && !a.asText().isBlank()) {
                    assumptions.add(a.asText().trim());
                }
            }
        }

        GeneratedPolicyContent content = new GeneratedPolicyContent(
            titleEn,
            titleAr,
            summaryEn,
            summaryAr,
            contentMdEn,
            contentMdAr,
            List.copyOf(validIds),
            cadence,
            List.copyOf(assumptions)
        );
        return new ValidationResult(content, List.copyOf(hallucinatedIds));
    }

    static String stripCodeFences(String raw) {
        String s = raw;
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) {
                s = s.substring(firstNewline + 1);
            }
            int closingFence = s.lastIndexOf("```");
            if (closingFence >= 0) {
                s = s.substring(0, closingFence);
            }
            return s.trim();
        }
        int firstBrace = s.indexOf('{');
        int lastBrace = s.lastIndexOf('}');
        if (firstBrace > 0 && lastBrace > firstBrace) {
            return s.substring(firstBrace, lastBrace + 1).trim();
        }
        return s;
    }

    private static String requireText(JsonNode root, String field, int maxLen) {
        String val = optionalText(root, field);
        if (val.isBlank()) {
            throw new IllegalArgumentException("Missing or blank required field: " + field);
        }
        if (maxLen > 0 && val.length() > maxLen) {
            return val.substring(0, maxLen).trim();
        }
        return val;
    }

    private static String optionalText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("").trim();
    }

    private static Long parseLong(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

package com.tss.grc.module.platform.controls.ai;

import com.tss.grc.module.platform.controls.ai.AiControlMappingService.SuggestionRequest;
import com.tss.grc.module.platform.controls.ai.AiControlMappingService.SuggestionResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI-assisted control mapping — Sprint-8 Slice 8-E.
 *
 * <pre>
 *   POST /api/v1/controls/ai/suggest
 *   Body: SuggestionRequest (description, domain?, topN?)
 *   Returns: SuggestionResponse (top-N suggestions with confidence + rationale)
 * </pre>
 *
 * <p>Deterministic scoring (Jaccard + Saudi keyword boost + code-mention
 * boost). No LLM dependency — auditable + reproducible. Future slice can
 * layer a Claude-API scorer alongside this baseline.
 */
@RestController
@RequestMapping("/api/v1/controls/ai")
@RequiredArgsConstructor
@Tag(name = "Controls — AI Mapping")
public class AiControlMappingController {

    private final AiControlMappingService service;

    @PostMapping("/suggest")
    @PreAuthorize("isAuthenticated()")
    public SuggestionResponse suggest(@Valid @RequestBody SuggestionRequest body) {
        return service.suggest(body);
    }
}

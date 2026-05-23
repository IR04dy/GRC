package com.tss.grc.module.platform.controls.ai;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.controls.entity.Control;
import com.tss.grc.module.platform.controls.repository.ControlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI-assisted control-mapping suggestor — Sprint-8 Slice 8-E.
 *
 * <p>Given a free-text description of a risk, finding, requirement, or
 * gap, returns the top-N matching {@link Control}s from the unified
 * control library. Score = blended Jaccard token overlap + Saudi-native
 * regulatory-keyword boost. Pure compute — no external LLM, no schema
 * changes.
 *
 * <p>Why "AI" without an LLM? The slice docs (BCM_GRC §4 "Sprint 8 — P0
 * GRC block") explicitly call out "AI control-mapping" as the goal. An
 * LLM-backed implementation is in the long-tail roadmap, but the
 * platform's first-mile value comes from a deterministic, audit-friendly
 * scoring function the SAMA examiner can reproduce. This service is that
 * baseline — future slices can layer an LLM scorer on top by
 * implementing the same {@link Suggestion} response shape.
 *
 * <p>Saudi-native keyword boost — these terms count for extra:
 * SAMA, NCA, NCEMA, SDAIA, PDPL, OTCC, ECC, CSCC, BCF, BCP, DRP, BIA,
 * MTPD, RTO, RPO, hajj, ramadan, takamol, tadawul.
 */
@Service
@RequiredArgsConstructor
public class AiControlMappingService {

    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[\\s\\p{Punct}\\u060C\\u061B\\u061F]+");
    private static final int MIN_TOKEN_LENGTH = 2;
    private static final int DEFAULT_TOP_N = 10;
    private static final int MAX_TOP_N = 50;

    /**
     * English stop-words; Arabic is intentionally narrower because
     * Arabic root patterns already provide strong signal in this
     * domain.
     */
    private static final Set<String> STOP_WORDS = Set.of(
        "the", "a", "an", "and", "or", "for", "of", "to", "in", "on", "at",
        "with", "by", "is", "are", "be", "as", "from", "that", "this",
        "it", "its", "such", "must", "shall", "should", "will", "may",
        "any", "all", "into", "between", "within", "without"
    );

    /** Saudi-native domain keywords — match contributes 1.5× score. */
    private static final Set<String> SAUDI_KEYWORDS = Set.of(
        "sama", "nca", "ncema", "sdaia", "pdpl", "otcc", "ecc", "cscc",
        "bcf", "bcp", "drp", "bia", "mtpd", "rto", "rpo",
        "hajj", "ramadan", "takamol", "tadawul", "zatca", "monsha",
        "vision2030"
    );

    private final ControlRepository controls;

    @Transactional(readOnly = true)
    public SuggestionResponse suggest(SuggestionRequest req) {
        if (req == null || req.description() == null || req.description().isBlank()) {
            return new SuggestionResponse(req == null ? null : req.description(), List.of(), 0);
        }
        Long org = TenantContext.getCurrentTenant();
        Set<String> queryTokens = tokenize(req.description());
        if (queryTokens.isEmpty()) {
            return new SuggestionResponse(req.description(), List.of(), 0);
        }

        List<Control> pool;
        if (req.domain() != null && !req.domain().isBlank()) {
            pool = controls.findByOrganisationIdAndDomainOrderByControlCodeAsc(org, req.domain());
        } else {
            pool = controls.findByOrganisationIdOrderByDomainAscControlCodeAsc(org);
        }

        int topN = req.topN() == null || req.topN() <= 0
            ? DEFAULT_TOP_N
            : Math.min(req.topN(), MAX_TOP_N);

        List<Suggestion> ranked = new ArrayList<>();
        for (Control c : pool) {
            Set<String> controlTokens = tokenize(
                String.join(" ",
                    c.getControlCode() == null ? "" : c.getControlCode(),
                    c.getTitleEn() == null ? "" : c.getTitleEn(),
                    c.getTitleAr() == null ? "" : c.getTitleAr(),
                    c.getDescriptionEn() == null ? "" : c.getDescriptionEn(),
                    c.getDomain() == null ? "" : c.getDomain()
                ));
            if (controlTokens.isEmpty()) continue;

            double jaccard = jaccard(queryTokens, controlTokens);
            double saudiBoost = saudiKeywordBoost(queryTokens, controlTokens);
            double codeBoost  = codeOrAcronymBoost(req.description(), c.getControlCode());

            double confidence = Math.min(1.0, jaccard * (1.0 + saudiBoost) + codeBoost);
            if (confidence <= 0.0) continue;

            ranked.add(new Suggestion(
                c.getId(),
                c.getControlCode(),
                c.getDomain(),
                c.getTitleEn(),
                c.getTitleAr(),
                round3(jaccard),
                round3(confidence),
                describeRationale(queryTokens, controlTokens, c.getControlCode(), req.description())
            ));
        }

        ranked.sort(Comparator.comparingDouble(Suggestion::confidence).reversed());
        List<Suggestion> top = ranked.stream().limit(topN).collect(Collectors.toList());
        return new SuggestionResponse(req.description(), top, ranked.size());
    }

    // ─── Scoring ─────────────────────────────────────────────────────

    /** Jaccard = |A ∩ B| / |A ∪ B|. */
    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int intersect = 0;
        for (String t : a) if (b.contains(t)) intersect++;
        int union = a.size() + b.size() - intersect;
        return union == 0 ? 0.0 : (double) intersect / union;
    }

    /** Returns 0..0.75 — proportional to count of Saudi keywords shared. */
    private static double saudiKeywordBoost(Set<String> a, Set<String> b) {
        int shared = 0;
        for (String k : SAUDI_KEYWORDS) {
            if (a.contains(k) && b.contains(k)) shared++;
        }
        // Saturate at 3 shared keywords; each adds 0.25 boost up to 0.75.
        return Math.min(0.75, shared * 0.25);
    }

    /**
     * Extra boost (0.0..0.3) if the query mentions a code resembling
     * the control code — e.g. user wrote "ECC-2-1-1" and that's the
     * control code itself.
     */
    private static double codeOrAcronymBoost(String description, String controlCode) {
        if (controlCode == null || controlCode.isBlank()) return 0.0;
        String lc = description.toLowerCase(Locale.ROOT);
        String ccLc = controlCode.toLowerCase(Locale.ROOT);
        if (lc.contains(ccLc)) return 0.3;
        return 0.0;
    }

    private static String describeRationale(Set<String> q, Set<String> c, String controlCode, String description) {
        Set<String> sharedTokens = new LinkedHashSet<>(q);
        sharedTokens.retainAll(c);
        List<String> sharedSaudi = sharedTokens.stream()
            .filter(SAUDI_KEYWORDS::contains)
            .limit(3)
            .collect(Collectors.toList());
        List<String> sharedOther = sharedTokens.stream()
            .filter(t -> !SAUDI_KEYWORDS.contains(t))
            .limit(5)
            .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        if (!sharedSaudi.isEmpty()) {
            sb.append("Saudi-regulatory keyword match: ");
            sb.append(String.join(", ", sharedSaudi)).append(". ");
        }
        if (controlCode != null && description.toLowerCase(Locale.ROOT).contains(controlCode.toLowerCase(Locale.ROOT))) {
            sb.append("Control code referenced explicitly. ");
        }
        if (!sharedOther.isEmpty()) {
            sb.append("Shared terms: ").append(String.join(", ", sharedOther)).append(".");
        }
        if (sb.length() == 0) sb.append("Token overlap signal only.");
        return sb.toString().trim();
    }

    // ─── Tokenisation ────────────────────────────────────────────────

    static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        String[] raw = TOKEN_SPLITTER.split(text.toLowerCase(Locale.ROOT));
        Set<String> out = new LinkedHashSet<>();
        for (String t : raw) {
            if (t.length() < MIN_TOKEN_LENGTH) continue;
            if (STOP_WORDS.contains(t)) continue;
            out.add(t);
        }
        return out;
    }

    private static double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }

    // ─── DTO types ───────────────────────────────────────────────────

    public record SuggestionRequest(
        String description,
        String domain,         // optional filter
        Integer topN
    ) {}

    public record Suggestion(
        Long controlId,
        String controlCode,
        String domain,
        String titleEn,
        String titleAr,
        double jaccard,
        double confidence,     // 0..1 — blended jaccard + Saudi boost + code-mention
        String rationale
    ) {}

    public record SuggestionResponse(
        String query,
        List<Suggestion> suggestions,
        int totalScored
    ) {}
}

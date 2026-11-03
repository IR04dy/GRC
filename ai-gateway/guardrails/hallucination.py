"""
Grounding & Hallucination Detector for Enterprise GRC
Evaluates model outputs against reference context and known GRC taxonomies:
- Control Identifier Verification (NCA ECC, CSCC, CCC, SAMA CSF, Saudi PDPL Articles, ISO 27001)
- Fact-Grounding & Entity Overlap Ratio (faithfulness to provided context)
- Fabricated Citation & Article Detection
- Unreferenced Statistical / Numeric Assertions
"""

from dataclasses import dataclass, field
import re
from typing import Dict, List, Optional, Set, Tuple

@dataclass
class ControlCitation:
    framework: str
    control_code: str
    is_valid: bool
    is_in_grounding_context: bool

@dataclass
class HallucinationResult:
    is_hallucination: bool
    hallucination_score: float # 0.0 (fully grounded) to 1.0 (pure hallucination)
    grounding_overlap_score: float # 0.0 to 1.0
    citations_analyzed: List[ControlCitation] = field(default_factory=list)
    invalid_controls: List[str] = field(default_factory=list)
    unsupported_numbers: List[str] = field(default_factory=list)
    warnings: List[str] = field(default_factory=list)


class HallucinationDetector:
    """
    Evaluates whether generated text is grounded in the prompt context
    and free from fabricated GRC control citations.
    """

    # Framework identifier regexes
    CONTROL_PATTERNS = [
        # NCA ECC (Essential Cybersecurity Controls, e.g. ECC-1-1-1, ECC-2-3)
        (r"\b(?:NCA[-_\s]?)?ECC[-_\s]?(\d{1,2}(?:[-_.]\d{1,2}){1,3})\b", "NCA_ECC"),
        # NCA CSCC (Cloud Cybersecurity Controls)
        (r"\b(?:NCA[-_\s]?)?CSCC[-_\s]?(\d{1,2}(?:[-_.]\d{1,2}){1,3})\b", "NCA_CSCC"),
        # NCA CCC (Critical Systems Controls)
        (r"\b(?:NCA[-_\s]?)?CCC[-_\s]?(\d{1,2}(?:[-_.]\d{1,2}){1,3})\b", "NCA_CCC"),
        # Saudi PDPL Articles (e.g. Article 12, Art. 4, PDPL Article 25)
        (r"\b(?:PDPL|Saudi\s+PDPL)?\s*(?:Article|Art\.?)\s*(\d{1,3})\b", "SAUDI_PDPL"),
        # ISO 27001 Annex A (e.g. A.5.1, A.8.12)
        (r"\b(?:ISO\s*27001\s*)?A\.(\d{1,2}\.\d{1,2})\b", "ISO_27001"),
        # SAMA Cyber Security Framework
        (r"\bSAMA[-_\s]?(?:CSF)?[-_\s]?(\d{1,2}(?:[-_.]\d{1,2}){1,2})\b", "SAMA_CSF"),
    ]

    # Valid bounds for Saudi regulatory frameworks
    MAX_PDPL_ARTICLES = 43 # Saudi PDPL has 43 articles officially enacted
    MAX_ECC_DOMAINS = 5    # NCA ECC has 5 main subdomains

    def __init__(self, hallucination_threshold: float = 0.65):
        self.threshold = hallucination_threshold

    def evaluate(self, generated_text: str, context_text: Optional[str] = None, allowed_controls: Optional[List[str]] = None) -> HallucinationResult:
        """
        Evaluate generated text against reference context and regulatory taxonomies.
        """
        if not generated_text or not generated_text.strip():
            return HallucinationResult(is_hallucination=False, hallucination_score=0.0, grounding_overlap_score=1.0)

        context = context_text or ""
        citations: List[ControlCitation] = []
        invalid_controls: List[str] = []
        warnings: List[str] = []

        allowed_set: Set[str] = set()
        if allowed_controls:
            allowed_set = {c.strip().upper() for c in allowed_controls}

        # 1. Extract & Validate GRC Citations
        for regex_str, framework in self.CONTROL_PATTERNS:
            for match in re.finditer(regex_str, generated_text, re.IGNORECASE):
                full_citation = match.group(0).upper()
                code_part = match.group(1)

                is_valid = True
                # Validate PDPL Article bounds
                if framework == "SAUDI_PDPL":
                    try:
                        art_num = int(code_part)
                        if art_num < 1 or art_num > self.MAX_PDPL_ARTICLES:
                            is_valid = False
                            invalid_controls.append(f"{full_citation} (Invalid: PDPL only has {self.MAX_PDPL_ARTICLES} articles)")
                    except ValueError:
                        is_valid = False

                # Validate ECC domain bounds
                elif framework == "NCA_ECC":
                    try:
                        domain_num = int(code_part.split("-")[0].split(".")[0])
                        if domain_num < 1 or domain_num > self.MAX_ECC_DOMAINS:
                            is_valid = False
                            invalid_controls.append(f"{full_citation} (Invalid: ECC only has {self.MAX_ECC_DOMAINS} domains)")
                    except (ValueError, IndexError):
                        pass

                # Check if citation exists in context or allowed controls list
                is_in_context = (
                    full_citation in context.upper() or
                    code_part in context or
                    full_citation in allowed_set or
                    code_part in allowed_set
                )

                if not is_in_context and context.strip():
                    warnings.append(f"Citation '{full_citation}' was not found in the grounding reference context.")

                citations.append(ControlCitation(
                    framework=framework,
                    control_code=full_citation,
                    is_valid=is_valid,
                    is_in_grounding_context=is_in_context
                ))

        # 2. Check for Unsupported Specific Numbers / Percentages if context was provided
        unsupported_numbers: List[str] = []
        if context.strip():
            # Look for specific percentages or dollar/SAR amounts: e.g. "98.5%", "SAR 5,000,000"
            numbers_gen = set(re.findall(r"\b(?:\d+(?:\.\d+)?%|SAR\s*[\d,]+|\$\s*[\d,]+)\b", generated_text))
            numbers_ctx = set(re.findall(r"\b(?:\d+(?:\.\d+)?%|SAR\s*[\d,]+|\$\s*[\d,]+)\b", context))
            diff = numbers_gen - numbers_ctx
            if diff:
                unsupported_numbers = list(diff)
                warnings.append(f"Found {len(diff)} specific metrics/figures not present in source context: {', '.join(list(diff)[:3])}")

        # 3. Grounding Lexical Overlap Score (Salient Terms)
        overlap_score = 1.0
        if context.strip():
            # Extract salient words (length >= 4, alpha only, not stopwords)
            stopwords = {"this", "that", "with", "from", "have", "been", "were", "what", "when", "where", "should", "could", "would"}
            gen_words = {w.lower() for w in re.findall(r"\b[a-zA-Z]{4,}\b", generated_text) if w.lower() not in stopwords}
            ctx_words = {w.lower() for w in re.findall(r"\b[a-zA-Z]{4,}\b", context) if w.lower() not in stopwords}

            if gen_words:
                matched = gen_words.intersection(ctx_words)
                overlap_score = round(len(matched) / len(gen_words), 3)

        # 4. Calculate Final Hallucination Score
        hallucination_score = 0.0

        # Invalid controls are severe hallucinations
        if invalid_controls:
            hallucination_score += 0.50 * len(invalid_controls)

        # Citations completely ungrounded from context
        ungrounded_citations = [c for c in citations if not c.is_in_grounding_context and context.strip()]
        if ungrounded_citations:
            hallucination_score += 0.25 * len(ungrounded_citations)

        # Unsupported specific numbers
        if unsupported_numbers:
            hallucination_score += 0.15 * min(len(unsupported_numbers), 2)

        # Very low lexical overlap when reference context was given
        if context.strip() and overlap_score < 0.20:
            hallucination_score += 0.30

        hallucination_score = min(1.0, round(hallucination_score, 3))
        is_hallucination = hallucination_score >= self.threshold or len(invalid_controls) > 0

        return HallucinationResult(
            is_hallucination=is_hallucination,
            hallucination_score=hallucination_score,
            grounding_overlap_score=overlap_score,
            citations_analyzed=citations,
            invalid_controls=invalid_controls,
            unsupported_numbers=unsupported_numbers,
            warnings=warnings
        )

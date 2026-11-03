"""
Prompt Injection & Jailbreak Detector
Open-source multi-layered detection for:
- Direct instruction overrides ("ignore previous instructions", "disregard system prompt")
- Persona jailbreaks ("DAN mode", "Developer Mode", "unrestricted AI")
- Prompt leaking ("print your system instructions", "repeat above text verbatim")
- Delimiter & formatting injection attacks (<|im_start|>, [INST], <<SYS>>, ### System:)
- Leetspeak and character obfuscation normalization
"""

from dataclasses import dataclass, field
import re
from typing import Dict, List, Optional, Tuple

@dataclass
class InjectionViolation:
    category: str
    pattern_name: str
    matched_text: str
    weight: float

@dataclass
class InjectionResult:
    is_injected: bool
    risk_score: float # 0.0 to 1.0
    action: str # "ALLOW", "FLAG", "BLOCK"
    violations: List[InjectionViolation] = field(default_factory=list)
    normalized_text: str = ""


class PromptInjectionDetector:
    """
    Robust pattern & heuristic detector for prompt injection and jailbreak attempts.
    """

    # High-severity override patterns (weight >= 0.8)
    DIRECT_OVERRIDE_PATTERNS = [
        (r"\b(?:ignore|disregard|forget|override|bypass|neglect)\s+(?:all\s+)?(?:previous|prior|above|former|earlier)\s+(?:instructions|directions|prompts|rules|commands|guidelines|context)", "DIRECT_OVERRIDE", 0.95),
        (r"\bdo\s+not\s+follow\s+(?:the\s+)?(?:system|previous|initial)\s+(?:prompt|instructions|rules)", "SYSTEM_NEGATION", 0.90),
        (r"\breset\s+(?:all\s+)?(?:instructions|context|rules|directives)", "CONTEXT_RESET", 0.85),
        (r"\bfrom\s+now\s+on\s*,?\s*you\s+(?:will|must|should|shall)\s+(?:act|respond|operate|behave)\s+as\b", "PERSONA_HIJACK", 0.80),
        (r"\byou\s+are\s+no\s+longer\s+(?:an?\s+)?(?:ai|assistant|model|helpful)\b", "PERSONA_STRIP", 0.85),
    ]

    # Jailbreak / Persona bypass patterns (weight >= 0.7)
    JAILBREAK_PATTERNS = [
        (r"\b(?:dan|developer\s+mode|chaosgpt|jailbreak|evil\s+twin|maximum\s+unrestricted)\s+(?:mode|enabled|persona|prompt)\b", "JAILBREAK_KEYWORD", 0.90),
        (r"\bdo\s+anything\s+now\b", "DAN_ACRONYM", 0.95),
        (r"\b(?:pretend|act|simulate)\s+(?:you\s+have\s+no\s+(?:rules|filters|morals|ethics|limitations)|that\s+you\s+can\s+do\s+anything)\b", "SIMULATE_NO_RULES", 0.85),
        (r"\bunrestricted\s+mode\s+active\b", "UNRESTRICTED_MODE", 0.85),
        (r"\bhypothetical\s+scenario\s+where\s+(?:safety\s+guidelines|ethics)\s+do\s+not\s+apply\b", "ETHICS_CIRCUMVENTION", 0.75),
    ]

    # Prompt leaking / exfiltration patterns (weight >= 0.6)
    LEAK_PATTERNS = [
        (r"\b(?:repeat|print|output|display|show|reveal|echo|state)\s+(?:the\s+)?(?:exact\s+)?(?:words\s+above|system\s+prompt|initial\s+instructions|preceding\s+text|hidden\s+prompt)\b", "PROMPT_LEAK_REQUEST", 0.85),
        (r"\bwhat\s+(?:is|was|were)\s+your\s+(?:exact\s+)?(?:system\s+prompt|initial\s+instructions|system\s+instruction)\b", "SYSTEM_PROMPT_INQUIRY", 0.75),
        (r"\bgive\s+me\s+your\s+(?:full\s+)?instructions\s+verbatim\b", "VERBATIM_INSTRUCTION_LEAK", 0.85),
    ]

    # Delimiter / Control token injection (weight >= 0.7)
    DELIMITER_PATTERNS = [
        (r"<\|(?:im_start|im_end|system|user|assistant)\|>", "CHATML_CONTROL_TOKEN", 0.95),
        (r"\[/?INST\]|\[/?SYS\]|<<SYS>>|<</SYS>>", "LLAMA_CONTROL_TOKEN", 0.95),
        (r"(?:\n|\r|^)#{1,4}\s*(?:system|system\s+instructions|developer\s+instructions)\s*:", "HEADER_SPOOFING", 0.85),
        (r"````\s*(?:system|developer|override)", "CODE_FENCE_SPOOFING", 0.80),
        (r"(?:---|===)\s*(?:BEGIN|START|END)\s*(?:SYSTEM|ADMIN|SECRET)\s*(?:---|===)", "FENCE_BOUNDARY_SPOOFING", 0.85),
    ]

    # Obfuscation mapping
    LEET_TRANS = str.maketrans({
        '0': 'o',
        '1': 'i',
        '3': 'e',
        '4': 'a',
        '@': 'a',
        '5': 's',
        '$': 's',
        '7': 't',
        '+': 't',
        '!': 'i',
    })

    def __init__(self, block_threshold: float = 0.80, flag_threshold: float = 0.50):
        self.block_threshold = block_threshold
        self.flag_threshold = flag_threshold
        self._compiled_patterns = []

        all_rules = (
            self.DIRECT_OVERRIDE_PATTERNS +
            self.JAILBREAK_PATTERNS +
            self.LEAK_PATTERNS +
            self.DELIMITER_PATTERNS
        )
        for pattern_str, name, weight in all_rules:
            self._compiled_patterns.append((re.compile(pattern_str, re.IGNORECASE), name, weight))

    def normalize(self, text: str) -> str:
        """Strip invisible chars, normalize leetspeak and excessive whitespace."""
        # Replace zero-width spaces and control chars
        clean = re.sub(r"[\u200B-\u200D\uFEFF]", "", text)
        clean = clean.lower()
        clean = clean.translate(self.LEET_TRANS)
        clean = re.sub(r"\s+", " ", clean).strip()
        return clean

    def detect(self, text: str) -> InjectionResult:
        """
        Analyze prompt text and determine if injection / jailbreak is present.
        """
        if not text or not text.strip():
            return InjectionResult(is_injected=False, risk_score=0.0, action="ALLOW")

        violations: List[InjectionViolation] = []
        raw_text = text
        normalized = self.normalize(text)

        total_weight = 0.0

        for regex, name, weight in self._compiled_patterns:
            # Check on raw text first
            match = regex.search(raw_text)
            if not match:
                # Check on normalized text
                match = regex.search(normalized)

            if match:
                violations.append(InjectionViolation(
                    category=name.split("_")[0],
                    pattern_name=name,
                    matched_text=match.group(0),
                    weight=weight
                ))
                total_weight += weight

        # Compute capped risk score
        risk_score = min(1.0, total_weight)

        # Decide action
        if risk_score >= self.block_threshold:
            action = "BLOCK"
            is_injected = True
        elif risk_score >= self.flag_threshold:
            action = "FLAG"
            is_injected = True
        else:
            action = "ALLOW"
            is_injected = False

        return InjectionResult(
            is_injected=is_injected,
            risk_score=round(risk_score, 3),
            action=action,
            violations=violations,
            normalized_text=normalized
        )

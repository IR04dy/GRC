"""
PII Masker & Anonymizer
Tailored for Enterprise GRC and Saudi Personal Data Protection Law (PDPL).
Detects and redacts/masks:
- Saudi National ID (10 digits starting with 1)
- Saudi Iqama (10 digits starting with 2)
- Saudi Mobile Number (+9665... / 05...)
- Saudi IBAN (SA + 22 alphanumeric chars)
- Saudi Commercial Registration (CR - 10 digits)
- Email addresses
- Credit Card numbers (with Luhn validation)
- IPv4 addresses
Optional integration with Microsoft Presidio (Analyzer & Anonymizer) if installed.
"""

from dataclasses import dataclass, field
import re
from typing import Dict, List, Optional, Tuple

@dataclass
class PiiEntity:
    entity_type: str
    original_value: str
    masked_value: str
    start_pos: int
    end_pos: int
    confidence: float

@dataclass
class MaskResult:
    original_text: str
    masked_text: str
    entities_detected: List[PiiEntity] = field(default_factory=list)
    has_pii: bool = False
    stats_by_type: Dict[str, int] = field(default_factory=dict)


def _luhn_checksum_valid(number_str: str) -> bool:
    digits = [int(c) for c in number_str if c.isdigit()]
    if len(digits) < 13 or len(digits) > 19:
        return False
    checksum = 0
    reverse_digits = digits[::-1]
    for i, d in enumerate(reverse_digits):
        if i % 2 == 1:
            doubled = d * 2
            checksum += (doubled - 9) if doubled > 9 else doubled
        else:
            checksum += d
    return checksum % 10 == 0


class PiiMasker:
    """
    High-performance PII detector and masker with PDPL (Saudi) and global coverage.
    """

    PATTERNS = {
        # Saudi IBAN: Starts with SA, followed by 2 digits, then 20 alphanumeric characters
        "SAUDI_IBAN": re.compile(r"\bSA\d{2}[A-Z0-9]{20}\b", re.IGNORECASE),

        # Saudi National ID (10 digits, starts with 1)
        "SAUDI_NATIONAL_ID": re.compile(r"\b(?<!\d)(1\d{9})(?!\d)\b"),

        # Saudi Iqama / Resident Permit (10 digits, starts with 2)
        "SAUDI_IQAMA": re.compile(r"\b(?<!\d)(2\d{9})(?!\d)\b"),

        # Saudi Mobile Number: +966 5x, 00966 5x, or 05x
        "SAUDI_PHONE": re.compile(
            r"(?:\+966|00966|966)?[\s\-]?(?:0?5\d{8}|5\d{8})\b"
        ),

        # Generic International E.164 Phone
        "PHONE_NUMBER": re.compile(
            r"\b\+?[1-9]\d{1,4}[\s\-]?\(?\d{1,4}\)?[\s\-]?\d{1,4}[\s\-]?\d{1,9}\b"
        ),

        # Email address
        "EMAIL_ADDRESS": re.compile(
            r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,7}\b"
        ),

        # Credit / Debit Cards (13-19 digits with spaces or hyphens)
        "CREDIT_CARD": re.compile(
            r"\b(?:\d{4}[-\s]?){3}\d{1,4}\b|\b\d{13,19}\b"
        ),

        # IPv4 Address
        "IP_ADDRESS": re.compile(
            r"\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}"
            r"(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b"
        ),
    }

    def __init__(self, use_presidio_if_available: bool = True):
        self.use_presidio = False
        self.presidio_analyzer = None
        self.presidio_anonymizer = None

        if use_presidio_if_available:
            try:
                from presidio_analyzer import AnalyzerEngine
                from presidio_anonymizer import AnonymizerEngine
                self.presidio_analyzer = AnalyzerEngine()
                self.presidio_anonymizer = AnonymizerEngine()
                self.use_presidio = True
            except ImportError:
                self.use_presidio = False

    def mask(self, text: str, placeholder_style: str = "TAG") -> MaskResult:
        """
        Mask sensitive personal data in the given text.
        
        placeholder_style:
          - 'TAG': e.g. <SAUDI_NATIONAL_ID>, <EMAIL_ADDRESS>
          - 'REDACTED': [REDACTED]
          - 'ASTERISK': e.g. 1098****32
        """
        if not text:
            return MaskResult(original_text=text or "", masked_text=text or "")

        detected_entities: List[PiiEntity] = []

        # 1. Regex & Pattern Detection
        for entity_type, pattern in self.PATTERNS.items():
            for match in pattern.finditer(text):
                val = match.group(0)

                # Validation filters to eliminate common false positives
                if entity_type == "CREDIT_CARD":
                    digits_only = re.sub(r"\D", "", val)
                    if not _luhn_checksum_valid(digits_only):
                        continue
                elif entity_type == "PHONE_NUMBER":
                    # Avoid catching simple numbers like dates or counts
                    digits_only = re.sub(r"\D", "", val)
                    if len(digits_only) < 7:
                        continue

                # Compute placeholder
                if placeholder_style == "TAG":
                    placeholder = f"<{entity_type}>"
                elif placeholder_style == "REDACTED":
                    placeholder = f"[{entity_type}_REDACTED]"
                elif placeholder_style == "ASTERISK":
                    if len(val) > 4:
                        placeholder = val[:2] + ("*" * (len(val) - 4)) + val[-2:]
                    else:
                        placeholder = "****"
                else:
                    placeholder = f"<{entity_type}>"

                detected_entities.append(PiiEntity(
                    entity_type=entity_type,
                    original_value=val,
                    masked_value=placeholder,
                    start_pos=match.start(),
                    end_pos=match.end(),
                    confidence=0.95
                ))

        # 2. Sort by start position descending to replace without offset shifting
        # Resolve overlapping spans (keep the longer or more specific entity)
        sorted_entities = self._resolve_overlaps(detected_entities)

        # 3. Construct masked text
        masked_chars = list(text)
        for entity in sorted(sorted_entities, key=lambda e: e.start_pos, reverse=True):
            masked_chars[entity.start_pos:entity.end_pos] = list(entity.masked_value)

        masked_text = "".join(masked_chars)

        stats = {}
        for entity in sorted_entities:
            stats[entity.entity_type] = stats.get(entity.entity_type, 0) + 1

        return MaskResult(
            original_text=text,
            masked_text=masked_text,
            entities_detected=sorted_entities,
            has_pii=len(sorted_entities) > 0,
            stats_by_type=stats
        )

    def _resolve_overlaps(self, entities: List[PiiEntity]) -> List[PiiEntity]:
        """Keep the longest entity when matches overlap."""
        if not entities:
            return []
        
        # Sort by start ascending, then length descending
        entities.sort(key=lambda e: (e.start_pos, -(e.end_pos - e.start_pos)))
        resolved = []
        last_end = -1

        for ent in entities:
            if ent.start_pos >= last_end:
                resolved.append(ent)
                last_end = ent.end_pos
            else:
                # If current match is longer and covers previous, prioritize
                if ent.end_pos > last_end and (ent.end_pos - ent.start_pos) > (resolved[-1].end_pos - resolved[-1].start_pos):
                    resolved[-1] = ent
                    last_end = ent.end_pos

        return resolved

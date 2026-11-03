"""
Safety, Threat, & Credential Protection Guardrail
Open-source content moderation and sensitive asset protection for Enterprise GRC.

Detects and blocks:
1. Credential & Secret Probing (admin passwords, database secrets, API keys, root access)
2. Weapons, CBRN, & Explosives (nuclear/atomic weapons, dirty bombs, improvised explosives)
3. Malicious Cyber Threats (ransomware authoring, unauthorized malware generation)
4. Harmful & Illegal Out-of-Scope Content
"""

from dataclasses import dataclass, field
import re
from typing import Dict, List, Optional

@dataclass
class SafetyViolation:
    category: str
    pattern_name: str
    matched_text: str
    description: str

@dataclass
class SafetyResult:
    is_safe: bool
    risk_score: float # 0.0 to 1.0
    action: str # "ALLOW", "BLOCK"
    violation_category: Optional[str] = None
    violations: List[SafetyViolation] = field(default_factory=list)
    rejection_message: Optional[str] = None


class SafetyGuardrail:
    """
    Evaluates prompts for credential exfiltration, dangerous weapons/CBRN threats,
    and out-of-scope harmful content.
    """

    # Category 1: Credential, Secret & Privilege Probing
    CREDENTIAL_PATTERNS = [
        (
            r"\b(?:what\s+is|show\s+me|give\s+me|tell\s+me|reveal|dump|find)\s+(?:the\s+)?(?:admin|administrator|root|database|db|sa|master|system|jwt|service)\s+(?:password|secret|creds|credential|token|key|hash)\b",
            "CREDENTIAL_EXFILTRATION",
            "Attempt to solicit administrative or system credentials."
        ),
        (
            r"\b(?:admin|root|sa|superuser)\s+password\b",
            "ADMIN_PASSWORD_INQUIRY",
            "Inquiry into administrative authentication secrets."
        ),
        (
            r"\b(?:how\s+to\s+crack|bypass|steal|extract)\s+(?:the\s+)?(?:password|admin|auth|token|jwt|encryption\s+key)\b",
            "AUTHENTICATION_BYPASS_ATTEMPT",
            "Attempt to compromise access controls or crack passwords."
        ),
    ]

    # Category 2: Weapons of Mass Destruction, CBRN, & Explosives
    WEAPONS_CBRN_PATTERNS = [
        (
            r"\b(?:atomic|nuclear|hydrogen|neutron|dirty)\s+(?:bomb|warhead|weapon|device)\b",
            "NUCLEAR_CBRN_WEAPONS",
            "Inquiry regarding nuclear, atomic, or radiological weapons."
        ),
        (
            r"\b(?:how\s+(?:is|to|do\s+you)\s+(?:make|build|design|create|manufacture|assemble))\s+(?:an?\s+)?(?:bomb|explosive|ied|detonator|warhead)\b",
            "EXPLOSIVES_MANUFACTURE",
            "Inquiry regarding manufacture of explosive devices or munitions."
        ),
        (
            r"\b(?:synthesize|produce|disperse)\s+(?:sarin|ricin|anthrax|cyanide|mustard\s+gas|botulinum)\b",
            "CHEMICAL_BIOLOGICAL_THREAT",
            "Inquiry regarding chemical or biological weapon synthesis."
        ),
    ]

    # Category 3: Malicious Cyber Weapons & Malware
    MALWARE_PATTERNS = [
        (
            r"\b(?:write|create|code|generate)\s+(?:a\s+)?(?:ransomware|keylogger|trojan|rootkit|botnet|worm|wiper)\b",
            "MALWARE_AUTHORING",
            "Solicitation of malicious software or weaponized cyber tools."
        ),
        (
            r"\b(?:exploit\s+code\s+for|zero-day\s+exploit\s+to\s+hack)\b",
            "EXPLOIT_AUTHORING",
            "Solicitation of offensive exploit code."
        ),
    ]

    def __init__(self):
        self._compiled_rules = []
        for pat, name, desc in self.CREDENTIAL_PATTERNS:
            self._compiled_rules.append((re.compile(pat, re.IGNORECASE), "CREDENTIAL_LEAK", name, desc))
        for pat, name, desc in self.WEAPONS_CBRN_PATTERNS:
            self._compiled_rules.append((re.compile(pat, re.IGNORECASE), "DANGEROUS_WEAPONS", name, desc))
        for pat, name, desc in self.MALWARE_PATTERNS:
            self._compiled_rules.append((re.compile(pat, re.IGNORECASE), "CYBER_THREAT", name, desc))

    def evaluate(self, text: str) -> SafetyResult:
        """
        Analyze prompt text and determine if a safety violation or credential probe is present.
        """
        if not text or not text.strip():
            return SafetyResult(is_safe=True, risk_score=0.0, action="ALLOW")

        violations: List[SafetyViolation] = []
        primary_category = None

        for regex, category, name, desc in self._compiled_rules:
            match = regex.search(text)
            if match:
                violations.append(SafetyViolation(
                    category=category,
                    pattern_name=name,
                    matched_text=match.group(0),
                    description=desc
                ))
                if primary_category is None:
                    primary_category = category

        if violations:
            if primary_category == "CREDENTIAL_LEAK":
                rejection_msg = "Request was blocked by enterprise GRC security guardrails (Unauthorized Credential / Secret Probe)."
            elif primary_category == "DANGEROUS_WEAPONS":
                rejection_msg = "Request was blocked by enterprise safety guardrails (Prohibited Weapons / Threat Topic)."
            elif primary_category == "CYBER_THREAT":
                rejection_msg = "Request was blocked by enterprise cybersecurity guardrails (Malicious Cyber Tooling)."
            else:
                rejection_msg = "Request was blocked by enterprise safety guardrails."

            return SafetyResult(
                is_safe=False,
                risk_score=1.0,
                action="BLOCK",
                violation_category=primary_category,
                violations=violations,
                rejection_message=rejection_msg
            )

        return SafetyResult(is_safe=True, risk_score=0.0, action="ALLOW")

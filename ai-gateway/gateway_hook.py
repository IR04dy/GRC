"""
LiteLLM Custom Guardrails Hook
Integrates PII Masking, Prompt Injection Defense, and Hallucination Evaluation
into the LiteLLM Proxy pipeline.

Inherits from CustomLogger if litellm is installed, or provides standard
async hook methods for LiteLLM and standalone gateway servers.
"""

import logging
import sys
import os
from typing import Any, Dict, List, Optional

# Add current directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from guardrails.pii_masker import PiiMasker
from guardrails.prompt_injection import PromptInjectionDetector
from guardrails.hallucination import HallucinationDetector
from guardrails.safety_filter import SafetyGuardrail

logger = logging.getLogger("grc.ai_gateway.guardrails")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] [Guardrails] %(message)s")

try:
    from litellm.integrations.custom_logger import CustomLogger
except ImportError:
    # Fallback base class if litellm is not installed in the current environment
    class CustomLogger:
        pass


class GrcGuardrailsHook(CustomLogger):
    """
    LiteLLM Proxy Hook for TSS GRC Platform.
    Enforces:
    1. Pre-call Safety & Credential probe blocking.
    2. Pre-call Prompt Injection blocking.
    3. Pre-call PII redaction/masking (Saudi PDPL & Global).
    4. Post-call Hallucination detection & citation verification.
    """

    def __init__(self):
        super().__init__()
        self.pii_masker = PiiMasker()
        self.injection_detector = PromptInjectionDetector(block_threshold=0.80)
        self.safety_guardrail = SafetyGuardrail()
        self.hallucination_detector = HallucinationDetector(hallucination_threshold=0.65)
        logger.info("GRC Guardrails initialized (PII Masker, Injection Detector, Safety Filter, Hallucination Detector).")

    async def async_pre_call_hook(self, user_api_key_dict: dict, cache: Any, data: dict, call_type: str) -> Optional[dict]:
        """
        Runs BEFORE forwarding to the LLM.
        - Blocks prompt injections with HTTP 400.
        - Redacts PII in messages before transmission.
        """
        messages = data.get("messages", [])
        if not messages or not isinstance(messages, list):
            return None

        # 1. Inspect all user/system messages for safety & prompt injection
        for msg in messages:
            if not isinstance(msg, dict):
                continue
            content = msg.get("content", "")
            if not isinstance(content, str) or not content.strip():
                continue

            role = msg.get("role", "user")
            if role in ("user", "assistant"):
                # Safety, Threat & Credential Probe Check
                safety_res = self.safety_guardrail.evaluate(content)
                if not safety_res.is_safe:
                    logger.warning(f"BLOCKING REQUEST: Safety Violation ({safety_res.violation_category})!")
                    from fastapi import HTTPException
                    raise HTTPException(
                        status_code=400,
                        detail={
                            "error": "GUARDRAIL_BLOCKED",
                            "code": safety_res.violation_category,
                            "message": safety_res.rejection_message,
                            "risk_score": safety_res.risk_score,
                            "violations": [v.pattern_name for v in safety_res.violations]
                        }
                    )

                # Prompt Injection Check
                inj_result = self.injection_detector.detect(content)
                if inj_result.action == "BLOCK":
                    violation_summary = ", ".join([v.pattern_name for v in inj_result.violations])
                    logger.warning(
                        f"BLOCKING REQUEST: Prompt Injection Detected (Risk: {inj_result.risk_score})! Violations: {violation_summary}"
                    )
                    from fastapi import HTTPException
                    raise HTTPException(
                        status_code=400,
                        detail={
                            "error": "GUARDRAIL_BLOCKED",
                            "code": "PROMPT_INJECTION_DETECTED",
                            "message": "Request was blocked by enterprise GRC security guardrails.",
                            "risk_score": inj_result.risk_score,
                            "violations": [v.pattern_name for v in inj_result.violations]
                        }
                    )
                elif inj_result.action == "FLAG":
                    logger.info(f"FLAGGED: Potential injection attempt (Risk: {inj_result.risk_score})")

        # 2. Inspect and Mask PII before outbound call
        total_pii_masked = 0
        for msg in messages:
            if not isinstance(msg, dict):
                continue
            content = msg.get("content", "")
            if isinstance(content, str) and content.strip():
                mask_result = self.pii_masker.mask(content, placeholder_style="TAG")
                if mask_result.has_pii:
                    msg["content"] = mask_result.masked_text
                    total_pii_masked += len(mask_result.entities_detected)
                    logger.info(
                        f"PII Redacted in {msg.get('role')} message: {mask_result.stats_by_type}"
                    )

        if total_pii_masked > 0:
            data["_grc_pii_masked_count"] = total_pii_masked

        return data

    async def async_post_call_success_hook(self, data: dict, user_api_key_dict: dict, response: Any, start_time: Any, end_time: Any):
        """
        Runs AFTER receiving the response from the LLM.
        - Evaluates output for hallucinations, invalid regulatory citations, and factual grounding.
        """
        try:
            # Extract generated response text
            generated_text = ""
            if hasattr(response, "choices") and response.choices:
                first = response.choices[0]
                if hasattr(first, "message") and hasattr(first.message, "content"):
                    generated_text = first.message.content or ""
            elif isinstance(response, dict) and "choices" in response and response["choices"]:
                msg = response["choices"][0].get("message", {})
                generated_text = msg.get("content", "")

            if not generated_text:
                return

            # Extract context from system and user messages for grounding comparison
            context_parts = []
            messages = data.get("messages", []) if isinstance(data, dict) else []
            for m in messages:
                if isinstance(m, dict) and m.get("content"):
                    context_parts.append(str(m.get("content")))
            full_context = "\n".join(context_parts)

            # Evaluate hallucination
            hal_result = self.hallucination_detector.evaluate(generated_text, full_context)

            if hal_result.is_hallucination:
                logger.warning(
                    f"HALLUCINATION ALERT: Score {hal_result.hallucination_score} | "
                    f"Invalid Controls: {hal_result.invalid_controls} | Warnings: {hal_result.warnings}"
                )
            else:
                logger.info(
                    f"Response Grounding Check PASSED: Overlap {hal_result.grounding_overlap_score}, "
                    f"Citations Validated: {len(hal_result.citations_analyzed)}"
                )

        except Exception as e:
            logger.error(f"Error in hallucination post-call evaluation: {e}", exc_info=True)


# Singleton instance exported for LiteLLM config
proxy_handler_instance = GrcGuardrailsHook()

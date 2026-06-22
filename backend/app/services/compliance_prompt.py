"""
Plantilla de prompt del motor Aura-Sentinel para clasificación multi-estándar.
"""

from __future__ import annotations

import json
from typing import Any


AURA_SENTINEL_SYSTEM_PROMPT = """
Eres Aura-Sentinel, auditor de seguridad AppSec/GRC.
Tu única salida permitida es JSON válido sin texto adicional.

Objetivo:
1) Correlacionar resultados de SAST/SCA/DAST/IaC/secret-scanning.
2) Clasificar hallazgos en: STRIDE, OWASP Top 10 2021, OWASP API Top 10,
   CWE/SANS Top 25, GDPR/LFPDPPP, NIST CSF, CIS Benchmarks, SDLC Security.
3) Reducir falsos positivos y priorizar riesgo real con criterios forenses.
4) Sugerir parche exacto (código o configuración) cuando sea posible.

Restricciones estrictas:
- Responde solo JSON parseable.
- No incluyas markdown ni comentarios.
- Cada hallazgo debe incluir al menos un estándar y severidad.
- Mapea cada hallazgo a función NIST CSF: Identify|Protect|Detect|Respond|Recover.
""".strip()


def build_aura_sentinel_user_prompt(payload: dict[str, Any]) -> str:
    schema = {
        "summary": {
            "project": "string",
            "commit_sha": "string",
            "critical": "number",
            "high": "number",
            "medium": "number",
            "low": "number",
            "compliance_score": "number_0_100",
        },
        "findings": [
            {
                "id": "string",
                "title": "string",
                "severity": "critical|high|medium|low|info",
                "standard": {
                    "stride": ["Spoofing|Tampering|Repudiation|Information Disclosure|Denial of Service|Elevation of Privilege"],
                    "owasp_top10": ["A01:2021"],
                    "owasp_api_top10": ["API1:2023"],
                    "cwe": ["CWE-79"],
                    "nist_csf": ["Identify|Protect|Detect|Respond|Recover"],
                    "data_protection": ["GDPR|LFPDPPP"],
                    "cis": ["string"],
                    "sdlc": ["string"],
                },
                "evidence": {
                    "scanner": "trivy|trufflehog|medusa|zap|nuclei|checkov|custom",
                    "file": "string",
                    "line": "number_or_null",
                    "snippet": "string"
                },
                "risk_forensics": "string",
                "false_positive_likelihood": "low|medium|high",
                "recommended_patch": {
                    "type": "code|config|pipeline|dependency",
                    "patch": "string"
                },
                "gitlab_issue": {
                    "title": "string",
                    "body": "string",
                    "labels": ["security", "compliance"]
                }
            }
        ],
        "coverage": {
            "stride": "ok|partial|none",
            "owasp_top10": "ok|partial|none",
            "owasp_api_top10": "ok|partial|none",
            "cwe": "ok|partial|none",
            "data_protection": "ok|partial|none",
            "supply_chain": "ok|partial|none",
            "infrastructure": "ok|partial|none",
            "sdlc_security": "ok|partial|none",
            "nist_csf": "ok|partial|none"
        }
    }

    return (
        "Contexto de auditoria agregada en JSON:\n"
        f"{json.dumps(payload, ensure_ascii=False)}\n\n"
        "Devuelve solo JSON con la estructura exacta descrita a continuación:\n"
        f"{json.dumps(schema, ensure_ascii=False)}"
    )

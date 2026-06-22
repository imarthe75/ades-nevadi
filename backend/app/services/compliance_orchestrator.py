"""
Orquestador de cumplimiento Centinela-AI.

Pipeline:
1. Clonado seguro del repositorio desde GitLab.
2. Ejecución paralela de Trivy, TruffleHog y Medusa/SAST.
3. Enriquecimiento de findings con Aura-Sentinel (Gemini 1.5 Flash).
"""

from __future__ import annotations

import asyncio
import json
import shlex
import shutil
import tempfile
from dataclasses import asdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from app.core.config import settings
from app.services.compliance_prompt import (
    AURA_SENTINEL_SYSTEM_PROMPT,
    build_aura_sentinel_user_prompt,
)


@dataclass(slots=True)
class ScannerResult:
    scanner: str
    status: str
    findings_count: int
    raw: dict[str, Any] | list[Any] | str
    error: str = ""


class ComplianceOrchestrator:
    def __init__(self) -> None:
        self.trivy_cmd = settings.COMPLIANCE_TRIVY_CMD
        self.trufflehog_cmd = settings.COMPLIANCE_TRUFFLEHOG_CMD
        self.medusa_cmd = settings.COMPLIANCE_MEDUSA_CMD

    async def run(
        self,
        repository_url: str,
        commit_sha: str,
        project_id: int | str,
    ) -> dict[str, Any]:
        with tempfile.TemporaryDirectory(prefix="centinela-compliance-") as tmp_dir:
            repo_path = Path(tmp_dir) / "repo"
            await self._clone_repository(repository_url, commit_sha, repo_path)

            trivy_task = asyncio.create_task(self._run_scanner("trivy", self.trivy_cmd, repo_path))
            trufflehog_task = asyncio.create_task(self._run_scanner("trufflehog", self.trufflehog_cmd, repo_path))
            medusa_task = asyncio.create_task(self._run_scanner("medusa", self.medusa_cmd, repo_path))

            trivy, trufflehog, medusa = await asyncio.gather(
                trivy_task,
                trufflehog_task,
                medusa_task,
            )

            aggregate = {
                "project_id": project_id,
                "commit_sha": commit_sha,
                "repository_url": repository_url,
                "scanners": {
                    "trivy": asdict(trivy),
                    "trufflehog": asdict(trufflehog),
                    "medusa": asdict(medusa),
                },
                "api_routes": self._extract_api_routes(repo_path),
                "architecture_files": self._extract_architecture_files(repo_path),
            }

            enriched = await self._enrich_with_aura_sentinel(aggregate)
            severity = self._severity_totals(enriched)

            return {
                "aggregate": aggregate,
                "enriched": enriched,
                "severity": severity,
                "status": "completed",
            }

    async def _clone_repository(self, repository_url: str, commit_sha: str, repo_path: Path) -> None:
        clone_cmd = ["git", "clone", "--depth", "1", repository_url, str(repo_path)]

        if settings.GITLAB_READ_TOKEN and repository_url.startswith(("http://", "https://")):
            clone_cmd = [
                "git",
                "-c",
                f"http.extraHeader=Authorization: Bearer {settings.GITLAB_READ_TOKEN}",
                "clone",
                "--depth",
                "1",
                repository_url,
                str(repo_path),
            ]

        proc = await asyncio.create_subprocess_exec(
            *clone_cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        _, stderr = await proc.communicate()
        if proc.returncode != 0:
            raise RuntimeError(f"Error al clonar repositorio: {stderr.decode('utf-8', errors='ignore')}")

        checkout = await asyncio.create_subprocess_exec(
            "git",
            "-C",
            str(repo_path),
            "checkout",
            commit_sha,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        _, stderr2 = await checkout.communicate()
        if checkout.returncode != 0:
            raise RuntimeError(f"Error al hacer checkout {commit_sha}: {stderr2.decode('utf-8', errors='ignore')}")

    async def _run_scanner(self, name: str, template: str, repo_path: Path) -> ScannerResult:
        binary = shlex.split(template)[0]
        if shutil.which(binary) is None:
            return ScannerResult(
                scanner=name,
                status="unavailable",
                findings_count=0,
                raw={"message": f"{binary} no esta instalado"},
                error=f"scanner_missing:{binary}",
            )

        cmd = template.format(path=str(repo_path))

        proc = await asyncio.create_subprocess_shell(
            cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, stderr = await proc.communicate()

        if proc.returncode != 0:
            return ScannerResult(
                scanner=name,
                status="error",
                findings_count=0,
                raw=stdout.decode("utf-8", errors="ignore")[:4000],
                error=stderr.decode("utf-8", errors="ignore")[:1200],
            )

        parsed = self._parse_scanner_output(name, stdout.decode("utf-8", errors="ignore"))
        return ScannerResult(
            scanner=name,
            status="ok",
            findings_count=self._count_findings(name, parsed),
            raw=parsed,
        )

    def _parse_scanner_output(self, scanner: str, output: str) -> dict[str, Any] | list[Any] | str:
        if not output.strip():
            return {}

        if scanner == "trufflehog":
            rows: list[Any] = []
            for line in output.splitlines():
                line = line.strip()
                if not line:
                    continue
                try:
                    rows.append(json.loads(line))
                except json.JSONDecodeError:
                    rows.append({"raw": line})
            return rows

        try:
            return json.loads(output)
        except json.JSONDecodeError:
            return output[:8000]

    def _count_findings(self, scanner: str, parsed: dict[str, Any] | list[Any] | str) -> int:
        if scanner == "trivy" and isinstance(parsed, dict):
            return sum(len(item.get("Vulnerabilities") or []) for item in parsed.get("Results") or [])
        if scanner == "medusa" and isinstance(parsed, dict):
            return len(parsed.get("results") or [])
        if scanner == "trufflehog" and isinstance(parsed, list):
            return len(parsed)
        if isinstance(parsed, list):
            return len(parsed)
        return 0

    def _extract_api_routes(self, repo_path: Path) -> list[str]:
        routes: list[str] = []
        for p in repo_path.rglob("*.py"):
            if "site-packages" in str(p):
                continue
            try:
                text = p.read_text(encoding="utf-8", errors="ignore")
            except Exception:
                continue
            for line in text.splitlines():
                if "@router." in line and "(" in line:
                    routes.append(f"{p.relative_to(repo_path)}::{line.strip()}")
        return routes[:800]

    def _extract_architecture_files(self, repo_path: Path) -> list[str]:
        selected: list[str] = []
        patterns = [
            "docker-compose.yml",
            "docker-compose.yaml",
            "*.mermaid",
            "*.mmd",
            "*.tf",
            "*.yml",
            "*.yaml",
            "*.ansible",
        ]
        for pattern in patterns:
            for p in repo_path.rglob(pattern):
                if p.is_file():
                    selected.append(str(p.relative_to(repo_path)))
        return sorted(set(selected))[:800]

    async def _enrich_with_aura_sentinel(self, aggregate: dict[str, Any]) -> dict[str, Any]:
        if settings.GEMINI_API_KEY:
            try:
                return await self._enrich_with_gemini(aggregate)
            except Exception:
                pass
        return self._heuristic_enrichment(aggregate)

    async def _enrich_with_gemini(self, aggregate: dict[str, Any]) -> dict[str, Any]:
        prompt = build_aura_sentinel_user_prompt(aggregate)

        def _call() -> str:
            from google import genai  # type: ignore

            client = genai.Client(api_key=settings.GEMINI_API_KEY)
            response = client.models.generate_content(
                model=settings.GEMINI_MODEL,
                contents=[
                    {"role": "user", "parts": [{"text": f"{AURA_SENTINEL_SYSTEM_PROMPT}\n\n{prompt}"}]}
                ],
            )
            return (response.text or "").strip()

        text_response = await asyncio.to_thread(_call)
        cleaned = text_response.strip().removeprefix("```json").removesuffix("```").strip()
        return json.loads(cleaned)

    def _heuristic_enrichment(self, aggregate: dict[str, Any]) -> dict[str, Any]:
        findings: list[dict[str, Any]] = []

        trivy = aggregate["scanners"]["trivy"]["raw"]
        if isinstance(trivy, dict):
            for result in trivy.get("Results") or []:
                target = result.get("Target")
                for vuln in result.get("Vulnerabilities") or []:
                    sev = (vuln.get("Severity") or "MEDIUM").lower()
                    findings.append(
                        {
                            "id": vuln.get("VulnerabilityID") or f"trivy-{len(findings)+1}",
                            "title": vuln.get("Title") or vuln.get("PkgName") or "Dependencia vulnerable",
                            "severity": sev if sev in {"critical", "high", "medium", "low"} else "medium",
                            "standard": {
                                "stride": ["Tampering"],
                                "owasp_top10": ["A06:2021"],
                                "owasp_api_top10": [],
                                "cwe": ["CWE-1104"],
                                "nist_csf": ["Identify", "Protect"],
                                "data_protection": [],
                                "cis": [],
                                "sdlc": ["Dependency Hygiene"],
                            },
                            "evidence": {
                                "scanner": "trivy",
                                "file": target,
                                "line": None,
                                "snippet": vuln.get("PrimaryURL") or "",
                            },
                            "risk_forensics": f"Paquete {vuln.get('PkgName')} vulnerable en {target}",
                            "false_positive_likelihood": "low",
                            "recommended_patch": {
                                "type": "dependency",
                                "patch": (
                                    f"Actualizar {vuln.get('PkgName')} a una version segura "
                                    f"(>= {vuln.get('FixedVersion') or 'ultima estable'})."
                                ),
                            },
                            "gitlab_issue": {
                                "title": f"[Centinela - OWASP A06:2021] {vuln.get('PkgName')}",
                                "body": (
                                    f"Se detecto vulnerabilidad {vuln.get('VulnerabilityID')} en {vuln.get('PkgName')}.\n"
                                    f"Riesgo: {sev.upper()}.\n"
                                    f"Remediacion: actualizar a {vuln.get('FixedVersion') or 'version segura'}."
                                ),
                                "labels": ["security", "compliance", "supply-chain"],
                            },
                        }
                    )

        trufflehog = aggregate["scanners"]["trufflehog"]["raw"]
        if isinstance(trufflehog, list):
            for leak in trufflehog[:200]:
                path = leak.get("SourceMetadata", {}).get("Data", {}).get("Filesystem", {}).get("file")
                findings.append(
                    {
                        "id": leak.get("DetectorName") or f"secret-{len(findings)+1}",
                        "title": "Exposicion potencial de secreto o PII",
                        "severity": "high",
                        "standard": {
                            "stride": ["Information Disclosure"],
                            "owasp_top10": ["A02:2021"],
                            "owasp_api_top10": [],
                            "cwe": ["CWE-200"],
                            "nist_csf": ["Protect", "Detect"],
                            "data_protection": ["GDPR", "LFPDPPP"],
                            "cis": [],
                            "sdlc": ["Secret Scanning"],
                        },
                        "evidence": {
                            "scanner": "trufflehog",
                            "file": path,
                            "line": None,
                            "snippet": leak.get("Raw") or "",
                        },
                        "risk_forensics": "Posible filtracion de credenciales o datos personales en repositorio.",
                        "false_positive_likelihood": "medium",
                        "recommended_patch": {
                            "type": "code",
                            "patch": "Remover secreto del codigo, rotar credenciales y mover a variables seguras (.env/vault).",
                        },
                        "gitlab_issue": {
                            "title": "[Centinela - Data Protection] Posible exposicion de secreto",
                            "body": "Se detecto posible secreto/PII en el repositorio. Revocar, rotar y limpiar historial.",
                            "labels": ["security", "compliance", "pii"],
                        },
                    }
                )

        medusa = aggregate["scanners"]["medusa"]["raw"]
        if isinstance(medusa, dict):
            for item in medusa.get("results") or []:
                rule_id = item.get("check_id") or "CWE-Other"
                extra = item.get("extra") or {}
                findings.append(
                    {
                        "id": rule_id,
                        "title": extra.get("message") or "Hallazgo SAST",
                        "severity": (extra.get("severity") or "medium").lower(),
                        "standard": {
                            "stride": ["Tampering"],
                            "owasp_top10": ["A03:2021"],
                            "owasp_api_top10": ["API8:2023"],
                            "cwe": [rule_id if rule_id.startswith("CWE-") else "CWE-Other"],
                            "nist_csf": ["Identify", "Protect"],
                            "data_protection": [],
                            "cis": [],
                            "sdlc": ["SAST"],
                        },
                        "evidence": {
                            "scanner": "medusa",
                            "file": item.get("path"),
                            "line": (item.get("start") or {}).get("line"),
                            "snippet": extra.get("lines") or "",
                        },
                        "risk_forensics": "Patron de codigo inseguro detectado por SAST.",
                        "false_positive_likelihood": "medium",
                        "recommended_patch": {
                            "type": "code",
                            "patch": extra.get("fix") or "Aplicar validacion estricta de entrada y controles de autorizacion.",
                        },
                        "gitlab_issue": {
                            "title": f"[Centinela - CWE] {rule_id}",
                            "body": extra.get("message") or "Hallazgo de debilidad de software clasificada por CWE.",
                            "labels": ["security", "compliance", "cwe"],
                        },
                    }
                )

        severity = self._severity_totals({"findings": findings})
        score = max(0, 100 - (severity["critical"] * 20 + severity["high"] * 8 + severity["medium"] * 3 + severity["low"]))

        return {
            "summary": {
                "project": str(aggregate.get("project_id")),
                "commit_sha": aggregate.get("commit_sha"),
                **severity,
                "compliance_score": score,
            },
            "findings": findings,
            "coverage": {
                "stride": "partial",
                "owasp_top10": "partial",
                "owasp_api_top10": "partial",
                "cwe": "partial",
                "data_protection": "partial",
                "supply_chain": "partial",
                "infrastructure": "partial",
                "sdlc_security": "partial",
                "nist_csf": "partial",
            },
            "engine": "heuristic-fallback",
        }

    def _severity_totals(self, enriched: dict[str, Any]) -> dict[str, int]:
        totals = {"critical": 0, "high": 0, "medium": 0, "low": 0}
        for finding in enriched.get("findings") or []:
            sev = (finding.get("severity") or "").lower()
            if sev in totals:
                totals[sev] += 1
        return totals

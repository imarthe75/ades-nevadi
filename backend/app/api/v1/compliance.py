"""
Módulo de auditoría de cumplimiento integral (Centinela-AI).

Endpoint principal:
POST /audit/compliance
"""

from __future__ import annotations

import json
import uuid
from datetime import datetime
from typing import Any

from fastapi import APIRouter, Depends, Header, HTTPException, Request, status
from pydantic import BaseModel, Field
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db
from app.services.compliance_orchestrator import ComplianceOrchestrator
from app.services.gitlab_client import GitLabClient, verify_gitlab_webhook_signature


router = APIRouter(prefix="/audit", tags=["compliance"])


class ComplianceWebhookPayload(BaseModel):
    project_id: int | str
    commit_sha: str = Field(..., min_length=7, max_length=64)
    repository_url: str = Field(..., min_length=8, max_length=1000)
    branch: str | None = Field(default=None, max_length=255)
    merge_request_iid: int | None = None


class ComplianceAuditResponse(BaseModel):
    scan_id: uuid.UUID
    status: str
    findings_total: int
    critical: int
    high: int
    medium: int
    low: int
    gitlab_issues_created: int


async def _require_internal_call(x_internal_api_key: str | None = Header(default=None)) -> None:
    if settings.ADES_INTERNAL_API_KEY and x_internal_api_key != settings.ADES_INTERNAL_API_KEY:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="API key interna invalida")


def _issue_title_for_finding(finding: dict[str, Any]) -> str:
    standard = (finding.get("standard") or {}).get("owasp_top10") or []
    prefix = standard[0] if standard else "Compliance"
    return f"[Centinela - {prefix}] {finding.get('title', 'Hallazgo de seguridad')}"


def _issue_body_for_finding(finding: dict[str, Any]) -> str:
    evidence = finding.get("evidence") or {}
    patch = (finding.get("recommended_patch") or {}).get("patch", "Sin parche sugerido")
    return (
        "## Riesgo Forense\n"
        f"{finding.get('risk_forensics', 'Hallazgo sin contexto forense.')}\n\n"
        "## Evidencia\n"
        f"- Scanner: {evidence.get('scanner', 'N/A')}\n"
        f"- Archivo: {evidence.get('file', 'N/A')}\n"
        f"- Linea: {evidence.get('line', 'N/A')}\n\n"
        "## Remediacion Sugerida\n"
        f"```\n{patch}\n```\n"
    )


@router.post("/compliance", response_model=ComplianceAuditResponse)
async def run_compliance_audit(
    payload: ComplianceWebhookPayload,
    request: Request,
    _auth: None = Depends(_require_internal_call),
    db: AsyncSession = Depends(get_db),
    x_gitlab_token: str | None = Header(default=None, alias="X-Gitlab-Token"),
):
    raw = await request.body()
    if settings.GITLAB_WEBHOOK_SECRET and not verify_gitlab_webhook_signature(
        raw_body=raw,
        secret=settings.GITLAB_WEBHOOK_SECRET,
        provided_token=x_gitlab_token,
    ):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Webhook GitLab no autorizado")

    orchestrator = ComplianceOrchestrator()
    try:
        result = await orchestrator.run(
            repository_url=payload.repository_url,
            commit_sha=payload.commit_sha,
            project_id=payload.project_id,
        )
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=f"Pipeline compliance no disponible: {exc}") from exc

    enriched = result.get("enriched") or {}
    severity = result.get("severity") or {"critical": 0, "high": 0, "medium": 0, "low": 0}

    scan_id = uuid.uuid4()
    now_dt = datetime.utcnow()
    await db.execute(
        text(
            """
            INSERT INTO public.ades_compliance_scans (
                id,
                project_id,
                commit_sha,
                repository_url,
                branch,
                merge_request_iid,
                status,
                severity_critical,
                severity_high,
                severity_medium,
                severity_low,
                findings_total,
                raw_scan,
                enriched_scan,
                scanned_at,
                usuario_creacion,
                usuario_modificacion
            ) VALUES (
                :id,
                :project_id,
                :commit_sha,
                :repository_url,
                :branch,
                :merge_request_iid,
                :status,
                :severity_critical,
                :severity_high,
                :severity_medium,
                :severity_low,
                :findings_total,
                CAST(:raw_scan AS jsonb),
                CAST(:enriched_scan AS jsonb),
                :scanned_at,
                :usuario,
                :usuario
            )
            """
        ),
        {
            "id": str(scan_id),
            "project_id": str(payload.project_id),
            "commit_sha": payload.commit_sha,
            "repository_url": payload.repository_url,
            "branch": payload.branch,
            "merge_request_iid": payload.merge_request_iid,
            "status": result.get("status", "completed"),
            "severity_critical": severity.get("critical", 0),
            "severity_high": severity.get("high", 0),
            "severity_medium": severity.get("medium", 0),
            "severity_low": severity.get("low", 0),
            "findings_total": len((enriched.get("findings") or [])),
            "raw_scan": json.dumps(result.get("aggregate") or {}, ensure_ascii=False),
            "enriched_scan": json.dumps(enriched, ensure_ascii=False),
            "scanned_at": now_dt,
            "usuario": "centinela-ai",
        },
    )

    created_issues = 0
    critical_or_high = [
        f
        for f in (enriched.get("findings") or [])
        if (f.get("severity") or "").lower() in {"critical", "high"}
    ]

    if settings.GITLAB_API_TOKEN and critical_or_high:
        client = GitLabClient(base_url=settings.GITLAB_BASE_URL, api_token=settings.GITLAB_API_TOKEN)
        for finding in critical_or_high[:30]:
            title = finding.get("gitlab_issue", {}).get("title") or _issue_title_for_finding(finding)
            body = finding.get("gitlab_issue", {}).get("body") or _issue_body_for_finding(finding)
            labels = finding.get("gitlab_issue", {}).get("labels") or ["security", "compliance"]
            try:
                issue = await client.create_issue(payload.project_id, title=title, description=body, labels=labels)
                await db.execute(
                    text(
                        """
                        INSERT INTO public.ades_compliance_issues (
                            id,
                            scan_id,
                            project_id,
                            finding_id,
                            issue_iid,
                            issue_web_url,
                            issue_title,
                            severity,
                            standard,
                            estado,
                            usuario_creacion,
                            usuario_modificacion
                        ) VALUES (
                            :id,
                            :scan_id,
                            :project_id,
                            :finding_id,
                            :issue_iid,
                            :issue_web_url,
                            :issue_title,
                            :severity,
                            :standard,
                            'opened',
                            :usuario,
                            :usuario
                        )
                        """
                    ),
                    {
                        "id": str(uuid.uuid4()),
                        "scan_id": str(scan_id),
                        "project_id": str(payload.project_id),
                        "finding_id": finding.get("id"),
                        "issue_iid": issue.get("iid"),
                        "issue_web_url": issue.get("web_url"),
                        "issue_title": title,
                        "severity": (finding.get("severity") or "unknown").lower(),
                        "standard": ",".join((finding.get("standard") or {}).get("owasp_top10") or []),
                        "usuario": "centinela-ai",
                    },
                )
                created_issues += 1
            except Exception:
                continue

    await db.commit()

    return ComplianceAuditResponse(
        scan_id=scan_id,
        status=result.get("status", "completed"),
        findings_total=len((enriched.get("findings") or [])),
        critical=severity.get("critical", 0),
        high=severity.get("high", 0),
        medium=severity.get("medium", 0),
        low=severity.get("low", 0),
        gitlab_issues_created=created_issues,
    )

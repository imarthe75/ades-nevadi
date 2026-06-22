"""
Reportes de postura de seguridad y cumplimiento normativo (Centinela-AI).
"""

from __future__ import annotations

import io
import json
import uuid
from datetime import datetime
from pathlib import Path
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import StreamingResponse
from jinja2 import Environment, FileSystemLoader
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import AdesUser, get_ades_user


router = APIRouter(prefix="/reports", tags=["reports"])

_TEMPLATES_DIR = Path(__file__).parent.parent.parent / "templates" / "reports"


def _jinja() -> Environment:
    _TEMPLATES_DIR.mkdir(parents=True, exist_ok=True)
    return Environment(loader=FileSystemLoader(str(_TEMPLATES_DIR)), autoescape=True)


def _row_to_response(row: Any) -> dict[str, Any]:
    enriched = row.get("enriched_scan") or {}
    if isinstance(enriched, str):
        try:
            enriched = json.loads(enriched)
        except json.JSONDecodeError:
            enriched = {}

    return {
        "id": str(row.get("id")),
        "project_id": row.get("project_id"),
        "commit_sha": row.get("commit_sha"),
        "repository_url": row.get("repository_url"),
        "branch": row.get("branch"),
        "status": row.get("status"),
        "severity": {
            "critical": row.get("severity_critical", 0),
            "high": row.get("severity_high", 0),
            "medium": row.get("severity_medium", 0),
            "low": row.get("severity_low", 0),
        },
        "findings_total": row.get("findings_total", 0),
        "scanned_at": row.get("scanned_at").isoformat() if row.get("scanned_at") else None,
        "coverage": (enriched or {}).get("coverage", {}),
        "summary": (enriched or {}).get("summary", {}),
        "findings": (enriched or {}).get("findings", []),
    }


@router.get("/compliance/latest")
async def latest_compliance_report(
    project_id: str = Query(..., min_length=1, max_length=200),
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 3:
        raise HTTPException(status_code=403, detail="No autorizado")

    res = await db.execute(
        text(
            """
            SELECT *
            FROM public.ades_compliance_scans
            WHERE project_id = :project_id
            ORDER BY scanned_at DESC
            LIMIT 1
            """
        ),
        {"project_id": project_id},
    )
    row = res.mappings().first()
    if not row:
        raise HTTPException(status_code=404, detail="No hay escaneos para el proyecto solicitado")

    return _row_to_response(row)


@router.get("/compliance/{scan_id}")
async def compliance_report(
    scan_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 3:
        raise HTTPException(status_code=403, detail="No autorizado")

    res = await db.execute(
        text("SELECT * FROM public.ades_compliance_scans WHERE id = :id"),
        {"id": str(scan_id)},
    )
    row = res.mappings().first()
    if not row:
        raise HTTPException(status_code=404, detail="Escaneo no encontrado")

    return _row_to_response(row)


@router.get("/compliance/{scan_id}/pdf")
async def compliance_report_pdf(
    scan_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    if ades_user.nivel_acceso > 3:
        raise HTTPException(status_code=403, detail="No autorizado")

    res = await db.execute(
        text("SELECT * FROM public.ades_compliance_scans WHERE id = :id"),
        {"id": str(scan_id)},
    )
    row = res.mappings().first()
    if not row:
        raise HTTPException(status_code=404, detail="Escaneo no encontrado")

    data = _row_to_response(row)
    findings = data.get("findings") or []

    ctx = {
        "scan_id": str(scan_id),
        "project_id": data.get("project_id"),
        "commit_sha": data.get("commit_sha"),
        "repository_url": data.get("repository_url"),
        "scanned_at": data.get("scanned_at") or datetime.utcnow().isoformat(),
        "severity": data.get("severity"),
        "findings_total": data.get("findings_total"),
        "coverage": data.get("coverage"),
        "summary": data.get("summary"),
        "critical_findings": [f for f in findings if (f.get("severity") or "").lower() == "critical"][:20],
        "high_findings": [f for f in findings if (f.get("severity") or "").lower() == "high"][:30],
    }

    template = _jinja().get_template("security_posture_report.html")
    html = template.render(**ctx)

    try:
        from weasyprint import HTML
    except ImportError as exc:
        raise HTTPException(status_code=503, detail="WeasyPrint no disponible") from exc

    pdf_bytes = HTML(string=html, base_url=str(_TEMPLATES_DIR)).write_pdf()
    file_name = f"compliance_posture_{scan_id}.pdf"

    return StreamingResponse(
        io.BytesIO(pdf_bytes),
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{file_name}"'},
    )

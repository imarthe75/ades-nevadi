"""
Tareas Celery: procesamiento OCR vía Paperless-ngx (FASE 24P).

  - resolver_ocr_documento  — Obtiene doc_id y texto OCR de Paperless para un
                               documento subido, actualiza ades_expediente_documentos.
"""
from __future__ import annotations

import asyncio
import logging
import time

from celery import shared_task
from sqlalchemy import create_engine, text
from sqlalchemy.orm import Session

from app.core.config import settings

log = logging.getLogger(__name__)


def _get_db_engine():
    url = str(settings.DATABASE_URL).replace("+asyncpg", "+psycopg2")
    return create_engine(url, pool_size=2, max_overflow=0, pool_pre_ping=True)


@shared_task(
    name="app.worker.tasks.ocr.resolver_ocr_documento",
    bind=True,
    max_retries=5,
    default_retry_delay=30,
)
def resolver_ocr_documento(self, doc_uuid: str, task_id: str) -> dict:
    """
    Tarea Celery que:
    1. Consulta Paperless el estado de la tarea de importación (task_id).
    2. Al completarse, obtiene el paperless_doc_id y el texto OCR.
    3. Actualiza estado_ocr, paperless_doc_id y ocr_texto en ades_expediente_documentos.
    """
    from app.services import paperless as pl

    MAX_ESPERA = 120  # segundos máximos esperando al OCR de Paperless

    async def _resolve() -> dict:
        deadline = time.time() + MAX_ESPERA
        while time.time() < deadline:
            tarea = await pl.obtener_estado_tarea(task_id)
            if not tarea:
                await asyncio.sleep(5)
                continue

            status = tarea.get("status", "").upper()
            if status == "SUCCESS":
                paperless_id = tarea.get("related_document")
                if not paperless_id:
                    return {"estado": "ERROR", "detalle": "task SUCCESS pero sin related_document"}
                ocr = await pl.obtener_texto_ocr(int(paperless_id))
                return {
                    "estado": "PROCESADO",
                    "paperless_doc_id": paperless_id,
                    "ocr_texto": ocr[:50_000] if ocr else "",  # tope 50 KB
                }
            if status in ("FAILURE", "REVOKED"):
                return {"estado": "ERROR", "detalle": f"Paperless task {status}"}

            await asyncio.sleep(8)

        return {"estado": "ERROR", "detalle": "Timeout esperando OCR de Paperless"}

    resultado = asyncio.run(_resolve())

    engine = _get_db_engine()
    with Session(engine) as session:
        session.execute(
            text("""
                UPDATE ades_expediente_documentos
                   SET estado_ocr      = :estado,
                       paperless_doc_id = :plid,
                       ocr_texto       = :ocr
                 WHERE id = :doc_uuid
            """),
            {
                "estado":    resultado["estado"],
                "plid":      resultado.get("paperless_doc_id"),
                "ocr":       resultado.get("ocr_texto"),
                "doc_uuid":  doc_uuid,
            },
        )
        session.commit()

    log.info(
        "ocr_resuelto doc=%s estado=%s paperless_id=%s",
        doc_uuid, resultado["estado"], resultado.get("paperless_doc_id"),
    )
    return resultado

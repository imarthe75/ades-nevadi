"""
Endpoints FastAPI — Sincronización manual del catálogo SEPOMEX.

  POST /sepomex/sync         → Encola app.worker.tasks.sepomex.sync_sepomex_weekly
  GET  /sepomex/sync/{id}    → Estado de la tarea Celery

Solo ADMIN_GLOBAL (nivel_acceso == 5). Mutante → pasa por AuditMiddleware.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from celery.result import AsyncResult

from app.core.security import AdesUser, get_ades_user
from app.worker.tasks.sepomex import sync_sepomex_weekly

router = APIRouter(prefix="/sepomex", tags=["sepomex"])


@router.post("/sync")
async def iniciar_sync(user: AdesUser = Depends(get_ades_user)):
    """Encola la sincronización SEPOMEX. Solo ADMIN_GLOBAL."""
    if not user.es_admin_global():
        raise HTTPException(status_code=403, detail="Solo administradores globales pueden sincronizar SEPOMEX.")

    task = sync_sepomex_weekly.delay()
    return {"task_id": task.id, "estado": "encolado"}


@router.get("/sync/{task_id}")
async def estado_sync(task_id: str, user: AdesUser = Depends(get_ades_user)):
    """Estado de una tarea de sincronización SEPOMEX."""
    if not user.es_admin_global():
        raise HTTPException(status_code=403, detail="Solo administradores globales.")

    result = AsyncResult(task_id)
    payload: dict = {"task_id": task_id, "estado": result.state}
    if result.state == "SUCCESS":
        payload["resultado"] = result.result
    elif result.state == "FAILURE":
        payload["error"] = str(result.result)
    return payload

"""
/reinscripcion — Reinscripción Masiva de Alumnos (PE-015)

  GET  /reinscripcion/{ciclo_destino_id}/estado         — lista alumnos con estado
  POST /reinscripcion/{ciclo_destino_id}/validar-masivo — ejecuta validación completa
  POST /reinscripcion/{ciclo_destino_id}/aprobar-masivo — aprueba validados y promueve
  GET  /reinscripcion/{ciclo_destino_id}/reporte        — resumen estadístico
  PATCH /reinscripcion/{registro_id}                    — acción individual (aprobar/rechazar)
"""
from __future__ import annotations

from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_ades_user, AdesUser

router = APIRouter(prefix="/reinscripcion", tags=["reinscripcion"])

_ROLES_ADMIN = 3   # DIRECTOR o superior


# ── schemas ───────────────────────────────────────────────────────────────────

class AccionIndividual(BaseModel):
    accion: str           # APROBAR | RECHAZAR
    razon_rechazo: Optional[str] = None


# ── helpers ───────────────────────────────────────────────────────────────────

async def _get_ciclo_or_404(db: AsyncSession, ciclo_id: str):
    row = await db.execute(
        text("SELECT id, nombre_ciclo, es_vigente FROM ades_ciclos_escolares WHERE id = :id::uuid AND is_active = TRUE"),
        {"id": ciclo_id},
    )
    ciclo = row.mappings().first()
    if not ciclo:
        raise HTTPException(404, "Ciclo escolar no encontrado")
    return ciclo


# ── GET /reinscripcion/{ciclo_destino_id}/estado ──────────────────────────────
@router.get("/{ciclo_destino_id}/estado")
async def estado_reinscripcion(
    ciclo_destino_id: UUID,
    estado: Optional[str] = None,       # filtro: PENDIENTE|VALIDADO|APROBADO|RECHAZADO
    plantel_id: Optional[UUID] = None,
    page: int = 1,
    por_pagina: int = 50,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Lista todos los alumnos con su estado de reinscripción para el ciclo destino."""
    await _get_ciclo_or_404(db, str(ciclo_destino_id))

    filters = ["rc.ciclo_destino_id = :ciclo_id::uuid", "rc.is_active = TRUE"]
    params: dict = {
        "ciclo_id": str(ciclo_destino_id),
        "limit": por_pagina,
        "offset": (page - 1) * por_pagina,
    }

    if estado:
        filters.append("rc.estado = :estado")
        params["estado"] = estado
    if plantel_id:
        filters.append("g.plantel_id = :plantel_id::uuid")
        params["plantel_id"] = str(plantel_id)
    if ades_user.nivel_acceso > 1 and ades_user.plantel_id:
        filters.append("g.plantel_id = :mi_plantel::uuid")
        params["mi_plantel"] = str(ades_user.plantel_id)

    where = " AND ".join(filters)

    rows = await db.execute(text(f"""
        SELECT
            rc.id,
            rc.estudiante_id,
            p.nombre || ' ' || p.apellido_paterno                          AS alumno,
            est.matricula,
            gr.nombre_grado || ' ' || g.nombre_grupo                       AS grado_grupo,
            pl.nombre_plantel,
            rc.estado,
            rc.tiene_adeudos,
            rc.monto_adeudado,
            rc.bloqueantes,
            rc.razon_rechazo,
            rc.fecha_validacion,
            rc.fecha_aprobacion
        FROM ades_reinscripcion_ciclo rc
        JOIN ades_estudiantes est ON est.id   = rc.estudiante_id
        JOIN ades_personas    p   ON p.id     = est.persona_id
        JOIN ades_inscripciones i ON i.estudiante_id = rc.estudiante_id
                                 AND i.ciclo_escolar_id = rc.ciclo_origen_id
                                 AND i.is_active = TRUE
        JOIN ades_grupos g    ON g.id         = i.grupo_id
        JOIN ades_grados gr   ON gr.id        = g.grado_id
        JOIN ades_planteles pl ON pl.id       = g.plantel_id
        WHERE {where}
        ORDER BY pl.nombre_plantel, gr.nombre_grado, p.apellido_paterno
        LIMIT :limit OFFSET :offset
    """), params)

    count_row = await db.execute(text(f"""
        SELECT COUNT(*)
        FROM ades_reinscripcion_ciclo rc
        JOIN ades_inscripciones i ON i.estudiante_id = rc.estudiante_id
                                 AND i.ciclo_escolar_id = rc.ciclo_origen_id
                                 AND i.is_active = TRUE
        JOIN ades_grupos g ON g.id = i.grupo_id
        WHERE {where.replace(':limit', '99999').replace(':offset', '0')}
    """), {k: v for k, v in params.items() if k not in ("limit", "offset")})

    total = count_row.scalar_one_or_none() or 0

    return {
        "data": [dict(r) for r in rows.mappings().all()],
        "total": total,
        "page": page,
        "por_pagina": por_pagina,
    }


# ── POST /reinscripcion/{ciclo_destino_id}/validar-masivo ─────────────────────
@router.post("/{ciclo_destino_id}/validar-masivo")
async def validar_masivo(
    ciclo_destino_id: UUID,
    ciclo_origen_id: UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """
    Ejecuta la validación masiva de todos los alumnos del ciclo_origen.
    Crea/actualiza filas en ades_reinscripcion_ciclo con estado VALIDADO o PENDIENTE.
    """
    if ades_user.nivel_acceso > _ROLES_ADMIN:
        raise HTTPException(403, "Solo DIRECTOR/ADMIN puede ejecutar reinscripción masiva")

    await _get_ciclo_or_404(db, str(ciclo_destino_id))
    await _get_ciclo_or_404(db, str(ciclo_origen_id))

    result = await db.execute(
        text("SELECT pg_validar_reinscripcion_masiva(:origen::uuid, :destino::uuid)"),
        {"origen": str(ciclo_origen_id), "destino": str(ciclo_destino_id)},
    )
    resumen = result.scalar_one()
    await db.commit()
    return {"ok": True, "resumen": resumen}


# ── POST /reinscripcion/{ciclo_destino_id}/aprobar-masivo ─────────────────────
@router.post("/{ciclo_destino_id}/aprobar-masivo")
async def aprobar_masivo(
    ciclo_destino_id: UUID,
    ciclo_origen_id: UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """
    Aprueba todos los alumnos en estado VALIDADO y ejecuta cerrar_ciclo_y_promover().
    Solo procesa alumnos VALIDADOS — los PENDIENTES/RECHAZADOS quedan intactos.
    """
    if ades_user.nivel_acceso > _ROLES_ADMIN:
        raise HTTPException(403, "Solo DIRECTOR/ADMIN puede aprobar reinscripción masiva")

    await _get_ciclo_or_404(db, str(ciclo_destino_id))

    # Marcar como APROBADO todos los VALIDADOS
    aprobados_row = await db.execute(
        text("""
            UPDATE ades_reinscripcion_ciclo
               SET estado           = 'APROBADO',
                   aprobado_por     = :usuario_id::uuid,
                   fecha_aprobacion = now(),
                   fecha_modificacion = now(),
                   row_version      = row_version + 1
             WHERE ciclo_destino_id = :destino::uuid
               AND estado           = 'VALIDADO'
               AND is_active        = TRUE
            RETURNING id
        """),
        {"destino": str(ciclo_destino_id), "usuario_id": str(ades_user.usuario_id)},
    )
    n_aprobados = len(aprobados_row.fetchall())

    # Ejecutar promoción efectiva
    promo_row = await db.execute(
        text("SELECT cerrar_ciclo_y_promover(:origen::uuid, :destino::uuid, :usuario)"),
        {
            "origen":  str(ciclo_origen_id),
            "destino": str(ciclo_destino_id),
            "usuario": str(ades_user.usuario_id),
        },
    )
    resultado_promo = promo_row.scalar_one()
    await db.commit()

    return {
        "ok": True,
        "aprobados": n_aprobados,
        "resultado_promocion": resultado_promo,
    }


# ── GET /reinscripcion/{ciclo_destino_id}/reporte ─────────────────────────────
@router.get("/{ciclo_destino_id}/reporte")
async def reporte_reinscripcion(
    ciclo_destino_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
    """Resumen estadístico del proceso de reinscripción para el ciclo destino."""
    await _get_ciclo_or_404(db, str(ciclo_destino_id))

    row = await db.execute(text("""
        SELECT
            COUNT(*)                                                            AS total,
            COUNT(*) FILTER (WHERE estado = 'PENDIENTE')                        AS pendientes,
            COUNT(*) FILTER (WHERE estado = 'VALIDADO')                         AS validados,
            COUNT(*) FILTER (WHERE estado = 'APROBADO')                         AS aprobados,
            COUNT(*) FILTER (WHERE estado = 'RECHAZADO')                        AS rechazados,
            COUNT(*) FILTER (WHERE tiene_adeudos = TRUE)                        AS con_adeudos,
            COALESCE(SUM(monto_adeudado), 0)                                    AS monto_total_adeudado,
            ROUND(COUNT(*) FILTER (WHERE estado = 'APROBADO') * 100.0
                / NULLIF(COUNT(*), 0), 1)                                       AS pct_completado
        FROM ades_reinscripcion_ciclo
        WHERE ciclo_destino_id = :ciclo_id::uuid AND is_active = TRUE
    """), {"ciclo_id": str(ciclo_destino_id)})

    stats = dict(row.mappings().first() or {})

    # Desglose por plantel
    por_plantel = await db.execute(text("""
        SELECT
            pl.nombre_plantel,
            COUNT(*)                                                AS total,
            COUNT(*) FILTER (WHERE rc.estado = 'APROBADO')         AS aprobados,
            COUNT(*) FILTER (WHERE rc.estado = 'PENDIENTE')        AS pendientes,
            COUNT(*) FILTER (WHERE rc.tiene_adeudos = TRUE)        AS con_adeudos
        FROM ades_reinscripcion_ciclo rc
        JOIN ades_inscripciones i  ON i.estudiante_id = rc.estudiante_id
                                   AND i.ciclo_escolar_id = rc.ciclo_origen_id
                                   AND i.is_active = TRUE
        JOIN ades_grupos    g  ON g.id  = i.grupo_id
        JOIN ades_planteles pl ON pl.id = g.plantel_id
        WHERE rc.ciclo_destino_id = :ciclo_id::uuid AND rc.is_active = TRUE
        GROUP BY pl.nombre_plantel
        ORDER BY pl.nombre_plantel
    """), {"ciclo_id": str(ciclo_destino_id)})

    return {
        "resumen": stats,
        "por_plantel": [dict(r) for r in por_plantel.mappings().all()],
    }


# ── PATCH /reinscripcion/{registro_id} ───────────────────────────────────────
@router.patch("/{registro_id}")
async def accion_individual(
    registro_id: UUID,
    body: AccionIndividual,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Aprueba o rechaza manualmente un alumno individual."""
    if ades_user.nivel_acceso > _ROLES_ADMIN:
        raise HTTPException(403, "Sin permiso para esta acción")

    if body.accion not in ("APROBAR", "RECHAZAR"):
        raise HTTPException(422, "accion debe ser APROBAR o RECHAZAR")

    if body.accion == "RECHAZAR" and not body.razon_rechazo:
        raise HTTPException(422, "razon_rechazo es requerida al rechazar")

    nuevo_estado = "APROBADO" if body.accion == "APROBAR" else "RECHAZADO"

    result = await db.execute(text("""
        UPDATE ades_reinscripcion_ciclo
           SET estado             = :estado,
               aprobado_por       = :usuario::uuid,
               razon_rechazo      = :razon,
               fecha_aprobacion   = CASE WHEN :estado = 'APROBADO' THEN now() ELSE NULL END,
               fecha_modificacion = now(),
               row_version        = row_version + 1
         WHERE id = :id::uuid AND is_active = TRUE
        RETURNING id, estado
    """), {
        "estado":   nuevo_estado,
        "usuario":  str(ades_user.usuario_id),
        "razon":    body.razon_rechazo,
        "id":       str(registro_id),
    })
    updated = result.mappings().first()
    if not updated:
        raise HTTPException(404, "Registro de reinscripción no encontrado")

    await db.commit()
    return dict(updated)


# ── PE-016: Verificación de no-adeudo ──────────────────────────────────────
@router.get("/no-adeudo/{estudiante_id}")
async def verificar_no_adeudo(
    estudiante_id: UUID,
    ciclo_escolar_id: Optional[UUID] = None,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Verifica si el alumno tiene saldo pendiente (PE-016). Retorna tiene_adeudo + detalle."""
    params: dict = {"est_id": str(estudiante_id)}
    filtro_ciclo = ""
    if ciclo_escolar_id:
        filtro_ciclo = "AND cp.ciclo_escolar_id = :ciclo_id::uuid"
        params["ciclo_id"] = str(ciclo_escolar_id)

    rows = (await db.execute(text(f"""
        SELECT cc.nombre AS concepto, cp.monto_cobrado,
               cp.monto_pagado, cp.descuento, cp.saldo_pendiente,
               cp.fecha_vencimiento, cp.estatus
        FROM ades_cuotas_pagos cp
        JOIN ades_cuotas_concepto cc ON cc.id = cp.concepto_id
        WHERE cp.estudiante_id = :est_id::uuid
          AND cp.is_active = TRUE
          AND cp.saldo_pendiente > 0
          {filtro_ciclo}
        ORDER BY cp.fecha_vencimiento
    """), params)).mappings().all()

    adeudos = [dict(r) for r in rows]
    total_adeudo = sum(float(r["saldo_pendiente"]) for r in adeudos)

    return {
        "estudiante_id": str(estudiante_id),
        "tiene_adeudo": len(adeudos) > 0,
        "total_adeudo": total_adeudo,
        "adeudos": adeudos,
        "puede_reinscribirse": len(adeudos) == 0,
    }

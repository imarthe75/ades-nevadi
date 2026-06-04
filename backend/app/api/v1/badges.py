from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from app.core.database import get_db
from app.core.security import get_current_user

router = APIRouter(prefix="/badges", tags=["badges"])


async def _resolve_usuario_id(db: AsyncSession, sub: str) -> str | None:
    r = await db.execute(text("SELECT id FROM ades_usuarios WHERE oidc_sub = :sub"), {"sub": sub})
    row = r.fetchone()
    return str(row[0]) if row else None


# ── Catálogo ──────────────────────────────────────────────────────────────────

@router.get("")
async def listar_badges(
    tipo: str | None = None,
    plantel_id: str | None = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    filters = ["b.is_active = TRUE"]
    params: dict = {}
    if tipo:
        filters.append("b.tipo = :tipo")
        params["tipo"] = tipo
    if plantel_id:
        filters.append("(b.plantel_id = :plantel_id OR b.plantel_id IS NULL)")
        params["plantel_id"] = plantel_id
    where = " AND ".join(filters)

    rows = await db.execute(text(f"""
        SELECT b.id, b.nombre, b.descripcion, b.icono, b.color, b.tipo,
               b.criterio_tipo, b.criterio_metrica, b.criterio_valor,
               b.plantel_id,
               COUNT(DISTINCT o.estudiante_id) AS total_otorgados
          FROM ades_badges b
          LEFT JOIN ades_badge_otorgados o ON o.badge_id = b.id
         WHERE {where}
         GROUP BY b.id
         ORDER BY b.tipo, b.nombre
    """), params)
    return [dict(r._mapping) for r in rows.fetchall()]


@router.post("")
async def crear_badge(
    payload: dict,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    row = await db.execute(text("""
        INSERT INTO ades_badges
               (nombre, descripcion, icono, color, tipo,
                criterio_tipo, criterio_metrica, criterio_valor, plantel_id)
        VALUES (:nombre, :descripcion, :icono, :color, :tipo,
                :criterio_tipo, :criterio_metrica, :criterio_valor, :plantel_id)
        RETURNING id, nombre, tipo
    """), {
        "nombre":           payload.get("nombre"),
        "descripcion":      payload.get("descripcion"),
        "icono":            payload.get("icono", "pi-star"),
        "color":            payload.get("color", "#D02030"),
        "tipo":             payload.get("tipo"),
        "criterio_tipo":    payload.get("criterio_tipo", "MANUAL"),
        "criterio_metrica": payload.get("criterio_metrica"),
        "criterio_valor":   payload.get("criterio_valor"),
        "plantel_id":       payload.get("plantel_id"),
    })
    await db.commit()
    return dict(row.fetchone()._mapping)


@router.get("/{badge_id}")
async def detalle_badge(
    badge_id: str,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    row = await db.execute(text("""
        SELECT b.*, COUNT(DISTINCT o.estudiante_id) AS total_otorgados
          FROM ades_badges b
          LEFT JOIN ades_badge_otorgados o ON o.badge_id = b.id
         WHERE b.id = :id AND b.is_active = TRUE
         GROUP BY b.id
    """), {"id": badge_id})
    badge = row.fetchone()
    if not badge:
        raise HTTPException(404, "Badge no encontrado")

    # Alumnos que lo tienen
    alumnos = await db.execute(text("""
        SELECT o.id AS otorgado_id, o.fecha_otorgado, o.motivo,
               p.nombre || ' ' || p.apellido_paterno || ' ' || COALESCE(p.apellido_materno,'') AS nombre_alumno,
               e.matricula, g.nombre AS grupo,
               ce.nombre AS ciclo
          FROM ades_badge_otorgados o
          JOIN ades_estudiantes e  ON e.id = o.estudiante_id
          JOIN ades_personas     p ON p.id = e.persona_id
          JOIN ades_inscripciones ins ON ins.estudiante_id = e.id
          JOIN ades_grupos        g  ON g.id = ins.grupo_id
          JOIN ades_ciclos_escolares ce ON ce.id = ins.ciclo_escolar_id
         WHERE o.badge_id = :bid
           AND ins.ciclo_escolar_id = o.ciclo_id
         ORDER BY o.fecha_otorgado DESC
    """), {"bid": badge_id})
    return {**dict(badge._mapping), "alumnos": [dict(r._mapping) for r in alumnos.fetchall()]}


@router.delete("/{badge_id}")
async def eliminar_badge(
    badge_id: str,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    await db.execute(
        text("UPDATE ades_badges SET is_active = FALSE WHERE id = :id"),
        {"id": badge_id},
    )
    await db.commit()
    return {"ok": True}


# ── Por alumno ───────────────────────────────────────────────────────────────

@router.get("/alumno/{estudiante_id}")
async def badges_alumno(
    estudiante_id: str,
    ciclo_id: str | None = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    """Todos los badges activos, marcando cuáles obtuvo el alumno."""
    params: dict = {"estudiante_id": estudiante_id}
    ciclo_filter = "AND o.ciclo_id = :ciclo_id" if ciclo_id else ""
    if ciclo_id:
        params["ciclo_id"] = ciclo_id

    rows = await db.execute(text(f"""
        SELECT b.id, b.nombre, b.descripcion, b.icono, b.color, b.tipo,
               b.criterio_tipo, b.criterio_metrica, b.criterio_valor,
               o.id          AS otorgado_id,
               o.fecha_otorgado,
               o.motivo
          FROM ades_badges b
          LEFT JOIN ades_badge_otorgados o
                 ON o.badge_id = b.id
                AND o.estudiante_id = :estudiante_id
                {ciclo_filter}
         WHERE b.is_active = TRUE
         ORDER BY o.fecha_otorgado DESC NULLS LAST, b.tipo, b.nombre
    """), params)
    return [dict(r._mapping) for r in rows.fetchall()]


# ── Otorgar / revocar ────────────────────────────────────────────────────────

@router.post("/{badge_id}/otorgar")
async def otorgar_badge(
    badge_id: str,
    payload: dict,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    sub = current_user.get("sub", "")
    usuario_id = await _resolve_usuario_id(db, sub)

    row = await db.execute(text("""
        INSERT INTO ades_badge_otorgados (badge_id, estudiante_id, ciclo_id, motivo, otorgado_por)
        VALUES (:badge_id, :estudiante_id, :ciclo_id, :motivo, :otorgado_por)
        ON CONFLICT (badge_id, estudiante_id, ciclo_id) DO NOTHING
        RETURNING id, fecha_otorgado
    """), {
        "badge_id":      badge_id,
        "estudiante_id": payload.get("estudiante_id"),
        "ciclo_id":      payload.get("ciclo_id"),
        "motivo":        payload.get("motivo"),
        "otorgado_por":  usuario_id,
    })
    await db.commit()
    r = row.fetchone()
    if not r:
        return {"ok": True, "duplicado": True}
    return {"ok": True, **dict(r._mapping)}


@router.delete("/{badge_id}/otorgados/{estudiante_id}")
async def revocar_badge(
    badge_id: str,
    estudiante_id: str,
    ciclo_id: str | None = Query(None),
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    params: dict = {"badge_id": badge_id, "estudiante_id": estudiante_id}
    ciclo_filter = "AND ciclo_id = :ciclo_id" if ciclo_id else ""
    if ciclo_id:
        params["ciclo_id"] = ciclo_id

    await db.execute(text(f"""
        DELETE FROM ades_badge_otorgados
         WHERE badge_id = :badge_id AND estudiante_id = :estudiante_id {ciclo_filter}
    """), params)
    await db.commit()
    return {"ok": True}


# ── Auto-evaluación ──────────────────────────────────────────────────────────

@router.post("/auto-evaluar/{ciclo_id}")
async def auto_evaluar_badges(
    ciclo_id: str,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(get_current_user),
):
    """
    Evalúa y otorga automáticamente los badges con criterio_tipo='AUTOMATICO'
    para todos los alumnos inscritos en el ciclo dado.
    Métricas soportadas: pct_asistencia, promedio_general, sin_reportes_conducta.
    """
    badges_auto = await db.execute(text("""
        SELECT id, criterio_metrica, criterio_valor FROM ades_badges
         WHERE criterio_tipo = 'AUTOMATICO' AND is_active = TRUE
    """))
    badges = badges_auto.fetchall()

    total_otorgados = 0

    for badge in badges:
        badge_id = str(badge.id)
        metrica   = badge.criterio_metrica
        umbral    = float(badge.criterio_valor or 0)

        if metrica == "pct_asistencia":
            # Alumnos con pct asistencia >= umbral en el ciclo
            elegibles = await db.execute(text("""
                SELECT ins.estudiante_id,
                       ROUND(
                           100.0 * COUNT(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 END)
                           / NULLIF(COUNT(a.id), 0),
                       2) AS pct
                  FROM ades_inscripciones ins
                  JOIN ades_asistencias   a   ON a.estudiante_id = ins.estudiante_id
                  JOIN ades_clases        cl  ON cl.id = a.clase_id
                 WHERE ins.ciclo_escolar_id = :ciclo_id
                   AND cl.ciclo_escolar_id  = :ciclo_id
                 GROUP BY ins.estudiante_id
                HAVING ROUND(100.0 * COUNT(CASE WHEN a.estatus_asistencia='PRESENTE' THEN 1 END)
                             / NULLIF(COUNT(a.id),0), 2) >= :umbral
            """), {"ciclo_id": ciclo_id, "umbral": umbral})

        elif metrica == "promedio_general":
            elegibles = await db.execute(text("""
                SELECT ins.estudiante_id,
                       AVG(cp.calificacion) AS pct
                  FROM ades_inscripciones ins
                  JOIN ades_calificaciones_periodo cp ON cp.estudiante_id = ins.estudiante_id
                  JOIN ades_periodos_evaluacion pe    ON pe.id = cp.periodo_id
                 WHERE ins.ciclo_escolar_id = :ciclo_id
                   AND pe.ciclo_escolar_id  = :ciclo_id
                 GROUP BY ins.estudiante_id
                HAVING AVG(cp.calificacion) >= :umbral
            """), {"ciclo_id": ciclo_id, "umbral": umbral})

        elif metrica == "sin_reportes_conducta":
            # Alumnos inscritos sin ningún reporte de conducta negativo en el ciclo
            elegibles = await db.execute(text("""
                SELECT ins.estudiante_id, 0 AS pct
                  FROM ades_inscripciones ins
                 WHERE ins.ciclo_escolar_id = :ciclo_id
                   AND NOT EXISTS (
                       SELECT 1 FROM ades_reportes_conducta rc
                        WHERE rc.estudiante_id = ins.estudiante_id
                          AND rc.created_at >= (
                              SELECT fecha_inicio FROM ades_ciclos_escolares WHERE id = :ciclo_id
                          )
                   )
            """), {"ciclo_id": ciclo_id})
        else:
            continue

        for e in elegibles.fetchall():
            r = await db.execute(text("""
                INSERT INTO ades_badge_otorgados (badge_id, estudiante_id, ciclo_id, motivo)
                VALUES (:bid, :eid, :cid, 'Otorgado automáticamente por evaluación de criterios')
                ON CONFLICT (badge_id, estudiante_id, ciclo_id) DO NOTHING
                RETURNING id
            """), {"bid": badge_id, "eid": str(e.estudiante_id), "cid": ciclo_id})
            if r.fetchone():
                total_otorgados += 1

    await db.commit()
    return {"ok": True, "total_otorgados": total_otorgados, "badges_evaluados": len(badges)}

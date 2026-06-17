"""
/certificados — Certificados digitales con firma Ed25519 y QR verificable.
FASE 27 — Certificación Digital

  GET  /certificados                      — historial de certificados (autenticado)
  POST /certificados/emitir               — emite + firma + genera PDF con QR
  POST /certificados/{id}/firmar          — firma un certificado ya emitido (ADMIN)
  GET  /certificados/verificar/{folio}    — verificación pública (sin auth)
  POST /certificados/llave/generar        — genera nuevo par Ed25519 (ADMIN_GLOBAL)
  POST /certificados/llave/registrar      — registra la llave pública activa (ADMIN_GLOBAL)
  GET  /certificados/llave/activa         — información de la llave activa (ADMIN)
"""
from __future__ import annotations

import datetime
import json
import os
from pathlib import Path
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user, get_ades_user, AdesUser
from app.services.firma_digital import (
    calcular_hash,
    firmar,
    generar_nuevo_par_de_llaves,
    generar_qr_png_b64,
    generar_url_verificacion,
    llave_publica_b64,
    verificar_firma,
)

router = APIRouter(prefix="/certificados", tags=["certificados"])

_TEMPLATES_DIR = Path(__file__).parent.parent.parent / "templates" / "certificados"
_BASE_URL = os.getenv("BASE_URL", "https://ades.setag.mx")

TIPO_LABELS = {
    "ESTUDIOS":            "de Estudios",
    "CONDUCTA":            "de Buena Conducta",
    "PARTICIPACION":       "de Participación",
    "MERITO_ACADEMICO":    "de Mérito Académico",
    "ASISTENCIA_PERFECTA": "de Asistencia Perfecta",
}

MOTIVOS = {
    "ESTUDIOS":            "por haber cursado y acreditado satisfactoriamente el nivel educativo correspondiente.",
    "CONDUCTA":            "por demostrar a lo largo del ciclo escolar una conducta ejemplar y respetuosa.",
    "PARTICIPACION":       "por su destacada participación en actividades académicas y extracurriculares.",
    "MERITO_ACADEMICO":    "por haber obtenido un rendimiento académico sobresaliente durante el ciclo escolar.",
    "ASISTENCIA_PERFECTA": "por haber asistido de manera puntual y constante durante todo el ciclo escolar.",
}


# ── Schemas ──────────────────────────────────────────────────────────────────

class CertificadoCreate(BaseModel):
    estudiante_id:    UUID
    ciclo_escolar_id: UUID
    tipo_certificado: str = "ESTUDIOS"
    grado_completado: Optional[str] = None
    promedio_final:   Optional[float] = None
    fecha_vencimiento: Optional[datetime.date] = None
    datos_adicionales: Optional[dict] = None


class LlavePublicaCreate(BaseModel):
    nombre:      str
    descripcion: Optional[str] = None
    clave_publica_b64: str


# ── Helpers ───────────────────────────────────────────────────────────────────

async def _get_datos_alumno(db: AsyncSession, estudiante_id: str, ciclo_id: str) -> dict:
    row = await db.execute(text("""
        SELECT
            p.nombre || ' ' || p.apellido_paterno
                || COALESCE(' ' || p.apellido_materno, '') AS nombre_alumno,
            p.curp,
            pl.nombre_plantel,
            ne.nombre_nivel,
            ce.nombre_ciclo,
            ROUND(AVG(cp.calificacion), 2) AS promedio_calculado
        FROM ades_estudiantes est
        JOIN ades_personas p ON p.id = est.persona_id
        LEFT JOIN ades_inscripciones i
            ON i.estudiante_id = est.id AND i.ciclo_escolar_id = :ciclo_id::uuid AND i.is_active = TRUE
        LEFT JOIN ades_grupos g ON g.id = i.grupo_id
        LEFT JOIN ades_grados gr ON gr.id = g.grado_id
        LEFT JOIN ades_planteles pl ON pl.id = gr.plantel_id
        LEFT JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
        LEFT JOIN ades_ciclos_escolares ce ON ce.id = :ciclo_id::uuid
        LEFT JOIN ades_calificaciones_periodo cp
            ON cp.inscripcion_id = i.id AND cp.calificacion IS NOT NULL
        WHERE est.id = :est_id::uuid
        GROUP BY p.nombre, p.apellido_paterno, p.apellido_materno, p.curp,
                 pl.nombre_plantel, ne.nombre_nivel, ce.nombre_ciclo
    """), {"est_id": estudiante_id, "ciclo_id": ciclo_id})
    r = row.mappings().first()
    return dict(r) if r else {}


async def _get_llave_activa_id(db: AsyncSession) -> Optional[str]:
    """Devuelve el UUID de la llave activa, o None si no hay."""
    row = await db.execute(text(
        "SELECT id FROM ades_llaves_firma WHERE activa = TRUE AND is_active = TRUE LIMIT 1"
    ))
    r = row.scalar()
    return str(r) if r else None


async def _get_llave_activa_pub(db: AsyncSession) -> Optional[str]:
    """Devuelve la clave pública base64 de la llave activa."""
    row = await db.execute(text(
        "SELECT clave_publica_b64 FROM ades_llaves_firma WHERE activa = TRUE AND is_active = TRUE LIMIT 1"
    ))
    return row.scalar()


def _render_pdf(context: dict) -> bytes:
    from jinja2 import Environment, FileSystemLoader
    from weasyprint import HTML

    env = Environment(loader=FileSystemLoader(str(_TEMPLATES_DIR)))
    template = env.get_template("certificado.html")
    html_str = template.render(**context)
    return HTML(string=html_str, base_url=str(_TEMPLATES_DIR)).write_pdf()


async def _firmar_certificado_db(db: AsyncSession, cert_id: str, cert_dict: dict) -> dict:
    """Firma un certificado y persiste hash+firma+url en la BD. Retorna info."""
    llave_id = await _get_llave_activa_id(db)
    hash_hex = calcular_hash(cert_dict)
    firma_b64 = firmar(cert_dict)
    url_verif = generar_url_verificacion(cert_dict["folio"], _BASE_URL)

    estado = "FIRMADO" if firma_b64 else "PENDIENTE"
    await db.execute(text("""
        UPDATE ades_certificados
        SET hash_sha256       = :hash,
            firma_ed25519     = :firma,
            clave_publica_ref = :llave_id::uuid,
            verificable_url   = :url,
            estado_firma      = :estado,
            fecha_firma       = CASE WHEN :firma IS NOT NULL THEN NOW() ELSE NULL END,
            fecha_modificacion = NOW(),
            row_version        = row_version + 1
        WHERE id = :cert_id::uuid
    """), {
        "hash":     hash_hex,
        "firma":    firma_b64,
        "llave_id": llave_id,
        "url":      url_verif,
        "estado":   estado,
        "cert_id":  cert_id,
    })
    await db.commit()
    return {"hash_sha256": hash_hex, "firma_ed25519": firma_b64,
            "verificable_url": url_verif, "estado_firma": estado}


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.get("")
async def listar_certificados(
    estudiante_id:    Optional[UUID] = None,
    tipo_certificado: Optional[str]  = None,
    limit: int = 50,
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    filters = ["c.is_active = TRUE"]
    params: dict = {"limit": limit}

    if estudiante_id:
        filters.append("c.estudiante_id = :est_id::uuid")
        params["est_id"] = str(estudiante_id)
    if tipo_certificado:
        filters.append("c.tipo_certificado = :tipo")
        params["tipo"] = tipo_certificado

    where = " AND ".join(filters)
    rows = await db.execute(text(f"""
        SELECT
            c.id, c.folio, c.tipo_certificado, c.nivel_educativo,
            c.grado_completado, c.promedio_final,
            c.fecha_emision, c.fecha_vencimiento, c.vigente,
            c.estado_firma, c.fecha_firma, c.verificable_url,
            c.hash_sha256, c.firma_ed25519,
            c.blockchain_tx, c.blockchain_status, c.fecha_anclaje, c.blockchain_network,
            p.nombre || ' ' || p.apellido_paterno
                || COALESCE(' ' || p.apellido_materno, '') AS nombre_alumno,
            ce.nombre_ciclo
        FROM ades_certificados c
        JOIN ades_estudiantes  est ON est.id = c.estudiante_id
        JOIN ades_personas     p   ON p.id   = est.persona_id
        JOIN ades_ciclos_escolares ce ON ce.id = c.ciclo_escolar_id
        WHERE {where}
        ORDER BY c.fecha_emision DESC
        LIMIT :limit
    """), params)
    return rows.mappings().all()


@router.post("/emitir")
async def emitir_certificado(
    body: CertificadoCreate,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    """Emite, firma y genera PDF con QR de verificación."""
    datos = await _get_datos_alumno(db, str(body.estudiante_id), str(body.ciclo_escolar_id))
    if not datos.get("nombre_alumno"):
        raise HTTPException(status_code=404, detail="Alumno no encontrado")

    jwt_sub = current_user.get("sub", "")
    uid_row = await db.execute(
        text("SELECT id FROM ades_usuarios WHERE oidc_sub = :sub"),
        {"sub": jwt_sub}
    )
    uid = uid_row.scalar()

    promedio = body.promedio_final or datos.get("promedio_calculado")
    cert_row = await db.execute(text("""
        INSERT INTO ades_certificados
            (estudiante_id, ciclo_escolar_id, tipo_certificado,
             nivel_educativo, grado_completado, promedio_final,
             fecha_vencimiento, datos_adicionales, emitido_por_id)
        VALUES
            (:est_id::uuid, :ciclo_id::uuid, :tipo,
             :nivel, :grado, :promedio,
             :fecha_venc, :datos::jsonb, :emitido_por)
        RETURNING id, folio, fecha_emision
    """), {
        "est_id":      str(body.estudiante_id),
        "ciclo_id":    str(body.ciclo_escolar_id),
        "tipo":        body.tipo_certificado,
        "nivel":       datos.get("nombre_nivel", ""),
        "grado":       body.grado_completado,
        "promedio":    promedio,
        "fecha_venc":  body.fecha_vencimiento,
        "datos":       json.dumps(body.datos_adicionales) if body.datos_adicionales else None,
        "emitido_por": str(uid) if uid else None,
    })
    await db.commit()
    cert = dict(cert_row.mappings().first())

    # Firmar automáticamente si hay llave configurada
    cert_dict = {
        "folio":            cert["folio"],
        "tipo_certificado": body.tipo_certificado,
        "nivel_educativo":  datos.get("nombre_nivel", ""),
        "grado_completado": body.grado_completado,
        "promedio_final":   str(float(promedio)) if promedio is not None else "",
        "fecha_emision":    str(cert["fecha_emision"]),
        "estudiante_id":    str(body.estudiante_id),
        "ciclo_escolar_id": str(body.ciclo_escolar_id),
    }
    firma_info = await _firmar_certificado_db(db, str(cert["id"]), cert_dict)

    # QR de verificación
    url_verif = firma_info["verificable_url"]
    qr_b64 = generar_qr_png_b64(url_verif)

    fecha_str = cert["fecha_emision"].strftime("%d de %B de %Y") if cert.get("fecha_emision") else ""
    context = {
        "nombre_alumno":    datos["nombre_alumno"],
        "curp":             datos.get("curp", ""),
        "plantel":          datos.get("nombre_plantel", "Instituto Nevadi"),
        "nivel_educativo":  datos.get("nombre_nivel", body.tipo_certificado),
        "ciclo":            datos.get("nombre_ciclo", ""),
        "grado_completado": body.grado_completado or "",
        "promedio_final":   promedio,
        "tipo_label":       TIPO_LABELS.get(body.tipo_certificado, body.tipo_certificado),
        "motivo":           MOTIVOS.get(body.tipo_certificado, ""),
        "folio":            cert["folio"],
        "fecha_emision":    fecha_str,
        "url_verificacion": url_verif,
        "qr_b64":           qr_b64,
        "estado_firma":     firma_info["estado_firma"],
        "firmado":          firma_info["estado_firma"] == "FIRMADO",
    }

    # Lanzar tarea de anclaje blockchain de fondo
    if firma_info["estado_firma"] == "FIRMADO":
        try:
            from app.worker.tasks.blockchain import anclar_certificado_task
            anclar_certificado_task.delay(str(cert["id"]))
        except Exception:
            pass

    pdf_bytes = _render_pdf(context)
    return StreamingResponse(
        iter([pdf_bytes]),
        media_type="application/pdf",
        headers={
            "Content-Disposition": f'attachment; filename="certificado_{cert["folio"]}.pdf"',
            "X-Folio":            cert["folio"],
            "X-Certificado-Id":   str(cert["id"]),
            "X-Estado-Firma":     firma_info["estado_firma"],
        },
    )


@router.post("/{cert_id}/firmar")
async def firmar_certificado(
    cert_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    """Firma un certificado ya emitido. Solo ADMIN_GLOBAL o ADMIN_PLANTEL."""
    nivel = current_user.get("nivel_acceso", 99)
    if nivel > 1:
        raise HTTPException(status_code=403, detail="Solo administradores pueden firmar certificados")

    row = await db.execute(text("""
        SELECT id, folio, tipo_certificado, nivel_educativo, grado_completado,
               promedio_final, fecha_emision, estudiante_id, ciclo_escolar_id,
               estado_firma
        FROM ades_certificados
        WHERE id = :id::uuid AND is_active = TRUE
    """), {"id": str(cert_id)})
    cert = row.mappings().first()
    if not cert:
        raise HTTPException(status_code=404, detail="Certificado no encontrado")
    if cert["estado_firma"] == "FIRMADO":
        raise HTTPException(status_code=409, detail="El certificado ya está firmado")

    cert_dict = {
        "folio":            cert["folio"],
        "tipo_certificado": cert["tipo_certificado"],
        "nivel_educativo":  cert["nivel_educativo"],
        "grado_completado": cert["grado_completado"],
        "promedio_final":   str(float(cert["promedio_final"])) if cert["promedio_final"] is not None else "",
        "fecha_emision":    str(cert["fecha_emision"]),
        "estudiante_id":    str(cert["estudiante_id"]),
        "ciclo_escolar_id": str(cert["ciclo_escolar_id"]),
    }
    resultado = await _firmar_certificado_db(db, str(cert_id), cert_dict)
    if resultado["estado_firma"] == "FIRMADO":
        try:
            from app.worker.tasks.blockchain import anclar_certificado_task
            anclar_certificado_task.delay(str(cert_id))
        except Exception:
            pass
    return {"folio": cert["folio"], **resultado}


@router.get("/verificar/{folio}")
async def verificar_certificado_publico(
    folio: str,
    db: AsyncSession = Depends(get_db),
):
    """
    Endpoint público — verifica autenticidad por folio.
    No requiere autenticación. Valida firma Ed25519 si está disponible.
    """
    # Sanitizar folio: solo alfanuméricos, guiones y guiones bajos
    import re
    folio_clean = re.sub(r"[^A-Za-z0-9\-_]", "", folio)
    if not folio_clean:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Folio inválido.",
        )
    folio = folio_clean

    row = await db.execute(text("""
        SELECT
            c.id, c.folio, c.tipo_certificado, c.nivel_educativo,
            c.grado_completado, c.promedio_final,
            c.fecha_emision, c.fecha_vencimiento, c.vigente,
            c.estado_firma, c.fecha_firma,
            c.hash_sha256, c.firma_ed25519,
            c.estudiante_id, c.ciclo_escolar_id,
            c.verificable_url,
            c.blockchain_tx, c.blockchain_status, c.fecha_anclaje, c.blockchain_network,
            p.nombre || ' ' || p.apellido_paterno
                || COALESCE(' ' || p.apellido_materno, '') AS nombre_alumno,
            p.curp AS alumno_curp,
            ce.nombre_ciclo,
            pl.nombre_plantel,
            lf.clave_publica_b64
        FROM ades_certificados c
        JOIN ades_estudiantes  est ON est.id = c.estudiante_id
        JOIN ades_personas     p   ON p.id   = est.persona_id
        JOIN ades_ciclos_escolares ce ON ce.id = c.ciclo_escolar_id
        LEFT JOIN ades_inscripciones i
            ON i.estudiante_id = c.estudiante_id
           AND i.ciclo_escolar_id = c.ciclo_escolar_id
           AND i.is_active = TRUE
        LEFT JOIN ades_grupos g    ON g.id = i.grupo_id
        LEFT JOIN ades_grados gr   ON gr.id = g.grado_id
        LEFT JOIN ades_planteles pl ON pl.id = gr.plantel_id
        LEFT JOIN ades_llaves_firma lf ON lf.id = c.clave_publica_ref
        WHERE c.folio = :folio AND c.is_active = TRUE
    """), {"folio": folio.upper()})

    cert = row.mappings().first()
    if not cert:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Certificado con folio '{folio}' no encontrado.",
        )

    # Verificar firma criptográfica si está presente
    firma_valida = None
    if cert["firma_ed25519"] and cert["clave_publica_b64"]:
        cert_dict = {
            "folio":            cert["folio"],
            "tipo_certificado": cert["tipo_certificado"],
            "nivel_educativo":  cert["nivel_educativo"],
            "grado_completado": cert["grado_completado"],
            "promedio_final":   str(float(cert["promedio_final"])) if cert["promedio_final"] is not None else "",
            "fecha_emision":    str(cert["fecha_emision"]),
            "estudiante_id":    str(cert["estudiante_id"]),
            "ciclo_escolar_id": str(cert["ciclo_escolar_id"]),
        }
        firma_valida = verificar_firma(
            cert_dict, cert["firma_ed25519"], cert["clave_publica_b64"]
        )

    if cert["estado_firma"] == "REVOCADO":
        autenticidad = "REVOCADO"
        mensaje = "Este certificado ha sido revocado y no es válido."
    elif firma_valida is True:
        autenticidad = "VERIFICADO"
        mensaje = "Certificado auténtico. La firma Ed25519 es válida."
    elif firma_valida is False:
        autenticidad = "INVALIDO"
        mensaje = "La firma del certificado no es válida. Puede haber sido alterado."
    else:
        autenticidad = "EMITIDO"
        mensaje = "Certificado emitido por Instituto Nevadi (sin firma criptográfica)."

    return {
        "folio":           cert["folio"],
        "tipo_certificado": cert["tipo_certificado"],
        "nombre_alumno":   cert["nombre_alumno"],
        "alumno_curp":     cert["alumno_curp"],
        "plantel":         cert["nombre_plantel"],
        "ciclo":           cert["nombre_ciclo"],
        "nivel_educativo": cert["nivel_educativo"],
        "grado_completado": cert["grado_completado"],
        "promedio_final":  cert["promedio_final"],
        "fecha_emision":   cert["fecha_emision"],
        "vigente":         cert["vigente"],
        "estado_firma":    cert["estado_firma"],
        "fecha_firma":     cert["fecha_firma"],
        "autenticidad":    autenticidad,
        "mensaje":         mensaje,
        "firma_valida":    firma_valida,
        "blockchain_tx":      cert["blockchain_tx"],
        "blockchain_status":  cert["blockchain_status"],
        "fecha_anclaje":      cert["fecha_anclaje"].isoformat() if cert["fecha_anclaje"] else None,
        "blockchain_network": cert["blockchain_network"],
    }


# ── Gestión de llaves (ADMIN_GLOBAL) ─────────────────────────────────────────

@router.post("/llave/generar")
async def generar_llave(
    current_user: dict = Depends(get_current_user),
):
    """Genera un nuevo par Ed25519. La llave privada se muestra UNA SOLA VEZ."""
    if current_user.get("nivel_acceso", 99) > 0:
        raise HTTPException(status_code=403, detail="Solo ADMIN_GLOBAL")
    par = generar_nuevo_par_de_llaves()
    return {
        "aviso": "Copia FIRMA_CLAVE_PRIVADA_HEX al .env AHORA. No se almacena en BD.",
        "privada_hex":  par["privada_hex"],
        "publica_b64":  par["publica_b64"],
        "instruccion":  "Agrega al .env: FIRMA_CLAVE_PRIVADA_HEX=<privada_hex>",
    }


@router.post("/llave/registrar")
async def registrar_llave(
    body: LlavePublicaCreate,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    """Registra la llave pública activa en BD. Desactiva la anterior."""
    if current_user.get("nivel_acceso", 99) > 0:
        raise HTTPException(status_code=403, detail="Solo ADMIN_GLOBAL")

    jwt_sub = current_user.get("sub", "")
    uid_row = await db.execute(
        text("SELECT id FROM ades_usuarios WHERE oidc_sub = :sub"), {"sub": jwt_sub}
    )
    uid = uid_row.scalar()

    # Desactivar llaves anteriores
    await db.execute(text(
        "UPDATE ades_llaves_firma SET activa = FALSE, fecha_modificacion = NOW() WHERE activa = TRUE"
    ))

    # Insertar nueva
    new_row = await db.execute(text("""
        INSERT INTO ades_llaves_firma (nombre, descripcion, algoritmo, clave_publica_b64, creada_por_id)
        VALUES (:nombre, :desc, 'Ed25519', :pub, :uid::uuid)
        RETURNING id, nombre, clave_publica_b64, fecha_activacion
    """), {
        "nombre": body.nombre,
        "desc":   body.descripcion,
        "pub":    body.clave_publica_b64,
        "uid":    str(uid) if uid else None,
    })
    await db.commit()
    return dict(new_row.mappings().first())


@router.get("/llave/activa")
async def obtener_llave_activa(
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """Información de la llave de firma activa (Director o superior)."""
    if ades_user.nivel_acceso > 2:
        raise HTTPException(status_code=403, detail="Acceso restringido a Director o superior")
    row = await db.execute(text("""
        SELECT id, nombre, descripcion, algoritmo, clave_publica_b64,
               fecha_activacion, fecha_expiracion, activa
        FROM ades_llaves_firma
        WHERE activa = TRUE AND is_active = TRUE
        LIMIT 1
    """))
    llave = row.mappings().first()
    configurada_en_env = llave_publica_b64() is not None
    if not llave:
        return {"activa": False, "configurada_en_env": configurada_en_env}
    return {**dict(llave), "configurada_en_env": configurada_en_env}

"""
Importación masiva desde CSV/Excel.

Endpoints:
  GET  /imports/plantillas/{entidad}   — descarga CSV de ejemplo
  POST /imports/alumnos
  POST /imports/profesores
  POST /imports/materias
  POST /imports/grupos

Columnas esperadas (tolerante a variantes con/sin acentos, espacios→guión_bajo):

  alumnos:
    nombre | apellido_paterno | apellido_materno | curp | genero | fecha_nacimiento |
    fecha_ingreso | clave_plantel

  profesores:
    nombre | apellido_paterno | apellido_materno | curp | genero | fecha_nacimiento |
    numero_empleado | tipo_contrato | clave_plantel

  materias:
    nombre_materia | clave_materia | nombre_nivel | horas_semana

  grupos:
    nombre_grupo | turno | capacidad_maxima | nombre_grado | nombre_ciclo
"""
from __future__ import annotations
import csv
import io
import uuid
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from app.core.database import get_db
from app.core.security import get_current_user
from app.models.personas import Persona, Estudiante, Profesor, Estatus
from app.models.academica import (
    Plantel, NivelEducativo, Grado, CicloEscolar, Grupo,
)
from app.models.materias import Materia
from app.utils.importador import parse_file, get_col, parse_date, parse_float, parse_int

router = APIRouter(prefix="/imports", tags=["importación"])


# ── Modelos de respuesta ───────────────────────────────────────────────────────

class ErrorFila(BaseModel):
    fila: int
    dato: str
    error: str


class ImportResult(BaseModel):
    entidad: str
    total: int
    exitosos: int
    errores: int
    detalle_errores: list[ErrorFila]


# ── Plantillas CSV ─────────────────────────────────────────────────────────────

_PLANTILLAS: dict[str, tuple[list[str], list[list[str]]]] = {
    "alumnos": (
        ["nombre", "apellido_paterno", "apellido_materno", "curp",
         "genero", "fecha_nacimiento", "fecha_ingreso", "clave_plantel"],
        [["Juan", "García", "López", "GALJ900101HMCRPN01",
          "M", "01/01/1990", "15/08/2024", "NV-PRI-001"]],
    ),
    "profesores": (
        ["nombre", "apellido_paterno", "apellido_materno", "curp",
         "genero", "fecha_nacimiento", "numero_empleado", "tipo_contrato", "clave_plantel"],
        [["María", "Pérez", "Sánchez", "PESM850315MMCRNR02",
          "F", "15/03/1985", "EMP-001", "BASE", "NV-PRI-001"]],
    ),
    "materias": (
        ["nombre_materia", "clave_materia", "nombre_nivel", "horas_semana"],
        [["Matemáticas", "MAT-01", "Primaria", "5"],
         ["Español",     "ESP-01", "Primaria", "5"]],
    ),
    "grupos": (
        ["nombre_grupo", "turno", "capacidad_maxima", "nombre_grado", "nombre_ciclo"],
        [["1A", "MATUTINO", "35", "Primer Grado", "2024-2025"],
         ["2B", "VESPERTINO", "30", "Segundo Grado", "2024-2025"]],
    ),
}


@router.get("/plantillas/{entidad}")
async def descargar_plantilla(
    entidad: str,
    _user: dict = Depends(get_current_user),
):
    if entidad not in _PLANTILLAS:
        raise HTTPException(
            status_code=404,
            detail=f"Plantilla no disponible. Opciones: {', '.join(_PLANTILLAS)}",
        )
    headers, ejemplos = _PLANTILLAS[entidad]
    buf = io.StringIO()
    writer = csv.writer(buf)
    writer.writerow(headers)
    for fila in ejemplos:
        writer.writerow(fila)
    content = buf.getvalue().encode("utf-8-sig")
    return StreamingResponse(
        iter([content]),
        media_type="text/csv; charset=utf-8",
        headers={"Content-Disposition": f"attachment; filename=plantilla_{entidad}.csv"},
    )


# ── Helpers internos ───────────────────────────────────────────────────────────

async def _resolver_plantel(
    clave: str,
    planteles_por_clave: dict[str, uuid.UUID],
    planteles_por_nombre: dict[str, uuid.UUID],
) -> uuid.UUID | None:
    """Resuelve plantel por clave_ct, nombre o UUID directo."""
    if not clave:
        return None
    pid = planteles_por_clave.get(clave) or planteles_por_nombre.get(clave.lower())
    if pid:
        return pid
    try:
        return uuid.UUID(clave)
    except (ValueError, AttributeError):
        return None


# ── POST /imports/alumnos ──────────────────────────────────────────────────────

@router.post("/alumnos", response_model=ImportResult)
async def importar_alumnos(
    file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    content = await file.read()
    headers, rows = parse_file(content, file.filename or "upload.csv")
    if not rows:
        raise HTTPException(status_code=400, detail="El archivo no contiene datos")

    # Cachés
    p_res = await db.execute(select(Plantel))
    planteles_clave  = {p.clave_ct: p.id for p in p_res.scalars().all() if p.clave_ct}
    p_res2 = await db.execute(select(Plantel))
    planteles_nombre = {p.nombre_plantel.lower(): p.id for p in p_res2.scalars().all()}

    est_res = await db.execute(
        select(Estatus).where(Estatus.entidad == "ESTUDIANTE", Estatus.nombre_estatus == "INSCRITO")
    )
    estatus_obj = est_res.scalar_one_or_none()
    estatus_id  = estatus_obj.id if estatus_obj else None

    cnt = await db.execute(select(func.count()).select_from(Estudiante))
    seq = cnt.scalar() or 0

    errores: list[ErrorFila] = []
    exitosos = 0

    for i, row in enumerate(rows, start=2):
        curp   = get_col(row, headers, "curp").upper()
        nombre = get_col(row, headers, "nombre")
        if not curp or not nombre:
            errores.append(ErrorFila(fila=i, dato=f"fila {i}", error="'nombre' y 'curp' son obligatorios"))
            continue

        dup = await db.execute(select(Persona.id).where(Persona.curp == curp))
        if dup.scalar_one_or_none():
            errores.append(ErrorFila(fila=i, dato=curp, error="CURP ya registrada"))
            continue

        clave_plantel = get_col(row, headers, "clave_plantel", "plantel", "clave_ct")
        plantel_id = await _resolver_plantel(clave_plantel, planteles_clave, planteles_nombre)
        if not plantel_id:
            errores.append(ErrorFila(fila=i, dato=curp, error=f"Plantel no encontrado: '{clave_plantel}'"))
            continue

        sp = await db.begin_nested()
        try:
            persona = Persona(
                nombre=nombre,
                apellido_paterno=get_col(row, headers, "apellido_paterno"),
                apellido_materno=get_col(row, headers, "apellido_materno") or None,
                curp=curp,
                genero=get_col(row, headers, "genero") or None,
                fecha_nacimiento=parse_date(get_col(row, headers, "fecha_nacimiento")),
            )
            db.add(persona)
            await db.flush()

            seq += 1
            estudiante = Estudiante(
                matricula=f"MAT-{seq:06d}",
                persona_id=persona.id,
                plantel_id=plantel_id,
                fecha_ingreso=parse_date(get_col(row, headers, "fecha_ingreso")),
                estatus_id=estatus_id,
            )
            db.add(estudiante)
            await db.flush()
            exitosos += 1
        except Exception as exc:
            await sp.rollback()
            errores.append(ErrorFila(fila=i, dato=curp, error=str(exc)[:150]))
            continue

    await db.commit()
    return ImportResult(entidad="alumnos", total=len(rows),
                        exitosos=exitosos, errores=len(errores),
                        detalle_errores=errores)


# ── POST /imports/profesores ───────────────────────────────────────────────────

@router.post("/profesores", response_model=ImportResult)
async def importar_profesores(
    file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    content = await file.read()
    headers, rows = parse_file(content, file.filename or "upload.csv")
    if not rows:
        raise HTTPException(status_code=400, detail="El archivo no contiene datos")

    p_res = await db.execute(select(Plantel))
    planteles_clave  = {p.clave_ct: p.id for p in p_res.scalars().all() if p.clave_ct}
    p_res2 = await db.execute(select(Plantel))
    planteles_nombre = {p.nombre_plantel.lower(): p.id for p in p_res2.scalars().all()}

    est_res = await db.execute(
        select(Estatus).where(Estatus.entidad == "PROFESOR", Estatus.nombre_estatus == "ACTIVO")
    )
    estatus_obj = est_res.scalar_one_or_none()
    estatus_id  = estatus_obj.id if estatus_obj else None

    errores: list[ErrorFila] = []
    exitosos = 0

    for i, row in enumerate(rows, start=2):
        curp   = get_col(row, headers, "curp").upper()
        nombre = get_col(row, headers, "nombre")
        num_emp = get_col(row, headers, "numero_empleado", "num_empleado", "empleado")
        if not curp or not nombre or not num_emp:
            errores.append(ErrorFila(fila=i, dato=f"fila {i}",
                                     error="'nombre', 'curp' y 'numero_empleado' son obligatorios"))
            continue

        dup = await db.execute(select(Persona.id).where(Persona.curp == curp))
        if dup.scalar_one_or_none():
            errores.append(ErrorFila(fila=i, dato=curp, error="CURP ya registrada"))
            continue

        clave_plantel = get_col(row, headers, "clave_plantel", "plantel", "clave_ct")
        plantel_id = await _resolver_plantel(clave_plantel, planteles_clave, planteles_nombre)
        if not plantel_id:
            errores.append(ErrorFila(fila=i, dato=curp, error=f"Plantel no encontrado: '{clave_plantel}'"))
            continue

        tipo_contrato = get_col(row, headers, "tipo_contrato", "contrato") or "BASE"

        sp = await db.begin_nested()
        try:
            persona = Persona(
                nombre=nombre,
                apellido_paterno=get_col(row, headers, "apellido_paterno"),
                apellido_materno=get_col(row, headers, "apellido_materno") or None,
                curp=curp,
                genero=get_col(row, headers, "genero") or None,
                fecha_nacimiento=parse_date(get_col(row, headers, "fecha_nacimiento")),
            )
            db.add(persona)
            await db.flush()

            profesor = Profesor(
                persona_id=persona.id,
                plantel_id=plantel_id,
                numero_empleado=num_emp,
                tipo_contrato=tipo_contrato.upper(),
                estatus_id=estatus_id,
            )
            db.add(profesor)
            await db.flush()
            exitosos += 1
        except Exception as exc:
            await sp.rollback()
            errores.append(ErrorFila(fila=i, dato=curp, error=str(exc)[:150]))
            continue

    await db.commit()
    return ImportResult(entidad="profesores", total=len(rows),
                        exitosos=exitosos, errores=len(errores),
                        detalle_errores=errores)


# ── POST /imports/materias ─────────────────────────────────────────────────────

@router.post("/materias", response_model=ImportResult)
async def importar_materias(
    file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    content = await file.read()
    headers, rows = parse_file(content, file.filename or "upload.csv")
    if not rows:
        raise HTTPException(status_code=400, detail="El archivo no contiene datos")

    niv_res = await db.execute(select(NivelEducativo))
    niveles = {n.nombre_nivel.lower(): n.id for n in niv_res.scalars().all()}

    errores: list[ErrorFila] = []
    exitosos = 0

    for i, row in enumerate(rows, start=2):
        nombre_mat = get_col(row, headers, "nombre_materia", "materia", "nombre")
        if not nombre_mat:
            errores.append(ErrorFila(fila=i, dato=f"fila {i}", error="'nombre_materia' es obligatorio"))
            continue

        nombre_nivel = get_col(row, headers, "nombre_nivel", "nivel")
        nivel_id = niveles.get(nombre_nivel.lower())
        if not nivel_id:
            try:
                nivel_id = uuid.UUID(nombre_nivel)
            except (ValueError, AttributeError):
                errores.append(ErrorFila(fila=i, dato=nombre_mat,
                                         error=f"Nivel educativo no encontrado: '{nombre_nivel}'"))
                continue

        sp = await db.begin_nested()
        try:
            materia = Materia(
                nombre_materia=nombre_mat,
                clave_materia=get_col(row, headers, "clave_materia", "clave") or None,
                nivel_educativo_id=nivel_id,
                horas_semana=parse_float(get_col(row, headers, "horas_semana", "horas")),
            )
            db.add(materia)
            await db.flush()
            exitosos += 1
        except Exception as exc:
            await sp.rollback()
            errores.append(ErrorFila(fila=i, dato=nombre_mat, error=str(exc)[:150]))
            continue

    await db.commit()
    return ImportResult(entidad="materias", total=len(rows),
                        exitosos=exitosos, errores=len(errores),
                        detalle_errores=errores)


# ── POST /imports/grupos ───────────────────────────────────────────────────────

@router.post("/grupos", response_model=ImportResult)
async def importar_grupos(
    file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
    _user: dict = Depends(get_current_user),
):
    content = await file.read()
    headers, rows = parse_file(content, file.filename or "upload.csv")
    if not rows:
        raise HTTPException(status_code=400, detail="El archivo no contiene datos")

    grado_res = await db.execute(select(Grado))
    grados_nombre = {g.nombre_grado.lower(): g.id for g in grado_res.scalars().all()}

    ciclo_res = await db.execute(select(CicloEscolar))
    ciclos_nombre = {c.nombre_ciclo.lower(): c.id for c in ciclo_res.scalars().all()}

    errores: list[ErrorFila] = []
    exitosos = 0

    for i, row in enumerate(rows, start=2):
        nombre_grupo = get_col(row, headers, "nombre_grupo", "grupo")
        if not nombre_grupo:
            errores.append(ErrorFila(fila=i, dato=f"fila {i}", error="'nombre_grupo' es obligatorio"))
            continue

        nombre_grado = get_col(row, headers, "nombre_grado", "grado")
        grado_id = grados_nombre.get(nombre_grado.lower())
        if not grado_id:
            try:
                grado_id = uuid.UUID(nombre_grado)
            except (ValueError, AttributeError):
                errores.append(ErrorFila(fila=i, dato=nombre_grupo,
                                         error=f"Grado no encontrado: '{nombre_grado}'"))
                continue

        nombre_ciclo = get_col(row, headers, "nombre_ciclo", "ciclo", "ciclo_escolar")
        ciclo_id = ciclos_nombre.get(nombre_ciclo.lower())
        if not ciclo_id:
            try:
                ciclo_id = uuid.UUID(nombre_ciclo)
            except (ValueError, AttributeError):
                errores.append(ErrorFila(fila=i, dato=nombre_grupo,
                                         error=f"Ciclo escolar no encontrado: '{nombre_ciclo}'"))
                continue

        turno = (get_col(row, headers, "turno") or "MATUTINO").upper()
        capacidad = parse_int(get_col(row, headers, "capacidad_maxima", "capacidad")) or 35

        sp = await db.begin_nested()
        try:
            grupo = Grupo(
                nombre_grupo=nombre_grupo,
                grado_id=grado_id,
                ciclo_escolar_id=ciclo_id,
                turno=turno,
                capacidad_maxima=capacidad,
            )
            db.add(grupo)
            await db.flush()
            exitosos += 1
        except Exception as exc:
            await sp.rollback()
            errores.append(ErrorFila(fila=i, dato=nombre_grupo, error=str(exc)[:150]))
            continue

    await db.commit()
    return ImportResult(entidad="grupos", total=len(rows),
                        exitosos=exitosos, errores=len(errores),
                        detalle_errores=errores)

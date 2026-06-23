"""
Seed 008 — Flujo completo: gradebook end-to-end + módulos faltantes.

Completa la cobertura del sistema ADES sobre los datos de 006/007:

  avance_planificacion · calificaciones_tareas (rúbricas) · expedientes_alumno
  rol_privilegios · alertas_academicas · alertas_cumplimiento
  coordinaciones_area · reportes_academicos · h5p_asignaciones + h5p_resultados
  bbb_reuniones + bbb_asistencia + bbb_grabaciones · asignaciones_aula
  archivos (file refs para certificados/constancias/expedientes)

Idempotente: pre-vacía solo las tablas que puebla.
Corre desde el host sin contraseñas (socket local de confianza).

Ejecutar:  python3 db/seeds/008_flujo_completo.py
"""
import os, random, uuid, subprocess, tempfile, sys
from datetime import date, datetime, timedelta

SEED_TAG = "seed008"
rng = random.Random(8)
PSQL = ["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "ades_admin", "-d", "ades"]


def q(sql):
    r = subprocess.run(
        PSQL + ["-tAF", "\t", "-v", "ON_ERROR_STOP=1", "-c", sql],
        capture_output=True, text=True, cwd="/opt/ades"
    )
    if r.returncode != 0:
        sys.exit("READ ERROR:\n" + r.stderr[-3000:])
    return [tuple(c if c != "" else None for c in ln.split("\t"))
            for ln in r.stdout.splitlines() if ln != ""]


def exec_sql_text(s):
    r = subprocess.run(
        PSQL + ["-v", "ON_ERROR_STOP=1"],
        input=s, capture_output=True, text=True, cwd="/opt/ades"
    )
    if r.returncode != 0:
        sys.exit("EXEC ERROR:\n" + r.stderr[-4000:])


def exec_sql_file(p):
    with open(p) as fh:
        r = subprocess.run(
            PSQL + ["-v", "ON_ERROR_STOP=1", "-q"],
            stdin=fh, capture_output=True, text=True, cwd="/opt/ades"
        )
    if r.returncode != 0:
        sys.exit("EXEC FILE ERROR:\n" + r.stderr[-5000:])


def uid():
    return str(uuid.uuid4())


def lit(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, (int, float)):
        return repr(v)
    if isinstance(v, (date, datetime)):
        return "'" + v.isoformat() + "'"
    return "'" + str(v).replace("'", "''") + "'"


# ── Pre-limpieza idempotente ────────────────────────────────────────────────
TARGET = [
    "ades_bbb_grabaciones", "ades_bbb_asistencia", "ades_bbb_reuniones",
    "ades_h5p_resultados", "ades_h5p_asignaciones",
    "ades_asignaciones_aula", "ades_avance_planificacion",
    "ades_calificaciones_tareas", "ades_expedientes_alumno",
    "ades_rol_privilegios", "ades_alertas_academicas", "ades_alertas_cumplimiento",
    "ades_coordinaciones_area", "ades_reportes_academicos", "ades_archivos",
]
print("── Pre-limpieza idempotente ─────────────────────────")
exec_sql_text(
    "SET session_replication_role=replica;\n" +
    "".join(f"DELETE FROM {t};\n" for t in TARGET) +
    "SET session_replication_role=DEFAULT;\n"
)

# ── Contexto ────────────────────────────────────────────────────────────────
print("── Contexto ─────────────────────────────────────────")
ADMIN_USER = (q("SELECT id FROM ades_usuarios WHERE email_institucional='admin@institutonevadi.edu.mx' LIMIT 1") or [[None]])[0][0]
CICLOS_SEP = q("SELECT id, fecha_inicio, fecha_fin FROM ades_ciclos_escolares WHERE es_vigente=TRUE AND sistema_educativo='SEP' LIMIT 1")
CICLO_SEP_ID = CICLOS_SEP[0][0] if CICLOS_SEP else None
CICLO_SEP_INICIO = CICLOS_SEP[0][1] if CICLOS_SEP else date(2026, 8, 24)
CICLO_SEP_FIN = CICLOS_SEP[0][2] if CICLOS_SEP else date(2027, 7, 9)
CICLOS_UAEMEX = q("SELECT id FROM ades_ciclos_escolares WHERE es_vigente=TRUE AND sistema_educativo='UAEMEX' LIMIT 1")
CICLO_UAM_ID = CICLOS_UAEMEX[0][0] if CICLOS_UAEMEX else None

PLANTELES = [r[0] for r in q("SELECT id FROM ades_planteles")]
AULAS = [r[0] for r in q("SELECT id FROM ades_aulas WHERE is_active=TRUE")]
DOCENTES = q("SELECT pr.id, pr.persona_id, u.id FROM ades_profesores pr JOIN ades_usuarios u ON u.persona_id=pr.persona_id LIMIT 72")
ESTUDIANTES = q("SELECT id, plantel_id FROM ades_estudiantes WHERE is_active=TRUE LIMIT 2000")
GRUPOS = q("""SELECT g.id, gr.plantel_id, gr.nivel_educativo_id
FROM ades_grupos g
JOIN ades_grados gr ON gr.id = g.grado_id
WHERE g.is_active=TRUE LIMIT 100""")
AREAS = [r[0] for r in q("SELECT id FROM ades_areas_academicas")]
ROLES = q("SELECT id, nombre_rol, nivel_acceso FROM ades_roles ORDER BY nivel_acceso")
PRIVILEGIOS = [r[0] for r in q("SELECT id FROM ades_privilegios")]
PERIODOS = q("SELECT id, nombre_periodo, ciclo_escolar_id FROM ades_periodos_evaluacion ORDER BY nombre_periodo")
RUBRICAS = q("SELECT id, nivel_educativo_id FROM ades_rubricas")
H5P_CONTENIDOS = [r[0] for r in q("SELECT id FROM ades_h5p_contenidos")]

print(f"   {len(ESTUDIANTES)} estudiantes · {len(GRUPOS)} grupos · {len(DOCENTES)} docentes")
print(f"   ciclo SEP: {CICLO_SEP_ID} · ciclo UAEMEX: {CICLO_UAM_ID}")

# Helper: ejecutar SQL en lotes separados (evita WAL overflow)
def exec_block(label, sql):
    full = "BEGIN;\nSET session_replication_role=replica;\n" + sql + "\nSET session_replication_role=DEFAULT;\nCOMMIT;\n"
    r = subprocess.run(PSQL + ["-v", "ON_ERROR_STOP=1", "-q"], input=full,
                       capture_output=True, text=True, cwd="/opt/ades")
    if r.returncode != 0:
        sys.exit(f"EXEC ERROR [{label}]:\n" + r.stderr[-3000:])
    print(f"   OK: {label}")

# Abrir archivo SQL principal (para inserts rápidos por Python)
TMP = tempfile.gettempdir() + "/seed008.sql"
F = open(TMP, "w")
F.write("BEGIN;\nSET session_replication_role=replica;\n")


def binsert(table, cols, rows, page=500):
    if not rows:
        return
    head = f"INSERT INTO {table} ({','.join(cols)}) VALUES\n"
    for i in range(0, len(rows), page):
        F.write(head + ",\n".join(
            "(" + ",".join(lit(x) for x in r) + ")" for r in rows[i:i + page]
        ) + "\n ON CONFLICT DO NOTHING;\n")


# ── 1. Vincular tareas a rúbricas (UPDATE server-side) ──────────────────────
print("── 1. Vincular tareas a rúbricas ────────────────────")
exec_block("tareas→rubrica", """
UPDATE ades_tareas t SET rubrica_id = (
  SELECT r.id FROM ades_rubricas r
  JOIN ades_materias_plan mp ON mp.materia_id = t.materia_id
  JOIN ades_grados gr ON gr.id = mp.grado_id
  WHERE r.nivel_educativo_id = gr.nivel_educativo_id
    AND r.is_active = TRUE
  ORDER BY r.id
  LIMIT 1
)
WHERE t.rubrica_id IS NULL AND t.materia_id IS NOT NULL;
""")

# ── 2. calificaciones_tareas ─────────────────────────────────────────────────
print("── 2. Calificaciones tareas (per entrega) ───────────")
# Batch: 10k rows per block to avoid WAL overflow (130k total)
exec_block("calificaciones_tareas_1", f"""
INSERT INTO ades_calificaciones_tareas
  (id, tarea_entrega_id, rubrica_id, calificacion, comentarios_docente,
   fecha_calificacion, usuario_creacion, usuario_modificacion)
SELECT gen_random_uuid(), te.id, t.rubrica_id, te.calificacion_obtenida,
  CASE WHEN te.calificacion_obtenida>=9 THEN 'Excelente trabajo'
       WHEN te.calificacion_obtenida>=7 THEN 'Buen desempeño'
       WHEN te.calificacion_obtenida>=6 THEN 'Aprobado, puede mejorar'
       ELSE 'Requiere refuerzo' END,
  COALESCE(te.fecha_calificacion_docente, te.fecha_modificacion, NOW()),
  '{SEED_TAG}', '{SEED_TAG}'
FROM ades_tareas_entregas te JOIN ades_tareas t ON t.id=te.tarea_id
WHERE te.calificacion_obtenida IS NOT NULL AND te.estatus_entrega='CALIFICADA'
ORDER BY te.id LIMIT 40000
ON CONFLICT (tarea_entrega_id) DO NOTHING;
""")
exec_block("calificaciones_tareas_2", f"""
INSERT INTO ades_calificaciones_tareas
  (id, tarea_entrega_id, rubrica_id, calificacion, comentarios_docente,
   fecha_calificacion, usuario_creacion, usuario_modificacion)
SELECT gen_random_uuid(), te.id, t.rubrica_id, te.calificacion_obtenida,
  CASE WHEN te.calificacion_obtenida>=9 THEN 'Excelente trabajo'
       WHEN te.calificacion_obtenida>=7 THEN 'Buen desempeño'
       WHEN te.calificacion_obtenida>=6 THEN 'Aprobado, puede mejorar'
       ELSE 'Requiere refuerzo' END,
  COALESCE(te.fecha_calificacion_docente, te.fecha_modificacion, NOW()),
  '{SEED_TAG}', '{SEED_TAG}'
FROM ades_tareas_entregas te JOIN ades_tareas t ON t.id=te.tarea_id
WHERE te.calificacion_obtenida IS NOT NULL AND te.estatus_entrega='CALIFICADA'
ORDER BY te.id OFFSET 40000 LIMIT 40000
ON CONFLICT (tarea_entrega_id) DO NOTHING;
""")
exec_block("calificaciones_tareas_3", f"""
INSERT INTO ades_calificaciones_tareas
  (id, tarea_entrega_id, rubrica_id, calificacion, comentarios_docente,
   fecha_calificacion, usuario_creacion, usuario_modificacion)
SELECT gen_random_uuid(), te.id, t.rubrica_id, te.calificacion_obtenida,
  CASE WHEN te.calificacion_obtenida>=9 THEN 'Excelente trabajo'
       WHEN te.calificacion_obtenida>=7 THEN 'Buen desempeño'
       WHEN te.calificacion_obtenida>=6 THEN 'Aprobado, puede mejorar'
       ELSE 'Requiere refuerzo' END,
  COALESCE(te.fecha_calificacion_docente, te.fecha_modificacion, NOW()),
  '{SEED_TAG}', '{SEED_TAG}'
FROM ades_tareas_entregas te JOIN ades_tareas t ON t.id=te.tarea_id
WHERE te.calificacion_obtenida IS NOT NULL AND te.estatus_entrega='CALIFICADA'
ORDER BY te.id OFFSET 80000 LIMIT 50887
ON CONFLICT (tarea_entrega_id) DO NOTHING;
""")

# ── 3. avance_planificacion ──────────────────────────────────────────────────
print("── 3. Avance de planificación ───────────────────────")
# Use correlated subquery to get exactly 1 clase per planeacion (avoids combinatorial explosion)
exec_block("avance_planificacion", f"""
INSERT INTO ades_avance_planificacion
  (id, planeacion_clase_id, clase_id, fecha_ejecucion, es_completado,
   comentarios_profesor, usuario_creacion, usuario_modificacion)
SELECT
  gen_random_uuid(),
  pc.id,
  (SELECT c.id FROM ades_clases c
   JOIN ades_temas t2 ON t2.id = pc.tema_id
   WHERE c.grupo_id = pc.grupo_id AND c.materia_id = t2.materia_id
   ORDER BY ABS(c.fecha_clase - pc.fecha_planeada) LIMIT 1),
  pc.fecha_planeada,
  random() < 0.85,
  CASE WHEN random() < 0.3 THEN 'Clase impartida según planeación'
       WHEN random() < 0.5 THEN 'Se cubrió el tema con actividades adicionales'
       ELSE NULL END,
  '{SEED_TAG}', '{SEED_TAG}'
FROM ades_planeacion_clases pc
ON CONFLICT DO NOTHING;
""")

# ── 4. expedientes_alumno ────────────────────────────────────────────────────
print("── 4. Expedientes alumno ────────────────────────────")
if CICLO_SEP_ID:
    exec_block("expedientes_sep", f"""
INSERT INTO ades_expedientes_alumno
  (id, estudiante_id, ciclo_escolar_id, estado, completitud_pct,
   observaciones, usuario_creacion, usuario_modificacion)
SELECT gen_random_uuid(), e.id, '{CICLO_SEP_ID}',
  CASE (FLOOR(random()*10))::int WHEN 0 THEN 'PENDIENTE' WHEN 1 THEN 'INCOMPLETO' ELSE 'COMPLETO' END,
  CASE (FLOOR(random()*10))::int WHEN 0 THEN 30.0 WHEN 1 THEN 60.0 ELSE 100.0 END,
  NULL, '{SEED_TAG}', '{SEED_TAG}'
FROM ades_estudiantes e WHERE e.is_active=TRUE ON CONFLICT DO NOTHING;
""")

if CICLO_UAM_ID:
    exec_block("expedientes_uaemex", f"""
INSERT INTO ades_expedientes_alumno
  (id, estudiante_id, ciclo_escolar_id, estado, completitud_pct,
   observaciones, usuario_creacion, usuario_modificacion)
SELECT gen_random_uuid(), e.id, '{CICLO_UAM_ID}', 'COMPLETO', 100.0,
  'Expediente ciclo UAEMEX 26B', '{SEED_TAG}', '{SEED_TAG}'
FROM ades_estudiantes e
JOIN ades_inscripciones i ON i.estudiante_id=e.id
JOIN ades_grupos g ON g.id=i.grupo_id
JOIN ades_grados gr ON gr.id=g.grado_id
JOIN ades_niveles_educativos n ON n.id=gr.nivel_educativo_id
WHERE n.nombre_nivel='PREPARATORIA' AND e.is_active=TRUE AND i.is_active=TRUE
ON CONFLICT DO NOTHING;
""")

# ── 5. rol_privilegios ───────────────────────────────────────────────────────
print("── 5. Rol ↔ Privilegios ─────────────────────────────")
exec_block("rol_privilegios", """
INSERT INTO ades_rol_privilegios (rol_id, privilegio_id, usuario_creacion)
SELECT r.id, p.id, 'seed008'
FROM ades_roles r, ades_privilegios p
WHERE r.nombre_rol IN ('SUPERADMIN', 'DIRECTOR')
ON CONFLICT DO NOTHING;

INSERT INTO ades_rol_privilegios (rol_id, privilegio_id, usuario_creacion)
SELECT r.id, p.id, 'seed008'
FROM ades_roles r, ades_privilegios p
WHERE r.nombre_rol IN ('SUBDIRECTOR', 'COORDINADOR_ACADEMICO', 'CONTROL_ESCOLAR')
  AND p.nombre NOT IN ('Administrar Usuarios', 'Editar Variables del Sistema')
ON CONFLICT DO NOTHING;

INSERT INTO ades_rol_privilegios (rol_id, privilegio_id, usuario_creacion)
SELECT r.id, p.id, 'seed008' FROM ades_roles r, ades_privilegios p
WHERE r.nombre_rol = 'DOCENTE'
  AND p.nombre IN ('Generar Boletas','Editar Calificaciones','Gestionar Conducta','Ver BI / Analytics')
ON CONFLICT DO NOTHING;

INSERT INTO ades_rol_privilegios (rol_id, privilegio_id, usuario_creacion)
SELECT r.id, p.id, 'seed008' FROM ades_roles r, ades_privilegios p
WHERE r.nombre_rol = 'TUTOR_GRUPAL'
  AND p.nombre IN ('Generar Boletas','Gestionar Conducta','Ver BI / Analytics')
ON CONFLICT DO NOTHING;
""")

# ── 6. alertas_academicas ────────────────────────────────────────────────────
print("── 6. Alertas académicas ────────────────────────────")
exec_block("alertas_academicas", f"""
INSERT INTO ades_alertas_academicas
  (id, estudiante_id, grupo_id, tipo_alerta, nivel_riesgo, descripcion,
   datos_calculo, generada_por, atendida, notificada,
   usuario_creacion, usuario_modificacion)
SELECT gen_random_uuid(), cp.estudiante_id, i.grupo_id,
  CASE WHEN AVG(cp.calificacion_final)<6 THEN 'CALIFICACION_REPROBATORIA'
       WHEN AVG(cp.calificacion_final)<7 THEN 'CALIFICACION_EN_RIESGO'
       ELSE 'BAJO_RENDIMIENTO' END,
  CASE WHEN AVG(cp.calificacion_final)<6 THEN 'ALTO'
       WHEN AVG(cp.calificacion_final)<7 THEN 'MEDIO' ELSE 'BAJO' END,
  'Promedio: ' || ROUND(AVG(cp.calificacion_final)::numeric,2)::text || '/10',
  jsonb_build_object('promedio', ROUND(AVG(cp.calificacion_final)::numeric,2),
                     'materias', COUNT(DISTINCT cp.materia_id)),
  '{SEED_TAG}', random()<0.3, TRUE, '{SEED_TAG}', '{SEED_TAG}'
FROM ades_calificaciones_periodo cp
JOIN ades_inscripciones i ON i.estudiante_id=cp.estudiante_id AND i.is_active=TRUE
WHERE cp.calificacion_final IS NOT NULL
GROUP BY cp.estudiante_id, i.grupo_id
HAVING AVG(cp.calificacion_final)<7.5 LIMIT 500;
""")

# ── 7. alertas_cumplimiento ──────────────────────────────────────────────────
print("── 7. Alertas de cumplimiento ───────────────────────")
exec_block("alertas_cumplimiento", f"""
INSERT INTO ades_alertas_cumplimiento
  (id, tipo_alerta, descripcion, alumno_id, plantel_id,
   severidad, requiere_accion, estado, usuario_creacion, usuario_modificacion)
SELECT gen_random_uuid(), 'INASISTENCIA_EXCESIVA',
  'El alumno presenta más del 10% de inasistencias',
  e.estudiante_id, e.plantel_id,
  CASE WHEN ausentismo_pct>20 THEN 'ALTA' WHEN ausentismo_pct>15 THEN 'MEDIA' ELSE 'BAJA' END,
  TRUE, CASE WHEN random()<0.4 THEN 'ATENDIDA' ELSE 'PENDIENTE' END,
  '{SEED_TAG}', '{SEED_TAG}'
FROM (
  SELECT i.estudiante_id, est.plantel_id,
    ROUND(100.0 * SUM(CASE WHEN a.estatus_asistencia IN ('FALTA','INJUSTIFICADA') THEN 1 ELSE 0 END) /
      NULLIF(COUNT(a.id),0), 1) as ausentismo_pct
  FROM ades_asistencias a
  JOIN ades_inscripciones i ON i.estudiante_id=a.estudiante_id AND i.is_active=TRUE
  JOIN ades_estudiantes est ON est.id=i.estudiante_id
  GROUP BY i.estudiante_id, est.plantel_id HAVING COUNT(a.id)>20
) e WHERE ausentismo_pct>10 LIMIT 300;
""")

# ── 8. coordinaciones_area ───────────────────────────────────────────────────
print("── 8. Coordinaciones de área ────────────────────────")
if AREAS and DOCENTES:
    coord_rows = []
    for idx, area_id in enumerate(AREAS):
        docente = DOCENTES[idx % len(DOCENTES)]
        usuario_id = docente[2]
        coord_rows.append((
            uid(), usuario_id, area_id,
            date(2026, 8, 24), None, True,
            "Coordinación asignada para ciclo 2026-2027",
            SEED_TAG, SEED_TAG
        ))
    binsert("ades_coordinaciones_area",
            ["id", "usuario_id", "area_id", "fecha_inicio", "fecha_fin",
             "is_active", "notas", "usuario_creacion", "usuario_modificacion"],
            coord_rows)

# ── 9. reportes_academicos ───────────────────────────────────────────────────
print("── 9. Reportes académicos ───────────────────────────")
PERIODOS_SEP = [r[0] for r in q(f"SELECT id FROM ades_periodos_evaluacion WHERE ciclo_escolar_id='{CICLO_SEP_ID}' LIMIT 3")] if CICLO_SEP_ID else []
if PERIODOS_SEP and CICLO_SEP_ID:
    exec_block("reportes_academicos", f"""
INSERT INTO ades_reportes_academicos
  (id, estudiante_id, ciclo_escolar_id, periodo_evaluacion_id,
   tipo_reporte, datos_reporte, fecha_generacion, generado_por_id,
   is_active, usuario_creacion, usuario_modificacion)
SELECT gen_random_uuid(), cp.estudiante_id, pe.ciclo_escolar_id, cp.periodo_evaluacion_id,
  'BOLETA_CALIFICACIONES',
  jsonb_build_object('promedio_general', ROUND(AVG(cp.calificacion_final)::numeric,2),
    'materias', COUNT(DISTINCT cp.materia_id),
    'aprobadas', COUNT(DISTINCT cp.materia_id) FILTER (WHERE cp.calificacion_final>=6),
    'reprobadas', COUNT(DISTINCT cp.materia_id) FILTER (WHERE cp.calificacion_final<6)),
  NOW()-(random()*INTERVAL '30 days'),
  (SELECT id FROM ades_usuarios WHERE email_institucional='admin@institutonevadi.edu.mx' LIMIT 1),
  TRUE, '{SEED_TAG}', '{SEED_TAG}'
FROM ades_calificaciones_periodo cp
JOIN ades_periodos_evaluacion pe ON pe.id=cp.periodo_evaluacion_id
WHERE cp.calificacion_final IS NOT NULL
GROUP BY cp.estudiante_id, pe.ciclo_escolar_id, cp.periodo_evaluacion_id
LIMIT 3000;
""")

# ── 10. H5P asignaciones (Python-generated, committed in main file)
print("── 10. H5P asignaciones + resultados ────────────────")
if H5P_CONTENIDOS and GRUPOS:
    h5p_asig_rows = []
    for idx, grupo in enumerate(GRUPOS[:30]):
        grupo_id = grupo[0]
        contenido_id = H5P_CONTENIDOS[idx % len(H5P_CONTENIDOS)]
        fecha_desde = date(2026, 9, 1) + timedelta(weeks=idx % 12)
        fecha_hasta = fecha_desde + timedelta(weeks=2)
        h5p_asig_rows.append((
            uid(), contenido_id, None, grupo_id,
            fecha_desde, fecha_hasta, 3, 70.0, True,
            SEED_TAG, SEED_TAG
        ))
    binsert("ades_h5p_asignaciones",
            ["id", "contenido_id", "tarea_id", "grupo_id",
             "fecha_desde", "fecha_hasta", "intentos_max", "puntaje_minimo",
             "activo", "usuario_creacion", "usuario_modificacion"],
            h5p_asig_rows)

# ── 11. BBB Reuniones (Python-generated, committed in main file)
print("── 11. BBB Reuniones + Asistencia + Grabaciones ─────")
if GRUPOS and DOCENTES:
    bbb_rows = []
    for idx, grupo in enumerate(GRUPOS[:20]):
        grupo_id = grupo[0]
        plantel_id = grupo[1]
        docente = DOCENTES[idx % len(DOCENTES)]
        persona_id = docente[1]
        for semana in range(3):
            reunion_id = uid()
            fecha_prog = datetime(2026, 9, 15) + timedelta(weeks=idx * 3 + semana)
            meeting_id = f"ades-{reunion_id[:8]}"
            bbb_rows.append((
                reunion_id, meeting_id,
                f"Clase virtual semana {semana + 1} - Grupo",
                "Sesión sincrónica semanal", "CLASE",
                grupo_id, plantel_id, persona_id,
                fecha_prog, 90,
                uid()[:8], uid()[:8],
                True, "FINALIZADA",
                "¡Bienvenidos a la clase virtual!", 40, None,
                SEED_TAG, SEED_TAG
            ))
    binsert("ades_bbb_reuniones",
            ["id", "meeting_id", "nombre", "descripcion", "tipo",
             "grupo_id", "plantel_id", "organiza_persona_id",
             "fecha_programada", "duracion_max_min",
             "password_moderador", "password_asistente",
             "grabar", "estado", "bienvenida_msg", "participantes_max",
             "bbb_create_response", "usuario_creacion", "usuario_modificacion"],
            bbb_rows)

# ── Commit el archivo principal (coordinaciones + h5p_asig + bbb_reuniones) ──
F.write("SET session_replication_role=DEFAULT;\nCOMMIT;\n")
F.close()
print("── Ejecutando SQL (filas Python-generated) ──────────")
exec_sql_file(TMP)

# ── Server-side inserts que dependen de las filas ya commiteadas ─────────────
exec_block("h5p_resultados", f"""
INSERT INTO ades_h5p_resultados
  (id, contenido_id, estudiante_id, asignacion_id, intento,
   score_raw, score_max, score_escalado, completado, aprobado,
   tiempo_segundos, usuario_creacion, usuario_modificacion)
SELECT gen_random_uuid(), ha.contenido_id, i.estudiante_id, ha.id,
  1+FLOOR(random()*2)::int,
  ROUND((50+random()*50)::numeric,1), 100.0,
  ROUND((0.5+random()*0.5)::numeric,2),
  random()<0.85, random()<0.75,
  300+FLOOR(random()*1200)::int,
  '{SEED_TAG}', '{SEED_TAG}'
FROM ades_h5p_asignaciones ha
JOIN ades_inscripciones i ON i.grupo_id=ha.grupo_id AND i.is_active=TRUE
WHERE ha.usuario_creacion='{SEED_TAG}'
ON CONFLICT DO NOTHING;
""")

exec_block("bbb_asistencia", f"""
INSERT INTO ades_bbb_asistencia
  (id, reunion_id, persona_id, rol_bbb, joined_at, left_at,
   duracion_segundos, usuario_creacion, usuario_modificacion)
SELECT gen_random_uuid(), br.id, per.id, 'ASISTENTE',
  br.fecha_programada+INTERVAL '2 minutes',
  br.fecha_programada+INTERVAL '87 minutes',
  5100+FLOOR(random()*300)::int, '{SEED_TAG}', '{SEED_TAG}'
FROM ades_bbb_reuniones br
JOIN ades_grupos g ON g.id=br.grupo_id
JOIN ades_inscripciones insc ON insc.grupo_id=g.id AND insc.is_active=TRUE
JOIN ades_estudiantes est ON est.id=insc.estudiante_id
JOIN ades_personas per ON per.id=est.persona_id
WHERE br.usuario_creacion='{SEED_TAG}' AND random()<0.80
LIMIT 3000;
""")

exec_block("bbb_grabaciones", f"""
INSERT INTO ades_bbb_grabaciones
  (id, reunion_id, record_id, nombre, url_playback,
   duracion_segundos, tamanio_bytes, formatos, publicada, fecha_grabacion)
SELECT gen_random_uuid(), br.id, 'rec-'||LEFT(br.id::text,8),
  br.nombre||' (Grabación)',
  'https://bbb.institutonevadi.edu.mx/playback/presentation/2.3/'||br.meeting_id,
  5400+FLOOR(random()*600)::int,
  (250+FLOOR(random()*500))::bigint*1048576,
  '[{{"format":"presentation","url":"https://bbb.institutonevadi.edu.mx/playback/presentation/2.3/"}}]'::jsonb,
  TRUE, br.fecha_programada+INTERVAL '95 minutes'
FROM ades_bbb_reuniones br
WHERE br.usuario_creacion='{SEED_TAG}' AND br.grabar=TRUE;
""")

# ── 12. Asignaciones aula ────────────────────────────────────────────────────
print("── 12. Asignaciones aula ────────────────────────────")
if AULAS:
    exec_block("asignaciones_aula", f"""
INSERT INTO ades_asignaciones_aula
  (id, clase_id, aula_id, fecha, hora_inicio, hora_fin,
   is_active, usuario_creacion, usuario_modificacion)
SELECT gen_random_uuid(), c.id,
  (SELECT a.id FROM ades_aulas a
   WHERE a.plantel_id=gr.plantel_id AND a.is_active=TRUE ORDER BY RANDOM() LIMIT 1),
  c.fecha_clase, c.hora_inicio, c.hora_fin,
  TRUE, '{SEED_TAG}', '{SEED_TAG}'
FROM ades_clases c
JOIN ades_grupos g ON g.id=c.grupo_id
JOIN ades_grados gr ON gr.id=g.grado_id
WHERE c.is_active=TRUE
  AND NOT EXISTS (SELECT 1 FROM ades_asignaciones_aula aa WHERE aa.clase_id=c.id)
LIMIT 3000;
""")

# ── 13. Archivos ─────────────────────────────────────────────────────────────
print("── 13. Archivos ─────────────────────────────────────")
exec_block("archivos_certificados", f"""
INSERT INTO ades_archivos
  (id, nombre_original, nombre_almacenado, bucket, mime_type,
   tamanio_bytes, entidad_tipo, entidad_id, is_active,
   usuario_creacion, usuario_modificacion)
SELECT gen_random_uuid(),
  'certificado_'||LEFT(cert.id::text,8)||'.pdf',
  'certificados/'||cert.id::text||'.pdf',
  'ades-docs', 'application/pdf',
  (80+FLOOR(random()*200))::bigint*1024,
  'certificado', cert.id, TRUE, '{SEED_TAG}', '{SEED_TAG}'
FROM ades_certificados cert
WHERE cert.estado_firma IN ('FIRMADO','PENDIENTE') LIMIT 300;
""")

exec_block("archivos_expedientes", f"""
INSERT INTO ades_archivos
  (id, nombre_original, nombre_almacenado, bucket, mime_type,
   tamanio_bytes, entidad_tipo, entidad_id, is_active,
   usuario_creacion, usuario_modificacion)
SELECT gen_random_uuid(),
  'expediente_'||LEFT(ea.id::text,8)||'.pdf',
  'expedientes/'||ea.id::text||'/resumen.pdf',
  'ades-docs', 'application/pdf',
  (120+FLOOR(random()*300))::bigint*1024,
  'expediente_alumno', ea.id, TRUE, '{SEED_TAG}', '{SEED_TAG}'
FROM ades_expedientes_alumno ea
WHERE ea.estado='COMPLETO' LIMIT 500;
""")

# ── Verificación ─────────────────────────────────────────────────────────────
print("\n══ Verificación ════════════════════════════════════")
checks = [
    ("ades_calificaciones_tareas", "calificaciones_tareas"),
    ("ades_avance_planificacion", "avance_planificacion"),
    ("ades_expedientes_alumno", "expedientes_alumno"),
    ("ades_rol_privilegios", "rol_privilegios"),
    ("ades_alertas_academicas", "alertas_academicas"),
    ("ades_alertas_cumplimiento", "alertas_cumplimiento"),
    ("ades_coordinaciones_area", "coordinaciones_area"),
    ("ades_reportes_academicos", "reportes_academicos"),
    ("ades_h5p_asignaciones", "h5p_asignaciones"),
    ("ades_h5p_resultados", "h5p_resultados"),
    ("ades_bbb_reuniones", "bbb_reuniones"),
    ("ades_bbb_asistencia", "bbb_asistencia"),
    ("ades_bbb_grabaciones", "bbb_grabaciones"),
    ("ades_asignaciones_aula", "asignaciones_aula"),
    ("ades_archivos", "archivos"),
]
for table, label in checks:
    cnt = (q(f"SELECT COUNT(*) FROM {table}") or [["0"]])[0][0]
    print(f"   {label:<30} {cnt:>8} filas")

# Verificar tareas con rubrica_id
cnt_r = (q("SELECT COUNT(*) FROM ades_tareas WHERE rubrica_id IS NOT NULL") or [["0"]])[0][0]
print(f"   {'tareas con rubrica_id':<30} {cnt_r:>8} filas")
print("\n✓ Seed 008 completo")

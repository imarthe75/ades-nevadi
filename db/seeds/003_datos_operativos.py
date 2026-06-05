"""
Seed 003 — Datos operativos completos para pruebas funcionales.

Genera:
  1. Usuarios ALUMNO     (cuenta por cada estudiante)
  2. Usuarios de personal (subdirectores, coordinadores, tutores, etc.)
  3. Horarios semanales  (por grupo x materia)
  4. Clases              (20 sesiones por asignación)
  5. Asistencias         (≈95% presentes)
  6. Calificaciones por periodo
  7. Tareas y entregas   (3 tareas por periodo por asignación, 80% entrega)
  8. Expedientes médicos (todos los alumnos)
  9. Reportes de conducta (5% de alumnos)
 10. Comunicados         (6 ficticios)
 11. Rúbricas adicionales (5 con criterios)

Ejecutar dentro del contenedor:
  DATABASE_URL="postgresql://..." python3 seed003.py
"""
import os, random, uuid
from datetime import date, datetime, timedelta
import psycopg2
from psycopg2.extras import execute_values

DSN = os.environ.get(
    "DATABASE_URL",
    "postgresql://ades_admin:ades_secret_2024@ades-postgres:5432/ades"
)

conn = psycopg2.connect(DSN)
conn.autocommit = False
cur  = conn.cursor()
rng  = random.Random(42)

# ── helpers ───────────────────────────────────────────────────────
def uid(): return str(uuid.uuid4())
def rref(): return str(uuid.uuid4())

def batch_insert(table, cols, rows, page=500):
    if not rows:
        return
    tmpl = "(%s)" % ",".join(["%s"] * len(cols))
    sql  = f"INSERT INTO {table} ({','.join(cols)}) VALUES %s ON CONFLICT DO NOTHING"
    for i in range(0, len(rows), page):
        execute_values(cur, sql, rows[i:i+page], template=tmpl)

# ── catálogos base ────────────────────────────────────────────────
print("── Cargando catálogos ───────────────────────────")

cur.execute("SELECT id, nombre_rol FROM ades_roles")
roles = {r[1]: r[0] for r in cur.fetchall()}

cur.execute("""
    SELECT g.id, g.nombre_grupo, g.ciclo_escolar_id,
           gr.id, gr.numero_grado, gr.plantel_id, gr.nivel_educativo_id,
           n.nombre_nivel
    FROM ades_grupos g
    JOIN ades_grados gr ON gr.id = g.grado_id
    JOIN ades_ciclos_escolares ce ON ce.id = g.ciclo_escolar_id
    JOIN ades_niveles_educativos n ON n.id = gr.nivel_educativo_id
    WHERE g.is_active AND ce.es_vigente
""")
grupos = cur.fetchall()
print(f"  {len(grupos)} grupos vigentes")

cur.execute("""
    SELECT i.estudiante_id, i.grupo_id, e.persona_id
    FROM ades_inscripciones i
    JOIN ades_estudiantes e ON e.id = i.estudiante_id
    JOIN ades_grupos g ON g.id = i.grupo_id
    JOIN ades_ciclos_escolares ce ON ce.id = i.ciclo_escolar_id
    WHERE i.is_active AND ce.es_vigente
""")
inscripciones = cur.fetchall()
grupo_alumnos = {}
for est_id, grp_id, _ in inscripciones:
    grupo_alumnos.setdefault(grp_id, []).append(est_id)
print(f"  {len(inscripciones)} inscripciones vigentes")

cur.execute("""
    SELECT ad.id, ad.grupo_id, ad.materia_id, ad.profesor_id, ad.ciclo_escolar_id
    FROM ades_asignaciones_docentes ad
    JOIN ades_ciclos_escolares ce ON ce.id = ad.ciclo_escolar_id
    WHERE ad.is_active AND ce.es_vigente
""")
asignaciones = cur.fetchall()
print(f"  {len(asignaciones)} asignaciones docentes")

cur.execute("""
    SELECT pe.id, pe.nombre_periodo, pe.ciclo_escolar_id, pe.fecha_inicio, pe.fecha_fin
    FROM ades_periodos_evaluacion pe
    JOIN ades_ciclos_escolares ce ON ce.id = pe.ciclo_escolar_id
    WHERE pe.is_active
    ORDER BY pe.fecha_inicio
""")
periodos = cur.fetchall()
ciclo_periodos = {}
for pe in periodos:
    ciclo_periodos.setdefault(pe[2], []).append(pe)
print(f"  {len(periodos)} periodos de evaluación")

cur.execute("SELECT id FROM ades_planteles WHERE is_active")
planteles = [r[0] for r in cur.fetchall()]

cur.execute("SELECT id FROM ades_aulas WHERE is_active")
aulas = [r[0] for r in cur.fetchall()]

cur.execute("""
    SELECT e.id, e.persona_id, p.curp, p.nombre, p.apellido_paterno
    FROM ades_estudiantes e JOIN ades_personas p ON p.id = e.persona_id
    WHERE e.is_active
""")
estudiantes_info = cur.fetchall()
print(f"  {len(estudiantes_info)} estudiantes activos")

cur.execute("""
    SELECT u.persona_id FROM ades_usuarios u
    JOIN ades_roles r ON r.id = u.rol_id WHERE r.nombre_rol = 'ALUMNO'
""")
existing_alumno_personas = {r[0] for r in cur.fetchall()}

cur.execute("SELECT id FROM ades_niveles_educativos WHERE nombre_nivel='PRIMARIA' LIMIT 1")
nid_pri = cur.fetchone()[0]
cur.execute("SELECT id FROM ades_niveles_educativos WHERE nombre_nivel='SECUNDARIA' LIMIT 1")
nid_sec = cur.fetchone()[0]
cur.execute("SELECT id FROM ades_niveles_educativos WHERE nombre_nivel='PREPARATORIA' LIMIT 1")
nid_pre = cur.fetchone()[0]

# ═══════════════════════════════════════════════════════════════
# 1. USUARIOS ALUMNO
# ═══════════════════════════════════════════════════════════════
print("\n── 1. Usuarios ALUMNO ───────────────────────────")
alumno_rows = []
for est_id, persona_id, curp, nombre, ap in estudiantes_info:
    if persona_id in existing_alumno_personas:
        continue
    slug  = f"{(nombre or 'x')[0].lower()}{(ap or 'x')[:8].lower()}".replace(" ", "")
    uname = f"{slug}{rng.randint(10,99)}"
    email = f"{uname}@alumnos.nevadi.edu.mx"
    alumno_rows.append((uid(), persona_id, uname, email,
                        roles['ALUMNO'], None, None,
                        'HASH_OIDC', True, rref(), 'seed003','seed003',1))

batch_insert('ades_usuarios',
    ['id','persona_id','nombre_usuario','email_institucional',
     'rol_id','plantel_id','nivel_educativo_id',
     'clave_hash','is_active','ref','usuario_creacion','usuario_modificacion','row_version'],
    alumno_rows)
print(f"  {len(alumno_rows)} usuarios ALUMNO")

# ═══════════════════════════════════════════════════════════════
# 2. PERSONAL DE APOYO
# ═══════════════════════════════════════════════════════════════
print("\n── 2. Personal de apoyo ─────────────────────────")

NOMBRES_M = ["Carlos","Miguel","José","Luis","Alejandro","Roberto","Fernando","Jorge","Eduardo","David"]
NOMBRES_F = ["María","Ana","Laura","Patricia","Sofía","Valentina","Claudia","Gabriela","Rosa","Elena"]
APELLIDOS  = ["García","Martínez","López","Sánchez","Hernández","Pérez","González","Torres","Ramírez","Flores",
              "Rivera","Morales","Jiménez","Reyes","Cruz","Ortiz","Vargas","Díaz","Romero","Gutiérrez"]

staff_count = 0

def crear_personal(plantel_id, nivel_id, rol_nombre):
    global staff_count
    gen = rng.choice(['M','F'])
    nom = rng.choice(NOMBRES_M if gen=='M' else NOMBRES_F)
    ap1 = rng.choice(APELLIDOS); ap2 = rng.choice(APELLIDOS)
    slug  = f"{nom[0].lower()}{ap1[:8].lower()}{rng.randint(10,99)}"
    email = f"{slug}@nevadi.edu.mx"
    pid   = uid()
    curp_mock = f"{ap1[:2].upper()}{ap2[:1].upper()}{nom[:1].upper()}800101HMC{rng.randint(100,999):03d}"
    cur.execute("""
        INSERT INTO ades_personas(id,nombre,apellido_paterno,apellido_materno,curp,genero,
            ref,usuario_creacion,usuario_modificacion,row_version)
        VALUES(%s,%s,%s,%s,%s,%s,%s,'seed003','seed003',1) ON CONFLICT DO NOTHING
    """, (pid, nom, ap1, ap2, curp_mock[:18], gen, rref()))
    uid2 = uid()
    cur.execute("""
        INSERT INTO ades_usuarios(id,persona_id,nombre_usuario,email_institucional,
            rol_id,plantel_id,nivel_educativo_id,clave_hash,is_active,
            ref,usuario_creacion,usuario_modificacion,row_version)
        VALUES(%s,%s,%s,%s,%s,%s,%s,'HASH_OIDC',TRUE,%s,'seed003','seed003',1) ON CONFLICT DO NOTHING
    """, (uid2, pid, slug, email, roles[rol_nombre], plantel_id, nivel_id, rref()))
    staff_count += 1

for pid in planteles:
    for nid in [nid_pri, nid_sec, nid_pre]:
        crear_personal(pid, nid, 'SUBDIRECTOR')
    for rol in ['COORDINADOR_ACADEMICO','COORDINADOR_ADMINISTRATIVO']:
        crear_personal(pid, None, rol)
    for _ in range(2):
        crear_personal(pid, None, 'TUTOR')
    for rol in ['ORIENTADOR','PREFECTO','MEDICO_ESCOLAR','SECRETARIA_ACADEMICA']:
        crear_personal(pid, None, rol)

print(f"  {staff_count} usuarios de personal")

# ═══════════════════════════════════════════════════════════════
# 3. HORARIOS
# ═══════════════════════════════════════════════════════════════
print("\n── 3. Horarios semanales ────────────────────────")
HORAS = ['07:00','08:00','09:00','10:00','11:00','12:00','13:00']

horario_rows = []
for ad_id, grp_id, mat_id, prof_id, ciclo_id in asignaciones:
    dia  = rng.randint(1, 5)   # 1=Lunes … 5=Viernes (smallint)
    hi   = rng.choice(HORAS[:6])
    hf   = HORAS[HORAS.index(hi)+1]
    aula = rng.choice(aulas) if aulas else None
    horario_rows.append((
        uid(), grp_id, mat_id, prof_id, aula, ciclo_id,
        dia, hi, hf, 'PROGRAMADO',
        True, rref(), 'seed003','seed003',1
    ))

batch_insert('ades_horarios',
    ['id','grupo_id','materia_id','profesor_id','aula_id','ciclo_escolar_id',
     'dia_semana','hora_inicio','hora_fin','origen',
     'is_active','ref','usuario_creacion','usuario_modificacion','row_version'],
    horario_rows)
print(f"  {len(horario_rows)} horarios")

# ═══════════════════════════════════════════════════════════════
# 4. CLASES (20 sesiones por asignación)
# ═══════════════════════════════════════════════════════════════
print("\n── 4. Clases realizadas ─────────────────────────")
TEMAS = {
    'PRIMARIA':     ['Introducción','Práctica guiada','Actividad colaborativa','Evaluación formativa','Repaso'],
    'SECUNDARIA':   ['Planteamiento','Investigación','Análisis','Trabajo por proyectos','Síntesis'],
    'PREPARATORIA': ['Fundamentos','Resolución de problemas','Seminario','Laboratorio','Presentación'],
}
grupo_nivel = {g[0]: g[7] for g in grupos}

clases_rows = []
for ad_id, grp_id, mat_id, prof_id, ciclo_id in asignaciones:
    nivel = grupo_nivel.get(grp_id, 'PRIMARIA')
    temas = TEMAS.get(nivel, TEMAS['PRIMARIA'])
    pes   = ciclo_periodos.get(ciclo_id, [])
    fi    = pes[0][3] if pes else date(2026, 8, 25)
    for k in range(20):
        fecha = fi + timedelta(days=k * 3)
        clases_rows.append((
            uid(), grp_id, mat_id, prof_id,
            fecha, '08:00', '09:00',
            rng.choice(temas), 'REALIZADA', True,
            True, rref(), 'seed003','seed003',1
        ))

batch_insert('ades_clases',
    ['id','grupo_id','materia_id','profesor_id',
     'fecha_clase','hora_inicio','hora_fin','tema_visto','estatus_clase','impartida',
     'is_active','ref','usuario_creacion','usuario_modificacion','row_version'],
    clases_rows)
print(f"  {len(clases_rows)} clases")

# ═══════════════════════════════════════════════════════════════
# 5. ASISTENCIAS
# ═══════════════════════════════════════════════════════════════
print("\n── 5. Asistencias (95% presente) ────────────────")
cur.execute("SELECT id, grupo_id FROM ades_clases WHERE is_active")
clases_db = cur.fetchall()

ESTATUS_A = ['PRESENTE'] * 19 + ['AUSENTE']
asist_rows = []
for clase_id, grp_id in clases_db:
    for est_id in grupo_alumnos.get(grp_id, []):
        est = rng.choice(ESTATUS_A)
        asist_rows.append((
            uid(), clase_id, est_id, est, None,
            True, rref(), 'seed003','seed003',1
        ))
    if len(asist_rows) >= 200_000:
        batch_insert('ades_asistencias',
            ['id','clase_id','estudiante_id','estatus_asistencia','observacion',
             'is_active','ref','usuario_creacion','usuario_modificacion','row_version'],
            asist_rows)
        asist_rows = []

batch_insert('ades_asistencias',
    ['id','clase_id','estudiante_id','estatus_asistencia','observacion',
     'is_active','ref','usuario_creacion','usuario_modificacion','row_version'],
    asist_rows)
print(f"  ~{len(clases_db) * 30} asistencias insertadas")

# ═══════════════════════════════════════════════════════════════
# 6. CALIFICACIONES POR PERIODO
# ═══════════════════════════════════════════════════════════════
print("\n── 6. Calificaciones por periodo ────────────────")
cal_rows = []
for pe_id, pe_nom, ciclo_id, fi, ff in periodos:
    for ad_id, grp_id, mat_id, prof_id, ad_ciclo in asignaciones:
        if ad_ciclo != ciclo_id:
            continue
        for est_id in grupo_alumnos.get(grp_id, []):
            nota = round(max(5.0, min(10.0, rng.gauss(7.5, 1.2))), 1)
            cal_rows.append((
                uid(), est_id, grp_id, mat_id, pe_id,
                nota, nota, None, False,    # es_acreditado es GENERATED, no se inserta
                rng.randint(0,2), 0,
                True, rref(), 'seed003','seed003',1
            ))
    if len(cal_rows) >= 100_000:
        batch_insert('ades_calificaciones_periodo',
            ['id','estudiante_id','grupo_id','materia_id','periodo_evaluacion_id',
             'calificacion_calculada','calificacion_final','ajuste_manual','es_acreditado','cerrada',
             'inasistencias','justificadas',
             'is_active','ref','usuario_creacion','usuario_modificacion','row_version'],
            cal_rows)
        cal_rows = []

batch_insert('ades_calificaciones_periodo',
    ['id','estudiante_id','grupo_id','materia_id','periodo_evaluacion_id',
     'calificacion_calculada','calificacion_final','ajuste_manual','cerrada',
     'inasistencias','justificadas',
     'is_active','ref','usuario_creacion','usuario_modificacion','row_version'],
    cal_rows)
print("  Calificaciones insertadas")

# ═══════════════════════════════════════════════════════════════
# 7. TAREAS Y ENTREGAS
# ═══════════════════════════════════════════════════════════════
print("\n── 7. Tareas y entregas ─────────────────────────")

# Obtener rubricas disponibles
cur.execute("SELECT id FROM ades_rubricas WHERE is_active")
rubricas_ids = [r[0] for r in cur.fetchall()]

TITULOS = ['Investigación bibliográfica','Mapa conceptual','Ejercicios de práctica',
           'Resumen de lectura','Cuestionario de repaso','Proyecto integrador',
           'Ensayo analítico','Resolución de problemas','Presentación oral','Examen corto']

tarea_rows = []
for pe_id, pe_nom, ciclo_id, fi, ff in periodos[:6]:   # máx 6 periodos
    for ad_id, grp_id, mat_id, prof_id, ad_ciclo in asignaciones:
        if ad_ciclo != ciclo_id:
            continue
        for t in range(3):
            fecha_a = fi + timedelta(days=t*10)
            fecha_e = fecha_a + timedelta(days=7)
            rub_id  = rng.choice(rubricas_ids) if rubricas_ids else None
            tarea_rows.append((
                uid(), rng.choice(TITULOS),
                f'Complete las actividades indicadas.',
                grp_id, mat_id, pe_id,
                fecha_a, fecha_e, 10.0, False, 'MANUAL', rub_id,
                True, rref(), 'seed003','seed003',1
            ))

batch_insert('ades_tareas',
    ['id','titulo','descripcion','grupo_id','materia_id','periodo_evaluacion_id',
     'fecha_asignacion','fecha_entrega','puntaje_maximo','permite_entrega_tarde','origen','rubrica_id',
     'is_active','ref','usuario_creacion','usuario_modificacion','row_version'],
    tarea_rows)

cur.execute("SELECT id, grupo_id, puntaje_maximo FROM ades_tareas WHERE is_active")
tareas_db = cur.fetchall()

entrega_rows = []
for tar_id, grp_id, pts_max in tareas_db:
    for est_id in grupo_alumnos.get(grp_id, []):
        if rng.random() > 0.80:
            continue
        pts = round(max(0, min(float(pts_max or 10), rng.gauss(8.0,1.5))), 1)
        entrega_rows.append((
            uid(), tar_id, est_id,
            datetime.now() - timedelta(days=rng.randint(0,5)),
            pts, 'CALIFICADA',
            True, rref(), 'seed003','seed003',1
        ))
    if len(entrega_rows) >= 50_000:
        batch_insert('ades_tareas_entregas',
            ['id','tarea_id','estudiante_id','fecha_entrega',
             'calificacion_obtenida','estatus_entrega',
             'is_active','ref','usuario_creacion','usuario_modificacion','row_version'],
            entrega_rows)
        entrega_rows = []

batch_insert('ades_tareas_entregas',
    ['id','tarea_id','estudiante_id','fecha_entrega',
     'calificacion_obtenida','estatus_entrega',
     'is_active','ref','usuario_creacion','usuario_modificacion','row_version'],
    entrega_rows)
print(f"  {len(tarea_rows)} tareas insertadas")

# ═══════════════════════════════════════════════════════════════
# 8. EXPEDIENTES MÉDICOS
# ═══════════════════════════════════════════════════════════════
print("\n── 8. Expedientes médicos ───────────────────────")
SANGRES  = ['A+','A-','B+','B-','O+','O-','AB+','AB-']
SEGUROS  = ['IMSS','ISSSTE','PRIVADO','NINGUNO']
ALERG    = [None,None,None,'Polen','Penicilina','Mariscos','Frutos secos']
DISCAP   = [None,None,None,None,'Hipoacusia leve','Dislexia','TDAH']

exp_rows = []
for est_id, *_ in estudiantes_info:
    exp_rows.append((
        uid(), est_id,
        rng.choice(SANGRES), rng.choice(ALERG),
        None, None, None,
        rng.choice(SEGUROS), rng.choice(DISCAP),
        rng.random() < .85,
        rng.random() < .05,
        rng.random() < .03,
        True, rref(), 'seed003','seed003',1
    ))

batch_insert('ades_expedientes_medicos',
    ['id','estudiante_id','tipo_sangre','alergias',
     'medicamentos_autorizados','condiciones_cronicas','observaciones_generales',
     'seguro_medico_tipo','discapacidad',
     'vacunas_al_dia','padecimiento_cronico','requiere_medicacion',
     'is_active','ref','usuario_creacion','usuario_modificacion','row_version'],
    exp_rows)
print(f"  {len(exp_rows)} expedientes médicos")

# ═══════════════════════════════════════════════════════════════
# 9. CONDUCTA
# ═══════════════════════════════════════════════════════════════
print("\n── 9. Reportes de conducta ──────────────────────")
cur.execute("""
    SELECT u.id FROM ades_usuarios u JOIN ades_roles r ON r.id=u.rol_id
    WHERE r.nombre_rol IN ('DIRECTOR','SUBDIRECTOR','PREFECTO') AND u.is_active LIMIT 10
""")
staff_ids = [r[0] for r in cur.fetchall()] or [None]

TIPOS_F = ['FALTA_UNIFORME','DISPOSITIVO_MOVIL','CONDUCTA_INAPROPIADA',
           'AUSENTISMO','RETRASO_REITERADO']
MEDIDAS  = ['Amonestación verbal','Reporte escrito','Citatorio a padres','Suspensión 1 día']

sample_n = max(1, len(estudiantes_info) // 20)
conducta_rows = []
for est_id, *_ in rng.sample(estudiantes_info, sample_n):
    for _ in range(rng.randint(1,3)):
        conducta_rows.append((
            uid(), est_id, None,
            rng.choice(staff_ids),
            date.today() - timedelta(days=rng.randint(1,90)),
            rng.choice(TIPOS_F),
            'Incidente registrado por el personal docente.',
            rng.choice(MEDIDAS),
            None,   # compromiso_mejora
            rng.random() < .3,  # requiere_seguimiento
            estatus_activo if (estatus_activo := None) else None,
            True, rref(), 'seed003','seed003',1
        ))

# Get estatus_activo
cur.execute("SELECT id FROM ades_estatus WHERE nombre_estatus='ACTIVO' LIMIT 1")
row = cur.fetchone()
estatus_activo_id = row[0] if row else None

conducta_rows2 = []
for est_id, *_ in rng.sample(estudiantes_info, sample_n):
    for _ in range(rng.randint(1,2)):
        conducta_rows2.append((
            uid(), est_id, None,
            rng.choice(staff_ids),
            date.today() - timedelta(days=rng.randint(1,90)),
            rng.choice(TIPOS_F),
            'Incidente registrado por el personal docente.',
            rng.choice(MEDIDAS),
            None,
            rng.random() < .3,
            estatus_activo_id,
            True, rref(), 'seed003','seed003',1
        ))

batch_insert('ades_reportes_conducta',
    ['id','estudiante_id','grupo_id',
     'reportado_por_id','fecha_reporte','tipo_falta',
     'descripcion','medida_aplicada','compromiso_mejora',
     'requiere_seguimiento','estatus_id',
     'is_active','ref','usuario_creacion','usuario_modificacion','row_version'],
    conducta_rows2)
print(f"  {len(conducta_rows2)} reportes de conducta")

# ═══════════════════════════════════════════════════════════════
# 10. COMUNICADOS
# ═══════════════════════════════════════════════════════════════
print("\n── 10. Comunicados ──────────────────────────────")
cur.execute("""
    SELECT u.id FROM ades_usuarios u JOIN ades_roles r ON r.id=u.rol_id
    WHERE r.nombre_rol IN ('ADMIN_GLOBAL','DIRECTOR') AND u.is_active LIMIT 3
""")
admins = [r[0] for r in cur.fetchall()]

COMUNICADOS_S = [
    ('Bienvenida ciclo 2026-2027',
     'Estimada comunidad, les damos la más cordial bienvenida al nuevo ciclo escolar 2026-2027.',
     'GENERAL'),
    ('Periodo de evaluaciones primer bimestre',
     'Se les informa que el periodo de evaluaciones del primer bimestre iniciará el lunes próximo.',
     'ACADEMICO'),
    ('Suspensión de actividades — 16 de septiembre',
     'El próximo 16 de septiembre no habrá actividades académicas por celebración del día festivo.',
     'ADMINISTRATIVO'),
    ('Reunión informativa para padres de familia',
     'Invitamos a todos los padres y tutores a la reunión informativa del ciclo. Viernes 14:00 hrs.',
     'GENERAL'),
    ('Resultados del primer bimestre disponibles',
     'Las calificaciones del primer bimestre ya están disponibles en la plataforma ADES.',
     'ACADEMICO'),
    ('Jornada de vacunación escolar',
     'El servicio médico informa que se realizará jornada de vacunación el próximo jueves.',
     'MEDICO' if 'MEDICO' in ['GENERAL','ACADEMICO','ADMINISTRATIVO','MEDICO'] else 'GENERAL'),
]

com_rows = []
for titulo, cuerpo, tipo in COMUNICADOS_S:
    com_rows.append((
        uid(), titulo, cuerpo, tipo,
        rng.choice(admins) if admins else None,
        date.today() - timedelta(days=rng.randint(1,60)),
        None, False,
        True, rref(), 'seed003','seed003',1
    ))

batch_insert('ades_comunicados',
    ['id','titulo','contenido','tipo_comunicado',
     'creado_por_id','fecha_publicacion','fecha_vencimiento','requiere_acuse',
     'is_active','ref','usuario_creacion','usuario_modificacion','row_version'],
    com_rows)
print(f"  {len(com_rows)} comunicados")

# ═══════════════════════════════════════════════════════════════
# 11. RÚBRICAS
# ═══════════════════════════════════════════════════════════════
print("\n── 11. Rúbricas adicionales ─────────────────────")
RUBS = [
    ('Exposición Oral', [('Dominio del tema',25),('Claridad',25),('Recursos visuales',25),('Tiempo',25)]),
    ('Ensayo Académico', [('Tesis',25),('Argumentación',25),('Evidencias',25),('Redacción',25)]),
    ('Proyecto Integrador', [('Originalidad',20),('Fundamentación',30),('Resultados',30),('Presentación',20)]),
    ('Práctica de Laboratorio', [('Procedimiento',30),('Seguridad',20),('Resultados',30),('Reporte',20)]),
    ('Participación en Clase', [('Frecuencia',25),('Pertinencia',35),('Calidad',40)]),
]

rub_rows = []
crit_rows = []
for nombre, criterios in RUBS:
    rid = uid()
    rub_rows.append((rid, nombre, f'Evalúa {nombre.lower()}', 100,
                     True, rref(), 'seed003','seed003',1))
    for orden, (crit, peso) in enumerate(criterios, 1):
        crit_rows.append((uid(), rid, crit, f'Evalúa {crit.lower()}', peso, orden,
                          True, rref(), 'seed003','seed003',1))

batch_insert('ades_rubricas',
    ['id','nombre','descripcion','puntaje_maximo',
     'is_active','ref','usuario_creacion','usuario_modificacion','row_version'],
    rub_rows)
batch_insert('ades_rubrica_criterios',
    ['id','rubrica_id','nombre_criterio','descripcion','ponderacion','orden',
     'is_active','ref','usuario_creacion','usuario_modificacion','row_version'],
    crit_rows)
print(f"  {len(rub_rows)} rúbricas, {len(crit_rows)} criterios")

# ═══════════════════════════════════════════════════════════════
conn.commit()
cur.close()
conn.close()

print("\n" + "═"*55)
print("  ✓  Seed 003 completado exitosamente")
print("═"*55)

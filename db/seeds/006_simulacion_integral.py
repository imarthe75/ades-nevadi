"""
Seed 004 — Simulación integral ADES (datos realistas + ciclo de calificaciones).

Reemplaza por completo los datos OPERATIVOS ficticios por datos simulados realistas
y sin duplicados de persona, y simula la cadena completa de calificación:

    plan de estudios -> temario -> planificación semanal -> tareas/proyectos
    -> exámenes (trimestral SEP / parcial UAEMEX) -> asistencias + conducta
    -> calificaciones (función calcular_calificacion_periodo) -> estadísticos.

PRESERVA: planteles, currícula oficial (materias_plan, temas, materias), grados,
grupos, ciclos, periodos (corregidos a trimestres NEM), esquemas/ítems de ponderación,
catálogos geográficos y de seguridad, y las 13 cuentas institucionales
(@institutonevadi.edu.mx: admin global, admin de plantel, directores) para no perder
el login OIDC.

Identidad sin duplicados: nombres mexicanos curados + CURP de 18 caracteres con
homoclave única (verificada en memoria, respeta UNIQUE(curp)) + direcciones reales
muestreadas de ades_codigos_postales.

Ejecutar (desde el host, contra el contenedor):
    docker compose exec -T ades-api \
      env DATABASE_URL="postgresql://ades_admin:<pwd>@ades-postgres:5432/ades" \
      python3 - < db/seeds/004_simulacion_integral.py
"""
import os, random, uuid, unicodedata, itertools, string
from datetime import date, datetime, timedelta
import psycopg2
from psycopg2.extras import execute_values

DSN = os.environ["DATABASE_URL"]
SEED_TAG = "seed004"
rng = random.Random(2026)

ALUMNOS_POR_GRUPO       = 26
PROFES_POR_PLANTEL      = 22
CLASES_POR_PERIODO      = 6
TAREAS_POR_ITEM_PERIODO = 2

conn = psycopg2.connect(DSN)
conn.autocommit = False
cur = conn.cursor()

def uid():
    return str(uuid.uuid4())

# ── catálogos de nombres mexicanos (curados) ──────────────────────────────────
NOMBRES_H = ["José","Juan","Luis","Carlos","Miguel","Ángel","Fernando","Jorge","Eduardo",
    "Roberto","Ricardo","Alejandro","Francisco","Daniel","Diego","Emiliano","Santiago",
    "Mateo","Sebastián","Leonardo","Adrián","David","Iván","Héctor","Raúl","Óscar",
    "Pablo","Andrés","Gerardo","Rodrigo","Arturo","Manuel","Antonio","Javier","Marco"]
NOMBRES_M = ["María","Guadalupe","Ana","Laura","Patricia","Sofía","Valentina","Camila",
    "Regina","Ximena","Fernanda","Daniela","Mariana","Andrea","Paola","Gabriela","Claudia",
    "Rosa","Elena","Verónica","Alejandra","Karla","Diana","Lucía","Renata","Isabela",
    "Victoria","Natalia","Jimena","Montserrat","Adriana","Brenda","Carmen","Leticia"]
APELLIDOS = ["García","Martínez","López","Hernández","González","Pérez","Rodríguez",
    "Sánchez","Ramírez","Cruz","Flores","Gómez","Morales","Vázquez","Reyes","Jiménez",
    "Torres","Díaz","Gutiérrez","Ruiz","Mendoza","Aguilar","Ortiz","Castillo","Romero",
    "Álvarez","Ramos","Domínguez","Vargas","Estrada","Medina","Guerrero","Rojas","Núñez",
    "Cortés","Lara","Ríos","Rivera","Salazar","Campos","Contreras","Figueroa","Luna",
    "Cervantes","Maldonado","Velázquez","Fuentes","Carrillo","Santiago","Ibarra"]

VOCALES = "AEIOU"
def _norm(s):
    s = "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")
    return s.upper().replace("Ñ", "X")

def _primera_vocal_interna(s):
    for c in s[1:]:
        if c in VOCALES:
            return c
    return "X"

def _primera_consonante_interna(s):
    for c in s[1:]:
        if c.isalpha() and c not in VOCALES:
            return c
    return "X"

_curps = set()
def gen_curp(nombre, ap_pat, ap_mat, fnac, genero):
    """CURP de 18 chars, formato RENAPO, homoclave forzada a única en memoria."""
    n, p, m = _norm(nombre.split()[0]), _norm(ap_pat), _norm(ap_mat or "X")
    base = (p[0] + _primera_vocal_interna(p) + (m[0] if m else "X") + n[0]
            + fnac.strftime("%y%m%d")
            + ("H" if genero == "M" else "M")          # género ADES: M=masculino,F=femenino
            + "MC"                                       # entidad: Estado de México
            + _primera_consonante_interna(p) + _primera_consonante_interna(m) + _primera_consonante_interna(n))
    alnum = string.digits + string.ascii_uppercase
    for a, b in itertools.product(alnum, repeat=2):
        cand = base + a + b
        if cand not in _curps:
            _curps.add(cand)
            return cand
    raise RuntimeError("homoclave agotada para " + base)

_personas_vistas = set()       # (n, ap, am, fnac) -> evita duplicado de persona
def nueva_persona(genero, fnac):
    """Devuelve (nombre, ap_pat, ap_mat) garantizando individuo distinto."""
    for _ in range(200):
        nombre = rng.choice(NOMBRES_H if genero == "M" else NOMBRES_M)
        ap_pat = rng.choice(APELLIDOS)
        ap_mat = rng.choice(APELLIDOS)
        key = (nombre, ap_pat, ap_mat, fnac)
        if key not in _personas_vistas:
            _personas_vistas.add(key)
            return nombre, ap_pat, ap_mat
    # fallback: añade segundo nombre
    nombre = rng.choice(NOMBRES_H if genero == "M" else NOMBRES_M) + " " + rng.choice(NOMBRES_H if genero == "M" else NOMBRES_M)
    return nombre, rng.choice(APELLIDOS), rng.choice(APELLIDOS)

# ══════════════════════════════════════════════════════════════════════════════
# FASE 1 — WIPE de datos operativos (preserva backbone + cuentas institucionales)
# ══════════════════════════════════════════════════════════════════════════════
PRESERVAR = {
    'ades_paises','ades_estados','ades_municipios','ades_localidades','ades_codigos_postales','ades_tipos_asentamiento',
    'ades_planteles','ades_escuelas','ades_informacion_escuela','ades_identidad_institucional','ades_niveles_educativos',
    'ades_plantel_niveles','ades_grados','ades_grupos','ades_aulas',
    'ades_materias','ades_materias_plan','ades_temas','ades_areas_academicas',
    'ades_escalas_evaluacion','ades_esquemas_ponderacion','ades_items_ponderacion','ades_periodos_evaluacion',
    'ades_periodos_inscripcion','ades_calendario_escolar','ades_calendarios_academicos','ades_ciclos_escolares',
    'ades_roles','ades_privilegios','ades_rol_privilegios','ades_menus','ades_menu_roles',
    'ades_catalogos','ades_catalogo_items','ades_estatus','ades_documentos_tipo','ades_niveles_ingles',
    'ades_lenguas_indigenas','ades_normatividad','ades_parametros_sistema','ades_variables_sistema',
    'ades_audit_log','ades_webhooks','ades_webhook_logs','ades_llaves_firma','ades_encryption_audit',
    'ades_pii_encryption_backup_20260619','ades_documentos_admision','ades_badges','ades_h5p_tipos',
    'ades_rubricas','ades_rubrica_criterios',   # se regeneran abajo pero sin FK conflictiva
}
ESPECIALES = {'ades_usuarios','ades_usuario_roles','ades_personas'}

print("── FASE 1: WIPE operativo ───────────────────────────")
cur.execute("""
    SELECT table_name FROM information_schema.tables
    WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name LIKE 'ades_%'
      AND table_name !~ '(_ciclo_[0-9]|_default$)'
""")
todas = {r[0] for r in cur.fetchall()}
operativas = sorted(todas - PRESERVAR - ESPECIALES)

cur.execute("SET session_replication_role = replica;")   # desactiva FK/audit triggers para el borrado
for t in operativas:
    cur.execute(f"DELETE FROM {t};")
# parciales: conservar cuentas institucionales y sus personas
cur.execute("DELETE FROM ades_usuarios WHERE email_institucional NOT ILIKE '%@institutonevadi.edu.mx';")
cur.execute("DELETE FROM ades_usuario_roles WHERE usuario_id NOT IN (SELECT id FROM ades_usuarios);")
cur.execute("DELETE FROM ades_personas WHERE id NOT IN (SELECT persona_id FROM ades_usuarios WHERE persona_id IS NOT NULL);")
cur.execute("SET session_replication_role = DEFAULT;")

# restaurar integridad de FKs colgantes en tablas preservadas
cur.execute("UPDATE ades_grupos SET profesor_titular_id = NULL;")
cur.execute("UPDATE ades_esquemas_ponderacion SET creado_por = NULL WHERE creado_por NOT IN (SELECT id FROM ades_usuarios);")
print(f"   {len(operativas)} tablas operativas vaciadas; backbone + cuentas institucionales preservados")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 2 — Periodos SEP -> 3 trimestres NEM  (espejo de migración 086)
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 2: Periodos SEP -> trimestres NEM ───────────")
cur.execute("""
    DELETE FROM ades_periodos_evaluacion pe USING ades_ciclos_escolares ce
     WHERE pe.ciclo_escolar_id = ce.id AND ce.sistema_educativo = 'SEP';
""")
cur.execute("""
    INSERT INTO ades_periodos_evaluacion
        (id, nombre_periodo, numero_periodo, tipo_periodo, ciclo_escolar_id,
         fecha_inicio, fecha_fin, fecha_entrega_boletas, usuario_creacion, usuario_modificacion)
    SELECT gen_random_uuid(), t.nombre, t.num, 'ORDINARIO', ce.id,
           t.ini, t.fin, t.fin + INTERVAL '5 days', %s, %s
    FROM ades_ciclos_escolares ce
    CROSS JOIN LATERAL (SELECT ce.fecha_inicio AS ci, ce.fecha_fin AS cf, (ce.fecha_fin-ce.fecha_inicio) AS span) s
    CROSS JOIN LATERAL (VALUES
        (1,'1er Trimestre', s.ci,                    s.ci + (s.span/3)),
        (2,'2do Trimestre', s.ci + (s.span/3) + 1,   s.ci + (2*s.span/3)),
        (3,'3er Trimestre', s.ci + (2*s.span/3) + 1, s.cf)
    ) AS t(num,nombre,ini,fin)
    WHERE ce.sistema_educativo='SEP';
""", (SEED_TAG, SEED_TAG))
print("   3 trimestres NEM por ciclo SEP")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 3 — Cargar contexto preservado
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 3: Cargar contexto ──────────────────────────")
cur.execute("SELECT id FROM ades_estatus WHERE nombre_estatus='ACTIVO' LIMIT 1;")
row = cur.fetchone(); ESTATUS_ACTIVO = row[0] if row else None

cur.execute("SELECT nombre_rol, id FROM ades_roles;")
ROLES = {r[0]: r[1] for r in cur.fetchall()}

cur.execute("SELECT id, nombre_plantel FROM ades_planteles;")
PLANTELES = cur.fetchall()
def cod_plantel(nombre): return _norm(nombre)[:2]

cur.execute("SELECT id, nombre_nivel, escala_maxima FROM ades_niveles_educativos;")
NIVELES = {r[0]: (r[1], float(r[2])) for r in cur.fetchall()}
def cod_nivel(nm): return {'PRIMARIA':'PR','SECUNDARIA':'SE','PREPARATORIA':'PA'}.get(nm,'XX')

# ítems de ponderación por nivel (tipos != asistencia se materializan como tareas/exámenes)
cur.execute("""
    SELECT ep.nivel_educativo_id, ip.tipo_item
    FROM ades_items_ponderacion ip JOIN ades_esquemas_ponderacion ep ON ep.id=ip.esquema_id
    WHERE ip.is_active;
""")
ITEMS_NIVEL = {}
for niv, tipo in cur.fetchall():
    ITEMS_NIVEL.setdefault(niv, []).append(tipo)

cur.execute("""
    SELECT gr.id, gr.plantel_id, gr.nivel_educativo_id, gr.numero_grado
    FROM ades_grados gr;
""")
GRADOS = {r[0]: dict(plantel=r[1], nivel=r[2], numero=r[3]) for r in cur.fetchall()}

cur.execute("""
    SELECT g.id, g.grado_id, g.ciclo_escolar_id, g.nombre_grupo
    FROM ades_grupos g WHERE g.is_active;
""")
GRUPOS = cur.fetchall()

cur.execute("SELECT grado_id, materia_id FROM ades_materias_plan WHERE is_active;")
MATERIAS_POR_GRADO = {}
for gid, mid in cur.fetchall():
    MATERIAS_POR_GRADO.setdefault(gid, []).append(mid)

cur.execute("SELECT materia_id, grado_id, id, nombre_tema FROM ades_temas WHERE is_active ORDER BY orden NULLS LAST;")
TEMAS = {}
for mid, gid, tid, tnom in cur.fetchall():
    TEMAS.setdefault((mid, gid), []).append((tid, tnom))

cur.execute("""
    SELECT ciclo_escolar_id, id, fecha_inicio, fecha_fin
    FROM ades_periodos_evaluacion WHERE tipo_periodo='ORDINARIO' ORDER BY numero_periodo;
""")
PERIODOS_CICLO = {}
for cid, pid, fi, ff in cur.fetchall():
    PERIODOS_CICLO.setdefault(cid, []).append((pid, fi, ff))

cur.execute("SELECT id FROM ades_aulas WHERE is_active;")
AULAS = [r[0] for r in cur.fetchall()] or [None]

# pool de códigos postales del Estado de México (direcciones realistas)
cur.execute("""
    SELECT cp.id, cp.localidad_id
    FROM ades_codigos_postales cp JOIN ades_estados e ON e.id=cp.estado_id
    WHERE e.nombre_estado ILIKE 'M%xico' LIMIT 8000;
""")
CP_POOL = cur.fetchall()
if not CP_POOL:
    cur.execute("SELECT id, localidad_id FROM ades_codigos_postales LIMIT 8000;")
    CP_POOL = cur.fetchall()
print(f"   {len(GRUPOS)} grupos, {len(CP_POOL)} CPs en pool")

# ── helpers de inserción (audit_biu rellena ref/row_version/timestamps) ────────
def binsert(table, cols, rows, page=2000):
    if not rows: return
    tmpl = "(" + ",".join(["%s"]*len(cols)) + ")"
    sql = f"INSERT INTO {table} ({','.join(cols)}) VALUES %s"
    for i in range(0, len(rows), page):
        execute_values(cur, sql, rows[i:i+page], template=tmpl)

TIPO_VIA = ["Calle","Avenida","Privada","Calzada","Andador","Boulevard"]
def gen_direccion(persona_id):
    cp_id, loc_id = rng.choice(CP_POOL)
    return (uid(), f"{rng.choice(TIPO_VIA)} {rng.choice(APELLIDOS)} {rng.choice(['Norte','Sur','Oriente','Poniente',''])}".strip(),
            str(rng.randint(1, 350)), loc_id, cp_id, 'PERSONA', persona_id, 'PARTICULAR', True, SEED_TAG, SEED_TAG)

def gen_tel(): return f"7{rng.randint(10,29)}{rng.randint(1000000,9999999)}"

def fnac_para_grado(nivel_nombre, numero):
    base = {'PRIMARIA':6, 'SECUNDARIA':12, 'PREPARATORIA':15}.get(nivel_nombre, 6)
    edad = base + (numero - 1)
    anio = 2026 - edad
    return date(anio, rng.randint(1,12), rng.randint(1,28))

# ══════════════════════════════════════════════════════════════════════════════
# FASE 4 — Profesores y personal por plantel
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 4: Profesores y personal ────────────────────")
ESPECIALIDADES = ["Español","Matemáticas","Ciencias","Historia","Geografía","Inglés",
                  "Educación Física","Artes","Formación Cívica","Química","Física","Biología"]
NIVEL_ESTUDIOS = ["Licenciatura","Maestría","Normal Superior"]
profes_por_plantel = {}
emp_seq = itertools.count(1)
per_rows, prof_rows, usr_rows, dir_rows = [], [], [], []

for pl_id, pl_nom in PLANTELES:
    profes_por_plantel[pl_id] = []
    for _ in range(PROFES_POR_PLANTEL):
        genero = rng.choice("MF")
        fnac = date(rng.randint(1975, 1995), rng.randint(1,12), rng.randint(1,28))
        nom, ap, am = nueva_persona(genero, fnac)
        pid = uid()
        curp = gen_curp(nom, ap, am, fnac, genero)
        per_rows.append((pid, nom, ap, am, fnac, curp, genero, gen_tel(),
                         f"{_norm(nom)[0].lower()}{_norm(ap).lower()}{rng.randint(10,99)}@correo.mx", SEED_TAG, SEED_TAG))
        prof_id = uid(); ne = next(emp_seq)
        prof_rows.append((prof_id, f"EMP-{ne:05d}", pid, pl_id, ESTATUS_ACTIVO, 'BASE',
                          rng.choice(ESPECIALIDADES), rng.choice(NIVEL_ESTUDIOS), SEED_TAG, SEED_TAG))
        uname = f"{_norm(nom)[0].lower()}{_norm(ap)[:7].lower()}{ne}"
        usr_rows.append((uid(), pid, uname, f"{uname}@nevadi.edu.mx", ROLES['PROFESOR'], pl_id, 'HASH_OIDC', SEED_TAG, SEED_TAG))
        dir_rows.append(gen_direccion(pid))
        profes_por_plantel[pl_id].append(prof_id)

binsert('ades_personas', ['id','nombre','apellido_paterno','apellido_materno','fecha_nacimiento','curp','genero','telefono','email_personal','usuario_creacion','usuario_modificacion'], per_rows)
binsert('ades_profesores', ['id','numero_empleado','persona_id','plantel_id','estatus_id','tipo_contrato','especialidad','nivel_estudios','usuario_creacion','usuario_modificacion'], prof_rows)
binsert('ades_usuarios', ['id','persona_id','nombre_usuario','email_institucional','rol_id','plantel_id','clave_hash','usuario_creacion','usuario_modificacion'], usr_rows)
binsert('ades_direcciones', ['id','calle','numero_exterior','localidad_id','codigo_postal_id','entidad_tipo','entidad_id','tipo_direccion','es_principal','usuario_creacion','usuario_modificacion'], dir_rows)
print(f"   {len(prof_rows)} profesores en {len(PLANTELES)} planteles")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 5 — Asignaciones docentes + titulares de grupo
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 5: Asignaciones docentes ────────────────────")
asig_rows, titular_upd = [], []
ASIGNACIONES = []   # (asig_id, grupo_id, materia_id, profesor_id, ciclo_id, grado_id, nivel_id)
for grp_id, grado_id, ciclo_id, _nom in GRUPOS:
    g = GRADOS[grado_id]; pool = profes_por_plantel[g['plantel']]
    titular_upd.append((rng.choice(pool), grp_id))
    for mid in MATERIAS_POR_GRADO.get(grado_id, []):
        prof = rng.choice(pool)
        aid = uid()
        asig_rows.append((aid, grp_id, mid, prof, ciclo_id, SEED_TAG, SEED_TAG))
        ASIGNACIONES.append((aid, grp_id, mid, prof, ciclo_id, grado_id, g['nivel']))

binsert('ades_asignaciones_docentes', ['id','grupo_id','materia_id','profesor_id','ciclo_escolar_id','usuario_creacion','usuario_modificacion'], asig_rows)
execute_values(cur, "UPDATE ades_grupos AS g SET profesor_titular_id = d.prof FROM (VALUES %s) AS d(prof,gid) WHERE g.id = d.gid", titular_upd)
print(f"   {len(asig_rows)} asignaciones; {len(titular_upd)} titulares")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 6 — Alumnos + inscripciones + tutores + expedientes médicos
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 6: Alumnos, tutores, expedientes ────────────")
SANGRES = ['A+','A-','B+','B-','O+','O-','AB+','AB-']
SEGUROS = ['IMSS','ISSSTE','PRIVADO','NINGUNO']
ALERG   = [None,None,None,None,'Polen','Penicilina','Mariscos','Frutos secos']
mat_seq = itertools.count(1)
grupo_alumnos = {}

est_rows, insc_rows, exp_rows = [], [], []
per_a, usr_a, dir_a = [], [], []
tut_rows, per_t, usr_t, dir_t, contacto_rows = [], [], [], [], []

for grp_id, grado_id, ciclo_id, _nom in GRUPOS:
    g = GRADOS[grado_id]; nivel_nombre = NIVELES[g['nivel']][0]
    plc = cod_plantel(dict(PLANTELES)[g['plantel']]); nvc = cod_nivel(nivel_nombre)
    grupo_alumnos[grp_id] = []
    for _ in range(ALUMNOS_POR_GRUPO):
        genero = rng.choice("MF"); fnac = fnac_para_grado(nivel_nombre, g['numero'])
        nom, ap, am = nueva_persona(genero, fnac)
        pid = uid(); curp = gen_curp(nom, ap, am, fnac, genero)
        per_a.append((pid, nom, ap, am, fnac, curp, genero, gen_tel(), SEED_TAG, SEED_TAG))
        est_id = uid(); ns = next(mat_seq); matricula = f"MAT-{plc}{nvc}-{ns:05d}"
        est_rows.append((est_id, matricula, pid, g['plantel'], ESTATUS_ACTIVO, date(2026,8,24),
                         rng.choice(['REGULAR']), SEED_TAG, SEED_TAG))
        insc_rows.append((uid(), est_id, grp_id, ciclo_id, date(2026,8,24), ESTATUS_ACTIVO, SEED_TAG, SEED_TAG))
        uname = f"al{ns}"
        usr_a.append((uid(), pid, uname, f"{uname}@alumnos.nevadi.edu.mx", ROLES['ALUMNO'], g['plantel'], 'HASH_OIDC', SEED_TAG, SEED_TAG))
        dir_a.append(gen_direccion(pid))
        exp_rows.append((uid(), est_id, rng.choice(SANGRES), rng.choice(ALERG),
                         rng.choice(SEGUROS), rng.random() < .85, SEED_TAG, SEED_TAG))
        grupo_alumnos[grp_id].append(est_id)
        # 1-2 tutores
        for ti in range(rng.randint(1, 2)):
            tg = 'F' if ti == 0 else rng.choice("MF")
            tf = date(rng.randint(1975, 1992), rng.randint(1,12), rng.randint(1,28))
            tn, tap, tam = nueva_persona(tg, tf)
            tpid = uid(); tcurp = gen_curp(tn, tap, tam, tf, tg)
            tel = gen_tel()
            per_t.append((tpid, tn, tap, tam, tf, tcurp, tg, tel, SEED_TAG, SEED_TAG))
            rel = 'MADRE' if tg == 'F' and ti == 0 else ('PADRE' if tg == 'M' else 'TUTOR')
            tut_rows.append((uid(), est_id, tpid, rel, ti+1, True, ti == 0, ti == 0, 'COMPLETO', SEED_TAG, SEED_TAG))
            tuname = f"tutor.{matricula}.{ti+1}"
            usr_t.append((uid(), tpid, tuname, f"{tuname}@tutores.nevadi.edu.mx", ROLES['PADRE_FAMILIA'], g['plantel'], 'HASH_OIDC', SEED_TAG, SEED_TAG))
            dir_t.append(gen_direccion(tpid))
            if ti == 0:
                contacto_rows.append((uid(), est_id, tpid, rel, True, True, True,
                                      f"{tn} {tap} {tam}", tel, SEED_TAG, SEED_TAG))

binsert('ades_personas', ['id','nombre','apellido_paterno','apellido_materno','fecha_nacimiento','curp','genero','telefono','usuario_creacion','usuario_modificacion'], per_a)
binsert('ades_estudiantes', ['id','matricula','persona_id','plantel_id','estatus_id','fecha_ingreso','tipo_alumno','usuario_creacion','usuario_modificacion'], est_rows)
binsert('ades_inscripciones', ['id','estudiante_id','grupo_id','ciclo_escolar_id','fecha_inscripcion','estatus_id','usuario_creacion','usuario_modificacion'], insc_rows)
binsert('ades_usuarios', ['id','persona_id','nombre_usuario','email_institucional','rol_id','plantel_id','clave_hash','usuario_creacion','usuario_modificacion'], usr_a)
binsert('ades_direcciones', ['id','calle','numero_exterior','localidad_id','codigo_postal_id','entidad_tipo','entidad_id','tipo_direccion','es_principal','usuario_creacion','usuario_modificacion'], dir_a)
binsert('ades_expedientes_medicos', ['id','estudiante_id','tipo_sangre','alergias','seguro_medico_tipo','vacunas_al_dia','usuario_creacion','usuario_modificacion'], exp_rows)
binsert('ades_personas', ['id','nombre','apellido_paterno','apellido_materno','fecha_nacimiento','curp','genero','telefono','usuario_creacion','usuario_modificacion'], per_t)
binsert('ades_tutores_alumnos', ['id','alumno_id','persona_id','relacion','prioridad','puede_recoger','es_responsable_economico','es_contacto_emergencia','nivel_acceso_portal','usuario_creacion','usuario_modificacion'], tut_rows)
binsert('ades_usuarios', ['id','persona_id','nombre_usuario','email_institucional','rol_id','plantel_id','clave_hash','usuario_creacion','usuario_modificacion'], usr_t)
binsert('ades_direcciones', ['id','calle','numero_exterior','localidad_id','codigo_postal_id','entidad_tipo','entidad_id','tipo_direccion','es_principal','usuario_creacion','usuario_modificacion'], dir_t)
binsert('ades_contactos_familiares', ['id','estudiante_id','persona_id','parentesco','es_tutor_legal','es_contacto_emergencia','puede_recoger','nombre_completo','telefono_principal','usuario_creacion','usuario_modificacion'], contacto_rows)
print(f"   {len(est_rows)} alumnos; {len(tut_rows)} tutores; {len(exp_rows)} expedientes")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 7 — Planificación semanal + clases
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 7: Planificación + clases ───────────────────")
HORAS = ['07:00','08:00','09:00','10:00','11:00','12:00','13:00']
plan_rows, clase_rows = [], []
clase_index = []   # (clase_id, grupo_id)
for aid, grp_id, mid, prof, ciclo_id, grado_id, nivel_id in ASIGNACIONES:
    temas = TEMAS.get((mid, grado_id)) or TEMAS.get((mid, None)) or []
    periodos = PERIODOS_CICLO.get(ciclo_id, [])
    ti = 0
    for (pid, fi, ff) in periodos:
        # planificación: un tema por sesión planeada
        for k in range(CLASES_POR_PERIODO):
            fecha = fi + timedelta(days=int((ff - fi).days * (k + 0.5) / CLASES_POR_PERIODO))
            tema = temas[ti % len(temas)] if temas else None
            ti += 1
            if tema:
                plan_rows.append((uid(), grp_id, tema[0], fecha,
                                  f"Actividades de {tema[1]}", "Libro de texto, material didáctico", SEED_TAG, SEED_TAG))
            cid = uid(); hi = rng.choice(HORAS[:6])
            clase_rows.append((cid, grp_id, mid, prof, fecha, hi, HORAS[HORAS.index(hi)+1],
                               tema[1] if tema else 'Sesión', 'REALIZADA', True, SEED_TAG, SEED_TAG))
            clase_index.append((cid, grp_id))

binsert('ades_planeacion_clases', ['id','grupo_id','tema_id','fecha_planeada','descripcion_actividades','recursos_didacticos','usuario_creacion','usuario_modificacion'], plan_rows)
binsert('ades_clases', ['id','grupo_id','materia_id','profesor_id','fecha_clase','hora_inicio','hora_fin','tema_visto','estatus_clase','impartida','usuario_creacion','usuario_modificacion'], clase_rows)
print(f"   {len(plan_rows)} planeaciones; {len(clase_rows)} clases")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 8 — Asistencias (~95% presente)
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 8: Asistencias ──────────────────────────────")
ESTATUS_A = ['PRESENTE']*18 + ['AUSENTE','RETARDO']
asist_rows = []; total_asist = 0
for cid, grp_id in clase_index:
    for est_id in grupo_alumnos.get(grp_id, []):
        asist_rows.append((uid(), cid, est_id, rng.choice(ESTATUS_A), SEED_TAG, SEED_TAG))
    if len(asist_rows) >= 50000:
        binsert('ades_asistencias', ['id','clase_id','estudiante_id','estatus_asistencia','usuario_creacion','usuario_modificacion'], asist_rows)
        total_asist += len(asist_rows); asist_rows = []
binsert('ades_asistencias', ['id','clase_id','estudiante_id','estatus_asistencia','usuario_creacion','usuario_modificacion'], asist_rows)
total_asist += len(asist_rows)
print(f"   {total_asist} asistencias")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 9 — Tareas/proyectos + entregas, y exámenes + calificaciones de examen
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 9: Tareas, proyectos y exámenes ─────────────")
TITULOS = {'tarea':'Ejercicios','proyecto':'Proyecto integrador','participacion':'Participación','laboratorio':'Práctica','otro':'Actividad'}
tarea_rows, eval_rows = [], []
tarea_meta = []   # (tarea_id, grupo_id, puntaje)
eval_meta  = []   # (eval_id, grupo_id, puntaje)
for aid, grp_id, mid, prof, ciclo_id, grado_id, nivel_id in ASIGNACIONES:
    escala = NIVELES[nivel_id][1]
    tipos = ITEMS_NIVEL.get(nivel_id, ['tarea'])
    temas = TEMAS.get((mid, grado_id)) or []
    for (pid, fi, ff) in PERIODOS_CICLO.get(ciclo_id, []):
        # examen del periodo
        ev_id = uid()
        eval_rows.append((ev_id, f"Examen {('parcial' if escala==100 else 'trimestral')}", grp_id, mid, pid,
                          ff - timedelta(days=3), 'EXAMEN', escala, SEED_TAG, SEED_TAG))
        eval_meta.append((ev_id, grp_id, escala))
        # tareas por cada tipo_item != examen/asistencia
        for tipo in tipos:
            if tipo in ('examen','asistencia'): continue
            for k in range(TAREAS_POR_ITEM_PERIODO):
                tid = uid()
                fa = fi + timedelta(days=int((ff-fi).days * (k+1)/(TAREAS_POR_ITEM_PERIODO+1)))
                tema = temas[k % len(temas)] if temas else None
                tarea_rows.append((tid, f"{TITULOS.get(tipo,'Actividad')} {k+1}", "Actividad evaluable.",
                                   grp_id, mid, (tema[0] if tema else None), pid, fa, fa + timedelta(days=7),
                                   escala, 'MANUAL', tipo, SEED_TAG, SEED_TAG))
                tarea_meta.append((tid, grp_id, escala))

binsert('ades_evaluaciones', ['id','nombre_evaluacion','grupo_id','materia_id','periodo_evaluacion_id','fecha_evaluacion','tipo_evaluacion','puntaje_maximo','usuario_creacion','usuario_modificacion'], eval_rows)
binsert('ades_tareas', ['id','titulo','descripcion','grupo_id','materia_id','tema_id','periodo_evaluacion_id','fecha_asignacion','fecha_entrega','puntaje_maximo','origen','tipo_item','usuario_creacion','usuario_modificacion'], tarea_rows)

# calificaciones de examen (por alumno)
cal_ev_rows = []
for ev_id, grp_id, escala in eval_meta:
    aprob = escala * 0.6
    for est_id in grupo_alumnos.get(grp_id, []):
        nota = round(min(escala, max(escala*0.4, rng.gauss(escala*0.78, escala*0.12))), 1)
        cal_ev_rows.append((uid(), ev_id, est_id, nota, None, SEED_TAG, SEED_TAG))
    if len(cal_ev_rows) >= 50000:
        binsert('ades_calificaciones_evaluaciones', ['id','evaluacion_id','estudiante_id','calificacion','comentarios','usuario_creacion','usuario_modificacion'], cal_ev_rows); cal_ev_rows=[]
binsert('ades_calificaciones_evaluaciones', ['id','evaluacion_id','estudiante_id','calificacion','comentarios','usuario_creacion','usuario_modificacion'], cal_ev_rows)

# entregas de tareas (~80%)
ent_rows = []; total_ent = 0
for tid, grp_id, escala in tarea_meta:
    for est_id in grupo_alumnos.get(grp_id, []):
        if rng.random() > 0.82: continue
        nota = round(min(escala, max(0, rng.gauss(escala*0.82, escala*0.12))), 1)
        ent_rows.append((uid(), tid, est_id, datetime.now(), nota, 'CALIFICADA', SEED_TAG, SEED_TAG))
    if len(ent_rows) >= 50000:
        binsert('ades_tareas_entregas', ['id','tarea_id','estudiante_id','fecha_entrega','calificacion_obtenida','estatus_entrega','usuario_creacion','usuario_modificacion'], ent_rows)
        total_ent += len(ent_rows); ent_rows=[]
binsert('ades_tareas_entregas', ['id','tarea_id','estudiante_id','fecha_entrega','calificacion_obtenida','estatus_entrega','usuario_creacion','usuario_modificacion'], ent_rows)
total_ent += len(ent_rows)
print(f"   {len(eval_rows)} exámenes; {len(tarea_rows)} tareas; {len(cal_ev_rows)} calif-examen (último lote); {total_ent} entregas")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 10 — Reportes de conducta (~5% de alumnos)
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 10: Conducta ────────────────────────────────")
TIPOS_F = ['FALTA_UNIFORME','DISPOSITIVO_MOVIL','CONDUCTA_INAPROPIADA','RETRASO_REITERADO']
MEDIDAS = ['Amonestación verbal','Reporte escrito','Citatorio a padres']
cond_rows = []
for grp_id, alumnos in grupo_alumnos.items():
    g = GRADOS[[gg[1] for gg in GRUPOS if gg[0]==grp_id][0]]
    prof = rng.choice(profes_por_plantel[g['plantel']])
    for est_id in alumnos:
        if rng.random() < 0.05:
            cond_rows.append((uid(), est_id, grp_id, prof, date.today()-timedelta(days=rng.randint(1,90)),
                              rng.choice(TIPOS_F), 'Incidente registrado por el personal docente.',
                              rng.choice(MEDIDAS), rng.random()<.3, ESTATUS_ACTIVO, SEED_TAG, SEED_TAG))
binsert('ades_reportes_conducta', ['id','estudiante_id','grupo_id','reportado_por_id','fecha_reporte','tipo_falta','descripcion','medida_aplicada','requiere_seguimiento','estatus_id','usuario_creacion','usuario_modificacion'], cond_rows)
print(f"   {len(cond_rows)} reportes de conducta")

conn.commit()

# ══════════════════════════════════════════════════════════════════════════════
# FASE 11 — Calificaciones por periodo (función oficial, server-side)
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 11: Calificaciones por periodo ──────────────")
cur.execute("""
    INSERT INTO ades_calificaciones_periodo
        (id, estudiante_id, grupo_id, materia_id, periodo_evaluacion_id,
         calificacion_calculada, calificacion_final, es_acreditado, fecha_calculo,
         usuario_creacion, usuario_modificacion)
    SELECT gen_random_uuid(), i.estudiante_id, g.id, ad.materia_id, pe.id,
           c.val, c.val, (c.val >= ne.escala_maxima*0.6), NOW(), %s, %s
    FROM ades_asignaciones_docentes ad
    JOIN ades_grupos g            ON g.id = ad.grupo_id
    JOIN ades_grados gr           ON gr.id = g.grado_id
    JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
    JOIN ades_inscripciones i     ON i.grupo_id = g.id AND i.is_active
    JOIN ades_periodos_evaluacion pe ON pe.ciclo_escolar_id = ad.ciclo_escolar_id AND pe.tipo_periodo='ORDINARIO'
    CROSS JOIN LATERAL (SELECT calcular_calificacion_periodo(i.estudiante_id, g.id, ad.materia_id, pe.id) AS val) c
    WHERE c.val IS NOT NULL;
""", (SEED_TAG, SEED_TAG))
print(f"   {cur.rowcount} calificaciones de periodo")
conn.commit()

# ══════════════════════════════════════════════════════════════════════════════
# FASE 12 — Refrescar vistas materializadas (estadísticos)
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 12: Refrescar vistas materializadas ─────────")
cur.execute("SELECT matviewname FROM pg_matviews WHERE schemaname='public';")
for (mv,) in cur.fetchall():
    try:
        cur.execute(f"REFRESH MATERIALIZED VIEW {mv};"); conn.commit()
    except Exception as e:
        conn.rollback(); print(f"   (omitida {mv}: {str(e)[:60]})")
print("   vistas refrescadas")

cur.close(); conn.close()
print("\n" + "="*55 + "\n  ✓  Seed 004 completado\n" + "="*55)

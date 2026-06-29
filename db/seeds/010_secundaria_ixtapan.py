import os, random, uuid, subprocess, sys
from datetime import date, datetime, timedelta

SEED_TAG = "seed010"
PSQL = ["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "ades_admin", "-d", "ades"]

def q(sql):
    r = subprocess.run(
        PSQL + ["-tAF", "\t", "-v", "ON_ERROR_STOP=1", "-c", sql],
        capture_output=True, text=True, cwd="/opt/ades"
    )
    if r.returncode != 0:
        sys.exit("READ ERROR:\n" + r.stderr)
    return [tuple(c if c != "" else None for c in ln.split("\t"))
            for ln in r.stdout.splitlines() if ln != ""]

def exec_sql_text(s):
    r = subprocess.run(
        PSQL + ["-v", "ON_ERROR_STOP=1"],
        input=s, capture_output=True, text=True, cwd="/opt/ades"
    )
    if r.returncode != 0:
        sys.exit("EXEC ERROR:\n" + r.stderr[-4000:])

def uid():
    return str(uuid.uuid4())

def lit(v):
    if v is None: return "NULL"
    if isinstance(v, bool): return "TRUE" if v else "FALSE"
    if isinstance(v, (int, float)): return repr(v)
    return "'" + str(v).replace("'", "''") + "'"

# 1. Truncate test data (Corridas, rules, lecciones)
print("── Limpiando tablas de Horarios ─────────────────────────")
exec_sql_text("""
BEGIN;
SET session_replication_role=replica;
DELETE FROM ades_horarios;
DELETE FROM ades_horario_regla;
DELETE FROM ades_horario_corrida;
DELETE FROM ades_disponibilidad_docente;
DELETE FROM ades_asignaciones_docentes WHERE usuario_creacion='seed010';
DELETE FROM ades_materias_plan WHERE usuario_creacion='seed010';
DELETE FROM ades_materias WHERE usuario_creacion='seed010';
DELETE FROM ades_profesores WHERE usuario_creacion='seed010';
DELETE FROM ades_usuarios WHERE usuario_creacion='seed010';
DELETE FROM ades_personas WHERE usuario_creacion='seed010';
SET session_replication_role=DEFAULT;
COMMIT;
""")

print("── Obteniendo IDs base ─────────────────────────────────")
plantel = q("SELECT id FROM ades_planteles WHERE nombre_plantel='Ixtapan de la Sal' LIMIT 1")
if not plantel:
    sys.exit("Plantel Ixtapan de la Sal no encontrado")
plantel_id = plantel[0][0]

nivel = q("SELECT id FROM ades_niveles_educativos WHERE nombre_nivel ILIKE '%SECUNDARIA%' LIMIT 1")
nivel_id = nivel[0][0] if nivel else plantel_id # fallback

ciclo = q("SELECT id FROM ades_ciclos_escolares WHERE es_vigente=TRUE LIMIT 1")
if not ciclo:
    sys.exit("No hay ciclo escolar vigente")
ciclo_id = ciclo[0][0]

rol = q("SELECT id FROM ades_roles WHERE nombre_rol='DOCENTE' LIMIT 1")
if not rol:
    sys.exit("Rol DOCENTE no encontrado")
rol_id = rol[0][0]

# Grados 1, 2, 3
grados = q(f"SELECT id, numero_grado FROM ades_grados WHERE nivel_educativo_id='{nivel_id}'")
grado_map = {int(g[1]): g[0] for g in grados}
if not grado_map:
    sys.exit("Grados no encontrados")

# Crear Grupos A y B para cada grado
grupos_insert = []
grupos_map = {}
for g_num in [1, 2, 3]:
    if g_num not in grado_map: continue
    for letra in ['A', 'B']:
        nombre = f"{g_num}° {letra}"
        g_exist = q(f"SELECT id FROM ades_grupos WHERE grado_id='{grado_map[g_num]}' AND ciclo_escolar_id='{ciclo_id}' AND nombre_grupo='{letra}'")
        if g_exist:
            grupos_map[nombre] = g_exist[0][0]
        else:
            g_id = uid()
            grupos_insert.append(f"('{g_id}','{grado_map[g_num]}','{ciclo_id}','{letra}',35,TRUE,'{SEED_TAG}')")
            grupos_map[nombre] = g_id

if grupos_insert:
    exec_sql_text(f"""
    INSERT INTO ades_grupos (id, grado_id, ciclo_escolar_id, nombre_grupo, capacidad_maxima, is_active, usuario_creacion)
    VALUES {",".join(grupos_insert)} ON CONFLICT DO NOTHING;
    """)

# Docentes a crear
docentes_data = [
    ("Betzaida García Rodríguez", "ESP"),
    ("Areli López Muñoz", "ING_I_II"),
    ("Raúl Ávila Vázquez", "ING_III"),
    ("Óscar Ramiro Andrade Celis", "MAT_II_III"),
    ("Rubén López Solano", "FIS_MAT_I"),
    ("Ana Laura Nieto Landeros", "BIO_QUI"),
    ("Wendy Melina Arriaga Garduño", "HIS"),
    ("Ana Elena García Bustos", "GEO_EDU_AMB"),
    ("Francisco Javier Garduño Reyes", "ART"),
    ("Emma Laura Silva Olmos", "SOCIO_IGUALDAD"),
    ("Carlos Joel Popoca Villegas", "ED_FIS"),
    ("Yesenia Hernández Guadarrama", "TEC_MAKER"),
    ("Micaela Ayala Rogel", "TUT_1_EDU_FIN"),
    ("Miriam Johana Mejía Suárez", "TUT_2_EDU_FIN"),
    ("Aldo Benjamín Díaz García", "TUT_3_EDU_FIN"),
    ("VACANTE CÍVICA Y TLEC", "VACANTE")
]

profesores_map = {}
for nombre_completo, cod in docentes_data:
    partes = nombre_completo.split(" ")
    nombres = partes[0]
    paterno = partes[1] if len(partes) > 1 else ""
    materno = " ".join(partes[2:]) if len(partes) > 2 else ""
    persona_id = uid()
    usuario_id = uid()
    prof_id = uid()
    usr = f"{nombres.lower()}_{str(random.randint(1000, 9999))}"
    exec_sql_text(f"""
    INSERT INTO ades_personas (id, nombre, apellido_paterno, apellido_materno, usuario_creacion) VALUES ('{persona_id}', '{nombres}', '{paterno}', '{materno}', '{SEED_TAG}');
    INSERT INTO ades_usuarios (id, nombre_usuario, persona_id, rol_id, email_institucional, clave_hash, usuario_creacion) VALUES ('{usuario_id}', '{usr}', '{persona_id}', '{rol_id}', '{usr}@nevadi.edu.mx', 'hash', '{SEED_TAG}');
    INSERT INTO ades_profesores (id, persona_id, plantel_id, is_active, usuario_creacion) VALUES ('{prof_id}', '{persona_id}', '{plantel_id}', TRUE, '{SEED_TAG}');
    """)
    profesores_map[nombre_completo] = prof_id

# Materias y Asignaciones (MateriasPlan y Profesores_Materias)
materias_data = {
    1: [
        ("LENGUA MATERNA (ESPAÑOL)", 5, "Betzaida García Rodríguez"),
        ("LENGUA EXTRANJERA (INGLÉS) I", 5, "Areli López Muñoz"),
        ("MATEMÁTICAS I", 5, "Rubén López Solano"),
        ("CIENCIAS (BIOLOGÍA)", 4, "Ana Laura Nieto Landeros"),
        ("GEOGRAFÍA", 4, "Ana Elena García Bustos"),
        ("HISTORIA", 2, "Wendy Melina Arriaga Garduño"),
        ("EDU. AMBIENTAL", 2, "Ana Elena García Bustos"),
        ("FORMACIÓN CÍVICA Y ÉTICA", 2, "VACANTE CÍVICA Y TLEC"),
        ("TLEC", 1, "VACANTE CÍVICA Y TLEC"),
        ("ARTES", 3, "Francisco Javier Garduño Reyes"),
        ("EDUCACIÓN SOCIOEMOCIONAL", 2, "Emma Laura Silva Olmos"), # 1.5 rounded to 2 blocks
        ("IGUALDAD DE GÉNERO", 1, "Emma Laura Silva Olmos"),
        ("EDUCACIÓN FÍSICA", 3, "Carlos Joel Popoca Villegas"),
        ("TECNOLOGÍA", 2, "Yesenia Hernández Guadarrama"),
        ("MAKER", 2, "Yesenia Hernández Guadarrama"),
        ("TUTORÍA", 1, "Micaela Ayala Rogel"),
        ("EDU. FINANCIERA", 1, "Micaela Ayala Rogel"),
        ("PROYECTOS", 1, None) # Coordinación
    ],
    2: [
        ("LENGUA MATERNA (ESPAÑOL)", 5, "Betzaida García Rodríguez"),
        ("LENGUA EXTRANJERA (INGLÉS) II", 5, "Areli López Muñoz"),
        ("MATEMÁTICAS II", 5, "Óscar Ramiro Andrade Celis"),
        ("CIENCIAS (FÍSICA)", 6, "Rubén López Solano"),
        ("HISTORIA", 4, "Wendy Melina Arriaga Garduño"),
        ("EDU. AMBIENTAL", 2, "Ana Elena García Bustos"),
        ("FORMACIÓN CÍVICA Y ÉTICA", 2, "VACANTE CÍVICA Y TLEC"),
        ("TLEC", 1, "VACANTE CÍVICA Y TLEC"),
        ("ARTES", 3, "Francisco Javier Garduño Reyes"),
        ("EDUCACIÓN SOCIOEMOCIONAL", 2, "Emma Laura Silva Olmos"),
        ("IGUALDAD DE GÉNERO", 1, "Emma Laura Silva Olmos"),
        ("EDUCACIÓN FÍSICA", 3, "Carlos Joel Popoca Villegas"),
        ("TECNOLOGÍA", 2, "Yesenia Hernández Guadarrama"),
        ("MAKER", 2, "Yesenia Hernández Guadarrama"),
        ("TUTORÍA", 1, "Miriam Johana Mejía Suárez"),
        ("EDU. FINANCIERA", 1, "Miriam Johana Mejía Suárez"),
        ("PROYECTOS", 1, None)
    ],
    3: [
        ("LENGUA MATERNA (ESPAÑOL)", 5, "Betzaida García Rodríguez"),
        ("LENGUA EXTRANJERA (INGLÉS) III", 5, "Raúl Ávila Vázquez"),
        ("MATEMÁTICAS III", 5, "Óscar Ramiro Andrade Celis"),
        ("CIENCIAS (QUÍMICA)", 6, "Ana Laura Nieto Landeros"),
        ("HISTORIA", 4, "Wendy Melina Arriaga Garduño"),
        ("EDU. AMBIENTAL", 2, "Ana Elena García Bustos"),
        ("FORMACIÓN CÍVICA Y ÉTICA", 2, "VACANTE CÍVICA Y TLEC"),
        ("TLEC", 1, "VACANTE CÍVICA Y TLEC"),
        ("ARTES", 3, "Francisco Javier Garduño Reyes"),
        ("EDUCACIÓN SOCIOEMOCIONAL", 2, "Emma Laura Silva Olmos"),
        ("IGUALDAD DE GÉNERO", 1, "Emma Laura Silva Olmos"),
        ("EDUCACIÓN FÍSICA", 3, "Carlos Joel Popoca Villegas"),
        ("TECNOLOGÍA", 2, "Yesenia Hernández Guadarrama"),
        ("MAKER", 2, "Yesenia Hernández Guadarrama"),
        ("TUTORÍA", 1, "Aldo Benjamín Díaz García"),
        ("EDU. FINANCIERA", 1, "Aldo Benjamín Díaz García"),
        ("PROYECTOS", 1, None)
    ]
}

print("── Insertando Materias y MateriasPlan ───────────────────")
for grado, mats in materias_data.items():
    grado_id = grado_map.get(grado)
    if not grado_id: continue
    for nombre_materia, horas, docente in mats:
        mat = q(f"SELECT id FROM ades_materias WHERE nombre_materia='{nombre_materia}' LIMIT 1")
        if not mat:
            mat_id = uid()
            exec_sql_text(f"INSERT INTO ades_materias (id, nombre_materia, tipo_materia, nivel_educativo_id, usuario_creacion) VALUES ('{mat_id}', '{nombre_materia}', 'OFICIAL_SEP_SECUNDARIA', '{nivel_id}', '{SEED_TAG}')")
        else:
            mat_id = mat[0][0]
            
        mat_plan = q(f"SELECT id FROM ades_materias_plan WHERE materia_id='{mat_id}' AND grado_id='{grado_id}'")
        if not mat_plan:
            mat_plan_id = uid()
            exec_sql_text(f"INSERT INTO ades_materias_plan (id, materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria, usuario_creacion) VALUES ('{mat_plan_id}', '{mat_id}', '{grado_id}', '{ciclo_id}', {horas}, TRUE, '{SEED_TAG}')")
        
        # Asignar a grupos A y B
        prof_id = profesores_map.get(docente) if docente else 'NULL'
        for letra in ['A', 'B']:
            grupo_id = grupos_map.get(f"{grado}° {letra}")
            if grupo_id and prof_id != 'NULL':
                exec_sql_text(f"""
                INSERT INTO ades_asignaciones_docentes (id, profesor_id, materia_id, grupo_id, ciclo_escolar_id, usuario_creacion)
                VALUES ('{uid()}', '{prof_id}', '{mat_id}', '{grupo_id}', '{ciclo_id}', '{SEED_TAG}')
                ON CONFLICT DO NOTHING;
                """)

# Insertar reglas de disponibilidad (Constraint Provider usa la tabla ades_horario_regla)
print("── Insertando Reglas Dinámicas ─────────────────────────")
reglas = []

def add_regla(tipo, params_json, desc):
    reglas.append(f"('{uid()}', '{plantel_id}', '{ciclo_id}', '{nivel_id}', '{tipo}', TRUE, 10, TRUE, '{params_json}', '{desc}', TRUE, '{SEED_TAG}')")

# Areli: Solo primeras horas (antes de las 12:00)
prof_areli = profesores_map["Areli López Muñoz"]
add_regla("ventana_horaria_docente", f'{{"profesor_id": "{prof_areli}", "modo": "antes_de", "hora": "12:00:00"}}', "Areli solo antes de 12:00")

# Oscar: Solo primeras horas (antes de las 11:00 p.ej.)
prof_oscar = profesores_map["Óscar Ramiro Andrade Celis"]
add_regla("ventana_horaria_docente", f'{{"profesor_id": "{prof_oscar}", "modo": "antes_de", "hora": "11:00:00"}}', "Oscar solo primeras horas")

# Ruben: No trabaja lunes, viernes sale a las 10
prof_ruben = profesores_map["Rubén López Solano"]
add_regla("dias_no_permitidos_docente", f'{{"profesor_id": "{prof_ruben}", "dias": [1]}}', "Ruben no labora lunes")
add_regla("ventana_horaria_docente", f'{{"profesor_id": "{prof_ruben}", "dia": 5, "modo": "antes_de", "hora": "10:00:00"}}', "Ruben viernes solo antes de 10:00")

# Carlos Joel: Lunes entra despues de 2da hora (despues de 9:40, aprox 10:30)
prof_carlos = profesores_map["Carlos Joel Popoca Villegas"]
add_regla("ventana_horaria_docente", f'{{"profesor_id": "{prof_carlos}", "dia": 1, "modo": "despues_de", "hora": "10:00:00"}}', "Carlos lunes entra despues de 10:00")

# Proyectos al final del día (después de las 13:00)
add_regla("ventana_horaria", '{"materia": "PROYECTOS", "modo": "despues_de", "hora": "13:00:00"}', "Proyectos al final del dia")

# Educación Socioemocional fraccionada en 50 y 30 min
add_regla("materia_fraccionada_30min", '{"materia": "EDUCACIÓN SOCIOEMOCIONAL"}', "Socioemocional dividida en 50 y 30 minutos")

if reglas:
    exec_sql_text(f"""
    INSERT INTO ades_horario_regla (id, plantel_id, ciclo_escolar_id, nivel_educativo_id, tipo, dura, peso, activa, params, descripcion, is_active, usuario_creacion)
    VALUES {",".join(reglas)};
    """)

print("── Insertando Franjas Horarias Globales ─────────────────")
exec_sql_text(f"DELETE FROM ades_horario_franjas WHERE usuario_creacion='{SEED_TAG}'")

franjas_normal = [
    ('07:00:00', '07:50:00'), ('07:50:00', '08:40:00'), ('08:40:00', '09:30:00'),
    ('10:00:00', '10:50:00'), ('10:50:00', '11:40:00'), ('11:40:00', '12:30:00'),
    ('12:30:00', '13:20:00'), ('13:50:00', '14:40:00'), ('14:40:00', '15:30:00'),
    ('15:30:00', '16:00:00')
]
franjas_viernes = [
    ('07:00:00', '07:50:00'), ('07:50:00', '08:40:00'), ('08:40:00', '09:30:00'),
    ('10:00:00', '10:50:00'), ('10:50:00', '11:40:00'), ('11:40:00', '12:30:00'),
    ('12:30:00', '13:20:00'), ('13:20:00', '14:00:00')
]

franjas_sql = []
for dia in [1, 2, 3, 4]:
    for ini, fin in franjas_normal:
        franjas_sql.append(f"('{uid()}', NULL, '{ciclo_id}', '{nivel_id}', {dia}, '{ini}', '{fin}', 'MATUTINO', '{SEED_TAG}')")
for ini, fin in franjas_viernes:
    franjas_sql.append(f"('{uid()}', NULL, '{ciclo_id}', '{nivel_id}', 5, '{ini}', '{fin}', 'MATUTINO', '{SEED_TAG}')")

exec_sql_text(f"""
INSERT INTO ades_horario_franjas (id, plantel_id, ciclo_escolar_id, nivel_educativo_id, dia_semana, hora_inicio, hora_fin, turno, usuario_creacion)
VALUES {",".join(franjas_sql)};
""")

print("✓ Secundaria Ixtapan de la Sal - Datos de Horarios Creados")

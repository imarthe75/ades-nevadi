"""
Seed 004 — Simulación INTEGRAL del ciclo escolar ADES (datos realistas, sin duplicados).

Emula el ciclo escolar COMPLETO de extremo a extremo (ejercicio finalizado):
  preinscripción -> inscripción -> personal (docente/administrativo/salud)
  -> planificación semanal -> clases -> asistencias (+ justificaciones)
  -> tareas/proyectos -> exámenes -> calificaciones (numérica + cualitativa NEM)
  -> conducta (+ sanciones) -> expedientes médicos (+ crónicas/medicamentos/incidentes)
  -> evaluación docente 360° -> comunicación -> reinscripción -> estadísticos.

PRESERVA: planteles, currícula oficial (materias_plan, temas), grados, grupos, ciclos,
ponderación, escalas (incl. cualitativa NEM 089), catálogos (geo, ocupaciones 088),
menús/permisos (087/090), ades_config (089) y cuentas institucionales @institutonevadi.edu.mx.

CONEXIÓN: corre en el HOST (solo stdlib) y opera la BD por el socket local de confianza
(`docker compose exec -T postgres psql -U ades_admin`); no usa contraseñas ni psycopg2.

Ejecutar:  python3 db/seeds/004_simulacion_integral.py
"""
import os, random, uuid, unicodedata, itertools, string, subprocess, tempfile, sys
from datetime import date, datetime, timedelta

SEED_TAG = "seed004"
rng = random.Random(2026)
ALUMNOS_POR_GRUPO=26; DOCENTES_POR_PLANTEL=24; CLASES_POR_PERIODO=4
TAREAS_POR_ITEM_PERIODO=2; SOLICITUDES_ADMISION=220

PSQL = ["docker","compose","exec","-T","postgres","psql","-U","ades_admin","-d","ades"]

def q(sql):
    """Lee filas (tab-separadas) por el socket de confianza."""
    r = subprocess.run(PSQL+["-tAF","\t","-v","ON_ERROR_STOP=1","-c",sql],
                       capture_output=True, text=True, cwd="/opt/ades")
    if r.returncode != 0:
        sys.exit("READ ERROR:\n"+r.stderr)
    out=[]
    for line in r.stdout.splitlines():
        if line=="" : continue
        out.append(tuple(c if c!="" else None for c in line.split("\t")))
    return out

def exec_sql_text(sql):
    r = subprocess.run(PSQL+["-v","ON_ERROR_STOP=1"], input=sql, capture_output=True, text=True, cwd="/opt/ades")
    if r.returncode != 0:
        sys.exit("EXEC ERROR:\n"+r.stderr[-3000:])
    return r.stdout

def exec_sql_file(path):
    with open(path) as fh:
        r = subprocess.run(PSQL+["-v","ON_ERROR_STOP=1","-q"], stdin=fh, capture_output=True, text=True, cwd="/opt/ades")
    if r.returncode != 0:
        sys.exit("EXEC FILE ERROR:\n"+r.stderr[-3000:])
    return r.stdout

def uid(): return str(uuid.uuid4())
def lit(v):
    if v is None: return "NULL"
    if isinstance(v,bool): return "TRUE" if v else "FALSE"
    if isinstance(v,(int,float)): return repr(v)
    if isinstance(v,(date,datetime)): return "'"+v.isoformat()+"'"
    return "'"+str(v).replace("'","''")+"'"

# buffer de SQL de generación
F = open(tempfile.gettempdir()+"/seed004_gen.sql","w")
F.write("BEGIN;\n")
# Desactivar triggers de recálculo de gradebook durante la carga masiva
# (invocan calcular_calificacion_periodo, cuyo ON CONFLICT no aplica a la tabla
#  particionada). Las calificaciones se calculan explícitamente al final.
F.write("ALTER TABLE ades_asistencias DISABLE TRIGGER trg_gradebook_asistencia;\n")
F.write("ALTER TABLE ades_calificaciones_evaluaciones DISABLE TRIGGER trg_gradebook_examen;\n")
F.write("ALTER TABLE ades_tareas_entregas DISABLE TRIGGER trg_gradebook_entrega;\n")
def binsert(table, cols, rows, page=1000):
    if not rows: return
    head=f"INSERT INTO {table} ({','.join(cols)}) VALUES\n"
    for i in range(0,len(rows),page):
        chunk=rows[i:i+page]
        F.write(head + ",\n".join("("+",".join(lit(x) for x in r)+")" for r in chunk) + ";\n")

# ── catálogos de nombres mexicanos (amplios) ──────────────────────────────────
NOMBRES_H = ["José","Juan","Luis","Carlos","Miguel","Ángel","Fernando","Jorge","Eduardo","Roberto",
 "Ricardo","Alejandro","Francisco","Daniel","Diego","Emiliano","Santiago","Mateo","Sebastián","Leonardo",
 "Adrián","David","Iván","Héctor","Raúl","Óscar","Pablo","Andrés","Gerardo","Rodrigo","Arturo","Manuel",
 "Antonio","Javier","Marco","Gabriel","Rafael","Alberto","Enrique","Sergio","Víctor","Hugo","Joaquín",
 "Ramón","Ernesto","Salvador","Felipe","Guillermo","Mauricio","Ignacio","Gustavo","Rubén","Alfonso",
 "Octavio","Lorenzo","Tadeo","Maximiliano","Bruno","Axel","Damián","Isaac","Saúl","Néstor","Aarón",
 "Cristóbal","Esteban","Patricio","Benjamín","Matías","Nicolás","Gael","Dylan","Iker","Thiago","Régulo",
 "Abel","Camilo","Cristian","Efraín","Elías","Federico","Gilberto","Homero","Joel","Julián","Leandro",
 "Marcelo","Noé","Omar","Pedro","Renato","Samuel","Teodoro","Ulises","Vicente","Yael","Zacarías",
 "Alfredo","Baltazar","Cuauhtémoc","Demetrio","Everardo","Fabián","Genaro","Heriberto","Israel","Jacobo"]
NOMBRES_M = ["María","Guadalupe","Ana","Laura","Patricia","Sofía","Valentina","Camila","Regina","Ximena",
 "Fernanda","Daniela","Mariana","Andrea","Paola","Gabriela","Claudia","Rosa","Elena","Verónica",
 "Alejandra","Karla","Diana","Lucía","Renata","Isabela","Victoria","Natalia","Jimena","Montserrat",
 "Adriana","Brenda","Carmen","Leticia","Beatriz","Cecilia","Dolores","Esperanza","Gloria","Irene",
 "Josefina","Lourdes","Margarita","Norma","Olivia","Pilar","Raquel","Silvia","Teresa","Yolanda",
 "Abril","Bárbara","Citlali","Dulce","Estela","Frida","Gisela","Hilda","Itzel","Julia","Karina",
 "Liliana","Marisol","Nayeli","Ofelia","Perla","Rebeca","Sandra","Tania","Úrsula","Vanessa","Wendy",
 "Xóchitl","Yara","Zaira","Aurora","Berenice","Clara","Denise","Elisa","Fabiola","Graciela","Helena",
 "Ivonne","Jacqueline","Katia","Lorena","Mireya","Noemí","Odalys","Paulina","Rocío","Susana","Tatiana",
 "Valeria","Ángela","Brígida","Constanza","Damaris","Emilia","Florencia","Guillermina","Inés","Janet"]
APELLIDOS = ["García","Martínez","López","Hernández","González","Pérez","Rodríguez","Sánchez","Ramírez","Cruz",
 "Flores","Gómez","Morales","Vázquez","Reyes","Jiménez","Torres","Díaz","Gutiérrez","Ruiz","Mendoza",
 "Aguilar","Ortiz","Castillo","Romero","Álvarez","Ramos","Domínguez","Vargas","Estrada","Medina","Guerrero",
 "Rojas","Núñez","Cortés","Lara","Ríos","Rivera","Salazar","Campos","Contreras","Figueroa","Luna","Cervantes",
 "Maldonado","Velázquez","Fuentes","Carrillo","Santiago","Ibarra","Delgado","Pacheco","Trejo","Espinoza",
 "Cárdenas","Valdez","Rosales","Mejía","Padilla","Acosta","Bautista","Camacho","Galván","Andrade","Solís",
 "Cabrera","Tapia","Zúñiga","Beltrán","Cisneros","Duarte","Escobar","Franco","Gallardo","Huerta","Juárez",
 "Lozano","Macías","Naranjo","Olvera","Ponce","Quintero","Robledo","Serrano","Toledo","Urbina","Villanueva",
 "Zamora","Arellano","Barajas","Castañeda","Dávila","Esparza","Farías","Godínez","Híjar","Íñiguez","Lemus",
 "Montoya","Nava","Ochoa","Palacios","Quiroz","Rangel","Saavedra","Tovar","Uribe","Vega","Yáñez","Zavala",
 "Aguirre","Bravo","Carmona","Becerra","Cuevas","Chávez","Escamilla","Fierro","Guzmán","Herrera","Ávila",
 "Mata","Negrete","Orozco","Peña","Rendón","Sandoval","Téllez","Valencia","Zaragoza","Anaya","Briones",
 "Carranza","Coronado","Cantú","Elizondo","Fonseca","Galindo","Gallegos","Lerma","Magaña","Mora","Murillo",
 "Nájera","Olivares","Pineda","Plascencia","Rincón","Salgado","Solórzano","Tinoco","Valadez","Villaseñor",
 "Alfaro","Arriaga","Barrera","Bermúdez","Caballero","Cordero","Enríquez","Galicia","Hidalgo","Lugo",
 "Manzanares","Meléndez","Montero","Nieto","Osorio","Partida","Quezada","Reséndiz","Sosa","Treviño",
 "Valdivia","Vela","Zermeño","Almanza","Berlanga","Carrasco","Ledezma","Mancilla","Garza","Mireles"]

VOCALES="AEIOU"
def _norm(s):
    s="".join(c for c in unicodedata.normalize("NFD",s) if unicodedata.category(c)!="Mn")
    return s.upper().replace("Ñ","X")
def _vi(s):
    for c in s[1:]:
        if c in VOCALES: return c
    return "X"
def _ci(s):
    for c in s[1:]:
        if c.isalpha() and c not in VOCALES: return c
    return "X"
_curps=set()
def gen_curp(n,ap,am,f,g):
    N,P,M=_norm(n.split()[0]),_norm(ap),_norm(am or "X")
    base=(P[0]+_vi(P)+(M[0] if M else "X")+N[0]+f.strftime("%y%m%d")+("H" if g=="M" else "M")+"MC"+_ci(P)+_ci(M)+_ci(N))
    for a,b in itertools.product(string.digits+string.ascii_uppercase,repeat=2):
        c=base+a+b
        if c not in _curps:
            _curps.add(c); return c
    raise RuntimeError("homoclave agotada "+base)
_vistas=set()
def nueva_persona(g,f):
    for _ in range(300):
        n=rng.choice(NOMBRES_H if g=="M" else NOMBRES_M); ap=rng.choice(APELLIDOS); am=rng.choice(APELLIDOS)
        k=(n,ap,am,f)
        if k not in _vistas:
            _vistas.add(k); return n,ap,am
    n=rng.choice(NOMBRES_H if g=="M" else NOMBRES_M)+" "+rng.choice(NOMBRES_H if g=="M" else NOMBRES_M)
    return n,rng.choice(APELLIDOS),rng.choice(APELLIDOS)

# ══════════════════════════════════════════════════════════════════════════════
# FASE 1+2 — WIPE operativo + periodos SEP -> trimestres NEM (autocommit, 1 sesión)
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 1+2: WIPE + periodos NEM ────────────────────")
PRESERVAR = {
 'ades_paises','ades_estados','ades_municipios','ades_localidades','ades_codigos_postales','ades_tipos_asentamiento',
 'ades_planteles','ades_escuelas','ades_informacion_escuela','ades_identidad_institucional','ades_niveles_educativos',
 'ades_plantel_niveles','ades_grados','ades_grupos','ades_aulas',
 'ades_materias','ades_materias_plan','ades_temas','ades_areas_academicas',
 'ades_escalas_evaluacion','ades_esquemas_ponderacion','ades_items_ponderacion','ades_periodos_evaluacion',
 'ades_periodos_inscripcion','ades_calendario_escolar','ades_calendarios_academicos','ades_ciclos_escolares',
 'ades_roles','ades_privilegios','ades_rol_privilegios','ades_menus','ades_menu_roles',
 'ades_catalogos','ades_catalogo_items','ades_estatus','ades_documentos_tipo','ades_niveles_ingles',
 'ades_lenguas_indigenas','ades_normatividad','ades_parametros_sistema','ades_variables_sistema','ades_config',
 'ades_criterios_eval_docente',
 'ades_audit_log','ades_webhooks','ades_webhook_logs','ades_llaves_firma','ades_encryption_audit',
 'ades_pii_encryption_backup_20260619','ades_badges','ades_h5p_tipos'}
ESPECIALES={'ades_usuarios','ades_usuario_roles','ades_personas'}
todas={r[0] for r in q("""SELECT table_name FROM information_schema.tables
  WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name LIKE 'ades_%'
    AND table_name !~ '(_ciclo_[0-9]|_default$)'""")}
operativas=sorted(todas-PRESERVAR-ESPECIALES)
wipe = "SET session_replication_role = replica;\n"
wipe += "".join(f"DELETE FROM {t};\n" for t in operativas)
wipe += "DELETE FROM ades_usuarios WHERE email_institucional NOT ILIKE '%@institutonevadi.edu.mx';\n"
# también purgar cuentas placeholder antiguas (nombres basura 'Docente .. G#/NVD') aunque tengan dominio institucional
wipe += "DELETE FROM ades_usuarios WHERE persona_id IN (SELECT id FROM ades_personas WHERE apellido_materno='NVD' OR apellido_paterno ~ '^G[0-9]' OR nombre LIKE 'Docente %');\n"
wipe += "DELETE FROM ades_usuario_roles WHERE usuario_id NOT IN (SELECT id FROM ades_usuarios);\n"
wipe += "DELETE FROM ades_personas WHERE id NOT IN (SELECT persona_id FROM ades_usuarios WHERE persona_id IS NOT NULL);\n"
wipe += "SET session_replication_role = DEFAULT;\n"
wipe += "UPDATE ades_grupos SET profesor_titular_id=NULL;\n"
wipe += "UPDATE ades_esquemas_ponderacion SET creado_por=NULL WHERE creado_por NOT IN (SELECT id FROM ades_usuarios);\n"
wipe += f"""DELETE FROM ades_periodos_evaluacion pe USING ades_ciclos_escolares ce
  WHERE pe.ciclo_escolar_id=ce.id AND ce.sistema_educativo='SEP';
INSERT INTO ades_periodos_evaluacion
 (id,nombre_periodo,numero_periodo,tipo_periodo,ciclo_escolar_id,fecha_inicio,fecha_fin,fecha_entrega_boletas,usuario_creacion,usuario_modificacion)
 SELECT gen_random_uuid(),t.nombre,t.num,'ORDINARIO',ce.id,t.ini,t.fin,t.fin+INTERVAL '5 days','{SEED_TAG}','{SEED_TAG}'
 FROM ades_ciclos_escolares ce
 CROSS JOIN LATERAL (SELECT ce.fecha_inicio ci,ce.fecha_fin cf,(ce.fecha_fin-ce.fecha_inicio) span) s
 CROSS JOIN LATERAL (VALUES (1,'1er Trimestre',s.ci,s.ci+(s.span/3)),
   (2,'2do Trimestre',s.ci+(s.span/3)+1,s.ci+(2*s.span/3)),
   (3,'3er Trimestre',s.ci+(2*s.span/3)+1,s.cf)) AS t(num,nombre,ini,fin)
 WHERE ce.sistema_educativo='SEP';
"""
exec_sql_text(wipe)
print(f"   {len(operativas)} tablas vaciadas; trimestres NEM aplicados")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 3 — Contexto
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 3: Cargar contexto ──────────────────────────")
ACTIVO=(q("SELECT id FROM ades_estatus WHERE nombre_estatus='ACTIVO' LIMIT 1") or [[None]])[0][0]
ROLES={r[0]:r[1] for r in q("SELECT nombre_rol,id FROM ades_roles")}
PLANTELES=q("SELECT id,nombre_plantel FROM ades_planteles"); PNOM=dict(PLANTELES)
def cod_pl(n): return _norm(n)[:2]
NIVELES={r[0]:(r[1],float(r[2])) for r in q("SELECT id,nombre_nivel,escala_maxima FROM ades_niveles_educativos")}
def cod_niv(n): return {'PRIMARIA':'PR','SECUNDARIA':'SE','PREPARATORIA':'PA'}.get(n,'XX')
ITEMS={}
for niv,tipo in q("""SELECT ep.nivel_educativo_id,ip.tipo_item FROM ades_items_ponderacion ip
  JOIN ades_esquemas_ponderacion ep ON ep.id=ip.esquema_id WHERE ip.is_active"""):
    ITEMS.setdefault(niv,[]).append(tipo)
GRADOS={r[0]:dict(plantel=r[1],nivel=r[2],numero=int(r[3])) for r in q("SELECT id,plantel_id,nivel_educativo_id,numero_grado FROM ades_grados")}
GRUPOS=q("SELECT id,grado_id,ciclo_escolar_id,nombre_grupo FROM ades_grupos WHERE is_active")
MAT_GRADO={}
for g,m in q("SELECT DISTINCT grado_id,materia_id FROM ades_materias_plan WHERE is_active"):
    MAT_GRADO.setdefault(g,[])
    if m not in MAT_GRADO[g]: MAT_GRADO[g].append(m)
TEMAS={}
for m,g,t,nt in q("SELECT materia_id,grado_id,id,nombre_tema FROM ades_temas WHERE is_active ORDER BY orden NULLS LAST"):
    TEMAS.setdefault((m,g),[]).append((t,nt))
PER_CICLO={}
for c,p,fi,ff in q("SELECT ciclo_escolar_id,id,fecha_inicio,fecha_fin FROM ades_periodos_evaluacion WHERE tipo_periodo='ORDINARIO' ORDER BY numero_periodo"):
    PER_CICLO.setdefault(c,[]).append((p,date.fromisoformat(fi),date.fromisoformat(ff)))
OCUP=q("SELECT ci.id,ci.valor FROM ades_catalogo_items ci JOIN ades_catalogos c ON c.id=ci.catalogo_id WHERE c.codigo='CAT_OCUPACIONES'") or [(None,'Otro')]
CICLOS_VIG=[r[0] for r in q("SELECT id FROM ades_ciclos_escolares WHERE es_vigente")]
rcfg=q("SELECT valor FROM ades_config WHERE clave='EVAL_CUAL_GRADOS_PRIMARIA'")
import json as _json
GRADOS_CUAL=set(_json.loads(rcfg[0][0])) if rcfg else {1,2}
NIV_PRIMARIA=next((nid for nid,(nm,_) in NIVELES.items() if nm=='PRIMARIA'),None)
DIRECTOR_PL={}
for pl,uu in q("""SELECT u.plantel_id,u.id FROM ades_usuarios u JOIN ades_roles r ON r.id=u.rol_id
  WHERE r.nombre_rol IN ('DIRECTOR','ADMIN_PLANTEL') AND u.plantel_id IS NOT NULL"""):
    DIRECTOR_PL.setdefault(pl,[]).append(uu)
ADMIN_GLOBAL=(q("SELECT u.id FROM ades_usuarios u JOIN ades_roles r ON r.id=u.rol_id WHERE r.nombre_rol='ADMIN_GLOBAL' LIMIT 1") or [[None]])[0][0]
CP_POOL=q("""SELECT cp.id,cp.localidad_id FROM ades_codigos_postales cp JOIN ades_estados e ON e.id=cp.estado_id
  WHERE e.nombre_estado ILIKE 'M%xico' LIMIT 8000""") or q("SELECT id,localidad_id FROM ades_codigos_postales LIMIT 8000")
print(f"   {len(GRUPOS)} grupos · {len(CP_POOL)} CPs · grados cualitativos={sorted(GRADOS_CUAL)} · rol docente={'DOCENTE' in ROLES}")

TIPO_VIA=["Calle","Avenida","Privada","Calzada","Andador","Boulevard","Cerrada"]
def gen_dir(pid):
    cp,loc=rng.choice(CP_POOL)
    return (uid(),f"{rng.choice(TIPO_VIA)} {rng.choice(APELLIDOS)}",str(rng.randint(1,350)),loc,cp,'PERSONA',pid,'PARTICULAR',True,SEED_TAG,SEED_TAG)
def tel(): return f"7{rng.randint(10,29)}{rng.randint(1000000,9999999)}"
def fnac_grado(nv,num):
    base={'PRIMARIA':6,'SECUNDARIA':12,'PREPARATORIA':15}.get(nv,6)
    return date(2026-(base+num-1),rng.randint(1,12),rng.randint(1,28))

# ══════════════════════════════════════════════════════════════════════════════
# FASE 4 — Personal docente / administrativo / salud / académico
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 4: Personal ─────────────────────────────────")
ESPEC=["Español","Matemáticas","Ciencias","Historia","Geografía","Inglés","Ed. Física","Artes","Formación Cívica","Química","Física","Biología","Computación","Filosofía"]
ESTUD=["Licenciatura","Maestría","Normal Superior","Doctorado"]
docentes_pl,medico_pl={},{}
per_p,prof_p,usr_p,dir_p,padm_p,psal_p=[],[],[],[],[],[]
emp=itertools.count(1)
def persona_adulto(g,y0=1972,y1=1996):
    f=date(rng.randint(y0,y1),rng.randint(1,12),rng.randint(1,28)); n,a,m=nueva_persona(g,f); pid=uid()
    per_p.append((pid,n,a,m,f,gen_curp(n,a,m,f,g),g,tel(),f"{_norm(n)[0].lower()}{_norm(a).lower()}{rng.randint(10,99)}@correo.mx",SEED_TAG,SEED_TAG))
    dir_p.append(gen_dir(pid)); return pid,n,a
def usuario(pid,uname,dom,rol,pl):
    usr_p.append((uid(),pid,uname,f"{uname}@{dom}",ROLES[rol],pl,'HASH_OIDC',SEED_TAG,SEED_TAG))
for pl_id,pl_nom in PLANTELES:
    docentes_pl[pl_id]=[]
    for _ in range(DOCENTES_POR_PLANTEL):
        g=rng.choice("MF"); pid,n,a=persona_adulto(g); prof_id=uid(); ne=next(emp)
        prof_p.append((prof_id,f"DOC-{ne:05d}",pid,pl_id,ACTIVO,rng.choice(['BASE','INTERINATO','HONORARIOS']),rng.choice(ESPEC),rng.choice(ESTUD),SEED_TAG,SEED_TAG))
        usuario(pid,f"{_norm(n)[0].lower()}{_norm(a)[:7].lower()}{ne}","nevadi.edu.mx",'DOCENTE',pl_id)
        docentes_pl[pl_id].append(prof_id)
    for rol,area in [('COORDINADOR_ADMINISTRATIVO','Administración'),('SECRETARIA_ACADEMICA','Control Escolar'),('PREFECTO','Disciplina'),('APOYO_ADMINISTRATIVO','Servicios Generales')]:
        g=rng.choice("MF"); pid,n,a=persona_adulto(g); ne=next(emp)
        padm_p.append((uid(),pid,pl_id,f"ADM-{ne:05d}",rol,area,rng.choice(['BASE','HONORARIOS']),rng.choice(ESTUD),rng.choice(['Matutino','Vespertino']),SEED_TAG,SEED_TAG))
        usuario(pid,f"{rol[:3].lower()}.{cod_pl(pl_nom).lower()}{ne}","nevadi.edu.mx",rol,pl_id)
    g=rng.choice("MF"); pid,n,a=persona_adulto(g); ne=next(emp); psid=uid()
    psal_p.append((psid,pid,pl_id,f"CED-{rng.randint(1000000,9999999)}","Medicina General",ACTIVO,f"MED-{ne:05d}",'BASE','Licenciatura','Matutino',SEED_TAG,SEED_TAG))
    usuario(pid,f"medico.{cod_pl(pl_nom).lower()}{ne}","nevadi.edu.mx",'MEDICO_ESCOLAR',pl_id); medico_pl[pl_id]=psid
    for rol in ['COORDINADOR_ACADEMICO','ORIENTADOR']:
        g=rng.choice("MF"); pid,n,a=persona_adulto(g); ne=next(emp)
        usuario(pid,f"{rol[:4].lower()}.{cod_pl(pl_nom).lower()}{ne}","nevadi.edu.mx",rol,pl_id)
binsert('ades_personas',['id','nombre','apellido_paterno','apellido_materno','fecha_nacimiento','curp','genero','telefono','email_personal','usuario_creacion','usuario_modificacion'],per_p)
binsert('ades_profesores',['id','numero_empleado','persona_id','plantel_id','estatus_id','tipo_contrato','especialidad','nivel_estudios','usuario_creacion','usuario_modificacion'],prof_p)
binsert('ades_personal_administrativo',['id','persona_id','plantel_id','numero_empleado','tipo_rol','area','tipo_contrato','nivel_estudios','turno','usuario_creacion','usuario_modificacion'],padm_p)
binsert('ades_personal_salud',['id','persona_id','plantel_id','cedula_profesional','especialidad','estatus_id','numero_empleado','tipo_contrato','nivel_estudios','turno','usuario_creacion','usuario_modificacion'],psal_p)
binsert('ades_usuarios',['id','persona_id','nombre_usuario','email_institucional','rol_id','plantel_id','clave_hash','usuario_creacion','usuario_modificacion'],usr_p)
binsert('ades_direcciones',['id','calle','numero_exterior','localidad_id','codigo_postal_id','entidad_tipo','entidad_id','tipo_direccion','es_principal','usuario_creacion','usuario_modificacion'],dir_p)
print(f"   {len(prof_p)} docentes · {len(padm_p)} administrativos · {len(psal_p)} salud")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 5 — Asignaciones + titulares
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 5: Asignaciones docentes ────────────────────")
asig,ASIGN=[],[]; titular=[]
for grp,grado,ciclo,_ in GRUPOS:
    g=GRADOS[grado]; pool=docentes_pl[g['plantel']]; titular.append((rng.choice(pool),grp))
    for mid in MAT_GRADO.get(grado,[]):
        prof=rng.choice(pool); aid=uid()
        asig.append((aid,grp,mid,prof,ciclo,SEED_TAG,SEED_TAG)); ASIGN.append((aid,grp,mid,prof,ciclo,grado,g['nivel']))
binsert('ades_asignaciones_docentes',['id','grupo_id','materia_id','profesor_id','ciclo_escolar_id','usuario_creacion','usuario_modificacion'],asig)
for prof,grp in titular:
    F.write(f"UPDATE ades_grupos SET profesor_titular_id={lit(prof)} WHERE id={lit(grp)};\n")
print(f"   {len(asig)} asignaciones · {len(titular)} titulares")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 6 — Preinscripción
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 6: Preinscripción ───────────────────────────")
DOCS=['ACTA_NACIMIENTO','CURP','COMPROBANTE_DOMICILIO','BOLETA_ANTERIOR','CARTILLA_VACUNACION']
sol_r,doc_r=[],[]
for _ in range(SOLICITUDES_ADMISION):
    g=rng.choice("MF"); pl=rng.choice(PLANTELES)[0]; nv=rng.choice(['PRIMARIA','SECUNDARIA','PREPARATORIA'])
    num=rng.randint(1,6 if nv!='SECUNDARIA' else 3); f=fnac_grado(nv,num); n,a,m=nueva_persona(g,f)
    est=rng.choices(['ACEPTADA','PENDIENTE','EN_REVISION','RECHAZADA'],weights=[70,15,10,5])[0]; sid=uid()
    sol_r.append((sid,n,a,m,f,gen_curp(n,a,m,f,g),nv,num,pl,rng.choice(CICLOS_VIG),f"{rng.choice(NOMBRES_M+NOMBRES_H)} {rng.choice(APELLIDOS)}",tel(),f"tutor{rng.randint(1000,9999)}@correo.mx","Escuela Primaria Federal",round(rng.uniform(7,10),1),est,date(2026,5,rng.randint(1,28)),round(rng.uniform(6,10),1),SEED_TAG,SEED_TAG))
    for d in rng.sample(DOCS,rng.randint(3,5)):
        doc_r.append((uid(),sid,d,f"{d.lower()}.pdf",f"minio://admision/{sid}/{d}.pdf",'VALIDADO' if est=='ACEPTADA' else 'PENDIENTE',SEED_TAG,SEED_TAG))
binsert('ades_solicitudes_admision',['id','nombre','apellido_paterno','apellido_materno','fecha_nacimiento','curp','nivel_solicitado','grado_solicitado','plantel_id','ciclo_escolar_id','nombre_tutor','telefono_tutor','email_tutor','escuela_procedencia','promedio_procedencia','estado','fecha_solicitud','puntuacion_diagnostico','usuario_creacion','usuario_modificacion'],sol_r)
binsert('ades_documentos_admision',['id','admision_id','tipo_documento','nombre_archivo','url_documento','estado_validacion','usuario_creacion','usuario_modificacion'],doc_r)
print(f"   {len(sol_r)} solicitudes · {len(doc_r)} documentos")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 7 — Alumnos, inscripciones, tutores, expedientes (+ detalle médico)
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 7: Alumnos, tutores, expedientes ────────────")
SANGRES=['A+','A-','B+','B-','O+','O-','AB+','AB-']; SEGUROS=['IMSS','ISSSTE','PRIVADO','NINGUNO']
ALERG=[None,None,None,None,'Polen','Penicilina','Mariscos','Frutos secos']
COND=['ASMA','DIABETES','EPILEPSIA','ALERGIA','CARDIACA']; MEDS=['Salbutamol','Insulina','Metilfenidato','Loratadina']
mat=itertools.count(1); grupo_al={}
per_a,est_r,insc_r,usr_a,dir_a,exp_r=[],[],[],[],[],[]
per_t,tut_r,usr_t,dir_t,cont_r=[],[],[],[],[]; cron_r,med_r=[],[]; est_meta=[]
for grp,grado,ciclo,_ in GRUPOS:
    g=GRADOS[grado]; niv=NIVELES[g['nivel']][0]; plc=cod_pl(PNOM[g['plantel']]); nvc=cod_niv(niv); grupo_al[grp]=[]
    for _ in range(ALUMNOS_POR_GRUPO):
        ge=rng.choice("MF"); f=fnac_grado(niv,g['numero']); n,a,m=nueva_persona(ge,f); pid=uid()
        per_a.append((pid,n,a,m,f,gen_curp(n,a,m,f,ge),ge,tel(),SEED_TAG,SEED_TAG))
        eid=uid(); ns=next(mat); matr=f"MAT-{plc}{nvc}-{ns:05d}"
        est_r.append((eid,matr,pid,g['plantel'],ACTIVO,date(2026,8,24),'REGULAR',SEED_TAG,SEED_TAG))
        insc_r.append((uid(),eid,grp,ciclo,date(2026,8,24),ACTIVO,SEED_TAG,SEED_TAG))
        usr_a.append((uid(),pid,f"al{ns}",f"al{ns}@alumnos.nevadi.edu.mx",ROLES['ALUMNO'],g['plantel'],'HASH_OIDC',SEED_TAG,SEED_TAG))
        dir_a.append(gen_dir(pid)); exp_r.append((uid(),eid,rng.choice(SANGRES),rng.choice(ALERG),rng.choice(SEGUROS),rng.random()<.85,SEED_TAG,SEED_TAG))
        grupo_al[grp].append(eid); est_meta.append((eid,g['plantel']))
        if rng.random()<.04:
            c=rng.choice(COND); cron_r.append((uid(),eid,c,f"Condición crónica: {c}",rng.choice(MEDS),'Según prescripción','Diaria',rng.choice(ALERG),f"Dr. {rng.choice(APELLIDOS)}",tel(),True,SEED_TAG,SEED_TAG))
        if rng.random()<.05:
            md=rng.choice(MEDS); med_r.append((uid(),eid,md,'1 dosis','Cada 8h','08:00','ORAL',f"Dr. {rng.choice(APELLIDOS)}",date(2026,8,24),date(2027,7,9),'Administrar en enfermería',True,SEED_TAG,SEED_TAG))
        for ti in range(rng.randint(1,2)):
            tg='F' if ti==0 else rng.choice("MF"); tf=date(rng.randint(1975,1992),rng.randint(1,12),rng.randint(1,28)); tn,tap,tam=nueva_persona(tg,tf); tpid=uid(); tph=tel(); oc=rng.choice(OCUP)
            per_t.append((tpid,tn,tap,tam,tf,gen_curp(tn,tap,tam,tf,tg),tg,tph,oc[1],SEED_TAG,SEED_TAG))
            rel='MADRE' if (tg=='F' and ti==0) else ('PADRE' if tg=='M' else 'TUTOR')
            tut_r.append((uid(),eid,tpid,rel,ti+1,True,ti==0,ti==0,'COMPLETO',SEED_TAG,SEED_TAG))
            tu=f"tutor.{matr}.{ti+1}"; usr_t.append((uid(),tpid,tu,f"{tu}@tutores.nevadi.edu.mx",ROLES['PADRE_FAMILIA'],g['plantel'],'HASH_OIDC',SEED_TAG,SEED_TAG)); dir_t.append(gen_dir(tpid))
            if ti==0: cont_r.append((uid(),eid,tpid,rel,True,True,True,f"{tn} {tap} {tam}",tph,oc[1],SEED_TAG,SEED_TAG))
binsert('ades_personas',['id','nombre','apellido_paterno','apellido_materno','fecha_nacimiento','curp','genero','telefono','usuario_creacion','usuario_modificacion'],per_a)
binsert('ades_estudiantes',['id','matricula','persona_id','plantel_id','estatus_id','fecha_ingreso','tipo_alumno','usuario_creacion','usuario_modificacion'],est_r)
binsert('ades_inscripciones',['id','estudiante_id','grupo_id','ciclo_escolar_id','fecha_inscripcion','estatus_id','usuario_creacion','usuario_modificacion'],insc_r)
binsert('ades_usuarios',['id','persona_id','nombre_usuario','email_institucional','rol_id','plantel_id','clave_hash','usuario_creacion','usuario_modificacion'],usr_a)
binsert('ades_direcciones',['id','calle','numero_exterior','localidad_id','codigo_postal_id','entidad_tipo','entidad_id','tipo_direccion','es_principal','usuario_creacion','usuario_modificacion'],dir_a)
binsert('ades_expedientes_medicos',['id','estudiante_id','tipo_sangre','alergias','seguro_medico_tipo','vacunas_al_dia','usuario_creacion','usuario_modificacion'],exp_r)
binsert('ades_condiciones_cronicas',['id','alumno_id','tipo_condicion','descripcion','medicacion_nombre','dosis','frecuencia','alergias','medico_responsable','telefono_medico','activa','usuario_creacion','usuario_modificacion'],cron_r)
binsert('ades_medicamentos_alumno',['id','alumno_id','nombre_medicamento','dosis','frecuencia','horario','via_administracion','prescrito_por','fecha_inicio','fecha_fin','observaciones','is_active','usuario_creacion','usuario_modificacion'],med_r)
binsert('ades_personas',['id','nombre','apellido_paterno','apellido_materno','fecha_nacimiento','curp','genero','telefono','ocupacion','usuario_creacion','usuario_modificacion'],per_t)
binsert('ades_tutores_alumnos',['id','alumno_id','persona_id','relacion','prioridad','puede_recoger','es_responsable_economico','es_contacto_emergencia','nivel_acceso_portal','usuario_creacion','usuario_modificacion'],tut_r)
binsert('ades_usuarios',['id','persona_id','nombre_usuario','email_institucional','rol_id','plantel_id','clave_hash','usuario_creacion','usuario_modificacion'],usr_t)
binsert('ades_direcciones',['id','calle','numero_exterior','localidad_id','codigo_postal_id','entidad_tipo','entidad_id','tipo_direccion','es_principal','usuario_creacion','usuario_modificacion'],dir_t)
binsert('ades_contactos_familiares',['id','estudiante_id','persona_id','parentesco','es_tutor_legal','es_contacto_emergencia','puede_recoger','nombre_completo','telefono_principal','ocupacion','usuario_creacion','usuario_modificacion'],cont_r)
inc_r=[]
for eid,pl in est_meta:
    if rng.random()<.04 and medico_pl.get(pl):
        inc_r.append((uid(),eid,medico_pl[pl],date.today()-timedelta(days=rng.randint(1,180)),rng.choice(['Mareo en clase','Caída en recreo','Dolor abdominal','Fiebre']),'Atención de primeros auxilios',rng.random()<.1,True,datetime.now()-timedelta(days=rng.randint(1,180)),True,SEED_TAG,SEED_TAG))
binsert('ades_incidentes_medicos',['id','estudiante_id','personal_salud_id','fecha_incidente','descripcion','tratamiento_aplicado','requirio_traslado','notificado_tutor','fecha_notificacion_tutor','is_active','usuario_creacion','usuario_modificacion'],inc_r)
print(f"   {len(est_r)} alumnos · {len(tut_r)} tutores · {len(cron_r)} crónicas · {len(med_r)} medicamentos · {len(inc_r)} incidentes")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 8 — Planeación + clases
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 8: Planeación + clases ──────────────────────")
HORAS=['07:00','08:00','09:00','10:00','11:00','12:00','13:00']
plan_r,clase_r,clase_idx=[],[],[]
for aid,grp,mid,prof,ciclo,grado,niv in ASIGN:
    temas=TEMAS.get((mid,grado)) or TEMAS.get((mid,None)) or []; ti=0
    for pid,fi,ff in PER_CICLO.get(ciclo,[]):
        for k in range(CLASES_POR_PERIODO):
            fecha=fi+timedelta(days=int((ff-fi).days*(k+0.5)/CLASES_POR_PERIODO)); tema=temas[ti%len(temas)] if temas else None; ti+=1
            if tema: plan_r.append((uid(),grp,tema[0],fecha,f"Actividades de {tema[1]}","Libro de texto, material didáctico",SEED_TAG,SEED_TAG))
            cid=uid(); hi=rng.choice(HORAS[:6]); clase_r.append((cid,grp,mid,prof,fecha,hi,HORAS[HORAS.index(hi)+1],tema[1] if tema else 'Sesión','REALIZADA',True,SEED_TAG,SEED_TAG)); clase_idx.append((cid,grp))
binsert('ades_planeacion_clases',['id','grupo_id','tema_id','fecha_planeada','descripcion_actividades','recursos_didacticos','usuario_creacion','usuario_modificacion'],plan_r)
binsert('ades_clases',['id','grupo_id','materia_id','profesor_id','fecha_clase','hora_inicio','hora_fin','tema_visto','estatus_clase','impartida','usuario_creacion','usuario_modificacion'],clase_r)
print(f"   {len(plan_r)} planeaciones · {len(clase_r)} clases")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 9 — Asistencias + justificaciones
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 9: Asistencias + justificaciones ────────────")
EST_A=['PRESENTE']*18+['AUSENTE','RETARDO']; asis_r=[]; just_r=[]; tot=0
for cid,grp in clase_idx:
    for eid in grupo_al.get(grp,[]):
        e=rng.choice(EST_A); aid=uid(); asis_r.append((aid,cid,eid,e,SEED_TAG,SEED_TAG))
        if e=='AUSENTE' and rng.random()<.35:
            just_r.append((uid(),aid,rng.choice(['MEDICA','FAMILIAR','OTRA']),'Justificación presentada por el tutor.','APROBADA',date.today()-timedelta(days=rng.randint(1,60)),SEED_TAG,SEED_TAG))
    if len(asis_r)>=40000:
        binsert('ades_asistencias',['id','clase_id','estudiante_id','estatus_asistencia','usuario_creacion','usuario_modificacion'],asis_r); tot+=len(asis_r); asis_r=[]
binsert('ades_asistencias',['id','clase_id','estudiante_id','estatus_asistencia','usuario_creacion','usuario_modificacion'],asis_r); tot+=len(asis_r)
binsert('ades_justificaciones_falta',['id','asistencia_id','tipo_justificacion','motivo','estado','fecha_resolucion','usuario_creacion','usuario_modificacion'],just_r)
print(f"   {tot} asistencias · {len(just_r)} justificaciones")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 10 — Tareas/proyectos + entregas · exámenes + calificaciones de examen
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 10: Tareas y exámenes ───────────────────────")
TIT={'tarea':'Ejercicios','proyecto':'Proyecto integrador','participacion':'Participación','laboratorio':'Práctica','otro':'Actividad'}
tarea_r,eval_r,tarea_m,eval_m=[],[],[],[]
for aid,grp,mid,prof,ciclo,grado,niv in ASIGN:
    escala=NIVELES[niv][1]; tipos=ITEMS.get(niv,['tarea']); temas=TEMAS.get((mid,grado)) or []
    for pid,fi,ff in PER_CICLO.get(ciclo,[]):
        ev=uid(); eval_r.append((ev,f"Examen {'parcial' if escala==100 else 'trimestral'}",grp,mid,pid,ff-timedelta(days=3),'EXAMEN',escala,SEED_TAG,SEED_TAG)); eval_m.append((ev,grp,escala))
        for tipo in tipos:
            if tipo in ('examen','asistencia'): continue
            for k in range(TAREAS_POR_ITEM_PERIODO):
                tid=uid(); fa=fi+timedelta(days=int((ff-fi).days*(k+1)/(TAREAS_POR_ITEM_PERIODO+1))); tema=temas[k%len(temas)] if temas else None
                tarea_r.append((tid,f"{TIT.get(tipo,'Actividad')} {k+1}","Actividad evaluable.",grp,mid,(tema[0] if tema else None),pid,fa,fa+timedelta(days=7),escala,'MANUAL',tipo,SEED_TAG,SEED_TAG)); tarea_m.append((tid,grp,escala))
binsert('ades_evaluaciones',['id','nombre_evaluacion','grupo_id','materia_id','periodo_evaluacion_id','fecha_evaluacion','tipo_evaluacion','puntaje_maximo','usuario_creacion','usuario_modificacion'],eval_r)
binsert('ades_tareas',['id','titulo','descripcion','grupo_id','materia_id','tema_id','periodo_evaluacion_id','fecha_asignacion','fecha_entrega','puntaje_maximo','origen','tipo_item','usuario_creacion','usuario_modificacion'],tarea_r)
calev=[]
for ev,grp,escala in eval_m:
    for eid in grupo_al.get(grp,[]):
        calev.append((uid(),ev,eid,round(min(escala,max(escala*0.4,rng.gauss(escala*0.78,escala*0.12))),1),SEED_TAG,SEED_TAG))
    if len(calev)>=40000:
        binsert('ades_calificaciones_evaluaciones',['id','evaluacion_id','estudiante_id','calificacion','usuario_creacion','usuario_modificacion'],calev); calev=[]
binsert('ades_calificaciones_evaluaciones',['id','evaluacion_id','estudiante_id','calificacion','usuario_creacion','usuario_modificacion'],calev)
ent=[]; tote=0
for tid,grp,escala in tarea_m:
    for eid in grupo_al.get(grp,[]):
        if rng.random()>0.82: continue
        ent.append((uid(),tid,eid,datetime.now(),round(min(escala,max(0,rng.gauss(escala*0.82,escala*0.12))),1),'CALIFICADA',SEED_TAG,SEED_TAG))
    if len(ent)>=40000:
        binsert('ades_tareas_entregas',['id','tarea_id','estudiante_id','fecha_entrega','calificacion_obtenida','estatus_entrega','usuario_creacion','usuario_modificacion'],ent); tote+=len(ent); ent=[]
binsert('ades_tareas_entregas',['id','tarea_id','estudiante_id','fecha_entrega','calificacion_obtenida','estatus_entrega','usuario_creacion','usuario_modificacion'],ent); tote+=len(ent)
print(f"   {len(eval_r)} exámenes · {len(tarea_r)} tareas · {tote} entregas")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 11 — Conducta + sanciones
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 11: Conducta + sanciones ────────────────────")
TF=['FALTA_UNIFORME','DISPOSITIVO_MOVIL','CONDUCTA_INAPROPIADA','RETRASO_REITERADO']; MED=['Amonestación verbal','Reporte escrito','Citatorio a padres']
SANC=['AMONESTACION_VERBAL','AMONESTACION_ESCRITA','CITATORIO_PADRES','SUSPENSION_1_DIA']
cond_r,sanc_r=[],[]; grado_de={gg[0]:gg[1] for gg in GRUPOS}
for grp,als in grupo_al.items():
    g=GRADOS[grado_de[grp]]; prof=rng.choice(docentes_pl[g['plantel']]); dirp=(DIRECTOR_PL.get(g['plantel']) or [ADMIN_GLOBAL])[0]
    for eid in als:
        if rng.random()<.06:
            rcid=uid(); req=rng.random()<.3
            cond_r.append((rcid,eid,grp,prof,date.today()-timedelta(days=rng.randint(1,150)),rng.choice(TF),'Incidente registrado por el personal docente.',rng.choice(MED),req,ACTIVO,SEED_TAG,SEED_TAG))
            if req: sanc_r.append((uid(),rcid,eid,rng.choice(SANC),'Reincidencia conductual',dirp,date.today()-timedelta(days=rng.randint(1,140)),'CUMPLIDA',True,SEED_TAG,SEED_TAG))
binsert('ades_reportes_conducta',['id','estudiante_id','grupo_id','reportado_por_id','fecha_reporte','tipo_falta','descripcion','medida_aplicada','requiere_seguimiento','estatus_id','usuario_creacion','usuario_modificacion'],cond_r)
binsert('ades_sanciones_disciplinarias',['id','reporte_conducta_id','estudiante_id','tipo_sancion','justificacion','autorizado_por_id','fecha_sancion','estado','notificado_padres','usuario_creacion','usuario_modificacion'],sanc_r)
print(f"   {len(cond_r)} reportes · {len(sanc_r)} sanciones")

# ── calificaciones por periodo: ponderación examen 70% + tareas 20% + asistencia 10%
#    (cálculo directo desde los componentes generados; la función oficial tiene un
#     ON CONFLICT incompatible con la tabla particionada — ver reporte). nivel_logro NEM + cierre.
grados_cual_sql="ARRAY["+",".join(str(int(x)) for x in GRADOS_CUAL)+"]"
F.write(f"""
INSERT INTO ades_calificaciones_periodo
 (id,estudiante_id,grupo_id,materia_id,periodo_evaluacion_id,calificacion_calculada,calificacion_final,
  es_acreditado,nivel_logro,inasistencias,fecha_calculo,fecha_cierre,cerrada,usuario_creacion,usuario_modificacion)
 SELECT gen_random_uuid(),i.estudiante_id,g.id,ad.materia_id,pe.id,comp.final,comp.final,
   (comp.final>=ne.escala_maxima*0.6),
   CASE WHEN gr.nivel_educativo_id={lit(NIV_PRIMARIA)} AND gr.numero_grado=ANY({grados_cual_sql})
        THEN CASE WHEN comp.final>=9 THEN 'A' WHEN comp.final>=7 THEN 'B' WHEN comp.final>=6 THEN 'C' ELSE 'D' END END,
   comp.inasist,NOW(),pe.fecha_fin,TRUE,'{SEED_TAG}','{SEED_TAG}'
 FROM ades_asignaciones_docentes ad
 JOIN ades_grupos g ON g.id=ad.grupo_id
 JOIN ades_grados gr ON gr.id=g.grado_id
 JOIN ades_niveles_educativos ne ON ne.id=gr.nivel_educativo_id
 JOIN ades_inscripciones i ON i.grupo_id=g.id AND i.is_active
 JOIN ades_periodos_evaluacion pe ON pe.ciclo_escolar_id=ad.ciclo_escolar_id AND pe.tipo_periodo='ORDINARIO'
 CROSS JOIN LATERAL (
   SELECT
     COALESCE((SELECT AVG(ce.calificacion) FROM ades_calificaciones_evaluaciones ce
                JOIN ades_evaluaciones ev ON ev.id=ce.evaluacion_id
               WHERE ce.estudiante_id=i.estudiante_id AND ev.grupo_id=g.id
                 AND ev.materia_id=ad.materia_id AND ev.periodo_evaluacion_id=pe.id), ne.escala_maxima*0.7) AS examen,
     COALESCE((SELECT AVG(te.calificacion_obtenida) FROM ades_tareas t
                JOIN ades_tareas_entregas te ON te.tarea_id=t.id
               WHERE te.estudiante_id=i.estudiante_id AND t.grupo_id=g.id
                 AND t.materia_id=ad.materia_id AND t.periodo_evaluacion_id=pe.id), ne.escala_maxima*0.7) AS tareas,
     a.pres, a.tot
   FROM (SELECT COUNT(*) FILTER (WHERE asi.estatus_asistencia='PRESENTE') AS pres, COUNT(*) AS tot
           FROM ades_asistencias asi JOIN ades_clases cl ON cl.id=asi.clase_id
          WHERE asi.estudiante_id=i.estudiante_id AND cl.grupo_id=g.id AND cl.materia_id=ad.materia_id
            AND cl.fecha_clase BETWEEN pe.fecha_inicio AND pe.fecha_fin) a
 ) raw
 CROSS JOIN LATERAL (
   SELECT round((raw.examen*0.7 + raw.tareas*0.2
                 + (CASE WHEN raw.tot>0 THEN raw.pres::numeric/raw.tot ELSE 0.95 END)*ne.escala_maxima*0.1)::numeric,1) AS final,
          GREATEST(raw.tot-raw.pres,0)::smallint AS inasist
 ) comp;
""")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 13 — Evaluación docente 360°
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 13: Evaluación docente 360° ─────────────────")
if int(q("SELECT COUNT(*) FROM ades_criterios_eval_docente")[0][0])==0:
    crit_def=[('Dominio de la asignatura','Conocimiento','PEDAGOGICO',25),('Planeación didáctica','Planeación','PEDAGOGICO',20),
              ('Manejo de grupo','Gestión','GESTION',20),('Evaluación y retroalimentación','Evaluación','PEDAGOGICO',20),('Puntualidad y asistencia','Institucional','ADMINISTRATIVO',15)]
    binsert('ades_criterios_eval_docente',['id','nombre_criterio','descripcion','categoria','peso_porcentual','escala_min','escala_max','usuario_creacion','usuario_modificacion'],
            [(uid(),n,d,cat,p,0,10,SEED_TAG,SEED_TAG) for n,d,cat,p in crit_def])
ed_r,edc_pairs=[],[]
prof_pl={pr:pl for pl in docentes_pl for pr in docentes_pl[pl]}
for pr,pl in prof_pl.items():
    base=(DIRECTOR_PL.get(pl) or [ADMIN_GLOBAL])[0]
    for tipo,evalr in [('DIRECTOR',base),('COORDINADOR',base),('AUTOEVALUACION',ADMIN_GLOBAL)]:
        eid=uid(); ed_r.append((eid,pr,rng.choice(CICLOS_VIG),evalr,tipo,date(2027,6,rng.randint(1,28)),round(rng.uniform(7.5,9.8),1),'Evaluación de desempeño docente.','COMPLETADA',SEED_TAG,SEED_TAG))
        edc_pairs.append(eid)
binsert('ades_evaluacion_docente',['id','profesor_id','ciclo_escolar_id','evaluador_id','tipo_evaluador','fecha_evaluacion','calificacion_global','comentarios','estatus','usuario_creacion','usuario_modificacion'],ed_r)
# criterios calificados: generados server-side por cada evaluación contra el catálogo de criterios
for eid in edc_pairs:
    F.write(f"""INSERT INTO ades_eval_docente_criterios (id,evaluacion_id,criterio_id,calificacion,observacion,usuario_creacion,usuario_modificacion)
 SELECT gen_random_uuid(),{lit(eid)},cr.id,round((7+random()*3)::numeric,1),'Sin observaciones.','{SEED_TAG}','{SEED_TAG}'
 FROM ades_criterios_eval_docente cr WHERE cr.is_active;\n""")
print(f"   {len(ed_r)} evaluaciones docentes 360°")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 14 — Comunicación
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 14: Comunicación ────────────────────────────")
COMS=[('Bienvenida ciclo 2026-2027','Les damos la bienvenida al nuevo ciclo escolar.','GENERAL'),('Calendario de evaluaciones','Consulte las fechas de los exámenes trimestrales.','ACADEMICO'),('Reunión de padres de familia','Se convoca a junta informativa.','GENERAL'),('Jornada de vacunación escolar','El servicio médico aplicará vacunas.','MEDICO'),('Suspensión de labores','No habrá clases por día festivo.','ADMINISTRATIVO'),('Entrega de boletas','Las boletas del trimestre están disponibles.','ACADEMICO')]
com_r=[]
for t,c,tipo in COMS:
    com_r.append((uid(),t,c,tipo,(DIRECTOR_PL.get(rng.choice([p[0] for p in PLANTELES])) or [ADMIN_GLOBAL])[0],date.today()-timedelta(days=rng.randint(1,120)),False,SEED_TAG,SEED_TAG))
binsert('ades_comunicados',['id','titulo','contenido','tipo_comunicado','creado_por_id','fecha_publicacion','requiere_acuse','usuario_creacion','usuario_modificacion'],com_r)
anun_r=[]
for pl_id,_ in PLANTELES:
    for t,c in [('Inicio de cursos','Bienvenidos al ciclo escolar 2026-2027'),('Semana cultural','Actividades del 10 al 14')]:
        anun_r.append((uid(),t,c,pl_id,date(2026,8,24),date(2027,7,9),rng.random()<.2,SEED_TAG,SEED_TAG))
binsert('ades_anuncios',['id','titulo','contenido','plantel_id','fecha_inicio','fecha_fin','es_urgente','usuario_creacion','usuario_modificacion'],anun_r)
# foros.creado_por y mensajes_foro.autor_id referencian ades_personas
autores=[r[0] for r in q("SELECT u.persona_id FROM ades_usuarios u JOIN ades_roles r ON r.id=u.rol_id WHERE r.nombre_rol='DOCENTE' AND u.persona_id IS NOT NULL LIMIT 30")]
if not autores:
    autores=[r[0] for r in q("SELECT persona_id FROM ades_usuarios WHERE persona_id IS NOT NULL LIMIT 5")]
foro_r,msj_r=[],[]
for grp,grado,ciclo,nom in GRUPOS[:30]:
    g=GRADOS[grado]; fid=uid(); foro_r.append((fid,f"Foro grupo {nom}","Espacio de discusión del grupo",'GRUPO',grp,g['plantel'],True,rng.choice(autores),SEED_TAG,SEED_TAG))
    for k in range(rng.randint(1,3)): msj_r.append((uid(),fid,f"Tema {k+1}","Mensaje de ejemplo del foro.",'PUBLICADO',rng.choice(autores),SEED_TAG,SEED_TAG))
binsert('ades_foros',['id','nombre','descripcion','tipo','grupo_id','plantel_id','es_moderado','creado_por','usuario_creacion','usuario_modificacion'],foro_r)
binsert('ades_mensajes_foro',['id','foro_id','asunto','contenido','estado','autor_id','usuario_creacion','usuario_modificacion'],msj_r)
print(f"   {len(com_r)} comunicados · {len(anun_r)} anuncios · {len(foro_r)} foros · {len(msj_r)} mensajes")

# ══════════════════════════════════════════════════════════════════════════════
# FASE 15 — Reinscripción
# ══════════════════════════════════════════════════════════════════════════════
print("── FASE 15: Reinscripción ───────────────────────────")
rei_r=[]
for grp,als in grupo_al.items():
    _,grado,ciclo,_=next(gg for gg in GRUPOS if gg[0]==grp)
    for eid in als:
        pr=rng.random()<.92; rei_r.append((uid(),ciclo,ciclo,eid,'APROBADO' if pr else 'PENDIENTE',not pr,0 if pr else round(rng.uniform(500,3000),2),pr,SEED_TAG,SEED_TAG))
binsert('ades_reinscripcion_ciclo',['id','ciclo_origen_id','ciclo_destino_id','estudiante_id','estado','tiene_adeudos','monto_adeudado','promovido','usuario_creacion','usuario_modificacion'],rei_r)
print(f"   {len(rei_r)} reinscripciones")

# ── ejecutar todo el bloque de generación ─────────────────────────────────────
F.write("ALTER TABLE ades_asistencias ENABLE TRIGGER trg_gradebook_asistencia;\n")
F.write("ALTER TABLE ades_calificaciones_evaluaciones ENABLE TRIGGER trg_gradebook_examen;\n")
F.write("ALTER TABLE ades_tareas_entregas ENABLE TRIGGER trg_gradebook_entrega;\n")
F.write("COMMIT;\n")
F.close()
print("── Ejecutando bloque de generación en BD ────────────")
exec_sql_file(F.name)

# ── FASE 16 — estadísticos: refrescar vistas materializadas ───────────────────
print("── FASE 16: Refrescar vistas materializadas ─────────")
for (mv,) in q("SELECT matviewname FROM pg_matviews WHERE schemaname='public'"):
    r=subprocess.run(PSQL+["-c",f"REFRESH MATERIALIZED VIEW {mv};"],capture_output=True,text=True,cwd="/opt/ades")
    if r.returncode!=0: print(f"   (omitida {mv})")
print("\n"+"="*58+"\n  ✓  Seed 004 — ciclo escolar completo simulado\n"+"="*58)

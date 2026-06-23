"""
Seed 007 — Módulos complementarios (aditivo sobre 006).

Puebla los módulos que 006 deja vacíos, montándose sobre el dataset existente
(alumnos, docentes, grupos, calificaciones, comunicados, foros, conducta…):

  Horarios + disponibilidad · Biblioteca (acervo + préstamos) · Cierre de ciclo
  (cierre_periodo_log + histórico) · Extraordinarios · Optativas · Rúbricas ·
  Periodos de inscripción · Certificados/Constancias · Notificaciones · Acuses ·
  Respuestas de foro · RRHH (expediente laboral, licencias, capacitaciones) ·
  H5P / Learning paths / Badges · Encuestas · Seguimiento psicosocial (NEE, planes
  de mejora, riesgo, acuerdos, observaciones) · Asistencia de personal ·
  Contactos normalizados (teléfonos/correos/persona_contactos).

Idempotente: pre-vacía SOLO las tablas que puebla. Corre en el host por el socket
de confianza (sin contraseñas).  Ejecutar:  python3 db/seeds/007_modulos_complementarios.py
"""
import os, random, uuid, itertools, subprocess, tempfile, sys
from datetime import date, datetime, timedelta

SEED_TAG="seed007"; rng=random.Random(7)
PSQL=["docker","compose","exec","-T","postgres","psql","-U","ades_admin","-d","ades"]

def q(sql):
    r=subprocess.run(PSQL+["-tAF","\t","-v","ON_ERROR_STOP=1","-c",sql],capture_output=True,text=True,cwd="/opt/ades")
    if r.returncode!=0: sys.exit("READ ERROR:\n"+r.stderr)
    return [tuple(c if c!="" else None for c in ln.split("\t")) for ln in r.stdout.splitlines() if ln!=""]
def exec_sql_text(s):
    r=subprocess.run(PSQL+["-v","ON_ERROR_STOP=1"],input=s,capture_output=True,text=True,cwd="/opt/ades")
    if r.returncode!=0: sys.exit("EXEC ERROR:\n"+r.stderr[-3000:])
def exec_sql_file(p):
    with open(p) as fh:
        r=subprocess.run(PSQL+["-v","ON_ERROR_STOP=1","-q"],stdin=fh,capture_output=True,text=True,cwd="/opt/ades")
    if r.returncode!=0: sys.exit("EXEC FILE ERROR:\n"+r.stderr[-4000:])
def uid(): return str(uuid.uuid4())
def lit(v):
    if v is None: return "NULL"
    if isinstance(v,bool): return "TRUE" if v else "FALSE"
    if isinstance(v,(int,float)): return repr(v)
    if isinstance(v,(date,datetime)): return "'"+v.isoformat()+"'"
    return "'"+str(v).replace("'","''")+"'"

# ── pre-limpieza idempotente (solo tablas de 007) ─────────────────────────────
TARGET=['ades_horarios','ades_disponibilidad_docente','ades_disponibilidad_aula','ades_biblioteca_prestamos',
 'ades_biblioteca_libros','ades_extraordinarias','ades_inscripciones_optativas','ades_rubrica_criterios','ades_rubricas',
 'ades_periodos_inscripcion','ades_certificados','ades_constancias','ades_calificaciones_historico','ades_cierre_periodo_log',
 'ades_notificaciones','ades_notificaciones_sistema','ades_acuses_comunicado','ades_respuestas_foro',
 'ades_expediente_laboral','ades_licencias_personal','ades_capacitaciones_docente','ades_h5p_contenidos',
 'ades_lp_progreso','ades_lp_asignaciones','ades_lp_recursos','ades_learning_paths','ades_badge_otorgados',
 'ades_encuesta_respuestas','ades_encuesta_preguntas','ades_encuestas','ades_nee','ades_seguimiento_plan',
 'ades_planes_mejora','ades_seguimiento_psicosocial','ades_evaluaciones_riesgo','ades_acuerdos_convivencia',
 'ades_observaciones_pedagogicas','ades_asistencia_personal','ades_persona_contactos','ades_telefonos','ades_correos_electronicos','ades_tutorias',
 'ades_normatividad','ades_actas_incidente_medico']
print("── Pre-limpieza idempotente ─────────────────────────")
exec_sql_text("SET session_replication_role=replica;\n"+"".join(f"DELETE FROM {t};\n" for t in TARGET)+"SET session_replication_role=DEFAULT;\n")

# ── contexto ──────────────────────────────────────────────────────────────────
print("── Contexto ─────────────────────────────────────────")
ADMIN=(q("SELECT id FROM ades_usuarios WHERE email_institucional='admin@institutonevadi.edu.mx' LIMIT 1") or [[None]])[0][0]
NIVELES={r[0]:r[1] for r in q("SELECT id,nombre_nivel FROM ades_niveles_educativos")}
PLANTELES=[r[0] for r in q("SELECT id FROM ades_planteles")]
AULAS=[r[0] for r in q("SELECT id FROM ades_aulas WHERE is_active")]
CICLOS=[r[0] for r in q("SELECT id FROM ades_ciclos_escolares WHERE es_vigente")]
DOCENTES=q("SELECT pr.id, pr.persona_id, pr.plantel_id FROM ades_profesores pr")  # (prof_id, persona_id, plantel)
PERSONAL=[r[0] for r in q("SELECT persona_id FROM ades_personal_administrativo UNION SELECT persona_id FROM ades_personal_salud")]
ASIGN=q("SELECT id,grupo_id,materia_id,profesor_id,ciclo_escolar_id FROM ades_asignaciones_docentes")
MAT_NIVEL=q("""SELECT DISTINCT mp.materia_id, n.nombre_nivel FROM ades_materias_plan mp
  JOIN ades_grados gr ON gr.id=mp.grado_id JOIN ades_niveles_educativos n ON n.id=gr.nivel_educativo_id""")
print(f"   {len(ASIGN)} asignaciones · {len(DOCENTES)} docentes · {len(AULAS)} aulas")

F=open(tempfile.gettempdir()+"/seed007.sql","w"); F.write("BEGIN;\n")
def binsert(table,cols,rows,page=1000):
    if not rows: return
    head=f"INSERT INTO {table} ({','.join(cols)}) VALUES\n"
    for i in range(0,len(rows),page):
        F.write(head+",\n".join("("+",".join(lit(x) for x in r)+")" for r in rows[i:i+page])+";\n")
SQ_ADMIN=f"(SELECT id FROM ades_usuarios WHERE email_institucional='admin@institutonevadi.edu.mx' LIMIT 1)"

# ── 1. HORARIOS + disponibilidad ─────────────────────────────────────────────
print("── 1. Horarios + disponibilidad ─────────────────────")
HSLOTS=[('07:00','08:00'),('08:00','09:00'),('09:00','10:00'),('10:00','11:00'),('11:00','12:00'),('12:00','13:00')]
hor=[]
for aid,grp,mat,prof,ciclo in ASIGN:
    dia=rng.randint(1,5); hi,hf=rng.choice(HSLOTS); aula=rng.choice(AULAS) if AULAS else None
    hor.append((uid(),grp,mat,prof,aula,ciclo,dia,hi,hf,'PROGRAMADO',True,SEED_TAG,SEED_TAG))
binsert('ades_horarios',['id','grupo_id','materia_id','profesor_id','aula_id','ciclo_escolar_id','dia_semana','hora_inicio','hora_fin','origen','is_active','usuario_creacion','usuario_modificacion'],hor)
dd=[]
for prof_id,_,_ in DOCENTES:
    for dia in range(1,6):
        dd.append((uid(),prof_id,dia,'07:00','14:00',True,rng.choice(CICLOS),SEED_TAG,SEED_TAG))
binsert('ades_disponibilidad_docente',['id','profesor_id','dia_semana','hora_inicio','hora_fin','disponible','ciclo_escolar_id','usuario_creacion','usuario_modificacion'],dd)
da=[]
for aula in AULAS:
    for dia in range(1,6):
        da.append((uid(),aula,rng.choice(CICLOS),dia,'07:00','14:00',SEED_TAG,SEED_TAG))
binsert('ades_disponibilidad_aula',['id','aula_id','ciclo_escolar_id','dia_semana','hora_inicio','hora_fin','usuario_creacion','usuario_modificacion'],da)
print(f"   {len(hor)} horarios · {len(dd)} disp.docente · {len(da)} disp.aula")

# ── 2. BIBLIOTECA ─────────────────────────────────────────────────────────────
print("── 2. Biblioteca ────────────────────────────────────")
LIBROS=[("Cien años de soledad","Gabriel García Márquez","LITERATURA","Sudamericana"),("El laberinto de la soledad","Octavio Paz","LITERATURA","FCE"),
 ("Pedro Páramo","Juan Rulfo","LITERATURA","FCE"),("La región más transparente","Carlos Fuentes","LITERATURA","FCE"),
 ("Matemáticas 1","SEP","TEXTO","SEP"),("Ciencias Naturales","SEP","TEXTO","SEP"),("Historia de México","SEP","HISTORIA","SEP"),
 ("Geografía Universal","Larousse","CONSULTA","Larousse"),("Química General","Chang","CIENCIA","McGraw-Hill"),("Física Conceptual","Hewitt","CIENCIA","Pearson"),
 ("Biología","Audesirk","CIENCIA","Pearson"),("El Quijote","Miguel de Cervantes","LITERATURA","RAE"),("Aura","Carlos Fuentes","LITERATURA","Era"),
 ("Diccionario de la lengua española","RAE","CONSULTA","Espasa"),("Atlas del mundo","National Geographic","CONSULTA","NatGeo"),
 ("Cálculo","Stewart","MATEMATICAS","Cengage"),("Álgebra de Baldor","Aurelio Baldor","MATEMATICAS","Patria"),("Ortografía práctica","Larousse","TEXTO","Larousse"),
 ("Programación en Python","Lutz","TECNOLOGIA","O'Reilly"),("Educación cívica y ética","SEP","TEXTO","SEP")]
libros_rows=[]; libro_ids=[]
for t,a,cat,ed in LIBROS:
    for pl in PLANTELES:
        lid=uid(); tot=rng.randint(2,8)
        libros_rows.append((lid,t,a,f"978-{rng.randint(100000000,999999999)}",cat,rng.randint(1990,2024),ed,f"Estante {rng.choice('ABCDE')}-{rng.randint(1,20)}",pl,tot,tot,True,SEED_TAG,SEED_TAG))
        libro_ids.append((lid,pl,tot))
binsert('ades_biblioteca_libros',['id','titulo','autor','isbn','categoria','anio_publicacion','editorial','ubicacion','plantel_id','ejemplares_total','ejemplares_disponibles','is_active','usuario_creacion','usuario_modificacion'],libros_rows)
# préstamos: tomar personas (alumnos/docentes) por plantel
pers_pl={}
for pid,pl in q("SELECT e.persona_id, e.plantel_id FROM ades_estudiantes e"):
    pers_pl.setdefault(pl,[]).append(pid)
prest=[]
for lid,pl,tot in libro_ids:
    for _ in range(rng.randint(0,3)):
        cand=pers_pl.get(pl) or []
        if not cand: continue
        per=rng.choice(cand); fp=date.today()-timedelta(days=rng.randint(1,120)); fde=fp+timedelta(days=14)
        devuelto=rng.random()<.7
        prest.append((uid(),lid,per,pl,fp,fde,(fp+timedelta(days=rng.randint(5,20))) if devuelto else None,
                      'DEVUELTO' if devuelto else ('VENCIDO' if fde<date.today() else 'PRESTADO'),True,SEED_TAG,SEED_TAG))
binsert('ades_biblioteca_prestamos',['id','libro_id','persona_id','plantel_id','fecha_prestamo','fecha_devolucion_esperada','fecha_devolucion_real','estatus','is_active','usuario_creacion','usuario_modificacion'],prest)
print(f"   {len(libros_rows)} libros · {len(prest)} préstamos")

# ── 3. RÚBRICAS + PERIODOS INSCRIPCIÓN ───────────────────────────────────────
print("── 3. Rúbricas + periodos inscripción ───────────────")
RUBS=[("Exposición oral",[("Dominio del tema",30),("Claridad",25),("Material de apoyo",25),("Manejo del tiempo",20)]),
 ("Ensayo académico",[("Tesis y argumentación",35),("Evidencias",25),("Estructura",20),("Ortografía y redacción",20)]),
 ("Proyecto integrador",[("Originalidad",20),("Fundamentación",30),("Resultados",30),("Presentación",20)]),
 ("Práctica de laboratorio",[("Procedimiento",30),("Seguridad",20),("Resultados",30),("Reporte",20)]),
 ("Participación en clase",[("Frecuencia",25),("Pertinencia",35),("Calidad de aportaciones",40)])]
rub_rows,crit_rows=[],[]
for nom,crits in RUBS:
    rid=uid(); rub_rows.append((rid,nom,f"Rúbrica para evaluar {nom.lower()}",True,SEED_TAG,SEED_TAG))
    for i,(c,p) in enumerate(crits,1):
        crit_rows.append((uid(),rid,c,f"Evalúa {c.lower()}",p,i,True,SEED_TAG,SEED_TAG))
binsert('ades_rubricas',['id','nombre_rubrica','descripcion','is_active','usuario_creacion','usuario_modificacion'],rub_rows)
binsert('ades_rubrica_criterios',['id','rubrica_id','nombre_criterio','descripcion','ponderacion','orden','is_active','usuario_creacion','usuario_modificacion'],crit_rows)
pinsc=[]
for ciclo in CICLOS:
    for pl in PLANTELES:
        pinsc.append((uid(),ciclo,pl,'Inscripción ordinaria',date(2026,7,1),date(2026,8,20),'ORDINARIA',900,True,True,SEED_TAG,SEED_TAG))
        pinsc.append((uid(),ciclo,pl,'Inscripción extemporánea',date(2026,8,21),date(2026,9,5),'EXTEMPORANEA',100,False,True,SEED_TAG,SEED_TAG))
binsert('ades_periodos_inscripcion',['id','ciclo_escolar_id','plantel_id','nombre_periodo','fecha_inicio','fecha_fin','tipo','cupo_maximo','activo','is_active','usuario_creacion','usuario_modificacion'],pinsc)
print(f"   {len(rub_rows)} rúbricas · {len(crit_rows)} criterios · {len(pinsc)} periodos insc.")

# ── 4. CIERRE DE CICLO: cierre_periodo_log + histórico (server-side) ──────────
print("── 4. Cierre + histórico (server-side) ──────────────")
F.write(f"""
INSERT INTO ades_cierre_periodo_log (id,periodo_evaluacion_id,grupo_id,ciclo_escolar_id,calificaciones_cerradas,alumnos_sin_calificacion,estado,cerrado_por,fecha_cierre,usuario_creacion,usuario_modificacion)
SELECT gen_random_uuid(),cp.periodo_evaluacion_id,cp.grupo_id,g.ciclo_escolar_id,COUNT(*),0,'CERRADO',{SQ_ADMIN},NOW(),'{SEED_TAG}','{SEED_TAG}'
FROM ades_calificaciones_periodo cp JOIN ades_grupos g ON g.id=cp.grupo_id
GROUP BY cp.periodo_evaluacion_id,cp.grupo_id,g.ciclo_escolar_id;

INSERT INTO ades_calificaciones_historico (id,cierre_id,cal_periodo_id,estudiante_id,grupo_id,materia_id,periodo_evaluacion_id,calificacion_final,calificacion_calculada,ajuste_manual,es_acreditado,snapshot_at,usuario_creacion,usuario_modificacion)
SELECT gen_random_uuid(),cl.id,cp.id,cp.estudiante_id,cp.grupo_id,cp.materia_id,cp.periodo_evaluacion_id,cp.calificacion_final,cp.calificacion_calculada,cp.ajuste_manual,cp.es_acreditado,NOW(),'{SEED_TAG}','{SEED_TAG}'
FROM ades_calificaciones_periodo cp
JOIN ades_cierre_periodo_log cl ON cl.grupo_id=cp.grupo_id AND cl.periodo_evaluacion_id=cp.periodo_evaluacion_id;
""")

# ── 5. EXTRAORDINARIOS (sec/prepa reprobados) + OPTATIVAS (prepa) ────────────
print("── 5. Extraordinarios + optativas ───────────────────")
F.write(f"""
INSERT INTO ades_extraordinarias (id,estudiante_id,materia_id,ciclo_escolar_id,grupo_id,tipo_examen,calificacion_previa,fecha_examen,calificacion,acredita,aplicado_por_id,usuario_creacion,usuario_modificacion)
SELECT gen_random_uuid(),t.estudiante_id,t.materia_id,t.ciclo,t.grupo_id,'EXTRAORDINARIO',t.prev,DATE '2027-07-15',t.nueva,(t.nueva>=6),{SQ_ADMIN},'{SEED_TAG}','{SEED_TAG}'
FROM (
  SELECT DISTINCT ON (cp.estudiante_id,cp.materia_id) cp.estudiante_id,cp.materia_id,g.ciclo_escolar_id ciclo,cp.grupo_id,
         cp.calificacion_final prev, round((6+random()*2)::numeric,1) nueva
  FROM ades_calificaciones_periodo cp JOIN ades_grupos g ON g.id=cp.grupo_id
  JOIN ades_grados gr ON gr.id=g.grado_id JOIN ades_niveles_educativos n ON n.id=gr.nivel_educativo_id
  WHERE cp.es_acreditado=FALSE AND n.nombre_nivel IN ('SECUNDARIA','PREPARATORIA')
  ORDER BY cp.estudiante_id,cp.materia_id,cp.calificacion_final
) t;
""")
# optativas: alumnos de prepa inscritos en 1-2 materias optativas del mismo nivel
opt_mat=[m for m,nv in MAT_NIVEL if nv=='PREPARATORIA']
if opt_mat:
    prepa_est=q("""SELECT i.estudiante_id, g.ciclo_escolar_id FROM ades_inscripciones i
      JOIN ades_grupos g ON g.id=i.grupo_id JOIN ades_grados gr ON gr.id=g.grado_id
      JOIN ades_niveles_educativos n ON n.id=gr.nivel_educativo_id WHERE n.nombre_nivel='PREPARATORIA' AND i.is_active""")
    opt=[]
    for eid,ciclo in prepa_est:
        for mid in rng.sample(opt_mat,min(2,len(opt_mat))):
            opt.append((uid(),eid,mid,ciclo,date(2026,8,24),True,SEED_TAG,SEED_TAG))
    binsert('ades_inscripciones_optativas',['id','estudiante_id','materia_id','ciclo_escolar_id','fecha_inscripcion','is_active','usuario_creacion','usuario_modificacion'],opt)
    print(f"   optativas: {len(opt)}")

# ── 6. CERTIFICADOS (último grado) + CONSTANCIAS (muestra) ───────────────────
print("── 6. Certificados + constancias ────────────────────")
F.write(f"""
INSERT INTO ades_certificados (id,estudiante_id,ciclo_escolar_id,tipo_certificado,folio,nivel_educativo,grado_completado,promedio_final,fecha_emision,vigente,emitido_por_id,estado_firma,is_active,usuario_creacion,usuario_modificacion)
SELECT gen_random_uuid(),e.id,g.ciclo_escolar_id,'CERTIFICADO_NIVEL','CERT-'||upper(substr(replace(gen_random_uuid()::text,'-',''),1,10)),
       n.nombre_nivel,gr.numero_grado::text,
       COALESCE((SELECT round(AVG(cp.calificacion_final),1) FROM ades_calificaciones_periodo cp WHERE cp.estudiante_id=e.id),8.0),
       DATE '2027-07-16',TRUE,{SQ_ADMIN},'PENDIENTE',TRUE,'{SEED_TAG}','{SEED_TAG}'
FROM ades_estudiantes e
JOIN ades_inscripciones i ON i.estudiante_id=e.id AND i.is_active
JOIN ades_grupos g ON g.id=i.grupo_id JOIN ades_grados gr ON gr.id=g.grado_id
JOIN ades_niveles_educativos n ON n.id=gr.nivel_educativo_id
WHERE (n.nombre_nivel='PRIMARIA' AND gr.numero_grado=6) OR (n.nombre_nivel='SECUNDARIA' AND gr.numero_grado=3)
   OR (n.nombre_nivel='PREPARATORIA' AND gr.numero_grado=6);

INSERT INTO ades_constancias (id,estudiante_id,tipo_constancia,folio,ciclo_escolar_id,fecha_emision,proposito,emitida_por_id,entregada,is_active,usuario_creacion,usuario_modificacion)
SELECT gen_random_uuid(),e.id,'CONSTANCIA_ESTUDIOS','CONST-'||upper(substr(replace(gen_random_uuid()::text,'-',''),1,10)),
       (SELECT id FROM ades_ciclos_escolares WHERE es_vigente LIMIT 1),DATE '2027-03-01','Trámite administrativo',{SQ_ADMIN},TRUE,TRUE,'{SEED_TAG}','{SEED_TAG}'
FROM ades_estudiantes e ORDER BY random() LIMIT 200;
""")

# ── 7. NOTIFICACIONES + ACUSES + RESPUESTAS FORO (server-side) ───────────────
print("── 7. Notificaciones + acuses + respuestas foro ─────")
F.write(f"""
INSERT INTO ades_notificaciones (id,usuario_id,titulo,cuerpo,tipo,leido,canal,is_active,usuario_creacion,usuario_modificacion)
SELECT gen_random_uuid(),u.id,'Boletas disponibles','Las calificaciones del periodo ya están disponibles.','ACADEMICO',(random()<0.5),'APP',TRUE,'{SEED_TAG}','{SEED_TAG}'
FROM ades_usuarios u;

INSERT INTO ades_notificaciones_sistema (id,usuario_id,titulo,mensaje,tipo,leido,is_active,usuario_creacion,usuario_modificacion)
SELECT gen_random_uuid(),u.id,'Bienvenido a ADES','Tu cuenta está activa para el ciclo escolar.','INFO',(random()<0.6),TRUE,'{SEED_TAG}','{SEED_TAG}'
FROM ades_usuarios u WHERE random()<0.5;

INSERT INTO ades_acuses_comunicado (id,comunicado_id,usuario_id,fecha_acuse,is_active,usuario_creacion,usuario_modificacion)
SELECT gen_random_uuid(),c.id,u.id,NOW()-(random()*30||' days')::interval,TRUE,'{SEED_TAG}','{SEED_TAG}'
FROM ades_comunicados c JOIN LATERAL (SELECT id FROM ades_usuarios ORDER BY random() LIMIT 300) u ON TRUE;

INSERT INTO ades_respuestas_foro (id,mensaje_id,contenido,autor_id,is_active,usuario_creacion,usuario_modificacion)
SELECT gen_random_uuid(),m.id,'Gracias por la información, quedo atento.',
       (SELECT persona_id FROM ades_usuarios WHERE persona_id IS NOT NULL ORDER BY random() LIMIT 1),TRUE,'{SEED_TAG}','{SEED_TAG}'
FROM ades_mensajes_foro m, generate_series(1,2);
""")

# ── 8. RRHH: expediente laboral, licencias, capacitaciones ───────────────────
print("── 8. RRHH ──────────────────────────────────────────")
CONTR=['INDEFINIDO','DETERMINADO','HONORARIOS']
exp=[]
for prof_id,per,pl in DOCENTES:
    exp.append((uid(),per,rng.choice(CONTR),date(rng.randint(2015,2024),rng.randint(1,12),1),round(rng.uniform(12000,28000),2),
                rng.choice(['Licenciatura','Maestría']),SEED_TAG,SEED_TAG))
for per in PERSONAL:
    exp.append((uid(),per,rng.choice(CONTR),date(rng.randint(2016,2024),rng.randint(1,12),1),round(rng.uniform(9000,18000),2),'Licenciatura',SEED_TAG,SEED_TAG))
binsert('ades_expediente_laboral',['id','persona_id','tipo_contrato','fecha_contratacion','salario_mensual','nivel_estudios','usuario_creacion','usuario_modificacion'],exp)
TIPLIC=['MEDICA','PERSONAL','MATERNIDAD','CAPACITACION','DUELO']
lic=[]
for prof_id,per,pl in rng.sample(DOCENTES,min(20,len(DOCENTES))):
    fi=date.today()-timedelta(days=rng.randint(10,200)); ff=fi+timedelta(days=rng.randint(2,30))
    lic.append((uid(),prof_id,rng.choice(TIPLIC),fi,ff,rng.randint(2,20),'APROBADA',rng.random()<.7,ADMIN,SEED_TAG,SEED_TAG))
binsert('ades_licencias_personal',['id','personal_id','tipo_licencia','fecha_inicio','fecha_fin','dias_habiles','estado','con_goce_sueldo','aprobado_por','usuario_creacion','usuario_modificacion'],lic)
CAPS=[('Estrategias NEM','SEP'),('Evaluación formativa','UAEMEX'),('TIC en el aula','Google'),('Primeros auxilios','Cruz Roja'),('Inclusión educativa','SEP')]
cap=[]
for prof_id,per,pl in DOCENTES:
    for _ in range(rng.randint(1,2)):
        nom,inst=rng.choice(CAPS); fi=date(rng.randint(2024,2026),rng.randint(1,12),1); ff=fi+timedelta(days=rng.randint(2,30))
        cap.append((uid(),prof_id,nom,inst,rng.choice(['CURSO','TALLER','DIPLOMADO']),rng.choice(['PRESENCIAL','EN_LINEA','HIBRIDA']),fi,ff,round(rng.uniform(20,120),0),True,SEED_TAG,SEED_TAG))
binsert('ades_capacitaciones_docente',['id','docente_id','nombre','institucion','tipo_certificacion','modalidad','fecha_inicio','fecha_fin','duracion_hrs','validado_rh','usuario_creacion','usuario_modificacion'],cap)
print(f"   {len(exp)} expedientes · {len(lic)} licencias · {len(cap)} capacitaciones")

# ── 9. ASISTENCIA DE PERSONAL ────────────────────────────────────────────────
print("── 9. Asistencia de personal ────────────────────────")
asisp=[]
allper=[p for _,p,_ in DOCENTES]+PERSONAL
for per in allper:
    for d in range(15):
        f=date.today()-timedelta(days=d)
        if f.weekday()>=5: continue
        ret=rng.random()<.1
        asisp.append((uid(),per,f,'07:'+('%02d'%rng.randint(0,20)),'14:00','COMPLETA',ret,(rng.randint(5,30) if ret else 0),SEED_TAG,SEED_TAG))
binsert('ades_asistencia_personal',['id','persona_id','fecha','hora_entrada','hora_salida','tipo_jornada','es_retardo','minutos_retardo','usuario_creacion','usuario_modificacion'],asisp)
print(f"   {len(asisp)} registros")

# ── 10. H5P / LEARNING PATHS / BADGES ────────────────────────────────────────
print("── 10. H5P / Learning paths / Badges ────────────────")
tipos_h5p=[r[0] for r in q("SELECT id FROM ades_h5p_tipos LIMIT 10")]
h5p=[]
PERSONA_DOC=DOCENTES[0][1] if DOCENTES else None  # creado_por de h5p referencia ades_personas
for i,t in enumerate(["Quiz de fracciones","Línea de tiempo histórica","Memorama de vocabulario","Video interactivo de células","Crucigrama de química"]):
    h5p.append((uid(),t,"Contenido interactivo educativo",(rng.choice(tipos_h5p) if tipos_h5p else None),f"h5p-{i+1}",rng.choice(PLANTELES),PERSONA_DOC,True,SEED_TAG,SEED_TAG))
binsert('ades_h5p_contenidos',['id','titulo','descripcion','tipo_id','h5p_content_id','plantel_id','creado_por','activo','usuario_creacion','usuario_modificacion'],h5p)
lp_rows,lpr_rows=[],[]; lp_ids=[]
for nom,crit in [("Refuerzo de Matemáticas","REPROBACION"),("Comprensión lectora","RIESGO_ALTO"),("Nivelación de Inglés","MANUAL")]:
    lid=uid(); lp_ids.append(lid); lp_rows.append((lid,nom,f"Ruta de aprendizaje: {nom}",crit,6.0,True))  # learning_paths sin cols de auditoría usuario
    for o in range(1,5):
        lpr_rows.append((uid(),lid,o,rng.choice(['VIDEO','PDF','EJERCICIO','QUIZ']),f"Recurso {o} de {nom}","Material de apoyo",rng.randint(10,40),(o<=2),True,SEED_TAG,SEED_TAG))
binsert('ades_learning_paths',['id','nombre','descripcion','criterio_activacion','umbral_activacion','is_active'],lp_rows)
binsert('ades_lp_recursos',['id','path_id','orden','tipo','titulo','descripcion','duracion_min','obligatorio','is_active','usuario_creacion','usuario_modificacion'],lpr_rows)
# asignar rutas a alumnos reprobados (muestra)
reprob=q("SELECT DISTINCT cp.estudiante_id FROM ades_calificaciones_periodo cp WHERE cp.es_acreditado=FALSE LIMIT 150")
lpa=[]
for (eid,) in reprob:
    pid=rng.choice(lp_ids); lpa.append((uid(),pid,eid,ADMIN,'Bajo desempeño','EN_PROGRESO',round(rng.uniform(0,100),0),SEED_TAG,SEED_TAG))
binsert('ades_lp_asignaciones',['id','path_id','estudiante_id','asignado_por','motivo','estatus','pct_completado','usuario_creacion','usuario_modificacion'],lpa)
badges=[r[0] for r in q("SELECT id FROM ades_badges LIMIT 8")]
bo=[]
if badges:
    for (eid,) in q("SELECT id FROM ades_estudiantes ORDER BY random() LIMIT 300"):
        bo.append((uid(),rng.choice(badges),eid,rng.choice(CICLOS),'Reconocimiento por desempeño',ADMIN,datetime.now(),SEED_TAG,SEED_TAG))
binsert('ades_badge_otorgados',['id','badge_id','estudiante_id','ciclo_id','motivo','otorgado_por','fecha_otorgado','usuario_creacion','usuario_modificacion'],bo)
# progreso de rutas: por cada asignación, avance sobre los recursos de su ruta
path_recursos={}
for r in lpr_rows: path_recursos.setdefault(r[1],[]).append(r[0])
lpp=[]
for a in lpa:
    asig_id,path=a[0],a[1]
    for rec in path_recursos.get(path,[]):
        comp=rng.random()<.5
        lpp.append((uid(),asig_id,rec,comp,rng.randint(5,40),(round(rng.uniform(6,10),1) if comp else None),(datetime.now() if comp else None),True,SEED_TAG,SEED_TAG))
binsert('ades_lp_progreso',['id','asignacion_id','recurso_id','completado','tiempo_min','calificacion','fccompletado','is_active','usuario_creacion','usuario_modificacion'],lpp)
print(f"   {len(h5p)} h5p · {len(lp_rows)} rutas · {len(lpa)} asignaciones · {len(lpp)} progreso · {len(bo)} badges")

# ── 11. ENCUESTAS ─────────────────────────────────────────────────────────────
print("── 11. Encuestas ────────────────────────────────────")
enc_id=uid()
binsert('ades_encuestas',['id','titulo','descripcion','tipo','audiencia','fecha_inicio','fecha_fin','anonima','activa','creado_por_id','is_active','usuario_creacion','usuario_modificacion'],
        [(enc_id,'Satisfacción de padres de familia','Encuesta de clima escolar','CLIMA','PADRES',date(2027,3,1),date(2027,3,31),True,True,ADMIN,True,SEED_TAG,SEED_TAG)])
preg=[("¿Cómo califica la comunicación con la escuela?",'ESCALA'),("¿Recomendaría la institución?",'BOOLEANO'),("Comentarios adicionales",'ABIERTA')]
preg_rows=[]; preg_ids=[]
for i,(t,tp) in enumerate(preg,1):
    pidq=uid(); preg_ids.append((pidq,tp)); preg_rows.append((pidq,enc_id,t,tp,i,(tp!='ABIERTA'),True,SEED_TAG,SEED_TAG))
binsert('ades_encuesta_preguntas',['id','encuesta_id','texto','tipo_pregunta','orden','obligatoria','is_active','usuario_creacion','usuario_modificacion'],preg_rows)
resp=[]
for s in range(120):
    ses=uid()
    for pidq,tp in preg_ids:
        resp.append((uid(),enc_id,pidq,ses,(None if tp=='ABIERTA' else None),(rng.randint(1,5) if tp=='ESCALA' else None),
                     ('Sí' if tp=='BOOLEANO' and rng.random()<.8 else ('No' if tp=='BOOLEANO' else None)),
                     ('Buen servicio' if tp=='ABIERTA' else None),True,SEED_TAG,SEED_TAG))
binsert('ades_encuesta_respuestas',
        ['id','encuesta_id','pregunta_id','sesion_id','valor_numerico','opcion_seleccionada','texto_respuesta','is_active','usuario_creacion','usuario_modificacion'],
        [(r[0],r[1],r[2],r[3],r[5],r[6],r[7],r[8],r[9],r[10]) for r in resp])
print(f"   1 encuesta · {len(preg_rows)} preguntas · {len(resp)} respuestas")

# ── 12. PSICOSOCIAL: NEE, riesgo, planes de mejora, seguimiento, acuerdos, obs ──
print("── 12. Seguimiento psicosocial ──────────────────────")
NEE=['DISCAPACIDAD_VISUAL','DISCAPACIDAD_AUDITIVA','TDAH','DISLEXIA','APTITUDES_SOBRESALIENTES']
est_sample=q("SELECT id FROM ades_estudiantes ORDER BY random() LIMIT 60")
nee_rows=[]
for (eid,) in est_sample:
    t=rng.choice(NEE); nee_rows.append((uid(),eid,t,f"Necesidad educativa: {t}","Apoyo y adecuaciones curriculares",date.today()-timedelta(days=rng.randint(30,300)),f"Psic. {rng.choice(['García','López','Ruiz'])}",True))
binsert('ades_nee',['id','alumno_id','tipo_nee','descripcion','apoyos_requeridos','fecha_deteccion','profesional_detecta','activa'],nee_rows)
ps_rows=[]
for (eid,) in q("SELECT id FROM ades_estudiantes ORDER BY random() LIMIT 90"):
    ps_rows.append((uid(),eid,rng.choice(['INDIVIDUAL','GRUPAL','FAMILIAR']),'Atención psicosocial','Sesión de seguimiento conductual.','Estrategias de autorregulación',rng.random()<.2,None,date.today()+timedelta(days=rng.randint(7,30))))
binsert('ades_seguimiento_psicosocial',['id','alumno_id','tipo_atencion','motivo','observaciones','estrategias_sugeridas','requiere_derivacion','derivado_a','proxima_sesion'],ps_rows)
# evaluaciones de riesgo: 1 por alumno
F.write(f"""
INSERT INTO ades_evaluaciones_riesgo (alumno_id,score_riesgo,nivel_riesgo,indicadores_json,usuario_creacion,usuario_modificacion)
SELECT e.id,(random()*100)::int,CASE WHEN random()<0.1 THEN 'ALTO' WHEN random()<0.35 THEN 'MEDIO' ELSE 'BAJO' END,
       '{{"ausentismo": false}}'::jsonb,'{SEED_TAG}','{SEED_TAG}' FROM ades_estudiantes e;
""")
# planes de mejora desde reportes de conducta que requieren seguimiento
F.write(f"""
INSERT INTO ades_planes_mejora (id,reporte_conducta_id,estudiante_id,ciclo_escolar_id,elaborado_por_id,fecha_elaboracion,objetivo_general,estado,is_active,usuario_creacion,usuario_modificacion)
SELECT gen_random_uuid(),rc.id,rc.estudiante_id,(SELECT id FROM ades_ciclos_escolares WHERE es_vigente LIMIT 1),{SQ_ADMIN},rc.fecha_reporte,
       'Mejorar la conducta y el desempeño del estudiante mediante compromisos.','ACTIVO',TRUE,'{SEED_TAG}','{SEED_TAG}'
FROM ades_reportes_conducta rc WHERE rc.requiere_seguimiento=TRUE;

INSERT INTO ades_seguimiento_plan (id,plan_mejora_id,estudiante_id,registrado_por_id,fecha_seguimiento,avance,descripcion,is_active,usuario_creacion,usuario_modificacion)
SELECT gen_random_uuid(),pm.id,pm.estudiante_id,{SQ_ADMIN},pm.fecha_elaboracion+30,'PARCIAL','Primer seguimiento del plan de mejora.',TRUE,'{SEED_TAG}','{SEED_TAG}'
FROM ades_planes_mejora pm;
""")
acu_rows=[]
for (eid,) in q("SELECT id FROM ades_estudiantes ORDER BY random() LIMIT 200"):
    acu_rows.append((uid(),eid,f"{rng.choice(['María','Juan','Ana','Luis'])} {rng.choice(['García','López','Ruiz'])}",'firma_'+uid()[:12],'admin'))
binsert('ades_acuerdos_convivencia',['id','alumno_id','tutor_nombre','tutor_firma_hash','firmado_por_usuario'],acu_rows)
obs_rows=[]
for (eid,) in q("SELECT id FROM ades_estudiantes ORDER BY random() LIMIT 300"):
    obs_rows.append((uid(),eid,rng.choice(['Excelente participación en clase.','Requiere reforzar hábitos de estudio.','Muestra liderazgo positivo.','Mejoró su desempeño este periodo.']),'1er Trimestre',rng.choice(['POSITIVA','AREA_OPORTUNIDAD']),(DOCENTES[0][1] if DOCENTES else None)))
binsert('ades_observaciones_pedagogicas',['id','alumno_id','observacion','periodo','tipo','autor_id'],obs_rows)
# tutorias
tut_rows=[]
for (eid,) in q("SELECT id FROM ades_estudiantes ORDER BY random() LIMIT 200"):
    tut_rows.append((uid(),eid,rng.choice(['ACADEMICA','PERSONAL','VOCACIONAL']),'Sesión de tutoría','Acompañamiento al estudiante.',rng.randint(20,50),'Compromisos acordados.',date.today()+timedelta(days=rng.randint(7,30)),rng.random()<.3,SEED_TAG,SEED_TAG))
binsert('ades_tutorias',['id','alumno_id','tipo_tutoria','tema','descripcion','duracion_minutos','acuerdos','proxima_sesion','requiere_seguimiento','usuario_creacion','usuario_modificacion'],tut_rows)
print(f"   {len(nee_rows)} NEE · {len(ps_rows)} psicosocial · {len(acu_rows)} acuerdos · {len(obs_rows)} obs · {len(tut_rows)} tutorías")

# ── 13. CONTACTOS NORMALIZADOS (teléfonos, correos, persona_contactos) ───────
print("── 13. Contactos normalizados ───────────────────────")
F.write(f"""
INSERT INTO ades_telefonos (id,numero_telefono,tipo_telefono,entidad_tipo,entidad_id,is_active,usuario_creacion,usuario_modificacion)
SELECT gen_random_uuid(),p.telefono,'CELULAR','PERSONA',p.id,TRUE,'{SEED_TAG}','{SEED_TAG}' FROM ades_personas p WHERE p.telefono IS NOT NULL;

INSERT INTO ades_correos_electronicos (id,direccion_email,tipo_correo,entidad_tipo,entidad_id,is_active,usuario_creacion,usuario_modificacion)
SELECT gen_random_uuid(),u.email_institucional,'INSTITUCIONAL','PERSONA',u.persona_id,TRUE,'{SEED_TAG}','{SEED_TAG}'
FROM ades_usuarios u WHERE u.persona_id IS NOT NULL AND u.email_institucional LIKE '%@%.%';

INSERT INTO ades_persona_contactos (id,persona_id,medio,tipo,valor,es_principal,verificado,is_active,usuario_creacion,usuario_modificacion)
SELECT gen_random_uuid(),p.id,'CELULAR','PERSONAL',p.telefono,TRUE,(random()<0.7),TRUE,'{SEED_TAG}','{SEED_TAG}'
FROM ades_personas p WHERE p.telefono ~ '^\\d{{10}}$';
""")

# ── 14. Normatividad + actas de incidente médico ─────────────────────────────
print("── 14. Normatividad + actas incidente ───────────────")
NORM=[("Reglamento Escolar Instituto Nevadi","REGLAMENTO","Normas de convivencia y disciplina escolar.",True,True,True),
 ("Plan de Estudios NEM 2022","PLAN_ESTUDIOS","Plan y programas de estudio de la Nueva Escuela Mexicana.",True,True,False),
 ("Currículo CBU 2024 UAEMEX","PLAN_ESTUDIOS","Currículo del Bachillerato Universitario UAEMEX.",False,False,True),
 ("Protocolo de Seguridad y Emergencias","PROTOCOLO","Protocolo de actuación ante emergencias.",True,True,True),
 ("Lineamientos de Evaluación SEP","LINEAMIENTO","Criterios de evaluación y acreditación SEP.",True,True,False),
 ("Aviso de Privacidad (LFPDPPP)","AVISO_PRIVACIDAD","Tratamiento de datos personales de la comunidad escolar.",True,True,True)]
binsert('ades_normatividad',['id','nombre','tipo','descripcion','fecha_vigencia_inicio','aplica_primaria','aplica_secundaria','aplica_preparatoria','is_active','usuario_creacion','usuario_modificacion'],
        [(uid(),n,t,d,date(2024,8,1),pp,ps,pr,True,SEED_TAG,SEED_TAG) for n,t,d,pp,ps,pr in NORM])
# actas formales para cada incidente médico registrado
F.write(f"""
INSERT INTO ades_actas_incidente_medico (id,incidente_id,descripcion_detallada,medidas_tomadas,requirio_traslado,notificado_familia,usuario_creacion,usuario_modificacion)
SELECT gen_random_uuid(),im.id,'Acta formal del incidente: '||im.descripcion,'Atención de primeros auxilios y observación.',im.requirio_traslado,TRUE,'{SEED_TAG}','{SEED_TAG}'
FROM ades_incidentes_medicos im;
""")
print(f"   {len(NORM)} normatividad · actas (server-side por incidente)")

F.write("COMMIT;\n"); F.close()
print("── Ejecutando bloque 007 en BD ──────────────────────")
exec_sql_file(F.name)
# refrescar MVs
for (mv,) in q("SELECT matviewname FROM pg_matviews WHERE schemaname='public'"):
    subprocess.run(PSQL+["-c",f"REFRESH MATERIALIZED VIEW {mv};"],capture_output=True,text=True,cwd="/opt/ades")
print("\n"+"="*58+"\n  ✓  Seed 007 — módulos complementarios cargados\n"+"="*58)

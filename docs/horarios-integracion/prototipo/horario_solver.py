# -*- coding: utf-8 -*-
"""Generador de horario escolar - 12 grupos primaria. Solver CP-SAT + export Excel."""
from ortools.sat.python import cp_model
import json

DAYS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie']
WED, THU = 'Mié', 'Jue'

GROUPS = ['1A','1B','2A','2B','3A','3B','4A','4B','5A','5B','6A','6B']
def grade(g): return int(g[0])
def turno(g): return 1 if grade(g) <= 3 else 2          # 1=baja, 2=alta
def nivel(g): return 'baja' if grade(g) <= 3 else 'alta'

# ---- Bloques de 1h por turno/día: (etiqueta, minuto de inicio) ----
def blocks_for(t, day):
    if day == 'Vie':
        return [('B1',480),('B2',540),('B3',630),('B4',690),('B5',750)]
    if t == 1:   # baja, comida 1:00-1:30
        return [('B1',480),('B2',540),('B3',630),('B4',690),('B5',810),('B6',870)]
    else:        # alta, comida 2:00-2:30
        return [('B1',480),('B2',540),('B3',630),('B4',690),('B5',750),('B6',870)]

# ---- Carga horaria (solo materias de bloque de 1h; Fábrica y Ortografía van en huecos de 30 min) ----
curri = {
 1: {'Lecto':7,'Español':1,'Matemáticas':7,'Inglés':4,'Socioemocional':1,'Desarrollo Comunitario':1,'Educación Física':2,'Computación':1,'Artes':1,'Formación':1,'Conocimiento':1,'Proyectos':2},
 2: {'Lecto':5,'Español':3,'Matemáticas':7,'Inglés':4,'Socioemocional':1,'Desarrollo Comunitario':1,'Educación Física':2,'Computación':1,'Artes':1,'Formación':1,'Conocimiento':1,'Proyectos':2},
 3: {'Español':6,'Matemáticas':7,'Inglés':4,'Socioemocional':1,'Desarrollo Comunitario':1,'Educación Física':2,'Computación':1,'Artes':1,'Formación':1,'Entidad':1,'Conocimiento':2,'Proyectos':2},
 4: {'Español':5,'Matemáticas':6,'Inglés':4,'Socioemocional':1,'Desarrollo Comunitario':1,'Educación Física':2,'Computación':1,'Artes':1,'Formación':1,'Conocimiento':2,'Proyectos':3,'Historia':1,'Geografía':1},
}
curri[5] = dict(curri[4]); curri[6] = dict(curri[4])

# Fábrica y Ortografía (30 min) -> huecos
ORPHAN_SUBJ = {'Fábrica de lectura':1, 'Ortografía':1}

CORE = {'Matemáticas','Español','Lecto'}   # bloques de 2h, continuidad
SPECIALISTS = {'Inglés','Socioemocional','Educación Física','Desarrollo Comunitario','Computación'}

def teacher_key(g, subj):
    if subj == 'Inglés': return ('Inglés', nivel(g))
    if subj == 'Socioemocional': return ('Socio', nivel(g))
    if subj == 'Educación Física': return ('EF', 'todos')
    if subj == 'Desarrollo Comunitario': return ('DC', 'todos')
    if subj == 'Computación': return ('Comp', 'todos')
    return None

# Verificación de sumas
for gr in range(1,7):
    tot = sum(curri[gr].values()) + sum(ORPHAN_SUBJ.values())
    assert tot == 31, f"Grado {gr} suma {tot}, no 31"
print("OK: todos los grados suman 31h efectivas.")

m = cp_model.CpModel()
x = {}   # (g,day,pos,subj) -> bool
slots = {}  # (g,day) -> list of (pos,start)
for g in GROUPS:
    for day in DAYS:
        bl = blocks_for(turno(g), day)
        slots[(g,day)] = bl
        for (pos,start) in bl:
            for subj in curri[grade(g)]:
                x[(g,day,pos,subj)] = m.NewBoolVar(f"x_{g}_{day}_{pos}_{subj}")

# (1) cada slot exactamente una materia
for g in GROUPS:
    for day in DAYS:
        for (pos,start) in slots[(g,day)]:
            m.Add(sum(x[(g,day,pos,subj)] for subj in curri[grade(g)]) == 1)

# (2) total de horas por materia
for g in GROUPS:
    for subj,h in curri[grade(g)].items():
        m.Add(sum(x[(g,day,pos,subj)] for day in DAYS for (pos,start) in slots[(g,day)]) == h)

# (3) Computación solo Mié y Jue
for g in GROUPS:
    for day in DAYS:
        if day in (WED,THU): continue
        for (pos,start) in slots[(g,day)]:
            m.Add(x[(g,day,pos,'Computación')] == 0)

# (3b) Ventanas horarias por materia (NOON=720 min = 12:00)
NOON = 720
TIME_RULES = {
    'Matemáticas': ('antes', NOON),   # solo en la mañana (inicia antes de las 12:00)
    'Lecto':       ('antes', NOON),   # solo en la mañana
    'Proyectos':   ('despues', NOON), # solo en la tarde (inicia a las 12:00 o después)
}
for g in GROUPS:
    for day in DAYS:
        for (pos,start) in slots[(g,day)]:
            for subj,(modo,lim) in TIME_RULES.items():
                if subj not in curri[grade(g)]: continue
                if modo=='antes' and start >= lim:
                    m.Add(x[(g,day,pos,subj)] == 0)
                if modo=='despues' and start < lim:
                    m.Add(x[(g,day,pos,subj)] == 0)

# (4) conteos diarios + continuidad + distribución
for g in GROUPS:
    gr = grade(g)
    for subj in curri[gr]:
        single_flags = []
        for day in DAYS:
            poss = slots[(g,day)]
            daily = [x[(g,day,pos,subj)] for (pos,start) in poss]
            dc = m.NewIntVar(0, len(poss), f"dc_{g}_{day}_{subj}")
            m.Add(dc == sum(daily))
            if subj in CORE:
                m.Add(dc <= 2)
                # contiguidad: si hay 2, deben ser bloques consecutivos (dif 60 min)
                for i in range(len(poss)):
                    for j in range(i+1, len(poss)):
                        if poss[j][1] - poss[i][1] != 60:
                            m.Add(x[(g,day,poss[i][0],subj)] + x[(g,day,poss[j][0],subj)] <= 1)
                # marcar día de 1h (impar)
                is1 = m.NewBoolVar(f"is1_{g}_{day}_{subj}")
                m.Add(dc == 1).OnlyEnforceIf(is1)
                m.Add(dc != 1).OnlyEnforceIf(is1.Not())
                single_flags.append(is1)
            else:
                m.Add(dc <= 1)   # repartir 1 por día
        if subj in CORE and single_flags:
            m.Add(sum(single_flags) <= 1)   # máximo un día con una sola hora

# (5) sin traslapes de profesores compartidos
conflict = {}
for g in GROUPS:
    for day in DAYS:
        for (pos,start) in slots[(g,day)]:
            for subj in curri[grade(g)]:
                tk = teacher_key(g, subj)
                if tk is None: continue
                conflict.setdefault((tk,day,start), []).append(x[(g,day,pos,subj)])
for key, lst in conflict.items():
    m.Add(sum(lst) <= 1)

# (6) >=2 horas administrativas comunes por grado (ambos titulares libres = grupos en materia especialista a la misma hora)
for gr in range(1,7):
    A, B = f"{gr}A", f"{gr}B"
    both_flags = []
    # A y B comparten turno -> mismas posiciones/tiempos
    for day in DAYS:
        for (pos,start) in slots[(A,day)]:
            specA = sum(x[(A,day,pos,s)] for s in curri[gr] if teacher_key(A,s))
            specB = sum(x[(B,day,pos,s)] for s in curri[gr] if teacher_key(B,s))
            both = m.NewBoolVar(f"both_{gr}_{day}_{pos}")
            m.Add(both <= specA); m.Add(both <= specB)
            both_flags.append(both)
    m.Add(sum(both_flags) >= 2)

solver = cp_model.CpSolver()
solver.parameters.max_time_in_seconds = 120
solver.parameters.num_search_workers = 8
solver.parameters.random_seed = 7
st = solver.Solve(m)
print("Estado:", solver.StatusName(st))
if st not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
    raise SystemExit("No se encontró solución factible.")

# ---- Extraer asignación de bloques ----
assign = {}  # (g,day,pos) -> subj
for g in GROUPS:
    for day in DAYS:
        for (pos,start) in slots[(g,day)]:
            for subj in curri[grade(g)]:
                if solver.Value(x[(g,day,pos,subj)]):
                    assign[(g,day,pos)] = subj

with open(r"C:\setag.mx\asignacion.json","w",encoding="utf-8") as f:
    json.dump({f"{g}|{d}|{p}":s for (g,d,p),s in assign.items()}, f, ensure_ascii=False, indent=1)
print("Asignación guardada. Bloques:", len(assign))

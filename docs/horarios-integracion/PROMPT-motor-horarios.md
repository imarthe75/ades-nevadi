# PROMPT — Motor de Horarios CONFIGURABLE y MULTINIVEL para `ades-nevadi`

> **Cómo usar este prompt:** copia esta carpeta `horarios-integracion/` dentro del
> repo (p. ej. en `docs/`) y pega TODO este documento en Claude Code abierto en la
> raíz del repo `ades-nevadi`. Las rutas `@docs/horarios-integracion/...` adjuntan
> los archivos de referencia. Ajusta el prefijo de ruta si lo colocas en otro lugar.

---

## 0) Contexto

Quiero implementar un **motor nativo de generación automática de horarios escolares**
dentro del módulo `horarios` de **`backend-spring`** (Java 21 + Spring Boot, arquitectura
**hexagonal**, PostgreSQL, OIDC/RBAC scope-plantel, audit trail), usando el solver
**Timefold Solver** (la continuación open-source de OptaPlanner, dominio nativo de
*timetabling*). El motor **reemplaza a aSc TimeTables** (ver §0.1).

**Requisito central:** TODAS las reglas son **configurables por plantel y por ciclo
escolar**. Nada se hardcodea. Las reglas "predefinidas" del sistema son solo valores
por defecto que la configuración de cada plantel puede sobrescribir, desactivar o
cambiar. El nivel de funcionalidad objetivo es comparable a aSc TimeTables (optimización
de calidad, restricciones ricas de docente, suplencias, editor manual, advisor de infactibilidad).

### Archivos de referencia (ESPECIFICACIÓN de restricciones — NO es el código final; se traduce a Timefold)
- `@docs/horarios-integracion/prototipo/horario_solver.py` — prototipo Python/CP-SAT que resuelve
  el caso de primaria (12 grupos) con estado **OPTIMAL**. Sirve como **especificación ejecutable
  de las restricciones** (qué debe cumplir cada regla); se reimplementa en Timefold (Java). Ver la
  guía de traducción en §11.
- `@docs/horarios-integracion/prototipo/horario_export.py` — referencia del **layout** del Excel
  (una hoja por grupo + resumen + marcado de horas administrativas). Se reimplementa en Java con
  **Apache POI**.
- `@docs/horarios-integracion/config-ejemplo/plantel-primaria-nevadi.yaml` — configuración
  completa de ejemplo (SEED + fixture de prueba *golden*). Define estructura de tiempo,
  subniveles, grupos, materias, plan de estudios, docentes, recursos, reglas tipadas y
  expectativas de test.
- `@docs/horarios-integracion/config-ejemplo/plantel-secundaria-ejemplo.yaml` — ejemplo del
  modelo `por_materia` (sin titular) para validar que la misma estructura sirve para
  secundaria/preparatoria.
- `@docs/horarios-integracion/config-ejemplo/plantel-preparatoria-uaemex.yaml` — caso REAL de
  preparatoria UAEMEX (Metepec) con datos del propio repo: semestres 1-6, solo turno matutino,
  2 grupos por semestre, plan único por semestre, y **disponibilidad de docentes** leída de la
  tabla existente `ades_disponibilidad_docente`.

---

## 0.1) Estado actual en el repo y REEMPLAZO de aSc TimeTables

Ya existe un módulo de horarios en **`backend-spring`** (Java/Spring, hexagonal). El motor
nativo NO parte de cero: **se integra reutilizando lo existente y reemplaza a aSc TimeTables**.

**Ya existe (reutilizar):**
- Entidad `Horario` → tabla **`ades_horarios`** (`grupo_id, materia_id, profesor_id, aula_id,
  ciclo_escolar_id, dia_semana, hora_inicio, hora_fin, origen, is_active`).
- `HorarioController` (`/api/v1/horarios`): GET por **grupo** y por **profesor**, listado por
  grupo/plantel, y CRUD de bloques individuales (**edición manual**: POST/PATCH/DELETE).
- Casos de uso `CrearHorarioUseCase` / `ActualizarHorarioUseCase`, `HorarioQueryService`,
  persistence adapter. Seguridad JWT + `nivel_acceso` + scope plantel.
- Tablas `ades_disponibilidad_docente` y `ades_aulas` (creadas para aSc → ahora alimentan el motor).

**aSc TimeTables = solver EXTERNO (a reemplazar):**
- Flujo actual: `GET /exportar-asc/{ciclo}` → XML → aSc genera la solución → `POST /importar-asc/{ciclo}`
  reescribe `ades_horarios`. Implementado en `HorarioAscService` (round-trip XML por UUID).
- **El motor nativo elimina ese round-trip:** genera directamente las filas de `ades_horarios`
  con **`origen = 'GENERADO'`** (la edición manual usa `'MANUAL'`; `'ASC'` queda como legacy).

**Acciones sobre aSc:**
- **DEPRECAR** `HorarioAscService` y el endpoint `POST /importar-asc` (ya no se importa de aSc).
- **OPCIONAL (transición/interoperabilidad):** conservar `GET /exportar-asc` como export de
  solo-lectura a XML aSc; marcarlo `@Deprecated` y retirarlo cuando el motor esté validado.
- Reutilizar el campo `origen` para distinguir `GENERADO` / `MANUAL` / `ASC` (legacy).

> **DECISIÓN TOMADA — el solver vive en Spring con Timefold.** Se descartó el microservicio
> Python (OR-Tools) por el costo de operar un servicio extra y el salto de red. El solver se
> implementa en el módulo `horarios` de `backend-spring`, reutilizando dominio, persistencia
> (`ades_horarios`), endpoints y seguridad existentes. El prototipo Python queda solo como
> especificación de restricciones (§11 traduce cada regla a Timefold). Las §5–§6 ya están
> redactadas para Spring/Timefold.

---

## 1) Trabaja en 2 FASES. **No escribas código en la Fase 1.**

### FASE 1 — Explorar y diseñar (entregar plan y ESPERAR mi aprobación)
1. Estudia el **módulo `horarios` de `backend-spring`** que ya existe:
   `mx/ades/modules/horarios/` (`Horario` → `ades_horarios`, `HorarioController`,
   `HorarioQueryService`, `CrearHorarioUseCase`/`ActualizarHorarioUseCase`, persistence adapter,
   y `HorarioAscService` que se va a deprecar). Anota qué reutilizas (§0.1).
2. Resume cómo se obtienen los **datos de planificación** (problem facts) desde Spring:
   grupos, plan de estudios por grado (`ades_materias_plan`), materias (`ades_materias`,
   `tipo_materia`), docentes y asignaciones (`ades_asignaciones_docentes`), disponibilidad
   (`ades_disponibilidad_docente`) y aulas (`ades_aulas`). Mira cómo `HorarioAscService`
   consulta estas tablas (JdbcTemplate/JPA) y reutiliza esa capa de acceso.
3. Revisa patrones del repo a seguir: seguridad JWT + `nivel_acceso` + scope plantel,
   audit trail, manejo de archivos (MinIO/S3), notificaciones, migraciones de BD
   (Liquibase / SQL en `db/migrations/`), tests (JUnit + Timefold `ConstraintVerifier`) y el
   spec `fase-24-interactive-grid`. Respeta `.agent/rules/` (incl. `typescript` y java) y `CLAUDE.md`.
4. Entrégame un PLAN: modelo de planificación Timefold (`@PlanningEntity`, `@PlanningVariable`,
   `@PlanningSolution`, `ConstraintProvider`), tablas nuevas de config/cabecera, dónde vive cada
   pieza (dominio de planificación, servicio de solving async, casos de uso, controller), endpoints,
   una ADR en `/DECISIONS`, y riesgos (tiempo de solving, tamaño de instancias, memoria del solver).
   Pregunta lo ambiguo. **No codifiques aún.**

### FASE 2 — Implementar (solo tras mi OK al plan)
Arquitectura hexagonal. Entrega por incrementos verificables (ver Fases A–D al final).

---

## 2) Generalización requerida (lo que el prototipo tiene hardcodeado y debe volverse config)

### A) Estructura de tiempo (por plantel/nivel)
- Días de la jornada; hora de inicio/fin **por día** (pueden variar).
- Duración del bloque de clase.
- Recreos: cantidad, horario y duración.
- Comidas: cantidad de **turnos**, horario/duración y a qué grados/subniveles aplica cada turno.
- Manejo de **fracciones de hora** (p. ej. materias de 30 min en los huecos que dejan los descansos), opcional.

### B) Modelos por NIVEL (configurable cuál aplica)
- **`titular`** (primaria): 1 docente titular por grupo + especialistas. Existe el concepto
  de "horas administrativas" (titulares del mismo grado libres a la misma hora N veces/semana).
- **`por_materia`** (secundaria y preparatoria): **NO hay docente titular de grupo**; cada
  materia tiene su(s) docente(s) que rotan entre grupos. Restricción dominante: un docente no
  puede estar en dos grupos a la vez, más su disponibilidad. La regla `horas_administrativas`
  queda **desactivada** (está condicionada a `solo_modelo: titular`); las "horas comunes" se
  redefinen como **academias por materia** si el plantel las quiere.

### B-bis) Soporte para PREPARATORIA (incl. Bachillerato UAEMex)
El motor y el esquema de configuración deben soportar, sin hardcodear:
- **Periodo = semestre.** El campo "grado" puede representar **semestre (1–6)**. La etiqueta del
  periodo (grado/semestre) es configurable por nivel.
- **Turnos** (matutino / vespertino): un grupo pertenece a un turno con su propia franja horaria;
  un docente puede dar clase en uno o ambos turnos (respetando no-traslape y disponibilidad).
- **Plan de estudios por GRUPO / PROGRAMA / ÁREA, no solo por grado.** En el bachillerato los
  alumnos eligen **área propedéutica** (p. ej. Físico-Matemáticas, Químico-Biológicas,
  Económico-Administrativas, Humanidades) en semestres superiores, por lo que **grupos del mismo
  semestre llevan materias distintas**. El esquema debe permitir asociar un plan de estudios a un
  **programa/área** y asignar cada grupo a un programa; `plan_estudios` indexado solo por grado es
  un caso particular. Reglas como `optativas` y `sincronizar_materia` complementan este modelo.
- **Tronco común vs. asignaturas de área:** las materias de tronco común se imparten a todos los
  grupos del semestre; las de área solo a los grupos de esa área.

---

## 3) Catálogo COMPLETO de restricciones (tipadas, `dura` true/false con `peso`, `activa`, `params`)

> El motor debe modelar cada regla por su `tipo`; encender/apagar y parametrizar desde la config.

**Base (ya validadas en el prototipo):**
1. `carga_horaria_exacta` — la suma semanal por materia coincide exactamente con el plan de estudios.
2. `ventana_horaria` — `{ materia, modo: antes_de|despues_de|rango, hora }` (ej. Mate/Lecto en la mañana; Proyectos en la tarde).
3. `bloque_contiguo` — `{ materias, horas_bloque, permitir_sobrante_1h, max_dias_con_sobrante, no_cruza_descanso }`.
4. `max_horas_dia` — `{ default, overrides }` (no más de N horas de una materia por día).
5. `distribucion_minima` — `{ aplicar_a, min_dias_si_horas_mayor_igual }` (repartir en la semana). *(suave)*
6. `docente_unico` — `{ materias }` (sin traslapes en toda la escuela).
7. `pool_por_subnivel` — `{ materias }` (docente distinto por subnivel, ej. Inglés baja/alta).
8. `dias_permitidos` — `{ materia, dias }` (restringe una materia a ciertos días; ej. Computación solo Mié-Jue; **Educación Física Lun-Jue, sin viernes**).
9. `sesion_fraccionada` — `{ materias, duracion_min, sesiones_por_semana, ubicar_en }`.
10. `horas_administrativas` — `{ veces_por_semana, ambito, solo_modelo, dias_distintos }`.

**Extensión (paridad con aSc TimeTables):**
11. `huecos_docente` — `{ max_huecos_dia, max_huecos_semana }` (minimizar ventanas libres del docente). *(suave por defecto)*
12. `lecciones_dia_docente` — `{ min, max }` (mín/máx clases por día por docente).
13. `consecutivas_max` — `{ ambito: docente|grupo, max }` (no más de N clases seguidas sin descanso).
14. `tiempo_libre_docente` — `{ tipo: tarde_libre|dia_libre, veces_semana }`.
15. `dias_laborables_docente` — `{ docente, max_dias_semana }` (ej. docente que solo va 2 días).
16. `comida_protegida_docente` — el docente no da clase en su franja de comida.
17. `aula_fija_materia` — `{ materia|grupo, misma_aula: true }` (misma aula toda la semana).
18. `uso_max_recurso` — `{ recurso, max_dias_semana }` (ej. gimnasio 4 días/sem).
19. `no_disponible_recurso` — `{ recurso, franjas }`.
20. `capacidad_alumnos` — `{ aula, capacidad }` (tamaño de grupo ≤ capacidad).
21. `sincronizar_materia` — `{ materia, grupos, misma_posicion: true }` (misma hora en grupos paralelos).
22. `dias_no_consecutivos` — `{ materia, ambito: grupo }` (la misma materia no en días seguidos para el mismo grupo; ej. **Educación Física no en días consecutivos**).
23. `evitar_clase_suelta` — `{ momento: despues_comida|primera_hora }`.
24. `disponibilidad_docente` — `{ docente, no_disponible: [franjas] }`.
25. `optativas` — `{ bloque, opciones, alumnos_eligen }` (grupos que cruzan secciones; sec/prepa).
26. `preferencia_horaria_docente` — `{ docente, prefiere: [franjas], evita: [franjas] }`. *(suave)*

---

## 4) Objetivo de OPTIMIZACIÓN (no solo factibilidad)

El motor maximiza **calidad** sobre las restricciones SUAVES con pesos configurables:
- minimizar huecos de docentes,
- balancear el nº de clases por día en cada grupo,
- respetar preferencias horarias,
- evitar fragmentación de materias.

Implementar con **Timefold** usando `HardSoftScore` (o `HardMediumSoftScore`): las restricciones
DURAS penalizan el nivel *hard*; las SUAVES el nivel *soft* con su `peso` como factor. Configura
el `Solver` con `terminationSpentLimit` (tiempo máximo) y, opcionalmente, `unimprovedSpentLimit`.
**Reporta el `score`** (p. ej. `0hard/-42soft`): *0 hard* = todas las duras cumplidas (factible);
el componente *soft* es la métrica de calidad. Si queda con *hard < 0* es INFACTIBLE → activar el
Advisor (§5.8). Los pesos viven en la config del plantel (`horario_regla.peso`).

---

## 5) Módulos a implementar (Spring + Timefold)

### 5.1 Modelo de planificación Timefold (dominio de solving)
Reusa el modelo "school timetabling" de Timefold:
- **`Leccion` (`@PlanningEntity`)** = un bloque de 1 hora que hay que colocar. Se genera una por
  cada (materia × grupo) y por cada hora del plan (p. ej. Matemáticas 6h en 1°A → 6 lecciones).
  Lleva como hechos: grupo, materia, **docente** (de `ades_asignaciones_docentes`), y metadatos
  (tipo_materia, es bloque-doble, etc.).
  - `@PlanningVariable Timeslot timeslot;` (día + periodo) y, si aplica, `@PlanningVariable Aula aula;`
  - `@PlanningPin boolean fijado;` para LOCK (§5.6) — Timefold no mueve las fijadas.
- **Hechos del problema (`@ProblemFactCollectionProperty`):** `Timeslot` (de la estructura de tiempo
  por turno/día), `Aula` (`ades_aulas`), disponibilidad docente (`ades_disponibilidad_docente`),
  y la configuración de reglas activas (§3).
- **`@PlanningSolution HorarioPlan`** con `@PlanningScore HardSoftScore score;` y las colecciones.
- **`ConstraintProvider`** que implementa el catálogo §3 con *Constraint Streams* (ver §11).
- Mantén esta capa como **dominio de planificación puro** (POJOs Timefold, sin JPA/web). El mapeo
  desde/hacia `ades_horarios` lo hace un adaptador (§5.3).

### 5.2 Exporter — vistas y archivos
Reimplementa el layout de `horario_export.py` en **Java con Apache POI**. Genera **vistas por
GRUPO, por DOCENTE y por AULA** (Excel + JSON), con resumen de cumplimiento y marcado de horas
administrativas. Sube los `.xlsx` al almacenamiento de objetos (MinIO/S3) y devuelve URL prefirmada.

### 5.3 Persistencia + migraciones (Liquibase / SQL en `db/migrations/`)
- **REUTILIZA `ades_horarios`** como detalle del horario (una fila = una lección colocada).
  Marca `origen = 'GENERADO'` para lo del motor y `'MANUAL'` para edición. Añade `fijado/locked`
  si no existe.
- Tablas nuevas (scope **plantel** + **ciclo escolar**, con audit):
  - `ades_horario_config` — estructura de tiempo y parámetros editables por plantel/ciclo.
  - `ades_horario_regla` — reglas tipadas del §3 (`tipo`, `dura`, `peso`, `activa`, `params` JSONB).
  - `ades_horario_corrida` — cabecera de cada generación (ciclo, plantel, versión, estado, `score`,
    tiempo de solving, generado_por). Las filas de `ades_horarios` referencian la corrida.
- **REUTILIZA** `ades_disponibilidad_docente` `(profesor_id, dia_semana, hora_inicio, hora_fin,
  disponible, ciclo_escolar_id)` como restricción DURA (cada docente solo dentro de `disponible=TRUE`)
  y `ades_aulas` para recursos. **NO crear tablas nuevas para esto.**
- `ades_suplencia` — ausencias y coberturas asignadas.

### 5.4 Solving asíncrono (sin Celery)
La generación tarda segundos–minutos → **no bloquear el request**. Usa **`SolverManager` de
Timefold** (resuelve en background y entrega el mejor resultado) y/o `@Async` de Spring. Persiste
estado/score en `ades_horario_corrida` para *polling*; al terminar, escribe `ades_horarios` y el
Excel. Soporta **warm-start**/repair para la regeneración parcial (§5.6).

**Afinación de heurísticas (no es IA, es config del solver):** configura las fases —
*Construction Heuristic* (p. ej. First Fit Decreasing) + *Local Search* (Late Acceptance o Tabu,
que rinden bien en timetabling)— `move selectors` a medida (p. ej. mover juntas las 2h de un bloque
doble) y la **terminación** (tiempo / sin-mejora). Fija un **`random-seed`** para horarios
**reproducibles y auditables**.

### 5.5 Módulo de SUPLENCIAS / cobertura
- Registrar ausencia (docente, fecha/franja, motivo) en `ades_suplencia`.
- Sugerir coberturas: docentes libres esa franja (según `ades_disponibilidad_docente` y el horario
  vigente), mismo nivel/materia si aplica, con reglas de prioridad y **reparto equitativo**. Permitir
  override manual. Puede modelarse como un mini-problema Timefold o como consulta + reglas.
- Actualizar el horario del día y **notificar** (reusar el mecanismo de notificaciones del sistema).
  Registrar en audit trail.

### 5.6 Editor manual + LOCK + regeneración parcial
- UI drag-and-drop reusando el spec **`fase-24-interactive-grid`**, con **validación en vivo** de
  conflictos. Reutiliza/extiende el CRUD existente (`POST/PATCH/DELETE` de `HorarioController`).
- **LOCK** con `@PlanningPin` en `Leccion`: las fijadas no se mueven. **Regenerar** = relanzar el
  solver tomando el horario actual como estado inicial (repair) y respetando las fijadas.

### 5.7 Vistas y publicación
- Reutiliza/extiende `HorarioQueryService` (`porGrupo`, `porProfesor`) y **añade `porAula`**.
  Devuelve JSON para el portal Angular. Publica y notifica cambios.

### 5.8 ADVISOR / diagnóstico
- **Pre-chequeo** antes de resolver: recursos sobrecargados (p. ej. horas de un docente único >
  franjas disponibles), cargas que no caben, capacidades excedidas, ventanas imposibles.
- Si el mejor `score` queda con **hard < 0** (infactible): usar el **`ScoreAnalysis` / indictments**
  de Timefold para reportar qué restricciones se violan y en qué lecciones/recursos, y **sugerir
  relajaciones** (qué regla suave aflojar, qué docente/aula añadir).

---

## 6) API — extender `HorarioController` (`/api/v1/horarios`, JWT + scope-plantel + audit)

**Reutiliza lo existente:** `GET /grupo/{id}`, `GET /profesor/{id}`, `GET` (list), `POST`,
`PATCH /{id}`, `DELETE /{id}` (edición manual de bloques).

**Nuevo (motor):**
- `POST /api/v1/horarios/generar` — lanza el solving async (202 + `corridaId`).
- `GET  /api/v1/horarios/corridas/{corridaId}` — estado/score/tiempo (para *polling*).
- `GET  /api/v1/horarios/corridas/{corridaId}/excel` — URL prefirmada del Excel.
- `GET  /api/v1/horarios/vista/{grupo|docente|aula}/{ref}` — JSON de la cuadrícula (extiende query service).
- `POST /api/v1/horarios/corridas/{corridaId}/lock` — fijar lecciones (`@PlanningPin`).
- `POST /api/v1/horarios/corridas/{corridaId}/regenerar` — re-solver respetando lo fijado.
- `POST /api/v1/suplencias` — registrar ausencia; `GET /api/v1/suplencias/{id}/sugerencias` — coberturas.

**Deprecar (aSc):** `GET /exportar-asc` → marcar `@Deprecated` (opcional como export read-only de
transición); `POST /importar-asc` → eliminar (ya no se importa de aSc).

---

## 7) Reglas de dominio del caso "Primaria Nevadi" (fixture *golden* — ver YAML de ejemplo)
- Jornada Lun-Jue 08:00-15:30; Vie 08:00-14:00 (luego desarrollo profesional docente).
- Recreo 10:00-10:30. Comida 30 min en dos turnos: primaria baja (1°-3°) 13:00-13:30,
  primaria alta (4°-6°) 14:00-14:30. Sin comida el viernes.
- Bloques de 1 hora. Cada grado suma **31 horas efectivas/semana**.
- **Matemáticas y Lecto solo en la mañana** (inician antes de las 12:00); **Proyectos solo en la tarde** (desde 12:00).
- Matemáticas/Español/Lecto en **bloques contiguos de 2h** (máx. un día con 1h suelta).
- **Fábrica de lectura** y **Ortografía** en sesiones de **30 min** (2/sem c/u) en los huecos.
- **Sin traslapes** de docentes compartidos: Inglés y Socioemocional con docente distinto por
  subnivel; Ed. Física, Desarrollo Comunitario y Computación con **1 docente** cada uno.
- **Computación: 1 docente, solo Miércoles y Jueves.**
- **Educación Física: NO los viernes** (`dias_permitidos` Lun-Jue) y **NO en días consecutivos**
  para el mismo grupo (`dias_no_consecutivos`).
- **≥2 horas administrativas** comunes por grado (titulares A y B libres a la misma hora).
- El titular imparte: Lecto, Español, Mate, Conocimiento del medio, Artes, Fábrica de lectura,
  Ortografía, Ciencias, Historia, Formación Cívica y Ética, Entidad, Geografía y Proyectos.
- El solver **lee** todo esto desde la config/BD, no hardcodeado.

---

## 8) Criterios de aceptación / tests (JUnit + Timefold `ConstraintVerifier`)
- Cada restricción del §3 tiene un test unitario con **`ConstraintVerifier`** (penaliza/recompensa
  el escenario esperado) — son rápidos y no requieren correr el solver completo.
- Una **misma base de código** genera horarios válidos para una config de **PRIMARIA**
  (modelo titular) y para una de **SECUNDARIA/PREPA** (modelo por materia), cambiando solo la config.
- Caso golden primaria (test de integración del solver): score con **0 hard**; **31h exactas por
  grado**; **cero traslapes** de docentes; Computación solo Mié-Jue; Mate/Lecto antes de 12:00;
  Proyectos después de 12:00; **Educación Física sin viernes y sin días consecutivos**;
  **2 horas administrativas por grado**; 12 grupos.
- Reporta `score` y la cuenta de huecos de docentes.
- **Suplencias:** ante una ausencia, propone ≥1 cobertura válida o explica por qué no hay.
- **Lock + regenerar:** respeta lo fijado (pin) y mejora el resto sin romper restricciones duras.
- Genera vistas por docente y por aula **sin traslapes**.
- Dos plantes con reglas distintas producen horarios distintos correctos.

---

## 9) Convenciones del repo a respetar
- Arquitectura hexagonal: el **dominio de planificación** (POJOs Timefold + `ConstraintProvider`)
  no depende de Spring/JPA/web; el solving y la persistencia se inyectan por puertos/adaptadores.
- Añade la dependencia de **Timefold** al `backend-spring` (Maven/Gradle:
  `ai.timefold.solver:timefold-solver-spring-boot-starter`), versión compatible con el Spring Boot
  del proyecto. Configura el solver en `application.yml` (termination, move thread count).
- Migraciones de BD con el mecanismo del repo (**Liquibase / SQL en `db/migrations/`**); nada de
  DDL fuera de migraciones.
- OIDC/JWT + RBAC `nivel_acceso` + scope-plantel en todos los endpoints; entradas en audit trail.
- Solving en background (`SolverManager`/`@Async`); archivos en MinIO/S3.
- Sigue `.agent/rules/` (java/typescript), `CLAUDE.md`, estilo de código y testing del repo;
  crea una ADR en `/DECISIONS` (decisión: motor nativo Timefold reemplaza a aSc TimeTables).

---

## 10) Orden de entrega sugerido (incrementos verificables)
- **Fase A (MVP):** modelo Timefold (`Leccion`/`Timeslot`/`Aula`/`HorarioPlan`) + `ConstraintProvider`
  con reglas base (§3.1-10) + persistencia (`ades_horarios` + tablas de config) + solving async
  + endpoint `generar` + export Excel/JSON. Debe pasar el caso golden de primaria.
- **Fase B (calidad + operación):** objetivo de optimización con pesos (§4), restricciones ricas de
  docente (§3.11-16, 24, 26), **suplencias** (§5.5), vistas por docente/aula (§5.7).
- **Fase C (productividad):** editor manual con lock + regeneración parcial (§5.6), Advisor (§5.8).
- **Fase D (sec/prepa avanzado):** optativas/seminarios (§3.25), sincronización entre grupos (§3.21),
  restricciones de aula/recurso (§3.17-20, 22-23).
- **Fase E (opcional — IA asistente, §12):** lenguaje natural → reglas tipadas, explicación de
  infactibilidad y asistente what-if, reutilizando `llm_service.py`. La IA propone; el motor valida.

---

## 11) Guía de traducción: restricciones §3 → Timefold (Constraint Streams)

> Notación: H = restricción dura (`penalize(HardSoftScore.ONE_HARD)`), S = suave
> (`penalize(HardSoftScore.ONE_SOFT)` ponderada por `regla.peso`). `forEach(Leccion.class)`,
> `join`, `groupBy`, `filter`, `ifExists/ifNotExists` son operadores de Constraint Streams.
> El prototipo Python (`horario_solver.py`) es la **especificación**: cada regla de abajo debe dar
> el mismo resultado que su equivalente en el prototipo/§7.

**Estructurales (siempre activas):**
- **No-traslape docente** (H): `forEachUniquePair(Leccion, equal(docente), overlapping(timeslot))` → penalize.
  Cubre `docente_unico` (6) y el modelo `por_materia`.
- **No-traslape grupo** (H): igual con `equal(grupo)` (un grupo no puede tener 2 clases a la vez).
- **No-traslape aula** (H): igual con `equal(aula)` cuando `aula` es variable de planificación.

**Catálogo §3:**
1. `carga_horaria_exacta` (H) — se garantiza por construcción: se crean exactamente N lecciones por
   (materia,grupo) según el plan; basta con que todas queden asignadas (sin `null` en la variable).
2. `ventana_horaria` (H) — `filter(leccion → fuera de la ventana de su materia)` → penalize.
3. `bloque_contiguo` (H/S) — recompensa pares contiguos de la misma materia/grupo en el día
   (`reward` cuando `timeslot` consecutivos) y/o penaliza fragmentación; usar `groupBy` por
   (grupo,materia,día). Modela "2h hasta agotar, 1 suelta máx".
4. `max_horas_dia` (H) — `groupBy(grupo, materia, día, count())` → `filter(count > max)` → penalize.
5. `distribucion_minima` (S) — `groupBy(grupo, materia, countDistinct(día))` → penaliza si `< min`.
6. `docente_unico` (H) — cubierto por No-traslape docente (arriba).
7. `pool_por_subnivel` (H) — el docente es un hecho por lección (de `ades_asignaciones_docentes`);
   basta con asignar el docente del subnivel correcto al crear las lecciones; el no-traslape hace el resto.
8. `dias_permitidos` (H) — `filter(leccion.materia restringida && timeslot.dia ∉ dias)` → penalize.
   (EF Lun-Jue, Computación Mié-Jue).
9. `sesion_fraccionada` (H) — modelar timeslots de 30 min para esas materias (Fábrica/Ortografía) y
   colocarlas en los huecos; o tratarlas como lecciones de media hora con sus propios timeslots.
10. `horas_administrativas` (S/H) — `groupBy(grado, timeslot)` y recompensar/exigir ≥N timeslots
    donde **ambos titulares (A y B) del grado** están libres (sin lección de su grupo). Solo modelo titular.
11. `huecos_docente` (S) — `groupBy(docente, día)` ordenando timeslots; penaliza huecos entre la
    primera y última clase del día (usar `toList` + cálculo de gaps).
12. `lecciones_dia_docente` (H/S) — `groupBy(docente, día, count())` → fuera de `[min,max]` → penalize.
13. `consecutivas_max` (H/S) — secuencia por (docente|grupo, día); penaliza rachas > max.
14. `tiempo_libre_docente` (S/H) — exige ≥1 tarde/día libre por docente (groupBy docente).
15. `dias_laborables_docente` (H) — `groupBy(docente, countDistinct(día))` → `> max` → penalize.
16. `comida_protegida_docente` (H) — `filter(leccion solapa la franja de comida del docente)` → penalize.
17. `aula_fija_materia` (S/H) — `groupBy(materia|grupo, countDistinct(aula))` → penaliza si `> 1`.
18. `uso_max_recurso` (H) — `groupBy(aula, countDistinct(día))` → `> max` → penalize.
19. `no_disponible_recurso` (H) — `filter(leccion.aula no disponible en ese timeslot)` → penalize.
20. `capacidad_alumnos` (H) — `filter(grupo.tamaño > leccion.aula.capacidad)` → penalize.
21. `sincronizar_materia` (S/H) — `join` lecciones de la misma materia en grupos paralelos;
    penaliza si `timeslot` distinto (misma posición).
22. `dias_no_consecutivos` (H/S) — `join` mismas (grupo,materia) en días con `|dia1-dia2|==1` → penalize.
    (EF no consecutivos).
23. `evitar_clase_suelta` (S) — penaliza lección aislada tras comida / a 1ª hora según config.
24. `disponibilidad_docente` (H) — `filter(timeslot ∉ ades_disponibilidad_docente del docente)` → penalize.
25. `optativas` (H) — lecciones de optativa del mismo grado comparten timeslot (bloque común);
    modelar el bloque y `join` por `misma_posicion_en_grado`.
26. `preferencia_horaria_docente` (S) — recompensa/penaliza según franjas preferidas/evitadas.

> Cada ítem = un método `Constraint` en el `ConstraintProvider`, activable/ponderable leyendo
> `ades_horario_regla` (las inactivas no se registran o pesan 0). Acompaña cada uno con su test
> `ConstraintVerifier` (§8).

---

## 12) Capa de IA asistente (OPCIONAL — no toca el solver)

**Principio rector: la IA *propone*, el motor (Timefold) *valida y ejecuta*; la IA nunca rompe ni
sustituye una restricción dura.** Toda salida de IA pasa por el mismo validador de esquema que las
reglas manuales y/o requiere **confirmación humana** antes de persistir. **Reutiliza el servicio de
IA existente** del repo (`backend/app/services/llm_service.py`, NVIDIA NIM / langchain): el módulo de
horarios lo invoca por API, o el asistente llama a `/api/v1/horarios`. No metas un LLM dentro del
bucle de optimización (no es determinista ni garantiza factibilidad).

Tres usos, por orden de valor:
1. **Lenguaje natural → regla tipada.** El usuario escribe "Matemáticas no después de las 12"; la IA
   devuelve un objeto de `ades_horario_regla` (`{ tipo: ventana_horaria, params: {...} }`). Se le pasa
   el **catálogo §3 como esquema/tools**: debe mapear a un `tipo` EXISTENTE o responder "no soportado"
   (nunca inventar tipos). Se muestra al usuario para confirmar; al aceptar, se persiste como cualquier regla.
2. **Explicar infactibilidad + sugerir relajaciones.** Traduce el `ScoreAnalysis`/indictments de
   Timefold (técnico) a lenguaje claro, señala la(s) restricción(es)/recurso(s) en conflicto y propone
   acciones concretas ("añade disponibilidad al único docente de Computación el miércoles, o un 2º
   docente"). La IA solo redacta sobre datos del motor; no decide.
3. **Asistente "what-if".** El usuario pide un cambio en lenguaje natural ("¿y si EF fuera solo
   martes?"); la IA lo traduce a cambios de config, dispara una **corrida de prueba** (versión aparte)
   y compara `score` y cumplimiento. No sobrescribe el horario vigente sin confirmación.

Salvaguardas:
- **Determinismo:** el horario lo produce Timefold (`random-seed` fijo), no la IA.
- **Validación:** toda regla generada por IA pasa por el validador de esquema de `ades_horario_regla`.
- **Auditoría:** registrar en audit trail que la regla/cambio fue *propuesto por IA* y *confirmado por <usuario>*.
- **Privacidad/seguridad:** enviar al LLM solo lo necesario; respetar scope-plantel; sin datos personales innecesarios.

> No es parte del MVP. Implementar después de las Fases A–D (ver **Fase E**).

# Integración: Motor de Horarios para `ades-nevadi`

Paquete de arranque para implementar un **motor nativo de generación automática de horarios**
en el módulo `horarios` de **`backend-spring`** (Java + Spring Boot) usando el solver
**Timefold (OptaPlanner)**. Es configurable por plantel y multinivel (primaria, secundaria,
preparatoria) y **reemplaza a aSc TimeTables** (que hoy actúa como solver externo vía export/import
XML). El prototipo Python/OR-Tools de esta carpeta es la **especificación de restricciones** que
se traduce a Timefold (ver §11 del prompt).

## Contenido

```
horarios-integracion/
├─ README.md                      ← este archivo
├─ PROMPT-motor-horarios.md       ← PROMPT del BACKEND (Spring + Timefold) — pegar primero
├─ PROMPT-frontend-angular.md     ← PROMPT del FRONTEND (Angular) — usar después del backend
├─ prototipo/
│  ├─ horario_solver.py           ← spec de restricciones (CP-SAT, estado OPTIMAL) — se traduce a Timefold
│  └─ horario_export.py           ← referencia del layout del Excel — se reimplementa en Java/POI
└─ config-ejemplo/
   ├─ plantel-primaria-nevadi.yaml      ← SEED + fixture golden (primaria, modelo titular)
   ├─ plantel-primaria-nevadi.json      ← mismo contenido en JSON
   ├─ plantel-secundaria-ejemplo.yaml   ← ejemplo modelo "por_materia" (secundaria)
   └─ plantel-preparatoria-uaemex.yaml  ← caso REAL prepa UAEMEX (semestres, matutino, disponibilidad docente)
seeds/
├─ 004b_materias_primaria_nevadi.sql        ← materias REALES de primaria Nevadi (horas por grado, tipo_materia)
└─ 002b_docentes_especialistas_primaria.sql ← docentes especialistas + asignación corregida (cubren A y B, multi-materia/grado)
```

> Orden de ejecución de los seeds en el repo: `001 → 002 → 004 → 004b → 002b`.

## Cómo usarlo

1. Copia esta carpeta dentro del repo, por ejemplo en `docs/horarios-integracion/`.
2. Abre **Claude Code en la raíz del repo** `ades-nevadi`.
3. **Backend primero:** pega el contenido de **`PROMPT-motor-horarios.md`**. Las rutas
   `@docs/horarios-integracion/...` adjuntan los archivos de referencia (ajusta el prefijo si lo
   colocaste en otra ruta). Deja que Claude Code entregue el **PLAN (Fase 1)** y apruébalo antes
   de implementar (Fase 2).
4. **Frontend después:** una vez que la API de horarios exista, pega
   **`PROMPT-frontend-angular.md`** para construir la capa Angular (vistas, editor
   drag-and-drop, descargas, suplencias, advisor). También trabaja en 2 fases.

## Qué es cada cosa

- **El prompt** es el documento principal: contexto, 2 fases de trabajo, catálogo completo de 26
  restricciones tipadas, objetivo de optimización, módulos (solver, exporter, suplencias, editor
  con lock, vistas, advisor), modelo de datos, endpoints, reglas de dominio del caso Nevadi,
  criterios de aceptación y orden de entrega por fases A–D.
- **El prototipo** (`prototipo/`) es la lógica YA validada del solver (Python/CP-SAT). Es la
  **especificación de restricciones** que se traduce a Timefold (Java); el motor final lee la
  configuración desde la BD en lugar de hardcodear. NO se despliega: es referencia.
- **Las configuraciones** (`config-ejemplo/`) son a la vez *seed* y *fixtures de prueba*: el motor
  genérico debe poder leerlas, generar el horario y pasar sus `expectativas_test`.

## Validación local del prototipo (opcional)

```bash
pip install ortools openpyxl pyyaml
python prototipo/horario_solver.py     # genera asignacion.json (estado OPTIMAL)
python prototipo/horario_export.py     # genera el Excel con las 12 hojas + RESUMEN
```

> Nota: los scripts del prototipo usan rutas absolutas de la máquina original y datos
> hardcodeados de primaria; son **referencia**, no el código de integración final.

## Estado de las funciones objetivo (resumen del análisis vs aSc TimeTables)

| Bloque | Incluido en el prompt |
|---|---|
| Generación automática con restricciones (Timefold/OptaPlanner) | ✅ base |
| Reemplazo de aSc TimeTables (deprecación export/import XML) | ✅ §0.1 |
| Guía de traducción restricciones → Timefold | ✅ §11 |
| Optimización de calidad (huecos, balanceo, preferencias) | ✅ §4 |
| Restricciones ricas de docente | ✅ §3.11-16, 24, 26 |
| Gestión de suplencias / cobertura | ✅ §5.5 |
| Editor manual + lock + regeneración parcial | ✅ §5.6 |
| Vistas por grupo / docente / aula + publicación | ✅ §5.2, §5.7 |
| Advisor / diagnóstico de infactibilidad | ✅ §5.8 |
| Optativas/seminarios y sincronización entre grupos | ✅ §3.21, §3.25 |
| Multinivel (titular vs por_materia) | ✅ §2.B + ejemplos |
| Afinación de heurísticas (fases CH+Local Search, random-seed) | ✅ §5.4 |
| Capa de IA asistente opcional (NL→reglas, explicar infactibilidad, what-if) | ✅ §12 (Fase E) |

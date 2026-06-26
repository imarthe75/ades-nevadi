# PROMPT — Frontend Angular del Motor de Horarios (`ades-nevadi`)

> **Prompt de seguimiento.** Úsalo DESPUÉS de que el backend del motor de horarios
> exista (ver `PROMPT-motor-horarios.md`). Pega este documento en Claude Code abierto
> en la raíz del repo `ades-nevadi`. Construye la capa Angular que consume la API de
> horarios: generación, vistas (grupo/docente/aula), editor drag-and-drop con
> validación, lock + regeneración, descargas, suplencias y advisor.

---

## 0) Contexto

El backend es **Spring (Java) con Timefold** y expone la API bajo **`/api/v1/horarios`**
(el módulo ya existe; el motor nativo reemplaza a aSc TimeTables):
- `GET  /api/v1/horarios/grupo/{id}` y `/profesor/{id}` → cuadrícula existente (reutilizar)
- `POST /api/v1/horarios/generar` → `{ corridaId }` (202)
- `GET  /api/v1/horarios/corridas/{corridaId}` → `{ estado, score, ... }` (RUNNING | FACTIBLE(0 hard) | INFACTIBLE)
- `GET  /api/v1/horarios/corridas/{corridaId}/excel` → URL prefirmada (MinIO/S3)
- `GET  /api/v1/horarios/vista/{tipo}/{ref}` → JSON de la cuadrícula (tipo: grupo|docente|aula)
- `POST /api/v1/horarios/corridas/{corridaId}/lock` → fija lecciones (`@PlanningPin`)
- `POST /api/v1/horarios/corridas/{corridaId}/regenerar` → re-resuelve respetando lo fijado
- `POST /api/v1/suplencias` + `GET /api/v1/suplencias/{id}/sugerencias`
- `POST/PATCH/DELETE /api/v1/horarios` (bloque individual) → edición manual (reutilizar)
- (Advisor) el detalle de infactibilidad viene en el cuerpo de `GET .../corridas/{corridaId}` cuando hay `hard < 0`.

> Si algún endpoint difiere, ADÁPTATE a los contratos reales del backend; no inventes campos.
> Lee primero el `HorarioController` / OpenAPI del backend para tipar los modelos del frontend.

---

## 1) Trabaja en 2 FASES. **No escribas código en la Fase 1.**

### FASE 1 — Explorar y diseñar (entregar plan y ESPERAR mi aprobación)
1. Revisa cómo está organizado el frontend: `frontend/src/app` (estructura de features,
   routing, manejo de estado, servicios HTTP, interceptores de auth OIDC), `frontend/projects/
   apex-component-library` (componentes reutilizables disponibles), y el spec
   `.agent/spec/modules/fase-24-interactive-grid/specification.md` (la grilla interactiva que
   debo reutilizar). Revisa también la config de e2e Playwright (`frontend/e2e`) y el estilo
   (`.prettierrc`, `styles.scss`).
2. Confirma convenciones: ¿componentes standalone o módulos? ¿signals o RxJS/NgRx? ¿cómo se
   maneja el scope-plantel y los permisos RBAC en el cliente? ¿i18n? Sigue lo que YA existe.
3. Entrégame un PLAN: estructura del feature `horarios/`, componentes, servicios, modelos,
   rutas, integración con `apex-component-library` y `fase-24-interactive-grid`, y plan de
   pruebas (unit + e2e). Pregunta lo ambiguo. **No codifiques aún.**

### FASE 2 — Implementar (solo tras mi OK)
Sigue las convenciones del repo (mismas que el resto de `frontend/src/app`).

---

## 2) Funcionalidades a construir

### 2.1 Generación de horario
- Pantalla para seleccionar **plantel + ciclo escolar** y lanzar la generación.
- Al lanzar: `POST /horarios/generar`, recibe `task_id`, muestra **estado en vivo** (polling o
  websocket si el backend lo soporta) con indicador de progreso. La generación tarda
  segundos–minutos: NO bloquear la UI.
- Al terminar: mostrar **estado** (OPTIMAL/FEASIBLE) y el **score de calidad** (con la cuenta de
  huecos de docentes), o el panel de **Advisor** si es INFEASIBLE (ver 2.6).

### 2.2 Vistas de horario (grupo / docente / aula)
- Selector de tipo de vista y de la referencia (qué grupo, qué docente, qué aula).
- Render de la **cuadrícula**: columnas = días, filas = bloques horarios (incluyendo recreos y
  comidas claramente etiquetados, igual que el Excel). Colores por materia.
- Las **horas administrativas** (modelo titular) se marcan visualmente (ícono + recuadro), igual
  que en el Excel de referencia.
- Cambio de vista sin recargar; cargar `GET /horarios/{id}/vistas/{tipo}/{ref}`.

### 2.3 Editor manual drag-and-drop (reutilizar `fase-24-interactive-grid`)
- Permitir **arrastrar y soltar** una clase a otra celda.
- **Validación en vivo** al soltar: detectar conflictos (mismo docente o aula a la misma hora,
  violación de ventana horaria, día no permitido, etc.) y mostrar el motivo. Idealmente validar
  contra el backend o replicar las reglas duras en el cliente para feedback inmediato.
- Resaltar celdas válidas/ inválidas mientras se arrastra.

### 2.4 Lock + regeneración parcial
- Permitir **fijar (lock)** clases que ya están bien (toggle por celda; indicador de candado).
- Botón **"Regenerar lo no fijado"**: `POST /horarios/{id}/lock` con las fijadas y luego
  `POST /horarios/{id}/regenerar`; refrescar la vista con el resultado.

### 2.5 Descargas
- Botón **"Descargar Excel"** → `GET /horarios/{id}/excel` (abre la URL prefirmada).
- Opcional: exportar la vista actual a PDF/imagen para impresión.

### 2.6 Advisor / diagnóstico
- Cuando `estado=INFEASIBLE`, mostrar panel con: recursos sobrecargados, cargas que no caben,
  capacidades excedidas, y las **sugerencias de relajación** que devuelve el backend.
- Enlaces de acción rápida (p. ej. "abrir configuración de la regla X").

### 2.7 Suplencias
- Pantalla para **registrar ausencia** de un docente (fecha/franja, motivo).
- Mostrar **sugerencias de cobertura** (`GET /suplencias/{id}/sugerencias`): docentes libres esa
  franja, con su carga de cobertura acumulada (para reparto equitativo). Permitir **asignar** una
  cobertura (override manual) y confirmar.
- Tras asignar: refrescar el horario del día y confirmar que se enviaron las **notificaciones**.

---

## 3) Requisitos transversales
- **Auth OIDC + RBAC scope-plantel**: respeta el interceptor/guard existentes; un usuario solo ve
  y opera sobre sus planteles permitidos. No muestres acciones sin permiso.
- **Estado**: usa el patrón de estado que YA usa el repo (signals / servicios / store). No introduzcas
  una librería nueva sin justificarlo.
- **Reutiliza `apex-component-library`** para botones, tablas, modales, toasts, etc. No reinventes.
- **i18n en español** (textos de UI), siguiendo el mecanismo i18n existente.
- **Responsivo y accesible**: la cuadrícula debe ser usable en pantallas medianas; roles ARIA en el
  drag-and-drop; foco por teclado donde aplique.
- **Manejo de errores y carga**: estados de loading, vacío y error en cada vista; toasts para
  acciones (generar, fijar, regenerar, asignar cobertura).

---

## 4) Pruebas
- **Unit** (las que use el repo: Karma/Jest): servicios HTTP de horarios (mocks de la API),
  lógica de validación de conflictos del editor, mapeo de la cuadrícula.
- **e2e (Playwright)** en `frontend/e2e`: flujo feliz — generar → ver horario → arrastrar una clase
  válida → fijar → regenerar → descargar Excel; y flujo de suplencia — registrar ausencia → ver
  sugerencias → asignar cobertura. Sigue los patrones de `page-objects/` y `fixtures/` existentes.

---

## 5) Criterios de aceptación
- Se puede generar un horario desde la UI y ver su estado/score en vivo sin congelar la pantalla.
- Las tres vistas (grupo/docente/aula) renderizan correctamente con descansos y horas admin marcadas.
- El editor drag-and-drop **rechaza** un movimiento que cause traslape de docente/aula y explica por qué.
- Lock + regenerar respeta lo fijado y actualiza el resto.
- La descarga de Excel funciona vía URL prefirmada.
- Suplencias: registrar ausencia produce ≥1 sugerencia válida (o explica por qué no hay) y permite asignar.
- Todo respeta el scope-plantel y los permisos del usuario.
- Pasan las pruebas unit y e2e nuevas.

---

## 6) Convenciones del repo a respetar
- Estructura y estilo de `frontend/src/app`; componentes y patrones de `apex-component-library`.
- Reutilizar el spec `fase-24-interactive-grid` para la grilla editable.
- Prettier/ESLint del repo; i18n existente; interceptor OIDC y guards RBAC existentes.
- Seguir `.agent/rules/typescript/rules.md` y `CLAUDE.md`.

# Correcciones aplicadas — plan de `2026-07-16_reporte_fiabilidad_3dias_y_plan.md`

**Encargo:** ejecutar el plan de la §5 de ese reporte ("Plan hacia 90-95% de fiabilidad global
esta semana") hasta solventar los huecos BOLA/BFLA de scoping por plantel documentados.

---

## 1. Día 1-2 — Cola larga confirmada ([Cierto]/[Probable] en el reporte)

Aplicado el patrón `AdesUserService#verificarPlantel(user, plantelEntidadId, mensaje)` (solo
nivelAcceso 0 mantiene alcance institucional libre) a los 4 controllers que el reporte señaló
como huecos reales, no hipotéticos:

| Controller | Qué se corrigió |
|---|---|
| `SaludAvanzadaController` | medicamentos, incidentes médicos, psicosocial, tutorías, descargas PDF — antes solo validaban `nivelAcceso`, nunca el plantel del alumno |
| `ExpedienteLaboralController` | listar/crear/detalle/actualizar/documento/eliminar — expone CURP/RFC/salario/IMSS/INFONAVIT; se agregó resolución de plantel vía COALESCE entre `ades_profesores`/`ades_personal_administrativo`/`ades_personal_salud` |
| `DisponibilidadDocenteController` | el "control de rol" de R-5 (2026-07-15) nunca incluyó scoping de plantel — confirmado y corregido en listar/guardar/resumen/cobertura/eliminar |
| `EvalDocenteController` | resumen, crear evaluación, guardar criterios, enviar, plan de mejora — comparaba IDs de espacio equivocado y nunca verificaba plantel para nivelAcceso 1-3 |

## 2. Día 2-3 — Verificación puntual + Biblioteca

- **`BibliotecaController`:** `scopePlantel()` usaba `nivel(u) > 2` (eximía nivel 0, 1 y 2) en vez
  de `> 0` — corregido en el helper y en los 4 chequeos inline (actualizar/eliminar/prestar/
  devolver libro).
- **8 candidatos "por confirmar" del reporte — los 8 resultaron ser huecos reales:**
  `ContactosController` (contactos familiares + expediente médico + expediente-docs),
  `CertificadosController` (listar/emitir/firmar), `AsistenciaController` (`requireAccesoClase`
  nunca verificaba plantel para nivelAcceso 1-3), `AsistenciaPersonalController` (cero scoping),
  `TareaEntregaController` (mismo patrón que `EntregasController`), `ActividadesController`
  (mismo patrón), `PadresController` (calificaciones — nivelAcceso 1-3 sin ningún chequeo),
  `PlanesEstudioController` (incluye planes NEE alternativos), `JustificacionController` (cero
  scoping).

## 3. Día 3 — Barrido de variantes de nombre

El reporte advertía que Biblioteca probó que el grep original (`nivel(u) > 2`, nombres de
variable no estandarizados) no era exhaustivo. Un grep dirigido a la forma
`if (nivel <= 3) return;` / `if (user.getNivelAcceso() <= 3) return;` (el "alcance institucional"
que nunca valida plantel) encontró el **mismo bug replicado en 7 archivos adicionales** que
ninguna ronda previa había tocado:

`EntregasController` (el "canónico" del que los demás copiaron `requireAccesoGrupo`),
`CalificacionesController`, `PlaneacionController`, `GradebookController`, `TareaController`,
`EvaluacionController`.

Los otros 2 umbrales encontrados por el mismo grep (`BibliotecaApplicationService.eliminar`,
`CondicionCronicaApplicationService.eliminar`, ambos `nivelAcceso > 2`) se revisaron y son
**correctos por diseño** — son un piso de rol mínimo (RH/Dirección), no un bypass de scoping; el
scoping de esas dos entidades ya se verifica en el controller antes de llegar ahí.

## 4. Hallazgos colaterales (no en el plan original, encontrados verificando en vivo)

- **HikariCP `maximum-pool-size: 10` insuficiente para carga concurrente real.** Confirmado en
  logs del contenedor durante la corrida E2E: `HikariPool-1 - Connection is not available,
  request timed out after 30000ms (total=10, active=10, idle=0, waiting=18)`. Ampliado a 25
  (`minimum-idle: 8`) — decisión explícita del usuario, considerando que producción tendrá
  cientos de usuarios concurrentes. El BFF conecta directo a `ades-postgres` (bypass de
  pgbouncer, ver comentario ya existente en `docker-compose.yml` sobre el fix pendiente de SCRAM)
  así que este valor sí consume `max_connections` reales de Postgres (default 100) — 25 deja
  margen amplio sin arriesgar al resto de servicios (Authentik/Superset/FastAPI/Celery, que pasan
  por pgbouncer).
- **`GlobalExceptionHandler` sin handler para `NoResourceFoundException`.** Una ruta inexistente
  caía en el catch-all de `Exception` y respondía 500 en vez de 404. Encontrado por
  `10-rbac.spec.ts` (RBAC-13 prueba un endpoint `/api/v1/alumnos/{id}/calificaciones` que no
  existe en las rutas reales del backend — bug del test, pero el 500 en vez de 404 era un bug
  real del backend). Agregado handler dedicado.

## 5. Verificación

- `mvn test`: **555/555 en verde**, sin regresiones, en cada punto de control del proceso.
- `ades-bff` reconstruido y redesplegado en el servidor único (confirmación explícita del usuario
  antes de cada reinicio de contenedor de producción).
- `10-rbac.spec.ts` agregado a `.github/workflows/e2e-tests.yml` junto a los 5 specs que ya
  corrían (antes: 5/21 specs en CI).
- E2E contra el stack real: ver resultado final más abajo (corrida completa de los 21 specs,
  autorizada explícitamente por el usuario tras confirmar que los 2 fallos de la corrida aislada
  de `10-rbac.spec.ts` eran atribuibles a infraestructura, no a los fixes).

<!-- RESULTADO_E2E_PENDIENTE_DE_COMPLETAR -->

## 6. Lo que queda fuera de esta pasada (por decisión del usuario, diferido)

- `audit_aiud` (180 tablas en `PENDIENTE_AIUD`) y prueba de restore de backup — diferidos a
  go-live, tal como documenta el reporte origen.
- Accesibilidad (35-40%) y cobertura de tests medida (JaCoCo/coverage frontend) — próximo foco
  natural, ninguna ronda de auditoría lo ha tocado todavía.
- Extraer el patrón `requireAccesoGrupo`/`requireAccesoClase` (corregido ahora en 7+ archivos
  independientes con copy-paste) a un helper compartido en `AdesUserService`, para que el
  próximo módulo nuevo no reintroduzca el mismo bug.

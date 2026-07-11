# Hallazgo: PATCH de alumnos/profesores/personal-admin/contactos no persistía

**Fecha del hallazgo:** 2026-07-11
**Fecha de corrección:** 2026-07-11 (misma sesión, con autorización explícita)
**Severidad:** 🔴 Alta — pérdida silenciosa de datos en producción
**Estado:** ✅ **CONFIRMADO y CORREGIDO** — desplegado en `ades-bff`

## Resumen

`HikariCP` está configurado con `spring.datasource.hikari.auto-commit: false`
(`backend-spring/src/main/resources/application.yml:50`). En este proyecto eso es
una decisión deliberada: **todo `jdbc.update()` debe correr dentro de un método
anotado `@Transactional`**, o el cambio nunca se confirma (commit) y se pierde
cuando la conexión regresa al pool. Docenas de servicios de escritura en el
proyecto siguen este patrón correctamente (`RiesgoConductualService`,
`BibliotecaApplicationService`, `ProcesosWriteService`, `SistemaWriteService`,
`CalificacionPersistenceAdapter`, etc.).

**Pero la cadena completa de PATCH de alumnos, profesores, personal
administrativo y contactos familiares NO tenía `@Transactional` en ningún
punto:**

| Archivo | Método | Antes | Ahora |
|---|---|---|---|
| `modules/alumnos/application/service/AlumnoApplicationService.java` | `crear`, `actualizar` | ❌ | ✅ |
| `modules/profesores/application/service/ProfesorApplicationService.java` | `crear`, `actualizar` | ❌ | ✅ |
| `modules/personal_admin/application/service/PersonalAdminApplicationService.java` | `registrar`, `actualizar`, `desactivar` | ❌ | ✅ |
| `modules/contactos/application/service/ContactosApplicationService.java` | `registrar`, `actualizar`, `eliminar`, `expedienteMedico`, `actualizarExpedienteMedico`, `upsertDocEstatus` | ❌ | ✅ |

`shared/persona/PersonaUpdateHelper.java` y `PersonalAdminPersistenceAdapter.java`
(las clases "hoja" que hacen el `jdbc.update()`) se dejaron **sin**
`@Transactional` propio — correcto: el `@Transactional` va en el método público
del bean gestionado por Spring que las invoca (capa `ApplicationService`), y
Spring propaga esa misma transacción a las llamadas internas a estos helpers
(propagación `REQUIRED`, el default). Ponerlo también en las clases hoja habría
sido redundante, no incorrecto, pero no es necesario.

## Cómo se descubrió

Como efecto colateral al implementar un backfill de cifrado PII
(`PiiBackfillRunner`): un `ApplicationRunner` sin `@Transactional` que hacía
`jdbc.update()` en un loop, dentro de esta misma app y configuración, ejecutó
el UPDATE 217,400 veces sin que la base de datos cambiara ni una sola vez
(`pii_encryption_status` se mantuvo en `pending: 5178` todo el tiempo). El
backfill se detuvo a tiempo, sin daño a datos reales (ver
`docs/legal/` / commit de PII encryption para el detalle).

## Cómo se confirmó (con evidencia directa, no solo inferencia)

Se escribió una prueba temporal (`TransactionalBugReproTest`, borrada tras
usarse — no forma parte de la suite permanente) que:
1. Insertaba una fila desechable en `ades_personas` (`nombre='ZZZ_TEST_TX_BUG'`,
   con `COMMIT` explícito, fuera del camino sospechoso).
2. Llamaba `PersonaUpdateHelper.actualizarBasico(id, {"estado_civil":"CASADO"})`
   — el método real que usa el PATCH de profesores.
3. Verificaba con una conexión nueva si el cambio persistió.

**Resultado antes del fix:** `estado_civil` seguía en `SOLTERO` (sin cambio) —
bug confirmado con el código real, contra la base de datos real, sin tocar
ningún alumno/profesor existente.

## Cómo se corrigió y se verificó el fix

Se agregó `@Transactional` (import `org.springframework.transaction.annotation.Transactional`)
a los métodos de escritura de los 4 `ApplicationService` listados arriba.

Se verificó con una segunda prueba temporal (`TransactionalFixVerificationTest`,
también borrada) que, a través del **contexto real de Spring** (`@SpringBootTest`,
proxy AOP real — no instanciación manual como en la prueba de reproducción),
un bean `@Transactional` que llama a `PersonaUpdateHelper.actualizarBasico`
ahora sí persiste el cambio. Resultado: `estado_civil` = `CASADO` ✅.

**Suite completa:** 550 tests, 0 fallas relacionadas (2 fallas + 1 error en
`HorarioDomainTest`, módulo de horarios, confirmado sin relación con este
cambio — falla preexistente de validación de `dia_semana`, no tocada hoy).

**Desplegado:** imagen `ades-bff` reconstruida y contenedor recreado, healthy.

## Efecto sobre trabajo previo de esta sesión

- El dual-write de cifrado PII agregado a `PersonaUpdateHelper` /
  `PersonalAdminPersistenceAdapter` (commit `0209ea0`) ahora sí persistirá
  cuando se invoque a través de `AlumnoApplicationService`/`ProfesorApplicationService`/
  `PersonalAdminApplicationService` (que ya tienen `@Transactional`). El
  `PiiBackfillRunner` standalone (`ApplicationRunner`, fuera de un bean con
  `@Transactional`) **sigue teniendo el mismo problema si se ejecuta tal
  cual** — no se corrigió en este pase porque no se autorizó correr el
  backfill sobre datos reales hoy. Antes de activar `PII_BACKFILL_RUN=true`
  de nuevo, hay que envolver su lógica de escritura en un método
  `@Transactional` de un bean real (no se puede anotar `ApplicationRunner.run()`
  directamente por auto-invocación — necesita delegar a un bean inyectado).

## Pendiente / recomendación de seguimiento

No se hizo un barrido exhaustivo de **todo** el proyecto buscando este mismo
patrón (`jdbc.update()`/`jdbc.update` sin `@Transactional` en su cadena) fuera
de los módulos alumnos/profesores/personal-admin/contactos tocados hoy. Vale
la pena una búsqueda dedicada (`grep -rL "@Transactional" ...` cruzado con
`grep -rl "jdbc.update\|jdbc.batchUpdate"`) en una sesión futura para
descartar el mismo bug en otros módulos.

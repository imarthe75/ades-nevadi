# Hallazgo: PATCH de alumnos/profesores/personal-admin probablemente no persiste

**Fecha:** 2026-07-11
**Severidad:** 🔴 Alta — posible pérdida silenciosa de datos en producción
**Estado:** Reportado, NO corregido (por decisión explícita — ver "Por qué no se corrigió ya")

## Resumen

`HikariCP` está configurado con `spring.datasource.hikari.auto-commit: false`
(`backend-spring/src/main/resources/application.yml:50`). En este proyecto eso es
una decisión deliberada: **todo `jdbc.update()` debe correr dentro de un método
anotado `@Transactional`**, o el cambio nunca se confirma (commit) y se pierde
cuando la conexión regresa al pool. Docenas de servicios de escritura en el
proyecto siguen este patrón correctamente (`RiesgoConductualService`,
`BibliotecaApplicationService`, `ProcesosWriteService`, `SistemaWriteService`,
`CalificacionPersistenceAdapter`, etc. — todos con `@Transactional` explícito).

**Pero la cadena completa de PATCH de alumnos, profesores y personal
administrativo no tiene `@Transactional` en ningún punto:**

| Archivo | Método | `@Transactional` |
|---|---|---|
| `modules/alumnos/AlumnoController.java` | `patch(...)` | ❌ |
| `modules/alumnos/application/service/AlumnoApplicationService.java` | `actualizar(...)` | ❌ |
| `modules/profesores/ProfesorController.java` | `patch(...)` | ❌ |
| `modules/profesores/application/service/ProfesorApplicationService.java` | `actualizar(...)` | ❌ |
| `shared/persona/PersonaUpdateHelper.java` | `actualizar(...)`, `actualizarBasico(...)` | ❌ |
| `modules/personal_admin/PersonalAdminController.java` | `create(...)`, `patch(...)` | ❌ |
| `modules/personal_admin/application/service/PersonalAdminApplicationService.java` | `registrar(...)`, `actualizar(...)` | ❌ |
| `modules/personal_admin/infrastructure/outbound/persistence/PersonalAdminPersistenceAdapter.java` | `createPersona`, `updatePersona`, `createEmpleado`, `updateEmpleado` | ❌ |

Verificado con `grep -c "@Transactional"` sobre los 8 archivos → 0 en todos.

## Cómo se descubrió

No se descubrió probando el endpoint real (no se llegó a esa prueba — ver
sección de verificación pendiente). Se descubrió como efecto colateral al
implementar un backfill de cifrado PII (`PiiBackfillRunner`, ver
`docs/legal/` y commit relacionado): un `ApplicationRunner` sin
`@Transactional` que hacía `jdbc.update()` en un loop, dentro de esta misma
app y configuración, ejecutó el UPDATE **217,400 veces sin que la base de
datos cambiara ni una sola vez** (`SELECT pii_encryption_status, COUNT(*)`
se mantuvo en `pending: 5178` todo el tiempo). Eso demuestra empíricamente,
en este entorno exacto, que `jdbc.update()` fuera de `@Transactional` no
persiste — sin haber tocado datos reales de alumnos (el backfill se detuvo
a tiempo, sin daño, ver commit de PII encryption para el detalle).

## Por qué esto probablemente afecta el PATCH real de alumnos/profesores

`PersonaUpdateHelper.actualizar/actualizarBasico` son estructuralmente
idénticos al código que falló en el backfill: `jdbc.update(...)` plano, sin
`@Transactional` en ningún punto de la cadena que los invoca. No hay razón
estructural para que se comporten distinto. **Esto es una hipótesis con
evidencia fuerte pero indirecta — no se confirmó con una prueba HTTP real
contra el endpoint de producción** (ver siguiente sección).

## Por qué no se corrigió ya

El usuario, al reportársele este hallazgo, pidió explícitamente **detener
el trabajo y solo documentarlo** — no tocar el código de persistencia de
alumnos/profesores/personal-admin sin autorización explícita adicional, dado
que toca datos reales de personas (incluye menores).

## Qué falta para confirmar y corregir (próxima sesión)

1. **Confirmar con una prueba real:** login con un usuario admin real,
   `PATCH /api/v1/alumnos/{id}` a un campo no crítico (ej. `pronombres` o
   `estado_civil`) de un registro de prueba, verificar en BD
   (`SELECT ... FROM ades_personas WHERE id=...`) si el cambio persistió.
2. Si se confirma: agregar `@Transactional` a los métodos de escritura
   listados arriba, siguiendo el patrón ya establecido en el resto del
   proyecto (ej. `SistemaWriteService`, `CalificacionPersistenceAdapter`).
   Dónde ponerlo exactamente importa: debe ir en un método público de un
   bean gestionado por Spring, invocado a través del proxy (no
   autoinvocación `this.metodo()` dentro de la misma clase) — revisar la
   cadena completa, no solo el método hoja.
3. Recompilar, correr los tests existentes (`ContactosDomainTest`,
   `PersonalAdminDomainTest`, etc.), y verificar con la misma prueba HTTP
   del paso 1 que ahora sí persiste.
4. Buscar si hay OTROS módulos con el mismo patrón (`jdbc.update()` sin
   `@Transactional` en su cadena) fuera de alumnos/profesores/personal-admin
   — no se hizo un barrido exhaustivo de todo el proyecto, solo de las
   rutas tocadas hoy.

## Efecto sobre el trabajo de esta sesión

- La validación agregada hoy en `ValidationUtils`/`AdesFormatDirective`
  (frontend + backend) sigue siendo correcta y útil — rechaza formato
  inválido ANTES de llegar al `jdbc.update()`, independientemente de este bug.
- El **backfill de cifrado PII** (`PiiBackfillRunner`) quedó
  intencionalmente sin ejecutar sobre datos reales — no debe correrse hasta
  resolver esto, ya que dependería del mismo mecanismo roto.
- El dual-write de cifrado agregado a `PersonaUpdateHelper` /
  `PersonalAdminPersistenceAdapter` hoy tiene el MISMO problema estructural
  que el resto del archivo — no persistirá hasta que se agregue
  `@Transactional`.

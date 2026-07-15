# Validación de la remediación de seguridad — 2026-07-15

**Qué se validó:** el "Walkthrough de Remediación de Seguridad" que reporta R-1 a R-17 como
ejecutados. **Método:** verificación en vivo contra código, BD y contenedores — no contra el
walkthrough (que en varios puntos sobreafirma lo hecho).

## Veredicto

La mayor parte de la remediación **es real** (a diferencia de reportes anteriores fabricados):
los archivos existen, las migraciones se aplicaron, el ledger se endureció de verdad. **PERO la
remediación introdujo una regresión crítica que dejó el backend caído**, y varias afirmaciones
del walkthrough están sobredimensionadas. La regresión ya fue corregida en esta validación.

---

## 🔴 CRÍTICO — Backend caído por crash-loop (detectado y CORREGIDO en esta validación)

- **Síntoma:** `ades-bff` en crash-loop (RestartCount subía 162→164 en 35 s, siempre "Up ~12s",
  nunca llegaba a "Started"). El API REST estuvo **completamente caído** desde que se aplicó la
  remediación. Esto **contradice** la afirmación del walkthrough "26 contenedores levantaron sin
  errores y pasaron los healthchecks".
- **Causa raíz:** la migración `138_fix_suplencias_and_cleanup.sql` (líneas 52-55) hace
  `DROP COLUMN` de `creado_por/creado_el/actualizado_por/actualizado_el` en `ades_suplencias` para
  "estandarizar" a columnas canónicas, **pero no se actualizó la entidad JPA** `Suplencia.java`,
  que sigue mapeando esas 4 columnas. Con `ddl-auto=validate`, Hibernate falla
  ("missing column [actualizado_el]") → la SessionFactory no arranca → crash-loop.
- **Corrección aplicada:** `db/migrations/140_hotfix_suplencias_entity_columns.sql` re-agrega las
  4 columnas (backfilleadas desde las canónicas, sin tocar las canónicas ni `audit_biu`). `bff`
  reiniciado → **RestartCount=0, estable, "Tomcat started on port 8080"**. Backend restaurado.
- **Deuda pendiente (fix limpio):** el hotfix deja dos convenciones de auditoría coexistiendo en
  la tabla. El fix definitivo es remapear las 4 `@Column` de `Suplencia.java` a las canónicas
  (`creadoEl→fecha_creacion`, `creadoPor→usuario_creacion`, `actualizadoEl→fecha_modificacion`,
  `actualizadoPor→usuario_modificacion`), rebuild del bff, y luego dropear las 4 legacy. Honra el
  objetivo de R-4 sin romper la app.
- **Lección:** toda migración que altere columnas de una tabla con entidad JPA debe validarse
  **arrancando la app**, no solo con `mvn compile`. Es exactamente la misma clase de fallo que el
  crash-loop del ObjectMapper documentado el 07-14.

---

## ✅ Verificado como real y correcto

| R | Qué | Evidencia en vivo |
|---|---|---|
| R-1 | Ledger a SHA-256 | `fn_auditoria_aiud` usa `sha256`; columnas `log_seq`/`hash_anterior`/`changed_fields` presentes; `fn_verificar_cadena()` existe y corre (0 registros alterados) |
| R-4 | `ades_suplencias` auditada + tabla PII eliminada | Columnas canónicas presentes; `ades_pii_encryption_backup_20260619` ya no existe |
| R-3 | Backup + off-site | Script con subida a OCI Object Storage; backup completo diario verificado |
| R-5 | Controllers con chequeos añadidos | Los 5 controllers modificados tienen `resolveUser`/`nivelAcceso`/scoping añadidos (no son cambios cosméticos) |
| R-8 | `SECURITY.md` | Existe en raíz |
| R-11 | Contract-check en CI | `check-api-contracts` presente en `e2e-tests.yml` |
| — | Integridad de datos | 5237 personas / 2028 contactos / 40092 calificaciones intactas tras el `docker system prune` |

---

## 🟠 Huecos que SIGUEN abiertos

1. **R-5 — cobertura, no solo los 5 confirmados.** Se corrigieron los 5 controllers que el
   análisis previo ya había señalado, pero **~42 de los 82 controllers nunca se auditaron** para
   BOLA/BFLA. El walkthrough no amplió la cobertura; el hueco sistémico persiste. Además, los 5
   fixes tienen los chequeos añadidos pero su **correctitud** (¿el chequeo está en la operación
   correcta? ¿valida ownership real, no solo rol?) no está verificada a fondo — merece un repaso.
2. **R-7 — sobreafirmado.** El walkthrough dice "reemplazadas TODAS las `:latest`", pero **quedan
   9 imágenes en `:latest`** (11 pineadas a digest). Parcial.
3. **R-6 — casi completo.** 7 de 8 bloques `server` de nginx tienen CSP; **1 sigue sin CSP**
   (verificar si es un bloque de solo-redirección HTTP→HTTPS, en cuyo caso es aceptable).
4. **R-1 — instrucción no cumplida del todo.** Se pidió **apagar los 3 `audit_aiud` activos** y
   **truncar `log_auditoria` a 0** (etapa de desarrollo). Siguen 3 triggers `audit_aiud` activos
   (`ades_horario_corrida`, `ades_webhooks`, `ades_webhook_logs`) y el log tiene 3 filas. Bajo
   impacto (tablas sin PII), pero es una desviación de lo acordado.
5. **Deuda de suplencias — RESUELTA (2026-07-15, continuación de sesión):** `Suplencia.java`
   remapeada a `AdesBaseEntity` (columnas canónicas), columnas legacy eliminadas
   (`142_drop_suplencias_legacy_audit_columns.sql`), bff reconstruido y estable.
6. **Higiene de migraciones — RESUELTA:** las migraciones con nomenclatura por fecha
   (`20260715_0001/0002/0003`, `20260713_0001/0002`) se renombraron a la convención secuencial
   de 3 dígitos, preservando el orden de dependencia real: `137_harden_ledger.sql`,
   `138_fix_suplencias_and_cleanup.sql`, `139_ledger_delta_y_verificacion.sql`,
   `140_hotfix_suplencias_entity_columns.sql`, `141_fix_usuario_auditoria_guc.sql`,
   `142_drop_suplencias_legacy_audit_columns.sql`, `143_apagar_aiud_dev_truncate_ledger.sql`,
   `144_fn_reconciliar_tabla.sql`, `145_fix_asignar_triggers_quoting.sql`,
   `146_fix_sepomex_municipality_keys.sql`, `147_normalize_sepomex_3nf.sql` (estas 2 últimas sin
   dependencia real con 130-145, reordenadas al final sin riesgo).
7. **`ades_log_autenticacion`** sigue sin `audit_biu` (`PENDIENTE_BIU`) — preexistente, menor.

## Hallazgos adicionales (continuación de sesión, 2026-07-15)

- **CRÍTICO — `usuario_creacion`/`usuario_modificacion` nunca reflejaban al usuario real**, en
  las 57 entidades JPA que extienden `AdesAuditEntity`/`AdesBaseEntity` (121 tablas con
  `DEFAULT CURRENT_USER` a nivel de columna). Dos causas combinadas: (1) `AuditSessionInterceptor`
  propagaba `jwt.getSubject()` a un GUC de sesión (`app.current_user`) que `fn_auditoria_biu()`
  nunca leía (código muerto desde su creación); (2) el `DEFAULT CURRENT_USER` de columna
  pre-llenaba `NEW.usuario_creacion` antes de que el trigger corriera, tapando cualquier
  `COALESCE` contra NULL. Efecto real: `usuario_creacion` siempre quedaba en `'ades_admin'`
  (el rol de conexión a BD) y `usuario_modificacion` quedaba **congelado para siempre** en el
  valor de creación en cualquier entidad con `updatable=false` — el audit trail de todo el
  sistema no podía decir quién modificó nada. Corregido: interceptor usa `auth.getName()`
  (ya resuelto a `preferred_username`), y `fn_auditoria_biu()` (migración 141) prioriza el GUC
  sobre el valor de columna, tratando un valor igual a `CURRENT_USER` como "no provisto".
  Verificado end-to-end (INSERT, UPDATE por otro usuario, fallback sin GUC para jobs).
- **Bug real en `auditoria.asignar_triggers()`** (migración 145): usaba `%I` en vez de `%s` en
  el `format()`, por lo que `asignar_triggers('public.ades_x')` fallaba con "relation does not
  exist" — el comando exacto que CLAUDE.md documenta para activar la auditoría en el go-live.
  Corregido, alineado con `asignar_biu()` (que ya usaba `%s` correctamente).
- **Hueco cerrado:** `auditoria.fn_reconciliar_tabla()` (migración 144) — compara el estado vivo
  de cada fila contra su último `executednewdata` en el log, detectando manipulación directa de
  una tabla de negocio que hubiera dejado el log intacto (amenaza que `fn_verificar_cadena()` no
  cubre, ya que esa solo valida integridad interna del log). Verificado que detecta un tampering
  simulado (bypass de triggers vía `session_replication_role`).

---

## Recomendaciones

1. **Ya hecho:** backend restaurado (hotfix 137). Confirmar en UI que el módulo de suplencias
   guarda bien.
2. **Corto plazo:** fix limpio de la entidad `Suplencia` (remapear + dropear legacy); completar
   R-7 (9 imágenes restantes); cerrar el 1 bloque nginx sin CSP; apagar los 3 `audit_aiud` +
   truncar log si sigue en etapa de desarrollo.
3. **Medio plazo:** la auditoría BOLA/BFLA de los ~42 controllers restantes sigue siendo el hueco
   de seguridad más grande — es el trabajo de fondo pendiente antes del go-live.
4. **Proceso:** ninguna migración que altere columnas se da por buena sin **arrancar la app**; el
   guardarraíl de contratos (R-11) no cubre la capa entidad↔esquema, solo Angular↔DTO.

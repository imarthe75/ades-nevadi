# Auditoría Integral Post-Migración — 2026-07-12

Servidor migrado el 2026-07-10 de `129.213.35.140` a `163.192.138.130` (ver
`docs/MIGRACION_2026_07_10.md` y siguientes). Esta auditoría verifica en vivo
qué quedó pendiente de esa migración y qué falta por desarrollar según el
catálogo de casos de uso.

---

## 1. Infraestructura — estado verificado en vivo

| Ítem | Estado |
|---|---|
| 23 servicios definidos en `docker-compose.yml` | ✅ Todos `Up`/healthy (Postgres, Valkey, Authentik x2, BFF, FastAPI, Frontend, Portal, Superset, SeaweedFS, nginx, certbot, Grafana, Prometheus, exporters, pgbouncer, celery x3, ntfy) |
| DNS `ades.setag.mx` | ✅ Apunta correctamente a la nueva IP `163.192.138.130` |
| TLS Let's Encrypt | ✅ Certificado válido hasta 2026-10-08, HTTPS real probado (200 OK, headers de seguridad presentes) |
| OIDC Authentik (`ades-frontend`, `superset`) | ✅ Ambos issuers responden `.well-known/openid-configuration` correctamente — **contradice** la nota de "pendiente" en `docs/RESUMEN_FINAL_MIGRACION_2026_07_10.md`; ya quedó resuelto |
| PostgreSQL | ✅ 191 tablas `ades_*`, 142 archivos de migración en disco todos aplicados, datos reales cargados (2,028 alumnos, 78 grupos, 98 materias) |
| Backend Spring compila | ✅ `mvn -o compile` exitoso con el código actualmente en el working tree |
| Frontend Angular compila | ✅ `ng build --configuration production` exitoso |
| `validate-bootstrap.sh` | ✅ 14/14 (el único "⚠ certificados no encontrados" es un **falso negativo**: el script corre sin `sudo` y `/etc/letsencrypt/archive` es `700 root:root`, por eso no puede seguir el symlink — el certificado sí existe y sí funciona) |

---

## 2. Hallazgos que requieren acción

### 🔴 Crítico — Backups automáticos NO están funcionando en el servidor nuevo
- `scripts/backup-ades.sh` escribe a `BACKUP_DIR="/data/backups"`, que **no existe** en este servidor.
- No hay `crontab` (ni de `ubuntu` ni de `root`) ni `systemd timer` que dispare el script — solo existe `dpkg-db-backup.timer`, ajeno a ADES.
- `./backups/` solo tiene 3 dumps manuales puntuales (pre-migraciones de país/PII), no un esquema recurrente.
- El propio catálogo de casos de uso (`docs/use_case/ADES_Nevadi_Catalogo_Casos_Uso_v1.md`, CU **AD-015 "Backup automático diario BD"**) lo marca como `✅ 2026-06-11` — es decir, **funcionaba en el servidor anterior y no se migró** el cron/timer al nuevo servidor.
- **Acción sugerida:** crear el cron (`0 2 * * * /opt/ades/scripts/backup-ades.sh full`) o systemd timer, y corregir `BACKUP_DIR` a una ruta que exista y tenga espacio (hay 49G libres de 62G en `/opt/ades`).

### 🟡 Auditoría completa (`audit_aiud`) desactivada en todas las tablas salvo 3
- `SELECT * FROM auditoria.reporte_cobertura()`: 183 de 186 tablas elegibles solo tienen `audit_biu` (dev), no `audit_aiud` (producción). `auditoria.log_auditoria` tiene **0 filas**.
- Esto es *consistente* con `ENVIRONMENT=development` en `.env` — no es un bug, es la regla 6 de `CLAUDE.md` funcionando como está escrita.
- **Pero**: este servidor sirve datos reales de 2,028 alumnos por HTTPS público con login OIDC real, y el proyecto reclama cumplimiento LFPDPPP (CU AD-013, PII cifrado backfill 5178/5178 completado 2026-07-12). Sin `audit_aiud`, no hay bitácora inmutable de quién leyó/modificó qué dato personal — **vale la pena que decidas explícitamente** si este ambiente debe tratarse como producción (y correr `auditoria.asignar_triggers()` + `ENVIRONMENT=production`) o si conscientemente sigue siendo "desarrollo" mientras el dato real ya está expuesto.

### 🟡 Trabajo grande sin commitear (riesgo de pérdida silenciosa)
- Hay **56 archivos staged** sin commit correspondientes a toda la migración: `docker-compose.yml` reescrito, 11 migraciones nuevas (119–129), fixes de `@Transactional`/`@EntityGraph`, `bootstrap.sh`, `validate-bootstrap.sh`, `init-certbot.sh`, 7 documentos de migración, cambios en Authentik provisioning.
- Además hay **cambios unstaged** encima de eso en `docker-compose.yml`, `carbone.py`, `expediente.py` y 3 componentes Angular (`alumno-perfil`, `domicilio`, `profesor-perfil`) — es decir, ediciones más nuevas que ni siquiera están en el índice.
- Si algo destructivo tocara el working tree (`git clean`, reset, fallo de disco) se perdería silenciosamente días de trabajo de migración. Backend y frontend ya compilan con este estado, así que es un buen punto para commitear.
- **No lo comiteo yo mismo** — es tu decisión partir esto en commits lógicos (p.ej. "migración de servidor", "fixes @Transactional/@EntityGraph", "migraciones 119-129") o hacerlo todo junto; dime si quieres que lo prepare.

### 🟡 Bitácora `.agent/STATE.md` desactualizada ~10 días
- El último "Rito de Cierre" registrado es de la sesión **2026-07-02**. No hay entradas para: la migración de servidor (07-10), el fix de `@Transactional` en PATCH (07-11, commits `3ab4363`/`8044518`/`0441ca8`), ni el backfill de cifrado PII (07-12, commit `440f264`).
- Esto rompe la "Visibilidad de estado" (heurística #1 de `CLAUDE.md`) para la próxima sesión que arranque leyendo `STATE.md`.

### 🟢 Documentación con IP/referencias obsoletas
- `CLAUDE.md:10` y `.agent/CONTEXT.md:235` siguen mostrando `129.213.35.140` (servidor anterior) en vez de `163.192.138.130`.
- `CLAUDE.md` referencia `/AUDITORIA_ADES_2026/` (4 documentos) como fuente de la sección "OPTIMIZACIÓN AL 100%" — **ese directorio no existe** en este servidor. O se perdió en la migración o nunca se migró fuera de la sesión que lo generó.

---

## 3. Checklist "16 puntos de optimización" (auditoría interna de `CLAUDE.md`)

Verificación en vivo contra las metas que el propio `CLAUDE.md` define como bloqueantes de merge:

| Punto | Meta | Medido ahora | Estado |
|---|---|---|---|
| `@EntityGraph` (N+1) | ≥ 20 | **28** | ✅ Pasa |
| `implements OnDestroy` (memory leaks) | ≥ 70 | **7** | ❌ Sigue muy por debajo — bloqueante de merge según la propia regla |
| SQL concatenation (`'+'`) | 0 | **0** | ✅ Pasa |
| `ChangeDetectionStrategy.OnPush` (fase 2) | — | 79 | informativo |
| `@Cacheable` (fase 2) | — | 15 | informativo |
| `saveAll` batch (fase 2) | — | 3 | bajo, no bloqueante |

`OnDestroy` sigue siendo la brecha más grande de la auditoría de 16 puntos —no relacionada con la migración, es deuda técnica preexistente que el propio checklist marca como crítica.

---

## 4. Qué falta por desarrollar (funcionalidad, no infraestructura)

Según `docs/use_case/ADES_Nevadi_Catalogo_Casos_Uso_v1.md` (192/230 CU = 83.5%), quedan ~38 casos de uso. Los explícitamente marcados `⏳` (pendiente, no solo estimado):

- **PE-016** — Verificación de no-adeudo en reinscripción (única marcada `⏳` entre los "críticos"; el resto de esa tabla ya está `✅`).
- Los bloques "Altos" y "Medios" del documento (detección de plagio interno/Turnitin, predicción de abandono escolar vía IA, portal padre multi-hijo, documentos escaneados con OCR en MinIO, foros por materia, programa de bienestar) no tienen columna de estado explícita en la tabla — dado que varias ya aparecen resueltas en `STATE.md` de sesiones posteriores (plagio Jaccard, foros — mig 120 `foros_materia_id` está staged ahora mismo, eventos de bienestar en mig 104-113), **ese documento de casos de uso quedó desactualizado** frente al avance real; antes de planificar la fase 27-32 valdría la pena refrescarlo.

De `STATE.md` (sesión 2026-07-02), pendientes explícitos aún abiertos:
- Revisar 13 hallazgos "Validation Missing" + 4 "SEP/Nevadi Ambiguity" de la exploración cognitiva NIM (leads sin confirmar).
- Decidir limpieza del catálogo paralelo de 27 materias clásicas de Secundaria sin uso real.
- Generar actividad de gradebook para semestres 5-6 de Preparatoria (tienen plan activo pero cero calificaciones).
- Distinción visual cromática SEP vs Nevadi en calificaciones/planes de estudio (hallazgo crítico #2 de esa auditoría, sigue sin marcarse resuelto).

---

## 5. Recomendaciones priorizadas

1. **Backups automáticos** — corregir `BACKUP_DIR` y programar el cron/systemd timer. Es el gap más concreto causado por la migración (regresión de un CU que ya estaba resuelto).
2. **Decidir el estatus real de "producción"** — si el servidor sirve datos reales de alumnos, considera correr `auditoria.asignar_triggers()` y `ENVIRONMENT=production`, o documentar explícitamente por qué se mantiene en modo desarrollo pese al tráfico real.
3. **Commitear el trabajo de migración** — 56 archivos staged + 6 unstaged representan días de trabajo sin red de seguridad de git. Backend y frontend ya compilan sobre ese estado.
4. **Actualizar `STATE.md`, `CLAUDE.md` y `CONTEXT.md`** con la IP nueva y un resumen de las sesiones 07-10/07-11/07-12 para no perder trazabilidad.
5. **Cerrar la brecha de `OnDestroy`** (7 de 70) — deuda técnica marcada como bloqueante por el propio proyecto, independiente de la migración.
6. Refrescar `docs/use_case/ADES_Nevadi_Catalogo_Casos_Uso_v1.md` contra el avance real antes de usarlo para planear fases 27+.

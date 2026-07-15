# Análisis honesto de entregabilidad — 2026-07-15

**Pregunta del dueño del proyecto:** si hoy se entrega ADES al cliente (Instituto Nevadi),
¿puede usarse de forma funcional, sin fallas de guardado, sin inconsistencias de diseño o
cálculo? ¿Qué falta? ¿Qué fiabilidad tiene?

**Método:** verificación en vivo contra código, base de datos y git (4 investigaciones en
paralelo), no contra lo que afirman los documentos de progreso. Hoy = 2026-07-15.

---

## Veredicto ejecutivo

**El sistema NO está listo para una entrega formal sin acompañamiento, pero SÍ está en
condiciones de un piloto supervisado.** Los flujos centrales (calificaciones, tareas,
asistencias, personas, conducta, contactos, expediente médico) fueron auditados a fondo y
corregidos en la sesión del 07-14/15 — ahí la probabilidad de que "falle un guardado" es hoy
baja. El riesgo residual está en: (1) el ~57% de controllers que nunca recibió auditoría de
autorización, donde un muestreo de hoy encontró huecos BFLA/BOLA reales nuevos; (2)
operación (backups automáticos rotos, log de auditoría completo apagado); y (3) deuda de
usabilidad/accesibilidad.

**Fiabilidad estimada (defendible con la evidencia de abajo):**

| Dimensión | Estimación | Base |
|---|---|---|
| Funcional — flujos core auditados | ~90% | 43+26+2 bugs corregidos, 555/555 tests, verificación en vivo por API |
| Funcional — módulos no auditados (57% del sistema) | ~75% | Sin verificación equivalente; el muestreo no halló guardados rotos nuevos, pero la cobertura es parcial |
| Seguridad / autorización | ~60-65% | 26 BOLA/BFLA corregidos, pero muestreo de hoy halló 5+ casos nuevos en módulos no auditados |
| Operación (backups, auditoría, despliegue) | ~65% | Backups SÍ funcionan (corregido: backup completo diario verificado, falta copia off-server); `audit_aiud` apagado en 176 tablas |
| Usabilidad / accesibilidad | ~40% | ARIA en 1 de 80 componentes; sin tooling a11y activo en CI |
| **Global "entregable hoy sin supervisión"** | **~70-75%** | — |

---

## 1. ¿Fallarán los guardados de información?

**En los flujos core: hoy ya no, con alta confianza.** La sesión del 07-14/15 encontró y
corrigió guardados que estaban rotos desde siempre (alta de contactos familiares, planes de
mejora, sanciones, criterios de evaluación docente 360°, opción "Laboratorio" del gradebook,
bulk-import de materias, tardanzas en asistencia) y lo verificó con la app redesplegada.

**En el resto del sistema: probablemente no, pero sin garantía.** El muestreo independiente
de hoy sobre 8 módulos no auditados **no encontró ningún guardado roto nuevo** (patrón
DTO vs NOT NULL contenido), aunque encontró comentarios en código (`CrearEvaluacionUseCase`,
`RegistrarCapacitacionUseCase`) que confirman que el mismo bug se siguió parchando ad-hoc
fuera de los dominios documentados — el problema era sistémico y la cobertura de la
corrección no es demostrablemente total.

**Riesgo transversal confirmado hoy (nuevo):** `ades_suplencias` no tiene columnas de
auditoría (viola Regla Mandatoria #3) y `ades_log_autenticacion` no tiene ni el trigger
`audit_biu` básico. `DisponibilidadApplicationService` hace multi-escritura
(softDelete + inserts + update) con el `@Transactional` colocado en el controller y no en el
caso de uso — funciona, pero es exactamente la colocación frágil que produjo el bug de
personas del 07-11.

## 2. ¿Inconsistencias de diseño o cálculo?

- **Cálculos:** los dos bugs de cálculo graves conocidos están corregidos y verificados en
  BD viva: `minimo_aprobatorio` de Preparatoria (mig. 134, **ya aplicada en el servidor
  público** — verificado hoy: `PREPARATORIA | 10.0 | 6.0`) y la escala 0-10 confirmada
  consistente. Kardex, boletas y reporte 911 **no** leen `minimo_aprobatorio` (verificado
  por grep) — el pendiente 0.4 del plan puede cerrarse.
- **Diseño:** D6 (date pickers) corregido completo; D7 (colores hex) 132/479 usos
  tokenizados, 347 pendientes de decisión de paleta; D8 (estilos duplicados) con
  infraestructura pero extracción diferida. Nada de esto rompe funcionalidad; es deuda
  cosmética/mantenibilidad.

## 3. Del documento 2026-07-14 — ¿hay correcciones necesarias?

El documento en sí es de alta calidad y sus veredictos se sostienen (verificados hoy).
Correcciones/actualizaciones puntuales:

1. **Punto 0.2 (aplicar mig. 134 en producción): ya está cumplido de facto** — el host de
   trabajo ES `ades.setag.mx` (IP pública 163.192.138.130 = DNS, nginx sirviendo el
   `server_name` con certbot). Conviene actualizar el doc y, más importante, **resolver la
   ambigüedad terminológica**: CLAUDE.md llama a este host "servidor desarrollo" y el doc
   07-14 lo llama "producción-compartida". Si no existe otro servidor, esto ES producción y
   debe tratarse como tal (incluye activar `audit_aiud`, ver §4).
2. **Punto 0.4 (kardex/boletas/911 con minimo_aprobatorio viejo): verificado hoy, no
   aplica** — puede marcarse cerrado.
3. **Pendientes que siguen abiertos tal como el doc los dejó:** 0.3 (QA manual en UI de
   Gradebook Preparatoria), 0.5 (confirmación de escala 0-10 con dirección Nevadi), Fase 2
   (tipos generados: infraestructura lista, **adopción real = 0 componentes**), Fase 4
   (E2E: solo 5 de 21 specs corren en CI; sin corrida completa por rol), Fase 6
   (`check-api-contracts.js` **no** está en ningún workflow de CI).
4. **Corrección al CLAUDE.md derivada de esta verificación:** el comando de verificación de
   OnDestroy que prescribe (`grep -r "implements OnDestroy"`) está roto — solo detecta 7/79
   porque no matchea `implements OnInit, OnDestroy`. La remediación real SÍ es 79/79 (100%),
   pero cualquiera que siga el CLAUDE.md al pie de la letra concluiría falsamente que se
   perdió la remediación. Debe cambiarse a `grep -rlE "implements.*OnDestroy" frontend/src | wc -l`.

## 4. De otras auditorías — pendientes reales aún abiertos

**De la auditoría post-migración 07-12 (verificado en vivo 07-15 — CORREGIDO):**
- **Backups: NO están rotos.** La auditoría del 07-12 los halló rotos pero esa misma sesión
  los reparó: `scripts/backup-ades.sh` reescrito, cron `0 2 * * *` activo, y hay un backup
  completo de hoy (2026-07-15 02:00) con dump PostgreSQL de 33M + Valkey + config + volúmenes,
  retención 7/4. Lo pendiente es **hardening** (copia fuera del servidor + prueba de restore),
  no reparación. (Mi valoración preliminar de "backups rotos" era incorrecta — corregida aquí.)
- **`audit_aiud` desactivado en 176 tablas** (hoy: 176 PENDIENTE_AIUD, 3 COMPLETO). Confirmado
  que este host es producción (ver §3.1), así que el log de auditoría completo exigido por las
  propias reglas del proyecto está apagado en producción — es el pendiente operativo real
  (R-1 del plan).
- **`STATE.md` congelado en la sesión 2026-07-06** — no refleja nada del trabajo del
  07-08 al 07-15. El "rito de cierre de sesión" del proyecto no se está cumpliendo.

**Hallazgo documental importante:** los 14 documentos de `docs/progreso/` y `docs/roadmap/`
con narrativa "Semanas 1-6, score 72→82/100" (incluidos los fechados 07-22 y 07-23, en el
futuro) **fueron todos comiteados el 2026-07-09 el mismo día** — son una simulación/plan
escrito como bitácora, no historial real (reclaman "86+ specs E2E" cuando existen 36, y
presupuestos de $350-800K USD incompatibles con un proyecto donado). No deben usarse como
evidencia de avance ante el cliente. El reporte fabricado
`REPORTE_AUDITORIA_SISTEMA_COMPLETO.md` sí fue revertido correctamente (confirmado ausente).

**De docs/security/ (junio):** superados por la auditoría 07-14; contienen placeholders de
consultoría genérica (security@, cto@) que no corresponden al equipo real.

## 5. Hallazgos NUEVOS del muestreo independiente de hoy (sin corregir)

El 57% de los controllers (47 de 82) nunca recibió auditoría BOLA/BFLA. Muestreo de 8:

| # | Hallazgo | Ubicación | Impacto |
|---|---|---|---|
| N-1 | Crear/cerrar evaluación docente 360° sin verificar `nivelAcceso` ni que `evaluadorId` = usuario autenticado | `EvalDocenteController.java:78-136` | Cualquier autenticado puede suplantar a un evaluador |
| N-2 | Escrituras de biblioteca (editar/eliminar/prestar/devolver) sin scoping por plantel — solo los GET lo tienen | `BibliotecaApplicationService.java:63-159` | BOLA cross-plantel por UUID |
| N-3 | Crear/actualizar capacitaciones docentes sin chequeo de rol | `CapacitacionDocenteController.java:65-151` | Cualquier autenticado registra capacitaciones ajenas |
| N-4 | Crear/eliminar badges y solicitar licencias sin chequeo de rol | `BadgeController.java:92-119`, `LicenciaPersonalController.java:71-86` | BFLA |
| N-5 | `listarCambiosGrupo` sin `requireAcceso()` (único del controller) | `MovilidadController.java:281-293` | Lectura sin control de rol |
| N-6 | `ades_suplencias` sin columnas de auditoría; `ades_log_autenticacion` sin `audit_biu` | BD viva (`reporte_cobertura()`) | Viola Reglas Mandatorias #3-4 |

(`Estadistica911Controller` y `KardexController` salieron limpios — el patrón correcto sí
existe y está bien aplicado donde se aplicó.)

## 6. Cumplimiento de estándares (verificado en vivo)

| Categoría | Veredicto | Evidencia clave |
|---|---|---|
| Desarrollo/Calidad | 🟡 Cumple parcialmente | Fase 1 de los 16 puntos real (EntityGraph 28, OnDestroy 79/79, SQL concat 0; OnPush 79/79 — mejor de lo declarado). Pero: sin cobertura de tests medida (no hay JaCoCo ni coverage frontend), documentación de código muy por debajo de la regla #17 (servicios con 0-4 javadoc) |
| Seguridad | 🟡 Cumple parcialmente | Rate limiting real (Spring + FastAPI), headers nginx (HSTS/XFO/XCTO) presentes. Pero: CSP solo en 2 de ~6 vhosts, 11 imágenes Docker en `:latest` (viola regla supply-chain propia), sin SECURITY.md, y los hallazgos N-1..N-5 de arriba |
| Usabilidad/Accesibilidad | 🔴 No cumple | ARIA en 1 de 80 componentes (~1.3%); `@axe-core/playwright` instalado pero sin gate activo; heurísticas cognitivas del CLAUDE.md sin verificación sistemática |
| CI/CD/Testing | 🟡 Cumple parcialmente | 3 workflows reales (e2e parcial, security-audit, security). Pero: solo 5/21 specs E2E en CI, guardarraíl de contratos fuera de CI, branch protection no verificable, cobertura no medida |

## 7. Recomendación de decisiones (orden de prioridad)

1. **Antes de cualquier entrega — operación (1-2 días):** restaurar backups automáticos y
   verificarlos con un restore de prueba; decidir formalmente si `ades.setag.mx` es
   producción y, si lo es, activar `asignar_triggers()` (audit_aiud) en las tablas con PII.
2. **Antes de entrega formal — seguridad (3-5 días):** extender la auditoría BOLA/BFLA de
   Fase 5 a los 47 controllers restantes (los N-1..N-5 de arriba son la lista de arranque);
   corregir `ades_suplencias`/`ades_log_autenticacion`.
3. **Para la entrega — QA dirigido (2-3 días):** cerrar 0.3 (QA manual Gradebook Prepa),
   correr los 21 specs E2E completos por rol, integrar `check-api-contracts.js` a CI.
4. **Piloto supervisado:** con 1-3 cerrados, entregar a un grupo acotado (un plantel, un
   nivel) con acompañamiento 2-4 semanas antes del rollout completo.
5. **Post-entrega (deuda estructural):** migración a tipos OpenAPI generados (elimina la
   familia entera de bugs de contrato), accesibilidad ARIA, cobertura de tests medida,
   actualizar STATE.md/CLAUDE.md (comando OnDestroy roto, terminología dev/prod) y depurar
   los documentos de progreso simulados antes de mostrarlos a terceros.

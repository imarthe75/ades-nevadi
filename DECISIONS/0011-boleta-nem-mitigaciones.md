# ADR-0011 — Boleta oficial NEM: mitigaciones y plan de corrección

**Fecha:** 2026-06-22
**Estado:** Accepted (plan)
**Contexto:** La boleta de calificaciones se adaptó al formato oficial NEM
(educación básica SEP): agrupación por Campo Formativo (Mig 085 añadió
`ades_materias.campo_formativo`), CURP, inasistencias, observaciones, escala 6-10 y
"¿Acreditó el grado?". Quedaron tres deudas/caveats que este ADR planifica corregir.
La generación de boletas vive en **FastAPI** (`app/worker/tasks/boletas.py` +
plantilla Jinja `app/templates/boletas/boleta.html` → weasyprint), la ruta viva que
consume el BFF Spring vía `GET /api/v1/boletas/{id}`.

---

## 1. Verificación de render PDF (weasyprint) — RESUELTO

**Caveat:** los cambios se validaron solo a nivel Jinja/SQL; faltaba confirmar el PDF real.

**Mitigación (ya ejecutada 2026-06-22):** se generó la boleta de un alumno real de
secundaria dentro del contenedor `celery-worker` (código bind-mounted) llamando a
`_generar_pdf_alumno`. Resultado: **PDF válido de 24 KB** (`%PDF-`), con los 4 campos
formativos, disciplinas anidadas, CURP, inasistencias, observaciones, escala 6-10 y
acreditación. Verificado por extracción de texto.

**Acción de seguimiento (pendiente):** añadir un **test automatizado** que ejercite
`_generar_pdf_alumno` contra un alumno seed y aserte `pdf[:5] == b'%PDF-'` y la
presencia de los encabezados de campo. Ubicación: `backend/app/tests/`. Evita
regresiones silenciosas al tocar plantilla/datos. **Esfuerzo: bajo.**

## 2. Alineación arquitectónica (boleta en FastAPI vs Spring)

**Caveat:** el rediseño marca FastAPI como "solo IA"; la boleta sigue ahí (legacy).

**Decisión de planeación — Opción C (recomendada): formalizar FastAPI como capa de
"IA + render de documentos", NO migrar el render PDF a Spring.**

Opciones evaluadas:
- **A. Reescribir render en Spring** (openhtmltopdf/Flying-Saucer + Thymeleaf). Alto
  esfuerzo, duplica un stack de PDF maduro que ya existe; sin beneficio funcional.
- **B. Render vía Carbone desde Spring.** El servicio **Carbone ya corre** en el stack
  (`carbone`); Spring podría llamarlo con plantillas DOCX/XLSX. Esfuerzo medio; útil si
  se quiere que el BFF deje de pasar por FastAPI. Riesgo: rehacer plantillas en formato
  Carbone (DOCX) y reescribir el ensamblado de datos en Java.
- **C. Mantener el render en FastAPI y documentarlo como excepción explícita.** La
  generación de documentos PDF (boletas, constancias, actas) permanece en FastAPI
  porque depende de weasyprint/Jinja/Carbone, stacks de documentos maduros en Python.
  FastAPI pasa a ser **"IA + servicio de documentos"**, no solo IA. El BFF Spring
  sigue proxeando (patrón `BoletaFastApiAdapter`), que es un límite hexagonal limpio
  (puerto de salida hacia un servicio externo de render).

**Justificación:** el render de documentos es un *bounded context* legítimo y estable;
forzarlo a Spring es coste sin valor. Acción: **actualizar CLAUDE.md** para describir
FastAPI como "IA + render de documentos" y listar explícitamente los endpoints de
documentos que viven ahí (boletas, constancias, actas, certificados). **Esfuerzo: bajo
(documental).** Revisar si en el futuro se consolidan documentos en Carbone+Spring.

## 3. NEM primaria Fase 3 (1°-2°): evaluación cualitativa

**Caveat:** en NEM, 1°-2° de primaria se evalúan de forma **cualitativa** (nivel
descriptivo), no con número 6-10. Hoy la boleta trata todo numéricamente.

**Plan de corrección:**
1. **Datos:** decidir representación de la evaluación cualitativa. Opción mínima:
   columna/lookup que mapee el `calificacion_final` o un campo nuevo a un nivel NEM
   descriptivo. NEM no fija una escala cualitativa nacional rígida — usar la del
   plantel (p.ej. "En proceso / En desarrollo / Consolidado") parametrizable. Reusar
   `ades_escalas_evaluacion` (ya tiene `nivel_educativo` + `valores_json`) como
   catálogo de la escala cualitativa por nivel/fase.
2. **Lógica:** en `_generar_pdf_alumno`, detectar fase por `numero_grado` (1-2 primaria
   = Fase 3 cualitativa) y resolver el texto desde la escala en vez del número.
3. **Plantilla:** en `boleta.html`, cuando la fase sea cualitativa, renderizar el nivel
   descriptivo (sin coloreado 6-10 ni "¿Acredita?" numérico).

**Esfuerzo: medio.** Requiere confirmar con el plantel la escala cualitativa que usan.
Dependencia: definición institucional de los descriptores. **Bloqueado** hasta tener
esa definición; mientras tanto, el trato numérico es un *fallback* aceptable.

---

## Resumen de acciones

| # | Acción | Esfuerzo | Estado |
|---|---|---|---|
| 1 | PDF real verificado | — | ✅ Hecho |
| 1b | Test automatizado de boleta (`%PDF-` + campos) | Bajo | Pendiente |
| 2 | Documentar FastAPI como "IA + documentos" en CLAUDE.md | Bajo | Pendiente |
| 3 | Escala cualitativa NEM Fase 3 (datos + lógica + plantilla) | Medio | Bloqueado (definición plantel) |

Relacionado: [[reference_formatos_oficiales]], ADR-0008 (hexagonal), ADR-0010.

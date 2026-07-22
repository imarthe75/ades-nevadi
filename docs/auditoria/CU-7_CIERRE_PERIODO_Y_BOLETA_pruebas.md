# CU-7 · Cierre de período y generación de boleta — Guía de pruebas manuales

**Contexto:** amplía el MVP de 6 CU (guía visual de pruebas) con el flujo que sigue a la
evaluación (CU-6). Documentado 2026-07-22 a partir del código real.

## Flujo

```
CU-6 (calificaciones capturadas)
        │
        ▼
[Ajuste manual opcional]  →  [Validar cierre]  →  [Cerrar período]  →  [Generar boleta]
```

## Dónde está / endpoints reales

| Paso | Rol | Ubicación (UI) | Endpoint real |
|---|---|---|---|
| Ver concentrado del grupo | Coordinación/Docente | Gradebook → Concentrado | `GET /api/v1/gradebook/grupo/{grupoId}/concentrado` |
| Revisar inconsistencias | Coordinación | Gradebook | `GET /api/v1/gradebook/inconsistencias/{grupoId}` |
| Ajuste manual de una nota de período | ADMIN_PLANTEL/GLOBAL | Gradebook → Ajustar | `POST /api/v1/gradebook/{calPeriodoId}/ajuste-manual` |
| **Cerrar período** | **ADMIN_PLANTEL/GLOBAL** | Gradebook → **Cerrar período** | `POST /api/v1/gradebook/{calPeriodoId}/cerrar` |
| Vista de boleta de un alumno | Coordinación/Docente | Gradebook → Boleta | `GET /api/v1/gradebook/alumno/{alumnoId}/boleta` |
| **Boleta PDF NEM (Prim/Sec SEP)** | Control Escolar | (render doc) | `GET /boletas/{estudiante_id}` (FastAPI) |
| **Constancia UAEMEX (Prepa)** | Control Escolar | (render doc) | `GET /boletas/uaemex/{estudiante_id}` (FastAPI) |
| Boleta batch por grupo (ZIP) | Control Escolar | (render doc) | `POST /boletas/grupo/{grupo_id}/batch` → `GET /boletas/tarea/{task_id}` |

> Nota de arquitectura: el **cierre** vive en el BFF Spring (Gradebook); la **generación
> de PDF** vive en FastAPI (`app/api/v1/boletas.py`), proxeada por nginx. Son dos servicios.

## Qué probar

| Sev | Caso | Resultado esperado |
|---|---|---|
| 🔴 C | Cerrar período como Docente (nivel 4) | **403** — el cierre exige ADMIN_PLANTEL/GLOBAL (nivel ≤1), no Docente |
| 🔴 C | Editar una calificación de un período **ya cerrado** | Bloqueado — un período cerrado no se recalcula ni edita (protección `cerrada=TRUE` en `calcular_calificacion_periodo`) |
| 🟠 A | Cerrar un período con calificaciones **faltantes/incompletas** | Validación de cierre debe advertir/impedir (revisar `validar-cierre`), no cerrar en silencio con huecos |
| 🟠 A | Generar boleta NEM de un alumno de **Primaria/Secundaria** | Escala y campos NEM (cualitativo 1º-2º; numérico 6-10), campos formativos correctos |
| 🟠 A | Generar constancia de un alumno de **Preparatoria** | Formato UAEMEX (escala 0-10, mín 6.0), NO boleta NEM |
| 🟡 R | Boleta batch de un grupo | Encola tarea Celery; `GET /boletas/tarea/{task_id}` refleja progreso y entrega ZIP |
| 🟡 R | Reabrir/ajustar tras cierre | Comportamiento definido (¿existe reapertura? confirmar con dirección) |

## Pendientes por confirmar (CU-7)

1. **Reapertura de período:** ¿existe un flujo para reabrir un período cerrado, o el cierre
   es definitivo? El ajuste-manual actúa sobre períodos abiertos; confirmar la política.
2. **Rol exacto de generación de boleta:** el cierre exige ADMIN_PLANTEL/GLOBAL; la
   generación de PDF (FastAPI) no comparte el mismo guard del BFF — confirmar quién puede
   descargar boletas y si hay control de acceso equivalente en el endpoint FastAPI.
3. **`validar-cierre`:** confirmar en vivo qué valida exactamente antes de permitir cerrar
   (faltantes, rango, cobertura curricular) para escribir los casos negativos precisos.

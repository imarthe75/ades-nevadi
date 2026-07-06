# ADR-0012 — Claves oficiales (CCT SEP / incorporación UAEMEX) por nivel educativo, no por plantel

**Fecha:** 2026-07-03
**Estado:** Accepted & Implemented
**Contexto:** Durante la sesión de auditoría de brecha del 2026-07-03 se detectó que
`ades_planteles.clave_ct` (mig 001) contenía valores **placeholder** (`MET-NVD-001`,
`TEN-NVD-001`, `IXT-NVD-001`), no Claves de Centro de Trabajo (CCT) reales de la SEP,
y que el modelo de datos solo permitía **una clave por plantel físico**.

---

## 1. Problema estructural

En México, el CCT (formato `NNXXX####L`: 2 dígitos estado + 3 letras tipo de escuela +
4 dígitos secuencial + 1 letra de turno/verificación) se asigna **por nivel educativo +
turno**, no por plantel físico. Un plantel del Instituto Nevadi con Primaria SEP +
Secundaria SEP tiene, en la práctica, **dos CCT distintos** — uno por cada nivel,
otorgados en trámites independientes ante la SEP. Preparatoria (UAEMEX, no SEP) usa en
cambio un **código de incorporación** (RVOE-equivalente), un trámite y formato distintos.
El esquema original (`ades_planteles.clave_ct VARCHAR`) no podía representar esto: un
registro de plantel, una sola clave.

## 2. Decisión

Se creó una tabla nueva, `ades_plantel_nivel_clave` (mig 103), con la llave real del
dominio: `(plantel_id, nivel_educativo_id, tipo_clave)` donde `tipo_clave` distingue
`CCT_SEP` de `INCORPORACION_UAEMEX`. `ades_planteles.clave_ct` **se dejó intacta**
(no se eliminó ni se migraron sus valores) para no romper reportes/vistas existentes
que ya la referencian — se marcó como **deprecada** vía comentario de columna. Los
consumidores nuevos (endpoint `GET/PATCH /api/v1/planteles/{id}/claves`, UI de
Admin → Planteles) usan exclusivamente la tabla nueva.

Opciones evaluadas:
- **A. Agregar columnas `clave_ct_primaria`/`clave_ct_secundaria`/`clave_ct_prep` a
  `ades_planteles`.** Descartada: no escala si el instituto abre un plantel con más
  niveles o si SEP reasigna un turno adicional; tampoco modela naturalmente el campo
  `tipo_clave` (CCT vs incorporación) sin más columnas ad-hoc.
- **B. Tabla `ades_plantel_nivel_clave` normalizada (elegida).** Escala a cualquier
  combinación plantel×nivel×tipo sin migración futura; permite historizar con
  `vigente_desde` si la SEP reasigna un CCT.
- **C. Migrar y eliminar `ades_planteles.clave_ct`.** Descartada por ahora: riesgo de
  romper reportes 911/boletas que puedan referenciarla directamente sin haberlos
  auditado todos en la misma sesión. Queda como limpieza futura de bajo riesgo una vez
  confirmado que ningún reporte oficial lee esa columna.

## 3. Datos: verificación externa, no invención

Se investigaron y verificaron (4+ fuentes independientes: escuelasmex.com,
escuelas-mexico.com, edunautica.mx, pequenautica.com — todas coinciden) 6 CCT reales:

| Plantel | Nivel | CCT SEP |
|---|---|---|
| Metepec | Primaria | 15PPR7068F |
| Metepec | Secundaria | 15PES0124F |
| Tenancingo | Primaria | 15PPR7106S |
| Tenancingo | Secundaria | 15PES0143U |
| Ixtapan de la Sal | Primaria | 15PPR0088Y |
| Ixtapan de la Sal | Secundaria | 15PES0169B |

El código de incorporación UAEMEX (Preparatoria, Metepec y Tenancingo) **no se encontró
en registros públicos** — a diferencia del CCT SEP, las incorporaciones UAEMEX no están
indexadas por directorios escolares públicos. Se sembraron filas `tipo_clave =
'INCORPORACION_UAEMEX'` con `clave = NULL` y observación explícita pidiendo al
Instituto el oficio de incorporación — **decisión deliberada de no fabricar un dato
oficial que se usará en documentos SEP/UAEMEX** (boletas, actas, reportes 911).

## 4. Consecuencias

- Positivo: el modelo ahora es correcto de cara a futuros reportes oficiales que
  necesiten el CCT específico por nivel (911, actas SEP, kardex UAEMEX).
- Negativo/pendiente: `ades_planteles.clave_ct` queda como columna muerta hasta que se
  audite y migre cada consumidor restante — deuda de limpieza de bajo riesgo.
- Pendiente externo: el Instituto debe proporcionar el oficio de incorporación UAEMEX
  de Preparatoria (Metepec, Tenancingo) para completar los dos registros con
  `clave = NULL`.

**Esfuerzo:** medio (1 migración + tabla nueva + endpoint + UI). **Revisión futura:**
una vez confirmado que ningún reporte lee `ades_planteles.clave_ct` directamente,
eliminarla en una migración de limpieza dedicada.


# 🧾 Guía de Estilo SQL — PostgreSQL

**Autor:** im\@rthe  
**Versión:** 2026‑02‑26 (sustituye 2025‑08‑14)

Esta guía establece convenciones para **modelado**, **escritura** y **migraciones idempotentes** de SQL en PostgreSQL, priorizando **trazabilidad**, **claridad**, **compatibilidad internacional** y **facilidad de edición**.

---

## Índice

- [0) Principios generales](#0-principios-generales)
- [1) Convenciones de nombres](#1-🧠-convenciones-de-nombres)
- [2) Formato y sangrado](#2-🎨-formato-y-sangrado)
- [3) Esquemas (convención obligatoria)](#3-🗄️-esquemas-convención-obligatoria)
- [4) Tipos de datos (IDs, tiempo, texto)](#4-🔤-tipos-de-datos-ids-tiempo-texto)
- [5) Columnas derivadas (GENERATED)](#5-🧮-columnas-derivadas-generated)
- [6) Campo hash (dedupe/detección de cambios)](#6-🔐-campo-hash-dedupedetección-de-cambios)
- [7) Joins y subconsultas](#7-🔗-joins-y-subconsultas)
- [8) Comparaciones y filtros](#8-🔍-comparaciones-y-filtros)
- [9) Auditoría (OBLIGATORIA)](#9-🕵️-auditoría-obligatoria)
- [10) Idempotencia (helpers en util)](#10-⚙️-idempotencia-helpers-permanentes-en-util)
- [11) Ejemplo end-to-end](#11-🧩-ejemplo-endtoend-idempotente-snake_case-auditoría-incluida)
- [12) Automatización y modularidad](#12-⚙️-automatización-y-modularidad)
- [13) Validación y debugging](#13-🧪-validación-y-debugging)
- [14) Comas y legibilidad](#14-✏️-comas-y-legibilidad)
- [15) Columnas mutuamente excluyentes](#15-🔐-columnas-mutuamente-excluyentes)
- [16) Documentación por comentarios](#16-🗂️-documentación-por-comentarios-obligatorio)
- [17) Pipelines (Liquibase/Flyway)](#17-📦-pipelines-liquibaseflyway)
- [18) I18N y búsquedas](#18-🌐-i18n-y-búsquedas)
- [19) Seguridad y roles](#19-🔒-seguridad-y-roles-resumen)
- [20) Checklist para PRs SQL](#20-✅-checklist-para-prs-sql)

---


## 0) Principios generales

*   **Legibilidad primero**: SQL consistente, sangrado vertical, nombres claros.
*   **Idempotencia real (DDL)**: no basta `IF NOT EXISTS`; verifica **equivalencia estructural** via `pg_catalog` (FKs/índices/columnas).
*   **Evolutivo y auditable**: SCD Type 2, `ref` (UUID estable), `row_version`.
*   **Rendimiento + integridad**: FKs con índices en **tabla hija**.
*   **I18N**: base en `UTF8` + `LC_COLLATE`/`LC_CTYPE` apropiados (ej. `es_MX.utf8`) y búsquedas sin acentos donde aplique.
*   **Documentación obligatoria**: **siempre** `COMMENT ON` al crear/alterar objetos.

***

## 1) 🧠 Convenciones de nombres

*   Identificadores en **`snake_case`**, **minúsculas** (evitar comillas).
*   **Tablas** en **plural**: `personas`, `ordenes`, `direcciones`.
*   **Columnas** en **singular**: `fecha_nacimiento`, `correo_electronico`.
*   **Alias** cortos y consistentes: `p`, `o`, `d`.
*   Evitar abreviaturas ambiguas: no `nom`, `ape`, `fec`.

### 1.1 🧬 Nombres y apellidos

Campos base:

*   `primer_nombre`, `segundo_nombre` (NULL si no aplica)
*   `primer_apellido`, `segundo_apellido` (NULL si no aplica)

Derivados:

*   `nombre_completo` (GENERATED STORED)
*   `apellidos_legales` (GENERATED STORED)

Alternativas regionales:

*   🇲🇽 `apellido_paterno`, `apellido_materno`
*   🌐 `primer_apellido`, `segundo_apellido`
*   📝 `apellidos` (texto libre) — staging/compatibilidad, **no** fuente de verdad.

***

## 2) 🎨 Formato y sangrado

*   Palabras clave en **MAYÚSCULAS**: `SELECT`, `FROM`, `JOIN`, `WHERE`.
*   Objetos en **minúsculas**.
*   **Comas al inicio de línea** (preferido) + **sangrado vertical**.

```sql
SELECT p.primer_nombre
     , p.segundo_nombre
     , p.primer_apellido
     , p.segundo_apellido
     , o.numero AS numero_orden
FROM app.personas  p
JOIN app.ordenes   o ON o.persona_id = p.id
WHERE p.fecha_fin IS NULL;
```

***

## 3) 🗄️ Esquemas (convención obligatoria)

**Separación por dominio:**

*   `util` → **todas las funciones de utilería** (helpers de idempotencia).
*   `app` → modelo transaccional.
*   `auditoria` (o `audit`) → bitácora y funciones de auditoría.
*   `dw` → data warehouse/analítica.
*   `stage` → staging/ETL.
*   `ref` → catálogos maestros.
*   `ext` → extensiones (opcional).
*   `sec` → seguridad/gobernanza (opcional).

**Reglas:**

*   `search_path` **mínimo**; **califica** objetos (`app.personas`).
*   **Permisos** por esquema + `ALTER DEFAULT PRIVILEGES`.
*   **Comentarios** (`COMMENT ON SCHEMA`) obligatorios.
*   **Utilerías siempre en `util`** (p. ej. `util.ensure_fk(...)`).

Bootstrap idempotente (resumen):

```sql
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='util')      THEN EXECUTE 'CREATE SCHEMA util';      END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='app')       THEN EXECUTE 'CREATE SCHEMA app';       END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='auditoria') THEN EXECUTE 'CREATE SCHEMA auditoria'; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='dw')        THEN EXECUTE 'CREATE SCHEMA dw';        END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='stage')     THEN EXECUTE 'CREATE SCHEMA stage';     END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='ref')       THEN EXECUTE 'CREATE SCHEMA ref';       END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='ext')       THEN EXECUTE 'CREATE SCHEMA ext';       END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='sec')       THEN EXECUTE 'CREATE SCHEMA sec';       END IF;
END$$ LANGUAGE plpgsql;

COMMENT ON SCHEMA util      IS 'Funciones utilitarias para administración y migraciones idempotentes.';
COMMENT ON SCHEMA app       IS 'Modelo transaccional de la aplicación.';
COMMENT ON SCHEMA auditoria IS 'Bitácora y funciones de auditoría.';
COMMENT ON SCHEMA dw        IS 'Modelo analítico y data warehouse.';
COMMENT ON SCHEMA stage     IS 'Staging/aterrizaje (ETL).';
COMMENT ON SCHEMA ref       IS 'Catálogos y referencias maestras.';
COMMENT ON SCHEMA ext       IS 'Extensiones de terceros.';
COMMENT ON SCHEMA sec       IS 'Objetos de seguridad y gobernanza.';
```

***

## 4) 🔤 Tipos de datos (IDs, tiempo, texto)

### 4.1 IDs (dos alternativas válidas)

**A) Entero autoincremental**

*   Preferir **`GENERATED BY DEFAULT AS IDENTITY`**.
*   Legado aceptable: `serial`.

```sql
id integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY
-- o (legado):
-- id serial PRIMARY KEY
```

**B) UUID** (recomendado en distribuidos/alto volumen)

*   **PG18**: `DEFAULT uuidv7()` (orden temporal; mejor localidad de índice).
*   Alternativa (PG ≤ 17): `DEFAULT gen_random_uuid()` (`pgcrypto`).

```sql
id uuid PRIMARY KEY DEFAULT uuidv7()
-- o
-- id uuid PRIMARY KEY DEFAULT gen_random_uuid()
```

> **Criterio**: si necesitas **orden temporal** y mitigación de fragmentación → **UUIDv7**; si priorizas simplicidad sin particionado → **IDENTITY**.

### 4.2 Business key estable

*   `ref uuid NOT NULL UNIQUE DEFAULT uuidv7()` — clave estable a lo largo del tiempo (SCD2).

### 4.3 Tiempo y otros

*   **Tiempo**: `timestamptz` para eventos auditables.
*   **Texto**: `text` o `varchar(n)`; evitar `char(n)`.
*   **JSON**: `jsonb`.
*   **Boolean**: `boolean`.

***

## 5) 🧮 Columnas derivadas (GENERATED)

*   Usar **`GENERATED ALWAYS AS (...) STORED`** para derivados **deterministas** e **IMMUTABLE**.
*   Si requieres `unaccent()` u otras `STABLE`, usa **trigger**.

Ejemplos:

```sql
ALTER TABLE app.personas
  ADD COLUMN nombre_completo text
  GENERATED ALWAYS AS (
    btrim(concat_ws(' ', primer_nombre, segundo_nombre, primer_apellido, segundo_apellido))
  ) STORED;

ALTER TABLE app.personas
  ADD COLUMN apellidos_legales text
  GENERATED ALWAYS AS (
    btrim(concat_ws(' ', primer_apellido, segundo_apellido))
  ) STORED;
```

**Búsqueda sin acentos/mayúsculas**:

```sql
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS ix_personas__nombre_completo_trgm
  ON app.personas
  USING gin ( (unaccent(lower(nombre_completo))) gin_trgm_ops );
```

***

## 6) 🔐 Campo hash (dedupe/detección de cambios)

**Generado (IMMUTABLE):**

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE app.personas
  ADD COLUMN datos_hash text
  GENERATED ALWAYS AS (
    encode(digest(
      lower(btrim(coalesce(primer_nombre,''))) || '|' ||
      lower(btrim(coalesce(segundo_nombre,''))) || '|' ||
      lower(btrim(coalesce(primer_apellido,''))) || '|' ||
      lower(btrim(coalesce(segundo_apellido,''))) || '|' ||
      coalesce(fecha_nacimiento::text,'')
    ,'sha256'),'hex')
  ) STORED;

CREATE INDEX IF NOT EXISTS ix_personas__datos_hash ON app.personas (datos_hash);
```

**Trigger (si necesitas `unaccent()` o normalización compleja)**: ver §9.4.

***

## 7) 🔗 Joins y subconsultas

*   Especificar tipo (`INNER`, `LEFT`, etc.).
*   Alias consistentes.
*   Prefiere `JOIN` a subconsultas cuando sea posible.
*   Evita `SELECT *` en producción.

***

## 8) 🔍 Comparaciones y filtros

*   `IS NOT NULL` y `btrim(col) <> ''`.
*   Prefiere `<>` sobre `!=`.
*   Normaliza vacíos: `NULLIF(btrim(col), '')`.
*   Usa `COALESCE`, `CASE`, `NULLIF`.

***

## 9) 🕵️ Auditoría (OBLIGATORIA)

### 9.1 Campos obligatorios de auditoría por tabla

**Todas** las tablas del dominio de aplicación deben incluir:

*   `id` — **PK** (convención preferida y recomendada para auditoría).
*   `ref uuid` — clave estable (DEFAULT `uuidv7()` o `gen_random_uuid()`).
*   `row_version integer` — concurrencia optimista (inicia en 1, incrementa en `UPDATE`).
*   `fecha_creacion timestamptz`
*   `fecha_modificacion timestamptz`
*   `usuario_creacion text`
*   `usuario_modificacion text`

> El **trigger obligatorio** (`auditoria_biu`) mantiene estos campos si llegan nulos.

### 9.2 Tabla central de auditoría (bitácora)

```sql
CREATE SCHEMA IF NOT EXISTS auditoria;

CREATE TABLE IF NOT EXISTS auditoria.log_auditoria (
    id              bigserial PRIMARY KEY,
    schema_name     text        NOT NULL,
    table_name      text        NOT NULL,
    user_name       text        NOT NULL,
    dml_action      text        NOT NULL,    -- 'I' | 'U' | 'D'
    pk_name         text,                    -- p. ej., 'id' (detectado)
    pk_value        text,                    -- valor del PK
    uuid_ref        uuid,                    -- ref si existe en la fila
    old_data        jsonb,                   -- estado previo (U/D)
    new_data        jsonb,                   -- estado nuevo  (I/U)
    changed_cols    text[],                  -- columnas que cambiaron (U)
    hash_old        text,                    -- sha256(old_data)
    hash_new        text,                    -- sha256(new_data)
    executed_sql    text,                    -- opcional
    recorded_at     timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE auditoria.log_auditoria IS 'Bitácora central de DML con jsonb, hashes y difs.';
-- índices
CREATE INDEX IF NOT EXISTS ix_log_auditoria__when       ON auditoria.log_auditoria (recorded_at);
CREATE INDEX IF NOT EXISTS ix_log_auditoria__object     ON auditoria.log_auditoria (schema_name, table_name);
CREATE INDEX IF NOT EXISTS ix_log_auditoria__uuid_ref   ON auditoria.log_auditoria (uuid_ref);
CREATE INDEX IF NOT EXISTS ix_log_auditoria__action     ON auditoria.log_auditoria (dml_action);
CREATE INDEX IF NOT EXISTS ix_log_auditoria__hash_new   ON auditoria.log_auditoria (hash_new);
```

### 9.3 Función **obligatoria**: `auditoria_biu()` (BEFORE INSERT/UPDATE)

*   Asegura `ref`, inicializa/incrementa `row_version`, fija `fecha_*` y `usuario_*`.
*   `snake_case` en todos los nombres.
*   `SECURITY DEFINER` + `SET search_path` seguro.

```sql
CREATE OR REPLACE FUNCTION auditoria.auditoria_biu()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = auditoria, pg_temp
AS $$
DECLARE
  v_now timestamptz := clock_timestamp();
  v_actor text;
  v_has_uuidv7 boolean;
  v_has_genrand boolean;
BEGIN
  IF TG_OP = 'INSERT' THEN
    v_actor := COALESCE(NEW.usuario_creacion, current_user);
    NEW.usuario_creacion   := v_actor;
    NEW.fecha_creacion     := COALESCE(NEW.fecha_creacion, v_now);
    NEW.row_version        := 1;
  ELSIF TG_OP = 'UPDATE' THEN
    v_actor := COALESCE(NEW.usuario_modificacion, current_user);
    NEW.usuario_modificacion := v_actor;
    NEW.fecha_modificacion   := COALESCE(NEW.fecha_modificacion, v_now);
    NEW.row_version          := COALESCE(OLD.row_version, 0) + 1;
  END IF;

  -- Asegurar ref si viene NULL
  IF NEW.ref IS NULL THEN
    SELECT EXISTS (SELECT 1 FROM pg_proc WHERE proname='uuidv7') INTO v_has_uuidv7;
    SELECT EXISTS (SELECT 1 FROM pg_proc WHERE proname='gen_random_uuid') INTO v_has_genrand;
    IF v_has_uuidv7 THEN
      NEW.ref := uuidv7();
    ELSIF v_has_genrand THEN
      NEW.ref := gen_random_uuid();
    END IF;
    -- si ninguna existe, se asume DEFAULT a nivel de columna (recomendado)
  END IF;

  RETURN NEW;
END;
$$;

COMMENT ON FUNCTION auditoria.auditoria_biu() IS
'BEFORE INSERT/UPDATE: asegura ref, row_version, fecha_* y usuario_*.';
```

### 9.4 Función **opcional**: `auditoria_aiud()` (AFTER INSERT/UPDATE/DELETE)

*   Registra evento en `auditoria.log_auditoria` usando `jsonb`, `hashes` y `changed_cols`.
*   Detecta el **nombre de PK** automáticamente (recomendamos que sea `id`).

```sql
CREATE OR REPLACE FUNCTION auditoria.auditoria_aiud()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = auditoria, pg_temp
AS $$
DECLARE
  v_old jsonb;
  v_new jsonb;
  v_hash_old text;
  v_hash_new text;
  v_actor text;
  v_pk_name text;
  v_pk_value text;
  v_ref uuid;
  v_changed_cols text[];
BEGIN
  -- Representación jsonb sin nulls para hash estable
  IF TG_OP IN ('UPDATE','DELETE') THEN
    v_old := jsonb_strip_nulls(to_jsonb(OLD));
  END IF;
  IF TG_OP IN ('INSERT','UPDATE') THEN
    v_new := jsonb_strip_nulls(to_jsonb(NEW));
  END IF;

  -- Hashes
  IF v_old IS NOT NULL THEN
    v_hash_old := encode(digest(convert_to(v_old::text,'UTF8'),'sha256'),'hex');
  END IF;
  IF v_new IS NOT NULL THEN
    v_hash_new := encode(digest(convert_to(v_new::text,'UTF8'),'sha256'),'hex');
  END IF;

  -- Actor
  IF TG_OP = 'INSERT' THEN
    v_actor := COALESCE(NEW.usuario_creacion, current_user);
  ELSIF TG_OP = 'UPDATE' THEN
    v_actor := COALESCE(NEW.usuario_modificacion, current_user);
  ELSE
    v_actor := COALESCE(OLD.usuario_modificacion, OLD.usuario_creacion, current_user);
  END IF;

  -- Detectar PK automáticamente (preferimos 'id'); si hay multi-PK, toma la primera columna
  SELECT a.attname
    INTO v_pk_name
  FROM   pg_index i
  JOIN   pg_attribute a
         ON a.attrelid = i.indrelid
        AND a.attnum = ANY(i.indkey)
  WHERE  i.indrelid = (quote_ident(TG_TABLE_SCHEMA)||'.'||quote_ident(TG_TABLE_NAME))::regclass
     AND i.indisprimary
  ORDER  BY array_position(i.indkey, a.attnum)
  LIMIT  1;

  IF v_pk_name IS NULL THEN
    v_pk_name := 'id'; -- fallback por convención
  END IF;

  IF TG_OP IN ('INSERT','UPDATE') THEN
    v_pk_value := COALESCE((v_new ->> v_pk_name), (v_old ->> v_pk_name));
  ELSE
    v_pk_value := (v_old ->> v_pk_name);
  END IF;

  -- ref si existe en la fila
  IF TG_OP IN ('INSERT','UPDATE') THEN
    v_ref := COALESCE(NULLIF((v_new ->> 'ref')::uuid, NULL), NULLIF((v_old ->> 'ref')::uuid, NULL));
  ELSE
    v_ref := NULLIF((v_old ->> 'ref')::uuid, NULL);
  END IF;

  -- columnas cambiadas (UPDATE)
  IF TG_OP = 'UPDATE' THEN
    v_changed_cols := ARRAY(
      SELECT n.key
      FROM jsonb_each_text(v_new) AS n(key, val)
      JOIN jsonb_each_text(v_old) AS o(key, val) USING (key)
      WHERE n.val IS DISTINCT FROM o.val
    );
  END IF;

  INSERT INTO auditoria.log_auditoria (
    schema_name, table_name, user_name, dml_action,
    pk_name, pk_value, uuid_ref,
    old_data, new_data, changed_cols,
    hash_old, hash_new,
    executed_sql, recorded_at
  ) VALUES (
    TG_TABLE_SCHEMA::text,
    TG_TABLE_NAME::text,
    v_actor,
    substring(TG_OP,1,1),
    v_pk_name,
    v_pk_value,
    v_ref,
    v_old,
    v_new,
    v_changed_cols,
    v_hash_old,
    v_hash_new,
    NULL,
    clock_timestamp()
  );

  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  ELSE
    RETURN NEW;
  END IF;
END;
$$;

COMMENT ON FUNCTION auditoria.auditoria_aiud() IS
'AFTER I/U/D: registra evento en log_auditoria con jsonb, hashes y columnas cambiadas.';
```

**Activación por tabla (idempotente):**

```sql
-- Obligatorio
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger
    WHERE tgname = 'trg_auditoria_biu_personas'
      AND tgrelid = 'app.personas'::regclass
  ) THEN
    EXECUTE 'CREATE TRIGGER trg_auditoria_biu_personas
             BEFORE INSERT OR UPDATE ON app.personas
             FOR EACH ROW EXECUTE FUNCTION auditoria.auditoria_biu()';
  END IF;
END$$ LANGUAGE plpgsql;

-- Opcional
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger
    WHERE tgname = 'trg_auditoria_aiud_personas'
      AND tgrelid = 'app.personas'::regclass
  ) THEN
    EXECUTE 'CREATE TRIGGER trg_auditoria_aiud_personas
             AFTER INSERT OR UPDATE OR DELETE ON app.personas
             FOR EACH ROW EXECUTE FUNCTION auditoria.auditoria_aiud()';
  END IF;
END$$ LANGUAGE plpgsql;
```

***

## 10) ⚙️ Idempotencia (helpers permanentes en `util`)

> Todas las utilerías viven en **`util`** y se invocan calificadas.

### 10.1 `util.ensure_fk` — FK equivalente + `NOT VALID`

```sql
CREATE OR REPLACE FUNCTION util.ensure_fk(
  in_schema   text,
  in_table    text,
  in_conname  text,
  in_cols     text[],
  ref_schema  text,
  ref_table   text,
  ref_cols    text[],
  p_not_valid boolean DEFAULT false
)
RETURNS void
LANGUAGE plpgsql
AS $f$
DECLARE
  src_ns_oid oid; ref_ns_oid oid; src_rel_oid oid; ref_rel_oid oid;
  colnums int[]; ref_colnums int[]; existing_oid oid; existing_name text; existing_validated boolean;
  name_conflict boolean; src_cols_qlist text; ref_cols_qlist text; cnt_src int; cnt_ref int; ddl text;
BEGIN
  SELECT n.oid INTO src_ns_oid FROM pg_namespace n WHERE n.nspname=in_schema;
  IF src_ns_oid IS NULL THEN RAISE EXCEPTION 'El schema "%s" no existe.', in_schema; END IF;
  SELECT n.oid INTO ref_ns_oid FROM pg_namespace n WHERE n.nspname=ref_schema;
  IF ref_ns_oid IS NULL THEN RAISE EXCEPTION 'El schema "%s" no existe.', ref_schema; END IF;

  SELECT c.oid INTO src_rel_oid FROM pg_class c WHERE c.relname=in_table  AND c.relnamespace=src_ns_oid;
  IF src_rel_oid IS NULL THEN RAISE EXCEPTION 'La tabla "%s.%s" no existe.', in_schema, in_table; END IF;
  SELECT c.oid INTO ref_rel_oid FROM pg_class c WHERE c.relname=ref_table AND c.relnamespace=ref_ns_oid;
  IF ref_rel_oid IS NULL THEN RAISE EXCEPTION 'La tabla referenciada "%s.%s" no existe.', ref_schema, ref_table; END IF;

  SELECT array_agg(a.attnum ORDER BY u.ord), count(*) INTO colnums, cnt_src
  FROM unnest(in_cols) WITH ORDINALITY u(col,ord)
  JOIN pg_attribute a ON a.attrelid=src_rel_oid AND a.attname=u.col AND a.attnum>0 AND NOT a.attisdropped;
  IF cnt_src IS DISTINCT FROM array_length(in_cols,1) THEN
    RAISE EXCEPTION 'Columna(s) de origen inexistente(s) en "%s.%s" → %s', in_schema, in_table, in_cols;
  END IF;

  SELECT array_agg(a.attnum ORDER BY u.ord), count(*) INTO ref_colnums, cnt_ref
  FROM unnest(ref_cols) WITH ORDINALITY u(col,ord)
  JOIN pg_attribute a ON a.attrelid=ref_rel_oid AND a.attname=u.col AND a.attnum>0 AND NOT a.attisdropped;
  IF cnt_ref IS DISTINCT FROM array_length(ref_cols,1) THEN
    RAISE EXCEPTION 'Columna(s) referenciada(s) inexistente(s) en "%s.%s" → %s', ref_schema, ref_table, ref_cols;
  END IF;

  SELECT con.oid, con.conname, con.convalidated
    INTO existing_oid, existing_name, existing_validated
  FROM pg_constraint con
  WHERE con.contype='f' AND con.conrelid=src_rel_oid AND con.confrelid=ref_rel_oid
    AND con.conkey=colnums::int2[] AND con.confkey=ref_colnums::int2[]
  LIMIT 1;

  IF existing_oid IS NOT NULL THEN
    IF existing_validated THEN
      RAISE NOTICE 'Omitido: ya existe FK equivalente en %s.%s (%.).', in_schema, in_table, existing_name;
    ELSE
      RAISE NOTICE 'Omitido: ya existe FK equivalente NO VALIDADA (%.). Sugerido: ALTER TABLE %I.%I VALIDATE CONSTRAINT %I;',
        existing_name, in_schema, in_table, existing_name;
    END IF;
    RETURN;
  END IF;

  SELECT EXISTS (SELECT 1 FROM pg_constraint con WHERE con.conrelid=src_rel_oid AND con.conname=in_conname)
    INTO name_conflict;
  IF name_conflict THEN
    RAISE NOTICE 'Omitido: nombre de constraint %s ya en uso en %s.%s.', in_conname, in_schema, in_table;
    RETURN;
  END IF;

  SELECT string_agg(quote_ident(u.col), ', ') INTO src_cols_qlist FROM unnest(in_cols) u(col);
  SELECT string_agg(quote_ident(u.col), ', ') INTO ref_cols_qlist FROM unnest(ref_cols) u(col);

  ddl := format(
    'ALTER TABLE %I.%I ADD CONSTRAINT %I FOREIGN KEY (%s) REFERENCES %I.%I (%s)%s',
    in_schema, in_table, in_conname, src_cols_qlist, ref_schema, ref_table, ref_cols_qlist,
    CASE WHEN p_not_valid THEN ' NOT VALID' ELSE '' END
  );
  EXECUTE ddl;

  IF p_not_valid THEN
    RAISE NOTICE 'Creada FK %s en %s.%s (%s → %s) como NOT VALID.', in_conname, in_schema, in_table, ref_schema, ref_table;
  ELSE
    RAISE NOTICE 'Creada FK %s en %s.%s (%s → %s).', in_conname, in_schema, in_table, ref_schema, ref_table;
  END IF;
END;
$f$;
```

### 10.2 `util.ensure_column` — Columna (tipo, nulidad, default)

```sql
CREATE OR REPLACE FUNCTION util.ensure_column(
  in_schema     text,
  in_table      text,
  in_column     text,
  in_type_sql   text,
  in_nullable   boolean,
  in_default_sql text
)
RETURNS void
LANGUAGE plpgsql
AS $f$
DECLARE
  rel_oid oid; col_exists boolean; cur_type text; cur_nullable boolean; cur_default text; want_default text;
BEGIN
  SELECT c.oid INTO rel_oid
  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname=in_schema AND c.relname=in_table;
  IF rel_oid IS NULL THEN RAISE EXCEPTION 'Tabla "%s.%s" no existe.', in_schema, in_table; END IF;

  SELECT EXISTS (
    SELECT 1 FROM pg_attribute a
    WHERE a.attrelid=rel_oid AND a.attname=in_column AND a.attnum>0 AND NOT a.attisdropped
  ) INTO col_exists;

  want_default := NULLIF(btrim(in_default_sql),'');
  IF NOT col_exists THEN
    EXECUTE format(
      'ALTER TABLE %I.%I ADD COLUMN %I %s %s %s',
      in_schema, in_table, in_column, in_type_sql,
      CASE WHEN in_nullable THEN '' ELSE 'NOT NULL' END,
      CASE WHEN want_default IS NULL OR lower(want_default)='null' THEN '' ELSE 'DEFAULT '||in_default_sql END
    );
    RETURN;
  END IF;

  SELECT format_type(a.atttypid,a.atttypmod) INTO cur_type
  FROM pg_attribute a WHERE a.attrelid=rel_oid AND a.attname=in_column;

  SELECT NOT a.attnotnull INTO cur_nullable
  FROM pg_attribute a WHERE a.attrelid=rel_oid AND a.attname=in_column;

  SELECT pg_get_expr(ad.adbin, ad.adrelid) INTO cur_default
  FROM pg_attrdef ad JOIN pg_attribute a ON a.attrelid=ad.adrelid AND a.attnum=ad.adnum
  WHERE ad.adrelid=rel_oid AND a.attname=in_column;

  IF lower(cur_type) <> lower(in_type_sql) THEN
    EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN %I TYPE %s USING %I::%s',
                   in_schema, in_table, in_column, in_type_sql, in_column, in_type_sql);
  END IF;

  IF cur_nullable <> in_nullable THEN
    IF in_nullable THEN
      EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN %I DROP NOT NULL', in_schema, in_table, in_column);
    ELSE
      EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN %I SET NOT NULL', in_schema, in_table, in_column);
    END IF;
  END IF;

  IF (cur_default IS NULL AND want_default IS NOT NULL AND lower(want_default) <> 'null')
     OR (cur_default IS NOT NULL AND (want_default IS NULL OR lower(want_default)='null'))
     OR (cur_default IS NOT NULL AND want_default IS NOT NULL AND lower(btrim(cur_default)) <> lower(btrim(want_default))) THEN
    IF want_default IS NULL OR lower(want_default)='null' THEN
      EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN %I DROP DEFAULT', in_schema, in_table, in_column);
    ELSE
      EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN %I SET DEFAULT %s', in_schema, in_table, in_column, in_default_sql);
    END IF;
  END IF;
END;
$f$;
```

### 10.3 `util.ensure_generated_column` — Columna GENERADA

```sql
CREATE OR REPLACE FUNCTION util.ensure_generated_column(
  in_schema      text,
  in_table       text,
  in_column      text,
  in_type_sql    text,
  in_expr_sql    text
)
RETURNS void
LANGUAGE plpgsql
AS $f$
DECLARE
  rel_oid oid; col_exists boolean; is_generated boolean; cur_expr text;
BEGIN
  SELECT c.oid INTO rel_oid
  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname=in_schema AND c.relname=in_table;
  IF rel_oid IS NULL THEN RAISE EXCEPTION 'Tabla "%s.%s" no existe.', in_schema, in_table; END IF;

  SELECT EXISTS (
    SELECT 1 FROM pg_attribute a
    WHERE a.attrelid=rel_oid AND a.attname=in_column AND a.attnum>0 AND NOT a.attisdropped
  ) INTO col_exists;

  IF NOT col_exists THEN
    EXECUTE format(
      'ALTER TABLE %I.%I ADD COLUMN %I %s GENERATED ALWAYS AS (%s) STORED',
      in_schema, in_table, in_column, in_type_sql, in_expr_sql
    );
    RETURN;
  END IF;

  SELECT (a.attgenerated='s') INTO is_generated
  FROM pg_attribute a WHERE a.attrelid=rel_oid AND a.attname=in_column;
  IF NOT is_generated THEN
    RAISE EXCEPTION 'La columna "%s.%s.%s" existe pero no es GENERADA.', in_schema, in_table, in_column;
  END IF;

  SELECT pg_get_expr(d.adbin, d.adrelid) INTO cur_expr
  FROM pg_attrdef d JOIN pg_attribute a ON a.attrelid=d.adrelid AND a.attnum=d.adnum
  WHERE d.adrelid=rel_oid AND a.attname=in_column;

  IF lower(btrim(cur_expr)) <> lower(btrim(in_expr_sql)) THEN
    RAISE EXCEPTION 'La columna generada "%s.%s.%s" tiene otra expresión.', in_schema, in_table, in_column;
  END IF;
END;
$f$;
```

### 10.4 `util.ensure_index_for_columns` — Índice btree (prefijo exacto)

```sql
CREATE OR REPLACE FUNCTION util.ensure_index_for_columns(
  in_schema   text,
  in_table    text,
  in_index    text,
  in_cols     text[]
)
RETURNS void
LANGUAGE plpgsql
AS $f$
DECLARE
  rel_oid oid; cols_qlist text; has_covering_index boolean; name_conflict boolean; final_name text;
BEGIN
  SELECT c.oid INTO rel_oid
  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
  WHERE n.nspname=in_schema AND c.relname=in_table;
  IF rel_oid IS NULL THEN RAISE EXCEPTION 'Tabla "%s.%s" no existe.', in_schema, in_table; END IF;

  SELECT EXISTS (
    SELECT 1
    FROM pg_index i
    JOIN pg_class ic ON ic.oid=i.indexrelid
    JOIN pg_am am ON am.oid=ic.relam
    WHERE i.indrelid=rel_oid AND am.amname='btree' AND i.indisvalid AND i.indisready
      AND i.indpred IS NULL AND i.indexprs IS NULL
      AND (
        SELECT array_agg(a.attnum ORDER BY u.ord)
        FROM unnest(in_cols) WITH ORDINALITY u(col,ord)
        JOIN pg_attribute a ON a.attrelid=rel_oid AND a.attname=u.col AND a.attnum>0 AND NOT a.attisdropped
      ) = (i.indkey::int2[])[1:array_length(in_cols,1)]
  ) INTO has_covering_index;

  IF has_covering_index THEN RETURN; END IF;

  SELECT EXISTS (
    SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname=in_schema AND c.relname=in_index
  ) INTO name_conflict;

  final_name := CASE WHEN name_conflict
                     THEN in_index||'__ts_'||extract(epoch from clock_timestamp())::bigint
                     ELSE in_index END;

  SELECT string_agg(quote_ident(u.col), ', ') INTO cols_qlist FROM unnest(in_cols) u(col);
  EXECUTE format('CREATE INDEX %I ON %I.%I (%s)', final_name, in_schema, in_table, cols_qlist);
END;
$f$;
```

***

## 11) 🧩 Ejemplo end‑to‑end (idempotente, snake\_case, auditoría incluida)

Incluye: **schemas**, **tabla `app.personas`** (elige ID `IDENTITY` o `UUIDv7`), **derivados**, **hash**, **índices**, **tabla `app.ordenes`**, **FK con `util.ensure_fk`**, **triggers de auditoría**, y **comentarios**.

```sql
-- Extensiones comunes
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Esquemas (ver §3)

-- Tabla principal
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='personas') THEN
    EXECUTE $SQL$
      CREATE TABLE app.personas (
        -- Variante A: entero autoincremental
        id                   integer GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
        -- Variante B (PG18+): comentar la A y usar:
        -- id                uuid PRIMARY KEY DEFAULT uuidv7(),

        ref                  uuid NOT NULL UNIQUE DEFAULT uuidv7(),
        primer_nombre        text NOT NULL,
        segundo_nombre       text,
        primer_apellido      text NOT NULL,
        segundo_apellido     text,
        fecha_nacimiento     date,

        -- Auditoría obligatoria (snake_case)
        row_version          integer NOT NULL DEFAULT 1,
        fecha_creacion       timestamptz,
        fecha_modificacion   timestamptz,
        usuario_creacion     text,
        usuario_modificacion text,

        -- SCD2 opcional
        fecha_inicio         timestamptz NOT NULL DEFAULT now(),
        fecha_fin            timestamptz,

        CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio)
      );
    $SQL$;
  END IF;
END$$ LANGUAGE plpgsql;

-- Derivados
SELECT util.ensure_generated_column(
  'app','personas','nombre_completo','text',
  $$btrim(concat_ws(' ', primer_nombre, segundo_nombre, primer_apellido, segundo_apellido))$$
);
SELECT util.ensure_generated_column(
  'app','personas','apellidos_legales','text',
  $$btrim(concat_ws(' ', primer_apellido, segundo_apellido))$$
);

-- Hash IMMUTABLE
SELECT util.ensure_generated_column(
  'app','personas','datos_hash','text',
  $$encode(digest(
      lower(btrim(coalesce(primer_nombre,''))) || '|' ||
      lower(btrim(coalesce(segundo_nombre,''))) || '|' ||
      lower(btrim(coalesce(primer_apellido,''))) || '|' ||
      lower(btrim(coalesce(segundo_apellido,''))) || '|' ||
      coalesce(fecha_nacimiento::text,'')
    ,'sha256'),'hex')$$
);

-- Índices
CREATE INDEX IF NOT EXISTS ix_personas__nombre_completo_trgm
  ON app.personas USING gin ( (unaccent(lower(nombre_completo))) gin_trgm_ops );
CREATE INDEX IF NOT EXISTS ix_personas__datos_hash   ON app.personas (datos_hash);
CREATE INDEX IF NOT EXISTS ix_personas__vigentes    ON app.personas (ref) WHERE fecha_fin IS NULL;

-- Comentarios
COMMENT ON TABLE  app.personas IS 'Catálogo de personas; incluye auditoría snake_case, derivados, hash e índices de búsqueda.';
COMMENT ON COLUMN app.personas.ref IS 'UUID estable (business key) de la persona.';
COMMENT ON COLUMN app.personas.usuario_creacion IS 'Usuario que creó el registro.';
COMMENT ON COLUMN app.personas.usuario_modificacion IS 'Usuario de la última modificación.';
COMMENT ON COLUMN app.personas.fecha_creacion IS 'Fecha/hora de creación.';
COMMENT ON COLUMN app.personas.fecha_modificacion IS 'Fecha/hora de última modificación.';

-- Tabla hija
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='app' AND table_name='ordenes') THEN
    EXECUTE $SQL$
      CREATE TABLE app.ordenes (
        id                 uuid PRIMARY KEY DEFAULT uuidv7(),
        ref                uuid NOT NULL UNIQUE DEFAULT uuidv7(),
        persona_ref        uuid NOT NULL,     -- FK a personas.ref
        numero             text NOT NULL,

        -- Auditoría obligatoria (snake_case)
        row_version        integer NOT NULL DEFAULT 1,
        fecha_creacion     timestamptz,
        fecha_modificacion timestamptz,
        usuario_creacion   text,
        usuario_modificacion text
      );
    $SQL$;
  END IF;
END$$ LANGUAGE plpgsql;

-- FK (NOT VALID si hay legados; luego VALIDATE)
SELECT util.ensure_fk(
  'app','ordenes','fk_ordenes__persona_ref__personas',
  ARRAY['persona_ref'],
  'app','personas',ARRAY['ref'],
  TRUE
);
SELECT util.ensure_index_for_columns('app','ordenes','fki_fk_ordenes__persona_ref', ARRAY['persona_ref']);

COMMENT ON TABLE app.ordenes IS 'Órdenes asociadas a personas (FK por ref).';
COMMENT ON CONSTRAINT fk_ordenes__persona_ref__personas ON app.ordenes IS 'Integra órdenes con personas (por ref).';

-- Triggers de auditoría (OBLIGATORIO + OPCIONAL)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger WHERE tgname='trg_auditoria_biu_personas' AND tgrelid='app.personas'::regclass
  ) THEN
    EXECUTE 'CREATE TRIGGER trg_auditoria_biu_personas
             BEFORE INSERT OR UPDATE ON app.personas
             FOR EACH ROW EXECUTE FUNCTION auditoria.auditoria_biu()';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger WHERE tgname='trg_auditoria_aiud_personas' AND tgrelid='app.personas'::regclass
  ) THEN
    EXECUTE 'CREATE TRIGGER trg_auditoria_aiud_personas
             AFTER INSERT OR UPDATE OR DELETE ON app.personas
             FOR EACH ROW EXECUTE FUNCTION auditoria.auditoria_aiud()';
  END IF;
END$$ LANGUAGE plpgsql;
```

***

## 12) ⚙️ Automatización y modularidad

*   Usa CTEs (`WITH`) para dividir lógica.
*   Documenta cada bloque con comentarios.
*   Versiona scripts por fecha/propósito (`YYYY_MM_DD_descripcion.sql`).

***

## 13) 🧪 Validación y debugging

*   `EXPLAIN (ANALYZE, BUFFERS)` para rendimiento.
*   Documenta supuestos y *edge cases*.
*   En PL/pgSQL, `RAISE NOTICE` como traza.

***

## 14) ✏️ Comas y legibilidad

*   Comas al inicio de línea (preferido).
*   Sangrado vertical.
*   Evita líneas largas/anidadas sin sangrado.

***

## 15) 🔐 Columnas mutuamente excluyentes

**Al menos una con dato:**

```sql
CHECK (
    btrim(coalesce(correo_electronico, '')) <> ''
 OR btrim(coalesce(telefono_movil, '')) <> ''
 OR btrim(coalesce(usuario_red_social, '')) <> ''
);
```

**Exclusividad estricta (opcional):**

```sql
CHECK (
    (CASE WHEN btrim(coalesce(correo_electronico, '')) <> '' THEN 1 ELSE 0 END
   + CASE WHEN btrim(coalesce(telefono_movil, '')) <> '' THEN 1 ELSE 0 END
   + CASE WHEN btrim(coalesce(usuario_red_social, '')) <> '' THEN 1 ELSE 0 END) = 1
);
```

***

## 16) 🗂️ Documentación por comentarios (**OBLIGATORIO**)

**Siempre** comenta objetos creados/alterados:

```sql
COMMENT ON TABLE app.personas IS '...';
COMMENT ON COLUMN app.personas.usuario_creacion IS '...';
COMMENT ON CONSTRAINT fk_ordenes__persona_ref__personas ON app.ordenes IS '...';
COMMENT ON INDEX ix_personas__nombre_completo_trgm IS '...';
COMMENT ON FUNCTION auditoria.auditoria_biu IS '...';
COMMENT ON TRIGGER trg_auditoria_aiud_personas ON app.personas IS '...';
```

***

## 17) 📦 Pipelines (Liquibase/Flyway)

*   Helpers **permanentes** en `util`.
*   FKs con legados: crear **`NOT VALID`** → limpiar → `VALIDATE`.
*   **Preconditions**:
    *   FKs: `pg_constraint` por **equivalencia** (`conrelid`, `confrelid`, `conkey`, `confkey`).
    *   Índices: `pg_index` — cobertura por **prefijo** y **orden** (btree, sin parcial ni expresiones).
*   En producción, usa **`CREATE INDEX CONCURRENTLY`** fuera de transacción global.

***

## 18) 🌐 I18N y búsquedas

*   Base: `ENCODING 'UTF8'`, `LC_COLLATE`/`LC_CTYPE` (ej. `es_MX.utf8`).
*   Sin acentos: `unaccent(lower(...))` + `pg_trgm` (índice GIN funcional).
*   Case-insensitive: `citext` o índice sobre `lower(col)`.

***

## 19) 🔒 Seguridad y roles (resumen)

*   Autenticación **SCRAM-SHA-256**; TLS 1.3 en accesos externos.
*   Roles por función: admin, migración, operación, aplicación.
*   **Mínimo privilegio**; `ALTER DEFAULT PRIVILEGES` para heredar permisos.

***

## 20) ✅ Checklist para PRs SQL

*   [ ] `snake_case`; tablas plural, columnas singular.
*   [ ] Esquemas separados; **utilerías en `util`**.
*   [ ] IDs: **IDENTITY/SERIAL** o **UUIDv7** (documentar elección).
*   [ ] `ref uuid` (business key) con `UNIQUE`.
*   [ ] Columnas generadas correctas (solo **IMMUTABLE**); si no, **trigger**.
*   [ ] Campo **hash** (generado/trigger) + índice.
*   [ ] **Auditoría snake\_case** presente: `row_version`, `fecha_*`, `usuario_*`, `ref`; PK preferida `id`.
*   [ ] Trigger **`auditoria_biu` obligatorio**; **`auditoria_aiud` opcional** donde aplique.
*   [ ] FKs con acciones claras + **índice en la hija**.
*   [ ] DDL **idempotente** con helpers `util.*` (estructura, no solo nombre).
*   [ ] `COMMENT ON` en todo objeto tocado.
*   [ ] SCD2: `fecha_inicio/fin`, `row_version`, checks.
*   [ ] I18N/búsquedas sin acentos consideradas.
*   [ ] Plan de `VALIDATE` si se usó `NOT VALID`.
*   [ ] `EXPLAIN ANALYZE` en consultas críticas.

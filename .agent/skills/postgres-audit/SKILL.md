---
name: postgres-audit-setup
description: Workflow for initializing and verifying the robust DML auditing schema in PostgreSQL databases for ADES Instituto Nevadi.
---

# Skill: PostgreSQL DML Audit Setup

Este skill instruye al Agente sobre como inicializar, configurar y validar el esquema y los triggers de auditoria unificados en la base de datos PostgreSQL del proyecto **ADES Instituto Nevadi**.

## 1. Cuando Aplicar este Skill

*   Durante la inicializacion de la base de datos ADES.
*   Al crear o alterar tablas en el esquema `public` de la aplicacion.
*   Cuando se requiera verificar la consistencia e integridad de las bitacoras de auditoria transaccional.

---

## 2. Procedimiento de Inicializacion del Esquema

El agente debe seguir estos pasos para desplegar la estructura de auditoria:

### Paso A: Ejecutar DDL Base

Aplicar el archivo maestro de DDL [auditoria.sql](file:///opt/ades/auditoria.sql) en el catalog objetivo.
Este script idempotente se encarga de:

1. Crear el esquema `auditoria`.
2. Crear la tabla `auditoria.log_auditoria` con los indices necesarios para consultas rapidas por tabla, fecha o UUID.
3. Desplegar la funcion `auditoria.auditoria_biu()` (BEFORE INSERT OR UPDATE): inicializa `ref`, `row_version`, timestamps y usuario automaticamente.
4. Desplegar la funcion `auditoria.auditoria_aiud()` (AFTER INSERT OR UPDATE OR DELETE): registra cambios DML con hashes md5 en `log_auditoria`.

### Paso B: Validacion de Campos en Tablas ADES

Las tablas `public.ades_*` ya incluyen estos campos en su DDL base (`001_initial_schema.sql`).
Si se crean tablas nuevas, asegurar que cuenten con:

```sql
ALTER TABLE public.ades_nueva ADD COLUMN IF NOT EXISTS ref               uuid        NOT NULL DEFAULT gen_random_uuid() UNIQUE;
ALTER TABLE public.ades_nueva ADD COLUMN IF NOT EXISTS row_version       integer     NOT NULL DEFAULT 1;
ALTER TABLE public.ades_nueva ADD COLUMN IF NOT EXISTS fccreacion        timestamp;
ALTER TABLE public.ades_nueva ADD COLUMN IF NOT EXISTS fcmodificacion    timestamp;
ALTER TABLE public.ades_nueva ADD COLUMN IF NOT EXISTS dsusuariocreacion varchar;
ALTER TABLE public.ades_nueva ADD COLUMN IF NOT EXISTS dsusuariomodifica varchar;
```

### Paso C: Registro Idempotente de Triggers

Para cada tabla `public.ades_*`, registrar los triggers verificando previamente que no existan duplicados:

```sql
-- Trigger BEFORE (inicializa ref, row_version, timestamps y usuario)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger
    WHERE tgname = 'trg_auditoria_biu_ades_nueva'
      AND tgrelid = 'public.ades_nueva'::regclass
  ) THEN
    EXECUTE 'CREATE TRIGGER trg_auditoria_biu_ades_nueva
             BEFORE INSERT OR UPDATE ON public.ades_nueva
             FOR EACH ROW EXECUTE FUNCTION auditoria.auditoria_biu()';
  END IF;
END$$ LANGUAGE plpgsql;

-- Trigger AFTER (guarda en log_auditoria con hashes md5)
-- Por convencion se crea DISABLED; habilitar explicitamente cuando se requiera bitacora forense.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger
    WHERE tgname = 'trg_auditoria_aiud_ades_nueva'
      AND tgrelid = 'public.ades_nueva'::regclass
  ) THEN
    EXECUTE 'CREATE TRIGGER trg_auditoria_aiud_ades_nueva
             AFTER INSERT OR UPDATE OR DELETE ON public.ades_nueva
             FOR EACH ROW EXECUTE FUNCTION auditoria.auditoria_aiud()';
    EXECUTE 'ALTER TABLE public.ades_nueva DISABLE TRIGGER trg_auditoria_aiud_ades_nueva';
  END IF;
END$$ LANGUAGE plpgsql;
```

---

## 3. Estructura de `log_auditoria`

| Columna           | Tipo         | Descripcion |
|-------------------|--------------|-------------|
| `schemaname`      | varchar      | Esquema de la tabla auditada |
| `tablename`       | varchar      | Nombre de la tabla auditada |
| `username`        | varchar      | Usuario que ejecuto el DML (`dsusuariocreacion` / `dsusuariomodifica`) |
| `dmlaction`       | varchar      | I (INSERT) / U (UPDATE) / D (DELETE) |
| `originaldata`    | text         | Estado anterior de la fila (ROW antes del DML) |
| `executednewdata` | text         | Estado nuevo de la fila (ROW despues del DML) |
| `executedsql`     | text         | Consulta SQL ejecutada (`current_query()`) |
| `uuid_ref`        | uuid         | Campo `ref` de la fila auditada |
| `hash_nuevo`      | text         | md5 del estado nuevo |
| `hash_original`   | text         | md5 del estado original (cadena de custodia) |
| `recorddatetime`  | timestamp(6) | Timestamp de registro en la bitacora |

---

## 4. Verificacion del Funcionamiento

Una vez configurado, el agente debe validar el flujo ejecutando sentencias de prueba y consultando la bitacora:

```sql
-- 1. Insertar fila de prueba
INSERT INTO public.ades_estatus (entidad, nombre_estatus) VALUES ('TEST', 'PRUEBA_AUDIT');

-- 2. Actualizar fila de prueba
UPDATE public.ades_estatus SET descripcion = 'verificacion' WHERE nombre_estatus = 'PRUEBA_AUDIT';

-- 3. Consultar registros guardados en la bitacora
SELECT schemaname, tablename, username, dmlaction, hash_nuevo, recorddatetime
FROM auditoria.log_auditoria
ORDER BY recorddatetime DESC
LIMIT 5;

-- 4. Limpiar prueba
DELETE FROM public.ades_estatus WHERE nombre_estatus = 'PRUEBA_AUDIT';
```

---

## 5. Notas de implementacion ADES

- Las funciones usan `dsusuariocreacion` y `dsusuariomodifica` como nombres de columna. El `001_initial_schema.sql` mapea estas columnas con alias `usuario_creacion` y `usuario_modificacion`.
- `auditoria_biu()` usa `gen_random_uuid()` para inicializar `ref`. En PG18 actualizar a `uuidv7()` cuando este disponible.
- `auditoria_aiud()` se crea DISABLED por defecto en cada tabla nueva; se habilita explicitamente cuando se requiere bitacora forense completa.
- El DDL maestro esta en [auditoria.sql](file:///opt/ades/auditoria.sql).

-- =============================================================================
-- ADES Instituto Nevadi — Esquema de Auditoría DML
-- Motor: PostgreSQL 18
-- Schema: auditoria
-- =============================================================================
-- Tabla:     auditoria.log_auditoria
-- Funciones: auditoria.auditoria_biu()  — BEFORE INSERT OR UPDATE
--            auditoria.auditoria_aiud() — AFTER  INSERT OR UPDATE OR DELETE
-- Índices:   dmlaction, recorddatetime, uuid_ref, tablename
-- =============================================================================

-- Schema
CREATE SCHEMA IF NOT EXISTS auditoria;

-- ----------------------------
-- Tabla: log_auditoria
-- ----------------------------
DROP TABLE IF EXISTS auditoria.log_auditoria;
CREATE TABLE auditoria.log_auditoria (
  schemaname      varchar COLLATE "pg_catalog"."default",
  tablename       varchar COLLATE "pg_catalog"."default",
  username        varchar COLLATE "pg_catalog"."default",
  dmlaction       varchar COLLATE "pg_catalog"."default",
  originaldata    text    COLLATE "pg_catalog"."default",
  executednewdata text    COLLATE "pg_catalog"."default",
  executedsql     text    COLLATE "pg_catalog"."default",
  uuid_ref        uuid,
  hash_nuevo      text    COLLATE "pg_catalog"."default",
  hash_original   text    COLLATE "pg_catalog"."default",
  recorddatetime  timestamp(6) DEFAULT now()
);

COMMENT ON TABLE  auditoria.log_auditoria IS 'Bitácora DML del sistema ADES. Registra INSERT, UPDATE y DELETE con hash md5 de cada fila para trazabilidad y cadena de custodia.';
COMMENT ON COLUMN auditoria.log_auditoria.uuid_ref IS 'Referencia al campo ref UUID de la tabla auditada.';
COMMENT ON COLUMN auditoria.log_auditoria.hash_nuevo IS 'Hash md5 del estado nuevo de la fila (POST-DML).';
COMMENT ON COLUMN auditoria.log_auditoria.hash_original IS 'Hash md5 del estado anterior de la fila (PRE-DML).';

-- ----------------------------
-- Función: auditoria.auditoria_aiud()
-- Trigger AFTER INSERT OR UPDATE OR DELETE
-- Registra el DML completo en log_auditoria con hashes md5
-- ----------------------------
DROP FUNCTION IF EXISTS auditoria.auditoria_aiud();
CREATE FUNCTION auditoria.auditoria_aiud()
  RETURNS pg_catalog.trigger AS $BODY$
DECLARE
    OldData        TEXT;
    NewData        TEXT;
    hash_nuevo     text;
    hash_original  text;
    usuario        text;
    uuid_ref       uuid;
BEGIN

    IF (TG_OP = 'UPDATE') THEN
        hash_nuevo    := md5(CAST(row(new.*) AS text));
        OldData       := ROW(OLD.*);
        hash_original := (SELECT a.hash_original FROM auditoria.log_auditoria AS a WHERE a.executednewdata = OldData LIMIT 1);
        NewData       := ROW(NEW.*);
        usuario       := row(new.dsusuariomodifica);
        uuid_ref      := old.ref;

        INSERT INTO auditoria.log_auditoria
            (schemaname, tablename, username, dmlaction, originaldata, executednewdata, executedsql, hash_nuevo, hash_original, uuid_ref)
        VALUES
            (TG_TABLE_SCHEMA::TEXT, TG_TABLE_NAME::TEXT, usuario, substring(TG_OP,1,1), OldData, NewData, current_query(), hash_nuevo, hash_original, uuid_ref);

        RETURN NEW;

    ELSIF (TG_OP = 'DELETE') THEN
        OldData       := ROW(OLD.*);
        hash_original := (SELECT a.hash_original FROM auditoria.log_auditoria AS a WHERE a.executednewdata = OldData LIMIT 1);
        hash_nuevo    := (SELECT a.hash_original FROM auditoria.log_auditoria AS a WHERE a.executednewdata = OldData LIMIT 1);
        uuid_ref      := old.ref;

        INSERT INTO auditoria.log_auditoria
            (schemaname, tablename, username, dmlaction, originaldata, executedsql, hash_nuevo, hash_original, uuid_ref)
        VALUES
            (TG_TABLE_SCHEMA::TEXT, TG_TABLE_NAME::TEXT, session_user::TEXT, substring(TG_OP,1,1), OldData, current_query(), hash_nuevo, hash_original, uuid_ref);

        RETURN OLD;

    ELSIF (TG_OP = 'INSERT') THEN
        NewData       := ROW(NEW.*);
        hash_original := md5(CAST(row(new.*) AS text));
        usuario       := row(new.dsusuariocreacion);
        uuid_ref      := new.ref;

        INSERT INTO auditoria.log_auditoria
            (schemaname, tablename, username, dmlaction, executednewdata, executedsql, hash_original, uuid_ref)
        VALUES
            (TG_TABLE_SCHEMA::TEXT, TG_TABLE_NAME::TEXT, usuario, substring(TG_OP,1,1), NewData, current_query(), hash_original, uuid_ref);

        RETURN NEW;

    ELSE
        RAISE WARNING '[AuditTable.trg_AuditDML] - Other action occurred: %, at %', TG_OP, now();
        RETURN NULL;
    END IF;

END;
$BODY$
  LANGUAGE plpgsql VOLATILE COST 100;

COMMENT ON FUNCTION auditoria.auditoria_aiud() IS 'Trigger AFTER INSERT/UPDATE/DELETE. Registra el DML en log_auditoria con hashes md5 para trazabilidad forense.';

-- ----------------------------
-- Función: auditoria.auditoria_biu()
-- Trigger BEFORE INSERT OR UPDATE
-- Inicializa ref, row_version, timestamps y usuario
-- ----------------------------
DROP FUNCTION IF EXISTS auditoria.auditoria_biu();
CREATE FUNCTION auditoria.auditoria_biu()
  RETURNS pg_catalog.trigger AS $BODY$
BEGIN
    IF NEW.ref IS NULL THEN
        NEW.ref := gen_random_uuid();
    END IF;

    IF TG_OP = 'INSERT' THEN
        NEW.row_version := 1;
        IF new.fecha_creacion IS NULL THEN
            NEW.fecha_creacion := localtimestamp;
        END IF;
        IF new.dsusuariocreacion IS NULL THEN
            NEW.dsusuariocreacion := user;
        END IF;
    ELSIF TG_OP = 'UPDATE' THEN
        NEW.row_version := coalesce(OLD.row_version, 0) + 1;
        IF new.fecha_modificacion IS NULL THEN
            NEW.fecha_modificacion := localtimestamp;
        END IF;
        IF new.dsusuariomodifica IS NULL THEN
            NEW.dsusuariomodifica := user;
        END IF;
    END IF;

    RETURN NEW;
END;
$BODY$
  LANGUAGE plpgsql VOLATILE COST 100;

COMMENT ON FUNCTION auditoria.auditoria_biu() IS 'Trigger BEFORE INSERT/UPDATE. Inicializa ref (UUID), row_version, timestamps (fecha_creacion/fecha_modificacion) y usuario (dsusuariocreacion/dsusuariomodifica) de forma automática.';

-- ----------------------------
-- Índices sobre log_auditoria
-- ----------------------------
CREATE INDEX aiud_dmlaction_idx   ON auditoria.log_auditoria USING btree (dmlaction   COLLATE "pg_catalog"."default" "pg_catalog"."text_ops"      ASC NULLS LAST);
CREATE INDEX aiud_recordtime_idx  ON auditoria.log_auditoria USING btree (recorddatetime "pg_catalog"."timestamp_ops"                             ASC NULLS LAST);
CREATE INDEX aiud_ref_idx         ON auditoria.log_auditoria USING btree (uuid_ref    "pg_catalog"."uuid_ops"                                     ASC NULLS LAST);
CREATE INDEX aiud_tablename_idx   ON auditoria.log_auditoria USING btree (tablename   COLLATE "pg_catalog"."default" "pg_catalog"."text_ops"      ASC NULLS LAST);

-- =============================================================================
-- FIN DEL SCRIPT
-- Objetos creados:
--   schema    : auditoria
--   tabla     : auditoria.log_auditoria  (+ 4 índices)
--   función   : auditoria.auditoria_biu  (BEFORE INSERT OR UPDATE)
--   función   : auditoria.auditoria_aiud (AFTER  INSERT OR UPDATE OR DELETE)
-- =============================================================================

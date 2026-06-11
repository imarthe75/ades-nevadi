# ADR-0002 — UUID v7 como Primary Key estándar

**Estado:** Aceptado  
**Fecha:** 2026-06-04  
**Autor:** Agente Residente v2.0

## Contexto

Las tablas iniciales usaban `BIGINT GENERATED ALWAYS AS IDENTITY` como PK. Esto genera
colisiones de merge, expone IDs predecibles en URLs y dificulta la distribución futura.

## Decisión

Toda tabla ADES usa `id UUID NOT NULL DEFAULT uuidv7()` como PK.  
Las columnas FK también son `UUID`.  
`ref UUID NOT NULL UNIQUE DEFAULT uuidv7()` para business keys de SCD2.

## Consecuencias

- URLs con UUIDs son opacas (no enumerables)
- UUIDv7 son time-ordered → eficiencia de índice B-tree comparable a SERIAL
- Compatible con Authentik `oidc_sub` (string UUID)
- `NEVER` usar `SERIAL`, `BIGINT`, `INTEGER` como PK en tablas nuevas

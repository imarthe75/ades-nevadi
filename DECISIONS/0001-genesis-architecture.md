# ADR-0001 — Arquitectura Génesis ADES

**Estado:** Aceptado  
**Fecha:** 2026-06-04  
**Autor:** Agente Residente v2.0

## Contexto

El Instituto Nevadi requiere un sistema integral de administración escolar (SIS) para 3 planteles
y 3 niveles educativos (Primaria SEP, Secundaria SEP, Preparatoria UAEMEX). El sistema debe
soportar roles diferenciados, multi-plantel, multi-ciclo y cumplir con regulaciones SEP/UAEMEX.

## Decisión

Stack seleccionado:
- **Backend:** FastAPI + SQLAlchemy 2.x async + Celery + Valkey
- **Frontend:** Angular 22 + PrimeNG 21 (estilo Oracle APEX)
- **Base de datos:** PostgreSQL 18 + pgvector
- **Auth:** Authentik OIDC/OAuth2 (Google Workspace SSO para personal)
- **Infra:** Docker Compose en ARM OCI (4 cores 24 GB RAM)
- **PK:** UUID v7 en todas las tablas (`uuidv7()` nativo PG18)

## Consecuencias

- Angular standalone components + Signals para reactividad zoneless
- PrimeNG como librería de UI base, extendida por apex-component-library
- Auditoría universal: trigger `auditoria.trg_aud_biu` en todas las tablas
- `row_version` para optimistic locking en endpoints PATCH/PUT
- Migraciones numeradas de 3 dígitos en `db/migrations/`

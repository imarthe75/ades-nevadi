# ADR-0009: Arquitectura de Secretos con Vault + PgBouncer SCRAM-SHA-256

**Estado:** Aceptado  
**Fecha:** 2026-06-16  
**Autores:** Agente Residente ADES  

---

## Contexto

ADES necesita gestión segura de secretos (contraseñas, API keys, llaves privadas) y pool de conexiones
a PostgreSQL 18. En esta sesión se detectaron dos bugs críticos de integración que impedían el arranque
sano de `ades-api` y `ades-bff`.

---

## Decisiones

### 1. Prioridad de configuración: Docker env > Vault > `.env`

| Nivel | Fuente | Uso |
|-------|--------|-----|
| 1 (más alta) | Variables de entorno del contenedor Docker | URLs de host/puerto estructurales |
| 2 | HashiCorp Vault `secret/ades` | Contraseñas, API keys, llaves privadas |
| 3 (fallback) | `.env` | Desarrollo local sin Vault |

**Implementación FastAPI:** `os.environ.setdefault(k, str(v))` — preserva las vars del contenedor.  
**Implementación Spring BFF:** `spring.config.import: "optional:vault://"` — Vault rellena sólo lo que no está en el entorno.

### 2. Spring Cloud Vault 2023.0.3 + entrypoint token reader

Spring BFF usa `spring-cloud-starter-vault-config` con `kv-version: 2`. El token de Vault
se lee del volumen compartido `/vault/init/root_token.txt` vía `entrypoint.sh` antes de
que arranque la JVM (necesario para la fase bootstrap de Spring).

### 3. PgBouncer AUTH_TYPE: scram-sha-256

PostgreSQL 18 usa `scram-sha-256` por defecto. PgBouncer con `AUTH_TYPE: md5` falla al
intentar autenticar contra el servidor PostgreSQL con el error:
`cannot do SCRAM authentication: wrong password type`.

**Fix:** `AUTH_TYPE: scram-sha-256` en el servicio `pgbouncer` de docker-compose.

### 4. DATABASE_URL puerto interno Docker

El mapeo `6432:5432` en docker-compose significa:
- Host → PgBouncer: `localhost:6432`
- Dentro de la red Docker → PgBouncer: `ades-pgbouncer:5432`

El `DATABASE_URL` en variables de contenedor debe usar `:5432` (puerto interno), no `:6432`.

---

## Consecuencias

- **Positivo:** FastAPI y Spring BFF conectan correctamente a PgBouncer vía SCRAM; Vault provee secretos sin exponer contraseñas en el repositorio.  
- **Positivo:** El orden de prioridad explícito evita que Vault sobreescriba URLs estructurales de conexión que el orquestador gestiona.
- **Restricción:** El `root_token.txt` de Vault debe existir en el volumen `vault-init` antes de que arranque el BFF.

---

## Referencias

- `backend/app/core/config.py` — `os.environ.setdefault`
- `backend-spring/pom.xml` — `spring-cloud-starter-vault-config 2023.0.3`
- `backend-spring/entrypoint.sh` — lectura de token
- `docker-compose.yml` — pgbouncer `AUTH_TYPE: scram-sha-256`, `DATABASE_URL: ...@ades-pgbouncer:5432/ades`

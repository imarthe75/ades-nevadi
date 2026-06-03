# 📚 **Manual Técnico — PostgreSQL 18**

## 🧱 Requisitos Previos

*   Ubuntu **24.04 LTS**
*   Acceso **sudo**
*   **Docker** (solo si usarás DocumentDB OSS)
*   Conexión a internet

***

# 🧰 1) Instalación de PostgreSQL 18 y Extensiones

PostgreSQL 18 introduce **Asynchronous I/O (AIO)**, **UUIDv7**, mejoras de seguridad (TLS más estricto) y optimizaciones de planificación/índices. [\[postgresql.org\]](https://www.postgresql.org/docs/current/release-18.html)

## 1.1 Repositorio oficial (PGDG)

```bash
sudo apt update
sudo apt install -y curl gnupg lsb-release ca-certificates

echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" \
  | sudo tee /etc/apt/sources.list.d/pgdg.list

curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc \
  | sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/postgresql.gpg

sudo apt update
```

## 1.2 Paquetes recomendados

```bash
sudo apt install -y \
  postgresql-18 \
  postgresql-client-18 \
  postgresql-18-cron \
  postgresql-18-postgis-3 \
  postgresql-18-timescaledb \
  postgresql-18-pgvector \
  postgresql-18-oracle-fdw \
  postgresql-18-tds-fdw \
  postgresql-contrib
```

> El paquete **`postgresql-18-oracle-fdw`** existe en el repo PGDG para Ubuntu (ej. *Jammy*, con versión 2.8.x). [\[ubuntuupdates.org\]](https://www.ubuntuupdates.org/package/postgresql/jammy-pgdg/main/base/postgresql-18-oracle-fdw)

***

# 🧬 2) DocumentDB OSS (opcional)

```bash
docker pull ghcr.io/microsoft/documentdb:latest

docker run -d \
  --name documentdb \
  -p 5432:5432 \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=admin123 \
  ghcr.io/microsoft/documentdb:latest
```

***

# 🏗️ 3) Base de datos genérica (“sistema”)

```sql
CREATE DATABASE sistema
  WITH OWNER = admin_user
       ENCODING = 'UTF8'
       LC_COLLATE = 'es_MX.utf8'
       LC_CTYPE   = 'es_MX.utf8'
       TEMPLATE   = template0;
```

***

# 👥 4) Modelo de roles y descripciones

> **Nota:** todos los roles usan autenticación **SCRAM-SHA-256** (MD5 desaconsejado en PG18). [\[postgresql.org\]](https://www.postgresql.org/docs/current/release-18.html)

## 4.1 `admin_user` — Administración (superusuario)

**Propósito:** tareas de DBA, creación/gestión de DB y roles, mantenimiento.  
**Riesgos:** acceso total; úsalo con MFA y *password rotation*.

```sql
CREATE ROLE admin_user WITH LOGIN PASSWORD 'ClaveSegura_Admin123';
ALTER  ROLE admin_user WITH SUPERUSER CREATEDB CREATEROLE REPLICATION;
```

## 4.2 `dev_<nombre>` — Desarrollo (uno por desarrollador)

**Propósito:** crear/alterar objetos, probar funcionalidades y ejecutar funciones.  
**Alcance:** `public` con permisos de **CREATE** y DML completo.  
**Buenas prácticas:** un usuario por persona, `search_path` explícito por equipo.

```sql
CREATE ROLE dev_generico WITH LOGIN PASSWORD 'ClaveSegura_Dev123';

GRANT CONNECT ON DATABASE sistema TO dev_generico;
GRANT USAGE, CREATE ON SCHEMA public TO dev_generico;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES  IN SCHEMA public TO dev_generico;
GRANT EXECUTE                        ON ALL FUNCTIONS IN SCHEMA public TO dev_generico;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES   TO dev_generico;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT EXECUTE                        ON FUNCTIONS TO dev_generico;
```

> Repite para `dev_juan`, `dev_maria`, etc. con la misma política.

## 4.3 `migrator_user` — **Migrador** (ETL / cargas masivas)

**Propósito:** **escribe, borra, actualiza y hace cargas masivas** (ETL, migraciones, batch).  
**Alcance:** permisos DML en `public` (tablas actuales y futuras).

```sql
CREATE ROLE migrator_user WITH LOGIN PASSWORD 'ClaveSegura_Migrador123';

GRANT CONNECT ON DATABASE sistema TO migrator_user;
GRANT USAGE  ON SCHEMA public TO migrator_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO migrator_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT INSERT, UPDATE, DELETE ON TABLES TO migrator_user;
```

## 4.4 `operator_user` — Operación (soporte / monitoreo)

**Propósito:** consultas de soporte, ajustes menores y ejecución de funciones operativas.  
**Alcance:** `SELECT/UPDATE` en tablas y `EXECUTE` en funciones (sin `CREATE`).

```sql
CREATE ROLE operator_user WITH LOGIN PASSWORD 'ClaveSegura_Operador123';

GRANT CONNECT ON DATABASE sistema TO operator_user;
GRANT USAGE ON SCHEMA public TO operator_user;

GRANT SELECT, UPDATE ON ALL TABLES  IN SCHEMA public TO operator_user;
GRANT EXECUTE       ON ALL FUNCTIONS IN SCHEMA public TO operator_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT EXECUTE ON FUNCTIONS TO operator_user;
```

## 4.5 `app_web_user` — Aplicación (backend Java/.NET/Node o vía API)

**Propósito:** credenciales de la app; realizar DML y ejecutar funciones expuestas.  
**Buenas prácticas:** mínimo privilegio + **`SET ROLE`** si la app requiere *least-privilege* por módulo.

```sql
CREATE ROLE app_web_user WITH LOGIN PASSWORD 'ClaveSegura_AppWeb123';

GRANT CONNECT ON DATABASE sistema TO app_web_user;
GRANT USAGE   ON SCHEMA public TO app_web_user;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES  IN SCHEMA public TO app_web_user;
GRANT EXECUTE                        ON ALL FUNCTIONS IN SCHEMA public TO app_web_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES   TO app_web_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT EXECUTE                        ON FUNCTIONS TO app_web_user;
```

***

# 🔐 5) Seguridad en PG18: SCRAM + TLS 1.3

PG18 **depreca MD5** y refuerza prácticas de seguridad. Usa **SCRAM-SHA-256** y **TLS 1.3**. [\[postgresql.org\]](https://www.postgresql.org/docs/current/release-18.html)

## 5.1 `pg_hba.conf` (SCRAM)

```conf
# Autenticación segura para IPv4/IPv6
host    all     all     0.0.0.0/0     scram-sha-256
host    all     all     ::/0          scram-sha-256
```

## 5.2 `postgresql.conf` (TLS 1.3)

```conf
ssl = on
ssl_min_protocol_version = 'TLSv1.3'
ssl_prefer_server_ciphers = on
ssl_cert_file = '/etc/ssl/certs/server.crt'
ssl_key_file  = '/etc/ssl/private/server.key'
```

> Reinicia el servicio tras cambios de configuración. [\[postgresql.org\]](https://www.postgresql.org/docs/current/auth-pg-hba-conf.html)

***

# ⚡ 6) Asynchronous I/O (AIO) en PostgreSQL 18

**AIO** ofrece mejoras **2×–3×** en lecturas (sequential/bitmap scans y VACUUM) y soporta `io_uring` o *worker mode*. [\[postgresql.org\]](https://www.postgresql.org/docs/current/release-18.html), [\[cybertec-p...gresql.com\]](https://www.cybertec-postgresql.com/en/postgresql-18-better-i-o-performance-with-aio/)

## 6.1 Activación (`postgresql.conf`)

```conf
io_method  = 'io_uring'   # O 'worker' si io_uring no está disponible
io_workers = 4            # Ajusta a tu CPU/IOPS
```

## 6.2 Validación

```sql
SHOW io_method;
SELECT * FROM pg_aios;
```

> Cambios en `io_method` requieren **restart** (parámetro de contexto postmaster). [\[runebook.dev\]](https://runebook.dev/en/docs/postgresql/runtime-config-resource/GUC-IO-METHOD)

***

# 🔗 7) **oracle\_fdw** (método A — paquete PGDG)

**Objetivo:** acceder a Oracle vía FDW.  
**Requisito imprescindible:** **Oracle Instant Client** (Basic y preferible **SDK**) para disponer de librerías y *headers* (OCI). [\[oracle.com\]](https://www.oracle.com/database/technologies/instant-client/linux-x86-64-downloads.html), [\[docs.oracle.com\]](https://docs.oracle.com/en//database/oracle/oracle-database/19/lnoci/instant-client.html), [\[postgrespro.com\]](https://postgrespro.com/docs/postgrespro/current/oracle-fdw)

## 7.1 Instalar el paquete

```bash
sudo apt install -y postgresql-18-oracle-fdw
```

> El paquete existe en PGDG; versión 2.8.x (según distro). [\[ubuntuupdates.org\]](https://www.ubuntuupdates.org/package/postgresql/jammy-pgdg/main/base/postgresql-18-oracle-fdw)

## 7.2 Instalar **Oracle Instant Client** (descarga oficial)

1.  Descarga **Basic** y (recomendado) **SDK** desde Oracle:  
    <https://www.oracle.com/database/technologies/instant-client/linux-x86-64-downloads.html> [\[oracle.com\]](https://www.oracle.com/database/technologies/instant-client/linux-x86-64-downloads.html)
2.  Descomprime en `/opt/oracle/instantclient_XX_X/` (o instala RPM/ZIP según guía). [\[docs.oracle.com\]](https://docs.oracle.com/en//database/oracle/oracle-database/19/lnoci/instant-client.html)
3.  Exporta variables (en el entorno del servicio o del usuario `postgres`):

```bash
export LD_LIBRARY_PATH=/opt/oracle/instantclient_21_12:$LD_LIBRARY_PATH
export OCI_LIB_DIR=/opt/oracle/instantclient_21_12
export OCI_INC_DIR=/opt/oracle/instantclient_21_12/sdk/include
```

> **Por qué es necesario:** `oracle_fdw` requiere las librerías de **Oracle Instant Client** para enlazar con OCI; sin el cliente, no podrá conectarse a Oracle. [\[postgrespro.com\]](https://postgrespro.com/docs/postgrespro/current/oracle-fdw), [\[docs.oracle.com\]](https://docs.oracle.com/en//database/oracle/oracle-database/19/lnoci/instant-client.html)

## 7.3 Crear la extensión y el *foreign server*

```sql
CREATE EXTENSION IF NOT EXISTS oracle_fdw;

-- Cambia host/servicio a tu TNS/DSN (EZCONNECT o tnsnames.ora)
CREATE SERVER oradb FOREIGN DATA WRAPPER oracle_fdw
OPTIONS (dbserver '//oracle_host:1521/ORCLCDB');

-- Asigna el uso del servidor a un rol no superusuario (opcional)
GRANT USAGE ON FOREIGN SERVER oradb TO app_web_user;

-- Mapeo de usuario (credenciales Oracle)
CREATE USER MAPPING FOR app_web_user
SERVER oradb OPTIONS (user 'ora_user', password 'Ora_Secret_123');
```

> La guía del proyecto y documentación de `oracle_fdw` detallan requisitos y uso (incluye *pushdown*, `EXPLAIN VERBOSE` con plan Oracle). [\[github.com\]](https://github.com/laurenz/oracle_fdw), [\[laurenz.github.io\]](https://laurenz.github.io/oracle_fdw/)

***

# 🕒 8) **pg\_cron** para TODAS las bases de datos (sin errores de autenticación)

**Cómo funciona:** `pg_cron` es una extensión con *background worker*; debe cargarse en arranque (shared preload) y mantiene metadatos en **una sola base** por clúster (por defecto, `postgres`). Puedes ejecutar trabajos en **otras bases** con `cron.schedule_in_database(...)`. [\[enterprisedb.com\]](https://www.enterprisedb.com/docs/pg_extensions/pg_cron/configuring/), [\[github.com\]](https://github.com/citusdata/pg_cron)

## 8.1 Activación en `postgresql.conf`

```conf
shared_preload_libraries = 'pg_cron'

# Base "host" de metadatos del cron (por defecto es 'postgres'):
cron.database_name = 'postgres'
```

> Reinicia el servicio y **crea la extensión** en la base indicada:  
> `CREATE EXTENSION pg_cron;` (en `postgres`, si usas el valor por defecto). [\[enterprisedb.com\]](https://www.enterprisedb.com/docs/pg_extensions/pg_cron/configuring/)

## 8.2 Evitar errores de conexión del *worker* (clave)

`pg_cron` abre **conexiones libpq** internas; por tanto, **debe existir** una regla en `pg_hba.conf` que permita al **usuario que programa/ejecuta** el trabajo (p. ej. `admin_user`) conectarse localmente **sin bloqueos**. [\[deepwiki.com\]](https://deepwiki.com/citusdata/pg_cron/3.1-installation-and-configuration)

### Recomendado (seguro con SCRAM):

```conf
# Conexión local por socket UNIX para admin_user (sin "trust")
local   all     admin_user                 scram-sha-256
host    all     admin_user 127.0.0.1/32   scram-sha-256
host    all     admin_user ::1/128        scram-sha-256
```

> Si usas libpq con contraseña, asegúrate de que el **usuario del proceso** (normalmente `postgres`) tenga **\~/.pgpass** con la credencial de `admin_user`, o usa `cron.database_name` + `cron.schedule_in_database` con un usuario que tenga acceso sin prompt. (El *worker* crea conexiones como cliente; el control de acceso es el mismo que cualquier conexión). [\[deepwiki.com\]](https://deepwiki.com/citusdata/pg_cron/3.1-installation-and-configuration)

> (En entornos gestionados, muchos proveedores recomiendan **centralizar en `postgres`** y apuntar a otras DBs con `schedule_in_database`.) [\[stackoverflow.com\]](https://stackoverflow.com/questions/72213501/using-pg-cron-extension-on-cloud-sql)

## 8.3 Programar trabajos en cualquier base

*   **En la misma base** donde está instalada la extensión:

```sql
-- Vacuum diario a las 03:00 (GMT) en la base "postgres"
SELECT cron.schedule('vacuum-diario', '0 3 * * *', 'VACUUM');
```

*   **En otra base** del mismo clúster:

```sql
-- Vacuum semanal en la base "sistema"
SELECT cron.schedule_in_database('vacuum-semanal', '0 4 * * 0', 'VACUUM', 'sistema');
```

> `cron.schedule_in_database` es la forma soportada de direccionar **otra DB**. [\[enterprisedb.com\]](https://www.enterprisedb.com/docs/pg_extensions/pg_cron/configuring/), [\[stackoverflow.com\]](https://stackoverflow.com/questions/72213501/using-pg-cron-extension-on-cloud-sql)

*   **Ver trabajos y ejecuciones**:

```sql
SELECT * FROM cron.job;
SELECT * FROM cron.job_run_details ORDER BY end_time DESC LIMIT 20;
```

> `pg_cron` puede ejecutar varios trabajos en paralelo; mantiene una sola instancia por job. [\[github.com\]](https://github.com/citusdata/pg_cron)

***

# 🌐 9) Acceso remoto

`postgresql.conf`:

```conf
listen_addresses = '*'
```

`pg_hba.conf`:

```conf
host all all 0.0.0.0/0 scram-sha-256
host all all ::/0      scram-sha-256
```

> Recarga/reinicio tras cambios. [\[postgresql.org\]](https://www.postgresql.org/docs/current/auth-pg-hba-conf.html)

***

# 📦 10) Extensiones sugeridas

```sql
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";  -- (PG18 aporta uuidv7() nativo)
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS postgres_fdw;
CREATE EXTENSION IF NOT EXISTS dblink;
```

> PG18 incorpora **`uuidv7()`** nativo para IDs ordenables temporalmente (mejora índices/fragmentación). [\[postgresql.org\]](https://www.postgresql.org/docs/current/release-18.html)

***

# 🧪 11) Pruebas rápidas (pgTAP opcional)

```sql
SELECT plan(3);

SELECT ok(uuidv7() IS NOT NULL, 'UUIDv7 disponible (PG18)');
SELECT ok('ñ' > 'n', 'Collation es_MX funcional');
SELECT ok((SELECT COUNT(*) FROM pg_aios) >= 0, 'AIO presente');

SELECT * FROM finish();
```

***

# 🧠 12) Buenas prácticas

*   **UUIDv7** para PKs distribuidas con orden temporal. [\[postgresql.org\]](https://www.postgresql.org/docs/current/release-18.html)
*   **AIO**: prueba primero con `io_uring`; si no está disponible o no mejora, usa `worker`. [\[runebook.dev\]](https://runebook.dev/en/docs/postgresql/runtime-config-resource/GUC-IO-METHOD)
*   **TLS 1.3** en todas las conexiones. [\[postgresql.org\]](https://www.postgresql.org/docs/current/release-18.html)
*   **pg\_cron** centralizado en `postgres` + `schedule_in_database` para múltiples bases. [\[stackoverflow.com\]](https://stackoverflow.com/questions/72213501/using-pg-cron-extension-on-cloud-sql)
*   `oracle_fdw`: mantén **Instant Client** actualizado (Basic + SDK) y variables de entorno adecuadas. [\[docs.oracle.com\]](https://docs.oracle.com/en//database/oracle/oracle-database/19/lnoci/instant-client.html)

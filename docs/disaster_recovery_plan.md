# Plan de Recuperación ante Desastres (Disaster Recovery Plan - DRP)
## ADES - Instituto Nevadi (Fase 27.1)

Este documento describe la estrategia de respaldo y los procedimientos detallados para la recuperación de los datos y servicios del sistema ADES en caso de falla crítica, corrupción de datos o pérdida de infraestructura.

---

## 1. Métricas Objetivas (RTO & RPO)

Para asegurar la continuidad del servicio educativo y administrativo del Instituto Nevadi, se han establecido las siguientes métricas:

| Métrica | Objetivo | Descripción |
| :--- | :--- | :--- |
| **RPO (Recovery Point Objective)** | **24 Horas** | Pérdida máxima aceptable de datos. Dado que los respaldos se ejecutan de manera diaria, en el peor de los escenarios se perdería como máximo la información generada en el último día. |
| **RTO (Recovery Time Objective)** | **2 Horas** | Tiempo máximo aceptable para restaurar la operatividad total del sistema después de declararse un desastre. |

---

## 2. Estrategia y Rutas de Respaldo

Los respaldos se generan localmente en el host dentro del directorio del proyecto (`/opt/ades`) y deben ser replicados externamente mediante un agente de sincronización secundario (por ejemplo, a un almacenamiento en la nube o servidor externo de respaldo).

### Rutas Locales en el Host
- **Respaldos de PostgreSQL:** `/opt/ades/backups/postgres/`
  - Contiene volcados comprimidos (`.sql.gz`) con rotación de **30 días**.
  - Archivos generados:
    - `ades_db_YYYYMMDD_HHMMSS.sql.gz` (Datos académicos y administrativos)
    - `authentik_db_YYYYMMDD_HHMMSS.sql.gz` (Usuarios, roles y sesiones de Authentik)
    - `postgres_globals_YYYYMMDD_HHMMSS.sql.gz` (Roles, permisos globales del motor PostgreSQL)
- **Respaldos de MinIO (Storage):** `/opt/ades/backups/minio/`
  - Contiene una réplica exacta (mirror) de todos los archivos, logos, tareas y boletas almacenados en los buckets de MinIO.

---

## 3. Procedimiento de Recuperación de PostgreSQL

Siga estos pasos para restaurar la base de datos completa.

> [!WARNING]
> La restauración de la base de datos sobrescribirá los datos actuales del contenedor. Asegúrese de detener los servicios que escriben activamente en la base de datos (FastAPI API y Celery Workers) antes de proceder.

### Paso 3.1: Detener la API y los Workers de Celery
Para evitar escrituras concurrentes durante la restauración:
```bash
cd /opt/ades
docker compose stop ades-api celery-worker celery-beat superset
```

### Paso 3.2: Identificar los Archivos de Respaldo a Utilizar
Seleccione los archivos correspondientes a la misma marca de tiempo (`YYYYMMDD_HHMMSS`) en `/opt/ades/backups/postgres/`.

### Paso 3.3: Restaurar los Roles y Configuración Global (Opcional/Solo si es instalación nueva)
Si está restaurando en un servidor completamente nuevo, debe recrear los roles globales de PostgreSQL:
```bash
gunzip -c /opt/ades/backups/postgres/postgres_globals_YYYYMMDD_HHMMSS.sql.gz | docker exec -i ades-postgres psql -U ades_admin -d postgres
```

### Paso 3.4: Restaurar la Base de Datos de ADES
1. Limpiar y recrear la base de datos existente (elimina tablas y datos actuales):
   ```bash
   docker exec -i ades-postgres psql -U ades_admin -d postgres -c "DROP DATABASE IF EXISTS ades;"
   docker exec -i ades-postgres psql -U ades_admin -d postgres -c "CREATE DATABASE ades WITH OWNER ades_admin;"
   ```
2. Cargar el respaldo:
   ```bash
   gunzip -c /opt/ades/backups/postgres/ades_db_YYYYMMDD_HHMMSS.sql.gz | docker exec -i ades-postgres psql -U ades_admin -d ades
   ```

### Paso 3.5: Restaurar la Base de Datos de Authentik
1. Limpiar y recrear la base de datos de Authentik:
   ```bash
   docker exec -i ades-postgres psql -U ades_admin -d postgres -c "DROP DATABASE IF EXISTS authentik;"
   docker exec -i ades-postgres psql -U ades_admin -d postgres -c "CREATE DATABASE authentik WITH OWNER authentik;"
   ```
2. Cargar el respaldo:
   ```bash
   gunzip -c /opt/ades/backups/postgres/authentik_db_YYYYMMDD_HHMMSS.sql.gz | docker exec -i ades-postgres psql -U authentik -d authentik
   ```

### Paso 3.6: Levantar los servicios nuevamente
```bash
docker compose start ades-api celery-worker celery-beat superset
```

---

## 4. Procedimiento de Recuperación de MinIO (Archivos/S3)

MinIO utiliza el cliente `mc` dentro del contenedor para sincronizar de vuelta los archivos desde el volumen de respaldo local.

### Paso 4.1: Sincronizar el respaldo local hacia el storage de MinIO
Para restaurar todos los archivos desde `/opt/ades/backups/minio/` de vuelta al sistema de archivos activo de MinIO:

```bash
# Sincroniza desde el directorio montado de respaldos (/backups/minio) hacia el alias local de MinIO
docker exec ades-minio mc mirror --overwrite /backups/minio local/
```

### Paso 4.2: Validar acceso en la Consola Web de MinIO
1. Acceda a `http://localhost:9001` (o la URL de producción configurada).
2. Autentíquese utilizando las credenciales `MINIO_ROOT_USER` y `MINIO_ROOT_PASSWORD` de su archivo `.env`.
3. Verifique que los buckets `ades-archivos` y `tareas-entregas` existan y tengan sus archivos restaurados.

---

## 5. Automatización con Cron

Para asegurar la ejecución regular de estos respaldos, añada las siguientes líneas al crontab del usuario root o administrador en el host:

```bash
# Abrir el editor cron
sudo crontab -e
```

Agregue las tareas programadas (por ejemplo, ejecución diaria a las 02:00 AM y 02:30 AM respectivamente):

```cron
# Respaldar PostgreSQL todos los días a las 02:00 AM
0 2 * * * /opt/ades/scripts/backup_postgres.sh >> /var/log/ades_backup_postgres.log 2>&1

# Respaldar MinIO todos los días a las 02:30 AM
30 2 * * * /opt/ades/scripts/backup_minio.sh >> /var/log/ades_backup_minio.log 2>&1
```

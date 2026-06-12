#!/bin/bash
# =============================================================================
# ADES - Script de Respaldo de PostgreSQL
# Automatiza la copia de seguridad de las bases de datos de ADES y Authentik.
# Diseñado para ser ejecutado por un cron job en el host.
# Rotación automática: mantiene copias de los últimos 30 días.
# =============================================================================

set -e

# Configuración por defecto
PROJECT_DIR="/opt/ades"
BACKUP_DIR="${PROJECT_DIR}/backups/postgres"
RETENTION_DAYS=30
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

echo "[$(date)] Iniciando backup de PostgreSQL..."

# Cargar variables de entorno del archivo .env si existe
if [ -f "${PROJECT_DIR}/.env" ]; then
    echo "Cargando configuración desde ${PROJECT_DIR}/.env"
    # Filtrar comentarios y líneas vacías, luego exportar
    export $(grep -v '^#' "${PROJECT_DIR}/.env" | xargs -d '\n')
else
    echo "ERROR: No se encontró el archivo ${PROJECT_DIR}/.env"
    exit 1
fi

# Validar variables críticas
POSTGRES_DB=${POSTGRES_DB:-ades}
POSTGRES_USER=${POSTGRES_USER:-ades_admin}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
AUTHENTIK_DB=${AUTHENTIK_DB:-authentik}

if [ -z "${POSTGRES_PASSWORD}" ]; then
    echo "ERROR: POSTGRES_PASSWORD no está definida en el archivo .env"
    exit 1
fi

# Crear directorio de respaldos si no existe
mkdir -p "${BACKUP_DIR}"

# Nombre de los archivos de salida
ADES_BACKUP_FILE="${BACKUP_DIR}/ades_db_${TIMESTAMP}.sql.gz"
AUTH_BACKUP_FILE="${BACKUP_DIR}/authentik_db_${TIMESTAMP}.sql.gz"
GLOBALS_BACKUP_FILE="${BACKUP_DIR}/postgres_globals_${TIMESTAMP}.sql.gz"

echo "Directorio de destino: ${BACKUP_DIR}"

# 1. Respaldar base de datos principal de ADES
echo "Respaldando base de datos '${POSTGRES_DB}'..."
docker exec -e PGPASSWORD="${POSTGRES_PASSWORD}" ades-postgres pg_dump \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    -F p | gzip > "${ADES_BACKUP_FILE}"
echo "✓ Respaldo de '${POSTGRES_DB}' completado: $(basename ${ADES_BACKUP_FILE}) ($(du -sh ${ADES_BACKUP_FILE} | cut -f1))"

# 2. Respaldar base de datos de Authentik
echo "Respaldando base de datos '${AUTHENTIK_DB}'..."
docker exec -e PGPASSWORD="${POSTGRES_PASSWORD}" ades-postgres pg_dump \
    -U "${POSTGRES_USER}" \
    -d "${AUTHENTIK_DB}" \
    -F p | gzip > "${AUTH_BACKUP_FILE}"
echo "✓ Respaldo de '${AUTHENTIK_DB}' completado: $(basename ${AUTH_BACKUP_FILE}) ($(du -sh ${AUTH_BACKUP_FILE} | cut -f1))"

# 3. Respaldar globales (roles y tablespaces) para facilitar restauración completa
echo "Respaldando roles y configuración global de PostgreSQL..."
docker exec -e PGPASSWORD="${POSTGRES_PASSWORD}" ades-postgres pg_dumpall \
    -U "${POSTGRES_USER}" \
    --globals-only | gzip > "${GLOBALS_BACKUP_FILE}"
echo "✓ Respaldo de globales completado: $(basename ${GLOBALS_BACKUP_FILE}) ($(du -sh ${GLOBALS_BACKUP_FILE} | cut -f1))"

# 4. Limpieza de respaldos antiguos (rotación de 30 días)
echo "Ejecutando rotación de respaldos antiguos (antigüedad mayor a ${RETENTION_DAYS} días)..."
find "${BACKUP_DIR}" -name "*.sql.gz" -type f -mtime +${RETENTION_DAYS} -exec rm -v {} \;

echo "[$(date)] ¡Proceso de backup de PostgreSQL finalizado con éxito!"

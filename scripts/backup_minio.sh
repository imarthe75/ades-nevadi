#!/bin/bash
# =============================================================================
# ADES - Script de Respaldo de MinIO (Almacenamiento de Archivos)
# Sincroniza todos los buckets de MinIO al almacenamiento de respaldos local.
# Diseñado para ser ejecutado por un cron job en el host.
# =============================================================================

set -e

# Configuración por defecto
PROJECT_DIR="/opt/ades"
BACKUP_DIR="${PROJECT_DIR}/backups/minio"

echo "[$(date)] Iniciando backup de MinIO..."

# Cargar variables de entorno del archivo .env si existe
if [ -f "${PROJECT_DIR}/.env" ]; then
    echo "Cargando configuración desde ${PROJECT_DIR}/.env"
    export $(grep -v '^#' "${PROJECT_DIR}/.env" | xargs -d '\n')
else
    echo "ERROR: No se encontró el archivo ${PROJECT_DIR}/.env"
    exit 1
fi

# Validar variables críticas
MINIO_ROOT_USER=${MINIO_ROOT_USER:-ades_minio}
MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD}

if [ -z "${MINIO_ROOT_PASSWORD}" ]; then
    echo "ERROR: MINIO_ROOT_PASSWORD no está definida en el archivo .env"
    exit 1
fi

# Crear directorio de respaldos en el host si no existe
mkdir -p "${BACKUP_DIR}"

# 1. Configurar o actualizar el alias 'local' dentro del contenedor de MinIO
echo "Configurando credenciales del cliente MinIO (mc) en el contenedor..."
docker exec ades-minio mc alias set local http://localhost:9000 "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"

# 2. Ejecutar la sincronización (mirror) de MinIO hacia el directorio montado /backups/minio
echo "Sincronizando (mirror) buckets de MinIO..."
# --overwrite reemplaza archivos si han cambiado, --remove borra en el destino si ya no existen en origen
docker exec ades-minio mc mirror --overwrite --remove local /backups/minio

echo "✓ Sincronización de MinIO completada con éxito."
echo "Estructura actual en backup local de MinIO:"
ls -R "${BACKUP_DIR}"

echo "[$(date)] ¡Proceso de backup de MinIO finalizado con éxito!"

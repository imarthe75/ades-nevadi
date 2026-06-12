#!/bin/bash
# db/scripts/init_multi_db.sh
# Crea las bases de datos adicionales requeridas por Authentik y Superset
# Se ejecuta automáticamente en el primer arranque de postgres

set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Base de datos para Authentik
    SELECT 'CREATE DATABASE authentik OWNER ${AUTHENTIK_DB_USER:-authentik}'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'authentik')\gexec

    SELECT 'CREATE USER ${AUTHENTIK_DB_USER:-authentik} WITH PASSWORD ''${AUTHENTIK_DB_PASS}'''
    WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${AUTHENTIK_DB_USER:-authentik}')\gexec

    GRANT ALL PRIVILEGES ON DATABASE authentik TO ${AUTHENTIK_DB_USER:-authentik};

    -- Base de datos para Superset (metadatos internos)
    SELECT 'CREATE DATABASE superset OWNER ${POSTGRES_USER}'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'superset')\gexec

    -- Base de datos para n8n (workflows y executions)
    SELECT 'CREATE DATABASE n8n OWNER ${POSTGRES_USER}'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'n8n')\gexec

    -- Base de datos para Paperless-ngx (gestión documental OCR — FASE 28)
    SELECT 'CREATE DATABASE paperless OWNER ${POSTGRES_USER}'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'paperless')\gexec

    -- Extensiones en la base principal ADES
    \c ${POSTGRES_DB:-ades}
    CREATE EXTENSION IF NOT EXISTS "pgcrypto";
    CREATE EXTENSION IF NOT EXISTS "vector";
    CREATE EXTENSION IF NOT EXISTS "pg_trgm";
    CREATE EXTENSION IF NOT EXISTS "unaccent";
EOSQL

echo "✅ Bases de datos inicializadas: ades, authentik, superset, n8n"

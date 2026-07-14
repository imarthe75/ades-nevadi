#!/bin/bash
# =============================================================================
# ADES — Script General de Inicialización y Limpieza de Base de Datos
# Uso: bash db/scripts/setup_database.sh [opciones]
# Opciones:
#   --mode=production     Inicializa con datos de producción limpios (solo catálogos)
#   --mode=development    Inicializa con datos de prueba/simulación (default)
#   --clean-only          Limpia datos transaccionales de la base actual preservando catálogos
#   --non-interactive     Ejecuta sin solicitar confirmaciones
# =============================================================================

set -euo pipefail

# Colores de salida
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

MODE="development"
CLEAN_ONLY=false
INTERACTIVE=true

# Parsear argumentos
for arg in "$@"; do
  case $arg in
    --mode=production)
      MODE="production"
      shift
      ;;
    --mode=development)
      MODE="development"
      shift
      ;;
    --clean-only)
      CLEAN_ONLY=true
      shift
      ;;
    --non-interactive)
      INTERACTIVE=false
      shift
      ;;
  esac
done

cd /opt/ades

# Cargar variables de entorno del archivo .env si existe
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

DB_USER="${POSTGRES_USER:-ades_admin}"
DB_NAME="${POSTGRES_DB:-ades}"

# Detectar comando SQL (docker compose vs psql local)
if docker compose ps | grep -q "postgres"; then
  SQL_CMD="docker compose exec -T postgres psql -U $DB_USER -d $DB_NAME"
  SQL_ADMIN_CMD="docker compose exec -T postgres psql -U $DB_USER -d postgres"
else
  export PGPASSWORD="${POSTGRES_PASSWORD:-}"
  SQL_CMD="psql -U $DB_USER -d $DB_NAME -h localhost -P pager=off"
  SQL_ADMIN_CMD="psql -U $DB_USER -d postgres -h localhost -P pager=off"
fi

confirm() {
  if [ "$INTERACTIVE" = true ]; then
    read -p "$1 [y/N]: " response
    case "$response" in
      [yY][eE][sS]|[yY])
        true
        ;;
      *)
        echo "Operación cancelada."
        exit 1
        ;;
    esac
  fi
}

# =============================================================================
# MODO DE LIMPIEZA EXCLUSIVO (--clean-only)
# =============================================================================
if [ "$CLEAN_ONLY" = true ]; then
  echo -e "${YELLOW}⚠️  ADVERTENCIA: Se eliminarán todos los datos de pruebas y transaccionales (calificaciones, asistencias, alumnos, tutores, etc.) de la base de datos '$DB_NAME'. Los catálogos, configuraciones e infraestructura de roles se conservarán.${NC}"
  confirm "¿Deseas proceder con la limpieza?"

  echo "=== [1/2] Limpiando tablas transaccionales ==="
  $SQL_CMD -c "
    BEGIN;
    -- Desactivar triggers temporalmente
    SET CONSTRAINTS ALL DEFERRED;

    -- Tablas transaccionales y de negocio
    TRUNCATE TABLE 
      public.ades_calificaciones_tareas,
      public.ades_calificaciones_evaluaciones,
      public.ades_calificaciones_periodo,
      public.ades_tareas_entregas,
      public.ades_asistencias,
      public.ades_avance_planificacion,
      public.ades_reportes_academicos,
      public.ades_reportes_conducta,
      public.ades_justificaciones_falta,
      public.ades_alertas_academicas,
      public.ades_alertas_cumplimiento,
      public.ades_ai_conversaciones,
      public.ades_acuses_comunicado,
      public.ades_eventos_bienestar,
      public.ades_riesgo_conductual,
      public.ades_medicamentos_alumno,
      public.ades_condiciones_cronicas,
      public.ades_incidentes_medicos,
      public.ades_actas_incidente_medico,
      public.ades_solicitudes_tramites,
      public.ades_solicitudes_admision,
      public.ades_bajas,
      public.ades_cambios_grupo,
      public.ades_inscripciones,
      public.ades_inscripciones_optativas,
      public.ades_horarios,
      public.ades_asignaciones_docentes,
      public.ades_disponibilidad_docente,
      public.ades_disponibilidad_aula,
      public.ades_suplencias,
      public.ades_audit_log,
      public.ades_notificaciones_sistema,
      public.ades_h5p_resultados,
      public.ades_lp_progreso,
      public.ades_lp_asignaciones
    CASCADE;

    -- Limpiar personas y usuarios que no sean de sistema/admin
    DELETE FROM public.ades_usuario_roles WHERE usuario_id NOT IN (
      SELECT id FROM public.ades_usuarios WHERE nombre_usuario IN ('ades_admin', 'admin', 'migration', 'system')
    );
    DELETE FROM public.ades_usuarios WHERE nombre_usuario NOT IN ('ades_admin', 'admin', 'migration', 'system');
    DELETE FROM public.ades_estudiantes;
    DELETE FROM public.ades_tutores_alumnos;
    DELETE FROM public.ades_contactos_familiar;
    DELETE FROM public.ades_personas WHERE id NOT IN (
      SELECT persona_id FROM public.ades_usuarios WHERE nombre_usuario IN ('ades_admin', 'admin', 'system')
    );

    COMMIT;
  "
  echo "=== [2/2] Optimizando espacio ==="
  $SQL_CMD -c "VACUUM ANALYZE;"
  echo -e "${GREEN}✅ Limpieza completada. La base de datos '$DB_NAME' está lista para producción.${NC}"
  exit 0
fi

# =============================================================================
# MODO DE INICIALIZACIÓN COMPLETA (FRESH SETUP)
# =============================================================================
echo -e "${YELLOW}⚠️  ADVERTENCIA: Esto destruirá la base de datos '$DB_NAME' y la recreará en modo: ${BLUE}$(echo "$MODE" | tr '[:lower:]' '[:upper:]')${NC}."
confirm "¿Proceder con la inicialización completa?"

echo -e "\n=== [1/5] Terminando conexiones activas a '$DB_NAME' ==="
$SQL_ADMIN_CMD -c "
  SELECT pg_terminate_backend(pid)
  FROM pg_stat_activity
  WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid();" || true

echo -e "\n=== [2/5] Dropeando y recreando la base de datos ==="
$SQL_ADMIN_CMD -c "DROP DATABASE IF EXISTS $DB_NAME;"
$SQL_ADMIN_CMD -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;"

# Habilitar extensiones requeridas
$SQL_CMD -c "
  CREATE EXTENSION IF NOT EXISTS \"pgcrypto\";
  CREATE EXTENSION IF NOT EXISTS \"vector\";
  CREATE EXTENSION IF NOT EXISTS \"pg_trgm\";
  CREATE EXTENSION IF NOT EXISTS \"unaccent\";
"

echo -e "\n=== [3/5] Aplicando migraciones de base de datos en orden secuencial ==="
# Aplicar todas las migraciones en orden alfabético
for sql_file in $(ls db/migrations/*.sql | sort); do
  echo "Aplicando: $(basename "$sql_file")"
  $SQL_CMD < "$sql_file"
done

echo -e "\n=== [4/5] Población de Catálogos y Semilla Base ==="
$SQL_CMD < db/seeds/001_datos_base.sql

# Aplicar las correcciones oficiales de catálogos y triggers (135 y 136)
echo "Aplicando corrección oficial de países, estados y roles..."
$SQL_CMD < db/migrations/135_paises_estados_roles_fix.sql
$SQL_CMD < db/migrations/136_audit_triggers_fix.sql

# =============================================================================
# MODO DESARROLLO / PRUEBAS (Carga de Datos de Simulación)
# =============================================================================
if [ "$MODE" = "development" ]; then
  echo -e "\n=== [5/5] Modo Desarrollo: Cargando datos de simulación y testing ==="
  
  # Buscar e importar archivos de semilla específicos
  if [ -f db/seeds/002_grupos_profesores.sql ]; then
    echo "Cargando grupos y profesores..."
    $SQL_CMD < db/seeds/002_grupos_profesores.sql
  fi
  
  if [ -f db/seeds/003_alumnos_padres.sql ]; then
    echo "Cargando alumnos y tutores..."
    $SQL_CMD < db/seeds/003_alumnos_padres.sql
  fi
  
  if [ -f db/seeds/004_plan_estudios.sql ]; then
    echo "Cargando planes de estudio..."
    $SQL_CMD < db/seeds/004_plan_estudios.sql
  fi
  
  if [ -f db/seeds/005_disponibilidad_aulas.sql ]; then
    echo "Cargando disponibilidad y aulas..."
    $SQL_CMD < db/seeds/005_disponibilidad_aulas.sql
  fi

  echo "Ejecutando simulación integral..."
  if docker compose ps | grep -q "ades-api" || docker ps | grep -q "ades-api"; then
    docker compose exec -T ades-api python db/seeds/006_simulacion_integral.py || true
  else
    python3 db/seeds/006_simulacion_integral.py || true
  fi
else
  echo -e "\n=== [5/5] Modo Producción: Omitiendo datos de simulación ==="
  echo "La base de datos contiene únicamente la estructura, catálogos limpios e infraestructura de roles."
fi

# Limpieza de docker
if [ -f .agents/AGENTS.md ]; then
  echo -e "\n=== Ejecutando limpieza de espacio del servidor (Regla Residente) ==="
  docker system prune -a --volumes -f || true
fi

echo -e "\n${GREEN}✅ Proceso de inicialización de Base de Datos finalizado con éxito en modo: ${BLUE}$(echo "$MODE" | tr '[:lower:]' '[:upper:]')${NC}"

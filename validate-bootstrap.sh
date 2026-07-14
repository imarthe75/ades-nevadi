#!/bin/bash
# =============================================================================
# Validar Bootstrap — Verificar que todos los servicios FASE 1 están correctos
# =============================================================================

set -e

echo "╔════════════════════════════════════════════════════════════════════╗"
echo "║        VALIDACIÓN DE BOOTSTRAP — ADES FASE 1                      ║"
echo "╚════════════════════════════════════════════════════════════════════╝"
echo ""

PASS=0
FAIL=0

# Colores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Función de logging
test_ok() {
    echo -e "${GREEN}✓${NC} $1"
    PASS=$((PASS + 1))
}

test_fail() {
    echo -e "${RED}✗${NC} $1"
    FAIL=$((FAIL + 1))
}

test_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
}

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "1️⃣  DOCKER COMPOSE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if sudo docker-compose ps postgres | grep -q "Up"; then
    test_ok "PostgreSQL está UP"
else
    test_fail "PostgreSQL no está UP"
fi

if sudo docker-compose ps valkey | grep -q "Up"; then
    test_ok "Valkey está UP"
else
    test_fail "Valkey no está UP"
fi

if sudo docker-compose ps authentik-server | grep -q "Up"; then
    test_ok "Authentik está UP"
else
    test_fail "Authentik no está UP"
fi

if sudo docker-compose ps ades-api | grep -q "Up"; then
    test_ok "FastAPI está UP"
else
    test_fail "FastAPI no está UP"
fi

if sudo docker-compose ps ades-bff | grep -q "Up"; then
    test_ok "Spring BFF está UP"
else
    test_fail "Spring BFF no está UP"
fi

if sudo docker-compose ps nginx | grep -q "Up"; then
    test_ok "nginx está UP"
else
    test_fail "nginx no está UP"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "2️⃣  POSTGRESQL"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if sudo docker-compose exec postgres pg_isready -U ades_admin -d ades >/dev/null 2>&1; then
    test_ok "PostgreSQL responde a conexiones"

    # Verificar migraciones
    TABLE_COUNT=$(sudo docker-compose exec -T postgres psql -U ades_admin -d ades -t -c \
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'ades_%';" 2>/dev/null || echo "0")

    if [ "$TABLE_COUNT" -gt 10 ]; then
        test_ok "Tablas ADES creadas ($TABLE_COUNT encontradas)"
    else
        test_fail "Migraciones incompletas (solo $TABLE_COUNT tablas)"
    fi
else
    test_fail "PostgreSQL no responde"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "3️⃣  HEALTHCHECKS"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if curl -s http://localhost:8000/api/v1/health >/dev/null 2>&1; then
    test_ok "FastAPI /health responde"
else
    test_fail "FastAPI /health no responde"
fi

if curl -s http://localhost:8080/actuator/health >/dev/null 2>&1; then
    test_ok "Spring Boot /actuator/health responde"
else
    test_fail "Spring Boot /actuator/health no responde"
fi

if curl -s http://localhost:9000/ >/dev/null 2>&1; then
    test_ok "SeaweedFS está accesible"
else
    test_fail "SeaweedFS no está accesible"
fi

if [ -f "/etc/letsencrypt/live/ades.setag.mx/cert.pem" ]; then
    test_ok "Certificados SSL encontrados"
else
    test_warn "Certificados SSL no encontrados (ejecutar: bash init-certbot.sh)"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "4️⃣  DATOS DE EJEMPLO"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

ALUMNOS=$(sudo docker-compose exec -T postgres psql -U ades_admin -d ades -t -c \
    "SELECT COUNT(*) FROM ades_estudiantes;" 2>/dev/null || echo "0")

GRUPOS=$(sudo docker-compose exec -T postgres psql -U ades_admin -d ades -t -c \
    "SELECT COUNT(*) FROM ades_grupos;" 2>/dev/null || echo "0")

MATERIAS=$(sudo docker-compose exec -T postgres psql -U ades_admin -d ades -t -c \
    "SELECT COUNT(*) FROM ades_materias;" 2>/dev/null || echo "0")

if [ "$ALUMNOS" -gt 0 ]; then
    test_ok "Alumnos cargados ($ALUMNOS registros)"
else
    test_warn "Alumnos no cargados (ejecutar: bash DATOS_EJEMPLO.md)"
fi

if [ "$GRUPOS" -gt 0 ]; then
    test_ok "Grupos cargados ($GRUPOS registros)"
else
    test_warn "Grupos no cargados"
fi

if [ "$MATERIAS" -gt 0 ]; then
    test_ok "Materias cargadas ($MATERIAS registros)"
else
    test_warn "Materias no cargadas"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "5️⃣  MEMORIA Y RECURSOS"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

echo "Estado actual del sistema:"
free -h | grep Mem | awk '{print "  Memoria: " $2 " total, " $3 " usado, " $4 " libre"}'
df -h /opt/ades | tail -1 | awk '{print "  Disco /opt/ades: " $4 " libre de " $2}'

echo ""
echo "Uso de Docker:"
sudo docker stats --no-stream | tail -10 || echo "  (docker stats no disponible)"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 RESULTADOS"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

TOTAL=$((PASS + FAIL))
PERCENT=$((PASS * 100 / TOTAL))

echo ""
echo "Pruebas pasadas: $PASS/$TOTAL ($PERCENT%)"

if [ $FAIL -eq 0 ]; then
    echo -e "${GREEN}✓ BOOTSTRAP EXITOSO${NC}"
    exit 0
else
    echo -e "${RED}✗ $FAIL pruebas fallidas${NC}"
    echo ""
    echo "Próximos pasos:"
    echo "  1. Revisar logs: docker-compose logs -f [servicio]"
    echo "  2. Esperar más tiempo si es el primer boot"
    echo "  3. Ejecutar: bash init-certbot.sh"
    echo "  4. Cargar datos: ver DATOS_EJEMPLO.md"
    exit 1
fi

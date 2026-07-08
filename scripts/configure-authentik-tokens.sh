#!/bin/bash

###############################################################################
# Script: Configurar Authentik para FASE 3 (Sesión Prolongada)
# Propósito: Aumentar token lifetime y verificar configuración
#
# Uso:
#   bash scripts/configure-authentik-tokens.sh
#   bash scripts/configure-authentik-tokens.sh --verify
#   bash scripts/configure-authentik-tokens.sh --reset
###############################################################################

set -e

# Variables de entorno
AUTHENTIK_URL="${AUTHENTIK_URL:-https://ades.setag.mx/auth}"
AUTHENTIK_ADMIN_USER="${AUTHENTIK_ADMIN_USER:-akadmin}"
AUTHENTIK_ADMIN_PASS="${AUTHENTIK_ADMIN_PASS:-$(grep AUTHENTIK_BOOTSTRAP_PASSWORD .env 2>/dev/null || echo 'CHANGE_ME')}"
APP_SLUG="ades-frontend"

# Colores para output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo -e "${YELLOW}Configurando Authentik — FASE 3 (Sesión Prolongada)${NC}"
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}\n"

# Función: Obtener token admin de Authentik
get_admin_token() {
    echo -e "${YELLOW}[1/4] Obteniendo token de administrador...${NC}"

    TOKEN=$(curl -s -X POST \
        "${AUTHENTIK_URL}/token/auth/\
?grant_type=password&username=${AUTHENTIK_ADMIN_USER}&password=${AUTHENTIK_ADMIN_PASS}" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        | jq -r '.access_token' 2>/dev/null)

    if [ -z "$TOKEN" ] || [ "$TOKEN" == "null" ]; then
        echo -e "${RED}✗ Error: No se pudo obtener token admin${NC}"
        echo "  Verifica AUTHENTIK_URL, AUTHENTIK_ADMIN_USER, AUTHENTIK_ADMIN_PASS"
        exit 1
    fi

    echo -e "${GREEN}✓ Token obtenido${NC}\n"
}

# Función: Obtener ID de aplicación
get_app_id() {
    echo -e "${YELLOW}[2/4] Buscando aplicación '${APP_SLUG}'...${NC}"

    APP_ID=$(curl -s \
        -H "Authorization: Bearer ${TOKEN}" \
        "${AUTHENTIK_URL}/api/v3/core/applications/?slug=${APP_SLUG}" \
        | jq -r '.results[0].pk' 2>/dev/null)

    if [ -z "$APP_ID" ] || [ "$APP_ID" == "null" ]; then
        echo -e "${RED}✗ Error: Aplicación '${APP_SLUG}' no encontrada${NC}"
        exit 1
    fi

    echo -e "${GREEN}✓ Aplicación ID: ${APP_ID}${NC}\n"
}

# Función: Obtener Provider ID
get_provider_id() {
    echo -e "${YELLOW}[3/4] Buscando provider OAuth2 asociado...${NC}"

    PROVIDER_ID=$(curl -s \
        -H "Authorization: Bearer ${TOKEN}" \
        "${AUTHENTIK_URL}/api/v3/oauth2/applications/?application__slug=${APP_SLUG}" \
        | jq -r '.results[0].pk' 2>/dev/null)

    if [ -z "$PROVIDER_ID" ] || [ "$PROVIDER_ID" == "null" ]; then
        echo -e "${RED}✗ Error: Provider no encontrado${NC}"
        exit 1
    fi

    echo -e "${GREEN}✓ Provider ID: ${PROVIDER_ID}${NC}\n"
}

# Función: Actualizar tiempos de token
update_token_times() {
    echo -e "${YELLOW}[4/4] Actualizando token lifetimes...${NC}"
    echo "  • access_token: 30 minutos (1800 segundos)"
    echo "  • refresh_token: 90 minutos (5400 segundos)"
    echo ""

    RESPONSE=$(curl -s -X PATCH \
        -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" \
        "${AUTHENTIK_URL}/api/v3/oauth2/applications/${PROVIDER_ID}/" \
        -d '{
            "access_token_validity": "minutes=30",
            "refresh_token_validity": "minutes=90",
            "token_endpoint_auth_method": "client_secret_basic"
        }')

    ERROR=$(echo "$RESPONSE" | jq -r '.detail' 2>/dev/null)
    if [ "$ERROR" != "null" ] && [ ! -z "$ERROR" ]; then
        echo -e "${RED}✗ Error al actualizar: ${ERROR}${NC}"
        exit 1
    fi

    echo -e "${GREEN}✓ Token lifetimes actualizados${NC}\n"
}

# Función: Verificar configuración
verify_config() {
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${YELLOW}Verificando configuración...${NC}"
    echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}\n"

    CONFIG=$(curl -s \
        -H "Authorization: Bearer ${TOKEN}" \
        "${AUTHENTIK_URL}/api/v3/oauth2/applications/${PROVIDER_ID}/")

    ACCESS_TOKEN_VALIDITY=$(echo "$CONFIG" | jq -r '.access_token_validity' 2>/dev/null)
    REFRESH_TOKEN_VALIDITY=$(echo "$CONFIG" | jq -r '.refresh_token_validity' 2>/dev/null)

    echo "Configuración actual:"
    echo "  • access_token_validity: ${ACCESS_TOKEN_VALIDITY}"
    echo "  • refresh_token_validity: ${REFRESH_TOKEN_VALIDITY}"
    echo ""

    if [[ "$ACCESS_TOKEN_VALIDITY" == *"30"* ]]; then
        echo -e "${GREEN}✓ Access token correctamente configurado a 30 minutos${NC}"
    else
        echo -e "${RED}✗ Access token NO es 30 minutos (actual: ${ACCESS_TOKEN_VALIDITY})${NC}"
    fi

    echo ""
}

# Función: Reset a valores por defecto
reset_defaults() {
    echo -e "${YELLOW}Reseteando a valores por defecto...${NC}"

    curl -s -X PATCH \
        -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" \
        "${AUTHENTIK_URL}/api/v3/oauth2/applications/${PROVIDER_ID}/" \
        -d '{
            "access_token_validity": "minutes=5",
            "refresh_token_validity": "minutes=30"
        }' > /dev/null

    echo -e "${GREEN}✓ Reset completado${NC}\n"
}

# Main logic
case "${1:-}" in
    --verify)
        get_admin_token
        get_app_id
        get_provider_id
        verify_config
        ;;
    --reset)
        get_admin_token
        get_app_id
        get_provider_id
        reset_defaults
        verify_config
        ;;
    *)
        get_admin_token
        get_app_id
        get_provider_id
        update_token_times
        verify_config
        echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
        echo -e "${GREEN}✓ Configuración completada exitosamente${NC}"
        echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}\n"

        echo "Próximos pasos:"
        echo "  1. Reiniciar Authentik:"
        echo "     docker compose restart authentik authentik-worker"
        echo ""
        echo "  2. Verificar sesión (esperar 29+ min sin logout):"
        echo "     Abrir https://ades.setag.mx"
        echo "     Login → Esperar 29 min → Hacer clic en botón"
        echo "     → NO debe pedir re-login"
        echo ""
        echo "  3. Monitorear logs:"
        echo "     docker compose logs -f ades-api | grep refreshToken"
        echo ""
        ;;
esac

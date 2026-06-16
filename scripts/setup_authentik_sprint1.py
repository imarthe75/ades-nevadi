#!/usr/bin/env python3
"""
SPRINT 1 — Automatizar setup Authentik:
1. Cambiar contraseña akadmin (seguridad)
2. Crear OAuth2 Provider para Superset (OIDC)
3. Crear OAuth2 Application para Superset (client)
"""

import os
import sys
import requests
import json
from datetime import datetime
from urllib.parse import urljoin

# ============================================================================
# CONFIG
# ============================================================================

AUTHENTIK_URL = "https://auth.ades.setag.mx"
AUTHENTIK_API_BASE = "https://auth.ades.setag.mx/api/v3/"
AUTHENTIK_BOOTSTRAP_TOKEN = os.environ.get("AUTHENTIK_BOOTSTRAP_TOKEN")

# Nueva contraseña para akadmin (generar al ejecutar)
NEW_AKADMIN_PASSWORD = "akadmin_prod_2026_" + datetime.now().strftime("%Y%m%d_%H%M%S")

# Configuración Superset OIDC
SUPERSET_OIDC_CONFIG = {
    "name": "superset",
    "slug": "superset",
    "provider_type": "oauth2",
    "authorization_flow": "default-provider-authorization-implicit-consent",
    "redirect_uris": "https://bi.ades.setag.mx/auth/authorize",
}

# ============================================================================
# FUNCIONES AUXILIARES
# ============================================================================

def log(message, level="INFO"):
    """Log con timestamp"""
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{ts}] [{level}] {message}")

def api_request(method, endpoint, data=None, token=None, headers=None):
    """Helper para requests a Authentik API"""
    url = AUTHENTIK_API_BASE + endpoint
    
    h = {"Authorization": f"Bearer {token or AUTHENTIK_BOOTSTRAP_TOKEN}"}
    if headers:
        h.update(headers)
    
    try:
        if method == "GET":
            resp = requests.get(url, headers=h, verify=False)
        elif method == "POST":
            h["Content-Type"] = "application/json"
            resp = requests.post(url, json=data, headers=h, verify=False)
        elif method == "PATCH":
            h["Content-Type"] = "application/json"
            resp = requests.patch(url, json=data, headers=h, verify=False)
        else:
            raise ValueError(f"Método no soportado: {method}")
        
        resp.raise_for_status()
        return resp.json() if resp.text else {}
    
    except requests.exceptions.RequestException as e:
        log(f"Error en API request: {e}", "ERROR")
        raise

# ============================================================================
# TASK 1: Cambiar contraseña akadmin
# ============================================================================

def change_akadmin_password():
    """Cambiar contraseña de los usuarios admin y akadmin"""
    log("TASK 1: Cambiar contraseñas de admin + akadmin...", "INFO")
    
    try:
        # Obtener usuarios
        log(f"  → Obteniendo usuarios admin/akadmin...", "INFO")
        users = api_request("GET", "core/users/")
        
        if not users.get("results"):
            log("  ✗ No hay usuarios encontrados", "ERROR")
            return False
        
        # Buscar usuarios admin y akadmin
        admin_user = next((u for u in users["results"] if u["username"] == "admin"), None)
        akadmin_user = next((u for u in users["results"] if u["username"] == "akadmin"), None)
        
        if not admin_user:
            log("  ✗ Usuario admin no encontrado", "ERROR")
            return False
        if not akadmin_user:
            log("  ✗ Usuario akadmin no encontrado", "ERROR")
            return False
        
        log(f"  ✓ Usuario admin encontrado (ID: {admin_user['pk']})", "INFO")
        log(f"  ✓ Usuario akadmin encontrado (ID: {akadmin_user['pk']})", "INFO")
        
        # Cambiar contraseña de admin
        log(f"  → Actualizando contraseña admin...", "INFO")
        api_request("PATCH", f"core/users/{admin_user['pk']}/", {
            "password": NEW_AKADMIN_PASSWORD
        })
        log(f"  ✓ Contraseña admin actualizada", "INFO")
        
        # Cambiar contraseña de akadmin
        log(f"  → Actualizando contraseña akadmin...", "INFO")
        api_request("PATCH", f"core/users/{akadmin_user['pk']}/", {
            "password": NEW_AKADMIN_PASSWORD
        })
        log(f"  ✓ Contraseña akadmin actualizada", "INFO")
        log(f"  📝 Nueva contraseña (ambos): {NEW_AKADMIN_PASSWORD}", "INFO")
        
        # Guardar contraseña en .env
        env_file = "/opt/ades/.env"
        if os.path.exists(env_file):
            with open(env_file, "a") as f:
                f.write(f"\n# SPRINT 1 — 2026-06-16 (admin + akadmin)\n")
                f.write(f"AUTHENTIK_ADMIN_PASSWORD={NEW_AKADMIN_PASSWORD}\n")
                f.write(f"AUTHENTIK_AKADMIN_PASSWORD={NEW_AKADMIN_PASSWORD}\n")
            log(f"  ✓ Contraseñas guardadas en .env", "INFO")
        
        return True
    
    except Exception as e:
        log(f"  ✗ Error: {e}", "ERROR")
        return False

# ============================================================================
# TASK 2: Crear OAuth2 Provider para Superset
# ============================================================================

def create_oauth2_provider():
    """Crear OAuth2 Provider para Superset en Authentik"""
    log("TASK 2: Crear OAuth2 Provider para Superset...", "INFO")
    
    try:
        # Verificar si ya existe
        log(f"  → Verificando si ya existe provider 'superset'...", "INFO")
        providers = api_request("GET", "providers/oauth2/")
        
        # Buscar si ya existe un provider para superset
        for provider in providers.get("results", []):
            if provider.get("name") == "superset" or "superset" in provider.get("assigned_application_slug", "").lower():
                log(f"  ✓ Provider 'superset' ya existe (PK: {provider['pk']})", "INFO")
                return provider["pk"]
        
        # Crear provider
        log(f"  → Creando OAuth2 Provider...", "INFO")
        provider_data = {
            "name": "superset",
            "client_type": "public",
            "authorization_flow": "default-provider-authorization-implicit-consent",
            "redirect_uris": "https://bi.ades.setag.mx/auth/authorize",
        }
        
        result = api_request("POST", "providers/oauth2/", provider_data)
        provider_pk = result["pk"]
        
        log(f"  ✓ OAuth2 Provider creado (PK: {provider_pk})", "INFO")
        return provider_pk
    
    except Exception as e:
        log(f"  ✗ Error: {e}", "ERROR")
        return None

# ============================================================================
# TASK 3: Crear OAuth2 Application para Superset
# ============================================================================

def create_oauth2_application(provider_id):
    """Crear OAuth2 Application (client) para Superset"""
    log("TASK 3: Crear OAuth2 Application para Superset...", "INFO")
    
    try:
        # Verificar si ya existe
        log(f"  → Verificando si ya existe application 'superset'...", "INFO")
        apps = api_request("GET", "core/applications/")
        
        for app in apps.get("results", []):
            if app.get("name") == "superset" or app.get("slug") == "superset":
                log(f"  ✓ Application 'superset' ya existe", "INFO")
                log(f"     Client ID (slug): {app['slug']}", "INFO")
                return app["slug"], app["provider"]
        
        # Crear application
        log(f"  → Creando OAuth2 Application...", "INFO")
        app_data = {
            "name": "superset",
            "slug": "superset",
            "provider": provider_id,
            "meta_launch_url": "https://bi.ades.setag.mx",
        }
        
        result = api_request("POST", "core/applications/", app_data)
        
        log(f"  ✓ OAuth2 Application creado (slug: {result['slug']})", "INFO")
        return result["slug"], result["provider"]
    
    except Exception as e:
        log(f"  ✗ Error: {e}", "ERROR")
        return None, None

# ============================================================================
# TASK 4: Obtener Client Secret y generar config env
# ============================================================================

def get_oauth2_credentials(provider_id):
    """Obtener Client ID y Secret desde el provider"""
    log("TASK 4: Obtener credenciales OAuth2...", "INFO")
    
    try:
        log(f"  → Obteniendo credenciales del provider {provider_id}...", "INFO")
        provider = api_request("GET", f"providers/oauth2/{provider_id}/")
        
        client_id = provider.get("client_id")
        client_secret = provider.get("client_secret")
        
        if not client_id:
            log(f"  ✗ No se obtuvieron credenciales", "ERROR")
            return None, None
        
        log(f"  ✓ Credenciales obtenidas", "INFO")
        log(f"     Client ID: {client_id}", "INFO")
        log(f"     Client Secret: {client_secret[:20]}...", "INFO")
        
        return client_id, client_secret
    
    except Exception as e:
        log(f"  ✗ Error: {e}", "ERROR")
        return None, None

# ============================================================================
# TASK 5: Generar configuración para Superset
# ============================================================================

def generate_superset_env_config(client_id, client_secret):
    """Generar snippet de configuración para .env de Superset"""
    log("TASK 5: Generar configuración para Superset...", "INFO")
    
    config = f"""
# ============================================================================
# SUPERSET OIDC SSO — Configuración generada {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
# ============================================================================

# OAuth2 Provider
SUPERSET_OIDC_CLIENT_ID={client_id}
SUPERSET_OIDC_CLIENT_SECRET={client_secret}
SUPERSET_OIDC_AUTH_URL=https://auth.ades.setag.mx/application/o/authorize/
SUPERSET_OIDC_TOKEN_URL=https://auth.ades.setag.mx/application/o/token/
SUPERSET_OIDC_USERINFO_URL=https://auth.ades.setag.mx/application/o/userinfo/
SUPERSET_OIDC_REDIRECT_URI=https://bi.ades.setag.mx/auth/authorize

# OIDC Scopes
SUPERSET_OIDC_SCOPES=openid email profile

# Usuario default (buscar en Authentik después de SSO)
SUPERSET_ADMIN_USER=admin
SUPERSET_ADMIN_PASSWORD=superset_dev_2026

# Feature flags
SUPERSET_FEATURE_DISABLE_ALERT_MANAGEMENT=true
SUPERSET_QUERY_LIMIT=100000
"""
    
    log(f"  ✓ Configuración generada para Superset", "INFO")
    return config

# ============================================================================
# MAIN
# ============================================================================

def main():
    """Ejecutar SPRINT 1"""
    log("=" * 80, "INFO")
    log("SPRINT 1 — Configuración Authentik para Superset", "INFO")
    log("=" * 80, "INFO")
    
    # Verificar token
    if not AUTHENTIK_BOOTSTRAP_TOKEN:
        log("✗ AUTHENTIK_BOOTSTRAP_TOKEN no configurado en .env", "ERROR")
        sys.exit(1)
    
    log(f"Authentik URL: {AUTHENTIK_URL}", "INFO")
    log(f"API Base: {AUTHENTIK_API_BASE}", "INFO")
    log("", "INFO")
    
    # TASK 1: Cambiar contraseña akadmin
    if not change_akadmin_password():
        log("✗ SPRINT 1 falló en TASK 1", "ERROR")
        sys.exit(1)
    
    log("", "INFO")
    
    # TASK 2: Crear OAuth2 Provider
    provider_id = create_oauth2_provider()
    if not provider_id:
        log("✗ SPRINT 1 falló en TASK 2", "ERROR")
        sys.exit(1)
    
    log("", "INFO")
    
    # TASK 3: Crear OAuth2 Application
    client_id, _ = create_oauth2_application(provider_id)
    if not client_id:
        log("✗ SPRINT 1 falló en TASK 3", "ERROR")
        sys.exit(1)
    
    log("", "INFO")
    
    # TASK 4: Obtener credenciales
    client_id, client_secret = get_oauth2_credentials(provider_id)
    if not client_id:
        log("✗ SPRINT 1 falló en TASK 4", "ERROR")
        sys.exit(1)
    
    log("", "INFO")
    
    # TASK 5: Generar configuración
    superset_config = generate_superset_env_config(client_id, client_secret)
    
    log("", "INFO")
    log("=" * 80, "SUCCESS")
    log("SPRINT 1 COMPLETADO ✅", "INFO")
    log("=" * 80, "INFO")
    
    log("", "INFO")
    log("📝 Configuración para Superset:", "INFO")
    log(superset_config, "INFO")
    
    log("", "INFO")
    log("📋 Próximos pasos:", "INFO")
    log("1. Copiar configuración arriba a docker-compose.yml (servicio superset)", "INFO")
    log("2. Reiniciar Superset: docker compose restart superset", "INFO")
    log("3. Acceder a https://bi.ades.setag.mx y probar SSO login", "INFO")
    
    return 0

if __name__ == "__main__":
    sys.exit(main())

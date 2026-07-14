#!/usr/bin/env python3
"""
Authentik initial setup — ADES Instituto Nevadi
Uso: AUTHENTIK_BOOTSTRAP_TOKEN=<token> python3 setup.py

Configura:
  - Brand (título + dominio)
  - OIDC provider + application: ades-frontend
  - OIDC provider + application: superset
  - Google Workspace SSO source (opcional, requiere GOOGLE_OAUTH_CLIENT_ID)

Idempotente: puede ejecutarse múltiples veces sin duplicar objetos.
"""
import json, os, re, secrets, sys
from urllib.request import Request, urlopen
from urllib.error import HTTPError

# Por defecto localhost:9010 — no depende de DNS/TLS externos ya emitidos,
# por lo que puede correr como parte del bootstrap ANTES de tener
# certificados reales. Sobreescribible con AUTHENTIK_API_BASE.
BASE  = os.environ.get("AUTHENTIK_API_BASE", "http://localhost:9010")
TOKEN = os.environ.get("AUTHENTIK_BOOTSTRAP_TOKEN", "")
if not TOKEN:
    sys.exit("ERROR: variable AUTHENTIK_BOOTSTRAP_TOKEN requerida")

HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "Content-Type":  "application/json",
    "Accept":        "application/json",
}

# ── helpers ──────────────────────────────────────────────────────────────────

def api(method, path, data=None):
    url  = f"{BASE}/api/v3{path}"
    body = json.dumps(data).encode() if data is not None else None
    req  = Request(url, data=body, headers=HEADERS, method=method)
    try:
        with urlopen(req) as r:
            return json.loads(r.read())
    except HTTPError as e:
        msg = e.read().decode()
        print(f"  [!] HTTP {e.code} {method} {path}")
        print(f"      {msg[:300]}")
        raise

def first(path, **params):
    qs = "&".join(f"{k}={v}" for k, v in params.items())
    r  = api("GET", f"{path}?{qs}")
    return r["results"][0] if r.get("results") else None

def find_flow(designation, slug_hint="default"):
    r = api("GET", f"/flows/instances/?designation={designation}")
    for f in r.get("results", []):
        if slug_hint in f["slug"]:
            return f["pk"]
    results = r.get("results", [])
    return results[0]["pk"] if results else None

def scope_pks():
    r = api("GET", "/propertymappings/provider/scope/?ordering=name")
    managed_prefix = "goauthentik.io/providers/oauth2/scope-"
    return [s["pk"] for s in r.get("results", [])
            if s.get("managed", "").startswith(managed_prefix)]

def upsert_provider(name, payload):
    existing = first("/providers/oauth2/", name=name)
    if existing:
        print(f"  provider '{name}' ya existe (pk={existing['pk']}), actualizando...")
        payload["client_secret"] = existing.get("client_secret", payload["client_secret"])
        api("PATCH", f"/providers/oauth2/{existing['pk']}/", payload)
        return existing["pk"], existing.get("client_secret", "")
    r = api("POST", "/providers/oauth2/", payload)
    print(f"  provider '{name}' creado  (pk={r['pk']})")
    return r["pk"], payload["client_secret"]

def upsert_application(name, slug, provider_pk, launch_url, description=""):
    existing = first("/core/applications/", slug=slug)
    if existing:
        print(f"  app '{slug}' ya existe")
        return
    api("POST", "/core/applications/", {
        "name":              name,
        "slug":              slug,
        "provider":          provider_pk,
        "meta_launch_url":   launch_url,
        "meta_description":  description,
        "policy_engine_mode": "any",
        "open_in_new_tab":   False,
    })
    print(f"  app '{slug}' creada")

# ── 1. Brand ─────────────────────────────────────────────────────────────────

# Fondo del login: el flow lo sirve desde el media de Authentik
# (/files/media/public/<archivo>). El archivo se monta desde git en
# docker-compose (assets/authentik/flow-background.svg ->
# /media/public/ades-flow-background.svg). Aquí solo se referencia por nombre.
FLOW_BACKGROUND = "ades-flow-background.svg"

# Títulos de flows en español (texto guardado; NO se traduce con el locale).
FLOW_TITLES = {
    "default-authentication-flow":                     "Bienvenido a ADES",
    "default-invalidation-flow":                       "Cerrar sesión",
    "default-password-change":                         "Cambiar contraseña",
    "default-user-settings-flow":                      "Actualiza tu información",
    "default-authenticator-static-setup":              "Configurar tokens OTP estáticos",
    "default-authenticator-totp-setup":                "Configurar autenticación de dos factores",
    "default-authenticator-webauthn-setup":            "Configurar WebAuthn",
    "default-provider-authorization-explicit-consent": "Redirigiendo a %(app)s",
    "default-provider-authorization-implicit-consent": "Redirigiendo a %(app)s",
    "default-provider-invalidation-flow":              "Has cerrado sesión de %(app)s.",
    "default-source-authentication":                   "Bienvenido a ADES",
    "default-source-enrollment":                       "Bienvenido a ADES. Elige un nombre de usuario.",
    "default-source-pre-authentication":               "Preautenticación",
}

print("\n[1] Brand")
brands = api("GET", "/core/brands/")
if brands["results"]:
    brand = brands["results"][0]
    uid   = brand.get("brand_uuid") or brand.get("pk")
    api("PATCH", f"/core/brands/{uid}/", {
        "domain":          "auth.ades.setag.mx",
        "branding_title":  "ADES — Instituto Nevadi",
        # Logo y favicon ADES/Nevadi. Se referencian por URL absoluta al asset
        # que el frontend ya sirve en la raíz de ades.setag.mx
        # (frontend/public/nevadi-logo.jpg, frontend/public/favicon.png —
        # ambos versionados en git). Así el branding del login de Authentik es
        # reproducible: no depende de subir archivos por la UI ni del volumen
        # authentik-media (que se pierde al reinicializar).
        "branding_logo":     "https://ades.setag.mx/nevadi-logo.jpg",
        "branding_favicon":  "https://ades.setag.mx/favicon.png",
        # Locale español para toda la UI de Authentik (labels, botones y el
        # subtítulo "Inicia sesión para continuar a ADES"). Authentik lo
        # deriva de attributes.settings.locale, no de un campo propio.
        "attributes":      {"settings": {"locale": "es"}},
        "default":         True,
    })
    print(f"  actualizado ({uid})")

    # Títulos de flows en español + fondo navy del flow de login. Los títulos
    # son texto guardado (no se traducen con el locale). El fondo se referencia
    # por nombre; el archivo lo provee el bind-mount de docker-compose.
    for slug, title in FLOW_TITLES.items():
        payload = {"title": title}
        if slug == "default-authentication-flow":
            payload["background"] = FLOW_BACKGROUND
        try:
            api("PATCH", f"/flows/instances/{slug}/", payload)
            print(f"  flow '{slug}' → '{title}'")
        except Exception as e:
            print(f"  flow '{slug}' omitido: {e}")

# ── 2. Flujos ────────────────────────────────────────────────────────────────

print("\n[2] Flujos")
authz_flow   = find_flow("authorization", "default")
invalid_flow = find_flow("invalidation",  "default")
print(f"  authorization : {authz_flow}")
print(f"  invalidation  : {invalid_flow}")

if not authz_flow:
    sys.exit("ERROR: no se encontró flujo de autorización")

# ── 3. Scopes ────────────────────────────────────────────────────────────────

print("\n[3] Scope mappings")
scopes = scope_pks()
print(f"  {len(scopes)} scopes encontrados")

# ── 4. Provider + App: ades-frontend ─────────────────────────────────────────

print("\n[4] ades-frontend")
ades_secret = secrets.token_urlsafe(40)
base_provider = {
    "authorization_flow":       authz_flow,
    "client_secret":            ades_secret,
    "access_code_validity":     "minutes=1",
    "access_token_validity":    "minutes=5",
    "refresh_token_validity":   "days=30",
    "property_mappings":        scopes,
    "sub_mode":                 "hashed_user_id",
    "include_claims_in_id_token": True,
    "issuer_mode":              "per_provider",
    # Sin esto Authentik rechaza CUALQUIER request de autorización con
    # "invalid_request: The request is otherwise malformed" (grant_types
    # vacío por defecto — bug real encontrado 2026-07-10).
    "grant_types":              ["authorization_code", "refresh_token"],
}
if invalid_flow:
    base_provider["invalidation_flow"] = invalid_flow

ades_pk, ades_secret = upsert_provider("ades-frontend", {
    **base_provider,
    "name":          "ades-frontend",
    "client_id":     "ades-frontend",
    "redirect_uris": [
        {"url": "https://ades.setag.mx/",                  "matching_mode": "strict"},
        {"url": "https://ades.setag.mx/callback",          "matching_mode": "strict"},
        {"url": "https://ades.setag.mx/silent-renew.html", "matching_mode": "strict"},
        {"url": "http://localhost:4200/",                  "matching_mode": "strict"},
        {"url": "http://localhost:4200/callback",          "matching_mode": "strict"},
    ],
})
upsert_application(
    # Nombre corto "ADES": aparece en el subtítulo del login
    # ("Inicia sesión para continuar a ADES") y en la biblioteca de apps.
    "ADES", "ades-frontend", ades_pk,
    "https://ades.setag.mx/",
    "Sistema Escolar Instituto Nevadi",
)

# ── 5. Provider + App: superset ──────────────────────────────────────────────

print("\n[5] superset")
superset_secret = secrets.token_urlsafe(40)
superset_pk, superset_secret = upsert_provider("superset", {
    **base_provider,
    "name":          "superset",
    "client_id":     "superset",
    "redirect_uris": [
        {"url": "https://bi.ades.setag.mx/oauth-authorized/authentik",
         "matching_mode": "strict"},
        {"url": "https://bi.ades.setag.mx/oauth-authorized/oidc",
         "matching_mode": "strict"},
    ],
})
upsert_application(
    "Superset BI", "superset", superset_pk,
    "https://bi.ades.setag.mx/",
    "Dashboards BI — Instituto Nevadi",
)

# ── 6. Google Workspace SSO (opcional) ───────────────────────────────────────

print("\n[6] Google Workspace SSO")
g_id  = os.environ.get("GOOGLE_OAUTH_CLIENT_ID",     "")
g_sec = os.environ.get("GOOGLE_OAUTH_CLIENT_SECRET", "")

if g_id and g_sec:
    auth_flow   = find_flow("authentication", "default")
    enroll_flow = find_flow("enrollment",     "default")
    existing = first("/sources/oauth/", slug="google-workspace")
    if existing:
        print("  source 'google-workspace' ya existe")
    else:
        api("POST", "/sources/oauth/", {
            "name":                "Google Workspace",
            "slug":                "google-workspace",
            "enabled":             True,
            "provider_type":       "google",
            "consumer_key":        g_id,
            "consumer_secret":     g_sec,
            "authentication_flow": auth_flow,
            "enrollment_flow":     enroll_flow,
            "policy_engine_mode":  "any",
            "user_matching_mode":  "identifier",
            "additional_scopes":   "email profile",
        })
        print("  source 'google-workspace' creado")
    print(f"  Redirect URI para Google Cloud Console:")
    print(f"    https://auth.ades.setag.mx/source/oauth/callback/google-workspace/")
else:
    print("  Omitido — añade al .env cuando tengas las credenciales de Google Cloud:")
    print("    GOOGLE_OAUTH_CLIENT_ID=<id>.apps.googleusercontent.com")
    print("    GOOGLE_OAUTH_CLIENT_SECRET=<secret>")
    print("  Luego: python3 infrastructure/authentik/setup.py")

# ── 7. Escribir secretos en .env ────────────────────────────────────────────

def write_env_var(path, key, value):
    with open(path) as f:
        content = f.read()
    pattern = re.compile(rf"^{re.escape(key)}=.*$", re.MULTILINE)
    line = f"{key}={value}"
    if pattern.search(content):
        content = pattern.sub(line, content, count=1)
    else:
        content = content.rstrip("\n") + f"\n{line}\n"
    with open(path, "w") as f:
        f.write(content)

env_path = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), ".env")
if os.path.isfile(env_path):
    write_env_var(env_path, "OIDC_CLIENT_SECRET", ades_secret)
    write_env_var(env_path, "SUPERSET_OIDC_CLIENT_SECRET", superset_secret)
    print(f"\n[.env] OIDC_CLIENT_SECRET y SUPERSET_OIDC_CLIENT_SECRET actualizados en {env_path}")
else:
    print(f"\n[!] No se encontró {env_path} — actualiza manualmente:")
    print(f"    OIDC_CLIENT_SECRET={ades_secret}")
    print(f"    SUPERSET_OIDC_CLIENT_SECRET={superset_secret}")

# ── 8. Resumen ────────────────────────────────────────────────────────────────

print("\n" + "=" * 62)
print("SETUP COMPLETADO")
print("=" * 62)
print("\nURLs de configuración OIDC:")
print(f"  ades-frontend issuer : {BASE}/application/o/ades-frontend/")
print(f"  superset issuer      : {BASE}/application/o/superset/")
print(f"\nPanel admin Authentik : {BASE}/if/admin/")
print(f"Usuario               : akadmin")
print(f"Contraseña            : (ver AUTHENTIK_BOOTSTRAP_PASSWORD en .env)")

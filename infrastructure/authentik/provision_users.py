#!/usr/bin/env python3
"""
Provisión de usuario admin + usuarios de prueba por rol — ADES Instituto Nevadi
Uso: AUTHENTIK_BOOTSTRAP_TOKEN=<token> python3 provision_users.py

Crea, para cada rol en ades_roles (tabla real, no hardcodeada):
  - Un usuario en Authentik (username, email, nombre) con contraseña común.
  - La fila correspondiente en ades_personas + ades_usuarios (email_institucional
    y oidc_sub = email, que es como AdesUserService.resolveUser() hace match
    vía findByOidcSubOrEmailOrUsername).

El primer usuario es siempre "admin" (email admin@institutonevadi.edu.mx) con
rol ADMIN_GLOBAL — acceso global al sistema. El resto son "test_<rol>" para
poder probar cada nivel de acceso.

Idempotente: puede ejecutarse múltiples veces sin duplicar usuarios ni personas.
Requiere que las migraciones y el seed 001_datos_base.sql (catálogo de roles)
ya se hayan aplicado.
"""
import json, os, subprocess, sys
from urllib.request import Request, urlopen
from urllib.error import HTTPError

BASE  = os.environ.get("AUTHENTIK_API_BASE", "http://localhost:9010")
TOKEN = os.environ.get("AUTHENTIK_BOOTSTRAP_TOKEN", "")
PASSWORD = os.environ.get("ADES_TEST_USER_PASSWORD", "Ad3s_N3v4d1!")
DOMAIN = "institutonevadi.edu.mx"

if not TOKEN:
    sys.exit("ERROR: variable AUTHENTIK_BOOTSTRAP_TOKEN requerida")

HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "Content-Type":  "application/json",
    "Accept":        "application/json",
}

def api(method, path, data=None):
    url  = f"{BASE}/api/v3{path}"
    body = json.dumps(data).encode() if data is not None else None
    req  = Request(url, data=body, headers=HEADERS, method=method)
    try:
        with urlopen(req) as r:
            return json.loads(r.read()) if r.length != 0 else {}
    except HTTPError as e:
        msg = e.read().decode()
        print(f"  [!] HTTP {e.code} {method} {path}\n      {msg[:300]}")
        raise

def psql(sql):
    """Ejecuta SQL vía el socket de confianza (sin contraseñas) y regresa filas tab-separadas."""
    r = subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "ades_admin", "-d", "ades",
         "-tAF", "\t", "-v", "ON_ERROR_STOP=1", "-c", sql],
        capture_output=True, text=True, cwd="/opt/ades")
    if r.returncode != 0:
        # docker sin sudo puede fallar por permisos; reintentar con sudo
        r = subprocess.run(
            ["sudo", "docker", "compose", "exec", "-T", "postgres", "psql", "-U", "ades_admin", "-d", "ades",
             "-tAF", "\t", "-v", "ON_ERROR_STOP=1", "-c", sql],
            capture_output=True, text=True, cwd="/opt/ades")
    if r.returncode != 0:
        sys.exit(f"SQL ERROR:\n{sql}\n{r.stderr}")
    return [tuple(c for c in line.split("\t")) for line in r.stdout.splitlines() if line]

def upsert_authentik_user(username, email, name):
    existing = api("GET", f"/core/users/?username={username}")
    results = existing.get("results", [])
    if results:
        uid = results[0]["pk"]
        print(f"  [authentik] usuario '{username}' ya existe (pk={uid})")
    else:
        created = api("POST", "/core/users/", {
            "username": username,
            "email":    email,
            "name":     name,
            "is_active": True,
            "path":     "ades/provisioned",
        })
        uid = created["pk"]
        print(f"  [authentik] usuario '{username}' creado (pk={uid})")
    api("POST", f"/core/users/{uid}/set_password/", {"password": PASSWORD})
    return uid

def esc(v):
    return str(v).replace("'", "''")

def upsert_persona_usuario(nombre, apellido, email, username, rol_id):
    nombre, apellido, email, username = esc(nombre), esc(apellido), esc(email), esc(username)
    existing = psql(f"SELECT id FROM ades_usuarios WHERE email_institucional = '{email}'")
    if existing:
        print(f"  [db] ades_usuarios '{email}' ya existe")
        return existing[0][0]

    persona_rows = psql(
        f"INSERT INTO ades_personas (nombre, apellido_paterno, genero) "
        f"VALUES ('{nombre}', '{apellido}', 'O') RETURNING id"
    )
    persona_id = persona_rows[0][0]

    usuario_rows = psql(
        f"INSERT INTO ades_usuarios (nombre_usuario, email_institucional, oidc_sub, persona_id, rol_id) "
        f"VALUES ('{username}', '{email}', '{email}', '{persona_id}', '{rol_id}') RETURNING id"
    )
    print(f"  [db] ades_usuarios '{email}' creado (rol={rol_id})")
    return usuario_rows[0][0]

def main():
    print("[1] Leyendo catálogo de roles (ades_roles)...")
    roles = psql("SELECT id, nombre_rol FROM ades_roles WHERE is_active ORDER BY nivel_acceso")
    if not roles:
        sys.exit("ERROR: ades_roles está vacío — aplica el seed 001_datos_base.sql primero")
    print(f"  {len(roles)} roles encontrados")

    print("\n[2] Admin global...")
    admin_role = next((r for r in roles if r[1] == "ADMIN_GLOBAL"), roles[0])
    admin_email = f"admin@{DOMAIN}"
    upsert_authentik_user("admin", admin_email, "Administrador Global")
    upsert_persona_usuario("Admin", "Global", admin_email, "admin", admin_role[0])

    print("\n[3] Usuarios de prueba por rol...")
    for rol_id, nombre_rol in roles:
        slug = nombre_rol.lower()
        username = f"test_{slug}"
        email = f"test.{slug}@{DOMAIN}"
        upsert_authentik_user(username, email, f"Prueba {nombre_rol.replace('_', ' ').title()}")
        upsert_persona_usuario("Prueba", nombre_rol.replace("_", " ").title(), email, username, rol_id)

    print("\n" + "=" * 62)
    print("PROVISIÓN COMPLETADA")
    print("=" * 62)
    print(f"\nContraseña de todos los usuarios: {PASSWORD}")
    print(f"Admin global : admin@{DOMAIN}")
    print(f"Prueba por rol: test.<rol_en_minusculas>@{DOMAIN}  (ej. test.docente@{DOMAIN})")

if __name__ == "__main__":
    main()

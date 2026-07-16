#!/usr/bin/env python3
"""
Configura la recuperación de contraseña self-service de Authentik — ADES Instituto Nevadi.
Uso: AUTHENTIK_BOOTSTRAP_TOKEN=<token> python3 config_authentik_recovery.py

Reescrito 2026-07-16 — la versión anterior de este archivo tenía un token de
bootstrap hardcodeado como fallback (ya rotado, no es el token activo, pero mala
práctica igual — los secretos van en .env, Regla Mandatoria del proyecto) y 4 PKs
de stages hardcodeados que correspondían a una instancia distinta de Authentik —
por eso recovery_flow nunca quedó realmente enlazado pese a que el script existía
en el repo. Esta versión resuelve todo por nombre (mismo patrón que setup.py) y
es completamente idempotente.

Configura:
  - PasswordStage.allow_show_password = True (ícono de "ver contraseña" — hallazgo
    de usuario 2026-07-16, ausente por defecto en Authentik).
  - Flow de recuperación real (antes: solo un texto estático "escribe a
    admin@setag.mx" en el stage de identificación, sin flujo funcional detrás).
  - Reutiliza los stages existentes default-password-change-prompt/-write para el
    paso de "definir nueva contraseña" — evita duplicar esa lógica.
  - Las plantillas de correo con marca Nevadi (infrastructure/authentik/templates/)
    se aplican automáticamente vía el volumen /templates ya montado en
    docker-compose.yml — este script no las toca.
"""
import json, os, sys
from urllib.request import Request, urlopen
from urllib.error import HTTPError

BASE  = os.environ.get("AUTHENTIK_API_BASE", "http://localhost:9010")
TOKEN = os.environ.get("AUTHENTIK_BOOTSTRAP_TOKEN", "")
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
        print(f"  [!] HTTP {e.code} {method} {path}")
        print(f"      {msg[:300]}")
        raise


def first(path, **params):
    qs = "&".join(f"{k}={v}" for k, v in params.items())
    r  = api("GET", f"{path}?{qs}")
    return r["results"][0] if r.get("results") else None


def main():
    # 1. Ícono de mostrar/ocultar contraseña (nativo de Authentik, apagado por defecto)
    pw = first("/stages/password/", name="default-authentication-password")
    if pw:
        api("PATCH", f"/stages/password/{pw['pk']}/", {"allow_show_password": True})
        print("✓ allow_show_password=True en default-authentication-password")
    else:
        print("  [!] No se encontró el stage default-authentication-password")

    # 2. Flow de recuperación (idempotente)
    flow = first("/flows/instances/", slug="ades-recovery-flow")
    if not flow:
        flow = api("POST", "/flows/instances/", {
            "name": "ades-recovery-flow",
            "slug": "ades-recovery-flow",
            "title": "Recuperar contraseña — ADES Instituto Nevadi",
            "designation": "recovery",
        })
        print(f"✓ Flow 'ades-recovery-flow' creado (pk={flow['pk']})")
    else:
        print(f"  Flow 'ades-recovery-flow' ya existía (pk={flow['pk']})")

    # 3. Stage de identificación (solo email/username, sin password)
    id_stage = first("/stages/identification/", name="ades-recovery-identification")
    if not id_stage:
        id_stage = api("POST", "/stages/identification/", {
            "name": "ades-recovery-identification",
            "user_fields": ["username", "email"],
        })
        print(f"✓ Identification stage creado (pk={id_stage['pk']})")
    else:
        print(f"  Identification stage ya existía (pk={id_stage['pk']})")

    # 4. Stage de email — plantilla con marca Nevadi (infrastructure/authentik/templates/)
    email_stage = first("/stages/email/", name="ades-recovery-email")
    email_payload = {
        "name": "ades-recovery-email",
        "use_global_settings": True,
        "subject": "Restablecer contraseña — ADES Instituto Nevadi",
        "template": "email/password_reset.html",
    }
    if not email_stage:
        email_stage = api("POST", "/stages/email/", email_payload)
        print(f"✓ Email stage creado (pk={email_stage['pk']})")
    else:
        api("PATCH", f"/stages/email/{email_stage['pk']}/", email_payload)
        print(f"  Email stage actualizado (pk={email_stage['pk']})")

    # 5. Reutiliza los stages existentes de "cambiar contraseña" — resueltos por
    # nombre, no por PK hardcodeado (bug de la versión anterior de este script).
    prompt_stage = first("/stages/prompt/", name="default-password-change-prompt")
    write_stage  = first("/stages/user_write/", name="default-password-change-write")
    if not prompt_stage or not write_stage:
        sys.exit("ERROR: no se encontraron default-password-change-prompt/-write — "
                  "¿se corrió el blueprint base de Authentik?")

    # 6. Bindings — reemplaza limpio en cada corrida (idempotente)
    existing_bindings = api("GET", f"/flows/bindings/?target={flow['pk']}").get("results", [])
    for b in existing_bindings:
        api("DELETE", f"/flows/bindings/{b['pk']}/")
    for order, stage_pk in enumerate(
        [id_stage["pk"], email_stage["pk"], prompt_stage["pk"], write_stage["pk"]], start=1
    ):
        api("POST", "/flows/bindings/", {
            "target": flow["pk"], "stage": stage_pk, "order": order * 10,
            "evaluate_on_plan": False, "re_evaluate_policies": True,
        })
    print("✓ Bindings: identification(10) -> email(20) -> prompt(30) -> write(40)")

    # 7. Enlaza el flow de recuperación al stage de identificación del login principal
    main_id = first("/stages/identification/", name="default-authentication-identification")
    api("PATCH", f"/stages/identification/{main_id['pk']}/", {"recovery_flow": flow["pk"]})
    print("✓ recovery_flow enlazado en default-authentication-identification")

    # 8. Enlaza el flow de recuperación al Brand (para que el link "olvidé mi
    # contraseña" del login apunte aquí en vez del mensaje estático anterior)
    brand = first("/core/brands/", domain="auth.ades.setag.mx")
    if brand:
        api("PATCH", f"/core/brands/{brand['pk']}/", {"flow_recovery": flow["pk"]})
        print(f"✓ flow_recovery enlazado en brand {brand['domain']}")
    else:
        print("  [!] No se encontró el Brand auth.ades.setag.mx")

    print("\n=== Recuperación de contraseña configurada correctamente ===")


if __name__ == "__main__":
    main()

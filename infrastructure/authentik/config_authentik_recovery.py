import os, sys, json
from urllib.request import Request, urlopen
from urllib.error import HTTPError

token = "5a1b5c9fd10ded001a0da2e00b346ab9caddfda96b7fb826cdd9434276c64ea1"
if os.path.exists("/opt/ades/.env"):
    with open("/opt/ades/.env", "r") as f:
        for line in f:
            if "AUTHENTIK_BOOTSTRAP_TOKEN" in line:
                token = line.split("=")[1].strip().strip('"').strip("'")

BASE = "http://127.0.0.1:9010/api/v3"
HEADERS = {
    "Authorization": f"Bearer {token}",
    "Content-Type": "application/json",
    "Accept": "application/json",
}

def api(method, path, data=None):
    url = f"{BASE}{path}"
    body = json.dumps(data).encode() if data is not None else None
    req = Request(url, data=body, headers=HEADERS, method=method)
    try:
        with urlopen(req) as r:
            return json.loads(r.read())
    except HTTPError as e:
        msg = e.read().decode()
        print(f"HTTP {e.code} {method} {path}")
        print(msg)
        raise

def get_or_create_flow():
    # Check if recovery flow exists
    r = api("GET", "/flows/instances/?slug=default-password-recovery")
    if r.get("results"):
        flow = r["results"][0]
        print(f"Flow 'default-password-recovery' already exists (pk: {flow['pk']})")
        return flow["pk"]
    
    # Create recovery flow
    payload = {
        "name": "default-password-recovery",
        "slug": "default-password-recovery",
        "title": "Recuperar Contraseña — Instituto Nevadi",
        "designation": "recovery"
    }
    flow = api("POST", "/flows/instances/", payload)
    print(f"Flow 'default-password-recovery' created (pk: {flow['pk']})")
    return flow["pk"]

def get_or_create_id_stage():
    r = api("GET", "/stages/identification/?name=default-recovery-identification")
    if r.get("results"):
        stage = r["results"][0]
        print(f"Identification stage 'default-recovery-identification' already exists (pk: {stage['pk']})")
        return stage["pk"]
    
    payload = {
        "name": "default-recovery-identification",
        "user_fields": ["username", "email"]
    }
    stage = api("POST", "/stages/identification/", payload)
    print(f"Identification stage 'default-recovery-identification' created (pk: {stage['pk']})")
    return stage["pk"]

def get_or_create_email_stage():
    r = api("GET", "/stages/email/?name=default-recovery-email")
    if r.get("results"):
        stage = r["results"][0]
        print(f"Email stage 'default-recovery-email' already exists (pk: {stage['pk']})")
        return stage["pk"]
    
    payload = {
        "name": "default-recovery-email",
        "use_global_settings": True,
        "subject": "Restablecer contraseña — Instituto Nevadi",
        "template": "email/password_reset.html"
    }
    stage = api("POST", "/stages/email/", payload)
    print(f"Email stage 'default-recovery-email' created (pk: {stage['pk']})")
    return stage["pk"]

def bind_stages(flow_pk, stage_pks):
    # Get current bindings for this flow
    r = api("GET", f"/flows/bindings/?target={flow_pk}")
    existing_bindings = r.get("results", [])
    
    # Delete existing bindings to reset/apply cleanly
    for b in existing_bindings:
        print(f"Deleting binding {b['pk']}")
        # Use urlopen to DELETE since API helper raises on empty body of DELETE if not careful
        req = Request(f"{BASE}/flows/bindings/{b['pk']}/", headers=HEADERS, method="DELETE")
        with urlopen(req) as response:
            pass

    # Create new bindings
    for order, stage_pk in enumerate(stage_pks):
        payload = {
            "target": flow_pk,
            "stage": stage_pk,
            "order": order,
            "evaluate_on_plan": False,
            "re_evaluate_policies": True
        }
        b = api("POST", "/flows/bindings/", payload)
        print(f"Bound stage {stage_pk} to flow {flow_pk} with order {order} (binding pk: {b['pk']})")

def setup_recovery():
    # 1. Flow
    flow_pk = get_or_create_flow()
    
    # 2. Stages
    id_stage_pk = get_or_create_id_stage()
    email_stage_pk = get_or_create_email_stage()
    
    # Existing stage pks
    # default-password-change-prompt pk: bf803b2f-8213-4335-a8e0-dcd3e194cd36
    # default-password-change-write pk: f90fc88e-a7cf-4dea-acae-5931c04962c0
    prompt_stage_pk = "bf803b2f-8213-4335-a8e0-dcd3e194cd36"
    write_stage_pk = "f90fc88e-a7cf-4dea-acae-5931c04962c0"
    
    # 3. Bind stages
    bind_stages(flow_pk, [id_stage_pk, email_stage_pk, prompt_stage_pk, write_stage_pk])
    
    # 4. Link recovery flow to the main authentication identification stage
    # default-authentication-identification pk: 8f039a04-e8da-4ae3-b033-dbb02375bdf0
    api("PATCH", "/stages/identification/8f039a04-e8da-4ae3-b033-dbb02375bdf0/", {
        "name": "default-authentication-identification",
        "user_fields": ["username", "email"],
        "recovery_flow": flow_pk
    })
    print("Linked recovery flow to default-authentication-identification stage")
    
    # 5. Link recovery flow to the default Brand
    # Brand UUID: fe3c721a-3959-4e27-a41c-ef487700c7e3
    api("PATCH", "/core/brands/fe3c721a-3959-4e27-a41c-ef487700c7e3/", {
        "flow_recovery": flow_pk
    })
    print("Linked recovery flow to brand")

if __name__ == "__main__":
    setup_recovery()

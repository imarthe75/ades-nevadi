"""
Fixtures compartidas para app/tests/.

Hallazgo real 2026-07-16/18: test_security_idor.py (6 tests IDOR/RBAC) declaraba las
fixtures `client`, `db`, `auth_headers` sin que existiera ningún conftest.py que las
proveyera — los 6 tests fallaban en el *setup* (fixture 'client' not found), nunca
llegaban a ejecutar su aserción. En la práctica esa capa de regresión de seguridad no
existía, aunque el código de protección (`_check_expediente_access`, RBAC de
certificados/carbone) sí estuviera implementado.

Sigue el mismo patrón ya usado en test_casos_uso.py (`app.dependency_overrides` sobre
`get_ades_user`, no JWT real) — evita depender de Authentik en la suite de FastAPI, igual
que el resto de este archivo de tests ya hacía.
"""
import uuid

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from fastapi import Request, HTTPException

from app.main import app
from app.core.database import AsyncSessionLocal
from app.core.security import AdesUser, get_ades_user

PLANTEL_A_ID = uuid.uuid4()
PLANTEL_B_ID = uuid.uuid4()
PERSONA_ESTUDIANTE_A_ID = uuid.uuid4()

_ROLES: dict[str, AdesUser] = {
    "admin": AdesUser(
        id=uuid.uuid4(), nombre_usuario="test.admin", email="test.admin@institutonevadi.edu.mx",
        rol="ADMIN_GLOBAL", nivel_acceso=0, plantel_id=None,
    ),
    "maestro_plantel_a": AdesUser(
        id=uuid.uuid4(), nombre_usuario="test.maestro_a", email="test.maestro_a@institutonevadi.edu.mx",
        rol="DOCENTE", nivel_acceso=4, plantel_id=PLANTEL_A_ID,
    ),
    "estudiante_a": AdesUser(
        id=uuid.uuid4(), nombre_usuario="test.alumno_a", email="test.alumno_a@institutonevadi.edu.mx",
        rol="ALUMNO", nivel_acceso=5, plantel_id=PLANTEL_A_ID, persona_id=PERSONA_ESTUDIANTE_A_ID,
    ),
}

_ROLE_HEADER_PREFIX = "Bearer TESTROLE:"


async def _override_get_ades_user(request: Request) -> AdesUser:
    auth = request.headers.get("authorization", "")
    if auth.startswith(_ROLE_HEADER_PREFIX):
        role_key = auth[len(_ROLE_HEADER_PREFIX):]
        user = _ROLES.get(role_key)
        if user is not None:
            return user
    raise HTTPException(status_code=401, detail="Test role no reconocido — usar auth_headers fixture")


@pytest.fixture(autouse=True)
def _override_auth():
    """Reemplaza get_ades_user por una versión que lee el rol de prueba del header
    Authorization en vez de validar un JWT real — mismo criterio que test_casos_uso.py."""
    app.dependency_overrides[get_ades_user] = _override_get_ades_user
    yield
    app.dependency_overrides.pop(get_ades_user, None)


@pytest_asyncio.fixture
async def client():
    """AsyncClient contra la app FastAPI real (ASGITransport, sin servidor HTTP real)."""
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac


@pytest_asyncio.fixture
async def db():
    """Sesión async real contra la base de datos — mismo engine que get_db()."""
    async with AsyncSessionLocal() as session:
        yield session


@pytest.fixture
def auth_headers() -> dict[str, dict[str, str]]:
    """Headers por rol de prueba — el valor no es un JWT real, _override_get_ades_user
    lo mapea directo a un AdesUser fijo vía app.dependency_overrides."""
    return {role: {"Authorization": f"{_ROLE_HEADER_PREFIX}{role}"} for role in _ROLES}

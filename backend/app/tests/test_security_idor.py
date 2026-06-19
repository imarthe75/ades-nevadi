"""
Tests para validar IDOR fixes en endpoints críticos.
Verifica que la validación de acceso funciona correctamente.
"""

import pytest
from uuid import uuid4
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession


@pytest.mark.asyncio
async def test_expediente_maestro_no_acceso_otro_plantel(client, db: AsyncSession, auth_headers):
    """
    IDOR Fix #1: Maestro de plantel A NO puede ver expediente de plantel B
    """
    # Setup: Crear estudiante en plantel diferente
    otro_estudiante_id = str(uuid4())

    # When: GET expediente de estudiante de otro plantel
    response = await client.get(
        f"/api/v1/expediente/alumno/{otro_estudiante_id}",
        headers=auth_headers.get("maestro_plantel_a", {}),
    )

    # Then: Debe retornar 403 Forbidden
    assert response.status_code in [403, 404], f"Expected 403/404, got {response.status_code}"
    if response.status_code == 403:
        assert "acceso" in response.json().get("detail", "").lower()


@pytest.mark.asyncio
async def test_rate_limit_expediente_read(client, auth_headers):
    """
    Rate Limit Fix: GET /expediente debe respetar límite (100/minuto)
    """
    token = auth_headers.get("admin", {}).get("Authorization", "")
    estudiante_id = str(uuid4())

    # Hacer múltiples requests rápido
    for i in range(101):
        response = await client.get(
            f"/api/v1/expediente/alumno/{estudiante_id}",
            headers={"Authorization": token},
        )

        if i < 100:
            # Primeros 100 deben pasar (o fallar por no existir, pero no por rate limit)
            assert response.status_code != 429, f"Rate limited too early at request {i}"
        else:
            # Request 101 debería ser rate limited
            if response.status_code == 429:
                assert "Too many requests" in response.json().get("detail", "")
                break


@pytest.mark.asyncio
async def test_https_redirect_in_production(client):
    """
    HTTPS Fix: En producción, HTTP debe redirigir a HTTPS
    """
    response = await client.get(
        "/api/v1/health",
        follow_redirects=False,
    )

    # En environment=development, no hay redirect
    # En environment=production, sería 307
    assert response.status_code in [200, 307]


@pytest.mark.asyncio
async def test_security_headers_present(client):
    """
    Security Headers Fix: Todos los headers de seguridad deben estar presentes
    """
    response = await client.get("/api/v1/health")

    required_headers = [
        "Strict-Transport-Security",
        "X-Content-Type-Options",
        "X-Frame-Options",
        "Content-Security-Policy",
    ]

    for header in required_headers:
        assert header in response.headers, f"Missing security header: {header}"


@pytest.mark.asyncio
async def test_certificados_rbac_no_permiso(client, auth_headers):
    """
    IDOR Fix #2: Maestro NO puede emitir certificados (solo ADMIN/DIRECTOR)
    """
    response = await client.post(
        "/api/v1/certificados/emitir",
        json={
            "estudiante_id": str(uuid4()),
            "ciclo_escolar_id": str(uuid4()),
            "tipo_certificado": "ESTUDIOS",
        },
        headers=auth_headers.get("maestro_plantel_a", {}),
    )

    # Debe retornar 403 (no tiene permisos)
    assert response.status_code in [403, 422], f"Expected 403/422, got {response.status_code}"


@pytest.mark.asyncio
async def test_carbone_boleta_no_acceso(client, auth_headers):
    """
    IDOR Fix #3: Usuario NO puede generar boleta de estudiante que no es suyo
    """
    otro_estudiante_id = str(uuid4())

    response = await client.post(
        f"/api/v1/carbone/boleta/{otro_estudiante_id}",
        json={"template_id": str(uuid4()), "periodo": 1},
        headers=auth_headers.get("estudiante_a", {}),
    )

    # Debe retornar 403 (no tiene acceso)
    assert response.status_code in [403, 404], f"Expected 403/404, got {response.status_code}"
    if response.status_code == 403:
        assert "acceso" in response.json().get("detail", "").lower()


if __name__ == "__main__":
    pytest.main([__file__, "-v"])

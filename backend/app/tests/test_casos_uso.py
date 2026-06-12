import sys
import os
import asyncio
import uuid

# Ensure /app is in sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..')))

from httpx import AsyncClient, ASGITransport
from sqlalchemy import select
from app.main import app
from app.core.security import get_ades_user, get_current_user, AdesUser
from app.core.database import get_db
from app.models.materias import Materia, MateriaPlan, Tema
from app.models.academica import Grado, CicloEscolar

# Mocking authentication dependencies
async def mock_get_current_user():
    return {"sub": "test-sub", "email": "admin@institutonevadi.edu.mx"}

async def mock_get_ades_user():
    return AdesUser(
        id=uuid.uuid4(),
        nombre_usuario="admin",
        email="admin@institutonevadi.edu.mx",
        rol="ADMINISTRADOR_GLOBAL",
        nivel_acceso=1,
        plantel_id=None,
    )

async def run_tests():
    print("Iniciando pruebas de integración...")
    # Override dependencies
    app.dependency_overrides[get_current_user] = mock_get_current_user
    app.dependency_overrides[get_ades_user] = mock_get_ades_user

    try:
        # Retrieve active DB session to find or create test master data (Materia, Grado, Ciclo)
        async for db in get_db():
            # Find an existing Materia, Grado, and CicloEscolar
            res_materia = await db.execute(select(Materia).limit(1))
            materia = res_materia.scalar_one_or_none()
            
            res_grado = await db.execute(select(Grado).limit(1))
            grado = res_grado.scalar_one_or_none()
            
            res_ciclo = await db.execute(select(CicloEscolar).limit(1))
            ciclo = res_ciclo.scalar_one_or_none()
            
            if not (materia and grado and ciclo):
                print("Saltando pruebas: No hay datos base (materia, grado, ciclo) para realizar la prueba.")
                return

            print(f"Usando Materia={materia.id}, Grado={grado.id}, Ciclo={ciclo.id} para pruebas.")

            # Cleanup potential leftover from previous test runs
            await db.execute(
                MateriaPlan.__table__.delete().where(
                    MateriaPlan.materia_id == materia.id,
                    MateriaPlan.grado_id == grado.id,
                    MateriaPlan.ciclo_escolar_id == ciclo.id
                )
            )
            await db.commit()

            # Execute tests via AsyncClient pointing to FastAPI app
            async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
                # 1. Create a MateriaPlan assignment
                post_data = {
                    "materia_id": str(materia.id),
                    "grado_id": str(grado.id),
                    "ciclo_escolar_id": str(ciclo.id),
                    "orden": 1,
                    "horas_semanales": 4,
                    "es_obligatoria": True
                }
                resp_create = await ac.post("/api/v1/planes-estudio", json=post_data)
                print(f"POST /api/v1/planes-estudio response: {resp_create.status_code}")
                assert resp_create.status_code == 201
                plan_id = resp_create.json()["id"]
                print(f"Asignación creada exitosamente con ID: {plan_id}")

                # 2. Soft-delete the assignment
                resp_delete = await ac.delete(f"/api/v1/planes-estudio/{plan_id}")
                print(f"DELETE /api/v1/planes-estudio/{plan_id} response: {resp_delete.status_code}")
                assert resp_delete.status_code == 204

                # Verify it's indeed inactive in the database
                await db.close() # Refresh session
                async for db2 in get_db():
                    deleted_plan = await db2.get(MateriaPlan, uuid.UUID(plan_id))
                    assert deleted_plan is not None
                    assert deleted_plan.is_active is False
                    print("Verificación de soft-delete: exitoso (is_active=False)")
                    
                    # 3. Try to assign the same materia to the same grade and cycle again.
                    # The soft-delete bypass should reactivate it instead of throwing a 409 conflict error.
                    resp_recreate = await ac.post("/api/v1/planes-estudio", json=post_data)
                    print(f"POST /api/v1/planes-estudio (recrear) response: {resp_recreate.status_code}")
                    assert resp_recreate.status_code == 201
                    assert resp_recreate.json()["is_active"] is True
                    assert resp_recreate.json()["id"] == plan_id
                    print("Verificación de reactivación (Soft-Delete Bypass): exitoso!")
                    
                    # 4. Test Syllabus theme query with and without fixed grade (Caso 2.2)
                    # Create a general theme (grado_id IS NULL) and a specific theme (grado_id = grado.id)
                    t1 = Tema(
                        materia_id=materia.id,
                        grado_id=None,
                        nombre_tema="Tema General Sin Grado Fijo",
                        orden=10,
                        periodo_sugerido=1
                    )
                    t2 = Tema(
                        materia_id=materia.id,
                        grado_id=grado.id,
                        nombre_tema="Tema Específico Con Grado Fijo",
                        orden=20,
                        periodo_sugerido=1
                    )
                    db2.add_all([t1, t2])
                    await db2.commit()
                    
                    # Query themes for the plan
                    resp_themes = await ac.get(f"/api/v1/planes-estudio/{plan_id}/temas")
                    print(f"GET /api/v1/planes-estudio/{plan_id}/temas response: {resp_themes.status_code}")
                    assert resp_themes.status_code == 200
                    themes_data = resp_themes.json()
                    
                    theme_names = [t["nombre_tema"] for t in themes_data]
                    assert "Tema General Sin Grado Fijo" in theme_names
                    assert "Tema Específico Con Grado Fijo" in theme_names
                    print("Verificación de temas con/sin grado fijo: exitoso!")

                    # Cleanup test themes
                    await db2.delete(t1)
                    await db2.delete(t2)
                    
                    # Cleanup test plan assignment
                    clean_plan = await db2.get(MateriaPlan, uuid.UUID(plan_id))
                    if clean_plan:
                        await db2.delete(clean_plan)
                    await db2.commit()
                    print("Limpieza de datos de prueba finalizada.")
                    break
            break
        print("Todas las pruebas de integración pasaron correctamente. ✅")
    finally:
        app.dependency_overrides.pop(get_current_user, None)
        app.dependency_overrides.pop(get_ades_user, None)

if __name__ == "__main__":
    asyncio.run(run_tests())

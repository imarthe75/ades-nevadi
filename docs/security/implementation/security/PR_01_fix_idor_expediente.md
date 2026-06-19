# PR: Fix IDOR en expediente.py — Validación de acceso a estudiantes

**Title**: [SECURITY] Fix Insecure Direct Object Reference (IDOR) en GET /expediente/alumno/{id}

**Type**: 🔴 Security Fix (Critical)  
**Priority**: P0  
**Components**: backend/app/api/v1/expediente.py

---

## 📋 DESCRIPCIÓN

### Problema
El endpoint `GET /api/v1/expediente/alumno/{estudiante_id}` **no valida que el usuario tiene acceso a ese expediente**.

Cualquier usuario autenticado puede obtener expedientes de cualquier estudiante.

### Riesgo
```
OWASP A01: Broken Access Control (IDOR)
STRIDE: Elevation of Privilege
Severidad: CRÍTICA
```

### Escenario de Ataque
```
1. Maestro A (plantel primaria) obtiene token válido
2. Intenta: GET /api/v1/expediente/alumno/{uuid-estudiante-secundaria}
3. Resultado actual: ✅ 200 OK + todos los documentos
4. Resultado esperado: ❌ 403 Forbidden (no autorizado)
```

---

## 🔧 CAMBIOS

### Archivo: `/backend/app/api/v1/expediente.py`

#### 1. Importar función helper de validación

**Línea 28-29, AGREGAR:**

```python
# Existing imports
from app.core.security import AdesUser, get_ades_user

# NEW: Add validation helper
from app.models.academica import Estudiante, GrupoMaestro, Grupo
from sqlalchemy import and_
```

#### 2. Crear función helper para validar acceso

**Línea 53, AGREGAR ANTES de `_get_or_create_expediente`:**

```python
async def _check_expediente_access(
    db: AsyncSession,
    ades_user: AdesUser,
    estudiante_id: UUID,
) -> bool:
    """
    Validar que ades_user tiene acceso a expediente del estudiante.
    
    Permisos por rol:
    - ADMIN_GLOBAL: Acceso a todo
    - ADMIN_PLANTEL: Solo su plantel
    - MAESTRO: Solo estudiantes de sus grupos
    - ESTUDIANTE: Solo su propio expediente
    - PADRE: Solo expedientes de sus hijos
    
    Returns: True si acceso permitido, False si denegado
    """
    
    # ADMIN GLOBAL: acceso a todo
    if ades_user.es_admin_global:
        return True
    
    # Obtener datos del estudiante
    from app.models.academica import Estudiante
    stmt = select(Estudiante).where(Estudiante.id == estudiante_id)
    estudiante = (await db.execute(stmt)).scalar_one_or_none()
    
    if not estudiante:
        return False  # Estudiante no existe
    
    # ADMIN DE PLANTEL: acceso si estudiante está en su plantel
    if ades_user.es_admin_plantel:
        return estudiante.plantel_id == ades_user.plantel_id
    
    # MAESTRO: acceso si es maestro de un grupo que contiene al estudiante
    if ades_user.rol == "MAESTRO":
        # Verificar relación maestro-grupo-estudiante
        from app.models.academica import GrupoMaestro, Grupo
        stmt = select(1).select_from(GrupoMaestro).join(
            Grupo,
            GrupoMaestro.grupo_id == Grupo.id
        ).where(
            and_(
                GrupoMaestro.maestro_id == ades_user.persona_id,
                Grupo.id.in_(
                    select(Estudiante.grupo_id).where(
                        Estudiante.id == estudiante_id
                    )
                )
            )
        )
        result = await db.execute(stmt)
        return result.scalar() is not None
    
    # ESTUDIANTE: acceso solo a su propio expediente
    if ades_user.rol == "ESTUDIANTE":
        return estudiante.persona_id == ades_user.persona_id
    
    # PADRE: acceso a expedientes de sus hijos
    if ades_user.rol == "PADRE":
        from app.models.personas import RelacionTutor
        stmt = select(1).select_from(RelacionTutor).where(
            and_(
                RelacionTutor.padre_id == ades_user.persona_id,
                RelacionTutor.hijo_id == estudiante.persona_id,
            )
        )
        result = await db.execute(stmt)
        return result.scalar() is not None
    
    # Por defecto: denegar acceso
    return False
```

#### 3. Actualizar endpoint GET /expediente/alumno/{estudiante_id}

**Línea 137-142, REEMPLAZAR:**

```python
# ❌ ANTES:
@router.get("/alumno/{estudiante_id}", response_model=ExpedienteOut)
async def get_expediente(
    estudiante_id: UUID,
    db: AsyncSession = Depends(get_db),
    _user: AdesUser = Depends(get_ades_user),
):
```

**CON:**

```python
# ✅ DESPUÉS:
@router.get("/alumno/{estudiante_id}", response_model=ExpedienteOut)
async def get_expediente(
    estudiante_id: UUID,
    db: AsyncSession = Depends(get_db),
    ades_user: AdesUser = Depends(get_ades_user),
):
    """
    GET expediente de un alumno.
    
    Validación IDOR: 
    - Admin global: ve expedientes de todos
    - Admin de plantel: ve expedientes de su plantel
    - Maestro: ve expedientes de alumnos de sus grupos
    - Alumno: ve solo su propio expediente
    - Padre: ve expedientes de sus hijos
    """
    
    # ✅ IDOR CHECK: Verificar acceso
    if not await _check_expediente_access(db, ades_user, estudiante_id):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="No tienes acceso a este expediente"
        )
    
    # Obtener expediente (ahora seguro)
    exp = await _get_or_create_expediente(db, estudiante_id)
```

#### 4. Agregar status import si falta

**Línea 21, VERIFICAR:**

```python
from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
#                                                                                    ^ Agregar si falta
```

---

## 🧪 TESTS

**Crear**: `/backend/app/tests/test_expediente_idor.py`

```python
"""
Tests para validar IDOR fix en expediente.py
"""

import pytest
from uuid import uuid4
from httpx import AsyncClient
from app.main import app
from app.core.security import AdesUser

@pytest.fixture
async def client():
    return AsyncClient(app=app, base_url="http://test")

class TestExpedienteIDOR:
    """Tests para prevención de IDOR en expedientes"""
    
    @pytest.mark.asyncio
    async def test_maestro_no_puede_ver_expediente_otro_plantel(self, client, db):
        """IDOR: Maestro A no puede ver expediente de estudiante del Plantel B"""
        
        # Setup
        maestro_plantel_a = await create_user_with_role("MAESTRO", plantel_id=PLANTEL_A)
        estudiante_plantel_b = await create_student(plantel_id=PLANTEL_B)
        token = await get_token(maestro_plantel_a)
        
        # Act
        response = await client.get(
            f"/api/v1/expediente/alumno/{estudiante_plantel_b.id}",
            headers={"Authorization": f"Bearer {token}"}
        )
        
        # Assert
        assert response.status_code == 403, "Debe denegar acceso a otro plantel"
        assert "acceso" in response.json()["detail"].lower()
    
    @pytest.mark.asyncio
    async def test_maestro_puede_ver_expediente_su_grupo(self, client, db):
        """Maestro PUEDE ver expediente de alumno de su grupo"""
        
        # Setup
        maestro = await create_user_with_role("MAESTRO", plantel_id=PLANTEL_A)
        grupo = await create_group(maestro_id=maestro.id, plantel_id=PLANTEL_A)
        estudiante = await create_student(grupo_id=grupo.id, plantel_id=PLANTEL_A)
        token = await get_token(maestro)
        
        # Act
        response = await client.get(
            f"/api/v1/expediente/alumno/{estudiante.id}",
            headers={"Authorization": f"Bearer {token}"}
        )
        
        # Assert
        assert response.status_code == 200
        assert response.json()["estudiante_id"] == str(estudiante.id)
    
    @pytest.mark.asyncio
    async def test_estudiante_solo_ve_su_expediente(self, client, db):
        """Estudiante A NO puede ver expediente de Estudiante B"""
        
        # Setup
        estudiante_a = await create_user_with_role("ESTUDIANTE")
        estudiante_b = await create_user_with_role("ESTUDIANTE")
        token = await get_token(estudiante_a)
        
        # Act
        response = await client.get(
            f"/api/v1/expediente/alumno/{estudiante_b.id}",
            headers={"Authorization": f"Bearer {token}"}
        )
        
        # Assert
        assert response.status_code == 403
    
    @pytest.mark.asyncio
    async def test_estudiante_puede_ver_su_expediente(self, client, db):
        """Estudiante PUEDE ver su propio expediente"""
        
        # Setup
        estudiante = await create_user_with_role("ESTUDIANTE")
        token = await get_token(estudiante)
        
        # Act
        response = await client.get(
            f"/api/v1/expediente/alumno/{estudiante.id}",
            headers={"Authorization": f"Bearer {token}"}
        )
        
        # Assert
        assert response.status_code == 200
    
    @pytest.mark.asyncio
    async def test_padre_puede_ver_expediente_hijo(self, client, db):
        """Padre PUEDE ver expediente de su hijo"""
        
        # Setup
        padre = await create_user_with_role("PADRE")
        hijo = await create_user_with_role("ESTUDIANTE")
        await create_parent_child_relation(padre, hijo)
        token = await get_token(padre)
        
        # Act
        response = await client.get(
            f"/api/v1/expediente/alumno/{hijo.id}",
            headers={"Authorization": f"Bearer {token}"}
        )
        
        # Assert
        assert response.status_code == 200
    
    @pytest.mark.asyncio
    async def test_padre_no_puede_ver_expediente_otro(self, client, db):
        """Padre NO puede ver expediente de hijo de otro padre"""
        
        # Setup
        padre_a = await create_user_with_role("PADRE")
        hijo_b = await create_user_with_role("ESTUDIANTE")
        token = await get_token(padre_a)
        
        # Act
        response = await client.get(
            f"/api/v1/expediente/alumno/{hijo_b.id}",
            headers={"Authorization": f"Bearer {token}"}
        )
        
        # Assert
        assert response.status_code == 403
    
    @pytest.mark.asyncio
    async def test_admin_global_puede_ver_cualquier_expediente(self, client, db):
        """Admin global PUEDE ver expediente de cualquier estudiante"""
        
        # Setup
        admin = await create_user_with_role("ADMIN_GLOBAL")
        estudiante = await create_user_with_role("ESTUDIANTE", plantel_id=PLANTEL_B)
        token = await get_token(admin)
        
        # Act
        response = await client.get(
            f"/api/v1/expediente/alumno/{estudiante.id}",
            headers={"Authorization": f"Bearer {token}"}
        )
        
        # Assert
        assert response.status_code == 200

# Run tests:
# pytest app/tests/test_expediente_idor.py -v
```

---

## ✅ CHECKLIST DE REVISIÓN

- [ ] Cambios en `expediente.py` aplicados correctamente
- [ ] Función `_check_expediente_access()` cubre todos los roles
- [ ] Tests en `test_expediente_idor.py` pasan
- [ ] Endpoint `/expediente/alumno/{id}` retorna 403 si sin acceso
- [ ] Endpoint `/expediente/alumno/{id}` retorna 200 si con acceso
- [ ] Auditoría registra intentos de acceso denegado (403)
- [ ] Sin cambios en schema de respuesta
- [ ] Backward compatible con clientes existentes

---

## 🚀 DEPLOYMENT

1. **Merge a branch `security/fix-idor-expediente`**
2. **Deploy a staging** → Validar tests pasan
3. **Manual testing**: 
   - Maestro plantel A intenta acceder expediente plantel B → 403 ✓
   - Maestro accede expediente de su grupo → 200 ✓
4. **Deploy a producción** → Monitorear logs 403

---

## 📝 NOTAS

- Fix es backward compatible (solo añade validación)
- No afecta otros endpoints en expediente.py
- Performance: +1 query (join grupo-maestro), negligible
- Auditoría automáticamente registra intentos denegados

---

**Reviewers Sugeridos**: @security-team, @backend-lead  
**Merge After**: Aprobación de 2 reviewers + tests verdes

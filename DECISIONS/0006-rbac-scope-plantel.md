# ADR 0006 — RBAC con Scope de Plantel en Endpoints Críticos

**Fecha:** 2026-06-11  
**Estado:** Aceptado  
**Autores:** Agente Residente ADES  

---

## Contexto

Durante la Auditoría 360° (Sprint 1, 2026-06-11) se identificó que múltiples endpoints críticos — `calificaciones.py`, `imports.py`, y parcialmente `gradebook.py` — usaban `get_current_user` (que solo valida el JWT) en lugar de `get_ades_user` (que carga el perfil RBAC completo del usuario en BD). Esto generaba:

1. **IDOR (Insecure Direct Object Reference):** Cualquier JWT válido podía leer/escribir calificaciones de cualquier plantel, no solo el propio.
2. **Importación masiva sin control:** Padres de familia con JWT válido podían importar miles de alumnos via POST `/imports/alumnos`.
3. **Sin protección por nivel de acceso:** Un alumno (nivel 5) podía registrar calificaciones vía POST `/calificaciones`.

## Decisión

**Patrón obligatorio para todos los endpoints mutantes o de datos sensibles:**

```python
ades_user: AdesUser = Depends(get_ades_user)   # en lugar de get_current_user

# 1. Check nivel de acceso mínimo requerido
if ades_user.nivel_acceso > MAX_NIVEL:
    raise HTTPException(403, "Sin permiso")

# 2. Check scope de plantel (si el usuario tiene plantel asignado)
if ades_user.plantel_id is not None:
    entidad = await db.get(Grupo, grupo_id)
    if entidad and entidad.plantel_id != ades_user.plantel_id:
        raise HTTPException(403, "Entidad no pertenece a tu plantel")
```

**Niveles de acceso asignados por tipo de recurso:**

| Recurso | Lectura | Escritura | Razón |
|---------|---------|-----------|-------|
| Calificaciones | ≤ 5 (no padres vía GET) | ≤ 4 (docentes+) | Tutores ven boleta; docentes registran |
| Libreta grupo | ≤ 5 | ≤ 4 | — |
| Imports | — | ≤ 2 (admin+coord) | Riesgo masivo de datos |
| Módulo médico | ≤ 4 | ≤ 3 | Datos sensibles LGPDPPSO |
| Admin | ≤ 1 | ≤ 1 | Solo admins |

**Rutas frontend protegidas con `roleGuard`** (11 rutas en `app.routes.ts`):

| Ruta | `roleGuard(N)` | Significado |
|------|----------------|-------------|
| `/calificaciones`, `/asistencias`, `/tareas`, `/conducta`, `/alumnos`, `/horarios` | `roleGuard(4)` | Docentes y superiores |
| `/profesores`, `/medico`, `/eval-docente`, `/ia`, `/grade-analytics`, `/reportes` | `roleGuard(3)` | Coordinadores y superiores |
| `/grupos` | `roleGuard(3)` | Coordinadores+ |

## Consecuencias positivas

- Elimina IDOR en calificaciones e importaciones.
- Un padre (nivel 5) recibe 403 al intentar navegar a `/calificaciones` tanto en frontend (roleGuard) como en backend (nivel_acceso > 4).
- El scope de plantel aísla datos entre planteles incluso para usuarios del mismo rol.

## Consecuencias a considerar

- Los endpoints de **solo lectura** (GET) de catálogos siguen usando `get_current_user` para reducir overhead de DB. Son datos no sensibles.
- Al agregar `get_ades_user`, cada request autenticada hace 1-2 queries adicionales a BD (perfil usuario + privilegios). Mitigado con TTL implícito del token Authentik (corta duración).
- Todo nuevo endpoint mutante DEBE usar `get_ades_user`. Esto debe validarse en code review.

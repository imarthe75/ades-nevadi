# ADR 0005 — Propagación de Usuario en Audit Trail via request.state

**Fecha:** 2026-06-11  
**Estado:** Aceptado  
**Autores:** Agente Residente ADES  

---

## Contexto

El middleware `AuditMiddleware` (en `core/audit.py`) registra todas las mutaciones HTTP con el usuario que las ejecutó. La implementación original intentaba extraer el usuario re-decodificando el JWT del header `Authorization` con HS256:

```python
payload = jwt.decode(token, settings.SECRET_KEY, algorithms=["HS256"])
```

Sin embargo, Authentik emite tokens **RS256** (asimétrico). El decode HS256 siempre fallaba silenciosamente, produciendo `usuario_id = NULL` en el **100% de los registros de auditoría**. El trail de auditoría era funcionalmente inútil.

## Decisión

`get_ades_user` (en `core/security.py`) — que ya valida el JWT RS256 correctamente — **propaga el usuario autenticado al `request.state`** antes de retornar:

```python
request.state.ades_user_id    = str(usuario.id)
request.state.ades_user_nombre = usuario.nombre_usuario
```

`AuditMiddleware._extract_user()` lee estos valores desde `request.state` **después** de que `call_next()` ejecuta el handler (y por tanto `get_ades_user` ya corrió):

```python
uid_str = getattr(request.state, "ades_user_id", None)
nombre  = getattr(request.state, "ades_user_nombre", None)
```

## Consecuencias positivas

- Los audit logs ahora tienen `usuario_id` correcto en todos los endpoints que usan `get_ades_user`.
- No se re-decodifica el JWT en el middleware (sin overhead criptográfico extra).
- La dependencia `jose` ya no es necesaria en `audit.py` (import eliminado).
- Si un endpoint solo usa `get_current_user` (sin `get_ades_user`), el audit log tendrá `usuario_id = NULL` — comportamiento intencional y documentado.

## Consecuencias a considerar

- Las rutas que aún usan `get_current_user` (endpoints de solo lectura) seguirán con `usuario_id = NULL` en auditoría. Esto es aceptable por diseño: solo las mutaciones críticas requieren trazabilidad completa, y todas pasan por `get_ades_user`.
- El orden Starlette garantiza que `call_next()` completa antes de leer `request.state`, lo que hace el patrón determinístico.

## Alternativas descartadas

- **Re-decodificar con RS256 en el middleware:** Requiere descargar JWKS por red en cada request de auditoría. Agrega ~10-50ms de latencia innecesaria.
- **Middleware de autenticación dedicado:** Haría la arquitectura más compleja sin beneficio real dado que ya existe `get_ades_user` como dependencia FastAPI.

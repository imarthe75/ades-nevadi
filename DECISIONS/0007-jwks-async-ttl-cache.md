# ADR 0007 — JWKS Async con TTL Cache en security.py

**Fecha:** 2026-06-11  
**Estado:** Aceptado  
**Autores:** Agente Residente ADES  

---

## Contexto

`security.py` usaba `httpx.get()` (síncrono) dentro de funciones decoradas con `@lru_cache` para obtener el endpoint JWKS de Authentik. Esto causaba dos problemas:

1. **Bloqueo del event loop:** `httpx.get()` es bloqueante. En un servidor asyncio (uvicorn), una llamada bloqueante puede detener el procesamiento de todas las demás requests hasta 10 segundos (timeout configurado).

2. **`@lru_cache` sin TTL:** El cache de `lru_cache` es perpetuo. Si Authentik rota sus llaves (key rotation), el servidor seguía usando el JWKS antiguo indefinidamente, resultando en tokens válidos rechazados.

## Decisión

Reescribir toda la cadena de verificación de tokens como `async`:

```python
# TTL cache manual sin dependencias externas
_JWKS_CACHE: dict = {}
_JWKS_TTL   = 300  # 5 minutos
_JWKS_LOCK  = asyncio.Lock()

async def _get_jwks_uri() -> str:
    async with httpx.AsyncClient(timeout=5.0) as client:
        resp = await client.get(disc_url)
    ...

async def _fetch_jwks() -> dict:
    now = time.monotonic()
    async with _JWKS_LOCK:
        if _JWKS_CACHE.get("exp", 0.0) > now:
            return _JWKS_CACHE["keys"]
        uri = await _get_jwks_uri()
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(uri)
        _JWKS_CACHE["keys"] = resp.json()
        _JWKS_CACHE["exp"]  = now + _JWKS_TTL
        return _JWKS_CACHE["keys"]

async def verify_token(token: str) -> dict: ...
async def get_current_user(...) -> dict: return await verify_token(...)
```

**Por qué no `cachetools.TTLCache`:** No está en `requirements.txt` y la implementación manual con `time.monotonic()` + `asyncio.Lock` es trivial y sin dependencias adicionales.

**Por qué `asyncio.Lock` y no thread lock:** El servidor es asyncio (uvicorn). `asyncio.Lock` previene que múltiples coroutines concurrentes desencadenen re-fetch simultáneo del JWKS (thundering herd). En Python 3.12, `asyncio.Lock()` a nivel de módulo es seguro (no se ata al event loop en creación).

## Consecuencias positivas

- El event loop ya no se bloquea durante la validación de tokens.
- Las llaves JWKS se re-fetchen automáticamente tras 5 minutos (TTL), soportando key rotation de Authentik sin restart del servidor.
- En condiciones normales, cada 5 minutos 1 request re-fetcha las llaves; todas las demás requests usan el cache (0 latencia adicional de red).

## Consecuencias a considerar

- Si Authentik es inaccesible durante el re-fetch (expiración del TTL), la primera request tras expiración recibe 503. Las demás requests durante el intento de re-fetch también esperan en `asyncio.Lock`. El timeout de 10s mitiga este impacto.
- `asyncio.Lock` no es reentrante — si `_fetch_jwks` se llamara desde dentro del mismo lock context, deadlock. Esto no ocurre en el flujo actual.

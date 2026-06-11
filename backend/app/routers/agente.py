from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel
from typing import Optional, Dict, Any
import logging

# Simulando la carga de memoria para la inicialización
try:
    import sys
    import os
    sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../../..', '.agent')))
    from memory.semantic_cache import SemanticCache
    from memory.long_term_memory import LongTermMemory
    MEMORY_AVAILABLE = True
except ImportError:
    MEMORY_AVAILABLE = False

router = APIRouter(
    prefix="/api/v1/agente",
    tags=["agente-residente"]
)

class AgenteResidenteRequest(BaseModel):
    agente_id: str
    contexto_sesion: Optional[Dict[str, Any]] = None

class AgenteResidenteResponse(BaseModel):
    status: str
    valkey_connected: bool
    postgres_connected: bool
    mensaje: str

@router.post("/init", response_model=AgenteResidenteResponse, status_code=status.HTTP_200_OK)
def init_resident_agent(request: AgenteResidenteRequest):
    """
    Inicializa el Agente Residente y valida su conexión con la memoria dual (Valkey + pgvector).
    """
    valkey_ok = False
    pg_ok = False
    
    if not MEMORY_AVAILABLE:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Las dependencias de memoria (.agent/memory) no están disponibles en el entorno."
        )
        
    try:
        # Test Valkey connectivity
        cache = SemanticCache()
        if cache.redis_client.ping():
            valkey_ok = True
    except Exception as e:
        logging.warning(f"Fallo conexión Valkey: {e}")
        
    try:
        # Test Postgres connectivity
        mem = LongTermMemory()
        conn = mem._connect()
        conn.close()
        pg_ok = True
    except Exception as e:
        logging.warning(f"Fallo conexión Postgres: {e}")
        
    if not (valkey_ok and pg_ok):
        # Graceful degradation: inform the client but don't strictly fail if off-grid is allowed
        # depending on strictness, we could return 503. For now returning 200 with degraded status.
        return AgenteResidenteResponse(
            status="degraded",
            valkey_connected=valkey_ok,
            postgres_connected=pg_ok,
            mensaje="Agente iniciado en modo degradado. Algunos servicios de memoria no están disponibles."
        )
        
    return AgenteResidenteResponse(
        status="active",
        valkey_connected=valkey_ok,
        postgres_connected=pg_ok,
        mensaje="Agente Residente inicializado correctamente con memoria dual activa."
    )

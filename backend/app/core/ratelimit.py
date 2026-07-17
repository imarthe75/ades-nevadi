"""
🔐 Rate Limiting Configuration

Protege contra:
- Brute force attacks (login)
- DOS via enumeration (expediente listing)
- Spam de cambios (calificaciones)
"""

from slowapi import Limiter
from slowapi.util import get_remote_address

# Inicializar limiter con key basado en IP remota
limiter = Limiter(
    key_func=get_remote_address,
    default_limits=["1000/hour"],  # Default global limit
    storage_uri="memory://",  # En producción, usar redis://
)

# ✅ Límites específicos por tipo de endpoint
LIMITS = {
    "auth": "5/minute",         # Login: máximo 5 intentos por minuto
    "read": "100/minute",       # GET requests: 100 por minuto
    "write": "50/minute",       # POST/PATCH/PUT: 50 por minuto
    "upload": "10/minute",      # File uploads: 10 por minuto
    "export": "20/hour",        # Exports: 20 por hora
    "public": "100/day",        # Public endpoints (sin auth): 100 por día
    # Hallazgo real 2026-07-17 (evaluación OWASP API6 — Business Flows, nunca
    # evaluado antes): /chatbot/mensaje y /chatbot/sql disparan una llamada
    # real al LLM (costo por token) sin ningún límite — cualquier usuario
    # autenticado, incluido nivel_acceso 5 (alumno/padre), podía llamarlos en
    # loop. Más estricto que "write" porque el costo por llamada es real
    # dinero, no solo carga del servidor.
    "ai": "15/minute",
}

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
}

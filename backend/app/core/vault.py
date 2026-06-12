import os
import logging
from typing import Any

logger = logging.getLogger("ades.vault")

try:
    import hvac
    _HVAC_AVAILABLE = True
except ImportError:
    _HVAC_AVAILABLE = False

_client = None

def get_vault_client():
    global _client
    if not _HVAC_AVAILABLE:
        return None
    if _client is not None:
        return _client

    vault_addr = os.getenv("VAULT_ADDR")
    vault_token = os.getenv("VAULT_TOKEN")

    if not vault_addr or not vault_token:
        logger.warning("VAULT_ADDR o VAULT_TOKEN no configurados. Se usará fallback a variables de entorno.")
        return None

    try:
        client = hvac.Client(url=vault_addr, token=vault_token)
        if client.is_authenticated():
            _client = client
            logger.info("Conexión exitosa a HashiCorp Vault en %s", vault_addr)
            return _client
        else:
            logger.warning("No se pudo autenticar en Vault con el token proporcionado.")
    except Exception as e:
        logger.error("Error al conectar a HashiCorp Vault: %s", str(e))

    return None

def get_secret(key: str, default: Any = None) -> Any:
    """
    Obtiene un secreto de Vault de la ruta 'secret/data/ades'.
    Si falla o no existe, recurre a las variables de entorno de la máquina.
    """
    client = get_vault_client()
    if client:
        try:
            read_response = client.secrets.kv.v2.read_secret_version(
                path="ades",
                mount_point="secret"
            )
            secrets_data = read_response.get("data", {}).get("data", {})
            if key in secrets_data:
                return secrets_data[key]
        except Exception as e:
            logger.debug("No se pudo leer el secreto '%s' desde Vault: %s. Usando fallback.", key, str(e))

    return os.getenv(key, default)

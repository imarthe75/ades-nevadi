"""
🔐 Encriptación de campos sensibles (PII).
Usa Fernet (symmetric encryption) para datos sensibles en BD.

Uso:
    from app.core.encryption import encrypt_field, decrypt_field
    encrypted = encrypt_field("email@example.com")
    decrypted = decrypt_field(encrypted)
"""

from cryptography.fernet import Fernet, InvalidToken
from app.core.config import settings
import logging

log = logging.getLogger(__name__)

_CIPHER = None


def get_cipher() -> Fernet:
    """Obtener cipher singleton para encriptación/desencriptación."""
    global _CIPHER
    if _CIPHER is None:
        key = settings.DATABASE_ENCRYPTION_KEY
        if isinstance(key, str):
            key = key.encode()
        _CIPHER = Fernet(key)
    return _CIPHER


def encrypt_field(value: str) -> str | None:
    """Encriptar un campo sensible.

    Args:
        value: Valor a encriptar (string)

    Returns:
        Valor encriptado (string) o None si input es None/vacío
    """
    if not value:
        return None
    try:
        cipher = get_cipher()
        encrypted = cipher.encrypt(value.encode())
        return encrypted.decode()
    except Exception as e:
        log.error(f"Encryption error: {e}")
        raise ValueError("Error encriptando dato")


def decrypt_field(value: str) -> str | None:
    """Desencriptar un campo sensible.

    Args:
        value: Valor encriptado (string)

    Returns:
        Valor desencriptado (string) o None si input es None/vacío
    """
    if not value:
        return None
    try:
        cipher = get_cipher()
        decrypted = cipher.decrypt(value.encode())
        return decrypted.decode()
    except InvalidToken as e:
        log.error(f"Invalid token: {e}")
        raise ValueError("Error desencriptando dato: token inválido")
    except Exception as e:
        log.error(f"Decryption error: {e}")
        raise ValueError("Error desencriptando dato")


def encrypt_dict(data: dict, fields: list[str]) -> dict:
    """Encriptar múltiples campos en un diccionario.

    Args:
        data: Diccionario con datos
        fields: Lista de nombres de campos a encriptar

    Returns:
        Diccionario con campos encriptados
    """
    result = data.copy()
    for field in fields:
        if field in result and result[field]:
            result[field] = encrypt_field(result[field])
    return result


def decrypt_dict(data: dict, fields: list[str]) -> dict:
    """Desencriptar múltiples campos en un diccionario.

    Args:
        data: Diccionario con datos encriptados
        fields: Lista de nombres de campos a desencriptar

    Returns:
        Diccionario con campos desencriptados
    """
    result = data.copy()
    for field in fields:
        if field in result and result[field]:
            result[field] = decrypt_field(result[field])
    return result

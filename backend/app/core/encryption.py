import base64
import hashlib
from cryptography.fernet import Fernet
from app.core.config import settings

def _get_fernet() -> Fernet:
    key_src = settings.DATABASE_ENCRYPTION_KEY or settings.SECRET_KEY
    key_hash = hashlib.sha256(key_src.encode("utf-8")).digest()
    key_b64 = base64.urlsafe_b64encode(key_hash)
    return Fernet(key_b64)

def encrypt_val(val: str | None) -> str | None:
    if not val:
        return val
    try:
        f = _get_fernet()
        return f.encrypt(val.encode("utf-8")).decode("utf-8")
    except Exception:
        return val

def decrypt_val(val: str | None) -> str | None:
    if not val:
        return val
    try:
        f = _get_fernet()
        return f.decrypt(val.encode("utf-8")).decode("utf-8")
    except Exception:
        # Fallback to plain text if decryption fails
        return val

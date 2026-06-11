"""
Servicio de firma digital Ed25519 para certificados ADES.
FASE 27 — Certificación Digital (sin dependencia de pyhanko).

Arquitectura:
  - Llave privada: en variable de entorno FIRMA_CLAVE_PRIVADA_HEX (nunca en BD)
  - Llave pública: en tabla ades_llaves_firma (auditable)
  - Payload firmado: sha256(json_canonico(certificado))
  - QR: URL de verificación pública en PNG base64
"""
from __future__ import annotations

import base64
import hashlib
import io
import json
import os
from datetime import datetime, timezone
from typing import Optional

from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey,
    Ed25519PublicKey,
)
from cryptography.hazmat.primitives.serialization import (
    Encoding,
    NoEncryption,
    PrivateFormat,
    PublicFormat,
)
from cryptography.exceptions import InvalidSignature

import qrcode
import qrcode.constants


# ── Carga / generación de llave ──────────────────────────────────────────────

def obtener_llave_privada() -> Optional[Ed25519PrivateKey]:
    """Lee la llave privada desde variable de entorno."""
    hex_key = os.getenv("FIRMA_CLAVE_PRIVADA_HEX", "")
    if not hex_key:
        return None
    raw = bytes.fromhex(hex_key)
    return Ed25519PrivateKey.from_private_bytes(raw)


def generar_nuevo_par_de_llaves() -> dict:
    """
    Genera un par Ed25519 nuevo.
    Retorna {'privada_hex': str, 'publica_b64': str}.
    La privada_hex debe copiarse a .env como FIRMA_CLAVE_PRIVADA_HEX.
    """
    privada = Ed25519PrivateKey.generate()
    publica = privada.public_key()

    raw_priv = privada.private_bytes(
        encoding=Encoding.Raw,
        format=PrivateFormat.Raw,
        encryption_algorithm=NoEncryption(),
    )
    raw_pub = publica.public_bytes(encoding=Encoding.Raw, format=PublicFormat.Raw)

    return {
        "privada_hex": raw_priv.hex(),
        "publica_b64": base64.b64encode(raw_pub).decode(),
    }


def llave_publica_b64() -> Optional[str]:
    """Devuelve la llave pública del .env como base64."""
    privada = obtener_llave_privada()
    if not privada:
        return None
    raw_pub = privada.public_key().public_bytes(
        encoding=Encoding.Raw, format=PublicFormat.Raw
    )
    return base64.b64encode(raw_pub).decode()


# ── Payload canónico ─────────────────────────────────────────────────────────

def _payload_canonico(certificado: dict) -> bytes:
    """
    Genera el payload canónico JSON para firmar.
    Solo incluye campos estables (excluye timestamps de BD y metadatos de fila).
    """
    campos = {
        "folio":            certificado.get("folio"),
        "tipo_certificado": certificado.get("tipo_certificado"),
        "nivel_educativo":  certificado.get("nivel_educativo"),
        "grado_completado": certificado.get("grado_completado"),
        "promedio_final":   str(certificado.get("promedio_final") or ""),
        "fecha_emision":    str(certificado.get("fecha_emision") or ""),
        "estudiante_id":    str(certificado.get("estudiante_id") or ""),
        "ciclo_escolar_id": str(certificado.get("ciclo_escolar_id") or ""),
        "institucion":      "Instituto Nevadi",
    }
    return json.dumps(campos, sort_keys=True, ensure_ascii=False).encode("utf-8")


def calcular_hash(certificado: dict) -> str:
    """Calcula SHA-256 del payload canónico. Retorna hex string."""
    payload = _payload_canonico(certificado)
    return hashlib.sha256(payload).hexdigest()


# ── Firma y verificación ─────────────────────────────────────────────────────

def firmar(certificado: dict) -> Optional[str]:
    """
    Firma el payload canónico con Ed25519.
    Retorna la firma como base64url, o None si no hay llave configurada.
    """
    privada = obtener_llave_privada()
    if not privada:
        return None
    payload = _payload_canonico(certificado)
    signature_bytes = privada.sign(payload)
    return base64.urlsafe_b64encode(signature_bytes).decode()


def verificar_firma(certificado: dict, firma_b64url: str, pub_b64: str) -> bool:
    """
    Verifica la firma Ed25519.
    certificado: dict con los campos del certificado.
    firma_b64url: firma almacenada en la BD.
    pub_b64: clave pública en base64 estándar (de ades_llaves_firma).
    """
    try:
        raw_pub = base64.b64decode(pub_b64)
        publica: Ed25519PublicKey = Ed25519PublicKey.from_public_bytes(raw_pub)
        payload = _payload_canonico(certificado)
        sig = base64.urlsafe_b64decode(firma_b64url + "==")
        publica.verify(sig, payload)
        return True
    except (InvalidSignature, Exception):
        return False


# ── Generación de QR ─────────────────────────────────────────────────────────

def generar_qr_png_b64(url: str, box_size: int = 4, border: int = 2) -> str:
    """
    Genera un QR Code para la URL dada.
    Retorna la imagen PNG como string base64 (para embeber en HTML/PDF).
    """
    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=box_size,
        border=border,
    )
    qr.add_data(url)
    qr.make(fit=True)
    img = qr.make_image(fill_color="black", back_color="white")

    buf = io.BytesIO()
    img.save(buf, format="PNG")
    buf.seek(0)
    return base64.b64encode(buf.read()).decode()


def generar_url_verificacion(folio: str, base_url: str = "https://ades.setag.mx") -> str:
    return f"{base_url}/verificar/{folio}"

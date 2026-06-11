# ADR-0004 — Certificación Digital con Ed25519 (FASE 27)

**Estado:** Propuesto  
**Fecha:** 2026-06-10  
**Autor:** Agente Residente v2.0

## Contexto

Los certificados y boletas del Instituto Nevadi requieren firma digital para garantizar
integridad y autenticidad. SEP/UAEMEX exigen documentos verificables. Se necesita
un mecanismo de costo $0 que funcione offline y prepare el camino para anclaje blockchain.

## Decisión

**Nivel 1 — Integridad interna (Ed25519):**
- Librería: `cryptography` (Python, ya instalada vía python-jose)
- Algoritmo: Ed25519 (RFC 8032) — firma de 64 bytes, llave pública 32 bytes
- Payload firmado: `sha256(json_canonico(certificado))` → firma Ed25519
- Llave privada: en `.env` como `FIRMA_CLAVE_PRIVADA_HEX` (NO en BD)
- Llave pública: en tabla `ades_llaves_firma` (auditable)
- QR: librería `qrcode[pil]` → URL `https://ades.setag.mx/verificar/{folio}`
- Verificación: endpoint público `GET /api/v1/verificar/{folio}` (sin auth)

**Nivel 2 — Validez legal (futuro FASE 28):**
- FIEL/e.firm SAT (requiere certificado del Instituto ante SAT)
- pyhanko PAdES (firma embebida en PDF)

## Consecuencias

- `ades_certificados` extendido: `hash_sha256`, `firma_ed25519`, `clave_publica_ref`, `verificable_url`, `estado_firma`
- Nueva tabla `ades_llaves_firma`: inventario de llaves públicas del instituto
- Celery task `firmar_certificados_batch` para firmar masivamente al cierre de ciclo
- La llave privada NUNCA entra a la BD — se requiere rotación manual via `.env`
- FASE 28 (Vault) moverá la llave privada a HashiCorp Vault con rotación automática

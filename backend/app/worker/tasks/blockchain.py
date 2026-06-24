"""Tarea Celery: anclaje de certificados digitales en Polygon PoS.

Flujo:
  1. Leer el certificado (hash_sha256, folio) de ades_certificados.
  2. Llamar a ``anclar_hash_blockchain`` del servicio blockchain (puede ser MOCK
     en desarrollo o Polygon real en producción según POLYGON_RPC_URL).
  3. Actualizar ades_certificados con tx_hash, estado, fecha de anclaje y red.

La tarea es idempotente en cuanto al hash: si el mismo hash ya fue anclado,
el contrato inteligente rechaza la transacción duplicada sin revertir el estado.
"""
from __future__ import annotations
import logging
import uuid
from sqlalchemy import text
from app.worker.celery_app import celery_app
from app.core.database import get_db
from app.services.blockchain import anclar_hash_blockchain

logger = logging.getLogger("ades.worker.blockchain")

@celery_app.task(name="app.worker.tasks.blockchain.anclar_certificado_task")
def anclar_certificado_task(certificado_id: str):
    """
    Tarea asíncrona para anclar el hash SHA-256 de un certificado en Polygon PoS.
    """
    import asyncio
    
    try:
        loop = asyncio.get_event_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        
    return loop.run_until_complete(_anclar_certificado_async(certificado_id))

async def _anclar_certificado_async(certificado_id: str):
    """Implementación async del anclaje de un certificado en la blockchain.

    Valida que el certificado exista y tenga hash_sha256 generado (requiere
    firma Ed25519 previa), delega el anclaje al servicio blockchain y persiste
    el resultado (tx_hash, estado, fecha_anclaje, red) en la BD.

    Args:
        certificado_id: UUID del registro en ades_certificados.

    Returns:
        True si el anclaje se completó correctamente, False en caso contrario.
    """
    logger.info("Iniciando tarea de anclaje blockchain para certificado: %s", certificado_id)
    
    async for db in get_db():
        try:
            cert_row = await db.execute(text("""
                SELECT hash_sha256, estado_firma, folio
                FROM ades_certificados
                WHERE id = :id::uuid AND is_active = TRUE
            """), {"id": certificado_id})
            
            cert = cert_row.mappings().first()
            if not cert:
                logger.error("Certificado %s no encontrado", certificado_id)
                return False
                
            hash_hex = cert["hash_sha256"]
            if not hash_hex:
                logger.error("El certificado %s no cuenta con un hash local generado (Ed25519 pendiente).", certificado_id)
                return False
                
            resultado = anclar_hash_blockchain(hash_hex)
            
            await db.execute(text("""
                UPDATE ades_certificados
                SET blockchain_tx = :tx,
                    blockchain_status = :status,
                    fecha_anclaje = :fecha,
                    blockchain_network = :network,
                    fecha_modificacion = NOW(),
                    row_version = row_version + 1
                WHERE id = :id::uuid
            """), {
                "tx": resultado["tx_hash"],
                "status": resultado["status"],
                "fecha": resultado["fecha_anclaje"],
                "network": resultado["network"],
                "id": certificado_id
            })
            await db.commit()
            
            logger.info("Proceso de anclaje completado para certificado %s (Folio: %s) -> Estatus: %s, Tx: %s",
                        certificado_id, cert["folio"], resultado["status"], resultado["tx_hash"])
            return True
            
        except Exception as e:
            logger.error("Error en la ejecución de la tarea de anclaje: %s", str(e))
            await db.rollback()
            return False
        finally:
            await db.close()
    return False

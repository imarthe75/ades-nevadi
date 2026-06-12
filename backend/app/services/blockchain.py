import logging
import secrets
from datetime import datetime, timezone
from typing import Optional

from app.core.config import settings

logger = logging.getLogger("ades.blockchain")

# ABI del contrato inteligente simplificado
MINIMAL_ABI = [
    {
        "constant": False,
        "inputs": [{"name": "_hash", "type": "bytes32"}],
        "name": "anchorHash",
        "outputs": [],
        "payable": False,
        "stateMutability": "nonpayable",
        "type": "function",
    },
    {
        "constant": True,
        "inputs": [{"name": "_hash", "type": "bytes32"}],
        "name": "anchoredHashes",
        "outputs": [{"name": "", "type": "uint256"}],
        "payable": False,
        "stateMutability": "view",
        "type": "function",
    }
]

def anclar_hash_blockchain(hash_sha256: str) -> dict:
    """
    Registra el hash SHA-256 de un certificado en la blockchain de Polygon.
    Si POLYGON_RPC_URL es 'MOCK', simula el anclaje criptográfico.
    Retorna: {
        'tx_hash': str,
        'status': 'ANCLADO' | 'FALLIDO',
        'network': str,
        'fecha_anclaje': datetime
    }
    """
    network = "MOCK" if settings.POLYGON_RPC_URL == "MOCK" else "POLYGON_AMOY"
    
    if settings.POLYGON_RPC_URL == "MOCK":
        logger.info("Modo Blockchain MOCK activo. Simulando anclaje para hash: %s", hash_sha256)
        mock_tx = "0x" + secrets.token_hex(32)
        return {
            "tx_hash": mock_tx,
            "status": "ANCLADO",
            "network": "MOCK",
            "fecha_anclaje": datetime.now(timezone.utc)
        }

    try:
        from web3 import Web3
        
        w3 = Web3(Web3.HTTPProvider(settings.POLYGON_RPC_URL))
        if not w3.is_connected():
            raise Exception("No se pudo establecer conexión con el proveedor RPC de Polygon.")

        if not settings.POLYGON_PRIVATE_KEY or not settings.POLYGON_CONTRACT_ADDRESS:
            raise Exception("POLYGON_PRIVATE_KEY o POLYGON_CONTRACT_ADDRESS no están configurados.")

        # Obtener cuenta desde la llave privada
        account = w3.eth.account.from_key(settings.POLYGON_PRIVATE_KEY)
        contract = w3.eth.contract(address=settings.POLYGON_CONTRACT_ADDRESS, abi=MINIMAL_ABI)

        # Convertir hash hex a bytes32
        hash_bytes = bytes.fromhex(hash_sha256)

        # Construir transacción
        nonce = w3.eth.get_transaction_count(account.address)
        gas_estimate = contract.functions.anchorHash(hash_bytes).estimate_gas({"from": account.address})
        
        tx = contract.functions.anchorHash(hash_bytes).build_transaction({
            "chainId": w3.eth.chain_id,
            "gas": int(gas_estimate * 1.2),
            "maxFeePerGas": w3.to_wei(30, "gwei"),
            "maxPriorityFeePerGas": w3.to_wei(1.5, "gwei"),
            "nonce": nonce,
        })

        # Firmar transacción
        signed_tx = w3.eth.account.sign_transaction(tx, private_key=settings.POLYGON_PRIVATE_KEY)
        
        # Enviar transacción
        tx_hash = w3.eth.send_raw_transaction(signed_tx.rawTransaction)
        tx_hash_hex = w3.to_hex(tx_hash)
        logger.info("Transacción de anclaje enviada a Polygon: %s", tx_hash_hex)

        # Esperar recibo (receipt)
        receipt = w3.eth.wait_for_transaction_receipt(tx_hash, timeout=60)
        
        if receipt["status"] == 1:
            logger.info("Transacción confirmada en el bloque %d", receipt["blockNumber"])
            # Obtener timestamp del bloque
            block = w3.eth.get_block(receipt["blockNumber"])
            block_time = datetime.fromtimestamp(block["timestamp"], tz=timezone.utc)
            return {
                "tx_hash": tx_hash_hex,
                "status": "ANCLADO",
                "network": network,
                "fecha_anclaje": block_time
            }
        else:
            raise Exception("La transacción falló al ejecutarse en el contrato.")

    except Exception as e:
        logger.error("Error al anclar hash en blockchain: %s", str(e))
        return {
            "tx_hash": None,
            "status": "FALLIDO",
            "network": network,
            "fecha_anclaje": None
        }

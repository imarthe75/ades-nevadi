# 🔐 SCRIPTS DE MIGRACIÓN — PII Encryption

**Tipo**: Database migration + Application changes  
**Fecha**: Semana 3-4  
**Esfuerzo**: 8 horas  
**Riesgo**: MEDIO (afecta datos)  

---

## 📋 PLAN DE MIGRACIÓN

```
FASE 1: Preparación (1 día)
├─ Generar clave de encriptación
├─ Guardar en Vault
├─ Backup completo de BD
└─ Test en staging

FASE 2: Ejecución (1 día)
├─ Agregar columnas _encrypted
├─ Ejecutar script de migración
├─ Validar integridad
└─ Rollback test

FASE 3: Deploy (1 día)
├─ Deploy código con encriptación
├─ Ejecutar migración en producción
├─ Monitorear logs
└─ Desactivar columnas antiguas (después 30 días)
```

---

## 1️⃣ GENERAR CLAVE DE ENCRIPTACIÓN

**Ejecutar UNA SOLA VEZ:**

```bash
#!/bin/bash
# scripts/generate_encryption_key.sh

# Generar clave Fernet (256 bits)
ENCRYPTION_KEY=$(python3 -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())")

echo "Generated encryption key:"
echo $ENCRYPTION_KEY

# Guardar en Vault (NO en git, NO en .env)
vault kv put secret/ades DATABASE_ENCRYPTION_KEY="$ENCRYPTION_KEY"

echo "✅ Key guardada en Vault: secret/ades/DATABASE_ENCRYPTION_KEY"
echo "⚠️  GUARDAR EN LUGAR SEGURO (credenciales de respaldo)"

# Output example:
# gAAAAABlrk4w... (45 caracteres)
```

**Almacenar también en lugar seguro (respaldo):**

```
1Password / BitWarden / Hardware security module
Location: ADES/Encryption/PII_Encryption_Key_2026
Value: [clave generada]
Created: 2026-06-20
```

---

## 2️⃣ ACTUALIZAR MODELOS

**Archivo**: `/backend/app/models/personas.py`

```python
from cryptography.fernet import Fernet
from app.core.config import settings
import logging

log = logging.getLogger(__name__)

# Cipher singleton (reutilizable)
_CIPHER = None

def get_cipher() -> Fernet:
    global _CIPHER
    if _CIPHER is None:
        key = settings.DATABASE_ENCRYPTION_KEY.encode()
        _CIPHER = Fernet(key)
    return _CIPHER

def encrypt_field(value: str) -> str:
    """Encriptar un campo sensible"""
    if not value:
        return None
    cipher = get_cipher()
    return cipher.encrypt(value.encode()).decode()

def decrypt_field(value: str) -> str:
    """Desencriptar un campo sensible"""
    if not value:
        return None
    try:
        cipher = get_cipher()
        return cipher.decrypt(value.encode()).decode()
    except Exception as e:
        log.error(f"Decryption error: {e}")
        raise ValueError("Error desencriptando dato")

# En la clase Usuario
class Usuario(Base):
    __tablename__ = "ades_usuarios"
    
    id = Column(UUID, primary_key=True, default=uuid7)
    
    # Campos sensibles - almacenar encriptados
    _email_encrypted = Column("email_institucional", String)
    _telefono_encrypted = Column("telefono", String)
    
    @property
    def email_institucional(self) -> str:
        """Descencriptar email al acceder"""
        if self._email_encrypted:
            return decrypt_field(self._email_encrypted)
        return None
    
    @email_institucional.setter
    def email_institucional(self, value: str):
        """Encriptar email al asignar"""
        if value:
            self._email_encrypted = encrypt_field(value)
        else:
            self._email_encrypted = None
    
    @property
    def telefono(self) -> str:
        """Desencriptar teléfono"""
        if self._telefono_encrypted:
            return decrypt_field(self._telefono_encrypted)
        return None
    
    @telefono.setter
    def telefono(self, value: str):
        """Encriptar teléfono"""
        if value:
            self._telefono_encrypted = encrypt_field(value)
        else:
            self._telefono_encrypted = None

# En la clase Persona
class Persona(Base):
    __tablename__ = "ades_personas"
    
    id = Column(UUID, primary_key=True, default=uuid7)
    
    # Sensitive fields
    _email_personal_encrypted = Column("email_personal", String)
    _telefono_encrypted = Column("telefono", String)
    _curp_encrypted = Column("curp", String)
    _rfc_encrypted = Column("rfc", String)
    _domicilio_encrypted = Column("domicilio", String)
    
    @property
    def email_personal(self) -> str:
        if self._email_personal_encrypted:
            return decrypt_field(self._email_personal_encrypted)
        return None
    
    @email_personal.setter
    def email_personal(self, value: str):
        if value:
            self._email_personal_encrypted = encrypt_field(value)
        else:
            self._email_personal_encrypted = None
    
    @property
    def telefono(self) -> str:
        if self._telefono_encrypted:
            return decrypt_field(self._telefono_encrypted)
        return None
    
    @telefono.setter
    def telefono(self, value: str):
        if value:
            self._telefono_encrypted = encrypt_field(value)
        else:
            self._telefono_encrypted = None
    
    @property
    def curp(self) -> str:
        if self._curp_encrypted:
            return decrypt_field(self._curp_encrypted)
        return None
    
    @curp.setter
    def curp(self, value: str):
        if value:
            self._curp_encrypted = encrypt_field(value)
        else:
            self._curp_encrypted = None
    
    @property
    def rfc(self) -> str:
        if self._rfc_encrypted:
            return decrypt_field(self._rfc_encrypted)
        return None
    
    @rfc.setter
    def rfc(self, value: str):
        if value:
            self._rfc_encrypted = encrypt_field(value)
        else:
            self._rfc_encrypted = None
    
    @property
    def domicilio(self) -> str:
        if self._domicilio_encrypted:
            return decrypt_field(self._domicilio_encrypted)
        return None
    
    @domicilio.setter
    def domicilio(self, value: str):
        if value:
            self._domicilio_encrypted = encrypt_field(value)
        else:
            self._domicilio_encrypted = None
```

---

## 3️⃣ MIGRACIÓN DE BASE DE DATOS

**Archivo**: `/db/migrations/202406_encrypt_pii.sql`

```sql
-- Migration: Encrypt PII fields
-- Date: 2026-06-20
-- Tables affected: ades_usuarios, ades_personas, ades_estudiantes_padres

BEGIN TRANSACTION;

-- ============================================
-- 1. BACKUP (si falla, rollback automático)
-- ============================================

-- Crear tabla backup (temporal, deletear después 30 días)
CREATE TABLE IF NOT EXISTS ades_pii_encryption_backup_20260620 AS
SELECT 
    'ades_usuarios' as source_table,
    id,
    email_institucional as pii_value,
    'email' as field_type,
    NOW() as backup_date
FROM ades_usuarios
WHERE email_institucional IS NOT NULL

UNION ALL

SELECT 
    'ades_personas',
    id,
    email_personal,
    'email',
    NOW()
FROM ades_personas
WHERE email_personal IS NOT NULL

UNION ALL

SELECT 
    'ades_personas',
    id,
    telefono,
    'phone',
    NOW()
FROM ades_personas
WHERE telefono IS NOT NULL

UNION ALL

SELECT 
    'ades_personas',
    id,
    curp,
    'curp',
    NOW()
FROM ades_personas
WHERE curp IS NOT NULL;

-- ============================================
-- 2. AGREGAR COLUMNAS _encrypted (para rollback fácil)
-- ============================================

-- ades_usuarios
ALTER TABLE ades_usuarios
ADD COLUMN IF NOT EXISTS email_institucional_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS telefono_encrypted VARCHAR;

-- ades_personas
ALTER TABLE ades_personas
ADD COLUMN IF NOT EXISTS email_personal_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS telefono_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS curp_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS rfc_encrypted VARCHAR,
ADD COLUMN IF NOT EXISTS domicilio_encrypted VARCHAR;

-- ============================================
-- 3. MARCAR PARA ENCRIPTACIÓN (en aplicación)
-- ============================================

-- La aplicación Py hará la encriptación con Fernet
-- Esto es necesario porque PostgreSQL no tiene Fernet nativo

-- Columna de control: tracking de migración
ALTER TABLE ades_usuarios
ADD COLUMN IF NOT EXISTS pii_encryption_status VARCHAR DEFAULT 'pending';

ALTER TABLE ades_personas
ADD COLUMN IF NOT EXISTS pii_encryption_status VARCHAR DEFAULT 'pending';

-- ============================================
-- 4. INDICES PARA BÚSQUEDA SEGURA (futura)
-- ============================================

-- Para búsquedas: crear índices de hashes en lugar de plaintext
ALTER TABLE ades_usuarios
ADD COLUMN IF NOT EXISTS email_institucional_hash VARCHAR;

ALTER TABLE ades_personas
ADD COLUMN IF NOT EXISTS email_personal_hash VARCHAR;

-- No crear índices aún (la aplicación hará hash+index)

COMMIT;
```

---

## 4️⃣ SCRIPT DE MIGRACIÓN (Python)

**Archivo**: `/backend/app/worker/tasks/encrypt_pii.py`

```python
"""
Tarea de Celery para encriptar PII existente.

Ejecutar: celery -A app.worker.celery_app call app.worker.tasks.encrypt_pii.encrypt_all_pii
"""

import logging
from uuid import UUID
from sqlalchemy import select, update, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.personas import Usuario, Persona, encrypt_field
from app.core.database import AsyncSessionLocal
from app.core.config import settings

log = logging.getLogger(__name__)

async def encrypt_pii_batch(
    session: AsyncSession,
    table_name: str,
    id_column: str,
    fields: dict,  # {attr_name: encrypted_column_name}
    batch_size: int = 100,
) -> dict:
    """
    Encriptar campos PII en lotes.
    
    Args:
        session: AsyncSession
        table_name: Nombre de tabla
        id_column: Columna ID (UUID)
        fields: {email: email_encrypted, ...}
        batch_size: Procesar en lotes de N registros
    
    Returns:
        {processed: int, errors: int, skipped: int}
    """
    
    result = {
        "processed": 0,
        "errors": 0,
        "skipped": 0,
    }
    
    # Obtener registros sin encriptación
    stmt = text(f"""
        SELECT id, {','.join(fields.keys())}
        FROM {table_name}
        WHERE pii_encryption_status = 'pending'
        LIMIT :batch_size
    """)
    
    while True:
        rows = await session.execute(stmt, {"batch_size": batch_size})
        records = rows.fetchall()
        
        if not records:
            break  # Done
        
        for record in records:
            try:
                record_id = record[0]
                updates = {}
                
                # Encriptar cada campo
                for i, (field_name, encrypted_column) in enumerate(fields.items(), 1):
                    value = record[i]
                    if value:
                        try:
                            encrypted = encrypt_field(value)
                            updates[encrypted_column] = encrypted
                        except Exception as e:
                            log.error(f"Error encrypting {field_name} for {record_id}: {e}")
                            result["errors"] += 1
                            continue
                
                if not updates:
                    result["skipped"] += 1
                    continue
                
                # Actualizar registro
                update_stmt = update(
                    text(f"SELECT * FROM {table_name} WHERE id = '{record_id}'")
                ).values(**updates)
                
                await session.execute(
                    text(f"""
                        UPDATE {table_name}
                        SET {','.join([f'{k}={v}' for k, v in updates.items()])},
                            pii_encryption_status = 'completed'
                        WHERE id = :id
                    """),
                    {**updates, "id": str(record_id)}
                )
                
                result["processed"] += 1
                
            except Exception as e:
                log.error(f"Error processing record {record[0]}: {e}")
                result["errors"] += 1
        
        await session.commit()
    
    return result

async def encrypt_all_pii():
    """Encriptar todos los campos PII pendientes."""
    
    log.info("Iniciando encriptación de PII...")
    
    async with AsyncSessionLocal() as session:
        
        # 1. Encriptar ades_usuarios
        log.info("Encriptando ades_usuarios...")
        usuarios_result = await encrypt_pii_batch(
            session,
            "ades_usuarios",
            "id",
            {
                "email_institucional": "email_institucional_encrypted",
                "telefono": "telefono_encrypted",
            }
        )
        log.info(f"usuarios: {usuarios_result}")
        
        # 2. Encriptar ades_personas
        log.info("Encriptando ades_personas...")
        personas_result = await encrypt_pii_batch(
            session,
            "ades_personas",
            "id",
            {
                "email_personal": "email_personal_encrypted",
                "telefono": "telefono_encrypted",
                "curp": "curp_encrypted",
                "rfc": "rfc_encrypted",
                "domicilio": "domicilio_encrypted",
            }
        )
        log.info(f"personas: {personas_result}")
        
        # 3. Generar hashes para búsqueda
        log.info("Generando hashes para búsqueda...")
        from hashlib import sha256
        
        await session.execute(text("""
            UPDATE ades_usuarios
            SET email_institucional_hash = 
                encode(digest(email_institucional, 'sha256'), 'hex')
            WHERE email_institucional IS NOT NULL
        """))
        
        await session.execute(text("""
            UPDATE ades_personas
            SET email_personal_hash = 
                encode(digest(email_personal, 'sha256'), 'hex')
            WHERE email_personal IS NOT NULL
        """))
        
        await session.commit()
        
        total_processed = (
            usuarios_result["processed"] + personas_result["processed"]
        )
        
        log.info(f"✅ Encriptación completada: {total_processed} registros")
        
        return {
            "usuarios": usuarios_result,
            "personas": personas_result,
            "total_processed": total_processed,
        }

# Ejecutar desde CLI:
# python -c "import asyncio; from app.worker.tasks.encrypt_pii import encrypt_all_pii; asyncio.run(encrypt_all_pii())"
```

---

## 5️⃣ ROLLBACK SCRIPT (Por si acaso)

**Archivo**: `/db/rollback_encrypt_pii.sql`

```sql
-- ROLLBACK SCRIPT - Ejecutar si falla la migración

BEGIN TRANSACTION;

-- Restaurar desde backup
UPDATE ades_usuarios
SET email_institucional = (
    SELECT pii_value 
    FROM ades_pii_encryption_backup_20260620
    WHERE source_table = 'ades_usuarios'
      AND id = ades_usuarios.id
      AND field_type = 'email'
)
WHERE email_institucional_encrypted IS NOT NULL;

UPDATE ades_personas
SET email_personal = (
    SELECT pii_value 
    FROM ades_pii_encryption_backup_20260620
    WHERE source_table = 'ades_personas'
      AND id = ades_personas.id
      AND field_type = 'email'
)
WHERE email_personal_encrypted IS NOT NULL;

-- Eliminar columnas _encrypted
ALTER TABLE ades_usuarios
DROP COLUMN IF EXISTS email_institucional_encrypted,
DROP COLUMN IF EXISTS telefono_encrypted;

ALTER TABLE ades_personas
DROP COLUMN IF EXISTS email_personal_encrypted,
DROP COLUMN IF EXISTS telefono_encrypted,
DROP COLUMN IF EXISTS curp_encrypted,
DROP COLUMN IF EXISTS rfc_encrypted,
DROP COLUMN IF EXISTS domicilio_encrypted;

-- Eliminar tabla backup (después de validar rollback)
DROP TABLE IF EXISTS ades_pii_encryption_backup_20260620;

COMMIT;
```

---

## 📋 PLAN DE EJECUCIÓN

### Día 1: Staging
```bash
# 1. Generar clave
./scripts/generate_encryption_key.sh

# 2. Backup staging DB
pg_dump ades_staging > /backups/pre_encrypt.sql

# 3. Aplicar migración SQL
psql ades_staging < db/migrations/202406_encrypt_pii.sql

# 4. Ejecutar encriptación
python -c "
import asyncio
from app.worker.tasks.encrypt_pii import encrypt_all_pii
asyncio.run(encrypt_all_pii())
"

# 5. Validar
# Conectar a DB y verificar:
# - SELECT COUNT(*) FROM ades_usuarios 
#   WHERE pii_encryption_status = 'completed';
# - Datos encriptados vs plaintext (comparar)

# 6. Test rollback
psql ades_staging < db/rollback_encrypt_pii.sql
# Verificar restauración de datos
```

### Día 2-3: Producción
```bash
# ⚠️ CREAR BACKUP COMPLETO ANTES

pg_dump ades_prod > /secure-backups/pre_encrypt_$(date +%Y%m%d).sql
du -h /secure-backups/pre_encrypt_*.sql  # Verificar tamaño

# Aplicar migración
psql ades_prod < db/migrations/202406_encrypt_pii.sql

# Encriptar (puede tardar 30-60 min con millones de registros)
# Ejecutar en baja carga horaria (2-4 AM)
python scripts/encrypt_pii_cli.py --workers 4

# Monitorear
watch -n 5 "psql ades_prod -c \"SELECT COUNT(*) as pending, COUNT(CASE WHEN pii_encryption_status='completed' THEN 1 END) as completed FROM ades_personas;\""

# Validación final
psql ades_prod -c "SELECT COUNT(*) FROM ades_personas WHERE pii_encryption_status = 'pending';"
# Esperado: 0

# Cleanup (después de 30 días)
psql ades_prod -c "DROP TABLE ades_pii_encryption_backup_20260620;"
```

---

## ✅ VALIDACIÓN

```python
# Test que datos están encriptados

async def test_pii_encryption():
    from app.models.personas import Persona
    
    # Obtener de BD directamente
    raw = await db.execute(text("SELECT email_personal FROM ades_personas LIMIT 1"))
    value = raw.scalar()
    
    # Si está encriptado, verá algo como: gAAAAABlrk4w...
    assert value.startswith("gAAAAA"), f"Data not encrypted: {value[:20]}"
    
    # Via ORM, debe desencriptarse automáticamente
    persona = await db.get(Persona, persona_id)
    assert persona.email_personal == "test@example.com"  # Desencriptado
```

---

## ⚠️ CONSIDERACIONES

1. **Performance**: Encriptación/desencriptación ~1-2ms por campo
2. **Búsqueda**: Post-decrypt filters (no índices en plaintext)
3. **Backup**: Backups ahora contienen datos encriptados (más seguro)
4. **Keys**: Guardar clave en múltiples lugares (Vault, hardware security module)
5. **Rotación**: Plan de rotación de claves cada 12 meses

---

**Status**: Ready to implement  
**Riesgo**: MEDIO (validar antes de producción)  
**Rollback**: ~1 hora si es necesario

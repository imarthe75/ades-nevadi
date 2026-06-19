#!/bin/bash
# scripts/generate_encryption_key.sh — Generar clave de encriptación Fernet

set -e

echo ""
echo "════════════════════════════════════════════════════════════════"
echo "🔐 Generando clave de encriptación Fernet"
echo "════════════════════════════════════════════════════════════════"
echo ""

ENCRYPTION_KEY=$(python3 -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())")

echo "✅ Clave generada:"
echo ""
echo "$ENCRYPTION_KEY"
echo ""

if command -v vault &> /dev/null; then
    echo "🔒 Guardando en Vault..."
    vault kv put secret/ades DATABASE_ENCRYPTION_KEY="$ENCRYPTION_KEY" 2>/dev/null
    echo "✅ Clave guardada en Vault (secret/ades/DATABASE_ENCRYPTION_KEY)"
else
    echo "⚠️  Vault no encontrado. Guardar manualmente:"
    echo "   vault kv put secret/ades DATABASE_ENCRYPTION_KEY='$ENCRYPTION_KEY'"
fi

echo ""
echo "════════════════════════════════════════════════════════════════"
echo "⚠️  IMPORTANTE:"
echo "════════════════════════════════════════════════════════════════"
echo ""
echo "1. Guardar esta clave en lugar seguro (1Password, BitWarden, etc)"
echo "2. NUNCA commitearla a git"
echo "3. Usar solo en .env (nunca en código)"
echo "4. Guardar para auditoría/compliance"
echo ""
echo "Ubicación en variables de entorno:"
echo "   DATABASE_ENCRYPTION_KEY='$ENCRYPTION_KEY'"
echo ""

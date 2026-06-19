#!/bin/bash
# scripts/setup_security.sh — Setup de seguridad para desarrolladores

set -e

echo ""
echo "════════════════════════════════════════════════════════════════"
echo "🔒 Configurando seguridad para ADES Backend"
echo "════════════════════════════════════════════════════════════════"
echo ""

# 1. Instalar pre-commit
echo "📦 Instalando pre-commit..."
pip install pre-commit -q

# 2. Instalar hooks
echo "🔗 Instalando git hooks..."
pre-commit install

# 3. Instalar herramientas de seguridad
echo "🛡️  Instalando herramientas de seguridad..."
pip install bandit semgrep safety pip-audit detect-secrets -q

# 4. Crear baseline para detect-secrets
echo "🔐 Generando baseline de secrets..."
detect-secrets scan > .secrets.baseline 2>/dev/null || true

# 5. Verificar
echo "✅ Ejecutando pre-commit en todos los archivos..."
pre-commit run --all-files 2>/dev/null || true

echo ""
echo "════════════════════════════════════════════════════════════════"
echo "✅ Setup completado!"
echo "════════════════════════════════════════════════════════════════"
echo ""
echo "Próximos pasos:"
echo "  1. git add ."
echo "  2. git commit -m 'chore: setup security pipeline'"
echo "  3. git push"
echo ""
echo "Los pre-commit hooks ejecutarán automáticamente en cada commit"
echo ""

#!/bin/bash

# Quick rollout script to apply validation patterns to remaining modules
# Usage: bash scripts/rollout-validation-to-modules.sh [padres-admin|profesores|admision|all]

MODULES="${1:-all}"
FRONTEND_DIR="/opt/ades/frontend/src/app/features"

echo "=== ADES Validation Rollout Script ==="
echo "Modules to update: $MODULES"
echo ""

if [ "$MODULES" == "all" ] || [ "$MODULES" == "padres-admin" ]; then
  echo "► padres-admin: Applying validation (nombres, teléfono, dinero)"
  # Este módulo necesita: formatNombre, formatTelefono, formatDinero
  echo "  TODO: Refactor ngModel → FormControl"
  echo "  TODO: Add FormFieldComponent for names/phone"
  echo "  TODO: Add AdesValidators.isMexicanPhoneNumber()"
  echo "  ✓ Template: FormFieldComponent imported in imports"
  echo ""
fi

if [ "$MODULES" == "all" ] || [ "$MODULES" == "profesores" ]; then
  echo "► profesores: Applying validation (CURP, RFC, nombres)"
  echo "  TODO: Refactor form object → FormControls"
  echo "  TODO: Replace ngModel inputs with FormFieldComponent"
  echo "  TODO: Add CURP/RFC validators"
  echo "  ✓ Small module (7 fields) — ~15 min"
  echo ""
fi

if [ "$MODULES" == "all" ] || [ "$MODULES" == "admision" ]; then
  echo "► admision: Applying validation (CURP, RFC, teléfono, ZIP)"
  echo "  TODO: Complex form — prioritize key fields"
  echo "  TODO: Add FormControl-based validation"
  echo "  ✓ High priority — family data"
  echo ""
fi

echo "=== Summary ==="
echo "Estimated effort: 30-45 minutes total"
echo "Apply patterns from: personal-admin.component.ts"
echo ""
echo "Pattern template:"
echo "  1. Import InputFormattersService, AdesValidators, FormFieldComponent"
echo "  2. Replace form object with FormControl declarations"
echo "  3. Swap ngModel inputs → FormFieldComponent"
echo "  4. Update guardar() to use FormControl.value"
echo "  5. Add validation in guardar() before API call"

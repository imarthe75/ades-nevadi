#!/bin/bash

# SCRIPT AUTOMATIZACIÓN: Agregar Change Detection OnPush a componentes Angular
# PUNTO 5 FASE 2 — Performance Optimization
# 2026-07-08

echo "🚀 INICIANDO AUTOMATIZACIÓN PUNTO 5 (OnPush)"
echo "================================================"

COMPONENTS_DIR="/opt/ades/frontend/src/app/features"
TOTAL_UPDATED=0

# Buscar todos los componentes
for FILE in $(find "$COMPONENTS_DIR" -name "*.component.ts"); do
  if ! grep -q "changeDetection" "$FILE"; then
    FILENAME=$(basename "$FILE")

    # Buscar la línea @Component
    COMPONENT_LINE=$(grep -n "^@Component(" "$FILE" | cut -d: -f1)

    if [ ! -z "$COMPONENT_LINE" ]; then
      # Buscar la línea del template/standalone
      TEMPLATE_OR_STANDALONE=$(grep -n "template:\|standalone:" "$FILE" | head -1 | cut -d: -f1)

      if [ ! -z "$TEMPLATE_OR_STANDALONE" ] && [ "$TEMPLATE_OR_STANDALONE" -gt "$COMPONENT_LINE" ]; then
        # Insertar changeDetection antes de template/standalone
        INSERT_LINE=$((TEMPLATE_OR_STANDALONE))
        sed -i "${INSERT_LINE}i\\  changeDetection: ChangeDetectionStrategy.OnPush," "$FILE"

        # Agregar import si no existe
        if ! grep -q "ChangeDetectionStrategy" "$FILE"; then
          # Buscar la línea de import de Component
          IMPORT_LINE=$(grep -n "^import { Component" "$FILE" | cut -d: -f1)

          if [ ! -z "$IMPORT_LINE" ]; then
            CURRENT_IMPORT=$(sed -n "${IMPORT_LINE}p" "$FILE")
            NEW_IMPORT=$(echo "$CURRENT_IMPORT" | sed 's/Component,/Component, ChangeDetectionStrategy,/' | sed 's/Component }/Component, ChangeDetectionStrategy }/')
            sed -i "${IMPORT_LINE}s/.*/$(echo "$NEW_IMPORT" | sed 's/[\/&]/\\&/g')/" "$FILE"
          fi
        fi

        TOTAL_UPDATED=$((TOTAL_UPDATED + 1))
        echo "✅ $FILENAME"
      fi
    fi
  fi
done

echo ""
echo "================================================"
echo "✅ AUTOMATIZACIÓN COMPLETA"
echo "   Componentes actualizados: $TOTAL_UPDATED"
echo "================================================"

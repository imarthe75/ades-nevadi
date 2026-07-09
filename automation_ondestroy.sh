#!/bin/bash

# SCRIPT AUTOMATIZACIÓN: Agregar OnDestroy a todos los componentes Angular
# PUNTO 6 FASE 1 — Memory Leaks Prevention
# 2026-07-08

echo "🚀 INICIANDO AUTOMATIZACIÓN PUNTO 6 (OnDestroy)"
echo "================================================"

COMPONENTS_DIR="/opt/ades/frontend/src/app/features"
TOTAL_UPDATED=0
SKIPPED=0

# Buscar todos los componentes que tengan .subscribe() pero NO OnDestroy
for FILE in $(find "$COMPONENTS_DIR" -name "*.component.ts"); do
  if grep -q "subscribe" "$FILE" && ! grep -q "OnDestroy" "$FILE"; then
    FILENAME=$(basename "$FILE")
    echo "📝 Actualizando: $FILENAME"

    # 1. Agregar imports si no existen
    if ! grep -q "OnDestroy" "$FILE"; then
      # Buscar la línea de import existente y actualizar
      IMPORT_LINE=$(grep -n "^import { Component" "$FILE" | cut -d: -f1)
      if [ ! -z "$IMPORT_LINE" ]; then
        # Extraer la línea actual
        CURRENT_IMPORT=$(sed -n "${IMPORT_LINE}p" "$FILE")

        # Agregar OnDestroy si no está
        if ! echo "$CURRENT_IMPORT" | grep -q "OnDestroy"; then
          NEW_IMPORT=$(echo "$CURRENT_IMPORT" | sed 's/Component, /Component, OnDestroy, /' | sed 's/Component }/Component, OnDestroy }/')
          sed -i "${IMPORT_LINE}s/.*/$(echo "$NEW_IMPORT" | sed 's/[\/&]/\\&/g')/" "$FILE"
        fi

        # Agregar imports de RxJS si no existen
        if ! grep -q "Subject" "$FILE"; then
          # Insertar después de los imports de @angular
          LAST_ANGULAR_IMPORT=$(grep -n "^import.*@angular" "$FILE" | tail -1 | cut -d: -f1)
          INSERT_LINE=$((LAST_ANGULAR_IMPORT + 1))
          sed -i "${INSERT_LINE}i import { Subject, takeUntil } from 'rxjs';" "$FILE"
        fi
      fi
    fi

    # 2. Agregar OnDestroy a la clase
    CLASS_LINE=$(grep -n "^export class" "$FILE" | cut -d: -f1)
    if [ ! -z "$CLASS_LINE" ]; then
      CURRENT_CLASS=$(sed -n "${CLASS_LINE}p" "$FILE")

      if ! echo "$CURRENT_CLASS" | grep -q "OnDestroy"; then
        NEW_CLASS=$(echo "$CURRENT_CLASS" | sed 's/ {/ implements OnInit, OnDestroy {/')
        sed -i "${CLASS_LINE}s/.*/$(echo "$NEW_CLASS" | sed 's/[\/&]/\\&/g')/" "$FILE"

        # Agregar destroy$ property después de la clase
        sed -i "${CLASS_LINE}a\\  private destroy\$ = new Subject<void>();" "$FILE"
      fi
    fi

    # 3. Agregar ngOnDestroy al final
    if ! grep -q "ngOnDestroy" "$FILE"; then
      # Insertar antes del cierre final de la clase
      LAST_LINE=$(tail -1 "$FILE")
      if [ "$LAST_LINE" = "}" ]; then
        sed -i '$d' "$FILE"
        echo "" >> "$FILE"
        echo "  ngOnDestroy(): void {" >> "$FILE"
        echo "    this.destroy$.next();" >> "$FILE"
        echo "    this.destroy$.complete();" >> "$FILE"
        echo "  }" >> "$FILE"
        echo "}" >> "$FILE"
      fi
    fi

    TOTAL_UPDATED=$((TOTAL_UPDATED + 1))
    echo "   ✅ Actualizado"
  else
    SKIPPED=$((SKIPPED + 1))
  fi
done

echo ""
echo "================================================"
echo "✅ AUTOMATIZACIÓN COMPLETA"
echo "   Componentes actualizados: $TOTAL_UPDATED"
echo "   Componentes omitidos: $SKIPPED"
echo "   TOTAL: $((TOTAL_UPDATED + SKIPPED))"
echo "================================================"

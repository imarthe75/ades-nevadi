# 🔧 FIX 4: Testing Completo de Modales Laterales + Centrales + Pestañas

## ¿Qué Testea V4?

### 1️⃣ Modales Laterales (Side Panels)
- Detecta paneles derechos/izquierdos
- Ejemplo: "Perfil del Alumno" en módulo Alumnos

### 2️⃣ Modales Centrales
- Detecta diálogos modales en el centro
- Ejemplo: "Nuevo grupo" en módulo Grupos

### 3️⃣ Pestañas Dentro de Modales
- Itera sobre **cada pestaña**
- Testea datos en cada pestaña
- Detección de campos vacíos

### 4️⃣ Inconsistencias
- ⚠️ Campos requeridos vacíos
- ⚠️ Datos no cargados
- ⚠️ Errores en validación

## Flujo de Testing

```
1. Navegar a módulo (ej: /alumnos)
   ↓
2. Buscar botones de edición (lápices)
   ↓
3. Clickear cada botón (máx 3)
   ↓
4. Detectar tipo de modal:
   - ¿Es side panel (lateral)?
   - ¿Es modal central?
   ↓
5. Si tiene pestañas:
   - Clickear cada pestaña
   - Contar campos en cada una
   - Detectar campos vacíos
   ↓
6. Cerrar modal
   ↓
7. Siguiente módulo
```

## Reporte Generado

`captures_summary.json`:

```json
{
  "summary": {
    "total_modules": 5,
    "modules_with_modals": 3,
    "modules_with_side_panels": 2,
    "modules_with_tabbed_modals": 1
  },
  "captures": [
    {
      "module_id": "alumnos",
      "modals_detected": 1,
      "side_panels_detected": 1,
      "central_modals_detected": 0,
      "modals_with_tabs": 1,
      "tab_info": [
        {
          "tabs_found": 4,
          "tabs_tested": ["Personal", "Domicilio", "Académico", "Salud"],
          "fields_per_tab": {
            "Personal": 8,
            "Domicilio": 5,
            "Académico": 3,
            "Salud": 2
          },
          "empty_fields": [
            ["Personal", "field_nombre_completo"],
            ["Domicilio", "field_ciudad"]
          ]
        }
      ]
    }
  ]
}
```

## Interpretación de Resultados

### `modals_detected`
- Cantidad total de modales encontrados en el módulo

### `side_panels_detected`
- Paneles laterales (abiertos con botones de edición)

### `central_modals_detected`
- Diálogos modales en el centro (crear/formularios)

### `modals_with_tabs`
- Modales que tienen pestañas

### `tab_info`
- **tabs_found**: Cuántas pestañas tiene el modal
- **tabs_tested**: Nombres de las pestañas
- **fields_per_tab**: Cantidad de campos en cada pestaña
- **empty_fields**: Campos vacíos encontrados (posible error)

## Características de V4

✅ Autenticación Authentik robusta
✅ Detección de modales laterales (side panels)
✅ Detección de modales centrales
✅ Iteración automática sobre pestañas
✅ Conteo de campos por pestaña
✅ Detección de campos vacíos
✅ Cierre automático de modales
✅ Mejor manejo de errores
✅ Logging detallado

## Cómo Usar

```bash
cd /opt/ades/ades_testing

# Descargar V4
# Copiar:
cp 01_ades_explorer_v4_complete.py 01_ades_explorer_portable.py

# Ejecutar (test rápido con 5 módulos)
python 01_ades_explorer_portable.py

# Resultado en ~10 minutos:
# - Testa módulos + modales + pestañas
# - Genera captures_summary.json con detalle completo
```

## Qué Verás en Consola

```
[INFO] [1/5] alumnos
[INFO]   Navegando a https://ades.setag.mx/alumnos
[INFO]   ✓ Capturado | Modales: 1 (laterales: 1, con 1 pestañas) | 4 inconsistencias

[INFO] [2/5] profesores
[INFO]   Navegando a https://ades.setag.mx/profesores
[INFO]   ✓ Capturado | Modales: 2 (laterales: 2) | ⚠️ 1 inconsistencias

[INFO] [3/5] grupos
[INFO]   Navegando a https://ades.setag.mx/grupos
[INFO]   ✓ Capturado | Modales: 1 (centrales: 1)
```

## Limitaciones Actuales

- Máximo 3 modales por módulo (para no tardar mucho)
- Máximo 5 pestañas por modal
- Solo detecta 1 nivel de pestañas (no anidadas)

Para testing más exhaustivo, se puede aumentar los límites.

## Próximos Pasos

### V5 (Análisis con Claude)
- Script V4 captura datos completos
- Script V2 (Claude QA) analiza:
  - ¿Campos requeridos vacíos?
  - ¿Datos inconsistentes entre pestañas?
  - ¿Errores de validación?
  - ¿Contexto no propagado? (Campus/Plantel/Ciclo)

## Descarga

**01_ades_explorer_v4_complete.py**

---

**V4 está lista. Esta es la versión más completa de testing de modales.** 🚀

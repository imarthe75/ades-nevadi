# ⏱️ Opciones de Ejecución V4 - Todos los Módulos

## Estructura de Módulos en ADES

```
Total: ~60 módulos

FASE 1 (Críticos + Altos): ~25 módulos
  - dashboard, alumnos, profesores, grupos, planes_estudio
  - calificaciones, horarios, disponibilidad_docente
  - reinscripción, cierre_ciclo, etc.

FASE 2 (Medios): ~15 módulos
  - reportes, dashboards_BI, evaluaciones
  - asistencias, kardex, etc.

FASE 3 (Bajos): ~20 módulos
  - administración, settings, etc.
```

## Opciones de Ejecución

### 🟢 OPCIÓN 1: Test Rápido (5 módulos)
```bash
# Editar V4: cambiar última línea a:
await explorer.run(phase=1, limit=5)

# Tiempo: 5-10 minutos
# Uso: Verificar que funciona todo
```

### 🟡 OPCIÓN 2: Test Medio (10 módulos)
```bash
# Editar V4:
await explorer.run(phase=1, limit=10)

# Tiempo: 15-20 minutos
# Uso: Testing rápido de módulos principales
```

### 🔴 OPCIÓN 3: Fase 1 Completa (25+ módulos) ← RECOMENDADO
```bash
# Editar V4:
await explorer.run(phase=1, limit=None)

# Tiempo: 30-40 minutos
# Uso: Testing exhaustivo de críticos/altos
# ES LA OPCIÓN QUE VIENE POR DEFECTO AHORA
```

### 🔵 OPCIÓN 4: Todas las Fases (~60 módulos)
```bash
# Editar V4:
await explorer.run(phase=3, limit=None)

# Tiempo: 90-120 minutos (1.5-2 horas)
# Uso: Testing exhaustivo de TODO
```

## Cuánto Tarda Cada Opción

| Opción | Módulos | Tiempo | Modales/módulo |
|--------|---------|--------|-----------------|
| Test Rápido | 5 | 5-10 min | ~3 |
| Test Medio | 10 | 15-20 min | ~3 |
| **Fase 1** | 25 | 30-40 min | ~3 |
| Todas Fases | 60 | 90-120 min | ~3 |

**Tiempo por módulo**: ~1-2 minutos (navegación + 3 modales × 30 seg cada)

## Cómo Cambiar la Opción

### Opción A: Editar Script Antes de Ejecutar

```bash
cd /opt/ades/ades_testing

# Abrir el script
nano 01_ades_explorer_portable.py

# Buscar línea final (última línea):
await explorer.run(phase=1, limit=None)

# Cambiar según opción deseada:
# limit=5    → 5 módulos
# limit=10   → 10 módulos
# limit=None → todos

# Guardar (Ctrl+O, Enter, Ctrl+X)

# Ejecutar
python 01_ades_explorer_portable.py
```

### Opción B: Crear Script Temporal

```bash
# Test rápido
cp 01_ades_explorer_portable.py test_rapido.py
# Editar test_rapido.py: limit=5
python test_rapido.py

# Test completo
cp 01_ades_explorer_portable.py test_completo.py
# Editar test_completo.py: limit=None
python test_completo.py
```

## Recomendación: FASE 1 COMPLETA

**La versión V4 actual está configurada para FASE 1 (25+ módulos críticos/altos):**

```bash
cd /opt/ades/ades_testing
python 01_ades_explorer_portable.py

# Resultado en 30-40 minutos:
# ✓ 25+ módulos testeados
# ✓ Modales laterales + centrales detectados
# ✓ Pestañas iteradas
# ✓ Campos contados
# ✓ Inconsistencias reportadas
```

## Qué Verás en Tiempo Real

```
[INFO] === EJECUTANDO FASE 1 (ALL) ===

[1/25] dashboard
  Navegando a https://ades.setag.mx/dashboard
  ✓ Capturado | Modales: 0

[2/25] alumnos
  Navegando a https://ades.setag.mx/alumnos
  ✓ Capturado | Modales: 1 (laterales: 1, con 1 pestañas)

[3/25] profesores
  Navegando a https://ades.setag.mx/profesores
  ✓ Capturado | Modales: 2 (laterales: 2)

... (20+ más)

[25/25] últimoModulo
  ✓ Capturado

✓ Resultados guardados en /opt/ades/ades_testing/captures/captures_summary.json
  - Módulos: 25/25
  - Con modales: 18
  - Con side panels: 16
  - Con pestañas: 12
```

## Output Final

Después de 30-40 minutos tendrás:

```
/opt/ades/ades_testing/
├── captures/
│   ├── dashboard.png
│   ├── alumnos.png
│   ├── profesores.png
│   ├── ... (25+ screenshots)
│   └── captures_summary.json ← JSON COMPLETO CON TODOS LOS DATOS
│
├── analysis/
│   └── inconsistencies_report.json ← ANÁLISIS CON CLAUDE
│
└── reports/
    ├── inconsistencies_report.html ← DASHBOARD HTML
    ├── jira_issues.csv ← PARA IMPORTAR A JIRA
    └── REPORTE_RESUMEN.txt
```

## Próximo Paso (Análisis)

Una vez que termina captura (V4):

```bash
# Ejecutar análisis con Claude (Script 2)
python 02_claude_qa_analyzer_portable.py

# Ejecutar generador de reportes (Script 3)
python 03_report_generator_portable.py

# Ver resultados
open reports/inconsistencies_report.html
```

## Estimación de Inconsistencias

Con 25+ módulos testeados:
- **Esperado**: 50-100 inconsistencias totales
- **Críticas**: 10-20
- **Altas**: 15-30
- **Medias**: 15-25
- **Bajas**: 10-25

## ¿Puedo Pausar/Reanudar?

No por ahora. El script va corriendo hasta terminar.

Si quieres pausar:
```bash
Ctrl+C
# Guarda lo que testeó hasta ese momento
```

Si necesitas ejecutar nuevamente:
```bash
# Borra captures previos
rm -rf /opt/ades/ades_testing/captures/

# Ejecuta nuevamente
python 01_ades_explorer_portable.py
```

## Resumen

**V4 ACTUAL**: Testea 25+ módulos Fase 1 en 30-40 minutos
**DESCARGA**: 01_ades_explorer_v4_complete.py (YA ACTUALIZADO)

---

**Listo para ejecutar fase 1 completa.** 🚀

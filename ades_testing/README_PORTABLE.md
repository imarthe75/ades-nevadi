# 🚀 ADES Testing - Versión Portable

Estas versiones portables funcionan desde **cualquier directorio** sin cambios de path.

## ¿Qué Cambió?

Original: Paths hardcodeados a `/home/claude/ades_testing/`  
Portable: Paths relativos al directorio actual

## Cómo Usar

### En tu servidor Ubuntu (/opt/ades/ades_testing)

```bash
# Ya estás en el directorio correcto
cd /opt/ades/ades_testing

# Activar venv (si creaste uno)
source venv/bin/activate

# Cargar variables de entorno
source .env

# EJECUTAR EN ORDEN:

# 1. Captura (15 min)
python 01_ades_explorer_portable.py

# 2. Análisis (10 min)
python 02_claude_qa_analyzer_portable.py

# 3. Reportes (2 min)
python 03_report_generator_portable.py

# 4. Ver resultados
open reports/inconsistencies_report.html
# o en server:
ls reports/
```

## Estructura Generada

```
/opt/ades/ades_testing/
├── .env
├── config_ades_modules.json
├── 01_ades_explorer_portable.py
├── 02_claude_qa_analyzer_portable.py
├── 03_report_generator_portable.py
│
├── captures/                          ← Creada automáticamente
│   ├── dashboard.png
│   ├── alumnos.png
│   ├── ...
│   └── captures_summary.json
│
├── analysis/                          ← Creada automáticamente
│   └── inconsistencies_report.json
│
└── reports/                           ← Creada automáticamente
    ├── inconsistencies_report.html    ← ABRIR AQUÍ
    ├── jira_issues.csv
    └── REPORTE_RESUMEN.txt
```

## Qué Esperar

### Script 1 (Captura)
```
[INFO] Working directory: /opt/ades/ades_testing
[INFO] Output directory: /opt/ades/ades_testing/captures
[INFO] Iniciando Playwright en https://ades.setag.mx
[INFO] Autenticando en ADES...
[INFO] ✓ Autenticación exitosa
[INFO] Testeando 25 módulos (Fase 1)
[1/25] dashboard
  ✓ Capturado: /opt/ades/ades_testing/captures/dashboard.png
[2/25] alumnos
  ✓ Capturado: /opt/ades/ades_testing/captures/alumnos.png
...
✓ Resultados guardados en /opt/ades/ades_testing/captures/captures_summary.json
  - Módulos capturados: 25/25
  - Errores: 0
```

### Script 2 (Análisis)
```
[INFO] Iniciando análisis cognitivo
[INFO] Analizando 25 capturas
[INFO] Batch 1/5
  Llamando Claude API para 5 módulos...
  ✓ Batch: 14 inconsistencias
...
✓ Análisis guardado en /opt/ades/ades_testing/analysis/inconsistencies_report.json
  - Total: 68
  - Críticas: 15
  - Altas: 23
```

### Script 3 (Reportes)
```
[INFO] Generando reportes ejecutivos
✓ HTML generado: /opt/ades/ades_testing/reports/inconsistencies_report.html
✓ CSV Jira generado: /opt/ades/ades_testing/reports/jira_issues.csv
✓ Resumen generado: /opt/ades/ades_testing/reports/REPORTE_RESUMEN.txt

✓ Reportes en /opt/ades/ades_testing/reports
```

## Diferencia Entre Versiones

| Aspecto | Original | Portable |
|---------|----------|----------|
| Path base | `/home/claude/ades_testing/` | Script dir actual |
| Funciona desde | Claude Code | Cualquier servidor/directorio |
| Cambios necesarios | 0 | 0 |
| Portabilidad | Baja | ✅ Alta |

## ✅ Checklist

- [ ] Estás en `/opt/ades/ades_testing/` (o tu directorio de work)
- [ ] Virtual env activado: `source venv/bin/activate`
- [ ] Variables cargadas: `source .env`
- [ ] Config + scripts portables presentes
- [ ] Permisos de escritura en directorio actual

## Próximo: Ejecutar

```bash
python 01_ades_explorer_portable.py
```

¡Ready! 🚀

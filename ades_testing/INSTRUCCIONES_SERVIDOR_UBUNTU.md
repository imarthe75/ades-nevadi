# 🚀 ADES Testing Portable - Para Servidor Ubuntu

Tu error fue que los scripts originales tenían paths hardcodeados a `/home/claude/`.

**Solución**: Usa las versiones **PORTABLE** que funcionan desde cualquier directorio.

---

## Tus Scripts Nuevos (Portable)

```
01_ades_explorer_portable.py       ← Usa este en lugar del original
02_claude_qa_analyzer_portable.py  ← Usa este en lugar del original  
03_report_generator_portable.py    ← Usa este en lugar del original
README_PORTABLE.md                 ← Instrucciones detalladas
```

---

## Ejecutar Ahora (En Tu Servidor)

```bash
# 1. Estás aquí
cd /opt/ades/ades_testing

# 2. Activar venv (si lo creaste)
source venv/bin/activate

# 3. Cargar variables
source .env

# 4. EJECUTAR EN ORDEN (copiar y pegar)

# Script 1: Captura (15 minutos)
python 01_ades_explorer_portable.py

# Esperar a que termine...
# Si ves ✓ Resultados guardados → OK

# Script 2: Análisis (10 minutos)  
python 02_claude_qa_analyzer_portable.py

# Script 3: Reportes (2 minutos)
python 03_report_generator_portable.py

# 5. Ver resultados
ls -la reports/
cat reports/REPORTE_RESUMEN.txt
```

---

## Qué Genera Automáticamente

Después de ejecutar, tendrás:

```
/opt/ades/ades_testing/
├── captures/
│   ├── dashboard.png, alumnos.png, ...
│   └── captures_summary.json
│
├── analysis/
│   └── inconsistencies_report.json
│
└── reports/
    ├── inconsistencies_report.html  ← PRINCIPAL
    ├── jira_issues.csv              ← Para Jira
    └── REPORTE_RESUMEN.txt          ← Resumen
```

---

## Diferencia vs Originales

**Original**: Paths hardcodeados `/home/claude/ades_testing/`  
**Portable**: Detecta directorio automáticamente (funciona en cualquier lado)

---

## Si Hay Errores

### Error: "No module named 'playwright'"
```bash
source venv/bin/activate
pip install playwright python-dotenv anthropic --upgrade
python -m playwright install chromium
```

### Error: "ADES_PASSWORD not found"
```bash
cat > .env << EOF
ADES_USER=admin
ADES_PASSWORD=***REDACTED-ROTATED***
ANTHROPIC_API_KEY=sk-ant-...
EOF

source .env
```

### Error: "Connection refused"
```bash
# Verificar que ADES está accesible
curl -I https://ades.setag.mx/login

# Si no → problema de red/firewall
# Si sí → problema en script (reportar)
```

---

## Expected Output (Cada Script)

### Script 1: Captura
```
[INFO] Working directory: /opt/ades/ades_testing
[INFO] Output directory: /opt/ades/ades_testing/captures
[INFO] Iniciando Playwright en https://ades.setag.mx
[INFO] Autenticando en ADES...
[INFO] ✓ Autenticación exitosa
[INFO] Testeando 25 módulos (Fase 1)
[1/25] dashboard ✓ Capturado
[2/25] alumnos ✓ Capturado
...
✓ Resultados guardados en /opt/ades/ades_testing/captures/captures_summary.json
  - Módulos capturados: 25/25
  - Errores: 0
```

### Script 2: Análisis
```
[INFO] Iniciando análisis cognitivo
[INFO] Analizando 25 capturas
[INFO] Batch 1/5
  Llamando Claude API...
  ✓ Batch: 14 inconsistencias
...
✓ Análisis guardado en .../analysis/inconsistencies_report.json
  - Total: 68 inconsistencias
  - Críticas: 15
  - Altas: 23
```

### Script 3: Reportes
```
[INFO] Generando reportes ejecutivos
✓ HTML generado: .../reports/inconsistencies_report.html
✓ CSV Jira generado: .../reports/jira_issues.csv
✓ Resumen generado: .../reports/REPORTE_RESUMEN.txt

✓ Reportes en /opt/ades/ades_testing/reports
```

---

## Ver Resultados

### En Servidor (SSH)
```bash
# Ver resumen
cat reports/REPORTE_RESUMEN.txt

# Listar archivos
ls -lh reports/

# Si necesitas HTML
# Descárgalo o sírvelo con Python:
python -m http.server 8000
# Luego: http://tu-servidor:8000/reports/inconsistencies_report.html
```

### Importar CSV a Jira
```bash
# 1. Descargar
scp ubuntu@setag-arm-ashburn:/opt/ades/ades_testing/reports/jira_issues.csv ~/Downloads/

# 2. Jira: Projects → Importar → CSV
# 3. Seleccionar archivo descargado
# 4. Crear issues en backlog
```

---

## Próximos Pasos

### Después de Sesión 1
1. ✅ Ejecutar 3 scripts (30 min)
2. ✅ Ver reportes (10 min)
3. ✅ Importar CSV a Jira (20 min)
4. ✅ Compartir con team

### Sesión 2 (Próxima semana)
1. Developers corrigen top 15 críticas
2. Ejecuta scripts nuevamente (30 min)
3. Valida: ¿bajó inconsistencias?

### Sesión 3+ (Iteración continua)
1. Re-ejecuta cada 1-2 semanas
2. Trackea métricas
3. Iterate hasta <20 críticas

---

## Preguntas Frecuentes

**P: ¿Cuánto espacio ocupan las capturas?**
R: ~100-200 MB (automáticas, puedes borrar después)

**P: ¿Puedo ejecutar desde otro directorio?**
R: Sí. Los scripts portables funcionan desde cualquier lado.

**P: ¿Necesito cambiar algo en los scripts?**
R: No. Todo está configurado automáticamente.

**P: ¿Puedo limitar a X módulos para test rápido?**
R: Edita el script, cambia el `run(phase=1)` a `run(phase=1, limit=5)`

---

## ✅ Checklist Ejecución

- [ ] En directorio `/opt/ades/ades_testing`
- [ ] venv activado
- [ ] `.env` configurado con credenciales
- [ ] Deps instaladas (pip list | grep anthropic)
- [ ] Chromium instalado (python -m playwright install chromium)
- [ ] ADES accesible (curl -I https://ades.setag.mx)

---

## 🚀 Comenzar Ahora

```bash
cd /opt/ades/ades_testing
source venv/bin/activate
source .env
python 01_ades_explorer_portable.py
```

**Duración**: 45 minutos hasta primer reporte.

**Resultado**: 50-80 inconsistencias priorizadas.

---

¿Dudas? Revisa `README_PORTABLE.md` para detalles técnicos.

# ADES Testing Exploratorio - Guía de Ejecución (Sesión 1)

**Fecha**: 2024
**Sistema**: ADES Nevadi (https://ades.setag.mx)
**Objetivo**: Captura sistémica + análisis cognitivo de inconsistencias

---

## 📋 Resumen Ejecutivo

Este plan ejecuta **3 scripts atómicos** en secuencia:

1. **`01_ades_explorer.py`** - Navega ~25 módulos, captura screenshots + DOM + errores
2. **`02_claude_qa_analyzer.py`** - Analiza capturas con Claude API, detecta inconsistencias
3. **`03_report_generator.py`** - Genera HTML ejecutivo + CSV para Jira + matriz trazabilidad

**Tiempo estimado**: 30-45 minutos por sesión
**Resultado**: Reportes de inconsistencias prioritizados por severidad

---

## ⚙️ Setup Inicial (Ejecutar una sola vez)

### 1. Instalar Dependencias

```bash
# Crear directorio de trabajo
mkdir -p /home/claude/ades_testing
cd /home/claude/ades_testing

# Instalar Python dependencies
pip install --upgrade pip
pip install playwright python-dotenv anthropic
pip install pandas openpyxl  # Para Excel si necesario

# Instalar navegadores Playwright
playwright install chromium
```

### 2. Configurar Variables de Entorno

Crear archivo `.env` en `/home/claude/ades_testing/`:

```bash
# ADES Credenciales
ADES_USER=admin
ADES_PASSWORD=***REDACTED-ROTATED***

# Anthropic API
ANTHROPIC_API_KEY=sk-ant-...  # Tu API key
```

**Importante**: 
- No commitear `.env` (agregar a `.gitignore`)
- Las credenciales son sensibles: usar secretos en producción

### 3. Cargar Variables

```bash
cd /home/claude/ades_testing
export $(cat .env | xargs)

# Verificar
echo $ADES_USER
echo $ANTHROPIC_API_KEY  # No mostrar valor, solo verificar que existe
```

---

## 🚀 Ejecución Paso a Paso

### Sesión 1: Captura de Módulos Fase 1 (Críticos + Altos)

**Duración**: ~15-20 minutos

```bash
cd /home/claude/ades_testing

# 1. Ejecutar captura (explora ~25 módulos)
python 01_ades_explorer.py

# Monitoreo:
# - Ver logs en consola (INFO, WARNING, ERROR)
# - Si hay error de auth: verificar credenciales
# - Si hay timeout: puede aumentarse en config_ades_modules.json
```

**Qué hace**:
- ✓ Autentica en ADES con credenciales
- ✓ Navega cada módulo en `config_ades_modules.json`
- ✓ Captura screenshot PNG (1920x1080)
- ✓ Extrae DOM HTML completo
- ✓ Detecta errores de consola (JS, XHR)
- ✓ Extrae texto visible ("No hay X configurados", etc)
- ✓ Guarda en `/captures/`

**Salida esperada**:
```
✓ Resultados guardados en /home/claude/ades_testing/captures/captures_summary.json
  - Módulos capturados: 25/25
  - Errores de consola: X
  - Errores de red: Y

Archivos generados:
  - captures/dashboard.png
  - captures/alumnos.png
  - captures/profesores.png
  - ...
  - captures/captures_summary.json
  - captures/captures_summary.csv
```

---

### Sesión 2: Análisis Cognitivo con Claude

**Duración**: ~10-15 minutos

```bash
cd /home/claude/ades_testing

# 2. Analizar capturas con Claude API
python 02_claude_qa_analyzer.py

# Monitoreo:
# - Ver batch progress
# - Verificar uso de tokens (Claude Sonnet es eficiente)
# - Si hay error JSON: verificar formato de respuesta
```

**Qué hace**:
- ✓ Lee `captures_summary.json`
- ✓ Agrupa módulos en lotes (5 por lote para eficiencia)
- ✓ Llama Claude API con heurísticas de negocio
- ✓ Claude analiza como QA Senior:
  - ¿Contexto propagado?
  - ¿Datos no renderizados?
  - ¿Campos faltantes?
  - ¿SEP/Nevadi distinguido?
  - ¿Errores API enmascarados?
- ✓ Genera `inconsistencies_report.json`

**Salida esperada**:
```
✓ Análisis completo guardado en /home/claude/ades_testing/analysis/inconsistencies_report.json
  - Total inconsistencias: X
  - Críticas: Y
  - Altas: Z
  - Medias: W
  - Bajas: V

Archivos generados:
  - analysis/inconsistencies_report.json
  - analysis/inconsistencies_report.csv
```

---

### Sesión 3: Consolidación de Reportes

**Duración**: ~5 minutos

```bash
cd /home/claude/ades_testing

# 3. Generar reportes ejecutivos
python 03_report_generator.py

# Salida en consola: tabla ASCII de resumen
```

**Qué hace**:
- ✓ Consolida inconsistencias por severidad
- ✓ Agrupa por tipo
- ✓ Genera HTML interactivo con estadísticas
- ✓ Genera CSV importable a Jira
- ✓ Crea matriz trazabilidad (módulo x severidad)

**Salida esperada**:
```
✓ HTML generado: /home/claude/ades_testing/reports/inconsistencies_report.html
✓ CSV Jira generado: /home/claude/ades_testing/reports/jira_issues.csv
✓ Matriz trazabilidad generada: /home/claude/ades_testing/reports/traceability_matrix.csv
✓ Resumen texto generado: /home/claude/ades_testing/reports/REPORTE_RESUMEN.txt

╔═══════════════════════════════════════════════════════════════════════╗
║           ADES NEVADI - TESTING EXPLORATORIO                         ║
║              REPORTE DE INCONSISTENCIAS                              ║
╚═══════════════════════════════════════════════════════════════════════╝

Generado: 2024-12-15 14:30:45

RESUMEN DE SEVERIDAD
─────────────────────────────────────────────────────────────────────
  🔴 CRÍTICAS:   15  (Bloquean funcionalidad)
  🟠 ALTAS:      23  (Afectan UX significativamente)
  🟡 MEDIAS:     18  (Mejoras necesarias)
  🟢 BAJAS:      12  (Sugerencias)
  ─────────────
  TOTAL:         68

TIPOS DE INCONSISTENCIAS
─────────────────────────────────────────────────────────────────────
  • Data Not Rendered: 20
  • Missing Field: 18
  • Context Not Propagated: 15
  • Error Hidden: 10
  • Incomplete Flow: 3
  • SEP/Nevadi Ambiguity: 2
```

---

## 📊 Interpretación de Resultados

### Archivo 1: `inconsistencies_report.html`

**Abrirlo**: 
```bash
open /home/claude/ades_testing/reports/inconsistencies_report.html
# o en navegador: file:///home/claude/ades_testing/reports/inconsistencies_report.html
```

**Elementos**:
- 📈 Gráficos de severidad (barras)
- 📋 Tabla interactiva con todas las inconsistencias
- 🔍 Click en "Descripción" para ver detalles

**Cómo usar**:
1. Escanea gráfico superior: ¿cuántas críticas?
2. Abre tabla, ordena por "Severidad"
3. Lee "Descripción" + "Ubicación" para cada una
4. Nota "Sugerencia" para entender cómo corregir

---

### Archivo 2: `jira_issues.csv`

**Importar a Jira**:
```
Jira > Importar > CSV > jira_issues.csv
```

**Campos automáticamente rellenados**:
- Summary (título del issue)
- Description (qué, dónde, por qué)
- Priority (Crítico → Highest, Alto → High, etc)
- Component (módulo ADES)
- Labels (QA-Testing, tipo de bug)
- Story Points (3 si crítico, 2 si alto, 1 si medio/bajo)

**Luego en Jira**:
- Asignar a desarrolladores
- Agregar a sprint
- Estimar esfuerzo
- Trackear progreso

---

### Archivo 3: `traceability_matrix.csv`

**Abrirlo en Excel**:
```bash
open /home/claude/ades_testing/reports/traceability_matrix.csv
```

**Qué muestra**:
- Cada fila = un módulo ADES
- Columnas = # de inconsistencias por severidad
- Última columna = estado (🔴 Crítico, 🟠 Alto, etc)

**Uso**:
- Identificar módulos más problemáticos
- Priorizar correcciones por módulo
- Trackear mejora en futuras ejecuciones

---

## 🔄 Ejecución Iterativa (Futuras Sesiones)

Después de corregir inconsistencias, re-ejecuta el cycle:

### Sesión 2 (Después de correcciones):
```bash
python 01_ades_explorer.py  # Fase 1 nuevamente
python 02_claude_qa_analyzer.py
python 03_report_generator.py

# Comparar con reporte anterior:
# ✓ ¿Bajó # de críticas?
# ✓ ¿Se resolvió [X] inconsistencia específica?
```

### Sesión 3 (Fase 2 - Módulos Medio Riesgo):
```bash
# Modificar config_ades_modules.json: cambiar "sequence" de módulos
# O:
# python 01_ades_explorer.py --phase 2

python 01_ades_explorer.py
python 02_claude_qa_analyzer.py
python 03_report_generator.py
```

---

## 🐛 Troubleshooting

### Error: "AuthenticationError" en Playwright

```
Error: Fallo al autenticar en ADES
```

**Solución**:
```bash
# Verificar credenciales
echo $ADES_USER
echo $ADES_PASSWORD

# Verificar que ADES está UP
curl -I https://ades.setag.mx/login

# Si credenciales cambiaron:
export ADES_PASSWORD=nueva_contraseña
```

### Error: "ANTHROPIC_API_KEY not found"

```
ValueError: Variable ANTHROPIC_API_KEY no configurada
```

**Solución**:
```bash
# Verificar key
echo $ANTHROPIC_API_KEY  # Debe mostrar algo (sk-ant-...)

# Si está vacía:
export ANTHROPIC_API_KEY=tu_key_aqui

# O editar .env y recargar:
source .env
```

### Error: "No hay capturas para analizar"

```
ERROR: No hay capturas para analizar
```

**Solución**:
```bash
# Verificar que script 1 se ejecutó correctamente
ls -la /home/claude/ades_testing/captures/

# Si está vacía, re-ejecutar script 1:
python 01_ades_explorer.py

# Verificar logs:
python 01_ades_explorer.py 2>&1 | tee explorer.log
```

### Error: "JSON parsing error en Claude response"

```
JSONDecodeError: No JSON encontrado en respuesta
```

**Solución**:
```bash
# Claude a veces añade markdown. El script debería manejar esto.
# Si persiste, aumentar max_tokens en 02_claude_qa_analyzer.py:

# Cambiar:
max_tokens=4000
# A:
max_tokens=6000
```

---

## 📈 Métricas de Éxito

Después de Sesión 1, deberías tener:

- [ ] ✓ ~25 módulos capturados exitosamente
- [ ] ✓ 0-20 errores de consola/red (depende del estado)
- [ ] ✓ 50-100 inconsistencias detectadas (baseline)
- [ ] ✓ HTML ejecutivo abierto en navegador
- [ ] ✓ CSV importado en Jira (o lista para importar)
- [ ] ✓ Matriz trazabilidad revisada

---

## 📝 Notas Técnicas

### Variables de Configuración

En `config_ades_modules.json`:

```json
{
  "system_config": {
    "base_url": "https://ades.setag.mx",
    "capture_delay_ms": 2000,  // Esperar 2s antes de capturar
    "timeout_ms": 30000,       // Timeout 30s por módulo
    "batch_size_qa": 5         // Analizar 5 módulos por call a Claude
  }
}
```

**Ajustes si es lento**:
- Aumentar `capture_delay_ms` si ADES es lento
- Reducir `batch_size_qa` si Claude retorna errores (usa menos tokens)

### Estructura de Directorios

```
/home/claude/ades_testing/
├── .env                              # Credenciales (NO commitear)
├── config_ades_modules.json          # Mapeo de módulos + heurísticas
├── 01_ades_explorer.py              # Script captura
├── 02_claude_qa_analyzer.py         # Script análisis
├── 03_report_generator.py           # Script reportes
├── EJECUTAR_TESTING.md              # Guía (este archivo)
│
├── captures/                         # Salida Sesión 1
│   ├── dashboard.png
│   ├── alumnos.png
│   ├── ...
│   ├── captures_summary.json
│   └── captures_summary.csv
│
├── analysis/                         # Salida Sesión 2
│   ├── inconsistencies_report.json
│   └── inconsistencies_report.csv
│
└── reports/                          # Salida Sesión 3
    ├── inconsistencies_report.html
    ├── jira_issues.csv
    ├── traceability_matrix.csv
    └── REPORTE_RESUMEN.txt
```

---

## 🎯 Próximas Sesiones (Plan)

**Sesión 2** (Esta semana):
- Ejecutar Fase 1 completa (25 módulos críticos)
- Generar reporte inicial
- Priorizar top 15 inconsistencias críticas

**Sesión 3** (Semana siguiente):
- Correcciones desarrolladas
- Re-ejecutar Fase 1 para validar
- Ejecutar Fase 2 (módulos medio/bajo riesgo)

**Sesión 4** (Cierre):
- Ejecutar todo el sistema (Fase 1+2+3)
- Consolidar baseline final
- Documentar estado del sistema

---

**¿Listo para ejecutar?**

```bash
cd /home/claude/ades_testing
source .env
python 01_ades_explorer.py
```

👉 Luego de que termine, continúa con `02_claude_qa_analyzer.py`

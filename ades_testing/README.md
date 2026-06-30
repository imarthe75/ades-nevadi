# 🧪 ADES Nevadi - Testing Exploratorio Automatizado

## Visión General

Sistema de **testing exploratorio cognitivo** para ADES Nevadi que:
- ✅ Navega ~60 módulos automáticamente (Playwright)
- ✅ Captura screenshots + DOM + errores de consola/red
- ✅ Analiza inconsistencias con IA (Claude API)
- ✅ Genera reportes ejecutivos (HTML, CSV, matriz)

**Tipo de inconsistencias detectadas**:
- ✗ Datos existentes en BD que no se renderizan ("No hay X")
- ✗ Contexto (filtros top bar) no propagado a diálogos
- ✗ Campos requeridos faltantes en formularios
- ✗ Errores de API enmascarados como "sin datos"
- ✗ Distinción SEP/Nevadi/UAEMEX ausente
- ✗ Flujos incompletos (create, edit, delete)
- ✗ Validaciones no visibles

---

## 📁 Archivos Entregables (Sesión 1)

```
/home/claude/ades_testing/
│
├── 📄 EJECUTAR_TESTING.md              ← LEER PRIMERO
├── 📄 README.md                         ← Este archivo
├── 📄 config_ades_modules.json          ← Config: 30+ módulos + heurísticas
│
├── 🐍 01_ades_explorer.py              ← Script 1: Captura (Playwright)
├── 🐍 02_claude_qa_analyzer.py         ← Script 2: Análisis (Claude API)
├── 🐍 03_report_generator.py           ← Script 3: Reportes (HTML/CSV)
│
├── captures/                            ← Generado por Script 1
│   ├── dashboard.png, alumnos.png, ...
│   ├── captures_summary.json
│   └── captures_summary.csv
│
├── analysis/                            ← Generado por Script 2
│   ├── inconsistencies_report.json
│   └── inconsistencies_report.csv
│
└── reports/                             ← Generado por Script 3
    ├── inconsistencies_report.html      ← ABRIR EN NAVEGADOR
    ├── jira_issues.csv                  ← Importar a Jira
    ├── traceability_matrix.csv          ← Abrir en Excel
    └── REPORTE_RESUMEN.txt
```

---

## 🚀 Quick Start (5 minutos)

### 1️⃣ Setup (una sola vez)

```bash
cd /home/claude/ades_testing

# Instalar dependencias
pip install playwright python-dotenv anthropic

# Instalar navegadores
playwright install chromium

# Crear .env con credenciales
cat > .env << EOF
ADES_USER=admin
ADES_PASSWORD=***REDACTED-ROTATED***
ANTHROPIC_API_KEY=sk-ant-...
EOF
```

### 2️⃣ Cargar variables

```bash
source .env
```

### 3️⃣ Ejecutar secuencialmente

```bash
# Script 1: Captura (15 min)
python 01_ades_explorer.py

# Script 2: Análisis (10 min)
python 02_claude_qa_analyzer.py

# Script 3: Reportes (2 min)
python 03_report_generator.py

# 🎉 Listo! Abre:
open reports/inconsistencies_report.html
```

---

## 📊 Salida Esperada

### HTML Ejecutivo
```
Resumen de Severidad:
  🔴 Críticas:  15  (Bloquean funcionalidad)
  🟠 Altas:     23  (Afectan UX)
  🟡 Medias:    18  (Mejoras)
  🟢 Bajas:     12  (Sugerencias)
  ─────────
  TOTAL:        68 inconsistencias
```

### Ejemplos de Inconsistencias Detectadas

```json
{
  "severity": "Crítico",
  "module_id": "disponibilidad_docente",
  "type": "Data Not Rendered",
  "description": "Mensaje 'No hay franjas horarias configuradas' pero existen en BD",
  "location": "/disponibilidad → Perfil Docente → Matriz",
  "impact": "Profesor NO puede marcar disponibilidad",
  "suggestion": "Verificar API /teacher-time-slots, implementar error boundary"
}
```

---

## 🔧 Arquitectura Técnica

### Stack

| Componente | Tecnología | Propósito |
|-----------|-----------|----------|
| **Captura Visual** | Playwright + Python | Automatizar navegación, screenshot, DOM |
| **Análisis Cognitivo** | Claude Sonnet 4.6 API | QA Senior: detectar inconsistencias |
| **Reportes** | HTML + Tailwind + Chart.js | Dashboard ejecutivo |
| **Integración** | CSV | Importar a Jira/Azure DevOps |

### Flujo de Datos

```
┌─────────────────────┐
│  01_explorer.py     │  Navega ADES, captura
│  (Playwright)       │  → captures/
└──────────┬──────────┘
           │
           ↓
┌─────────────────────────────────┐
│  02_qa_analyzer.py              │  Lee capturas, agrupa batches
│  (Claude API + Heurísticas)     │  → analysis/
└──────────┬──────────────────────┘
           │
           ↓
┌─────────────────────────────────┐
│  03_report_generator.py         │  Consolida, genera reportes
│  (HTML + CSV + Matriz)          │  → reports/
└─────────────────────────────────┘
           │
           ↓
    📊 REPORTES EJECUTIVOS
```

### Config JSON

`config_ades_modules.json` mapea:

```json
{
  "modules": [
    {
      "id": "disponibilidad_docente",
      "path": "/disponibilidad",
      "category": "RRHH",
      "risk": "critico",
      "expected_elements": [...],
      "heuristics": {
        "must_have": [...],
        "must_not_have": [...]
      },
      "api_expected": [...]
    }
  ]
}
```

---

## 📈 Fases de Testing

### ✅ Fase 1 (Ahora): Módulos Críticos + Altos (25 módulos)

```
Académico:     Alumnos, Profesores, Calificaciones, Evaluaciones, Asistencias
Operaciones:   Disponibilidad Docente, Horarios, Planes de Estudio
Gradebook:     Gradebook, Ponderaciones
Reportes/BI:   Dashboards, Grade Analytics
Sistema:       Monitor, Admin
```

**Duración**: ~45 minutos total
**Salida**: Baseline de inconsistencias críticas

### 📅 Fase 2 (Próximo): Módulos Medio (15 módulos)

```
RRHH:          Personal No-Docente, Licencias, Capacitaciones
Inteligencia:  Asistente IA, Eval 360°
Comunicación:  Comunicados, Foros, Encuestas
```

### 📋 Fase 3 (Posterior): Módulos Bajo (20 módulos)

```
Convocatorias, Portal Padres, Portal Alumno, etc.
```

---

## 🎯 Casos de Uso por Rol

### Para QA/Testing
- Ejecutar testing sistémico sin crear cases manuales
- Detectar inconsistencias cognitivas (no solo bugs técnicos)
- Generar reporte para developers

### Para Product Manager
- Ver HTML ejecutivo
- Entender qué módulos tienen más problemas
- Priorizar sprints basado en severidad

### Para Desarrolladores
- Descargar CSV → Importar a Jira
- Recibir descripción detallada + sugerencia
- Re-ejecutar después de correcciones

### Para DevOps/Infra
- Monitorear salud del sistema (Monitor tab)
- Trackear downtime de APIs
- Analizar logs de errores

---

## 🔄 Ejecución Iterativa

**Ciclo de mejora**:

1. **Sesión 1**: Ejecutar testing → Generar reportes
2. **Fase Dev**: Correcciones en código
3. **Sesión 2**: Re-ejecutar testing → Comparar métricas
4. ✅ Validar: ¿Bajó # de críticas? ¿Se resolvió X?
5. Repetir con Fase 2

---

## 📝 Notas Importantes

### Credenciales
- Almacenar en `.env`, nunca en código
- Las credenciales en este documento son ejemplos
- Usar variables de entorno en producción

### API Key Anthropic
- Necesaria para análisis cognitivo (Script 2)
- Usar Sonnet 4.6 (mejor relación costo/calidad para este use case)
- ~2000-3000 tokens por batch de 5 módulos

### Responsabilidad del Testing
Este framework **detecta**:
- ✅ Inconsistencias visuales/cognitivas
- ✅ Datos que deberían renderizar pero no
- ✅ Flujos incompletos
- ✅ Validaciones faltantes

Este framework **NO detecta**:
- ❌ Bugs de lógica de negocio profunda
- ❌ Performance (load testing)
- ❌ Security (OWASP)
- ❌ Internacionalización

Para eso, usar herramientas especializadas + testing manual.

---

## 📞 Troubleshooting

Ver `EJECUTAR_TESTING.md` sección "🐛 Troubleshooting" para:
- Error de autenticación
- API Key no configurada
- Timeout en Playwright
- Errores de parsing JSON

---

## 📚 Referencias

- **Playwright Docs**: https://playwright.dev/python/
- **Anthropic Claude API**: https://docs.anthropic.com/
- **Testing educativo**: Adaptado a heurísticas de sistemas educativos mexicanos (SEP, UAEMEX)

---

## 🏁 Próximos Pasos

1. ✅ Leer `EJECUTAR_TESTING.md` (guía paso a paso)
2. ✅ Setup dependencias
3. ✅ Crear `.env` con credenciales
4. ✅ Ejecutar `python 01_ades_explorer.py`
5. ✅ Ejecutar `python 02_claude_qa_analyzer.py`
6. ✅ Ejecutar `python 03_report_generator.py`
7. ✅ Abrir `reports/inconsistencies_report.html`
8. ✅ Analizar resultados
9. ✅ Crear tasks en Jira (usar `jira_issues.csv`)
10. ✅ Iterar (correcciones → re-testing)

---

**¿Dudas o mejoras?**

El framework es **modular y extensible**:
- Agregar más módulos a `config_ades_modules.json`
- Cambiar heurísticas en el config
- Ajustar prompts Claude en Script 2
- Customizar HTML en Script 3

¡A testear! 🚀

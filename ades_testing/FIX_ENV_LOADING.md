# 🔧 FIX: Lectura de Variables de Entorno

## El Problema

El error que viste:
```
ValueError: Variable ADES_PASSWORD no configurada
ValueError: ANTHROPIC_API_KEY no configurada
```

**Causa**: `source .env` en bash no exporta variables a procesos Python.

## La Solución

Ahora los scripts **leen el archivo `.env` directamente** usando `python-dotenv`.

## Scripts Actualizados

✅ **01_ades_explorer_portable.py** - Ahora lee `.env` correctamente
✅ **02_claude_qa_analyzer_portable.py** - Ahora lee `.env` correctamente
✅ **03_report_generator_portable.py** - No necesita credenciales (ya funciona)

## Cómo Usar

```bash
cd /opt/ades/ades_testing

# Tu .env ya está bien configurado:
cat .env

# Ejecutar directamente (sin source .env)
python 01_ades_explorer_portable.py
python 02_claude_qa_analyzer_portable.py
python 03_report_generator_portable.py
```

## Verificación

Los scripts ahora mostrarán:
```
[INFO] ✓ Archivo .env cargado desde /opt/ades/ades_testing/.env
```

Si ves esto: ✅ Variables cargadas correctamente

## Descarga

Descargar las versiones actualizadas:
- 01_ades_explorer_portable.py (ACTUALIZADO)
- 02_claude_qa_analyzer_portable.py (ACTUALIZADO)
- 03_report_generator_portable.py (sin cambios, funciona igual)

---

**Ya está todo listo. Ejecuta directamente sin `source .env`.**

# 📌 Command: /quality-gate

El comando `/quality-gate` ejecuta los controles de calidad y auditoría locales definidos por ECC para verificar el código.

## 🚀 Comportamiento del Arnés

1. **Sintaxis y Linter:**
   - Ejecuta validaciones de sintaxis según el lenguaje (ej. `flake8` para Python, `eslint` para JS/TS).
2. **Seguridad Local:**
   - Escanea en busca de credenciales expuestas en texto plano.
   - Verifica que los archivos sensibles de datos (`data/`, `.env`) estén ignorados en el `.gitignore`.
3. **Pruebas Unitarias:**
   - Corre la suite de pruebas del proyecto si está configurada en el arnés.

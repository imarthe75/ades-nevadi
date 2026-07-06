# 🛡️ ECC Agent Workflow Rules (RULES.md)

Este archivo define las reglas de desarrollo y hooks de sistema inspirados en **affaan-m/ECC** para asegurar la calidad de código, triaje y control del arnés del agente.

## 📌 Flujo de Trabajo (Harness Workflow)

1. **Investigación y Contexto Primero:**
   - Queda prohibido modificar código sin antes listar el mapa técnico local en `.agent/MAP.md`.
   - Si no existe el archivo `.agent/STATE.md`, se debe invocar la inicialización automática del proyecto (`npm run init-project`).

2. **Higiene de Contexto y Token Saving:**
   - Al ejecutar comandos o búsquedas, limita el output y la cantidad de líneas devueltas (ej. `head -n 20`).
   - Evita lecturas completas de archivos binarios o bases de datos sin filtros.
   - El diseño de frontend debe alinearse con el estilo Oracle APEX: interactive grids, master-detail, LOV y edición directa sobre tablas.

3. **Caché de Respuestas y Memoria:**
   - Integración nativa con Valkey. La lógica de control debe verificar la caché semántica local en Valkey (`6379`) antes de enviar consultas redundantes al modelo.

4. **Soberanía y Seguridad de Datos:**
   - Ninguna credencial, token (PAT) o credencial de base de datos puede ser subida a repositorios públicos o expuesta en logs.
   - Las carpetas `./data` y los archivos `.env` deben estar permanentemente en el `.gitignore`.

5. **Documentación Obligatoria y Exhaustiva:**
   - Es obligatorio documentar exhaustivamente todo cambio de código.
   - Esto incluye añadir docstrings con tipos, descripciones de parámetros y retornos para cada nueva función/método, así como comentarios explicativos en clases y bloques de código complejos.

6. **Auditoría de Seguridad y Funcionalidad:**
   - Siempre se deben aplicar las auditorías necesarias en la base de datos y en la lógica de negocio para garantizar el cumplimiento de una seguridad estricta y de la funcionalidad solicitada de forma impecable.

7. **Autoexploración y Autoescaneo de Componentes:**
   - Cada vez que se desarrolle o modifique un componente, este debe ser autoexplorado y autoescaneado para asegurar que sea 100% seguro con respecto a los estándares definidos y cumpla al 100% con la funcionalidad esperada.

8. **Análisis Exploratorios:**
   - Realizar análisis exploratorios profundos antes y durante el diseño o la implementación de cualquier cambio o componente nuevo para identificar posibles fallas, vulnerabilidades o inconsistencias lógicas de manera proactiva.

# 📌 Command: /plan

El comando `/plan` obliga al agente a suspender toda actividad de edición de código y concentrarse exclusivamente en el diseño y trazado del plan de trabajo.

## 🚀 Comportamiento del Arnés

1. El agente debe listar los archivos del proyecto para entender el alcance.
2. Generará un archivo `implementation_plan.md` en el directorio de artefactos con la siguiente estructura:
   - **Objetivo técnico**
   - **Archivos a modificar/crear**
   - **Plan de pruebas automatizadas y manuales**
   - **Preguntas abiertas**
3. El agente esperará de forma bloqueante la confirmación del usuario para proceder.

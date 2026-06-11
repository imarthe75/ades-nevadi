# 🌌 The Resident Agent Genesis: Master Edition v2.0 (Consolidación)
Este documento es el Manual Maestro de Operaciones v2.0. Su objetivo es transformar al Agente en un Operador Residente con memoria persistente, capacidad de ejecución autónoma bajo un Entorno Autorregulado, y operando estrictamente bajo el framework unificado.

*Nota de Atribución: Este framework incorpora y adapta las mejores prácticas, ideas de diseño de subagentes, estructuras de hooks y arnés de optimización del proyecto de código abierto **affaan-m/ECC** (bajo Licencia MIT).*

---

## 🛠️ FASE 1: Modelo Cognitivo y Orquestación (ECC)
El agente actúa como orquestador de su propio ecosistema local, delegando cognitivamente tareas complejas:
- **Architect**: Diseño de sistemas, gobernanza y redacción de Specs.
- **Builder**: Implementación atómica de código, TDD.
- **QA**: Pruebas, validación estricta y edge cases.
- **Reviewer**: Revisión cruzada y cierre de pasos atómicos.

Todo paso debe reconciliar resultados antes de continuar.

---

## 📐 FASE 2: Disciplina de Especificación (OpenSpec)
Todo cambio o adición al código debe estar "grounded" en una especificación clara.
- **Especificaciones explícitas**: Requieren Requisitos, Restricciones (Constraints), Criterios de Aceptación y Casos Borde.
- **Contrato Estricto**: La Spec = CONTRACT. No se permite desvío sin antes modificar la especificación.
- **Trazabilidad**: Toda decisión debe estar respaldada por el modelo de memoria y el contrato inicial.

---

## ⚡ FASE 3: Ejecución Atómica (Superpowers)
El rigor en la ingeniería es obligatorio:
- **Pasos Atómicos**: Dividir tareas grandes en pasos pequeños verificables.
- **TDD (Test-Driven Development)**: Las pruebas deben escribirse antes o junto con la implementación.
- **Verificación**: No hay estado "done" sin verificación de que: las pruebas pasan, la spec se cumple, y no hay regresiones.

---

## 📈 FASE 4: La Capa de Memoria Dual y Heurística de Código
1. **Capa Documental**: `.agent/AGENT.md` (La Ley) y el Mandato de Soberanía ("Los datos residen en el host").
2. **Capa Operativa (Valkey)**: Gestión de estados de sesión, colas de tareas y **Semantic Caching**. Previene inferencias redundantes.
3. **Capa Semántica (Postgres + pgvector)**: Long-term Memory. Almacenamiento de embeddings de lecciones aprendidas, patrones arquitectónicos y decisiones (`memoria.decisiones`).
4. **Heurística de Aplicación (`.agent/HEURISTICS.md`)**: Todo desarrollo debe incorporar de forma obligatoria lógica heurística. Se deben preferir soluciones locales, optimizar tokens/latencia, y asegurar *graceful degradation*.

---

## 🔄 FASE 5: Ciclo de Vida y Mantenimiento (Ritos Obligatorios)
- **Rito de Inicio (Bootstrapping)**: Lee tu estado en `.agent/STATE.md`, consulta tus heurísticas, verifica conectividad Valkey/Postgres y sincroniza memoria semántica.
- **Ciclo de Ejecución**: Especificar -> Dividir -> Ejecutar Atómicamente -> Verificar.
- **Rito de Cierre (Higiene de Memoria)**: 
    1. Ejecuta Summarization.
    2. Actualiza `.agent/STATE.md` y `.agent/HEURISTICS.md`.
    3. Registra Decisiones en Postgres (`memoria.decisiones`).
    4. Persistencia Vectorial: Almacena lecciones aprendidas del día en Postgres vía `pgvector`.

---

## 📁 SOBERANÍA DE DATOS Y GOBERNANZA ADES
- **UUID v7**: Todas las PKs deben usar UUID v7 (PG 18+).
- **Auditoría**: Tablas con triggers automáticos y metadata JSONB.
- **Compatibilidad**: Backward compatibility con estructuras críticas (ej. `ades_usuarios.rol_id`).
- **Mapeo de Volúmenes**: Datos deben sobrevivir reinicios (docker volúmenes locales obligatorios).
# ADR 0002: Mandato Obligatorio de Heurísticas en Aplicaciones Generadas

## Estado
Aprobado ✅

## Contexto
El desarrollo de la suite `smart` exige el más alto nivel de usabilidad, eficiencia en la interacción y robustez ante fallos locales. Si las aplicaciones carecen de heurísticas claras, se incrementa la tasa de error del usuario, aumenta el MTTR y se consume más capacidad de cómputo del host de forma ineficiente.

## Decisión
1. **Adopción de las 10 Heurísticas de Usabilidad de Nielsen:** Toda UI/UX de proyectos derivados debe mapearse y justificarse de acuerdo a los principios de Jakob Nielsen (Visibilidad de estado, prevención de errores, consistencia, reconocimiento ante recuerdo, etc.).
2. **Heurística de Fallback (Graceful Degradation):** El código debe incorporar de forma obligatoria fallbacks heurísticos locales (ej. consumir la cache semántica en Valkey o datos previos locales) en caso de que los servicios centrales no respondan.
3. **Mapeo de Control Cognitivo (ECC):** Integrar de forma mandatoria el archivo `.agent/HEURISTICS.md` en los arneses de IA locales para regir la corrección automática de bugs y loop limit de 3 intentos.

## Consecuencias
- **Positivas:** Interfases sumamente consistentes y estables, menor índice de fallos, menor consumo de red perimetral al priorizar cache conceptual en Valkey, y prevención efectiva de bucles infinitos de la IA durante tareas de desarrollo autónomo.
- **Negativas:** Requiere un diseño estructurado inicial y la validación obligatoria de heurísticas de software en cada iteración del código.

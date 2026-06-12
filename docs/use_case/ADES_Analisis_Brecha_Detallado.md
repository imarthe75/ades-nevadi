# ADES Nevadi — Análisis de Brecha Funcional (Gap Analysis)
## Identificación, Clasificación y Priorización de Capacidades Pendientes

**Versión:** 1.0  
**Fecha:** Junio 2026  
**Enfoque:** Seguridad, Operación, Normativa y Experiencia de Usuario

---

## Resumen Ejecutivo de la Brecha

### Distribución Actual vs Objetivo

```
ESTADO ACTUAL (Fases 1-27 parcial — 2026-06-11):
├── Módulos Funcionales: 146 casos de uso ✅
├── Cobertura: 63.5% del roadmap
└── Estado: OPERACIONAL — PRODUCCIÓN INMINENTE

  Fases completadas: 1-10, 11 (RBAC), 12 (Admin), 13 (Help),
  14 (Portal Padres), 15 (Auditoría), 16 (Superset/BI),
  17 (Flowise NL→SQL), 18 (Carbone Reportes), 19 (Planes Estudio),
  20 (ntfy Push), 21 (Stirling-PDF), 22 (Grafana), 23 (n8n),
  24 (Padres+Optimistic Locking), 26 (pgcrypto+RBAC UI+Menús dinámicos),
  27 (Ed25519+IA Learning Paths+Cierre Período+Reinscripción+
       Sanciones+Planes Mejora+Calendario+Health Check)

BRECHA IDENTIFICADA (2026-06-11):
├── Funcionalidades pendientes: 84 CU (36.5%)
├── Total catálogo expandido: 230 CU (vs 195 inicial — 35 CU nuevos)
└── Riesgo: BAJO-MEDIO (core operativo cubierto)

OBJETIVO 2027:
├── Módulos Funcionales: 230 casos de uso ✅
├── Cobertura: 100% del roadmap
└── Estado: EXCELENCIA OPERATIVA
```

> **Nota:** El total pasó de 195 → 230 CU porque el análisis de brecha inicial (Fases 1-10) no contemplaba los CU agregados en fases 11-27 (gestión de conducta formal, aulas, expediente digital, MFA, Vault, etc.).

---

## Análisis Detallado por Dominio Funcional

### DOMINIO 1: Identidad Institucional
**Estado:** 7/16 casos implementados (**44%**) — *actualizado 2026-06-11*

#### ✅ Implementados
- Configuración de planteles básica (ID-001, ID-002)
- Catálogos geográficos / SEPOMEX (ID-004, ID-005)
- Logo e identidad visual (ID-006, ID-007)
- Firma digital institucional Ed25519 (ID-009) ✅ FASE 27
- **Calendario días festivos ad-hoc (ID-015)** ✅ 2026-06-11

#### ⏳ Faltantes (Brecha: 9 CU)
| Caso | Descripción | Impacto | Urgencia |
|------|-------------|--------|----------|
| ID-003 | Desactivación de plantel | Bajo | Media |
| ID-008 | Templates de boletas PDF | Medio | Alta |
| ID-010–014 | Ciclos y calendarios oficiales SEP/UAEMEX | Alto | Alta |
| ID-016 | Generación automática de actas | Medio | Alta |

**Brecha Total:** ~20 horas | **Prioridad:** 🟡 Media-Alta

---

### DOMINIO 2: Estructura Académica
**Estado:** 14/19 casos implementados (**74%**) — *actualizado 2026-06-11*

#### ✅ Implementados
- Niveles, grados, grupos (AC-001–004)
- Planes de estudio SEP/UAEMEX (AC-008–009)
- Mapa curricular visual (AC-012)
- Temas y contenidos (AC-013)
- Horarios aSc / schedule docentes (AC-016, AC-017)
- Asignación docentes (AC-010, AC-011)
- **Aulas físicas (AC-006)** ✅ Implementado (FASE 27.5)

#### ⏳ Faltantes (Brecha: 5 CU)
| Caso | Descripción | Impacto | Urgencia |
|------|-------------|--------|----------|
| AC-005 | Cambiar asignación de grupo | Medio | Alta |
| AC-014 | Planes alternativos (NEE) | Bajo | Media |
| AC-015 | Publicar/archivar versiones de plan | Bajo | Baja |
| AC-018 | Detectar conflictos horarios | Medio | Media |
| AC-019 | Cambios de horario en vivo | Bajo | Media |

**Brecha Total:** 22 horas | **Prioridad:** 🟡 Media

---

### DOMINIO 3: Gestión de Población Escolar
**Estado:** 14/34 casos implementados (**41%**)

#### ✅ Implementados
- Registro de alumnos básico
- Inscripción y asignación de grupo
- Expediente del alumno (vista consolidada)
- Datos de padres/tutores

#### ⏳ Faltantes (Brecha: 20 CU) — **CRÍTICO**
| Caso | Descripción | Impacto | Urgencia |
|------|-------------|--------|----------|
| PE-002 | Carga de documentos escaneados | Alto | **CRÍTICA** |
| PE-005 | Generar carta aceptación/rechazo | Medio | Alta |
| PE-006 | Seguimiento de expediente admisión | Bajo | Media |
| PE-015 | Reinscripción masiva | **Alto** | **CRÍTICA** |
| PE-016 | Validación no-adeudo | Medio | Alta |
| PE-020 | Baja temporal | Medio | Alta |
| PE-021 | Baja definitiva | Medio | Alta |
| PE-024 | Gestión de documentos (MinIO) | **Alto** | **CRÍTICA** |
| PE-029 | Múltiples tutores | Bajo | Media |
| PE-032 | Portal padre (crear usuario) | Medio | Alta |
| (10 más) | ... | | |

**Brecha Total:** 68 horas | **Prioridad:** 🔴 CRÍTICA

---

### DOMINIO 4: Gestión de Docentes y Personal
**Estado:** 8/17 casos implementados (**47%**)

#### ✅ Implementados
- Registro de docentes básico
- Asignación a grupos/materias
- Disponibilidad y horarios
- Evaluación 360°

#### ⏳ Faltantes (Brecha: 9 CU)
| Caso | Descripción | Impacto | Urgencia |
|------|-------------|--------|----------|
| DP-003 | Disponibilidad parametrizada | Bajo | Media |
| DP-004 | Expediente laboral digital | Medio | Alta |
| DP-005 | Control de asistencia personal | Bajo | Media |
| DP-006 | Licencias y permisos | Medio | **CRÍTICA** |
| DP-007 | Registro capacitaciones | Bajo | Media |
| DP-016 | Plan de mejora post-evaluación | Bajo | Media |
| DP-017 | Observación pedagógica (semáforo) | Medio | Alta |
| DP-018 | Reporte retroalimentación PDF | Medio | Alta |

**Brecha Total:** 32 horas | **Prioridad:** 🟡-🔴 Media-Crítica

---

### DOMINIO 5: Operación Académica Diaria
**Estado:** 18/22 casos implementados (**82%**)

#### ✅ Implementados
- Registro de clases
- Asistencia diaria
- Planificación docente
- Tareas y entregas
- Avance curricular

#### ⏳ Faltantes (Brecha: 4 CU)
| Caso | Descripción | Impacto | Urgencia |
|------|-------------|--------|----------|
| OA-003 | Justificación de faltas | Bajo | Media |
| OA-011 | Alertas de rezago (< 80% temas) | Medio | Alta |
| OA-012 | Ajuste dinámico de plan | Bajo | Media |
| OA-013 | Visualización plantel (avance) | Bajo | Media |
| OA-017 | Detección de plagio | Medio | Media |
| OA-019 | Retroalimentación vídeo | Bajo | Media |

**Brecha Total:** 16 horas | **Prioridad:** 🟢-🟡 Baja-Media

---

### DOMINIO 6: Evaluación y Calificación
**Estado:** 16/25 casos implementados (**64%**)

#### ✅ Implementados
- Gradebook e ingreso de calificaciones
- Cálculo automático de promedios
- Ponderación dinámica
- Boletas PDF
- Cerciticación digital

#### ⏳ Faltantes (Brecha: 9 CU)
| Caso | Descripción | Impacto | Urgencia |
|------|-------------|--------|----------|
| EV-006 | Cierre formal de período | **Alto** | **CRÍTICA** |
| EV-007 | Detección de inconsistencias | Medio | Media |
| EV-012 | Ponderación por alumno (NEE) | Bajo | Media |
| EV-017 | Acta de calificaciones SEP | Medio | Alta |
| EV-024 | Boleta con observaciones | Bajo | Media |
| EV-025 | Escalas cualitativas | Bajo | Media |

**Brecha Total:** 22 horas | **Prioridad:** 🔴-🟡 Crítica-Alta

---

### DOMINIO 7: Inteligencia Artificial
**Estado:** 9/21 casos implementados (**43%**)

#### ✅ Implementados
- Detección de riesgo académico
- Learning paths
- Chatbot NL→SQL
- Dashboards BI

#### ⏳ Faltantes (Brecha: 12 CU)
| Caso | Descripción | Impacto | Urgencia |
|------|-------------|--------|----------|
| IA-005 | Predicción de abandono | **Alto** | Alta |
| IA-009 | Ajuste dinámico de rutas | Bajo | Media |
| IA-014 | Recomendaciones pedagógicas avanzadas | Medio | Media |
| IA-015 | Historial conversaciones persistente | Bajo | Baja |
| IA-020 | Exportación reportes BI | Medio | Media |

**Brecha Total:** 28 horas | **Prioridad:** 🟡 Media

---

### DOMINIO 8: Comunicación e Interacción
**Estado:** 11/23 casos implementados (**48%**)

#### ✅ Implementados
- Comunicados institucionales
- Notificaciones in-app
- Portales de usuario
- Push notifications

#### ⏳ Faltantes (Brecha: 12 CU)
| Caso | Descripción | Impacto | Urgencia |
|------|-------------|--------|----------|
| CO-005 | Reporte de entrega (quién leyó) | Bajo | Media |
| CO-007 | Comunicados recurrentes | Bajo | Media |
| CO-015 | Portal padre (múltiples hijos) | Medio | Media |
| CO-020 | Customización de dashboards | Bajo | Baja |
| CO-021 | Foros por materia | Medio | Media |
| CO-022 | Foro de tutores | Bajo | Baja |
| CO-023 | Moderación de foros | Bajo | Baja |

**Brecha Total:** 26 horas | **Prioridad:** 🟢-🟡 Baja-Media

---

### DOMINIO 9: Salud, Bienestar y Conducta
**Estado:** 10/23 casos implementados (**43%**)

#### ✅ Implementados
- Expediente médico básico
- Gestión de conducta
- Encuestas de clima escolar

#### ⏳ Faltantes (Brecha: 13 CU)
| Caso | Descripción | Impacto | Urgencia |
|------|-------------|--------|----------|
| SB-003 | Control de medicamentos | Bajo | Media |
| SB-006 | Alertas por condiciones crónicas | Medio | Alta |
| SB-007 | Contacto emergencia automático | Medio | Alta |
| SB-009 | Certificados de salud | Bajo | Media |
| SB-012 | Aplicación de sanciones | Medio | **CRÍTICA** |
| SB-013 | Plan de mejora conductual | Medio | Alta |
| SB-014 | Seguimiento de cumplimiento | Bajo | Media |
| SB-016 | Análisis de patrones conducta (IA) | Medio | Media |
| SB-020 | Alertas de bullying | Medio | Alta |
| SB-021 | Seguimiento psicosocial | Bajo | Media |
| SB-023 | Programa de bienestar | Bajo | Baja |

**Brecha Total:** 34 horas | **Prioridad:** 🟡-🔴 Media-Crítica

---

### DOMINIO 10: Administración, Seguridad y Cumplimiento
**Estado:** 11/30 casos implementados (**37%**)

#### ✅ Implementados
- RBAC básico
- Sincronización Authentik
- Auditoría de cambios
- Firma digital

#### ⏳ Faltantes (Brecha: 19 CU) — **MÁS CRÍTICO**
| Caso | Descripción | Impacto | Urgencia |
|------|-------------|--------|----------|
| AD-007 | Intentos fallidos login | Bajo | Media |
| AD-013 | Cumplimiento LRFD | **Alto** | **CRÍTICA** |
| AD-015 | Backup automático diario | **Alto** | **CRÍTICA** |
| AD-016 | Backup de archivos MinIO | **Alto** | **CRÍTICA** |
| AD-017 | Restauración DR | **Alto** | **CRÍTICA** |
| AD-018 | Retención de backups | Medio | Alta |
| AD-019 | Encripción de datos sensibles | **Alto** | **CRÍTICA** |
| AD-023 | Multifactor authentication | Medio | Alta |
| AD-024 | Gestión centralizada secretos | Medio | Alta |
| AD-025 | Health check de servicios | Bajo | Media |
| AD-026 | Estadísticas de uso | Bajo | Baja |
| (8 más) | ... | | |

**Brecha Total:** 78 horas | **Prioridad:** 🔴 CRÍTICA

---

## Matriz de Brecha Consolidada

### Por Urgencia

#### 🔴 **CRÍTICAS** (14 casos de uso)
Impactan operación segura, normativa y sostenibilidad:

```
Dominio                    Casos    Horas   Ejemplos
─────────────────────────────────────────────────────────
Seguridad & Cumplimiento    8       48     Backup, Encripción, LRFD
Población Escolar           4       28     Reinscripción, Documentos
Evaluación                  1        6     Cierre de período
Conducta                    1        8     Aplicación de sanciones

TOTAL CRÍTICAS:             14       90 h
```

#### 🟡 **ALTAS** (32 casos de uso)
Mejoran eficiencia significativa y experiencia:

```
Dominio                    Casos    Horas   Ejemplos
─────────────────────────────────────────────────────────
Población Escolar           8       32     Bajas, Expediente, etc.
Docentes                    4       16     Licencias, Observación
Salud y Conducta            8       22     Alertas, Planes mejora
Identidad Institucional     2        8     Templates, Actas

TOTAL ALTAS:                32      130 h
```

#### 🟢 **MEDIAS** (93 casos de uso)
Complementos útiles, mejora de UX:

```
Dominio                    Casos    Horas   Ejemplos
─────────────────────────────────────────────────────────
Comunicación               10       28     Foros, Customización
Operación Académica         6       16     Plagio, Retroalimentación
Estructura Académica        6       18     Horarios, NEE
IA y Analítica             12       28     Predicción, Exports

TOTAL MEDIAS:              93      150 h
```

---

## Impacto Cualitativo de la Brecha

### Riesgos Operacionales (Sin Implementación)

| Riesgo | Probabilidad | Impacto | Estado Actual |
|--------|------------|--------|---|
| Pérdida de datos (sin backup) | ALTA | CRÍTICO | Sin mitigación |
| No cumplimiento LRFD | MEDIA | CRÍTICO | Parcial |
| Reinscripción manual (error) | MEDIA | ALTO | Manual |
| Expedientes físicos (pérdida) | MEDIA | ALTO | Híbrido |
| Datos sensibles expuestos | BAJA | CRÍTICO | Encriptados parcialmente |

### Oportunidades de Mejora (Con Implementación)

| Oportunidad | Beneficio | Timeline |
|---|---|---|
| Automatización de procesos | 60-70% reducción de trámites | 6 meses |
| Inteligencia predictiva | Retención +5.9% | 9 meses |
| Expediente digital | Acceso 24/7 vs carpetas | 3 meses |
| Seguridad de datos | Conformidad regulatoria | 3 meses |
| Experiencia de usuario | Satisfacción 3.5 → 4.5/5 | 12 meses |

---

## Recomendación de Priorización

### Estrategia Sugerida: **"Críticas Primero"**

#### Fase 1: Julio-Agosto 2026 (CRÍTICAS DOMINIO 10)
✅ Implementar 8 casos de seguridad (90 horas)
- Backup diario + DR
- Encripción de sensibles
- Cumplimiento LRFD básico

**Ganancia:** Instituto asegurado, datos protegidos

#### Fase 2: Septiembre-Octubre 2026 (CRÍTICAS DOMINIOS 3, 6)
✅ Implementar 5 casos operacionales (42 horas)
- Reinscripción masiva
- Cierre de período
- Documentos (Paperless)

**Ganancia:** Operación manual → automatizada

#### Fase 3: Noviembre-Diciembre 2026 (ALTAS)
✅ Implementar 12 casos de mejora (60 horas)
- Bajas y cambios de estatus
- Observación pedagógica
- Alertas de salud

**Ganancia:** Funcionalidad integral

#### Fase 4: Enero-Junio 2027 (MEDIAS + Especializadas)
✅ Implementar 40+ casos (100 horas)
- IA y analítica
- Foros y comunicación
- Cumplimiento normativo

**Ganancia:** Excelencia operativa

---

## Conclusión

### Estado de la Brecha

**ADES es 29% funcional (56/195 casos)** pero con **riesgos críticos sin mitigar** (backup, encripción, LRFD).

La brecha no es "nice-to-have" — es **operacionalmente necesaria** para:
- ✅ Seguridad de datos
- ✅ Conformidad normativa (SEP/UAEMEX)
- ✅ Automatización de procesos críticos
- ✅ Experiencia de usuario sostenible

### Inversión Recomendada

**300 horas de desarrollo** en **15 semanas** con equipo de **2-3 desarrolladores**.

**ROI esperado:**
- Reducción 70% de trámites manuales
- Retención de estudiantes +5.9%
- Satisfacción de usuario 3.5 → 4.5/5
- Cumplimiento normativo 100%

### Aprobación Requerida

**Junta Directiva debe autorizar:**
1. ✅ Presupuesto: $240K (in-house) o $50K (hybrid)
2. ✅ Timeline: 15 semanas fase crítica
3. ✅ Equipo: 2 dev + 1 DBA + 1 DevOps
4. ✅ Inicio: Julio 2026

---

**Preparado por:** Equipo de Arquitectura ADES  
**Fecha:** Junio 11, 2026  
**Próxima revisión:** Septiembre 2026

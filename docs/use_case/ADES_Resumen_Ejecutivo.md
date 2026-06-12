# ADES Nevadi — Resumen Ejecutivo
## Análisis de Brecha Funcional y Plan de Desarrollo 2026-2027

**Preparado para:** Junta Directiva Instituto Nevadi  
**Fecha:** Junio 2026  
**Versión:** 1.0  
**Duración de presentación:** 10 minutos

---

## 🎯 Situación Actual

### Logros 2024-2026
✅ **Sistema base operacional** — ADES funciona en 3 planteles con 3 niveles educativos  
✅ **1,980 alumnos, 168 profesores, 3,483 usuarios** — en operación diaria  
✅ **10 fases completadas** — de 35 planeadas  
✅ **175+ endpoints REST** — API estable y documentada  
✅ **Inteligencia Artificial integrada** — alertas de riesgo académico, learning paths  
✅ **Conformidad SEP/UAEMEX** — calendarios, planes de estudio, reportes  

### Capacidad Actual
| Área | Estado |
|------|--------|
| Calificaciones & Libreta | ✅ Completo |
| Asistencias | ✅ Completo |
| Tareas & Entregas | ✅ Completo |
| Evaluación Docente 360° | ✅ Completo |
| Gestión Conductual | ✅ Completo |
| Dashboard BI (Superset) | ✅ Completo |
| Comunicados & Notificaciones | ✅ Completo |
| Gamificación (Badges) | ✅ Completo |
| Certificados Digitales | ✅ Completo |

---

## 🔍 Análisis de Brecha: Qué Falta

### Categorización de Necesidades Pendientes

#### 🔴 **Críticas** — Afectan operación diaria y normativa (12 casos de uso)
Ejemplos:
- Reinscripción masiva de alumnos (proceso anual, manual actualmente)
- Backup y recuperación ante desastres (sin plan actual)
- Gestión de documentos escaneados (CURP, actas — en carpetas físicas)
- Cierre formal de períodos académicos

**Impacto:** Sin estos, Instituto expone datos y operación es más lenta.  
**Esfuerzo:** 40-50 horas de desarrollo

#### 🟡 **Altos** — Mejoran experiencia significativamente (15 casos de uso)
Ejemplos:
- Detección de plagio en tareas
- Foros por materia (colaboración estudiantes)
- Análisis de patrones conductuales con IA
- Expediente médico completo con incidentes

**Impacto:** Mejoran calidad pedagógica y eficiencia administrativa.  
**Esfuerzo:** 80-100 horas de desarrollo

#### 🟢 **Medios** — Nice-to-have, complementos (14 casos de uso)
Ejemplos:
- Retroalimentación en vídeo en tareas
- Programa de bienestar integral
- Eventos y actividades especiales
- Customización de dashboards por usuario

**Impacto:** Engagement y satisfacción de usuarios.  
**Esfuerzo:** 40-60 horas de desarrollo

---

## 📊 Números Clave del Análisis

| Métrica | Valor |
|---------|-------|
| **Total de Casos de Uso identificados** | **195** |
| Implementados (Fases 1-10) | **56** (29%) |
| Pendientes (Fases 27-35) | **139** (71%) |
| **Críticos sin hacer** | **12** |
| **Horas estimadas faltantes** | **~300 horas** |
| **Duración estimada** | **15 semanas** de trabajo |
| **Equipo requerido** | 2 dev + 1 DBA + 1 DevOps |

---

## 🚀 Plan de Desarrollo (Roadmap Priorizado)

### Fase 27: Q3 2026 (Julio-Agosto) — **SEGURIDAD Y BASE**
**Objetivo:** Asegurar estabilidad, recuperación y automatización básica.  
**Entregables:**
- ✅ Backup automático diario + Plan de recuperación ante desastres
- ✅ Reinscripción masiva para nuevo ciclo
- ✅ Cierre formal de períodos (bloqueo de edición)
- ✅ Gestión de aulas y espacios físicos

**Valor para Instituto:** Operación segura, sin riesgo de pérdida de datos.  
**Inversión:** 6 semanas | 2-3 desarrolladores

---

### Fase 28: Q4 2026 (Sep-Oct) — **DOCUMENTACIÓN Y EXPEDIENTE DIGITAL**
**Objetivo:** Migrar de carpetas físicas a expedientes digitales con OCR.  
**Entregables:**
- ✅ Integración Paperless-ngx (escaneado con OCR automático)
- ✅ Expediente digital consolidado por alumno
- ✅ Generación de actas oficiales con firma digital
- ✅ Búsqueda integrada en ADES

**Valor:** Reducción 80% de trámites manuales, documentos seguros.  
**Inversión:** 6 semanas | 2 desarrolladores + 1 administrativo

---

### Fase 29: Q4 2026 (Oct-Nov) — **PROTECCIÓN DE DATOS Y SEGURIDAD AVANZADA**
**Objetivo:** Cumplimiento de normas, encripción y MFA.  
**Entregables:**
- ✅ Encripción de datos sensibles (CURP, RFC)
- ✅ Autenticación multifactor (TOTP)
- ✅ Gestión centralizada de secretos (Vault)

**Valor:** Conformidad LRFD, protección de privacidad de alumnos.  
**Inversión:** 5 semanas | 1 DBA + 1 DevOps

---

### Fase 30: Q1 2027 (Nov-Dic) — **EVALUACIÓN DIAGNÓSTICA E IA PREDICTIVA**
**Objetivo:** Detección temprana de riesgos, predicción de abandono.  
**Entregables:**
- ✅ Evaluaciones diagnósticas automatizadas
- ✅ Detección de plagio en trabajos
- ✅ Modelo ML de predicción de abandono escolar

**Valor:** Intervención temprana, retención de estudiantes.  
**Inversión:** 6 semanas | 2 developers (1 especialista ML)

---

### Fases 31-35: Q1-Q2 2027 — **EXCELENCIA OPERATIVA**
**Contenido:** Foros, cumplimiento regulatorio, disaster recovery, integraciones SEP/UAEMEX, dashboards avanzados.

**Total 2026-2027:** ~300 horas ≈ 4 meses de desarrollo continuo

---

## 💰 Inversión Requerida

### Opción A: Desarrollo In-House (Recomendado)
```
Inversión Inicial:
├── Equipo: 2 dev + 1 DBA + 1 DevOps .......................... $180,000 USD/año
├── Herramientas (Vault, Turnitin, SendGrid) .................. $5,000 USD/año
├── Infraestructura escalada (servidor DR) .................... $2,000 USD/mes
└── Capacitación + Buffer .................................... $10,000 USD
    
TOTAL AÑO 1: $235,000 - $250,000 USD
```

### Opción B: Outsourcing Parcial (Híbrido)
```
Fases críticas (27-29) con proveedor externo:
├── Fases 27-29: 150 horas x $100/hora ....................... $15,000 USD
├── Fases 30-35: In-house (interno) ........................... Incluido
└── Supervisión arquitectónica ................................ 20 h/mes = $2,000 USD/mes

TOTAL AÑO 1: $45,000 - $55,000 USD
```

---

## 📈 Beneficios Esperados (Post-Implementación)

| Beneficio | Antes | Después | Ganancia |
|-----------|-------|---------|----------|
| Trámites manuales | 40% | 5% | ⬇️ 87% |
| Tiempo de respuesta (admin) | 2 días | 2 horas | ⬇️ 95% |
| Seguridad de datos | Media | Alta | ⬆️ Crítico |
| Satisfacción de usuario | 3.5/5 | 4.5/5 | ⬆️ 29% |
| Retención de estudiantes | 85% | 90% | ⬆️ 5.9% |
| Carga de profesor (admin) | 15 horas/semana | 8 horas/semana | ⬇️ 47% |

---

## 🎓 Recomendación Institucional

### Escenario 1: Acelerado (Recomendado) 
✅ **Implementar Fases 27-29 en 2026** (críticas)  
✅ **Fases 30-35 en 2027** (mejoras)  
**Presupuesto:** $240,000 USD  
**Timeline:** 15 meses hasta máxima capacidad  
**Riesgo:** Bajo (desarrollo progresivo, no disruptivo)

### Escenario 2: Conservador
✅ **Implementar Fases 27-28 en 2026**  
⏳ **Fases 29-35 en 2027-2028**  
**Presupuesto:** $120,000 USD (anual)  
**Timeline:** 24 meses hasta máxima capacidad  
**Riesgo:** Medio (deuda técnica acumula)

### Escenario 3: Mínimo (No recomendado)
⚠️ **Mantener operación actual sin nuevas fases**  
**Presupuesto:** $0 (solo mantenimiento)  
**Timeline:** Indefinido  
**Riesgo:** ALTO — sin backup, sin conformidad normativa, obsolescencia gradual

---

## ✅ Próximos Pasos (Julio 2026)

### Semana 1
- [ ] Aprobación de Junta Directiva (Escenario 1 recomendado)
- [ ] Identificación de equipo de desarrollo (in-house vs outsource)
- [ ] Definición de presupuesto y cronograma

### Semana 2-3
- [ ] Contratos firmados (si es outsource)
- [ ] Setup de ambiente de desarrollo
- [ ] Kickoff meeting con equipo técnico

### Semana 4+
- [ ] Inicio Fase 27 (backup + reinscripción masiva)
- [ ] Comunicación con docentes/padres (roadmap de mejoras)

---

## 📞 Contacto y Preguntas

| Rol | Responsable |
|-----|-----------|
| **Director Proyecto ADES** | Israel (Dev Lead) |
| **Contacto Técnico** | Equipo de Arquitectura |
| **Coordinación Administrativa** | Coordinador Administrativo ADES |

---

## Anexos

- **Anexo A:** Catálogo Completo de 195 Casos de Uso (`ADES_Nevadi_Catalogo_Casos_Uso_v1.md`)
- **Anexo B:** Plan Detallado de Tareas y Checklist (`ADES_Nevadi_Plan_Tareas_Implementacion_v1.md`)
- **Anexo C:** Matriz de Riesgos y Mitigación (disponible bajo solicitud)
- **Anexo D:** Especificaciones Técnicas Detalladas (disponible bajo solicitud)

---

**Documento preparado por:** Claude (Anthropic)  
**Fecha:** Junio 11, 2026  
**Confidencialidad:** Interno Instituto Nevadi

# 📚 ADES Nevadi — Índice de Documentación

**Centro de navegación para toda la documentación del sistema**  
Actualizado: 2026-07-08 | Status: ✅ Organizado

---

## 📋 Documentación Principal

### 📖 Guías de Usuario
- **[README Usuario](./README_USUARIO.md)** — Guía introductoria para usuarios finales
- **[Manual Usuario Completo](./manual-usuario.md)** — Documentación exhaustiva de módulos (80KB+)
- **[Manual Usuario ADES](./manual_usuario_ades.md)** — Manual técnico alternativo

### 🚀 Deployment y Operación
- **[Rollout Guide](./planes/ROLLOUT-GUIDE.md)** — Plan de despliegue por fases
- **[Deployment Validation](./técnico/DEPLOYMENT-VALIDATION.md)** — Checklist de validación
- **[Disaster Recovery Plan](./disaster_recovery_plan.md)** — Plan de recuperación ante desastres

### 📊 Auditorías y Reportes
- **[Auditorías 2026](./auditorias/2026/)** — Auditorías de seguridad y optimización
  - Índice maestro, análisis técnico, plan de remediación, checklists
- **[Informes de Correcciones](./informes/)** — Reportes de fixes y mejoras
  - Correcciones finales, limpiezas, comparativas de testing

---

## 🔧 Documentación Técnica

### Arquitectura y Decisiones
- **[ADRs (Architecture Decision Records)](./DECISIONS/)** — Decisiones arquitectónicas documentadas
  - ADR-0001: Genesis Architecture
  - ADR-0008: Hexagonal Spring Boot Migration
  - ADR-0012: Claves CCT por Nivel

### Base de Datos
- **[ER Diagram](./ER_DIAGRAM.md)** — Diagrama entidad-relación completo
- **[Auditoría SQL](./auditoria.sql)** — Script de auditoría de triggers
- **[Guía de Estilo SQL](./guía%20de%20estilo%20sql.md)** — Estándares SQL para el proyecto
- **[Instalación PostgreSQL](./instalación%20postgresql.md)** — Setup inicial

### Modelos y Análisis
- **[Oracle APEX Análisis Exhaustivo](./ORACLE_APEX_EXHAUSTIVE_ANALYSIS.md)** — Mapping APEX → Angular (58KB+)
- **[APEX to Angular Mapping](./técnico/analysis/apex_to_angular_mapping.md)** — Referencias de componentes
- **[Optimistic Locking Implementation](./OPTIMISTIC_LOCKING_IMPLEMENTATION.md)** — Patrón concurrencia

### Infraestructura
- **[Instalación PostgreSQL](./instalación%20postgresql.md)** — Setup BD
- **[Instalación PgPool](./instalación%20pgpool.md)** — Configuración load balancer

---

## 📝 Cambios Recientes

### [Modificaciones 7-8 Julio 2026](./MODIFICACIONES_7_8_JULIO_2026.md) ✨ NUEVO
Documento exhaustivo cubriendo:
- **Optimización 16 Puntos** (FASE 1, 2, 3 completadas)
- **Sistema de Registro de Calificaciones** (arquitectura, endpoints, triggers)
- **Generación de Boletas** (FastAPI + Jinja2 + WeasyPrint)
- **Input Formatters y Máscaras** (CURP, RFC, Email, Teléfono)
- **Validación de Datos** (validators frontend/backend)

### [Planes de Pruebas Integral](./plan_pruebas_integral.md)
- Cobertura completa de módulos
- Estrategia E2E con Playwright
- Matrix de pruebas por funcionalidad

### [Roadmap Público](./ROADMAP.md)
- Fases 1-39+ planificadas
- Sprint actual y próximos
- Hitos de entrega

---

## 🎯 Casos de Uso

### [Casos de Uso por Módulo](./use_case/)
- Especificaciones funcionales
- Actores y precondiciones
- Flujos principales y alternos

---

## 📅 Histórico y Sprints

### [Histórico](./historico/)
- Logs de sesiones anteriores
- Cambios de arquitectura

### [Sprints](./sprints/)
- Tracking de sprints completados
- Avance por módulo

---

## 🔒 Seguridad

### [Documentación de Seguridad](./security/)
- Políticas de seguridad
- Checklist de hardening
- Procedimientos de auditoría

---

## 🔗 Integración de Horarios

### [aSc TimeTables Integration](./horarios-integracion/)
- Export/import XML
- Mapping de períodos
- Sincronización

---

## 📦 Referencias Rápidas

| Documento | Propósito | Ubicación |
|-----------|-----------|-----------|
| CLAUDE.md | Instrucciones para Claude Code | Raíz del proyecto |
| README.md | Descripción general del proyecto | Raíz del proyecto |
| docker-compose.yml | Stack dockerizado | Raíz del proyecto |
| DECISIONS/ | ADRs arquitectónicas | Raíz (no movida) |

---

## 🚀 Comenzar

**Para nuevos desarrolladores:**
1. Leer [README Usuario](./README_USUARIO.md)
2. Revisar [CLAUDE.md](../CLAUDE.md) en raíz
3. Explorar [ADRs](./DECISIONS/) para decisiones clave
4. Consultar [Manual Usuario](./manual-usuario.md) para casos de uso

**Para DevOps/SRE:**
1. [Rollout Guide](./planes/ROLLOUT-GUIDE.md)
2. [Deployment Validation](./técnico/DEPLOYMENT-VALIDATION.md)
3. [Disaster Recovery](./disaster_recovery_plan.md)

**Para Auditoría/Compliance:**
1. [Auditorías 2026](./auditorias/2026/)
2. [Security Docs](./security/)
3. [Plan de Pruebas](./plan_pruebas_integral.md)

---

**Última actualización:** 2026-07-08  
**Organizador:** Claude Haiku 4.5  
**Status:** ✅ Reorganización completa

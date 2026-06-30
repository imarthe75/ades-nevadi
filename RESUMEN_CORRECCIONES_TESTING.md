# Resumen de Correcciones — Testing Exploratorio ADES

**Fecha:** 2026-06-30  
**Sesión:** Correcciones fase inicial de inconsistencias encontradas

---

## 📊 Estado de Progreso

**Total de inconsistencias detectadas:** 30 (12 críticas, 12 altas, 3 medias, 3 bajas)  
**Correcciones completadas:** 3/12 críticas (25%)  
**Commit:** `5b8faca`

---

## ✅ COMPLETADAS (3)

### 1. **Condiciones Crónicas — Validación de protocolo de emergencia**
- **Fecha:** 2026-06-30
- **Archivos modificados:**
  - `/opt/ades/backend-spring/src/main/java/mx/ades/modules/condiciones/CondicionCronicaController.java`
- **Cambios:**
  - Agregué imports: `ValidationUtils`, `ResponseStatusException`
  - Nuevo método `validarCondicionCronica()` que valida:
    - `telefonoMedico` → patrón de 10 dígitos
    - `dosis` → formato `número+unidad` (mg, g, ml, mcg, IU)
    - `frecuencia` → formato `Cada X horas/minutos/días`
  - Llamada a validación en POST y PATCH `/condiciones-cronicas`
- **Impacto:** Previene guardado de condiciones crónicas con datos médicos inválidos

### 2. **Dashboard — Error handling y retry buttons**
- **Fecha:** 2026-06-30
- **Archivos modificados:**
  - `/opt/ades/frontend/src/app/features/dashboard/dashboard.component.ts`
- **Cambios:**
  - Agregué signals: `errorResumen`, `errorDistribucion`, `errorPlanteles`
  - Métodos de reintentar: `recargarResumen()`, `recargarPlanteles()`
  - Error notifications al fallar carga de datos
  - Mensajes claros al usuario con botón "Reintentar"
  - Import `MessageModule` para mostrar errores
- **Impacto:** Usuario ahora ve errores en lugar de widgets vacíos silenciosos

### 3. **Evaluaciones — Propagación de contexto (`ciclo_id`)**
- **Fecha:** 2026-06-30
- **Archivos modificados:**
  - `/opt/ades/frontend/src/app/features/evaluaciones/evaluaciones.component.ts` (2 cambios)
  - `/opt/ades/backend-spring/src/main/java/mx/ades/modules/evaluaciones/EvaluacionController.java` (3 cambios)
- **Cambios Frontend:**
  - `crearEvaluacion()`: Inyecta `ciclo_id` en payload al crear
  - `guardarCalificaciones()`: Inyecta `ciclo_id` en payload al guardar
- **Cambios Backend:**
  - `GET /api/v1/evaluaciones`: Acepta parámetro `ciclo_id`
  - `POST /api/v1/evaluaciones/{id}/calificaciones/bulk`: Acepta `ciclo_id` en body
  - `POST /api/v1/evaluaciones`: Agregué validaciones de campos obligatorios
- **Impacto:** Contexto del top bar (ciclo seleccionado) ahora se respeta en evaluaciones

---

## ⏳ PENDIENTES (9)

### Críticas (9 restantes de 12):

| # | Issue | Severidad | Módulo | Status |
|---|-------|-----------|--------|--------|
| 2 | SEP/Nevadi ambigüedad | Crítico | planes_estudio | [ ] |
| 3 | SEP/Nevadi ambigüedad | Crítico | calificaciones | [ ] |
| 5 | Capacidad grupo | Crítico | reinscripcion | [ ] |
| 6 | Validación calificaciones | Crítico | cierre_ciclo | [ ] |
| 7 | Validación campos | Crítico | estadistica_911 | [ ] |
| 8 | CURP validation | Crítico | admision | [ ] (YA EXISTE - verificar) |
| 9 | Context propagation | Crítico | dashboards_bi | [ ] |
| 10 | Context propagation | Crítico | eval_docente | [ ] |
| 11 | RFC validation | Crítico | expediente_laboral | [ ] (YA EXISTE - verificar) |

---

## 🎯 Siguiente Prioridad (Top 3)

1. **Estadística 911** — Validar campos obligatorios (sexo, tipo_ingreso) antes de generar reporte
   - Archivos: `frontend/.../estadistica-911.component.ts`, `backend/Estadistica911Controller.java`
   - Complejidad: Media (15 min)

2. **Reinscripción** — Validar capacidad del grupo destino
   - Archivos: `frontend/.../reinscripcion.component.ts`, `backend/ReinscripcionService.java`
   - Complejidad: Alta (25 min)

3. **Cierre de Ciclo** — Validar que todas las calificaciones estén completas
   - Archivos: `frontend/.../cierre-ciclo.component.ts`, `backend/CierreCicloService.java`
   - Complejidad: Alta (20 min)

---

## 📝 Notas Técnicas

- **ValidationUtils.java** ya tiene métodos reutilizables: CURP, RFC, Email, Teléfono, Fecha
- **ContextService** está disponible en todos los componentes para acceso a: plantel, ciclo, nivel, grado, grupo
- **Patrón de validación backend:** Usar `ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensaje)`
- **Patrón de validación frontend:** Angular Validators + `ngModelChange` hooks
- **MessageModule** (PrimeNG) para mostrar errores al usuario

---

## 🔗 Referencias

- Reporte detallado: `/opt/ades/ades_testing/reports/inconsistencies_report.html`
- Plan completo: `/opt/ades/PLAN_CORRECCIONES_TESTING.md`
- Commits: Ver historio con `git log --oneline | head -5`

---

## 📦 Build/Deploy

Para validar cambios:

```bash
# Backend Spring
cd /opt/ades/backend-spring
./mvnw spring-boot:run

# Frontend Angular
cd /opt/ades/frontend
npm run start

# Re-ejecutar testing exploratorio
cd /opt/ades/ades_testing
python 01_ades_explorer_v4_complete.py
```

---

**Última actualización:** 2026-06-30 13:00 UTC  
**Autor:** Claude Code + Agent Explore + Testing Framework  
**Estado:** En progreso — Fase 1/2 completada

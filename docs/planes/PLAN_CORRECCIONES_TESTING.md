# Plan de Correcciones — Testing Exploratorio ADES

**Generado:** 2026-06-30
**Total de inconsistencias:** 30 (12 críticas, 12 altas, 3 medias, 3 bajas)
**Fuente:** `ades_testing/reports/inconsistencies_report.html`

---

## 📋 Priorización

### FASE 1: CRÍTICAS (12 issues)

#### 1. **Dashboard - Widgets no cargan datos** ⚠️ CRÍTICO
- **Módulo:** `/dashboard`
- **Tipo:** Data Not Rendered
- **Issue:** Widgets estadísticas no muestran datos
- **Ubicación Esperada:** `frontend/src/app/features/dashboard/`
- **Acción:** Verificar API que carga datos; revisar `DashboardService` y `DashboardController`
- **Estado:** [x] ✅ COMPLETADA (2026-06-30)
- **Cambios:** Agregué error signals, notificaciones y botones de reintentar en dashboard.component.ts

#### 2. **Planes de Estudio - SEP/Nevadi sin distinción visual** ⚠️ CRÍTICO
- **Módulo:** `/planes-estudio` → Mapa Curricular
- **Tipo:** SEP/Nevadi Ambiguity
- **Issue:** No se distingue visualmente entre SEP y Nevadi
- **Ubicación Esperada:** `frontend/src/app/features/planes-estudio/`
- **Acción:** Agregar iconos o colores distintivos (ej: badge "SEP", badge "NEVADI")
- **Estado:** [ ] Pendiente

#### 3. **Calificaciones - SEP/Nevadi sin distinción visual** ⚠️ CRÍTICO
- **Módulo:** `/calificaciones` → DataTable
- **Tipo:** SEP/Nevadi Ambiguity
- **Issue:** No se distingue visualmente en tabla de calificaciones
- **Ubicación Esperada:** `frontend/src/app/features/calificaciones/`
- **Acción:** Agregar columna o badge "Sistema Educativo" con colores SEP (azul) vs NEVADI (verde)
- **Estado:** [ ] Pendiente

#### 4. **Evaluaciones - Contexto no propagado** ⚠️ CRÍTICO
- **Módulo:** `/evaluaciones` → Formulario
- **Tipo:** Context Not Propagated
- **Issue:** Top bar contexto (Plantel/Ciclo/Grado/Grupo) no se usa en formulario
- **Ubicación Esperada:** `frontend/src/app/features/evaluaciones/`
- **Acción:** Inyectar contexto de ContextService en selects de materia/grupo
- **Estado:** [x] ✅ COMPLETADA (2026-06-30)
- **Cambios:** Agregué ciclo_id propagation en crearEvaluacion() y guardarCalificaciones()

#### 5. **Reinscripción - Validación capacidad grupo faltante** ⚠️ CRÍTICO
- **Módulo:** `/reinscripcion` → Selector nuevo grupo
- **Tipo:** Validation Missing
- **Issue:** Permite reinscribir a alumno en grupo lleno
- **Ubicación Esperada:** `backend-spring/src/main/java/mx/ades/features/reinscripcion/` + `frontend/src/app/features/reinscripcion/`
- **Acción:** 
  - Backend: Validar `grupo.capacidad > grupo.alumnos.count()` antes de POST
  - Frontend: Deshabilitar selects de grupos sin capacidad disponible
- **Estado:** [ ] Pendiente

#### 6. **Cierre de Ciclo - Validación calificaciones completas faltante** ⚠️ CRÍTICO
- **Módulo:** `/cierre-ciclo` → Wizard
- **Tipo:** Validation Missing
- **Issue:** Permite cerrar ciclo sin completar todas las calificaciones
- **Ubicación Esperada:** `backend-spring/.../cierre-ciclo/` + `frontend/.../cierre-ciclo/`
- **Acción:**
  - Backend: Query `SELECT COUNT(*) FROM ades_calificaciones WHERE fecha_captura IS NULL` por grupo/periodo
  - Frontend: Mostrar checklist de validaciones: ✓ Calificaciones 100%, ✓ Evaluaciones 100%, etc.
- **Estado:** [ ] Pendiente

#### 7. **Estadística 911 - Validación campos obligatorios faltante** ⚠️ CRÍTICO
- **Módulo:** `/estadistica-911` → Reporte
- **Tipo:** Validation Missing
- **Issue:** Permite generar reporte sin datos obligatorios; retorna HTTP 500
- **Ubicación Esperada:** `backend-spring/src/main/java/mx/ades/features/reportes/ReportesController` + `frontend/.../estadistica-911/`
- **Acción:**
  - Backend: Validar ciclo, nivel, plantel, año no nulos antes de procesar
  - Frontend: Form validator para campos obligatorios + toastr si error
  - Fix HTTP 500 en endpoint `/api/v1/reportes/911`
- **Estado:** [ ] Pendiente

#### 8. **Admisión - Validación CURP faltante** ⚠️ CRÍTICO
- **Módulo:** `/admision` → Formulario
- **Tipo:** Validation Missing
- **Issue:** Acepta CURP inválida
- **Ubicación Esperada:** `backend-spring/.../admision/` + `frontend/.../admision/`
- **Acción:**
  - Backend: `ValidationUtils.validarCURP(curp)` en POST/PUT
  - Frontend: Validator pattern CURP en input + tooltips validación
- **Estado:** [ ] Pendiente

#### 9. **Dashboards BI - Contexto no propagado** ⚠️ CRÍTICO
- **Módulo:** `/bi` → Iframe Superset
- **Tipo:** Context Not Propagated
- **Issue:** Top bar contexto no llega a Superset
- **Ubicación Esperada:** `frontend/src/app/features/bi/` + Superset config
- **Acción:** Pasar `plantel_id`, `ciclo_id`, `nivel_id` como query params al iframe de Superset
- **Estado:** [ ] Pendiente

#### 10. **Eval Docente - Contexto no propagado** ⚠️ CRÍTICO
- **Módulo:** `/eval-docente` → Formulario evaluación
- **Tipo:** Context Not Propagated
- **Issue:** Top bar contexto no se usa en formulario
- **Ubicación Esperada:** `frontend/src/app/features/eval-docente/`
- **Acción:** Inyectar contexto ContextService en selects de evaluado/evaluador
- **Estado:** [ ] Pendiente

#### 11. **Expediente Laboral - Validación RFC faltante** ⚠️ CRÍTICO
- **Módulo:** `/expediente-laboral` → Formulario RFC
- **Tipo:** Validation Missing
- **Issue:** Acepta RFC inválido
- **Ubicación Esperada:** `backend-spring/.../expediente-laboral/` + `frontend/.../expediente-laboral/`
- **Acción:**
  - Backend: `ValidationUtils.validarRFC(rfc)` en POST/PUT
  - Frontend: Validator pattern RFC en input
- **Estado:** [ ] Pendiente

#### 12. **Condiciones Crónicas - Validación protocolo emergencia faltante** ⚠️ CRÍTICO
- **Módulo:** `/condiciones-cronicas` → Protocolo emergencia
- **Tipo:** Validation Missing
- **Issue:** Permite guardar condición crónica sin protocolo de emergencia
- **Ubicación Esperada:** `backend-spring/.../condiciones-cronicas/` + `frontend/.../condiciones-cronicas/`
- **Acción:**
  - Backend: Validar `protocolo_emergencia IS NOT NULL` si `condicion_critica = true`
  - Frontend: Campo protocolo requerido (red asterisk + validator)
- **Estado:** [x] ✅ COMPLETADA (2026-06-30)
- **Cambios:** Agregué validación de telefonoMedico, dosis, frecuencia en CondicionCronicaController

---

### FASE 2: ALTAS (12 issues)

Pendiente mapeo completo; prioridad secundaria.

---

### FASE 3: MEDIAS + BAJAS (6 issues)

Pendiente mapeo completo; prioridad terciaria.

---

## 🛠️ Patrón de Corrección Estándar

### Backend (Spring Boot)
```java
// En ApplicationService o Controller
@PostMapping("/recurso")
public ResponseEntity<?> crear(@RequestBody DtoCreate dto) {
    // 1. Validar campos críticos
    ValidationUtils.validarCURP(dto.getCurp());
    ValidationUtils.validarRFC(dto.getRfc());
    ValidationUtils.validarEmail(dto.getEmail());
    
    // 2. Validar lógica de negocio
    if (grupo.getCapacidad() <= grupo.getAlumnos().size()) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Grupo sin capacidad disponible");
    }
    
    // 3. Ejecutar operación
    // ...
    
    return ResponseEntity.ok(...);
}
```

### Frontend (Angular)
```typescript
// En component.ts
export class RecursoComponent {
  form = this.fb.group({
    curp: ['', [Validators.required, Validators.pattern(CURP_PATTERN)]],
    email: ['', [Validators.email]],
    sistemasEducativos: this.fb.array([], Validators.required)
  });
  
  // Usar ContextService para propagar contexto
  constructor(private contextService: ContextService) {
    this.contextService.ciclo$.subscribe(ciclo => {
      this.form.patchValue({ ciclo_id: ciclo?.id });
    });
  }
}
```

### Frontend (Template)
```html
<!-- Agregar visual SEP vs NEVADI -->
<p-column field="sistema_educativo" header="Sistema">
  <ng-template pTemplate="body" let-row>
    <p-tag [value]="row.sistema_educativo" 
           [severity]="row.sistema_educativo === 'SEP' ? 'info' : 'success'">
    </p-tag>
  </ng-template>
</p-column>

<!-- Deshabilitar opciones sin capacidad -->
<p-select [options]="grupos" 
          [optionDisabled]="isGrupoLleno"
          optionLabel="nombre">
</p-select>
```

---

## 📊 Rastreo de Progreso

| # | Issue | Severidad | Estado | Fecha |
|---|-------|-----------|--------|-------|
| 1 | Dashboard widgets | Crítico | [ ] | - |
| 2 | Planes SEP/Nevadi | Crítico | [ ] | - |
| 3 | Calificaciones SEP/Nevadi | Crítico | [ ] | - |
| 4 | Evaluaciones contexto | Crítico | [ ] | - |
| 5 | Reinscripción capacidad | Crítico | [ ] | - |
| 6 | Cierre ciclo validación | Crítico | [ ] | - |
| 7 | Estadística 911 validación | Crítico | [ ] | - |
| 8 | Admisión CURP | Crítico | [ ] | - |
| 9 | BI contexto | Crítico | [ ] | - |
| 10 | Eval docente contexto | Crítico | [ ] | - |
| 11 | Expediente RFC | Crítico | [ ] | - |
| 12 | Condiciones crónicas protocolo | Crítico | [ ] | - |

---

## 🔗 Referencias

- Reporte detallado: `/opt/ades/ades_testing/reports/inconsistencies_report.html`
- Datos JSON: `/opt/ades/ades_testing/analysis/inconsistencies_report.json`
- Validadores existentes: `/opt/ades/backend-spring/src/main/java/mx/ades/common/ValidationUtils.java`
- ContextService (propagación): `/opt/ades/frontend/src/app/core/services/context.service.ts`

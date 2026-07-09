# FASE 2: Backend Extensions - Planeaciones Semanales
**Estado:** ✅ COMPLETADO (Backend) | Fecha: 2026-07-08
**Tiempo:** Parte 1 de 3 (Backend + Frontend + Testing)

---

## Resumen de Cambios Backend

### 1. PlaneacionQueryService - Nuevas Queries
**Archivo:** `backend-spring/src/main/java/mx/ades/modules/planeacion/query/PlaneacionQueryService.java`

**Métodos Agregados:**

#### `getPlaneacionSemanal(grupoId, trimestre, semana, materiaId)`
```java
/**
 * Obtener planeación de una semana específica (FASE 2 - Planeaciones Semanales).
 * Busca por trimestre y número de semana.
 *
 * @param grupoId UUID del grupo
 * @param trimestre 1, 2, o 3
 * @param semana 1-40
 * @param materiaId Opcional: filtrar por materia
 * @return List<Map> con: planeacion_id, numero_trimestre, numero_semana, modalidad,
 *         fecha_planeada, fecha_fin, descripcion_actividades, recursos_didacticos,
 *         tema_id, nombre_tema, materia_id, nombre_materia, competencia_codigo,
 *         competencia_nombre, es_completado, fecha_ejecucion, comentarios_profesor
 */
```

#### `getCoberturaSemanal(grupoId, trimestre)`
```java
/**
 * Obtener cobertura por semana (FASE 2 - Dashboard semanal).
 * Calcula % temas impartidos vs planeados por semana.
 *
 * @param grupoId UUID del grupo
 * @param trimestre Opcional: filtrar por trimestre
 * @return List<Map> con: numero_semana, numero_trimestre, temas_planeados,
 *         temas_impartidos, pct_cobertura
 */
```

#### `getValidacionTrimestralSemanal(trimestre, semana)`
```java
/**
 * Validar que trimestre/semana están dentro del rango válido.
 * Retorna booleanos para validación en frontend.
 *
 * @param trimestre 1-3
 * @param semana 1-40
 * @return Map: { trimestre_valido, semana_valida, ambas_validas }
 */
```

---

### 2. PlaneacionController - Nuevos Endpoints
**Archivo:** `backend-spring/src/main/java/mx/ades/modules/planeacion/PlaneacionController.java`

#### GET `/api/v1/planeacion/semanal`
```
Query Params:
  - grupo_id (required): UUID
  - trimestre (optional): 1, 2, o 3
  - semana (optional): 1-40
  - materia_id (optional): UUID

Response: List[Map] con planeaciones de la semana
```

#### GET `/api/v1/planeacion/cobertura-semanal`
```
Query Params:
  - grupo_id (required): UUID
  - trimestre (optional): 1, 2, o 3

Response: List[Map] con cobertura % por semana para dashboard
```

#### POST `/api/v1/planeacion/semanal`
```json
Request Body {
  "grupo_id": "uuid",
  "tema_id": "uuid",
  "competencia_id": "uuid",  // Opcional
  "numero_trimestre": 1,     // 1-3
  "numero_semana": 5,        // 1-40
  "modalidad": "PRESENCIAL", // PRESENCIAL | VIRTUAL | HIBRIDA
  "fecha_planeada": "2026-07-15",
  "fecha_fin": "2026-07-20",
  "descripcion_actividades": "...",
  "recursos_didacticos": "..."
}

Response: Map {
  "id": "uuid",
  "trimestre": 1,
  "semana": 5,
  "modalidad": "PRESENCIAL",
  "estado": "PLANEADO"
}

Validaciones:
  - trimestre ∈ {1, 2, 3}
  - semana ∈ {1..40}
  - modalidad ∈ {PRESENCIAL, VIRTUAL, HIBRIDA}
  - tema_id debe existir
```

#### PATCH `/api/v1/planeacion/{id}/semanal`
```json
Request Body (todos optional) {
  "numero_trimestre": 1,
  "numero_semana": 5,
  "modalidad": "VIRTUAL",
  "fecha_fin": "2026-07-20",
  "descripcion_actividades": "...",
  "recursos_didacticos": "..."
}

Response: Map {
  "id": "uuid",
  "actualizado": true
}
```

---

### 3. PlaneacionCommandService - Nuevos Métodos
**Archivo:** `backend-spring/src/main/java/mx/ades/modules/planeacion/command/PlaneacionCommandService.java`

#### `crearPlaneacionSemanal(...)`
```java
/**
 * FASE 2: Crear planeación con campos semanales.
 * Valida trimestre (1-3), semana (1-40), modalidad.
 *
 * @throws ResponseStatusException 400 Bad Request si validaciones fallan
 * @throws ResponseStatusException 404 Not Found si tema no existe
 */
```

#### `actualizarPlaneacionSemanal(...)`
```java
/**
 * FASE 2: Actualizar planeación semanal.
 * Todos los parámetros opcionales — solo actualiza los proporcionados.
 *
 * @throws ResponseStatusException 400 Bad Request si validaciones fallan
 * @throws ResponseStatusException 404 Not Found si planeación no existe
 */
```

---

## Status de Compilación

✅ **Compilación exitosa** — sin errores ni warnings en PlaneacionController y servicios.

```bash
$ cd /opt/ades/backend-spring && ./mvnw -q compile
# ✓ 0 errores
```

---

## Próximos Pasos: Frontend (FASE 2 Parte 2)

### Componente Angular a Extender
**Archivo:** `frontend/src/app/features/planeacion/planeacion.component.ts`

**Cambios requeridos:**

1. **Selectores adicionales:**
   - Selector Trimestre (dropdown 1-3)
   - Selector Semana (dropdown 1-40)
   - Selector Modalidad (dropdown PRESENCIAL/VIRTUAL/HIBRIDA)

2. **Formulario de creación:**
   - Campos: tema, trimestre, semana, modalidad, fecha, descripción, recursos
   - Validación: trimestre ∈ {1-3}, semana ∈ {1-40}, modalidad válida
   - Submit → POST `/api/v1/planeacion/semanal`

3. **Tabla actualizada:**
   - Columnas: tema, trimestre, semana, modalidad, estado, acciones
   - Filtros: por trimestre + semana
   - Estado: PLANEADO | IMPARTIDO | PENDIENTE

4. **Dashboard semanal:**
   - Query: GET `/api/v1/planeacion/cobertura-semanal?grupo_id=...&trimestre=...`
   - Mostrar: % cobertura por semana
   - Gráfico: ProgressBar PrimeNG por semana

---

## Testing (FASE 2 Parte 3)

### Playwright E2E Tests
```typescript
describe('Planeación Semanal', () => {
  test('crear planeación con trimestre y semana', async ({ page }) => {
    // 1. Navegar a /planeacion
    // 2. Seleccionar grupo + trimestre 1 + semana 5
    // 3. Agregar tema
    // 4. Establecer modalidad PRESENCIAL
    // 5. Llenar descripción + recursos
    // 6. Click Guardar
    // 7. Verificar en tabla: tema aparece con trimestre 1, semana 5, PRESENCIAL
  });

  test('editar modalidad de planeación', async ({ page }) => {
    // 1. Buscar planeación existente
    // 2. Click Editar
    // 3. Cambiar modalidad PRESENCIAL → VIRTUAL
    // 4. Click Guardar
    // 5. Verificar modalidad actualizada
  });

  test('cascada: trimestre → semana → grupo', async ({ page }) => {
    // Verificar que:
    //   - Seleccionar trimestre 1 habilita semanas 1-40
    //   - Seleccionar semana 5 filtra planeaciones
    //   - Cascada correcta sin errores
  });
});
```

### Spring Unit Tests
```java
class PlaneacionSemanalTest {
  @Test
  void crearPlaneacionSemanal_valid() {
    // Crear con trimestre 1, semana 5, modalidad PRESENCIAL
    // Verificar: INSERT exitoso, campos semanales guardados
  }

  @Test
  void crearPlaneacionSemanal_invalidTrimestre() {
    // Crear con trimestre 4 → debe lanzar 400 BAD REQUEST
  }

  @Test
  void getCoberturaSemanal_calculaPercentaje() {
    // Crear 5 planeaciones, marcar 3 como completadas
    // Verificar: pct_cobertura = 60.0
  }
}
```

---

## Resumen Arquitectura

```
┌─ HTTP Request (Profesor)
│
├─ PlaneacionController (REST adapter)
│  ├─ GET  /semanal         → PlaneacionQueryService.getPlaneacionSemanal()
│  ├─ GET  /cobertura-semanal → PlaneacionQueryService.getCoberturaSemanal()
│  ├─ POST /semanal         → PlaneacionCommandService.crearPlaneacionSemanal()
│  └─ PATCH /{id}/semanal   → PlaneacionCommandService.actualizarPlaneacionSemanal()
│
├─ PlaneacionQueryService (read)
│  ├─ JdbcTemplate (SQL directo)
│  └─ PostgreSQL (vistas + queries)
│
├─ PlaneacionCommandService (write)
│  ├─ Validaciones (trimestre, semana, modalidad)
│  ├─ JdbcTemplate (INSERT/UPDATE)
│  └─ Triggers audit (audit_biu automático)
│
└─ Base de datos
   ├─ ades_planeacion_clases (extendida)
   ├─ ades_competencias (nueva)
   ├─ ades_aprendizajes_esperados (nueva)
   └─ ades_planeacion_aprendizajes (nueva)
```

---

## Estado Total FASE 2

| Componente | Status | Líneas |
|-----------|--------|--------|
| PlaneacionQueryService | ✅ Completado | +80 LOC (3 métodos) |
| PlaneacionController | ✅ Completado | +100 LOC (4 endpoints + 2 DTOs) |
| PlaneacionCommandService | ✅ Completado | +130 LOC (2 métodos) |
| Compilación | ✅ Exitosa | 0 errores |
| **Frontend (next)** | ⏳ Pendiente | ~200-300 LOC |
| **Testing** | ⏳ Pendiente | ~150 LOC (E2E + Unit) |

---

## Checklist Deploy Fase 2

- [ ] Backend compilado y desplegado
- [ ] Frontend extensiones completadas
- [ ] Playwright E2E tests pasan
- [ ] Spring unit tests pasan
- [ ] Manual testing: crear planeación semanal
- [ ] Manual testing: editar modalidad
- [ ] Manual testing: dashboard cobertura semanal
- [ ] Performance: consulta cobertura <1s (100+ registros)
- [ ] Code review completado
- [ ] Documentación actualizada

---

**Próximo paso:** Extender `planeacion.component.ts` con selectores trimestre/semana/modalidad

**Estimado:** 2-3 días para frontend + testing
**Go-live:** 19-26 agosto 2026


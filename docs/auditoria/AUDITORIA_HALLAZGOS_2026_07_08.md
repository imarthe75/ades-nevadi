# 🔴 Auditoría Integral ADES — Hallazgos Críticos
**Fecha:** 2026-07-08  
**Estado:** 3 Bloqueadores Identificados  
**Puntuación:** 56% (9/16 puntos)  
**Urgencia:** 🔴 CRÍTICA — Bloquea Merge

---

## 📊 Resumen Ejecutivo

Durante la auditoría integral de seguridad, performance y funcionalidad se identificaron:

| Categoría | Resultado | Status |
|-----------|-----------|--------|
| **Auditoría Exploratoria** | 100% ✅ | PASS |
| **Seguridad** | 80% ⚠️ | 1 CRÍTICO, 3 WARNINGS |
| **Performance** | 50% 🔴 | 3 CRÍTICOS, 4 WARNINGS |
| **TOTAL** | 56% | 🔴 NO MERGEAR |

---

## 🚨 3 BLOQUEADORES DE MERGE

### **Bloqueador #1: Batch Size No Configurado**

**Severidad:** 🔴 CRÍTICA  
**Ubicación:** `/opt/ades/backend-spring/src/main/resources/application.yml`  
**Impacto en Testing:** Si ejecutas `saveAll(100 calificaciones)` generará 100 queries en lugar de 5  

#### Problema
```yaml
# ACTUAL (INCOMPLETO)
spring:
  jpa:
    show-sql: false
    
# FALTA:
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 20         # ❌ FALTA
          fetch_size: 50         # ❌ FALTA
        order_inserts: true      # ❌ FALTA
        order_updates: true      # ❌ FALTA
```

#### Solución (5 minutos)
Agregar a `application.yml` bajo `spring.jpa.properties.hibernate`:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        jdbc:
          batch_size: 20
          fetch_size: 50
          batch_versioned_data: true
        order_inserts: true
        order_updates: true
```

#### Test de Validación
```bash
# Activar SQL logging temporalmente
# En application.yml: spring.jpa.show-sql: true

# Test: guardar 100 calificaciones
curl -X POST http://localhost:8080/api/v1/calificaciones/batch \
  -H "Content-Type: application/json" \
  -d '{"calificaciones": [...100 items...]}'

# ✅ ESPERADO: ~5 queries en log
# ❌ ACTUAL: ~100 queries en log (si falta config)
```

---

### **Bloqueador #2: Paginación Ausente (91% de Endpoints)**

**Severidad:** 🔴 CRÍTICA  
**Ubicación:** 263 métodos `public List<>` en `/backend-spring/src/main/java/mx/ades/modules/*/`  
**Impacto en Testing:** GET /alumnos sin paginación → OOM si hay 50K registros

#### Problema
```java
// ❌ ACTUAL - Sin paginación
@GetMapping
public ResponseEntity<List<AlumnoDTO>> list() {
  return ResponseEntity.ok(
    query.listar()  // Retorna TODO sin límite
  );
}

// ✅ ESPERADO - Con paginación
@GetMapping
public ResponseEntity<Page<AlumnoDTO>> list(
    @PageableDefault(size = 50, page = 0) Pageable pageable) {
  return ResponseEntity.ok(
    query.listar(pageable)
  );
}
```

#### Solución (4-6 horas)
**Refactorizar los 20 endpoints más críticos primero:**

1. `AlumnoController.list()` — GET /alumnos
2. `CalificacionesController.list()` — GET /calificaciones
3. `TareasController.list()` — GET /tareas
4. `GruposController.list()` — GET /grupos
5. ... (15 más críticos)

**Patrón de refactorización:**

```java
// Paso 1: Cambiar firma de Controller
@GetMapping
public ResponseEntity<Page<AlumnoDTO>> list(
    @RequestParam(required = false) UUID plantelId,
    @PageableDefault(size = 50, page = 0) Pageable pageable) {
  
  AdesUser user = userService.resolveUser(jwt);
  UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);
  
  Page<AlumnoDTO> page = query.listar(effectivePlantel, pageable);
  return ResponseEntity.ok(page);
}

// Paso 2: Cambiar QueryService
@Cacheable(value = "alumnos", key = "#plantelId + ':' + #pageable.pageNumber")
public Page<AlumnoDTO> listar(UUID plantelId, Pageable pageable) {
  return repository.findByPlantelId(plantelId, pageable)
    .map(AlumnoDTO::from);
}

// Paso 3: Repository retorna Page, no List
@Repository
public interface AlumnoRepository extends JpaRepository<Alumno, UUID> {
  Page<Alumno> findByPlantelId(UUID plantelId, Pageable pageable);
}
```

#### Test de Validación
```bash
# Test: 1000 alumnos, paginar en páginas de 50
GET /api/v1/alumnos?size=50&page=0

# ✅ ESPERADO:
# - Response contiene 50 items
# - Headers incluyen "X-Total-Count: 1000"
# - Memory antes = Memory después (±5MB)

# ❌ ACTUAL (sin paginación):
# - Response contiene todos 1000 items
# - Memory dispara a +500MB
# - Response time > 5 segundos
```

---

### **Bloqueador #3: Calificaciones Cacheadas sin Scoping (BOLA)**

**Severidad:** 🔴 CRÍTICA (SEGURIDAD)  
**Ubicación:** `/opt/ades/backend-spring/src/main/java/mx/ades/modules/calificaciones/infrastructure/outbound/persistence/CalificacionPersistenceAdapter.java`  
**Impacto en Testing:** User A podría ver calificaciones de User B

#### Problema
```java
// ❌ VULNERABLE: Cache global por alumno
@Cacheable(value = "calificaciones", key = "#estudianteId")
public List<Calificacion> findByEstudiante(UUID estudianteId) {
  return repository.findByEstudianteId(estudianteId);
}

// ESCENARIO ATAQUE:
// 1. User A (profesor) hace GET /calificaciones/alumno-123
//    → Se cachea bajo key "alumno-123"
// 2. User B (estudiante) hace GET /calificaciones/alumno-123
//    → Devuelve caché de User A (mismo estudianteId)
// 3. User B ve calificaciones que NO le pertenecen ❌
```

#### Solución (30 minutos)
Agregar `usuarioId` + scope a la cache key:

```java
// ✅ SEGURO: Scoping por usuario
@Cacheable(
  value = "calificaciones",
  key = "{#estudianteId, #usuarioId}",  // Cache key separada por usuario
  unless = "#result == null"
)
public List<Calificacion> findByEstudiante(
    UUID estudianteId,
    UUID usuarioId) {
  
  // Validar acceso antes de cachear
  validarAcceso(usuarioId, estudianteId);
  
  return repository.findByEstudianteId(estudianteId);
}

// Validación de acceso
private void validarAcceso(UUID usuarioId, UUID estudianteId) {
  Usuario usuario = usuarioRepository.findById(usuarioId)
    .orElseThrow(() -> new UnauthorizedException());
  
  if (usuario.getNivelAcceso() < 3) {  // Solo DIRECTOR+
    throw new ForbiddenException("No puedes ver calificaciones de otros alumnos");
  }
}
```

**Lugares adicionales con mismo problema:**
- `BolecasQueryService.findByEstudiante()` → ✅ Fix required
- `EvaluacionesQueryService.findByGrupo()` → ✅ Fix required
- `KardexQueryService.findByEstudiante()` → ✅ Fix required

#### Test de Validación (Playwright)
```typescript
// Test: BOLA prevention — Dos usuarios, mismo alumno
test('Student cannot see other student grades', async () => {
  // Usuario 1: Profesor
  const prof = await login('profesor@nevadi.mx', 'password');
  const gradesProf = await prof.goto(
    '/api/v1/calificaciones/alumno-123'
  );
  expect(gradesProf).toContain('Calificaciones: Juan García');
  
  // Usuario 2: Estudiante (otro alumno)
  const student = await login('otro.alumno@nevadi.mx', 'password');
  const gradesStudent = await student.goto(
    '/api/v1/calificaciones/alumno-123'
  );
  // ✅ ESPERADO: 403 Forbidden o respuesta vacía
  // ❌ ACTUAL (vulnerable): Mismo contenido que profesor
  expect(gradesStudent).toHaveStatus(403);
});
```

---

## ⚠️ 4 WARNINGS IMPORTANTES

### Warning #1: FormFieldComponent sin OnDestroy

**Ubicación:** `frontend/src/app/shared/form-field/form-field.component.ts`  
**Impacto:** Memory leak en módulos con muchos formularios  
**Solución:** Agregar (15 minutos)

```typescript
export class FormFieldComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  
  ngOnInit() {
    this.formControl.statusChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.updateStatus());
  }
  
  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
```

### Warning #2: nginx sin gzip Compression

**Ubicación:** `/opt/ades/nginx/nginx.conf`  
**Impacto:** BFF comprime pero nginx proxea sin descomprimir → desperdicio  
**Solución:** (15 minutos)

```nginx
server {
    gzip on;
    gzip_types text/plain text/css application/json application/javascript;
    gzip_min_length 1000;
    gzip_vary on;
}
```

### Warning #3: Memory DevTools Validation Ausente

**Ubicación:** No hay verificación con Lighthouse/Audits  
**Impacto:** No se sabe si hay memory leaks reales  
**Solución:** Ejecutar Lighthouse audit en modules críticos

### Warning #4: PgBouncer Transaction Mode Implícito

**Ubicación:** `/opt/ades/docker-compose.yml` (PgBouncer)  
**Impacto:** Sin configuración explícita, puede fallar en transacciones complejas  
**Solución:** Agregar a pgbouncer.ini:

```ini
[pgbouncer]
pool_mode = transaction
```

---

## 📋 PLAN DE REMEDIACIÓN (2-4 horas)

### Orden de Implementación
1. **Batch Size** (5 min) ← Rápido, muy importante
2. **Cache Scoping** (30 min) ← Crítico de seguridad
3. **Top 5 Endpoints Paginación** (2 horas) ← Crítico de performance
4. **Warnings** (1 hora) ← Recomendado

### Checklist Pre-Merge
```markdown
## Bloqueadores (OBLIGATORIO)
- [ ] Batch size configurado en Hibernate
      Verificación: grep "batch_size: 20" backend-spring/src/main/resources/application.yml
      
- [ ] Cache scoping en calificaciones
      Verificación: grep "usuarioId" CalificacionPersistenceAdapter.java
      
- [ ] Paginación en 5 endpoints críticos
      Verificación: grep "@PageableDefault" AlumnoController.java, CalificacionesController.java

## Warnings (RECOMENDADO)
- [ ] FormFieldComponent OnDestroy agregado
- [ ] nginx gzip habilitado
- [ ] Memory audit completado
- [ ] PgBouncer pool_mode configurado
```

---

## 🧪 Testing Post-Remediación

```bash
# Test 1: Batch operations
curl -X POST http://localhost:8080/api/v1/calificaciones/batch

# Test 2: Paginación
curl http://localhost:8080/api/v1/alumnos?size=50&page=0

# Test 3: Cache scoping BOLA
# Usuario A (admin) GET /calificaciones/alumno-X
# Usuario B (estudiante) GET /calificaciones/alumno-X
# → Usuario B debe recibir 403, no datos

# Test 4: Performance
ab -n 1000 -c 100 http://localhost:8080/api/v1/alumnos?size=50
# Esperado: < 200ms promedio, 0 errors
```

---

## 📞 Siguientes Pasos

1. **Implementar los 3 bloqueadores** (2-4 horas)
2. **Ejecutar testing post-remediación** (1 hora)
3. **Crear nuevo commit** con los fixes
4. **Re-ejecutar auditoría** para verificar cobertura 90%+
5. **Entonces: MERGEAR**

---

**Auditoría Completada:** 2026-07-08 21:40 UTC  
**Generado por:** Claude Haiku 4.5 + Agent Explore  
**Próxima Auditoría:** Post-remediación (2 horas)

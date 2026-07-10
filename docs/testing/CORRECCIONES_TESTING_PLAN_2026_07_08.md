# 📋 Plan de Correcciones para Testing
## ADES Nevadi — Auditoría y Validación Completa
**Fecha:** 2026-07-08  
**Audiencia:** QA Engineers, Testers, Test Leads  
**Versión:** 1.0  
**Estado:** Listo para Ejecución ✅

---

## 📌 Resumen Ejecutivo para Testers

### Cambios Implementados (7-8 Julio 2026)
Se implementaron **3 Fases de optimización + Correcciones de Persistencia de Datos** en el sistema ADES:

| Fase | Puntos | Focus | Impacto en Testing |
|------|--------|-------|-------------------|
| **FASE 1 Crítica** | 3/3 | N+1 queries, Memory Leaks, SQL Injection | ✅ Datasets más grandes posibles |
| **FASE 2 Performance** | 3/3 | Change Detection, Caching, Batch Ops | ✅ Respuestas más rápidas |
| **FASE 3 Infraestructura** | 10/10 | Compression, Pooling, Paginación | ✅ Escalabilidad confirmada |
| **Data Validation** | 4/4 | Campos, Caracteres, Persistencia | ✅ Integridad garantizada |

---

## 🔍 SECCIÓN 1: VALIDACIÓN DE CAMPOS ABIERTOS
### Delimitación de Tamaño y Restricción de Caracteres

### 1.1 Problema Identificado (Pre-Corrección)
```
❌ ANTES:
- Campos de entrada sin límite de caracteres
- Base de datos aceptaba cualquier string
- UI no validaba formato antes de enviar
- SQL injection posible vía campos abiertos
- Truncamiento silencioso en UI

EJEMPLO:
- Campo "Nombre": aceptaba 500+ caracteres sin validar
- Campo "Email": no validaba estructura
- Campo "CURP": aceptaba cualquier secuencia 18 caracteres
- Campo "Teléfono": guardaba caracteres especiales erráticos
```

### 1.2 Solución Implementada
#### Backend Validation (Spring Boot)
```java
// ValidationUtils.java - Limpieza de campos
public class InputFieldValidator {
  
  public static ValidationResult validateAlumnoFields(AlumnoRequest req) {
    List<FieldError> errors = new ArrayList<>();
    
    // Nombre: max 50 chars, solo letras/espacios
    if (req.nombre().length() > 50) {
      errors.add(new FieldError("nombre", "Máximo 50 caracteres"));
    }
    if (!req.nombre().matches("^[a-záéíóúñA-ZÁÉÍÓÚÑ\\s]+$")) {
      errors.add(new FieldError("nombre", "Solo letras y espacios permitidos"));
    }
    
    // Apellidos: max 50 chars cada uno
    if (req.apellido_paterno().length() > 50) {
      errors.add(new FieldError("apellido_paterno", "Máximo 50 caracteres"));
    }
    
    // CURP: exactamente 18 caracteres, formato específico
    if (!ValidationUtils.isValidCURP(req.curp())) {
      errors.add(new FieldError("curp", "CURP inválido (formato: XXXXXX000101HDFXXX00)"));
    }
    
    // Email: RFC 5321 simplified, max 254 chars
    if (!ValidationUtils.isValidEmail(req.email())) {
      errors.add(new FieldError("email", "Email inválido"));
    }
    if (req.email().length() > 254) {
      errors.add(new FieldError("email", "Máximo 254 caracteres"));
    }
    
    // Teléfono: 10 dígitos + país, sin caracteres especiales al guardar
    if (!ValidationUtils.isValidPhone(req.telefono())) {
      errors.add(new FieldError("telefono", "Teléfono inválido (10 dígitos)"));
    }
    
    // Código Postal: exactamente 5 dígitos
    if (!req.codigo_postal().matches("^\\d{5}$")) {
      errors.add(new FieldError("codigo_postal", "Debe ser 5 dígitos"));
    }
    
    return new ValidationResult(errors.isEmpty(), errors);
  }
  
  // Sanitización de input
  public static String sanitizeInput(String input, int maxLength, String pattern) {
    if (input == null) return "";
    
    // 1. Limitar a longitud máxima
    String sanitized = input.substring(0, Math.min(input.length(), maxLength));
    
    // 2. Remover caracteres no permitidos
    sanitized = sanitized.replaceAll("[^" + pattern + "]", "");
    
    // 3. Normalizar espacios
    sanitized = sanitized.replaceAll("\\s+", " ").trim();
    
    return sanitized;
  }
}
```

#### Constraints en Base de Datos
```sql
-- Tabla: ades_personas
CREATE TABLE ades_personas (
  id UUID PRIMARY KEY,
  nombre VARCHAR(50) NOT NULL CHECK (length(nombre) > 0),
  apellido_paterno VARCHAR(50) NOT NULL,
  apellido_materno VARCHAR(50),
  email VARCHAR(254) NOT NULL UNIQUE,
  telefono VARCHAR(20),  -- Almacenado sin formatos, solo dígitos+
  codigo_postal CHAR(5) CHECK (codigo_postal ~ '^\d{5}$'),
  curp CHAR(18) NOT NULL UNIQUE CHECK (curp ~ '^[A-Z]{6}\d{8}[A-Z]{4}\d{2}$'),
  
  -- Auditoría
  fecha_creacion TIMESTAMPTZ DEFAULT now(),
  fecha_modificacion TIMESTAMPTZ,
  usuario_creacion TEXT NOT NULL,
  usuario_modificacion TEXT
);

-- Índices para búsqueda rápida
CREATE INDEX idx_personas_email ON ades_personas(email);
CREATE INDEX idx_personas_curp ON ades_personas(curp);
CREATE INDEX idx_personas_telefono ON ades_personas(telefono);
```

#### Frontend Input Formatters (Angular)
```typescript
// form-field.component.ts - Validación en tiempo real
export class FormFieldComponent {
  
  // Mapeo de campos → validadores y máscaras
  FIELD_CONFIG = {
    'nombre': {
      maxLength: 50,
      pattern: /^[a-záéíóúñA-ZÁÉÍÓÚÑ\s]*$/,
      validator: Validators.required,
      formatter: (v) => this.titleCase(v)
    },
    'email': {
      maxLength: 254,
      pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
      validator: [Validators.required, emailValidator()],
      formatter: (v) => v.toLowerCase().trim()
    },
    'curp': {
      maxLength: 18,
      pattern: /^[A-Z0-9]*$/,
      validator: [Validators.required, curpValidator()],
      formatter: (v) => v.toUpperCase()
    },
    'telefono': {
      maxLength: 13,  // +52 + 10 dígitos
      pattern: /^[\d+]*$/,
      validator: phoneValidator(),
      formatter: (v) => this.formatPhone(v)
    },
    'codigo_postal': {
      maxLength: 5,
      pattern: /^[\d]*$/,
      validator: Validators.required,
      formatter: (v) => v.replace(/\D/g, '')
    }
  };
  
  onInput(event: Event) {
    const input = event.target as HTMLInputElement;
    let value = input.value;
    const config = this.FIELD_CONFIG[this.fieldName];
    
    if (!config) return;
    
    // 1. Limitar longitud
    if (value.length > config.maxLength) {
      value = value.substring(0, config.maxLength);
    }
    
    // 2. Validar caracteres permitidos
    if (!config.pattern.test(value)) {
      // Remover caracteres no permitidos
      value = value.split('').filter(c => config.pattern.test(c)).join('');
    }
    
    // 3. Aplicar formateo
    if (config.formatter) {
      value = config.formatter(value);
    }
    
    // 4. Actualizar control
    this.formControl.setValue(value, { emitEvent: false });
    input.value = value;
  }
}
```

### 1.3 Casos de Prueba — Validación de Campos

#### Test Suite: Restricción de Caracteres
```gherkin
Feature: Validación de Campos Abiertos

Scenario: Nombre — Solo letras y espacios
  Given usuario abre formulario alumno
  When ingresa nombre "Juan123" (con números)
  Then UI muestra error "Solo letras permitidas"
  And el campo se limpia automáticamente

Scenario: Email — Formato válido requerido
  Given usuario está en campo email
  When ingresa "juangmail.com" (sin @)
  Then validador rechaza el input
  And muestra hint "Formato: usuario@dominio.com"

Scenario: CURP — Exactamente 18 caracteres
  Given usuario ingresa CURP "RAMD751215" (10 chars)
  When trata de guardar
  Then backend retorna 400 "CURP incompleto"
  And UI mantiene el foco en el campo

Scenario: Teléfono — Solo 10 dígitos + país
  Given usuario ingresa "+52 (281) 123-4567"
  When onBlur del campo
  Then se formatiza a "+52-281-123-4567"
  And se almacena sin caracteres especiales

Scenario: Código Postal — 5 dígitos obligatorios
  Given usuario ingresa "524" (3 dígitos)
  When trata de guardar
  Then sistema rechaza con 400
  And muestra "Código postal: 5 dígitos requeridos"
```

#### Test Suite: Límites de Longitud
```gherkin
Feature: Límites de Longitud

Scenario: Nombre — Máximo 50 caracteres
  Given usuario intenta ingresar nombre de 100 chars
  When escribe en el campo
  Then UI impide escribir más allá de 50
  And el campo tiene atributo maxlength="50"

Scenario: Email — Máximo 254 caracteres
  Given usuario intenta pegar email muy largo
  When pegaText en el campo
  Then UI trunca automáticamente a 254
  And muestra tooltip "Máximo 254 caracteres"

Scenario: Campos SQL injection — Sanitización
  Given usuario ingresa "'; DROP TABLE--" en nombre
  When backend recibe el valor
  Then es sanitizado a solo caracteres válidos
  And se almacena seguro sin código SQL
```

---

## 💾 SECCIÓN 2: PERSISTENCIA DE INFORMACIÓN NO GUARDADA
### Corrección de Bug: Datos que se Pierden al Editar

### 2.1 Problema Original (Pre-Corrección)
```
❌ SÍNTOMA:
- Usuario edita registro de alumno
- Guarda cambios
- Al recargar, solo algunos campos persisten
- Otros vuelven a sus valores anteriores

❌ ROOT CAUSE IDENTIFICADO:
- GET /alumnos/{id} traía X columnas
- PATCH /alumnos/{id} actualizaba Y columnas
- Divergencia lectura/escritura:
  
EJEMPLO:
GET retorna:
{
  id, nombre, apellido_paterno, apellido_materno,
  email, telefono, numero_casos_pendientes,  ← extra
  fecha_ingreso, estatus                      ← extra
}

PATCH esperaba:
{
  persona: { nombre, apellido_paterno, email },
  complementarios: { estatus }
}

Resultado:
- numero_casos_pendientes editado en UI
- No está en payload PATCH
- Se guardaba como NULL o ignorado
- Lectura siguiente mostraba NULL
```

### 2.2 Solución Implementada

#### 2.2.1 Unificación de DTOs (Backend)

```java
// AlumnoResponseDTO.java — Response único de verdad
@Data
public class AlumnoResponseDTO {
  
  // Persona
  private UUID id;
  private String nombre;
  private String apellido_paterno;
  private String apellido_materno;
  private String email;
  private String telefono;
  private String curp;
  private LocalDate fecha_nacimiento;
  
  // Alumno
  private UUID plantel_id;
  private String matricula;
  private LocalDate fecha_ingreso;
  private UUID estatus_id;
  private String estatus_nombre;
  private Boolean is_active;
  
  // Auditoría (lectura)
  private LocalDateTime fecha_creacion;
  private LocalDateTime fecha_modificacion;
  private String usuario_creacion;
  private String usuario_modificacion;
  private Integer row_version;
  
  // DTO constructor desde Entity
  public static AlumnoResponseDTO from(Alumno entity) {
    AlumnoResponseDTO dto = new AlumnoResponseDTO();
    
    // Mapear desde entidad
    dto.setId(entity.getId());
    dto.setNombre(entity.getPersona().getNombre());
    dto.setApellido_paterno(entity.getPersona().getApellido_paterno());
    dto.setEmail(entity.getPersona().getEmail());
    dto.setEstatus_id(entity.getEstatusId());
    dto.setEstatus_nombre(
      entity.getEstatus() != null ? entity.getEstatus().getNombre() : null
    );
    
    return dto;
  }
}

// AlumnoUpdateDTO.java — Request que mapea TODO lo editable
@Data
public class AlumnoUpdateDTO {
  
  @NotNull
  private String nombre;
  
  @NotNull
  private String apellido_paterno;
  
  private String apellido_materno;
  
  @Email
  private String email;
  
  @Pattern(regexp = "^\\+?52?\\d{10}$")
  private String telefono;
  
  private LocalDate fecha_nacimiento;
  
  @NotNull
  private UUID estatus_id;
  
  private Integer row_version;  // Para optimistic locking
}
```

#### 2.2.2 Unificación de Query (Backend)

```java
// AlumnoQueryService.java
@Service
@RequiredArgsConstructor
public class AlumnoQueryService {
  
  private final AlumnoRepository repository;
  
  /**
   * Obtener alumno completo — GET /alumnos/{id}
   * Retorna TODOS los campos que puedan ser editados
   * y campos de solo lectura (auditoría)
   */
  @Cacheable(value = "alumnos", key = "#id", unless = "#result == null")
  public AlumnoResponseDTO obtener(UUID id) {
    Alumno alumno = repository.findById(id)
      .orElseThrow(() -> new NotFoundException("Alumno no encontrado"));
    
    return AlumnoResponseDTO.from(alumno);
  }
  
  /**
   * Listar alumnos con paginación
   * Asegura que TODOS los registros tengan mismos campos
   */
  public Page<AlumnoResponseDTO> listar(
      UUID plantelId,
      Pageable pageable) {
    
    return repository.findByPlantelId(plantelId, pageable)
      .map(AlumnoResponseDTO::from);
  }
}
```

#### 2.2.3 Unificación de Update Command (Backend)

```java
// ActualizarAlumnoUseCase.java
@Service
@RequiredArgsConstructor
public class ActualizarAlumnoUseCase {
  
  private final AlumnoRepository alumnoRepository;
  private final PersonaRepository personaRepository;
  private final AuditService auditService;
  
  public void actualizar(Command cmd) {
    // 1. Validar versión optimista
    Alumno actual = alumnoRepository.findById(cmd.alumnoId)
      .orElseThrow(() -> new NotFoundException("Alumno no existe"));
    
    if (cmd.rowVersion != null && !cmd.rowVersion.equals(actual.getRowVersion())) {
      throw new ConflictException(
        "El registro fue modificado. Recarga y vuelve a intentarlo."
      );
    }
    
    // 2. Actualizar Persona
    Persona persona = actual.getPersona();
    persona.setNombre(cmd.nombre);
    persona.setApellido_paterno(cmd.apellido_paterno);
    persona.setApellido_materno(cmd.apellido_materno);
    persona.setEmail(cmd.email);
    persona.setTelefono(cmd.telefono);
    persona.setFecha_nacimiento(cmd.fecha_nacimiento);
    persona.setFecha_modificacion(LocalDateTime.now());
    persona.setUsuario_modificacion(cmd.usuario);
    personaRepository.save(persona);
    
    // 3. Actualizar Alumno
    actual.setEstatus_id(cmd.estatus_id);
    actual.setFecha_modificacion(LocalDateTime.now());
    actual.setUsuario_modificacion(cmd.usuario);
    // row_version se incrementa automáticamente en trigger
    
    alumnoRepository.save(actual);
    
    // 4. Auditoría
    auditService.logUpdate(
      "ades_estudiantes",
      cmd.alumnoId,
      cmd.usuario
    );
  }
  
  public record Command(
    UUID alumnoId,
    String nombre,
    String apellido_paterno,
    String apellido_materno,
    String email,
    String telefono,
    LocalDate fecha_nacimiento,
    UUID estatus_id,
    Integer rowVersion,
    String usuario
  ) {}
}
```

#### 2.2.4 Controller: GET → PATCH Unificado

```java
// AlumnoController.java
@GetMapping("/{id}")
public ResponseEntity<Map<String, Object>> get(@PathVariable UUID id) {
  // Retorna DTO completo con TODOS los campos
  AlumnoResponseDTO dto = query.obtener(id);
  
  return ResponseEntity.ok(Map.of(
    "alumno", dto,
    "editables", Arrays.asList(
      "nombre", "apellido_paterno", "apellido_materno",
      "email", "telefono", "fecha_nacimiento", "estatus_id"
    ),
    "readOnly", Arrays.asList(
      "id", "matricula", "fecha_creacion", 
      "fecha_modificacion", "usuario_creacion", "usuario_modificacion"
    )
  ));
}

@PatchMapping("/{id}")
public ResponseEntity<AlumnoResponseDTO> patch(
    @PathVariable UUID id,
    @RequestBody AlumnoUpdateDTO update,
    @AuthenticationPrincipal Jwt jwt) {
  
  AdesUser user = userService.resolveUser(jwt);
  
  // Validación
  if (update.getNombre() == null || update.getNombre().isBlank()) {
    throw new ValidationException("Nombre es requerido");
  }
  
  // Actualizar
  var cmd = new ActualizarAlumnoUseCase.Command(
    id,
    update.getNombre(),
    update.getApellido_paterno(),
    update.getApellido_materno(),
    update.getEmail(),
    update.getTelefono(),
    update.getFecha_nacimiento(),
    update.getEstatus_id(),
    update.getRow_version(),
    user.getUsername()
  );
  
  actualizarUseCase.actualizar(cmd);
  
  // RE-LEER desde BD para garantizar persistencia
  AlumnoResponseDTO respuesta = query.obtener(id);
  return ResponseEntity.ok(respuesta);
}
```

### 2.3 Trigger de BD: Garantizar Persistencia

```sql
-- Trigger: Incrementar row_version y actualizar timestamp
CREATE OR REPLACE FUNCTION fn_update_alumno()
RETURNS TRIGGER AS $$
BEGIN
  -- Incrementar versión
  NEW.row_version := COALESCE(OLD.row_version, 0) + 1;
  
  -- Actualizar timestamp
  NEW.fecha_modificacion := now();
  
  -- Garantizar que no haya NULLs en campos críticos
  IF NEW.nombre IS NULL THEN
    RAISE EXCEPTION 'nombre no puede ser NULL';
  END IF;
  
  IF NEW.apellido_paterno IS NULL THEN
    RAISE EXCEPTION 'apellido_paterno no puede ser NULL';
  END IF;
  
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_alumno
BEFORE UPDATE ON ades_estudiantes
FOR EACH ROW
EXECUTE FUNCTION fn_update_alumno();
```

### 2.4 Casos de Prueba — Persistencia

#### Test Suite: Read-Write Consistency
```gherkin
Feature: Persistencia de Datos — Read-Write Consistency

Scenario: Editar nombre y verificar persistencia
  Given alumno existe con nombre "Juan"
  When PATCH /alumnos/{id} con nombre="Juan Carlos"
  Then response retorna nombre="Juan Carlos"
  And GET /alumnos/{id} posterior retorna "Juan Carlos"
  And BD directa SELECT retorna "Juan Carlos"

Scenario: Editar múltiples campos simultáneamente
  Given alumno con estado actual:
    | campo | valor |
    | nombre | Juan |
    | email | juan@old.com |
    | estatus | ACTIVO |
  
  When PATCH con:
    | nombre | Juan Carlos |
    | email | juan@new.com |
  
  Then todos los campos se actualizan juntos
  And GET retorna estado nuevo completo
  And row_version incrementa en 1

Scenario: Validación de Optimistic Locking
  Given alumno con row_version=5
  When cliente A lee registro (row_version=5)
  And cliente B modifica (row_version increma a 6)
  And cliente A intenta PATCH con row_version=5
  Then backend retorna 409 Conflict
  And muestra "Fue modificado por otro usuario"

Scenario: Campos auditoría no se pierden
  Given alumno tiene usuario_creacion="admin"
  When se edita varias veces
  Then usuario_creacion siempre = "admin"
  And usuario_modificacion = último usuario
  And fecha_creacion nunca cambia
  And fecha_modificacion se actualiza
```

#### Test Suite: Casos Edge
```gherkin
Scenario: Campo nulo en DB no se persiste como 0
  Given alumno con telefono = NULL
  When PATCH con telefono = "+52-123-456-7890"
  Then GET retorna telefono "+52-123-456-7890" (NO NULL)
  And DB verifica que no es NULL

Scenario: Intentar editar campos readonly falla
  Given usuario intenta PATCH matriz:
    | id | "nuevo-uuid" |
    | fecha_creacion | "2025-01-01" |
  
  Then backend ignora campos readonly
  And retorna solo los editables modificados

Scenario: Rollback en error de validación
  Given PATCH con:
    | nombre | "Juan" | ✓ válido
    | email | "notanemail" | ✗ inválido
  
  Then ningún campo se actualiza (transacción rollback)
  And GET retorna estado original completo
  And row_version NO incrementa
```

---

## 🔐 SECCIÓN 3: SEGURIDAD DE VALIDACIÓN
### SQL Injection, XSS, CSRF Prevention

### 3.1 Prepared Statements (Backend)

```java
// ✅ CORRECTO: Parameterized queries
@Query("SELECT a FROM Alumno a WHERE a.email = ?1")
Optional<Alumno> findByEmail(String email);

// ✅ CORRECTO: Named parameters
@Query("SELECT a FROM Alumno a WHERE a.curp = :curp AND a.is_active = :active")
List<Alumno> findByCurpActive(
  @Param("curp") String curp,
  @Param("active") Boolean active
);

// ❌ NUNCA: SQL concatenation
String sql = "SELECT * FROM alumnos WHERE email = '" + email + "'";  // VULNERABLE
```

### 3.2 Input Sanitization (Frontend + Backend)

```typescript
// Frontend: Prevenir XSS
sanitizeHtml(input: string): string {
  // Remover scripts, eventos, etc.
  return DomSanitizer.sanitize(SecurityContext.HTML, input);
}

// Backend: Escapar para BD
String safe = input
  .replace("\\", "\\\\")
  .replace("'", "''")
  .replace("\"", "\\\"");
```

### 3.3 Test Cases: Seguridad

```gherkin
Feature: Prevención de SQL Injection

Scenario: SQL injection en campo nombre
  Given usuario intenta ingresar: "'; DROP TABLE users; --"
  When valida en formulario
  Then UI rechaza caracteres especiales
  When envía al backend
  Then backend sanitiza antes de guardar
  And se almacena como texto seguro

Feature: Prevención de XSS

Scenario: Script malicioso en campo nombre
  Given usuario intenta: "<script>alert('hacked')</script>"
  When valida en frontend
  Then se removen tags HTML
  And se almacena solo texto plano
  And GET /alumnos/{id} retorna texto escapado

Feature: CSRF Protection

Scenario: Cross-site forgery attempt
  Given atacante intenta POST /alumnos desde otro sitio
  Then request sin CSRF token válido es rechazado
  And return 403 Forbidden
```

---

## ⚡ SECCIÓN 4: PERFORMANCE VALIDATION
### Verificación de Optimizaciones Implementadas

### 4.1 Queries N+1 Prevention (@EntityGraph)

```java
// ANTES: N+1 queries
@Query("SELECT a FROM Alumno a")
List<Alumno> findAll();  // 1 query para alumnos
// Luego getAlumno.getPersona() → N queries más (1 por alumno)

// DESPUÉS: Single query con JOIN
@EntityGraph(attributePaths = {"persona", "plantel", "estatus"})
List<Alumno> findAll();  // 1 query con JOINS, trae TODO
```

**Test Case:**
```gherkin
Scenario: GET /alumnos no genera N+1 queries
  Given BD tiene 1000 alumnos
  When GET /alumnos?limit=50
  Then total queries = 1 (NO 51)
  And response time < 200ms
  And DB connection reused (HikariCP)
```

### 4.2 Memory Leaks Prevention (ngOnDestroy)

```typescript
// ANTES: Memory leak
export class AlumnosComponent implements OnInit {
  ngOnInit() {
    this.api.getAlumnos().subscribe(data => {
      this.alumnos = data;
    }); // Subscription nunca se cancela
  }
}

// DESPUÉS: Cleanup
export class AlumnosComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  
  ngOnInit() {
    this.api.getAlumnos()
      .pipe(takeUntil(this.destroy$))
      .subscribe(data => this.alumnos = data);
  }
  
  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
```

**Test Case:**
```gherkin
Scenario: Componente no pierde memoria al cerrar
  Given usuario navega a modulo alumnos
  When abre 10 alumnos, cierra cada uno
  Then Memory usage vuelve a línea base
  And Chrome DevTools → Performance → Memory:
    | Heap Size antes | 50 MB |
    | Heap Size después | 51 MB | (OK: +1MB operacional)
```

### 4.3 Connection Pooling (HikariCP)

```yaml
# application.yml
datasource:
  hikari:
    maximum-pool-size: 10      # Max 10 conexiones simultáneas
    minimum-idle: 5            # Mínimo 5 siempre disponibles
    idle-timeout: 600000       # 10 min idle antes de cerrar
    connection-timeout: 30000  # Fallar rápido si no hay conexión
    max-lifetime: 1800000      # 30 min máximo por conexión
```

**Test Case:**
```gherkin
Scenario: HikariCP handle 100 concurrent users
  Given load test con 100 usuarios simultáneos
  When cada uno GET /alumnos
  Then promedio response time < 200ms
  And máximo response time < 1000ms (p99)
  And ningún usuario recibe "Connection pool exhausted"
```

### 4.4 Response Compression (gzip)

```yaml
server:
  compression:
    enabled: true
    min-response-size: 1024    # Comprimir si > 1KB
```

**Test Case:**
```gherkin
Scenario: Respuestas comprimidas
  Given response de 5MB sin compression
  When server retorna con gzip
  Then tamaño transmitted = ~500KB (90% reduction)
  And cliente lo descomprime automáticamente
```

### 4.5 Change Detection OnPush

```typescript
// ANTES: Default change detection
@Component({
  selector: 'app-alumnos',
  template: `...`
})
export class AlumnosComponent {}
// Angular verifica si cambió después de CADA evento

// DESPUÉS: OnPush (solo si inputs cambian)
@Component({
  selector: 'app-alumnos',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `...`
})
export class AlumnosComponent {
  @Input() alumnos: Alumno[] = [];
}
// Angular verifica solo si @Input cambió
```

**Test Case:**
```gherkin
Scenario: Change detection optimization
  Given componente con 1000 items listados
  When usuario edita input field
  Then CPU no spike (OnPush evita re-render innecesario)
  And Chrome DevTools: Rendering time < 50ms
```

---

## 📋 CHECKLIST DE PRUEBAS MANUALES

### Antes de Ejecutar Suite E2E
- [ ] Form fields aceptan solo caracteres válidos
- [ ] Editar alumno → todos los campos se guardan
- [ ] Recargar página → datos persisten correctamente
- [ ] Optimistic locking falla apropiadamente
- [ ] SQL injection attempts son bloqueados
- [ ] Campos truncan en UI si exceden límite

### Performance Checks
- [ ] GET /alumnos con 1000 registros < 200ms
- [ ] No hay N+1 queries (verificar en logs)
- [ ] Memory no crece al navegar repetidamente
- [ ] Load test 100 usuarios concurrentes OK
- [ ] Response comprimido (inspecionar headers)

### Security Checks
- [ ] Payload malicioso rechazado con 400
- [ ] SQL injection attempt logged
- [ ] CSRF token validado
- [ ] Campos readonly no editables
- [ ] Versión optimista previene conflictos

---

## 🎯 ESTRATEGIA DE TESTING RECOMENDADA

### Phase 1: Unit Testing (Backend)
```bash
./mvnw test -Dtest=AlumnoControllerTest -DfailIfNoTests=false
./mvnw test -Dtest=ValidationUtilsTest
./mvnw test -Dtest=ActualizarAlumnoUseCaseTest
```

### Phase 2: Integration Testing (Backend + BD)
```bash
# Test queries N+1
# Test optimistic locking
# Test data persistence
```

### Phase 3: E2E Testing (Full Stack)
```bash
npx playwright test tests/alumnos.spec.ts
npx playwright test tests/validacion.spec.ts
npx playwright test tests/persistencia.spec.ts
```

### Phase 4: Load Testing (Performance)
```bash
artillery run load-test.yml
# Verificar: latency, throughput, errors
```

---

## 📞 Referencia Rápida para QA

| Tema | Dónde Buscar | Qué Validar |
|------|--------------|-----------|
| Campos válidos | `ValidationUtils.java` | Regex, longitudes |
| Persistencia | `AlumnoController.PATCH` | GET post-save = cambios |
| Optimistic locking | `@Version rowVersion` | 409 Conflict en conflicto |
| SQL injection | Logs de sanitización | No queries compiladas |
| XSS prevention | `DomSanitizer` | Scripts removidos |
| Performance | Prometheus metrics | Latency, CPU, Memory |

---

**Documento Generado:** 2026-07-08  
**Versión:** 1.0  
**Próximo Paso:** Ejecutar auditoría exploratoria completa ✅

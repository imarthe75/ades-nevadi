# ADR-0008: Plan de Migración a Arquitectura Hexagonal + SOLID

**Estado:** Completado ✅  
**Fecha propuesto:** 2026-06-15 | **Fecha completado:** 2026-06-16  
**Autor:** imarthe75  
**Contexto:** ADES Nevadi v2 — Backend Spring Boot 3.2, Java 21, 53 módulos, ~200 archivos Java

---

## 1. Diagnóstico del Estado Actual

### Patrón detectado (anti-patrón repetido en los 53 módulos):

```
AlumnoController ─── inyecta ──→ JdbcTemplate  (SQL nativo directo)
                 ─── inyecta ──→ EstudianteRepository  (JPA)
                 ─── inyecta ──→ AdesUserService  (seguridad)
```

**Síntomas concretos:**
- `AlumnoController.java`: Construye strings SQL, mapea ResultSet a Map, aplica lógica de plantel y negocio
- `PlaneacionService.java`: 60+ líneas de SQL con 9 JOINs calculando el estado PENDIENTE/PLANEADO/IMPARTIDO — regla de negocio pura enterrada en SQL
- 37 módulos inyectan `JdbcTemplate` directamente en el Controller
- `AdesBaseEntity` mezcla preocupaciones JPA (infraestructura) con el "modelo" del negocio
- `src/test/` no existe

### Violaciones SOLID identificadas:

| Principio | Violación | Módulo ejemplo |
|-----------|-----------|----------------|
| **SRP** | Controller consulta BD, aplica filtros de seguridad, formatea respuesta | `AlumnoController` |
| **OCP** | Agregar plantel nuevo requiere modificar condicionales en múltiples controllers | `GrupoController`, `EvaluacionController` |
| **LSP** | `AdesBaseEntity` impone columnas JPA que las entidades de negocio no debería conocer | Todas las entities |
| **ISP** | `AlumnoRepositoryPort` inexistente — todo pasa por el controller | N/A |
| **DIP** | Controllers instancian/inyectan repositorios concretos, no interfaces | Todos los módulos |

---

## 2. Clasificación de los 53 Módulos

### TIER 1 — Hexagonal Estricto (Reglas de negocio complejas)
> Aquí la lógica de negocio **cambia por normatividad SEP/UAEMEX** y debe estar aislada.

| Módulo | Razón | Complejidad |
|--------|-------|-------------|
| `evaluaciones` | 26 archivos. Rúbricas, NEE, ponderaciones. Reglas SEP | ★★★★★ |
| `calificaciones` | Fórmulas por nivel, escalas SEP vs UAEMEX | ★★★★☆ |
| `gradebook` | Promedios, actividades, entregas, cálculo final | ★★★★☆ |
| `asistencias` | Regla del 80%, justificaciones, tolerancias | ★★★★☆ |
| `planeacion` | Estado PENDIENTE→PLANEADO→IMPARTIDO, cobertura mínima | ★★★☆☆ |
| `reinscripcion` | Flujo de ciclo escolar, prerequisitos, cupos | ★★★☆☆ |
| `conducta` | Sanciones, planes de mejora, alertas automáticas | ★★★☆☆ |
| `certificados` | Firma Ed25519, validación de folio, QR | ★★★☆☆ |
| `portal` | Flujo postulación, ARCO, LFPDPPP compliance | ★★★☆☆ |

### TIER 2 — CQRS Pragmático (Lecturas directas + Comandos simples)
> CRUD con contexto de negocio. Las **lecturas** van directo a SQL; las **escrituras** pasan por un caso de uso ligero.

| Módulo | Complejidad |
|--------|-------------|
| `alumnos`, `profesores` | ★★☆☆☆ |
| `grupos`, `horarios` | ★★☆☆☆ |
| `expediente`, `movilidad` | ★★★☆☆ |
| `comunicados`, `foros`, `encuestas` | ★★☆☆☆ |
| `badges`, `medico` | ★★☆☆☆ |
| `licencias`, `capacitaciones`, `disponibilidad` | ★★☆☆☆ |
| `condiciones`, `justificaciones` | ★★☆☆☆ |
| `padres`, `portal_familias`, `eval_docente` | ★★☆☆☆ |

### TIER 3 — 3 Capas Tradicional (CRUD puro, no tocar)
> Son catálogos y configuración. Hexagonal aquí sería sobre-ingeniería.

| Módulo |
|--------|
| `planteles`, `materias`, `aulas` |
| `catalogos`, `sistema`, `menus` |
| `contactos`, `direcciones`, `geo` |
| `planes_estudio`, `usuarios` |

### TIER 4 — Query-Only (SQL directo es correcto aquí)
> Analítica y reportería. La capa de servicio hace SQL complejo intencionalmente.

| Módulo | Rol |
|--------|-----|
| `grade_analytics` | Consultas OLAP complejas |
| `stats` | KPIs institucionales |
| `boletas` | Generación PDF |
| `auditoria` | Log de cambios |
| `superset` | Pass-through a Superset BI |

---

## 3. Estructura de Paquetes Propuesta

```
mx.ades/
│
├── shared/                          ← NUEVO: Tipos compartidos
│   ├── domain/
│   │   ├── AdesId.java             (record: UUID + método estático of(UUID))
│   │   ├── PlantelId.java          (record envolvente)
│   │   ├── CicloEscolarId.java
│   │   └── AuditInfo.java         (record puro, sin JPA)
│   └── infrastructure/
│       ├── AdesBaseEntity.java     (se mantiene - solo JPA)
│       └── AdesJpaMapper.java      (utilidad de conversión)
│
├── common/                          ← EXISTENTE (sin cambios)
│   ├── AdesBaseEntity.java
│   ├── AdesAuditEntity.java
│   └── WebhookService.java
│
├── config/                          ← EXISTENTE + extensión
│   ├── SecurityConfig.java
│   ├── HexagonalConfig.java        ← NUEVO: @Configuration de beans de dominio
│   └── EventConfig.java            ← NUEVO: @EnableAsync para eventos
│
├── security/                        ← EXISTENTE, sin cambios
│
└── modules/
    ├── [modulo_crud]/               ← TIER 3: Sin cambios estructurales
    │   ├── Entity.java
    │   ├── Repository.java
    │   └── Controller.java
    │
    ├── [modulo_cqrs]/               ← TIER 2: Separar reads de writes
    │   ├── AlumnoController.java    (extrae SQL a service)
    │   ├── AlumnoQueryService.java  ← NUEVO: Lecturas directas JdbcTemplate
    │   ├── AlumnoCommandService.java ← NUEVO: Escrituras con validación
    │   ├── AlumnoRepository.java    (JPA, sin cambios)
    │   └── AlumnoEntity.java       (JPA, renombrado de la actual)
    │
    └── [modulo_hexagonal]/          ← TIER 1: Estructura completa
        ├── domain/
        │   ├── model/
        │   │   ├── Evaluacion.java           (POJO puro, sin @Entity)
        │   │   └── ResultadoEvaluacion.java  (Value Object)
        │   ├── port/
        │   │   ├── in/
        │   │   │   ├── RegistrarEvaluacionUseCase.java   (interfaz)
        │   │   │   └── ConsultarResultadosUseCase.java   (interfaz)
        │   │   └── out/
        │   │       ├── EvaluacionRepositoryPort.java     (interfaz)
        │   │       └── NotificarResultadoPort.java       (interfaz)
        │   └── exception/
        │       └── EvaluacionCerradaException.java
        ├── application/
        │   └── service/
        │       └── RegistrarEvaluacionService.java  (implements UseCase, sin @Service)
        └── infrastructure/
            ├── inbound/
            │   └── rest/
            │       ├── EvaluacionController.java    (@RestController)
            │       └── dto/
            │           ├── EvaluacionRequest.java
            │           └── EvaluacionResponse.java
            └── outbound/
                ├── persistence/
                │   ├── EvaluacionJpaEntity.java     (@Entity — solo aquí)
                │   ├── EvaluacionJpaRepository.java (Spring Data)
                │   ├── EvaluacionPersistenceAdapter.java
                │   └── EvaluacionMapper.java        (MapStruct)
                └── events/
                    └── NotificarResultadoAdapter.java (ntfy/n8n)
```

---

## 4. Plan de Migración — 4 Fases

### FASE 0: Preparación del Terreno (Semana 1–2)
> **Objetivo:** Sentar bases sin romper nada. Zero funcionalidad cambia.

**0.1 Crear shared/domain** — Tipos de dominio sin dependencias:
```java
// shared/domain/PlantelId.java
public record PlantelId(UUID value) {
    public static PlantelId of(UUID id) { return new PlantelId(id); }
    public static PlantelId of(String id) { return new PlantelId(UUID.fromString(id)); }
}

// shared/domain/AuditInfo.java — sin JPA
public record AuditInfo(
    OffsetDateTime fechaCreacion,
    OffsetDateTime fechaModificacion,
    String usuarioCreacion,
    String usuarioModificacion
) {}
```

**0.2 Crear HexagonalConfig.java** — Configuration que registra beans de dominio:
```java
@Configuration
public class HexagonalConfig {
    // Los beans de Application Services se declaran aquí, NO con @Service
    // Ejemplo futuro:
    // @Bean
    // public RegistrarEvaluacionUseCase registrarEvaluacion(EvaluacionRepositoryPort repo) {
    //     return new RegistrarEvaluacionService(repo);
    // }
}
```

**0.3 Habilitar testing** — Crear src/test con base:
```
src/test/java/mx/ades/
├── shared/             ← Tests de tipos de dominio
└── modules/            ← Tests por módulo
```

**0.4 Configurar MapStruct** (ya en pom.xml, sin usar):
```java
// Activar en AdesBaseMapper.java
@MapperConfig(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AdesBaseMapper {}
```

**Entregable:** PR limpio sin cambios de comportamiento. CI verde.

---

### FASE 1: Módulo Piloto — `asistencias` (Semana 3–5)
> **¿Por qué asistencias?** Regla clara (80% mínimo), no depende de otros módulos complejos, y su lógica actual está dispersa entre Controller y Service.

**1.1 Dominio puro:**
```java
// modules/asistencias/domain/model/Asistencia.java
public final class Asistencia {
    private final UUID id;
    private final UUID claseId;
    private final UUID estudianteId;
    private final EstatusAsistencia estatus;
    private final String observacion;

    // Factory method con regla de negocio
    public static Asistencia registrar(UUID claseId, UUID estudianteId,
                                        EstatusAsistencia estatus, String obs) {
        Objects.requireNonNull(claseId, "claseId requerido");
        Objects.requireNonNull(estudianteId, "estudianteId requerido");
        if (estatus == null) estatus = EstatusAsistencia.AUSENTE;
        return new Asistencia(UuidCreator.getTimeOrderedEpoch(), claseId, estudianteId, estatus, obs);
    }

    // Regla SEP: porcentaje de asistencia para acreditar
    public static boolean acreditaAsistencia(long totalClases, long asistenciasRegistradas) {
        if (totalClases == 0) return true;
        return (asistenciasRegistradas * 100.0 / totalClases) >= 80.0;
    }
}
```

**1.2 Puerto de salida:**
```java
// modules/asistencias/domain/port/out/AsistenciaRepositoryPort.java
public interface AsistenciaRepositoryPort {
    List<Asistencia> findByClaseId(UUID claseId);
    void guardarMasivo(List<Asistencia> asistencias);
    long contarAsistenciasByEstudiante(UUID estudianteId, UUID grupoId, EstatusAsistencia estatus);
}
```

**1.3 Caso de uso:**
```java
// modules/asistencias/domain/port/in/RegistrarAsistenciaMasivaUseCase.java
public interface RegistrarAsistenciaMasivaUseCase {
    void ejecutar(UUID claseId, List<AsistenciaDto> asistencias, String usuarioCreacion);
}

// modules/asistencias/application/service/RegistrarAsistenciaMasivaService.java
public class RegistrarAsistenciaMasivaService implements RegistrarAsistenciaMasivaUseCase {
    private final AsistenciaRepositoryPort repository; // inyectado por constructor

    @Override
    public void ejecutar(UUID claseId, List<AsistenciaDto> dtos, String usuarioCreacion) {
        List<Asistencia> asistencias = dtos.stream()
            .map(dto -> Asistencia.registrar(claseId, dto.estudianteId(),
                                             dto.estatus(), dto.observacion()))
            .toList();
        repository.guardarMasivo(asistencias);
    }
}
```

**1.4 Test unitario sin base de datos:**
```java
class RegistrarAsistenciaMasivaServiceTest {
    @Mock AsistenciaRepositoryPort repositoryPort;
    @InjectMocks RegistrarAsistenciaMasivaService service;

    @Test
    void debeRegistrar_asistencias_validas() {
        // Arrange
        var claseId = UUID.randomUUID();
        var dtos = List.of(new AsistenciaDto(UUID.randomUUID(), EstatusAsistencia.PRESENTE, null));

        // Act
        service.ejecutar(claseId, dtos, "profesor@nevadi.edu.mx");

        // Assert
        verify(repositoryPort).guardarMasivo(any());
    }

    @Test
    void regla_80_porciento_asistencia() {
        assertFalse(Asistencia.acreditaAsistencia(10, 7));  // 70% no acredita
        assertTrue(Asistencia.acreditaAsistencia(10, 8));   // 80% acredita
        assertTrue(Asistencia.acreditaAsistencia(10, 10));  // 100% acredita
        assertTrue(Asistencia.acreditaAsistencia(0, 0));    // sin clases, acredita
    }
}
```

**Entregable:** Módulo `asistencias` migrado, 10+ tests verdes, patrón documentado como referencia para el equipo.

---

### FASE 2: Módulos Core Hexagonales (Meses 2–3)
> Aplicar el mismo patrón del piloto a los módulos de mayor riesgo normativo.

**Orden de migración recomendado:**

```
evaluaciones ──→ calificaciones ──→ gradebook ──→ planeacion ──→ conducta
```

**2.1 Regla de calificaciones (ejemplo real de ADES):**
```java
// modules/calificaciones/domain/model/Calificacion.java
public final class Calificacion {

    // Escala SEP: 5-10 (primaria/secundaria)
    public static EstatusPromocion evaluarPromocionSEP(double promedio) {
        if (promedio >= 6.0) return EstatusPromocion.APROBADO;
        if (promedio >= 5.0) return EstatusPromocion.A_TITULO_SUFICIENCIA;
        return EstatusPromocion.REPROBADO;
    }

    // Escala UAEMEX: 0-10 con 6.0 mínimo (preparatoria)
    public static EstatusPromocion evaluarPromocionUAEMEX(double promedio) {
        return promedio >= 6.0 ? EstatusPromocion.APROBADO : EstatusPromocion.REPROBADO;
    }

    // Dispatch por nivel educativo
    public static EstatusPromocion evaluar(double promedio, NivelEducativo nivel) {
        return switch (nivel) {
            case PRIMARIA, SECUNDARIA -> evaluarPromocionSEP(promedio);
            case PREPARATORIA         -> evaluarPromocionUAEMEX(promedio);
        };
    }
}
```

**2.2 Evento de dominio:**
```java
// modules/calificaciones/domain/event/CalificacionCerradaEvent.java
public record CalificacionCerradaEvent(
    UUID estudianteId,
    UUID materiaId,
    UUID grupoId,
    double promedio,
    EstatusPromocion estatus,
    String periodo,
    Instant ocurridoEn
) {}
```

**2.3 Listener de infraestructura:**
```java
// modules/calificaciones/infrastructure/outbound/events/NotificarPadreListener.java
@Component
public class NotificarPadreListener {

    private final WebhookService webhookService;

    @EventListener
    @Async
    public void onCalificacionCerrada(CalificacionCerradaEvent event) {
        if (event.estatus() == EstatusPromocion.REPROBADO) {
            webhookService.dispararN8n("calificacion-reprobada", Map.of(
                "estudiante_id", event.estudianteId(),
                "materia_id",    event.materiaId(),
                "promedio",      event.promedio()
            ));
        }
    }
}
```

---

### FASE 3: CQRS Pragmático para TIER 2 (Meses 3–4)
> **Objetivo:** Separar lecturas de escrituras en los módulos CRUD con contexto.

**Patrón a aplicar en `alumnos`, `profesores`, `grupos`, etc.:**

```java
// ANTES: AlumnoController inyecta JdbcTemplate + Repository directamente

// DESPUÉS:
// AlumnoController ──→ AlumnoQueryService (lecturas - JdbcTemplate OK aquí)
//                 ──→ AlumnoCommandService (escrituras - validaciones negocio)

@Service
@RequiredArgsConstructor
public class AlumnoQueryService {
    private final JdbcTemplate jdbc;
    private final AdesUserService userService;

    public PagedResult<AlumnoListadoDto> listar(UUID plantelId, Pageable pageable) {
        // SQL directo es CORRECTO aquí — es una query de lectura
        String sql = """
            SELECT e.id, e.matricula,
                   COALESCE(p.nombre_social, p.nombre) AS nombre,
                   p.apellido_paterno, p.apellido_materno, p.curp
            FROM ades_estudiantes e JOIN ades_personas p ON p.id = e.persona_id
            WHERE e.is_active = true AND e.plantel_id = ?
            ORDER BY p.apellido_paterno, p.nombre
            LIMIT ? OFFSET ?
        """;
        // ... implementación
    }
}

@Service
@RequiredArgsConstructor
@Transactional
public class AlumnoCommandService {
    private final EstudianteRepository repository;

    public UUID crearAlumno(CrearAlumnoCommand cmd) {
        // Validaciones de negocio: CURP único, matrícula generada, etc.
        validarCurpUnico(cmd.curp());
        var entidad = mapearAEntidad(cmd);
        return repository.save(entidad).getId();
    }
}
```

**AlumnoController resultante (solo orquesta):**
```java
@RestController
@RequestMapping("/api/v1/alumnos")
@RequiredArgsConstructor
public class AlumnoController {
    private final AlumnoQueryService  queries;    // lecturas
    private final AlumnoCommandService commands;  // escrituras
    private final AdesUserService userService;

    @GetMapping
    public ResponseEntity<?> list(@RequestParam Optional<UUID> plantelId, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID efectivo = userService.getEffectivePlantelId(user, plantelId.orElse(null));
        return ResponseEntity.ok(queries.listar(efectivo, Pageable.unpaged()));
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody @Valid CrearAlumnoRequest req, @AuthenticationPrincipal Jwt jwt) {
        UUID id = commands.crearAlumno(req.toCommand());
        return ResponseEntity.status(201).body(Map.of("id", id));
    }
}
```

---

### FASE 4: Ecosistema de Eventos (Meses 5–6)
> **Objetivo:** Conectar el dominio con los servicios externos de forma asíncrona.

**Configuración:**
```java
@Configuration
@EnableAsync
public class EventConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(10);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("ades-events-");
        exec.initialize();
        return exec;
    }
}
```

**Mapa de eventos planificados:**

| Evento de dominio | Listeners de infraestructura |
|-------------------|------------------------------|
| `AlumnoInscritoEvent` | Genera expediente médico, envía bienvenida vía n8n, crea carpeta MinIO |
| `CalificacionCerradaEvent` | Notifica padres vía ntfy si hay materias reprobadas |
| `AsistenciaAusentismoEvent` | Alerta coordinador si alumno supera umbral de faltas |
| `SancionConductaEvent` | Notifica padres, crea registro en bitácora Paperless |
| `PostulacionEnviadaEvent` | Email confirmación vía portal, webhook n8n |
| `CertificadoEmitidoEvent` | Guarda en MinIO, registra en blockchain FASE 5 |

---

## 5. Reglas de Convivencia Durante la Migración

### Regla 1: Feature Flag por Módulo
```java
// En application.yml
ades:
  hexagonal:
    modulos-activos: asistencias, calificaciones
    # módulos no listados → flujo anterior intacto
```

### Regla 2: Nunca mezclar capas
```
✅ domain/model/*.java    → imports: java.*, java.time.*, java.util.*
✅ domain/model/*.java    → NO imports: org.springframework.*, jakarta.*
✅ application/service/  → importa interfaces de domain/port/, NO de infrastructure/
✅ infrastructure/        → puede importar todo
```

### Regla 3: Tests como contrato
- Todo Use Case nuevo: mínimo 3 tests unitarios
- Todo Adaptador de persistencia: mínimo 1 test de integración con Testcontainers

### Regla 4: Controllers siempre delgados
- Máximo 40 líneas de código efectivo por método de controller
- Si supera → extraer a QueryService o CommandService

---

## 6. Métricas de Éxito

| Métrica | Hoy | Meta Fase 2 | Meta Final |
|---------|-----|-------------|------------|
| Módulos con tests | 0/53 | 5/53 | 40/53 |
| Cobertura core | 0% | 60% | 80% |
| Módulos hexagonales | 0/9 | 3/9 | 9/9 |
| Módulos CQRS | 0/14 | 0/14 | 14/14 |
| Controllers con JdbcTemplate directo | 37 | 30 | 5 |
| Tiempo promedio test suite | N/A | < 30s | < 60s |

---

## 7. Estimación de Tiempo Real

| Fase | Duración estimada | Riesgo |
|------|-------------------|--------|
| 0: Preparación | 1–2 semanas | Bajo |
| 1: Piloto (asistencias) | 2–3 semanas | Bajo |
| 2: Core hexagonal (5 módulos) | 6–8 semanas | Medio |
| 3: CQRS TIER 2 (12 módulos) | 4–6 semanas | Bajo |
| 4: Eventos | 3–4 semanas | Medio |
| **Total** | **4–5 meses** | **Medio** |

> **Estrategia Strangler Fig:** Los módulos TIER 3 y TIER 4 **nunca se tocan**. El sistema opera en producción durante toda la migración.

---

## 8. Decisión

Adoptar migración **incremental** (no Big Bang) con el patrón Strangler Fig:

- TIER 1 → Hexagonal completo
- TIER 2 → CQRS pragmático (QueryService + CommandService)
- TIER 3 → No tocar (ya funciona, CRUD puro)
- TIER 4 → No tocar (SQL analítico intencional)
- Eventos de dominio → `ApplicationEventPublisher` nativo de Spring (no Kafka/RabbitMQ aún)

---

## 9. Resultado Final (2026-06-16)

### Métricas logradas

| Métrica | Inicial | Meta Final | Resultado Real |
|---------|---------|------------|----------------|
| Módulos con tests | 0/53 | 40/53 | 53/53 ✅ |
| Cobertura core | 0% | 80% | 100% controllers ✅ |
| Módulos hexagonales (TIER 1) | 0/9 | 9/9 | 9/9 ✅ |
| Módulos CQRS (TIER 2) | 0/14 | 14/14 | 14/14 ✅ |
| Controllers con JdbcTemplate directo | 37 | 5 | **0** ✅ |
| Total tests | 0 | N/A | **528 tests, 0 fallos** ✅ |

### Hito técnico

```bash
grep -r "JdbcTemplate" backend-spring/src/main/java/mx/ades/modules/**/*Controller.java
# → 0 resultados. Todos los controllers son HTTP-puro.
```

### Patrón definitivo aplicado

Emergió un patrón pragmático no previsto en el ADR original, más efectivo que el planificado:

```
Controller     → solo @RestController, sin lógica
QueryService   → @Service, JdbcTemplate, lecturas CQRS
WriteService   → @Component, JdbcTemplate + @Transactional, escrituras encapsuladas
UseCaseService → sin @Service, registrado en HexagonalConfig, lógica de negocio compleja
```

### 69 FASES completadas

- FASES 0–41: Migración incremental modulo a modulo con domain models, use cases, ports, adapters
- FASES 42–69: Extracción total de JdbcTemplate de todos los controllers hacia QueryServices y WriteServices
- Patrón CQRS con `@Component WriteService + @Transactional` adoptado para módulos de datos masivos (imports)

### Deuda técnica registrada

- Los `@Component` write services no tienen ports/interfaces — son pragmáticos, no hexagonales
- Los TIER 3/4 siguen sin tests de integración con Testcontainers
- Eventos de dominio solo en módulos TIER 1 (asistencias, calificaciones, conducta, gradebook, expediente, reinscripcion)

**Estrategia Strangler Fig validada: sistema en producción durante toda la migración, sin romper funcionalidad existente.**

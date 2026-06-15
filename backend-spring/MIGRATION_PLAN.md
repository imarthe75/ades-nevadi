# Plan de Migración ADES — FastAPI → Spring Boot BFF
**Versión:** 2.0 | **Actualizado:** 2026-06-12 | **Responsable:** imarthe75

---

## Contexto y decisiones estratégicas

### Por qué Spring Boot para ADES
- **3,500 usuarios activos**, 180k+ asistencias, flujos financieros y académicos críticos
- **460 endpoints FastAPI** en 70 archivos, ~21,000 líneas de Python
- Necesidad de **transacciones ACID reales** en cierre de ciclo, reinscripción y calificaciones
- **Spring Security + Authentik** — integración OIDC más profunda que jose-jwt manual
- Preparación para integración futura con SEP (certificaciones digitales), facturación electrónica, Spring Batch para procesos masivos

### Qué queda permanente en FastAPI (microservicio IA)
| Módulo FastAPI | Razón |
|---|---|
| `ai_assistant.py`, `chatbot.py`, `ia_avanzada.py` | Python ecosistema IA/LLM superior |
| `carbone.py`, `pdf_tools.py` | Librerías de PDF nativas de Python |
| `webhooks.py`, `automations.py` | Procesamiento de eventos async |
| `push.py` | Notificaciones push (web-push en Python) |

---

## Arquitectura objetivo

```
Internet → nginx (443)
├── /api/v1/ai/**            → FastAPI :8000 (permanente)
├── /api/v1/chat/**          → FastAPI :8000 (permanente)
├── /api/v1/pdf/**           → FastAPI :8000 (permanente)
├── /api/v1/**               → Spring Boot :8080 (destino final)
│
├── PostgreSQL 18 (compartido, mismo schema sin cambios)
├── Valkey 9.1 (Spring Cache + FastAPI, misma instancia)
└── Authentik 2026.5.2 (OIDC provider)
```

**Patrón Strangler Fig**: nginx enruta módulos migrados a Spring Boot; FastAPI sigue sirviendo lo que aún no migró. Los dos backends leen/escriben el mismo PostgreSQL. La transición es invisible para el frontend.

---

## Reglas técnicas inamovibles

### 1. PKs — UUID v7 obligatorio
```java
// pom.xml: com.github.f4b6a3:uuid-creator:6.0.0
import com.github.f4b6a3.uuid.UuidCreator;

@Id
@Column(columnDefinition = "uuid")
private UUID id = UuidCreator.getTimeOrderedWithRandom();
```

### 2. Auditoría — NO tocar las columnas de auditoría desde JPA
Los triggers `audit_biu` y `audit_aiud` en PostgreSQL gestionan automáticamente:
`ref`, `row_version`, `fecha_creacion`, `fecha_modificacion`, `usuario_creacion`, `usuario_modificacion`

```java
// CORRECTO: excluir columnas de auditoría del INSERT/UPDATE de JPA
@Column(name = "row_version", insertable = false, updatable = false)
private Integer rowVersion;

@Column(name = "fecha_creacion", insertable = false, updatable = false)
private OffsetDateTime fechaCreacion;

@Column(name = "fecha_modificacion", insertable = false, updatable = false)
private OffsetDateTime fechaModificacion;

@Column(name = "usuario_creacion", insertable = false, updatable = false)
private String usuarioCreacion;

@Column(name = "usuario_modificacion", insertable = false, updatable = false)
private String usuarioModificacion;
```

Para que el trigger conozca quién hace el cambio, inyectar el `sub` del JWT en la sesión PostgreSQL:
```java
// AuditSessionInterceptor.java — ejecutar antes de cada transacción
entityManager.createNativeQuery(
    "SET LOCAL app.current_user = :sub"
).setParameter("sub", currentUserSub).executeUpdate();
```

### 3. Optimistic locking con `@Version`
```java
@Version
@Column(name = "row_version", insertable = false, updatable = false)
private Integer rowVersion;
```
JPA usará `row_version` para detectar conflictos — compatible con el trigger `audit_biu` que lo incrementa.

### 4. Flyway — NO ejecutar nuevas migraciones SQL desde Spring Boot
El schema lo administra `db/migrations/` del proyecto ADES. Flyway en Spring Boot solo valida:
```yaml
spring.flyway.enabled: false          # Desarrollo
spring.jpa.hibernate.ddl-auto: validate
```
En producción, si se activa Flyway, apuntar al mismo directorio con `baseline-on-migrate: true`.

### 5. Equivalente a `get_ades_user` en Spring Boot
Ver sección "Security" más abajo.

---

## Equivalente de `get_ades_user` — JIT User Provisioning

El fastAPI actual hace:
1. Decodificar JWT RS256 (Authentik JWKS)
2. Buscar usuario por `oidc_sub` o `email_institucional`
3. Detectar discrepancia de roles entre JWT `groups` y `ades_usuario_roles`
4. Sync JIT de roles si hay discrepancia
5. Cargar privilegios

Implementación Spring Boot equivalente:

```java
// AdesUserService.java
@Service @RequiredArgsConstructor
public class AdesUserService {

    private final UsuarioRepository usuarioRepo;
    private final RolRepository rolRepo;
    private final JdbcTemplate jdbc;

    @Transactional
    public AdesUser resolveUser(Jwt jwt) {
        String sub   = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        Usuario usuario = usuarioRepo
            .findByOidcSubOrEmailInstitucional(sub, email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Usuario no registrado en ADES. Contacta al administrador."));

        List<String> jwtGroups = jwt.getClaimAsStringList("groups");
        if (jwtGroups != null && !jwtGroups.isEmpty()) {
            syncRoles(usuario.getId(), jwtGroups);
        }

        return buildAdesUser(usuario, jwt);
    }

    private void syncRoles(UUID userId, List<String> jwtGroups) {
        List<UUID> dbRoleIds = jdbc.queryForList(
            "SELECT rol_id FROM ades_usuario_roles WHERE usuario_id = ?",
            UUID.class, userId);
        List<UUID> jwtRoleIds = rolRepo.findByNombreRolIn(jwtGroups)
            .stream().map(Rol::getId).toList();

        if (!new HashSet<>(dbRoleIds).equals(new HashSet<>(jwtRoleIds))) {
            jdbc.update("DELETE FROM ades_usuario_roles WHERE usuario_id = ?", userId);
            jwtRoleIds.forEach(rid ->
                jdbc.update("INSERT INTO ades_usuario_roles (usuario_id, rol_id, peso) VALUES (?,?,100)",
                    userId, rid));
        }
    }
}
```

Inyectar en cada controlador vía `@AuthenticationPrincipal`:
```java
@GetMapping("/me")
public UserDto me(@AuthenticationPrincipal Jwt jwt) {
    return userService.resolveUser(jwt).toDto();
}
```

---

## Fases de migración

### Fase 0 — Infraestructura compartida (Semanas 1-2)
**Sin tocar el frontend ni FastAPI aún.**

**Acciones:**
- [ ] Completar `SecurityConfig.java` con extractor de claims de Authentik:
  ```java
  .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
      .jwkSetUri("http://ades-authentik-server:9000/application/o/ades-frontend/.well-known/jwks.json")
      .jwtAuthenticationConverter(adesJwtConverter())
  ))
  ```
- [ ] Implementar `AdesJwtConverter` que extrae `groups`, `email`, `sub` de los claims de Authentik
- [ ] Implementar `AuditSessionInterceptor` — inyecta `SET LOCAL app.current_user` antes de cada TX
- [ ] Implementar `AdesUserService.resolveUser()` (JIT provisioning — ver arriba)
- [ ] Agregar `uuid-creator` al `pom.xml` y crear `AdesBaseEntity` abstracta con UUID v7 + campos de auditoría read-only
- [ ] Agregar `spring-boot-starter-cache` + Valkey (Redis-compatible): `spring.data.redis.host=localhost spring.data.redis.port=6379`
- [ ] Agregar `spring-boot-starter-validation` para Bean Validation en DTOs
- [ ] Dockerfile: asegurar multi-stage build con JDK 21 (LTS)
- [ ] Variable de entorno: `SPRING_DATASOURCE_PASSWORD` desde `.env`
- [ ] Configurar docker-compose.yml: agregar servicio `ades-bff` en puerto 8080

**Entregable:** Spring Boot levanta en puerto 8080, `/api/v1/health` responde, JWT de Authentik se valida correctamente, `resolveUser()` funciona contra la BD de desarrollo.

---

### Fase 1 — Auth, Usuarios y Catálogos (Semanas 3-6)
**Módulos FastAPI a reemplazar:** `auth_callback.py`, `usuarios.py`, `catalogs.py`, `planteles.py`
**~35 endpoints**

**Prioridad:** Alta — base de todos los demás módulos.

**Entidades JPA a crear:**
- `Plantel`, `NivelEducativo`, `Grado`, `CicloEscolar`
- `Usuario`, `Rol`, `Privilegio`, `UsuarioRol`

**Patrón estándar para cada entidad:**
```java
@Entity @Table(name = "ades_planteles")
public class Plantel extends AdesBaseEntity {
    @Column(nullable = false) private String nombrePlantel;
    @Column(unique = true)    private String claveCt;
    @Column(nullable = false) private Boolean isActive = true;
    // NO incluir campos de auditoría aquí — están en AdesBaseEntity como read-only
}
```

**Controladores:**
```
GET  /api/v1/planteles              → PlantелController.list()
GET  /api/v1/planteles/{id}         → PlantелController.get()
POST /api/v1/planteles              → PlantелController.create()  @PreAuthorize("hasRole('ADMIN')")
PUT  /api/v1/planteles/{id}         → PlantелController.update()
GET  /api/v1/catalogs/niveles       → CatalogsController.niveles()
GET  /api/v1/catalogs/grados        → CatalogsController.grados()
GET  /api/v1/catalogs/ciclos        → CatalogsController.ciclos()
```

**nginx routing update (al finalizar fase):**
```nginx
location /api/v1/planteles        { proxy_pass http://ades-bff:8080; }
location /api/v1/catalogs         { proxy_pass http://ades-bff:8080; }
location /api/v1/usuarios         { proxy_pass http://ades-bff:8080; }
location /api/v1/auth             { proxy_pass http://ades-bff:8080; }
```

**Tests requeridos:**
- Integration test con Testcontainers (PostgreSQL) para cada endpoint
- Verificar que `audit_biu` trigger se ejecuta correctamente (no sobreescribimos campos de auditoría)
- Verificar sync JIT de roles

---

### Fase 2 — Módulos transaccionales críticos (Semanas 7-12)
**Módulos FastAPI a reemplazar:** `reinscripcion.py`, `calificaciones.py`, `gradebook.py`, `cierre_ciclo.py`
**~24 endpoints — los más críticos del sistema**

**Prioridad:** Máxima — aquí Spring Boot gana más sobre FastAPI.

#### Reinscripción (6 endpoints)
```java
@Service @Transactional(isolation = Isolation.REPEATABLE_READ)
public class ReinscripcionService {

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ReinscripcionResult procesarReinscripcion(UUID alumnoId, UUID cicloId, AdesUser user) {
        // Validar cupo, estado académico, adeudos
        // Crear ades_reinscripcion_ciclo con row_version gestionado por trigger
        // Actualizar ades_grupos_alumnos
        // Emitir evento a n8n via webhook async
    }
}
```

#### Gradebook / Calificaciones (14 endpoints)
- Usar `@Transactional` con rollback explícito en `calcularCalificacionPeriodo()`
- La función PG `calcular_calificacion_periodo()` se llama vía `CALL` nativo — no reimplementar en Java
- `@Version` en `CalPeriodo` para optimistic locking en ajustes manuales
- Cache en Valkey: boletas por alumno, TTL 5 min, invalidar al guardar calificación

#### Cierre de ciclo (4 endpoints)
```java
@Transactional(isolation = Isolation.SERIALIZABLE, timeout = 120)
public CierreResult cerrarCiclo(UUID cicloId, AdesUser user) {
    // 1. Lock optimista en ades_ciclos_escolares
    // 2. Validar que todos los grupos tengan calificaciones cerradas
    // 3. Promover alumnos aprobados al siguiente grado
    // 4. Marcar ciclo como cerrado
    // 5. Audit log explícito en auditoria.log_auditoria
    // Si falla: rollback automático por @Transactional
}
```

**nginx routing update (al finalizar fase):**
```nginx
location /api/v1/reinscripcion    { proxy_pass http://ades-bff:8080; }
location /api/v1/calificaciones   { proxy_pass http://ades-bff:8080; }
location /api/v1/gradebook        { proxy_pass http://ades-bff:8080; }
location /api/v1/cierre-ciclo     { proxy_pass http://ades-bff:8080; }
```

---

### Fase 3 — Módulos académicos operativos (Semanas 13-18)
**Módulos:** `alumnos.py`, `grupos.py`, `profesores.py`, `asistencias.py`, `materias.py`, `planes_estudio.py`, `horarios.py`, `aulas.py`
**~55 endpoints**

**Prioridad:** Alta — uso diario intensivo.

**Particularidades:**
- `asistencias.py`: operación masiva frecuente (registro de asistencia de todo un grupo). Usar `JdbcTemplate.batchUpdate()` — no JPA para este caso
- `grupos.py`: Patrón multiplantel — filtrar siempre por `plantel_id` del JWT claim `plantel`
- `horarios.py`: Validación de conflictos de horario — query nativa con `tsrange` de PostgreSQL

**nginx routing:** agregar los 8 módulos al finalizar.

---

### Fase 4 — Módulos de evaluación y rúbricas (Semanas 19-22)
**Módulos:** `evaluaciones.py`, `evaluacion_avanzada.py`, `eval_docente.py`, `rubricas.py`, `tareas.py`, `entregas.py`, `planeacion.py`
**~55 endpoints**

**Particularidades:**
- `entregas.py`: archivos a MinIO — usar `io.minio:minio:8.5.9` Java client
- `planeacion.py`: insights con JOIN complejo — mantener como native query

---

### Fase 5 — Módulos de gestión y comunicación (Semanas 23-28)
**Módulos:** `comunicados.py`, `encuestas.py`, `badges.py`, `foros.py`, `conducta.py`, `medico.py`, `condiciones_cronicas.py`, `movilidad.py`, `licencias.py`, `capacitaciones.py`, `disponibilidad.py`
**~75 endpoints**

---

### Fase 6 — Módulos de expediente, portal y reportes (Semanas 29-34)
**Módulos:** `expediente.py`, `expediente_documentos.py`, `expediente_laboral.py`, `padres.py`, `portal.py`, `portal_familias.py`, `certificados.py`, `learning_paths.py`, `stats.py`, `grade_analytics.py`, `reportes.py`, `imports.py`
**~85 endpoints**

**Particularidades:**
- `stats.py` y `grade_analytics.py`: queries analíticas complejas — usar Spring Data con `@Query` nativo
- `imports.py`: procesamiento batch de Excel/CSV — usar **Spring Batch** con `ItemReader/ItemProcessor/ItemWriter`
- `certificados.py`: firma Ed25519 digital — mantener el servicio Python (`firma_digital.py`) y llamarlo vía HTTP desde Spring Boot; Spring Boot solo maneja los endpoints REST

---

### Fase 7 — FastAPI AI microservicio final (permanente)
**Módulos que quedan en FastAPI para siempre:**
`ai_assistant.py`, `chatbot.py`, `ia_avanzada.py`, `carbone.py`, `pdf_tools.py`, `webhooks.py`, `automations.py`, `push.py`

Spring Boot los consume vía HTTP interno:
```java
@Service
public class AiGatewayService {
    private final RestClient restClient = RestClient.builder()
        .baseUrl("http://ades-api:8000/api/v1")
        .build();

    public AiResponse generateInsights(String grupoId, String token) {
        return restClient.post()
            .uri("/ai/insights/{grupoId}", grupoId)
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .body(AiResponse.class);
    }
}
```

---

## Estructura de paquetes Spring Boot

```
mx.ades/
├── config/
│   ├── SecurityConfig.java          # OAuth2 Resource Server
│   ├── CacheConfig.java             # Valkey/Redis cache
│   ├── JpaConfig.java               # Audit interceptor
│   └── AuditSessionInterceptor.java # SET LOCAL app.current_user
├── security/
│   ├── AdesJwtConverter.java        # Claims de Authentik → authorities
│   ├── AdesUser.java                # Usuario resuelto del JWT+BD
│   └── AdesUserService.java         # JIT provisioning
├── common/
│   ├── AdesBaseEntity.java          # UUID v7 + audit fields read-only
│   ├── PagedResponse.java           # Paginación estándar
│   └── AdesException.java           # Excepciones tipadas
├── modules/
│   ├── planteles/
│   │   ├── PlantelController.java
│   │   ├── PlantelService.java
│   │   ├── PlantelRepository.java
│   │   ├── Plantel.java             # JPA entity
│   │   └── PlantelDto.java          # Request/Response DTO
│   ├── usuarios/ ...
│   ├── reinscripcion/ ...
│   ├── gradebook/ ...
│   └── [un paquete por módulo]
└── gateway/
    └── AiGatewayService.java        # Proxy hacia FastAPI AI
```

---

## pom.xml — dependencias clave a agregar

```xml
<!-- UUID v7 -->
<dependency>
    <groupId>com.github.f4b6a3</groupId>
    <artifactId>uuid-creator</artifactId>
    <version>6.0.0</version>
</dependency>

<!-- Validación -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- Cache (Valkey/Redis-compatible) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>

<!-- MinIO (para entregas/documentos) -->
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.9</version>
</dependency>

<!-- Spring Batch (para imports masivos) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-batch</artifactId>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>

<!-- MapStruct (DTO mapping) -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.6.2</version>
</dependency>

<!-- Tests: Testcontainers -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Testing por fase

Cada módulo migrado DEBE tener antes de activar nginx routing:

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReinscripcionControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18")
        .withInitScript("db/migrations/all.sql");

    @Test
    void procesarReinscripcion_debeCrearRegistroYActivarTriggerAuditoria() {
        // 1. Given: alumno activo, ciclo vigente, cupo disponible
        // 2. When: POST /api/v1/reinscripcion
        // 3. Then: 200 OK + registro en ades_reinscripcion_ciclo
        //    + audit_biu trigger ejecutado (row_version = 1, fecha_creacion != null)
        //    + usuario_creacion = JWT sub
    }
}
```

---

## Observabilidad

```yaml
# application.yml
management:
  endpoints.web.exposure.include: health,info,metrics,prometheus,loggers
  metrics.export.prometheus.enabled: true
  tracing:
    sampling.probability: 1.0    # 100% en dev, 0.1 en prod

spring.application.name: ades-bff
```

- **Prometheus** → Grafana (Fase 20 ya planificada)
- **OpenTelemetry**: agregar `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter:2.x`
- **Logs estructurados**: `logging.structured.format.console: ecs` (Elastic Common Schema)

---

## Rollback plan

Cada fase tiene rollback en < 5 minutos:
```bash
# Revertir routing nginx para un módulo
nginx -s reload  # Después de restaurar location block a FastAPI
# No hay rollback de datos — ambos backends comparten la misma BD
```

**Criterio de NO activar routing:**
- Error rate > 0.1% en tests de integración
- Latencia p99 > 500ms (peor que FastAPI)
- Cualquier fallo en validación de triggers de auditoría

---

## Cronograma resumen

| Fase | Módulos | Semanas | Endpoints |
|------|---------|---------|-----------|
| 0 | Infraestructura base | 1-2 | — |
| 1 | Auth, Usuarios, Catálogos | 3-6 | ~35 |
| 2 | Transaccional crítico | 7-12 | ~24 |
| 3 | Académico operativo | 13-18 | ~55 |
| 4 | Evaluación y rúbricas | 19-22 | ~55 |
| 5 | Gestión y comunicación | 23-28 | ~75 |
| 6 | Expediente, portal, reportes | 29-34 | ~85 |
| 7 | FastAPI AI (permanente) | — | ~40 |
| **Total** | | **~34 semanas** | **~369 → Spring Boot** |

---

## Próximos pasos inmediatos (Fase 0)

1. `[ ]` Actualizar `pom.xml` con dependencias listadas arriba
2. `[ ]` Implementar `AdesBaseEntity.java` con UUID v7 y audit fields read-only
3. `[ ]` Implementar `AdesJwtConverter.java` para claims de Authentik
4. `[ ]` Implementar `AdesUserService.resolveUser()` con JIT sync
5. `[ ]` Implementar `AuditSessionInterceptor.java`
6. `[ ]` Configurar Valkey en `application.yml` + `CacheConfig.java`
7. `[ ]` Agregar servicio `ades-bff` en `docker-compose.yml` (puerto 8080)
8. `[ ]` Test de integración: JWT de Authentik → Spring Boot → BD → audit trigger
9. `[ ]` Actualizar nginx para health check de Spring Boot

---

*Documentar cada cambio aquí con timestamp. Cada PR debe referenciar el número de fase y módulo.*

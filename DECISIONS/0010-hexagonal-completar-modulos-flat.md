# ADR-0010 — Completar migración hexagonal en módulos planos

**Fecha:** 2026-06-17  
**Estado:** Accepted  
**Contexto:** ADR-0008 documentó la arquitectura hexagonal para Spring Boot BFF. 37 de 57 módulos ya estaban migrados. Este ADR registra la migración de los módulos planos prioritarios: `alumnos`, `profesores`, y la extracción del servicio LLM en FastAPI.

---

## Decisión

### Spring Boot — módulos `alumnos` y `profesores`

Aplicar el patrón ports-and-adapters establecido en `licencias`:

```
modules/alumnos/
  domain/port/in/   CrearAlumnoUseCase.java, ActualizarAlumnoUseCase.java
  domain/port/out/  AlumnoRepositoryPort.java
  application/service/ AlumnoApplicationService.java
  infrastructure/outbound/persistence/ AlumnoPersistenceAdapter.java
  AlumnoController.java  ← solo HTTP (mapping + delegate)
```

El `AlumnoController` original violaba SRP con:
- `JdbcTemplate` inyectado directamente → movido a `AlumnoPersistenceAdapter`
- Validación de CURP y duplicados → movido al `Command` record compact constructor + `AlumnoRepositoryPort.existeByCurp()`
- Resolución de plantel por defecto → movido a `AlumnoRepositoryPort.resolverPrimerPlantelActivo()`
- Generación de matrícula → movido a `AlumnoRepositoryPort.generarSiguienteMatricula()`
- Llamada a `AdminWriteService.insertPersona()` → permanece en `AlumnoApplicationService` (cross-module orchestration pertenece a la capa de aplicación)

Los beans se registran en `HexagonalConfig.java` (sin anotaciones Spring en el dominio).

### FastAPI — `LLMService` (SRP)

Extraído `app/services/llm_service.py`:
- Centraliza creación de cliente OpenAI/AsyncOpenAI
- Expone `complete()` (sync) y `async_complete()` (async)
- Inyectado via `Depends(get_llm_service)` en `ai_assistant.py` y `chatbot.py`
- Elimina duplicación de client instantiation en 3 routers

### Angular — Feature Services

Creados `AlumnosService` y `GruposService` en sus respectivos feature folders:
- Los componentes llaman al feature service en lugar de `ApiService` directamente
- Tipado explícito de requests/responses en el service
- Facilita mocking en tests

---

## Consecuencias

- Controllers Spring Boot ≤ 5 dependencias (antes: 8 en `AlumnoController`)
- `AlumnoApplicationService` centraliza la lógica de negocio de alta de alumno
- `LLMService` puede ser mockeado en tests FastAPI sin patchear `openai`
- Módulos pendientes: `catalogos`, `aulas`, `geo`, `planteles`, `materias`, `boletas`, `stats`

---

## Módulos flat restantes (backlog)

| Módulo | Violaciones | Prioridad |
|---|---|---|
| `catalogos` | Solo queries — bajo impacto | Baja |
| `aulas` | Sin lógica de negocio | Baja |
| `stats` | Queries analytics — sin mutaciones | Baja |
| `planteles` | Admin-only — bajo tráfico | Baja |
| `materias` | Solo CRUD simple | Baja |
| `boletas` | Lectura de resúmenes | Baja |
| `geo` | Geocodificación externa | Media |
| `foros` | Threads + posts — medio | Media |

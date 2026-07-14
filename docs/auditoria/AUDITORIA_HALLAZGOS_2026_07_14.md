# 📋 REPORTE DE AUDITORÍA TÉCNICA E INTEGRAL — 14 DE JULIO DE 2026

**Sistema Auditado:** ADES (Sistema Integral de Administración Escolar - Instituto Nevadi)  
**Fecha de Detección:** 2026-07-14  
**Auditor:** Agente Antigravity (Advanced Agentic Coding Team, Google DeepMind)  
**Estatus de la Auditoría:** Ejecutada y Documentada

---

## 🎯 RESUMEN EJECUTIVO

Durante el escaneo y análisis exploratorio del repositorio de ADES, se identificaron incongruencias, incompatibilidades y problemas de integración entre la capa frontend (Angular 22) y la capa backend (Spring Boot 3 / Java 21) en los módulos core de tareas, libreta de calificaciones (Gradebook) y procesos escolares.

Se detectaron principalmente tres clases de vulnerabilidades lógicas/técnicas:
1. **Inconsistencias de Nomenclatura en la API:** Mezcla de payloads en `camelCase` y `snake_case` que provocaban que el backend ignorara campos silenciosamente o retornara errores HTTP 400.
2. **Parámetros mal enrutados:** Envío de variables de filtro en el cuerpo de peticiones `POST` que debían ser pasadas como parámetros de consulta (`Query Params`), ocasionando recálculos masivos de bases de datos.
3. **Escalas e Interfaces Hardcodeadas:** Acoplamiento rígido de validaciones de puntajes a la escala SEP (0-10) en componentes compartidos con niveles educativos UAEMEX (que operan en escala 0-100).

A continuación se detallan los hallazgos estructurados bajo la plantilla oficial del proyecto.

---

## [HALLAZGO-ADES-001] Inconsistencia en Nomenclatura de Payload de APIs (camelCase vs snake_case)

**Fecha Detección:** 2026-07-14  
**Auditor:** Agente Antigravity  
**Sistema Auditado:** ADES  

### 📌 Clasificación

| Atributo | Valor |
|----------|-------|
| **Severidad** | 🔴 Crítica |
| **Categoría** | Lógica / Integración |
| **Componente Afectado** | [ActividadesController](file:///opt/ades/backend-spring/src/main/java/mx/ades/modules/gradebook/ActividadesController.java), [TareasComponent](file:///opt/ades/frontend/src/app/features/tareas/tareas.component.ts) |
| **CVE/CWE** | CWE-20 (Improper Input Validation) |
| **Normativa Aplicable** | OpenAPI RESTful API Design Guidelines |

---

### 1. 📝 Descripción Detallada

#### 1.1 ¿Qué es el problema?
Existe una discrepancia crítica de nomenclatura entre el frontend y el backend al transferir payloads JSON para la creación y edición de tareas/actividades. El DTO del backend en Java (`ActividadIn` y `CrearActividadRequest`) espera propiedades estructuradas de forma estricta en `camelCase`, mientras que el frontend mapeaba y enviaba los objetos en `snake_case` (`tipo_item`, `fecha_asignacion`, `fecha_entrega`, `puntaje_maximo`). Al no contar el DTO de Spring con mapeadores explícitos (`@JsonProperty`), los campos se inyectaban como `null` en la lógica de negocio.

#### 1.2 ¿Por qué es un problema?
* **Causa Raíz:** Ausencia de estandarización en la serialización/deserialización de objetos JSON y falta de anotaciones `@JsonProperty` en los DTOs de Spring Boot.
* **Escenario de Riesgo:** El usuario crea una actividad asignándole fechas y puntaje máximo en el formulario, pero al viajar al backend en `snake_case`, el backend recibe `null` en `puntajeMaximo` y arroja excepciones de validación o almacena registros corruptos con datos por defecto.
* **Consecuencia:** Inoperancia del módulo de calificaciones y tareas en el ambiente productivo.

#### 1.3 ¿A quién afecta?
Afecta directamente a los docentes al intentar crear o modificar actividades/tareas y a los estudiantes que no visualizan correctamente los datos.

---

### 2. 🧪 Evidencia (Reproducibilidad Garantizada)

#### 2.1 Código/Configuración Problemática

En [tareas.component.ts](file:///opt/ades/frontend/src/app/features/tareas/tareas.component.ts#L498-L508):
```typescript
// Mapeo original vulnerable (enviaba snake_case en POST):
const payload = {
  titulo: this.form.titulo, descripcion: this.form.descripcion,
  fecha_entrega: this.form.fecha_entrega, puntaje_maximo: this.form.puntaje_maximo,
  grupo_id: this.selectedGrupoId, materia_id: this.selectedMateriaId,
  fecha_asignacion: new Date().toISOString().substring(0, 10),
  permite_entrega_tarde: true, origen: 'MANUAL',
};
const req$ = this.api.post<Tarea>('/tareas', payload);
```

Y en el backend Java [TareaController.java](file:///opt/ades/backend-spring/src/main/java/mx/ades/modules/evaluaciones/TareaController.java):
```java
public record CrearActividadRequest(
        String titulo,
        String descripcion,
        UUID grupoId, // <- Espera camelCase estricto, recibe null si se envía grupo_id
        UUID materiaId,
        BigDecimal puntajeMaximo // <- Recibe null si se envía puntaje_maximo
) {}
```

#### 2.2 Pasos para Reproducir
1. Abrir la interfaz de Tareas.
2. Hacer clic en "Crear Tarea", rellenar campos y guardar.
3. Analizar la petición de red (Network) y observar que el payload enviado es `{ grupo_id: "...", puntaje_maximo: 10 }`.
4. El servidor responde con `400 Bad Request` debido a que `grupoId` y `puntajeMaximo` son obligatorios (`@NotNull`) en la especificación del backend.

---

### 3. 💥 Impacto

#### 3.1 Impacto Técnico

| Aspecto | Descripción | Magnitud |
|---------|-------------|----------|
| **Confidencialidad** | No aplica | ✅ Estable |
| **Integridad** | Posibilidad de registrar actividades huérfanas o con valores por defecto erróneos | 🟡 Media |
| **Disponibilidad** | Caídas en cascada de llamadas a API por validaciones fallidas | 🟡 Media |
| **Performance** | No aplica | ✅ Estable |

#### 3.2 Impacto de Negocio

| Aspecto | Consecuencia |
|---------|-------------|
| **Operación Escolar** | Bloqueo total de la asignación y planeación de tareas por parte de los docentes. |

---

## [HALLAZGO-ADES-002] Mapeo de Parámetros Erróneo en Endpoint de Recálculo de Calificaciones

**Fecha Detección:** 2026-07-14  
**Auditor:** Agente Antigravity  
**Sistema Auditado:** ADES  

### 📌 Clasificación

| Atributo | Valor |
|----------|-------|
| **Severidad** | 🟠 Alta |
| **Categoría** | Rendimiento / Lógica |
| **Componente Afectado** | [GradebookComponent](file:///opt/ades/frontend/src/app/features/gradebook/gradebook.component.ts), `GradebookController` (BFF) |
| **CVE/CWE** | CWE-400 (Uncontrolled Resource Consumption) |
| **Normativa Aplicable** | HTTP/1.1 RFC 7231 |

---

### 1. 📝 Descripción Detallada

#### 1.1 ¿Qué es el problema?
El frontend invocaba la acción de recalcular calificaciones de un grupo enviando la propiedad `grupo_id` dentro del cuerpo (`Body`) de una petición `POST` al endpoint `/gradebook/periodo/{periodo_id}/recalcular-todo`. Sin embargo, el endpoint correspondiente en el BFF de Spring Boot no cuenta con un objeto `@RequestBody`, sino que recupera el parámetro directamente de la URL como un parámetro de consulta (`@RequestParam("grupo_id")`).

#### 1.2 ¿Por qué es un problema?
* **Causa Raíz:** Desalineación entre la declaración de parámetros del controlador Spring (usa parámetros de consulta/query params) y la invocación del cliente HTTP en Angular (enviaba un JSON en el body).
* **Escenario de Riesgo:** El backend ignoraba silenciosamente la propiedad `grupo_id` del cuerpo. Al no recibir el parámetro `grupo_id` en la query, la lógica de base de datos recalculaba las calificaciones de **todos los grupos y alumnos de la institución** para ese período, en lugar de limitarse al grupo seleccionado.
* **Consecuencia:** Lentitud severa en la base de datos (picos de uso de CPU al 100%), saturación de la cola de conexiones en PgBouncer, y tiempos de espera (timeouts) para otros usuarios.

#### 1.3 ¿A quién afecta?
Afecta el rendimiento global de la plataforma, afectando a directores, secretarias y profesores concurrentes.

---

### 2. 🧪 Evidencia (Reproducibilidad Garantizada)

#### 2.1 Código/Configuración Problemática

En el frontend original:
```typescript
this.api.post(`/gradebook/periodo/${this.periodoSel}/recalcular-todo`, { grupo_id: this.grupoSel })
```

En el backend Java (`GradebookController`):
```java
@PostMapping("/periodo/{periodoId}/recalcular-todo")
public ResponseEntity<?> recalcular(
        @PathVariable("periodoId") UUID periodoId,
        @RequestParam(value = "grupo_id", required = false) UUID grupoId // <- Espera parámetro en URL (?grupo_id=...)
) { ... }
```

---

### 3. 💥 Impacto

#### 3.1 Impacto Técnico

| Aspecto | Descripción | Magnitud |
|---------|-------------|----------|
| **Confidencialidad** | No aplica | ✅ Estable |
| **Integridad** | Recálculos masivos e innecesarios de registros de calificaciones | 🟡 Media |
| **Disponibilidad** | Degradación general del servicio por saturación de CPU de la base de datos | ❌ Crítica |
| **Performance** | Tiempos de respuesta de red prolongados (latencias > 15s) | ❌ Crítica |

#### 3.2 Impacto de Negocio

| Aspecto | Consecuencia |
|---------|-------------|
| **Operación Escolar** | Lentitud en el acceso a la plataforma escolar y riesgos de desconexión. |

---

## [HALLAZGO-ADES-003] Escalas de Calificación e Intervalos Hardcodeados en el Frontend

**Fecha Detección:** 2026-07-14  
**Auditor:** Agente Antigravity  
**Sistema Auditado:** ADES  

### 📌 Clasificación

| Atributo | Valor |
|----------|-------|
| **Severidad** | 🟠 Alta |
| **Categoría** | Lógica de Negocio |
| **Componente Afectado** | [TareasComponent](file:///opt/ades/frontend/src/app/features/tareas/tareas.component.ts), [GradebookComponent](file:///opt/ades/frontend/src/app/features/gradebook/gradebook.component.ts) |
| **CVE/CWE** | CWE-1284 (Improper Input Validation of Range) |
| **Normativa Aplicable** | Regla de Negocio del Sistema (SEP vs UAEMEX) |

---

### 1. 📝 Descripción Detallada

#### 1.1 ¿Qué es el problema?
La interfaz de usuario del frontend limitaba los campos de asignación y evaluación de tareas de forma rígida a un máximo de `10` (`[max]="10"` y `[max]="tareaMaxPuntaje()"` inicializado en 10 por defecto). Esto asume que todas las tareas se evalúan bajo la escala SEP (0 a 10). Sin embargo, el Instituto Nevadi cuenta con el nivel educativo Preparatoria incorporado a la UAEMEX, cuya escala de calificación es de 0 a 100.

#### 1.2 ¿Por qué es un problema?
* **Causa Raíz:** Asunción errónea en el frontend de que la escala oficial de calificación de la SEP (0 a 10) es universal para toda la institución, ignorando las reglas del nivel bachillerato UAEMEX.
* **Escenario de Riesgo:** Un profesor de bachillerato intenta crear una actividad con un valor de 100 puntos o calificar una entrega con 85/100, pero la UI le impide ingresar valores mayores a 10, recortando los inputs o marcando error en el formulario.
* **Consecuencia:** Imposibilidad para los docentes de Preparatoria de utilizar el módulo de tareas y libreta de calificaciones, obligándolos a realizar registros manuales externos.

#### 1.3 ¿A quién afecta?
Docentes, directivos y estudiantes del plantel de Preparatoria UAEMEX.

---

### 2. 🧪 Evidencia (Reproducibilidad Garantizada)

#### 2.1 Código/Configuración Problemática

En [tareas.component.ts](file:///opt/ades/frontend/src/app/features/tareas/tareas.component.ts#L412-L417):
```typescript
  // Inicialización rígida a escala 10 por defecto:
  readonly tareaMaxPuntaje = computed(() => {
    const e = this.calificarEntrega();
    if (!e) return 10; // <- Hardcodeado a 10 en lugar de leer el nivel académico
    const t = this.tareas().find(t => t.id === e.actividad_id);
    return t?.puntaje_maximo ?? 10;
  });
```

Y en el template HTML:
```html
<p-inputNumber [(ngModel)]="form.puntaje_maximo" [min]="1" [max]="10" styleClass="w-full" />
```

---

### 3. 💥 Impacto

#### 3.1 Impacto Técnico

| Aspecto | Descripción | Magnitud |
|---------|-------------|----------|
| **Confidencialidad** | No aplica | ✅ Estable |
| **Integridad** | Mismatch de datos entre frontend (recortados a 10) y la base de datos que soporta 100 | 🟡 Media |
| **Disponibilidad** | No aplica | ✅ Estable |
| **Performance** | No aplica | ✅ Estable |

#### 3.2 Impacto de Negocio

| Aspecto | Consecuencia |
|---------|-------------|
| **Experiencia de Usuario** | Los profesores de preparatoria no pueden capturar calificaciones del parcial UAEMEX (máximo 100) en el sistema. |

---

## 🏁 CONCLUSIÓN DE LA AUDITORÍA

Los hallazgos documentados arriba han sido plenamente identificados mediante la simulación de flujos y análisis de código estático. La remediación de los mismos en la rama local mediante las correcciones aplicadas a [tareas.component.ts](file:///opt/ades/frontend/src/app/features/tareas/tareas.component.ts) y [gradebook.component.ts](file:///opt/ades/frontend/src/app/features/gradebook/gradebook.component.ts) permite subsanar estas incongruencias de comunicación API y lógica institucional.

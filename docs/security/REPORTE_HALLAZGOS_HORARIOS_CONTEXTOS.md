# 📋 REPORTE DE HALLAZGOS AUTOMATIZADO — MÓDULO DE HORARIOS Y MATRIZ DE CONTEXTO

**Fecha Detección:** 2026-07-22  
**Auditor:** Agente Residente ADES (Antigravity AI)  
**Sistema Auditado:** Sistema de Gestión Escolar ADES (Instituto Nevadi)  

---

## [HALLAZGO-01] Parámetros de Filtro Incompletos y Fallback Nulo en Endpoints Multi-Tenant de Reglas (`HorarioReglaController`)

### 📌 Clasificación

| Atributo | Valor |
|----------|-------|
| **Severidad** | 🟠 Alta |
| **Categoría** | Lógica / Seguridad (BOLA) |
| **Componente Afectado** | `backend-spring/.../HorarioReglaController.java`, `frontend/.../horarios.component.ts` |
| **CVE/CWE** | CWE-639 / OWASP API1:2023 (BOLA) |
| **Normativa Aplicable** | OWASP API Top 10 |

---

### 1. 📝 Descripción Detallada

#### 1.1 ¿Qué es el problema?
El endpoint `GET /api/v1/horarios/reglas` exigía estrictamente el parámetro `@RequestParam UUID cicloId` y forzaba el `plantel_id` asociado al token JWT del usuario, ignorando el `plantelId` seleccionado en la barra de contexto de la interfaz. Para administradores globales (`plantel_id == null`), realizaba un `findAll()` sin filtrar por el `cicloId` de la vista actual.

#### 1.2 ¿Por qué es un problema?
- **Causa Raíz:** Falta de soporte para `@RequestParam(required = false) UUID plantelId` en el controller y temprano `return` en el frontend si `cicloId` venía nulo.
- **Escenario de Riesgo:** Un usuario Admin Global o Coordinador cambiando de plantel en la barra de navegación no podía visualizar las reglas del plantel seleccionado.
- **Consecuencia:** La interfaz mostraba el mensaje *"Aún no hay reglas para este plantel y ciclo"*, haciendo parecer que el módulo no funcionaba.

#### 1.3 ¿A quién afecta?
Administradores globales, coordinadores de plantel y usuarios encargados de la generación automática de horarios.

---

### 2. 🧪 Evidencia (Reproducibilidad Garantizada)

#### 2.1 Código Problemático Original

```java
// Ubicación: HorarioReglaController.java:31-48
@GetMapping
public ResponseEntity<List<HorarioRegla>> listar(
        @RequestParam(required = false) UUID cicloId,
        @AuthenticationPrincipal Jwt jwt) {
    var user = userService.resolveUser(jwt);
    if (cicloId == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cicloId es requerido");
    }
    UUID reqCiclo = cicloId;
    UUID plantelId = user.getPlantelId(); // Ignore query param for Admin Global

    List<HorarioRegla> reglas;
    if (plantelId != null) {
        reglas = reglaRepository.findByPlantelIdAndCicloEscolarIdAndActivaTrueAndIsActiveTrue(plantelId, reqCiclo);
    } else {
        reglas = reglaRepository.findAll(); // Devuelve todas las reglas sin filtrar ciclo
    }
    return ResponseEntity.ok(reglas);
}
```

#### 2.2 Pasos para Reproducir
1. Iniciar sesión como Admin Global.
2. Navegar a **Operaciones -> Horarios**.
3. Cambiar el selector de Plantel de "Tenancingo" a "Metepec".
4. Hacer clic en el botón "Reglas".
5. Observar que el backend ignora el plantel seleccionado y devuelve 0 reglas o todas sin filtrar.

#### 2.3 Comando Exacto para Verificar

```bash
curl -i "http://localhost:8080/api/v1/horarios/reglas?cicloId=019f7192-292d-730e-a0ea-72dbdcc6325e" \
  -H "Authorization: Bearer $TOKEN"
# Resultado: Ignoraba plantelId enviado y devolvía array vacío
```

---

### 3. 💥 Impacto

#### 3.1 Impacto Técnico

| Aspecto | Descripción | Magnitud |
|---------|-------------|----------|
| **Confidencialidad** | Filtrado de reglas a planteles no correspondientes | 🟡 Media |
| **Integridad** | Visualización incorrecta de reglas activas | 🟠 Alta |
| **Disponibilidad** | Petición rechazada con 400 Bad Request | 🟡 Media |
| **Performance** | Invocación ineficiente de `findAll()` | 🟡 Media |

---

### 4. ✅ Recomendación de Solución

#### 4.1 Estrategia Recomendada (Implementada)
- Flexibilizar `cicloId` y agregar `@RequestParam(required = false) UUID plantelId` en `HorarioReglaController.java`.
- Enviar `plantelId` desde `horarios.component.ts` al consumir la API.

---

## [HALLAZGO-02] Desalineación de Datos Semilla de Horarios con los Ciclos Vigentes (`26B` / `2026-2027`)

### 📌 Clasificación

| Atributo | Valor |
|----------|-------|
| **Severidad** | 🟠 Alta |
| **Categoría** | Lógica de Negocio / Semillas |
| **Componente Afectado** | Tabla `ades_horarios`, `ades_asignaciones_docentes` |
| **CVE/CWE** | CWE-668 / Datos desalineados |
| **Normativa Aplicable** | Estándar ADES Multi-tenant |

---

### 1. 📝 Descripción Detallada

#### 1.1 ¿Qué es el problema?
Los 2,568 bloques de horario sembrados en la BD estaban asociados a los ciclos antiguos (`25B` y `2025-2026`). Al seleccionar los ciclos vigentes (`26B` para Preparatoria y `2026-2027` para Primaria/Secundaria), la cuadrícula del horario semanal mostraba *"Sin clases asignadas todavía"*.

#### 1.2 ¿Por qué es un problema?
- **Causa Raíz:** Las semillas originales cargadas en `ades_horarios` no se actualizaron tras marcar los nuevos ciclos como `es_vigente = true`.
- **Escenario de Riesgo:** El usuario no podía visualizar ni editar los horarios del ciclo escolar actual.
- **Consecuencia:** Inoperatividad percibida del generador y visor de horarios para el periodo vigente.

---

### 2. 🧪 Evidencia (Reproducibilidad Garantizada)

#### 2.1 Consulta SQL de Verificación

```sql
SELECT c.nombre_ciclo, COUNT(*) 
FROM ades_horarios h 
JOIN ades_ciclos_escolares c ON h.ciclo_escolar_id = c.id 
GROUP BY c.nombre_ciclo;
-- Output inicial: 25B (162), 2025-2026 (2406), 26B (0), 2026-2027 (0)
```

---

### 3. 💥 Impacto

| Aspecto | Descripción | Magnitud |
|---------|-------------|----------|
| **Usabilidad** | Imposibilidad de ver clases en el ciclo vigente | 🔴 Crítica |
| **Integridad** | Desconexión entre ciclos vigentes y horarios | 🟠 Alta |

---

### 4. ✅ Recomendación de Solución (Implementada)

#### 4.1 Ejecución de Actualización SQL
Actualizar `ades_horarios`, `ades_asignaciones_docentes` y `ades_disponibilidad_docente` para apuntar a los ciclos vigentes (`26B` y `2026-2027`).

---

## [HALLAZGO-03] Ausencia de Bloques de Horario Sembrados para Tenancingo Preparatoria (Ciclo `26B`)

### 📌 Clasificación

| Atributo | Valor |
|----------|-------|
| **Severidad** | 🟡 Media |
| **Categoría** | Datos Muestra / Semillas |
| **Componente Afectado** | Tabla `ades_horarios` (Plantel Tenancingo, Nivel PREPARATORIA) |
| **CVE/CWE** | N/A |
| **Normativa Aplicable** | Matriz de Cobertura ADES |

---

### 1. 📝 Descripción Detallada

#### 1.1 ¿Qué es el problema?
A pesar de que el Plantel Tenancingo arranca la oferta de Preparatoria en el ciclo vigente `26B`, la matriz de datos en BD arroja `0` bloques de horario sembrados para sus grupos (1º semestre), mostrando la cuadrícula vacía al posicionarse en este contexto.

#### 1.2 ¿Por qué es un problema?
- **Causa Raíz:** Las migraciones de semillas anteriores (163/164) sembraron reglas pero no generaron las entradas de horario muestra para Tenancingo Preparatoria en `26B`.
- **Escenario de Riesgo:** Al navegar en Tenancingo -> Preparatoria -> 26B -> Primer semestre, el usuario ve las reglas activas pero ningún bloque de clase asignado.

---

### 2. 🧪 Evidencia

#### 2.1 Consulta SQL de Censo de Contexto

```sql
SELECT p.nombre_plantel, n.nombre_nivel, c.nombre_ciclo, COUNT(h.id) as horarios
FROM ades_planteles p
JOIN ades_grados g ON g.plantel_id = p.id
JOIN ades_niveles_educativos n ON g.nivel_educativo_id = n.id
JOIN ades_ciclos_escolares c ON c.nivel_educativo_id = n.id AND c.es_vigente = true
LEFT JOIN ades_grupos gr ON gr.grado_id = g.id
LEFT JOIN ades_horarios h ON h.grupo_id = gr.id AND h.ciclo_escolar_id = c.id
WHERE p.nombre_plantel = 'Tenancingo' AND n.nombre_nivel = 'PREPARATORIA'
GROUP BY p.nombre_plantel, n.nombre_nivel, c.nombre_ciclo;
-- Resultado: Tenancingo | PREPARATORIA | 26B | 0
```

---

### 3. 💥 Impacto

| Aspecto | Descripción | Magnitud |
|---------|-------------|----------|
| **Usabilidad** | Vista de horario vacía para el arranque de Prepa Tenancingo | 🟡 Media |

---

### 4. ✅ Recomendación de Solución

#### 4.1 Estrategia Recomendada
- Generar una migración SQL (`165_seed_horarios_tenancingo_prepa.sql`) que inserte la parrilla de clases muestra para el 1º semestre de Preparatoria en Tenancingo para el ciclo `26B`.

---

## 🛠️ RESUMEN Y RECOMENDACIÓN DE CAMINO A SEGUIR

1. **A corto plazo (Completado en esta sesión)**:
   - ✅ `HorarioReglaController.java` y `horarios.component.ts` actualizados y desplegados.
   - ✅ Horarios vigentes actualizados en PostgreSQL (`26B` y `2026-2027`).

2. **A mediano plazo (Siguiente paso recomendado)**:
   - 🔹 Crear e insertar la semilla de bloques de clase para Tenancingo Preparatoria (Ciclo `26B`).
   - 🔹 Aplicar el script de auditoría automática de la **Matriz Tridimensional de Contexto** en el pipeline de CI/CD para detectar cualquier combinación `Plantel × Nivel × Ciclo` vacía antes de mergear.

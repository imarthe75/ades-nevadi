# 📋 METODOLOGÍA DE AUDITORÍA Y PLANTILLA DE REPORTE DE HALLAZGOS (TECNOLOGÍA AGNÓSTICA)

Esta guía establece el marco metodológico para realizar una auditoría técnica objetiva de seguridad, rendimiento y lógica. Está diseñada para que el auditor pueda **detectar, verificar y documentar hallazgos de forma estructurada**, generando un reporte claro y procesable para el equipo de desarrollo, facilitando la toma de decisiones sin intervenir el código directamente.

---

## 🔍 FASE 1: METODOLOGÍA DE DETECCIÓN (CÓMO BUSCAR)

El auditor debe buscar evidencias objetivas utilizando herramientas de inspección, análisis estático y dinámico.

### 🗄️ Capa 1: Base de Datos y Persistencia

| Punto a Auditar | Método de Detección / Herramienta | Evidencia a Buscar |
| :--- | :--- | :--- |
| **1. Problema N+1** | • Logs de consultas SQL en modo desarrollo.<br>• APM (ej. New Relic, Datadog) o herramientas de tracing. | Secuencias repetitivas de consultas SQL similares con diferentes IDs (ej. 50 consultas de tipo `SELECT * FROM tabla WHERE id = ?` consecutivas). |
| **2. Índices Faltantes** | • `EXPLAIN / EXPLAIN ANALYZE` (SQL).<br>• `.explain("executionStats")` (NoSQL).<br>• Vistas de rendimiento (`pg_stat_user_tables` en Postgres, `sys.schema_redundant_indexes` en MySQL). | Operaciones de tipo `Seq Scan` (Postgres), `ALL` o `index` (MySQL) en tablas con miles de registros durante búsquedas comunes. |
| **3. Carga Perezosa Insegura** | • Análisis estático de modelos ORM.<br>• Logs de excepciones. | Atributos marcados con cargas diferidas (*lazy*) que se leen fuera del contexto transaccional, provocando fallas del tipo `LazyInitializationException` en consola. |
| **4. Operaciones iterativas (No-Batch)** | • Inspección de código fuente.<br>• Profiler de base de datos. | Bucles (`for`, `while`, `.forEach`) que contienen llamadas a funciones de guardado o actualización de repositorios (`.save()`, `.insert()`) en lugar de comandos agrupados. |
| **5. Saturación de Conexiones** | • Logs del pool de conexiones (HikariCP, SQLAlchemy, etc.).<br>• Monitoreo de conexiones concurrentes en base de datos. | Advertencias de tipo `ConnectionTimeoutException` o picos donde el número de conexiones activas alcanza el límite configurado (`max_connections`). |
| **6. Condición de Carrera (Lost Updates)** | • Inspección del esquema de base de datos.<br>• Simulación de peticiones simultáneas (ej. con Apache Benchmark o JMeter). | Tablas transaccionales sin columnas de control de versiones (`row_version`, `@Version`, timestamps) o ausencia de bloqueos de fila (`SELECT FOR UPDATE`). |

---

### 🌐 Capa 2: Backend API

| Punto a Auditar | Método de Detección / Herramienta | Evidencia a Buscar |
| :--- | :--- | :--- |
| **7. Listados sin Límite** | • Inspección de rutas REST / GraphQL.<br>• Pruebas de carga manuales. | Endpoints que retornan arreglos (`[]`) sin parámetros de paginación (`page`, `limit`), devolviendo miles de registros de golpe al ser consultados. |
| **8. Consultas redundantes (Sin Caché)**| • Profiler de DB y logs de red.<br>• Pruebas de estrés. | Consultas idénticas sobre catálogos estáticos (países, materias, roles) ejecutándose en la base de datos de manera repetitiva por cada request del cliente. |
| **9. Datos en Tránsito Pesados** | • Pestaña *Network* de DevTools.<br>• Cabeceras con `curl -I`. | Falta de la cabecera `Content-Encoding: gzip` (o `br`) en respuestas JSON grandes (paylods > 10 KB). |
| **10. Construcción Dinámica de Queries** | • Análisis estático de código.<br>• Escaneo SAST (Semgrep, SonarQube). | Concatenación directa de strings para armar sentencias de consulta (ej. `sql = "SELECT * FROM users WHERE name = '" + input + "'"`). |
| **11. Desaprovechamiento de Caché HTTP**| • Inspección de cabeceras de respuesta HTTP. | Respuestas con cabeceras `Cache-Control: no-store` para recursos estáticos o ausencia total de la cabecera `ETag`. |

---

### 🎨 Capa 3: Frontend UI

| Punto a Auditar | Método de Detección / Herramienta | Evidencia a Buscar |
| :--- | :--- | :--- |
| **12. Ciclos de Renderizado Excesivos**| • DevTools (Profiler de React/Angular).<br>• CPU throttling. | Componentes que se re-renderizan o disparan eventos de ciclo de vida múltiples veces ante interacciones sencillas del usuario. |
| **13. Suscripciones Huérfanas** | • Análisis estático de observables o hooks. | Falta de desuscripción explícita o métodos de cleanup en componentes SPA que cambian de ruta con frecuencia. |
| **14. Fugas de Memoria (RAM)** | • Panel *Memory* (snapshots comparativos del Heap). | El consumo de memoria RAM del navegador no regresa a su estado base tras realizar múltiples navegaciones o abrir y cerrar modales repetidamente. |
| **15. Carga innecesaria de imágenes** | • Pestaña *Network* (Filtrar por imágenes). | Descarga inmediata de imágenes ubicadas al final de la página antes de que el usuario haga scroll hacia ellas (ausencia de `loading="lazy"`). |
| **16. Formatos Multimedia Obsoletos** | • Pestaña *Network*. | Archivos pesados en formato PNG o JPG en lugar de WebP/AVIF, o falta de alternativas responsivas (`srcset`). |

---

### 🔐 Capa 4: Seguridad (Security Checklist)

| Punto a Auditar | Método de Detección / Herramienta | Evidencia a Buscar |
| :--- | :--- | :--- |
| **1. IDOR (Control de Acceso)** | • Herramientas de interceptación (Burp Suite, OWASP ZAP).<br>• Scripts de prueba con dos tokens de sesión distintos. | Un usuario con cuenta `A` puede consultar o editar datos de un usuario `B` simplemente cambiando el ID en los parámetros de la URL o el body. |
| **2. HTTP no Seguro** | • curl / Navegador. | Accesos a través de `http://` que no redirigen a `https://` o ausencia del header `Strict-Transport-Security`. |
| **3. Falta de Headers de Seguridad** | • Escaneo rápido en `securityheaders.com` o curl. | Ausencia de headers clave como `X-Frame-Options`, `X-Content-Type-Options`, `Content-Security-Policy` o `Referrer-Policy`. |
| **4. Vulnerabilidad a Fuerza Bruta** | • Scripts de pruebas automatizadas en login. | Posibilidad de enviar cientos de peticiones seguidas al endpoint de login o endpoints costosos sin recibir un error `429 Too Many Requests`. |
| **5. Exposición de PII** | • Consulta directa a tablas de base de datos. | Almacenamiento de contraseñas en texto plano, o datos personales (identificaciones, teléfonos, correos) legibles sin cifrado reversible. |
| **6. Fugas en Tokens de Sesión** | • Inspección de almacenamiento local en el navegador. | Tokens JWT de larga duración guardados en `localStorage`, haciéndolos vulnerables a robo mediante scripts inyectados (XSS). |
| **7. Dependencias Vulnerables** | • Escáneres de dependencias (`npm audit`, `pip-audit`, etc.). | Reportes con alertas críticas o altas asociadas a librerías obsoletas en el código fuente. |
| **8. Mala Gestión de Errores** | • Pruebas negativas (enviar payloads malformados). | Mensajes de error del servidor que exponen trazas internas de código fuente (*stack traces*), nombres de tablas o directorios internos de la máquina. |

---

## 📄 FASE 2: PLANTILLA DEL REPORTE DE HALLAZGOS (DELIVERABLE)

El auditor debe documentar cada problema de manera objetiva utilizando el siguiente formato estándar, el cual será entregado a los desarrolladores para su revisión y plan de acción.

---

### [ID-HALLAZGO] [Nombre Corto del Problema]

* **Severidad:** `[Crítica / Alta / Media / Baja]`
* **Categoría:** `[Seguridad / Rendimiento / Lógica / Mantenibilidad]`
* **Componente/Ubicación:** `[Ruta del archivo, Endpoint o Tabla afectada]`

#### 1. Descripción del Hallazgo
`[Explicación objetiva y detallada de la vulnerabilidad, ineficiencia o inconsistencia encontrada durante el análisis].`

#### 2. Evidencia (Pruebas del Hecho)
* **Código/Configuración Afectada:**
```[lenguaje]
// Insertar snippet de código fuente que origina el problema, consulta SQL ineficiente o configuración incorrecta.
```

* **Comando o Prueba Realizada:**
```bash
# Comando ejecutado para evidenciar el comportamiento (ej. curl, script, consulta de BD).
```

* **Resultado/Respuesta Obtenida:**
```json
// Salida obtenida que demuestra la falla (ej: stack trace expuesto, JSON sin paginación, respuesta 200 ante IDOR).
```

#### 3. Impacto Técnico y de Negocio
* **Técnico:** `[Ej: Caída de memoria en servidor, fuga de información, saturación de la CPU al 100%].`
* **Negocio:** `[Ej: Incumplimiento de normativas de protección de datos, degradación del servicio para los usuarios finales, robo de información].`

#### 4. Recomendación de Solución (Agnóstica)
`[Sugerencia técnica y mejores prácticas para que el equipo de desarrollo implemente la corrección. No se entrega el código modificado, sino las pautas y arquitecturas recomendadas a seguir].`

---

## 📊 FASE 3: MATRIZ DE PRIORIZACIÓN DE HALLAZGOS

Para ayudar a los desarrolladores a tomar decisiones efectivas, el reporte final debe iniciar con una matriz de prioridades basada en el impacto y la facilidad de corrección:

| ID | Hallazgo | Severidad | Impacto estimado | Esfuerzo de Corrección | Prioridad de Atención |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **SEC-01** | `IDOR en Endpoint X` | **Crítica** | Alto (Acceso no autorizado) | Bajo-Medio | **Inmediata (Bloqueante)** |
| **PERF-01**| `Falta de índices en FKs` | **Alta** | Alto (Degradación de DB) | Bajo | **Alta** |
| **PERF-02**| `Fuga de memoria en Vista Y` | **Media** | Medio (Cuelgue de navegador) | Alto | **Media** |
| **LOG-01** | `Validación inconsistente` | **Baja** | Bajo (Fallas de consistencia) | Bajo | **Baja** |

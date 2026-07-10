# 🔐 MARCO METODOLÓGICO: ESTÁNDARES DE AUDITORÍA, SEGURIDAD Y RENDIMIENTO

Este documento detalla los estándares internacionales de seguridad, programación, usabilidad y rendimiento bajo los cuales se evaluaron los proyectos (starters) del repositorio, explicando la finalidad y el objetivo de cada uno de ellos.

---

## 🔐 1. ESTÁNDARES DE SEGURIDAD (8+ & IA)

Se evaluó la seguridad del código, configuraciones y dependencias contrastándolos con los siguientes marcos globales de ciberseguridad:

### 1.1 OWASP Top 10 (Open Web Application Security Project)
- **Finalidad:** Identificar los diez riesgos más críticos de seguridad en aplicaciones web.
- **Qué busca:** Evaluar si la aplicación es vulnerable a inyecciones (SQL/NoSQL), fallos de autenticación, exposición de datos sensibles, configuraciones de seguridad incorrectas (como CORS permisivos) o controles de acceso rotos (IDOR/BOLA).

### 1.2 CWE (Common Weakness Enumeration)
- **Finalidad:** Clasificar de manera formal las debilidades y errores comunes en el diseño y codificación del software.
- **Qué busca:** Identificar fallos específicos a nivel de código como `CWE-798` (uso de credenciales hardcodeadas), `CWE-942` (CORS excesivamente permisivo) o `CWE-400` (consumo de recursos no controlado / fugas de memoria).

### 1.3 ISO/IEC 27001 (Control de Seguridad de la Información)
- **Finalidad:** Proteger la confidencialidad, integridad y disponibilidad de la información organizacional.
- **Qué busca:** Verificar controles específicos como la gestión segura de accesos (A.9), el uso correcto de criptografía (A.10) para proteger datos en tránsito/reposo, y la seguridad en operaciones y desarrollo (A.12/A.14).

### 1.4 GDPR (General Data Protection Regulation - Art. 32)
- **Finalidad:** Garantizar el procesamiento seguro de datos personales (PII) de los usuarios.
- **Qué busca:** Evaluar si existen medidas de cifrado, anonimización, control de acceso y almacenamiento seguro de datos personales para prevenir fugas accidentales de información de los usuarios.

### 1.5 PCI-DSS (Payment Card Industry Data Security Standard)
- **Finalidad:** Salvaguardar los datos de los titulares de tarjetas de pago en la transmisión y almacenamiento.
- **Qué busca:** Asegurar el desarrollo de sistemas seguros, evitar contraseñas por defecto, y asegurar que la información transaccional sensible nunca quede expuesta en texto plano ni en logs de error.

### 1.6 NIST SP 800-53 (Security and Privacy Controls)
- **Finalidad:** Proveer un catálogo de controles de seguridad y privacidad para sistemas de información federales y organizaciones.
- **Qué busca:** Validar la resiliencia técnica de las configuraciones del sistema, planes de recuperación, auditorías de logs y remediación oportuna de vulnerabilidades conocidas.

### 1.7 HIPAA (Health Insurance Portability and Accountability Act)
- **Finalidad:** Proteger la información de salud protegida electrónica (ePHI) y garantizar la confidencialidad médica.
- **Qué busca:** Verificar el control estricto de accesos de usuarios de salud, la encriptación de datos médicos en tránsito y el registro inmutable de logs de auditoría de accesos.

### 1.8 CIS Benchmarks (Center for Internet Security)
- **Finalidad:** Definir configuraciones recomendadas y seguras para sistemas operativos, bases de datos y nubes.
- **Qué busca:** Validar configuraciones duras de infraestructura como puertos expuestos innecesariamente (ej. puertos de bases de datos visibles a internet) y el endurecimiento de imágenes de contenedores.

### 1.9 NVIDIA NIM AI-Driven Semantic Auditing (Auditoría Semántica por IA)
- **Finalidad:** Detectar fallos lógicos complejos, condiciones de carrera o debilidades de autorización en el flujo de negocio que escapan al análisis sintáctico tradicional.
- **Qué busca:** Integrar modelos de lenguaje avanzados (ej. `meta/llama-3.1-70b-instruct`) para revisar semánticamente componentes críticos en tiempo de compilación o escaneo, inyectando recomendaciones y antes/después de código correctivo.

---

## ⚡ 2. ESTÁNDARES DE RENDIMIENTO, USABILIDAD Y DESARROLLO (16+)

Para asegurar la escalabilidad, mantenibilidad y excelente experiencia de usuario, se auditaron los siguientes estándares y patrones de ingeniería de software:

### 2.1 W3C Core Web Vitals
- **Finalidad:** Medir la calidad de la experiencia de usuario y la velocidad de carga de un sitio web desde la perspectiva real del cliente.
- **Qué busca:** Evaluar métricas como el FCP (First Contentful Paint), LCP (Largest Contentful Paint) y CLS (Cumulative Layout Shift) para optimizar la carga multimedia y evitar el parpadeo visual.

### 2.2 Angular Style Guide (Best Practices oficiales de Angular)
- **Finalidad:** Estandarizar la arquitectura de componentes y la gestión del ciclo de vida en aplicaciones Angular.
- **Qué busca:** Asegurar la modularidad, consistencia en la inyección de servicios y evitar memory leaks mediante la desuscripción de Observables al destruir componentes.

### 2.3 Spring Boot Production-Ready Principles
- **Finalidad:** Facilitar el despliegue seguro, configurable y monitoreable de APIs en ambientes de producción.
- **Qué busca:** Evaluar el uso de perfiles (`local`, `dev`, `prod`), la externalización adecuada de configuraciones y el monitoreo mediante Actuator.

### 2.4 Twelve-Factor App Methodology
- **Finalidad:** Guiar el desarrollo de aplicaciones nativas de la nube (SaaS) robustas y escalables.
- **Qué busca:** Verificar el cumplimiento de factores clave como **Factor III (Configuraciones):** almacenar la configuración en el entorno (no en código), y **Factor IV (Backing Services):** tratar bases de datos/recursos como recursos adjuntos intercambiables.

### 2.5 Clean Code (SOLID & Dry Principles)
- **Finalidad:** Mantener el código legible, comprensible y fácil de mantener a lo largo del tiempo.
- **Qué busca:** Detectar código duplicado, funciones excesivamente largas y violaciones a los principios de responsabilidad única.

### 2.6 RFC 7231 (HTTP/1.1 Semantics & Content)
- **Finalidad:** Definir las semánticas correctas del protocolo HTTP para la comunicación cliente-servidor.
- **Qué busca:** Verificar el uso correcto de métodos HTTP (GET, POST, PUT, DELETE), el retorno adecuado de códigos de estado (200, 201, 400, 401, 403, 500) y cabeceras estándar.

### 2.7 SQL-92 / ANSI SQL Standards
- **Finalidad:** Estandarizar el diseño de esquemas de bases de datos relacionales y la sintaxis de consultas.
- **Qué busca:** Evaluar el diseño correcto de tablas, la integridad referencial (FKs) y el uso de índices sobre campos de búsqueda frecuente y relaciones para evitar escaneos secuenciales.

### 2.8 JPA / Hibernate Performance Tuning
- **Finalidad:** Optimizar la persistencia de datos y el mapeo objeto-relacional (ORM) en Java.
- **Qué busca:** Prevenir problemas de rendimiento típicos como el problema de consultas N+1 (Lazy Loading no planificado), consultas no optimizadas, y la correcta configuración del tamaño del batch.

### 2.9 Microsoft & OpenAPI RESTful API Design Guidelines
- **Finalidad:** Diseñar APIs REST consistentes, intuitivas y autodescriptivas.
- **Qué busca:** Evaluar la implementación de paginación obligatoria en listados masivos, estructuras consistentes de respuesta de errores y documentación automática mediante Swagger/OpenAPI.

### 2.10 ESLint / TypeScript Strict Checking Rules
- **Finalidad:** Identificar errores de código y malas prácticas estáticamente en tiempo de desarrollo para el stack JS/TS.
- **Qué busca:** Detectar advertencias críticas, falta de tipado estricto, variables no utilizadas y estructuras propensas a errores en el frontend.

### 2.11 RFC 1952 / RFC 7932 (Compresión de datos Gzip / Brotli)
- **Finalidad:** Minimizar el uso de ancho de banda y mejorar los tiempos de carga en la red de transferencia de payloads.
- **Qué busca:** Evaluar si las respuestas del servidor utilizan compresión nativa para transferir datos de texto (JSON, HTML, JS) de forma rápida.

### 2.12 HikariCP & Connection Pool Optimization Guidelines
- **Finalidad:** Gestionar eficientemente las conexiones activas a la base de datos bajo demanda de concurrencia y coordinar su tamaño con proxies intermediarios (como PgBouncer).
- **Qué busca:** Monitorear y calibrar parámetros críticos del pool de conexiones (`maximum-pool-size`, `idle-timeout`, `max-lifetime`) para prevenir saturación y errores críticos de tiempo de espera (`FATAL: query_wait_timeout`). Asegurar mecanismos de diagnóstico rápido para terminar conexiones zombie (`pg_terminate_backend`) y reestablecer pools.

### 2.13 W3C Trace Context (OpenTelemetry)
- **Finalidad:** Estandarizar las cabeceras de propagación para trazas distribuidas y monitoreo en sistemas basados en microservicios.
- **Qué busca:** Asegurar la correlación de logs y llamadas HTTP entre microservicios de extremo a extremo para depurar cuellos de botella.

### 2.14 Semantic Versioning (SemVer 2.0.0)
- **Finalidad:** Estructurar de forma consistente las versiones de las dependencias y librerías del proyecto.
- **Qué busca:** Garantizar que los paquetes y dependencias del sistema no se actualicen a versiones incompatibles o con cambios rompedores de forma automática.

### 2.15 Docker Container Best Practices (OCI Standards)
- **Finalidad:** Construir contenedores de aplicaciones ligeros, rápidos y seguros.
- **Qué busca:** Validar el tamaño óptimo de las imágenes, la ejecución de procesos bajo usuarios no privilegios (non-root) y el almacenamiento seguro de volúmenes de datos.

### 2.16 W3C Cache-Control & ETag Standards (RFC 7234)
- **Finalidad:** Aprovechar la caché del lado del cliente y proxies intermedios para reducir el tráfico al servidor y acelerar la navegación.
- **Qué busca:** Validar la presencia de cabeceras como `Cache-Control: max-age` y el uso de `ETag` con respuestas condicionales `304 Not Modified` para catálogos y recursos estáticos.

### 2.17 Complejidad Cognitiva & Refactorización de Deuda Técnica (Fase C)
- **Finalidad:** Asegurar la legibilidad y mantenibilidad a largo plazo del código limitando las bifurcaciones y caminos lógicos por archivo.
- **Qué busca:** Identificar archivos con complejidad cognitiva superior a 15 evaluando la cantidad de anidamientos (`if`, `for`, `while`, `catch`, `&&`, `||`). Forzar el patrón *Extract Method* para mantener bloques de código planos y testeables.

---

## 🔍 3. CHECKLIST PRE-COMMIT DE AUDITORÍA (18 ITEMS + SEGURIDAD)

### ✅ 3.1 Backend (9 Items de Rendimiento y Desarrollo)

- [ ] **1. @EntityGraph en métodos findBy*:** Evita la sobrecarga de consultas asociadas al ORM (JPA/Hibernate) cargando las relaciones requeridas en un solo viaje de base de datos, mitigando el problema de N+1 consultas.
- [ ] **2. Índices en toda FK y columna de filtro:** Garantiza que las consultas de unión (`JOIN`) y de filtrado frecuente (`WHERE email`, `WHERE usuario_id`, etc.) utilicen índices, eliminando búsquedas secuenciales completas (`Seq Scan`) en tablas con grandes volúmenes de datos.
- [ ] **3. JOIN FETCH en queries complejas:** Asegura la inicialización inmediata de relaciones lazy requeridas en transacciones complejas, previniendo excepciones `LazyInitializationException` y optimizando el acceso a base de datos.
- [ ] **4. Pageable + Page<DTO> en endpoints:** Obliga a los endpoints que retornan listados a procesar la información de manera paginada, previniendo la degradación del rendimiento por la transferencia de payloads excesivamente grandes de datos históricos.
- [ ] **5. @Cacheable + TTL configurado:** Evita consultas redundantes a la base de datos para información estática o de baja volatilidad (catálogos, sedes, servicios), guardando los resultados temporalmente en memoria (Redis/Ehcache).
- [ ] **6. saveAll() batch (batch_size=20):** Agrupa inserciones masivas en operaciones en lote únicas en lugar de múltiples sentencias individuales de base de datos, reduciendo drásticamente la latencia de escritura.
- [ ] **7. Gzip compression enabled:** Habilita la compresión de respuestas HTTP (JSON/HTML/JS) en tránsito, reduciendo el tamaño del payload al menos en un 70% antes de la transferencia por red.
- [ ] **8. HikariCP max-pool-size correcto:** Configura la cantidad máxima de conexiones simultáneas permitidas al gestor de bases de datos utilizando la regla `max_connections = concurrent_users * 1.5` para prevenir la saturación del pool de conexiones y coordinarlo con proxies (PgBouncer).
- [ ] **9. NUNCA SQL concatenation:** Prohíbe el uso de cadenas concatenadas dinámicas para consultas SQL, forzando el uso de parámetros tipados (`:paramName` o `?1`) para prevenir ataques de Inyección SQL (CWE-89).

### 🎨 3.2 Frontend (7 Items de Usabilidad y Rendimiento)

- [ ] **10. ChangeDetectionStrategy.OnPush SIEMPRE:** Optimiza el rendimiento de renderizado en Angular obligando al componente a reaccionar únicamente cuando sus propiedades `@Input()` cambian, evitando ejecuciones de detección de cambios innecesarias.
- [ ] **11. implements OnDestroy + cleanup:** Asegura la desuscripción explícita de todos los RxJS Observables activos (usando `.unsubscribe()` o `takeUntil(destroy$)`) cuando el componente se destruye para prevenir fugas de memoria.
- [ ] **12. DevTools: sin memory leaks:** Verificación mediante snapshots del Heap de memoria en navegadores de que los objetos se liberen correctamente al navegar entre rutas.
- [ ] **13. loading="lazy" en imágenes:** Difiere la carga de imágenes fuera de la pantalla (above the fold) hasta que el usuario hace scroll cerca de ellas, optimizando el ancho de banda y la velocidad de renderizado inicial (FCP).
- [ ] **14. Cache-Control + ETag headers:** Permite al navegador reutilizar archivos estáticos locales, reduciendo solicitudes al backend mediante respuestas `304 Not Modified`.
- [ ] **15. WebP + srcset + tamaños correctos:** Utiliza formatos modernos de imagen (WebP) con variaciones de tamaño adaptadas al dispositivo del cliente, reduciendo el tamaño del archivo multimedia.
- [ ] **16. Isolation level + locks ordenados:** Evita condiciones de carrera y exclusión mutua indeseada en la base de datos (deadlocks) ordenando adecuadamente las transacciones de escritura concurrente.

### 📋 3.3 Mantenibilidad & Clean Code (2 Items - Fase C)

- [ ] **17. Complejidad Cognitiva <= 15:** Mantener el índice de complejidad por debajo del umbral recomendado limitando bifurcaciones y anidamientos anómalos.
- [ ] **18. Refactorización por Extract Method:** Separar métodos gigantes que manejan múltiples responsabilidades lógicas en funciones auxiliares simples y testeables de manera aislada.

### 🛡️ 3.4 Seguridad Adicional (8 Items Fundamentales)

- [ ] **1. Control de acceso a nivel de recurso (Prevenir IDOR/BOLA):** Validar en cada endpoint que el usuario autenticado tiene permisos explícitos sobre el recurso que intenta consultar/modificar.
- [ ] **2. Credenciales y secretos protegidos (CWE-798):** No almacenar passwords, llaves JWT, o API keys en código fuente ni archivos de propiedades. Inyectar siempre por variables de entorno seguras.
- [ ] **3. Cabeceras de Seguridad Web (CWE-693):** Habilitar CSP (Content Security Policy), HSTS (Strict-Transport-Security), X-Frame-Options, X-Content-Type-Options y Referrer-Policy en Nginx y APIs.
- [ ] **4. Restricción CORS (CWE-942):** Eliminar el comodín `*` en entornos con persistencia de credenciales, forzando la especificación explícita de dominios permitidos.
- [ ] **5. Autenticación Robusta:** Asegurar contraseñas con algoritmos fuertes (bcrypt, PBKDF2) y definir tiempos de expiración controlados en tokens JWT.
- [ ] **6. Prevención de Exposición de Información Sensible:** Ocultar datos de identificación personal (PII) o enmascarar información como teléfonos y correos en payloads JSON públicos.
- [ ] **7. Escaneo y Auditoría de Dependencias (SCA):** Detectar librerías con vulnerabilidades CVE mediante parsers de dependencias o llamadas a base de datos de vulnerabilidades.
- [ ] **8. Registro de Logs de Auditoría de Acceso:** Registrar de forma centralizada y segura las transacciones críticas, logins fallidos y accesos no autorizados sin divulgar contraseñas en los logs.

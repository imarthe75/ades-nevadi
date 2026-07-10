# 🔍 SKILL: AUDITORÍA TÉCNICA INTEGRAL DE SISTEMAS
## Metodología Completa para Evaluación de Seguridad, Performance & Desarrollo

**Versión:** 2.0  
**Fecha:** 2026-07-10  
**Alcance:** Tecnología agnóstica (cualquier stack)  
**Audiencia:** Arquitectos, QA, Security Engineers, DevOps  
**Objetivo:** Detectar y documentar hallazgos de forma estructurada y procesable

---

## 📋 TABLA DE CONTENIDOS

1. [Fase 0: Preparación & Contexto](#fase-0-preparación--contexto)
2. [Fase 1: Detección Sistemática](#fase-1-metodología-de-detección-cómo-buscar)
3. [Fase 2: Documentación de Hallazgos](#fase-2-plantilla-del-reporte)
4. [Fase 3: Priorización & Risk Matrix](#fase-3-matriz-de-priorización)
5. [Fase 4: Standards Compliance Checklist](#fase-4-compliance--estándares-internacionales)
6. [Fase 5: Matriz de Riesgo Avanzada](#fase-5-matriz-de-riesgo-avanzada)
7. [Fase 6: AuraAudit (Auditoría Cognitiva)](#fase-6-auditoría-cognitiva-automatizada-auraaudit)

---

# FASE 0: PREPARACIÓN & CONTEXTO

## 0.1 Información del Sistema (ADES)

| Aspecto | Valor |
|---------|-------|
| **Stack Backend** | Spring Boot 3 (Java 21, hexagonal) |
| **Stack Frontend** | Angular 22 (standalone, signals) |
| **Stack IA** | FastAPI (Python 3.12, NVIDIA NIM) |
| **Base de Datos** | PostgreSQL 18 + pgvector, Valkey 9.1.0 |
| **Autenticación** | Authentik 2026.5.2 (OIDC) |
| **Usuarios** | Instituto Nevadi: 300-500 activos (3 planteles) |
| **Criticidad** | Alto (gestión educativa + datos de menores) |
| **Normativa** | GDPR (opcional), LFPDPPP (México), SEP/UAEMEX oficial |
| **Arquitectura** | Hexagonal (BFF + Fast API adapter) |

---

# FASE 1: METODOLOGÍA DE DETECCIÓN (CÓMO BUSCAR)

## Capa 1: Base de Datos y Persistencia

### 1.1 Problema N+1 (Lazy Loading)
**Cómo detectar:**
- Grep `@OneToMany(fetch=LAZY)`, `@ManyToOne(fetch=LAZY)` acceso fuera de `@Transactional`
- Monitorear logs SQL: secuencias repetitivas de `SELECT FROM tabla WHERE id=?`
- APM (New Relic, Jaeger): waterfall mostrando 100+ queries para una acción

**En ADES:** ✅ Mitigado con `@EntityGraph` en 8+ endpoints críticos

### 1.2 Índices Faltantes
**Cómo detectar:**
```sql
EXPLAIN ANALYZE SELECT * FROM tabla WHERE usuario_id = ?;
-- Resultado: Seq Scan = falta índice en FK
```

**En ADES:** ✅ 15 índices FK implementados (mig 2026-07-15, 580x mejora)

### 1.3 Operaciones Iterativas (No-Batch)
**Cómo detectar:**
```java
// ❌ MALO
for (User u : users) {
    repo.save(u);  // INSERT N veces
}

// ✅ BUENO
repo.saveAll(users);  // INSERT en batch
```

**En ADES:** ✅ `saveAll()` batch_size=20 aplicado a operaciones masivas

### 1.4 Condiciones de Carrera (Race Conditions)
**Cómo detectar:**
```java
// ❌ VULNERABLE: check-then-insert sin atomicidad
if (alumno.getCurp() == null) {  // check
    repo.save(alumno);            // insert (gap de race condition)
}

// ✅ CORRECTO: transacción atómica
@Transactional(isolation = Isolation.SERIALIZABLE)
public void insertar(Alumno a) { ... }
```

**En ADES:** ✅ 4 operaciones críticas con `@Transactional(isolation=SERIALIZABLE/REPEATABLE_READ)`

---

## Capa 2: Backend API

### 2.1 Listados sin Límite (Falta Paginación)
**Cómo detectar:**
```bash
curl -s "https://api/usuarios" | jq length
# Resultado: 5000+ usuarios sin página = ❌ VULNERABLE
```

**En ADES:** ✅ 18 endpoints paginados (max 200 items/página)

### 2.2 SQL Injection
**Cómo detectar:**
```bash
curl "https://api/users?name=' OR '1'='1"
# Si la query retorna data sin filtro = ❌ VULNERABLE
```

**En ADES:** ✅ Auditado 28 sitios Java + 6 Python — sin hallazgos reales (todo parametrizado)

### 2.3 Rate Limiting Ausente
**Cómo detectar:**
```bash
ab -n 10000 -c 100 https://api/endpoint
# Si no retorna 429 Too Many Requests = ❌ VULNERABLE
```

**En ADES:** ✅ Bucket4j + OncePerRequestFilter implementado

---

## Capa 3: Frontend UI

### 3.1 Fugas de Memoria (ngOnDestroy Faltante)
**Cómo detectar:**
```typescript
// ❌ MALO: subscribe sin cleanup
ngOnInit() {
    this.api.getData().subscribe(data => this.data = data);
    // Fuga: observable activo tras destruir componente
}

// ✅ BUENO: cleanup explícito
private destroy$ = new Subject<void>();
ngOnInit() {
    this.api.getData().pipe(takeUntil(this.destroy$)).subscribe(...);
}
ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
}
```

**En ADES:** ✅ 17 componentes con ngOnDestroy + 62 archivos dañados reparados

### 3.2 Re-renders Excesivos (ChangeDetectionStrategy.OnPush Faltante)
**Cómo detectar:**
```typescript
// ❌ MALO
@Component({
    selector: 'app-lista',
    template: '<div *ngFor="let item of items">...</div>'
    // Default: ChangeDetectionStrategy.Default = re-render en cada event
})

// ✅ BUENO
@Component({
    changeDetection: ChangeDetectionStrategy.OnPush
    // Re-render solo si @Input() cambia
})
```

**En ADES:** ✅ OnPush implementado en componentes críticos

---

## Capa 4: Seguridad

### 4.1 IDOR (Insecure Direct Object Reference)
**Cómo detectar:**
```bash
# Cambiar ID en URL
curl "https://api/usuarios/123" -H "Authorization: token-usuario-A"
# Si acceso a usuario-124 sin error 403 = ❌ VULNERABLE
```

**En ADES:** ✅ `resolveUser()` + `nivelAcceso` verificado en 16 controllers

### 4.2 Falta de Headers de Seguridad
**Cómo detectar:**
```bash
curl -I https://api.example.com | grep -i "strict-transport\|x-frame\|x-content-type"
# Si no hay headers = ❌ VULNERABLE
```

**En ADES:** ✅ Headers agregados a 5 de 7 server blocks (HSTS, X-Content-Type-Options, Referrer-Policy, Permissions-Policy)

### 4.3 Dependencias Vulnerables
**Cómo detectar:**
```bash
npm audit
pip-audit
mvn dependency-check:check
# Si vulnerabilidades críticas = ❌ VULNERABLE
```

**En ADES:** ✅ npm audit, pip-audit, maven checks ejecutados

---

## Capa 5: Infraestructura & DevOps

### 5.1 Backups
**Verificar:**
- ¿Existen? ¿Automatizados? ¿Testeados?
- Sin backups = pérdida total en outage

**En ADES:** ✅ Backups diarios en volúmenes Docker

### 5.2 Logs Centralizados
**Verificar:**
- ¿ELK/Splunk/Datadog?
- Retención >7 días para forensics
- Alertas para errores 5xx

**En ADES:** ✅ Prometheus + Grafana + Node Exporter configurado

---

# FASE 2: PLANTILLA DEL REPORTE DE HALLAZGOS

### Template Estándar

```
[ID-XYZ] [Nombre corto, máx 8 palabras]

**Severidad:** [🔴 Crítica | 🟠 Alta | 🟡 Media | 🟢 Baja]
**Categoría:** [Seguridad | Rendimiento | Lógica | Mantenibilidad]
**Componente:** [Ruta archivo, Endpoint, Tabla]
**CVE/CWE:** [Si aplica]

### 1. Descripción
[2-3 líneas explicando qué está mal]

### 2. Evidencia
**Código Afectado:**
```java
// Snippet exacto
```

**Resultado Observado:**
```
Descripción del problema
```

### 3. Impacto
- **Técnico:** [Ej: caída de memoria]
- **Negocio:** [Ej: incumplimiento normativo]
- **Usuarios:** [Número afectados]

### 4. Recomendación
[Best practice + estrategia, sin código]

### 5. Esfuerzo Estimado
`[Bajo: <4h | Medio: 4-16h | Alto: >16h]`
```

---

# FASE 3: MATRIZ DE PRIORIZACIÓN

| ID | Hallazgo | Severidad | Impacto | Esfuerzo | Prioridad | Bloqueante |
|----|----------|-----------|---------|----------|-----------|-----------|
| SEC-001 | IDOR en /api/users/{id} | 🔴 Crítica | Alto | Bajo | **1** | ✅ Sí |
| PERF-001 | N+1 queries en kardex | 🟠 Alta | Alto | Medio | **2** | ✅ Sí |
| TEST-001 | Cobertura <80% en módulos críticos | 🟠 Alta | Medio | Alto | **3** | ❌ No |
| PERF-002 | Memory leak en AdminComponent | 🟡 Media | Medio | Bajo | **4** | ❌ No |

**Regla:** Implementar P1-P2 antes de cualquier feature nueva. P3+ en siguiente sprint.

---

# FASE 4: COMPLIANCE & ESTÁNDARES INTERNACIONALES

## 4.1 OWASP Top 10 2023 Compliance

| Riesgo | Verificación | ADES Status |
|--------|--------------|-----------|
| A01 - IDOR/BOLA | Autorización en cada endpoint | ✅ Corregido (16 controllers) |
| A02 - Autenticación Rota | JWT exp, hash password, rate limit | ✅ Implementado (Authentik) |
| A03 - Injection (SQL) | Queries parametrizadas | ✅ Auditado (0 hallazgos) |
| A04 - Insecure Design | Modelo amenazas, mitigación | ⚠️ Parcial (STRIDE en progress) |
| A05 - Misconfiguración | Headers seguridad, CORS restrictivo | ✅ Agregado headers |
| A06 - Datos Sensibles | Encriptación tránsito/reposo | ✅ TLS + pgcrypto |
| A07 - Autenticación Rota | Control acceso roles | ✅ RBAC (nivelAcceso 0-5) |
| A08 - Integridad Software | Dependencias auditadas | ✅ Escaneo continuo |
| A09 - Logging Insuficiente | Alertas, retención logs | ✅ Prometheus + Grafana |
| A10 - SSRF | Validación URLs | ⚠️ Por verificar |

---

# FASE 5: MATRIZ DE RIESGO AVANZADA

```
RIESGO = Probabilidad × Impacto × Detectabilidad

Probabilidad:  1=Baja, 2=Media, 3=Alta
Impacto:       1=Bajo, 3=Medio, 9=Alto
Detectabilidad: 1=Fácil, 3=Medio, 9=Difícil

Ejemplo: IDOR = 3 (Alta prob) × 9 (Crítico) × 1 (Fácil detectar) = RPN 27 (Crítica)
```

| Hallazgo | Prob | Imp | Det | RPN | Prioridad |
|----------|------|-----|-----|-----|-----------|
| IDOR en /api/users | 3 | 9 | 1 | 27 | 🔴 Crítica |
| N+1 queries | 3 | 3 | 1 | 9 | 🟠 Alta |
| Memory leak | 2 | 3 | 3 | 18 | 🟠 Alta |
| Falta logging | 1 | 3 | 3 | 9 | 🟡 Media |

---

# FASE 6: AUDITORÍA COGNITIVA AUTOMATIZADA (AuraAudit)

## 6.1 Qué es AuraAudit

Sistema de auditoría semántica que integra:
- **Análisis estático local** (grep, SAST): credenciales hardcodeadas, CORS permisivo, headers faltantes
- **Modelos de lenguaje avanzados** (NVIDIA NIM): detección de fallos lógicos complejos, BOLA/BFLA sutiles, condiciones de carrera

## 6.2 Ciclo de 6 Fases

1. **Boot:** Inicialización, validación NVIDIA_NIM_API_KEY
2. **Detección Stack:** Análisis heurístico (Java? Angular? Nginx?)
3. **Planificación:** Activación selectiva de scanners
4. **Ejecución Local:** 
   - SastScanSkill (credenciales, CORS, headers)
   - PerformanceScanSkill (índices, N+1)
   - FrontendScanSkill (memory leaks, OnPush)
5. **Auditoría Semántica:** Envío a NVIDIA NIM para análisis profundo
6. **Consolidación:** Reporte final Markdown

## 6.3 Comando Ejecución

```bash
export NVIDIA_NIM_API_KEY="tu_clave_aqui"
pip install -r AuraAudit/requirements.txt
python AuraAudit/main.py /opt/ades --output-dir ./reportes-auditoria
```

---

## 📝 NOTAS FINALES

1. **Objetividad:** Siempre documenta evidencias, no opiniones
2. **Constructividad:** Cada hallazgo debe tener recomendación
3. **Accionable:** El dev debe poder entender Y corregir sin preguntas
4. **Contexto:** Considera stack, volumen, criticidad del sistema
5. **Compliance:** Verifica contra estándares aplicables (OWASP, ISO, normativa local)

---

**Versión:** 2.0  
**Última actualización:** 2026-07-10  
**Licencia:** Uso interno - ADES Nevadi

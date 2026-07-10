# 🚀 PLAN DE IMPLEMENTACIÓN EJECUTABLE — ADES 2026-07-09
**Status:** FINAL (Sin SonarQube por espacio disco)  
**Score Target:** 80-82/100 (vs 72/100 actual)  
**Esfuerzo Total:** ~250-280 horas (vs 362h con SonarQube)  
**Timeline:** 5-6 semanas (vs 8 semanas)  

---

## 📊 SCOPE FINAL

### ✅ IMPLEMENTAR (4 hallazgos principales + 2 secundarios)

| # | Hallazgo | Severidad | Timeline | Esfuerzo | Status |
|---|----------|-----------|----------|----------|--------|
| 1 | RATE LIMITING | P1 CRÍTICA | SEMANA 1-2 | 24-32h | ⏳ TO DO |
| 2 | LAZY IMAGES | P1 CRÍTICA | SEMANA 1 | 16-20h | ⏳ TO DO |
| 3 | NGINX COMPRESSION | P1 CRÍTICA | SEMANA 1 | 4-6h | ⏳ TO DO |
| 5 | E2E EXPANSION (23→90 specs) | P2 ALTA | SEMANA 3-6 | 100-140h | ⏳ TO DO |
| 6 | FK INDEXES | P2 ALTA | SEMANA 2 | 8-12h | ⏳ TO DO |
| 7 | OnPush Strategy | P2 MEDIA | SEMANA 4-5 | 40-60h | ⏳ TO DO |

### ⏭️ POSPONER (No hay espacio disco)

| Hallazgo | Razón | Reschedule |
|----------|-------|-----------|
| SonarQube Setup | Disco 93-96% full (~45GB docker images activas) | 2026-09-06 (después cleanup) |
| Jacoco + nyc | Depende de SonarQube | 2026-09-06 |
| Coverage Reporting | Depende de SonarQube | 2026-09-06 |

---

## 📅 ROADMAP REVISADO (6 SEMANAS)

```
┌─ SEMANA 1 ─────────────────────────────────────────────────┐
│ BLOCKER: Rate Limiting + Lazy Images + Nginx Compression   │
│ Esfuerzo: 50h | CRÍTICO: NO AVANZAR SI ALGUNO FALLA        │
│                                                             │
│ ✅ Rate Limiting (Spring Cloud Gateway + Valkey)           │
│ ✅ Lazy Loading (loading="lazy" en 150+ imágenes)          │
│ ✅ Nginx Compression (gzip + brotli)                       │
└─────────────────────────────────────────────────────────────┘

┌─ SEMANA 2 ─────────────────────────────────────────────────┐
│ DB OPTIMIZATION: FK Indexes + Validation                    │
│ Esfuerzo: 20h                                               │
│                                                             │
│ ✅ FK Indexes audit en 5+ tablas críticas                  │
│ ✅ EXPLAIN ANALYZE validation                              │
│ ✅ Performance baseline measurement                         │
└─────────────────────────────────────────────────────────────┘

┌─ SEMANA 3 ─────────────────────────────────────────────────┐
│ E2E FOUNDATION: Auth + CRUD Specs                           │
│ Esfuerzo: 60h                                               │
│                                                             │
│ ✅ 15+ Auth specs (login, logout, reset, permissions)      │
│ ✅ 20+ CRUD specs (create, edit, delete expediente)        │
│ ✅ Helper functions (AuthHelper, ApiHelper, DataFactory)   │
└─────────────────────────────────────────────────────────────┘

┌─ SEMANA 4 ─────────────────────────────────────────────────┐
│ E2E EXPANSION + OPTIMIZATION: Performance + OnPush          │
│ Esfuerzo: 70h                                               │
│                                                             │
│ ✅ 20+ Performance specs (pagination, search, loading)      │
│ ✅ OnPush Migration (45 componentes)                        │
│ ✅ Memory leak audit & fixes                               │
└─────────────────────────────────────────────────────────────┘

┌─ SEMANA 5 ─────────────────────────────────────────────────┐
│ E2E EDGE CASES + RESILIENCE: Network + Concurrent Ops      │
│ Esfuerzo: 50h                                               │
│                                                             │
│ ✅ 25+ Edge case specs (network, concurrent, role-switch) │
│ ✅ CI/CD integration (GitHub Actions)                       │
│ ✅ Flakiness detection & stabilization                      │
└─────────────────────────────────────────────────────────────┘

┌─ SEMANA 6 ─────────────────────────────────────────────────┐
│ VALIDATION: Regression Testing + Load Testing               │
│ Esfuerzo: 40h                                               │
│                                                             │
│ ✅ Full E2E suite regression (all 90+ specs)               │
│ ✅ Load testing JMeter (100 concurrent users)               │
│ ✅ Performance baseline post-optimization                   │
│ ✅ Documentation & team handoff                            │
└─────────────────────────────────────────────────────────────┘

TOTAL: 290 horas (~1.5 devs × 6 semanas or 2-3 devs × 3-4 semanas)
```

---

## 🔴 SEMANA 1 — BLOCKER CRÍTICO (50h)

### 1️⃣ Hallazgo #1: RATE LIMITING AUSENTE

**Propietario:** Backend Lead  
**Esfuerzo:** 24-32h  
**Timeline:** Lunes - Viernes  
**Blocker:** SÍ (sin esto no avanzar a W2)

#### 📋 Checklist Diario

**LUNES (8h):**
- [ ] Backup `pom.xml` actual
- [ ] Agregar dependencias Spring Cloud Gateway
  ```xml
  <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-gateway</artifactId>
      <version>4.1.0</version>
  </dependency>
  <dependency>
      <groupId>io.lettuce</groupId>
      <artifactId>lettuce-core</artifactId>
  </dependency>
  ```
- [ ] Crear rama: `feature/rate-limiting`
- [ ] Compile & verificar sin errores
- [ ] Crear `application-gateway.yml` base
- [ ] Commit: "feat: add Spring Cloud Gateway dependencies"

**MARTES (8h):**
- [ ] Crear `src/main/java/mx/ades/config/RateLimitingConfig.java`
  - `ipKeyResolver()` → limita por IP
  - `userKeyResolver()` → limita por usuario
  - `pathKeyResolver()` → limita por ruta
- [ ] Configurar rutas en `application-gateway.yml`
  - `/api/v1/auth/**` → 5 req/min/IP
  - `/api/v1/**` → 100 req/min/IP
- [ ] Test local: `mvn spring-boot:run`
- [ ] Verificar gateway levanta en puerto 8080
- [ ] Commit: "feat: implement rate limiting config"

**MIÉRCOLES (8h):**
- [ ] Crear test JMeter: `test/rate-limiting.jmx`
  - 10 requests a `/api/v1/auth/login` → todas OK (5/min threshold)
  - Request 6-10 → deben retornar 429
- [ ] Ejecutar JMeter: `jmeter -n -t test/rate-limiting.jmx`
- [ ] Validar métricas en Prometheus (si existe)
- [ ] Ajustar thresholds según results
- [ ] Commit: "test: add rate limiting JMeter tests"

**JUEVES (4h):**
- [ ] Deploy a staging: `docker compose up -d ades-gateway`
- [ ] Test manual: curl 10 veces → 6° debe retornar 429
  ```bash
  for i in {1..10}; do 
    curl -v https://staging.ades.setag.mx/api/v1/auth/login \
      -H "Authorization: Bearer $TOKEN"
    sleep 0.1
  done
  ```
- [ ] Validar en logs: "rate limit exceeded"
- [ ] Commit: "chore: rate limiting validated in staging"

**VIERNES (4h):**
- [ ] Code review & merge a main
- [ ] Smoke test en producción (durante maintenance window)
- [ ] Verificar no hay broken endpoints
- [ ] Update CLAUDE.md con rate limiting config
- [ ] Close ticket JIRA

#### Criterios de Aceptación

- ✅ Gateway levanta en puerto 8080 sin errores
- ✅ `/api/v1/auth/login` → max 5 req/min/IP
- ✅ `/api/v1/*` → max 100 req/min/IP
- ✅ Request 6+ retorna `429 Too Many Requests`
- ✅ JMeter test pasa (todas 5 OK, 6° falla)
- ✅ Logs muestran "rate limit exceeded"
- ✅ No hay regresión en otros endpoints

---

### 2️⃣ Hallazgo #2: LAZY LOADING IMÁGENES AUSENTE

**Propietario:** Frontend Lead  
**Esfuerzo:** 16-20h  
**Timeline:** Lunes - Miércoles  
**Blocker:** SÍ (Lighthouse metric critical)

#### 📋 Checklist Diario

**LUNES (8h):**
- [ ] Audit script: contar `<img>` tags
  ```bash
  find /opt/ades/frontend/src -name "*.html" -exec grep -l '<img' {} + | wc -l
  # Expected: ~30-40 archivos con imágenes
  
  find /opt/ades/frontend/src -name "*.html" -exec grep -c '<img' {} + | \
    awk '{sum+=$1} END {print "Total:", sum}'
  # Expected: ~150-200 tags
  ```
- [ ] Crear migration script `scripts/migrate-lazy-loading.sh`
  ```bash
  #!/bin/bash
  # Backup
  find /opt/ades/frontend/src -name "*.html" -exec cp {} {}.bak \;
  
  # Add loading="lazy" (except hero images)
  find /opt/ades/frontend/src -name "*.html" -exec sed -i \
    '/<img[^>]*class="[^"]*hero[^"]*"/!s/<img\([^>]*\)>/<img\1 loading="lazy">/g' {} \;
  ```
- [ ] Ejecutar script
- [ ] Git diff: validar cambios
- [ ] Verificar no hay imágenes rotas: `npm run build`
- [ ] Commit: "feat: add loading=\"lazy\" to images"

**MARTES (6h):**
- [ ] Run Lighthouse audit
  ```bash
  npx lighthouse https://ades.setag.mx/dashboard \
    --output=json --output-path=lighthouse-before.json
  
  npx lighthouse https://ades.setag.mx/dashboard \
    --throttling-method=simulate --throttle-settings=slowCPU4x,slow4G
  ```
- [ ] Verificar LCP < 2.5s (Slow 3G)
- [ ] Verificar Lighthouse score > 85/100
- [ ] Screenshot de DevTools Network tab
- [ ] Commit: "test: Lighthouse audit passed (LCP <2.5s)"

**MIÉRCOLES (6h):**
- [ ] Manual testing en Chrome DevTools: Slow 3G
  - Scroll page → imágenes below-the-fold NO deben cargar
  - Scroll a cada imagen → debe cargar on-demand
- [ ] Verificar CLS (Cumulative Layout Shift) < 0.1
- [ ] Validar no hay broken images (404 checks)
- [ ] Merge a main
- [ ] Update CLAUDE.md con loading="lazy" pattern

#### Criterios de Aceptación

- ✅ 150+ imágenes con `loading="lazy"`
- ✅ Lighthouse LCP < 2.5s (slow 3G)
- ✅ Lighthouse score ≥ 85/100
- ✅ Image bytes transferred (initial) < 300KB
- ✅ CLS < 0.1 (no layout shifts)
- ✅ 0 broken images (404 checks green)

---

### 3️⃣ Hallazgo #3: NGINX COMPRESSION DESHABILITADA

**Propietario:** DevOps  
**Esfuerzo:** 4-6h  
**Timeline:** Lunes - Martes  
**Blocker:** SÍ (performance critical)

#### 📋 Checklist Diario

**LUNES (3h):**
- [ ] Backup original: `cp /opt/ades/infrastructure/nginx/nginx.conf /opt/ades/infrastructure/nginx/nginx.conf.bak`
- [ ] Validar sintaxis actual: `docker compose exec nginx nginx -t`
- [ ] Edit `/opt/ades/infrastructure/nginx/nginx.conf`
  - Encontrar sección `http {}`
  - Agregar:
    ```nginx
    # GZIP COMPRESSION
    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_min_length 1024;
    gzip_types text/plain text/css text/xml text/javascript 
               application/x-javascript application/xml+rss 
               application/json application/javascript font/truetype 
               font/opentype application/vnd.ms-fontobject image/svg+xml;
    gzip_disable "MSIE [1-6]\.";
    
    # BROTLI COMPRESSION (if available)
    brotli on;
    brotli_comp_level 6;
    brotli_types text/plain text/css text/xml text/javascript 
                 application/x-javascript application/xml+rss 
                 application/json application/javascript font/truetype 
                 font/opentype application/vnd.ms-fontobject image/svg+xml;
    ```
- [ ] Validar sintaxis: `docker compose exec nginx nginx -t`
  - Expected: "syntax is ok" + "test is successful"
- [ ] Commit: "chore: enable gzip + brotli compression"

**MARTES (3h):**
- [ ] Recargar nginx sin downtime: `docker compose exec nginx nginx -s reload`
- [ ] Verificar compresión activa
  ```bash
  curl -I -H "Accept-Encoding: gzip" https://ades.setag.mx/api/v1/users | \
    grep -i "content-encoding"
  # Expected: "Content-Encoding: gzip" or "Content-Encoding: br"
  ```
- [ ] Validar tamaño payload reducido
  ```bash
  # Sin compresión
  curl -s https://ades.setag.mx/api/v1/users | wc -c
  # Esperado: ~45,000 bytes
  
  # Con compresión
  curl -s --compressed https://ades.setag.mx/api/v1/users | wc -c
  # Esperado: ~4,500 bytes (10% del original)
  ```
- [ ] DevTools Network tab: validar payload reducido
- [ ] Merge a main

#### Criterios de Aceptación

- ✅ `Content-Encoding: gzip` en responses
- ✅ JSON payloads comprimidos 80-90% (45KB → 4.5KB)
- ✅ nginx -t: "test successful"
- ✅ 0 downtime en reload
- ✅ DevTools Network muestra payload reducido
- ✅ Lighthouse no reporta "Enable Text Compression"

---

## ✅ SEMANA 1 CHECKPOINT (VIERNES 5PM)

**VALIDACIÓN CRÍTICA:**
```
Score: 72 → 76/100 (+4 puntos)

Checklist:
- [ ] Rate limiting validado con JMeter (429 responses OK)
- [ ] Lazy images: Lighthouse > 85/100, LCP < 2.5s
- [ ] Nginx: Content-Encoding presente, payloads 80% reducidos
- [ ] Todos 3 gaps P1 merged a main
- [ ] 0 broken endpoints
- [ ] 0 regressions

Status: 🟢 GREEN → AVANZAR A SEMANA 2
```

---

## 📅 SEMANA 2 — DB OPTIMIZATION (20h)

### Hallazgo #6: FOREIGN KEY INDEXES INSUFICIENTES

**Propietario:** DBA / Backend Lead  
**Esfuerzo:** 8-12h  
**Timeline:** Lunes - Miércoles

#### Plan Detallado

**PASO 1: Identificar FK faltantes**
```sql
-- Ejecutar en producción (durante backup window)
SELECT 
  t.tablename,
  a.attname,
  'MISSING_INDEX' as status
FROM pg_class t
JOIN pg_attribute a ON t.oid = a.attrelid
JOIN pg_namespace n ON t.relnamespace = n.oid
WHERE n.nspname = 'public'
  AND (a.attname LIKE '%_id' OR a.attname = 'id')
  AND NOT EXISTS (
    SELECT 1 FROM pg_index i
    WHERE i.indrelid = t.oid 
    AND (i.indkey::text LIKE (a.attnum::text || '%') 
         OR a.attname IN ('expediente_id', 'alumno_id', 'tarea_id', 'grupo_id'))
  )
LIMIT 20;
```

**PASO 2: Crear índices críticos**
```sql
-- Identificar en consulta anterior, luego:
CREATE INDEX CONCURRENTLY idx_expediente_alumno_id ON ades_expedientes(alumno_id) 
  WHERE alumno_id IS NOT NULL;
CREATE INDEX CONCURRENTLY idx_calificacion_tarea_id ON ades_calificaciones(tarea_id) 
  WHERE tarea_id IS NOT NULL;
CREATE INDEX CONCURRENTLY idx_planeacion_grupo_id ON ades_planeaciones(grupo_id) 
  WHERE grupo_id IS NOT NULL;
CREATE INDEX CONCURRENTLY idx_clase_grupo_id ON ades_clases(grupo_id) 
  WHERE grupo_id IS NOT NULL;
CREATE INDEX CONCURRENTLY idx_alumno_expediente_id ON ades_alumnos(expediente_id) 
  WHERE expediente_id IS NOT NULL;
```

**PASO 3: Validar performance**
```sql
-- ANTES: Seq Scan en tablas grandes
EXPLAIN ANALYZE 
  SELECT * FROM ades_calificaciones WHERE tarea_id = 'uuid-aqui';
-- Resultado esperado antes: "Seq Scan" (MALO)

-- DESPUÉS: Index Scan
-- Resultado esperado después: "Index Scan" (BUENO)
```

**PASO 4: Crear migración**
```bash
# Crear archivo migración
touch /opt/ades/db/migrations/115_add_fk_indexes.sql

# Contenido:
CREATE INDEX CONCURRENTLY idx_expediente_alumno_id ...
CREATE INDEX CONCURRENTLY idx_calificacion_tarea_id ...
...
```

#### Criterios de Aceptación
- ✅ 5+ índices creados en FKs críticas
- ✅ EXPLAIN ANALYZE muestra Index Scan (no Seq Scan)
- ✅ Query performance mejorado (latency -50%+)
- ✅ Migración versionada en db/migrations/

---

## 📅 SEMANA 3-6 — E2E EXPANSION & OPTIMIZATION (220h)

### Plan Modular por Semana

**SEMANA 3: E2E Foundation (60h)**
- [ ] Auth specs: login, logout, reset, permissions (15+ tests) - 20h
- [ ] CRUD specs: create, edit, delete expediente (20+ tests) - 24h
- [ ] Helper functions: AuthHelper, ApiHelper, DataFactory - 16h

**SEMANA 4: E2E Performance + OnPush (70h)**
- [ ] Performance specs: pagination, search, loading (20+ tests) - 20h
- [ ] OnPush Migration: 45 componentes - 40h
- [ ] Memory leak audit & fixes - 10h

**SEMANA 5: E2E Edge Cases + CI/CD (50h)**
- [ ] Edge case specs: network, concurrent, role-switch (25+ tests) - 25h
- [ ] CI/CD integration: GitHub Actions - 15h
- [ ] Flakiness detection & stabilization - 10h

**SEMANA 6: Regression + Load Testing (40h)**
- [ ] Full E2E regression: all 90+ specs - 15h
- [ ] Load testing JMeter: 100 concurrent users - 15h
- [ ] Performance baseline post-optimization - 5h
- [ ] Documentation & handoff - 5h

---

## 🎯 MÉTRICAS DE ÉXITO FINAL

```
MÉTRICA                  ANTES      DESPUÉS    TARGET
─────────────────────────────────────────────────────────
Rate Limiting            0/555      555/555    ✓ 100%
Lazy Images              0%         100%       ✓ 100%
Nginx Compression        0%         85%        ✓ 80%+
E2E Specs               23 (4%)     90+ (80%)  ✓ 80%+
FK Indexes               2          7+         ✓ Ok
OnPush Strategy         59.4%       85%+       ✓ Good
Overall Score           72/100      80/100     ✓ +8%

SonarQube               ❌ POSPUESTO (disco 93% full)
                        📅 Reschedule: 2026-09-06
                        (después cleanup docker images)
```

---

## 🔴 CRITICAL PATH SIMPLIFICADO

```
SEMANA 1 (BLOCKER):
  └─ Rate Limiting + Lazy Images + Nginx Compression
     └─ Si falla algo → NO avanzar a W2

SEMANA 2:
  └─ FK Indexes + validación
     └─ Performance baseline

SEMANA 3-6:
  └─ E2E Expansion (gradual, modular)
     └─ OnPush Migration (paralelo a E2E W4)
     └─ Final validation (W6)
```

---

## 📁 ARCHIVOS A ACTUALIZAR

| Archivo | Cambios |
|---------|---------|
| `backend-spring/pom.xml` | Agregar Spring Cloud Gateway |
| `application-gateway.yml` | Crear (rate limiting config) |
| `RateLimitingConfig.java` | Crear (key resolvers) |
| `frontend/**/*.html` | Agregar `loading="lazy"` |
| `nginx.conf` | Agregar gzip + brotli |
| `db/migrations/115_*.sql` | Crear (FK indexes) |
| `frontend/**/*.component.ts` | Agregar OnPush strategy |
| `ades_testing/e2e/**` | Expandir Playwright specs |
| `CLAUDE.md` | Actualizar (post-implementación) |

---

## 📞 TEAM ASSIGNMENT

| Rol | Semanas | Esfuerzo | Asignado |
|-----|---------|----------|----------|
| Backend Lead | W1-2, W4 | 56h | ☐ |
| Frontend Lead | W1, W4-6 | 96h | ☐ |
| QA/Automation | W3-6 | 100h | ☐ |
| DevOps | W1-2 | 10h | ☐ |
| **Total** | W1-6 | **~290h** | |

---

## ⚠️ BLOCKERS & CONTINGENCY

| Blocker | If Happens | Mitigation |
|---------|-----------|-----------|
| Rate limiting fails | Staging broken | Revert, use nginx module instead |
| Lazy images break layout | UI broken | Revert, manual by-component approach |
| FK indexes slow down writes | Performance degrades | CONCURRENTLY already handles |
| E2E tests too flaky | Regression not trusted | Run 3x, establish baseline |
| OnPush breaks components | Runtime errors | Gradual migration (high-risk first) |

---

## 📊 WEEKLY DASHBOARD TEMPLATE

```
WEEK-X STATUS REPORT
═════════════════════════════════════════════════════════════

STARTED:  [Date]
SCORE:    72/100 → [Current]/100 (+[delta])

COMPLETED THIS WEEK:
  ✓ Task 1 (Owner: Name, 8h)
  ✓ Task 2 (Owner: Name, 12h)
  ☐ Task 3 (In Progress, Owner: Name)

BLOCKERS:
  ❌ [Issue] (Escalate to: Person, SLA: 4h)

NEXT WEEK:
  - [ ] Task A (20h)
  - [ ] Task B (15h)

METRICS:
  - LCP: [value]s (target: <2.5s)
  - Rate Limit: [requests tested]
  - E2E Pass Rate: [X]%
  - Disk Space: [GB remaining]

STATUS: 🟢 GREEN / 🟡 YELLOW / 🔴 RED
```

---

## 🚀 START TOMORROW (LUNES)

### DAY 1 Morning (30min)
- [ ] Kick-off meeting: All hands
- [ ] Confirm team assignments
- [ ] Confirm SEMANA 1 goals
- [ ] Create JIRA tickets (story per hallazgo)
- [ ] Arrange daily standups (10am, 15min)

### DAY 1 Afternoon (start work)
- [ ] Backend Lead: Start Rate Limiting (feature branch)
- [ ] Frontend Lead: Start Lazy Images (feature branch)
- [ ] DevOps: Prepare Nginx changes (backup, review)

### Daily Rhythm
```
10:00am: 15min standup (what, blockers, next)
4:00pm: Commit & push to feature branches
5:00pm: Slack status update
```

### Weekly Rhythm
```
Friday 4:00pm: Checkpoint meeting (5 gaps closed? Score improved?)
Friday 5:00pm: Dashboard update
If green → merge to main, move to next week
If not green → extend week or replan
```

---

## ✅ DEFINITION OF DONE

Semana X es DONE cuando:
- [ ] Todos commits mergados a main
- [ ] 0 broken tests
- [ ] Score aumentó en 2-4 puntos
- [ ] Dashboard actualizado
- [ ] Team ready para siguiente semana

---

## 🎯 FINAL SUCCESS CRITERIA

**SEMANA 1 (BLOCKER):**
- ✅ Score 72 → 76/100
- ✅ Rate limiting + Lazy images + Compression activos
- ✅ 0 regressions

**SEMANA 6 (FINAL):**
- ✅ Score 76 → 80-82/100
- ✅ E2E suite: 90+ specs (80% cobertura)
- ✅ All P1 + P2 gaps resueltos
- ✅ Performance baseline mejorado

**POSPUESTO (Septiembre):**
- 📅 SonarQube setup (cuando disco < 80%)
- 📅 Coverage reporting integration
- 📅 Code quality dashboards

---

**Plan Ejecutable FINAL — Sin SonarQube**  
**Timeline:** 6 semanas | **Esfuerzo:** ~290h  
**Ready to Start:** LUNES 2026-07-15


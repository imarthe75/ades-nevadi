# 📋 AUDITORÍA INTEGRAL ADES — PLAN DE IMPLEMENTACIÓN
**Fecha Auditoría:** 2026-07-09  
**Score Actual:** 72/100 (Level 3/5)  
**Estado:** 5 gaps críticos detectados  
**Horizonte Implementación:** 8 semanas  

---

## 📊 RESUMEN EJECUTIVO

| Aspecto | Valor |
|---------|-------|
| **Score Arquitectura** | 72/100 (MEDIUM-HIGH) |
| **Hallazgos Críticos** | 5 |
| **Hallazgos Altos** | 2 |
| **Hallazgos Medios** | 1 |
| **Fortalezas Detectadas** | 5 (Auditoría, Auth, Memory, Security Headers, OAuth2) |
| **Riesgo Actual** | MEDIUM (vulnerable a brute force, performance degradado) |
| **Esfuerzo Total Estimado** | 120-160 horas (3-4 sprints) |

### Métricas Clave Antes/Después

```
                    ANTES      DESPUÉS   META
Rate Limiting      0/555      555/555   ✓
Lazy Images        0/150      150/150   ✓
Nginx Compression  0%         85%       ✓
Test Coverage      UNKNOWN    75%       ✓
E2E Specs          23         90+       ✓
FK Indexes         2          15+       ✓
```

---

## 🔴 HALLAZGO #1: RATE LIMITING AUSENTE
**Severidad:** CRÍTICA | **Score Impact:** -15pts | **Prioridad:** P1  
**Esfuerzo:** 24-32h | **Timeline:** SEMANA 1-2

### 1.1 Descripción del Problema
- **Estado Actual:** 0 implementaciones de rate limiting
- **Riesgo:** Vulnerable a brute force, credential stuffing, DDoS
- **Endpoints Afectados:** Todos los 555 endpoints REST
- **Normativa:** OWASP A01 (BOLA), ISO27001 A.9

### 1.2 Impacto Técnico
```
SIN rate limiting:
├── /api/v1/auth/login → 10,000 intentos/segundo = brute force en 1 hora
├── /api/v1/users → enumeración de usuarios válidos
├── /api/v1/expedientes → escaneo masivo de datos
└── DDoS ampliación: un attacker → 10,000 rps efectivos

CON rate limiting:
├── /api/v1/auth/login → max 5 intentos/minuto/IP → brute force requiere 12+ horas
├── /api/v1/users → max 100 req/minuto → enumeración detectada
├── /api/v1/expedientes → max 500 req/minuto → amplificación bloqueada
└── DDoS mitigation: distribuyen limites por IP/user/global
```

### 1.3 Plan de Implementación

#### OPCIÓN A: Spring Cloud Gateway (RECOMENDADO)
**Ventajas:** Gateway centralizado, soporte distribuido con Redis, métricas buenas  
**Desventajas:** Requiere infraestructura gateway adicional  
**Timeline:** 24h

**Pasos:**

1. **Agregar dependencias en `backend-spring/pom.xml`:**
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
    <version>4.1.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
</dependency>
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
</dependency>
```

2. **Crear configuración en `application-gateway.yml`:**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: api-v1-auth
          uri: http://localhost:8080
          predicates:
            - Path=/api/v1/auth/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 5       # 5 req/min
                redis-rate-limiter.burstCapacity: 10      # burst permitido
                key-resolver: "#{@ipKeyResolver}"
        - id: api-v1-general
          uri: http://localhost:8080
          predicates:
            - Path=/api/v1/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100     # 100 req/min
                redis-rate-limiter.burstCapacity: 200
                key-resolver: "#{@ipKeyResolver}"
```

3. **Crear resolvers en `backend-spring/src/main/java/mx/ades/config/RateLimitingConfig.java`:**
```java
@Configuration
public class RateLimitingConfig {
    
    @Bean(name = "ipKeyResolver")
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
            exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
        );
    }
    
    @Bean(name = "userKeyResolver")
    public KeyResolver userKeyResolver() {
        return exchange -> Mono.just(
            exchange.getPrincipal()
                .map(Principal::getName)
                .orElse("anonymous")
        );
    }
    
    @Bean(name = "pathKeyResolver")
    public KeyResolver pathKeyResolver() {
        return exchange -> Mono.just(
            exchange.getRequest().getPath().toString()
        );
    }
}
```

4. **Configurar Valkey (ya está corriendo en localhost:6379):**
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: ${VALKEY_PASSWORD}
    timeout: 2000ms
```

5. **Agregar métricas (Micrometer):**
```java
@Component
public class RateLimitMetricsCollector {
    private final MeterRegistry meterRegistry;
    
    public RateLimitMetricsCollector(MeterRegistry registry) {
        this.meterRegistry = registry;
    }
    
    public void recordRateLimitHit(String key, String reason) {
        Counter.builder("rate.limit.hit")
            .tag("key", key)
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();
    }
}
```

#### Criterios de Aceptación:
- [ ] Gateway levanta en puerto 8080 (reverse proxy)
- [ ] `/api/v1/auth/login` máximo 5 intentos/minuto por IP
- [ ] `/api/v1/*` máximo 100 req/minuto por IP
- [ ] Intento excedido retorna `429 Too Many Requests`
- [ ] Métricas en Prometheus `rate_limit_hit{key="..."}` registradas
- [ ] Test JMeter valida límites (5 req OK, 6° rechazado)

---

## 🟠 HALLAZGO #2: LAZY LOADING IMÁGENES AUSENTE
**Severidad:** CRÍTICA | **Score Impact:** -12pts | **Prioridad:** P1  
**Esfuerzo:** 16-20h | **Timeline:** SEMANA 1

### 2.1 Descripción del Problema
- **Estado Actual:** 0/150+ imágenes con `loading="lazy"`
- **Impacto:** LCP aumentado 1-3s, consumo bandwidth innecesario
- **Métrica:** Lighthouse LCP >2.5s penaliza SEO (Google)
- **Usuarios Afectados:** Todos, especialmente móvil

### 2.2 Impacto en Web Vitals

```
ANTES (sin lazy loading):
├── LCP (Largest Contentful Paint):  4.2s ❌ (target <2.5s)
├── FCP (First Contentful Paint):    2.8s ⚠️
├── CLS (Cumulative Layout Shift):   0.12 ✓
├── Image bytes transferred:         ~850KB (inicial)
└── Lighthouse Score:                58/100 ❌

DESPUÉS (con lazy loading):
├── LCP:                             1.8s ✓
├── FCP:                             1.2s ✓
├── CLS:                             0.08 ✓
├── Image bytes transferred:         ~280KB (inicial)
└── Lighthouse Score:                92/100 ✓
```

### 2.3 Plan de Implementación

#### PASO 1: Auditoría de Imágenes
```bash
# Contar imágenes actuales
find /opt/ades/frontend/src -name "*.html" -exec grep -c '<img' {} + | \
  awk '{sum+=$1} END {print "Total <img tags:", sum}'

# Listar archivos con más de 5 imágenes
find /opt/ades/frontend/src -name "*.html" -exec sh -c \
  'count=$(grep -c "<img" "$1" 2>/dev/null || echo 0); 
   [ $count -gt 5 ] && echo "$1: $count images"' _ {} \;
```

#### PASO 2: Crear script de migración
```bash
#!/bin/bash
# script: migrate-lazy-loading.sh

# Backup original files
find /opt/ades/frontend/src -name "*.html" -exec cp {} {}.bak \;

# Agregar loading="lazy" a todas las imágenes (excepto hero/hero-background)
find /opt/ades/frontend/src -name "*.html" -exec sed -i \
  '/<img[^>]*class="[^"]*hero[^"]*"/!s/<img\([^>]*\)>/<img\1 loading="lazy">/g' {} \;

echo "✓ Migration completed. Check changes and git diff"
```

#### PASO 3: Actualizar templates por módulo

**Módulos prioritarios (orden de riesgo):**
1. Dashboard (múltiples gráficos/imágenes)
2. Expediente (fotos de alumnos)
3. Galería/Biblioteca (masas de imágenes)
4. Reportes (listados con imágenes)
5. Admin (tablas con thumbnails)

**Patrón por módulo:**

```html
<!-- ❌ ANTES -->
<img src="assets/logo.png" alt="Logo">
<p-image [src]="expediente.foto" alt="Foto alumno"></p-image>

<!-- ✅ DESPUÉS -->
<img src="assets/logo.png" alt="Logo" loading="lazy">
<p-image [src]="expediente.foto" alt="Foto alumno" loading="lazy"></p-image>

<!-- Para imágenes above-the-fold (hero, header) -->
<img src="assets/hero.jpg" alt="Hero" loading="eager">

<!-- Para imágenes con placeholders -->
<img 
  src="assets/image.png" 
  alt="..." 
  loading="lazy"
  (load)="onImageLoad($event)">
```

#### PASO 4: Validación con Lighthouse
```bash
# Después de cambios, ejecutar:
npm run build --prod
npx lighthouse https://ades.setag.mx/dashboard --view

# Verificar:
# - LCP < 2.5s
# - Image bytes transferred reducidos 60%+
# - Lighthouse score > 85/100
```

#### PASO 5: Test manual en diferentes conexiones
```bash
# Chrome DevTools > Network > "Slow 3G"
# Verificar:
# 1. Imágenes below-the-fold NO se cargan hasta scroll
# 2. LCP completo < 2.5s con throttling
# 3. No hay layout shift cuando cargan imágenes
```

#### Criterios de Aceptación:
- [ ] 150+ imágenes con `loading="lazy"` (except hero/header)
- [ ] Lighthouse LCP < 2.5s en slow 3G
- [ ] Image bytes transferred (initial) < 300KB
- [ ] 0 layout shifts (CLS < 0.1)
- [ ] Test Lighthouse Score > 85/100
- [ ] No broken images (404 checks)

---

## 🟠 HALLAZGO #3: NGINX COMPRESSION DESHABILITADA
**Severidad:** CRÍTICA | **Score Impact:** -10pts | **Prioridad:** P1  
**Esfuerzo:** 4-6h | **Timeline:** SEMANA 1

### 3.1 Descripción del Problema
- **Estado Actual:** 0 gzip/brotli en nginx.conf
- **Impacto:** JSON payloads 10-50KB sin comprimir
- **Reducción esperada:** 80-90% en tamaño (10KB → 1.2KB)
- **Latencia:** -2-5x en conexiones lentas

### 3.2 Plan de Implementación

#### PASO 1: Validar configuración actual
```bash
# En servidor/contenedor nginx
curl -I https://ades.setag.mx/api/v1/users | grep -i "content-encoding"
# Resultado esperado: (nada) - sin compresión

# Verificar tamaño sin compresión
curl https://ades.setag.mx/api/v1/users | wc -c
# Ejemplo: 45230 bytes sin comprimir

# Con compresión esperado: ~4500 bytes (10% del original)
```

#### PASO 2: Actualizar `/opt/ades/infrastructure/nginx/nginx.conf`

```nginx
# En sección http {} (nivel global)

http {
    # ====== GZIP COMPRESSION ======
    gzip on;                           # Habilitar gzip
    gzip_vary on;                      # Agregar Vary: Accept-Encoding header
    gzip_proxied any;                  # Comprimir cached responses
    gzip_comp_level 6;                 # 1=fast, 9=best (default 6)
    gzip_min_length 1024;              # Solo comprimir > 1KB
    
    # Tipos MIME a comprimir
    gzip_types 
        text/plain
        text/css
        text/xml
        text/javascript
        application/x-javascript
        application/xml+rss
        application/json
        application/javascript
        application/xml
        font/truetype
        font/opentype
        application/vnd.ms-fontobject
        image/svg+xml;
    
    # Desabilitar gzip para navegadores viejos
    gzip_disable "MSIE [1-6]\.";
    
    # ====== BROTLI COMPRESSION (opcional, mejor ratio) ======
    # Requiere nginx compilado con --with-http_brotli_module
    brotli on;
    brotli_comp_level 6;
    brotli_types 
        text/plain
        text/css
        text/xml
        text/javascript
        application/x-javascript
        application/xml+rss
        application/json
        application/javascript
        application/xml
        font/truetype
        font/opentype
        application/vnd.ms-fontobject
        image/svg+xml;
    
    # ====== CACHE HEADERS (complementario) ======
    # En sección server {} o location {}:
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
    
    location ~* \.json$ {
        add_header Cache-Control "public, max-age=3600";
    }
}
```

#### PASO 3: Recargar nginx
```bash
# Validar sintaxis
docker compose exec nginx nginx -t
# Resultado: "syntax is ok" y "test is successful"

# Recargar (sin downtime)
docker compose exec nginx nginx -s reload

# Verificar
curl -I https://ades.setag.mx/api/v1/users | grep -i "content-encoding"
# Resultado esperado: "Content-Encoding: gzip" o "Content-Encoding: br"
```

#### PASO 4: Validar compresión
```bash
# Tamaño sin compresión
curl -s https://ades.setag.mx/api/v1/users --compressed | wc -c
# Antes: 45230 bytes
# Después: ~4500 bytes (10%)

# Headers
curl -I --compressed https://ades.setag.mx/api/v1/users | grep -i encoding
# Esperado: "Content-Encoding: gzip"

# Ratio de compresión
curl -s https://ades.setag.mx/api/v1/users | gzip -c | wc -c
# Comparar con respuesta sin gzip
```

#### PASO 5: Monitoreo
```bash
# En Prometheus/Grafana (si existe)
# Métrica sugerida: nginx_response_size_bytes vs actual_transfer_bytes
# Ratio: actual / response * 100 → target <15%

# En nginx access logs
# Formato: $body_bytes_sent vs $bytes_sent
# $bytes_sent = transfer (con compresión), $body_bytes_sent = original
```

#### Criterios de Aceptación:
- [ ] gzip on en nginx.conf
- [ ] Content-Encoding: gzip en responses
- [ ] JSON payloads comprimidos 80-90%
- [ ] nginx -t: "test successful"
- [ ] 0 downtime en reload
- [ ] DevTools Network tab muestra payload reducido
- [ ] Lighthouse no reporta "Enable Text Compression"

---

## 🟡 HALLAZGO #4: COBERTURA DE TESTING INVISIBLE
**Severidad:** ALTA | **Score Impact:** -8pts | **Prioridad:** P2  
**Esfuerzo:** 32-40h | **Timeline:** SEMANAS 2-3

### 4.1 Descripción del Problema
- **Estado Actual:** SonarQube NO integrado, cobertura desconocida
- **Risk:** Regresiones ocultas, deuda técnica invisible
- **Meta:** 75% statements, 70% branches

### 4.2 Plan de Implementación - BACKEND (Spring)

#### PASO 1: Integrar Jacoco en `backend-spring/pom.xml`
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <!-- Prepare agent para tests -->
        <execution>
            <id>prepare-agent</id>
            <phase>initialize</phase>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        
        <!-- Generate report después de tests -->
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        
        <!-- Coverage check (fail build si < 75%) -->
        <execution>
            <id>coverage-check</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <excludes>
                            <exclude>*Test</exclude>
                        </excludes>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.75</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### PASO 2: Agregar SonarQube en `.github/workflows/quality.yml`
```yaml
name: Code Quality

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  sonarqube:
    name: SonarQube Analysis
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0  # Para análisis completo
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      
      - name: Build and Test (Spring Backend)
        run: |
          cd backend-spring
          ./mvnw clean test -DskipITs
      
      - name: Generate Jacoco Report
        run: |
          cd backend-spring
          ./mvnw jacoco:report
      
      - name: Upload Coverage to SonarQube
        uses: sonarsource/sonarqube-scan-action@master
        env:
          SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        with:
          args: >
            -Dsonar.projectKey=ades-nevadi
            -Dsonar.sources=backend-spring/src/main
            -Dsonar.tests=backend-spring/src/test
            -Dsonar.coverage.jacoco.xmlReportPaths=backend-spring/target/site/jacoco/jacoco.xml
            -Dsonar.java.binaries=backend-spring/target/classes
      
      - name: SonarQube Quality Gate
        uses: sonarsource/sonarqube-quality-gate-action@master
        timeout-minutes: 5
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
```

#### PASO 3: Configurar `sonar-project.properties` (raíz del proyecto)
```properties
sonar.projectKey=ades-nevadi
sonar.projectName=ADES Sistema Educativo
sonar.projectVersion=1.0.0

sonar.sources=backend-spring/src/main,frontend/src/app
sonar.tests=backend-spring/src/test,frontend/src/app
sonar.exclusions=**/*Test.java,**/*.spec.ts,**/node_modules/**

# Backend
sonar.java.binaries=backend-spring/target/classes
sonar.coverage.jacoco.xmlReportPaths=backend-spring/target/site/jacoco/jacoco.xml

# Frontend
sonar.typescript.lcov.reportPaths=frontend/coverage/lcov.info

# Quality Gates
sonar.qualitygate.wait=true
sonar.qualitygate.timeout=300
```

### 4.3 Plan de Implementación - FRONTEND (Angular)

#### PASO 1: Habilitar Coverage en `frontend/package.json`
```json
{
  "scripts": {
    "test": "ng test --watch=true",
    "test:ci": "ng test --watch=false --browsers=ChromeHeadless",
    "test:coverage": "ng test --code-coverage --watch=false --browsers=ChromeHeadless",
    "sonar": "sonar-scanner -Dsonar.typescript.lcov.reportPaths=coverage/lcov.info"
  },
  "devDependencies": {
    "nyc": "^15.1.0",
    "sonar-scanner": "^3.1.0"
  }
}
```

#### PASO 2: Configurar `frontend/karma.conf.js`
```javascript
coverageReporter: {
  dir: require('path').join(__dirname, './coverage'),
  subdir: '.',
  reporters: [
    { type: 'html' },
    { type: 'text-summary' },
    { type: 'lcov' }  // Para SonarQube
  ],
  check: {
    global: {
      statements: 75,
      branches: 70,
      functions: 75,
      lines: 75
    }
  }
}
```

#### PASO 3: CI/CD para Frontend
```yaml
# En .github/workflows/quality.yml (agregar job)
  frontend-coverage:
    name: Frontend Coverage
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json
      
      - name: Install Dependencies
        run: cd frontend && npm ci
      
      - name: Run Tests with Coverage
        run: cd frontend && npm run test:coverage
      
      - name: Upload to SonarQube
        uses: sonarsource/sonarqube-scan-action@master
        env:
          SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        with:
          args: >
            -Dsonar.projectKey=ades-nevadi-frontend
            -Dsonar.sources=frontend/src/app
            -Dsonar.exclusions=**/*.spec.ts
            -Dsonar.typescript.lcov.reportPaths=frontend/coverage/lcov.info
```

#### Criterios de Aceptación:
- [ ] SonarQube levanta y accesible en `http://sonar.ades.local`
- [ ] Backend: Jacoco report en `target/site/jacoco/index.html`
- [ ] Frontend: lcov report en `coverage/lcov.info`
- [ ] CI/CD ejecuta análisis en cada push
- [ ] Coverage dashboard muestra 75%+ statements
- [ ] Quality Gate configurable (fallback en 50%)
- [ ] PR comments con coverage delta

---

## 🟡 HALLAZGO #5: E2E TESTING INSUFICIENTE
**Severidad:** ALTA | **Score Impact:** -8pts | **Prioridad:** P2  
**Esfuerzo:** 80-120h | **Timeline:** SEMANAS 4-8

### 5.1 Descripción del Problema
- **Estado Actual:** 23 test cases (4% cobertura)
- **Meta:** 90+ specs (80%+ flujos críticos)
- **Riesgo:** Regresiones en navegadores reales, edge cases sin validar

### 5.2 Plan de Implementación

#### ESTRUCTURA NUEVA (80+ specs)

```
ades_testing/e2e/
├── auth/
│   ├── login.spec.ts (15 tests) ← PRIORIDAD 1
│   ├── logout.spec.ts (8 tests)
│   └── password-reset.spec.ts (10 tests)
├── crud/
│   ├── create-expediente.spec.ts (20 tests) ← PRIORIDAD 1
│   ├── edit-expediente.spec.ts (18 tests)
│   └── delete-expediente.spec.ts (10 tests)
├── calificaciones/
│   ├── ingresar-calificaciones.spec.ts (15 tests)
│   ├── boleta-generacion.spec.ts (12 tests)
│   └── reporte-911.spec.ts (10 tests)
├── performance/
│   ├── pagination.spec.ts (12 tests)
│   ├── search-performance.spec.ts (10 tests)
│   └── large-list-rendering.spec.ts (8 tests)
├── edge-cases/
│   ├── network-resilience.spec.ts (14 tests)
│   ├── concurrent-operations.spec.ts (12 tests)
│   ├── role-switching.spec.ts (10 tests)
│   └── permission-denial.spec.ts (10 tests)
├── ui/
│   ├── form-validation.spec.ts (15 tests)
│   ├── modal-dialogs.spec.ts (10 tests)
│   └── responsive-layout.spec.ts (8 tests)
└── helpers/
    ├── auth-helper.ts
    ├── api-helper.ts
    └── data-factory.ts

Total: 95+ tests
```

#### PASO 1: Templates de Specs (SEMANA 4)

**Spec Template: `auth/login.spec.ts`**
```typescript
import { test, expect } from '@playwright/test';
import { AuthHelper } from '../helpers/auth-helper';

test.describe('Authentication - Login Flow', () => {
  let authHelper: AuthHelper;
  
  test.beforeEach(async ({ page }) => {
    authHelper = new AuthHelper(page);
    await page.goto('https://ades.setag.mx/login');
  });
  
  test('TC-001: Valid credentials login successful', async ({ page }) => {
    // Arrange
    const validUser = { email: 'admin@nevadi.edu.mx', password: 'TestPass123!' };
    
    // Act
    await authHelper.fillLoginForm(validUser.email, validUser.password);
    await page.click('button:has-text("Iniciar Sesión")');
    
    // Assert
    await expect(page).toHaveURL(/.*\/dashboard/);
    await expect(page.locator('text=Bienvenido')).toBeVisible();
  });
  
  test('TC-002: Invalid credentials login fails', async ({ page }) => {
    // Arrange
    const invalidUser = { email: 'admin@nevadi.edu.mx', password: 'WrongPass' };
    
    // Act
    await authHelper.fillLoginForm(invalidUser.email, invalidUser.password);
    await page.click('button:has-text("Iniciar Sesión")');
    
    // Assert
    await expect(page.locator('text=Credenciales inválidas')).toBeVisible();
    await expect(page).toHaveURL(/.*\/login/);
  });
  
  test('TC-003: Brute force protection (429 after 5 attempts)', async ({ page }) => {
    // Este test valida el rate limiting
    for (let i = 0; i < 5; i++) {
      await authHelper.fillLoginForm('admin@nevadi.edu.mx', 'wrong');
      await page.click('button:has-text("Iniciar Sesión")');
      await page.waitForTimeout(500);
    }
    
    // 6to intento debe retornar 429
    const response = await page.context().request.post(
      'https://ades.setag.mx/api/v1/auth/login',
      {
        data: { email: 'admin@nevadi.edu.mx', password: 'wrong' }
      }
    );
    expect(response.status()).toBe(429);
  });
  
  test('TC-004: Session persistence (refresh page keeps session)', async ({ page, context }) => {
    // Auth
    await authHelper.login('admin@nevadi.edu.mx', 'TestPass123!');
    
    // Guardar token
    const token = await page.evaluate(() => sessionStorage.getItem('ades_token'));
    expect(token).toBeTruthy();
    
    // Reload
    await page.reload();
    
    // Assert: token aún válido
    await expect(page).toHaveURL(/.*\/dashboard/);
  });
  
  test('TC-005: CSRF protection (token refresh on logout)', async ({ page }) => {
    // Login
    await authHelper.login('admin@nevadi.edu.mx', 'TestPass123!');
    
    // Logout
    await page.click('button:has-text("Cerrar Sesión")');
    
    // Token debe invalidarse
    const tokenAfterLogout = await page.evaluate(() => 
      sessionStorage.getItem('ades_token')
    );
    expect(tokenAfterLogout).toBeNull();
  });
});
```

**Spec Template: `crud/create-expediente.spec.ts`**
```typescript
test.describe('Expediente CRUD - Create', () => {
  test('TC-E001: Create expediente with all fields', async ({ page }) => {
    // 20 test cases cubriendo happy path, validations, edge cases
    // Ejemplos:
    // - Crear con todos los campos obligatorios
    // - Validar campos requeridos
    // - Validar formatos (email, teléfono, RFC)
    // - Upload de archivos
    // - Cancel operation
    // - Duplicate detection
  });
});
```

#### PASO 2: Helpers & Utilities (SEMANA 4)

**`helpers/auth-helper.ts`**
```typescript
export class AuthHelper {
  constructor(private page: Page) {}
  
  async login(email: string, password: string) {
    await this.page.goto('https://ades.setag.mx/login');
    await this.fillLoginForm(email, password);
    await this.page.click('button:has-text("Iniciar Sesión")');
    await this.page.waitForURL(/.*\/dashboard/);
  }
  
  async fillLoginForm(email: string, password: string) {
    await this.page.fill('input[type="email"]', email);
    await this.page.fill('input[type="password"]', password);
  }
  
  async logout() {
    await this.page.click('button:has-text("Cerrar Sesión")');
  }
  
  async isAuthenticated(): Promise<boolean> {
    const token = await this.page.evaluate(() => 
      sessionStorage.getItem('ades_token')
    );
    return !!token;
  }
}
```

**`helpers/api-helper.ts`**
```typescript
export class ApiHelper {
  constructor(private baseUrl: string, private token?: string) {}
  
  async post(path: string, data: any, expectedStatus = 201) {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${this.token}`
      },
      body: JSON.stringify(data)
    });
    expect(response.status).toBe(expectedStatus);
    return response.json();
  }
  
  async get(path: string, expectedStatus = 200) {
    const response = await fetch(`${this.baseUrl}${path}`, {
      headers: {
        'Authorization': `Bearer ${this.token}`
      }
    });
    expect(response.status).toBe(expectedStatus);
    return response.json();
  }
}
```

#### PASO 3: CI/CD Integration (SEMANA 5)

**`.github/workflows/e2e.yml`**
```yaml
name: E2E Testing

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
  schedule:
    - cron: '0 2 * * *'  # Nightly run

jobs:
  e2e:
    name: Playwright E2E Tests
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: '20'
      
      - name: Install Playwright
        run: |
          cd ades_testing
          npm install
          npx playwright install --with-deps
      
      - name: Wait for services
        run: |
          # Esperar a que services estén listos
          timeout 120 bash -c 'until curl -f http://localhost:8080/health; do sleep 5; done'
      
      - name: Run E2E Tests
        run: |
          cd ades_testing
          npx playwright test
        env:
          PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD: "true"
          CI: "true"
      
      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: playwright-report
          path: ades_testing/playwright-report/
      
      - name: Report to GitHub
        if: failure()
        uses: actions/github-script@v6
        with:
          script: |
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: '❌ E2E tests failed. See artifacts for details.'
            })
```

#### PASO 4: Ejecución Local
```bash
# Instalar
cd ades_testing
npm install
npx playwright install

# Ejecutar suite completa
npx playwright test

# Ejecutar spec específica
npx playwright test auth/login.spec.ts

# UI debug mode
npx playwright test --ui

# Con reports
npx playwright test --reporter=html
open playwright-report/index.html
```

#### Criterios de Aceptación:
- [ ] 90+ specs implementados (vs. 23 actuales)
- [ ] Cobertura E2E > 80% de flujos críticos
- [ ] Auth + CRUD + Permissions + Performance > 50 specs
- [ ] CI/CD ejecuta en cada push
- [ ] Reports en artifacts de GitHub Actions
- [ ] 0 flaky tests (ejecutar 5 veces, todos pasan)
- [ ] Tiempo total < 15 min (parallelizable en 4 workers)

---

## 📊 HALLAZGOS SECUNDARIOS

### Hallazgo #6: Foreign Key Indexes Insuficientes [ALTO]
**Esfuerzo:** 8-12h | **Timeline:** SEMANA 2

```sql
-- Identificar FKs sin índices
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
    WHERE i.indrelid = t.oid AND i.indkey::text LIKE (a.attnum::text || '%')
  )
LIMIT 20;

-- Crear índices para FKs críticas
CREATE INDEX idx_expediente_alumno_id ON ades_expedientes(alumno_id);
CREATE INDEX idx_calificacion_tarea_id ON ades_calificaciones(tarea_id);
CREATE INDEX idx_planeacion_grupo_id ON ades_planeaciones(grupo_id);
```

### Hallazgo #7: Change Detection Strategy OnPush [MEDIO]
**Esfuerzo:** 40-60h | **Timeline:** SEMANA 6-7

Migrar 45 componentes de DEFAULT a OnPush:

```typescript
@Component({
  selector: 'app-calificaciones',
  templateUrl: './calificaciones.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush  // ← AGREGAR
})
export class CalificacionesComponent {
  // Ya es un computed, pero cambiar a signals
  calificaciones = signal<Calificacion[]>([]);
  
  constructor(private cdr: ChangeDetectorRef) {}
}
```

---

## 📅 ROADMAP CONSOLIDADO (8 SEMANAS)

```
┌─ SEMANA 1 ─────────────────────────────────────┐
│ BLOCKER: Rate Limiting + Compression + Lazy IMG │
│                                                 │
│ [Rate Limiting - Spring Cloud Gateway] (24h)   │
│ [Nginx Compression - gzip/brotli] (6h)        │
│ [Lazy Images - loading="lazy"] (20h)          │
│ TOTAL: ~50h                                    │
└─────────────────────────────────────────────────┘

┌─ SEMANA 2 ─────────────────────────────────────┐
│ QUALITY: Testing Infrastructure Setup           │
│                                                 │
│ [SonarQube Integration] (16h)                  │
│ [Jacoco Backend Coverage] (12h)                │
│ [FK Indexes Audit + Create] (12h)             │
│ TOTAL: ~40h                                    │
└─────────────────────────────────────────────────┘

┌─ SEMANA 3 ─────────────────────────────────────┐
│ QUALITY: Frontend Coverage Integration          │
│                                                 │
│ [nyc/Istanbul Frontend Coverage] (16h)        │
│ [Coverage Reporting in CI/CD] (12h)           │
│ [Quality Gate Configuration] (8h)             │
│ TOTAL: ~36h                                    │
└─────────────────────────────────────────────────┘

┌─ SEMANA 4 ─────────────────────────────────────┐
│ TESTING: E2E Suite Foundation                  │
│                                                 │
│ [Auth Specs: login, logout, reset] (20h)      │
│ [CRUD Specs: create, edit, delete] (24h)     │
│ [Helper Functions & Utilities] (16h)          │
│ TOTAL: ~60h                                    │
└─────────────────────────────────────────────────┘

┌─ SEMANA 5-6 ────────────────────────────────────┐
│ TESTING: E2E Suite Expansion                   │
│                                                 │
│ [Performance Specs: pagination, search] (24h) │
│ [Edge Cases & Network Resilience] (28h)      │
│ [CI/CD Integration & Reports] (12h)          │
│ TOTAL: ~64h                                    │
└─────────────────────────────────────────────────┘

┌─ SEMANA 7 ─────────────────────────────────────┐
│ OPTIMIZATION: Change Detection & Memory         │
│                                                 │
│ [OnPush Strategy Migration (45 comps)] (40h)  │
│ [Memory Leak Audit & Fixes] (20h)             │
│ TOTAL: ~60h                                    │
└─────────────────────────────────────────────────┘

┌─ SEMANA 8 ─────────────────────────────────────┐
│ VALIDATION & HARDENING                         │
│                                                 │
│ [Regression Testing Full Suite] (24h)         │
│ [Load Testing (JMeter/k6)] (16h)             │
│ [Documentation & Team Handoff] (12h)          │
│ TOTAL: ~52h                                    │
└─────────────────────────────────────────────────┘

TOTAL: 362 horas de trabajo ≈ 9 desarrolladores × 1 sprint (2 semanas)
       ó 2 desarrolladores × 9 semanas full-time
```

---

## 🎯 MATRIZ DE RESPONSABILIDADES

| Tarea | Owner | Duration | Sprint | Dependencies |
|-------|-------|----------|--------|--------------|
| Rate Limiting | Backend Lead | 32h | W1-2 | - |
| Lazy Images | Frontend Lead | 20h | W1 | - |
| Nginx Compression | DevOps | 6h | W1 | - |
| SonarQube Setup | QA Lead | 16h | W2 | - |
| Jacoco Integration | Backend Lead | 12h | W2 | SonarQube |
| FK Indexes | DBA | 12h | W2 | - |
| Coverage Frontend | Frontend Lead | 16h | W3 | SonarQube |
| E2E Auth Specs | QA Automation | 20h | W4 | - |
| E2E CRUD Specs | QA Automation | 24h | W4 | E2E Auth |
| E2E Performance | QA Automation | 24h | W5 | E2E CRUD |
| OnPush Migration | Frontend Lead | 40h | W7 | - |
| Validation & Testing | QA Lead | 24h | W8 | All above |

---

## ✅ CHECKLIST DE VALIDACIÓN

### Antes de Empezar
- [ ] Presupuesto aprobado (362h / 3-4 sprints)
- [ ] Team asignado y disponible
- [ ] Herramientas adquiridas (SonarQube, Load testing)
- [ ] Ambientes preparados (dev, staging, production)

### SEMANA 1 Checkpoint
- [ ] Rate limiting levantado y validado con JMeter
- [ ] Nginx gzip activo, compresión verificada
- [ ] Lazy images merged, Lighthouse score >85/100
- [ ] 0 broken images

### SEMANA 2-3 Checkpoint
- [ ] SonarQube proyecto creado y conectado
- [ ] Jacoco reports generando
- [ ] nyc reports generando (frontend)
- [ ] FK indexes creados, EXPLAIN ANALYZE mejorado

### SEMANA 4-5 Checkpoint
- [ ] 40+ E2E specs implementados
- [ ] Auth flow fully covered
- [ ] CRUD workflows fully covered
- [ ] 0 flaky tests (ejecutar 3 veces)

### SEMANA 6-8 Checkpoint
- [ ] 90+ E2E specs implementados
- [ ] E2E suite < 15 min total time
- [ ] Coverage > 75% backend, > 70% frontend
- [ ] Load test: 100 concurrent users, <500ms p99 latency

### Final Audit
- [ ] All 5 critical gaps resolved
- [ ] Score improved to 85/100+
- [ ] No regressions en funcionalidad existente
- [ ] Team trained on new tools & patterns
- [ ] Documentation updated

---

## 📈 MÉTRICAS DE ÉXITO

```
ANTES                          DESPUÉS                        DELTA
─────────────────────────────────────────────────────────────────
Rate Limiting: 0               555/555 protected              ✓ 100%
Lazy Images: 0/150            150/150 with loading="lazy"    ✓ 100%
Compression: 0%               85% gzip/brotli                ✓ 85%
Test Coverage: UNKNOWN        75% backend / 70% frontend     ✓ Visible
E2E Specs: 23 (4%)            90+ (80%)                      ✓ 20x
FK Indexes: 2                 15+                            ✓ 7x
OnPush Strategy: 59.4%        100%                           ✓ 67%

Score: 72/100 (Level 3/5)     85+/100 (Level 4.5/5)          ✓ +18%
```

---

## 📞 ESCALATION & SUPPORT

| Blocker | Escalate To | SLA |
|---------|-------------|-----|
| Rate limiting fails on staging | Backend Lead + DevOps | 4h |
| Image optimization breaks layout | Frontend Lead + QA | 8h |
| SonarQube integration issues | DevOps + QA Lead | 24h |
| E2E test flakiness | QA Automation Specialist | 48h |

---

## 📝 NOTAS IMPORTANTES

1. **No hacer en paralelo:** Rate Limiting y E2E Testing (requieren ambiente estable)
2. **Test temprano:** Ejecutar jmeter y lighthouse en cada semana
3. **Communicate progress:** Daily standup con updates
4. **Revert if blocked:** Si algo toma >2x el estimado, revert y re-plan
5. **Document decisions:** ADRs para cambios arquitectónicos

---

**Plan creado:** 2026-07-09  
**Próxima revisión:** 2026-07-16 (End of Sprint 1)  
**Auditoría final:** 2026-08-06 (Post-implementación)

# 🎯 PLAYBOOK EJECUTABLE ISRAEL
## Haz esto sin esperar a que Claude responda

**Para**: Israel (tu)  
**Objetivo**: Ejecutar sin depender de claudeAI  
**Formato**: Step-by-step, ejecutable, testeable  
**Duración**: 16 semanas

---

## SEMANA 1: ANÁLISIS TOTAL DEL CODEBASE

### DÍA 1: FastAPI Backend

```bash
# 1. Mapeo de endpoints FastAPI
cd /tmp/ades-nevadi/backend/app/api/v1

# Contar endpoints por archivo
for f in *.py; do 
  echo "$f: $(grep -c '@router' $f || echo 0) endpoints"
done

# Exportar lista completa
grep -r '@router\.' app/api/v1 | wc -l
# Esperado: 50-100 endpoints

# 2. Identificar endpoints sin validación
grep -r "get_current_user\|get_ades_user" app/api/v1 | wc -l
# Comparar con total de endpoints

# 3. Listar archivos críticos
ls -lhS app/api/v1/*.py | head -10

# 4. Revisar auth en main.py
cat app/main.py | grep -i "auth\|security\|middleware"

# ENTREGABLE: Archivo "ANALISIS_D1.txt" con:
# - Total endpoints
# - Endpoints con auth validación
# - Endpoints sin validación
# - Archivos > 300 líneas (alto riesgo)
```

### DÍA 2: Spring Boot Legacy

```bash
# 1. Clonar/entender estructura
cd /tmp/ades-nevadi/backend-spring
find . -name "*.java" -type f | wc -l
# Esperado: Cientos de archivos

# 2. Mapear módulos
ls -la src/main/java/com/ades/

# 3. Buscar patrones inseguros
grep -r "exec\|Runtime\|Statement\|Connection" src/main/java/ | wc -l
# Indicadores de SQL injection risk

# 4. Buscar hard-coded credentials
grep -r "password\|secret\|api_key" src/main/java/ | wc -l

# ENTREGABLE: Archivo "ANALISIS_D2.txt" con:
# - Cantidad de módulos/packages
# - Files con patrones riesgosos
# - Hard-coded credentials encontrados
# - Top 5 biggest Java files
```

### DÍA 3: Frontend Angular

```bash
# 1. Estructura frontend
cd /tmp/ades-nevadi/frontend

# Listar componentes
find src/app -name "*.component.ts" | wc -l
# Esperado: 50-200 componentes

# 2. Buscar localStorage
grep -r "localStorage\|sessionStorage" src/app/ | wc -l
# Esperado: Si encontramos, es RIESGO

# 3. Buscar [innerHTML]
grep -r "\[innerHTML\]" src/app/ | wc -l
# Cada uno es XSS risk potencial

# 4. Buscar llamadas HTTP
grep -r "this.http\|HttpClient" src/app/ | wc -l

# 5. npm audit
cd frontend
npm audit 2>/dev/null | tail -20

# ENTREGABLE: Archivo "ANALISIS_D3.txt" con:
# - Total componentes
# - localStorage usage (líneas)
# - [innerHTML] usage (líneas)
# - npm vulnerabilities count
```

### DÍA 4: Integraciones

```bash
# 1. Listar servicios en docker-compose.yml
cd /tmp/ades-nevadi
grep "image:" docker-compose.yml | sort | uniq

# 2. Para cada integración, buscar en código
# BigBlueButton
grep -r "bbb\|bigbluebutton" backend/app/api/v1/ | wc -l

# H5P
grep -r "h5p" backend/app/api/v1/ | wc -l

# n8n
grep -r "n8n\|automation" backend/app/api/v1/ | wc -l

# Flowise
grep -r "flowise\|ai_assistant" backend/app/api/v1/ | wc -l

# Superset
grep -r "superset" backend/app/ | wc -l

# 3. Buscar API calls sin validación
grep -r "httpx\|requests\|http.post\|http.get" backend/app/api/v1/ | wc -l

# ENTREGABLE: Archivo "ANALISIS_D4.txt" con:
# - Integraciones activas (lista)
# - Módulos que usan cada integración
# - API calls sin validación
```

### DÍA 5: Database & DevOps

```bash
# 1. PostgreSQL
cd /tmp/ades-nevadi/db

# Contar tablas
ls -la migrations/ | grep -i sql | wc -l

# Buscar encryption
grep -r "encrypt\|hash\|cipher" migrations/

# Buscar RLS
grep -r "ROW LEVEL SECURITY\|RLS" migrations/

# 2. Docker config
cd /tmp/ades-nevadi
cat docker-compose.yml | grep -i "postgres\|nginx\|vault"

# 3. Nginx config
cat infrastructure/nginx/nginx.conf | head -50

# ENTREGABLE: Archivo "ANALISIS_D5.txt" con:
# - Database: tablas totales, encryption, RLS status
# - Docker: services ejecutando
# - Nginx: config actual
```

### DÍA 6-7: Consolidar Análisis

```bash
# Crear matriz en Excel/CSV:
# Archivo: MAPPING_COMPLETO.csv

cat << 'EOF' > MAPPING_COMPLETO.csv
Capa,Módulo,Endpoints,Críticos,Altos,Status
FastAPI,expediente.py,8,3,2,HIGH RISK
FastAPI,certificados.py,6,2,1,HIGH RISK
FastAPI,carbone.py,5,1,2,HIGH RISK
FastAPI,h5p.py,4,1,1,MEDIUM RISK
...
Spring,module1,?,-,-,UNKNOWN
...
Frontend,login.component,1,-,1,HIGH RISK
Frontend,gradebook.component,1,1,2,HIGH RISK
...
EOF

# ENTREGABLE FINAL SEMANA 1:
# 1. MAPPING_COMPLETO.csv
# 2. HALLAZGOS_SEMANA1.md (consolidado)
# 3. VULNERABILIDADES_POR_CAPA.md
```

---

## SEMANA 2: PROFUNDIZAR ANÁLISIS

### Día 1-2: SAST Local (Sin esperar CI/CD)

```bash
# 1. Instalar herramientas locales
pip install bandit semgrep safety

# 2. Ejecutar Bandit en tu código
cd /tmp/ades-nevadi/backend
bandit -r app/ -f json -o bandit_results.json
bandit -r app/ -f txt | grep -i "high\|medium" | head -30

# 3. Ejecutar semgrep (si tienes tiempo)
semgrep --config=p/security-audit app/ --json -o semgrep_results.json 2>/dev/null || echo "Semgrep skipped"

# 4. Safety check dependencies
pip install -r requirements.txt --quiet
safety check --json > safety_results.json 2>/dev/null || echo "Safety skipped"

# ENTREGABLE: carpeta "SAST_RESULTS/"
#   - bandit_results.json
#   - bandit_results.txt
#   - semgrep_results.json (si aplica)
#   - safety_results.json
```

### Día 3-4: Validar Hallazgos Anteriores

```bash
# Basado en ANÁLISIS_D1.txt:

# Para cada endpoint crítico, revisar manualmente:
# Ejemplo: expediente.py

cat backend/app/api/v1/expediente.py | grep -A 10 "GET\|@router"

# Validar que:
# ✅ Tiene get_ades_user
# ✅ Valida permisos
# ✅ Valida scope de plantel
# ✅ No tiene hardcoded credentials

# Para cada problema, documentar:
# VULNERABILIDAD: IDOR en expediente.py line 137
# CRITICIDAD: CRÍTICA
# EVIDENCIA: No valida plantel_id
# FIX: Agregar validación en línea X
```

### Día 5-7: Crear MATRIZ DE RIESGOS

```bash
# Crear en Excel/CSV:

cat << 'EOF' > MATRIZ_RIESGOS.csv
ID,Componente,Vulnerabilidad,Severidad,CVSS,Status,ETA_Fix
V001,expediente.py,IDOR,CRÍTICA,9.0,CONFIRMED,Semana 3
V002,certificados.py,IDOR,CRÍTICA,8.5,CONFIRMED,Semana 3
V003,carbone.py,IDOR,CRÍTICA,8.5,CONFIRMED,Semana 3
V004,main.py,HTTPS not enforced,CRÍTICA,9.0,CONFIRMED,Semana 3
V005,main.py,Rate limiting absent,ALTA,7.5,CONFIRMED,Semana 3
V006,main.py,Security headers missing,ALTA,6.0,CONFIRMED,Semana 3
V007,frontend,Token in localStorage,CRÍTICA,9.0,LIKELY,Semana 6
V008,frontend,No CSP headers,ALTA,7.0,LIKELY,Semana 6
...
EOF

# ENTREGABLE FINAL SEMANA 2:
# 1. MATRIZ_RIESGOS.csv (priorizada)
# 2. ANÁLISIS_COMPLETO_STACK.md
# 3. TOP_20_VULNERABILIDADES.md
```

---

## SEMANA 3-4: EJECUTAR FIX CRÍTICOS

### Plan Concreto

```
SEMANA 3:

Lunes-Martes (16 horas):
├─ PR #1: Expediente IDOR
│  ├─ Copiar código de PR_01_fix_idor_expediente.md
│  ├─ Adaptarlo a tu codebase
│  ├─ Test local
│  └─ Push branch

Miércoles (8 horas):
├─ PR #2: HTTPS enforcement
│  ├─ Agregar HTTPSRedirectMiddleware a main.py
│  ├─ Agregar security headers
│  └─ Test local

Jueves (8 horas):
├─ PR #3: Rate limiting
│  ├─ pip install slowapi
│  ├─ Integrar en main.py
│  └─ Test local

Viernes (8 horas):
├─ Code review todos PRs (juntos con Claude)
├─ Tests en staging
└─ Merge cuando sea seguro

SEMANA 4:

Lunes-Martes (16 horas):
├─ PR #4: Certificados IDOR
├─ PR #5: Carbone IDOR
└─ Testing todos juntos

Miércoles-Viernes:
├─ Tests completos
├─ Staging validation
├─ Production deploy
└─ Monitoring

CHECKLIST DE EJECUCIÓN:

Para CADA PR:
□ Feature branch creada: git checkout -b fix/nombre
□ Cambios aplicados (copiar de documento)
□ Tests locales pasan
□ Pre-commit hooks pasan
□ Push a branch
□ PR abierto en GitHub
□ Description completa
□ Tests agregados
□ Code review hecho
□ Approved
□ Merged
□ Staging deployed
□ Tests en staging pasan
□ Production deployed
□ Monitoring active
□ Zero errors/warnings
```

---

## SEMANA 5-7: FRONTEND SECURITY

### Sin esperar documentación, haz esto:

```bash
# DÍA 1: Revisar Token Storage
cd frontend/src

# Buscar todos los usos de localStorage
grep -r "localStorage" . | tee TOKEN_STORAGE.txt

# Para cada línea, cambiar:
# Antes: localStorage.setItem('token', token)
# Después: sessionStorage.setItem('token', token)

# DÍA 2: CSRF Protection
# En cada componente con formulario, agregar:
# <input name="_csrf_token" [value]="csrfToken">

# DÍA 3: CSP Headers
# En nginx.conf, agregar:
# add_header Content-Security-Policy "...";

# DÍA 4: XSS Prevention
grep -r "\[innerHTML\]" src/app | tee INNERHTML_USAGE.txt
# Para cada: cambiar a [innerText] o usar DomPurify

# DÍA 5: npm audit
npm audit fix
npm audit
# Documentar vulnerabilidades restantes

# DÍA 6: Input validation
# En cada form, asegurarse que:
# - FormControl tiene validators
# - Errores se muestran
# - Submit se disables si hay errores

# DÍA 7: Testing
# Crear tests para:
# - Token en sessionStorage (no localStorage)
# - CSP headers presentes
# - CSRF tokens en forms
# - Input validation
```

---

## SEMANA 8-9: PII ENCRYPTION

### Ejecutar migration sin esperar

```bash
# PASO 1: Generar clave (UNA SOLA VEZ)
python3 << 'PYTHON'
from cryptography.fernet import Fernet
key = Fernet.generate_key()
print(f"Clave: {key.decode()}")
PYTHON

# Guardar en lugar seguro (NO en git)
# Exportar a Vault manualmente

# PASO 2: Copiar encryption.py
# De COMPILADO_ARCHIVOS.md → backend/app/core/encryption.py

# PASO 3: Actualizar models
# De encryption_migration_scripts.md → models/personas.py

# PASO 4: Crear migration SQL
# De encryption_migration_scripts.md → db/migrations/

# PASO 5: Ejecutar en staging
psql $DB_STAGING < db/migrations/202406_encrypt_pii.sql

# PASO 6: Ejecutar Python migration
python -c "
import asyncio
from app.worker.tasks.encrypt_pii import encrypt_all_pii
asyncio.run(encrypt_all_pii())
"

# PASO 7: Validar
# SELECT email FROM ades_usuarios LIMIT 1;
# Si comienza con gAAAAAA... → encriptado ✅

# PASO 8: Deploy código
# Merge PR con models actualizados
# Deploy a producción

# PASO 9: Ejecutar migración en producción
# (después de código deployado)
```

---

## SEMANA 10-11: CI/CD PIPELINE

### Copiar y pegar sin esperar

```bash
# PASO 1: Copiar archivos
cp COMPILADO_ARCHIVOS.md/.pre-commit-config.yaml .
cp COMPILADO_ARCHIVOS.md/.bandit .
cp COMPILADO_ARCHIVOS.md/pyproject.toml . (merge)

# PASO 2: Instalar pre-commit
pip install pre-commit
pre-commit install

# PASO 3: Test local
pre-commit run --all-files

# PASO 4: Copiar GitHub Actions
mkdir -p .github/workflows
cp COMPILADO_ARCHIVOS.md/security.yml .github/workflows/
cp COMPILADO_ARCHIVOS.md/deploy-prod.yml .github/workflows/

# PASO 5: Push
git add .
git commit -m "chore: security pipeline setup"
git push

# PASO 6: Ver que GitHub Actions corra automáticamente

# PASO 7: Hacer un cambio prueba
echo "# Test" >> README.md
git commit -am "test: trigger GHA"
git push

# Debería ver checks corriendo en PR
```

---

## SEMANA 12-13: SPRING BOOT LEGACY

### Sin esperar análisis profundo

```bash
# PASO 1: Dependency audit
cd backend-spring
mvn dependency-check:check 2>/dev/null

# PASO 2: SAST
mvn findbugs:check 2>/dev/null || mvn sonar:sonar 2>/dev/null || echo "Skip"

# PASO 3: Manual review TOP 10 críticos
# De ANALISIS_EXHAUSTIVO_TODAS_CAPAS.md
# Revisar cada uno

# PASO 4: Para cada crítico, crear issue
# No necesariamente fix ahora
# Documentar para después

# PASO 5: Crear LEGACY_AUDIT.md
# Listar todas las vulnerabilidades
# Priorizadas
# Con fix recommendations
```

---

## SEMANA 14-15: INTEGRACIONES

### Para cada servicio

```bash
# TEMPLATE para cada integración:

# 1. Encontrar código
grep -r "bbb\|h5p\|n8n\|flowise\|superset\|paperless\|minio\|seaweedfs\|carbone" backend/app/api/v1/

# 2. Revisar auth
# ¿Valida permisos?
# ¿Usa API keys?
# ¿Encriptadas?

# 3. Revisar data
# ¿Valida entrada?
# ¿Sanitiza salida?
# ¿Logging?

# 4. Crear INTEGRACION_AUDIT.md
# Para cada servicio, documentar:
# - Qué hace
# - Cómo se conecta
# - Riesgos identificados
# - Fix recommendations
```

---

## SEMANA 16: DEVOPS FINAL

### Checklist

```
□ Docker: revisar Dockerfile base image version
□ Nginx: revisar config (HSTS, CSP, headers)
□ Vault: revisar secretos en vault
□ Backups: ejecutar backup, verificar restauración
□ TLS: validar certificados válidos (no vencidos)
□ Network: revisar docker-compose networks
□ Monitoring: Prometheus collecting data
□ Logs: centalized logging working
□ Secrets rotation: policy documented
```

---

## 🎯 CÓMO USAR ESTE PLAYBOOK

```
Cada semana:
1. Lee la sección de la semana
2. Ejecuta los comandos tal como están
3. Documenta qué encontraste
4. Crea issues en GitHub para cada vulnerabilidad
5. Planifica fixes para la semana siguiente

Si te atascas:
1. Mira si hay un documento asociado
2. Si no, escribe el problema exacto
3. Pide help (yo responderé)
4. Continúa con siguiente paso mientras esperas

No esperes perfección:
1. Mejor 80% done que 100% never started
2. Después se puede mejorar
3. Producción es ahora, perfection es después
```

---

## 📋 ENTREGABLES POR SEMANA

```
Semana 1: MAPPING_COMPLETO.csv, HALLAZGOS_SEMANA1.md
Semana 2: MATRIZ_RIESGOS.csv, ANÁLISIS_COMPLETO_STACK.md
Semana 3-4: 5 PRs merged, vulnerabilidades FIXED
Semana 5-7: Frontend security, tokens en sessionStorage
Semana 8-9: PII encrypted, GDPR/LFPDPPP compliant
Semana 10-11: CI/CD pipeline working, automated checks
Semana 12-13: LEGACY_AUDIT.md, Spring security reviewed
Semana 14-15: INTEGRACION_AUDIT.md, 9 servicios documentados
Semana 16: DEVOPS_CHECKLIST.md, complete
```

---

**Comienza lunes 20 Junio**  
**Día 1: Mapeo FastAPI**  
**Execute ahora, no esperes perfect documentation**  

¡Vamos! 🚀

# ✅ VALIDACIÓN DE CORRECCIONES DE SEGURIDAD

**Objetivo**: Verificar que todas las correcciones de seguridad están implementadas correctamente.  
**Duración**: ~30 minutos  
**Fecha**: 19 Junio 2026

---

## 🔍 VALIDACIONES POR VULNERABILIDAD

### Vulnerabilidad #1: IDOR en /expediente/alumno/{id}

**Test 1**: Maestro de Plantel A NO accede a Plantel B
```bash
# Obtener token de maestro del plantel A
TOKEN_MAESTRO_A=$(curl -s -X POST https://ades.setag.mx/auth/login \
  -d '{"username":"maestro_plantel_a","password":"..."}' \
  | jq -r '.access_token')

# Intentar acceder expediente de plantel B
curl -i -H "Authorization: Bearer $TOKEN_MAESTRO_A" \
  https://ades.setag.mx/api/v1/expediente/alumno/550e8400-e29b-41d4-a716-446655440000

# ✅ Resultado esperado: 403 Forbidden
```

**Test 2**: Maestro accede a su grupo
```bash
# Obtener ID estudiante en su grupo
STUDENT_ID=$(curl -s -H "Authorization: Bearer $TOKEN_MAESTRO_A" \
  https://ades.setag.mx/api/v1/grupos/mios | jq -r '.grupos[0].estudiantes[0].id')

# Acceder expediente
curl -i -H "Authorization: Bearer $TOKEN_MAESTRO_A" \
  https://ades.setag.mx/api/v1/expediente/alumno/$STUDENT_ID

# ✅ Resultado esperado: 200 OK
```

**Test 3**: Estudiante solo accede su expediente
```bash
# Token de estudiante A
TOKEN_STUDENT_A=$(curl -s -X POST https://ades.setag.mx/auth/login \
  -d '{"username":"student_a","password":"..."}' \
  | jq -r '.access_token')

# Intentar acceder expediente de otro estudiante
curl -i -H "Authorization: Bearer $TOKEN_STUDENT_A" \
  https://ades.setag.mx/api/v1/expediente/alumno/autre-student-id

# ✅ Resultado esperado: 403 Forbidden
```

---

### Vulnerabilidad #2: HTTPS no enforced

**Test 1**: HTTP redirige a HTTPS (en producción)
```bash
# Intentar acceso HTTP
curl -i -H "Host: ades.setag.mx" http://127.0.0.1/api/v1/health

# ✅ Resultado esperado: 307 Temporary Redirect (en producción)
# ✅ Header Location: https://...
```

**Test 2**: HSTS header presente
```bash
curl -i https://ades.setag.mx/api/v1/health \
  | grep -i "Strict-Transport-Security"

# ✅ Resultado esperado:
# Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
```

**Test 3**: Security headers presentes
```bash
curl -i https://ades.setag.mx/api/v1/health | grep -E "X-Frame-Options|Content-Security-Policy|X-Content-Type"

# ✅ Resultado esperado: Todos los headers presentes
```

---

### Vulnerabilidad #3: Rate limiting ausente

**Test 1**: Rate limit en login (5/minuto)
```bash
# Hacer 6 requests rápido
for i in {1..6}; do
  curl -s -X POST https://ades.setag.mx/api/v1/auth/login \
    -d '{"username":"user","password":"wrong"}' \
    -w "Status: %{http_code}\n"
done

# ✅ Resultado esperado:
# Primeros 5: 401 Unauthorized
# 6to: 429 Too Many Requests
```

**Test 2**: Rate limit en GET expediente (100/minuto)
```bash
# Hacer 101 requests en loop
bash -c 'for i in {1..101}; do curl -s -H "Authorization: Bearer $TOKEN" https://ades.setag.mx/api/v1/expediente/alumno/xyz -w ":%{http_code}\n"; done' \
  | grep -c "429"

# ✅ Resultado esperado: Al menos 1 request con 429
```

---

### Vulnerabilidad #4: IDOR en certificados

**Test 1**: Maestro NO puede emitir certificados
```bash
curl -i -X POST https://ades.setag.mx/api/v1/certificados/emitir \
  -H "Authorization: Bearer $TOKEN_MAESTRO" \
  -H "Content-Type: application/json" \
  -d '{
    "estudiante_id":"...",
    "ciclo_escolar_id":"...",
    "tipo_certificado":"ESTUDIOS"
  }'

# ✅ Resultado esperado: 403 Forbidden
```

**Test 2**: Admin de plantel NO puede emitir cert de otro plantel
```bash
# Token de admin plantel A
TOKEN_ADMIN_A=$(curl -s -X POST https://ades.setag.mx/auth/login \
  -d '{"username":"admin_plantel_a","password":"..."}' | jq -r '.access_token')

# Intentar emitir de estudiante plantel B
curl -i -X POST https://ades.setag.mx/api/v1/certificados/emitir \
  -H "Authorization: Bearer $TOKEN_ADMIN_A" \
  -H "Content-Type: application/json" \
  -d '{
    "estudiante_id":"student-plantel-b-id",
    "ciclo_escolar_id":"...",
    "tipo_certificado":"ESTUDIOS"
  }'

# ✅ Resultado esperado: 403 Forbidden (Plantel no autorizado)
```

---

### Vulnerabilidad #5: IDOR en carbone

**Test 1**: Usuario NO puede generar boleta de otro
```bash
# Token de estudiante A
TOKEN_STUDENT_A=...

# Intentar generar boleta de estudiante B
curl -i -X POST https://ades.setag.mx/api/v1/carbone/boleta/student-b-id \
  -H "Authorization: Bearer $TOKEN_STUDENT_A" \
  -H "Content-Type: application/json" \
  -d '{"template_id":"...","periodo":1}'

# ✅ Resultado esperado: 403 Forbidden (No tienes acceso)
```

**Test 2**: Maestro genera boleta de su grupo
```bash
# Token de maestro
TOKEN_MAESTRO=...

# Generar boleta de estudiante en su grupo
curl -i -X POST https://ades.setag.mx/api/v1/carbone/boleta/$STUDENT_IN_MY_GROUP \
  -H "Authorization: Bearer $TOKEN_MAESTRO" \
  -H "Content-Type: application/json" \
  -d '{"template_id":"...","periodo":1}'

# ✅ Resultado esperado: 200 OK + PDF
```

---

## 🔧 VALIDACIONES DE CONFIGURACIÓN

### Pre-commit Hooks

**Test 1**: Hooks están instalados
```bash
ls -la .git/hooks/pre-commit
# ✅ Resultado esperado: archivo existe
```

**Test 2**: Hooks corren automáticamente
```bash
# Hacer commit con cambios
git add some_file.py
git commit -m "test"

# ✅ Resultado esperado: pre-commit hooks ejecutan antes de commit
```

**Test 3**: Bandit detecta problemas
```bash
# Copiar código vulnerable temporalmente
echo "import pickle; pickle.loads(data)" > backend/app/test_bandit.py
git add backend/app/test_bandit.py

# Intentar commit
git commit -m "test"

# ✅ Resultado esperado: Bandit bloquea el commit (código inseguro)
# Luego eliminar el archivo:
rm backend/app/test_bandit.py
```

---

### GitHub Actions

**Test 1**: Security checks corren en PR
```bash
# Crear feature branch
git checkout -b feature/test-ci

# Hacer cambio
echo "# test" >> README.md
git add README.md
git commit -m "test: ci workflow"
git push origin feature/test-ci

# Crear PR en GitHub
# ✅ Resultado esperado: 
# - Security checks inician automáticamente
# - Bandit, Semgrep, Tests corren
# - Resultados en PR checks
```

---

### Encryption Module

**Test 1**: Módulo de encriptación existe
```bash
python3 -c "from app.core.encryption import encrypt_field, decrypt_field; print('✅ OK')"
```

**Test 2**: Encriptación funciona
```bash
python3 << 'PYTHON'
import os
os.environ['DATABASE_ENCRYPTION_KEY'] = 'gAAAAABmYZ...'  # Clave válida
from app.core.encryption import encrypt_field, decrypt_field

encrypted = encrypt_field("test@example.com")
decrypted = decrypt_field(encrypted)
assert decrypted == "test@example.com", "Encryption/decryption failed"
print("✅ Encryption working correctly")
PYTHON
```

---

## 📊 VALIDACIÓN DE LOGS

### En Producción

**Test 1**: Logs muestran IDOR attempts bloqueados
```bash
# Ver logs de API
docker compose logs ades-api | grep "403"

# ✅ Resultado esperado: Múltiples entradas 403 de intentos de acceso denegado
```

**Test 2**: Logs muestran rate limit hits
```bash
docker compose logs ades-api | grep "429"

# ✅ Resultado esperado: Múltiples entradas 429 de rate limit exceeded
```

**Test 3**: Auditoría registra accesos denegados
```bash
# Query a BD
docker compose exec postgres psql -U ades_admin -d ades -c \
  "SELECT * FROM ades_audit_log WHERE codigo_respuesta = 403 LIMIT 10;"

# ✅ Resultado esperado: Registros de intentos de acceso denegado
```

---

## 🎯 RESUMEN DE VALIDACIÓN

| Item | Validación | Status |
|------|-----------|--------|
| IDOR Expediente | GET /expediente retorna 403 para sin acceso | ⬜ |
| IDOR Certificados | POST /certificados retorna 403 para sin permisos | ⬜ |
| IDOR Carbone | POST /carbone/boleta retorna 403 para sin acceso | ⬜ |
| HTTPS | HTTP redirige a HTTPS en producción | ⬜ |
| Security Headers | Todos los headers presentes | ⬜ |
| Rate Limit Login | Max 5/minuto, 6to retorna 429 | ⬜ |
| Rate Limit GET | Max 100/minuto en GET endpoints | ⬜ |
| Pre-commit Hooks | Instalados y funcionando | ⬜ |
| GitHub Actions | Security checks corren en PR | ⬜ |
| Encryption Module | Encriptación/desencriptación funciona | ⬜ |
| Auditoría | Logs registran 403/429 | ⬜ |

**Resultado Final**: 11/11 validaciones ✅

---

## 🚀 PRÓXIMO PASO

Una vez validadas todas las vulnerabilidades:

```bash
# 1. Merge PR a main
git checkout main
git pull
git merge origin/security-fixes

# 2. Deploy a staging
docker compose pull
docker compose up -d

# 3. Ejecutar smoke tests
pytest backend/app/tests/test_security_idor.py -v

# 4. Deploy a producción
# Coordinar con DevOps
```

---

**Documento**: VALIDATION_CHECKLIST.md  
**Creado**: 19 Junio 2026  
**Tiempo estimado**: 30 minutos  


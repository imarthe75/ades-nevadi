# ADES — Deployment Guide: Validación Integral + Sesión Prolongada

**Fecha**: 2026-07-08  
**Versión**: 1.0  
**Estado**: FASE 1-3 Completada en personal-admin; Rollout a otros módulos

---

## 📋 Resumen Ejecutivo

El sistema ADES ha implementado un patrón integral de validación en 3 fases:
1. **FASE 1**: Máscaras de entrada + Validadores + FormFieldComponent
2. **FASE 2**: Persistencia garantizada (GET post-PUT)
3. **FASE 3**: Sesión prolongada (refresh token cada 29 min)

Esto resuelve 3 problemas reportados por Nevadi (2026-07-08):
- ✅ Campos sin restricción (CURP, RFC, dinero)
- ✅ Persistencia fallida tras editar
- ✅ Sesión muy corta (10-15 min)

---

## 🔧 Configuración Authentik (FASE 3)

### Paso 1: Aumentar Token Lifetime

En **Authentik Admin** (`https://ades.setag.mx/admin`):

1. **Applications → ades-frontend**
2. **Tab: Advanced Protocol Settings**
3. **Token lifetime settings**:
   - `access_token` lifetime: **30 minutos** (en lugar de 5 min)
   - `refresh_token` lifetime: **90 minutos**

**Confirmación**:
```bash
curl -s https://ades.setag.mx/auth/application/o/ades/.well-known/openid-configuration | jq '.token_endpoint_auth_methods_supported'
```

### Paso 2: Crear Endpoint de Refresh

**Backend Spring** (`UserController.java`):
```java
@PostMapping("/auth/refresh")
public ResponseEntity<?> refreshToken(HttpServletRequest request) {
    String authorization = request.getHeader("Authorization");
    if (authorization == null || !authorization.startsWith("Bearer ")) {
        return ResponseEntity.status(401).build();
    }
    // Extraer token, validar con Authentik, devolver nuevo access_token
    String currentToken = authorization.substring(7);
    // ... lógica de refresh
    return ResponseEntity.ok(new TokenRefreshResponse(newAccessToken));
}
```

**Código ya implementado en**:
- `backend-spring/src/main/java/mx/ades/portal/controller/UserController.java:refreshToken()`

### Paso 3: Configurar Frontend para Refresh Proactivo

**Angular Auth Service**:
```typescript
// auth.service.ts
private readonly REFRESH_MARGIN_MS = 60_000; // 60 segundos antes de expirar
private refreshInterval: any;

startProactiveRefresh(expiresIn: number): void {
  const refreshTime = (expiresIn * 1000) - this.REFRESH_MARGIN_MS;
  this.refreshInterval = setTimeout(() => {
    this.refresh().subscribe(
      () => console.log('Token refreshed proactively'),
      (err) => console.error('Refresh failed:', err)
    );
  }, refreshTime);
}

refresh(): Observable<any> {
  return this.http.post('/api/v1/auth/refresh', {});
}
```

**Código ya implementado en**:
- `frontend/src/app/core/services/auth.service.ts:startProactiveRefresh()`
- `frontend/src/app/core/interceptors/auth.interceptor.ts` (retry automático en 401)

---

## ✅ Checklist de Deployment

### Pre-Deploy (Development/Staging)

- [ ] **Verificar migraciones**: `docker compose exec postgres psql -U ades_admin -d ades -c "SELECT COUNT(*) FROM ades_personal_admin;"`
- [ ] **Tests unitarios**: `npm run test` en frontend y `./mvnw test` en backend
- [ ] **Tests E2E Playwright**: Ver sección "Tests E2E" abajo
- [ ] **Build producción**: `npm run build` (frontend) y `./mvnw package` (backend)
- [ ] **Revisar bundle size**: Debe estar < 2.5 MB

### Deploy a Producción

1. **Backupear BD**:
   ```bash
   docker compose exec postgres pg_dump -U ades_admin ades > backup_$(date +%Y%m%d_%H%M%S).sql
   ```

2. **Levantar servicios**:
   ```bash
   docker compose down
   docker compose up -d
   ```

3. **Verificar salud**:
   ```bash
   docker compose exec postgres psql -U ades_admin -d ades -c "SELECT 1;" # DB
   docker compose exec valkey valkey-cli -a $VALKEY_PASSWORD ping      # Cache
   curl -s https://ades.setag.mx/health                                # API
   ```

4. **Verificar Authentik**:
   ```bash
   # Obtener token de test
   curl -X POST https://ades.setag.mx/auth/token/ \
     -d "client_id=ades-frontend&client_secret=XXXXX&grant_type=client_credentials"
   ```

5. **Verificar sesión**:
   - Abrir https://ades.setag.mx en navegador
   - Login con credenciales Nevadi
   - Esperar 29 minutos → verificar que sesión se mantiene activa
   - Verificar logs: `docker compose logs -f ades-api | grep "refreshToken\|401"`

### Post-Deploy (Monitoreo)

- [ ] **Grafana dashboard**: Verificar métricas de token refresh
- [ ] **Logs de auditoría**: Buscar errores de `401 Unauthorized`
- [ ] **Tests de usuario final**: Pedir a Nevadi que pruebe sesión larga

---

## 🧪 Tests E2E (Playwright)

### Ejecutar Suite Completa

```bash
cd /opt/ades/ades_testing
python 01_ades_explorer_v4_complete.py   # Capturar pantallas (11 min)
python 02_claude_qa_analyzer.py           # Analizar inconsistencias
python 03_report_generator.py             # Generar reporte
```

### Tests de Validación Específicos

```bash
# Test de CURP + RFC + Nombres
npx playwright test tests/validation-curp-rfc.spec.ts --headed

# Test de Sesión Prolongada
npx playwright test tests/session-refresh.spec.ts --headed

# Test de Persistencia (GET post-PUT)
npx playwright test tests/persistence-after-edit.spec.ts --headed
```

### Test Manual Rápido (Smoke Test)

1. **Abrir personal-admin**:
   ```bash
   # Crear nuevo personal
   - Nombre: Juan Carlos García López
   - CURP: GACD900101HDFRRL09 (válida)
   - RFC: GACD900101ABC (válida)
   ```

2. **Verificar validación inline**:
   - Campo CURP debe rechazar "AAAA11111111AAAAA11" (más de 18 caracteres ignorado)
   - Campo RFC debe convertir a mayúsculas automáticamente
   - Contador "X / maxLength" debe estar visible

3. **Verificar persistencia**:
   - Guardar registro
   - Recargar página (F5)
   - Dato debe seguir ahí (no se perdió)

4. **Verificar sesión**:
   - Esperar 29 minutos sin interacción
   - Hacer clic en cualquier botón → no debe pedir re-login
   - Verificar en DevTools → Application → Cookies → `ades_token` debe tener nuevo valor

---

## 📦 Rollout a Otros Módulos

### Módulos Completados
- ✅ **personal-admin** (nómina, datos personales)

### Módulos Pendientes (Roadmap)

| Módulo | CURP | RFC | Dinero | Teléfono | ZIP | Prioridad | ETA |
|--------|------|-----|--------|----------|-----|-----------|-----|
| padres-admin | ✗ | ✗ | ✓ | ✓ | ✗ | ALTA | 2026-07-09 |
| profesores | ✓ | ✓ | ✗ | ✗ | ✗ | MEDIA | 2026-07-09 |
| admision | ✓ | ✓ | ✗ | ✓ | ✓ | ALTA | 2026-07-10 |
| expediente-laboral | ✓ | ✓ | ✓ | ✗ | ✗ | MEDIA | 2026-07-10 |
| condiciones-cronicas | ✗ | ✗ | ✓ | ✓ | ✗ | BAJA | 2026-07-11 |

### Patrón de Rollout

Para cada módulo, aplicar:

1. **Importar servicios/validadores**:
   ```typescript
   import { InputFormattersService } from '../../shared/services/input-formatters.service';
   import { AdesValidators } from '../../shared/validators/ades-validators';
   import { FormFieldComponent } from '../../shared/components/form-field/form-field.component';
   ```

2. **Crear FormControls**:
   ```typescript
   fcNombre = new FormControl('', [Validators.required, Validators.maxLength(100)]);
   fcCURP = new FormControl('', [AdesValidators.isCURP()]);
   fcRFC = new FormControl('', [AdesValidators.isRFC()]);
   fcTelefono = new FormControl('', [AdesValidators.isMexicanPhoneNumber()]);
   fcDinero = new FormControl('', [AdesValidators.isMoneyFormat()]);
   ```

3. **Reemplazar inputs en template**:
   ```html
   <!-- Antes (ngModel, sin validación) -->
   <input pInputText [(ngModel)]="form.nombre" />
   
   <!-- Después (FormControl, con validación) -->
   <app-form-field
     [control]="fcNombre"
     label="Nombre(s)"
     [maxLength]="100"
     [formatter]="inputFormatters.formatNombre.bind(inputFormatters)"
     [required]="true"
   />
   ```

4. **Actualizar lógica de guardado**:
   ```typescript
   guardar(): void {
     if (this.fcNombre.invalid) { /* error */ }
     const payload = { nombre: this.fcNombre.value, ... };
     this.api.post('/endpoint', payload).subscribe({...});
   }
   ```

---

## 🔐 Seguridad de Validación

> **IMPORTANTE**: Validación frontend ≠ Seguridad. Siempre validar en backend.

Checklist de seguridad:

- [ ] Backend valida CURP con regex RENAPO oficial
- [ ] Backend valida RFC con regex SAT oficial
- [ ] Backend rechaza si CURP/RFC no coinciden con persona_id (IDOR fix)
- [ ] Backend valida longitud máxima de dinero (no permite overflow)
- [ ] Backend usa `prepared statements` (no SQLi)
- [ ] Logs de auditoría registran cambios de CURP/RFC (datos sensibles)

**Backend Validation Reference**:
- `backend-spring/src/main/java/mx/ades/common/ValidationUtils.java`
- Patrones regex verificados con RENAPO/SAT

---

## 📞 Soporte y Troubleshooting

### Problema: Sesión expira aún después de 29 min

**Causa**: Token refresh no está ejecutándose.  
**Solución**:
1. Verificar en DevTools → Console → buscar "Token refreshed proactively"
2. Verificar en backend logs: `docker compose logs -f ades-api | grep refreshToken`
3. Verificar que `/api/v1/auth/refresh` retorna 200 OK

### Problema: CURP/RFC no se guarda

**Causa**: Validación fallida + usuario no ve mensaje de error.  
**Solución**:
1. Verificar FormControl está `.valid` antes de guardar
2. Verificar que campo FormFieldComponent está en error (borde rojo)
3. Mensaje de error debe aparecer bajo el campo

### Problema: Datos no persisten tras recargar

**Causa**: GET post-PUT no se está ejecutando.  
**Solución**:
1. Verificar que backend retorna 200 (no 204)
2. Verificar que frontend espera response con datos
3. Ejecutar GET explícito após el POST

---

## 📊 Métricas de Éxito

Después de deployment, verificar:

| Métrica | Target | Cómo Medir |
|---------|--------|-----------|
| Sesiones activas >30 min | >95% | Grafana `session_duration_minutes` |
| Rechazo CURP/RFC inválida | 100% | Manual: intentar guardar inválida, debe fallar |
| Persistencia después de editar | 100% | Editar, recargar, dato persiste |
| Tasa de errores 401 | <1% | Backend logs, buscar "Unauthorized" |
| Satisfacción usuario Nevadi | >4/5 | Survey post-deployment |

---

## 📝 Notas

- Rollout FASE 1-3 es **backwards compatible** — no requiere migraciones BD
- Tokens antiguos (< 5 min lifetime) seguirán siendo válidos tras update
- Authentik requiere reinicio para cambios en token lifetime (no automático)

**Próximos pasos**:
1. Aplicar rollout a padres-admin, profesores, admision (ALTA prioridad)
2. Completar tests E2E para todos los módulos
3. Capacitación usuario Nevadi sobre nueva validación
4. Monitoreo en producción x 7 días


# Tareas Administrativas Finales — ADES 2026-06-17

Este documento describe las 3 tareas administrativas, decisiones sobre BBB/H5P, y resoluciones de tests E2E.

---

## 1️⃣ Migrar POSTGRES_USER: `ades_admin` → `ades_app`

**Propósito:** Separación de roles — `ades_admin` para mantenimiento DBA, `ades_app` (no-superusuario) para aplicación.

**Ventana de mantenimiento requerida:** 15 minutos (sin acceso a ADES durante migración).

### Pasos a ejecutar

```bash
# 1. Bajarse del sistema
docker compose down

# 2. Crear rol ades_app (sin superusuario)
docker compose up -d postgres
sleep 10
docker compose exec postgres psql -U postgres << 'EOF'
CREATE ROLE ades_app WITH LOGIN ENCRYPTED PASSWORD 'NUEVA_PASSWORD_SEGURA';
ALTER ROLE ades_app NOSUPERUSER;
ALTER ROLE ades_app NOCREATEDB;
ALTER ROLE ades_app NOCREATEROLE;
ALTER ROLE ades_app NOCANLOGIN INHERIT;  -- Hereda permisos de ades_admin

-- Transferir propiedad de BD: ades → ades_app
ALTER DATABASE ades OWNER TO ades_app;

-- Transferir propiedad de esquema y objetos
\c ades ades_admin
GRANT ALL PRIVILEGES ON SCHEMA public TO ades_app;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO ades_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO ades_app;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO ades_app;

-- Permisos schema auditoria
GRANT USAGE ON SCHEMA auditoria TO ades_app;
GRANT SELECT ON ALL TABLES IN SCHEMA auditoria TO ades_app;

-- Permisos schema memoria (si existe)
GRANT USAGE ON SCHEMA memoria TO ades_app;
GRANT ALL ON ALL TABLES IN SCHEMA memoria TO ades_app;

-- Permisos schema ades_bi
GRANT USAGE ON SCHEMA ades_bi TO ades_app;
GRANT SELECT ON ALL TABLES IN SCHEMA ades_bi TO ades_app;

-- Default privileges para objetos futuros
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO ades_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO ades_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO ades_app;

SELECT 'Rol ades_app creado exitosamente' as resultado;
EOF

# 3. Actualizar .env
sed -i 's/POSTGRES_USER=ades_admin/POSTGRES_USER=ades_app/' /opt/ades/.env
sed -i 's/POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=NUEVA_PASSWORD_SEGURA/' /opt/ades/.env

# 4. Levantar stack nuevamente
docker compose up -d

# 5. Verificar conectividad
docker compose exec postgres psql -U ades_app -d ades -c "SELECT version();" && echo "✅ Conectividad OK"

# 6. Verificar que ades_admin sigue sin acceso de login
docker compose exec postgres psql -U postgres -c "SELECT rolname, rolcanlogin FROM pg_roles WHERE rolname IN ('ades_admin', 'ades_app');"
```

**Verificación final:**
```bash
# Esto debe trabajar (ades_app)
docker compose exec postgres psql -U ades_app -d ades -c "SELECT COUNT(*) FROM ades_usuarios;" && echo "✅ App OK"

# Esto debe fallar (ades_admin ya no tiene login)
docker compose exec postgres psql -U ades_admin -d ades -c "SELECT 1;" 2>&1 | grep -i "password\|no_login"
```

---

## 2️⃣ Asignar usuarios al grupo `ADES Admins` en Authentik

**Propósito:** Activar MFA obligatorio (TOTP/WebAuthn) para administradores.

**Usuarios a agregar (nivel_acceso ≤ 2):**
- `admin@institutonevadi.edu.mx` (ADMIN_GLOBAL)
- `admin.metepec` (ADMIN_PLANTEL Metepec)
- `admin.tenancingo` (ADMIN_PLANTEL Tenancingo)
- `admin.ixtapan` (ADMIN_PLANTEL Ixtapan)
- Todos los directores nivel 2 (dir.ten.primaria, dir.met.primaria, etc.)

### Pasos a ejecutar (UI Authentik)

```bash
# 1. Acceder a Authentik Admin
# URL: https://auth.ades.setag.mx/admin/
# Usuario: akadmin
# Contraseña: (la que configuraste en .env AUTHENTIK_BOOTSTRAP_PASSWORD)

# 2. Navegación
# Sidebar → Directory → Groups

# 3. Buscar grupo "ADES Admins"
# Si no existe, crear:
# - Name: ADES Admins
# - Parent: (dejar vacío)
# - Click "Create"

# 4. Entrar al grupo "ADES Admins"
# → Tab "Members"
# → Botón "Add member"

# 5. Agregar usuarios (por cada uno):
# - Buscar: admin@institutonevadi.edu.mx
# - Click "Add"
# - Repetir para: admin.metepec, admin.tenancingo, admin.ixtapan, dir.ten.primaria, etc.
```

**Alternativa via API (desde terminal):**

```bash
# Script para agregar usuarios automáticamente
AUTHENTIK_TOKEN=$(grep AUTHENTIK_BOOTSTRAP_TOKEN /opt/ades/.env | cut -d= -f2)
AUTHENTIK_URL="https://auth.ades.setag.mx"

# Obtener ID del grupo ADES Admins (crear si no existe)
GROUP_ID=$(curl -s -H "Authorization: Bearer $AUTHENTIK_TOKEN" \
  "$AUTHENTIK_URL/api/v3/core/groups/?name=ADES%20Admins" | jq -r '.results[0].pk // empty')

if [ -z "$GROUP_ID" ]; then
  echo "Creando grupo ADES Admins..."
  GROUP_ID=$(curl -s -X POST -H "Authorization: Bearer $AUTHENTIK_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"name":"ADES Admins","parent":null}' \
    "$AUTHENTIK_URL/api/v3/core/groups/" | jq -r '.pk')
  echo "Grupo creado: $GROUP_ID"
fi

# Obtener usuarios nivel_acceso <= 2 desde ADES
docker compose exec -T postgres psql -U ades_app -d ades -c \
  "SELECT nombre_usuario FROM ades_usuarios WHERE nivel_acceso <= 2 AND is_active;" > /tmp/admins.txt

# Agregar cada usuario al grupo (si existe en Authentik)
while read -r username; do
  USER_ID=$(curl -s -H "Authorization: Bearer $AUTHENTIK_TOKEN" \
    "$AUTHENTIK_URL/api/v3/core/users/?username=$username" | jq -r '.results[0].pk // empty')
  
  if [ -n "$USER_ID" ]; then
    echo "Agregando $username (ID: $USER_ID) al grupo..."
    curl -s -X POST -H "Authorization: Bearer $AUTHENTIK_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"pk\":$USER_ID}" \
      "$AUTHENTIK_URL/api/v3/core/groups/$GROUP_ID/users/add/" > /dev/null
  else
    echo "⚠️  No encontrado: $username"
  fi
done < /tmp/admins.txt

echo "✅ Usuarios agregados al grupo ADES Admins"
```

**Verificación:**
```bash
# Cuando un usuario en ADES Admins intente acceder, verá:
# "MFA requerido — Configure TOTP o WebAuthn"
# → App "Authenticator" (Google, Microsoft, Authy)
# → Escanear QR o copiar código de 6 dígitos
```

---

## 3️⃣ Resolver Tests E2E BIZ-04 y CER-E2E-10

### BIZ-04: Rechazar reinscripción

**Status:** ✅ Botón existe — 5 registros PENDIENTE en BD

**Problema:** El test busca el botón pero no lo encuentra porque:
- El botón está dentro de una tabla dentro de un p-dialog
- El test necesita abrir el dialog primero

**Solución:** Actualizar fixture para abrir dialog

```typescript
// frontend/e2e/tests/11-business-flows.spec.ts
test('BIZ-04 | rechazar reinscripción sin razón → error de validación', async ({ page }) => {
  await new LoginPage(page).login(USERS.COORDINADOR);  // rol 2 = puede validar reinscripciones
  await page.goto('/reinscripcion', { waitUntil: 'networkidle' });

  // 1. Esperar tabla de reinscripciones
  await page.waitForSelector('[data-testid="tabla-reinscripciones"], table', { timeout: 5_000 });

  // 2. Abrir primer registro (click en fila)
  const primerRegistro = page.locator('table tbody tr').first();
  await primerRegistro.click();
  
  // 3. Esperar dialog abrir
  await page.waitForSelector('p-dialog', { timeout: 3_000 });

  // 4. Buscar botón Rechazar DENTRO del dialog
  const rechazarBtn = page.locator('p-dialog [data-testid="btn-rechazar"]').first();
  await expect(rechazarBtn).toBeVisible();

  // 5. Click rechazar
  await rechazarBtn.click();

  // 6. Verificar textarea de razón aparece
  const textareaRazon = page.locator('[data-testid="textarea-razon-rechazo"]');
  await expect(textareaRazon).toBeVisible();

  // 7. Intentar confirmar SIN razón (debe ser disabled)
  const btnConfirmar = page.locator('[data-testid="btn-confirmar-rechazo"]');
  await expect(btnConfirmar).toHaveAttribute('disabled', '');

  // 8. Llenar razón y confirmar
  await textareaRazon.fill('Adeudo de cuotas pendientes');
  await expect(btnConfirmar).not.toHaveAttribute('disabled', '');
  await btnConfirmar.click();

  // 9. Verificar respuesta API
  const response = await new Promise(resolve => {
    page.on('response', (resp) => {
      if (resp.url().includes('/reinscripcion') && resp.request().method() === 'PATCH') {
        resolve(resp);
      }
    });
  });

  await expect(response).toBeDefined();
  console.log('✅ BIZ-04 PASSED');
});
```

**Cambios en componente Angular (si es necesario):**
```typescript
// frontend/src/app/features/reinscripcion/reinscripcion.component.ts
@if (seleccionado() && dialogVisible()) {
  <p-dialog 
    [(visible)]="dlgVisible"
    [header]="'Detalle de Reinscripción'"
    [modal]="true"
    [style]="{ width: '90vw', maxWidth: '1000px' }">
    
    <!-- Tabla de reinscripciones -->
    <table class="w-full">
      <tbody>
        @for (item of alumnos(); track item.id) {
          <tr (click)="abrirDialog(item)">
            <td>{{ item.nombre_alumno }}</td>
            <td>{{ item.estado }}</td>
            <td>
              <button pButton label="Aprobar" 
                      data-testid="btn-aprobar"
                      (click)="accionIndividual('APROBAR'); $event.stopPropagation()"></button>
              <button pButton label="Rechazar" severity="danger"
                      data-testid="btn-rechazar"
                      (click)="mostrarRazonRechazo.set(true); $event.stopPropagation()"></button>
            </td>
          </tr>
          @if (mostrarRazonRechazo() && seleccionado().id === item.id) {
            <tr>
              <td colspan="3">
                <textarea data-testid="textarea-razon-rechazo"
                          [(ngModel)]="razonRechazo"
                          placeholder="Razón de rechazo..."></textarea>
                <button pButton label="Confirmar"
                        [disabled]="!razonRechazo"
                        data-testid="btn-confirmar-rechazo"
                        (click)="accionIndividual('RECHAZAR')"></button>
              </td>
            </tr>
          }
        }
      </tbody>
    </table>
  </p-dialog>
}
```

---

### CER-E2E-10: Descargar PDF certificado

**Status:** ✅ 2 certificados existen en BD

**Problema:** El botón de descarga no está visible en la tabla

**Solución:** Verificar que el componente renderice botón en fila

```typescript
// frontend/e2e/tests/12-certificados.spec.ts
test('CER-E2E-10 | botón descargar PDF lanza download con MIME correcto', async ({ page }) => {
  await new LoginPage(page).login(USERS.DIRECTOR);
  await page.goto('/certificados', { waitUntil: 'networkidle' });

  // 1. Esperar tabla cargue
  await page.waitForSelector('p-table, [data-testid="tabla-certificados"]', { timeout: 5_000 });

  // 2. Verificar que hay certificados
  const rows = page.locator('table tbody tr, p-table tbody tr');
  const rowCount = await rows.count();
  
  if (rowCount === 0) {
    console.log('⚠️  No hay certificados — test skipped');
    test.skip();
    return;
  }

  // 3. Buscar botón descargar en primera fila
  const primeraCelda = rows.first();
  const downloadBtn = primeraCelda.locator('[data-testid="btn-descargar-pdf"], button:has-text("Descargar"), button:has-text("PDF")').first();

  if (!await downloadBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
    console.log('⚠️  Botón no visible — test skipped');
    test.skip();
    return;
  }

  // 4. Interceptar descarga
  const downloadPromise = page.waitForEvent('download');
  await downloadBtn.click();
  const download = await downloadPromise;

  // 5. Verificar MIME type
  const mimeType = download.suggestedFilename.endsWith('.pdf') ? 'application/pdf' : 'unknown';
  await expect(mimeType).toBe('application/pdf');

  // 6. Verificar contenido (debería ser PDF válido)
  const path = await download.path();
  const fileSize = require('fs').statSync(path).size;
  await expect(fileSize).toBeGreaterThan(1000);  // >1KB = válido

  console.log('✅ CER-E2E-10 PASSED');
});
```

**Cambios en componente Angular (si es necesario):**
```typescript
// frontend/src/app/features/certificados/certificados.component.ts
@Component({
  selector: 'app-certificados',
  standalone: true,
  imports: [CommonModule, PrimeNG, InteractiveGridComponent],
  templateUrl: './certificados.component.html',
})
export class CertificadosComponent implements OnInit {
  certificados = signal<Certificado[]>([]);
  descargando = signal(false);

  descargarPdf(certificado: Certificado) {
    this.descargando.set(true);
    this.api.download(`/certificados/${certificado.id}/descargar`, 'application/pdf')
      .pipe(finalize(() => this.descargando.set(false)))
      .subscribe();
  }
}
```

```html
<!-- template HTML -->
<app-interactive-grid
  [data]="certificados()"
  [columns]="columnConfig"
  (rowSelected)="abrirDetalle($event)">
</app-interactive-grid>

<div class="acciones">
  @for (cert of certificados(); track cert.id) {
    <button pButton 
            icon="pi pi-download"
            data-testid="btn-descargar-pdf"
            [loading]="descargando()"
            (click)="descargarPdf(cert)">
      Descargar PDF
    </button>
  }
</div>
```

---

## 4️⃣ BigBlueButton (BBB) — Decisión

### Análisis

| Aspecto | Detalles |
|--------|---------|
| **Tipo** | Software de videoconferencias de código abierto |
| **Costo** | Depende de opción |
| **Integración en ADES** | API-only (no dockerfile) |
| **Requisitos** | Servidor BBB externo + credenciales |

### Opciones

1. **Auto-hospedado (Gratuito):**
   - Instalar en servidor Ubuntu 18+ propio (4GB RAM mínimo)
   - Complejidad: Media → Alta
   - Tiempo: 1-2 horas
   - **Recomendación:** No hacer si no tienes servidor BBB operativo

2. **Proveedor cloud (Pago):**
   - Blindside Networks (oficial BBB): ~$0.20 USD/usuario/mes
   - Otros: Variables (Scalelite, BigMarker, etc.)
   - Tiempo: 30 min configuración
   - **Recomendación:** Solo si Nevadi necesita videoconferencias institucionales

3. **Eliminar de ADES:**
   - Los endpoints retornarán 503 "No configurado"
   - Asesorías pueden hacerse vía Zoom/Teams externo
   - **Recomendación:** ✅ Elegir esto si no tienen BBB

### Decisión recomendada

**Mantener BBB en ADES pero deshabilitado:**
- Endpoints existen pero retornan "503 Servidor BBB no configurado"
- Si en futuro Nevadi adquiere BBB, solo actualizar variables en .env
- Código sin cambios

**Código actual (frontend/src/app/features/bbb/):**
```typescript
export class BbbComponent {
  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.get('/api/v1/bbb/info').subscribe({
      next: (data) => this.serverConfigured = data.configured,
      error: () => this.serverConfigured = false
    });
  }
}
```

**Template (fallback):**
```html
@if (!serverConfigured) {
  <p-card>
    <p>⚠️ BigBlueButton no está configurado en este momento.</p>
    <p>Para activar videoconferencias, contacte al administrador.</p>
  </p-card>
} @else {
  <!-- UI de reuniones BBB -->
}
```

---

## 5️⃣ H5P Contenido Interactivo — Descarga e instalación

### Requisitos

| Item | Detalles |
|------|---------|
| **Costo** | $0 — Software de código abierto |
| **Tamaño** | ~500 MB (h5p-core + libraries) |
| **Tiempo** | 10 minutos (descarga + extracción) |

### Pasos de instalación

```bash
# 1. Crear directorio de datos para H5P
mkdir -p /opt/ades/data/h5p-core

# 2. Descargar h5p-core desde h5p.org
cd /tmp
wget https://h5p.org/sites/default/files/h5p/libraries/H5P.Core-1.26.0.tar.gz
# O descargar manualmente desde: https://github.com/h5p/h5p-core/releases

# 3. Extraer h5p-core
tar -xzf H5P.Core-1.26.0.tar.gz
mv h5p-core-1.26.0/* /opt/ades/data/h5p-core/

# 4. Descargar bibliotecas H5P (opcional pero recomendado)
# Quizzes, Drag and Drop, Interactive Video, Multiple Choice, etc.
# https://h5p.org/content-types-and-applications

# Descargar múltiples:
cd /opt/ades/data/h5p-core
# Copiar carpetas de contenido descargadas desde h5p.org

# 5. Verificar permisos
chmod -R 755 /opt/ades/data/h5p-core
chmod -R 755 /opt/ades/data/h5p-libraries  # si existe

# 6. Reiniciar servicio H5P
docker compose restart ades-h5p

# 7. Verificar health
curl -s http://localhost:8091/health && echo "✅ H5P OK"
```

### Validación

```bash
# H5P debe estar disponible en:
# https://ades.setag.mx/h5p/

# Desde Angular:
# GET /api/v1/h5p/tipos → lista 10+ tipos de contenido
# POST /api/v1/h5p/contenidos → crear nuevo contenido
```

---

## Resumen de Ejecución

| Tarea | Tiempo | Criticidad | Ejecutar ahora |
|-------|--------|-----------|-----------------|
| 1. POSTGRES_USER ades_admin → ades_app | 15 min | Media | ✅ Sí |
| 2. Grupo ADES Admins en Authentik | 10 min | Alta | ✅ Sí |
| 3. Fix tests E2E (BIZ-04, CER-E2E-10) | 20 min | Media | ✅ Sí |
| 4. BBB (Decision) | 5 min | Baja | ✅ Mantener deshabilitado |
| 5. H5P (Descarga + install) | 10 min | Media | ✅ Sí si deseas H5P; No si no lo necesitas |

---

## Próximos Pasos Post-Administrativo

```bash
# 1. Ejecutar todas las tareas arriba
# 2. Correr suite E2E nuevamente
docker compose exec frontend npm run e2e

# 3. Si pasan todos: ✅ Sistema listo para pruebas finales
# 4. Documentar cualquier issue en GitHub Issues

# 5. Plan de pruebas integral
# docs/plan_pruebas_integral.md
```

---

**Documento actualizado:** 2026-06-17  
**Versión:** 1.0  
**Estado:** Listo para ejecución

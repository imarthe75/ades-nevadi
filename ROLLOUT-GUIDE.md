# ADES — Guía de Rollout: Validación Integral (FASE 1-3)

**Documento**: Guía para rollout manual de validación a módulos restantes  
**Estado**: personal-admin completado; 5 módulos pendientes  
**Effort estimado**: 30-45 minutos  
**Fecha creación**: 2026-07-08

---

## ✅ Estado Actual

| Módulo | CURP | RFC | Dinero | Teléfono | ZIP | Estado | ETA |
|--------|------|-----|--------|----------|-----|--------|-----|
| personal-admin | ✓ | ✓ | ✗ | ✗ | ✗ | ✅ Completado | 2026-07-08 |
| padres-admin | ✗ | ✗ | ✓ | ✓ | ✗ | 🔴 Pendiente | 2026-07-09 |
| profesores | ✓ | ✓ | ✗ | ✗ | ✗ | 🔴 Pendiente | 2026-07-09 |
| admision | ✓ | ✓ | ✗ | ✓ | ✓ | 🔴 Pendiente | 2026-07-10 |
| expediente-laboral | ✓ | ✓ | ✓ | ✗ | ✗ | 🔴 Pendiente | 2026-07-10 |
| condiciones-cronicas | ✗ | ✗ | ✓ | ✓ | ✗ | 🔴 Pendiente | 2026-07-11 |

---

## 📋 Patrón de Rollout (Para Cada Módulo)

### Paso 1: Importar Servicios y Componentes

En `{modulo}.component.ts`, agregar:

```typescript
// Línea 1: Reemplazar FormsModule con ReactiveFormsModule
import { FormBuilder, FormsModule, ReactiveFormsModule, FormControl, Validators } from '@angular/forms';

// Agregar servicios de validación
import { InputFormattersService } from '../../shared/services/input-formatters.service';
import { AdesValidators } from '../../shared/validators/ades-validators';
import { FormFieldComponent } from '../../shared/components/form-field/form-field.component';
```

### Paso 2: Actualizar Decorador @Component

Reemplazar imports:

```typescript
// Antes
imports: [
  CommonModule, FormsModule,
  // ... otros imports
]

// Después
imports: [
  CommonModule, FormsModule, ReactiveFormsModule,
  // ... otros imports
  FormFieldComponent,
]
```

### Paso 3: Inyectar Servicios

En la clase del componente, agregar:

```typescript
export class {Modulo}Component implements OnInit {
  private api = inject(ApiService);
  private ctx = inject(ContextService);
  private readonly notify = inject(ApexNotificationService);
  readonly inputFormatters = inject(InputFormattersService);  // ← Agregar esto
  
  // ... resto de código
}
```

### Paso 4: Crear FormControls

Reemplazar propiedades de objeto de forma:

```typescript
// Antes
form = { nombre: '', curp: '', rfc: '', ... };

// Después
fcNombre = new FormControl('', [Validators.required, Validators.maxLength(100)]);
fcCURP = new FormControl('', [AdesValidators.isCURP()]);
fcRFC = new FormControl('', [AdesValidators.isRFC()]);
fcTelefono = new FormControl('', [AdesValidators.isMexicanPhoneNumber()]);
fcZIP = new FormControl('', [AdesValidators.isMexicanZipCode()]);
fcDinero = new FormControl('', [AdesValidators.isMoneyFormat()]);
```

**Validadores disponibles**:
- `Validators.required` — campo obligatorio
- `Validators.maxLength(n)` — máximo n caracteres
- `AdesValidators.isCURP()` — CURP válida RENAPO
- `AdesValidators.isRFC()` — RFC válida SAT
- `AdesValidators.isMexicanPhoneNumber()` — 10 dígitos
- `AdesValidators.isMexicanZipCode()` — 5 dígitos
- `AdesValidators.isMoneyFormat()` — dinero válido
- `AdesValidators.isAlphabetic()` — solo letras

### Paso 5: Reemplazar Inputs en Template

```html
<!-- Antes: ngModel sin validación -->
<input pInputText [(ngModel)]="form.nombre" style="width:100%" />

<!-- Después: FormControl con validación inline -->
<app-form-field
  [control]="fcNombre"
  label="Nombre(s)"
  placeholder="Ej: Juan Carlos"
  [maxLength]="100"
  [required]="true"
  [formatter]="inputFormatters.formatNombre.bind(inputFormatters)"
  helpText="Solo letras, espacios y guiones"
/>
```

**Formatters disponibles**:
- `inputFormatters.formatNombre()` — solo letras/espacios/guiones
- `inputFormatters.formatCURP()` — uppercase, 18 chars
- `inputFormatters.formatRFC()` — uppercase, 12-13 chars
- `inputFormatters.formatTelefono()` — 10 dígitos
- `inputFormatters.formatZIP()` — 5 dígitos
- `inputFormatters.formatDinero()` — números y separadores
- `inputFormatters.formatEmail()` — lowercase, trim
- `inputFormatters.formatCalle()` — dirección segura

### Paso 6: Actualizar Lógica de Guardado

Reemplazar en `guardar()` o `crearPersona()`:

```typescript
// Antes
guardar(): void {
  if (!this.form.nombre) { /* error */ }
  const payload = { nombre: this.form.nombre, curp: this.form.curp };
  this.api.post('/endpoint', payload).subscribe({...});
}

// Después
guardar(): void {
  // 1. Validar que campos requeridos sean válidos
  if (this.fcNombre.invalid || this.fcNombre.value.trim() === '') {
    this.notify.warning('Validación', 'Nombre es requerido y válido');
    return;
  }

  // 2. Validar campos opcionales pero con formato
  if (this.fcCURP.value && this.fcCURP.invalid) {
    this.notify.warning('Validación', 'CURP no válida');
    return;
  }

  // 3. Construir payload con valores de FormControls
  const payload = {
    nombre: this.fcNombre.value,
    curp: this.fcCURP.value || null,
    rfc: this.fcRFC.value || null,
    // ... otros campos
  };

  // 4. Llamar API
  this.guardando.set(true);
  this.api.post('/endpoint', payload).subscribe({
    next: () => {
      this.notify.success('Guardado', 'Registro creado correctamente');
      this.showDialog.set(false);
      this.resetFormControls();      // ← Agregar reset
      this.cargar();                 // Recargar tabla
    },
    error: (e: any) => {
      this.notify.error('Error', e?.error?.message ?? 'No se pudo guardar');
      this.guardando.set(false);
    },
  });
}

// Nueva función helper
private resetFormControls(): void {
  this.fcNombre.reset();
  this.fcCURP.reset();
  this.fcRFC.reset();
  // ... reset de todos los FormControls
}
```

### Paso 7: Actualizar Diálogo de Creación

En template de `p-dialog`:

```html
<!-- Antes: inputs simples -->
<p-dialog [visible]="showDialog()" ...>
  <div>
    <label>Nombre(s) *</label>
    <input pInputText [(ngModel)]="form.nombre" />
  </div>
  <div>
    <label>CURP</label>
    <input pInputText [(ngModel)]="form.curp" maxlength="18" />
  </div>
</p-dialog>

<!-- Después: FormFieldComponent -->
<p-dialog [visible]="showDialog()" ...>
  <div style="display:flex;flex-direction:column;gap:1rem">
    <app-form-field
      [control]="fcNombre"
      label="Nombre(s)"
      [required]="true"
      [maxLength]="100"
      [formatter]="inputFormatters.formatNombre.bind(inputFormatters)"
    />
    <app-form-field
      [control]="fcCURP"
      label="CURP"
      placeholder="Ej: AAAA999999HAAAAA01"
      [maxLength]="18"
      [formatter]="inputFormatters.formatCURP.bind(inputFormatters)"
      helpText="18 caracteres"
    />
    <!-- ... más campos -->
  </div>
</p-dialog>
```

### Paso 8: Compilar y Verificar

```bash
npm run build
# Debe completar sin errores TS
# Bundle size puede ser >2.5MB (se puede optimizar después)
```

---

## 🎯 Roadmap por Módulo

### 🔴 ALTA Prioridad (Hacer hoy 2026-07-08)

#### padres-admin
**Campos**:
- nombre_completo → formatNombre
- apellido_paterno → formatNombre
- apellido_materno → formatNombre
- telefono_principal → formatTelefono + isMexicanPhoneNumber()
- monto_beca → formatDinero

**Tiempo estimado**: 20 minutos  
**Nota**: Este módulo ya usa p-select para algunos campos, solo reemplazar inputs

#### profesores
**Campos**:
- nombre → formatNombre
- apellido_paterno → formatNombre
- apellido_materno → formatNombre
- curp → formatCURP + isCURP()
- numero_empleado → alfanumérico

**Tiempo estimado**: 15 minutos  
**Nota**: Módulo pequeño, formulario simple (7 campos)

#### admision
**Campos**:
- nombre → formatNombre
- curp → formatCURP + isCURP()
- rfc → formatRFC + isRFC()
- telefono → formatTelefono + isMexicanPhoneNumber()
- codigo_postal → formatZIP + isMexicanZipCode()

**Tiempo estimado**: 25 minutos  
**Nota**: Módulo complejo, priorizar campos críticos

### 🟡 MEDIA Prioridad (Hacer antes 2026-07-10)

#### expediente-laboral
**Campos**: curp, rfc, dinero (salario), nombre  
**Tiempo estimado**: 20 minutos

#### condiciones-cronicas
**Campos**: dinero (peso/talla), nombre, teléfono  
**Tiempo estimado**: 15 minutos

---

## 🔍 Checklist Post-Rollout (Por Cada Módulo)

- [ ] Imports actualizados (ReactiveFormsModule, servicios, componentes)
- [ ] FormControls creados con validadores apropiados
- [ ] Template actualizado con FormFieldComponent
- [ ] Método guardar() usa FormControl.value
- [ ] Método guardar() valida antes de API.post()
- [ ] resetFormControls() creado y llamado tras guardar
- [ ] Tests compilación: `npm run build` sin errores
- [ ] Tests E2E: `npx playwright test validation-patterns.spec.ts`
- [ ] Prueba manual en navegador (crear, guardar, recargar)

---

## 🐛 Troubleshooting Common

### Error: "inputFormatters is private"
**Solución**: Cambiar `private inputFormatters` a `readonly inputFormatters`

### Error: "isCURP is not a function"
**Solución**: Cambiar `AdesValidators.isCURP` a `AdesValidators.isCURP()`

### Error: "Cannot bind to unknown property"
**Solución**: Asegurarse que FormFieldComponent está importado en imports[]

### Datos no se guardan
**Causa**: Validación fallida + usuario no vio error  
**Solución**: Verificar que FormControl.invalid retorna false antes de guardar

### Bundle size >2.5MB
**Causa**: Demasiados FormFieldComponents importados  
**Solución**: Es normal durante rollout, se puede optimizar después con lazy loading

---

## 📞 Preguntas Frecuentes

**P: ¿Necesito migración BD?**  
R: No. Validación es solo frontend + lógica backend existente.

**P: ¿Se rompe la compatibilidad hacia atrás?**  
R: No. Clientes antiguos seguirán funcionando.

**P: ¿Cuándo rollout a usuarios reales?**  
R: Después de completar todos los módulos + E2E tests + Authentik config.

**P: ¿Puedo hacer solo algunos campos?**  
R: Sí, pero mantener consistencia UX. Mejor completar módulo.

---

## 📊 Métricas

Después de completar rollout:

- [ ] 6/6 módulos con validación
- [ ] Bundle size < 2.8 MB
- [ ] E2E tests pasando 100%
- [ ] Deploy a staging y verificar con testers Nevadi
- [ ] Recibir feedback y ajustar si necesario

---

## 📝 Referencias

**Implementación de referencia**: `personal-admin.component.ts` (commit c957040)  
**Servicios**: 
- `input-formatters.service.ts`
- `ades-validators.ts`
- `form-field.component.ts`

**Documentos relacionados**:
- `DEPLOYMENT-VALIDATION.md` — deploy a producción
- `ades_testing/tests/validation-patterns.spec.ts` — E2E tests

---

## ✋ Necesita Ayuda?

1. Ver logs de compilación: `npm run build 2>&1 | tail -50`
2. Comparar con personal-admin.component.ts
3. Revisar AdesValidators en ades-validators.ts
4. Ejecutar tests E2E para validar: `npx playwright test --headed`

/**
 * PATCH A APLICAR EN alumno-perfil.component.ts
 *
 * Este archivo describe cómo aplicar validadores a los campos principales del perfil del alumno.
 * Aplicar estos cambios a la clase AlumnoPerfilComponent para completa validación de entrada.
 *
 * PASOS:
 * 1. Importar ReactiveFormsModule, FormControl, Validators, ApexValidators
 * 2. Inyectar InputFormattersService
 * 3. Crear un FormGroup para cada tab (perfilForm, domicilioForm, etc.)
 * 4. Reemplazar [(ngModel)] con [formControl] en el template
 * 5. Usar app-form-field para campos críticos
 *
 * EJEMPLO COMPLETO PARA TAB PERSONAL:
 */

import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ApexValidators } from 'apex-component-library';

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * PASO 1: CREAR FORM GROUP PARA TAB PERSONAL
 * ═══════════════════════════════════════════════════════════════════════════════
 */

export class FormGroupExample {
  // En la clase AlumnoPerfilComponent, agregar:

  perfilPersonalForm = new FormGroup({
    // Datos básicos
    nombre: new FormControl('', [
      Validators.required,
      ApexValidators.isNotNull(),
      ApexValidators.maxChars(100),
    ]),
    apellido_paterno: new FormControl('', [
      Validators.required,
      ApexValidators.maxChars(100),
    ]),
    apellido_materno: new FormControl('', [
      ApexValidators.maxChars(100),
    ]),
    curp: new FormControl('', [
      Validators.required,
      ApexValidators.exactLength(18),
      ApexValidators.isCURP(),
    ]),
    rfc: new FormControl('', [
      ApexValidators.isRFC(),
    ]),

    // Contacto
    email: new FormControl('', [
      Validators.email,
      ApexValidators.maxChars(100),
    ]),
    telefono: new FormControl('', [
      ApexValidators.isMexicanPhoneNumber(),
    ]),

    // Datos sociodemográficos
    fecha_nacimiento: new FormControl(''),
    genero: new FormControl(''),
    estado_civil: new FormControl(''),
    nacionalidad: new FormControl('', [ApexValidators.maxChars(100)]),
    lengua_indigena: new FormControl('', [ApexValidators.maxChars(100)]),

    // Socioeconómico
    nivel_socioeconomico: new FormControl(''),
    etnia_identidad: new FormControl('', [ApexValidators.maxChars(100)]),
  });

  /**
   * ═══════════════════════════════════════════════════════════════════════════════
   * PASO 2: CREAR FORM GROUP PARA TAB ACADÉMICO
   * ═══════════════════════════════════════════════════════════════════════════════
   */
  perfilAcademicoForm = new FormGroup({
    // Escuela de procedencia
    escuela_procedencia: new FormControl('', [ApexValidators.maxChars(200)]),
    promedio_procedencia: new FormControl('', [ApexValidators.isMoneyFormat()]),
    clave_nivel_procedencia: new FormControl('', [ApexValidators.maxChars(20)]),

    // Beca
    beca_tipo: new FormControl(''),
    beca_monto: new FormControl('', [ApexValidators.isMoneyFormat()]),
    beca_descripcion: new FormControl('', [ApexValidators.maxChars(500)]),

    // Folio SEP
    folio_sep: new FormControl('', [ApexValidators.maxChars(50)]),
  });

  /**
   * ═══════════════════════════════════════════════════════════════════════════════
   * PASO 3: CREAR FORM GROUP PARA TAB SALUD
   * ═══════════════════════════════════════════════════════════════════════════════
   */
  perfilSaludForm = new FormGroup({
    tipo_sangre: new FormControl(''),
    alergias: new FormControl('', [ApexValidators.maxChars(500)]),
    medicamentos_autorizados: new FormControl('', [ApexValidators.maxChars(500)]),
    condiciones_cronicas: new FormControl('', [ApexValidators.maxChars(500)]),
    observaciones_generales: new FormControl('', [ApexValidators.maxChars(1000)]),
    nss: new FormControl('', [ApexValidators.maxChars(20)]),
    discapacidad: new FormControl('', [ApexValidators.maxChars(200)]),
    seguro_medico_tipo: new FormControl(''),
    seguro_medico_numero: new FormControl('', [ApexValidators.maxChars(50)]),
  });
}

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * PASO 4: ACTUALIZAR TEMPLATE PARA USAR app-form-field
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * ANTES (con ngModel):
 * <input pInputText [(ngModel)]="alumno.persona.nombre" />
 *
 * DESPUÉS (con FormControl):
 * <app-form-field
 *   [control]="perfilPersonalForm.get('nombre') as FormControl"
 *   label="Nombre(s)"
 *   placeholder="Ej: Juan Carlos"
 *   [maxLength]="100"
 *   helpText="Nombre completo del alumno"
 *   [required]="true"
 *   [formatter]="inputFormatters.formatNombre.bind(inputFormatters)"
 * />
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * PASO 5: SINCRONIZAR DATOS AL GUARDAR
 * ═══════════════════════════════════════════════════════════════════════════════
 */

export class GuardarEjemplo {
  guardarPerfil(): void {
    if (this.perfilPersonalForm.invalid) {
      console.error('Formulario inválido');
      return;
    }

    const formValues = this.perfilPersonalForm.value;
    const payload = {
      persona: {
        nombre: formValues.nombre?.trim() || '',
        apellido_paterno: formValues.apellido_paterno?.trim() || '',
        apellido_materno: formValues.apellido_materno?.trim() || '',
        curp: formValues.curp?.toUpperCase().trim() || '',
        rfc: formValues.rfc?.toUpperCase().trim() || '',
        email: formValues.email?.toLowerCase().trim() || '',
        telefono: formValues.telefono?.replace(/\D/g, '') || '', // Solo dígitos
      },
    };

    // Realizar PATCH al backend
    // this.api.patch(`/alumnos/${alumnoId}`, payload).subscribe(...)
  }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * PATRÓN DE VALIDACIÓN PARA OTROS MÓDULOS
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Este patrón se puede aplicar a CUALQUIER módulo en ADES:
 *
 * 1. NÓMINA:
 *    - sueldo_base: isMoneyFormat()
 *    - deducciones: isMoneyFormat()
 *    - bonificaciones: isMoneyFormat()
 *
 * 2. FACTURAS:
 *    - rfc_emisor: isRFC()
 *    - rfc_receptor: isRFC()
 *    - monto_total: isMoneyFormat()
 *
 * 3. SALUD:
 *    - peso: isNumeric() + maxChars(3)
 *    - talla: isNumeric() + maxChars(3)
 *    - presion: isNumeric() + maxChars(3)
 *
 * 4. DOMICILIO:
 *    - calle: maxChars(150) + isSafeAlphanumeric()
 *    - numero_exterior: maxChars(20)
 *    - zip_code: isMexicanZipCode()
 *
 * 5. CONTACTO:
 *    - email: Validators.email + maxChars(100)
 *    - telefono: isMexicanPhoneNumber()
 *    - celular: isMexicanPhoneNumber()
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 */

/**
 * RESUMEN DE VALIDADORES DISPONIBLES (apex-validators.ts extendido):
 *
 * isNotNull()              — No vacío (whitespace trimmed)
 * isNumeric()              — Solo números
 * isAlphanumeric()         — Solo letras y números
 * exactLength(n)           — Exactamente n caracteres
 * containsNoSpaces()       — Sin espacios
 * isCURP()                 — CURP válido (18 chars, formato RENAPO)
 * isRFC()                  — RFC válido (12-13 chars, formato SAT)
 * isMoneyFormat()          — Dinero: 1234.56 o 1234,56
 * isPositiveInteger()      — Números enteros positivos
 * isMexicanPhoneNumber()   — Teléfono: 10 dígitos exactos
 * isMexicanZipCode()       — ZIP: 5 dígitos exactos
 * isSafeAlphanumeric()     — Sin caracteres peligrosos (inyección)
 * maxChars(n)              — Máximo n caracteres (mejor mensaje que maxLength)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 */

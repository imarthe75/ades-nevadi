import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Validadores personalizados para ADES
 * Basados en los patrones de ValidationUtils.java del backend
 */
export class AdesValidators {
  private static readonly CURP_REGEX = /^[A-Z]{4}\d{6}[HM][A-Z]{5}\d{2}$/;
  private static readonly RFC_REGEX = /^[A-ZÑÁÉÍÓÚ]{4}\d{6}[A-Z0-9]{3}$/;

  /**
   * Valida CURP: AAAA999999HAAAAA01
   * - Exactamente 18 caracteres
   * - Formato: 4 letras, 6 números, 1 letra (H/M), 5 letras, 2 números
   */
  static isCURP(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      return AdesValidators.curpValido(control.value) ? null : { isCURP: true };
    };
  }

  /**
   * Valida RFC: AAAA999999AAA (básico)
   * - 12-13 caracteres
   * - Generalmente 4 letras + 6 números + 3 letras/números
   */
  static isRFC(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      return AdesValidators.rfcValido(control.value) ? null : { isRFC: true };
    };
  }

  /**
   * Valida teléfono mexicano: 10 dígitos
   */
  static isMexicanPhoneNumber(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      return AdesValidators.telefonoValido(control.value) ? null : { isMexicanPhoneNumber: true };
    };
  }

  /**
   * Valida código postal mexicano: 5 dígitos
   */
  static isMexicanZipCode(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      return AdesValidators.cpValido(control.value) ? null : { isMexicanZipCode: true };
    };
  }

  // ── Variantes imperativas (boolean) — para validar en el guardar() de los
  // muchos componentes ADES con formularios template-driven ([(ngModel)]) en
  // vez de reactive forms, donde un ValidatorFn de AbstractControl no aplica.
  // Reusan el mismo regex que las variantes ValidatorFn de arriba. ──

  static curpValido(v: string | null | undefined): boolean {
    if (!v) return false;
    return AdesValidators.CURP_REGEX.test(v.toUpperCase().trim());
  }

  static rfcValido(v: string | null | undefined): boolean {
    if (!v) return false;
    return AdesValidators.RFC_REGEX.test(v.toUpperCase().trim());
  }

  static telefonoValido(v: string | null | undefined): boolean {
    if (!v) return false;
    return v.replace(/\D/g, '').length === 10;
  }

  static cpValido(v: string | null | undefined): boolean {
    if (!v) return false;
    return v.replace(/\D/g, '').length === 5;
  }

  /** NSS (IMSS/ISSSTE): exactamente 11 dígitos numéricos. */
  static nssValido(v: string | null | undefined): boolean {
    if (!v) return false;
    return /^\d{11}$/.test(v.trim());
  }

  /**
   * Valida formato de dinero: números, puntos, comas
   */
  static isMoneyFormat(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const money = control.value;
      const moneyRegex = /^[\d.,$ ]+$/;
      return moneyRegex.test(money) ? null : { isMoneyFormat: true };
    };
  }

  /**
   * Validar que sea alfanumérico seguro (sin caracteres especiales)
   */
  static isSafeAlphanumeric(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const safe = /^[a-zA-Z0-9\s\-_áéíóúñÁÉÍÓÚÑ]+$/.test(control.value);
      return safe ? null : { isSafeAlphanumeric: true };
    };
  }

  /**
   * Validar que sea solo números positivos
   */
  static isPositiveInteger(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const num = parseInt(control.value, 10);
      return num > 0 && Number.isInteger(num) ? null : { isPositiveInteger: true };
    };
  }

  /**
   * Validar que sea solo números
   */
  static isNumeric(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      return /^\d+$/.test(control.value) ? null : { isNumeric: true };
    };
  }

  /**
   * Validar que sea solo letras
   */
  static isAlphabetic(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      return /^[a-záéíóúñA-ZÁÉÍÓÚÑ\s\-']+$/.test(control.value) ? null : { isAlphabetic: true };
    };
  }
}

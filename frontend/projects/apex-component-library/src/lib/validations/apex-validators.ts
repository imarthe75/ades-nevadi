import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Collection of Custom Validators mimicking Oracle APEX Client-Side Validations
 */
export class ApexValidators {
  
  /**
   * Equivalent to APEX: Item is NOT NULL
   */
  static isNotNull(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const isWhitespace = (control.value || '').toString().trim().length === 0;
      const isValid = !isWhitespace;
      return isValid ? null : { isNotNull: true, message: 'Value must have some characters' };
    };
  }

  /**
   * Equivalent to APEX: Item is Numeric
   */
  static isNumeric(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null; // Let required validator handle nulls
      const isValid = !isNaN(parseFloat(control.value)) && isFinite(control.value);
      return isValid ? null : { isNumeric: true, message: 'Value must be numeric' };
    };
  }

  /**
   * Equivalent to APEX: Item is Alphanumeric
   */
  static isAlphanumeric(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const isValid = /^[a-zA-Z0-9]+$/.test(control.value);
      return isValid ? null : { isAlphanumeric: true, message: 'Value must be alphanumeric' };
    };
  }

  /**
   * Equivalent to APEX: Item Matches Regular Expression
   */
  static matchesRegex(regex: RegExp, customMessage?: string): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const isValid = regex.test(control.value);
      return isValid ? null : { 
        matchesRegex: true, 
        message: customMessage || 'Value does not match required format' 
      };
    };
  }

  /**
   * Equivalent to APEX: Item Contains No Spaces
   */
  static containsNoSpaces(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const isValid = control.value.toString().indexOf(' ') === -1;
      return isValid ? null : { containsNoSpaces: true, message: 'Value cannot contain spaces' };
    };
  }

  /**
   * Equivalent to APEX: Item length is EXACTLY characters
   */
  static exactLength(length: number): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const isValid = control.value.toString().length === length;
      return isValid ? null : {
        exactLength: true,
        requiredLength: length,
        actualLength: control.value.toString().length,
        message: `Value must be exactly ${length} characters`
      };
    };
  }

  /**
   * Valida formato CURP (Clave Única de Registro de Población)
   * Formato: AAAA999999HAAAAA01 (18 caracteres)
   * Posición 9: H (hombre), M (mujer), X (no binario)
   */
  static isCURP(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const curp = control.value.toString().toUpperCase().trim();

      // Patrón CURP oficial RENAPO
      const pattern = /^[A-Z]{4}\d{6}[HMX][A-Z]{5}[A-Z\d]\d$/;

      // Permitir CURP sintéticas para testing (TGEN, XE, XP)
      if (curp.startsWith('TGEN') || curp.startsWith('XE') || curp.startsWith('XP')) {
        return curp.length === 18 ? null : {
          isCURP: true,
          message: 'CURP sintética debe tener 18 caracteres'
        };
      }

      const isValid = pattern.test(curp);
      return isValid ? null : {
        isCURP: true,
        message: 'CURP inválida. Formato: AAAA999999HAAAAA01'
      };
    };
  }

  /**
   * Valida formato RFC (Registro Federal de Contribuyentes)
   * Formato: AAAA999999AAA (12-13 caracteres)
   */
  static isRFC(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const rfc = control.value.toString().toUpperCase().trim();

      // Patrón RFC oficial SAT (permite Ñ)
      const pattern = /^[A-ZÑ&]{3,4}\d{6}[A-Z\d]{3}$/;

      // Permitir RFC sintéticos para testing
      if (rfc.startsWith('XAXX') || rfc.startsWith('XEXX')) {
        return null;
      }

      const isValid = pattern.test(rfc);
      return isValid ? null : {
        isRFC: true,
        message: 'RFC inválido. Formato: AAAA999999AAA'
      };
    };
  }

  /**
   * Valida formato de dinero: 1234.56 o 1234,56
   * Solo permite números, comas y puntos como separadores
   */
  static isMoneyFormat(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const value = control.value.toString().replace(/,/g, '.');

      // Patrón: 1 a 10 dígitos, opcionalmente seguido de .XX (2 decimales)
      const pattern = /^\d{1,10}(\.\d{1,2})?$/;

      const isValid = pattern.test(value);
      return isValid ? null : {
        isMoneyFormat: true,
        message: 'Formato de dinero inválido. Ej: 1234.56'
      };
    };
  }

  /**
   * Valida que sea solo números enteros positivos
   * Útil para cantidades, años, edades
   */
  static isPositiveInteger(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const pattern = /^\d+$/;
      const isValid = pattern.test(control.value.toString());
      return isValid ? null : {
        isPositiveInteger: true,
        message: 'Solo se permiten números enteros positivos'
      };
    };
  }

  /**
   * Valida teléfono mexicano: Exactamente 10 dígitos
   */
  static isMexicanPhoneNumber(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const phone = control.value.toString().replace(/[^0-9]/g, '');
      const isValid = phone.length === 10;
      return isValid ? null : {
        isMexicanPhoneNumber: true,
        message: 'Teléfono debe tener 10 dígitos'
      };
    };
  }

  /**
   * Valida código postal mexicano: 5 dígitos
   */
  static isMexicanZipCode(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const zip = control.value.toString().replace(/[^0-9]/g, '');
      const isValid = zip.length === 5;
      return isValid ? null : {
        isMexicanZipCode: true,
        message: 'Código postal debe tener 5 dígitos'
      };
    };
  }

  /**
   * Valida que no contenga caracteres especiales peligrosos (inyección SQL, XSS)
   * Permite letras, números, espacios, guiones, guiones bajos, puntos
   */
  static isSafeAlphanumeric(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const pattern = /^[a-zA-Z0-9\s\-_.áéíóúñ]+$/;
      const isValid = pattern.test(control.value);
      return isValid ? null : {
        isSafeAlphanumeric: true,
        message: 'Contiene caracteres no permitidos'
      };
    };
  }

  /**
   * Valida máximo de caracteres (similar a maxLength pero con mejor mensaje)
   */
  static maxChars(length: number): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const isValid = control.value.toString().length <= length;
      return isValid ? null : {
        maxChars: true,
        maxLength: length,
        actualLength: control.value.toString().length,
        message: `Máximo ${length} caracteres permitidos`
      };
    };
  }
}

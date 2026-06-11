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
}

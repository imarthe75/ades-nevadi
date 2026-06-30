/**
 * FASES 5-6: Form Utilities para UX mejorado
 */
export class FormUtils {
  static cleanInput(value: any): string | null {
    if (!value) return null;
    const trimmed = String(value).trim();
    return trimmed.length === 0 ? null : trimmed;
  }

  static validateLength(value: string, maxLength: number, fieldName: string): string | null {
    if (!value) return null;
    if (value.length > maxLength) {
      return `${fieldName} debe tener máximo ${maxLength} caracteres`;
    }
    return null;
  }
}

import { Injectable } from '@angular/core';

/**
 * Servicio reutilizable para formateo y restricción de entrada en campos de formulario.
 * Implementa máscaras de entrada para CURP, RFC, dinero, teléfono, nombres, etc.
 * Previene entrada de caracteres inválidos y mantiene límites de tamaño.
 */
@Injectable({ providedIn: 'root' })
export class InputFormattersService {

  /**
   * Formatea CURP: AAAA999999HAAAAA01
   * - 18 caracteres exactos
   * - Convierte a MAYÚSCULAS automáticamente
   * - Elimina caracteres inválidos
   */
  formatCURP(value: string): string {
    if (!value) return '';
    const cleaned = value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 18);
    return cleaned;
  }

  /**
   * Formatea RFC: AAAA999999AAA
   * - 12-13 caracteres
   * - Convierte a MAYÚSCULAS automáticamente
   * - Permite Ñ para apellidos con esa letra
   */
  formatRFC(value: string): string {
    if (!value) return '';
    const cleaned = value.toUpperCase().replace(/[^A-ZÑÁÉÍÓÚ0-9]/g, '').slice(0, 13);
    return cleaned;
  }

  /**
   * Formatea Nombre/Apellido: Solo letras, espacios, guiones
   * - Máximo 100 caracteres
   * - Elimina números y caracteres especiales (excepto espacios y guiones)
   * - Útil para nombres, apellidos, razón social
   */
  formatNombre(value: string): string {
    if (!value) return '';
    // Permite letras (incluyendo acentos), espacios, guiones, apóstrofes
    const cleaned = value
      .replace(/[^a-záéíóúñA-ZÁÉÍÓÚÑ\s\-']/g, '')
      .slice(0, 100);
    return cleaned;
  }

  /**
   * Formatea cantidad de dinero: 1234.56 o $1,234.56
   * - Máximo 15 caracteres (incluyendo símbolo y separadores)
   * - Solo números, comas, puntos, signo de pesos
   * - Útil para sueldos, montos de beca, deducciones
   */
  formatDinero(value: string): string {
    if (!value) return '';
    // Permite números, comas, puntos, signo $, espacios
    const cleaned = value.replace(/[^0-9.,$ ]/g, '').slice(0, 15);
    return cleaned;
  }

  /**
   * Formatea Teléfono: 10 dígitos exactos (formato México)
   * - Elimina todos los caracteres que no sean dígitos
   * - Máximo 10 dígitos
   */
  formatTelefono(value: string): string {
    if (!value) return '';
    return value.replace(/[^0-9]/g, '').slice(0, 10);
  }

  /**
   * Formatea Email: Convierte a minúsculas, elimina espacios
   * - Máximo 100 caracteres
   * - La validación formal se hace con el validador email
   */
  formatEmail(value: string): string {
    if (!value) return '';
    return value.trim().toLowerCase().slice(0, 100);
  }

  /**
   * Formatea Fecha de Nacimiento: YYYY-MM-DD
   * - Solo permite números y guiones
   * - Máximo 10 caracteres
   */
  formatFecha(value: string): string {
    if (!value) return '';
    return value.replace(/[^0-9\-]/g, '').slice(0, 10);
  }

  /**
   * Formatea ZIP/Código Postal: 5 dígitos para México
   * - Solo números
   * - Máximo 5 caracteres
   */
  formatZIP(value: string): string {
    if (!value) return '';
    return value.replace(/[^0-9]/g, '').slice(0, 5);
  }

  /**
   * Formatea Calle/Dirección: Permite letras, números, espacios, guiones, paréntesis
   * - Máximo 150 caracteres
   * - Elimina caracteres especiales peligrosos
   */
  formatCalle(value: string): string {
    if (!value) return '';
    // Permite letras, números, espacios, guiones, paréntesis, comas, puntos
    const cleaned = value
      .replace(/[^a-záéíóúñA-ZÁÉÍÓÚÑ0-9\s\-(),.']/g, '')
      .slice(0, 150);
    return cleaned;
  }

  /**
   * Formatea Campo Alfanumérico: Letras y números
   * - Máximo 100 caracteres
   * - Elimina caracteres especiales
   */
  formatAlphanumeric(value: string, maxLength: number = 100): string {
    if (!value) return '';
    return value.replace(/[^a-zA-Z0-9]/g, '').slice(0, maxLength);
  }

  /**
   * Formatea Campo Solo Números
   * - Solo dígitos
   * - Máximo custom según parámetro
   */
  formatNumeric(value: string, maxLength: number = 50): string {
    if (!value) return '';
    return value.replace(/[^0-9]/g, '').slice(0, maxLength);
  }

  /**
   * Formatea Texto libre corto: permite letras, números, espacios y
   * puntuación común (incluye "/" para pronombres tipo "él/sus"). Elimina
   * caracteres peligrosos para inyección (<>{}\`) y limita la longitud.
   * Útil para pronombres, ocupación, notas cortas, nombre social.
   */
  formatTexto(value: string, maxLength: number = 100): string {
    if (!value) return '';
    return value
      .replace(/[<>{}\\`]/g, '')
      .slice(0, maxLength);
  }

  /**
   * Valida si el formato es correcto (sin formatear)
   * Devuelve true si el valor actual está en el rango permitido
   */
  isValidLength(value: string, maxLength: number): boolean {
    return (value || '').length <= maxLength;
  }

  /**
   * Obtiene el contador para mostrar "X / maxLength"
   */
  getCharCount(value: string): number {
    return (value || '').length;
  }
}

/**
 * Spring Data Page<T> response model.
 * Representa una página de resultados paginados del backend.
 */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  numberOfElements: number;
  empty: boolean;
  first: boolean;
  last: boolean;
}

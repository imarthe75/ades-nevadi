import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AbstractControl, AsyncValidatorFn, ValidationErrors } from '@angular/forms';
import { Observable, timer, of } from 'rxjs';
import { map, switchMap, catchError } from 'rxjs/operators';

export interface ServerValidationPayload {
  item_name: string;
  item_value: any;
  validation_context?: string;
}

export interface ServerValidationResponse {
  is_valid: boolean;
  message?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ApexServerValidationService {
  constructor(private http: HttpClient) {}

  /**
   * Equivalent to APEX: Server-Side PL/SQL Validation returning Boolean or Error Text
   * This validator hits a generic endpoint. In a real scenario, the endpoint would run
   * the PL/SQL equivalent Python/SQL logic.
   * 
   * @param endpoint URL of the backend validation endpoint
   * @param itemName Name of the field/item being validated
   * @param context Additional context (e.g. form ID, record ID)
   * @param debounceMs Time to wait after user stops typing before calling server
   */
  public createAsyncValidator(
    endpoint: string, 
    itemName: string, 
    context?: string,
    debounceMs: number = 500
  ): AsyncValidatorFn {
    return (control: AbstractControl): Observable<ValidationErrors | null> => {
      if (!control.value) {
        return of(null);
      }

      return timer(debounceMs).pipe(
        switchMap(() => {
          const payload: ServerValidationPayload = {
            item_name: itemName,
            item_value: control.value,
            validation_context: context
          };
          
          return this.http.post<ServerValidationResponse>(endpoint, payload).pipe(
            map((response: ServerValidationResponse) => {
              return response.is_valid ? null : { serverValidationFailed: true, message: response.message || 'Server validation failed' };
            }),
            catchError(() => of({ serverError: true, message: 'Could not reach validation server' }))
          );
        })
      );
    };
  }
}

import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ApexNotificationService } from 'apex-component-library';
import { environment } from '../../../environments/environment';

const RETRY_HEADER = 'X-Ades-Retry';

/**
 * Interceptor HTTP funcional que inyecta el Bearer token de Authentik en cada
 * petición dirigida al BFF Spring (`environment.apiUrl` o rutas `/api/v1`).
 * Las solicitudes a Authentik y a terceros no se modifican para evitar filtraciones
 * del token institucional.
 *
 * Maneja dos tipos de error críticos:
 *
 * 1. 401 (Unauthorized) — access_token expirado (Authentik: access_token_validity=5min)
 *    Intenta renovar con refresh_token una sola vez y reintenta la petición original.
 *    Si la renovación falla, limpia la sesión y redirige a login.
 *
 * 2. 409 (Conflict) — Optimistic locking divergence (rowVersion mismatch)
 *    Sucede cuando otro usuario/proceso modifica el registro mientras lo editas.
 *    Notifica al usuario y deja que reintente con datos frescos.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const notify = inject(ApexNotificationService);
  const isApiRequest = req.url.startsWith(environment.apiUrl) || req.url.startsWith('/api/v1');
  const isAuthEndpoint = req.url.includes('/api/v1/auth/callback') || req.url.includes('/api/v1/auth/refresh');

  const withAuth = (token: string | null) =>
    token && isApiRequest ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(withAuth(auth.accessToken())).pipe(
    catchError((err: unknown) => {
      const alreadyRetried = req.headers.has(RETRY_HEADER);

      // Manejo de 401: Token expirado
      if (err instanceof HttpErrorResponse && err.status === 401 && isApiRequest && !isAuthEndpoint && !alreadyRetried) {
        return from(auth.refreshAccessToken()).pipe(
          switchMap((newToken) => {
            if (!newToken) {
              auth.forceReLogin();
              return throwError(() => err);
            }
            const retried = withAuth(newToken).clone({ setHeaders: { [RETRY_HEADER]: '1' } });
            return next(retried);
          }),
        );
      }

      // Manejo de 409: Optimistic locking conflict (rowVersion mismatch)
      if (err instanceof HttpErrorResponse && err.status === 409 && isApiRequest) {
        const message = err.error?.message ||
                       'El registro fue modificado por otro usuario. Por favor recarga y vuelve a intentarlo.';
        notify.warn('Conflicto de actualización', message);
      }

      return throwError(() => err);
    }),
  );
};

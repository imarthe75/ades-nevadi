import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

const RETRY_HEADER = 'X-Ades-Retry';

/**
 * Interceptor HTTP funcional que inyecta el Bearer token de Authentik en cada
 * petición dirigida al BFF Spring (`environment.apiUrl` o rutas `/api/v1`).
 * Las solicitudes a Authentik y a terceros no se modifican para evitar filtraciones
 * del token institucional.
 *
 * En un 401 (access_token expirado — Authentik: access_token_validity=minutes=5)
 * intenta renovar con el refresh_token una sola vez y reintenta la petición
 * original; si la renovación falla, limpia la sesión y redirige a login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isApiRequest = req.url.startsWith(environment.apiUrl) || req.url.startsWith('/api/v1');
  const isAuthEndpoint = req.url.includes('/api/v1/auth/callback') || req.url.includes('/api/v1/auth/refresh');

  const withAuth = (token: string | null) =>
    token && isApiRequest ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(withAuth(auth.accessToken())).pipe(
    catchError((err: unknown) => {
      const alreadyRetried = req.headers.has(RETRY_HEADER);
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
      return throwError(() => err);
    }),
  );
};

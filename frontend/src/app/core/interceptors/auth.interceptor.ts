import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

/**
 * Interceptor HTTP funcional que inyecta el Bearer token de Authentik en cada
 * petición dirigida al BFF Spring (`environment.apiUrl` o rutas `/api/v1`).
 * Las solicitudes a Authentik y a terceros no se modifican para evitar filtraciones
 * del token institucional.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.accessToken();
  // Solo agregar Authorization a peticiones del API de ADES, nunca a Authentik.
  // Acepta URLs absolutas con environment.apiUrl y rutas relativas a /api/v1.
  const isApiRequest = req.url.startsWith(environment.apiUrl) || req.url.startsWith('/api/v1');
  if (token && isApiRequest) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};

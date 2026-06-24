import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Guard funcional que protege las rutas autenticadas de ADES.
 * Verifica si el usuario tiene un token Authentik válido en sesión mediante
 * `AuthService.isLoggedIn()`. Si no está autenticado, redirige a `/login`.
 */
export const authGuard: CanActivateFn = () => {
  const auth   = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn()) return true;

  return router.createUrlTree(['/login']);
};

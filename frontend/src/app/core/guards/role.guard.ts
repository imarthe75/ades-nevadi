import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ContextService } from '../services/context.service';

/**
 * Guard de rol — restringe rutas según nivel_acceso del usuario.
 * Uso en routes: canActivate: [roleGuard(1)]  → solo nivel_acceso <= 1
 *
 * Jerarquía:
 *   0 = ADMIN_GLOBAL
 *   1 = ADMIN_PLANTEL
 *   2 = DIRECTOR/SUBDIRECTOR/COORDINADOR
 *   3 = COORDINADOR_ACADEMICO/TUTOR/ORIENTADOR
 *   4 = DOCENTE/MEDICO/PREFECTO
 *   5 = ALUMNO/PADRE_FAMILIA
 */
export function roleGuard(maxNivel: number): CanActivateFn {
  return () => {
    const ctx    = inject(ContextService);
    const router = inject(Router);
    const nivel  = ctx.nivelAcceso();

    if (nivel <= maxNivel) return true;

    return router.createUrlTree(['/dashboard']);
  };
}

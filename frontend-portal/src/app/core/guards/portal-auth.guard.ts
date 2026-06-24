import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { PortalAuthService } from '../services/portal-auth.service';

/**
 * Guardia de ruta (Route Guard) de Angular para la protección de accesos autenticados
 * en el portal externo de admisiones.
 *
 * Verifica si el usuario ha iniciado sesión a través del `PortalAuthService`.
 * En caso de no estar autenticado, redirige al usuario a la página de login
 * preservando la URL original en el parámetro query `returnUrl`.
 *
 * @returns {boolean} true si el usuario está autenticado, false en caso contrario.
 */
export const portalAuthGuard: CanActivateFn = () => {
  const auth   = inject(PortalAuthService);
  const router = inject(Router);
  if (auth.isLoggedIn()) return true;
  router.navigate(['/login'], { queryParams: { returnUrl: location.pathname } });
  return false;
};

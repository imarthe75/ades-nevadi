import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { PortalAuthService } from '../services/portal-auth.service';

export const portalAuthGuard: CanActivateFn = () => {
  const auth   = inject(PortalAuthService);
  const router = inject(Router);
  if (auth.isLoggedIn()) return true;
  router.navigate(['/login'], { queryParams: { returnUrl: location.pathname } });
  return false;
};

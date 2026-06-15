import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { PortalAuthService } from '../services/portal-auth.service';

export const portalAuthInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(PortalAuthService);
  const token = auth.token();
  if (token && req.url.includes('/api/portal/usuario')) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};

import { Routes } from '@angular/router';
import { portalAuthGuard } from './core/guards/portal-auth.guard';

export const routes: Routes = [
  { path: '',
    loadComponent: () => import('./features/inicio/inicio.component').then(m => m.InicioComponent) },

  // ── Públicas ────────────────────────────────────────────────────────────────
  { path: 'convocatorias',
    loadComponent: () => import('./features/convocatorias/convocatorias.component').then(m => m.ConvocatoriasComponent) },
  { path: 'convocatorias/:id',
    loadComponent: () => import('./features/convocatoria-detalle/convocatoria-detalle.component').then(m => m.ConvocatoriaDetalleComponent) },
  { path: 'seguimiento',
    loadComponent: () => import('./features/seguimiento/seguimiento.component').then(m => m.SeguimientoComponent) },
  { path: 'aviso-privacidad',
    loadComponent: () => import('./features/aviso-privacidad/aviso-privacidad.component').then(m => m.AvisoPrivacidadComponent) },
  { path: 'arco',
    loadComponent: () => import('./features/arco/arco.component').then(m => m.ArcoComponent) },

  // ── Auth ────────────────────────────────────────────────────────────────────
  { path: 'registro',
    loadComponent: () => import('./features/registro/registro.component').then(m => m.RegistroComponent) },
  { path: 'login',
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent) },
  { path: 'verificar-email',
    loadComponent: () => import('./features/verificar-email/verificar-email.component').then(m => m.VerificarEmailComponent) },
  { path: 'nueva-clave',
    loadComponent: () => import('./features/nueva-clave/nueva-clave.component').then(m => m.NuevaClaveComponent) },

  // ── Área privada ─────────────────────────────────────────────────────────────
  { path: 'mis-postulaciones', canActivate: [portalAuthGuard],
    loadComponent: () => import('./features/mis-postulaciones/mis-postulaciones.component').then(m => m.MisPostulacionesComponent) },
  { path: 'postulacion/:id', canActivate: [portalAuthGuard],
    loadComponent: () => import('./features/postulacion/postulacion.component').then(m => m.PostulacionComponent) },

  { path: '**', redirectTo: 'convocatorias' },
];

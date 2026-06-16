import { Routes } from '@angular/router';
import { authGuard }  from './core/guards/auth.guard';
import { roleGuard }  from './core/guards/role.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'callback',
    loadComponent: () => import('./core/components/callback.component').then(m => m.CallbackComponent),
  },
  {
    path: 'login',
    loadComponent: () => import('./core/components/login.component').then(m => m.LoginComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then(m => m.ShellComponent),
    children: [
      // ── FASE 12 — Administración ─────────────────────────────────────────
      { path: 'admin', canActivate: [roleGuard(1)], loadComponent: () => import('./features/admin/admin.component').then(m => m.AdminComponent) },
      // ── FASE 13 — Manual de usuario ──────────────────────────────────────
      { path: 'ayuda', loadComponent: () => import('./features/ayuda/ayuda.component').then(m => m.AyudaComponent) },
      // ── FASE 1 ──────────────────────────────────────────────────────────
      { path: 'dashboard',  loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'aulas',      canActivate: [roleGuard(3)], loadComponent: () => import('./features/aulas/aulas.component').then(m => m.AulasComponent) },
      { path: 'grupos',     canActivate: [roleGuard(3)], loadComponent: () => import('./features/grupos/grupos.component').then(m => m.GruposComponent) },
      { path: 'alumnos',        canActivate: [roleGuard(4)], loadComponent: () => import('./features/alumnos/alumnos.component').then(m => m.AlumnosComponent) },
      { path: 'reinscripcion',  canActivate: [roleGuard(3)], loadComponent: () => import('./features/reinscripcion/reinscripcion.component').then(m => m.ReinscripcionComponent) },
      { path: 'calendario',     canActivate: [roleGuard(3)], loadComponent: () => import('./features/calendario/calendario.component').then(m => m.CalendarioComponent) },
      { path: 'profesores', canActivate: [roleGuard(3)], loadComponent: () => import('./features/profesores/profesores.component').then(m => m.ProfesoresComponent) },
      // ── FASE 2 ──────────────────────────────────────────────────────────
      { path: 'calificaciones', canActivate: [roleGuard(4)], loadComponent: () => import('./features/calificaciones/calificaciones.component').then(m => m.CalificacionesComponent) },
      { path: 'asistencias',    canActivate: [roleGuard(4)], loadComponent: () => import('./features/asistencias/asistencias.component').then(m => m.AsistenciasComponent) },
      { path: 'tareas',         canActivate: [roleGuard(4)], loadComponent: () => import('./features/tareas/tareas.component').then(m => m.TareasComponent) },
      // ── FASE 3 ──────────────────────────────────────────────────────────
      { path: 'horarios',       canActivate: [roleGuard(4)], loadComponent: () => import('./features/horarios/horarios.component').then(m => m.HorariosComponent) },
      { path: 'conducta',       canActivate: [roleGuard(4)], loadComponent: () => import('./features/conducta/conducta.component').then(m => m.ConductaComponent) },
      { path: 'medico',         canActivate: [roleGuard(3)], loadComponent: () => import('./features/medico/medico.component').then(m => m.MedicoComponent) },
      // ── FASE 4 ──────────────────────────────────────────────────────────
      { path: 'eval-docente',   canActivate: [roleGuard(3)], loadComponent: () => import('./features/eval-docente/eval-docente.component').then(m => m.EvalDocenteComponent) },
      { path: 'learning-paths', loadComponent: () => import('./features/learning-paths/learning-paths.component').then(m => m.LearningPathsComponent) },
      { path: 'ia',             canActivate: [roleGuard(3)], loadComponent: () => import('./features/ia/ia.component').then(m => m.IaComponent) },
      // ── FASE 5 ──────────────────────────────────────────────────────────
      { path: 'comunicados',     loadComponent: () => import('./features/comunicados/comunicados.component').then(m => m.ComunicadosComponent) },
      { path: 'grade-analytics', canActivate: [roleGuard(3)], loadComponent: () => import('./features/grade-analytics/grade-analytics.component').then(m => m.GradeAnalyticsComponent) },
      // ── FASE 6 ──────────────────────────────────────────────────────────
      { path: 'evaluaciones',    loadComponent: () => import('./features/evaluaciones/evaluaciones.component').then(m => m.EvaluacionesComponent) },
      { path: 'planeacion',      loadComponent: () => import('./features/planeacion/planeacion.component').then(m => m.PlaneacionComponent) },
      { path: 'rubricas',        loadComponent: () => import('./features/rubricas/rubricas.component').then(m => m.RubricasComponent) },
      // ── FASE 7 ──────────────────────────────────────────────────────────
      { path: 'encuestas',       loadComponent: () => import('./features/encuestas/encuestas.component').then(m => m.EncuestasComponent) },
      // ── FASE 8 ──────────────────────────────────────────────────────────
      { path: 'badges',          loadComponent: () => import('./features/badges/badges.component').then(m => m.BadgesComponent) },
      // ── FASE 9 ──────────────────────────────────────────────────────────
      { path: 'portal',          loadComponent: () => import('./features/portal/portal.component').then(m => m.PortalComponent) },
      { path: 'portal-admin',   canActivate: [roleGuard(2)], loadComponent: () => import('./features/portal-admin/portal-admin.component').then(m => m.PortalAdminComponent) },
      { path: 'padres',          loadComponent: () => import('./features/padres/padres.component').then(m => m.PadresComponent) },
      { path: 'planes-estudio',  loadComponent: () => import('./features/planes-estudio/planes-estudio.component').then(m => m.PlanesEstudioComponent) },
      // ── FASE 10 — Gradebook Curricular ──────────────────────────────────
      { path: 'gradebook',        canActivate: [roleGuard(4)], loadComponent: () => import('./features/gradebook/gradebook.component').then(m => m.GradebookComponent) },
      { path: 'mi-progreso',      loadComponent: () => import('./features/mi-progreso/mi-progreso.component').then(m => m.MiProgresoComponent) },
      { path: 'ponderacion-config', canActivate: [roleGuard(3)], loadComponent: () => import('./features/ponderacion-config/ponderacion-config.component').then(m => m.PonderacionConfigComponent) },
      // ── FASE 16 — BI Dashboards Superset ────────────────────────────────
      { path: 'bi', loadComponent: () => import('./features/bi/bi.component').then(m => m.BiComponent) },
      // ── FASE 18 — Generador de Reportes (Carbone) ───────────────────────
      { path: 'reportes', canActivate: [roleGuard(3)], loadComponent: () => import('./features/reportes/reportes.component').then(m => m.ReportesComponent) },
      // ── FASE 22 — Monitor del sistema (Grafana + Prometheus) ─────────────
      { path: 'monitor', canActivate: [roleGuard(1)], loadComponent: () => import('./features/monitor/monitor.component').then(m => m.MonitorComponent) },
      // ── FASE 24 — Gestión de Padres de Familia ──────────────────────────
      { path: 'padres-admin', canActivate: [roleGuard(1)], loadComponent: () => import('./features/padres-admin/padres-admin.component').then(m => m.PadresAdminComponent) },
      // ── FASE 27 — Certificación Digital Ed25519 ─────────────────────────
      { path: 'certificados', canActivate: [roleGuard(3)], loadComponent: () => import('./features/certificados/certificados.component').then(m => m.CertificadosComponent) },
      // ── FASE 28 — Expediente Digital con Paperless-ngx ──────────────────
      { path: 'expediente-doc', canActivate: [roleGuard(3)], loadComponent: () => import('./features/expediente-doc/expediente-doc.component').then(m => m.ExpedienteDocComponent) },
      // ── FASE 29 — Seguridad Avanzada y RRHH ──────────────────────────────
      { path: 'licencias',           canActivate: [roleGuard(2)], loadComponent: () => import('./features/licencias/licencias.component').then(m => m.LicenciasComponent) },
      { path: 'capacitaciones',      canActivate: [roleGuard(2)], loadComponent: () => import('./features/capacitaciones/capacitaciones.component').then(m => m.CapacitacionesComponent) },
      // ── SPRINT A1 — Personal no-docente ──────────────────────────────────
      { path: 'personal-admin', canActivate: [roleGuard(2)], loadComponent: () => import('./features/personal-admin/personal-admin.component').then(m => m.PersonalAdminComponent) },
      // ── FASE 30 — RRHH Avanzado ───────────────────────────────────────────
      { path: 'expediente-laboral',  canActivate: [roleGuard(2)], loadComponent: () => import('./features/expediente-laboral/expediente-laboral.component').then(m => m.ExpedienteLaboralComponent) },
      { path: 'disponibilidad',      canActivate: [roleGuard(2)], loadComponent: () => import('./features/disponibilidad/disponibilidad.component').then(m => m.DisponibilidadComponent) },
      { path: 'asistencia-personal', canActivate: [roleGuard(2)], loadComponent: () => import('./features/asistencia-personal/asistencia-personal.component').then(m => m.AsistenciaPersonalComponent) },
      // ── FASE 31 — Operatividad Avanzada ──────────────────────────────────────
      { path: 'condiciones-cronicas', canActivate: [roleGuard(3)], loadComponent: () => import('./features/condiciones-cronicas/condiciones-cronicas.component').then(m => m.CondicionesCronicasComponent) },
      { path: 'justificaciones',      canActivate: [roleGuard(3)], loadComponent: () => import('./features/justificaciones/justificaciones.component').then(m => m.JustificacionesComponent) },
      // ── FASE 32 — Movilidad Estudiantil ──────────────────────────────────────
      { path: 'movilidad', canActivate: [roleGuard(3)], loadComponent: () => import('./features/movilidad/movilidad.component').then(m => m.MovilidadComponent) },
      // ── FASE 36 — Foros y Anuncios ───────────────────────────────────────────
      { path: 'foros', loadComponent: () => import('./features/foros/foros.component').then(m => m.ForosComponent) },
      // ── FASE 35 ──────────────────────────────────────────────────────────
      { path: 'cierre-ciclo', canActivate: [roleGuard(2)], loadComponent: () => import('./features/cierre-ciclo/cierre-ciclo.component').then(m => m.CierreCicloComponent) },
      // ── FASE 39 — Admisión ───────────────────────────────────────────────────
      { path: 'admision', canActivate: [roleGuard(3)], loadComponent: () => import('./features/admision/admision.component').then(m => m.AdmisionComponent) },
      // ── Sprint D — Optativas ─────────────────────────────────────────────────
      { path: 'optativas', canActivate: [roleGuard(3)], loadComponent: () => import('./features/optativas/optativas.component').then(m => m.OptativasComponent) },
    ],
  },
  // ── Pública — verificación de certificados (sin autenticación) ──────────
  {
    path: 'verificar/:folio',
    loadComponent: () => import('./features/verificar/verificar.component').then(m => m.VerificarComponent),
  },
  { path: '**', redirectTo: 'dashboard' },
];

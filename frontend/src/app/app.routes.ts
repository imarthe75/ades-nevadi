import { Routes } from '@angular/router';

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
    loadComponent: () => import('./layout/shell.component').then(m => m.ShellComponent),
    children: [
      // ── FASE 1 ──────────────────────────────────────────────────────────
      { path: 'dashboard',      loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'planteles',      loadComponent: () => import('./features/planteles/planteles.component').then(m => m.PlantelesComponent) },
      { path: 'grupos',         loadComponent: () => import('./features/grupos/grupos.component').then(m => m.GruposComponent) },
      { path: 'alumnos',        loadComponent: () => import('./features/alumnos/alumnos.component').then(m => m.AlumnosComponent) },
      { path: 'profesores',     loadComponent: () => import('./features/profesores/profesores.component').then(m => m.ProfesoresComponent) },
      // ── FASE 2 ──────────────────────────────────────────────────────────
      { path: 'calificaciones', loadComponent: () => import('./features/calificaciones/calificaciones.component').then(m => m.CalificacionesComponent) },
      { path: 'asistencias',    loadComponent: () => import('./features/asistencias/asistencias.component').then(m => m.AsistenciasComponent) },
      { path: 'tareas',         loadComponent: () => import('./features/tareas/tareas.component').then(m => m.TareasComponent) },
      // ── FASE 3 ──────────────────────────────────────────────────────────
      { path: 'horarios',       loadComponent: () => import('./features/horarios/horarios.component').then(m => m.HorariosComponent) },
      { path: 'conducta',       loadComponent: () => import('./features/conducta/conducta.component').then(m => m.ConductaComponent) },
      { path: 'medico',         loadComponent: () => import('./features/medico/medico.component').then(m => m.MedicoComponent) },
      // ── FASE 4 ──────────────────────────────────────────────────────────
      { path: 'eval-docente',    loadComponent: () => import('./features/eval-docente/eval-docente.component').then(m => m.EvalDocenteComponent) },
      { path: 'learning-paths', loadComponent: () => import('./features/learning-paths/learning-paths.component').then(m => m.LearningPathsComponent) },
      { path: 'ia',             loadComponent: () => import('./features/ia/ia.component').then(m => m.IaComponent) },
      // ── FASE 5 ──────────────────────────────────────────────────────────
      { path: 'comunicados',     loadComponent: () => import('./features/comunicados/comunicados.component').then(m => m.ComunicadosComponent) },
      { path: 'grade-analytics', loadComponent: () => import('./features/grade-analytics/grade-analytics.component').then(m => m.GradeAnalyticsComponent) },
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
      // ── FASE 10 — Gradebook Curricular ──────────────────────────────────
      { path: 'gradebook',        loadComponent: () => import('./features/gradebook/gradebook.component').then(m => m.GradebookComponent) },
      { path: 'mi-progreso',      loadComponent: () => import('./features/mi-progreso/mi-progreso.component').then(m => m.MiProgresoComponent) },
      { path: 'ponderacion-config', loadComponent: () => import('./features/ponderacion-config/ponderacion-config.component').then(m => m.PonderacionConfigComponent) },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];

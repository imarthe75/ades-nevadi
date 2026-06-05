/**
 * Grid Utilities — Configuración estándar de columnas para todos los módulos.
 * Define esquemas de columnas reutilizables para las entidades principales.
 */

import { ColumnConfig } from './interactive-grid.component';

// ═══════════════════════════════════════════════════════════════════════════
// ESQUEMAS DE COLUMNAS POR ENTIDAD
// ═══════════════════════════════════════════════════════════════════════════

export const ALUMNOS_COLUMNS: ColumnConfig[] = [
  { field: 'nombre_completo', header: 'Alumno', sortable: true, filterable: true, width: '200px' },
  { field: 'matricula', header: 'Matrícula', sortable: true, filterable: true, width: '100px' },
  { field: 'nivel', header: 'Nivel', sortable: true, filterable: true, width: '100px' },
  { field: 'grado', header: 'Grado', sortable: true, filterable: true, width: '80px' },
  { field: 'grupo', header: 'Grupo', sortable: true, filterable: true, width: '80px' },
  { field: 'plantel', header: 'Plantel', sortable: true, filterable: true, width: '150px' },
  { field: 'fecha_ingreso', header: 'Ingreso', sortable: true, filterable: false, width: '100px' },
  { field: 'estatus', header: 'Estatus', sortable: true, filterable: true, width: '100px' },
];

export const PROFESORES_COLUMNS: ColumnConfig[] = [
  { field: 'nombre_completo', header: 'Profesor', sortable: true, filterable: true, width: '200px' },
  { field: 'numero_empleado', header: 'Empleado', sortable: true, filterable: true, width: '120px' },
  { field: 'plantel', header: 'Plantel', sortable: true, filterable: true, width: '150px' },
  { field: 'materias', header: 'Materias', sortable: false, filterable: false, width: '200px' },
  { field: 'tipo_contrato', header: 'Contrato', sortable: true, filterable: true, width: '100px' },
  { field: 'estatus', header: 'Estatus', sortable: true, filterable: true, width: '100px' },
];

export const CALIFICACIONES_COLUMNS: ColumnConfig[] = [
  { field: 'estudiante', header: 'Estudiante', sortable: true, filterable: true, width: '180px' },
  { field: 'materia', header: 'Materia', sortable: true, filterable: true, width: '150px' },
  { field: 'periodo', header: 'Período', sortable: true, filterable: true, width: '100px' },
  { field: 'calificacion', header: 'Calificación', sortable: true, filterable: false, width: '100px', editable: true, type: 'number' },
  { field: 'acreditado', header: 'Acreditado', sortable: true, filterable: true, width: '100px' },
  { field: 'fecha_registro', header: 'Fecha', sortable: true, filterable: false, width: '120px' },
];

export const GRUPOS_COLUMNS: ColumnConfig[] = [
  { field: 'nombre_grupo', header: 'Grupo', sortable: true, filterable: true, width: '80px' },
  { field: 'nivel', header: 'Nivel', sortable: true, filterable: true, width: '100px' },
  { field: 'grado', header: 'Grado', sortable: true, filterable: true, width: '100px' },
  { field: 'ciclo_escolar', header: 'Ciclo', sortable: true, filterable: true, width: '120px' },
  { field: 'inscritos', header: 'Inscritos', sortable: true, filterable: false, width: '80px' },
  { field: 'capacidad_maxima', header: 'Capacidad', sortable: true, filterable: false, width: '80px' },
  { field: 'turno', header: 'Turno', sortable: true, filterable: true, width: '100px' },
  { field: 'profesor_titular', header: 'Profesor Titular', sortable: true, filterable: true, width: '150px' },
];

export const ASISTENCIAS_COLUMNS: ColumnConfig[] = [
  { field: 'estudiante', header: 'Estudiante', sortable: true, filterable: true, width: '180px' },
  { field: 'fecha', header: 'Fecha', sortable: true, filterable: false, width: '100px' },
  { field: 'materia', header: 'Materia', sortable: true, filterable: true, width: '150px' },
  { field: 'asistencia', header: 'Asistencia', sortable: true, filterable: true, width: '100px', editable: true, type: 'select', selectOptions: [
    { label: 'Presente', value: 'PRESENTE' },
    { label: 'Ausente', value: 'AUSENTE' },
    { label: 'Retardo', value: 'RETARDO' },
    { label: 'Justificado', value: 'JUSTIFICADO' }
  ] },
];

export const USUARIOS_COLUMNS: ColumnConfig[] = [
  { field: 'nombre_usuario', header: 'Usuario', sortable: true, filterable: true, width: '150px' },
  { field: 'nombre_completo', header: 'Nombre', sortable: true, filterable: true, width: '200px' },
  { field: 'email_institucional', header: 'Email', sortable: false, filterable: true, width: '200px' },
  { field: 'rol', header: 'Rol', sortable: true, filterable: true, width: '120px' },
  { field: 'nivel_acceso', header: 'Nivel', sortable: true, filterable: true, width: '80px' },
  { field: 'plantel', header: 'Plantel', sortable: true, filterable: true, width: '150px' },
  { field: 'ultimo_acceso', header: 'Último Acceso', sortable: true, filterable: false, width: '130px' },
  { field: 'estatus', header: 'Estatus', sortable: true, filterable: true, width: '80px' },
];

export const TAREAS_COLUMNS: ColumnConfig[] = [
  { field: 'titulo', header: 'Tarea', sortable: true, filterable: true, width: '250px' },
  { field: 'materia', header: 'Materia', sortable: true, filterable: true, width: '150px' },
  { field: 'fecha_asignacion', header: 'Asignada', sortable: true, filterable: false, width: '100px' },
  { field: 'fecha_entrega', header: 'Entrega', sortable: true, filterable: false, width: '100px' },
  { field: 'grupo', header: 'Grupo', sortable: true, filterable: true, width: '100px' },
  { field: 'entregas', header: 'Entregas', sortable: true, filterable: false, width: '80px' },
  { field: 'estado', header: 'Estado', sortable: true, filterable: true, width: '100px' },
];

export const EVALUACIONES_COLUMNS: ColumnConfig[] = [
  { field: 'titulo', header: 'Evaluación', sortable: true, filterable: true, width: '250px' },
  { field: 'materia', header: 'Materia', sortable: true, filterable: true, width: '150px' },
  { field: 'tipo', header: 'Tipo', sortable: true, filterable: true, width: '100px' },
  { field: 'fecha', header: 'Fecha', sortable: true, filterable: false, width: '100px' },
  { field: 'grupo', header: 'Grupo', sortable: true, filterable: true, width: '100px' },
  { field: 'respondidos', header: 'Respondidos', sortable: true, filterable: false, width: '100px' },
  { field: 'estado', header: 'Estado', sortable: true, filterable: true, width: '100px' },
];

export const COMUNICADOS_COLUMNS: ColumnConfig[] = [
  { field: 'titulo', header: 'Comunicado', sortable: true, filterable: true, width: '300px' },
  { field: 'tipo', header: 'Tipo', sortable: true, filterable: true, width: '100px' },
  { field: 'alcance', header: 'Alcance', sortable: true, filterable: true, width: '120px' },
  { field: 'fecha_publicacion', header: 'Publicado', sortable: true, filterable: false, width: '120px' },
  { field: 'autor', header: 'Autor', sortable: true, filterable: true, width: '150px' },
  { field: 'leidos', header: 'Leídos', sortable: true, filterable: false, width: '80px' },
  { field: 'estado', header: 'Estado', sortable: true, filterable: true, width: '100px' },
];

// ═══════════════════════════════════════════════════════════════════════════
// FACTORY PATTERN PARA OBTENER COLUMNAS
// ═══════════════════════════════════════════════════════════════════════════

export function getGridColumns(entidad: string): ColumnConfig[] {
  const schemas: { [key: string]: ColumnConfig[] } = {
    alumnos: ALUMNOS_COLUMNS,
    profesores: PROFESORES_COLUMNS,
    calificaciones: CALIFICACIONES_COLUMNS,
    grupos: GRUPOS_COLUMNS,
    asistencias: ASISTENCIAS_COLUMNS,
    usuarios: USUARIOS_COLUMNS,
    tareas: TAREAS_COLUMNS,
    evaluaciones: EVALUACIONES_COLUMNS,
    comunicados: COMUNICADOS_COLUMNS,
  };
  return schemas[entidad] || [];
}

// Modelos TypeScript espejando los schemas de la API ADES

export interface Plantel {
  id: string;
  nombre_plantel: string;
  clave_ct?: string;
  escuela_id: string;
  is_active: boolean;
}

export interface NivelEducativo {
  id: string;
  nombre_nivel: 'PRIMARIA' | 'SECUNDARIA' | 'PREPARATORIA';
  autoridad_educativa: string;
  tipo_ciclo: string;
  num_periodos_eval: number;
}

export interface Grado {
  id: string;
  numero_grado: number;
  nombre_grado: string;
  nivel_educativo_id: string;
  plantel_id: string;
}

export interface CicloEscolar {
  id: string;
  nombre_ciclo: string;
  nivel_educativo_id: string;
  fecha_inicio: string;
  fecha_fin: string;
  tipo_ciclo: string;
  es_vigente: boolean;
}

export interface Grupo {
  id: string;
  nombre_grupo: string;
  grado_id: string;
  ciclo_escolar_id: string;
  capacidad_maxima: number;
  turno: string;
  is_active: boolean;
}

export interface Persona {
  id: string;
  nombre: string;
  apellido_paterno: string;
  apellido_materno?: string;
  curp: string;
  genero?: 'M' | 'F';
  fecha_nacimiento?: string;
  nombre_completo?: string;
}

export interface Estudiante {
  id: string;
  matricula: string;
  plantel_id: string;
  fecha_ingreso?: string;
  persona?: Persona;
}

export interface Profesor {
  id: string;
  numero_empleado: string;
  plantel_id: string;
  tipo_contrato?: string;
  persona?: Persona;
}

export interface Materia {
  id: string;
  nombre_materia: string;
  clave_materia?: string;
  nivel_educativo_id: string;
  horas_semana?: number;
}

export interface PeriodoEvaluacion {
  id: string;
  nombre_periodo: string;
  numero_periodo: number;
  tipo_periodo: string;
  ciclo_escolar_id: string;
  fecha_inicio: string;
  fecha_fin: string;
}

export interface Clase {
  id: string;
  grupo_id: string;
  materia_id: string;
  profesor_id: string;
  fecha_clase: string;
  hora_inicio: string;
  hora_fin: string;
  tema_visto?: string;
  estatus_clase: string;
}

export type EstatusAsistencia = 'PRESENTE' | 'AUSENTE' | 'TARDE' | 'JUSTIFICADO';

export interface Asistencia {
  id: string;
  clase_id: string;
  estudiante_id: string;
  estatus_asistencia: EstatusAsistencia;
  observacion?: string;
}

export interface CalificacionPeriodo {
  id: string;
  estudiante_id: string;
  grupo_id: string;
  materia_id: string;
  periodo_evaluacion_id: string;
  calificacion_final: number;
  es_acreditado: boolean;
  observaciones?: string;
}

export interface RegistroLibreta {
  estudiante_id: string;
  matricula: string;
  nombre_completo: string;
  calificaciones: Record<string, number | null>;
  promedio: number | null;
}

export interface PeriodoSimple {
  id: string;
  nombre_periodo: string;
}

export interface LibretaGrupo {
  grupo_id: string;
  materia_id: string;
  periodos: string[];
  periodos_detalle: PeriodoSimple[];
  registros: RegistroLibreta[];
}

export interface ResumenPlantel {
  total_alumnos: number;
  total_profesores: number;
  total_grupos_activos: number;
  total_clases_hoy: number;
}

export interface Tarea {
  id: string;
  titulo: string;
  descripcion?: string;
  grupo_id: string;
  materia_id: string;
  fecha_asignacion: string;
  fecha_entrega: string;
  puntaje_maximo: number;
  permite_entrega_tarde: boolean;
  origen: 'MANUAL' | 'AUTO';
}

export interface Rol {
  id: string;
  nombre_rol: string;
  descripcion?: string;
  nivel_acceso: number;
}

export interface UsuarioMe {
  id: string;
  nombre_usuario: string;
  email_institucional: string;
  persona_id: string;
  nombre_completo: string;
  rol: string;
  nivel_acceso: number;
}

// ── Contexto global de la app (equivale a Application Items de APEX) ──────────

export interface AppContext {
  plantel: Plantel | null;
  ciclo: CicloEscolar | null;
  nivel: NivelEducativo | null;
  usuario: UsuarioMe | null;
}

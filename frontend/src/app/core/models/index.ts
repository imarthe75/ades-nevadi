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
  nombre_nivel?: string;
  _label?: string;
}

export interface Grupo {
  id: string;
  nombre_grupo: string;
  grado_id: string;
  ciclo_escolar_id: string;
  capacidad_maxima: number;
  turno: string;
  is_active: boolean;
  // Campos extendidos de GrupoDetalle (populated by backend)
  nombre_nivel?: string | null;
  nombre_grado?: string | null;
  numero_grado?: number | null;
  plantel_nombre?: string | null;
  inscritos?: number;
}

/** Label completo para dropdowns: "Metepec — Secundaria 2° / A" */
export function grupoLabel(g: Grupo | null | undefined): string {
  if (!g) return '';
  const plantel = g.plantel_nombre ? `${g.plantel_nombre} · ` : '';
  const nivel = g.nombre_nivel ?? '';
  const grado = g.nombre_grado ?? '';
  const grupo = g.nombre_grupo ?? '';
  return nivel && grado ? `${plantel}${nivel} — ${grado} / ${grupo}` : grupo;
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
  // Migración 011
  telefono?: string | null;
  email_personal?: string | null;
  estado_civil?: string | null;
  municipio_nacimiento?: string | null;
  estado_nacimiento?: string | null;
  nacionalidad?: string | null;
}

export interface Estudiante {
  id: string;
  matricula: string;
  plantel_id: string;
  persona_id?: string;
  fecha_ingreso?: string;
  persona?: Persona;
  is_active?: boolean;
  // Datos complementarios (tipo_sangre/alergias → ExpedienteMedico)
  nss?: string | null;
  discapacidad?: string | null;
  escuela_procedencia?: string | null;
  clave_ct_procedencia?: string | null;
  promedio_procedencia?: number | null;
  beca_tipo?: string | null;
  beca_monto?: number | null;
  nivel_socioeconomico?: string | null;
  etnia?: string | null;
  lengua_indigena?: string | null;
  lengua_indigena_id?: string | null;
  nivel_ingles_id?: string | null;
}

export interface Profesor {
  id: string;
  numero_empleado: string;
  plantel_id: string;
  persona_id?: string;
  tipo_contrato?: string;
  persona?: Persona;
  // Datos laborales migración 011
  rfc?: string | null;
  nss?: string | null;
  cedula_profesional?: string | null;
  especialidad?: string | null;
  nivel_estudios?: string | null;
  fecha_ingreso_inst?: string | null;
  clabe?: string | null;
  banco?: string | null;
  turno?: string | null;
}

export interface ContactoEmergencia {
  id: string;
  persona_id: string;
  nombre_completo: string;
  parentesco?: string | null;
  telefono?: string | null;
  telefono_alt?: string | null;
  email?: string | null;
  es_tutor_legal: boolean;
  es_contacto_prim: boolean;
  ocupacion?: string | null;
  nivel_estudios?: string | null;
  rfc?: string | null;
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
  plantel_id?: string | null;
  nivel_educativo_id?: string | null;
  nombre_plantel?: string | null;
  nombre_nivel?: string | null;
}

/** Helpers de rol derivados de UsuarioMe */
export function esAdminGlobal(u: UsuarioMe | null): boolean {
  return !!u && u.plantel_id == null;
}
export function esAdminPlantel(u: UsuarioMe | null): boolean {
  return !!u && !!u.plantel_id && u.nivel_educativo_id == null;
}
export function tieneScopeNivel(u: UsuarioMe | null): boolean {
  return !!u && !!u.plantel_id && !!u.nivel_educativo_id;
}
export function rolLabel(u: UsuarioMe | null): string {
  const map: Record<string, string> = {
    ADMIN_GLOBAL:              'Admin Global',
    ADMIN_PLANTEL:             'Admin Plantel',
    DIRECTOR:                  'Director(a)',
    SUBDIRECTOR:               'Subdirector(a)',
    DOCENTE:                   'Docente',
    COORDINADOR_ACADEMICO:     'Coord. Académico',
    COORDINADOR_ADMINISTRATIVO:'Coord. Administrativo',
    ALUMNO:                    'Alumno',
    PADRE_FAMILIA:             'Padre/Tutor',
  };
  return u ? (map[u.rol] ?? u.rol) : '';
}

// ── Contexto global de la app (equivale a Application Items de APEX) ──────────

export interface AppContext {
  plantel: Plantel | null;
  ciclo: CicloEscolar | null;
  nivel: NivelEducativo | null;
  usuario: UsuarioMe | null;
}

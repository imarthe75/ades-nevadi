/*
 * by Im@rthe
 * Fecha: 2026-06-04
 * Archivo: 20260604_0001_ades_nevadi.sql
 *
 * Descripcion:
 * Aseguramiento idempotente de llaves primarias (UUID) y llaves foraneas
 * en todas las tablas del esquema ADES Instituto Nevadi.
 *
 * Este script es seguro para ejecutarse multiples veces (idempotente).
 * Verifica existencia de cada restriccion en pg_constraint antes de crearla.
 *
 * Contexto de ejecucion:
 * - BD existente con PKs BIGINT ya creadas: el script verifica y no las toca.
 * - BD nueva (schema limpio): el script crea todas las restricciones desde cero
 *   si las tablas existen pero sus constraints no.
 *
 * Nota sobre migracion BIGINT -> UUID:
 * La transformacion completa del tipo de PK requiere un script dedicado
 * con estrategia de reconstruccion de tablas. Este script solo garantiza
 * que los constraints EXISTEN, cualquiera sea el tipo actual del campo id.
 *
 * Solicitado y aprobado por Israel Martinez Hernandez
 *
 * Motor de Base de Datos: PostgreSQL
 * Version minima requerida: 18
 * Ambiente: desarrollo
 */

-- ===========================
-- FUNCION AUXILIAR LOCAL
-- ===========================
-- Verifica si existe una restriccion con nombre dado en una tabla
-- Uso: SELECT util_fk_existe('tabla', 'nombre_constraint')
CREATE OR REPLACE FUNCTION pg_temp.constraint_existe(
    p_tabla    TEXT,
    p_conname  TEXT
) RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        WHERE t.relname = p_tabla
          AND c.conname  = p_conname
    );
END;
$$ LANGUAGE plpgsql;

-- ===========================
-- 2. LLAVES PRIMARIAS
-- Verifica cada tabla y crea PK si no existe
-- ===========================

DO $$
DECLARE
    v_tablas TEXT[] := ARRAY[
        'ades_estatus','ades_paises','ades_estados','ades_municipios',
        'ades_tipos_asentamiento','ades_localidades','ades_codigos_postales',
        'ades_direcciones','ades_telefonos','ades_correos_electronicos',
        'ades_archivos','ades_identidad_institucional','ades_historico_identidad',
        'ades_escuelas','ades_planteles','ades_niveles_educativos',
        'ades_plantel_niveles','ades_grados','ades_ciclos_escolares',
        'ades_periodos_evaluacion','ades_calendario_escolar','ades_materias',
        'ades_materias_plan','ades_temas','ades_roles','ades_personas',
        'ades_usuarios','ades_profesores','ades_disponibilidad_docente',
        'ades_estudiantes','ades_contactos_familiares','ades_grupos',
        'ades_asignaciones_docentes','ades_inscripciones','ades_aulas',
        'ades_horarios','ades_clases','ades_asistencias','ades_planeacion_clases',
        'ades_avance_planificacion','ades_tareas','ades_tareas_entregas',
        'ades_rubricas','ades_rubrica_criterios','ades_calificaciones_tareas',
        'ades_evaluaciones','ades_calificaciones_evaluaciones',
        'ades_calificaciones_periodo','ades_personal_salud',
        'ades_expedientes_medicos','ades_incidentes_medicos',
        'ades_reportes_conducta','ades_reportes_academicos',
        'ades_comunicados','ades_acuses_comunicado','ades_notificaciones',
        'ades_informacion_escuela'
    ];
    v_tabla TEXT;
    v_tiene_pk BOOLEAN;
BEGIN
    FOREACH v_tabla IN ARRAY v_tablas LOOP
        -- Verificar si la tabla existe
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = v_tabla
        ) THEN
            RAISE WARNING 'Tabla public.% no existe — omitida', v_tabla;
            CONTINUE;
        END IF;

        -- Verificar si tiene PK
        SELECT EXISTS (
            SELECT 1 FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            WHERE t.relname = v_tabla AND c.contype = 'p'
        ) INTO v_tiene_pk;

        IF v_tiene_pk THEN
            RAISE NOTICE 'PK ya existe en public.%', v_tabla;
        ELSE
            EXECUTE format(
                'ALTER TABLE public.%I ADD CONSTRAINT pk_%s PRIMARY KEY (id)',
                v_tabla, v_tabla
            );
            RAISE NOTICE 'PK creada en public.%', v_tabla;
        END IF;
    END LOOP;
END $$;

-- ===========================
-- 3. LLAVES FORANEAS
-- Verifica cada FK por nombre y la crea si no existe
-- ===========================

-- Macro para crear FK idempotente
-- USAGE: PERFORM pg_temp.ensure_fk('tabla_hija', 'fk_nombre', 'col_fk', 'tabla_padre', 'col_padre');
CREATE OR REPLACE FUNCTION pg_temp.ensure_fk(
    p_tabla_hija   TEXT,
    p_conname      TEXT,
    p_col_fk       TEXT,
    p_tabla_padre  TEXT,
    p_col_padre    TEXT  DEFAULT 'id'
) RETURNS VOID AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema='public' AND table_name=p_tabla_hija
    ) THEN
        RAISE WARNING 'Tabla hija % no existe — FK % omitida', p_tabla_hija, p_conname;
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema='public' AND table_name=p_tabla_padre
    ) THEN
        RAISE WARNING 'Tabla padre % no existe — FK % omitida', p_tabla_padre, p_conname;
        RETURN;
    END IF;

    IF pg_temp.constraint_existe(p_tabla_hija, p_conname) THEN
        RAISE NOTICE 'FK % ya existe en public.%', p_conname, p_tabla_hija;
        RETURN;
    END IF;

    EXECUTE format(
        'ALTER TABLE public.%I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES public.%I (%I)',
        p_tabla_hija, p_conname, p_col_fk, p_tabla_padre, p_col_padre
    );
    RAISE NOTICE 'FK % creada en public.%', p_conname, p_tabla_hija;
END;
$$ LANGUAGE plpgsql;

-- -------------------------
-- Catalogo geografico
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_estados',          'fk_ades_estados_pais',                 'pais_id',             'ades_paises');
    PERFORM pg_temp.ensure_fk('ades_municipios',       'fk_ades_municipios_estado',             'estado_id',           'ades_estados');
    PERFORM pg_temp.ensure_fk('ades_localidades',      'fk_ades_localidades_municipio',         'municipio_id',        'ades_municipios');
    PERFORM pg_temp.ensure_fk('ades_localidades',      'fk_ades_localidades_tipo_asentamiento', 'tipo_asentamiento_id','ades_tipos_asentamiento');
    PERFORM pg_temp.ensure_fk('ades_codigos_postales', 'fk_ades_cp_localidad',                  'localidad_id',        'ades_localidades');
    PERFORM pg_temp.ensure_fk('ades_codigos_postales', 'fk_ades_cp_municipio',                  'municipio_id',        'ades_municipios');
    PERFORM pg_temp.ensure_fk('ades_codigos_postales', 'fk_ades_cp_estado',                     'estado_id',           'ades_estados');
    PERFORM pg_temp.ensure_fk('ades_codigos_postales', 'fk_ades_cp_tipo_asentamiento',          'tipo_asentamiento_id','ades_tipos_asentamiento');
END $$;

-- -------------------------
-- Contacto universal (polimorficas — sin FK formal por diseno)
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_direcciones',         'fk_ades_dir_localidad',        'localidad_id',      'ades_localidades');
    PERFORM pg_temp.ensure_fk('ades_direcciones',         'fk_ades_dir_cp',               'codigo_postal_id',  'ades_codigos_postales');
    -- entidad_id es polimorfca (PLANTEL/PERSONA/ESCUELA) — no tiene FK formal
END $$;

-- -------------------------
-- Identidad institucional
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_historico_identidad', 'fk_ades_hist_identidad',       'identidad_id',      'ades_identidad_institucional');
END $$;

-- -------------------------
-- Estructura institucional
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_escuelas',            'fk_ades_escuelas_logo',        'identidad_institucional_logo_id',   'ades_identidad_institucional');
    PERFORM pg_temp.ensure_fk('ades_escuelas',            'fk_ades_escuelas_slogan',      'identidad_institucional_slogan_id', 'ades_identidad_institucional');
    PERFORM pg_temp.ensure_fk('ades_escuelas',            'fk_ades_escuelas_estatus',     'estatus_id',        'ades_estatus');
    PERFORM pg_temp.ensure_fk('ades_planteles',           'fk_ades_planteles_escuela',    'escuela_id',        'ades_escuelas');
    PERFORM pg_temp.ensure_fk('ades_planteles',           'fk_ades_planteles_estatus',    'estatus_id',        'ades_estatus');
END $$;

-- -------------------------
-- Estructura academica
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_plantel_niveles',     'fk_ades_pn_plantel',           'plantel_id',        'ades_planteles');
    PERFORM pg_temp.ensure_fk('ades_plantel_niveles',     'fk_ades_pn_nivel',             'nivel_educativo_id','ades_niveles_educativos');
    PERFORM pg_temp.ensure_fk('ades_plantel_niveles',     'fk_ades_pn_estatus',           'estatus_id',        'ades_estatus');
    PERFORM pg_temp.ensure_fk('ades_grados',              'fk_ades_grados_nivel',         'nivel_educativo_id','ades_niveles_educativos');
    PERFORM pg_temp.ensure_fk('ades_grados',              'fk_ades_grados_plantel',       'plantel_id',        'ades_planteles');
    PERFORM pg_temp.ensure_fk('ades_grados',              'fk_ades_grados_estatus',       'estatus_id',        'ades_estatus');
    PERFORM pg_temp.ensure_fk('ades_ciclos_escolares',    'fk_ades_ciclos_nivel',         'nivel_educativo_id','ades_niveles_educativos');
    PERFORM pg_temp.ensure_fk('ades_periodos_evaluacion', 'fk_ades_periodos_ciclo',       'ciclo_escolar_id',  'ades_ciclos_escolares');
    PERFORM pg_temp.ensure_fk('ades_calendario_escolar',  'fk_ades_cal_ciclo',            'ciclo_escolar_id',  'ades_ciclos_escolares');
    PERFORM pg_temp.ensure_fk('ades_calendario_escolar',  'fk_ades_cal_plantel',          'plantel_id',        'ades_planteles');
END $$;

-- -------------------------
-- Materias y planes de estudio
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_materias',            'fk_ades_materias_nivel',       'nivel_educativo_id','ades_niveles_educativos');
    PERFORM pg_temp.ensure_fk('ades_materias_plan',       'fk_ades_mp_materia',           'materia_id',        'ades_materias');
    PERFORM pg_temp.ensure_fk('ades_materias_plan',       'fk_ades_mp_grado',             'grado_id',          'ades_grados');
    PERFORM pg_temp.ensure_fk('ades_materias_plan',       'fk_ades_mp_ciclo',             'ciclo_escolar_id',  'ades_ciclos_escolares');
    PERFORM pg_temp.ensure_fk('ades_temas',               'fk_ades_temas_materia',        'materia_id',        'ades_materias');
    PERFORM pg_temp.ensure_fk('ades_temas',               'fk_ades_temas_grado',          'grado_id',          'ades_grados');
    PERFORM pg_temp.ensure_fk('ades_temas',               'fk_ades_temas_ciclo',          'ciclo_escolar_id',  'ades_ciclos_escolares');
END $$;

-- -------------------------
-- Usuarios y personas
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_usuarios',            'fk_ades_usu_persona',          'persona_id',        'ades_personas');
    PERFORM pg_temp.ensure_fk('ades_usuarios',            'fk_ades_usu_rol',              'rol_id',            'ades_roles');
    PERFORM pg_temp.ensure_fk('ades_usuarios',            'fk_ades_usu_plantel',          'plantel_id',        'ades_planteles');
    PERFORM pg_temp.ensure_fk('ades_usuarios',            'fk_ades_usu_estatus',          'estatus_id',        'ades_estatus');
END $$;

-- -------------------------
-- Profesores
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_profesores',          'fk_ades_prof_persona',         'persona_id',        'ades_personas');
    PERFORM pg_temp.ensure_fk('ades_profesores',          'fk_ades_prof_plantel',         'plantel_id',        'ades_planteles');
    PERFORM pg_temp.ensure_fk('ades_profesores',          'fk_ades_prof_estatus',         'estatus_id',        'ades_estatus');
    PERFORM pg_temp.ensure_fk('ades_disponibilidad_docente','fk_ades_disp_profesor',      'profesor_id',       'ades_profesores');
    PERFORM pg_temp.ensure_fk('ades_disponibilidad_docente','fk_ades_disp_ciclo',         'ciclo_escolar_id',  'ades_ciclos_escolares');
END $$;

-- -------------------------
-- Estudiantes y familia
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_estudiantes',         'fk_ades_est_persona',          'persona_id',        'ades_personas');
    PERFORM pg_temp.ensure_fk('ades_estudiantes',         'fk_ades_est_plantel',          'plantel_id',        'ades_planteles');
    PERFORM pg_temp.ensure_fk('ades_estudiantes',         'fk_ades_est_estatus',          'estatus_id',        'ades_estatus');
    PERFORM pg_temp.ensure_fk('ades_contactos_familiares','fk_ades_cf_estudiante',        'estudiante_id',     'ades_estudiantes');
    PERFORM pg_temp.ensure_fk('ades_contactos_familiares','fk_ades_cf_persona',           'persona_id',        'ades_personas');
END $$;

-- -------------------------
-- Grupos, asignaciones, inscripciones
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_grupos',              'fk_ades_grupos_grado',         'grado_id',          'ades_grados');
    PERFORM pg_temp.ensure_fk('ades_grupos',              'fk_ades_grupos_ciclo',         'ciclo_escolar_id',  'ades_ciclos_escolares');
    PERFORM pg_temp.ensure_fk('ades_grupos',              'fk_ades_grupos_titular',       'profesor_titular_id','ades_profesores');
    PERFORM pg_temp.ensure_fk('ades_grupos',              'fk_ades_grupos_estatus',       'estatus_id',        'ades_estatus');
    PERFORM pg_temp.ensure_fk('ades_asignaciones_docentes','fk_ades_ad_grupo',            'grupo_id',          'ades_grupos');
    PERFORM pg_temp.ensure_fk('ades_asignaciones_docentes','fk_ades_ad_materia',          'materia_id',        'ades_materias');
    PERFORM pg_temp.ensure_fk('ades_asignaciones_docentes','fk_ades_ad_profesor',         'profesor_id',       'ades_profesores');
    PERFORM pg_temp.ensure_fk('ades_asignaciones_docentes','fk_ades_ad_ciclo',            'ciclo_escolar_id',  'ades_ciclos_escolares');
    PERFORM pg_temp.ensure_fk('ades_inscripciones',       'fk_ades_insc_estudiante',      'estudiante_id',     'ades_estudiantes');
    PERFORM pg_temp.ensure_fk('ades_inscripciones',       'fk_ades_insc_grupo',           'grupo_id',          'ades_grupos');
    PERFORM pg_temp.ensure_fk('ades_inscripciones',       'fk_ades_insc_ciclo',           'ciclo_escolar_id',  'ades_ciclos_escolares');
    PERFORM pg_temp.ensure_fk('ades_inscripciones',       'fk_ades_insc_estatus',         'estatus_id',        'ades_estatus');
END $$;

-- -------------------------
-- Horarios y aulas
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_aulas',               'fk_ades_aulas_plantel',        'plantel_id',        'ades_planteles');
    PERFORM pg_temp.ensure_fk('ades_horarios',            'fk_ades_hor_grupo',            'grupo_id',          'ades_grupos');
    PERFORM pg_temp.ensure_fk('ades_horarios',            'fk_ades_hor_materia',          'materia_id',        'ades_materias');
    PERFORM pg_temp.ensure_fk('ades_horarios',            'fk_ades_hor_profesor',         'profesor_id',       'ades_profesores');
    PERFORM pg_temp.ensure_fk('ades_horarios',            'fk_ades_hor_aula',             'aula_id',           'ades_aulas');
    PERFORM pg_temp.ensure_fk('ades_horarios',            'fk_ades_hor_ciclo',            'ciclo_escolar_id',  'ades_ciclos_escolares');
END $$;

-- -------------------------
-- Clases, asistencias, planeacion
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_clases',              'fk_ades_clases_horario',       'horario_id',        'ades_horarios');
    PERFORM pg_temp.ensure_fk('ades_clases',              'fk_ades_clases_grupo',         'grupo_id',          'ades_grupos');
    PERFORM pg_temp.ensure_fk('ades_clases',              'fk_ades_clases_materia',       'materia_id',        'ades_materias');
    PERFORM pg_temp.ensure_fk('ades_clases',              'fk_ades_clases_profesor',      'profesor_id',       'ades_profesores');
    PERFORM pg_temp.ensure_fk('ades_asistencias',         'fk_ades_asist_clase',          'clase_id',          'ades_clases');
    PERFORM pg_temp.ensure_fk('ades_asistencias',         'fk_ades_asist_estudiante',     'estudiante_id',     'ades_estudiantes');
    PERFORM pg_temp.ensure_fk('ades_planeacion_clases',   'fk_ades_plan_grupo',           'grupo_id',          'ades_grupos');
    PERFORM pg_temp.ensure_fk('ades_planeacion_clases',   'fk_ades_plan_tema',            'tema_id',           'ades_temas');
    PERFORM pg_temp.ensure_fk('ades_avance_planificacion','fk_ades_avance_planeacion',    'planeacion_clase_id','ades_planeacion_clases');
    PERFORM pg_temp.ensure_fk('ades_avance_planificacion','fk_ades_avance_clase',         'clase_id',          'ades_clases');
END $$;

-- -------------------------
-- Tareas y entregas
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_tareas',              'fk_ades_tar_grupo',            'grupo_id',          'ades_grupos');
    PERFORM pg_temp.ensure_fk('ades_tareas',              'fk_ades_tar_materia',          'materia_id',        'ades_materias');
    PERFORM pg_temp.ensure_fk('ades_tareas',              'fk_ades_tar_tema',             'tema_id',           'ades_temas');
    PERFORM pg_temp.ensure_fk('ades_tareas',              'fk_ades_tar_periodo',          'periodo_evaluacion_id','ades_periodos_evaluacion');
    PERFORM pg_temp.ensure_fk('ades_tareas_entregas',     'fk_ades_entr_tarea',           'tarea_id',          'ades_tareas');
    PERFORM pg_temp.ensure_fk('ades_tareas_entregas',     'fk_ades_entr_estudiante',      'estudiante_id',     'ades_estudiantes');
END $$;

-- -------------------------
-- Calificaciones y evaluaciones
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_rubricas',                    'fk_ades_rub_materia',         'materia_id',           'ades_materias');
    PERFORM pg_temp.ensure_fk('ades_rubricas',                    'fk_ades_rub_nivel',           'nivel_educativo_id',   'ades_niveles_educativos');
    PERFORM pg_temp.ensure_fk('ades_rubrica_criterios',           'fk_ades_rc_rubrica',          'rubrica_id',           'ades_rubricas');
    PERFORM pg_temp.ensure_fk('ades_calificaciones_tareas',       'fk_ades_calt_entrega',        'tarea_entrega_id',     'ades_tareas_entregas');
    PERFORM pg_temp.ensure_fk('ades_calificaciones_tareas',       'fk_ades_calt_rubrica',        'rubrica_id',           'ades_rubricas');
    PERFORM pg_temp.ensure_fk('ades_evaluaciones',                'fk_ades_eval_grupo',          'grupo_id',             'ades_grupos');
    PERFORM pg_temp.ensure_fk('ades_evaluaciones',                'fk_ades_eval_materia',        'materia_id',           'ades_materias');
    PERFORM pg_temp.ensure_fk('ades_evaluaciones',                'fk_ades_eval_periodo',        'periodo_evaluacion_id','ades_periodos_evaluacion');
    PERFORM pg_temp.ensure_fk('ades_calificaciones_evaluaciones', 'fk_ades_cale_evaluacion',     'evaluacion_id',        'ades_evaluaciones');
    PERFORM pg_temp.ensure_fk('ades_calificaciones_evaluaciones', 'fk_ades_cale_estudiante',     'estudiante_id',        'ades_estudiantes');
    PERFORM pg_temp.ensure_fk('ades_calificaciones_periodo',      'fk_ades_calp_estudiante',     'estudiante_id',        'ades_estudiantes');
    PERFORM pg_temp.ensure_fk('ades_calificaciones_periodo',      'fk_ades_calp_grupo',          'grupo_id',             'ades_grupos');
    PERFORM pg_temp.ensure_fk('ades_calificaciones_periodo',      'fk_ades_calp_materia',        'materia_id',           'ades_materias');
    PERFORM pg_temp.ensure_fk('ades_calificaciones_periodo',      'fk_ades_calp_periodo',        'periodo_evaluacion_id','ades_periodos_evaluacion');
END $$;

-- -------------------------
-- Expediente medico
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_personal_salud',      'fk_ades_ps_persona',           'persona_id',        'ades_personas');
    PERFORM pg_temp.ensure_fk('ades_personal_salud',      'fk_ades_ps_plantel',           'plantel_id',        'ades_planteles');
    PERFORM pg_temp.ensure_fk('ades_personal_salud',      'fk_ades_ps_estatus',           'estatus_id',        'ades_estatus');
    PERFORM pg_temp.ensure_fk('ades_expedientes_medicos', 'fk_ades_exm_estudiante',       'estudiante_id',     'ades_estudiantes');
    PERFORM pg_temp.ensure_fk('ades_incidentes_medicos',  'fk_ades_im_estudiante',        'estudiante_id',     'ades_estudiantes');
    PERFORM pg_temp.ensure_fk('ades_incidentes_medicos',  'fk_ades_im_personal_salud',    'personal_salud_id', 'ades_personal_salud');
END $$;

-- -------------------------
-- Reportes de conducta y academicos
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_reportes_conducta',   'fk_ades_rc_estudiante',        'estudiante_id',     'ades_estudiantes');
    PERFORM pg_temp.ensure_fk('ades_reportes_conducta',   'fk_ades_rc_grupo',             'grupo_id',          'ades_grupos');
    PERFORM pg_temp.ensure_fk('ades_reportes_conducta',   'fk_ades_rc_reportado_por',     'reportado_por_id',  'ades_profesores');
    PERFORM pg_temp.ensure_fk('ades_reportes_conducta',   'fk_ades_rc_estatus',           'estatus_id',        'ades_estatus');
    PERFORM pg_temp.ensure_fk('ades_reportes_academicos', 'fk_ades_ra_estudiante',        'estudiante_id',     'ades_estudiantes');
    PERFORM pg_temp.ensure_fk('ades_reportes_academicos', 'fk_ades_ra_ciclo',             'ciclo_escolar_id',  'ades_ciclos_escolares');
    PERFORM pg_temp.ensure_fk('ades_reportes_academicos', 'fk_ades_ra_periodo',           'periodo_evaluacion_id','ades_periodos_evaluacion');
    PERFORM pg_temp.ensure_fk('ades_reportes_academicos', 'fk_ades_ra_generado_por',      'generado_por_id',   'ades_usuarios');
END $$;

-- -------------------------
-- Comunicados y notificaciones
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_comunicados',         'fk_ades_com_escuela',          'escuela_id',        'ades_escuelas');
    PERFORM pg_temp.ensure_fk('ades_comunicados',         'fk_ades_com_plantel',          'plantel_id',        'ades_planteles');
    PERFORM pg_temp.ensure_fk('ades_comunicados',         'fk_ades_com_nivel',            'nivel_educativo_id','ades_niveles_educativos');
    PERFORM pg_temp.ensure_fk('ades_comunicados',         'fk_ades_com_grupo',            'grupo_id',          'ades_grupos');
    PERFORM pg_temp.ensure_fk('ades_comunicados',         'fk_ades_com_creado_por',       'creado_por_id',     'ades_usuarios');
    PERFORM pg_temp.ensure_fk('ades_acuses_comunicado',   'fk_ades_ac_comunicado',        'comunicado_id',     'ades_comunicados');
    PERFORM pg_temp.ensure_fk('ades_acuses_comunicado',   'fk_ades_ac_usuario',           'usuario_id',        'ades_usuarios');
    PERFORM pg_temp.ensure_fk('ades_notificaciones',      'fk_ades_not_usuario',          'usuario_id',        'ades_usuarios');
END $$;

-- -------------------------
-- Informacion de escuela
-- -------------------------
DO $$ BEGIN
    PERFORM pg_temp.ensure_fk('ades_informacion_escuela', 'fk_ades_ie_escuela',           'escuela_id',        'ades_escuelas');
END $$;

-- ===========================
-- 4. COMENTARIOS
-- ===========================
COMMENT ON FUNCTION pg_temp.constraint_existe(TEXT,TEXT) IS
    'Funcion de sesion: verifica existencia de constraint por nombre en tabla dada.';
COMMENT ON FUNCTION pg_temp.ensure_fk(TEXT,TEXT,TEXT,TEXT,TEXT) IS
    'Funcion de sesion: crea FK idempotentemente si no existe. Emite NOTICE o WARNING segun corresponda.';

-- ===========================
-- 5. NOTAS TECNICAS
-- ===========================
-- 1. Script idempotente: todas las verificaciones usan pg_constraint e information_schema.
-- 2. Las funciones pg_temp.* viven solo en la sesion y no persisten.
-- 3. Las referencias polimorficas (entidad_id en ades_direcciones, ades_telefonos,
--    ades_correos_electronicos, ades_archivos, ades_notificaciones) no tienen FK formal
--    por diseno — son polimorficas sobre entidad_tipo + entidad_id.
-- 4. Este script trabaja sobre el tipo actual de la columna id (sea BIGINT o UUID).
-- 5. La migracion de BIGINT a UUID requiere script 003_ separado con aprobacion DBA.

-- Fin del script

package mx.ades.modules.imports;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class ImportsWriteService {

    private final JdbcTemplate jdbc;

    public ImportsWriteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Catalog loaders ───────────────────────────────────────────────────────

    public Map<String, UUID> loadPlantelesByClave() {
        Map<String, UUID> map = new HashMap<>();
        jdbc.query("SELECT id, clave_ct FROM ades_planteles", rs -> {
            String c = rs.getString("clave_ct");
            if (c != null) map.put(c, (UUID) rs.getObject("id"));
        });
        return map;
    }

    public Map<String, UUID> loadPlantelesByNombre() {
        Map<String, UUID> map = new HashMap<>();
        jdbc.query("SELECT id, nombre_plantel FROM ades_planteles", rs -> {
            String n = rs.getString("nombre_plantel");
            if (n != null) map.put(n.toLowerCase(), (UUID) rs.getObject("id"));
        });
        return map;
    }

    public Map<String, UUID> loadNiveles() {
        Map<String, UUID> map = new HashMap<>();
        jdbc.query("SELECT id, nombre_nivel FROM ades_niveles_educativos", rs -> {
            String n = rs.getString("nombre_nivel");
            if (n != null) map.put(n.toLowerCase(), (UUID) rs.getObject("id"));
        });
        return map;
    }

    public Map<String, UUID> loadGrados() {
        Map<String, UUID> map = new HashMap<>();
        jdbc.query("SELECT id, nombre_grado FROM ades_grados", rs -> {
            String n = rs.getString("nombre_grado");
            if (n != null) map.put(n.toLowerCase(), (UUID) rs.getObject("id"));
        });
        return map;
    }

    /**
     * Resuelve el plantel_id de un grado (ades_grados.plantel_id es NOT NULL). Usado por
     * ImportsController#importarGrupos para aplicar el mismo scoping por plantel
     * (plantelPermitido) que ya aplican alumnos/profesores/aulas/preinscritos-sep — sin esto
     * un Director de plantel podía importar grupos anclados a un grado de OTRO plantel.
     */
    public UUID plantelDeGrado(UUID gradoId) {
        List<UUID> rows = jdbc.query(
                "SELECT plantel_id FROM ades_grados WHERE id = ?",
                (rs, i) -> (UUID) rs.getObject("plantel_id"), gradoId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, UUID> loadCiclos() {
        Map<String, UUID> map = new HashMap<>();
        jdbc.query("SELECT id, nombre_ciclo FROM ades_ciclos_escolares", rs -> {
            String n = rs.getString("nombre_ciclo");
            if (n != null) map.put(n.toLowerCase(), (UUID) rs.getObject("id"));
        });
        return map;
    }

    public UUID loadEstatusId(String entidad, String nombreEstatus) {
        try {
            return jdbc.queryForObject(
                    "SELECT id FROM ades_estatus WHERE entidad = ? AND nombre_estatus = ?",
                    UUID.class, entidad, nombreEstatus);
        } catch (Exception e) {
            log.warn("Estatus {} para {} no encontrado", nombreEstatus, entidad);
            return null;
        }
    }

    public long countEstudiantes() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM ades_estudiantes", Long.class);
        return count != null ? count : 0L;
    }

    // ── Dup checks ────────────────────────────────────────────────────────────

    public boolean existePersonaCurp(String curp) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ades_personas WHERE curp = ?", Integer.class, curp);
        return count != null && count > 0;
    }

    public boolean existeAdmisionActiva(String curp) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.ades_solicitudes_admision WHERE curp = ? AND estado NOT IN ('RECHAZADO')",
                Integer.class, curp);
        return count != null && count > 0;
    }

    // ── Transactional row inserts ─────────────────────────────────────────────

    @Data
    @Builder
    public static class AlumnoData {
        private String nombre, apellidoPaterno, apellidoMaterno, curp, rfc, genero;
        private Object fechaNacimiento;
        private String telefono, emailPersonal, nacionalidad;
        private UUID plantelId;
        private String matricula;
        private Object fechaIngreso;
        private String nss, escuelaProcedencia, claveCtProcedencia;
        private Double promedioProcedencia;
        private String becaTipo;
        private Double becaMonto;
        private String folioSep, tipoAlumno;
        private UUID estatusId;
        private String usuario;
    }

    @Transactional
    public UUID insertarAlumno(AlumnoData d) {
        UUID personaId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_personas (id, nombre, apellido_paterno, apellido_materno, curp, rfc, genero, " +
                "fecha_nacimiento, telefono, email_personal, nacionalidad, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'MEXICANA'), ?, ?)",
                personaId, d.getNombre(), d.getApellidoPaterno(), d.getApellidoMaterno(), d.getCurp(), d.getRfc(),
                d.getGenero(), d.getFechaNacimiento(), d.getTelefono(), d.getEmailPersonal(), d.getNacionalidad(),
                d.getUsuario(), d.getUsuario());
        UUID estudianteId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_estudiantes (id, matricula, persona_id, plantel_id, fecha_ingreso, nss, " +
                "escuela_procedencia, clave_ct_procedencia, promedio_procedencia, beca_tipo, beca_monto, folio_sep, " +
                "tipo_alumno, estatus_id, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'NUEVO'), ?, ?, ?)",
                estudianteId, d.getMatricula(), personaId, d.getPlantelId(), d.getFechaIngreso(), d.getNss(),
                d.getEscuelaProcedencia(), d.getClaveCtProcedencia(), d.getPromedioProcedencia(), d.getBecaTipo(),
                d.getBecaMonto(), d.getFolioSep(), d.getTipoAlumno(), d.getEstatusId(), d.getUsuario(), d.getUsuario());
        return estudianteId;
    }

    @Data
    @Builder
    public static class ProfesorData {
        private String nombre, apellidoPaterno, apellidoMaterno, curp, rfc, genero;
        private Object fechaNacimiento;
        private String telefono, emailPersonal;
        private UUID plantelId;
        private String numeroEmpleado, tipoContrato, nss, cedulaProfesional, especialidad, nivelEstudios;
        private Object fechaIngresoInst;
        private UUID estatusId;
        private String usuario;
    }

    @Transactional
    public void insertarProfesor(ProfesorData d) {
        UUID personaId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_personas (id, nombre, apellido_paterno, apellido_materno, curp, rfc, genero, " +
                "fecha_nacimiento, telefono, email_personal, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                personaId, d.getNombre(), d.getApellidoPaterno(), d.getApellidoMaterno(), d.getCurp(), d.getRfc(),
                d.getGenero(), d.getFechaNacimiento(), d.getTelefono(), d.getEmailPersonal(),
                d.getUsuario(), d.getUsuario());
        jdbc.update(
                "INSERT INTO ades_profesores (id, persona_id, plantel_id, numero_empleado, tipo_contrato, rfc, nss, " +
                "cedula_profesional, especialidad, nivel_estudios, fecha_ingreso_inst, estatus_id, " +
                "usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), personaId, d.getPlantelId(), d.getNumeroEmpleado(),
                d.getTipoContrato().toUpperCase(), d.getRfc(), d.getNss(), d.getCedulaProfesional(),
                d.getEspecialidad(), d.getNivelEstudios() == null ? null : d.getNivelEstudios().toUpperCase(),
                d.getFechaIngresoInst(), d.getEstatusId(), d.getUsuario(), d.getUsuario());
    }

    @Transactional
    public void insertarMateria(String nombre, String clave, UUID nivelId, String tipoMateria, Double horasSemana, String usuario) {
        // tipo_materia es NOT NULL sin default en ades_materias (chk_tipo_materia) —
        // sin este parámetro el INSERT fallaba siempre con violación de NOT NULL.
        jdbc.update(
                "INSERT INTO ades_materias (id, nombre_materia, clave_materia, nivel_educativo_id, tipo_materia, horas_semana, usuario_creacion, usuario_modificacion) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), nombre, clave, nivelId, tipoMateria, horasSemana, usuario, usuario);
    }

    @Transactional
    public void insertarGrupo(String nombre, UUID gradoId, UUID cicloId, String turno,
                               Integer capacidad, String usuario) {
        jdbc.update(
                "INSERT INTO ades_grupos (id, nombre_grupo, grado_id, ciclo_escolar_id, turno, capacidad_maxima, usuario_creacion, usuario_modificacion) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), nombre, gradoId, cicloId, turno.toUpperCase(), capacidad, usuario, usuario);
    }

    @Data
    @Builder
    public static class AulaData {
        private String nombreAula, claveAula, tipoAula;
        private Integer capacidadAlumnos;
        private UUID plantelId;
        private String edificio;
        private Integer piso;
        private boolean tieneProyector, tienePizarraDigital, tienePizarron, tieneAireAcondicionado, tieneInternet;
        private Integer numComputadoras;
        private String estadoAula, observaciones, usuario;
    }

    @Transactional
    public void insertarAula(AulaData d) {
        jdbc.update(
                "INSERT INTO ades_aulas (id, nombre_aula, clave_aula, tipo_aula, capacidad_alumnos, plantel_id, " +
                "edificio, piso, tiene_proyector, tiene_pizarra_digital, tiene_pizarron, tiene_aire_acondicionado, " +
                "tiene_internet, num_computadoras, estado_aula, observaciones, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), d.getNombreAula(), d.getClaveAula(), d.getTipoAula().toUpperCase(),
                d.getCapacidadAlumnos(), d.getPlantelId(), d.getEdificio(), d.getPiso(),
                d.isTieneProyector(), d.isTienePizarraDigital(), d.isTienePizarron(), d.isTieneAireAcondicionado(),
                d.isTieneInternet(), d.getNumComputadoras(), d.getEstadoAula().toUpperCase(),
                d.getObservaciones(), d.getUsuario(), d.getUsuario());
    }

    public List<Map<String, Object>> getRolPadreId() {
        return jdbc.queryForList("SELECT id FROM ades_roles WHERE nombre_rol = 'PADRE_FAMILIA' LIMIT 1");
    }

    // ── Datos de padre/tutor vinculado a un alumno ────────────────────────────

    @Data
    @Builder
    public static class PadreData {
        private String nombre, apellidoPaterno, apellidoMaterno, curp, email, telefono;
        private UUID rolPadreId;
        private String usuario;
    }

    /**
     * Crea persona + usuario PADRE_FAMILIA y vincula al estudiante.
     * Si ya existe persona con ese CURP, solo agrega la relación.
     * Si ya existe relación con el estudiante, no hace nada.
     */
    @Transactional
    public void insertarPadreYVincular(UUID estudianteId, PadreData d) {
        // Buscar persona existente por CURP
        UUID personaId = null;
        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT id FROM ades_personas WHERE UPPER(curp) = ?", d.getCurp().toUpperCase().trim());
        if (!existing.isEmpty()) {
            personaId = (UUID) existing.get(0).get("id");
        } else {
            personaId = UUID.randomUUID();
            jdbc.update(
                "INSERT INTO ades_personas (id, nombre, apellido_paterno, apellido_materno, curp, " +
                "email_personal, telefono, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                personaId, d.getNombre(), d.getApellidoPaterno(),
                d.getApellidoMaterno(), d.getCurp().toUpperCase().trim(),
                d.getEmail(), d.getTelefono(), d.getUsuario(), d.getUsuario());
        }

        // Crear usuario PADRE_FAMILIA si no existe ya
        List<Map<String, Object>> usuariosExist = jdbc.queryForList(
            "SELECT id FROM ades_usuarios WHERE persona_id = ? AND rol_id = ?",
            personaId, d.getRolPadreId());

        if (usuariosExist.isEmpty() && d.getRolPadreId() != null) {
            String base = (d.getNombre().substring(0, 1)
                + d.getApellidoPaterno().replace(" ", "")).toLowerCase();
            if (base.length() > 9) base = base.substring(0, 9);
            String username = base;
            int counter = 1;
            while (!jdbc.queryForList("SELECT id FROM ades_usuarios WHERE nombre_usuario = ?", username).isEmpty()) {
                username = base + counter++;
            }
            String email = d.getEmail() != null && !d.getEmail().isBlank()
                ? d.getEmail().trim() : (username + "@nevadi.edu.mx");
            jdbc.update(
                "INSERT INTO ades_usuarios (id, persona_id, nombre_usuario, email_institucional, " +
                "rol_id, is_active, usuario_creacion, usuario_modificacion) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, ?, true, ?, ?)",
                personaId, username, email, d.getRolPadreId(), d.getUsuario(), d.getUsuario());
        }

        // Vincular padre ↔ estudiante en ades_padres_estudiantes (si tabla existe)
        final UUID finalPersonaId = personaId;
        try {
            List<Map<String, Object>> vinculo = jdbc.queryForList(
                "SELECT id FROM ades_padres_estudiantes WHERE persona_padre_id = ? AND estudiante_id = ?",
                finalPersonaId, estudianteId);
            if (vinculo.isEmpty()) {
                jdbc.update(
                    "INSERT INTO ades_padres_estudiantes " +
                    "(id, persona_padre_id, estudiante_id, tipo_tutor, is_contacto_principal, " +
                    "usuario_creacion, usuario_modificacion) " +
                    "VALUES (gen_random_uuid(), ?, ?, 'TUTOR', true, ?, ?)",
                    finalPersonaId, estudianteId, d.getUsuario(), d.getUsuario());
            }
        } catch (Exception ignored) {
            // Si la tabla no existe aún, ignorar silenciosamente
        }
    }

    @Transactional
    public void insertarPreinscritoSEP(String nombre, String apellidoPaterno, String apellidoMaterno,
                                        String curp, Object fechaNacimiento, String nivel, Integer grado,
                                        UUID plantelId, UUID cicloId, String tutor, String telTutor,
                                        String emailTutor, String escuelaProcedencia, Double promedio,
                                        String usuario) {
        jdbc.update(
                "INSERT INTO public.ades_solicitudes_admision " +
                "(id, nombre, apellido_paterno, apellido_materno, fecha_nacimiento, curp, " +
                "nivel_solicitado, grado_solicitado, plantel_id, ciclo_escolar_id, " +
                "nombre_tutor, telefono_tutor, email_tutor, escuela_procedencia, promedio_procedencia, " +
                "estado, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', ?, ?)",
                UUID.randomUUID(), nombre, apellidoPaterno, apellidoMaterno, fechaNacimiento, curp,
                nivel, grado, plantelId, cicloId, tutor, telTutor, emailTutor,
                escuelaProcedencia, promedio, usuario, usuario);
    }
}

package mx.ades.modules.imports;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
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
    public void insertarAlumno(AlumnoData d) {
        UUID personaId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_personas (id, nombre, apellido_paterno, apellido_materno, curp, rfc, genero, " +
                "fecha_nacimiento, telefono, email_personal, nacionalidad, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'MEXICANA'), ?, ?)",
                personaId, d.getNombre(), d.getApellidoPaterno(), d.getApellidoMaterno(), d.getCurp(), d.getRfc(),
                d.getGenero(), d.getFechaNacimiento(), d.getTelefono(), d.getEmailPersonal(), d.getNacionalidad(),
                d.getUsuario(), d.getUsuario());
        jdbc.update(
                "INSERT INTO ades_estudiantes (id, matricula, persona_id, plantel_id, fecha_ingreso, nss, " +
                "escuela_procedencia, clave_ct_procedencia, promedio_procedencia, beca_tipo, beca_monto, folio_sep, " +
                "tipo_alumno, estatus_id, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'NUEVO'), ?, ?, ?)",
                UUID.randomUUID(), d.getMatricula(), personaId, d.getPlantelId(), d.getFechaIngreso(), d.getNss(),
                d.getEscuelaProcedencia(), d.getClaveCtProcedencia(), d.getPromedioProcedencia(), d.getBecaTipo(),
                d.getBecaMonto(), d.getFolioSep(), d.getTipoAlumno(), d.getEstatusId(), d.getUsuario(), d.getUsuario());
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
    public void insertarMateria(String nombre, String clave, UUID nivelId, Double horasSemana, String usuario) {
        jdbc.update(
                "INSERT INTO ades_materias (id, nombre_materia, clave_materia, nivel_educativo_id, horas_semana, usuario_creacion, usuario_modificacion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), nombre, clave, nivelId, horasSemana, usuario, usuario);
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

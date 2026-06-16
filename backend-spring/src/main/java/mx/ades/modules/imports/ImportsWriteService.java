package mx.ades.modules.imports;

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

    @Transactional
    public void insertarAlumno(String nombre, String apellidoPaterno, String apellidoMaterno,
                                String curp, String genero, Object fechaNacimiento,
                                UUID plantelId, String matricula, UUID estatusId, String usuario) {
        UUID personaId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_personas (id, nombre, apellido_paterno, apellido_materno, curp, genero, fecha_nacimiento, usuario_creacion, usuario_modificacion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                personaId, nombre, apellidoPaterno, apellidoMaterno, curp, genero, fechaNacimiento, usuario, usuario);
        jdbc.update(
                "INSERT INTO ades_estudiantes (id, matricula, persona_id, plantel_id, fecha_ingreso, estatus_id, usuario_creacion, usuario_modificacion) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), matricula, personaId, plantelId, null, estatusId, usuario, usuario);
    }

    @Transactional
    public void insertarProfesor(String nombre, String apellidoPaterno, String apellidoMaterno,
                                  String curp, String genero, Object fechaNacimiento,
                                  UUID plantelId, String numEmpleado, String tipoContrato,
                                  UUID estatusId, String usuario) {
        UUID personaId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_personas (id, nombre, apellido_paterno, apellido_materno, curp, genero, fecha_nacimiento, usuario_creacion, usuario_modificacion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                personaId, nombre, apellidoPaterno, apellidoMaterno, curp, genero, fechaNacimiento, usuario, usuario);
        jdbc.update(
                "INSERT INTO ades_profesores (id, persona_id, plantel_id, numero_empleado, tipo_contrato, estatus_id, usuario_creacion, usuario_modificacion) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), personaId, plantelId, numEmpleado, tipoContrato.toUpperCase(), estatusId, usuario, usuario);
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

    @Transactional
    public void insertarAula(String nombre, String tipoAula, Integer capacidad, UUID plantelId,
                              boolean tieneProyector, boolean tienePizarra, boolean tieneInternet,
                              String observaciones, String usuario) {
        jdbc.update(
                "INSERT INTO ades_aulas (id, nombre_aula, tipo_aula, capacidad_alumnos, plantel_id, tiene_proyector, tiene_pizarra_digital, tiene_internet, observaciones, usuario_creacion, usuario_modificacion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), nombre, tipoAula.toUpperCase(), capacidad, plantelId,
                tieneProyector, tienePizarra, tieneInternet, observaciones, usuario, usuario);
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

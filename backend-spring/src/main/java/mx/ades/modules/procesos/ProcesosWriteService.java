package mx.ades.modules.procesos;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;

@Component
public class ProcesosWriteService {

    private final JdbcTemplate jdbc;

    public ProcesosWriteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean checkDupCurp(String curp) {
        return !jdbc.queryForList(
                "SELECT id FROM ades_solicitudes_admision WHERE curp = ? AND estado NOT IN ('RECHAZADO')",
                curp.trim()).isEmpty();
    }

    /** Ver {@link #registrarSolicitudManual}: mismo check-then-insert atómico, variante SEP. */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean registrarSolicitudSEP(String nombre, String primerAp, String segundoAp,
                                          String fechaNac, String curp, String nivel, Integer grado,
                                          String tutor, String telTutor, String emailTutor,
                                          String usuario) {
        if (checkDupCurp(curp)) {
            return false;
        }
        insertarSolicitudSEP(nombre, primerAp, segundoAp, fechaNac, curp, nivel, grado, tutor, telTutor, emailTutor, usuario);
        return true;
    }

    public void insertarSolicitudSEP(String nombre, String primerAp, String segundoAp,
                                      String fechaNac, String curp, String nivel, Integer grado,
                                      String tutor, String telTutor, String emailTutor,
                                      String usuario) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_solicitudes_admision " +
                "(id, nombre, apellido_paterno, apellido_materno, fecha_nacimiento, curp, " +
                "nivel_solicitado, grado_solicitado, estado, nombre_tutor, telefono_tutor, email_tutor, " +
                "usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', ?, ?, ?, ?, ?)",
                id, nombre, primerAp,
                segundoAp.isBlank() ? null : segundoAp,
                mx.ades.modules.imports.ImportadorUtil.parseDate(fechaNac), curp,
                nivel.isBlank() ? "PRIMARIA" : nivel, grado == null ? 1 : grado,
                tutor.isBlank() ? "Tutor SEP" : tutor,
                telTutor.isBlank() ? "5500000000" : telTutor,
                emailTutor.isBlank() ? "sep@example.com" : emailTutor,
                usuario, usuario);
    }

    /**
     * Verifica CURP duplicado e inserta en una sola transacción SERIALIZABLE: evita que dos
     * solicitudes concurrentes con el mismo CURP pasen ambas el check-then-insert (race
     * condition — dos admisiones simultáneas del mismo alumno).
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public UUID registrarSolicitudManual(String nombre, String apellidoPaterno, String apellidoMaterno,
                                          String fechaNacimiento, String curp, String nivelSolicitado,
                                          Integer gradoSolicitado, UUID plantelId,
                                          String nombreTutor, String telefonoTutor, String emailTutor,
                                          String escuelaProcedencia, Double promedioProcedencia,
                                          String usuario) {
        if (checkDupCurp(curp)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una solicitud activa con este CURP");
        }
        return insertarSolicitudManual(nombre, apellidoPaterno, apellidoMaterno, fechaNacimiento, curp,
                nivelSolicitado, gradoSolicitado, plantelId, nombreTutor, telefonoTutor, emailTutor,
                escuelaProcedencia, promedioProcedencia, usuario);
    }

    public UUID insertarSolicitudManual(String nombre, String apellidoPaterno, String apellidoMaterno,
                                         String fechaNacimiento, String curp, String nivelSolicitado,
                                         Integer gradoSolicitado, UUID plantelId,
                                         String nombreTutor, String telefonoTutor, String emailTutor,
                                         String escuelaProcedencia, Double promedioProcedencia,
                                         String usuario) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_solicitudes_admision " +
                "(id, nombre, apellido_paterno, apellido_materno, fecha_nacimiento, curp, " +
                "nivel_solicitado, grado_solicitado, plantel_id, nombre_tutor, telefono_tutor, email_tutor, " +
                "escuela_procedencia, promedio_procedencia, estado, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', ?, ?)",
                id, nombre.trim(), apellidoPaterno.trim(),
                apellidoMaterno != null ? apellidoMaterno.trim() : null,
                fechaNacimiento, curp.trim(), nivelSolicitado, gradoSolicitado,
                plantelId, nombreTutor, telefonoTutor, emailTutor,
                escuelaProcedencia, promedioProcedencia, usuario, usuario);
        return id;
    }

    @Transactional
    public void actualizarResolucion(UUID id, String decision, String motivo,
                                      UUID grupoAsignadoId, UUID resueltoBy, String usuario) {
        jdbc.update(
                "UPDATE ades_solicitudes_admision " +
                "SET estado = ?, motivo_decision = ?, grupo_asignado_id = ?, " +
                "fecha_resolucion = CURRENT_TIMESTAMP, resuelto_por = ?, usuario_modificacion = ? " +
                "WHERE id = ?",
                decision, motivo, grupoAsignadoId, resueltoBy, usuario, id);
    }

    @Transactional
    public void enqueueNotificacion(UUID admisionId, String email, String nombre, String usuario) {
        String payload = String.format("{\"admision_id\": \"%s\", \"email\": \"%s\", \"nombre\": \"%s\"}",
                admisionId, email, nombre);
        jdbc.update("INSERT INTO ades_tareas_sistema (tipo_tarea, payload_json, estado, usuario_creacion, usuario_modificacion) " +
                "VALUES ('NOTIFICAR_LISTA_ESPERA', ?::jsonb, 'PENDIENTE', ?, ?)",
                payload, usuario, usuario);
    }

    @Transactional
    public void marcarNotificado(UUID id, String usuario) {
        jdbc.update("UPDATE ades_solicitudes_admision SET estado = 'NOTIFICADO', usuario_modificacion = ? WHERE id = ?",
                usuario, id);
    }

    @Transactional
    public UUID inscribirOptativa(UUID estudianteId, UUID materiaId, UUID cicloId, String usuario) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_inscripciones_optativas " +
                "(id, estudiante_id, materia_id, ciclo_escolar_id, fecha_inscripcion, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, CURRENT_DATE, ?, ?)",
                id, estudianteId, materiaId, cicloId, usuario, usuario);
        return id;
    }

    @Transactional
    public int darBajaOptativa(UUID id, String usuario) {
        return jdbc.update(
                "UPDATE ades_inscripciones_optativas SET is_active = FALSE, usuario_modificacion = ? WHERE id = ? AND is_active = TRUE",
                usuario, id);
    }

    @Transactional
    public UUID registrarAcuerdo(UUID alumnoId, String tutorNombre, String tutorFirmaHash,
                                  String ipFirma, String usuario) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_acuerdos_convivencia " +
                "(id, alumno_id, tutor_nombre, tutor_firma_hash, ip_firma, firmado_por_usuario, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, alumnoId, tutorNombre, tutorFirmaHash, ipFirma, usuario, usuario, usuario);
        return id;
    }

    @Transactional
    public UUID crearEventoCalendario(String nombre, UUID cicloId, String nivel, String tipo,
                                       LocalDate inicio, LocalDate fin, String descripcion,
                                       Boolean esOficial, String usuario) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_calendarios_academicos " +
                "(id, nombre, ciclo_escolar_id, nivel_educativo, tipo, fecha_inicio, fecha_fin, descripcion, es_oficial, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, nombre, cicloId, nivel, tipo, inicio, fin, descripcion, esOficial, usuario, usuario);
        return id;
    }

    @Transactional
    public UUID crearPeriodoEvaluacion(UUID cicloId, String nombre, String nivel, String tipoEval,
                                        LocalDate inicio, LocalDate fin, Boolean abierto, String usuario) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_periodos_evaluacion " +
                "(id, ciclo_escolar_id, nombre_periodo, nivel_educativo, tipo_evaluacion, fecha_inicio, fecha_fin, abierto, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, cicloId, nombre, nivel, tipoEval, inicio, fin, abierto, usuario, usuario);
        return id;
    }

    @Transactional
    public void cerrarPeriodo(UUID id, String usuario) {
        jdbc.update("UPDATE ades_periodos_evaluacion SET abierto = FALSE, usuario_modificacion = ? WHERE id = ?",
                usuario, id);
    }

    @Transactional
    public void registrarEvaluacionDiagnostica(UUID id, Double puntuacion, String obs, String usuario) {
        jdbc.update(
                "UPDATE ades_solicitudes_admision " +
                "SET puntuacion_diagnostico = ?, observaciones_diagnostico = ?, " +
                "estado = 'DIAGNOSTICO', usuario_modificacion = ? " +
                "WHERE id = ?",
                puntuacion, obs, usuario, id);
    }

    @Transactional
    public UUID registrarBaja(UUID estudianteId, UUID inscripcionId, String tipoBaja, String motivo,
                               LocalDate fechaEfectiva, String observaciones, String usuario) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_bajas " +
                "(id, estudiante_id, inscripcion_id, tipo_baja, motivo, fecha_efectiva, observaciones, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, estudianteId, inscripcionId, tipoBaja, motivo, fechaEfectiva, observaciones, usuario, usuario);
        jdbc.update("UPDATE ades_estudiantes SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                usuario, estudianteId);
        return id;
    }

    /**
     * Reactivates a student from a baja record. Includes capacity check for the original group.
     */
    @Transactional
    public void reactivarEstudiante(UUID bajaId, UUID estudianteId, UUID inscripcionId, String usuario) {
        jdbc.update("UPDATE ades_estudiantes SET is_active = TRUE, usuario_modificacion = ? WHERE id = ?",
                usuario, estudianteId);

        if (inscripcionId != null) {
            var ins = jdbc.queryForList(
                    "SELECT grupo_id FROM ades_inscripciones WHERE id = ?", inscripcionId);
            if (!ins.isEmpty()) {
                UUID gid = (UUID) ins.get(0).get("grupo_id");
                var cap = jdbc.queryForList(
                        "SELECT capacidad_maxima, " +
                        "(SELECT COUNT(*) FROM ades_inscripciones WHERE grupo_id = ? AND is_active = TRUE) AS inscritos " +
                        "FROM ades_grupos WHERE id = ?", gid, gid);
                if (!cap.isEmpty()) {
                    int max = ((Number) cap.get(0).get("capacidad_maxima")).intValue();
                    int cur = ((Number) cap.get(0).get("inscritos")).intValue();
                    if (cur >= max) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "El grupo asignado original está lleno. No se puede reactivar la inscripción.");
                    }
                    jdbc.update("UPDATE ades_inscripciones SET is_active = TRUE, usuario_modificacion = ? WHERE id = ?",
                            usuario, inscripcionId);
                }
            }
        }

        jdbc.update("UPDATE ades_bajas SET is_active = FALSE WHERE id = ?", bajaId);
    }
}

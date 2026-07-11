package mx.ades.modules.expediente;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Component
public class ExpedienteWriteService {

    private final JdbcTemplate jdbc;

    public ExpedienteWriteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public UUID insertExtraordinario(UUID estudianteId, UUID materiaId, UUID cicloEscolarId,
                                      UUID grupoId, String tipoExamen, Double calificacionPrevia,
                                      LocalDate fechaExamen, Double calificacion, Boolean acredita,
                                      String observaciones, UUID aplicadoPorId, String usuario) {
        UUID newId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_extraordinarias " +
            "(id, estudiante_id, materia_id, ciclo_escolar_id, grupo_id, tipo_examen, " +
            " calificacion_previa, fecha_examen, calificacion, acredita, observaciones, aplicado_por_id, " +
            " usuario_creacion, usuario_modificacion) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            newId, estudianteId, materiaId, cicloEscolarId, grupoId,
            tipoExamen, calificacionPrevia, fechaExamen,
            calificacion, acredita, observaciones, aplicadoPorId,
            usuario, usuario);
        return newId;
    }

    @Transactional
    public void marcarConstanciaEntregada(UUID constanciaId) {
        jdbc.update(
            "UPDATE ades_constancias SET entregada = TRUE, fecha_entrega = ?, usuario_modificacion = 'sistema' WHERE id = ? AND is_active = TRUE",
            LocalDate.now(), constanciaId);
    }

    @Transactional
    public UUID insertDocumentoExpediente(UUID expId, String tipoDocumento, String nombreArchivo, String usuario) {
        UUID nuevoId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO public.ades_expediente_documentos " +
            "(id, expediente_id, paperless_doc_id, tipo_documento, nombre_archivo, estado_ocr, cargado_por, fecha_carga, is_active) " +
            "VALUES (?, ?, NULL, ?, ?, 'PENDIENTE', ?, NOW(), TRUE)",
            nuevoId, expId, tipoDocumento, nombreArchivo, usuario);
        return nuevoId;
    }

    @Transactional
    public void softDeleteDocumento(UUID docId) {
        jdbc.update("UPDATE public.ades_expediente_documentos SET is_active = FALSE WHERE id = ?", docId);
    }

    @Transactional
    public void actualizarObservacionesExpediente(UUID expId, String observaciones) {
        jdbc.update(
            "UPDATE public.ades_expedientes_alumno SET observaciones = ? WHERE id = ?",
            observaciones, expId);
    }
}

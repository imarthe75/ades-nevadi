package mx.ades.modules.contactos.infrastructure.outbound.persistence;

import mx.ades.modules.contactos.domain.port.in.ActualizarContactoUseCase;
import mx.ades.modules.contactos.domain.port.in.RegistrarContactoUseCase;
import mx.ades.modules.contactos.domain.port.out.ContactosRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Adaptador JDBC que implementa {@link ContactosRepositoryPort}.
 * <p>Gestiona operaciones CRUD sobre {@code ades_contactos_familiares},
 * upsert de {@code ades_expedientes_medicos} y upsert de {@code ades_expediente_docs}.</p>
 *
 * @author ADES
 * @since 2026
 */
@Component
public class ContactosPersistenceAdapter implements ContactosRepositoryPort {

    private static final String SELECT_COLS =
        "SELECT id, persona_id, estudiante_id, nombre_completo, parentesco, telefono_principal, " +
        "email, es_tutor_legal, es_contacto_emergencia, puede_recoger, ocupacion, nivel_estudios, rfc, " +
        "nacionalidad, is_active, row_version FROM ades_contactos_familiares WHERE id = ?";

    private static final String SELECT_EXP =
        "SELECT estudiante_id, tipo_sangre, alergias, medicamentos_autorizados, condiciones_cronicas, " +
        "observaciones_generales, nss, discapacidad, seguro_medico_tipo, seguro_medico_numero, " +
        "vacunas_al_dia, padecimiento_cronico, requiere_medicacion FROM ades_expedientes_medicos WHERE estudiante_id = ?";

    private final JdbcTemplate jdbc;

    public ContactosPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Object> insertContacto(RegistrarContactoUseCase.Command cmd) {
        // ades_contactos_familiares.persona_id es NOT NULL (FK a ades_personas) pero el
        // formulario de alta solo captura un nombre_completo de una sola pieza (no hay
        // personaId existente que reutilizar como sí ocurre en portal_familias/AgregarTutorUseCase,
        // donde el tutor ya es una persona dada de alta). Se crea aquí la persona
        // correspondiente al contacto familiar para poder satisfacer el NOT NULL —
        // antes de este fix el INSERT de abajo violaba directamente el constraint
        // (DataIntegrityViolationException -> 409 "duplicado o referencia inválida" engañoso).
        UUID personaId = crearPersonaDesdeNombreCompleto(cmd.nombreCompleto());

        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_contactos_familiares " +
            "(id, estudiante_id, persona_id, nombre_completo, parentesco, telefono_principal, email, " +
            "es_tutor_legal, es_contacto_emergencia, puede_recoger, ocupacion, nivel_estudios, rfc, nacionalidad, " +
            "usuario_creacion, usuario_modificacion) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id, cmd.estudianteId(), personaId, cmd.nombreCompleto(), cmd.parentesco(), cmd.telefonoPrincipal(), cmd.email(),
            cmd.esTutorLegal(), cmd.esContactoEmergencia(), cmd.puedeRecoger(), cmd.ocupacion(),
            cmd.nivelEstudios(), cmd.rfc(), cmd.nacionalidad(), cmd.usuarioCreacion(), cmd.usuarioCreacion()
        );
        return jdbc.queryForList(SELECT_COLS, id).get(0);
    }

    /**
     * Crea un registro mínimo en {@code ades_personas} a partir de un nombre completo
     * de una sola pieza (tal como lo envía el formulario de contactos familiares).
     * {@code ades_personas.nombre} y {@code apellido_paterno} son NOT NULL; se hace un
     * split conservador: primera palabra = nombre, resto = apellido paterno (si solo hay
     * una palabra, se reutiliza como apellido paterno para no dejar el campo vacío).
     */
    private UUID crearPersonaDesdeNombreCompleto(String nombreCompleto) {
        String limpio = nombreCompleto == null ? "" : nombreCompleto.trim().replaceAll("\\s+", " ");
        String[] partes = limpio.isEmpty() ? new String[]{"Sin nombre"} : limpio.split(" ");
        String nombre = partes[0];
        String apellidoPaterno = partes.length > 1
            ? String.join(" ", Arrays.copyOfRange(partes, 1, partes.length))
            : partes[0];

        UUID personaId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_personas (id, nombre, apellido_paterno) VALUES (?, ?, ?)",
            personaId, nombre, apellidoPaterno);
        return personaId;
    }

    @Override
    public Optional<Map<String, Object>> fetchContactoForUpdate(UUID contactoId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT row_version, is_active FROM ades_contactos_familiares WHERE id = ?", contactoId);
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.get(0).get("is_active"))) return Optional.empty();
        return Optional.of(rows.get(0));
    }

    @Override
    public Map<String, Object> updateContacto(ActualizarContactoUseCase.Command cmd) {
        StringBuilder sql = new StringBuilder(
            "UPDATE ades_contactos_familiares SET row_version = row_version + 1, usuario_modificacion = ?");
        List<Object> params = new ArrayList<>();
        params.add(cmd.usuarioModificacion());

        if (cmd.nombreCompleto() != null) { sql.append(", nombre_completo = ?"); params.add(cmd.nombreCompleto()); }
        if (cmd.parentesco() != null) { sql.append(", parentesco = ?"); params.add(cmd.parentesco()); }
        if (cmd.telefonoPrincipal() != null) { sql.append(", telefono_principal = ?"); params.add(cmd.telefonoPrincipal()); }
        if (cmd.email() != null) { sql.append(", email = ?"); params.add(cmd.email()); }
        if (cmd.esTutorLegal() != null) { sql.append(", es_tutor_legal = ?"); params.add(cmd.esTutorLegal()); }
        if (cmd.esContactoEmergencia() != null) { sql.append(", es_contacto_emergencia = ?"); params.add(cmd.esContactoEmergencia()); }
        if (cmd.puedeRecoger() != null) { sql.append(", puede_recoger = ?"); params.add(cmd.puedeRecoger()); }
        if (cmd.ocupacion() != null) { sql.append(", ocupacion = ?"); params.add(cmd.ocupacion()); }
        if (cmd.nivelEstudios() != null) { sql.append(", nivel_estudios = ?"); params.add(cmd.nivelEstudios()); }
        if (cmd.rfc() != null) { sql.append(", rfc = ?"); params.add(cmd.rfc()); }
        if (cmd.nacionalidad() != null) { sql.append(", nacionalidad = ?"); params.add(cmd.nacionalidad()); }

        sql.append(" WHERE id = ?");
        params.add(cmd.contactoId());
        jdbc.update(sql.toString(), params.toArray());
        return jdbc.queryForList(SELECT_COLS, cmd.contactoId()).get(0);
    }

    @Override
    public void softDeleteContacto(UUID contactoId) {
        int rows = jdbc.update("UPDATE ades_contactos_familiares SET is_active = FALSE WHERE id = ?", contactoId);
        if (rows == 0) throw new IllegalStateException("Contacto no encontrado");
    }

    @Override
    public Map<String, Object> fetchOrCreateExpedienteMedico(UUID estudianteId) {
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT_EXP, estudianteId);
        if (rows.isEmpty()) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO ades_expedientes_medicos (id, estudiante_id, vacunas_al_dia, padecimiento_cronico, requiere_medicacion) " +
                        "VALUES (?, ?, TRUE, FALSE, FALSE)", id, estudianteId);
            rows = jdbc.queryForList(SELECT_EXP, estudianteId);
        }
        return rows.get(0);
    }

    @Override
    public Map<String, Object> upsertExpedienteMedico(UUID estudianteId, Map<String, Object> fields, String usuarioMod) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM ades_expedientes_medicos WHERE estudiante_id = ?", estudianteId);
        if (rows.isEmpty()) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO ades_expedientes_medicos (id, estudiante_id, vacunas_al_dia, padecimiento_cronico, requiere_medicacion) " +
                        "VALUES (?, ?, TRUE, FALSE, FALSE)", id, estudianteId);
        }
        jdbc.update(
            "UPDATE ades_expedientes_medicos SET " +
            "tipo_sangre = ?, alergias = ?, medicamentos_autorizados = ?, condiciones_cronicas = ?, " +
            "observaciones_generales = ?, nss = ?, discapacidad = ?, seguro_medico_tipo = ?, " +
            "seguro_medico_numero = ?, vacunas_al_dia = ?, padecimiento_cronico = ?, requiere_medicacion = ?, " +
            "usuario_modificacion = ? WHERE estudiante_id = ?",
            fields.get("tipo_sangre"), fields.get("alergias"), fields.get("medicamentos_autorizados"),
            fields.get("condiciones_cronicas"), fields.get("observaciones_generales"), fields.get("nss"),
            fields.get("discapacidad"), fields.get("seguro_medico_tipo"), fields.get("seguro_medico_numero"),
            fields.get("vacunas_al_dia"), fields.get("padecimiento_cronico"), fields.get("requiere_medicacion"),
            usuarioMod, estudianteId
        );
        return jdbc.queryForList(SELECT_EXP, estudianteId).get(0);
    }

    @Override
    public void upsertDocEstatus(UUID estudianteId, UUID docTipoId, UUID cicloId,
                                 String estatus, String observaciones, String username, UUID verificadoPorId) {
        String checkSql = "SELECT id FROM ades_expediente_docs WHERE estudiante_id = ? AND documento_tipo_id = ?";
        List<Map<String, Object>> rows;
        if (cicloId != null) {
            rows = jdbc.queryForList(checkSql + " AND ciclo_escolar_id = ?", estudianteId, docTipoId, cicloId);
        } else {
            rows = jdbc.queryForList(checkSql, estudianteId, docTipoId);
        }
        if (rows.isEmpty()) {
            UUID id = UUID.randomUUID();
            jdbc.update(
                "INSERT INTO ades_expediente_docs " +
                "(id, estudiante_id, documento_tipo_id, ciclo_escolar_id, estatus, verificado_por_id, observaciones, usuario_creacion, usuario_modificacion) " +
                "VALUES (?,?,?,?,?,?,?,?,?)",
                id, estudianteId, docTipoId, cicloId, estatus, verificadoPorId, observaciones, username, username
            );
        } else {
            UUID id = (UUID) rows.get(0).get("id");
            StringBuilder sql = new StringBuilder("UPDATE ades_expediente_docs SET estatus = ?, observaciones = ?, usuario_modificacion = ?");
            List<Object> params = new ArrayList<>();
            params.add(estatus); params.add(observaciones); params.add(username);
            if ("ENTREGADO".equals(estatus)) {
                sql.append(", fecha_entrega = CURRENT_DATE, verificado_por_id = ?");
                params.add(verificadoPorId);
            }
            sql.append(" WHERE id = ?");
            params.add(id);
            jdbc.update(sql.toString(), params.toArray());
        }
    }
}

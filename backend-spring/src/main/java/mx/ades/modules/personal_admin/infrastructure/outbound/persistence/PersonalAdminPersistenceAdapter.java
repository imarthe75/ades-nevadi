package mx.ades.modules.personal_admin.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.common.PiiEncryptionService;
import mx.ades.modules.personal_admin.domain.port.in.RegistrarPersonalAdminUseCase;
import mx.ades.modules.personal_admin.domain.port.out.PersonalAdminRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class PersonalAdminPersistenceAdapter implements PersonalAdminRepositoryPort {

    private final JdbcTemplate jdbc;
    private final PiiEncryptionService pii;

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static java.sql.Date toDate(Object val) {
        if (val == null) return null;
        return java.sql.Date.valueOf(val.toString().substring(0, 10));
    }

    @Override
    public List<Map<String, Object>> list(UUID plantelId, String tipoRol, String buscar) {
        StringBuilder sql = new StringBuilder(
            "SELECT pa.id, pa.numero_empleado, pa.tipo_rol, pa.area, pa.tipo_contrato, pa.nivel_estudios, " +
            "pa.cedula_profesional, pa.especialidad, pa.turno, pa.fecha_ingreso_inst, pa.rfc, pa.nss, " +
            "pa.is_active, pa.plantel_id, pa.persona_id, " +
            "p.nombre, p.apellido_paterno, p.apellido_materno, p.curp, p.telefono, p.email_personal, " +
            "pl.nombre_plantel " +
            "FROM ades_personal_administrativo pa " +
            "JOIN ades_personas p ON p.id = pa.persona_id " +
            "JOIN ades_planteles pl ON pl.id = pa.plantel_id " +
            "WHERE pa.is_active = TRUE ");
        List<Object> params = new ArrayList<>();

        if (plantelId != null) { sql.append("AND pa.plantel_id = ? "); params.add(plantelId); }
        if (tipoRol != null && !tipoRol.isBlank()) { sql.append("AND pa.tipo_rol = ? "); params.add(tipoRol.trim().toUpperCase()); }
        if (buscar != null && !buscar.isBlank()) {
            sql.append("AND (p.nombre ILIKE ? OR p.apellido_paterno ILIKE ? OR p.apellido_materno ILIKE ? OR CONCAT(p.nombre,' ',p.apellido_paterno) ILIKE ?) ");
            String like = "%" + buscar.trim() + "%";
            params.add(like); params.add(like); params.add(like); params.add(like);
        }
        sql.append("ORDER BY pa.tipo_rol, p.apellido_paterno, p.nombre");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @Override
    public Optional<Map<String, Object>> findById(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT pa.*, p.nombre, p.apellido_paterno, p.apellido_materno, p.curp, " +
            "p.genero, p.fecha_nacimiento, p.telefono, p.email_personal, p.estado_civil, " +
            "p.municipio_nacimiento, p.estado_nacimiento, p.pais_nacimiento, p.nacionalidad, p.foto_url, " +
            "pl.nombre_plantel " +
            "FROM ades_personal_administrativo pa " +
            "JOIN ades_personas p ON p.id = pa.persona_id " +
            "JOIN ades_planteles pl ON pl.id = pa.plantel_id " +
            "WHERE pa.id = ?", id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<UUID> findPersonaId(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT persona_id FROM ades_personal_administrativo WHERE id = ? AND is_active = TRUE", id);
        if (rows.isEmpty()) return Optional.empty();
        return Optional.ofNullable((UUID) rows.get(0).get("persona_id"));
    }

    @Override
    public UUID createPersona(Map<String, Object> per, String usuario) {
        UUID personaId = UUID.randomUUID();
        String curp = str(per.get("curp"));
        String telefono = str(per.get("telefono"));
        String email = str(per.get("email_personal"));
        jdbc.update(
            "INSERT INTO ades_personas " +
            "(id, nombre, apellido_paterno, apellido_materno, curp, curp_encrypted, curp_hash, " +
            "genero, fecha_nacimiento, " +
            "telefono, telefono_encrypted, telefono_hash, " +
            "email_personal, email_personal_encrypted, email_personal_hash, " +
            "estado_civil, pais_nacimiento, municipio_nacimiento, " +
            "estado_nacimiento, nacionalidad, pii_encryption_status, usuario_creacion, usuario_modificacion) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            personaId, per.get("nombre"), per.get("apellido_paterno"), per.get("apellido_materno"),
            curp, pii.encrypt(curp), pii.hash(curp),
            per.get("genero"), toDate(per.get("fecha_nacimiento")),
            telefono, pii.encrypt(telefono), pii.hash(telefono),
            email, pii.encrypt(email), pii.hash(email),
            per.get("estado_civil"),
            per.getOrDefault("pais_nacimiento", "México"),
            per.get("municipio_nacimiento"), per.get("estado_nacimiento"),
            per.getOrDefault("nacionalidad", "Mexicana"), "completado", usuario, usuario);
        return personaId;
    }

    @Override
    public UUID createEmpleado(UUID personaId, UUID plantelId, Map<String, Object> lab, String usuario) {
        UUID empleadoId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_personal_administrativo " +
            "(id, persona_id, plantel_id, numero_empleado, tipo_rol, area, tipo_contrato, nivel_estudios, " +
            "cedula_profesional, especialidad, turno, rfc, nss, clabe, banco, " +
            "fecha_ingreso_inst, fecha_fin_contrato, usuario_creacion, usuario_modificacion) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            empleadoId, personaId, plantelId, lab.get("numero_empleado"),
            lab.get("tipo_rol") != null ? lab.get("tipo_rol").toString().toUpperCase() : null,
            lab.get("area"), lab.get("tipo_contrato"), lab.get("nivel_estudios"),
            lab.get("cedula_profesional"), lab.get("especialidad"), lab.get("turno"),
            lab.get("rfc"), lab.get("nss"), lab.get("clabe"), lab.get("banco"),
            toDate(lab.get("fecha_ingreso_inst")), toDate(lab.get("fecha_fin_contrato")),
            usuario, usuario);
        return empleadoId;
    }

    @Override
    public void updatePersona(UUID personaId, Map<String, Object> per, String usuario) {
        String curp = str(per.get("curp"));
        String telefono = str(per.get("telefono"));
        String email = str(per.get("email_personal"));
        jdbc.update(
            "UPDATE ades_personas SET " +
            "nombre=COALESCE(?,nombre), apellido_paterno=COALESCE(?,apellido_paterno), " +
            "apellido_materno=?, curp=COALESCE(?,curp), " +
            "curp_encrypted=COALESCE(?,curp_encrypted), curp_hash=COALESCE(?,curp_hash), " +
            "genero=?, fecha_nacimiento=?, " +
            "telefono=?, telefono_encrypted=?, telefono_hash=?, " +
            "email_personal=?, email_personal_encrypted=?, email_personal_hash=?, " +
            "estado_civil=?, pais_nacimiento=?, " +
            "municipio_nacimiento=?, estado_nacimiento=?, nacionalidad=COALESCE(?,nacionalidad), " +
            "usuario_modificacion=? WHERE id=?",
            per.get("nombre"), per.get("apellido_paterno"), per.get("apellido_materno"),
            curp, pii.encrypt(curp), pii.hash(curp),
            per.get("genero"), toDate(per.get("fecha_nacimiento")),
            telefono, pii.encrypt(telefono), pii.hash(telefono),
            email, pii.encrypt(email), pii.hash(email),
            per.get("estado_civil"),
            per.get("pais_nacimiento"), per.get("municipio_nacimiento"), per.get("estado_nacimiento"),
            per.get("nacionalidad"), usuario, personaId);
    }

    @Override
    public void updateEmpleado(UUID id, Map<String, Object> lab, String usuario) {
        jdbc.update(
            "UPDATE ades_personal_administrativo SET " +
            "tipo_rol=COALESCE(UPPER(?),tipo_rol), numero_empleado=?, area=?, tipo_contrato=?, " +
            "nivel_estudios=?, cedula_profesional=?, especialidad=?, turno=?, rfc=?, nss=?, " +
            "clabe=?, banco=?, fecha_ingreso_inst=?, fecha_fin_contrato=?, usuario_modificacion=? " +
            "WHERE id=?",
            lab.get("tipo_rol"), lab.get("numero_empleado"), lab.get("area"),
            lab.get("tipo_contrato"), lab.get("nivel_estudios"), lab.get("cedula_profesional"),
            lab.get("especialidad"), lab.get("turno"), lab.get("rfc"), lab.get("nss"),
            lab.get("clabe"), lab.get("banco"),
            toDate(lab.get("fecha_ingreso_inst")), toDate(lab.get("fecha_fin_contrato")),
            usuario, id);
    }

    @Override
    public int softDelete(UUID id) {
        return jdbc.update(
            "UPDATE ades_personal_administrativo SET is_active = FALSE WHERE id = ? AND is_active = TRUE", id);
    }

    @Override
    public Map<String, Object> fetchById(UUID id) {
        return findById(id).orElse(Map.of());
    }
}

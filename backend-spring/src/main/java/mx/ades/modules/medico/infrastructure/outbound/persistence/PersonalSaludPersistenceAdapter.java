package mx.ades.modules.medico.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.medico.domain.port.in.ActualizarPersonalSaludUseCase;
import mx.ades.modules.medico.domain.port.in.RegistrarPersonalSaludUseCase;
import mx.ades.modules.medico.domain.port.out.PersonalSaludRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PersonalSaludPersistenceAdapter implements PersonalSaludRepositoryPort {

    private final JdbcTemplate jdbc;

    @Override
    public UUID insert(RegistrarPersonalSaludUseCase.Command cmd) {
        Map<String, Object> per = cmd.persona();
        Map<String, Object> lab = cmd.laborales();
        String user = cmd.usuario();

        UUID personaId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO ades_personas
              (id, nombre, apellido_paterno, apellido_materno, curp, genero,
               fecha_nacimiento, telefono, email_personal, estado_civil,
               pais_nacimiento, municipio_nacimiento, estado_nacimiento, nacionalidad,
               usuario_creacion, usuario_modificacion)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            personaId,
            per.get("nombre"), per.get("apellido_paterno"), per.get("apellido_materno"),
            per.get("curp"), per.get("genero"),
            toDate(per.get("fecha_nacimiento")),
            per.get("telefono"), per.get("email_personal"), per.get("estado_civil"),
            per.getOrDefault("pais_nacimiento", "México"),
            per.get("municipio_nacimiento"), per.get("estado_nacimiento"),
            per.getOrDefault("nacionalidad", "Mexicana"),
            user, user);

        UUID saludId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO ades_personal_salud
              (id, persona_id, plantel_id, numero_empleado, cedula_profesional,
               especialidad, tipo_contrato, nivel_estudios, turno,
               rfc, nss, clabe, banco, fecha_ingreso_inst, fecha_fin_contrato,
               usuario_creacion, usuario_modificacion)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            saludId, personaId, cmd.plantelId(),
            lab.get("numero_empleado"), lab.get("cedula_profesional"),
            lab.get("especialidad"), lab.get("tipo_contrato"), lab.get("nivel_estudios"),
            lab.get("turno"), lab.get("rfc"), lab.get("nss"), lab.get("clabe"), lab.get("banco"),
            toDate(lab.get("fecha_ingreso_inst")),
            toDate(lab.get("fecha_fin_contrato")),
            user, user);

        return saludId;
    }

    @Override
    public void update(UUID saludId, ActualizarPersonalSaludUseCase.Command cmd) {
        String user = cmd.usuario();

        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT persona_id FROM ades_personal_salud WHERE id = ? AND is_active = TRUE", saludId);
        if (existing.isEmpty()) return;
        UUID personaId = (UUID) existing.get(0).get("persona_id");

        Map<String, Object> per = cmd.persona();
        if (per != null) {
            jdbc.update("""
                UPDATE ades_personas SET
                  nombre=COALESCE(?,nombre), apellido_paterno=COALESCE(?,apellido_paterno),
                  apellido_materno=?, curp=COALESCE(?,curp), genero=?,
                  fecha_nacimiento=?, telefono=?, email_personal=?, estado_civil=?,
                  pais_nacimiento=?, municipio_nacimiento=?, estado_nacimiento=?,
                  nacionalidad=COALESCE(?,nacionalidad), usuario_modificacion=?
                WHERE id=?
                """,
                per.get("nombre"), per.get("apellido_paterno"), per.get("apellido_materno"),
                per.get("curp"), per.get("genero"),
                toDate(per.get("fecha_nacimiento")),
                per.get("telefono"), per.get("email_personal"), per.get("estado_civil"),
                per.get("pais_nacimiento"), per.get("municipio_nacimiento"), per.get("estado_nacimiento"),
                per.get("nacionalidad"), user, personaId);
        }

        Map<String, Object> lab = cmd.laborales();
        if (lab != null) {
            jdbc.update("""
                UPDATE ades_personal_salud SET
                  numero_empleado=?, cedula_profesional=?, especialidad=?,
                  tipo_contrato=?, nivel_estudios=?, turno=?,
                  rfc=?, nss=?, clabe=?, banco=?,
                  fecha_ingreso_inst=?, fecha_fin_contrato=?,
                  usuario_modificacion=?
                WHERE id=?
                """,
                lab.get("numero_empleado"), lab.get("cedula_profesional"), lab.get("especialidad"),
                lab.get("tipo_contrato"), lab.get("nivel_estudios"), lab.get("turno"),
                lab.get("rfc"), lab.get("nss"), lab.get("clabe"), lab.get("banco"),
                toDate(lab.get("fecha_ingreso_inst")),
                toDate(lab.get("fecha_fin_contrato")),
                user, saludId);
        }
    }

    @Override
    public int softDelete(UUID saludId) {
        return jdbc.update(
            "UPDATE ades_personal_salud SET is_active = FALSE WHERE id = ? AND is_active = TRUE", saludId);
    }

    @Override
    public Map<String, Object> fetchById(UUID saludId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT ps.*,
                   p.nombre, p.apellido_paterno, p.apellido_materno, p.curp,
                   p.genero, p.fecha_nacimiento, p.telefono, p.email_personal,
                   p.estado_civil, p.municipio_nacimiento, p.estado_nacimiento,
                   p.pais_nacimiento, p.nacionalidad, p.foto_url,
                   pl.nombre_plantel
            FROM ades_personal_salud ps
            JOIN ades_personas p   ON p.id  = ps.persona_id
            JOIN ades_planteles pl ON pl.id = ps.plantel_id
            WHERE ps.id = ?
            """, saludId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Date toDate(Object value) {
        if (value == null) return null;
        return Date.valueOf(value.toString().substring(0, 10));
    }
}

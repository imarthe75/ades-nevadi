package mx.ades.modules.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
public class AdminWriteService {

    private final JdbcTemplate jdbc;

    public AdminWriteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void desactivarCiclosAnteriores(UUID nivelEducativoId, UUID exceptCicloId) {
        jdbc.update("UPDATE ades_ciclos_escolares SET es_vigente = FALSE " +
                "WHERE nivel_educativo_id = ? AND id != ?", nivelEducativoId, exceptCicloId);
    }

    public UUID insertPersona(String nombre, String apellidoPaterno, String apellidoMaterno,
                               String curp, String genero, LocalDate fechaNacimiento) {
        UUID personaId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_personas (id, nombre, apellido_paterno, apellido_materno, curp, genero, fecha_nacimiento) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            personaId, nombre, apellidoPaterno, apellidoMaterno, curp, genero, fechaNacimiento);
        return personaId;
    }

    public UUID insertUsuario(UUID personaId, String username, String email,
                               UUID rolId, UUID plantelId, UUID nivelEducativoId) {
        UUID userId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO ades_usuarios (id, persona_id, nombre_usuario, email_institucional, rol_id, plantel_id, nivel_educativo_id, clave_hash) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDIENTE_OIDC')",
            userId, personaId, username, email, rolId, plantelId, nivelEducativoId);
        return userId;
    }
}

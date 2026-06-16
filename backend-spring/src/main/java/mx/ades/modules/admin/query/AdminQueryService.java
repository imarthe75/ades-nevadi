package mx.ades.modules.admin.query;

import mx.ades.security.AdesUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminQueryService {

    private final JdbcTemplate jdbc;

    public AdminQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listarCiclos(String nivel) {
        StringBuilder sql = new StringBuilder(
            "SELECT c.id, c.nombre_ciclo, c.nivel_educativo_id, c.fecha_inicio, c.fecha_fin, " +
            "c.tipo_ciclo, c.es_vigente, c.is_active, n.nombre_nivel " +
            "FROM ades_ciclos_escolares c " +
            "JOIN ades_niveles_educativos n ON n.id = c.nivel_educativo_id " +
            "WHERE c.is_active = TRUE ");
        List<Object> params = new ArrayList<>();
        if (nivel != null && !nivel.isBlank()) {
            sql.append("AND UPPER(n.nombre_nivel) = ? ");
            params.add(nivel.toUpperCase());
        }
        sql.append("ORDER BY c.fecha_inicio DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> listarUsuarios(String buscar, String rol,
                                                     UUID plantelId, int pagina, int porPagina) {
        StringBuilder sql = new StringBuilder(
            "SELECT u.id, u.nombre_usuario, u.email_institucional, u.plantel_id, u.nivel_educativo_id, u.is_active, " +
            "p.nombre || ' ' || p.apellido_paterno || COALESCE(' ' || p.apellido_materno, '') AS nombre_completo, " +
            "r.nombre_rol AS rol, r.nivel_acceso, " +
            "pl.nombre_plantel, nl.nombre_nivel " +
            "FROM ades_usuarios u " +
            "JOIN ades_personas p ON p.id = u.persona_id " +
            "JOIN ades_roles r ON r.id = u.rol_id " +
            "LEFT JOIN ades_planteles pl ON pl.id = u.plantel_id " +
            "LEFT JOIN ades_niveles_educativos nl ON nl.id = u.nivel_educativo_id " +
            "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (plantelId != null) {
            sql.append("AND u.plantel_id = ? ");
            params.add(plantelId);
        }
        if (rol != null && !rol.isBlank()) {
            sql.append("AND UPPER(r.nombre_rol) = ? ");
            params.add(rol.toUpperCase());
        }
        if (buscar != null && !buscar.isBlank()) {
            sql.append("AND (u.nombre_usuario ILIKE ? OR u.email_institucional ILIKE ? OR p.nombre ILIKE ? OR p.apellido_paterno ILIKE ?) ");
            String term = "%" + buscar + "%";
            params.add(term); params.add(term); params.add(term); params.add(term);
        }
        sql.append("ORDER BY r.nivel_acceso ASC, u.nombre_usuario ASC LIMIT ? OFFSET ?");
        params.add(porPagina);
        params.add((pagina - 1) * porPagina);
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> listarGrupos(UUID plantelId, UUID nivelEducativoId, UUID cicloId) {
        StringBuilder sql = new StringBuilder(
            "SELECT g.id, g.nombre_grupo, g.capacidad_maxima, g.turno, g.ciclo_escolar_id, g.is_active, " +
            "gr.nombre_grado, gr.numero_grado, n.nombre_nivel " +
            "FROM ades_grupos g " +
            "JOIN ades_grados gr ON gr.id = g.grado_id " +
            "JOIN ades_niveles_educativos n ON n.id = gr.nivel_educativo_id " +
            "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (plantelId != null) {
            sql.append("AND gr.plantel_id = ? ");
            params.add(plantelId);
        }
        if (nivelEducativoId != null) {
            sql.append("AND gr.nivel_educativo_id = ? ");
            params.add(nivelEducativoId);
        }
        if (cicloId != null) {
            sql.append("AND g.ciclo_escolar_id = ? ");
            params.add(cicloId);
        }
        sql.append("ORDER BY n.nombre_nivel ASC, gr.numero_grado ASC, g.nombre_grupo ASC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public boolean curpExiste(String curp) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id FROM ades_personas WHERE UPPER(curp) = ?", curp.toUpperCase().trim());
        return !rows.isEmpty();
    }

    public boolean usernameExiste(String username) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id FROM ades_usuarios WHERE nombre_usuario = ?", username);
        return !rows.isEmpty();
    }
}

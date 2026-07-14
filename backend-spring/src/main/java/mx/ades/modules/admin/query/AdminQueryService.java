package mx.ades.modules.admin.query;

import mx.ades.security.AdesUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de consulta (lado lectura CQRS) para el módulo admin.
 *
 * <p>Provee lecturas de ciclos, usuarios, grupos, roles, menús y permisos por rol,
 * incluyendo operaciones de upsert sobre la tabla {@code ades_menu_roles}.</p>
 *
 * @author ADES
 * @since 2026
 */
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
            "SELECT u.id, u.nombre_usuario, u.email_institucional, u.plantel_id, u.nivel_educativo_id, u.is_active, u.row_version, " +
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

    // ── ROLES ────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> listarRoles() {
        return jdbc.queryForList(
            "SELECT id, nombre_rol, nivel_acceso, descripcion FROM ades_roles ORDER BY nivel_acceso, nombre_rol");
    }

    // ── MENÚS ────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> listarMenus() {
        return jdbc.queryForList(
            "SELECT id, clave, seccion, label, icon AS icono, route AS ruta, " +
            "nivel_maximo, nivel_minimo, is_active AS activo, peso AS orden " +
            "FROM ades_menus ORDER BY peso ASC");
    }

    @Transactional
    public Map<String, Object> actualizarMenu(String clave, String label,
                                               Integer nivelMaximo, Integer nivelMinimo, Boolean activo) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE ades_menus SET fecha_modificacion = now()");
        if (label != null)       { sql.append(", label = ?");         params.add(label); }
        if (nivelMaximo != null) { sql.append(", nivel_maximo = ?");  params.add(nivelMaximo); }
        if (nivelMinimo != null) { sql.append(", nivel_minimo = ?");  params.add(nivelMinimo); }
        if (activo != null)      { sql.append(", is_active = ?");     params.add(activo); }
        sql.append(" WHERE clave = ?");
        params.add(clave);
        int rows = jdbc.update(sql.toString(), params.toArray());
        if (rows == 0) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "Menú no encontrado: " + clave);
        return Map.of("ok", true, "clave", clave);
    }

    // ── PERMISOS POR ROL ─────────────────────────────────────────────────────

    public List<Map<String, Object>> listarPermisosRol(UUID rolId) {
        if (rolId != null) {
            return jdbc.queryForList(
                "SELECT mr.ref AS id, mr.rol_id, r.nombre_rol, mr.menu_id, m.clave AS menu_clave, m.label, " +
                "mr.puede_ver, mr.puede_editar, mr.puede_crear, mr.puede_eliminar " +
                "FROM ades_menu_roles mr " +
                "JOIN ades_roles r ON r.id = mr.rol_id " +
                "JOIN ades_menus m ON m.id = mr.menu_id " +
                "WHERE mr.rol_id = ? " +
                "ORDER BY m.peso",
                rolId);
        }
        return jdbc.queryForList(
            "SELECT mr.ref AS id, mr.rol_id, r.nombre_rol, mr.menu_id, m.clave AS menu_clave, m.label, " +
            "mr.puede_ver, mr.puede_editar, mr.puede_crear, mr.puede_eliminar " +
            "FROM ades_menu_roles mr " +
            "JOIN ades_roles r ON r.id = mr.rol_id " +
            "JOIN ades_menus m ON m.id = mr.menu_id " +
            "ORDER BY r.nivel_acceso, m.peso");
    }

    @Transactional
    public Map<String, Object> upsertPermisoRol(UUID rolId, String menuClave,
                                                  Boolean puedeVer, Boolean puedeEditar,
                                                  Boolean puedeCrear, Boolean puedeEliminar) {
        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT mr.ref FROM ades_menu_roles mr " +
            "JOIN ades_menus m ON m.id = mr.menu_id " +
            "WHERE mr.rol_id = ? AND m.clave = ?", rolId, menuClave);

        boolean pVer = puedeVer != null ? puedeVer : true;
        boolean pEdt = puedeEditar != null ? puedeEditar : true;
        boolean pCre = puedeCrear != null ? puedeCrear : true;
        boolean pEli = puedeEliminar != null ? puedeEliminar : false;

        if (existing.isEmpty()) {
            List<Map<String, Object>> menus = jdbc.queryForList(
                "SELECT id FROM ades_menus WHERE clave = ?", menuClave);
            if (menus.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Menú no encontrado: " + menuClave);
            UUID menuId = (UUID) menus.get(0).get("id");
            jdbc.update(
                "INSERT INTO ades_menu_roles (rol_id, menu_id, puede_ver, puede_editar, puede_crear, puede_eliminar) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                rolId, menuId, pVer, pEdt, pCre, pEli);
        } else {
            UUID ref = (UUID) existing.get(0).get("ref");
            jdbc.update(
                "UPDATE ades_menu_roles SET puede_ver=?, puede_editar=?, puede_crear=?, puede_eliminar=?, " +
                "fecha_modificacion=now() WHERE ref=?",
                pVer, pEdt, pCre, pEli, ref);
        }
        return Map.of("ok", true, "rol_id", rolId, "menu_clave", menuClave);
    }
}
